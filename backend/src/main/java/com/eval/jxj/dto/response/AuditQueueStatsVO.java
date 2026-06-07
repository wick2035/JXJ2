package com.eval.jxj.dto.response;

import lombok.Data;

@Data
public class AuditQueueStatsVO {
    private long totalSubmitted;
    private long myPending;
    private long assignedPending;
    private long unassigned;
    private long finishedTotal;
    private long finishedMine;
    private long finishedApproved;
    private long finishedRejected;
}
