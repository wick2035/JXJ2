package com.eval.jxj.service;

import com.eval.jxj.dto.request.AwardCreateRequest;
import com.eval.jxj.dto.response.AwardVO;
import com.eval.jxj.entity.AwardLevelDef;

import java.util.List;

public interface AwardService {
    List<AwardVO> listAwards(String category);
    AwardVO getAward(String id);
    AwardVO createAward(AwardCreateRequest request);
    AwardVO updateAward(String id, AwardCreateRequest request);
    void deleteAward(String id);
    List<AwardLevelDef> listLevels();
    List<AwardVO> listBatchAwards(String batchId, String category);
}
