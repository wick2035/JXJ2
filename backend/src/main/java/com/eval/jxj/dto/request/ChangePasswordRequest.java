package com.eval.jxj.dto.request;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class ChangePasswordRequest {
    @NotBlank(message = "原密码不能为空")
    private String oldPassword;
    @NotBlank(message = "新密码不能为空")
    private String newPassword;
}
