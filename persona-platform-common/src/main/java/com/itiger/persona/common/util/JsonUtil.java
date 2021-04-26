package com.itiger.persona.common.util;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * json utils
 *
 * @author tiny.wang
 */
@Slf4j
public class JsonUtil {

    public static List<String> toList(String res) {
        return Optional.ofNullable((List<Object>) JSON.parseArray(res))
                .orElse(Collections.emptyList())
                .stream()
                .map(Object::toString)
                .collect(Collectors.toList());
    }

    public static Map<String, Object> toMap(String res) {
        return Optional.ofNullable((Map<String, Object>) JSON.parseObject(res))
                .orElse(Collections.emptyMap());
    }

    public static Map<String, String> toStrMap(String res) {
        return JsonUtil.toMap(res)
                .entrySet()
                .stream()
                .filter(entry -> Objects.nonNull(entry.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> String.valueOf(entry.getValue())));
    }

    public static <T> T toBean(String res, Class<T> clazz) {
        return JSON.parseObject(res, clazz);
    }

    public static <T> T toBean(Path path, Class<T> clazz) throws Exception {
        InputStream inputStream = Files.newInputStream(path);
        return JSON.parseObject(inputStream, StandardCharsets.UTF_8, clazz);
    }

    public static String toJsonString(Object obj) {
        return JSON.toJSONString(obj);
    }

}
