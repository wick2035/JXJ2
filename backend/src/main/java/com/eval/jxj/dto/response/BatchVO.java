package com.eval.jxj.dto.response;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class BatchVO {
    private String id;
    private String name;
    private String status;
    private String targetType;
    private LocalDate startDate;
    private LocalDate endDate;
    private String description;
    private List<CategoryVO> categories;
    private List<ReviewerVO> reviewers;
    private List<ClassTargetVO> targetClasses;
    private Integer reviewerCount;
    private Integer declarationCount;
    private Integer pendingAuditCount;
    private Integer approvedCount;
    private Integer submittedStudentCount;
    private Integer eligibleStudentCount;
    private Integer pendingReviewCount;

    @Data
    public static class CategoryVO {
        private String id;
        private String category;
        private BigDecimal weightPercent;
        private BigDecimal maxScoreCap;
    }

    @Data
    public static class ClassTargetVO {
        private String grade;
        private String className;
    }

    @Data
    public static class ReviewerVO {
        private String id;
        private String loginId;
        private String name;
    }
}
