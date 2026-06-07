package com.eval.jxj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eval.jxj.common.exception.BizException;
import com.eval.jxj.dto.response.BatchAssignmentGenerateVO;
import com.eval.jxj.entity.AuditAssignment;
import com.eval.jxj.entity.Declaration;
import com.eval.jxj.entity.EvalBatch;
import com.eval.jxj.mapper.AuditAssignmentMapper;
import com.eval.jxj.mapper.BatchReviewerMapper;
import com.eval.jxj.mapper.DeclarationMapper;
import com.eval.jxj.mapper.EvalBatchMapper;
import com.eval.jxj.service.BatchAssignmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BatchAssignmentServiceImpl implements BatchAssignmentService {

    private final DeclarationMapper declarationMapper;
    private final EvalBatchMapper batchMapper;
    private final BatchReviewerMapper batchReviewerMapper;
    private final AuditAssignmentMapper assignmentMapper;
    private final Random random = new Random();

    public BatchAssignmentServiceImpl(DeclarationMapper declarationMapper,
                                      EvalBatchMapper batchMapper,
                                      BatchReviewerMapper batchReviewerMapper,
                                      AuditAssignmentMapper assignmentMapper) {
        this.declarationMapper = declarationMapper;
        this.batchMapper = batchMapper;
        this.batchReviewerMapper = batchReviewerMapper;
        this.assignmentMapper = assignmentMapper;
    }

    @Override
    @Transactional
    public int generateForDeclaration(String declarationId, boolean replacePending) {
        Declaration declaration = declarationMapper.selectById(declarationId);
        if (declaration == null) throw new BizException("申报记录不存在");
        EvalBatch batch = batchMapper.selectById(declaration.getBatchId());
        if (batch == null) throw new BizException("批次不存在");

        if (replacePending) {
            assignmentMapper.delete(new LambdaQueryWrapper<AuditAssignment>()
                    .eq(AuditAssignment::getDeclarationId, declarationId)
                    .eq(AuditAssignment::getStatus, "pending"));
        }
        assignmentMapper.restoreCancelledByDeclarationId(declarationId);
        assignmentMapper.restoreReturnedByDeclarationId(declarationId);

        return assignAutomatically(batch, List.of(declaration)).getAssignmentCount();
    }

    @Override
    @Transactional
    public BatchAssignmentGenerateVO generateForBatch(String batchId, boolean replacePending) {
        EvalBatch batch = batchMapper.selectById(batchId);
        if (batch == null) throw new BizException("批次不存在");

        if (replacePending) {
            assignmentMapper.delete(new LambdaQueryWrapper<AuditAssignment>()
                    .eq(AuditAssignment::getBatchId, batchId)
                    .eq(AuditAssignment::getStatus, "pending"));
        }

        List<Declaration> declarations = declarationMapper.selectList(new LambdaQueryWrapper<Declaration>()
                .eq(Declaration::getBatchId, batchId)
                .eq(Declaration::getStatus, "submitted"));
        declarations.forEach(declaration -> {
            assignmentMapper.restoreCancelledByDeclarationId(declaration.getId());
            assignmentMapper.restoreReturnedByDeclarationId(declaration.getId());
        });

        return assignAutomatically(batch, declarations);
    }

    @Override
    @Transactional
    public BatchAssignmentGenerateVO generateForReviewer(String batchId, String reviewerId, int count) {
        if (count <= 0) {
            throw new BizException("指定分配份数必须大于 0");
        }
        EvalBatch batch = batchMapper.selectById(batchId);
        if (batch == null) throw new BizException("批次不存在");

        List<String> reviewerIds = selectReviewerIds(batchId);
        int reviewerCount = normalizeReviewerCount(batch, reviewerIds);
        if (!reviewerIds.contains(reviewerId)) {
            throw new BizException("指定审核人不属于该批次");
        }

        List<Declaration> declarations = declarationMapper.selectList(new LambdaQueryWrapper<Declaration>()
                .eq(Declaration::getBatchId, batchId)
                .eq(Declaration::getStatus, "submitted"));
        List<AuditAssignment> existingAssignments = selectAssignments(batchId);
        Map<String, List<AuditAssignment>> assignmentsByDeclaration = groupByDeclaration(existingAssignments);

        List<Declaration> shuffledDeclarations = new ArrayList<>(declarations);
        Collections.shuffle(shuffledDeclarations, random);

        int assignmentCount = 0;
        for (Declaration declaration : shuffledDeclarations) {
            if (assignmentCount >= count) {
                break;
            }
            List<AuditAssignment> declarationAssignments = assignmentsByDeclaration
                    .computeIfAbsent(declaration.getId(), key -> new ArrayList<>());
            if (activeAssignmentCount(declarationAssignments) >= reviewerCount) {
                continue;
            }
            if (hasReviewerEverAssigned(declarationAssignments, reviewerId)) {
                continue;
            }

            AuditAssignment assignment = insertAssignment(declaration, reviewerId);
            declarationAssignments.add(assignment);
            assignmentCount++;
        }

        BatchAssignmentGenerateVO vo = new BatchAssignmentGenerateVO();
        vo.setDeclarationCount(declarations.size());
        vo.setAssignmentCount(assignmentCount);
        return vo;
    }

    private BatchAssignmentGenerateVO assignAutomatically(EvalBatch batch, List<Declaration> declarations) {
        List<String> reviewerIds = selectReviewerIds(batch.getId());
        if (reviewerIds.isEmpty()) {
            BatchAssignmentGenerateVO vo = new BatchAssignmentGenerateVO();
            vo.setDeclarationCount(declarations.size());
            vo.setAssignmentCount(0);
            return vo;
        }
        int reviewerCount = normalizeReviewerCount(batch, reviewerIds);
        List<AuditAssignment> existingAssignments = selectAssignments(batch.getId());
        Map<String, List<AuditAssignment>> assignmentsByDeclaration = groupByDeclaration(existingAssignments);
        Map<String, Integer> reviewerLoads = buildReviewerLoads(reviewerIds, existingAssignments);

        List<Declaration> shuffledDeclarations = new ArrayList<>(declarations);
        Collections.shuffle(shuffledDeclarations, random);

        int assignmentCount = 0;
        for (Declaration declaration : shuffledDeclarations) {
            List<AuditAssignment> declarationAssignments = assignmentsByDeclaration
                    .computeIfAbsent(declaration.getId(), key -> new ArrayList<>());
            while (activeAssignmentCount(declarationAssignments) < reviewerCount) {
                List<String> candidates = reviewerIds.stream()
                        .filter(reviewerId -> !hasReviewerEverAssigned(declarationAssignments, reviewerId))
                        .collect(Collectors.toList());
                if (candidates.isEmpty()) {
                    break;
                }
                String selectedReviewerId = selectLeastLoadedRandomReviewer(candidates, reviewerLoads);
                AuditAssignment assignment = insertAssignment(declaration, selectedReviewerId);
                declarationAssignments.add(assignment);
                reviewerLoads.put(selectedReviewerId, reviewerLoads.getOrDefault(selectedReviewerId, 0) + 1);
                assignmentCount++;
            }
        }

        BatchAssignmentGenerateVO vo = new BatchAssignmentGenerateVO();
        vo.setDeclarationCount(declarations.size());
        vo.setAssignmentCount(assignmentCount);
        return vo;
    }

    private List<String> selectReviewerIds(String batchId) {
        List<String> reviewerIds = batchReviewerMapper.selectReviewerIdsByBatchId(batchId);
        if (reviewerIds == null) {
            return Collections.emptyList();
        }
        return reviewerIds.stream()
                .filter(id -> id != null && !id.trim().isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    private int normalizeReviewerCount(EvalBatch batch, List<String> reviewerIds) {
        int reviewerCount = batch.getReviewerCount() == null ? 1 : batch.getReviewerCount();
        if (reviewerCount <= 0) reviewerCount = 1;
        if (reviewerCount > reviewerIds.size()) {
            throw new BizException("每份申报审核人数不能超过批次审核人数量");
        }
        return reviewerCount;
    }

    private List<AuditAssignment> selectAssignments(String batchId) {
        return assignmentMapper.selectList(new LambdaQueryWrapper<AuditAssignment>()
                .eq(AuditAssignment::getBatchId, batchId));
    }

    private Map<String, List<AuditAssignment>> groupByDeclaration(List<AuditAssignment> assignments) {
        Map<String, List<AuditAssignment>> grouped = new HashMap<>();
        for (AuditAssignment assignment : assignments) {
            grouped.computeIfAbsent(assignment.getDeclarationId(), key -> new ArrayList<>()).add(assignment);
        }
        return grouped;
    }

    private Map<String, Integer> buildReviewerLoads(List<String> reviewerIds, List<AuditAssignment> assignments) {
        Set<String> configuredReviewers = new HashSet<>(reviewerIds);
        Map<String, Integer> loads = new HashMap<>();
        for (String reviewerId : reviewerIds) {
            loads.put(reviewerId, 0);
        }
        for (AuditAssignment assignment : assignments) {
            if (!configuredReviewers.contains(assignment.getReviewerId()) || isCancelled(assignment)) {
                continue;
            }
            loads.put(assignment.getReviewerId(), loads.getOrDefault(assignment.getReviewerId(), 0) + 1);
        }
        return loads;
    }

    private int activeAssignmentCount(List<AuditAssignment> assignments) {
        return (int) assignments.stream().filter(assignment -> !isCancelled(assignment)).count();
    }

    private boolean hasReviewerEverAssigned(List<AuditAssignment> assignments, String reviewerId) {
        return assignments.stream().anyMatch(assignment ->
                reviewerId.equals(assignment.getReviewerId()) && !isCancelled(assignment));
    }

    private boolean isCancelled(AuditAssignment assignment) {
        return "cancelled".equals(assignment.getStatus());
    }

    private String selectLeastLoadedRandomReviewer(List<String> candidates, Map<String, Integer> reviewerLoads) {
        int minLoad = candidates.stream()
                .mapToInt(reviewerId -> reviewerLoads.getOrDefault(reviewerId, 0))
                .min()
                .orElse(0);
        List<String> leastLoaded = candidates.stream()
                .filter(reviewerId -> reviewerLoads.getOrDefault(reviewerId, 0) == minLoad)
                .collect(Collectors.toList());
        Collections.shuffle(leastLoaded, random);
        return leastLoaded.get(0);
    }

    private AuditAssignment insertAssignment(Declaration declaration, String reviewerId) {
        AuditAssignment assignment = new AuditAssignment();
        assignment.setDeclarationId(declaration.getId());
        assignment.setBatchId(declaration.getBatchId());
        assignment.setReviewerId(reviewerId);
        assignment.setStatus("pending");
        assignmentMapper.insert(assignment);
        return assignment;
    }
}
