package com.eval.jxj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eval.jxj.entity.Declaration;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface DeclarationMapper extends BaseMapper<Declaration> {
    @Select("select * from declaration where batch_id = #{batchId} and student_id = #{studentId} and is_deleted = 1 limit 1")
    Declaration selectDeletedByBatchAndStudent(@Param("batchId") String batchId,
                                               @Param("studentId") String studentId);

    @Select("<script>"
            + "select count(*) from declaration d "
            + "join sys_user u on u.id = d.student_id "
            + "where d.is_deleted = 0 and d.batch_id = #{batchId} and d.status = 'approved' "
            + "and u.class_name = #{className} "
            + "and (u.grade = #{grade} or (u.grade is null and #{grade} is null))"
            + "</script>")
    int countApprovedClassmates(@Param("batchId") String batchId,
                                @Param("grade") String grade,
                                @Param("className") String className);

    @Select("<script>"
            + "select count(*) from declaration d "
            + "join sys_user u on u.id = d.student_id "
            + "where d.is_deleted = 0 and d.batch_id = #{batchId} and d.status = 'approved' "
            + "and u.class_name = #{className} "
            + "and (u.grade = #{grade} or (u.grade is null and #{grade} is null)) "
            + "and d.total_score is not null and d.total_score &gt; #{totalScore}"
            + "</script>")
    int countApprovedClassmatesAhead(@Param("batchId") String batchId,
                                     @Param("grade") String grade,
                                     @Param("className") String className,
                                     @Param("totalScore") java.math.BigDecimal totalScore);

    @Update("update declaration set is_deleted = 0, status = 'draft', total_score = null, morality_score = null, ability_score = null, sports_score = null, submitted_at = null, updated_at = now() where id = #{id}")
    int restoreDeleted(String id);

    @Select("<script>"
            + "select distinct d.* "
            + "from declaration d "
            + "left join sys_user u on u.id = d.student_id "
            + "left join eval_batch b on b.id = d.batch_id "
            + "where d.is_deleted = 0 and d.status = 'submitted' "
            + "<if test='batchId != null and batchId != \"\"'>and d.batch_id = #{batchId} </if>"
            + "<if test='keyword != null and keyword != \"\"'>"
            + "and (u.name like concat('%', #{keyword}, '%') "
            + "or u.login_id like concat('%', #{keyword}, '%') "
            + "or b.name like concat('%', #{keyword}, '%')) "
            + "</if>"
            + "<choose>"
            + "<when test='scope == \"mine\"'>"
            + "and exists (select 1 from audit_assignment aa where aa.declaration_id = d.id and aa.status = 'pending' and aa.reviewer_id = #{reviewerId}) "
            + "</when>"
            + "<when test='scope == \"assigned\"'>"
            + "and exists (select 1 from audit_assignment aa where aa.declaration_id = d.id and aa.status = 'pending') "
            + "</when>"
            + "<when test='scope == \"unassigned\"'>"
            + "and not exists (select 1 from audit_assignment aa where aa.declaration_id = d.id and aa.status = 'pending') "
            + "</when>"
            + "</choose>"
            + "order by d.submitted_at asc"
            + "</script>")
    Page<Declaration> selectAuditQueuePage(Page<Declaration> page,
                                           @Param("scope") String scope,
                                           @Param("reviewerId") String reviewerId,
                                           @Param("batchId") String batchId,
                                           @Param("keyword") String keyword);

    @Select("<script>"
            + "select count(distinct d.id) "
            + "from declaration d "
            + "left join sys_user u on u.id = d.student_id "
            + "left join eval_batch b on b.id = d.batch_id "
            + "where d.is_deleted = 0 and d.status = 'submitted' "
            + "<if test='batchId != null and batchId != \"\"'>and d.batch_id = #{batchId} </if>"
            + "<if test='keyword != null and keyword != \"\"'>"
            + "and (u.name like concat('%', #{keyword}, '%') "
            + "or u.login_id like concat('%', #{keyword}, '%') "
            + "or b.name like concat('%', #{keyword}, '%')) "
            + "</if>"
            + "<choose>"
            + "<when test='scope == \"mine\"'>"
            + "and exists (select 1 from audit_assignment aa where aa.declaration_id = d.id and aa.status = 'pending' and aa.reviewer_id = #{reviewerId}) "
            + "</when>"
            + "<when test='scope == \"assigned\"'>"
            + "and exists (select 1 from audit_assignment aa where aa.declaration_id = d.id and aa.status = 'pending') "
            + "</when>"
            + "<when test='scope == \"unassigned\"'>"
            + "and not exists (select 1 from audit_assignment aa where aa.declaration_id = d.id and aa.status = 'pending') "
            + "</when>"
            + "</choose>"
            + "</script>")
    long countAuditQueue(@Param("scope") String scope,
                         @Param("reviewerId") String reviewerId,
                         @Param("batchId") String batchId,
                         @Param("keyword") String keyword);

    @Select("<script>"
            + "select distinct d.* "
            + "from declaration d "
            + "left join sys_user u on u.id = d.student_id "
            + "left join eval_batch b on b.id = d.batch_id "
            + "where d.is_deleted = 0 "
            + "<if test='batchId != null and batchId != \"\"'>and d.batch_id = #{batchId} </if>"
            + "<if test='keyword != null and keyword != \"\"'>"
            + "and (u.name like concat('%', #{keyword}, '%') "
            + "or u.login_id like concat('%', #{keyword}, '%') "
            + "or b.name like concat('%', #{keyword}, '%')) "
            + "</if>"
            + "<choose>"
            + "<when test='scope == \"mine\"'>"
            + "and (exists (select 1 from audit_assignment aa where aa.declaration_id = d.id and aa.reviewer_id = #{reviewerId} and aa.status in ('approved','rejected','returned')) "
            + "or exists (select 1 from audit_record ar where ar.declaration_id = d.id and ar.reviewer_id = #{reviewerId})) "
            + "</when>"
            + "<otherwise>"
            + "and (exists (select 1 from audit_assignment aa where aa.declaration_id = d.id and aa.status in ('approved','rejected','returned')) "
            + "or exists (select 1 from audit_record ar where ar.declaration_id = d.id)) "
            + "</otherwise>"
            + "</choose>"
            + "order by d.updated_at desc"
            + "</script>")
    Page<Declaration> selectFinishedAuditPage(Page<Declaration> page,
                                              @Param("scope") String scope,
                                              @Param("reviewerId") String reviewerId,
                                              @Param("batchId") String batchId,
                                              @Param("keyword") String keyword);

    @Select("<script>"
            + "select count(distinct d.id) "
            + "from declaration d "
            + "left join sys_user u on u.id = d.student_id "
            + "left join eval_batch b on b.id = d.batch_id "
            + "where d.is_deleted = 0 "
            + "<if test='batchId != null and batchId != \"\"'>and d.batch_id = #{batchId} </if>"
            + "<if test='keyword != null and keyword != \"\"'>"
            + "and (u.name like concat('%', #{keyword}, '%') "
            + "or u.login_id like concat('%', #{keyword}, '%') "
            + "or b.name like concat('%', #{keyword}, '%')) "
            + "</if>"
            + "<choose>"
            + "<when test='statusFilter == \"approved\"'>and d.status = 'approved' </when>"
            + "<when test='statusFilter == \"rejected\"'>and d.status in ('rejected','returned') </when>"
            + "</choose>"
            + "<choose>"
            + "<when test='scope == \"mine\"'>"
            + "and (exists (select 1 from audit_assignment aa where aa.declaration_id = d.id and aa.reviewer_id = #{reviewerId} and aa.status in ('approved','rejected','returned')) "
            + "or exists (select 1 from audit_record ar where ar.declaration_id = d.id and ar.reviewer_id = #{reviewerId})) "
            + "</when>"
            + "<otherwise>"
            + "and (exists (select 1 from audit_assignment aa where aa.declaration_id = d.id and aa.status in ('approved','rejected','returned')) "
            + "or exists (select 1 from audit_record ar where ar.declaration_id = d.id)) "
            + "</otherwise>"
            + "</choose>"
            + "</script>")
    long countFinishedAudit(@Param("scope") String scope,
                            @Param("statusFilter") String statusFilter,
                            @Param("reviewerId") String reviewerId,
                            @Param("batchId") String batchId,
                            @Param("keyword") String keyword);
}
