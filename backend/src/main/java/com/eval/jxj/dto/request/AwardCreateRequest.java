package com.eval.jxj.dto.request;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;

@Data
public class AwardCreateRequest {
    @NotBlank(message = "奖项名称不能为空")
    private String name;
    @NotBlank(message = "类别不能为空")
    private String category;
    private String awardType;
    private String description;
    private List<LevelScoreItem> levelScores;

    @Data
    public static class LevelScoreItem {
        private String levelId;
        private BigDecimal baseScore;
    }
}
