package com.eval.jxj.dto.response;

import lombok.Data;

@Data
public class UserVO {
    private String id;
    private String loginId;
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
}
