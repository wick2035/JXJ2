package com.eval.jxj.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("eval_batch_category")
public class EvalBatchCategory {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String batchId;
    private String category;
    private BigDecimal weightPercent;
    private BigDecimal maxScoreCap;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
