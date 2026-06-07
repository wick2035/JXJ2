package com.eval.jxj.dto.request;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

@Data
public class AuditCorrectionRequest {
    @NotBlank(message = "审核动作不能为空")
    private String action;
    private String comment;
    @Valid
    private List<ItemScoreAdjustment> itemScoreAdjustments;

    @Data
    public static class ItemScoreAdjustment {
        @NotBlank(message = "申报明细不能为空")
        private String itemId;
        @NotNull(message = "调整分数不能为空")
        @DecimalMin(value = "0.0", message = "调整分数不能小于0")
        private BigDecimal finalScore;
    }
}
