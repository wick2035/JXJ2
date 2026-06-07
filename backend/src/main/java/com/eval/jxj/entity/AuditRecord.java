package com.eval.jxj.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("audit_record")
public class AuditRecord {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String declarationId;
    private String reviewerId;
    private String action;
    private String comment;
    private String snapshotScores;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
