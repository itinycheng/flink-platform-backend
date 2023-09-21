package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.constants.Constant;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.User;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.entity.response.ResultInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.flink.platform.common.util.DateUtil.GLOBAL_DATE_TIME_FORMAT;
import static com.flink.platform.web.entity.response.ResultInfo.success;
import static java.util.Objects.nonNull;

/** Dashboard statistics. */
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    @Autowired
    private JobFlowRunService jobFlowRunService;

    @Autowired
    private JobRunInfoService jobRunService;

    @GetMapping(value = "/jobRunStatusCount")
    public ResultInfo<List<Map<String, Object>>> jobRunStatusCount(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser,
            @DateTimeFormat(pattern = GLOBAL_DATE_TIME_FORMAT) @RequestParam(name = "startTime", required = false)
                    LocalDateTime startTime,
            @DateTimeFormat(pattern = GLOBAL_DATE_TIME_FORMAT) @RequestParam(name = "endTime", required = false)
                    LocalDateTime endTime) {
        List<Map<String, Object>> maps = jobRunService.listMaps(new QueryWrapper<JobRunInfo>()
                .select("status, count(id) as count")
                .groupBy("status")
                .between(nonNull(startTime) && nonNull(endTime), "create_time", startTime, endTime)
                .eq("user_id", loginUser.getId()));
        return success(maps);
    }

    @GetMapping(value = "/jobFlowRunStatusCount")
    public ResultInfo<List<Map<String, Object>>> jobFlowRunStatusCount(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser,
            @DateTimeFormat(pattern = GLOBAL_DATE_TIME_FORMAT) @RequestParam(name = "startTime", required = false)
                    LocalDateTime startTime,
            @DateTimeFormat(pattern = GLOBAL_DATE_TIME_FORMAT) @RequestParam(name = "endTime", required = false)
                    LocalDateTime endTime) {
        List<Map<String, Object>> maps = jobFlowRunService.listMaps(new QueryWrapper<JobFlowRun>()
                .select("status, count(id) as count")
                .groupBy("status")
                .between(nonNull(startTime) && nonNull(endTime), "create_time", startTime, endTime)
                .eq("user_id", loginUser.getId()));
        return success(maps);
    }
}
