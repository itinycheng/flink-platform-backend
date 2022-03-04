package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.common.model.JobVertex;
import com.flink.platform.dao.entity.JobFlow;
import com.flink.platform.dao.entity.JobFlowDag;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.mapper.JobFlowMapper;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** job config info. */
@Service
@DS("master_platform")
public class JobFlowService extends ServiceImpl<JobFlowMapper, JobFlow> {

    @Autowired private JobInfoService jobInfoService;

    @Transactional(rollbackFor = Exception.class)
    public boolean deleteAllById(long flowId) {
        JobFlow jobFlow = getById(flowId);
        if (jobFlow.getFlow() != null) {
            jobFlow.getFlow().getVertices().stream()
                    .map(JobVertex::getJobId)
                    .filter(Objects::nonNull)
                    .forEach(jobId -> jobInfoService.removeById(jobId));
        }
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

        Object[] jobIds =
                newJobFlow.getFlow().getVertices().stream()
                        .map(JobVertex::getJobId)
                        .toArray(Object[]::new);
        jobInfoService.remove(
                new QueryWrapper<JobInfo>()
                        .lambda()
                        .eq(JobInfo::getFlowId, newJobFlow.getId())
                        .notIn(ArrayUtils.isNotEmpty(jobIds), JobInfo::getId, jobIds));
        updateById(newJobFlow);
    }
}
