package com.flink.platform.common.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** json utils. */
@Slf4j
public class JsonUtil {

    public static final ObjectMapper MAPPER;

    static {
        MAPPER = new ObjectMapper();
        MAPPER.setSerializationInclusion(JsonInclude.Include.ALWAYS);
        MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public static List<String> toList(String json) {
        if (StringUtils.isBlank(json)) {
            return Collections.emptyList();
        }

        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.error("Failed to serial {} to List[String].", json, e);
            return Collections.emptyList();
        }
    }

    public static <T> List<T> toList(String json, JavaType javaType) {
        if (StringUtils.isBlank(json)) {
            return Collections.emptyList();
        }

        try {
            return MAPPER.readValue(json, javaType);
        } catch (Exception e) {
            log.error("Failed to serial {} to List[T]", json, e);
            return Collections.emptyList();
        }
    }

    public static Map<String, Object> toMap(String json) {
        if (StringUtils.isBlank(json)) {
            return Collections.emptyMap();
        }

        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Failed to serial {} to Map[String, Object].", json, e);
            return Collections.emptyMap();
        }
    }

    public static Map<String, String> toStrMap(String json) {
        if (StringUtils.isBlank(json)) {
            return Collections.emptyMap();
        }

        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.error("Failed to serial {} to Map[String, String].", json, e);
            return Collections.emptyMap();
        }
    }

    public static <T> T toBean(String json, Class<T> clazz) {
        if (StringUtils.isBlank(json)) {
            return null;
        }

        try {
            return MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            log.error("Failed to serial {} to {}.", json, clazz, e);
            return null;
        }
    }

    public static <T> T toBean(Path path, Class<T> clazz) throws IOException {
        InputStream inputStream = Files.newInputStream(path);
        return MAPPER.readValue(inputStream, clazz);
    }

    public static String toJsonString(Object obj) {
        if (obj == null) {
            return null;
        }

        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Failed to serial {}.", obj, e);
            return null;
        }
    }
}
