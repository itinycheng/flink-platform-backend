package com.flink.platform.web.controller;

import com.flink.platform.web.config.annotation.ApiException;
import com.flink.platform.web.entity.response.ResultInfo;
import com.flink.platform.web.environment.HadoopService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static com.flink.platform.web.entity.response.ResultInfo.success;

@Slf4j
@RestController
@RequestMapping("/stats")
public class StatsController {

    private final HadoopService hadoopService;

    @Autowired
    public StatsController(@Lazy HadoopService hadoopService) {
        this.hadoopService = hadoopService;
    }

    @ApiException
    @GetMapping(value = "/runningYarnJobStatusList")
    public ResultInfo<Map<?, ?>> runningYarnJobStatusList() {
        return success(hadoopService.getRunningApplications());
    }
}
