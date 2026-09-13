package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.common.enums.JobParamType;
import com.flink.platform.common.enums.Status;
import com.flink.platform.dao.entity.JobParam;
import com.flink.platform.dao.mapper.JobParamMapper;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

import static java.util.Objects.nonNull;

/** job param service. */
@Service
@DS("master_platform")
public class JobParamService extends ServiceImpl<JobParamMapper, JobParam> {

    /**
     * TODO: only support global params.
     */
    public List<JobParam> getJobParams(Long flowId, Long workspaceId) {
        return this.list(new QueryWrapper<JobParam>()
                .lambda()
                .nested(qw -> qw.eq(JobParam::getType, JobParamType.GLOBAL)
                        .or(nonNull(flowId), inner -> inner.eq(JobParam::getFlowId, flowId)))
                .eq(JobParam::getStatus, Status.ENABLE)
                .eq(JobParam::getWorkspaceId, workspaceId)
                .orderByAsc(Arrays.asList(JobParam::getType, JobParam::getId)));
    }
}
