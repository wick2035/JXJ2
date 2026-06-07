package com.eval.jxj.dto.request;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class UserCreateRequest {
    @NotBlank(message = "账号不能为空")
    private String loginId;
    @NotBlank(message = "姓名不能为空")
    private String name;
    @NotBlank(message = "角色不能为空")
    private String role;
    private String password;
    private String email;
    private String phone;
    private String college;
    private String major;
    private String className;
    private String grade;
}
