package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.enums.JobFlowStatus;
import com.flink.platform.common.enums.JobFlowType;
import com.flink.platform.common.enums.JobStatus;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.mapper.JobRunInfoMapper;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.flink.platform.common.enums.ExecutionStatus.FAILURE_STATUSES;
import static com.flink.platform.common.enums.ExecutionStatus.getNonTerminals;
import static com.flink.platform.common.enums.JobType.SUB_FLOW;

/** job run info. */
@Service
@DS("master_platform")
public class JobRunInfoService extends ServiceImpl<JobRunInfoMapper, JobRunInfo> {

    private static final Set<String> LARGE_FIELDS = Set.of("backInfo", "params", "subject");

    public static final List<ExecutionStatus> UNEXPECTED = new ArrayList<>() {
        {
            addAll(FAILURE_STATUSES);
            add(ExecutionStatus.KILLABLE);
        }
    };

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

    public List<JobRunInfo> getJobRunsWithUnexpectedStatus() {
        return this.baseMapper.queryLastJobRuns(
                JobFlowType.JOB_LIST, JobFlowStatus.SCHEDULING, JobStatus.ONLINE, UNEXPECTED);
    }

    public List<JobRunInfo> findJobsOfSubflowType(@Nonnull Long flowRunId) {
        return list(new QueryWrapper<JobRunInfo>()
                .lambda()
                .select(JobRunInfo::getId, JobRunInfo::getBackInfo, JobRunInfo::getConfig)
                .eq(JobRunInfo::getFlowRunId, flowRunId)
                .in(JobRunInfo::getType, SUB_FLOW));
    }

    public JobRunInfo getLiteById(Serializable id) {
        return getOne(new QueryWrapper<JobRunInfo>()
                .lambda()
                .select(JobRunInfo.class, this::isNonLargeField)
                .eq(JobRunInfo::getId, id));
    }

    public boolean isNonLargeField(TableFieldInfo field) {
        return !LARGE_FIELDS.contains(field.getProperty());
    }
}
