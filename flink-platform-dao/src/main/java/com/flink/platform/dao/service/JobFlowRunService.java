package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.mapper.JobFlowRunMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.flink.platform.common.constants.Constant.HOST_IP;
import static com.flink.platform.common.enums.ExecutionStatus.EXECUTABLE_STATUSES;
import static com.flink.platform.common.enums.ExecutionStatus.WAITING;
import static com.flink.platform.common.enums.ExecutionStatus.getNonTerminals;
import static com.flink.platform.common.util.Preconditions.checkNotNull;

/** job config info. */
@Slf4j
@Service
@DS("master_platform")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class JobFlowRunService extends ServiceImpl<JobFlowRunMapper, JobFlowRun> {

    public JobFlowRun getLiteByIdOrNull(Long flowRunId) {
        try {
            return getLiteById(flowRunId);
        } catch (Exception e) {
            log.warn("Get lite flow run {} failed", flowRunId, e);
            return null;
        }
    }

    public JobFlowRun getLiteById(Long flowRunId) {
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

    public List<JobFlowRun> listExecutableRunsOnHost(Collection<Long> excludeRunIds, int limit) {
        return list(new QueryWrapper<JobFlowRun>()
                .lambda()
                .eq(JobFlowRun::getHost, HOST_IP)
                .in(JobFlowRun::getStatus, EXECUTABLE_STATUSES)
                .notIn(CollectionUtils.isNotEmpty(excludeRunIds), JobFlowRun::getId, excludeRunIds)
                .orderByDesc(JobFlowRun::getPriority)
                .orderByAsc(JobFlowRun::getId)
                .last("LIMIT " + limit));
    }

    public LocalDateTime resolveScheduleTimeOrNow(Long flowRunId) {
        if (flowRunId == null) {
            return LocalDateTime.now();
        }

        var flowRun = getOne(new QueryWrapper<JobFlowRun>()
                .lambda()
                .select(JobFlowRun::getScheduleTime, JobFlowRun::getConfig)
                .eq(JobFlowRun::getId, flowRunId));
        checkNotNull(flowRun, "No JobFlowRun found for flowRunId=" + flowRunId);
        return flowRun.getResolvedScheduleTime();
    }

    public void updateStatusById(Long flowRunId, ExecutionStatus status) {
        var newJobFlowRun = new JobFlowRun();
        newJobFlowRun.setId(flowRunId);
        newJobFlowRun.setStatus(status);
        updateById(newJobFlowRun);
    }

    public boolean updateStatusFromTo(Long flowRunId, ExecutionStatus fromStatus, ExecutionStatus toStatus) {
        var newFlowRun = new JobFlowRun();
        newFlowRun.setId(flowRunId);
        newFlowRun.setStatus(toStatus);
        if (toStatus.isTerminalState()) {
            newFlowRun.setEndTime(LocalDateTime.now());
        }

        return baseMapper.update(
                        newFlowRun,
                        new QueryWrapper<JobFlowRun>()
                                .lambda()
                                .eq(JobFlowRun::getId, flowRunId)
                                .eq(JobFlowRun::getStatus, fromStatus))
                == 1;
    }

    /**
     * Advances all still-WAITING runs in one conditional update. The returned count can be smaller
     * than {@code flowRunIds.size()} when another worker or a kill operation wins the race.
     */
    public int updateStatusFromWaiting(List<Long> flowRunIds, ExecutionStatus status) {
        if (CollectionUtils.isEmpty(flowRunIds)) {
            return 0;
        }

        var newFlowRun = new JobFlowRun();
        newFlowRun.setStatus(status);
        if (status.isTerminalState()) {
            newFlowRun.setEndTime(LocalDateTime.now());
        }

        return baseMapper.update(
                newFlowRun,
                new QueryWrapper<JobFlowRun>()
                        .lambda()
                        .in(JobFlowRun::getId, flowRunIds)
                        .eq(JobFlowRun::getStatus, WAITING));
    }

    public List<JobFlowRun> listActiveRuns(Long flowId, Long excludedRunId) {
        return list(new QueryWrapper<JobFlowRun>()
                .lambda()
                .eq(JobFlowRun::getFlowId, flowId)
                .in(JobFlowRun::getStatus, getNonTerminals())
                .ne(excludedRunId != null, JobFlowRun::getId, excludedRunId));
    }

    @Transactional
    public void lockAndUpdateParams(Long flowRunId, Map<String, Object> inputParams) {
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

    public List<Map<String, Object>> countJobFlowRunGroupByStatus(
            Long workspaceId, LocalDateTime startTime, LocalDateTime endTime) {
        return baseMapper.countJobFlowRunGroupByStatus(workspaceId, startTime, endTime);
    }
}
