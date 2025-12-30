package com.flink.platform.common.util;

import com.flink.platform.common.util.json.Jackson2Mapper;
import com.flink.platform.common.util.json.Jackson3Mapper;
import com.flink.platform.common.util.json.JacksonBaseMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** json utils. */
@Slf4j
public class JsonUtil {

    public static final JacksonBaseMapper<?> MAPPER;

    static {
        MAPPER = createMapper();
    }

    public static List<String> toList(String json) {
        return MAPPER.toList(json);
    }

    public static <T> List<T> toList(String json, Class<T> clazz) {
        return MAPPER.toList(json, clazz);
    }

    public static Map<String, Object> toMap(String json) {
        return MAPPER.toMap(json);
    }

    public static Map<String, String> toStrMap(String json) {
        return MAPPER.toStrMap(json);
    }

    public static <T> T toBean(String json, Class<T> clazz) {
        return MAPPER.toBean(json, clazz);
    }

    public static <T> T toBean(Path path, Class<T> clazz) throws Exception {
        return MAPPER.toBean(path, clazz);
    }

    public static <T> T toBean(InputStream inputStream, Class<T> clazz) throws Exception {
        return MAPPER.toBean(inputStream, clazz);
    }

    public static String toJsonString(Object obj) {
        return MAPPER.toJsonString(obj);
    }

    private static JacksonBaseMapper<?> createMapper() {
        try {
            Class.forName("tools.jackson.databind.json.JsonMapper");
            log.info("Jackson 3.x found, using Jackson 3.x");
            return new Jackson3Mapper();
        } catch (Exception e) {
            try {
                Class.forName("com.fasterxml.jackson.databind.json.JsonMapper");
                log.info("Jackson 3.x not found, fallback to Jackson 2.x");
                return new Jackson2Mapper();
            } catch (Exception ex) {
                throw new RuntimeException("No Jackson library found in classpath", ex);
            }
        }
    }
}
