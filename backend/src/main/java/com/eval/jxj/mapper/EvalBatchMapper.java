package com.eval.jxj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eval.jxj.entity.EvalBatch;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface EvalBatchMapper extends BaseMapper<EvalBatch> {

    @Select("<script>"
            + "select b.* from eval_batch b "
            + "where b.is_deleted = 0 and b.status in ('open_timed','open','closed') "
            + "<if test='status != null and status != \"\"'>and b.status = #{status} </if>"
            + "and (b.target_type = 'all' or exists ("
            + "  select 1 from batch_class bc where bc.batch_id = b.id "
            + "    and bc.class_name = #{className} "
            + "    and (bc.grade = #{grade} or (bc.grade is null and #{grade} is null)))) "
            + "order by b.created_at desc"
            + "</script>")
    Page<EvalBatch> selectStudentBatchPage(Page<EvalBatch> page,
                                           @Param("grade") String grade,
                                           @Param("className") String className,
                                           @Param("status") String status);

    @Select("<script>"
            + "select b.* from eval_batch b "
            + "where b.is_deleted = 0 "
            + "and exists (select 1 from batch_reviewer br where br.batch_id = b.id and br.reviewer_id = #{reviewerId}) "
            + "<if test='status != null and status != \"\"'>and b.status = #{status} </if>"
            + "order by b.created_at desc"
            + "</script>")
    Page<EvalBatch> selectReviewerBatchPage(Page<EvalBatch> page,
                                            @Param("reviewerId") String reviewerId,
                                            @Param("status") String status);
}
