package com.eval.jxj.dto.response;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class AwardVO {
    private String id;
    private String category;
    private String name;
    private String awardType;
    private String description;
    private List<LevelScoreVO> levelScores;

    @Data
    public static class LevelScoreVO {
        private String id;
        private String levelId;
        private String levelName;
        private String levelCode;
        private Integer sortOrder;
        private BigDecimal baseScore;
    }
}
