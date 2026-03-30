package com.jing.monitor.service;

import com.jing.monitor.core.CourseCrawler;
import com.jing.monitor.model.Course;
import com.jing.monitor.model.CourseSection;
import com.jing.monitor.model.SectionInfo;
import com.jing.monitor.model.StatusMapping;
import com.jing.monitor.model.UserSectionSubscription;
import com.jing.monitor.repository.CourseRepository;
import com.jing.monitor.repository.CourseSectionRepository;
import com.jing.monitor.repository.FileRepository;
import com.jing.monitor.repository.UserSectionSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Service responsible for polling synced courses and dispatching notifications
 * for subscribed sections.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SchedulerService {

    private final CourseCrawler crawler;
    private final MailService mailService;
    private final FileRepository fileRepository;
    private final CourseRepository courseRepository;
    private final CourseSectionRepository courseSectionRepository;
    private final UserSectionSubscriptionRepository subscriptionRepository;
    private final Random random = new Random();

    enum AlertAction { NONE, SEND_OPEN_EMAIL, SEND_WAITLIST_EMAIL }

    /**
     * Polls all distinct courses that still have enabled subscriptions.
     * The loop fetches each course only once per cycle, then fans out alerts
     * to all subscribed users for the affected section ids.
     */
    @Scheduled(fixedDelayString = "${monitor.poll-interval-ms}")
    public void monitorTask() {
        // activeSubs: all enabled user->section subscriptions currently participating in polling.
        List<UserSectionSubscription> activeSubs = subscriptionRepository.findAllByEnabledTrue();

        // subsByCourseId: course business id -> enabled subs whose section belongs to that course.
        Map<String, List<UserSectionSubscription>> subsByCourseId = activeSubs.stream()
                .filter(sub -> sub.getSection() != null && sub.getSection().getCourse() != null)
                .collect(Collectors.groupingBy(sub -> sub.getSection().getCourse().getCourseId(), HashMap::new, Collectors.toList()));

        if (subsByCourseId.isEmpty()) {
            log.info("[Scheduler] No active tasks. Idle.");
            return;
        }

        int totalCourses = subsByCourseId.size();
        log.info("[Scheduler] Starting cycle. Monitoring {} distinct courses.", totalCourses);

        int processed = 0;
        for (Map.Entry<String, List<UserSectionSubscription>> entry : subsByCourseId.entrySet()) {
            processSingleCourse(entry.getKey(), entry.getValue());
            processed++;
            try {
                if (processed < totalCourses) {
                    long sleepTime = 5000 + random.nextInt(500);
                    Thread.sleep(sleepTime);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Fetches one course snapshot, syncs the latest section state, and dispatches
     * notifications only for sections whose status transitioned in this poll.
     *
     * @param courseId UW 6-digit course id
     * @param subs enabled subscriptions that belong to this course
     */
    private void processSingleCourse(String courseId, List<UserSectionSubscription> subs) {
        try {
            List<SectionInfo> infos = crawler.fetchCourseStatus(courseId);
            if (infos == null || infos.isEmpty()) {
                log.warn("[Scheduler] Fetch failed or returned empty data for course {}", courseId);
                return;
            }

            // Keep the shared course row current before touching section snapshots.
            Course course = upsertCourse(infos.get(0));

            // existingSectionsBySectionId: section business id -> previously persisted section snapshot.
            Map<String, CourseSection> existingSectionsBySectionId =
                    courseSectionRepository.findAllByCourse_CourseId(courseId).stream()
                            .collect(Collectors.toMap(CourseSection::getSectionId, section -> section, (left, right) -> left, HashMap::new));

            // subsBySectionId: section business id -> enabled subs targeting that section.
            Map<String, List<UserSectionSubscription>> subsBySectionId =
                    subs.stream().collect(Collectors.groupingBy(sub -> sub.getSection().getSectionId(), HashMap::new, Collectors.toList()));

            // Apply each fresh crawler snapshot and fan out alerts only on state transitions.
            for (SectionInfo info : infos) {
                String sectionId = info.getSectionId();
                StatusMapping currentStatus = info.getStatus();
                CourseSection section = existingSectionsBySectionId.get(sectionId);
                StatusMapping previousStatus = section == null ? null : section.getLastStatus();

                if (section == null) {
                    section = new CourseSection();
                    section.setSectionId(sectionId);
                    log.info("[Scheduler] New section found {}. Adding to DB.", sectionId);
                }

                section.setCourse(course);
                section.setLastStatus(currentStatus);
                section.setOpenSeats(info.getOpenSeats());
                section.setCapacity(info.getCapacity());
                section.setWaitlistSeats(info.getWaitlistSeats());
                section.setWaitlistCapacity(info.getWaitlistCapacity());
                section.setMeetingInfo(info.getMeetingInfo());
                CourseSection savedSection = courseSectionRepository.save(section);
                existingSectionsBySectionId.put(savedSection.getSectionId(), savedSection);

                if (previousStatus != currentStatus) {
                    if (previousStatus != null) {
                        log.info("[Scheduler] State changed: {} -> {} for {}", previousStatus, currentStatus, sectionId);
                    }
                    fileRepository.save(info);

                    AlertAction action = determineAction(previousStatus, currentStatus);
                    for (UserSectionSubscription sub : subsBySectionId.getOrDefault(sectionId, List.of())) {
                        String recipientEmail = sub.getUser().getEmail();
                        if (recipientEmail == null || recipientEmail.isBlank()) {
                            log.warn("[Scheduler] Skipping sub {} because email is missing.", sub.getId());
                            continue;
                        }
                        dispatchMail(action, recipientEmail, info);
                    }
                }
            }
        } catch (Exception e) {
            log.error("[Scheduler] Error processing course {}", courseId, e);
        }
    }

    /**
     * Calculates which alert action, if any, should be triggered by the transition.
     *
     * @param prev previous persisted section status
     * @param curr latest crawler status
     * @return alert action to dispatch
     */
    private AlertAction determineAction(StatusMapping prev, StatusMapping curr) {
        if (prev == null || prev == curr) {
            return AlertAction.NONE;
        }

        switch (curr) {
            case OPEN:
                return AlertAction.SEND_OPEN_EMAIL;
            case WAITLISTED:
                return (prev == StatusMapping.CLOSED) ? AlertAction.SEND_WAITLIST_EMAIL : AlertAction.NONE;
            default:
                return AlertAction.NONE;
        }
    }

    /**
     * Sends the selected email side effect for one subscribed user.
     *
     * @param action chosen alert action
     * @param recipientEmail notification recipient
     * @param info latest section snapshot
     */
    private void dispatchMail(AlertAction action, String recipientEmail, SectionInfo info) {
        if (action == AlertAction.SEND_OPEN_EMAIL) {
            log.info("[Scheduler] ALERT OPEN detected for {}", info.getSectionId());
            mailService.sendCourseOpenAlert(recipientEmail, info.getSectionId(), info.getCourseDisplayName());
        } else if (action == AlertAction.SEND_WAITLIST_EMAIL) {
            log.info("[Scheduler] ALERT WAITLIST detected for {}", info.getSectionId());
            mailService.sendCourseWaitlistedAlert(recipientEmail, info.getSectionId(), info.getCourseDisplayName());
        }
    }

    /**
     * Persists the canonical course row shared by the incoming section snapshots.
     *
     * @param info representative section snapshot carrying course metadata
     * @return persisted canonical course row
     */
    private Course upsertCourse(SectionInfo info) {
        Course course = courseRepository.findByCourseId(info.getCourseId())
                .orElseGet(() -> new Course(info.getCourseId()));
        course.setTermCode(info.getTermCode());
        course.setSubjectCode(info.getSubjectCode());
        course.setSubjectShortName(info.getSubjectShortName());
        course.setCatalogNumber(info.getCatalogNumber());
        return courseRepository.save(course);
    }
}
