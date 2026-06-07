package com.eval.jxj.service;

import com.eval.jxj.dto.response.BatchAssignmentGenerateVO;

public interface BatchAssignmentService {
    int generateForDeclaration(String declarationId, boolean replacePending);
    BatchAssignmentGenerateVO generateForBatch(String batchId, boolean replacePending);
    BatchAssignmentGenerateVO generateForReviewer(String batchId, String reviewerId, int count);
}
