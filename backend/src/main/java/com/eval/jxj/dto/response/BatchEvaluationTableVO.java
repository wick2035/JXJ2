package com.eval.jxj.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class BatchEvaluationTableVO {
    private List<CategoryColumn> categories;
    private List<StudentRow> rows;

    @Data
    public static class CategoryColumn {
        private String code;
        private String name;
        private String color;
        private List<AwardColumn> awards;
    }

    @Data
    public static class AwardColumn {
        private String awardId;
        private String name;
        private boolean custom;
    }

    @Data
    public static class StudentRow {
        private String studentId;
        private String studentLoginId;
        private String studentName;
        private Map<String, BigDecimal> scores;
        private Map<String, BigDecimal> subtotals;
    }
}
