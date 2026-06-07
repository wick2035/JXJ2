package com.eval.jxj.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuditAssignmentVO {
    private String id;
    private String declarationId;
    private String studentLoginId;
    private String studentName;
    private String reviewerId;
    private String reviewerName;
    private String status;
    private String action;
    private String comment;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
}
