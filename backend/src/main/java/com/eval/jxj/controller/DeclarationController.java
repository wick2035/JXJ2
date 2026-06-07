package com.eval.jxj.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eval.jxj.common.Result;
import com.eval.jxj.dto.request.DeclarationSaveRequest;
import com.eval.jxj.dto.response.DeclarationVO;
import com.eval.jxj.service.DeclarationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/declarations")
public class DeclarationController {

    private final DeclarationService declarationService;
    private final ObjectMapper objectMapper;

    public DeclarationController(DeclarationService declarationService, ObjectMapper objectMapper) {
        this.declarationService = declarationService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public Result<Page<DeclarationVO>> list(@RequestParam(required = false) String batchId,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return Result.ok(declarationService.listDeclarations(batchId, status, keyword, page, size));
    }

    @GetMapping("/{id}")
    public Result<DeclarationVO> get(@PathVariable String id) {
        return Result.ok(declarationService.getDeclaration(id));
    }

    @PostMapping
    public Result<DeclarationVO> save(@RequestBody DeclarationSaveRequest request) {
        return Result.ok(declarationService.saveDeclaration(request));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<DeclarationVO> saveMultipart(@RequestPart("payload") String payload,
                                               @RequestPart(value = "files", required = false) MultipartFile[] files)
            throws IOException {
        DeclarationSaveRequest request = objectMapper.readValue(payload, DeclarationSaveRequest.class);
        return Result.ok(declarationService.saveDeclaration(request, files));
    }

    @PostMapping("/{id}/submit")
    public Result<DeclarationVO> submit(@PathVariable String id) {
        return Result.ok(declarationService.submitDeclaration(id));
    }

    @PostMapping("/{id}/withdraw")
    public Result<DeclarationVO> withdraw(@PathVariable String id) {
        return Result.ok(declarationService.withdrawDeclaration(id));
    }

    @PostMapping(value = "/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<DeclarationVO> submitMultipart(@RequestPart("payload") String payload,
                                                 @RequestPart(value = "files", required = false) MultipartFile[] files)
            throws IOException {
        DeclarationSaveRequest request = objectMapper.readValue(payload, DeclarationSaveRequest.class);
        return Result.ok(declarationService.submitDeclaration(request, files));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable String id) {
        declarationService.deleteDeclaration(id);
        return Result.ok();
    }
}
