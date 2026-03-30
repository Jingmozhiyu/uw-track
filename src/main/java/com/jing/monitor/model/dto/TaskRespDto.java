package com.jing.monitor.model.dto;

import com.jing.monitor.model.StatusMapping;
import lombok.Data;

import java.util.UUID;

/**
 * Response DTO returned to task UI clients.
 */
@Data
public class TaskRespDto {
    private UUID id;
    private String sectionId;
    private String courseId;
    private String subjectCode;
    private String catalogNumber;
    private String courseDisplayName;
    private String meetingInfo;
    private StatusMapping status;
    private boolean enabled;
}
