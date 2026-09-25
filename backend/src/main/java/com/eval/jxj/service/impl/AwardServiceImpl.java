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
import com.eval.jxj.entity.DeclarationItem;
import com.eval.jxj.mapper.AwardCategoryMapper;
import com.eval.jxj.mapper.AwardLevelDefMapper;
import com.eval.jxj.mapper.AwardLevelScoreMapper;
import com.eval.jxj.mapper.AwardMapper;
import com.eval.jxj.mapper.BatchAwardMapper;
import com.eval.jxj.mapper.DeclarationItemMapper;
import com.eval.jxj.service.AwardService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AwardServiceImpl implements AwardService {

    private final AwardMapper awardMapper;
    private final AwardLevelScoreMapper levelScoreMapper;
    private final AwardLevelDefMapper levelDefMapper;
    private final BatchAwardMapper batchAwardMapper;
    private final AwardCategoryMapper categoryMapper;
    private final DeclarationItemMapper itemMapper;

    public AwardServiceImpl(AwardMapper awardMapper, AwardLevelScoreMapper levelScoreMapper,
                           AwardLevelDefMapper levelDefMapper, BatchAwardMapper batchAwardMapper,
                           AwardCategoryMapper categoryMapper, DeclarationItemMapper itemMapper) {
        this.awardMapper = awardMapper;
        this.levelScoreMapper = levelScoreMapper;
        this.levelDefMapper = levelDefMapper;
        this.batchAwardMapper = batchAwardMapper;
        this.categoryMapper = categoryMapper;
        this.itemMapper = itemMapper;
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
            syncLevelScores(award.getId(), request.getLevelScores());
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
            syncLevelScores(id, List.of());
        } else if (request.getLevelScores() != null) {
            syncLevelScores(id, request.getLevelScores());
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
                new LambdaQueryWrapper<AwardLevelDef>()
                        .isNull(AwardLevelDef::getAwardId)
                        .orderByAsc(AwardLevelDef::getSortOrder));
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

    private void syncLevelScores(String awardId, List<AwardCreateRequest.LevelScoreItem> items) {
        List<AwardLevelDef> shared = listLevels();
        List<AwardLevelDef> owned = levelDefMapper.selectList(new LambdaQueryWrapper<AwardLevelDef>()
                .eq(AwardLevelDef::getAwardId, awardId));
        List<AwardLevelScore> previous = levelScoreMapper.selectList(new LambdaQueryWrapper<AwardLevelScore>()
                .eq(AwardLevelScore::getAwardId, awardId));

        Map<String, AwardLevelDef> available = new HashMap<>();
        Set<String> usedNames = new HashSet<>();
        for (AwardLevelDef level : shared) {
            available.put(level.getId(), level);
            usedNames.add(level.getName().trim().toLowerCase(java.util.Locale.ROOT));
        }
        for (AwardLevelDef level : owned) available.put(level.getId(), level);

        Set<String> requestedIds = new HashSet<>();
        List<AwardLevelDef> newLevels = new ArrayList<>();
        List<AwardLevelDef> changedLevels = new ArrayList<>();
        List<AwardLevelDef> desiredLevels = new ArrayList<>();
        int customOrder = 6;
        for (AwardCreateRequest.LevelScoreItem item : items) {
            BigDecimal value = item.getBaseScore();
            if (value == null || value.compareTo(BigDecimal.ZERO) <= 0
                    || value.compareTo(new BigDecimal("999999.99")) > 0 || value.scale() > 2) {
                throw new BizException("级别分值须大于 0，最多两位小数且不超过 999999.99");
            }

            AwardLevelDef level;
            if (StringUtils.hasText(item.getLevelId())) {
                if (!requestedIds.add(item.getLevelId())) throw new BizException("同一级别不能重复配置");
                level = available.get(item.getLevelId());
                if (level == null) throw new BizException("级别不存在或不属于当前奖项");
                if (level.getAwardId() == null) {
                    if (StringUtils.hasText(item.getLevelName())
                            && !level.getName().equals(item.getLevelName().trim())) {
                        throw new BizException("公共级别名称不能在奖项中修改");
                    }
                } else {
                    String name = validateCustomName(item.getLevelName());
                    if (!level.getName().equals(name)) {
                        assertNotReferenced(awardId, level.getId(), "改名");
                        level.setName(name);
                    }
                    level.setSortOrder(customOrder++);
                    changedLevels.add(level);
                    if (!usedNames.add(name.toLowerCase(java.util.Locale.ROOT))) {
                        throw new BizException("级别名称不能与其他级别重复：" + name);
                    }
                }
            } else {
                String name = validateCustomName(item.getLevelName());
                if (!usedNames.add(name.toLowerCase(java.util.Locale.ROOT))) {
                    throw new BizException("级别名称不能与其他级别重复：" + name);
                }
                level = new AwardLevelDef();
                level.setAwardId(awardId);
                level.setCode("custom_" + UUID.randomUUID().toString().replace("-", ""));
                level.setName(name);
                level.setSortOrder(customOrder++);
                newLevels.add(level);
            }
            desiredLevels.add(level);
        }

        for (AwardLevelScore score : previous) {
            if (desiredLevels.stream().noneMatch(level -> score.getLevelId().equals(level.getId()))) {
                assertNotReferenced(awardId, score.getLevelId(), "移除");
            }
        }
        for (AwardLevelDef level : owned) {
            if (!requestedIds.contains(level.getId())) {
                assertNotReferenced(awardId, level.getId(), "移除");
            }
        }

        levelScoreMapper.deleteByAwardId(awardId);
        for (AwardLevelDef level : changedLevels) levelDefMapper.updateById(level);
        for (AwardLevelDef level : newLevels) levelDefMapper.insert(level);
        for (int i = 0; i < desiredLevels.size(); i++) {
            AwardLevelScore score = new AwardLevelScore();
            score.setAwardId(awardId);
            score.setLevelId(desiredLevels.get(i).getId());
            score.setBaseScore(items.get(i).getBaseScore());
            levelScoreMapper.insert(score);
        }
        for (AwardLevelDef level : owned) {
            if (!requestedIds.contains(level.getId())) levelDefMapper.deleteById(level.getId());
        }
    }

    private String validateCustomName(String value) {
        if (!StringUtils.hasText(value)) throw new BizException("请输入自定义级别名称");
        String name = value.trim();
        if (name.length() > 100) throw new BizException("级别名称不能超过 100 个字符");
        return name;
    }

    private void assertNotReferenced(String awardId, String levelId, String action) {
        Long declarations = itemMapper.selectCount(new LambdaQueryWrapper<DeclarationItem>()
                .eq(DeclarationItem::getAwardId, awardId)
                .eq(DeclarationItem::getLevelId, levelId));
        Long batches = batchAwardMapper.selectCount(new LambdaQueryWrapper<BatchAward>()
                .eq(BatchAward::getAwardId, awardId)
                .eq(BatchAward::getLevelId, levelId));
        if ((declarations != null && declarations > 0) || (batches != null && batches > 0)) {
            throw new BizException("级别已被申报或批次分值配置使用，不能" + action);
        }
    }

    private AwardVO toVO(Award award) {
        AwardVO vo = new AwardVO();
        BeanUtils.copyProperties(award, vo);

        List<AwardLevelDef> levels = levelDefMapper.selectList(
                new LambdaQueryWrapper<AwardLevelDef>()
                        .and(query -> query.isNull(AwardLevelDef::getAwardId)
                                .or().eq(AwardLevelDef::getAwardId, award.getId()))
                        .orderByAsc(AwardLevelDef::getSortOrder));
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
