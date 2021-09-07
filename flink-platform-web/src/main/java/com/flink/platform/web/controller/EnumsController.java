package com.flink.platform.web.controller;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.flink.platform.web.entity.response.ResultInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * @author tiny.wang
 */
@RestController
@RequestMapping("/enums")
public class EnumsController {

    private final static String CLASS_PATH_PREFIX = "com.flink.platform.common.enums";

    @GetMapping
    public ResultInfo list(String enumsClass) {
        List<Map<String, Object>> enums = Lists.newArrayList();
        String clazz = CLASS_PATH_PREFIX + "." + enumsClass;
        try {
            Class<?> clz = Class.forName(clazz);
            Method values = clz.getMethod("values", null);
            Object invoke = values.invoke(null);
            for (Object obj : (Object[]) invoke) {
                Method getCode = obj.getClass().getMethod("getCode");
                Object code = getCode.invoke(obj);
                Method getDesc = obj.getClass().getMethod("getDesc");
                Object desc = getDesc.invoke(obj);
                Map<String, Object> map = Maps.newHashMap();
                map.put("code", code);
                map.put("desc", desc);
                enums.add(map);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ResultInfo.success(enums);
    }

}
