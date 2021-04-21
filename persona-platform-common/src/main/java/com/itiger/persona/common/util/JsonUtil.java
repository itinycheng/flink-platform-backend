package com.itiger.persona.common.util;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

/**
 * json utils
 *
 * @author tiny.wang
 */
@Slf4j
public class JsonUtil {

    public static List<String> toJsonList(String res) {
        return JSON.parseArray(res).stream()
                .map(Object::toString)
                .collect(toList());
    }

    public static Map<String, Object> toJsonMap(String res) {
        return JSON.parseObject(res);
    }

    public static <T> T toJson(String res, Class<T> clazz) {
        try {
            return JSON.parseObject(res, clazz);
        } catch (Exception e) {
            log.error("parse string to class instance failed", e);
            return null;
        }
    }

    public static <T> T toJson(Path path, Class<T> clazz) throws Exception {
        InputStream inputStream = Files.newInputStream(path);
        return JSON.parseObject(inputStream, StandardCharsets.UTF_8, clazz);
    }

    public static String toJsonString(Object obj) {
        return JSON.toJSONString(obj);
    }

}
