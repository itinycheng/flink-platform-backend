package com.itiger.persona.common.util;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * json utils
 *
 * @author tiny.wang
 */
@Slf4j
public class JsonUtil {

    public static <T> T toJson(String res, Class<T> clazz) {
        try {
            return JSON.parseObject(res, clazz);
        } catch (Exception e) {
            log.error("parseObject is error");
            return null;
        }
    }

    public static <T> T toJson(Path path, Class<T> clazz) throws Exception {
        InputStream inputStream = Files.newInputStream(path);
        return JSON.parseObject(inputStream, StandardCharsets.UTF_8, clazz);
    }

    public static String toJsonString(Object obj) {
        try {
            return JSON.toJSONString(obj);
        } catch (Exception e) {
            log.error("parse object to json string failed", e);
            return null;
        }
    }

}
