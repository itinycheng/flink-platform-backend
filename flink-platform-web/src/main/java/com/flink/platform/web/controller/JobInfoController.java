package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flink.platform.common.constants.Constant;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.User;
import com.flink.platform.dao.service.JobInfoService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.entity.JobQuartzInfo;
import com.flink.platform.web.entity.request.JobInfoRequest;
import com.flink.platform.web.entity.response.ResultInfo;
import com.flink.platform.web.service.QuartzService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.flink.platform.common.enums.JobStatus.ONLINE;
import static com.flink.platform.common.enums.ResponseStatus.ERROR_PARAMETER;
import static com.flink.platform.common.enums.ResponseStatus.EXIST_UNFINISHED_PROCESS;
import static com.flink.platform.common.enums.ResponseStatus.NOT_RUNNABLE_STATUS;
import static com.flink.platform.common.enums.ResponseStatus.SERVICE_ERROR;
import static com.flink.platform.web.entity.response.ResultInfo.failure;

/** manage job info. */
@RestController
@RequestMapping("/jobInfo")
public class JobInfoController {

    @Autowired private JobInfoService jobInfoService;

    @Autowired private JobRunInfoService jobRunService;

    @Autowired private QuartzService quartzService;

    @PostMapping(value = "/create")
    public ResultInfo<JobInfo> create(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser,
            @RequestBody JobInfoRequest jobInfoRequest) {
        String errorMsg = jobInfoRequest.validateOnCreate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        JobInfo jobInfo = jobInfoRequest.getJobInfo();
        jobInfo.setId(null);
        jobInfo.setStatus(ONLINE);
        jobInfo.setUserId(loginUser.getId());
        jobInfoService.save(jobInfo);
        return ResultInfo.success(jobInfo);
    }

    @PostMapping(value = "/update")
    public ResultInfo<JobInfo> update(@RequestBody JobInfoRequest jobInfoRequest) {
        String errorMsg = jobInfoRequest.validateOnUpdate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        JobInfo jobInfo = jobInfoRequest.getJobInfo();
        jobInfoService.updateById(jobInfo);
        return ResultInfo.success(jobInfo);
    }

    @GetMapping(value = "/get/{jobId}")
    public ResultInfo<JobInfo> get(@PathVariable Long jobId) {
        JobInfo jobInfo = jobInfoService.getById(jobId);
        return ResultInfo.success(jobInfo);
    }

    @GetMapping(value = "/delete/{jobId}")
    public ResultInfo<Boolean> delete(@PathVariable Long jobId) {
        boolean bool = jobInfoService.removeById(jobId);
        return ResultInfo.success(bool);
    }

    @GetMapping(value = "/page")
    public ResultInfo<IPage<JobInfo>> page(
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "20") Integer size,
            @RequestParam(name = "name", required = false) String name) {
        Page<JobInfo> pager = new Page<>(page, size);
        IPage<JobInfo> iPage =
                jobInfoService.page(
                        pager,
                        new QueryWrapper<JobInfo>()
                                .lambda()
                                .like(Objects.nonNull(name), JobInfo::getName, name));

        return ResultInfo.success(iPage);
    }

    @PostMapping(value = "/getByIds")
    public ResultInfo<List<JobInfo>> list(@RequestBody List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return ResultInfo.success(Collections.emptyList());
        }

        List<JobInfo> jobs = jobInfoService.listByIds(ids);
        return ResultInfo.success(jobs);
    }

    @GetMapping(value = "/schedule/runOnce/{jobId}")
    public ResultInfo<Long> runOnce(@PathVariable long jobId) {
        JobInfo jobInfo = jobInfoService.getById(jobId);
        if (jobInfo.getStatus() != ONLINE) {
            return failure(NOT_RUNNABLE_STATUS);
        }

        List<JobRunInfo> notFinishedList =
                jobRunService.list(
                        new QueryWrapper<JobRunInfo>()
                                .lambda()
                                .eq(JobRunInfo::getJobId, jobId)
                                .in(JobRunInfo::getStatus, ExecutionStatus.getNonTerminals())
                                .gt(JobRunInfo::getCreateTime, LocalDateTime.now().minusDays(1)));
        if (CollectionUtils.isNotEmpty(notFinishedList)) {
            return failure(EXIST_UNFINISHED_PROCESS);
        }

        JobQuartzInfo jobQuartzInfo = new JobQuartzInfo(jobInfo);
        if (quartzService.runOnce(jobQuartzInfo)) {
            return ResultInfo.success(jobId);
        } else {
            return failure(SERVICE_ERROR);
        }
    }
}
