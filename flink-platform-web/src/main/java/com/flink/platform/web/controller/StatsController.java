package com.flink.platform.web.controller;

import com.flink.platform.web.annotation.RequirePermission;
import com.flink.platform.web.dto.ResultInfo;
import com.flink.platform.web.environment.YarnAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Map.Entry;

import static com.flink.platform.common.enums.Permission.WORKSPACE_VIEW;
import static com.flink.platform.web.dto.ResultInfo.success;
import static java.util.stream.Collectors.toMap;

@Slf4j
@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class StatsController {

    private final YarnAppService yarnAppService;

    @RequirePermission(WORKSPACE_VIEW)
    @GetMapping(value = "/runningYarnJobStatusList")
    public ResultInfo<Map<?, ?>> runningYarnJobStatusList() {
        var runningApplications = yarnAppService.getRunningApplications().entrySet().stream()
                .collect(toMap(Entry::getKey, entry -> entry.getValue().toString()));
        return success(runningApplications);
    }
}
