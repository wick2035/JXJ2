package com.eval.jxj.service.impl;

import com.eval.jxj.dto.request.ChangePasswordRequest;
import com.eval.jxj.entity.SysUser;
import com.eval.jxj.mapper.SysUserMapper;
import com.eval.jxj.security.JwtTokenProvider;
import com.eval.jxj.service.OperationLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private OperationLogService operationLogService;

    @Test
    void changePassword_clearsForcePasswordChangeFlag() {
        SysUser user = new SysUser();
        user.setId("user-1");
        user.setPasswordHash("old-hash");
        user.setForcePasswordChange(1);

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword("old-password");
        request.setNewPassword("new-password");

        when(userMapper.selectById("user-1")).thenReturn(user);
        when(passwordEncoder.matches("old-password", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

        AuthServiceImpl service = new AuthServiceImpl(userMapper, jwtTokenProvider, passwordEncoder, operationLogService);
        service.changePassword("user-1", request);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("new-hash");
        assertThat(captor.getValue().getForcePasswordChange()).isZero();
    }
}
