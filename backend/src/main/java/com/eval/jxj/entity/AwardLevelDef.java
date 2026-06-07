package com.eval.jxj.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("award_level_def")
public class AwardLevelDef {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String code;
    private String name;
    private Integer sortOrder;
    @TableLogic
    private Integer isDeleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
