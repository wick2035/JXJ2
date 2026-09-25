package com.eval.jxj.service.impl;

import com.eval.jxj.entity.Award;
import com.eval.jxj.entity.AwardLevelDef;
import com.eval.jxj.entity.AwardLevelScore;
import com.eval.jxj.entity.BatchBasicScore;
import com.eval.jxj.entity.Declaration;
import com.eval.jxj.entity.DeclarationItem;
import com.eval.jxj.entity.EvalBatchCategory;
import com.eval.jxj.mapper.AwardLevelDefMapper;
import com.eval.jxj.mapper.AwardLevelScoreMapper;
import com.eval.jxj.mapper.AwardMapper;
import com.eval.jxj.mapper.BatchAwardMapper;
import com.eval.jxj.mapper.BatchBasicScoreMapper;
import com.eval.jxj.mapper.DeclarationItemMapper;
import com.eval.jxj.mapper.DeclarationMapper;
import com.eval.jxj.mapper.EvalBatchCategoryMapper;
import com.eval.jxj.mapper.ScoreRecalcLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoreCalculationServiceImplTest {

    @Mock
    private AwardLevelScoreMapper levelScoreMapper;
    @Mock
    private AwardLevelDefMapper levelDefMapper;
    @Mock
    private BatchAwardMapper batchAwardMapper;
    @Mock
    private EvalBatchCategoryMapper batchCategoryMapper;
    @Mock
    private DeclarationMapper declarationMapper;
    @Mock
    private DeclarationItemMapper itemMapper;
    @Mock
    private ScoreRecalcLogMapper recalcLogMapper;
    @Mock
    private BatchBasicScoreMapper basicScoreMapper;
    @Mock
    private AwardMapper awardMapper;

    @InjectMocks
    private ScoreCalculationServiceImpl service;

    @Test
    void computeDeclarationScoreIncludesBasicScoresBeforeCapAndWeight() {
        Declaration declaration = new Declaration();
        declaration.setId("decl-1");
        declaration.setBatchId("batch-1");
        declaration.setStudentId("student-1");

        DeclarationItem item = new DeclarationItem();
        item.setCategory("morality");
        item.setFinalScore(new BigDecimal("8.00"));

        BatchBasicScore basicScore = new BatchBasicScore();
        basicScore.setAwardId("award-basic-1");
        basicScore.setScore(new BigDecimal("5.00"));

        Award basicAward = new Award();
        basicAward.setId("award-basic-1");
        basicAward.setCategory("morality");
        basicAward.setAwardType("basic");

        EvalBatchCategory morality = new EvalBatchCategory();
        morality.setCategory("morality");
        morality.setWeightPercent(new BigDecimal("50"));
        morality.setMaxScoreCap(new BigDecimal("10"));

        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        when(basicScoreMapper.selectStudentScores("batch-1", "student-1")).thenReturn(List.of(basicScore));
        when(awardMapper.selectBatchBasicAwards("batch-1")).thenReturn(List.of(basicAward));
        when(batchCategoryMapper.selectList(any())).thenReturn(List.of(morality));

        service.computeDeclarationScore(declaration);

        assertThat(declaration.getMoralityScore()).isEqualByComparingTo("10");
        assertThat(declaration.getTotalScore()).isEqualByComparingTo("5.00");
        verify(declarationMapper).updateById(declaration);
    }

    @Test
    void customLevelDoesNotDowngradeToAnotherLevel() {
        DeclarationItem item = new DeclarationItem();
        item.setAwardId("award-1");
        item.setLevelId("custom-1");
        item.setUseDowngrade(1);
        AwardLevelDef custom = new AwardLevelDef();
        custom.setAwardId("award-1");
        custom.setSortOrder(6);
        AwardLevelScore score = new AwardLevelScore();
        score.setBaseScore(new BigDecimal("8.50"));
        when(levelDefMapper.selectById("custom-1")).thenReturn(custom);
        when(levelScoreMapper.selectOne(any())).thenReturn(score);

        assertThat(service.computeItemScore(item, "batch-1")).isEqualByComparingTo("8.50");
        assertThat(item.getUseDowngrade()).isZero();
        verify(levelDefMapper, never()).selectOne(any());
    }

    @Test
    void sharedLevelStillUsesNextSharedLevelWhenDowngraded() {
        DeclarationItem item = new DeclarationItem();
        item.setAwardId("award-1");
        item.setLevelId("national");
        item.setUseDowngrade(1);
        AwardLevelDef national = new AwardLevelDef();
        national.setSortOrder(1);
        AwardLevelDef provincial = new AwardLevelDef();
        provincial.setId("provincial");
        AwardLevelScore score = new AwardLevelScore();
        score.setBaseScore(new BigDecimal("6.00"));
        when(levelDefMapper.selectById("national")).thenReturn(national);
        when(levelDefMapper.selectOne(any())).thenReturn(provincial);
        when(levelScoreMapper.selectOne(any())).thenReturn(score);

        assertThat(service.computeItemScore(item, "batch-1")).isEqualByComparingTo("6.00");
        verify(levelDefMapper).selectOne(any());
    }
}
