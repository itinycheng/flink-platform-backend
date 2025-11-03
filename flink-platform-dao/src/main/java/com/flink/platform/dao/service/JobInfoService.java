package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.mapper.JobInfoMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.flink.platform.common.enums.JobType.SUB_FLOW;
import static com.flink.platform.dao.entity.JobInfo.LARGE_FIELDS;
import static java.util.stream.Collectors.toSet;

/** job config info. */
@Service
@DS("master_platform")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class JobInfoService extends ServiceImpl<JobInfoMapper, JobInfo> {

    private final JobRunInfoService jobRunService;

    private final JobFlowRunService jobFlowRunService;

    public List<JobInfo> listWithoutLargeFields(Collection<Long> jobIds) {
        if (CollectionUtils.isEmpty(jobIds)) {
            return Collections.emptyList();
        }

        return super.list(new QueryWrapper<JobInfo>()
                .lambda()
                .select(JobInfo.class, field -> !LARGE_FIELDS.contains(field.getProperty()))
                .in(JobInfo::getId, jobIds));
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteAllById(long jobId) {
        var flowRunIds = jobRunService
                .list(new QueryWrapper<JobRunInfo>()
                        .select("distinct flow_run_id")
                        .lambda()
                        .eq(JobRunInfo::getJobId, jobId)
                        .groupBy(JobRunInfo::getFlowRunId)
                        .having("count(1) <= 1"))
                .stream()
                .map(JobRunInfo::getFlowRunId)
                .collect(toSet());

        jobRunService.remove(new QueryWrapper<JobRunInfo>().lambda().in(JobRunInfo::getJobId, jobId));
        jobFlowRunService.remove(new QueryWrapper<JobFlowRun>().lambda().in(JobFlowRun::getId, flowRunIds));
        remove(new QueryWrapper<JobInfo>().lambda().in(JobInfo::getId, jobId));
    }

    public JobInfo findRunnableJobUsingSubFlow(Long flowId) {
        var jobList = baseMapper.queryJobConfigAndFlowStatus(flowId, SUB_FLOW);
        if (CollectionUtils.isEmpty(jobList)) {
            return null;
        }

        return jobList.stream()
                .filter(job -> job.getJobFlowStatus() != null)
                .filter(job -> job.getJobFlowStatus().isRunnable())
                .findAny()
                .orElse(null);
    }
}
