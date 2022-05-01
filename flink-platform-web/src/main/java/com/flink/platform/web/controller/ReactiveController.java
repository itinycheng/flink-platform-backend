package com.flink.platform.web.controller;

import com.flink.platform.common.enums.DbType;
import com.flink.platform.common.enums.JobType;
import com.flink.platform.dao.entity.Datasource;
import com.flink.platform.dao.entity.task.SqlJob;
import com.flink.platform.dao.service.DatasourceService;
import com.flink.platform.web.entity.request.ReactiveRequest;
import com.flink.platform.web.entity.response.ResultInfo;
import com.flink.platform.web.entity.vo.ReactiveDataVo;
import com.flink.platform.web.service.ReactiveService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.flink.platform.common.constants.Constant.SQL;
import static com.flink.platform.common.enums.ResponseStatus.DATASOURCE_NOT_FOUND;
import static com.flink.platform.common.enums.ResponseStatus.ERROR_PARAMETER;
import static com.flink.platform.web.entity.response.ResultInfo.failure;
import static com.flink.platform.web.entity.response.ResultInfo.success;

/** Reactive programing. */
@Slf4j
@RestController
@RequestMapping("/reactive")
public class ReactiveController {

    @Autowired private ReactiveService reactiveService;

    @Autowired private DatasourceService datasourceService;

    @GetMapping(value = "/jobToDbTypes")
    public ResultInfo<Map<JobType, DbType>> jobToDbTypes() {
        List<JobType> typeList = new ArrayList<>();
        typeList.add(JobType.FLINK_SQL);
        typeList.addAll(JobType.from(SQL));
        Map<JobType, DbType> result = new HashMap<>(typeList.size());
        for (JobType jobType : typeList) {
            result.put(jobType, jobType.getDbType());
        }
        return success(result);
    }

    @PostMapping(value = "/syncJob")
    public ResultInfo<ReactiveDataVo> sync(@RequestBody ReactiveRequest reactiveRequest) {
        ReactiveDataVo reactiveDataVo;
        try {
            SqlJob sqlJob = reactiveRequest.getConfig().unwrap(SqlJob.class);
            if (sqlJob == null) {
                return failure(ERROR_PARAMETER);
            }

            String errorMsg = reactiveRequest.validateSql();
            if (StringUtils.isNotBlank(errorMsg)) {
                return failure(ERROR_PARAMETER, errorMsg);
            }

            Datasource datasource = datasourceService.getById(sqlJob.getDsId());
            if (datasource == null) {
                return failure(DATASOURCE_NOT_FOUND);
            }

            reactiveDataVo = reactiveService.execSql(reactiveRequest.getJobInfo(), datasource);
        } catch (Exception e) {
            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer, true));
            reactiveDataVo =
                    new ReactiveDataVo(new String[] {"exception"}, null, writer.toString());
        }
        return success(reactiveDataVo);
    }

    @PostMapping(value = "/async")
    public ResultInfo<String> async(@RequestBody ReactiveRequest reactiveRequest) throws Exception {
        String execId = reactiveService.execCmd(reactiveRequest);
        return success(execId);
    }

    @GetMapping(value = "/data/{execId}")
    public ResultInfo<List<String>> data(@PathVariable String execId) throws Exception {
        List<String> dataList = reactiveService.getByExecId(execId);
        return success(dataList);
    }
}
