package com.eval.jxj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eval.jxj.entity.BatchClass;
import org.apache.ibatis.annotations.Delete;

public interface BatchClassMapper extends BaseMapper<BatchClass> {
    @Delete("delete from batch_class where batch_id = #{batchId}")
    int deleteByBatchId(String batchId);
}
