package com.eval.jxj.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eval.jxj.common.Result;
import com.eval.jxj.dto.request.BatchAssignmentGenerateRequest;
import com.eval.jxj.dto.request.BatchCreateRequest;
import com.eval.jxj.dto.request.BatchStatusUpdateRequest;
import com.eval.jxj.dto.response.BatchVO;
import com.eval.jxj.service.BatchExportService;
import com.eval.jxj.service.BatchService;
import com.eval.jxj.service.ScoreCalculationService;
import com.eval.jxj.util.SecurityUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/batches")
public class BatchController {

    private final BatchService batchService;
    private final ScoreCalculationService scoreService;
    private final BatchExportService batchExportService;

    public BatchController(BatchService batchService, ScoreCalculationService scoreService,
                           BatchExportService batchExportService) {
        this.batchService = batchService;
        this.scoreService = scoreService;
        this.batchExportService = batchExportService;
    }

    @GetMapping
    public Result<Page<BatchVO>> list(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "20") int size,
                                      @RequestParam(required = false) String status) {
        return Result.ok(batchService.listBatches(page, size, status));
    }

    @GetMapping("/{id}")
    public Result<BatchVO> get(@PathVariable String id) {
        return Result.ok(batchService.getBatch(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<BatchVO> create(@Valid @RequestBody BatchCreateRequest request) {
        return Result.ok(batchService.createBatch(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<BatchVO> update(@PathVariable String id, @Valid @RequestBody BatchCreateRequest request) {
        return Result.ok(batchService.updateBatch(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<?> delete(@PathVariable String id) {
        batchService.deleteBatch(id);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<?> updateStatus(@PathVariable String id, @RequestBody BatchStatusUpdateRequest request) {
        batchService.updateStatus(id, request);
        return Result.ok();
    }

    @GetMapping("/{id}/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<?> stats(@PathVariable String id) {
        return Result.ok(batchService.getStats(id));
    }

    @GetMapping("/{id}/ranking")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<?> ranking(@PathVariable String id) {
        return Result.ok(batchService.getRanking(id));
    }

    @GetMapping("/{id}/details")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<?> details(@PathVariable String id) {
        return Result.ok(batchService.getDetails(id));
    }

    @GetMapping("/{id}/evaluation-table")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<?> evaluationTable(@PathVariable String id) {
        return Result.ok(batchService.getEvaluationTable(id));
    }

    @GetMapping("/{id}/assignments")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<?> assignments(@PathVariable String id) {
        return Result.ok(batchService.getAssignments(id));
    }

    @PostMapping("/{id}/assignments/generate")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<?> generateAssignments(@PathVariable String id,
                                         @RequestBody(required = false) BatchAssignmentGenerateRequest request) {
        return Result.ok(batchService.generateAssignments(id, request));
    }

    @GetMapping("/{id}/export")
    @PreAuthorize("hasRole('ADMIN')")
    public void export(@PathVariable String id, HttpServletResponse response) throws IOException {
        BatchVO batch = batchService.getBatch(id);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"batch-" + batch.getId() + ".xlsx\"");
        batchExportService.writeBatchExport(id, response.getOutputStream());
    }

    @PostMapping("/{id}/recalculate")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<?> recalculate(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : "管理员手动触发";
        scoreService.recalcBatch(id, SecurityUtil.getCurrentUserId(), reason);
        return Result.ok();
    }
}
