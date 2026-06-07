package com.eval.jxj.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class BatchBasicAwardVO {
    private String awardId;
    private String awardName;
    private String category;
    private Integer importedCount;
    private LocalDateTime updatedAt;
    private List<StudentScoreVO> scores;

    @Data
    public static class StudentScoreVO {
        private String studentId;
        private String studentLoginId;
        private String studentName;
        private BigDecimal score;
    }
}
