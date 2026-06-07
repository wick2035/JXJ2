package com.eval.jxj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eval.jxj.common.exception.BizException;
import com.eval.jxj.dto.request.AuditCorrectionRequest;
import com.eval.jxj.dto.request.AuditRequest;
import com.eval.jxj.dto.response.AuditQueueStatsVO;
import com.eval.jxj.dto.response.DeclarationVO;
import com.eval.jxj.entity.AuditAssignment;
import com.eval.jxj.entity.AuditRecord;
import com.eval.jxj.entity.Declaration;
import com.eval.jxj.entity.DeclarationItem;
import com.eval.jxj.entity.SysUser;
import com.eval.jxj.entity.EvalBatch;
import com.eval.jxj.mapper.AuditAssignmentMapper;
import com.eval.jxj.mapper.AuditRecordMapper;
import com.eval.jxj.mapper.DeclarationMapper;
import com.eval.jxj.mapper.DeclarationItemMapper;
import com.eval.jxj.mapper.SysUserMapper;
import com.eval.jxj.mapper.EvalBatchMapper;
import com.eval.jxj.service.AuditService;
import com.eval.jxj.service.ScoreCalculationService;
import com.eval.jxj.util.SecurityUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AuditServiceImpl implements AuditService {

    private final DeclarationMapper declarationMapper;
    private final AuditRecordMapper auditRecordMapper;
    private final AuditAssignmentMapper assignmentMapper;
    private final DeclarationItemMapper itemMapper;
    private final ScoreCalculationService scoreService;
    private final SysUserMapper userMapper;
    private final EvalBatchMapper batchMapper;

    public AuditServiceImpl(DeclarationMapper declarationMapper, AuditRecordMapper auditRecordMapper,
                            AuditAssignmentMapper assignmentMapper, DeclarationItemMapper itemMapper,
                            ScoreCalculationService scoreService, SysUserMapper userMapper,
                            EvalBatchMapper batchMapper) {
        this.declarationMapper = declarationMapper;
        this.auditRecordMapper = auditRecordMapper;
        this.assignmentMapper = assignmentMapper;
        this.itemMapper = itemMapper;
        this.scoreService = scoreService;
        this.userMapper = userMapper;
        this.batchMapper = batchMapper;
    }

    @Override
    public Page<DeclarationVO> listPending(String scope, String batchId, String keyword, int page, int size) {
        String reviewerId = SecurityUtil.getCurrentUserId();
        String normalizedScope = normalizeScope(scope);
        Page<Declaration> result = declarationMapper.selectAuditQueuePage(
                new Page<>(page, size), normalizedScope, reviewerId, batchId, keyword);
        Page<DeclarationVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toQueueVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public Page<DeclarationVO> listFinished(String scope, String batchId, String keyword, int page, int size) {
        String reviewerId = SecurityUtil.getCurrentUserId();
        String normalizedScope = normalizeScope(scope);
        Page<Declaration> result = declarationMapper.selectFinishedAuditPage(
                new Page<>(page, size), normalizedScope, reviewerId, batchId, keyword);
        Page<DeclarationVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toQueueVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public AuditQueueStatsVO getStats(String batchId, String keyword) {
        String reviewerId = SecurityUtil.getCurrentUserId();
        AuditQueueStatsVO stats = new AuditQueueStatsVO();
        if ("teacher".equals(SecurityUtil.getCurrentRole())) {
            // 教师只能看到与自己相关的统计，不泄露全局聚合数据
            long myPending = declarationMapper.countAuditQueue("mine", reviewerId, batchId, keyword);
            long myFinished = declarationMapper.countFinishedAudit("mine", null, reviewerId, batchId, keyword);
            stats.setTotalSubmitted(myPending);
            stats.setMyPending(myPending);
            stats.setAssignedPending(myPending);
            stats.setUnassigned(0L);
            stats.setFinishedTotal(myFinished);
            stats.setFinishedMine(myFinished);
            stats.setFinishedApproved(declarationMapper.countFinishedAudit("mine", "approved", reviewerId, batchId, keyword));
            stats.setFinishedRejected(declarationMapper.countFinishedAudit("mine", "rejected", reviewerId, batchId, keyword));
            return stats;
        }
        stats.setTotalSubmitted(declarationMapper.countAuditQueue("all", reviewerId, batchId, keyword));
        stats.setMyPending(declarationMapper.countAuditQueue("mine", reviewerId, batchId, keyword));
        stats.setAssignedPending(declarationMapper.countAuditQueue("assigned", reviewerId, batchId, keyword));
        stats.setUnassigned(declarationMapper.countAuditQueue("unassigned", reviewerId, batchId, keyword));
        stats.setFinishedTotal(declarationMapper.countFinishedAudit("all", null, reviewerId, batchId, keyword));
        stats.setFinishedMine(declarationMapper.countFinishedAudit("mine", null, reviewerId, batchId, keyword));
        stats.setFinishedApproved(declarationMapper.countFinishedAudit("all", "approved", reviewerId, batchId, keyword));
        stats.setFinishedRejected(declarationMapper.countFinishedAudit("all", "rejected", reviewerId, batchId, keyword));
        return stats;
    }

    @Override
    @Transactional
    public void audit(String declarationId, AuditRequest request) {
        Declaration decl = declarationMapper.selectById(declarationId);
        if (decl == null) throw new BizException("申报记录不存在");
        if (!"submitted".equals(decl.getStatus())) {
            throw new BizException("只能审核已提交的记录");
        }

        String reviewerId = SecurityUtil.getCurrentUserId();
        String role = SecurityUtil.getCurrentRole();
        AuditAssignment assignment = assignmentMapper.selectPendingByDeclarationAndReviewer(declarationId, reviewerId);
        if ("teacher".equals(role) && assignment == null) {
            throw new BizException("当前申报未分配给你审核");
        }

        // 第一次审核也允许审核员调整明细分数（与修正审核同一套应用+重算逻辑）
        applyItemAdjustments(decl, request.getItemScoreAdjustments());
        scoreService.computeDeclarationScore(decl);

        // Create audit record
        AuditRecord record = new AuditRecord();
        record.setDeclarationId(declarationId);
        record.setReviewerId(reviewerId);
        record.setAction(request.getAction());
        record.setComment(request.getComment());

        String snapshot = String.format(
                "{\"morality\":%.2f,\"ability\":%.2f,\"sports\":%.2f,\"total\":%.2f}",
                decl.getMoralityScore(), decl.getAbilityScore(),
                decl.getSportsScore(), decl.getTotalScore());
        record.setSnapshotScores(snapshot);
        auditRecordMapper.insert(record);

        if (assignment != null) {
            assignment.setStatus(toAssignmentStatus(request.getAction()));
            assignment.setAction(request.getAction());
            assignment.setComment(request.getComment());
            assignment.setReviewedAt(LocalDateTime.now());
            assignmentMapper.updateById(assignment);
        }

        switch (request.getAction()) {
            case "approve":
                if (assignment == null || shouldApproveDeclaration(declarationId)) {
                    decl.setStatus("approved");
                }
                break;
            case "reject":
                decl.setStatus("rejected");
                assignmentMapper.cancelPendingByDeclarationId(declarationId);
                break;
            case "return":
                decl.setStatus("returned");
                assignmentMapper.cancelPendingByDeclarationId(declarationId);
                break;
            default:
                throw new BizException("无效的审核动作");
        }
        declarationMapper.updateById(decl);
    }

    @Override
    @Transactional
    public void correctAudit(String declarationId, AuditCorrectionRequest request) {
        Declaration decl = declarationMapper.selectById(declarationId);
        if (decl == null) throw new BizException("申报记录不存在");
        String action = request.getAction();
        if (!isSupportedAction(action)) {
            throw new BizException("无效的审核动作");
        }

        String reviewerId = SecurityUtil.getCurrentUserId();
        String role = SecurityUtil.getCurrentRole();
        AuditAssignment assignment = assignmentMapper.selectByDeclarationAndReviewer(declarationId, reviewerId);
        if ("teacher".equals(role) && !hasFinishedRelation(declarationId, reviewerId, assignment)) {
            throw new BizException("当前申报不属于你的已审核记录");
        }

        String beforeSnapshot = snapshotScores(decl);
        applyItemAdjustments(decl, request.getItemScoreAdjustments());
        scoreService.computeDeclarationScore(decl);

        if (assignment != null) {
            assignment.setStatus(toAssignmentStatus(action));
            assignment.setAction(action);
            assignment.setComment(request.getComment());
            assignment.setReviewedAt(LocalDateTime.now());
            assignmentMapper.updateById(assignment);
        }

        applyDeclarationStatus(decl, action, declarationId);

        AuditRecord record = new AuditRecord();
        record.setDeclarationId(declarationId);
        record.setReviewerId(reviewerId);
        record.setAction("correction_" + action);
        record.setComment(request.getComment());
        record.setSnapshotScores("{\"before\":" + beforeSnapshot + ",\"after\":" + snapshotScores(decl) + "}");
        auditRecordMapper.insert(record);

        declarationMapper.updateById(decl);
    }

    private boolean shouldApproveDeclaration(String declarationId) {
        return assignmentMapper.countPendingByDeclarationId(declarationId) == 0
                && assignmentMapper.countNegativeByDeclarationId(declarationId) == 0;
    }

    private String toAssignmentStatus(String action) {
        switch (action) {
            case "approve":
                return "approved";
            case "reject":
                return "rejected";
            case "return":
                return "returned";
            default:
                return action;
        }
    }

    private String normalizeScope(String scope) {
        if ("teacher".equals(SecurityUtil.getCurrentRole())) {
            return "mine";
        }
        if ("mine".equals(scope) || "assigned".equals(scope) || "unassigned".equals(scope)) {
            return scope;
        }
        return "all";
    }

    private DeclarationVO toQueueVO(Declaration decl) {
        DeclarationVO vo = new DeclarationVO();
        BeanUtils.copyProperties(decl, vo);
        SysUser student = userMapper.selectById(decl.getStudentId());
        if (student != null) {
            vo.setStudentName(student.getName());
            vo.setStudentLoginId(student.getLoginId());
        }
        EvalBatch batch = batchMapper.selectById(decl.getBatchId());
        if (batch != null) {
            vo.setBatchName(batch.getName());
        }
        vo.setStage(computeStage(decl));
        return vo;
    }

    private String computeStage(Declaration decl) {
        if ("approved".equals(decl.getStatus())) {
            return "approved";
        }
        if ("rejected".equals(decl.getStatus()) || "returned".equals(decl.getStatus())) {
            return "rejected";
        }
        String declarationId = decl.getId();
        int pendingCount = assignmentMapper.countPendingByDeclarationId(declarationId);
        if (pendingCount <= 0) {
            return "submitted_unassigned";
        }
        int auditCount = auditRecordMapper.countByDeclarationId(declarationId);
        return auditCount > 0 ? "reviewing" : "assigned";
    }

    private boolean isSupportedAction(String action) {
        return "approve".equals(action) || "reject".equals(action) || "return".equals(action);
    }

    private boolean hasFinishedRelation(String declarationId, String reviewerId, AuditAssignment assignment) {
        if (assignment != null && isFinishedAssignmentStatus(assignment.getStatus())) {
            return true;
        }
        return auditRecordMapper.countByDeclarationAndReviewer(declarationId, reviewerId) > 0;
    }

    private boolean isFinishedAssignmentStatus(String status) {
        return "approved".equals(status) || "rejected".equals(status) || "returned".equals(status);
    }

    private void applyItemAdjustments(Declaration decl, List<AuditCorrectionRequest.ItemScoreAdjustment> adjustments) {
        if (adjustments == null) {
            return;
        }
        for (AuditCorrectionRequest.ItemScoreAdjustment adjustment : adjustments) {
            if (adjustment == null) {
                continue;
            }
            DeclarationItem item = itemMapper.selectById(adjustment.getItemId());
            if (item == null || !decl.getId().equals(item.getDeclarationId())) {
                throw new BizException("申报明细不存在或不属于当前申报");
            }
            BigDecimal finalScore = adjustment.getFinalScore();
            if (finalScore == null || finalScore.compareTo(BigDecimal.ZERO) < 0) {
                throw new BizException("调整分数不能小于0");
            }
            item.setFinalScore(finalScore);
            itemMapper.updateById(item);
        }
    }

    private void applyDeclarationStatus(Declaration decl, String action, String declarationId) {
        switch (action) {
            case "approve":
                if (shouldApproveDeclaration(declarationId)) {
                    decl.setStatus("approved");
                } else {
                    decl.setStatus("submitted");
                }
                break;
            case "reject":
                decl.setStatus("rejected");
                assignmentMapper.cancelPendingByDeclarationId(declarationId);
                break;
            case "return":
                decl.setStatus("returned");
                assignmentMapper.cancelPendingByDeclarationId(declarationId);
                break;
            default:
                throw new BizException("无效的审核动作");
        }
    }

    private String snapshotScores(Declaration decl) {
        return String.format(
                "{\"morality\":%s,\"ability\":%s,\"sports\":%s,\"total\":%s}",
                scoreValue(decl.getMoralityScore()),
                scoreValue(decl.getAbilityScore()),
                scoreValue(decl.getSportsScore()),
                scoreValue(decl.getTotalScore()));
    }

    private String scoreValue(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }
}
