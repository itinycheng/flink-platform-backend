package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.mapper.JobFlowRunMapper;
import org.springframework.stereotype.Service;

/** job config info. */
@Service
@DS("master_platform")
public class JobFlowRunService extends ServiceImpl<JobFlowRunMapper, JobFlowRun> {}
