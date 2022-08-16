package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.common.model.JobVertex;
import com.flink.platform.dao.entity.JobFlow;
import com.flink.platform.dao.entity.JobFlowDag;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.mapper.JobFlowMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.flink.platform.common.enums.ExecutionMode.STREAMING;
import static java.util.stream.Collectors.toList;

/** job config info. */
@Service
@DS("master_platform")
public class JobFlowService extends ServiceImpl<JobFlowMapper, JobFlow> {

    @Autowired private JobInfoService jobInfoService;

    @Autowired private JobFlowRunService jobFlowRunService;

    @Autowired private JobRunInfoService jobRunInfoService;

    @Transactional(rollbackFor = Exception.class)
    public boolean deleteAllById(long flowId) {
        List<JobInfo> jobInfoList =
                jobInfoService.list(
                        new QueryWrapper<JobInfo>().lambda().eq(JobInfo::getFlowId, flowId));
        if (CollectionUtils.isNotEmpty(jobInfoList)) {
            List<Long> jobIds = jobInfoList.stream().map(JobInfo::getId).collect(toList());
            jobRunInfoService.remove(
                    new QueryWrapper<JobRunInfo>().lambda().in(JobRunInfo::getJobId, jobIds));
            jobInfoService.remove(
                    new QueryWrapper<JobInfo>().lambda().eq(JobInfo::getFlowId, flowId));
        }
        jobFlowRunService.remove(
                new QueryWrapper<JobFlowRun>().lambda().eq(JobFlowRun::getFlowId, flowId));
        removeById(flowId);
        return true;
    }

    public boolean allStreamingJobs(JobFlowDag jobFlowDag) {
        List<Long> jobIds =
                jobFlowDag.getVertices().stream().map(JobVertex::getJobId).collect(toList());

        return jobInfoService
                .list(
                        new QueryWrapper<JobInfo>()
                                .lambda()
                                .select(JobInfo::getExecMode)
                                .in(JobInfo::getId, jobIds))
                .stream()
                .distinct()
                .allMatch(jobInfo -> jobInfo.getExecMode() == STREAMING);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateFlowById(JobFlow origin) {
        if (origin.getId() == null) {
            return;
        }

        JobFlow newJobFlow = new JobFlow();
        newJobFlow.setId(origin.getId());
        if (origin.getFlow() != null) {
            newJobFlow.setFlow(origin.getFlow());
        } else {
            newJobFlow.setFlow(new JobFlowDag());
        }
        updateById(newJobFlow);
    }
}
