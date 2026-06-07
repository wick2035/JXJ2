package com.eval.jxj.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eval.jxj.dto.request.UserCreateRequest;
import com.eval.jxj.dto.request.UserProfileUpdateRequest;
import com.eval.jxj.dto.response.ClassOptionVO;
import com.eval.jxj.dto.response.UserImportResult;
import com.eval.jxj.dto.response.UserVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UserService {
    Page<UserVO> listUsers(int page, int size, String role, String keyword);
    List<ClassOptionVO> listClassOptions();
    UserVO getUser(String id);
    UserVO createUser(UserCreateRequest request);
    UserVO updateUser(String id, UserCreateRequest request);
    UserVO updateMeProfile(String id, UserProfileUpdateRequest request);
    void deleteUser(String id);
    void resetPassword(String id);
    void setStatus(String id, Integer status);
    UserImportResult importUsers(MultipartFile file);
}
