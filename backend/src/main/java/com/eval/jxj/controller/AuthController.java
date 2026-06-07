package com.eval.jxj.controller;

import com.eval.jxj.common.Result;
import com.eval.jxj.dto.request.ChangePasswordRequest;
import com.eval.jxj.dto.request.LoginRequest;
import com.eval.jxj.dto.response.LoginResponse;
import com.eval.jxj.service.AuthService;
import com.eval.jxj.util.SecurityUtil;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@RequestBody Map<String, String> body) {
        return Result.ok(authService.refresh(body.get("refreshToken")));
    }

    @PutMapping("/password")
    public Result<?> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(SecurityUtil.getCurrentUserId(), request);
        return Result.ok();
    }
}
