package com.eval.jxj.dto.request;

import lombok.Data;

import java.time.LocalDate;

@Data
public class BatchStatusUpdateRequest {
    private String status;
    private LocalDate endDate;
}
