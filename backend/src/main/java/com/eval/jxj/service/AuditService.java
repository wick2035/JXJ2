package com.eval.jxj.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eval.jxj.dto.request.AuditCorrectionRequest;
import com.eval.jxj.dto.request.AuditRequest;
import com.eval.jxj.dto.response.AuditQueueStatsVO;
import com.eval.jxj.dto.response.DeclarationVO;

public interface AuditService {
    Page<DeclarationVO> listPending(String scope, String batchId, String keyword, int page, int size);
    Page<DeclarationVO> listFinished(String scope, String batchId, String keyword, int page, int size);
    AuditQueueStatsVO getStats(String batchId, String keyword);
    void audit(String declarationId, AuditRequest request);
    void correctAudit(String declarationId, AuditCorrectionRequest request);
}
