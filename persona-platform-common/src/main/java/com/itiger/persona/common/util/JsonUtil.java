package com.itiger.persona.common.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * json utils
 *
 * @author tiny.wang
 */
@Slf4j
public class JsonUtil {

    public static List<String> toList(String res) {
        return toList(res, String.class);
    }

    public static <T> List<T> toList(String res, Class<T> clazz) {
        return FunctionUtil.getOrDefault(() -> JSON.parseArray(res, clazz),
                Collections.emptyList());
    }

    public static Map<String, Object> toMap(String res) {
        return FunctionUtil.getOrDefault(() -> JSON.parseObject(res),
                Collections.emptyMap());
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
        return FunctionUtil.getOrDefault(() -> JSON.parseObject(res, clazz), null);
    }

    public static <T> T toBean(Path path, Class<T> clazz) throws Exception {
        InputStream inputStream = Files.newInputStream(path);
        return JSON.parseObject(inputStream, StandardCharsets.UTF_8, clazz);
    }

    public static String toJsonString(Object obj) {
        return JSON.toJSONString(obj, SerializerFeature.WriteMapNullValue);
    }

}
