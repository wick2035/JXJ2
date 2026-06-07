package com.eval.jxj.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("batch_class")
public class BatchClass {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String batchId;
    private String grade;
    private String className;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
