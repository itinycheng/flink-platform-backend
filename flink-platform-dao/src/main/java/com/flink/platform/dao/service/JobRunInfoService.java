package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.mapper.JobRunInfoMapper;
import org.springframework.stereotype.Service;

import static com.flink.platform.common.enums.ExecutionStatus.CREATED;

/** job run info. */
@Service
@DS("master_platform")
public class JobRunInfoService extends ServiceImpl<JobRunInfoMapper, JobRunInfo> {

    public JobRunInfo createFrom(JobInfo jobInfo, Long flowRunId, String host) {
        JobRunInfo jobRunInfo = new JobRunInfo();
        jobRunInfo.setName(jobInfo.getName() + "-" + System.currentTimeMillis());
        jobRunInfo.setJobId(jobInfo.getId());
        jobRunInfo.setFlowRunId(flowRunId);
        jobRunInfo.setUserId(jobInfo.getUserId());
        jobRunInfo.setType(jobInfo.getType());
        jobRunInfo.setVersion(jobInfo.getVersion());
        jobRunInfo.setDeployMode(jobInfo.getDeployMode());
        jobRunInfo.setExecMode(jobInfo.getExecMode());
        jobRunInfo.setConfig(jobInfo.getConfig());
        jobRunInfo.setVariables(jobInfo.getVariables());
        jobRunInfo.setSubject(jobInfo.getSubject());
        jobRunInfo.setRouteUrl(jobInfo.getRouteUrl());
        jobRunInfo.setHost(host);
        jobRunInfo.setStatus(CREATED);
        return jobRunInfo;
    }
}
