package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.enums.ExecutionStrategy;
import com.flink.platform.dao.entity.ExecutionConfig;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.mapper.JobFlowRunMapper;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.flink.platform.common.enums.ExecutionStatus.getNonTerminals;
import static com.flink.platform.common.enums.ExecutionStrategy.ONLY_CUR_JOB;

/** job config info. */
@Service
@DS("master_platform")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class JobFlowRunService extends ServiceImpl<JobFlowRunMapper, JobFlowRun> {

    private final JobRunInfoService jobRunService;

    @Transactional
    public void deleteAllById(long flowRunId, long userId) {
        jobRunService.remove(new QueryWrapper<JobRunInfo>()
                .lambda()
                .eq(JobRunInfo::getFlowRunId, flowRunId)
                .eq(JobRunInfo::getUserId, userId));
        removeById(flowRunId);
    }

    public void updateStatusById(Long flowRunId, ExecutionStatus status) {
        var newJobFlowRun = new JobFlowRun();
        newJobFlowRun.setId(flowRunId);
        newJobFlowRun.setStatus(status);
        updateById(newJobFlowRun);
    }

    public JobFlowRun findRunningFlow(@Nonnull Long flowId, ExecutionConfig config) {
        Long startJobId;
        ExecutionStrategy strategy;
        if (config != null) {
            startJobId = config.getStartJobId();
            strategy = config.getStrategy();
        } else {
            startJobId = null;
            strategy = null;
        }

        return list(new QueryWrapper<JobFlowRun>()
                        .lambda()
                        .eq(JobFlowRun::getFlowId, flowId)
                        .in(JobFlowRun::getStatus, getNonTerminals()))
                .stream()
                .filter(jobFlowRun -> {
                    if (!ONLY_CUR_JOB.equals(strategy)) {
                        return true;
                    }

                    if (startJobId == null) {
                        return true;
                    }

                    var conf = jobFlowRun.getConfig();
                    return startJobId.equals(conf.getStartJobId());
                })
                .findAny()
                .orElse(null);
    }

    @Transactional
    public void lockAndUpdateParams(Long flowRunId, @Nonnull Map<String, Object> inputParams) {
        if (MapUtils.isEmpty(inputParams)) {
            return;
        }

        var jobFlowRun = baseMapper.queryParamsForUpdate(flowRunId);
        var existedParams = jobFlowRun.getParams();
        var sharedParams = existedParams != null ? new HashMap<>(existedParams) : new HashMap<String, Object>();
        if (MapUtils.isEmpty(sharedParams)) {
            sharedParams.putAll(inputParams);
        } else {
            inputParams.forEach((k, v) -> {
                var inValue = v != null ? v.toString() : null;
                if (inValue == null) {
                    return;
                }

                switch (sharedParams.get(k)) {
                    case null -> sharedParams.put(k, inValue);
                    case String s -> sharedParams.put(k, List.of(s, inValue));
                    case List<?> sharedList -> {
                        var list = new ArrayList<Object>(sharedList);
                        list.add(inValue);
                        sharedParams.put(k, list);
                    }
                    default -> throw new IllegalStateException("Unexpected value: " + sharedParams.get(k));
                }
            });
        }

        var newFlowRun = new JobFlowRun();
        newFlowRun.setId(jobFlowRun.getId());
        newFlowRun.setParams(sharedParams);
        updateById(newFlowRun);
    }
}
