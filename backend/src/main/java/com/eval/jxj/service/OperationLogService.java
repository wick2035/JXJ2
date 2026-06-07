package com.eval.jxj.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eval.jxj.entity.OperationLog;
import com.eval.jxj.mapper.OperationLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class OperationLogService {

    private static final Logger log = LoggerFactory.getLogger(OperationLogService.class);

    private final OperationLogMapper logMapper;

    public OperationLogService(OperationLogMapper logMapper) {
        this.logMapper = logMapper;
    }

    /** 写入一条操作记录，任何异常都吞掉，绝不影响主流程。 */
    public void record(OperationLog entity) {
        try {
            logMapper.insert(entity);
        } catch (Exception e) {
            log.warn("写入操作记录失败: {}", e.getMessage());
        }
    }

    public Page<OperationLog> page(int page, int size, String role, String module,
                                   String keyword, String startDate, String endDate) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(role)) {
            wrapper.eq(OperationLog::getOperatorRole, role);
        }
        if (StringUtils.hasText(module)) {
            wrapper.eq(OperationLog::getModule, module);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(OperationLog::getOperatorName, keyword)
                    .or().like(OperationLog::getOperatorLoginId, keyword)
                    .or().like(OperationLog::getAction, keyword));
        }
        if (StringUtils.hasText(startDate)) {
            wrapper.ge(OperationLog::getCreatedAt, LocalDateTime.of(LocalDate.parse(startDate), LocalTime.MIN));
        }
        if (StringUtils.hasText(endDate)) {
            wrapper.le(OperationLog::getCreatedAt, LocalDateTime.of(LocalDate.parse(endDate), LocalTime.MAX));
        }
        wrapper.orderByDesc(OperationLog::getCreatedAt);
        return logMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /** 软删除（MyBatis Plus @TableLogic 自动转为 is_deleted=1）。 */
    public void softDelete(List<String> ids) {
        if (ids != null && !ids.isEmpty()) {
            logMapper.deleteBatchIds(ids);
        }
    }
}
