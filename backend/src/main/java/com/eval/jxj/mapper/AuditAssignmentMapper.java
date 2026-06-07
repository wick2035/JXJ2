package com.eval.jxj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eval.jxj.entity.AuditAssignment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface AuditAssignmentMapper extends BaseMapper<AuditAssignment> {
    @Select("select count(*) from audit_assignment where reviewer_id = #{reviewerId} and status = 'pending'")
    int countPendingByReviewerId(String reviewerId);

    @Select("select declaration_id from audit_assignment where reviewer_id = #{reviewerId} and status = 'pending'")
    List<String> selectPendingDeclarationIdsByReviewerId(String reviewerId);

    @Select("select * from audit_assignment where declaration_id = #{declarationId} and reviewer_id = #{reviewerId} and status = 'pending' limit 1")
    AuditAssignment selectPendingByDeclarationAndReviewer(@Param("declarationId") String declarationId,
                                                          @Param("reviewerId") String reviewerId);

    @Select("select * from audit_assignment where declaration_id = #{declarationId} and reviewer_id = #{reviewerId} limit 1")
    AuditAssignment selectByDeclarationAndReviewer(@Param("declarationId") String declarationId,
                                                   @Param("reviewerId") String reviewerId);

    @Select("select count(*) from audit_assignment where declaration_id = #{declarationId} and status = 'pending'")
    int countPendingByDeclarationId(String declarationId);

    @Select("select count(*) from audit_assignment where declaration_id = #{declarationId} and status in ('rejected','returned')")
    int countNegativeByDeclarationId(String declarationId);

    @Select("select count(*) from audit_assignment where declaration_id = #{declarationId}")
    int countByDeclarationId(String declarationId);

    @Update("update audit_assignment set status = 'cancelled', updated_at = now() where declaration_id = #{declarationId} and status = 'pending'")
    int cancelPendingByDeclarationId(String declarationId);

    @Update("update audit_assignment set status = 'pending', action = null, comment = null, reviewed_at = null, updated_at = now() where declaration_id = #{declarationId} and status = 'cancelled'")
    int restoreCancelledByDeclarationId(String declarationId);

    @Update("update audit_assignment set status = 'pending', action = null, comment = null, reviewed_at = null, updated_at = now() where declaration_id = #{declarationId} and status = 'returned'")
    int restoreReturnedByDeclarationId(String declarationId);
}
