package com.eval.jxj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eval.jxj.common.exception.BizException;
import com.eval.jxj.dto.request.UserCreateRequest;
import com.eval.jxj.dto.request.UserProfileUpdateRequest;
import com.eval.jxj.dto.response.ClassOptionVO;
import com.eval.jxj.dto.response.UserImportResult;
import com.eval.jxj.dto.response.UserVO;
import com.eval.jxj.entity.SysUser;
import com.eval.jxj.mapper.SysUserMapper;
import com.eval.jxj.service.UserService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class UserServiceImpl implements UserService {

    public static final List<String> IMPORT_HEADERS = Arrays.asList(
            "loginId", "name", "role", "password", "email", "phone", "college", "major", "className", "grade"
    );
    private static final List<String> REQUIRED_IMPORT_HEADERS = Arrays.asList("loginId", "name", "role");

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(SysUserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Page<UserVO> listUsers(int page, int size, String role, String keyword) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(role)) {
            wrapper.eq(SysUser::getRole, role);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysUser::getName, keyword)
                    .or().like(SysUser::getLoginId, keyword));
        }
        wrapper.orderByDesc(SysUser::getCreatedAt);

        Page<SysUser> result = userMapper.selectPage(new Page<>(page, size), wrapper);
        Page<UserVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toVO).collect(java.util.stream.Collectors.toList()));
        return voPage;
    }

    @Override
    public List<ClassOptionVO> listClassOptions() {
        return userMapper.selectStudentClassOptions();
    }

    @Override
    public UserVO getUser(String id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) throw new BizException("用户不存在");
        return toVO(user);
    }

    @Override
    public UserVO createUser(UserCreateRequest request) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getLoginId, request.getLoginId()));
        if (count > 0) throw new BizException("账号已存在");

        SysUser user = new SysUser();
        BeanUtils.copyProperties(request, user);
        String pwd = StringUtils.hasText(request.getPassword()) ? request.getPassword() : "123456";
        user.setPasswordHash(passwordEncoder.encode(pwd));
        user.setForcePasswordChange(1);
        userMapper.insert(user);
        return toVO(user);
    }

    @Override
    public UserVO updateUser(String id, UserCreateRequest request) {
        SysUser user = userMapper.selectById(id);
        if (user == null) throw new BizException("用户不存在");

        user.setName(request.getName());
        user.setRole(request.getRole());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setCollege(request.getCollege());
        user.setMajor(request.getMajor());
        user.setClassName(request.getClassName());
        user.setGrade(request.getGrade());
        userMapper.updateById(user);
        return toVO(user);
    }

    @Override
    public UserVO updateMeProfile(String id, UserProfileUpdateRequest request) {
        SysUser user = userMapper.selectById(id);
        if (user == null) throw new BizException("用户不存在");

        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        userMapper.updateById(user);
        return toVO(user);
    }

    @Override
    public void deleteUser(String id) {
        userMapper.deleteById(id);
    }

    @Override
    public void setStatus(String id, Integer status) {
        SysUser user = userMapper.selectById(id);
        if (user == null) throw new BizException("用户不存在");
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException("状态值非法");
        }
        user.setStatus(status);
        // 启用时解除锁定并清零失败计数
        if (status == 1) {
            user.setFailedAttempts(0);
        }
        userMapper.updateById(user);
    }

    @Override
    public void resetPassword(String id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) throw new BizException("用户不存在");
        user.setPasswordHash(passwordEncoder.encode("123456"));
        user.setForcePasswordChange(1);
        userMapper.updateById(user);
    }

    @Override
    public UserImportResult importUsers(MultipartFile file) {
        validateImportFile(file);
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new BizException("导入文件没有工作表");
            }

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BizException("导入模板缺少表头");
            }

            DataFormatter formatter = new DataFormatter();
            Map<String, Integer> columns = readHeaderColumns(headerRow, formatter);
            validateRequiredHeaders(columns);

            UserImportResult result = new UserImportResult();
            Set<String> seenLoginIds = new HashSet<>();
            int lastRow = sheet.getLastRowNum();
            for (int rowIndex = 1; rowIndex <= lastRow; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (isEmptyRow(row, formatter)) {
                    continue;
                }
                importRow(row, rowIndex + 1, columns, formatter, seenLoginIds, result);
            }
            return result;
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            throw new BizException("读取导入文件失败");
        } catch (Exception e) {
            throw new BizException("导入文件格式不正确");
        }
    }

    private UserVO toVO(SysUser user) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    private void validateImportFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("导入文件不能为空");
        }
        String fileName = file.getOriginalFilename();
        if (!StringUtils.hasText(fileName)) {
            throw new BizException("导入文件名不能为空");
        }
        String lowerName = fileName.toLowerCase();
        if (!lowerName.endsWith(".xlsx") && !lowerName.endsWith(".xls")) {
            throw new BizException("仅支持 .xls 或 .xlsx 文件");
        }
    }

    private Map<String, Integer> readHeaderColumns(Row headerRow, DataFormatter formatter) {
        Map<String, Integer> columns = new HashMap<>();
        short lastCell = headerRow.getLastCellNum();
        for (int cellIndex = 0; cellIndex < lastCell; cellIndex++) {
            String header = cellText(headerRow.getCell(cellIndex), formatter);
            if (StringUtils.hasText(header)) {
                columns.put(header.trim(), cellIndex);
            }
        }
        return columns;
    }

    private void validateRequiredHeaders(Map<String, Integer> columns) {
        List<String> missing = REQUIRED_IMPORT_HEADERS.stream()
                .filter(header -> !columns.containsKey(header))
                .collect(java.util.stream.Collectors.toList());
        if (!missing.isEmpty()) {
            throw new BizException("导入模板缺少必填表头：" + String.join("、", missing));
        }
    }

    private void importRow(Row row, int displayRow, Map<String, Integer> columns, DataFormatter formatter,
                           Set<String> seenLoginIds, UserImportResult result) {
        String loginId = value(row, columns, formatter, "loginId");
        String name = value(row, columns, formatter, "name");
        String roleText = value(row, columns, formatter, "role");

        if (!StringUtils.hasText(loginId)) {
            result.addFailed(displayRow, loginId, "账号不能为空");
            return;
        }
        if (!StringUtils.hasText(name)) {
            result.addFailed(displayRow, loginId, "姓名不能为空");
            return;
        }
        String role = normalizeRole(roleText);
        if (role == null) {
            result.addFailed(displayRow, loginId, "角色只能填写 student/teacher/admin 或 学生/教师/管理员");
            return;
        }
        if (!seenLoginIds.add(loginId)) {
            result.addSkipped(displayRow, loginId, "文件内账号重复，已跳过");
            return;
        }

        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getLoginId, loginId));
        if (count != null && count > 0) {
            result.addSkipped(displayRow, loginId, "账号已存在，已跳过");
            return;
        }

        SysUser user = new SysUser();
        user.setLoginId(loginId);
        user.setName(name);
        user.setRole(role);
        user.setEmail(value(row, columns, formatter, "email"));
        user.setPhone(value(row, columns, formatter, "phone"));
        user.setCollege(value(row, columns, formatter, "college"));
        user.setMajor(value(row, columns, formatter, "major"));
        user.setClassName(value(row, columns, formatter, "className"));
        user.setGrade(value(row, columns, formatter, "grade"));
        String password = value(row, columns, formatter, "password");
        user.setPasswordHash(passwordEncoder.encode(StringUtils.hasText(password) ? password : "123456"));
        user.setForcePasswordChange(1);
        userMapper.insert(user);
        result.addSuccess();
    }

    private String normalizeRole(String roleText) {
        if (!StringUtils.hasText(roleText)) {
            return null;
        }
        switch (roleText.trim()) {
            case "student":
            case "学生":
                return "student";
            case "teacher":
            case "教师":
                return "teacher";
            case "admin":
            case "管理员":
                return "admin";
            default:
                return null;
        }
    }

    private boolean isEmptyRow(Row row, DataFormatter formatter) {
        if (row == null) {
            return true;
        }
        short lastCell = row.getLastCellNum();
        if (lastCell < 0) {
            return true;
        }
        for (int cellIndex = 0; cellIndex < lastCell; cellIndex++) {
            if (StringUtils.hasText(cellText(row.getCell(cellIndex), formatter))) {
                return false;
            }
        }
        return true;
    }

    private String value(Row row, Map<String, Integer> columns, DataFormatter formatter, String header) {
        Integer cellIndex = columns.get(header);
        if (cellIndex == null) {
            return null;
        }
        String text = cellText(row.getCell(cellIndex), formatter);
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private String cellText(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell);
    }
}
