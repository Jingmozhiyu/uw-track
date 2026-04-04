package com.jing.monitor;

import com.jing.monitor.model.Course;
import com.jing.monitor.model.CourseSection;
import com.jing.monitor.model.User;
import com.jing.monitor.model.UserSectionSubscription;
import com.jing.monitor.repository.CourseRepository;
import com.jing.monitor.repository.CourseSectionRepository;
import com.jing.monitor.repository.UserRepository;
import com.jing.monitor.repository.UserSectionSubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;
import java.util.UUID;

/**
 * Integration test for the refactored course/section/subscription schema.
 */
@SpringBootTest
class TaskRepositoryTest {

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseSectionRepository courseSectionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSectionSubscriptionRepository subscriptionRepository;

    @Test
    void testCrudOperations() {
        String uniqueSuffix = String.valueOf(System.currentTimeMillis());

        Course course = new Course("9999", "9" + uniqueSuffix.substring(uniqueSuffix.length() - 5));
        course.setSubjectCode("266");
        course.setSubjectShortName("COMP SCI");
        course.setCatalogNumber("999");
        courseRepository.save(course);

        CourseSection section = new CourseSection();
        section.setDocId("doc-" + uniqueSuffix);
        section.setSectionId("8" + uniqueSuffix.substring(uniqueSuffix.length() - 4));
        section.setCourse(course);
        section.setMeetingInfo("[]");
        CourseSection savedSection = courseSectionRepository.save(section);

        User user = new User("repo-test-" + uniqueSuffix + "@example.com", "hash");
        User savedUser = userRepository.save(user);

        UserSectionSubscription subscription = new UserSectionSubscription();
        subscription.setUser(savedUser);
        subscription.setSection(savedSection);
        subscription.setEnabled(true);
        UserSectionSubscription savedSubscription = subscriptionRepository.save(subscription);

        assert savedSubscription.getId() != null;

        Optional<UserSectionSubscription> fetchResult = subscriptionRepository.findById(savedSubscription.getId());
        assert fetchResult.isPresent();

        UserSectionSubscription subscriptionToUpdate = fetchResult.get();
        subscriptionToUpdate.setEnabled(false);
        subscriptionRepository.save(subscriptionToUpdate);
        assert !subscriptionRepository.findById(savedSubscription.getId()).orElseThrow().isEnabled();

        subscriptionRepository.deleteById(savedSubscription.getId());
        UUID savedSectionUuid = savedSection.getId();
        UUID savedCourseUuid = course.getId();
        courseSectionRepository.deleteById(savedSectionUuid);
        courseRepository.deleteById(savedCourseUuid);
        userRepository.deleteById(savedUser.getId());

        assert !subscriptionRepository.existsById(savedSubscription.getId());
    }
}
