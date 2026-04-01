package com.jing.monitor.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Canonical course record keyed by an internal UUID and a unique UW course id.
 */
@Entity
@Table(name = "courses")
@Data
@NoArgsConstructor
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "course_uuid", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "course_id", nullable = false, unique = true, length = 6)
    private String courseId;

    @Column(name = "term_code", nullable = false)
    private String termCode;

    @Column(name = "subject_code", nullable = false)
    private String subjectCode;

    @Column(name = "subject_short_name")
    private String subjectShortName;

    @Column(name = "catalog_number", nullable = false)
    private String catalogNumber;

    @Column(name = "last_polled_at")
    private LocalDateTime lastPolledAt;

    @Column(name = "next_poll_at")
    private LocalDateTime nextPollAt;

    @Column(name = "unchanged_poll_count", nullable = false)
    private Integer unchangedPollCount = 0;

    public Course(String courseId) {
        this.courseId = courseId;
        this.unchangedPollCount = 0;
    }
}
