package com.itiger.persona.controller;

import com.itiger.persona.common.enums.ExecutionMode;
import com.itiger.persona.common.util.JsonUtil;
import com.itiger.persona.common.util.UuidGenerator;
import com.itiger.persona.constants.UserGroupConst;
import com.itiger.persona.entity.JobInfo;
import com.itiger.persona.entity.request.UserGroupRequest;
import com.itiger.persona.entity.response.ResultInfo;
import com.itiger.persona.enums.DeployMode;
import com.itiger.persona.enums.JobType;
import com.itiger.persona.parser.SqlIdentifier;
import com.itiger.persona.parser.SqlSelect;
import com.itiger.persona.service.IJobInfoService;
import com.itiger.persona.service.JobInfoQuartzService;
import com.itiger.persona.service.UserGroupSqlGenService;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

import static com.itiger.persona.constants.UserGroupConst.COMMON;
import static com.itiger.persona.constants.UserGroupConst.UUID;

/**
 * @author tiny.wang
 */
@RestController
@RequestMapping("/userGroup")
public class UserGroupController {

    @Resource
    private UserGroupSqlGenService sqlGenService;

    @Resource
    private IJobInfoService jobInfoService;

    @Resource
    public JobInfoQuartzService jobInfoQuartzService;

    @PostMapping(value = "/sqlGenerator/insertSelect")
    public ResultInfo insertSelect(@RequestBody SqlSelect sqlSelect) {
        // set default table for query from
        sqlSelect.setFrom(UserGroupConst.SOURCE_TABLE_IDENTIFIER);
        // set default select list
        List<SqlIdentifier> identifiers = sqlSelect.getWhere().exhaustiveSqlIdentifiers();
        String qualifier = CollectionUtils.isEmpty(identifiers) ? COMMON : identifiers.get(0).getQualifier();
        sqlSelect.setSelectList(Collections.singletonList(SqlIdentifier.of(qualifier, UUID)));
        // generate sql
        String sqlString = sqlGenService.generateInsertSelect(sqlSelect);
        return ResultInfo.success(sqlString);
    }

    @PostMapping(value = "/createOrUpdate")
    public ResultInfo createOrUpdate(@RequestBody UserGroupRequest userGroupRequest) {
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
        if (jobInfo.getId() == null) {
            jobInfo.setCode(UuidGenerator.generateShortUuid());
        }
        jobInfoService.saveOrUpdate(jobInfo);
        jobInfoQuartzService.runOnce(jobInfo);
        jobInfoQuartzService.addJobToQuartz(jobInfo);
        return ResultInfo.success(jobInfo.getId());
    }
}
