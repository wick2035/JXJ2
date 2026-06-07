package com.eval.jxj.service;

import com.eval.jxj.dto.request.ChangePasswordRequest;
import com.eval.jxj.dto.request.LoginRequest;
import com.eval.jxj.dto.response.LoginResponse;

public interface AuthService {
    LoginResponse login(LoginRequest request);
    LoginResponse refresh(String refreshToken);
    void changePassword(String userId, ChangePasswordRequest request);
}
