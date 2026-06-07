package com.eval.jxj.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String loginId;
    private String passwordHash;
    private Integer forcePasswordChange;
    private Integer status;
    private Integer failedAttempts;
    private String name;
    private String role;
    private String email;
    private String phone;
    private String college;
    private String major;
    private String className;
    private String grade;
    @TableLogic
    private Integer isDeleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
