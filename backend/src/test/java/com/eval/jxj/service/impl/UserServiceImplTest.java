package com.eval.jxj.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.eval.jxj.dto.request.UserProfileUpdateRequest;
import com.eval.jxj.dto.response.UserImportResult;
import com.eval.jxj.dto.response.UserVO;
import com.eval.jxj.entity.SysUser;
import com.eval.jxj.mapper.SysUserMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void updateMeProfile_onlyUpdatesEmailAndPhone() {
        SysUser user = new SysUser();
        user.setId("user-1");
        user.setLoginId("S2026001");
        user.setName("张三");
        user.setRole("student");
        user.setEmail("old@example.com");
        user.setPhone("13000000000");
        user.setCollege("计算机学院");
        user.setMajor("软件工程");
        user.setClassName("软工1班");
        user.setGrade("2026");
        when(userMapper.selectById("user-1")).thenReturn(user);

        UserProfileUpdateRequest request = new UserProfileUpdateRequest();
        request.setEmail("new@example.com");
        request.setPhone("13900000000");

        UserServiceImpl service = new UserServiceImpl(userMapper, passwordEncoder);
        UserVO result = service.updateMeProfile("user-1", request);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        SysUser updated = captor.getValue();
        assertThat(updated.getEmail()).isEqualTo("new@example.com");
        assertThat(updated.getPhone()).isEqualTo("13900000000");
        assertThat(updated.getLoginId()).isEqualTo("S2026001");
        assertThat(updated.getName()).isEqualTo("张三");
        assertThat(updated.getRole()).isEqualTo("student");
        assertThat(updated.getCollege()).isEqualTo("计算机学院");
        assertThat(updated.getMajor()).isEqualTo("软件工程");
        assertThat(updated.getClassName()).isEqualTo("软工1班");
        assertThat(updated.getGrade()).isEqualTo("2026");
        assertThat(result.getEmail()).isEqualTo("new@example.com");
        assertThat(result.getPhone()).isEqualTo("13900000000");
    }

    @Test
    void importUsers_createsValidRowsAndMapsChineseAndEnglishRoles() throws Exception {
        when(userMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode("secret")).thenReturn("encoded-secret");
        when(passwordEncoder.encode("123456")).thenReturn("encoded-default");

        UserServiceImpl service = new UserServiceImpl(userMapper, passwordEncoder);
        MockMultipartFile file = workbookFile(
                row("loginId", "name", "role", "password", "email", "phone", "college", "major", "className", "grade"),
                row("S2026001", "张三", "学生", "secret", "s1@example.com", "13800000001", "计算机学院", "软件工程", "软工1班", "2026"),
                row("T2026001", "李老师", "teacher", "", "", "", "计算机学院", "", "", "")
        );

        UserImportResult result = service.importUsers(file);

        assertThat(result.getSuccessCount()).isEqualTo(2);
        assertThat(result.getSkippedCount()).isZero();
        assertThat(result.getFailedCount()).isZero();

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper, times(2)).insert(captor.capture());
        List<SysUser> users = captor.getAllValues();
        assertThat(users).extracting(SysUser::getLoginId).containsExactly("S2026001", "T2026001");
        assertThat(users).extracting(SysUser::getRole).containsExactly("student", "teacher");
        assertThat(users.get(0).getPasswordHash()).isEqualTo("encoded-secret");
        assertThat(users.get(1).getPasswordHash()).isEqualTo("encoded-default");
        assertThat(users).extracting(SysUser::getForcePasswordChange).containsExactly(1, 1);
    }

    @Test
    void createUser_marksAccountForPasswordChange() {
        when(userMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode("custom-password")).thenReturn("encoded-custom");

        com.eval.jxj.dto.request.UserCreateRequest request = new com.eval.jxj.dto.request.UserCreateRequest();
        request.setLoginId("S2026009");
        request.setName("新用户");
        request.setRole("student");
        request.setPassword("custom-password");

        UserServiceImpl service = new UserServiceImpl(userMapper, passwordEncoder);
        service.createUser(request);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insert(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("encoded-custom");
        assertThat(captor.getValue().getForcePasswordChange()).isEqualTo(1);
    }

    @Test
    void resetPassword_marksAccountForPasswordChange() {
        SysUser user = new SysUser();
        user.setId("user-1");
        user.setForcePasswordChange(0);
        when(userMapper.selectById("user-1")).thenReturn(user);
        when(passwordEncoder.encode("123456")).thenReturn("encoded-default");

        UserServiceImpl service = new UserServiceImpl(userMapper, passwordEncoder);
        service.resetPassword("user-1");

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("encoded-default");
        assertThat(captor.getValue().getForcePasswordChange()).isEqualTo(1);
    }

    @Test
    void importUsers_reportsInvalidExistingAndDuplicateRowsWithoutInsertingThem() throws Exception {
        when(userMapper.selectCount(any(Wrapper.class))).thenReturn(1L, 0L);
        when(passwordEncoder.encode("123456")).thenReturn("encoded-default");

        UserServiceImpl service = new UserServiceImpl(userMapper, passwordEncoder);
        MockMultipartFile file = workbookFile(
                row("loginId", "name", "role", "password", "email", "phone", "college", "major", "className", "grade"),
                row("EXISTING", "已存在", "student", "", "", "", "", "", "", ""),
                row("S2026002", "", "学生", "", "", "", "", "", "", ""),
                row("S2026003", "角色错误", "校友", "", "", "", "", "", "", ""),
                row("S2026004", "王五", "管理员", "", "", "", "", "", "", ""),
                row("S2026004", "重复王五", "admin", "", "", "", "", "", "", "")
        );

        UserImportResult result = service.importUsers(file);

        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getSkippedCount()).isEqualTo(2);
        assertThat(result.getFailedCount()).isEqualTo(2);
        assertThat(result.getErrors()).extracting(UserImportResult.RowError::getRow)
                .containsExactly(2, 3, 4, 6);
        assertThat(result.getErrors()).extracting(UserImportResult.RowError::getReason)
                .containsExactly("账号已存在，已跳过", "姓名不能为空", "角色只能填写 student/teacher/admin 或 学生/教师/管理员", "文件内账号重复，已跳过");

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insert(captor.capture());
        assertThat(captor.getValue().getLoginId()).isEqualTo("S2026004");
        assertThat(captor.getValue().getRole()).isEqualTo("admin");
    }

    @Test
    void importUsers_rejectsFilesMissingRequiredHeaders() throws Exception {
        UserServiceImpl service = new UserServiceImpl(userMapper, passwordEncoder);
        MockMultipartFile file = workbookFile(
                row("loginId", "name", "password"),
                row("S2026001", "张三", "123456")
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.importUsers(file))
                .hasMessage("导入模板缺少必填表头：role");
        verify(userMapper, never()).insert(any(SysUser.class));
    }

    private static Object[] row(Object... values) {
        return values;
    }

    private static MockMultipartFile workbookFile(Object[]... rows) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("用户导入");
            for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
                Row row = sheet.createRow(rowIndex);
                Object[] values = rows[rowIndex];
                for (int cellIndex = 0; cellIndex < values.length; cellIndex++) {
                    row.createCell(cellIndex).setCellValue(values[cellIndex] == null ? "" : String.valueOf(values[cellIndex]));
                }
            }
            workbook.write(out);
            return new MockMultipartFile(
                    "file",
                    "users.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray()
            );
        }
    }
}
