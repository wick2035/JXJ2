package com.eval.jxj.dto.request;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class LoginRequest {
    @NotBlank(message = "账号不能为空")
    private String loginId;
    @NotBlank(message = "密码不能为空")
    private String password;
}
