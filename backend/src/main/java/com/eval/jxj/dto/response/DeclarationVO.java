package com.eval.jxj.dto.response;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class DeclarationVO {
    private String id;
    private String batchId;
    private String batchName;
    private String studentId;
    private String studentName;
    private String studentLoginId;
    private String status;
    private String stage;
    private Boolean canWithdraw;
    private BigDecimal totalScore;
    private BigDecimal moralityScore;
    private BigDecimal abilityScore;
    private BigDecimal sportsScore;
    private LocalDateTime submittedAt;
    private LocalDateTime createdAt;
    private Integer classRank;
    private Integer classRankTotal;
    private List<CategoryScoreVO> categoryScores;
    private List<DeclarationItemVO> items;
    private List<BasicItemVO> basicItems;
    private List<AuditRecordVO> auditRecords;

    @Data
    public static class CategoryScoreVO {
        private String category;
        private BigDecimal rawScore;
    }

    @Data
    public static class DeclarationItemVO {
        private String id;
        private String category;
        private String awardId;
        private String awardName;
        private String levelId;
        private String levelName;
        private String customAwardName;
        private String customLevelName;
        private BigDecimal customBaseScore;
        private Integer useDowngrade;
        private BigDecimal computedScore;
        private BigDecimal finalScore;
        private String description;
        private Integer sortOrder;
        private List<AttachmentVO> attachments;
    }

    @Data
    public static class BasicItemVO {
        private String awardId;
        private String awardName;
        private String category;
        private BigDecimal computedScore;
        private BigDecimal finalScore;
        private String source;
    }

    @Data
    public static class AttachmentVO {
        private String id;
        private String fileName;
        private String filePath;
        private Long fileSize;
        private String mimeType;
    }

    @Data
    public static class AuditRecordVO {
        private String id;
        private String reviewerId;
        private String reviewerName;
        private String action;
        private String comment;
        private String snapshotScores;
        private LocalDateTime createdAt;
    }
}
