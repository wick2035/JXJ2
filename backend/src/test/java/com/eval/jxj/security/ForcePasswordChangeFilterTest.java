package com.eval.jxj.security;

import com.eval.jxj.entity.SysUser;
import com.eval.jxj.mapper.SysUserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForcePasswordChangeFilterTest {

    @Mock
    private SysUserMapper userMapper;

    @BeforeEach
    void setUp() {
        LoginUser loginUser = new LoginUser();
        loginUser.setId("user-1");
        loginUser.setRole("student");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void blocksBusinessApiWhenPasswordChangeIsRequired() throws Exception {
        SysUser user = new SysUser();
        user.setId("user-1");
        user.setForcePasswordChange(1);
        when(userMapper.selectById("user-1")).thenReturn(user);

        ForcePasswordChangeFilter filter = new ForcePasswordChangeFilter(userMapper, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/declarations");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("\"code\":40301");
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("请先修改初始密码");
    }

    @Test
    void allowsChangePasswordApiWhenPasswordChangeIsRequired() throws Exception {
        ForcePasswordChangeFilter filter = new ForcePasswordChangeFilter(userMapper, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/auth/password");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        verify(userMapper, never()).selectById("user-1");
    }
}
