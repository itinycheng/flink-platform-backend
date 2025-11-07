package com.flink.platform.web.controller;

import com.flink.platform.common.enums.DeployMode;
import com.flink.platform.common.enums.ExecutionCondition;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.enums.JobType;
import com.flink.platform.dao.entity.task.DependentJob;
import com.flink.platform.web.config.FlinkConfig;
import com.flink.platform.web.entity.response.ResultInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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
import static com.flink.platform.web.entity.response.ResultInfo.success;

/** Attrs controller. */
@Slf4j
@RestController
@RequestMapping("/attr")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class AttrsController {

    private static final String CLASS_PATH_PREFIX = "com.flink.platform.common.enums";

    private final List<FlinkConfig> flinkConfigs;

    @GetMapping(value = "/preconditions")
    public ResultInfo<List<ExecutionCondition>> precondition() {
        var conditions = new ArrayList<ExecutionCondition>();
        conditions.add(AND);
        conditions.add(OR);
        return success(conditions);
    }

    @GetMapping(value = "/dependentRelations")
    public ResultInfo<DependentJob.DependentRelation[]> dependentRelations() {
        return success(DependentJob.DependentRelation.values());
    }

    @GetMapping(value = "/versions")
    public ResultInfo<List<String>> versions(String type) {
        var versions = new ArrayList<String>();
        if (FLINK.equals(type)) {
            versions.addAll(flinkConfigs.stream()
                    .map(FlinkConfig::getVersion)
                    .filter(Objects::nonNull)
                    .toList());
        } else {
            versions.add(FULL_VERSION);
        }

        return success(versions);
    }

    @GetMapping(value = "/deployModes")
    public ResultInfo<List<DeployMode>> deployModes(String type) {
        List<DeployMode> result;
        if (FLINK.equals(type)) {
            result = Arrays.asList(FLINK_YARN_PER, FLINK_YARN_SESSION, FLINK_YARN_RUN_APPLICATION);
        } else {
            result = Collections.singletonList(RUN_LOCAL);
        }
        return success(result);
    }

    @GetMapping(value = "/nodeTypes")
    public ResultInfo<List<JobType>> nodeTypes(String type) {
        return success(JobType.fromClassification(type));
    }

    @GetMapping(value = "/nodeClassification")
    public ResultInfo<String> nodeClassification(JobType jobType) {
        return success(jobType.getClassification());
    }

    @GetMapping(value = "/edgeStates")
    public ResultInfo<List<ExecutionStatus>> edgeStates(Long jobId) {
        // Add RUNNING status for STREAMING job ?
        return success(Arrays.asList(SUCCESS, FAILURE));
    }

    @GetMapping(value = "/enums")
    public ResultInfo<List<Map<String, Object>>> list(
            @RequestParam(name = "className", required = false) String className) {
        var enums = new ArrayList<Map<String, Object>>();
        var clazz = CLASS_PATH_PREFIX + "." + className;
        try {
            var clz = Class.forName(clazz);
            var values = clz.getMethod("values");
            var invoke = values.invoke(null);
            for (var obj : (Object[]) invoke) {
                var getName = obj.getClass().getMethod("name");
                var code = getName.invoke(obj);
                var map = new HashMap<String, Object>();
                map.put("name", code);
                enums.add(map);
            }
        } catch (Exception e) {
            log.error("Get enum list error", e);
        }
        return success(enums);
    }
}
