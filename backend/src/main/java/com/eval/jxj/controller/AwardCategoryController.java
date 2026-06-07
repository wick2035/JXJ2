package com.eval.jxj.controller;

import com.eval.jxj.common.Result;
import com.eval.jxj.dto.request.AwardCategoryRequest;
import com.eval.jxj.dto.response.AwardCategoryVO;
import com.eval.jxj.service.AwardCategoryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class AwardCategoryController {

    private final AwardCategoryService categoryService;

    public AwardCategoryController(AwardCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public Result<List<AwardCategoryVO>> listCategories() {
        return Result.ok(categoryService.listCategories());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<AwardCategoryVO> createCategory(@Valid @RequestBody AwardCategoryRequest request) {
        return Result.ok(categoryService.createCategory(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<AwardCategoryVO> updateCategory(@PathVariable String id,
                                                  @Valid @RequestBody AwardCategoryRequest request) {
        return Result.ok(categoryService.updateCategory(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<?> deleteCategory(@PathVariable String id) {
        categoryService.deleteCategory(id);
        return Result.ok();
    }
}
