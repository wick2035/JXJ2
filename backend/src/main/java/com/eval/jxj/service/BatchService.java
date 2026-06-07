package com.eval.jxj.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eval.jxj.dto.request.BatchAssignmentGenerateRequest;
import com.eval.jxj.dto.request.BatchCreateRequest;
import com.eval.jxj.dto.request.BatchStatusUpdateRequest;
import com.eval.jxj.dto.response.AuditAssignmentVO;
import com.eval.jxj.dto.response.BatchAssignmentGenerateVO;
import com.eval.jxj.dto.response.BatchDetailRowVO;
import com.eval.jxj.dto.response.BatchEvaluationTableVO;
import com.eval.jxj.dto.response.BatchRankingVO;
import com.eval.jxj.dto.response.BatchStatsVO;
import com.eval.jxj.dto.response.BatchVO;

import java.util.List;

public interface BatchService {
    Page<BatchVO> listBatches(int page, int size, String status);
    BatchVO getBatch(String id);
    BatchVO createBatch(BatchCreateRequest request);
    BatchVO updateBatch(String id, BatchCreateRequest request);
    void deleteBatch(String id);
    void updateStatus(String id, BatchStatusUpdateRequest request);
    BatchStatsVO getStats(String id);
    List<BatchRankingVO> getRanking(String id);
    List<BatchDetailRowVO> getDetails(String id);
    BatchEvaluationTableVO getEvaluationTable(String id);
    List<AuditAssignmentVO> getAssignments(String id);
    BatchAssignmentGenerateVO generateAssignments(String id, BatchAssignmentGenerateRequest request);
}
