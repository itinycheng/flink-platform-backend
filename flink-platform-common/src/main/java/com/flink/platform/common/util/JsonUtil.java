package com.flink.platform.common.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.json.JsonMapper.Builder;
import tools.jackson.databind.module.SimpleModule;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.flink.platform.common.constants.Constant.GLOBAL_TIME_ZONE;
import static com.flink.platform.common.util.DateUtil.GLOBAL_DATE_TIME_FORMAT;
import static java.time.format.DateTimeFormatter.ofPattern;
import static tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static tools.jackson.databind.MapperFeature.PROPAGATE_TRANSIENT_MARKER;

/** json utils. */
@Slf4j
public class JsonUtil {

    public static final JsonMapper MAPPER;

    static {
        MAPPER = jacksonBuilderWithGlobalConfigs()
                .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.ALWAYS))
                .build();
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
        return toBean(inputStream, clazz);
    }

    public static <T> T toBean(InputStream inputStream, Class<T> clazz) {
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

    // ====================================================
    // ============== jackson global configs ==============
    // ====================================================

    public static JsonMapper.Builder jacksonBuilderWithGlobalConfigs() {
        Builder builder =
                JsonMapper.builder().addModules(defaultGlobalModules()).defaultTimeZone(GLOBAL_TIME_ZONE);
        builder.configure(PROPAGATE_TRANSIENT_MARKER, true);
        builder.configure(FAIL_ON_UNKNOWN_PROPERTIES, false);
        return builder;
    }

    private static List<JacksonModule> defaultGlobalModules() {
        return Collections.singletonList(new SimpleModule()
                .addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(ofPattern(GLOBAL_DATE_TIME_FORMAT)))
                .addDeserializer(
                        LocalDateTime.class, new LocalDateTimeDeserializer(ofPattern(GLOBAL_DATE_TIME_FORMAT))));
    }
}
