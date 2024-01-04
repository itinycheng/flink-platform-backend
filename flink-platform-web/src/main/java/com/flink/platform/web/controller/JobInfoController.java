package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flink.platform.common.constants.Constant;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.model.JobVertex;
import com.flink.platform.dao.entity.JobFlow;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.User;
import com.flink.platform.dao.service.JobFlowService;
import com.flink.platform.dao.service.JobInfoService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.entity.JobQuartzInfo;
import com.flink.platform.web.entity.request.JobInfoRequest;
import com.flink.platform.web.entity.response.ResultInfo;
import com.flink.platform.web.service.QuartzService;
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

import static com.flink.platform.common.enums.JobStatus.ONLINE;
import static com.flink.platform.common.enums.ResponseStatus.ERROR_PARAMETER;
import static com.flink.platform.common.enums.ResponseStatus.EXIST_UNFINISHED_PROCESS;
import static com.flink.platform.common.enums.ResponseStatus.NOT_RUNNABLE_STATUS;
import static com.flink.platform.common.enums.ResponseStatus.SERVICE_ERROR;
import static com.flink.platform.dao.entity.JobInfo.LARGE_FIELDS;
import static com.flink.platform.web.entity.response.ResultInfo.failure;
import static com.flink.platform.web.entity.response.ResultInfo.success;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

/** manage job info. */
@RestController
@RequestMapping("/jobInfo")
public class JobInfoController {

    @Autowired
    private JobInfoService jobInfoService;

    @Autowired
    private JobRunInfoService jobRunService;

    @Autowired
    private JobFlowService jobFlowService;

    @Autowired
    private QuartzService quartzService;

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
        return success(jobInfo);
    }

    @PostMapping(value = "/update")
    public ResultInfo<JobInfo> update(@RequestBody JobInfoRequest jobInfoRequest) {
        String errorMsg = jobInfoRequest.validateOnUpdate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        JobInfo jobInfo = jobInfoRequest.getJobInfo();
        jobInfoService.updateById(jobInfo);
        return success(jobInfo);
    }

    @GetMapping(value = "/get/{jobId}")
    public ResultInfo<JobInfo> get(@PathVariable Long jobId) {
        JobInfo jobInfo = jobInfoService.getById(jobId);
        return success(jobInfo);
    }

    @GetMapping(value = "/delete/{jobId}")
    public ResultInfo<Boolean> delete(@PathVariable Long jobId) {
        boolean bool = jobInfoService.removeById(jobId);
        return success(bool);
    }

    @GetMapping(value = "/page")
    public ResultInfo<IPage<JobInfo>> page(
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "20") Integer size,
            @RequestParam(name = "name", required = false) String name) {
        Page<JobInfo> pager = new Page<>(page, size);
        IPage<JobInfo> iPage = jobInfoService.page(
                pager, new QueryWrapper<JobInfo>().lambda().like(nonNull(name), JobInfo::getName, name));

        return success(iPage);
    }

    @GetMapping(value = "/list")
    public ResultInfo<List<JobInfo>> list(
            @RequestParam(name = "flowId") Long flowId,
            @RequestParam(name = "flag", defaultValue = "all") String flag) {
        List<Long> jobIds = null;
        if ("flow".equals(flag)) {
            JobFlow jobFlow = jobFlowService.getById(flowId);
            if (jobFlow != null && jobFlow.getFlow() != null) {
                jobIds = jobFlow.getFlow().getVertices().stream()
                        .map(JobVertex::getJobId)
                        .collect(toList());
            }
        }

        List<JobInfo> list = jobInfoService.list(new QueryWrapper<JobInfo>()
                .lambda()
                .select(JobInfo.class, field -> !LARGE_FIELDS.contains(field.getProperty()))
                .eq(JobInfo::getFlowId, flowId)
                .in(isNotEmpty(jobIds), JobInfo::getId, jobIds));
        return success(list);
    }

    @PostMapping(value = "/getByIds")
    public ResultInfo<List<JobInfo>> getByIds(@RequestBody List<Long> ids) {
        if (isEmpty(ids)) {
            return success(Collections.emptyList());
        }

        List<JobInfo> jobs = jobInfoService.list(new QueryWrapper<JobInfo>()
                .lambda()
                .select(JobInfo.class, field -> !LARGE_FIELDS.contains(field.getProperty()))
                .in(JobInfo::getId, ids));
        return success(jobs);
    }

    @GetMapping(value = "/schedule/runOnce/{jobId}")
    public ResultInfo<Long> runOnce(@PathVariable long jobId) {
        JobInfo jobInfo = jobInfoService.getById(jobId);
        if (jobInfo.getStatus() != ONLINE) {
            return failure(NOT_RUNNABLE_STATUS);
        }

        List<JobRunInfo> notFinishedList = jobRunService.list(new QueryWrapper<JobRunInfo>()
                .lambda()
                .eq(JobRunInfo::getJobId, jobId)
                .in(JobRunInfo::getStatus, ExecutionStatus.getNonTerminals())
                .gt(JobRunInfo::getCreateTime, LocalDateTime.now().minusDays(1)));
        if (isNotEmpty(notFinishedList)) {
            return failure(EXIST_UNFINISHED_PROCESS);
        }

        JobQuartzInfo jobQuartzInfo = new JobQuartzInfo(jobInfo);
        if (quartzService.runOnce(jobQuartzInfo)) {
            return success(jobId);
        } else {
            return failure(SERVICE_ERROR);
        }
    }
}
