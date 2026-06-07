package com.eval.jxj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eval.jxj.common.exception.BizException;
import com.eval.jxj.dto.request.BatchAssignmentGenerateRequest;
import com.eval.jxj.dto.request.BatchCreateRequest;
import com.eval.jxj.dto.request.BatchStatusUpdateRequest;
import com.eval.jxj.dto.response.AuditAssignmentVO;
import com.eval.jxj.dto.response.BatchAssignmentGenerateVO;
import com.eval.jxj.dto.response.BatchDetailRowVO;
import com.eval.jxj.dto.response.BatchEvaluationTableVO;
import com.eval.jxj.dto.response.BatchRankingVO;
import com.eval.jxj.dto.response.BatchStatsVO;
import com.eval.jxj.dto.response.BatchVO;
import com.eval.jxj.entity.AuditAssignment;
import com.eval.jxj.entity.Award;
import com.eval.jxj.entity.AwardCategory;
import com.eval.jxj.entity.AwardLevelDef;
import com.eval.jxj.entity.BatchBasicScore;
import com.eval.jxj.entity.BatchClass;
import com.eval.jxj.entity.BatchReviewer;
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
import com.eval.jxj.service.BatchAssignmentService;
import com.eval.jxj.service.BatchService;
import com.eval.jxj.service.ScoreCalculationService;
import com.eval.jxj.util.SecurityUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BatchServiceImpl implements BatchService {

    private final EvalBatchMapper batchMapper;
    private final EvalBatchCategoryMapper categoryMapper;
    private final DeclarationMapper declarationMapper;
    private final DeclarationItemMapper itemMapper;
    private final AwardMapper awardMapper;
    private final AwardCategoryMapper awardCategoryMapper;
    private final AwardLevelDefMapper levelDefMapper;
    private final SysUserMapper userMapper;
    private final BatchReviewerMapper batchReviewerMapper;
    private final BatchClassMapper batchClassMapper;
    private final AuditAssignmentMapper assignmentMapper;
    private final BatchAssignmentService assignmentService;
    private final BatchBasicScoreMapper basicScoreMapper;
    private final ScoreCalculationService scoreService;

    public BatchServiceImpl(EvalBatchMapper batchMapper,
                            EvalBatchCategoryMapper categoryMapper,
                            DeclarationMapper declarationMapper,
                            DeclarationItemMapper itemMapper,
                            AwardMapper awardMapper,
                            AwardCategoryMapper awardCategoryMapper,
                            AwardLevelDefMapper levelDefMapper,
                            SysUserMapper userMapper,
                            BatchReviewerMapper batchReviewerMapper,
                            BatchClassMapper batchClassMapper,
                            AuditAssignmentMapper assignmentMapper,
                            BatchAssignmentService assignmentService,
                            BatchBasicScoreMapper basicScoreMapper,
                            ScoreCalculationService scoreService) {
        this.batchMapper = batchMapper;
        this.categoryMapper = categoryMapper;
        this.declarationMapper = declarationMapper;
        this.itemMapper = itemMapper;
        this.awardMapper = awardMapper;
        this.awardCategoryMapper = awardCategoryMapper;
        this.levelDefMapper = levelDefMapper;
        this.userMapper = userMapper;
        this.batchReviewerMapper = batchReviewerMapper;
        this.batchClassMapper = batchClassMapper;
        this.assignmentMapper = assignmentMapper;
        this.assignmentService = assignmentService;
        this.basicScoreMapper = basicScoreMapper;
        this.scoreService = scoreService;
    }

    @Override
    public Page<BatchVO> listBatches(int page, int size, String status) {
        String role = SecurityUtil.getCurrentRole();
        Page<EvalBatch> result;
        if ("student".equals(role)) {
            SysUser current = userMapper.selectById(SecurityUtil.getCurrentUserId());
            String grade = current == null ? null : current.getGrade();
            String className = current == null ? null : current.getClassName();
            result = batchMapper.selectStudentBatchPage(new Page<>(page, size), grade, className,
                    StringUtils.hasText(status) ? status : null);
        } else if ("teacher".equals(role)) {
            result = batchMapper.selectReviewerBatchPage(new Page<>(page, size),
                    SecurityUtil.getCurrentUserId(),
                    StringUtils.hasText(status) ? status : null);
        } else {
            LambdaQueryWrapper<EvalBatch> wrapper = new LambdaQueryWrapper<>();
            if (StringUtils.hasText(status)) {
                wrapper.eq(EvalBatch::getStatus, status);
            }
            wrapper.orderByDesc(EvalBatch::getCreatedAt);
            result = batchMapper.selectPage(new Page<>(page, size), wrapper);
        }
        Page<BatchVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public BatchVO getBatch(String id) {
        EvalBatch batch = mustGetBatch(id);
        assertCanViewBatch(id);
        return toVO(batch);
    }

    private void assertCanViewBatch(String id) {
        if (!"teacher".equals(SecurityUtil.getCurrentRole())) {
            return;
        }
        List<String> reviewerIds = batchReviewerMapper.selectReviewerIdsByBatchId(id);
        if (reviewerIds == null || !reviewerIds.contains(SecurityUtil.getCurrentUserId())) {
            throw new BizException("无权查看当前批次");
        }
    }

    @Override
    @Transactional
    public BatchVO createBatch(BatchCreateRequest request) {
        validateReviewerConfig(request.getReviewerIds(), request.getReviewerCount());

        EvalBatch batch = new EvalBatch();
        batch.setName(request.getName());
        batch.setStartDate(request.getStartDate());
        batch.setEndDate(request.getEndDate());
        batch.setDescription(request.getDescription());
        batch.setReviewerCount(defaultReviewerCount(request.getReviewerCount()));
        batch.setStatus("draft");
        batch.setTargetType(resolveTargetType(request));
        batch.setCreatedBy(SecurityUtil.getCurrentUserId());
        batchMapper.insert(batch);

        if (request.getCategories() != null) {
            saveCategories(batch.getId(), request.getCategories());
        }
        saveReviewers(batch.getId(), request.getReviewerIds());
        saveTargetClasses(batch.getId(), batch.getTargetType(), request.getTargetClasses());
        return toVO(batch);
    }

    @Override
    @Transactional
    public BatchVO updateBatch(String id, BatchCreateRequest request) {
        EvalBatch batch = mustGetBatch(id);
        validateReviewerConfig(request.getReviewerIds(), request.getReviewerCount());

        batch.setName(request.getName());
        batch.setStartDate(request.getStartDate());
        batch.setEndDate(request.getEndDate());
        batch.setDescription(request.getDescription());
        batch.setReviewerCount(defaultReviewerCount(request.getReviewerCount()));
        batch.setTargetType(resolveTargetType(request));
        batchMapper.updateById(batch);

        if (request.getCategories() != null) {
            categoryMapper.delete(new LambdaQueryWrapper<EvalBatchCategory>()
                    .eq(EvalBatchCategory::getBatchId, id));
            saveCategories(id, request.getCategories());
            // 权重/上限变更后，立即重算本批次已有非草稿申报的聚合分，使新配置即时生效
            declarationMapper.selectList(new LambdaQueryWrapper<Declaration>()
                            .eq(Declaration::getBatchId, id)
                            .in(Declaration::getStatus, "submitted", "approved", "rejected", "returned"))
                    .forEach(scoreService::computeDeclarationScore);
        }
        saveReviewers(id, request.getReviewerIds());
        saveTargetClasses(id, batch.getTargetType(), request.getTargetClasses());
        return toVO(batch);
    }

    @Override
    @Transactional
    public void deleteBatch(String id) {
        mustGetBatch(id);
        Long declCount = declarationMapper.selectCount(
                new LambdaQueryWrapper<Declaration>().eq(Declaration::getBatchId, id));
        if (declCount != null && declCount > 0) {
            throw new BizException("该批次下已有学生申报，无法删除");
        }
        // 无申报时仅剩配置行，清理后软删批次（复用 updateBatch 同款删法）
        categoryMapper.delete(new LambdaQueryWrapper<EvalBatchCategory>()
                .eq(EvalBatchCategory::getBatchId, id));
        batchReviewerMapper.deleteByBatchId(id);
        batchClassMapper.deleteByBatchId(id);
        batchMapper.deleteById(id);
    }

    @Override
    public void updateStatus(String id, BatchStatusUpdateRequest request) {
        EvalBatch batch = mustGetBatch(id);
        String status = request == null ? null : request.getStatus();
        if (!"draft".equals(status) && !"open_timed".equals(status)
                && !"open".equals(status) && !"closed".equals(status)) {
            throw new BizException("无效的批次状态");
        }
        batch.setStatus(status);
        if (("open_timed".equals(status) || "open".equals(status)) && request.getEndDate() != null) {
            batch.setEndDate(request.getEndDate());
        }
        batchMapper.updateById(batch);
    }

    @Override
    public BatchStatsVO getStats(String id) {
        mustGetBatch(id);
        List<Declaration> declarations = selectDeclarations(id);
        List<AuditAssignment> assignments = selectAssignments(id);

        BatchStatsVO vo = new BatchStatsVO();
        vo.setBatchId(id);
        vo.setTotalDeclarations(declarations.size());
        vo.setDraftCount(countStatus(declarations, "draft"));
        vo.setSubmittedCount(countStatus(declarations, "submitted"));
        vo.setApprovedCount(countStatus(declarations, "approved"));
        vo.setRejectedCount(countStatus(declarations, "rejected"));
        vo.setReturnedCount(countStatus(declarations, "returned"));
        vo.setPendingAuditCount(countAssignmentStatus(assignments, "pending"));
        vo.setFinishedAuditCount((int) assignments.stream().filter(a -> !"pending".equals(a.getStatus())).count());

        List<BigDecimal> approvedScores = declarations.stream()
                .filter(d -> "approved".equals(d.getStatus()))
                .map(Declaration::getTotalScore)
                .filter(score -> score != null)
                .collect(Collectors.toList());
        if (!approvedScores.isEmpty()) {
            BigDecimal sum = approvedScores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            vo.setAverageScore(sum.divide(BigDecimal.valueOf(approvedScores.size()), 2, RoundingMode.HALF_UP));
            vo.setMaxScore(approvedScores.stream().max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO));
            vo.setMinScore(approvedScores.stream().min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO));
        }
        int totalAssignments = assignments.size();
        if (totalAssignments == 0) {
            vo.setAuditProgress(BigDecimal.ZERO);
        } else {
            vo.setAuditProgress(BigDecimal.valueOf(vo.getFinishedAuditCount())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalAssignments), 2, RoundingMode.HALF_UP));
        }
        return vo;
    }

    @Override
    public List<BatchRankingVO> getRanking(String id) {
        mustGetBatch(id);
        List<EvalBatchCategory> batchCategories = selectBatchCategories(id);
        List<Declaration> approved = selectDeclarations(id).stream()
                .filter(d -> "approved".equals(d.getStatus()))
                .sorted(Comparator
                        .comparing(Declaration::getTotalScore, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Declaration::getSubmittedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(d -> {
                            SysUser user = userMapper.selectById(d.getStudentId());
                            return user == null ? "" : user.getLoginId();
                        }))
                .collect(Collectors.toList());

        List<BatchRankingVO> rows = new ArrayList<>();
        for (int i = 0; i < approved.size(); i++) {
            Declaration declaration = approved.get(i);
            SysUser student = userMapper.selectById(declaration.getStudentId());
            BatchRankingVO row = new BatchRankingVO();
            row.setRank(i + 1);
            row.setDeclarationId(declaration.getId());
            row.setStudentId(declaration.getStudentId());
            if (student != null) {
                row.setStudentLoginId(student.getLoginId());
                row.setStudentName(student.getName());
            }
            row.setTotalScore(declaration.getTotalScore());
            row.setMoralityScore(declaration.getMoralityScore());
            row.setAbilityScore(declaration.getAbilityScore());
            row.setSportsScore(declaration.getSportsScore());
            row.setCategoryScores(computeCappedCategoryScores(declaration, batchCategories));
            row.setSubmittedAt(declaration.getSubmittedAt());
            rows.add(row);
        }
        return rows;
    }

    @Override
    public BatchEvaluationTableVO getEvaluationTable(String id) {
        mustGetBatch(id);

        List<AwardCategory> categoryMetas = awardCategoryMapper.selectList(new LambdaQueryWrapper<AwardCategory>()
                .orderByAsc(AwardCategory::getSortOrder)
                .orderByAsc(AwardCategory::getCode));
        List<Award> awards = awardMapper.selectList(new LambdaQueryWrapper<Award>()
                .orderByAsc(Award::getCategory)
                .orderByAsc(Award::getCreatedAt)
                .orderByAsc(Award::getName));
        Map<String, List<Award>> awardsByCategory = awards.stream()
                .sorted(Comparator
                        .comparing(Award::getCategory, Comparator.nullsLast(String::compareTo))
                        .thenComparing(Award::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Award::getName, Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.groupingBy(Award::getCategory, LinkedHashMap::new, Collectors.toList()));
        Map<String, Award> awardById = awards.stream()
                .collect(Collectors.toMap(Award::getId, Function.identity(), (a, b) -> a));

        // 预取已通过申报及其明细（自定义列与行数据共用，避免重复查询）
        List<Declaration> approvedDeclarations = selectDeclarations(id).stream()
                .filter(d -> "approved".equals(d.getStatus()))
                .collect(Collectors.toList());
        Map<String, List<DeclarationItem>> itemsByDeclaration = new LinkedHashMap<>();
        Map<String, LinkedHashSet<String>> customNamesByCategory = new LinkedHashMap<>();
        for (Declaration declaration : approvedDeclarations) {
            List<DeclarationItem> items = itemMapper.selectList(new LambdaQueryWrapper<DeclarationItem>()
                    .eq(DeclarationItem::getDeclarationId, declaration.getId())
                    .orderByAsc(DeclarationItem::getSortOrder));
            itemsByDeclaration.put(declaration.getId(), items);
            for (DeclarationItem item : items) {
                if (!StringUtils.hasText(item.getAwardId()) && StringUtils.hasText(item.getCustomAwardName())) {
                    customNamesByCategory
                            .computeIfAbsent(item.getCategory(), k -> new LinkedHashSet<>())
                            .add(item.getCustomAwardName().trim());
                }
            }
        }

        List<BatchEvaluationTableVO.CategoryColumn> categoryColumns = new ArrayList<>();
        for (AwardCategory category : categoryMetas) {
            List<Award> categoryAwards = awardsByCategory.getOrDefault(category.getCode(), Collections.emptyList());
            LinkedHashSet<String> customNames = customNamesByCategory.getOrDefault(category.getCode(), new LinkedHashSet<>());
            if (categoryAwards.isEmpty() && customNames.isEmpty()) {
                continue;
            }
            BatchEvaluationTableVO.CategoryColumn column = new BatchEvaluationTableVO.CategoryColumn();
            column.setCode(category.getCode());
            column.setName(category.getName());
            column.setColor(category.getColor());
            List<BatchEvaluationTableVO.AwardColumn> awardColumns = new ArrayList<>();
            for (Award award : categoryAwards) {
                BatchEvaluationTableVO.AwardColumn awardColumn = new BatchEvaluationTableVO.AwardColumn();
                awardColumn.setAwardId(award.getId());
                awardColumn.setName(award.getName());
                awardColumns.add(awardColumn);
            }
            for (String customName : customNames) {
                BatchEvaluationTableVO.AwardColumn awardColumn = new BatchEvaluationTableVO.AwardColumn();
                awardColumn.setAwardId(customAwardKey(category.getCode(), customName));
                awardColumn.setName(customName);
                awardColumn.setCustom(true);
                awardColumns.add(awardColumn);
            }
            column.setAwards(awardColumns);
            categoryColumns.add(column);
        }

        Set<String> visibleCategoryCodes = categoryColumns.stream()
                .map(BatchEvaluationTableVO.CategoryColumn::getCode)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        // 本批次各类别封顶配置：分类小计需按上限封顶，与排名页 computeCappedCategoryScores 一致
        Map<String, BigDecimal> capByCategory = categoryMapper.selectList(
                        new LambdaQueryWrapper<EvalBatchCategory>().eq(EvalBatchCategory::getBatchId, id)).stream()
                .filter(c -> c.getMaxScoreCap() != null)
                .collect(Collectors.toMap(EvalBatchCategory::getCategory, EvalBatchCategory::getMaxScoreCap, (a, b) -> a));
        List<BatchEvaluationTableVO.StudentRow> rows = approvedDeclarations.stream()
                .map(declaration -> toEvaluationRow(declaration,
                        itemsByDeclaration.getOrDefault(declaration.getId(), Collections.emptyList()),
                        awardById, visibleCategoryCodes, capByCategory))
                .sorted(Comparator.comparing(BatchEvaluationTableVO.StudentRow::getStudentLoginId,
                        Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());

        BatchEvaluationTableVO table = new BatchEvaluationTableVO();
        table.setCategories(categoryColumns);
        table.setRows(rows);
        return table;
    }

    @Override
    public List<BatchDetailRowVO> getDetails(String id) {
        mustGetBatch(id);
        List<BatchDetailRowVO> rows = new ArrayList<>();
        for (Declaration declaration : selectDeclarations(id)) {
            SysUser student = userMapper.selectById(declaration.getStudentId());
            List<DeclarationItem> items = itemMapper.selectList(new LambdaQueryWrapper<DeclarationItem>()
                    .eq(DeclarationItem::getDeclarationId, declaration.getId())
                    .orderByAsc(DeclarationItem::getSortOrder));
            for (DeclarationItem item : items) {
                BatchDetailRowVO row = new BatchDetailRowVO();
                row.setDeclarationId(declaration.getId());
                row.setStatus(declaration.getStatus());
                if (student != null) {
                    row.setStudentLoginId(student.getLoginId());
                    row.setStudentName(student.getName());
                }
                row.setCategory(item.getCategory());
                row.setAwardName(resolveAwardName(item));
                row.setLevelName(resolveLevelName(item));
                row.setSource("student");
                row.setComputedScore(item.getComputedScore());
                row.setFinalScore(item.getFinalScore());
                row.setDescription(item.getDescription());
                rows.add(row);
            }
            List<Award> basicAwards = awardMapper.selectBatchBasicAwards(id);
            if (basicAwards != null && !basicAwards.isEmpty()) {
                List<BatchBasicScore> basicScores = basicScoreMapper.selectStudentScores(id, declaration.getStudentId());
                Map<String, BigDecimal> scoreByAwardId = basicScores.stream()
                        .collect(Collectors.toMap(BatchBasicScore::getAwardId,
                                score -> score.getScore() != null ? score.getScore() : BigDecimal.ZERO,
                                (a, b) -> b));
                for (Award award : basicAwards) {
                    BatchDetailRowVO row = new BatchDetailRowVO();
                    row.setDeclarationId(declaration.getId());
                    row.setStatus(declaration.getStatus());
                    if (student != null) {
                        row.setStudentLoginId(student.getLoginId());
                        row.setStudentName(student.getName());
                    }
                    row.setCategory(award.getCategory());
                    row.setAwardName(award.getName());
                    row.setLevelName("系统基础分");
                    row.setSource("basic");
                    BigDecimal score = scoreByAwardId.getOrDefault(award.getId(), BigDecimal.ZERO);
                    row.setComputedScore(score);
                    row.setFinalScore(score);
                    row.setDescription("系统导入基础分");
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    @Override
    public List<AuditAssignmentVO> getAssignments(String id) {
        mustGetBatch(id);
        return selectAssignments(id).stream()
                .sorted(Comparator.comparing(AuditAssignment::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toAssignmentVO)
                .collect(Collectors.toList());
    }

    @Override
    public BatchAssignmentGenerateVO generateAssignments(String id, BatchAssignmentGenerateRequest request) {
        mustGetBatch(id);
        boolean replacePending = request != null && Boolean.TRUE.equals(request.getReplacePending());
        boolean hasReviewerId = request != null && StringUtils.hasText(request.getReviewerId());
        boolean hasCount = request != null && request.getCount() != null;
        if (hasReviewerId || hasCount) {
            if (replacePending) {
                throw new BizException("指定分配不支持同时重分配待审任务");
            }
            if (!hasReviewerId || !hasCount) {
                throw new BizException("指定分配必须选择审核人并填写份数");
            }
            return assignmentService.generateForReviewer(id, request.getReviewerId(), request.getCount());
        }
        return assignmentService.generateForBatch(id, replacePending);
    }

    private List<EvalBatchCategory> selectBatchCategories(String batchId) {
        List<EvalBatchCategory> configs = categoryMapper.selectList(
                new LambdaQueryWrapper<EvalBatchCategory>().eq(EvalBatchCategory::getBatchId, batchId));
        Map<String, Integer> orderMap = awardCategoryMapper.selectList(new LambdaQueryWrapper<AwardCategory>()
                        .orderByAsc(AwardCategory::getSortOrder)
                        .orderByAsc(AwardCategory::getCode))
                .stream()
                .collect(Collectors.toMap(AwardCategory::getCode,
                        c -> c.getSortOrder() == null ? Integer.MAX_VALUE : c.getSortOrder(),
                        (a, b) -> a));
        return configs.stream()
                .sorted(Comparator
                        .comparing((EvalBatchCategory c) -> orderMap.getOrDefault(c.getCategory(), Integer.MAX_VALUE))
                        .thenComparing(EvalBatchCategory::getCategory, Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());
    }

    private Map<String, BigDecimal> computeCappedCategoryScores(Declaration declaration,
                                                                 List<EvalBatchCategory> batchCategories) {
        Map<String, BigDecimal> rawScores = computeRawCategoryScores(declaration);
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (EvalBatchCategory category : batchCategories) {
            BigDecimal raw = rawScores.getOrDefault(category.getCategory(), BigDecimal.ZERO);
            BigDecimal capped = raw;
            if (category.getMaxScoreCap() != null && raw.compareTo(category.getMaxScoreCap()) > 0) {
                capped = category.getMaxScoreCap();
            }
            result.put(category.getCategory(), capped);
        }
        return result;
    }

    private Map<String, BigDecimal> computeRawCategoryScores(Declaration declaration) {
        Map<String, BigDecimal> categoryScores = new LinkedHashMap<>();
        List<DeclarationItem> items = itemMapper.selectList(new LambdaQueryWrapper<DeclarationItem>()
                .eq(DeclarationItem::getDeclarationId, declaration.getId()));
        for (DeclarationItem item : items) {
            BigDecimal score = item.getFinalScore() != null ? item.getFinalScore() : BigDecimal.ZERO;
            categoryScores.merge(item.getCategory(), score, BigDecimal::add);
        }

        Map<String, String> basicAwardCategoryById = awardMapper.selectBatchBasicAwards(declaration.getBatchId()).stream()
                .collect(Collectors.toMap(Award::getId, Award::getCategory, (a, b) -> a));
        List<BatchBasicScore> basicScores = basicScoreMapper.selectStudentScores(
                declaration.getBatchId(), declaration.getStudentId());
        for (BatchBasicScore basicScore : basicScores) {
            String category = basicAwardCategoryById.get(basicScore.getAwardId());
            if (category == null) {
                continue;
            }
            BigDecimal score = basicScore.getScore() != null ? basicScore.getScore() : BigDecimal.ZERO;
            categoryScores.merge(category, score, BigDecimal::add);
        }
        return categoryScores;
    }

    private static String customAwardKey(String category, String name) {
        return "custom::" + category + "::" + name;
    }

    private BatchEvaluationTableVO.StudentRow toEvaluationRow(Declaration declaration,
                                                               List<DeclarationItem> items,
                                                               Map<String, Award> awardById,
                                                               Set<String> visibleCategoryCodes,
                                                               Map<String, BigDecimal> capByCategory) {
        SysUser student = userMapper.selectById(declaration.getStudentId());
        Map<String, BigDecimal> scores = new LinkedHashMap<>();
        Map<String, BigDecimal> subtotals = new LinkedHashMap<>();
        for (String category : visibleCategoryCodes) {
            subtotals.put(category, BigDecimal.ZERO);
        }

        for (DeclarationItem item : items) {
            BigDecimal score = item.getFinalScore() != null ? item.getFinalScore() : BigDecimal.ZERO;
            if (visibleCategoryCodes.contains(item.getCategory())) {
                subtotals.merge(item.getCategory(), score, BigDecimal::add);
            }
            if (StringUtils.hasText(item.getAwardId()) && awardById.containsKey(item.getAwardId())) {
                scores.merge(item.getAwardId(), score, BigDecimal::add);
            } else if (!StringUtils.hasText(item.getAwardId()) && StringUtils.hasText(item.getCustomAwardName())) {
                scores.merge(customAwardKey(item.getCategory(), item.getCustomAwardName().trim()), score, BigDecimal::add);
            }
        }

        List<BatchBasicScore> basicScores = basicScoreMapper.selectStudentScores(
                declaration.getBatchId(), declaration.getStudentId());
        for (BatchBasicScore basicScore : basicScores) {
            Award award = awardById.get(basicScore.getAwardId());
            if (award == null) {
                continue;
            }
            BigDecimal score = basicScore.getScore() != null ? basicScore.getScore() : BigDecimal.ZERO;
            scores.merge(basicScore.getAwardId(), score, BigDecimal::add);
            if (visibleCategoryCodes.contains(award.getCategory())) {
                subtotals.merge(award.getCategory(), score, BigDecimal::add);
            }
        }

        // 分类小计按类别上限封顶（逐奖项明细 scores 保持原值）
        subtotals.replaceAll((category, raw) -> {
            BigDecimal cap = capByCategory.get(category);
            return cap != null && raw.compareTo(cap) > 0 ? cap : raw;
        });

        BatchEvaluationTableVO.StudentRow row = new BatchEvaluationTableVO.StudentRow();
        row.setStudentId(declaration.getStudentId());
        if (student != null) {
            row.setStudentLoginId(student.getLoginId());
            row.setStudentName(student.getName());
        }
        row.setScores(scores);
        row.setSubtotals(subtotals);
        return row;
    }

    private EvalBatch mustGetBatch(String id) {
        EvalBatch batch = batchMapper.selectById(id);
        if (batch == null) throw new BizException("批次不存在");
        return batch;
    }

    private void saveCategories(String batchId, List<BatchCreateRequest.CategoryConfig> categories) {
        for (BatchCreateRequest.CategoryConfig cc : categories) {
            validateCategoryExists(cc.getCategory());
            EvalBatchCategory cat = new EvalBatchCategory();
            cat.setBatchId(batchId);
            cat.setCategory(cc.getCategory());
            cat.setWeightPercent(cc.getWeightPercent());
            cat.setMaxScoreCap(cc.getMaxScoreCap());
            categoryMapper.insert(cat);
        }
    }

    private void saveReviewers(String batchId, List<String> reviewerIds) {
        batchReviewerMapper.deleteByBatchId(batchId);
        if (reviewerIds == null) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (String reviewerId : reviewerIds) {
            if (!StringUtils.hasText(reviewerId) || !seen.add(reviewerId)) {
                continue;
            }
            BatchReviewer reviewer = new BatchReviewer();
            reviewer.setBatchId(batchId);
            reviewer.setReviewerId(reviewerId);
            batchReviewerMapper.insert(reviewer);
        }
    }

    private String resolveTargetType(BatchCreateRequest request) {
        if ("specified".equals(request.getTargetType())) {
            if (request.getTargetClasses() == null || request.getTargetClasses().isEmpty()) {
                throw new BizException("指定班级发布时请至少选择一个班级");
            }
            return "specified";
        }
        return "all";
    }

    private void saveTargetClasses(String batchId, String targetType,
                                   List<BatchCreateRequest.ClassTarget> classes) {
        batchClassMapper.deleteByBatchId(batchId);
        if (!"specified".equals(targetType) || classes == null) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (BatchCreateRequest.ClassTarget ct : classes) {
            if (ct == null || !StringUtils.hasText(ct.getClassName())) {
                continue;
            }
            String grade = StringUtils.hasText(ct.getGrade()) ? ct.getGrade() : null;
            if (!seen.add(grade + "||" + ct.getClassName())) {
                continue;
            }
            BatchClass bc = new BatchClass();
            bc.setBatchId(batchId);
            bc.setGrade(grade);
            bc.setClassName(ct.getClassName());
            batchClassMapper.insert(bc);
        }
    }

    private void validateReviewerConfig(List<String> reviewerIds, Integer reviewerCount) {
        int count = defaultReviewerCount(reviewerCount);
        int reviewerSize = reviewerIds == null ? 0 : new HashSet<>(reviewerIds).size();
        if (reviewerSize > 0 && count > reviewerSize) {
            throw new BizException("每份申报审核人数不能超过批次审核人数量");
        }
        if (reviewerIds == null) {
            return;
        }
        for (String reviewerId : reviewerIds) {
            SysUser user = userMapper.selectById(reviewerId);
            if (user == null || (!"teacher".equals(user.getRole()) && !"admin".equals(user.getRole()))) {
                throw new BizException("审核人不存在或角色无效");
            }
        }
    }

    private int defaultReviewerCount(Integer reviewerCount) {
        return reviewerCount == null || reviewerCount <= 0 ? 1 : reviewerCount;
    }

    private BatchVO toVO(EvalBatch batch) {
        BatchVO vo = new BatchVO();
        BeanUtils.copyProperties(batch, vo);

        List<EvalBatchCategory> cats = categoryMapper.selectList(
                new LambdaQueryWrapper<EvalBatchCategory>().eq(EvalBatchCategory::getBatchId, batch.getId()));
        vo.setCategories(cats.stream().map(c -> {
            BatchVO.CategoryVO cv = new BatchVO.CategoryVO();
            BeanUtils.copyProperties(c, cv);
            return cv;
        }).collect(Collectors.toList()));

        List<String> reviewerIds = batchReviewerMapper.selectReviewerIdsByBatchId(batch.getId());
        vo.setReviewers(reviewerIds.stream().map(reviewerId -> {
            SysUser user = userMapper.selectById(reviewerId);
            BatchVO.ReviewerVO rv = new BatchVO.ReviewerVO();
            rv.setId(reviewerId);
            if (user != null) {
                rv.setLoginId(user.getLoginId());
                rv.setName(user.getName());
            }
            return rv;
        }).collect(Collectors.toList()));

        List<BatchClass> classRows = batchClassMapper.selectList(
                new LambdaQueryWrapper<BatchClass>().eq(BatchClass::getBatchId, batch.getId()));
        vo.setTargetClasses(classRows.stream().map(tc -> {
            BatchVO.ClassTargetVO cv = new BatchVO.ClassTargetVO();
            cv.setGrade(tc.getGrade());
            cv.setClassName(tc.getClassName());
            return cv;
        }).collect(Collectors.toList()));

        Long count = declarationMapper.selectCount(
                new LambdaQueryWrapper<Declaration>().eq(Declaration::getBatchId, batch.getId()));
        vo.setDeclarationCount(count.intValue());
        vo.setPendingAuditCount(assignmentMapper.selectCount(new LambdaQueryWrapper<AuditAssignment>()
                .eq(AuditAssignment::getBatchId, batch.getId())
                .eq(AuditAssignment::getStatus, "pending")).intValue());
        vo.setApprovedCount(declarationMapper.selectCount(new LambdaQueryWrapper<Declaration>()
                .eq(Declaration::getBatchId, batch.getId())
                .eq(Declaration::getStatus, "approved")).intValue());

        // 管理员/教师概览：提交人数、目标班级学生数、待审核份数
        if (!"student".equals(SecurityUtil.getCurrentRole())) {
            vo.setSubmittedStudentCount(declarationMapper.selectCount(new LambdaQueryWrapper<Declaration>()
                    .eq(Declaration::getBatchId, batch.getId())
                    .in(Declaration::getStatus, "submitted", "approved", "rejected")).intValue());
            vo.setEligibleStudentCount(userMapper.countStudentsInBatchScope(batch.getId()));
            vo.setPendingReviewCount(declarationMapper.selectCount(new LambdaQueryWrapper<Declaration>()
                    .eq(Declaration::getBatchId, batch.getId())
                    .eq(Declaration::getStatus, "submitted")).intValue());
        }
        return vo;
    }

    private List<Declaration> selectDeclarations(String batchId) {
        return declarationMapper.selectList(new LambdaQueryWrapper<Declaration>()
                .eq(Declaration::getBatchId, batchId)
                .orderByAsc(Declaration::getSubmittedAt));
    }

    private List<AuditAssignment> selectAssignments(String batchId) {
        return assignmentMapper.selectList(new LambdaQueryWrapper<AuditAssignment>()
                .eq(AuditAssignment::getBatchId, batchId));
    }

    private int countStatus(List<Declaration> declarations, String status) {
        return (int) declarations.stream().filter(d -> status.equals(d.getStatus())).count();
    }

    private int countAssignmentStatus(List<AuditAssignment> assignments, String status) {
        return (int) assignments.stream().filter(a -> status.equals(a.getStatus())).count();
    }

    private String resolveAwardName(DeclarationItem item) {
        if (StringUtils.hasText(item.getCustomAwardName())) {
            return item.getCustomAwardName();
        }
        Award award = item.getAwardId() == null ? null : awardMapper.selectById(item.getAwardId());
        return award == null ? null : award.getName();
    }

    private String resolveLevelName(DeclarationItem item) {
        if (StringUtils.hasText(item.getCustomLevelName())) {
            return item.getCustomLevelName();
        }
        AwardLevelDef level = item.getLevelId() == null ? null : levelDefMapper.selectById(item.getLevelId());
        return level == null ? null : level.getName();
    }

    private void validateCategoryExists(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BizException("类别不能为空");
        }
        AwardCategory category = awardCategoryMapper.selectOne(new LambdaQueryWrapper<AwardCategory>()
                .eq(AwardCategory::getCode, code));
        if (category == null) {
            throw new BizException("类别不存在");
        }
    }

    private AuditAssignmentVO toAssignmentVO(AuditAssignment assignment) {
        AuditAssignmentVO vo = new AuditAssignmentVO();
        BeanUtils.copyProperties(assignment, vo);
        Declaration declaration = declarationMapper.selectById(assignment.getDeclarationId());
        if (declaration != null) {
            SysUser student = userMapper.selectById(declaration.getStudentId());
            if (student != null) {
                vo.setStudentLoginId(student.getLoginId());
                vo.setStudentName(student.getName());
            }
        }
        SysUser reviewer = userMapper.selectById(assignment.getReviewerId());
        if (reviewer != null) {
            vo.setReviewerName(reviewer.getName());
        }
        return vo;
    }
}
