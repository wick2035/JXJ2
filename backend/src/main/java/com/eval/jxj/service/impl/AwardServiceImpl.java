package com.eval.jxj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eval.jxj.common.exception.BizException;
import com.eval.jxj.dto.request.AwardCreateRequest;
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
import com.eval.jxj.service.AwardService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AwardServiceImpl implements AwardService {

    private final AwardMapper awardMapper;
    private final AwardLevelScoreMapper levelScoreMapper;
    private final AwardLevelDefMapper levelDefMapper;
    private final BatchAwardMapper batchAwardMapper;
    private final AwardCategoryMapper categoryMapper;

    public AwardServiceImpl(AwardMapper awardMapper, AwardLevelScoreMapper levelScoreMapper,
                           AwardLevelDefMapper levelDefMapper, BatchAwardMapper batchAwardMapper,
                           AwardCategoryMapper categoryMapper) {
        this.awardMapper = awardMapper;
        this.levelScoreMapper = levelScoreMapper;
        this.levelDefMapper = levelDefMapper;
        this.batchAwardMapper = batchAwardMapper;
        this.categoryMapper = categoryMapper;
    }

    @Override
    public List<AwardVO> listAwards(String category) {
        LambdaQueryWrapper<Award> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(category)) {
            wrapper.eq(Award::getCategory, category);
        }
        wrapper.orderByAsc(Award::getCategory).orderByAsc(Award::getName);
        return awardMapper.selectList(wrapper).stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public AwardVO getAward(String id) {
        Award award = awardMapper.selectById(id);
        if (award == null) throw new BizException("奖项不存在");
        return toVO(award);
    }

    @Override
    @Transactional
    public AwardVO createAward(AwardCreateRequest request) {
        validateCategoryExists(request.getCategory());
        Award award = new Award();
        award.setName(request.getName());
        award.setCategory(request.getCategory());
        award.setAwardType(normalizeAwardType(request.getAwardType()));
        award.setDescription(request.getDescription());
        awardMapper.insert(award);

        if (!"basic".equals(award.getAwardType()) && request.getLevelScores() != null) {
            saveLevelScores(award.getId(), request.getLevelScores());
        }
        return toVO(award);
    }

    @Override
    @Transactional
    public AwardVO updateAward(String id, AwardCreateRequest request) {
        Award award = awardMapper.selectById(id);
        if (award == null) throw new BizException("奖项不存在");
        validateCategoryExists(request.getCategory());

        award.setName(request.getName());
        award.setCategory(request.getCategory());
        award.setAwardType(normalizeAwardType(request.getAwardType()));
        award.setDescription(request.getDescription());
        awardMapper.updateById(award);

        if ("basic".equals(award.getAwardType())) {
            levelScoreMapper.deleteByAwardId(id);
        } else if (request.getLevelScores() != null) {
            levelScoreMapper.deleteByAwardId(id);
            saveLevelScores(id, request.getLevelScores());
        }
        return toVO(award);
    }

    @Override
    public void deleteAward(String id) {
        awardMapper.deleteById(id);
    }

    @Override
    public List<AwardLevelDef> listLevels() {
        return levelDefMapper.selectList(
                new LambdaQueryWrapper<AwardLevelDef>().orderByAsc(AwardLevelDef::getSortOrder));
    }

    @Override
    public List<AwardVO> listBatchAwards(String batchId, String category) {
        List<BatchAward> batchAwards = batchAwardMapper.selectList(
                new LambdaQueryWrapper<BatchAward>().eq(BatchAward::getBatchId, batchId));
        Map<String, List<BatchAward>> byAwardId = batchAwards.stream()
                .collect(Collectors.groupingBy(BatchAward::getAwardId));

        LambdaQueryWrapper<Award> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Award::getAwardType, "normal");
        if (StringUtils.hasText(category)) {
            wrapper.eq(Award::getCategory, category);
        }
        wrapper.orderByAsc(Award::getCategory).orderByAsc(Award::getName);

        return awardMapper.selectList(wrapper).stream().map(award -> {
            AwardVO vo = toVO(award);
            List<BatchAward> overrides = byAwardId.getOrDefault(award.getId(), List.of());
            if (!overrides.isEmpty()) {
                for (AwardVO.LevelScoreVO ls : vo.getLevelScores()) {
                    overrides.stream()
                            .filter(ba -> ba.getLevelId().equals(ls.getLevelId()) && ba.getOverrideBaseScore() != null)
                            .findFirst()
                            .ifPresent(ba -> ls.setBaseScore(ba.getOverrideBaseScore()));
                }
            }
            return vo;
        }).collect(Collectors.toList());
    }

    private void saveLevelScores(String awardId, List<AwardCreateRequest.LevelScoreItem> items) {
        for (AwardCreateRequest.LevelScoreItem item : items) {
            AwardLevelScore score = new AwardLevelScore();
            score.setAwardId(awardId);
            score.setLevelId(item.getLevelId());
            score.setBaseScore(item.getBaseScore());
            levelScoreMapper.insert(score);
        }
    }

    private AwardVO toVO(Award award) {
        AwardVO vo = new AwardVO();
        BeanUtils.copyProperties(award, vo);

        List<AwardLevelDef> levels = levelDefMapper.selectList(
                new LambdaQueryWrapper<AwardLevelDef>().orderByAsc(AwardLevelDef::getSortOrder));
        Map<String, AwardLevelDef> levelMap = levels.stream()
                .collect(Collectors.toMap(AwardLevelDef::getId, l -> l));

        List<AwardLevelScore> scores = levelScoreMapper.selectList(
                new LambdaQueryWrapper<AwardLevelScore>().eq(AwardLevelScore::getAwardId, award.getId()));

        vo.setLevelScores(scores.stream().map(s -> {
            AwardVO.LevelScoreVO lsvo = new AwardVO.LevelScoreVO();
            lsvo.setId(s.getId());
            lsvo.setLevelId(s.getLevelId());
            lsvo.setBaseScore(s.getBaseScore());
            AwardLevelDef def = levelMap.get(s.getLevelId());
            if (def != null) {
                lsvo.setLevelName(def.getName());
                lsvo.setLevelCode(def.getCode());
                lsvo.setSortOrder(def.getSortOrder());
            }
            return lsvo;
        }).sorted((a, b) -> {
            int sa = a.getSortOrder() != null ? a.getSortOrder() : 999;
            int sb = b.getSortOrder() != null ? b.getSortOrder() : 999;
            return sa - sb;
        }).collect(Collectors.toList()));
        return vo;
    }

    private String normalizeAwardType(String awardType) {
        return "basic".equals(awardType) ? "basic" : "normal";
    }

    private void validateCategoryExists(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BizException("类别不能为空");
        }
        AwardCategory category = categoryMapper.selectOne(new LambdaQueryWrapper<AwardCategory>()
                .eq(AwardCategory::getCode, code));
        if (category == null) {
            throw new BizException("类别不存在");
        }
    }
}
