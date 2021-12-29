package com.flink.platform.web.controller;

import com.flink.platform.web.entity.response.ResultInfo;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/** Enums controller. */
@RestController
@RequestMapping("/enums")
public class EnumsController {

    private static final String CLASS_PATH_PREFIX = "com.flink.platform.common.enums";

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
