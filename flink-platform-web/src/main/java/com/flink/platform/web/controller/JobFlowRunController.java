package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flink.platform.common.constants.Constant;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.User;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.web.entity.response.ResultInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

import static java.util.Objects.nonNull;

/** crud job flow. */
@RestController
@RequestMapping("/jobFlowRun")
public class JobFlowRunController {

    @Autowired private JobFlowRunService jobFlowRunService;

    @GetMapping(value = "/get/{flowRunId}")
    public ResultInfo<JobFlowRun> get(@PathVariable long flowRunId) {
        JobFlowRun jobFlowRun = jobFlowRunService.getById(flowRunId);
        return ResultInfo.success(jobFlowRun);
    }

    @GetMapping(value = "/page")
    public ResultInfo<IPage<JobFlowRun>> page(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser,
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "20") Integer size,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "status", required = false) ExecutionStatus status,
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
                    @RequestParam(name = "startTime", required = false)
                    LocalDateTime startTime,
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
                    @RequestParam(name = "endTime", required = false)
                    LocalDateTime endTime,
            @RequestParam(name = "sort", required = false) String sort) {
        Page<JobFlowRun> pager = new Page<>(page, size);

        LambdaQueryWrapper<JobFlowRun> queryWrapper =
                new QueryWrapper<JobFlowRun>()
                        .lambda()
                        .eq(JobFlowRun::getUserId, loginUser.getId())
                        .eq(nonNull(status), JobFlowRun::getStatus, status)
                        .like(nonNull(name), JobFlowRun::getName, name)
                        .between(
                                nonNull(startTime) && nonNull(endTime),
                                JobFlowRun::getCreateTime,
                                startTime,
                                endTime);
        if ("-id".equals(sort)) {
            queryWrapper.orderByDesc(JobFlowRun::getId);
        }

        IPage<JobFlowRun> iPage = jobFlowRunService.page(pager, queryWrapper);
        return ResultInfo.success(iPage);
    }
}
