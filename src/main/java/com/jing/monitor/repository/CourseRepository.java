package com.jing.monitor.repository;

import com.jing.monitor.model.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for canonical course records.
 */
@Repository
public interface CourseRepository extends JpaRepository<Course, UUID> {

    Optional<Course> findByCourseId(String courseId);
}
