package com.eval.jxj.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eval.jxj.common.Result;
import com.eval.jxj.dto.request.AuditCorrectionRequest;
import com.eval.jxj.dto.request.AuditRequest;
import com.eval.jxj.dto.response.DeclarationVO;
import com.eval.jxj.service.AuditService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public Result<Page<DeclarationVO>> listPending(@RequestParam(required = false) String scope,
                                                    @RequestParam(required = false) String batchId,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return Result.ok(auditService.listPending(scope, batchId, keyword, page, size));
    }

    @GetMapping("/finished")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public Result<Page<DeclarationVO>> listFinished(@RequestParam(required = false) String scope,
                                                     @RequestParam(required = false) String batchId,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return Result.ok(auditService.listFinished(scope, batchId, keyword, page, size));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public Result<?> stats(@RequestParam(required = false) String batchId,
                           @RequestParam(required = false) String keyword) {
        return Result.ok(auditService.getStats(batchId, keyword));
    }

    @PostMapping("/declarations/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public Result<?> audit(@PathVariable String id, @Valid @RequestBody AuditRequest request) {
        auditService.audit(id, request);
        return Result.ok();
    }

    @PostMapping("/declarations/{id}/correction")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public Result<?> correctAudit(@PathVariable String id, @Valid @RequestBody AuditCorrectionRequest request) {
        auditService.correctAudit(id, request);
        return Result.ok();
    }
}
