package com.eval.jxj.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eval.jxj.dto.request.DeclarationSaveRequest;
import com.eval.jxj.dto.response.DeclarationVO;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface DeclarationService {
    Page<DeclarationVO> listDeclarations(String batchId, String status, String keyword, int page, int size);
    DeclarationVO getDeclaration(String id);
    DeclarationVO saveDeclaration(DeclarationSaveRequest request);
    DeclarationVO saveDeclaration(DeclarationSaveRequest request, MultipartFile[] files) throws IOException;
    DeclarationVO submitDeclaration(String id);
    DeclarationVO submitDeclaration(DeclarationSaveRequest request, MultipartFile[] files) throws IOException;
    DeclarationVO withdrawDeclaration(String id);
    void deleteDeclaration(String id);
}
