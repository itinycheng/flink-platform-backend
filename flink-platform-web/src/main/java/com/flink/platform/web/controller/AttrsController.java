package com.flink.platform.web.controller;

import com.flink.platform.common.enums.DeployMode;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.enums.JobType;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.service.JobInfoService;
import com.flink.platform.web.entity.response.ResultInfo;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.flink.platform.common.constants.Constant.FLINK;
import static com.flink.platform.common.enums.DeployMode.FLINK_YARN_PER;
import static com.flink.platform.common.enums.DeployMode.FLINK_YARN_RUN_APPLICATION;
import static com.flink.platform.common.enums.DeployMode.FLINK_YARN_SESSION;
import static com.flink.platform.common.enums.DeployMode.RUN_LOCAL;
import static com.flink.platform.common.enums.ExecutionMode.STREAMING;
import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.RUNNING;
import static com.flink.platform.common.enums.ExecutionStatus.SUCCESS;

/** Attrs controller. */
@RestController
@RequestMapping("/attr")
public class AttrsController {

    private static final String CLASS_PATH_PREFIX = "com.flink.platform.common.enums";

    @Autowired private JobInfoService jobInfoService;

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
        if (jobId == null) {
            return ResultInfo.success(Arrays.asList(SUCCESS, FAILURE));
        }

        JobInfo jobInfo = jobInfoService.getById(jobId);
        List<ExecutionStatus> result;
        if (STREAMING.equals(jobInfo.getExecMode())) {
            result = Arrays.asList(SUCCESS, FAILURE, RUNNING);
        } else {
            result = Arrays.asList(SUCCESS, FAILURE);
        }
        return ResultInfo.success(result);
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
