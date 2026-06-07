package com.eval.jxj.dto.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.List;

@Data
public class NoticeSaveRequest {
    @NotBlank(message = "公告标题不能为空")
    private String title;
    @NotBlank(message = "公告内容不能为空")
    private String content;
    @NotBlank(message = "公告范围不能为空")
    private String targetType;
    private List<String> recipientUserIds;
}
