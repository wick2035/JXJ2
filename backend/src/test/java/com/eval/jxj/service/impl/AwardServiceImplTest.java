package com.eval.jxj.service.impl;

import com.eval.jxj.dto.response.AwardVO;
import com.eval.jxj.entity.Award;
import com.eval.jxj.entity.AwardCategory;
import com.eval.jxj.entity.AwardLevelDef;
import com.eval.jxj.entity.AwardLevelScore;
import com.eval.jxj.entity.BatchAward;
import com.eval.jxj.mapper.AwardCategoryMapper;
import com.eval.jxj.mapper.AwardLevelDefMapper;
import com.eval.jxj.mapper.AwardLevelScoreMapper;
import com.eval.jxj.mapper.AwardMapper;
import com.eval.jxj.mapper.BatchAwardMapper;
import com.eval.jxj.common.exception.BizException;
import com.eval.jxj.dto.request.AwardCreateRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AwardServiceImplTest {

    @Mock
    private AwardMapper awardMapper;
    @Mock
    private AwardLevelScoreMapper levelScoreMapper;
    @Mock
    private AwardLevelDefMapper levelDefMapper;
    @Mock
    private BatchAwardMapper batchAwardMapper;
    @Mock
    private AwardCategoryMapper categoryMapper;

    @InjectMocks
    private AwardServiceImpl service;

    @Test
    void createAward_rejectsMissingCategory() {
        AwardCreateRequest request = new AwardCreateRequest();
        request.setName("创新竞赛");
        request.setCategory("innovation");
        when(categoryMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.createAward(request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("类别不存在");
    }

    @Test
    void createAward_acceptsExistingCategory() {
        AwardCreateRequest request = new AwardCreateRequest();
        request.setName("创新竞赛");
        request.setCategory("innovation");
        AwardCategory category = new AwardCategory();
        category.setCode("innovation");
        when(categoryMapper.selectOne(any())).thenReturn(category);
        when(levelDefMapper.selectList(any())).thenReturn(List.of());
        when(levelScoreMapper.selectList(any())).thenReturn(List.of());

        service.createAward(request);

        assertThat(request.getCategory()).isEqualTo("innovation");
    }

    @Test
    void listBatchAwards_returnsLibraryAwardsWhenBatchHasNoOverrides() {
        Award award = new Award();
        award.setId("award-1");
        award.setCategory("morality");
        award.setName("三好学生");

        AwardLevelDef level = new AwardLevelDef();
        level.setId("level-1");
        level.setCode("school");
        level.setName("校级");
        level.setSortOrder(1);

        AwardLevelScore score = new AwardLevelScore();
        score.setId("score-1");
        score.setAwardId("award-1");
        score.setLevelId("level-1");
        score.setBaseScore(new BigDecimal("6.00"));

        when(batchAwardMapper.selectList(any())).thenReturn(List.of());
        when(awardMapper.selectList(any())).thenReturn(List.of(award));
        when(levelDefMapper.selectList(any())).thenReturn(List.of(level));
        when(levelScoreMapper.selectList(any())).thenReturn(List.of(score));

        List<AwardVO> result = service.listBatchAwards("batch-1", "morality");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("三好学生");
        assertThat(result.get(0).getLevelScores()).extracting(AwardVO.LevelScoreVO::getBaseScore)
                .containsExactly(new BigDecimal("6.00"));
    }

    @Test
    void listBatchAwards_appliesBatchOverrideScoresOverLibraryDefaults() {
        Award award = new Award();
        award.setId("award-1");
        award.setCategory("morality");
        award.setName("三好学生");

        AwardLevelDef level = new AwardLevelDef();
        level.setId("level-1");
        level.setCode("school");
        level.setName("校级");
        level.setSortOrder(1);

        AwardLevelScore score = new AwardLevelScore();
        score.setId("score-1");
        score.setAwardId("award-1");
        score.setLevelId("level-1");
        score.setBaseScore(new BigDecimal("6.00"));

        BatchAward override = new BatchAward();
        override.setBatchId("batch-1");
        override.setAwardId("award-1");
        override.setLevelId("level-1");
        override.setOverrideBaseScore(new BigDecimal("8.50"));

        when(batchAwardMapper.selectList(any())).thenReturn(List.of(override));
        when(awardMapper.selectList(any())).thenReturn(List.of(award));
        when(levelDefMapper.selectList(any())).thenReturn(List.of(level));
        when(levelScoreMapper.selectList(any())).thenReturn(List.of(score));

        List<AwardVO> result = service.listBatchAwards("batch-1", "morality");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLevelScores()).extracting(AwardVO.LevelScoreVO::getBaseScore)
                .containsExactly(new BigDecimal("8.50"));
    }
}
