package com.flink.platform.web.controller;

import com.flink.platform.common.enums.DeployMode;
import com.flink.platform.common.enums.ExecutionMode;
import com.flink.platform.common.enums.JobStatus;
import com.flink.platform.common.enums.JobType;
import com.flink.platform.common.enums.ResponseStatus;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.LongArrayList;
import com.flink.platform.dao.service.JobInfoService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.constants.UserGroupConst;
import com.flink.platform.web.entity.JobQuartzInfo;
import com.flink.platform.web.entity.request.UserGroupRequest;
import com.flink.platform.web.entity.response.ResultInfo;
import com.flink.platform.web.parser.SqlIdentifier;
import com.flink.platform.web.parser.SqlSelect;
import com.flink.platform.web.service.QuartzService;
import com.flink.platform.web.service.UserGroupSqlGenService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

import java.util.Collections;
import java.util.List;

import static com.flink.platform.web.constants.UserGroupConst.COMMON;
import static com.flink.platform.web.constants.UserGroupConst.UUID;

/** User group controller. */
@Slf4j
@RestController
@RequestMapping("/userGroup")
public class UserGroupController {

    @Resource private UserGroupSqlGenService sqlGenService;

    @Resource private JobInfoService jobInfoService;

    @Resource private JobRunInfoService jobRunInfoService;

    @Resource private QuartzService quartzService;

    @PostMapping(value = "/sqlGenerator/insertSelect")
    public ResultInfo<String> insertSelect(@RequestBody SqlSelect sqlSelect) {
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
    public ResultInfo<Long> createOrUpdate(@RequestBody UserGroupRequest userGroupRequest) {
        try {
            ResultInfo<String> sqlInfo = insertSelect(userGroupRequest.getSelect());
            String sql = sqlInfo.getData();
            JobInfo jobInfo = new JobInfo();
            BeanUtils.copyProperties(userGroupRequest, jobInfo);
            jobInfo.setSubject(sql);
            jobInfo.setType(JobType.FLINK_SQL);
            jobInfo.setDeployMode(DeployMode.FLINK_YARN_PER);
            jobInfo.setExecMode(ExecutionMode.BATCH);
            LongArrayList longs = new LongArrayList();
            longs.add(1L);
            jobInfo.setCatalogs(longs);
            jobInfo.setStatus(JobStatus.ONLINE);
            boolean bool = jobInfoService.saveOrUpdate(jobInfo);
            if (bool && userGroupRequest.getId() == null) {
                JobQuartzInfo jobQuartzInfo = new JobQuartzInfo(jobInfo);
                quartzService.runOnce(jobQuartzInfo);
                quartzService.addJobToQuartz(jobQuartzInfo);
            }
            return ResultInfo.success(jobInfo.getId());
        } catch (Exception e) {
            log.error("create or update user group failed", e);
            return ResultInfo.failure(ResponseStatus.SERVICE_ERROR);
        }
    }
}
