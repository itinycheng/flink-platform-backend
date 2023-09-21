package com.flink.platform.web.controller;

import com.flink.platform.common.enums.DbType;
import com.flink.platform.common.enums.JobType;
import com.flink.platform.common.util.ExceptionUtil;
import com.flink.platform.dao.entity.Datasource;
import com.flink.platform.dao.entity.task.FlinkJob;
import com.flink.platform.dao.entity.task.SqlJob;
import com.flink.platform.dao.service.DatasourceService;
import com.flink.platform.web.entity.request.ReactiveRequest;
import com.flink.platform.web.entity.response.ResultInfo;
import com.flink.platform.web.entity.vo.ReactiveDataVo;
import com.flink.platform.web.entity.vo.ReactiveExecVo;
import com.flink.platform.web.service.ReactiveService;
import com.flink.platform.web.service.WorkerApplyService;
import com.flink.platform.web.util.HttpUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.flink.platform.common.constants.Constant.LINE_SEPARATOR;
import static com.flink.platform.common.constants.Constant.SQL;
import static com.flink.platform.common.enums.ResponseStatus.DATASOURCE_NOT_FOUND;
import static com.flink.platform.common.enums.ResponseStatus.ERROR_PARAMETER;
import static com.flink.platform.web.entity.response.ResultInfo.failure;
import static com.flink.platform.web.entity.response.ResultInfo.success;

/**
 * Reactive programing.<br>
 * TODO: Better use the rpc protocol instead.
 */
@Slf4j
@RestController
@RequestMapping("/reactive")
public class ReactiveController {

    @Autowired
    private ReactiveService reactiveService;

    @Autowired
    private DatasourceService datasourceService;

    @Autowired
    private WorkerApplyService workerApplyService;

    @Autowired
    private RestTemplate restTemplate;

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

    @GetMapping(value = "/execLog/{execId}")
    public ResultInfo<?> execLog(
            @PathVariable String execId, @RequestParam(name = "worker", required = false) Long worker) {
        String routeUrl = workerApplyService.chooseWorker(Collections.singletonList(worker));
        if (HttpUtil.isRemoteUrl(routeUrl)) {
            return restTemplate.getForObject(routeUrl + "/reactive/execLog/" + execId, ResultInfo.class);
        }

        List<String> dataList = reactiveService.getBufferByExecId(execId);
        String concat = "";
        if (CollectionUtils.isNotEmpty(dataList)) {
            concat = String.join(LINE_SEPARATOR, dataList);
        }

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("remain", reactiveService.bufferExists(execId));
        resultMap.put("log", concat);
        return success(resultMap);
    }

    @PostMapping(value = "/execJob")
    public ResultInfo<?> execJob(@RequestBody ReactiveRequest reactiveRequest) {
        String execId = generateExecId();
        try {
            String routeUrl = workerApplyService.chooseWorker(reactiveRequest.getRouteUrl());
            if (HttpUtil.isRemoteUrl(routeUrl)) {
                return restTemplate.postForObject(routeUrl + "/reactive/execJob", reactiveRequest, ResultInfo.class);
            }

            if (SQL.equals(reactiveRequest.getType().getClassification())) {
                return execSql(execId, reactiveRequest);
            }

            if (reactiveRequest.getType().equals(JobType.FLINK_SQL)) {
                return execFlink(execId, reactiveRequest);
            }

            throw new RuntimeException("unsupported job type: " + reactiveRequest.getType());
        } catch (Exception e) {
            ReactiveDataVo reactiveDataVo =
                    new ReactiveDataVo(execId, new String[] {"exception"}, null, ExceptionUtil.stackTrace(e));
            return success(reactiveDataVo);
        }
    }

    private ResultInfo<?> execFlink(String execId, ReactiveRequest reactiveRequest) throws Exception {
        FlinkJob flinkJob = reactiveRequest.getConfig().unwrap(FlinkJob.class);
        if (flinkJob == null) {
            return failure(ERROR_PARAMETER);
        }

        String errorMsg = reactiveRequest.validateFlink();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        reactiveRequest.setId(0L);
        reactiveRequest.setName(execId);

        ReactiveExecVo reactiveExecVo =
                reactiveService.execFlink(execId, reactiveRequest.getJobInfo(), reactiveRequest.getEnvProps());
        return success(reactiveExecVo);
    }

    private ResultInfo<ReactiveDataVo> execSql(String execId, ReactiveRequest reactiveRequest) throws Exception {
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

        ReactiveDataVo reactiveDataVo = reactiveService.execSql(execId, reactiveRequest.getJobInfo(), datasource);
        return success(reactiveDataVo);
    }

    private String generateExecId() {
        return "reactive-" + UUID.randomUUID().toString().replace("-", "");
    }
}
