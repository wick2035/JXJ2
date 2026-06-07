package com.eval.jxj.dto.request;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class DeclarationSaveRequest {
    private String batchId;
    private List<ItemData> items;

    @Data
    public static class ItemData {
        private String id;
        private String category;
        private String awardId;
        private String levelId;
        private String customAwardName;
        private String customLevelName;
        private BigDecimal customBaseScore;
        private Integer useDowngrade;
        private String description;
        private Integer sortOrder;
        private List<AttachmentData> attachments;
    }

    @Data
    public static class AttachmentData {
        private String id;
        private Integer fileIndex;
    }
}
