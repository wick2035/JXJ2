package com.eval.jxj.dto.request;

import lombok.Data;

@Data
public class BatchAssignmentGenerateRequest {
    private Boolean replacePending;
    private String reviewerId;
    private Integer count;
}
