package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.common.enums.JobStatus;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.task.FlowJob;
import com.flink.platform.dao.mapper.JobInfoMapper;
import com.flink.platform.dao.query.JobPageQuery;
import com.flink.platform.dao.view.JobDetails;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/** job config info. */
@Service
@DS("master_platform")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class JobInfoService extends ServiceImpl<JobInfoMapper, JobInfo> {

    public static final Set<String> LARGE_FIELDS = Set.of("params", "subject");

    private final JobRunInfoService jobRunService;

    private final JobFlowRunService jobFlowRunService;

    @Transactional(rollbackFor = Exception.class)
    public void removeAllById(long jobId) {
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
        if (!flowRunIds.isEmpty()) {
            jobFlowRunService.remove(new QueryWrapper<JobFlowRun>().lambda().in(JobFlowRun::getId, flowRunIds));
        }

        jobRunService.remove(new QueryWrapper<JobRunInfo>().lambda().in(JobRunInfo::getJobId, jobId));
        remove(new QueryWrapper<JobInfo>().lambda().in(JobInfo::getId, jobId));
    }

    public List<JobInfo> listWithoutLargeFields(Collection<Long> jobIds) {
        if (CollectionUtils.isEmpty(jobIds)) {
            return Collections.emptyList();
        }

        return super.list(new QueryWrapper<JobInfo>()
                .lambda()
                .select(JobInfo.class, this::isNonLargeField)
                .in(JobInfo::getId, jobIds));
    }

    public JobDetails findRunnableJobUsingJobFlow(Long flowId) {
        return baseMapper.queryRunnableJobUsingJobFlow(flowId).stream()
                .filter(job -> job.getConfig() instanceof FlowJob flowJob && flowJob.getFlowId() == flowId)
                .filter(JobDetails::isStillInUse)
                .findAny()
                .orElse(null);
    }

    public IPage<JobDetails> pageDetails(JobPageQuery query) {
        var wrapper = new LambdaQueryWrapper<JobInfo>()
                .eq(nonNull(query.getId()), JobInfo::getId, query.getId())
                .eq(nonNull(query.getFlowId()), JobInfo::getFlowId, query.getFlowId())
                .like(isNotBlank(query.getName()), JobInfo::getName, query.getName())
                .between(query.hasTimeRange(), JobInfo::getCreateTime, query.getStartTime(), query.getEndTime())
                .eq(nonNull(query.getStatus()), JobInfo::getStatus, query.getStatus())
                .ne(isNull(query.getStatus()), JobInfo::getStatus, JobStatus.DELETE)
                .notIn(isNotEmpty(query.getExcludeJobIds()), JobInfo::getId, query.getExcludeJobIds())
                .orderByDesc(query.isSortByIdDesc(), JobInfo::getId);
        return baseMapper.selectPageDetails(new Page<>(query.getPage(), query.getSize()), wrapper);
    }

    public boolean isNonLargeField(TableFieldInfo field) {
        return !LARGE_FIELDS.contains(field.getProperty());
    }
}
