package com.jing.monitor.service;

import com.jing.monitor.model.Course;
import com.jing.monitor.model.CourseSection;
import com.jing.monitor.model.User;
import com.jing.monitor.model.UserRole;
import com.jing.monitor.model.UserSectionSubscription;
import com.jing.monitor.model.dto.AdminSectionSubRespDto;
import com.jing.monitor.model.dto.AdminUserSubsRespDto;
import com.jing.monitor.repository.UserRepository;
import com.jing.monitor.repository.UserSectionSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service responsible for admin-only subscription views and operations.
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final UserSectionSubscriptionRepository subscriptionRepository;
    private final AuthContextService authContextService;

    /**
     * Returns every user's email together with their current section subscriptions.
     *
     * @return grouped admin-facing subscription data
     */
    @Transactional(readOnly = true)
    public List<AdminUserSubsRespDto> getAllUserSubscriptions() {
        requireAdmin();

        List<UserSectionSubscription> subs = subscriptionRepository.findAll();
        Map<UUID, AdminUserSubsRespDto> userMap = new LinkedHashMap<>();

        for (UserSectionSubscription sub : subs) {
            User user = sub.getUser();
            AdminUserSubsRespDto userResp = userMap.computeIfAbsent(user.getId(), ignored -> {
                AdminUserSubsRespDto dto = new AdminUserSubsRespDto();
                dto.setUserId(user.getId());
                dto.setEmail(user.getEmail());
                dto.setRole(user.getRole());
                return dto;
            });
            userResp.getSubscriptions().add(toAdminSubResp(sub));
        }

        return userMap.values().stream()
                .peek(dto -> dto.getSubscriptions().sort(Comparator.comparing(AdminSectionSubRespDto::getSectionId)))
                .sorted(Comparator.comparing(AdminUserSubsRespDto::getEmail))
                .toList();
    }

    /**
     * Enables or disables one subscription as an admin action.
     *
     * @param subscriptionId subscription UUID
     * @param enabled target enabled state
     * @return updated admin-facing subscription DTO
     */
    @Transactional
    public AdminSectionSubRespDto updateSubscriptionEnabled(UUID subscriptionId, boolean enabled) {
        requireAdmin();
        UserSectionSubscription sub = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("Subscription not found: " + subscriptionId));
        sub.setEnabled(enabled);
        UserSectionSubscription savedSub = subscriptionRepository.save(sub);
        return toAdminSubResp(savedSub);
    }

    private void requireAdmin() {
        UUID currentUserId = authContextService.currentUserId();
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new RuntimeException("Unauthorized"));
        if (currentUser.getRole() != UserRole.ADMIN) {
            throw new RuntimeException("Unauthorized");
        }
    }

    private AdminSectionSubRespDto toAdminSubResp(UserSectionSubscription sub) {
        CourseSection section = sub.getSection();
        Course course = section.getCourse();

        AdminSectionSubRespDto dto = new AdminSectionSubRespDto();
        dto.setSubscriptionId(sub.getId());
        dto.setEnabled(sub.isEnabled());
        dto.setCourseId(course.getCourseId());
        dto.setSubjectCode(course.getSubjectCode());
        dto.setCatalogNumber(course.getCatalogNumber());
        dto.setCourseDisplayName(buildCourseDisplayName(course));
        dto.setSectionId(section.getSectionId());
        dto.setStatus(section.getLastStatus());
        dto.setOpenSeats(section.getOpenSeats());
        dto.setCapacity(section.getCapacity());
        dto.setWaitlistSeats(section.getWaitlistSeats());
        dto.setWaitlistCapacity(section.getWaitlistCapacity());
        dto.setMeetingInfo(section.getMeetingInfo());
        return dto;
    }

    private String buildCourseDisplayName(Course course) {
        String subject = (course.getSubjectShortName() == null || course.getSubjectShortName().isBlank())
                ? course.getSubjectCode()
                : course.getSubjectShortName();
        return subject + " " + course.getCatalogNumber();
    }
}
