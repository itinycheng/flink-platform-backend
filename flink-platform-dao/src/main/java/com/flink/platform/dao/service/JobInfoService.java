package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.task.FlowJob;
import com.flink.platform.dao.mapper.JobInfoMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

/** job config info. */
@Service
@DS("master_platform")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class JobInfoService extends ServiceImpl<JobInfoMapper, JobInfo> {

    public static final Set<String> LARGE_FIELDS = Set.of("params", "subject");

    private final JobRunInfoService jobRunService;

    private final JobFlowRunService jobFlowRunService;

    public List<JobInfo> listWithoutLargeFields(Collection<Long> jobIds) {
        if (CollectionUtils.isEmpty(jobIds)) {
            return Collections.emptyList();
        }

        return super.list(new QueryWrapper<JobInfo>()
                .lambda()
                .select(JobInfo.class, this::isNonLargeField)
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

    public JobInfo findRunnableJobUsingJobFlow(Long flowId) {
        var jobs = baseMapper.queryRunnableJobUsingJobFlow(flowId);

        return jobs.stream()
                .filter(job -> job != null && job.getConfig() instanceof FlowJob)
                .filter(job -> ((FlowJob) job.getConfig()).getFlowId() == flowId)
                .findAny()
                .orElse(null);
    }

    public boolean isNonLargeField(TableFieldInfo field) {
        return !LARGE_FIELDS.contains(field.getProperty());
    }
}
