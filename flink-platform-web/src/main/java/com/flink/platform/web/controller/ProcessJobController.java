package com.flink.platform.web.controller;

import com.flink.platform.common.exception.UncaughtException;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.web.monitor.StatusInfo;
import com.flink.platform.web.service.ProcessJobService;
import com.flink.platform.web.service.ProcessJobStatusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Process a job. */
@Slf4j
@RestController
@RequestMapping("/internal")
public class ProcessJobController {

    @Autowired private ProcessJobService processJobService;

    @Autowired private ProcessJobStatusService processJobStatusService;

    @GetMapping(value = "/process/{jobId}/{flowRunId}")
    public JobRunInfo process(@PathVariable Long jobId, @PathVariable Long flowRunId) {
        try {
            return processJobService.processJob(jobId, flowRunId);
        } catch (Exception e) {
            throw new UncaughtException("Process job failed.", e);
        }
    }

    @PostMapping(value = "/getStatus")
    public StatusInfo getStatus(@RequestBody JobRunInfo jobRunInfo) {
        try {
            return processJobStatusService.getStatus(jobRunInfo);
        } catch (Exception e) {
            throw new UncaughtException("Get job status failed.", e);
        }
    }
}
