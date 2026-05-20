package com.flink.platform.web.controller;

import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.annotation.RequirePermission;
import com.flink.platform.web.common.RequestContext;
import com.flink.platform.web.dto.ResultInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.flink.platform.common.enums.Permission.WORKSPACE_VIEW;
import static com.flink.platform.common.util.DateUtil.GLOBAL_DATE_TIME_FORMAT;
import static com.flink.platform.web.dto.ResultInfo.success;

/** Dashboard statistics. */
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DashboardController {

    private final JobFlowRunService jobFlowRunService;

    private final JobRunInfoService jobRunService;

    @RequirePermission(WORKSPACE_VIEW)
    @GetMapping(value = "/jobRunStatusCount")
    public ResultInfo<List<Map<String, Object>>> jobRunStatusCount(
            @DateTimeFormat(pattern = GLOBAL_DATE_TIME_FORMAT) @RequestParam(name = "startTime", required = false)
                    LocalDateTime startTime,
            @DateTimeFormat(pattern = GLOBAL_DATE_TIME_FORMAT) @RequestParam(name = "endTime", required = false)
                    LocalDateTime endTime) {
        var maps = jobRunService.countJobRunGroupByStatus(RequestContext.requireWorkspaceId(), startTime, endTime);
        return success(maps);
    }

    @RequirePermission(WORKSPACE_VIEW)
    @GetMapping(value = "/jobFlowRunStatusCount")
    public ResultInfo<List<Map<String, Object>>> jobFlowRunStatusCount(
            @DateTimeFormat(pattern = GLOBAL_DATE_TIME_FORMAT) @RequestParam(name = "startTime", required = false)
                    LocalDateTime startTime,
            @DateTimeFormat(pattern = GLOBAL_DATE_TIME_FORMAT) @RequestParam(name = "endTime", required = false)
                    LocalDateTime endTime) {
        var maps =
                jobFlowRunService.countJobFlowRunGroupByStatus(RequestContext.requireWorkspaceId(), startTime, endTime);
        return success(maps);
    }
}
