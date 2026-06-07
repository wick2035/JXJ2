package com.eval.jxj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eval.jxj.entity.AuditRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AuditRecordMapper extends BaseMapper<AuditRecord> {
    @Select("select count(*) from audit_record where declaration_id = #{declarationId}")
    int countByDeclarationId(String declarationId);

    @Select("select count(*) from audit_record where declaration_id = #{declarationId} and reviewer_id = #{reviewerId}")
    int countByDeclarationAndReviewer(@Param("declarationId") String declarationId,
                                      @Param("reviewerId") String reviewerId);
}
