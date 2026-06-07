package com.eval.jxj.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eval.jxj.common.Result;
import com.eval.jxj.common.exception.BizException;
import com.eval.jxj.dto.request.OperationLogDeleteRequest;
import com.eval.jxj.entity.OperationLog;
import com.eval.jxj.service.OperationLogService;
import com.eval.jxj.service.SecondaryPasswordService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/operation-logs")
@PreAuthorize("hasRole('ADMIN')")
public class OperationLogController {

    private final OperationLogService operationLogService;
    private final SecondaryPasswordService secondaryPasswordService;

    public OperationLogController(OperationLogService operationLogService,
                                 SecondaryPasswordService secondaryPasswordService) {
        this.operationLogService = operationLogService;
        this.secondaryPasswordService = secondaryPasswordService;
    }

    @GetMapping
    public Result<Page<OperationLog>> list(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int size,
                                           @RequestParam(required = false) String role,
                                           @RequestParam(required = false) String module,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String startDate,
                                           @RequestParam(required = false) String endDate) {
        return Result.ok(operationLogService.page(page, size, role, module, keyword, startDate, endDate));
    }

    @PostMapping("/delete")
    public Result<?> delete(@RequestBody OperationLogDeleteRequest request) {
        if (request.getIds() == null || request.getIds().isEmpty()) {
            throw new BizException("请选择要删除的记录");
        }
        secondaryPasswordService.verify(request.getSecondaryPassword());
        operationLogService.softDelete(request.getIds());
        return Result.ok();
    }
}
