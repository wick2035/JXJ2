package com.eval.jxj.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("operation_log")
public class OperationLog {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String operatorId;
    private String operatorLoginId;
    private String operatorName;
    private String operatorRole;
    private String module;
    private String action;
    private String method;
    private String uri;
    private String params;
    private String ip;
    private Integer success;
    private String errorMsg;
    @TableLogic
    private Integer isDeleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
