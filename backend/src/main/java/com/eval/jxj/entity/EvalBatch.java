package com.eval.jxj.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("eval_batch")
public class EvalBatch {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String name;
    private String status;
    private String targetType;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer reviewerCount;
    private String description;
    private String createdBy;
    @TableLogic
    private Integer isDeleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
