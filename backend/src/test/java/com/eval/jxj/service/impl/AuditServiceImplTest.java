package com.eval.jxj.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eval.jxj.common.exception.BizException;
import com.eval.jxj.dto.request.AuditRequest;
import com.eval.jxj.dto.request.AuditCorrectionRequest;
import com.eval.jxj.dto.response.AuditQueueStatsVO;
import com.eval.jxj.dto.response.DeclarationVO;
import com.eval.jxj.entity.AuditAssignment;
import com.eval.jxj.entity.AuditRecord;
import com.eval.jxj.entity.Declaration;
import com.eval.jxj.entity.DeclarationItem;
import com.eval.jxj.entity.EvalBatch;
import com.eval.jxj.entity.SysUser;
import com.eval.jxj.mapper.AuditAssignmentMapper;
import com.eval.jxj.mapper.AuditRecordMapper;
import com.eval.jxj.mapper.DeclarationMapper;
import com.eval.jxj.mapper.DeclarationItemMapper;
import com.eval.jxj.mapper.EvalBatchMapper;
import com.eval.jxj.mapper.SysUserMapper;
import com.eval.jxj.security.LoginUser;
import com.eval.jxj.service.ScoreCalculationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceImplTest {

    @Mock
    private DeclarationMapper declarationMapper;
    @Mock
    private AuditRecordMapper auditRecordMapper;
    @Mock
    private AuditAssignmentMapper assignmentMapper;
    @Mock
    private DeclarationItemMapper itemMapper;
    @Mock
    private ScoreCalculationService scoreService;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private EvalBatchMapper batchMapper;

    @InjectMocks
    private AuditServiceImpl service;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void teacherPendingQueueAlwaysUsesMineScope() {
        setCurrentUser("teacher-1", "teacher");
        Page<Declaration> mapperPage = pageWith(declaration("decl-1", "student-1"));
        when(declarationMapper.selectAuditQueuePage(any(), eq("mine"), eq("teacher-1"), eq("batch-1"), eq("Alice")))
                .thenReturn(mapperPage);
        when(userMapper.selectById("student-1")).thenReturn(student("student-1", "Alice", "S001"));
        when(batchMapper.selectById("batch-1")).thenReturn(batch("batch-1", "2026 Spring"));
        when(auditRecordMapper.countByDeclarationId("decl-1")).thenReturn(0);
        when(assignmentMapper.countPendingByDeclarationId("decl-1")).thenReturn(1);

        Page<DeclarationVO> result = service.listPending("all", "batch-1", "Alice", 1, 10);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getStudentName()).isEqualTo("Alice");
        assertThat(result.getRecords().get(0).getStage()).isEqualTo("assigned");
    }

    @Test
    void teacherFinishedQueueAlwaysUsesMineScope() {
        setCurrentUser("teacher-1", "teacher");
        Declaration declaration = declaration("decl-1", "student-1");
        declaration.setStatus("approved");
        Page<Declaration> mapperPage = pageWith(declaration);
        when(declarationMapper.selectFinishedAuditPage(any(), eq("mine"), eq("teacher-1"), eq("batch-1"), eq("Alice")))
                .thenReturn(mapperPage);
        when(userMapper.selectById("student-1")).thenReturn(student("student-1", "Alice", "S001"));
        when(batchMapper.selectById("batch-1")).thenReturn(batch("batch-1", "2026 Spring"));

        Page<DeclarationVO> result = service.listFinished("all", "batch-1", "Alice", 1, 10);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getStudentName()).isEqualTo("Alice");
        assertThat(result.getRecords().get(0).getStage()).isEqualTo("approved");
    }

    @Test
    void adminPendingQueueCanUseAllAssignedAndUnassignedScopes() {
        setCurrentUser("admin-1", "admin");
        when(declarationMapper.selectAuditQueuePage(any(), eq("all"), eq("admin-1"), eq(null), eq(null)))
                .thenReturn(pageWith(declaration("decl-all", "student-1")));
        when(declarationMapper.selectAuditQueuePage(any(), eq("assigned"), eq("admin-1"), eq(null), eq(null)))
                .thenReturn(pageWith(declaration("decl-assigned", "student-1")));
        when(declarationMapper.selectAuditQueuePage(any(), eq("unassigned"), eq("admin-1"), eq(null), eq(null)))
                .thenReturn(pageWith(declaration("decl-unassigned", "student-1")));
        when(userMapper.selectById("student-1")).thenReturn(student("student-1", "Alice", "S001"));
        when(batchMapper.selectById("batch-1")).thenReturn(batch("batch-1", "2026 Spring"));
        when(auditRecordMapper.countByDeclarationId("decl-all")).thenReturn(0);
        when(auditRecordMapper.countByDeclarationId("decl-assigned")).thenReturn(0);
        when(assignmentMapper.countPendingByDeclarationId("decl-all")).thenReturn(1);
        when(assignmentMapper.countPendingByDeclarationId("decl-assigned")).thenReturn(1);
        when(assignmentMapper.countPendingByDeclarationId("decl-unassigned")).thenReturn(0);

        assertThat(service.listPending("all", null, null, 1, 10).getRecords().get(0).getId()).isEqualTo("decl-all");
        assertThat(service.listPending("assigned", null, null, 1, 10).getRecords().get(0).getId()).isEqualTo("decl-assigned");
        assertThat(service.listPending("unassigned", null, null, 1, 10).getRecords().get(0).getId()).isEqualTo("decl-unassigned");
    }

    @Test
    void adminStatsUsesSameKeywordAndBatchFiltersForAllScopes() {
        setCurrentUser("admin-1", "admin");
        when(declarationMapper.countAuditQueue("all", "admin-1", "batch-1", "Alice")).thenReturn(5L);
        when(declarationMapper.countAuditQueue("mine", "admin-1", "batch-1", "Alice")).thenReturn(2L);
        when(declarationMapper.countAuditQueue("assigned", "admin-1", "batch-1", "Alice")).thenReturn(3L);
        when(declarationMapper.countAuditQueue("unassigned", "admin-1", "batch-1", "Alice")).thenReturn(2L);
        when(declarationMapper.countFinishedAudit("all", null, "admin-1", "batch-1", "Alice")).thenReturn(8L);
        when(declarationMapper.countFinishedAudit("mine", null, "admin-1", "batch-1", "Alice")).thenReturn(4L);
        when(declarationMapper.countFinishedAudit("all", "approved", "admin-1", "batch-1", "Alice")).thenReturn(6L);
        when(declarationMapper.countFinishedAudit("all", "rejected", "admin-1", "batch-1", "Alice")).thenReturn(2L);

        AuditQueueStatsVO stats = service.getStats("batch-1", "Alice");

        assertThat(stats.getTotalSubmitted()).isEqualTo(5);
        assertThat(stats.getMyPending()).isEqualTo(2);
        assertThat(stats.getAssignedPending()).isEqualTo(3);
        assertThat(stats.getUnassigned()).isEqualTo(2);
        assertThat(stats.getFinishedTotal()).isEqualTo(8);
        assertThat(stats.getFinishedMine()).isEqualTo(4);
        assertThat(stats.getFinishedApproved()).isEqualTo(6);
        assertThat(stats.getFinishedRejected()).isEqualTo(2);
    }

    @Test
    void teacherStatsAreScopedToMineOnly() {
        setCurrentUser("teacher-1", "teacher");
        when(declarationMapper.countAuditQueue("mine", "teacher-1", "batch-1", "Alice")).thenReturn(3L);
        when(declarationMapper.countFinishedAudit("mine", null, "teacher-1", "batch-1", "Alice")).thenReturn(7L);
        when(declarationMapper.countFinishedAudit("mine", "approved", "teacher-1", "batch-1", "Alice")).thenReturn(5L);
        when(declarationMapper.countFinishedAudit("mine", "rejected", "teacher-1", "batch-1", "Alice")).thenReturn(2L);

        AuditQueueStatsVO stats = service.getStats("batch-1", "Alice");

        assertThat(stats.getTotalSubmitted()).isEqualTo(3);
        assertThat(stats.getMyPending()).isEqualTo(3);
        assertThat(stats.getAssignedPending()).isEqualTo(3);
        assertThat(stats.getUnassigned()).isEqualTo(0);
        assertThat(stats.getFinishedTotal()).isEqualTo(7);
        assertThat(stats.getFinishedMine()).isEqualTo(7);
        assertThat(stats.getFinishedApproved()).isEqualTo(5);
        assertThat(stats.getFinishedRejected()).isEqualTo(2);

        // 教师不得触发任何全局聚合查询
        verify(declarationMapper, never()).countAuditQueue(eq("all"), any(), any(), any());
        verify(declarationMapper, never()).countAuditQueue(eq("assigned"), any(), any(), any());
        verify(declarationMapper, never()).countAuditQueue(eq("unassigned"), any(), any(), any());
        verify(declarationMapper, never()).countFinishedAudit(eq("all"), any(), any(), any(), any());
    }

    @Test
    void keywordIsPassedToAuditQueueMapper() {
        setCurrentUser("admin-1", "admin");
        when(declarationMapper.selectAuditQueuePage(any(), eq("all"), eq("admin-1"), eq("batch-1"), eq("S001")))
                .thenReturn(pageWith(declaration("decl-1", "student-1")));

        service.listPending("all", "batch-1", "S001", 1, 10);

        ArgumentCaptor<String> keywordCaptor = ArgumentCaptor.forClass(String.class);
        verify(declarationMapper).selectAuditQueuePage(any(), eq("all"), eq("admin-1"), eq("batch-1"), keywordCaptor.capture());
        assertThat(keywordCaptor.getValue()).isEqualTo("S001");
    }

    @Test
    void teacherCannotCorrectDeclarationWithoutFinishedRelation() {
        setCurrentUser("teacher-1", "teacher");
        Declaration declaration = declaration("decl-1", "student-1");
        declaration.setStatus("approved");
        when(declarationMapper.selectById("decl-1")).thenReturn(declaration);
        when(assignmentMapper.selectByDeclarationAndReviewer("decl-1", "teacher-1")).thenReturn(null);
        when(auditRecordMapper.countByDeclarationAndReviewer("decl-1", "teacher-1")).thenReturn(0);

        AuditCorrectionRequest request = correctionRequest("approve", BigDecimal.valueOf(8));

        assertThatThrownBy(() -> service.correctAudit("decl-1", request))
                .isInstanceOf(BizException.class)
                .hasMessage("当前申报不属于你的已审核记录");
    }

    @Test
    void correctionUpdatesItemScoresRecomputesDeclarationAndRecordsSnapshot() {
        setCurrentUser("teacher-1", "teacher");
        Declaration declaration = declaration("decl-1", "student-1");
        declaration.setStatus("approved");
        declaration.setMoralityScore(BigDecimal.valueOf(10));
        declaration.setAbilityScore(BigDecimal.valueOf(20));
        declaration.setSportsScore(BigDecimal.valueOf(30));
        declaration.setTotalScore(BigDecimal.valueOf(60));
        DeclarationItem item = item("item-1", "decl-1", BigDecimal.valueOf(5));
        AuditAssignment assignment = assignment("assign-1", "decl-1", "teacher-1", "approved");
        when(declarationMapper.selectById("decl-1")).thenReturn(declaration);
        when(assignmentMapper.selectByDeclarationAndReviewer("decl-1", "teacher-1")).thenReturn(assignment);
        when(itemMapper.selectById("item-1")).thenReturn(item);

        AuditCorrectionRequest request = correctionRequest("approve", BigDecimal.valueOf(8));
        request.setComment("修正分数");

        service.correctAudit("decl-1", request);

        assertThat(item.getFinalScore()).isEqualByComparingTo("8");
        verify(itemMapper).updateById(item);
        verify(scoreService).computeDeclarationScore(declaration);
        ArgumentCaptor<AuditRecord> recordCaptor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditRecordMapper).insert(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getAction()).isEqualTo("correction_approve");
        assertThat(recordCaptor.getValue().getComment()).isEqualTo("修正分数");
        assertThat(recordCaptor.getValue().getSnapshotScores()).contains("\"before\"");
        assertThat(recordCaptor.getValue().getSnapshotScores()).contains("\"after\"");
    }

    @Test
    void correctionRejectUpdatesDeclarationStatusAndCancelsPendingAssignments() {
        setCurrentUser("teacher-1", "teacher");
        Declaration declaration = declaration("decl-1", "student-1");
        declaration.setStatus("approved");
        AuditAssignment assignment = assignment("assign-1", "decl-1", "teacher-1", "approved");
        when(declarationMapper.selectById("decl-1")).thenReturn(declaration);
        when(assignmentMapper.selectByDeclarationAndReviewer("decl-1", "teacher-1")).thenReturn(assignment);
        when(itemMapper.selectById("item-1")).thenReturn(item("item-1", "decl-1", BigDecimal.valueOf(5)));

        service.correctAudit("decl-1", correctionRequest("reject", BigDecimal.valueOf(8)));

        assertThat(assignment.getStatus()).isEqualTo("rejected");
        assertThat(declaration.getStatus()).isEqualTo("rejected");
        verify(assignmentMapper).cancelPendingByDeclarationId("decl-1");
        verify(declarationMapper).updateById(declaration);
    }

    @Test
    void correctionApproveKeepsDeclarationSubmittedWhenOtherAssignmentsArePending() {
        setCurrentUser("teacher-1", "teacher");
        Declaration declaration = declaration("decl-1", "student-1");
        declaration.setStatus("approved");
        AuditAssignment assignment = assignment("assign-1", "decl-1", "teacher-1", "approved");
        when(declarationMapper.selectById("decl-1")).thenReturn(declaration);
        when(assignmentMapper.selectByDeclarationAndReviewer("decl-1", "teacher-1")).thenReturn(assignment);
        when(itemMapper.selectById("item-1")).thenReturn(item("item-1", "decl-1", BigDecimal.valueOf(5)));
        when(assignmentMapper.countPendingByDeclarationId("decl-1")).thenReturn(1);

        service.correctAudit("decl-1", correctionRequest("approve", BigDecimal.valueOf(8)));

        assertThat(assignment.getStatus()).isEqualTo("approved");
        assertThat(declaration.getStatus()).isEqualTo("submitted");
        verify(declarationMapper).updateById(declaration);
    }

    @Test
    void adminCanAuditSpecificDeclarationWithoutAssignment() {
        setCurrentUser("admin-1", "admin");
        Declaration declaration = declaration("decl-1", "student-1");
        declaration.setMoralityScore(BigDecimal.valueOf(10));
        declaration.setAbilityScore(BigDecimal.valueOf(20));
        declaration.setSportsScore(BigDecimal.valueOf(30));
        declaration.setTotalScore(BigDecimal.valueOf(60));
        when(declarationMapper.selectById("decl-1")).thenReturn(declaration);
        when(assignmentMapper.selectPendingByDeclarationAndReviewer("decl-1", "admin-1")).thenReturn(null);

        AuditRequest request = new AuditRequest();
        request.setAction("approve");
        request.setComment("管理员抽审通过");

        service.audit("decl-1", request);

        ArgumentCaptor<AuditRecord> recordCaptor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditRecordMapper).insert(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getReviewerId()).isEqualTo("admin-1");
        assertThat(declaration.getStatus()).isEqualTo("approved");
        verify(declarationMapper).updateById(declaration);
    }

    @Test
    void auditAppliesItemScoreAdjustmentsAndRecomputes() {
        setCurrentUser("admin-1", "admin");
        Declaration declaration = declaration("decl-1", "student-1");
        declaration.setMoralityScore(BigDecimal.valueOf(10));
        declaration.setAbilityScore(BigDecimal.valueOf(20));
        declaration.setSportsScore(BigDecimal.valueOf(30));
        declaration.setTotalScore(BigDecimal.valueOf(60));
        DeclarationItem item = item("item-1", "decl-1", BigDecimal.valueOf(5));
        when(declarationMapper.selectById("decl-1")).thenReturn(declaration);
        when(assignmentMapper.selectPendingByDeclarationAndReviewer("decl-1", "admin-1")).thenReturn(null);
        when(itemMapper.selectById("item-1")).thenReturn(item);

        AuditRequest request = new AuditRequest();
        request.setAction("approve");
        AuditCorrectionRequest.ItemScoreAdjustment adjustment = new AuditCorrectionRequest.ItemScoreAdjustment();
        adjustment.setItemId("item-1");
        adjustment.setFinalScore(BigDecimal.valueOf(9));
        request.setItemScoreAdjustments(List.of(adjustment));

        service.audit("decl-1", request);

        assertThat(item.getFinalScore()).isEqualByComparingTo("9");
        verify(itemMapper).updateById(item);
        verify(scoreService).computeDeclarationScore(declaration);
        assertThat(declaration.getStatus()).isEqualTo("approved");
        verify(declarationMapper).updateById(declaration);
    }

    @Test
    void teacherCannotAuditDeclarationWithoutAssignment() {
        setCurrentUser("teacher-1", "teacher");
        Declaration declaration = declaration("decl-1", "student-1");
        when(declarationMapper.selectById("decl-1")).thenReturn(declaration);
        when(assignmentMapper.selectPendingByDeclarationAndReviewer("decl-1", "teacher-1")).thenReturn(null);

        AuditRequest request = new AuditRequest();
        request.setAction("approve");

        assertThatThrownBy(() -> service.audit("decl-1", request))
                .isInstanceOf(BizException.class)
                .hasMessage("当前申报未分配给你审核");
    }

    private static Page<Declaration> pageWith(Declaration declaration) {
        Page<Declaration> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(declaration));
        return page;
    }

    private static Declaration declaration(String id, String studentId) {
        Declaration declaration = new Declaration();
        declaration.setId(id);
        declaration.setBatchId("batch-1");
        declaration.setStudentId(studentId);
        declaration.setStatus("submitted");
        return declaration;
    }

    private static SysUser student(String id, String name, String loginId) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setName(name);
        user.setLoginId(loginId);
        return user;
    }

    private static EvalBatch batch(String id, String name) {
        EvalBatch batch = new EvalBatch();
        batch.setId(id);
        batch.setName(name);
        return batch;
    }

    private static DeclarationItem item(String id, String declarationId, BigDecimal finalScore) {
        DeclarationItem item = new DeclarationItem();
        item.setId(id);
        item.setDeclarationId(declarationId);
        item.setFinalScore(finalScore);
        return item;
    }

    private static AuditAssignment assignment(String id, String declarationId, String reviewerId, String status) {
        AuditAssignment assignment = new AuditAssignment();
        assignment.setId(id);
        assignment.setDeclarationId(declarationId);
        assignment.setBatchId("batch-1");
        assignment.setReviewerId(reviewerId);
        assignment.setStatus(status);
        return assignment;
    }

    private static AuditCorrectionRequest correctionRequest(String action, BigDecimal finalScore) {
        AuditCorrectionRequest request = new AuditCorrectionRequest();
        request.setAction(action);
        AuditCorrectionRequest.ItemScoreAdjustment adjustment = new AuditCorrectionRequest.ItemScoreAdjustment();
        adjustment.setItemId("item-1");
        adjustment.setFinalScore(finalScore);
        request.setItemScoreAdjustments(List.of(adjustment));
        return request;
    }

    private static void setCurrentUser(String id, String role) {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(id);
        loginUser.setRole(role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
    }
}
