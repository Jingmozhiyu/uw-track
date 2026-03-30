package com.jing.monitor.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * In-memory model that represents one section snapshot returned by the crawler.
 */
@Data
@NoArgsConstructor
public class SectionInfo {
    private String termCode;
    private String courseId;
    private String sectionId;
    private String subjectCode;
    private String subjectShortName;
    private String catalogNumber;
    private StatusMapping status;
    private Integer openSeats;
    private Integer capacity;
    private Integer waitlistSeats;
    private Integer waitlistCapacity;
    private String meetingInfo;

    public SectionInfo(
            String termCode,
            String courseId,
            String sectionId,
            String subjectCode,
            String subjectShortName,
            String catalogNumber,
            StatusMapping status,
            Integer openSeats,
            Integer capacity,
            Integer waitlistSeats,
            Integer waitlistCapacity,
            String meetingInfo
    ) {
        this.termCode = termCode;
        this.courseId = courseId;
        this.sectionId = sectionId;
        this.subjectCode = subjectCode;
        this.subjectShortName = subjectShortName;
        this.catalogNumber = catalogNumber;
        this.status = status;
        this.openSeats = openSeats;
        this.capacity = capacity;
        this.waitlistSeats = waitlistSeats;
        this.waitlistCapacity = waitlistCapacity;
        this.meetingInfo = meetingInfo;
    }

    public String getSubject() {
        return subjectShortName;
    }

    public String getSection() {
        return sectionId;
    }

    public String getCourseDisplayName() {
        if (subjectShortName == null || subjectShortName.isBlank()) {
            return catalogNumber;
        }
        if (catalogNumber == null || catalogNumber.isBlank()) {
            return subjectShortName;
        }
        return subjectShortName + " " + catalogNumber;
    }
}
