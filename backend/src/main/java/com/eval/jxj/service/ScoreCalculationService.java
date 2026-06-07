package com.eval.jxj.service;

import com.eval.jxj.entity.Declaration;
import com.eval.jxj.entity.DeclarationItem;

import java.math.BigDecimal;

public interface ScoreCalculationService {
    BigDecimal computeItemScore(DeclarationItem item, String batchId);
    void computeDeclarationScore(Declaration declaration);
    void recalcBatch(String batchId, String triggeredBy, String reason);
}
