package com.flink.platform.web.controller;

import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.web.annotation.RequirePermission;
import com.flink.platform.web.dto.ResultInfo;
import com.flink.platform.web.environment.YarnAppService;
import com.flink.platform.web.runner.FlowRunDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static com.flink.platform.common.enums.Permission.WORKSPACE_VIEW;
import static com.flink.platform.web.dto.ResultInfo.success;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toMap;

@Slf4j
@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class StatsController {

    private final YarnAppService yarnAppService;

    private final FlowRunDispatcher flowRunDispatcher;

    @RequirePermission(WORKSPACE_VIEW)
    @GetMapping(value = "/runningYarnJobStatusList")
    public ResultInfo<Map<?, ?>> runningYarnJobStatusList() {
        var runningApplications = yarnAppService.getRunningApplications().entrySet().stream()
                .collect(toMap(Entry::getKey, entry -> entry.getValue().toString()));
        return success(runningApplications);
    }

    @RequirePermission(WORKSPACE_VIEW)
    @GetMapping(value = "/worker/active-flow-runs")
    public ResultInfo<List<JobFlowRun>> activeFlowRuns() {
        var activeFlowRuns = flowRunDispatcher.getInFlightFlowRuns().stream()
                .sorted(comparing(JobFlowRun::getId))
                .toList();
        return success(activeFlowRuns);
    }
}
