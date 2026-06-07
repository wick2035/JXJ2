package com.eval.jxj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eval.jxj.common.exception.BizException;
import com.eval.jxj.dto.request.AwardCategoryRequest;
import com.eval.jxj.dto.response.AwardCategoryVO;
import com.eval.jxj.entity.Award;
import com.eval.jxj.entity.AwardCategory;
import com.eval.jxj.entity.DeclarationItem;
import com.eval.jxj.entity.EvalBatchCategory;
import com.eval.jxj.mapper.AwardCategoryMapper;
import com.eval.jxj.mapper.AwardMapper;
import com.eval.jxj.mapper.DeclarationItemMapper;
import com.eval.jxj.mapper.EvalBatchCategoryMapper;
import com.eval.jxj.service.AwardCategoryService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AwardCategoryServiceImpl implements AwardCategoryService {

    private final AwardCategoryMapper categoryMapper;
    private final AwardMapper awardMapper;
    private final EvalBatchCategoryMapper batchCategoryMapper;
    private final DeclarationItemMapper declarationItemMapper;

    public AwardCategoryServiceImpl(AwardCategoryMapper categoryMapper, AwardMapper awardMapper,
                                    EvalBatchCategoryMapper batchCategoryMapper,
                                    DeclarationItemMapper declarationItemMapper) {
        this.categoryMapper = categoryMapper;
        this.awardMapper = awardMapper;
        this.batchCategoryMapper = batchCategoryMapper;
        this.declarationItemMapper = declarationItemMapper;
    }

    @Override
    public List<AwardCategoryVO> listCategories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<AwardCategory>()
                        .orderByAsc(AwardCategory::getSortOrder)
                        .orderByAsc(AwardCategory::getCode))
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AwardCategoryVO createCategory(AwardCategoryRequest request) {
        Long duplicate = categoryMapper.selectCount(new LambdaQueryWrapper<AwardCategory>()
                .eq(AwardCategory::getCode, request.getCode()));
        if (duplicate != null && duplicate > 0) {
            throw new BizException("类别编码已存在");
        }

        AwardCategory category = new AwardCategory();
        category.setCode(request.getCode());
        category.setName(request.getName());
        category.setColor(request.getColor());
        category.setSortOrder(request.getSortOrder());
        categoryMapper.insert(category);
        return toVO(category);
    }

    @Override
    @Transactional
    public AwardCategoryVO updateCategory(String id, AwardCategoryRequest request) {
        AwardCategory category = mustGetCategory(id);
        category.setName(request.getName());
        category.setColor(request.getColor());
        category.setSortOrder(request.getSortOrder());
        categoryMapper.updateById(category);
        return toVO(category);
    }

    @Override
    @Transactional
    public void deleteCategory(String id) {
        AwardCategory category = mustGetCategory(id);
        String code = category.getCode();
        long awardRefs = count(awardMapper.selectCount(new LambdaQueryWrapper<Award>().eq(Award::getCategory, code)));
        long batchRefs = count(batchCategoryMapper.selectCount(new LambdaQueryWrapper<EvalBatchCategory>()
                .eq(EvalBatchCategory::getCategory, code)));
        long itemRefs = count(declarationItemMapper.selectCount(new LambdaQueryWrapper<DeclarationItem>()
                .eq(DeclarationItem::getCategory, code)));
        if (awardRefs + batchRefs + itemRefs > 0) {
            throw new BizException("类别已被使用，不能删除");
        }
        categoryMapper.deleteById(id);
    }

    private AwardCategory mustGetCategory(String id) {
        AwardCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BizException("类别不存在");
        }
        return category;
    }

    private long count(Long value) {
        return value == null ? 0 : value;
    }

    private AwardCategoryVO toVO(AwardCategory category) {
        AwardCategoryVO vo = new AwardCategoryVO();
        BeanUtils.copyProperties(category, vo);
        return vo;
    }
}
