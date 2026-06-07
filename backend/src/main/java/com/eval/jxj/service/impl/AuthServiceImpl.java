package com.eval.jxj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eval.jxj.common.exception.BizException;
import com.eval.jxj.dto.request.ChangePasswordRequest;
import com.eval.jxj.dto.request.LoginRequest;
import com.eval.jxj.dto.response.LoginResponse;
import com.eval.jxj.dto.response.UserVO;
import com.eval.jxj.entity.OperationLog;
import com.eval.jxj.entity.SysUser;
import com.eval.jxj.mapper.SysUserMapper;
import com.eval.jxj.security.JwtTokenProvider;
import com.eval.jxj.service.AuthService;
import com.eval.jxj.service.OperationLogService;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

@Service
public class AuthServiceImpl implements AuthService {

    /** 连续密码错误达到该次数后自动锁定账号 */
    private static final int MAX_FAILED_ATTEMPTS = 6;
    /** 统一的登录错误提示（不泄露账号是否存在/剩余次数） */
    private static final String LOGIN_FAIL_MSG = "用户名或密码错误，达到6次账号将被锁定";

    private final SysUserMapper userMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final OperationLogService operationLogService;

    public AuthServiceImpl(SysUserMapper userMapper, JwtTokenProvider jwtTokenProvider,
                          PasswordEncoder passwordEncoder, OperationLogService operationLogService) {
        this.userMapper = userMapper;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.operationLogService = operationLogService;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getLoginId, request.getLoginId()));

        // 账号不存在：不暴露账号是否存在，统一提示
        if (user == null) {
            logLogin(null, request.getLoginId(), false, LOGIN_FAIL_MSG);
            throw new BizException(401, LOGIN_FAIL_MSG);
        }

        // 账号被禁用/锁定
        if (user.getStatus() != null && user.getStatus() == 0) {
            logLogin(user, request.getLoginId(), false, "账号已被禁用");
            throw new BizException(403, "账号已被禁用，请联系管理员");
        }

        // 密码错误：累加失败次数，达到上限则锁定（不泄露剩余次数，统一提示）
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            int attempts = (user.getFailedAttempts() == null ? 0 : user.getFailedAttempts()) + 1;
            user.setFailedAttempts(attempts);
            String msg;
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                user.setStatus(0);
                msg = "密码连续错误" + MAX_FAILED_ATTEMPTS + "次，账号已被锁定，请联系管理员";
            } else {
                msg = LOGIN_FAIL_MSG;
            }
            userMapper.updateById(user);
            logLogin(user, request.getLoginId(), false, msg);
            throw new BizException(401, msg);
        }

        // 登录成功：重置失败计数
        if (user.getFailedAttempts() != null && user.getFailedAttempts() != 0) {
            user.setFailedAttempts(0);
            userMapper.updateById(user);
        }

        LoginResponse resp = new LoginResponse();
        resp.setToken(jwtTokenProvider.generateToken(user.getId(), user.getLoginId(), user.getRole(), user.getName()));
        resp.setRefreshToken(jwtTokenProvider.generateRefreshToken(user.getId()));

        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        resp.setUser(vo);

        logLogin(user, request.getLoginId(), true, null);
        return resp;
    }

    private void logLogin(SysUser user, String loginId, boolean success, String errorMsg) {
        OperationLog logEntity = new OperationLog();
        if (user != null) {
            logEntity.setOperatorId(user.getId());
            logEntity.setOperatorName(user.getName());
            logEntity.setOperatorRole(user.getRole());
        }
        logEntity.setOperatorLoginId(loginId);
        logEntity.setModule("登录");
        logEntity.setAction(success ? "登录成功" : "登录失败");
        logEntity.setMethod("POST");
        logEntity.setUri("/api/auth/login");
        logEntity.setSuccess(success ? 1 : 0);
        logEntity.setErrorMsg(errorMsg);
        logEntity.setIp(currentIp());
        operationLogService.record(logEntity);
    }

    private String currentIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            HttpServletRequest request = attrs.getRequest();
            String ip = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
                int idx = ip.indexOf(',');
                return idx > 0 ? ip.substring(0, idx).trim() : ip.trim();
            }
            ip = request.getHeader("X-Real-IP");
            if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
                return ip;
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public LoginResponse refresh(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BizException(401, "刷新令牌已过期");
        }
        String userId = jwtTokenProvider.getUserId(refreshToken);
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(401, "用户不存在");
        }

        LoginResponse resp = new LoginResponse();
        resp.setToken(jwtTokenProvider.generateToken(user.getId(), user.getLoginId(), user.getRole(), user.getName()));
        resp.setRefreshToken(jwtTokenProvider.generateRefreshToken(user.getId()));

        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        resp.setUser(vo);
        return resp;
    }

    @Override
    public void changePassword(String userId, ChangePasswordRequest request) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BizException("原密码错误");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setForcePasswordChange(0);
        userMapper.updateById(user);
    }
}
