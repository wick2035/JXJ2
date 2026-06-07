package com.eval.jxj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eval.jxj.entity.BatchReviewer;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface BatchReviewerMapper extends BaseMapper<BatchReviewer> {
    @Select("select reviewer_id from batch_reviewer where batch_id = #{batchId}")
    List<String> selectReviewerIdsByBatchId(String batchId);

    @Delete("delete from batch_reviewer where batch_id = #{batchId}")
    int deleteByBatchId(String batchId);
}
