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
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.flink.platform.common.enums.ExecutionStatus.CREATED;
import static com.flink.platform.common.enums.ExecutionStatus.FAILURE_STATUSES;
import static com.flink.platform.common.enums.ExecutionStatus.UNFINISHED_STATUSES;
import static com.flink.platform.common.enums.JobType.SUB_FLOW;

/** job run info. */
@Service
@DS("master_platform")
public class JobRunInfoService extends ServiceImpl<JobRunInfoMapper, JobRunInfo> {

    private static final Set<String> LARGE_FIELDS = Set.of("subject");

    public static final List<ExecutionStatus> UNEXPECTED = new ArrayList<>() {
        {
            addAll(FAILURE_STATUSES);
            add(ExecutionStatus.KILLING);
        }
    };

    public int updateStatusFromAnyTo(Long id, Collection<ExecutionStatus> expected, JobRunInfo update) {
        if (id == null || expected.isEmpty() || update.getStatus() == null) {
            return 0;
        }
        return baseMapper.update(
                update,
                new QueryWrapper<JobRunInfo>()
                        .lambda()
                        .eq(JobRunInfo::getId, id)
                        .in(JobRunInfo::getStatus, expected));
    }

    public List<JobRunInfo> listLiteRunsLatestFirst(long jobId, long flowRunId) {
        return list(new QueryWrapper<JobRunInfo>()
                .lambda()
                .select(JobRunInfo.class, this::isLiteField)
                .eq(JobRunInfo::getJobId, jobId)
                .eq(JobRunInfo::getFlowRunId, flowRunId)
                .orderByDesc(JobRunInfo::getId));
    }

    public @Nullable JobRunInfo findCreatedRun(long jobId, long flowRunId) {
        return getOne(new QueryWrapper<JobRunInfo>()
                .lambda()
                .eq(JobRunInfo::getJobId, jobId)
                .eq(JobRunInfo::getFlowRunId, flowRunId)
                .eq(JobRunInfo::getStatus, CREATED)
                .last("LIMIT 1"));
    }

    public List<JobRunInfo> listLatestLiteRunPerJob(Long flowRunId, List<Long> jobIds) {
        return this.baseMapper.selectLatestLiteRunPerJob(flowRunId, jobIds);
    }

    public JobRunInfo findUnfinishedByJobId(Long jobId) {
        return getOne(new QueryWrapper<JobRunInfo>()
                .lambda()
                .eq(JobRunInfo::getJobId, jobId)
                .in(JobRunInfo::getStatus, UNFINISHED_STATUSES)
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

    public JobRunInfo getLiteById(Long id) {
        return getOne(new QueryWrapper<JobRunInfo>()
                .lambda()
                .select(JobRunInfo.class, this::isLiteField)
                .eq(JobRunInfo::getId, id));
    }

    public boolean isLiteField(TableFieldInfo field) {
        return field.getTypeHandler() == null && !LARGE_FIELDS.contains(field.getProperty());
    }

    /** The run list needs backInfo for its tracking link; internal lite reads do not. */
    public boolean isPageField(TableFieldInfo field) {
        return isLiteField(field) || "backInfo".equals(field.getProperty());
    }

    public List<Map<String, Object>> countJobRunGroupByStatus(
            Long workspaceId, LocalDateTime startTime, LocalDateTime endTime) {
        return baseMapper.countJobRunGroupByStatus(workspaceId, startTime, endTime);
    }

    public List<JobRunInfo> listLiteUnfinishedRuns(Long flowRunId) {
        return list(new QueryWrapper<JobRunInfo>()
                .lambda()
                .select(JobRunInfo.class, this::isLiteField)
                .eq(JobRunInfo::getFlowRunId, flowRunId)
                .in(JobRunInfo::getStatus, UNFINISHED_STATUSES));
    }
}
