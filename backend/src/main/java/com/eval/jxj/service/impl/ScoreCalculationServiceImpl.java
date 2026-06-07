package com.eval.jxj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eval.jxj.entity.*;
import com.eval.jxj.mapper.*;
import com.eval.jxj.service.ScoreCalculationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ScoreCalculationServiceImpl implements ScoreCalculationService {

    private final AwardLevelScoreMapper levelScoreMapper;
    private final AwardLevelDefMapper levelDefMapper;
    private final BatchAwardMapper batchAwardMapper;
    private final EvalBatchCategoryMapper batchCategoryMapper;
    private final DeclarationMapper declarationMapper;
    private final DeclarationItemMapper itemMapper;
    private final ScoreRecalcLogMapper recalcLogMapper;
    private final BatchBasicScoreMapper basicScoreMapper;
    private final AwardMapper awardMapper;

    public ScoreCalculationServiceImpl(AwardLevelScoreMapper levelScoreMapper,
                                       AwardLevelDefMapper levelDefMapper,
                                       BatchAwardMapper batchAwardMapper,
                                       EvalBatchCategoryMapper batchCategoryMapper,
                                       DeclarationMapper declarationMapper,
                                       DeclarationItemMapper itemMapper,
                                       ScoreRecalcLogMapper recalcLogMapper,
                                       BatchBasicScoreMapper basicScoreMapper,
                                       AwardMapper awardMapper) {
        this.levelScoreMapper = levelScoreMapper;
        this.levelDefMapper = levelDefMapper;
        this.batchAwardMapper = batchAwardMapper;
        this.batchCategoryMapper = batchCategoryMapper;
        this.declarationMapper = declarationMapper;
        this.itemMapper = itemMapper;
        this.recalcLogMapper = recalcLogMapper;
        this.basicScoreMapper = basicScoreMapper;
        this.awardMapper = awardMapper;
    }

    @Override
    public BigDecimal computeItemScore(DeclarationItem item, String batchId) {
        // Custom award: use custom_base_score directly
        if (item.getAwardId() == null) {
            BigDecimal score = item.getCustomBaseScore() != null ? item.getCustomBaseScore() : BigDecimal.ZERO;
            item.setComputedScore(score);
            item.setFinalScore(score);
            return score;
        }

        String targetLevelId = item.getLevelId();

        // Handle downgrade: find the level with sort_order + 1
        if (item.getUseDowngrade() != null && item.getUseDowngrade() == 1 && targetLevelId != null) {
            AwardLevelDef currentLevel = levelDefMapper.selectById(targetLevelId);
            if (currentLevel != null) {
                AwardLevelDef downgraded = levelDefMapper.selectOne(
                        new LambdaQueryWrapper<AwardLevelDef>()
                                .eq(AwardLevelDef::getSortOrder, currentLevel.getSortOrder() + 1));
                if (downgraded != null) {
                    targetLevelId = downgraded.getId();
                }
            }
        }

        BigDecimal baseScore = resolveBaseScore(item.getAwardId(), targetLevelId, batchId);
        item.setComputedScore(baseScore);
        item.setFinalScore(baseScore);
        return baseScore;
    }

    private BigDecimal resolveBaseScore(String awardId, String levelId, String batchId) {
        // 1. Check batch_award override first
        BatchAward ba = batchAwardMapper.selectOne(
                new LambdaQueryWrapper<BatchAward>()
                        .eq(BatchAward::getBatchId, batchId)
                        .eq(BatchAward::getAwardId, awardId)
                        .eq(BatchAward::getLevelId, levelId));
        if (ba != null && ba.getOverrideBaseScore() != null) {
            return ba.getOverrideBaseScore();
        }

        // 2. Fallback to global award_level_score
        AwardLevelScore als = levelScoreMapper.selectOne(
                new LambdaQueryWrapper<AwardLevelScore>()
                        .eq(AwardLevelScore::getAwardId, awardId)
                        .eq(AwardLevelScore::getLevelId, levelId));
        return als != null ? als.getBaseScore() : BigDecimal.ZERO;
    }

    @Override
    public void computeDeclarationScore(Declaration declaration) {
        List<DeclarationItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<DeclarationItem>()
                        .eq(DeclarationItem::getDeclarationId, declaration.getId()));

        List<EvalBatchCategory> categories = batchCategoryMapper.selectList(
                new LambdaQueryWrapper<EvalBatchCategory>()
                        .eq(EvalBatchCategory::getBatchId, declaration.getBatchId()));

        Map<String, EvalBatchCategory> catMap = categories.stream()
                .collect(Collectors.toMap(EvalBatchCategory::getCategory, c -> c));

        // Group items by category, sum final_score
        Map<String, BigDecimal> categoryRawSums = items.stream()
                .collect(Collectors.groupingBy(DeclarationItem::getCategory,
                        Collectors.reducing(BigDecimal.ZERO,
                                i -> i.getFinalScore() != null ? i.getFinalScore() : BigDecimal.ZERO,
                                BigDecimal::add)));
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
            categoryRawSums.merge(category, score, BigDecimal::add);
        }

        BigDecimal totalWeighted = BigDecimal.ZERO;
        BigDecimal moralityRaw = BigDecimal.ZERO, abilityRaw = BigDecimal.ZERO, sportsRaw = BigDecimal.ZERO;

        for (Map.Entry<String, BigDecimal> entry : categoryRawSums.entrySet()) {
            String cat = entry.getKey();
            BigDecimal rawSum = entry.getValue();
            EvalBatchCategory config = catMap.get(cat);

            if (config != null) {
                // Apply cap
                BigDecimal capped = rawSum;
                if (config.getMaxScoreCap() != null && rawSum.compareTo(config.getMaxScoreCap()) > 0) {
                    capped = config.getMaxScoreCap();
                }

                // Apply weight
                BigDecimal weighted = capped.multiply(config.getWeightPercent())
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                totalWeighted = totalWeighted.add(weighted);

                switch (cat) {
                    case "morality": moralityRaw = capped; break;
                    case "ability": abilityRaw = capped; break;
                    case "sports": sportsRaw = capped; break;
                }
            }
        }

        declaration.setMoralityScore(moralityRaw);
        declaration.setAbilityScore(abilityRaw);
        declaration.setSportsScore(sportsRaw);
        declaration.setTotalScore(totalWeighted);
        declarationMapper.updateById(declaration);
    }

    @Override
    @Transactional
    public void recalcBatch(String batchId, String triggeredBy, String reason) {
        // Create recalc log
        ScoreRecalcLog log = new ScoreRecalcLog();
        log.setBatchId(batchId);
        log.setTriggeredBy(triggeredBy);
        log.setReason(reason);
        log.setStatus("running");
        log.setStartedAt(LocalDateTime.now());
        recalcLogMapper.insert(log);

        try {
            // Get all non-draft declarations in this batch
            List<Declaration> declarations = declarationMapper.selectList(
                    new LambdaQueryWrapper<Declaration>()
                            .eq(Declaration::getBatchId, batchId)
                            .in(Declaration::getStatus, "submitted", "approved", "rejected", "returned"));

            int affected = 0;
            for (Declaration decl : declarations) {
                List<DeclarationItem> items = itemMapper.selectList(
                        new LambdaQueryWrapper<DeclarationItem>()
                                .eq(DeclarationItem::getDeclarationId, decl.getId()));

                for (DeclarationItem item : items) {
                    if (item.getAwardId() != null) {
                        computeItemScore(item, batchId);
                        itemMapper.updateById(item);
                    }
                }

                computeDeclarationScore(decl);
                affected++;
            }

            log.setAffectedCount(affected);
            log.setStatus("completed");
            log.setFinishedAt(LocalDateTime.now());
            recalcLogMapper.updateById(log);
        } catch (Exception e) {
            log.setStatus("failed");
            log.setFinishedAt(LocalDateTime.now());
            recalcLogMapper.updateById(log);
            throw e;
        }
    }
}
