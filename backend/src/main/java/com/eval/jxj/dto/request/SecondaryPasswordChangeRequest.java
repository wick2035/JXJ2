package com.eval.jxj.dto.request;

import lombok.Data;

@Data
public class SecondaryPasswordChangeRequest {
    private String oldPassword;
    private String newPassword;
}
