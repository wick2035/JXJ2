package com.eval.jxj.dto.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

@Data
public class AwardCategoryRequest {
    @NotBlank(message = "类别编码不能为空")
    @Pattern(regexp = "^[a-z][a-z0-9_]{1,49}$", message = "类别编码只能使用小写字母、数字和下划线，并以字母开头")
    private String code;

    @NotBlank(message = "类别名称不能为空")
    private String name;

    @NotBlank(message = "类别颜色不能为空")
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "类别颜色必须是十六进制颜色")
    private String color;

    @NotNull(message = "排序不能为空")
    private Integer sortOrder;
}
