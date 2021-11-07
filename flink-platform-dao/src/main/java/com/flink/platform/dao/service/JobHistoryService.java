package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.dao.entity.JobHistory;
import com.flink.platform.dao.mapper.JobHistoryMapper;
import org.springframework.stereotype.Service;

/** job modify info. */
@Service
@DS("master_platform")
public class JobHistoryService extends ServiceImpl<JobHistoryMapper, JobHistory> {}
