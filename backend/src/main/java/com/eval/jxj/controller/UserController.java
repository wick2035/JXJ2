package com.eval.jxj.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eval.jxj.common.Result;
import com.eval.jxj.dto.request.UserCreateRequest;
import com.eval.jxj.dto.request.UserProfileUpdateRequest;
import com.eval.jxj.dto.response.UserImportResult;
import com.eval.jxj.dto.response.UserVO;
import com.eval.jxj.service.UserService;
import com.eval.jxj.service.impl.UserServiceImpl;
import com.eval.jxj.util.SecurityUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Page<UserVO>> list(@RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int size,
                                     @RequestParam(required = false) String role,
                                     @RequestParam(required = false) String keyword) {
        return Result.ok(userService.listUsers(page, size, role, keyword));
    }

    @GetMapping("/classes")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<?> classes() {
        return Result.ok(userService.listClassOptions());
    }

    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(userService.getUser(SecurityUtil.getCurrentUserId()));
    }

    @PutMapping("/me")
    public Result<UserVO> updateMe(@Valid @RequestBody UserProfileUpdateRequest request) {
        return Result.ok(userService.updateMeProfile(SecurityUtil.getCurrentUserId(), request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<UserVO> get(@PathVariable String id) {
        return Result.ok(userService.getUser(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<UserVO> create(@Valid @RequestBody UserCreateRequest request) {
        return Result.ok(userService.createUser(request));
    }

    @PostMapping("/import")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<UserImportResult> importUsers(@RequestParam("file") MultipartFile file) {
        return Result.ok(userService.importUsers(file));
    }

    @GetMapping("/import-template")
    @PreAuthorize("hasRole('ADMIN')")
    public void downloadImportTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"user-import-template.xlsx\"");

        try (Workbook workbook = new XSSFWorkbook(); ServletOutputStream out = response.getOutputStream()) {
            Sheet sheet = workbook.createSheet("用户导入");
            Row header = sheet.createRow(0);
            for (int i = 0; i < UserServiceImpl.IMPORT_HEADERS.size(); i++) {
                header.createCell(i).setCellValue(UserServiceImpl.IMPORT_HEADERS.get(i));
                sheet.setColumnWidth(i, 18 * 256);
            }

            Row sample = sheet.createRow(1);
            sample.createCell(0).setCellValue("S2026001");
            sample.createCell(1).setCellValue("张三");
            sample.createCell(2).setCellValue("学生");
            sample.createCell(3).setCellValue("123456");
            sample.createCell(4).setCellValue("student@example.com");
            sample.createCell(5).setCellValue("13800000000");
            sample.createCell(6).setCellValue("计算机学院");
            sample.createCell(7).setCellValue("软件工程");
            sample.createCell(8).setCellValue("软工1班");
            sample.createCell(9).setCellValue("2026");

            workbook.write(out);
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<UserVO> update(@PathVariable String id, @Valid @RequestBody UserCreateRequest request) {
        return Result.ok(userService.updateUser(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<?> delete(@PathVariable String id) {
        userService.deleteUser(id);
        return Result.ok();
    }

    @PutMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<?> resetPassword(@PathVariable String id) {
        userService.resetPassword(id);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<?> setStatus(@PathVariable String id, @RequestBody java.util.Map<String, Integer> body) {
        userService.setStatus(id, body.get("status"));
        return Result.ok();
    }
}
