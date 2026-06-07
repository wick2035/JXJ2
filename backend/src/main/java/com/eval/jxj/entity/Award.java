package com.eval.jxj.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("award")
public class Award {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String category;
    private String name;
    private String awardType;
    private String description;
    @TableLogic
    private Integer isDeleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
