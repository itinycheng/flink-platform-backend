package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.common.enums.JobParamType;
import com.flink.platform.common.enums.Status;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.JobParam;
import com.flink.platform.dao.mapper.JobParamMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** job param service. */
@Service
@DS("master_platform")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class JobParamService extends ServiceImpl<JobParamMapper, JobParam> {

    private final JobInfoService jobInfoService;

    public List<JobParam> getJobParams(Long jobId) {
        JobInfo jobInfo = jobInfoService.getById(jobId);
        if (jobInfo == null) {
            return Collections.emptyList();
        }

        return this.list(new QueryWrapper<JobParam>()
                .lambda()
                .nested(qw ->
                        qw.eq(JobParam::getFlowId, jobInfo.getFlowId()).or().eq(JobParam::getType, JobParamType.GLOBAL))
                .eq(JobParam::getStatus, Status.ENABLE)
                .eq(JobParam::getUserId, jobInfo.getUserId())
                .orderByAsc(Arrays.asList(JobParam::getType, JobParam::getId)));
    }
}
