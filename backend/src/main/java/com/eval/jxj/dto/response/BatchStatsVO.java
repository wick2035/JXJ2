package com.eval.jxj.dto.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BatchStatsVO {
    private String batchId;
    private int totalDeclarations;
    private int draftCount;
    private int submittedCount;
    private int approvedCount;
    private int rejectedCount;
    private int returnedCount;
    private int pendingAuditCount;
    private int finishedAuditCount;
    private BigDecimal averageScore;
    private BigDecimal maxScore;
    private BigDecimal minScore;
    private BigDecimal auditProgress;
}
