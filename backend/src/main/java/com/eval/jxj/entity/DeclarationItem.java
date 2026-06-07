package com.eval.jxj.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("declaration_item")
public class DeclarationItem {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String declarationId;
    private String category;
    private String awardId;
    private String levelId;
    private String customAwardName;
    private String customLevelName;
    private BigDecimal customBaseScore;
    private Integer useDowngrade;
    private BigDecimal computedScore;
    private BigDecimal finalScore;
    private String description;
    private Integer sortOrder;
    @TableLogic
    private Integer isDeleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
