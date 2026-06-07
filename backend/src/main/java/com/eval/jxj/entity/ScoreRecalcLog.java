package com.eval.jxj.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("score_recalc_log")
public class ScoreRecalcLog {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String batchId;
    private String triggeredBy;
    private String reason;
    private Integer affectedCount;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String status;
}
