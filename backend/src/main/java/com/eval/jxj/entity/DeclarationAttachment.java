package com.eval.jxj.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("declaration_attachment")
public class DeclarationAttachment {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String declarationItemId;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String mimeType;
    @TableLogic
    private Integer isDeleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
