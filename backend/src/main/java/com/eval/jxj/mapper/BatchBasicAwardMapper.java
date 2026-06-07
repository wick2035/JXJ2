package com.eval.jxj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eval.jxj.entity.BatchBasicAward;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface BatchBasicAwardMapper extends BaseMapper<BatchBasicAward> {
    @Delete("<script>"
            + "delete from batch_basic_award where batch_id = #{batchId} and award_id in "
            + "<foreach collection='awardIds' item='awardId' open='(' separator=',' close=')'>#{awardId}</foreach>"
            + "</script>")
    int deleteByBatchAndAwardIds(@Param("batchId") String batchId, @Param("awardIds") List<String> awardIds);
}
