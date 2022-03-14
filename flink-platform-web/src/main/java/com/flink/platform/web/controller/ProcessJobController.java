package com.flink.platform.web.controller;

import com.flink.platform.web.entity.response.ResultInfo;
import com.flink.platform.web.service.ProcessJobService;
import com.flink.platform.web.service.ProcessJobStatusService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.flink.platform.common.enums.ResponseStatus.ERROR_PARAMETER;

/** Process a job. */
@Slf4j
@RestController
@RequestMapping("/internal")
public class ProcessJobController {

    @Autowired private ProcessJobService processJobService;

    @Autowired private ProcessJobStatusService processJobStatusService;

    @PostMapping(value = "/process/{jobId}")
    public ResultInfo<Long> process(
            @PathVariable Long jobId, @RequestBody Map<String, Object> dataMap) throws Exception {
        Long jobRunId = processJobService.processJob(jobId, dataMap);
        return ResultInfo.success(jobRunId);
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
