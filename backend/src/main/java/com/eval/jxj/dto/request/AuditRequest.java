package com.eval.jxj.dto.request;

import lombok.Data;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.List;

@Data
public class AuditRequest {
    @NotBlank(message = "审核动作不能为空")
    private String action;
    private String comment;
    /** 第一次审核也允许审核员调整明细分数，复用修正审核的明细分调整结构 */
    @Valid
    private List<AuditCorrectionRequest.ItemScoreAdjustment> itemScoreAdjustments;
}
