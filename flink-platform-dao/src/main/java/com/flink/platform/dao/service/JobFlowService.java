package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.common.model.JobVertex;
import com.flink.platform.dao.entity.JobFlow;
import com.flink.platform.dao.mapper.JobFlowMapper;
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
}
