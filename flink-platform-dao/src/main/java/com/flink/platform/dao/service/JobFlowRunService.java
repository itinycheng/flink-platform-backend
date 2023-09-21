package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.mapper.JobFlowRunMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** job config info. */
@Service
@DS("master_platform")
public class JobFlowRunService extends ServiceImpl<JobFlowRunMapper, JobFlowRun> {

    @Autowired
    private JobRunInfoService jobRunService;

    @Transactional
    public void deleteAllById(long flowRunId, long userId) {
        jobRunService.remove(new QueryWrapper<JobRunInfo>()
                .lambda()
                .eq(JobRunInfo::getFlowRunId, flowRunId)
                .eq(JobRunInfo::getUserId, userId));
        removeById(flowRunId);
    }
}
