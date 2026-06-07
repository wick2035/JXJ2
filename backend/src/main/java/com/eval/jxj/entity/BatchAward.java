package com.eval.jxj.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("batch_award")
public class BatchAward {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String batchId;
    private String awardId;
    private String levelId;
    private BigDecimal overrideBaseScore;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
