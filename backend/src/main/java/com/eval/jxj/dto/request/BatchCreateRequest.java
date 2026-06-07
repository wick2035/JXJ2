package com.eval.jxj.dto.request;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

@Data
public class BatchCreateRequest {
    @NotBlank(message = "批次名称不能为空")
    private String name;
    @NotNull(message = "开始日期不能为空")
    private LocalDate startDate;
    @NotNull(message = "截止日期不能为空")
    private LocalDate endDate;
    private String description;
    private Integer reviewerCount;
    private List<String> reviewerIds;
    private List<CategoryConfig> categories;
    private String targetType;
    private List<ClassTarget> targetClasses;

    @Data
    public static class CategoryConfig {
        private String category;
        private java.math.BigDecimal weightPercent;
        private java.math.BigDecimal maxScoreCap;
    }

    @Data
    public static class ClassTarget {
        private String grade;
        private String className;
    }
}
