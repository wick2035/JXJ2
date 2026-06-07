package com.eval.jxj.dto.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BatchDetailRowVO {
    private String declarationId;
    private String studentLoginId;
    private String studentName;
    private String status;
    private String category;
    private String awardName;
    private String levelName;
    private String source;
    private BigDecimal computedScore;
    private BigDecimal finalScore;
    private String description;
}
