package com.eval.jxj.service;

import com.eval.jxj.dto.request.AwardCategoryRequest;
import com.eval.jxj.dto.response.AwardCategoryVO;

import java.util.List;

public interface AwardCategoryService {
    List<AwardCategoryVO> listCategories();

    AwardCategoryVO createCategory(AwardCategoryRequest request);

    AwardCategoryVO updateCategory(String id, AwardCategoryRequest request);

    void deleteCategory(String id);
}
