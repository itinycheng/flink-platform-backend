package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.mapper.JobRunInfoMapper;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.flink.platform.common.enums.ExecutionStatus.getNonTerminals;

/** job run info. */
@Service
@DS("master_platform")
public class JobRunInfoService extends ServiceImpl<JobRunInfoMapper, JobRunInfo> {

    public List<JobRunInfo> listLastWithoutLargeFields(Long flowRunId, List<Long> jobIds) {
        return this.baseMapper.lastJobRunList(flowRunId, jobIds);
    }

    public JobRunInfo findRunningJob(@Nonnull Long jobId) {
        return getOne(new QueryWrapper<JobRunInfo>()
                .lambda()
                .eq(JobRunInfo::getJobId, jobId)
                .in(JobRunInfo::getStatus, getNonTerminals())
                .last("limit 1"));
    }
}
