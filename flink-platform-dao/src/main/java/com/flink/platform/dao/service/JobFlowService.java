package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
