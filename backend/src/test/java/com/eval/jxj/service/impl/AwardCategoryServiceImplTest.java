package com.eval.jxj.service.impl;

import com.eval.jxj.common.exception.BizException;
import com.eval.jxj.dto.request.AwardCategoryRequest;
import com.eval.jxj.dto.response.AwardCategoryVO;
import com.eval.jxj.entity.AwardCategory;
import com.eval.jxj.mapper.AwardCategoryMapper;
import com.eval.jxj.mapper.AwardMapper;
import com.eval.jxj.mapper.DeclarationItemMapper;
import com.eval.jxj.mapper.EvalBatchCategoryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AwardCategoryServiceImplTest {

    @Mock
    private AwardCategoryMapper categoryMapper;
    @Mock
    private AwardMapper awardMapper;
    @Mock
    private EvalBatchCategoryMapper batchCategoryMapper;
    @Mock
    private DeclarationItemMapper declarationItemMapper;

    @InjectMocks
    private AwardCategoryServiceImpl service;

    @Test
    void createCategory_rejectsDuplicateCode() {
        AwardCategoryRequest request = new AwardCategoryRequest();
        request.setCode("innovation");
        request.setName("创新发展");
        request.setColor("#722ED1");
        request.setSortOrder(4);
        when(categoryMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.createCategory(request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("类别编码已存在");
    }

    @Test
    void updateCategory_keepsOriginalCodeAndUpdatesDisplayFields() {
        AwardCategory existing = new AwardCategory();
        existing.setId("cat-1");
        existing.setCode("morality");
        existing.setName("品德表现");
        existing.setColor("#1677FF");
        existing.setSortOrder(1);
        when(categoryMapper.selectById("cat-1")).thenReturn(existing);

        AwardCategoryRequest request = new AwardCategoryRequest();
        request.setCode("changed");
        request.setName("德育表现");
        request.setColor("#13C2C2");
        request.setSortOrder(2);

        AwardCategoryVO result = service.updateCategory("cat-1", request);

        ArgumentCaptor<AwardCategory> captor = ArgumentCaptor.forClass(AwardCategory.class);
        verify(categoryMapper).updateById(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("morality");
        assertThat(result.getCode()).isEqualTo("morality");
        assertThat(result.getName()).isEqualTo("德育表现");
    }

    @Test
    void deleteCategory_rejectsCategoryReferencedByAwards() {
        AwardCategory existing = new AwardCategory();
        existing.setId("cat-1");
        existing.setCode("morality");
        when(categoryMapper.selectById("cat-1")).thenReturn(existing);
        when(awardMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteCategory("cat-1"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("类别已被使用");
    }

    @Test
    void listCategories_ordersBySortOrder() {
        AwardCategory first = new AwardCategory();
        first.setId("cat-1");
        first.setCode("morality");
        first.setName("品德表现");
        first.setColor("#1677FF");
        first.setSortOrder(1);
        when(categoryMapper.selectList(any())).thenReturn(List.of(first));

        List<AwardCategoryVO> result = service.listCategories();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCode()).isEqualTo("morality");
    }
}
