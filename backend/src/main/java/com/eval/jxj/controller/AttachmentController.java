package com.eval.jxj.controller;

import com.eval.jxj.common.Result;
import com.eval.jxj.entity.DeclarationAttachment;
import com.eval.jxj.mapper.DeclarationAttachmentMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    private final DeclarationAttachmentMapper attachmentMapper;

    @Value("${upload.path}")
    private String uploadPath;

    public AttachmentController(DeclarationAttachmentMapper attachmentMapper) {
        this.attachmentMapper = attachmentMapper;
    }

    @PostMapping("/upload/{itemId}")
    public Result<List<DeclarationAttachment>> upload(@PathVariable String itemId,
                                                       @RequestParam("files") MultipartFile[] files) throws IOException {
        List<DeclarationAttachment> result = new ArrayList<>();
        for (MultipartFile file : files) {
            String originalName = file.getOriginalFilename();
            String ext = originalName != null && originalName.contains(".")
                    ? originalName.substring(originalName.lastIndexOf(".")) : "";
            String storedName = UUID.randomUUID().toString() + ext;

            File dir = new File(uploadPath + "/" + itemId).getAbsoluteFile();
            if (!dir.exists()) dir.mkdirs();
            File target = new File(dir, storedName);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            DeclarationAttachment att = new DeclarationAttachment();
            att.setDeclarationItemId(itemId);
            att.setFileName(originalName);
            att.setFilePath("/uploads/" + itemId + "/" + storedName);
            att.setFileSize(file.getSize());
            att.setMimeType(file.getContentType());
            attachmentMapper.insert(att);
            result.add(att);
        }
        return Result.ok(result);
    }

    /**
     * 鉴权下载/预览附件：该路径位于 /api/** 下，已被 Spring Security 要求登录后才能访问，
     * 因此未登录无法通过链接读取上传文件。文件字节经此接口返回，前端用 axios 带 Token 拉取。
     */
    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> download(@PathVariable String id) {
        DeclarationAttachment att = attachmentMapper.selectById(id);
        if (att == null || att.getFilePath() == null) {
            return ResponseEntity.notFound().build();
        }

        // filePath 形如 /uploads/{itemId}/{stored}，去掉 /uploads 前缀后拼到 uploadPath 下
        String relative = att.getFilePath().replaceFirst("^/uploads", "");
        Path base = Paths.get(uploadPath).toAbsolutePath().normalize();
        Path target = base.resolve(relative.replaceFirst("^/", "")).normalize();
        // 防路径穿越：解析后的路径必须仍位于 uploadPath 之内
        if (!target.startsWith(base) || !Files.exists(target) || Files.isDirectory(target)) {
            return ResponseEntity.notFound().build();
        }

        MediaType contentType;
        try {
            contentType = att.getMimeType() != null ? MediaType.parseMediaType(att.getMimeType())
                    : MediaType.APPLICATION_OCTET_STREAM;
        } catch (Exception e) {
            contentType = MediaType.APPLICATION_OCTET_STREAM;
        }

        String fileName = att.getFileName() != null ? att.getFileName() : target.getFileName().toString();
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.status(HttpStatus.OK)
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedName)
                .body(new FileSystemResource(target.toFile()));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable String id) {
        attachmentMapper.deleteById(id);
        return Result.ok();
    }
}
