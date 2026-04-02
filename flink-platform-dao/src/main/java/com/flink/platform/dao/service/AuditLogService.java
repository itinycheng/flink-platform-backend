package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.dao.entity.AuditLog;
import com.flink.platform.dao.mapper.AuditLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Audit log service. */
@Slf4j
@Service
@DS("master_platform")
public class AuditLogService extends ServiceImpl<AuditLogMapper, AuditLog> {}
