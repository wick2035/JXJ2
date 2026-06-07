package com.eval.jxj.dto.response;

import lombok.Data;

@Data
public class LoginResponse {
    private String token;
    private String refreshToken;
    private UserVO user;
}
