package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.mapper.JobFlowRunMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.flink.platform.common.constants.Constant.HOST_IP;
import static com.flink.platform.common.enums.ExecutionStatus.EXECUTABLE_STATUSES;
import static com.flink.platform.common.enums.ExecutionStatus.KILLING;
import static com.flink.platform.common.enums.ExecutionStatus.RUNNING;
import static com.flink.platform.common.enums.ExecutionStatus.WAITING;
import static com.flink.platform.common.enums.ExecutionStatus.getUnfinishedStatuses;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

/** job config info. */
@Slf4j
@Service
@DS("master_platform")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class JobFlowRunService extends ServiceImpl<JobFlowRunMapper, JobFlowRun> {

    @Transactional(propagation = Propagation.MANDATORY)
    public @Nullable JobFlowRun lockForUpdate(Long flowRunId) {
        return getOne(new QueryWrapper<JobFlowRun>()
                .lambda()
                .select(
                        JobFlowRun::getId,
                        JobFlowRun::getStatus,
                        JobFlowRun::getHost,
                        JobFlowRun::getStartTime,
                        JobFlowRun::getEndTime,
                        JobFlowRun::getParams)
                .eq(JobFlowRun::getId, flowRunId)
                .last("FOR UPDATE"));
    }

    public int updateStatusFromAnyTo(
            Long flowRunId,
            Collection<ExecutionStatus> expectedStatuses,
            @Nullable String expectedHost,
            ExecutionStatus target) {
        if (flowRunId == null || expectedStatuses.isEmpty()) {
            return 0;
        }

        var now = LocalDateTime.now();
        var update = new JobFlowRun();
        update.setStatus(target);
        if (target == RUNNING) {
            update.setStartTime(now);
        } else if (target.isTerminalState()) {
            update.setEndTime(now);
        }

        return baseMapper.update(
                update,
                new QueryWrapper<JobFlowRun>()
                        .lambda()
                        .eq(JobFlowRun::getId, flowRunId)
                        .in(JobFlowRun::getStatus, expectedStatuses)
                        .eq(expectedHost != null, JobFlowRun::getHost, expectedHost));
    }

    public JobFlowRun getLiteByIdOrNull(Long flowRunId) {
        try {
            return getLiteById(flowRunId);
        } catch (Exception e) {
            log.warn("Get lite flow run {} failed", flowRunId, e);
            return null;
        }
    }

    public @Nullable JobFlowRun getLiteById(Long flowRunId) {
        return getOne(new QueryWrapper<JobFlowRun>()
                .lambda()
                .select(JobFlowRun.class, info -> info.getTypeHandler() == null)
                .eq(JobFlowRun::getId, flowRunId));
    }

    public List<JobFlowRun> listWaitingRunsOnHost() {
        return list(new QueryWrapper<JobFlowRun>()
                .lambda()
                .select(
                        JobFlowRun::getId,
                        JobFlowRun::getName,
                        JobFlowRun::getType,
                        JobFlowRun::getFlowId,
                        JobFlowRun::getCronExpr,
                        JobFlowRun::getDependsOn,
                        JobFlowRun::getAlerts,
                        JobFlowRun::getScheduleTime,
                        JobFlowRun::getCreateTime,
                        JobFlowRun::getStatus)
                .eq(JobFlowRun::getHost, HOST_IP)
                .eq(JobFlowRun::getStatus, WAITING));
    }

    public List<JobFlowRun> listKillingRunsOnHost(Collection<Long> excludeRunIds, int limit) {
        return list(new QueryWrapper<JobFlowRun>()
                .lambda()
                .select(JobFlowRun::getId, JobFlowRun::getStatus, JobFlowRun::getHost, JobFlowRun::getType)
                .eq(JobFlowRun::getHost, HOST_IP)
                .eq(JobFlowRun::getStatus, KILLING)
                .notIn(isNotEmpty(excludeRunIds), JobFlowRun::getId, excludeRunIds)
                .orderByAsc(JobFlowRun::getId)
                .last("LIMIT " + limit));
    }

    public List<JobFlowRun> listExecutableRunsOnHost(Collection<Long> excludeRunIds, int limit) {
        return list(new QueryWrapper<JobFlowRun>()
                .lambda()
                .eq(JobFlowRun::getHost, HOST_IP)
                .in(JobFlowRun::getStatus, EXECUTABLE_STATUSES)
                .notIn(isNotEmpty(excludeRunIds), JobFlowRun::getId, excludeRunIds)
                .orderByDesc(JobFlowRun::getPriority)
                .orderByAsc(JobFlowRun::getId)
                .last("LIMIT " + limit));
    }

    public LocalDateTime getScheduleTimeOrNow(Long flowRunId) {
        if (flowRunId == null) {
            return LocalDateTime.now();
        }

        return getOne(new QueryWrapper<JobFlowRun>()
                        .lambda()
                        .select(JobFlowRun::getScheduleTime)
                        .eq(JobFlowRun::getId, flowRunId))
                .getScheduleTime();
    }

    public List<JobFlowRun> listActiveRuns(Long flowId) {
        return list(new QueryWrapper<JobFlowRun>()
                .lambda()
                .eq(JobFlowRun::getFlowId, flowId)
                .in(JobFlowRun::getStatus, getUnfinishedStatuses()));
    }

    @Transactional
    public void lockAndUpdateParams(Long flowRunId, Map<String, Object> inputParams) {
        if (MapUtils.isEmpty(inputParams)) {
            return;
        }

        var jobFlowRun = lockForUpdate(flowRunId);
        if (jobFlowRun == null) {
            throw new IllegalStateException("Flow run not found: " + flowRunId);
        }

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

    public List<Map<String, Object>> countJobFlowRunGroupByStatus(
            Long workspaceId, LocalDateTime startTime, LocalDateTime endTime) {
        return baseMapper.countJobFlowRunGroupByStatus(workspaceId, startTime, endTime);
    }
}
