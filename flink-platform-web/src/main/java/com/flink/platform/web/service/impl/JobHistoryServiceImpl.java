package com.flink.platform.web.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.web.entity.JobHistory;
import com.flink.platform.web.mapper.JobHistoryMapper;
import com.flink.platform.web.service.IJobHistoryService;
import org.springframework.stereotype.Service;

/** job modify info. */
@Service
@DS("master_platform")
public class JobHistoryServiceImpl extends ServiceImpl<JobHistoryMapper, JobHistory>
        implements IJobHistoryService {}
