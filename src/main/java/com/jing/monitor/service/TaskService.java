package com.jing.monitor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.jing.monitor.core.CourseCrawler;
import com.jing.monitor.model.Course;
import com.jing.monitor.model.CourseSection;
import com.jing.monitor.model.SectionInfo;
import com.jing.monitor.model.User;
import com.jing.monitor.model.UserSectionSubscription;
import com.jing.monitor.model.dto.TaskRespDto;
import com.jing.monitor.repository.CourseRepository;
import com.jing.monitor.repository.CourseSectionRepository;
import com.jing.monitor.repository.UserRepository;
import com.jing.monitor.repository.UserSectionSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Locale;

/**
 * Application service for authenticated section search and subscription management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskService {

    private static final Duration SEARCH_MISS_TTL = Duration.ofMinutes(2);

    private final CourseCrawler crawler;
    private final CourseRepository courseRepository;
    private final CourseSectionRepository courseSectionRepository;
    private final UserSectionSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final AuthContextService authContextService;
    private final StringRedisTemplate redisTemplate;

    /**
     * Returns all section subscriptions owned by the current authenticated user.
     *
     * @return list of section subscription DTOs
     */
    @Transactional(readOnly = true)
    public List<TaskRespDto> getAllTasks() {
        UUID userId = authContextService.currentUserId();
        return subscriptionRepository.findAllByUser_Id(userId).stream()
                .sorted(Comparator.comparing(sub -> sub.getSection().getSectionId()))
                .map(this::toSubscribedResp)
                .collect(Collectors.toList());
    }

    /**
     * Searches a course, syncs canonical course/section rows into the database,
     * and returns section rows directly to the frontend without creating subscriptions.
     *
     * @param courseName user-provided course query
     * @return synced section DTOs, enriched with the current user's existing sub state when present
     */
    @Transactional
    public List<TaskRespDto> searchCourse(String courseName) {
        String normalizedQuery = normalizeCourseQuery(courseName);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(buildSearchMissKey(normalizedQuery)))) {
            throw new RuntimeException("Course not found: " + courseName);
        }

        JsonNode root = crawler.searchCourse(courseName);
        if (root == null || root.path("found").asInt() == 0) {
            rememberSearchMiss(normalizedQuery);
            throw new RuntimeException("Course not found: " + courseName);
        }

        JsonNode firstHit = root.path("hits").get(0);
        String foundName = firstHit.path("courseDesignation").asText();
        if (!foundName.replace(" ", "").equalsIgnoreCase(courseName.replace(" ", ""))) {
            rememberSearchMiss(normalizedQuery);
            throw new RuntimeException("Wrong input / Course not found: " + courseName);
        }

        String courseId = firstHit.path("courseId").asText();
        List<SectionInfo> infos = crawler.fetchCourseStatus(courseId);
        if (infos == null || infos.isEmpty()) {
            throw new RuntimeException("Course details unavailable: " + courseName);
        }

        Map<String, CourseSection> sectionsBySectionId = syncSections(infos);
        UUID userId = authContextService.currentUserId();
        Map<String, UserSectionSubscription> subsBySectionId =
                subscriptionRepository.findAllBySection_SectionIdInAndUser_Id(sectionsBySectionId.keySet(), userId).stream()
                        .collect(Collectors.toMap(sub -> sub.getSection().getSectionId(), sub -> sub));

        return sectionsBySectionId.values().stream()
                .sorted(Comparator.comparing(CourseSection::getSectionId))
                .map(section -> toSearchResp(section, subsBySectionId.get(section.getSectionId())))
                .collect(Collectors.toList());
    }

    /**
     * Creates or returns one subscription for the provided 5-digit section id.
     *
     * @param sectionId validated 5-digit section identifier from the frontend
     * @return the persisted section subscription DTO
     */
    @Transactional
    public TaskRespDto addSection(String sectionId) {
        requireValidSectionId(sectionId);
        UUID userId = authContextService.currentUserId();
        CourseSection section = courseSectionRepository.findBySectionId(sectionId)
                .orElseThrow(() -> new RuntimeException("Section not found. Search before adding: " + sectionId));

        UserSectionSubscription existingSub = subscriptionRepository.findByUser_IdAndSection_SectionId(userId, sectionId)
                .orElse(null);
        if (existingSub != null) {
            existingSub.setEnabled(true);
            armCourseForImmediatePolling(existingSub.getSection().getCourse());
            return toSubscribedResp(subscriptionRepository.save(existingSub));
        }

        User user = userRepository.getReferenceById(userId);
        UserSectionSubscription sub = new UserSectionSubscription();
        sub.setUser(user);
        sub.setSection(section);
        sub.setEnabled(true);
        UserSectionSubscription savedSub = subscriptionRepository.save(sub);
        armCourseForImmediatePolling(section.getCourse());
        return toSubscribedResp(savedSub);
    }

    /**
     * Soft-deletes one subscription by disabling it through the business section id.
     *
     * @param sectionId validated 5-digit section identifier from the frontend
     */
    @Transactional
    public void deleteTask(String sectionId) {
        requireValidSectionId(sectionId);
        UUID userId = authContextService.currentUserId();
        UserSectionSubscription sub = subscriptionRepository.findByUser_IdAndSection_SectionId(userId, sectionId)
                .orElseThrow(() -> new RuntimeException("Subscription not found for section: " + sectionId));
        sub.setEnabled(false);
        subscriptionRepository.save(sub);
    }

    /**
     * Syncs canonical course and section rows from crawler snapshots.
     *
     * @param infos fresh section snapshots returned by the crawler
     * @return map of persisted sections keyed by business section id
     */
    private Map<String, CourseSection> syncSections(List<SectionInfo> infos) {
        if (infos == null || infos.isEmpty()) {
            return Map.of();
        }

        // Persist or refresh the canonical course row shared by all section snapshots.
        SectionInfo firstInfo = infos.get(0);
        Course course = courseRepository.findByCourseId(firstInfo.getCourseId())
                .orElseGet(() -> new Course(firstInfo.getCourseId()));
        course.setTermCode(firstInfo.getTermCode());
        course.setSubjectCode(firstInfo.getSubjectCode());
        course.setSubjectShortName(firstInfo.getSubjectShortName());
        course.setCatalogNumber(firstInfo.getCatalogNumber());
        Course savedCourse = courseRepository.save(course);

        // existingSectionsBySectionId: section business id -> existing persisted section row.
        Map<String, CourseSection> existingSectionsBySectionId =
                courseSectionRepository.findAllByCourse_CourseId(savedCourse.getCourseId()).stream()
                        .collect(Collectors.toMap(CourseSection::getSectionId, section -> section));

        // savedSectionsBySectionId: section business id -> freshly saved section row.
        Map<String, CourseSection> savedSectionsBySectionId = new LinkedHashMap<>();

        // Apply each crawler snapshot onto its matching section row and keep seat/status metadata current.
        for (SectionInfo info : infos) {
            CourseSection section = existingSectionsBySectionId.getOrDefault(info.getSectionId(), new CourseSection());
            section.setSectionId(info.getSectionId());
            section.setCourse(savedCourse);
            section.setLastStatus(info.getStatus());
            section.setOpenSeats(info.getOpenSeats());
            section.setCapacity(info.getCapacity());
            section.setWaitlistSeats(info.getWaitlistSeats());
            section.setWaitlistCapacity(info.getWaitlistCapacity());
            section.setMeetingInfo(info.getMeetingInfo());

            CourseSection savedSection = courseSectionRepository.save(section);
            savedSectionsBySectionId.put(savedSection.getSectionId(), savedSection);
        }

        return savedSectionsBySectionId;
    }

    /**
     * Builds a response DTO for an already persisted subscription.
     *
     * @param sub persisted section subscription
     * @return frontend-ready DTO
     */
    private TaskRespDto toSubscribedResp(UserSectionSubscription sub) {
        return toResp(sub.getSection(), sub);
    }

    /**
     * Builds a response DTO for a search result section, optionally enriched with
     * the current user's existing sub state.
     *
     * @param section canonical section row
     * @param sub optional existing subscription for the current user
     * @return frontend-ready DTO
     */
    private TaskRespDto toSearchResp(CourseSection section, UserSectionSubscription sub) {
        return toResp(section, sub);
    }

    /**
     * Converts a canonical section and optional subscription into one frontend DTO.
     *
     * @param section canonical section row
     * @param sub optional subscription row for the current user
     * @return frontend-ready DTO
     */
    private TaskRespDto toResp(CourseSection section, UserSectionSubscription sub) {
        Course course = section.getCourse();
        TaskRespDto resp = new TaskRespDto();
        resp.setId(sub == null ? null : sub.getId());
        resp.setSectionId(section.getSectionId());
        resp.setCourseId(course.getCourseId());
        resp.setSubjectCode(course.getSubjectCode());
        resp.setCatalogNumber(course.getCatalogNumber());
        resp.setCourseDisplayName(buildCourseDisplayName(course));
        resp.setMeetingInfo(section.getMeetingInfo());
        resp.setStatus(section.getLastStatus());
        resp.setEnabled(sub != null && sub.isEnabled());
        return resp;
    }

    /**
     * Builds the display name expected by the current frontend.
     *
     * @param course canonical course row
     * @return human-readable course display name
     */
    private String buildCourseDisplayName(Course course) {
        String subject = (course.getSubjectShortName() == null || course.getSubjectShortName().isBlank())
                ? course.getSubjectCode()
                : course.getSubjectShortName();
        return subject + " " + course.getCatalogNumber();
    }

    /**
     * Enforces the canonical section id format expected by add and delete operations.
     *
     * @param sectionId frontend-provided business section id
     */
    private void requireValidSectionId(String sectionId) {
        if (sectionId == null || !sectionId.matches("\\d{5}")) {
            throw new RuntimeException("Section id must be a 5-digit number.");
        }
    }

    private void armCourseForImmediatePolling(Course course) {
        if (course == null) {
            return;
        }
        course.setUnchangedPollCount(0);
        course.setNextPollAt(LocalDateTime.now());
        courseRepository.save(course);
    }

    /**
     * Stores a short-lived negative cache entry for a clearly invalid course query.
     *
     * @param normalizedQuery normalized user query
     */
    private void rememberSearchMiss(String normalizedQuery) {
        redisTemplate.opsForValue().set(buildSearchMissKey(normalizedQuery), "1", SEARCH_MISS_TTL);
    }

    private String buildSearchMissKey(String normalizedQuery) {
        return "search:miss:" + normalizedQuery;
    }

    private String normalizeCourseQuery(String courseName) {
        if (courseName == null) {
            return "";
        }
        return courseName.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
