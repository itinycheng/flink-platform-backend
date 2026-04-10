package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flink.platform.common.enums.OperationType;
import com.flink.platform.dao.entity.AuditLog;
import com.flink.platform.dao.service.AuditLogService;
import com.flink.platform.web.entity.response.ResultInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

import static com.flink.platform.common.util.DateUtil.GLOBAL_DATE_TIME_FORMAT;
import static com.flink.platform.web.entity.response.ResultInfo.success;
import static java.util.Objects.nonNull;

/** Audit log query API. */
@RestController
@RequestMapping("/auditLog")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping(value = "/get/{id}")
    public ResultInfo<AuditLog> get(@PathVariable Long id) {
        return success(auditLogService.getById(id));
    }

    @GetMapping(value = "/page")
    public ResultInfo<IPage<AuditLog>> page(
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "20") Integer size,
            @RequestParam(name = "entityType", required = false) String entityType,
            @RequestParam(name = "entityId", required = false) Long entityId,
            @RequestParam(name = "operation", required = false) OperationType operation,
            @RequestParam(name = "operatorId", required = false) Long operatorId,
            @DateTimeFormat(pattern = GLOBAL_DATE_TIME_FORMAT) @RequestParam(name = "startTime", required = false)
                    LocalDateTime startTime,
            @DateTimeFormat(pattern = GLOBAL_DATE_TIME_FORMAT) @RequestParam(name = "endTime", required = false)
                    LocalDateTime endTime) {
        var query = new QueryWrapper<AuditLog>()
                .lambda()
                .eq(nonNull(entityType), AuditLog::getEntityType, entityType)
                .eq(nonNull(entityId), AuditLog::getEntityId, entityId)
                .eq(nonNull(operation), AuditLog::getOperation, operation)
                .eq(nonNull(operatorId), AuditLog::getOperatorId, operatorId)
                .between(nonNull(startTime) && nonNull(endTime), AuditLog::getOperateTime, startTime, endTime)
                .orderByDesc(AuditLog::getId);
        return success(auditLogService.page(new Page<>(page, size), query));
    }
}
