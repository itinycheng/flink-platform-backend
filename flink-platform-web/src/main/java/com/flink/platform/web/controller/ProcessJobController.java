package com.flink.platform.web.controller;

import com.flink.platform.web.entity.response.ResultInfo;
import com.flink.platform.web.service.ProcessJobService;
import com.flink.platform.web.service.ProcessJobStatusService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.flink.platform.common.enums.ResponseStatus.ERROR_PARAMETER;
import static com.flink.platform.common.enums.ResponseStatus.SERVICE_ERROR;

/** Process a job. */
@Slf4j
@RestController
@RequestMapping("/internal")
public class ProcessJobController {

    @Autowired private ProcessJobService processJobService;

    @Autowired private ProcessJobStatusService processJobStatusService;

    @GetMapping(value = "/process/{jobCode}")
    public ResultInfo<Long> process(@PathVariable String jobCode) {
        try {
            if (StringUtils.isBlank(jobCode)) {
                return ResultInfo.failure(ERROR_PARAMETER);
            }
            Long jobRunId = processJobService.processJob(jobCode);
            return ResultInfo.success(jobRunId);
        } catch (Exception e) {
            log.error("Cannot exec job: {}", jobCode, e);
            return ResultInfo.failure(SERVICE_ERROR);
        }
    }

    @PostMapping(value = "/updateStatus")
    public ResultInfo<Object> updateStatus(@RequestBody List<Long> jobInstanceIdList) {
        if (CollectionUtils.isEmpty(jobInstanceIdList)) {
            return ResultInfo.failure(ERROR_PARAMETER);
        }
        processJobStatusService.updateStatus(jobInstanceIdList);
        return ResultInfo.success(null);
    }
}
