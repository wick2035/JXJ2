package com.eval.jxj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eval.jxj.entity.AwardLevelScore;
import org.apache.ibatis.annotations.Delete;

public interface AwardLevelScoreMapper extends BaseMapper<AwardLevelScore> {
    @Delete("delete from award_level_score where award_id = #{awardId}")
    int deleteByAwardId(String awardId);
}
