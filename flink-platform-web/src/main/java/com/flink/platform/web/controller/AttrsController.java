package com.flink.platform.web.controller;

import com.flink.platform.common.enums.DeployMode;
import com.flink.platform.common.enums.ExecutionCondition;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.enums.JobType;
import com.flink.platform.dao.service.JobInfoService;
import com.flink.platform.web.config.FlinkConfig;
import com.flink.platform.web.entity.response.ResultInfo;
import com.flink.platform.web.util.HttpUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.flink.platform.common.constants.Constant.FLINK;
import static com.flink.platform.common.constants.Constant.FULL_VERSION;
import static com.flink.platform.common.enums.DeployMode.FLINK_YARN_PER;
import static com.flink.platform.common.enums.DeployMode.FLINK_YARN_RUN_APPLICATION;
import static com.flink.platform.common.enums.DeployMode.FLINK_YARN_SESSION;
import static com.flink.platform.common.enums.DeployMode.RUN_LOCAL;
import static com.flink.platform.common.enums.ExecutionCondition.AND;
import static com.flink.platform.common.enums.ExecutionCondition.OR;
import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.SUCCESS;
import static java.util.stream.Collectors.toList;

/** Attrs controller. */
@RestController
@RequestMapping("/attr")
public class AttrsController {

    private static final String CLASS_PATH_PREFIX = "com.flink.platform.common.enums";

    @Autowired private JobInfoService jobInfoService;

    @Autowired private List<FlinkConfig> flinkConfigs;

    @GetMapping(value = "/preconditions")
    public ResultInfo<List<ExecutionCondition>> precondition() {
        List<ExecutionCondition> conditions = new ArrayList<>();
        conditions.add(AND);
        conditions.add(OR);
        return ResultInfo.success(conditions);
    }

    @GetMapping(value = "/versions")
    public ResultInfo<List<String>> versions(String type) {
        List<String> versions = new ArrayList<>();
        if (FLINK.equals(type)) {
            versions.addAll(
                    flinkConfigs.stream()
                            .map(FlinkConfig::getVersion)
                            .filter(Objects::nonNull)
                            .collect(toList()));
        } else {
            versions.add(FULL_VERSION);
        }

        return ResultInfo.success(versions);
    }

    @GetMapping(value = "/routeUrls")
    public ResultInfo<List<String>> routeUrls() {
        List<String> routingUrls = new ArrayList<>();
        routingUrls.add(HttpUtil.LOCALHOST_URL);
        return ResultInfo.success(routingUrls);
    }

    @GetMapping(value = "/deployModes")
    public ResultInfo<List<DeployMode>> deployModes(String type) {
        List<DeployMode> result;
        if (FLINK.equals(type)) {
            result = Arrays.asList(FLINK_YARN_PER, FLINK_YARN_SESSION, FLINK_YARN_RUN_APPLICATION);
        } else {
            result = Collections.singletonList(RUN_LOCAL);
        }
        return ResultInfo.success(result);
    }

    @GetMapping(value = "/nodeTypes")
    public ResultInfo<List<JobType>> nodeTypes(String type) {
        return ResultInfo.success(JobType.from(type));
    }

    @GetMapping(value = "/edgeStates")
    public ResultInfo<List<ExecutionStatus>> edgeStates(Long jobId) {
        // Add RUNNING status for STREAMING job ?
        return ResultInfo.success(Arrays.asList(SUCCESS, FAILURE));
    }

    @GetMapping(value = "/list")
    public ResultInfo<List<Map<String, Object>>> list(
            @RequestParam(name = "className", required = false) String className) {
        List<Map<String, Object>> enums = Lists.newArrayList();
        String clazz = CLASS_PATH_PREFIX + "." + className;
        try {
            Class<?> clz = Class.forName(clazz);
            Method values = clz.getMethod("values");
            Object invoke = values.invoke(null);
            for (Object obj : (Object[]) invoke) {
                Method getName = obj.getClass().getMethod("name");
                Object code = getName.invoke(obj);
                Map<String, Object> map = Maps.newHashMap();
                map.put("name", code);
                enums.add(map);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ResultInfo.success(enums);
    }
}
