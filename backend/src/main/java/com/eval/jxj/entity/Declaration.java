package com.eval.jxj.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("declaration")
public class Declaration {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String batchId;
    private String studentId;
    private String status;
    private BigDecimal totalScore;
    private BigDecimal moralityScore;
    private BigDecimal abilityScore;
    private BigDecimal sportsScore;
    private LocalDateTime submittedAt;
    @TableLogic
    private Integer isDeleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
