package com.eval.jxj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eval.jxj.entity.BatchBasicScore;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface BatchBasicScoreMapper extends BaseMapper<BatchBasicScore> {
    @Delete("<script>"
            + "delete from batch_basic_score where batch_id = #{batchId} and award_id in "
            + "<foreach collection='awardIds' item='awardId' open='(' separator=',' close=')'>#{awardId}</foreach>"
            + "</script>")
    int deleteByBatchAndAwardIds(@Param("batchId") String batchId, @Param("awardIds") List<String> awardIds);

    @Select("select * from batch_basic_score where batch_id = #{batchId} and student_id = #{studentId}")
    List<BatchBasicScore> selectStudentScores(@Param("batchId") String batchId, @Param("studentId") String studentId);
}
