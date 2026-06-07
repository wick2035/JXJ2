package com.eval.jxj.service.impl;

import com.eval.jxj.entity.AuditAssignment;
import com.eval.jxj.entity.Declaration;
import com.eval.jxj.entity.EvalBatch;
import com.eval.jxj.dto.response.BatchAssignmentGenerateVO;
import com.eval.jxj.mapper.AuditAssignmentMapper;
import com.eval.jxj.mapper.BatchReviewerMapper;
import com.eval.jxj.mapper.DeclarationMapper;
import com.eval.jxj.mapper.EvalBatchMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchAssignmentServiceImplTest {

    @Mock
    private DeclarationMapper declarationMapper;
    @Mock
    private EvalBatchMapper batchMapper;
    @Mock
    private BatchReviewerMapper batchReviewerMapper;
    @Mock
    private AuditAssignmentMapper assignmentMapper;

    @InjectMocks
    private BatchAssignmentServiceImpl service;

    @Test
    void generateForBatch_evenlyDistributesSingleReviewerAssignments() {
        EvalBatch batch = batch(1);
        when(batchMapper.selectById("batch-1")).thenReturn(batch);
        when(declarationMapper.selectList(any())).thenReturn(Arrays.asList(
                declaration("decl-1"),
                declaration("decl-2"),
                declaration("decl-3"),
                declaration("decl-4"),
                declaration("decl-5"),
                declaration("decl-6")
        ));
        when(batchReviewerMapper.selectReviewerIdsByBatchId("batch-1"))
                .thenReturn(Arrays.asList("teacher-1", "teacher-2", "teacher-3"));
        when(assignmentMapper.selectList(any())).thenReturn(Collections.emptyList());

        BatchAssignmentGenerateVO result = service.generateForBatch("batch-1", false);

        ArgumentCaptor<AuditAssignment> captor = ArgumentCaptor.forClass(AuditAssignment.class);
        verify(assignmentMapper, times(6)).insert(captor.capture());
        Map<String, Long> counts = captor.getAllValues().stream()
                .collect(Collectors.groupingBy(AuditAssignment::getReviewerId, Collectors.counting()));

        assertThat(result.getDeclarationCount()).isEqualTo(6);
        assertThat(result.getAssignmentCount()).isEqualTo(6);
        assertThat(counts).containsEntry("teacher-1", 2L)
                .containsEntry("teacher-2", 2L)
                .containsEntry("teacher-3", 2L);
    }

    @Test
    void generateForBatch_assignsDistinctReviewersPerDeclaration() {
        EvalBatch batch = batch(2);
        when(batchMapper.selectById("batch-1")).thenReturn(batch);
        when(declarationMapper.selectList(any())).thenReturn(List.of(declaration("decl-1")));
        when(batchReviewerMapper.selectReviewerIdsByBatchId("batch-1"))
                .thenReturn(Arrays.asList("teacher-1", "teacher-2", "teacher-3"));
        when(assignmentMapper.selectList(any())).thenReturn(Collections.emptyList());

        service.generateForBatch("batch-1", false);

        ArgumentCaptor<AuditAssignment> captor = ArgumentCaptor.forClass(AuditAssignment.class);
        verify(assignmentMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AuditAssignment::getDeclarationId)
                .containsOnly("decl-1");
        assertThat(captor.getAllValues())
                .extracting(AuditAssignment::getReviewerId)
                .doesNotHaveDuplicates();
    }

    @Test
    void generateForReviewer_assignsRequestedCountOnlyToSpecifiedReviewer() {
        EvalBatch batch = batch(1);
        when(batchMapper.selectById("batch-1")).thenReturn(batch);
        when(declarationMapper.selectList(any())).thenReturn(Arrays.asList(
                declaration("decl-1"),
                declaration("decl-2"),
                declaration("decl-3"),
                declaration("decl-4")
        ));
        when(batchReviewerMapper.selectReviewerIdsByBatchId("batch-1"))
                .thenReturn(Arrays.asList("teacher-1", "teacher-2"));
        when(assignmentMapper.selectList(any())).thenReturn(List.of(
                assignment("decl-4", "batch-1", "teacher-1", "pending")
        ));

        BatchAssignmentGenerateVO result = service.generateForReviewer("batch-1", "teacher-2", 3);

        ArgumentCaptor<AuditAssignment> captor = ArgumentCaptor.forClass(AuditAssignment.class);
        verify(assignmentMapper, times(3)).insert(captor.capture());
        assertThat(result.getAssignmentCount()).isEqualTo(3);
        assertThat(captor.getAllValues())
                .extracting(AuditAssignment::getReviewerId)
                .containsOnly("teacher-2");
        assertThat(captor.getAllValues())
                .extracting(AuditAssignment::getDeclarationId)
                .doesNotContain("decl-4");
    }

    @Test
    void generateForDeclaration_restoresCancelledAssignmentAfterResubmit() {
        EvalBatch batch = batch(1);
        Declaration declaration = declaration("decl-1");
        when(declarationMapper.selectById("decl-1")).thenReturn(declaration);
        when(batchMapper.selectById("batch-1")).thenReturn(batch);
        when(batchReviewerMapper.selectReviewerIdsByBatchId("batch-1")).thenReturn(List.of("teacher-1"));
        when(assignmentMapper.selectList(any())).thenReturn(List.of(
                assignment("decl-1", "batch-1", "teacher-1", "pending")
        ));

        int assignmentCount = service.generateForDeclaration("decl-1", false);

        verify(assignmentMapper).restoreCancelledByDeclarationId("decl-1");
        verify(assignmentMapper, never()).insert(any(AuditAssignment.class));
        assertThat(assignmentCount).isZero();
    }

    @Test
    void generateForDeclaration_restoresReturnedAssignmentAfterResubmit() {
        EvalBatch batch = batch(1);
        Declaration declaration = declaration("decl-1");
        when(declarationMapper.selectById("decl-1")).thenReturn(declaration);
        when(batchMapper.selectById("batch-1")).thenReturn(batch);
        when(batchReviewerMapper.selectReviewerIdsByBatchId("batch-1")).thenReturn(List.of("teacher-1"));
        when(assignmentMapper.selectList(any())).thenReturn(List.of(
                assignment("decl-1", "batch-1", "teacher-1", "returned")
        ));

        int assignmentCount = service.generateForDeclaration("decl-1", false);

        verify(assignmentMapper).restoreReturnedByDeclarationId("decl-1");
        verify(assignmentMapper, never()).insert(any(AuditAssignment.class));
        assertThat(assignmentCount).isZero();
    }

    @Test
    void generateForBatch_replacePendingKeepsFinishedAssignmentsAndFillsRemainingSlots() {
        EvalBatch batch = batch(2);
        when(batchMapper.selectById("batch-1")).thenReturn(batch);
        when(declarationMapper.selectList(any())).thenReturn(List.of(declaration("decl-1")));
        when(batchReviewerMapper.selectReviewerIdsByBatchId("batch-1"))
                .thenReturn(Arrays.asList("teacher-1", "teacher-2", "teacher-3"));
        when(assignmentMapper.selectList(any())).thenReturn(List.of(
                assignment("decl-1", "batch-1", "teacher-1", "approved")
        ));

        BatchAssignmentGenerateVO result = service.generateForBatch("batch-1", true);

        verify(assignmentMapper).delete(any());
        ArgumentCaptor<AuditAssignment> captor = ArgumentCaptor.forClass(AuditAssignment.class);
        verify(assignmentMapper).insert(captor.capture());
        assertThat(result.getAssignmentCount()).isEqualTo(1);
        assertThat(captor.getValue().getDeclarationId()).isEqualTo("decl-1");
        assertThat(captor.getValue().getReviewerId()).isNotEqualTo("teacher-1");
    }

    private static EvalBatch batch(int reviewerCount) {
        EvalBatch batch = new EvalBatch();
        batch.setId("batch-1");
        batch.setReviewerCount(reviewerCount);
        return batch;
    }

    private static Declaration declaration(String id) {
        Declaration declaration = new Declaration();
        declaration.setId(id);
        declaration.setBatchId("batch-1");
        declaration.setStatus("submitted");
        return declaration;
    }

    private static AuditAssignment assignment(String declarationId, String batchId, String reviewerId, String status) {
        AuditAssignment assignment = new AuditAssignment();
        assignment.setDeclarationId(declarationId);
        assignment.setBatchId(batchId);
        assignment.setReviewerId(reviewerId);
        assignment.setStatus(status);
        return assignment;
    }
}
