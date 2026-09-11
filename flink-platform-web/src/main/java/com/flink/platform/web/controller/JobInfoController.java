package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.flink.platform.common.annotation.Auditable;
import com.flink.platform.common.constants.Constant;
import com.flink.platform.common.model.JobVertex;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.User;
import com.flink.platform.dao.query.JobPageQuery;
import com.flink.platform.dao.service.JobFlowService;
import com.flink.platform.dao.service.JobInfoService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.dao.view.JobDetails;
import com.flink.platform.web.annotation.RequirePermission;
import com.flink.platform.web.common.RequestContext;
import com.flink.platform.web.dto.ResultInfo;
import com.flink.platform.web.dto.request.JobInfoRequest;
import lombok.RequiredArgsConstructor;
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

import java.util.Collections;
import java.util.List;

import static com.flink.platform.common.enums.EntityType.JOB;
import static com.flink.platform.common.enums.JobStatus.ONLINE;
import static com.flink.platform.common.enums.OperationType.DELETE;
import static com.flink.platform.common.enums.OperationType.INSERT;
import static com.flink.platform.common.enums.OperationType.UPDATE;
import static com.flink.platform.common.enums.Permission.TASK_EDIT;
import static com.flink.platform.common.enums.Permission.TASK_PURGE;
import static com.flink.platform.common.enums.Permission.TASK_VIEW;
import static com.flink.platform.common.enums.ResponseStatus.ERROR_PARAMETER;
import static com.flink.platform.common.enums.ResponseStatus.OPERATION_NOT_ALLOWED;
import static com.flink.platform.web.dto.ResultInfo.failure;
import static com.flink.platform.web.dto.ResultInfo.success;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

/** manage job info. */
@RestController
@RequestMapping("/jobInfo")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class JobInfoController {

    private final JobInfoService jobInfoService;

    private final JobRunInfoService jobRunService;

    private final JobFlowService jobFlowService;

    @RequirePermission(TASK_EDIT)
    @Auditable(type = JOB, operation = INSERT)
    @PostMapping(value = "/create")
    public ResultInfo<JobInfo> create(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser,
            @RequestBody JobInfoRequest jobInfoRequest) {
        var errorMsg = jobInfoRequest.validateOnCreate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        var job = jobInfoRequest.getJobInfo();
        job.setId(null);
        job.setStatus(ONLINE);
        job.setUserId(loginUser.getId());
        job.setWorkspaceId(RequestContext.requireWorkspaceId());
        jobInfoService.save(job);
        return success(job);
    }

    @RequirePermission(TASK_EDIT)
    @Auditable(type = JOB, operation = UPDATE)
    @PostMapping(value = "/update")
    public ResultInfo<JobInfo> update(@RequestBody JobInfoRequest jobInfoRequest) {
        var errorMsg = jobInfoRequest.validateOnUpdate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        var job = jobInfoRequest.getJobInfo();
        jobFlowService.updateJobAndSyncPrecondition(job);
        return success(job);
    }

    @RequirePermission(TASK_VIEW)
    @GetMapping(value = "/get/{jobId}")
    public ResultInfo<JobInfo> get(@PathVariable Long jobId) {
        var jobInfo = jobInfoService.getById(jobId);
        return success(jobInfo);
    }

    @RequirePermission(TASK_EDIT)
    @Auditable(type = JOB, operation = DELETE)
    @GetMapping(value = "/delete/{jobId}")
    public ResultInfo<Boolean> delete(@PathVariable Long jobId) {
        var bool = jobInfoService.removeById(jobId);
        return success(bool);
    }

    @RequirePermission(TASK_VIEW)
    @GetMapping(value = "/page")
    public ResultInfo<IPage<JobDetails>> page(JobPageQuery query) {
        if (query.isExcludeJobsInFlow()) {
            query.setExcludeJobIds(getJobIdsInFlow(query.getFlowId()));
        }

        var result = jobInfoService.pageDetails(query);
        // Add jobRun info.
        if (query.isIncludeJobRuns() && CollectionUtils.isNotEmpty(result.getRecords())) {
            var jobIds = result.getRecords().stream().map(JobInfo::getId).collect(toList());
            var runningJobsMap = jobRunService.listLastWithoutLargeFields(null, jobIds).stream()
                    .collect(toMap(JobRunInfo::getJobId, jobRun -> jobRun));
            result.getRecords().forEach(job -> {
                var jobRun = runningJobsMap.get(job.getId());
                if (jobRun != null) {
                    job.setJobRunId(jobRun.getId());
                    job.setJobRunStatus(jobRun.getStatus());
                    job.setFlowRunId(jobRun.getFlowRunId());
                }
            });
        }

        return success(result);
    }

    @RequirePermission(TASK_VIEW)
    @GetMapping(value = "/list")
    public ResultInfo<List<JobInfo>> list(
            @RequestParam(name = "flowId") Long flowId,
            @RequestParam(name = "flag", defaultValue = "all") String flag) {
        List<Long> jobIds = null;
        if ("flow".equals(flag)) {
            jobIds = getJobIdsInFlow(flowId);
        }

        var list = jobInfoService.list(new QueryWrapper<JobInfo>()
                .lambda()
                .select(JobInfo.class, jobInfoService::isNonLargeField)
                .eq(JobInfo::getFlowId, flowId)
                .in(isNotEmpty(jobIds), JobInfo::getId, jobIds));
        return success(list);
    }

    @RequirePermission(TASK_VIEW)
    @PostMapping(value = "/getByIds")
    public ResultInfo<List<JobInfo>> getByIds(@RequestBody List<Long> ids) {
        if (isEmpty(ids)) {
            return success(Collections.emptyList());
        }

        var jobs = jobInfoService.list(new QueryWrapper<JobInfo>()
                .lambda()
                .select(JobInfo.class, jobInfoService::isNonLargeField)
                .in(JobInfo::getId, ids));
        return success(jobs);
    }

    @RequirePermission(TASK_PURGE)
    @Auditable(type = JOB, operation = DELETE)
    @GetMapping(value = "/purge/{jobId}")
    public ResultInfo<Long> purge(@PathVariable long jobId) {
        var jobInfo = jobInfoService.getById(jobId);
        if (jobInfo == null) {
            return failure(ERROR_PARAMETER);
        }

        var flowId = jobInfo.getFlowId();
        var jobFlow = jobFlowService.getById(flowId);
        if (jobFlow != null && jobFlow.getFlow() != null) {
            if (jobFlow.getFlow().containsVertex(jobId)) {
                return failure(OPERATION_NOT_ALLOWED, "Job is in flow, can't be deleted");
            }
        }

        jobInfoService.removeAllById(jobId);
        return success(jobId);
    }

    // ====================================================
    // ====================== private =====================
    // ====================================================

    private List<Long> getJobIdsInFlow(Long flowId) {
        if (flowId == null) {
            return Collections.emptyList();
        }

        var jobFlow = jobFlowService.getById(flowId);
        var flow = jobFlow != null ? jobFlow.getFlow() : null;
        if (flow == null) {
            return Collections.emptyList();
        }

        return flow.getVertices().stream().map(JobVertex::getJobId).collect(toList());
    }
}
