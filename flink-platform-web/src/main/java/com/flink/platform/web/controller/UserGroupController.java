package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.enums.ExecutionMode;
import com.flink.platform.common.enums.JobYarnStatusEnum;
import com.flink.platform.common.enums.ResponseStatus;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.common.util.UuidGenerator;
import com.flink.platform.web.constants.UserGroupConst;
import com.flink.platform.web.entity.JobInfo;
import com.flink.platform.web.entity.JobRunInfo;
import com.flink.platform.web.entity.request.UserGroupRequest;
import com.flink.platform.web.entity.response.ResultInfo;
import com.flink.platform.web.enums.DeployMode;
import com.flink.platform.web.enums.JobType;
import com.flink.platform.web.parser.SqlIdentifier;
import com.flink.platform.web.parser.SqlSelect;
import com.flink.platform.web.service.IJobInfoService;
import com.flink.platform.web.service.IJobRunInfoService;
import com.flink.platform.web.service.JobInfoQuartzService;
import com.flink.platform.web.service.UserGroupService;
import com.flink.platform.web.service.UserGroupSqlGenService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.flink.platform.web.constants.UserGroupConst.COMMON;
import static com.flink.platform.web.constants.UserGroupConst.UUID;

/** User group controller. */
@Slf4j
@RestController
@RequestMapping("/userGroup")
public class UserGroupController {

    private static final List<Integer> FINAL_STATUS =
            Arrays.asList(
                    JobYarnStatusEnum.FINISHED.getCode(),
                    JobYarnStatusEnum.FAILED.getCode(),
                    JobYarnStatusEnum.KILLED.getCode());

    @Resource private UserGroupSqlGenService sqlGenService;

    @Resource private IJobInfoService jobInfoService;

    @Resource private IJobRunInfoService jobRunInfoService;

    @Resource public JobInfoQuartzService jobInfoQuartzService;

    @Resource private UserGroupService userGroupService;

    @PostMapping(value = "/sqlGenerator/insertSelect")
    public ResultInfo insertSelect(@RequestBody SqlSelect sqlSelect) {
        // set default table for query from
        sqlSelect.setFrom(UserGroupConst.SOURCE_TABLE_IDENTIFIER);
        // set default select list
        List<SqlIdentifier> identifiers = sqlSelect.getWhere().exhaustiveSqlIdentifiers();
        String qualifier =
                CollectionUtils.isEmpty(identifiers) ? COMMON : identifiers.get(0).getQualifier();
        sqlSelect.setSelectList(Collections.singletonList(SqlIdentifier.of(qualifier, UUID)));
        // generate sql
        String sqlString = sqlGenService.generateInsertSelect(sqlSelect);
        return ResultInfo.success(sqlString);
    }

    @PostMapping(value = "/createOrUpdate")
    public ResultInfo createOrUpdate(@RequestBody UserGroupRequest userGroupRequest) {
        try {
            ResultInfo sqlInfo = insertSelect(userGroupRequest.getSelect());
            String sql = sqlInfo.getResult().toString();
            JobInfo jobInfo = new JobInfo();
            BeanUtils.copyProperties(userGroupRequest, jobInfo);
            jobInfo.setSubject(sql);
            jobInfo.setType(JobType.FLINK_SQL);
            jobInfo.setDeployMode(DeployMode.FLINK_YARN_PER);
            jobInfo.setExecMode(ExecutionMode.BATCH);
            jobInfo.setSqlPlan(JsonUtil.toJsonString(userGroupRequest.getSelect()));
            jobInfo.setCatalogs("[1]");
            jobInfo.setStatus(3);
            if (userGroupRequest.getId() == null) {
                jobInfo.setCode(UuidGenerator.generateShortUuid());
            }
            boolean bool = jobInfoService.saveOrUpdate(jobInfo);
            if (bool && userGroupRequest.getId() == null) {
                jobInfoQuartzService.runOnce(jobInfo);
                jobInfoQuartzService.addJobToQuartz(jobInfo);
            }
            return ResultInfo.success(jobInfo.getId());
        } catch (Exception e) {
            log.error("create or update user group failed", e);
            return ResultInfo.failure(ResponseStatus.SERVICE_ERROR);
        }
    }

    @PostMapping(value = "/resultSize")
    public ResultInfo resultSize(@RequestBody UserGroupRequest userGroupRequest) {
        JobRunInfo jobRunInfo = null;
        try {
            if (userGroupRequest == null
                    || ObjectUtils.defaultIfNull(userGroupRequest.getId(), 0L) <= 0) {
                return ResultInfo.failure(ResponseStatus.ERROR_PARAMETER);
            }

            Long runId = userGroupRequest.getRunId();
            if (runId != null) {
                jobRunInfo =
                        jobRunInfoService.getOne(
                                new QueryWrapper<JobRunInfo>()
                                        .lambda()
                                        .eq(JobRunInfo::getId, runId));
            } else {
                jobRunInfo = jobRunInfoService.getLatestByJobId(userGroupRequest.getId());
            }

            if (jobRunInfo.getResultSize() != null) {
                return ResultInfo.success(jobRunInfo.getResultSize());
            } else {
                long resultSize = 0;
                if (FINAL_STATUS.contains(jobRunInfo.getStatus())) {
                    resultSize =
                            userGroupService.getAndStoreResultSize(
                                    jobRunInfo.getId(), jobRunInfo.getVariables());
                }
                return ResultInfo.success(resultSize);
            }
        } catch (Exception e) {
            log.error("get result size failed, runInfo: {}", jobRunInfo, e);
            return ResultInfo.failure(ResponseStatus.SERVICE_ERROR);
        }
    }
}
