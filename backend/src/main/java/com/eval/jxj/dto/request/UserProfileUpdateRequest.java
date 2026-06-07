package com.eval.jxj.dto.request;

import lombok.Data;

import javax.validation.constraints.Email;

@Data
public class UserProfileUpdateRequest {
    @Email(message = "邮箱格式不正确")
    private String email;
    private String phone;
}
