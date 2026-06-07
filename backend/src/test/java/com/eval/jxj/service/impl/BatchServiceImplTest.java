package com.eval.jxj.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eval.jxj.common.exception.BizException;
import com.eval.jxj.dto.request.BatchAssignmentGenerateRequest;
import com.eval.jxj.dto.request.BatchStatusUpdateRequest;
import com.eval.jxj.dto.response.BatchAssignmentGenerateVO;
import com.eval.jxj.dto.response.BatchEvaluationTableVO;
import com.eval.jxj.dto.response.BatchRankingVO;
import com.eval.jxj.dto.response.BatchVO;
import com.eval.jxj.entity.Award;
import com.eval.jxj.entity.AwardCategory;
import com.eval.jxj.entity.BatchBasicScore;
import com.eval.jxj.entity.Declaration;
import com.eval.jxj.entity.DeclarationItem;
import com.eval.jxj.entity.EvalBatch;
import com.eval.jxj.entity.EvalBatchCategory;
import com.eval.jxj.entity.SysUser;
import com.eval.jxj.mapper.AuditAssignmentMapper;
import com.eval.jxj.mapper.AwardCategoryMapper;
import com.eval.jxj.mapper.AwardLevelDefMapper;
import com.eval.jxj.mapper.AwardMapper;
import com.eval.jxj.mapper.BatchBasicScoreMapper;
import com.eval.jxj.mapper.BatchClassMapper;
import com.eval.jxj.mapper.BatchReviewerMapper;
import com.eval.jxj.mapper.DeclarationItemMapper;
import com.eval.jxj.mapper.DeclarationMapper;
import com.eval.jxj.mapper.EvalBatchCategoryMapper;
import com.eval.jxj.mapper.EvalBatchMapper;
import com.eval.jxj.mapper.SysUserMapper;
import com.eval.jxj.security.LoginUser;
import com.eval.jxj.service.BatchAssignmentService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchServiceImplTest {

    @Mock
    private EvalBatchMapper batchMapper;
    @Mock
    private EvalBatchCategoryMapper categoryMapper;
    @Mock
    private DeclarationMapper declarationMapper;
    @Mock
    private DeclarationItemMapper itemMapper;
    @Mock
    private AwardMapper awardMapper;
    @Mock
    private AwardCategoryMapper awardCategoryMapper;
    @Mock
    private AwardLevelDefMapper levelDefMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private BatchReviewerMapper batchReviewerMapper;
    @Mock
    private BatchClassMapper batchClassMapper;
    @Mock
    private AuditAssignmentMapper auditAssignmentMapper;
    @Mock
    private BatchAssignmentService assignmentService;
    @Mock
    private BatchBasicScoreMapper basicScoreMapper;
    @Mock
    private ScoreCalculationService scoreService;

    @InjectMocks
    private BatchServiceImpl service;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateStatus_reopensClosedBatchAndUpdatesEndDate() {
        EvalBatch batch = new EvalBatch();
        batch.setId("batch-1");
        batch.setStatus("closed");
        batch.setEndDate(LocalDate.of(2026, 6, 1));
        when(batchMapper.selectById("batch-1")).thenReturn(batch);

        BatchStatusUpdateRequest request = new BatchStatusUpdateRequest();
        request.setStatus("open");
        request.setEndDate(LocalDate.of(2026, 6, 30));

        service.updateStatus("batch-1", request);

        ArgumentCaptor<EvalBatch> captor = ArgumentCaptor.forClass(EvalBatch.class);
        verify(batchMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("open");
        assertThat(captor.getValue().getEndDate()).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    @Test
    void generateAssignments_delegatesSpecifiedReviewerRequest() {
        EvalBatch batch = new EvalBatch();
        batch.setId("batch-1");
        when(batchMapper.selectById("batch-1")).thenReturn(batch);

        BatchAssignmentGenerateRequest request = new BatchAssignmentGenerateRequest();
        request.setReviewerId("teacher-2");
        request.setCount(3);
        BatchAssignmentGenerateVO vo = new BatchAssignmentGenerateVO();
        vo.setDeclarationCount(5);
        vo.setAssignmentCount(3);
        when(assignmentService.generateForReviewer("batch-1", "teacher-2", 3)).thenReturn(vo);

        BatchAssignmentGenerateVO result = service.generateAssignments("batch-1", request);

        assertThat(result.getAssignmentCount()).isEqualTo(3);
        verify(assignmentService).generateForReviewer("batch-1", "teacher-2", 3);
    }

    @Test
    void generateAssignments_rejectsReplacePendingWithSpecifiedReviewer() {
        EvalBatch batch = new EvalBatch();
        batch.setId("batch-1");
        when(batchMapper.selectById("batch-1")).thenReturn(batch);

        BatchAssignmentGenerateRequest request = new BatchAssignmentGenerateRequest();
        request.setReplacePending(true);
        request.setReviewerId("teacher-2");
        request.setCount(3);

        assertThatThrownBy(() -> service.generateAssignments("batch-1", request))
                .isInstanceOf(BizException.class);
    }

    @Test
    void teacherListBatchesUsesReviewerScopedQuery() {
        setCurrentUser("teacher-1", "teacher");
        when(batchMapper.selectReviewerBatchPage(any(), eq("teacher-1"), isNull()))
                .thenReturn(new Page<>(1, 20, 0));

        service.listBatches(1, 20, null);

        verify(batchMapper).selectReviewerBatchPage(any(), eq("teacher-1"), isNull());
        verify(batchMapper, never()).selectPage(any(), any());
    }

    @Test
    void teacherCannotViewBatchWhenNotReviewer() {
        setCurrentUser("teacher-1", "teacher");
        EvalBatch batch = new EvalBatch();
        batch.setId("batch-1");
        when(batchMapper.selectById("batch-1")).thenReturn(batch);
        when(batchReviewerMapper.selectReviewerIdsByBatchId("batch-1")).thenReturn(List.of("teacher-2"));

        assertThatThrownBy(() -> service.getBatch("batch-1"))
                .isInstanceOf(BizException.class)
                .hasMessage("无权查看当前批次");
    }

    @Test
    void teacherCanViewBatchWhenReviewer() {
        setCurrentUser("teacher-1", "teacher");
        EvalBatch batch = new EvalBatch();
        batch.setId("batch-1");
        when(batchMapper.selectById("batch-1")).thenReturn(batch);
        when(batchReviewerMapper.selectReviewerIdsByBatchId("batch-1")).thenReturn(List.of("teacher-1"));
        when(declarationMapper.selectCount(any())).thenReturn(0L);
        when(auditAssignmentMapper.selectCount(any())).thenReturn(0L);

        BatchVO vo = service.getBatch("batch-1");

        assertThat(vo.getId()).isEqualTo("batch-1");
    }

    @Test
    void getRanking_calculatesDynamicCategoryScoresFromItemsAndBasicScoresWithCaps() {
        EvalBatch batch = new EvalBatch();
        batch.setId("batch-1");
        when(batchMapper.selectById("batch-1")).thenReturn(batch);

        Declaration declaration = declaration("decl-1", "student-1", "approved", "100.00");
        when(declarationMapper.selectList(any())).thenReturn(List.of(declaration));

        SysUser student = student("student-1", "2321911001", "Alice");
        when(userMapper.selectById("student-1")).thenReturn(student);

        when(categoryMapper.selectList(any())).thenReturn(List.of(
                batchCategory("research", "10"),
                batchCategory("service", null)
        ));

        when(itemMapper.selectList(any())).thenReturn(List.of(
                item("research", "award-r1", "7.00"),
                item("research", "award-r2", "8.00"),
                item("service", "award-s1", "2.00")
        ));

        when(awardMapper.selectBatchBasicAwards("batch-1")).thenReturn(List.of(
                award("basic-r", "research", "基础研究")
        ));
        when(basicScoreMapper.selectStudentScores("batch-1", "student-1")).thenReturn(List.of(
                basicScore("batch-1", "student-1", "basic-r", "5.00")
        ));

        List<BatchRankingVO> rankings = service.getRanking("batch-1");

        assertThat(rankings).hasSize(1);
        Map<String, BigDecimal> scores = rankings.get(0).getCategoryScores();
        assertThat(scores).containsEntry("research", new BigDecimal("10"));
        assertThat(scores).containsEntry("service", new BigDecimal("2.00"));
    }

    @Test
    void getEvaluationTableBuildsAwardCrossTableAndKeepsCustomItemsInSubtotalOnly() {
        EvalBatch batch = new EvalBatch();
        batch.setId("batch-1");
        when(batchMapper.selectById("batch-1")).thenReturn(batch);

        Declaration declaration = declaration("decl-1", "student-1", "approved", "88.00");
        when(declarationMapper.selectList(any())).thenReturn(List.of(declaration));

        SysUser student = student("student-1", "2321911001", "Alice");
        when(userMapper.selectById("student-1")).thenReturn(student);

        when(awardCategoryMapper.selectList(any())).thenReturn(List.of(
                category("research", "科研", "#1677ff", 1),
                category("service", "服务", "#52c41a", 2)
        ));
        when(awardMapper.selectList(any())).thenReturn(List.of(
                award("award-r", "research", "论文"),
                award("basic-r", "research", "基础分"),
                award("award-s", "service", "志愿")
        ));
        when(itemMapper.selectList(any())).thenReturn(List.of(
                item("research", "award-r", "4.00"),
                item("research", null, "6.00")
        ));
        when(basicScoreMapper.selectStudentScores("batch-1", "student-1")).thenReturn(List.of(
                basicScore("batch-1", "student-1", "basic-r", "5.00")
        ));

        BatchEvaluationTableVO table = service.getEvaluationTable("batch-1");

        assertThat(table.getCategories()).extracting(BatchEvaluationTableVO.CategoryColumn::getCode)
                .containsExactly("research", "service");
        assertThat(table.getRows()).hasSize(1);
        BatchEvaluationTableVO.StudentRow row = table.getRows().get(0);
        assertThat(row.getStudentLoginId()).isEqualTo("2321911001");
        assertThat(row.getScores()).containsEntry("award-r", new BigDecimal("4.00"));
        assertThat(row.getScores()).containsEntry("basic-r", new BigDecimal("5.00"));
        assertThat(row.getSubtotals()).containsEntry("research", new BigDecimal("15.00"));
    }

    @Test
    void getEvaluationTableCapsCategorySubtotalAtMaxScoreCap() {
        EvalBatch batch = new EvalBatch();
        batch.setId("batch-1");
        when(batchMapper.selectById("batch-1")).thenReturn(batch);

        Declaration declaration = declaration("decl-1", "student-1", "approved", "88.00");
        when(declarationMapper.selectList(any())).thenReturn(List.of(declaration));

        SysUser student = student("student-1", "2321911001", "Alice");
        when(userMapper.selectById("student-1")).thenReturn(student);

        when(awardCategoryMapper.selectList(any())).thenReturn(List.of(
                category("research", "科研", "#1677ff", 1)
        ));
        when(awardMapper.selectList(any())).thenReturn(List.of(
                award("award-r", "research", "论文"),
                award("basic-r", "research", "基础分")
        ));
        when(itemMapper.selectList(any())).thenReturn(List.of(
                item("research", "award-r", "4.00"),
                item("research", null, "6.00")
        ));
        when(basicScoreMapper.selectStudentScores("batch-1", "student-1")).thenReturn(List.of(
                basicScore("batch-1", "student-1", "basic-r", "5.00")
        ));
        // research 原始小计 4+6+5=15，封顶 12
        when(categoryMapper.selectList(any())).thenReturn(List.of(batchCategory("research", "12")));

        BatchEvaluationTableVO table = service.getEvaluationTable("batch-1");

        BatchEvaluationTableVO.StudentRow row = table.getRows().get(0);
        // 分类小计封顶到 12，逐奖项明细保持原值
        assertThat(row.getSubtotals()).containsEntry("research", new BigDecimal("12"));
        assertThat(row.getScores()).containsEntry("award-r", new BigDecimal("4.00"));
        assertThat(row.getScores()).containsEntry("basic-r", new BigDecimal("5.00"));
    }

    private Declaration declaration(String id, String studentId, String status, String totalScore) {
        Declaration declaration = new Declaration();
        declaration.setId(id);
        declaration.setBatchId("batch-1");
        declaration.setStudentId(studentId);
        declaration.setStatus(status);
        declaration.setTotalScore(new BigDecimal(totalScore));
        declaration.setSubmittedAt(LocalDateTime.of(2026, 6, 1, 9, 0));
        return declaration;
    }

    private SysUser student(String id, String loginId, String name) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setLoginId(loginId);
        user.setName(name);
        return user;
    }

    private EvalBatchCategory batchCategory(String category, String maxScoreCap) {
        EvalBatchCategory config = new EvalBatchCategory();
        config.setBatchId("batch-1");
        config.setCategory(category);
        config.setWeightPercent(new BigDecimal("100"));
        if (maxScoreCap != null) {
            config.setMaxScoreCap(new BigDecimal(maxScoreCap));
        }
        return config;
    }

    private DeclarationItem item(String category, String awardId, String finalScore) {
        DeclarationItem item = new DeclarationItem();
        item.setDeclarationId("decl-1");
        item.setCategory(category);
        item.setAwardId(awardId);
        item.setFinalScore(new BigDecimal(finalScore));
        return item;
    }

    private Award award(String id, String category, String name) {
        Award award = new Award();
        award.setId(id);
        award.setCategory(category);
        award.setName(name);
        award.setCreatedAt(LocalDateTime.of(2026, 6, 1, 8, 0));
        return award;
    }

    private AwardCategory category(String code, String name, String color, int sortOrder) {
        AwardCategory category = new AwardCategory();
        category.setCode(code);
        category.setName(name);
        category.setColor(color);
        category.setSortOrder(sortOrder);
        return category;
    }

    private static void setCurrentUser(String id, String role) {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(id);
        loginUser.setRole(role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
    }

    private BatchBasicScore basicScore(String batchId, String studentId, String awardId, String score) {
        BatchBasicScore basicScore = new BatchBasicScore();
        basicScore.setBatchId(batchId);
        basicScore.setStudentId(studentId);
        basicScore.setAwardId(awardId);
        basicScore.setScore(new BigDecimal(score));
        return basicScore;
    }
}
