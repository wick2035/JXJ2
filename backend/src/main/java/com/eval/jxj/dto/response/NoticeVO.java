package com.eval.jxj.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class NoticeVO {
    private String id;
    private String title;
    private String content;
    private String targetType;
    private String status;
    private String createdBy;
    private String creatorName;
    private Integer recipientCount;
    private Integer confirmedCount;
    private Integer unconfirmedCount;
    private Integer confirmed;
    private LocalDateTime confirmedAt;
    private LocalDateTime withdrawnAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<String> recipientUserIds;
    private List<RecipientVO> recipients;

    @Data
    public static class RecipientVO {
        private String id;
        private String loginId;
        private String name;
        private String role;
        private Integer confirmed;
        private LocalDateTime confirmedAt;
    }
}
