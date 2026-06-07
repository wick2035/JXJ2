package com.eval.jxj.security;

import com.eval.jxj.common.Result;
import com.eval.jxj.entity.SysUser;
import com.eval.jxj.mapper.SysUserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class ForcePasswordChangeFilter extends OncePerRequestFilter {

    private static final int FORCE_PASSWORD_CHANGE_CODE = 40301;

    private final SysUserMapper userMapper;
    private final ObjectMapper objectMapper;

    public ForcePasswordChangeFilter(SysUserMapper userMapper, ObjectMapper objectMapper) {
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isAlwaysAllowed(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        LoginUser loginUser = currentLoginUser();
        if (loginUser == null || !StringUtils.hasText(loginUser.getId())) {
            filterChain.doFilter(request, response);
            return;
        }

        SysUser user = userMapper.selectById(loginUser.getId());
        if (user != null && Integer.valueOf(1).equals(user.getForcePasswordChange())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), Result.error(FORCE_PASSWORD_CHANGE_CODE, "请先修改初始密码"));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAlwaysAllowed(HttpServletRequest request) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        if (!path.startsWith("/api/")) {
            return true;
        }
        return ("POST".equalsIgnoreCase(request.getMethod()) && "/api/auth/login".equals(path))
                || ("POST".equalsIgnoreCase(request.getMethod()) && "/api/auth/refresh".equals(path))
                || ("PUT".equalsIgnoreCase(request.getMethod()) && "/api/auth/password".equals(path))
                || ("GET".equalsIgnoreCase(request.getMethod()) && "/api/users/me".equals(path));
    }

    private LoginUser currentLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            return (LoginUser) authentication.getPrincipal();
        }
        return null;
    }
}
