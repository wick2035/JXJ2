package com.eval.jxj.service;

import com.eval.jxj.dto.response.BasicAwardImportResult;
import com.eval.jxj.dto.response.BatchBasicAwardVO;
import com.eval.jxj.dto.response.DeclarationVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface BasicAwardService {
    BasicAwardImportResult importScores(String batchId, String category, MultipartFile file);
    List<BatchBasicAwardVO> listBatchBasicAwards(String batchId, String category);
    List<DeclarationVO.BasicItemVO> listMyBasicItems(String batchId);
}
