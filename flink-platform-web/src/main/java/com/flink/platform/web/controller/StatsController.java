package com.flink.platform.web.controller;

import com.flink.platform.web.config.annotation.ApiException;
import com.flink.platform.web.entity.response.ResultInfo;
import com.flink.platform.web.external.LocalHadoopService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Map.Entry;

import static com.flink.platform.web.entity.response.ResultInfo.success;
import static java.util.stream.Collectors.toMap;

@SuppressWarnings("unused")
@Slf4j
@RestController
@RequestMapping("/stats")
public class StatsController {

    private final LocalHadoopService localHadoopService;

    @Autowired
    public StatsController(@Lazy LocalHadoopService localHadoopService) {
        this.localHadoopService = localHadoopService;
    }

    @ApiException
    @GetMapping(value = "/runningYarnJobStatusList")
    public ResultInfo<Map<?, ?>> runningYarnJobStatusList() {
        var runningApplications = localHadoopService.getRunningApplications().entrySet().stream()
                .collect(toMap(Entry::getKey, entry -> entry.getValue().toString()));
        return success(runningApplications);
    }
}
