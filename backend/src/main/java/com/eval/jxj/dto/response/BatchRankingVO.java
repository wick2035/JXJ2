package com.eval.jxj.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class BatchRankingVO {
    private int rank;
    private String declarationId;
    private String studentId;
    private String studentLoginId;
    private String studentName;
    private BigDecimal totalScore;
    private BigDecimal moralityScore;
    private BigDecimal abilityScore;
    private BigDecimal sportsScore;
    private Map<String, BigDecimal> categoryScores;
    private LocalDateTime submittedAt;
}
