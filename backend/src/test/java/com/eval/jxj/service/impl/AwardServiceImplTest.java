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
import com.eval.jxj.mapper.DeclarationItemMapper;
import com.eval.jxj.common.exception.BizException;
import com.eval.jxj.dto.request.AwardCreateRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

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
    @Mock
    private DeclarationItemMapper itemMapper;

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

    @Test
    void createAward_createsPrivateLevelAndScore() {
        AwardCreateRequest request = requestWithScore(null, "国家级一等奖", "12.50");
        AwardCategory category = new AwardCategory();
        when(categoryMapper.selectOne(any())).thenReturn(category);
        when(awardMapper.insert(any())).thenAnswer(invocation -> {
            ((Award) invocation.getArgument(0)).setId("award-1");
            return 1;
        });
        when(levelDefMapper.selectList(any())).thenReturn(List.of());
        when(levelScoreMapper.selectList(any())).thenReturn(List.of());
        when(levelDefMapper.insert(any())).thenAnswer(invocation -> {
            ((AwardLevelDef) invocation.getArgument(0)).setId("private-1");
            return 1;
        });

        service.createAward(request);

        ArgumentCaptor<AwardLevelDef> level = ArgumentCaptor.forClass(AwardLevelDef.class);
        ArgumentCaptor<AwardLevelScore> score = ArgumentCaptor.forClass(AwardLevelScore.class);
        verify(levelDefMapper).insert(level.capture());
        verify(levelScoreMapper).insert(score.capture());
        assertThat(level.getValue().getAwardId()).isEqualTo("award-1");
        assertThat(level.getValue().getName()).isEqualTo("国家级一等奖");
        assertThat(level.getValue().getCode()).startsWith("custom_");
        assertThat(score.getValue().getLevelId()).isEqualTo("private-1");
        assertThat(score.getValue().getBaseScore()).isEqualByComparingTo("12.50");
    }

    @Test
    void copiedTemplateLevelGetsIndependentIdentity() {
        when(categoryMapper.selectOne(any())).thenReturn(new AwardCategory());
        when(levelDefMapper.selectList(any())).thenReturn(List.of());
        when(levelScoreMapper.selectList(any())).thenReturn(List.of());
        AtomicInteger sequence = new AtomicInteger();
        when(awardMapper.insert(any())).thenAnswer(invocation -> {
            ((Award) invocation.getArgument(0)).setId("award-" + sequence.incrementAndGet());
            return 1;
        });
        when(levelDefMapper.insert(any())).thenAnswer(invocation -> {
            ((AwardLevelDef) invocation.getArgument(0)).setId("level-" + sequence.get());
            return 1;
        });

        service.createAward(requestWithScore(null, "国家级一等奖", "12.50"));
        service.createAward(requestWithScore(null, "国家级一等奖", "12.50"));

        ArgumentCaptor<AwardLevelDef> levels = ArgumentCaptor.forClass(AwardLevelDef.class);
        verify(levelDefMapper, org.mockito.Mockito.times(2)).insert(levels.capture());
        assertThat(levels.getAllValues()).extracting(AwardLevelDef::getAwardId)
                .containsExactly("award-1", "award-2");
        assertThat(levels.getAllValues()).extracting(AwardLevelDef::getCode)
                .doesNotHaveDuplicates();
    }

    @Test
    void createAward_rejectsAnotherAwardsPrivateLevel() {
        AwardCreateRequest request = requestWithScore("other-level", null, "5.00");
        when(categoryMapper.selectOne(any())).thenReturn(new AwardCategory());
        when(levelDefMapper.selectList(any())).thenReturn(List.of());
        when(levelScoreMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.createAward(request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不属于当前奖项");
        verify(levelScoreMapper, never()).insert(any());
    }

    @Test
    void createAward_rejectsDuplicatePrivateNames() {
        AwardCreateRequest request = requestWithScore(null, "国家级一等奖", "5.00");
        AwardCreateRequest.LevelScoreItem duplicate = new AwardCreateRequest.LevelScoreItem();
        duplicate.setLevelName(" 国家级一等奖 ");
        duplicate.setBaseScore(new BigDecimal("4.00"));
        request.setLevelScores(List.of(request.getLevelScores().get(0), duplicate));
        when(categoryMapper.selectOne(any())).thenReturn(new AwardCategory());
        when(levelDefMapper.selectList(any())).thenReturn(List.of());
        when(levelScoreMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.createAward(request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("重复");
        verify(levelDefMapper, never()).insert(any());
    }

    @Test
    void updateAward_rejectsRemovingReferencedLevel() {
        Award award = new Award();
        award.setId("award-1");
        AwardLevelDef owned = privateLevel("private-1", "award-1", "国家级一等奖");
        AwardLevelScore score = score("award-1", "private-1", "5.00");
        AwardCreateRequest request = new AwardCreateRequest();
        request.setName("比赛");
        request.setCategory("ability");
        request.setLevelScores(List.of());
        when(awardMapper.selectById("award-1")).thenReturn(award);
        when(categoryMapper.selectOne(any())).thenReturn(new AwardCategory());
        when(levelDefMapper.selectList(any())).thenReturn(List.of(), List.of(owned));
        when(levelScoreMapper.selectList(any())).thenReturn(List.of(score));
        when(itemMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.updateAward("award-1", request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能移除");
        verify(levelScoreMapper, never()).deleteByAwardId(anyString());
    }

    @Test
    void updateAward_rejectsRenamingLevelUsedByBatch() {
        Award award = new Award();
        award.setId("award-1");
        AwardLevelDef owned = privateLevel("private-1", "award-1", "国家级一等奖");
        AwardCreateRequest request = requestWithScore("private-1", "国家级二等奖", "5.00");
        when(awardMapper.selectById("award-1")).thenReturn(award);
        when(categoryMapper.selectOne(any())).thenReturn(new AwardCategory());
        when(levelDefMapper.selectList(any())).thenReturn(List.of(), List.of(owned));
        when(levelScoreMapper.selectList(any())).thenReturn(List.of());
        when(batchAwardMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.updateAward("award-1", request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能改名");
        verify(levelScoreMapper, never()).deleteByAwardId(anyString());
    }

    private AwardCreateRequest requestWithScore(String levelId, String levelName, String value) {
        AwardCreateRequest request = new AwardCreateRequest();
        request.setName("比赛");
        request.setCategory("ability");
        AwardCreateRequest.LevelScoreItem item = new AwardCreateRequest.LevelScoreItem();
        item.setLevelId(levelId);
        item.setLevelName(levelName);
        item.setBaseScore(new BigDecimal(value));
        request.setLevelScores(List.of(item));
        return request;
    }

    private AwardLevelDef privateLevel(String id, String awardId, String name) {
        AwardLevelDef level = new AwardLevelDef();
        level.setId(id);
        level.setAwardId(awardId);
        level.setName(name);
        return level;
    }

    private AwardLevelScore score(String awardId, String levelId, String value) {
        AwardLevelScore score = new AwardLevelScore();
        score.setAwardId(awardId);
        score.setLevelId(levelId);
        score.setBaseScore(new BigDecimal(value));
        return score;
    }
}
