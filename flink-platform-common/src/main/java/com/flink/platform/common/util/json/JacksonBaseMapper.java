package com.flink.platform.common.util.json;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface JacksonBaseMapper<M> {

    M getMapper();

    Logger getLogger();

    Map<String, String> readValueStrMap(String json) throws Exception;

    Map<String, Object> readValueMap(String json) throws Exception;

    <OUT> List<OUT> readValueList(String json, Class<OUT> clazz) throws Exception;

    <OUT> OUT readValue(String json, Class<OUT> clazz) throws Exception;

    <OUT> OUT readValue(InputStream inputStream, Class<OUT> clazz) throws Exception;

    String writeValueAsString(Object obj) throws Exception;

    default List<String> toList(String json) {
        if (StringUtils.isBlank(json)) {
            return Collections.emptyList();
        }

        try {
            return readValueList(json, String.class);
        } catch (Exception e) {
            getLogger().error("Failed to serial {} to List[String].", json, e);
            return Collections.emptyList();
        }
    }

    default <T> List<T> toList(String json, Class<T> clazz) {
        if (StringUtils.isBlank(json)) {
            return Collections.emptyList();
        }

        try {
            return readValueList(json, clazz);
        } catch (Exception e) {
            getLogger().error("Failed to serial {} to List[T]", json, e);
            return Collections.emptyList();
        }
    }

    default Map<String, Object> toMap(String json) {
        if (StringUtils.isBlank(json)) {
            return Collections.emptyMap();
        }

        try {
            return readValueMap(json);
        } catch (Exception e) {
            getLogger().error("Failed to serial {} to Map[String, Object].", json, e);
            return Collections.emptyMap();
        }
    }

    default Map<String, String> toStrMap(String json) {
        if (StringUtils.isBlank(json)) {
            return Collections.emptyMap();
        }

        try {
            return readValueStrMap(json);
        } catch (Exception e) {
            getLogger().error("Failed to serial {} to Map[String, String].", json, e);
            return Collections.emptyMap();
        }
    }

    default <T> T toBean(String json, Class<T> clazz) {
        if (StringUtils.isBlank(json)) {
            return null;
        }

        try {
            return readValue(json, clazz);
        } catch (Exception e) {
            getLogger().error("Failed to serial {} to {}.", json, clazz, e);
            return null;
        }
    }

    default <T> T toBean(Path path, Class<T> clazz) throws Exception {
        InputStream inputStream = Files.newInputStream(path);
        return toBean(inputStream, clazz);
    }

    default <T> T toBean(InputStream inputStream, Class<T> clazz) throws Exception {
        return readValue(inputStream, clazz);
    }

    default String toJsonString(Object obj) {
        if (obj == null) {
            return null;
        }

        try {
            return writeValueAsString(obj);
        } catch (Exception e) {
            getLogger().error("Failed to serial {}.", obj, e);
            return null;
        }
    }
}
