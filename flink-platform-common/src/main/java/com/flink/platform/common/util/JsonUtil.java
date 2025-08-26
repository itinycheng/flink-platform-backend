package com.flink.platform.common.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.json.JsonMapper.Builder;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.MapperFeature.PROPAGATE_TRANSIENT_MARKER;
import static com.flink.platform.common.constants.Constant.GLOBAL_TIME_ZONE;
import static com.flink.platform.common.util.DateUtil.GLOBAL_DATE_TIME_FORMAT;
import static java.time.format.DateTimeFormatter.ofPattern;

/** json utils. */
@Slf4j
public class JsonUtil {

    public static final ObjectMapper MAPPER;

    static {
        MAPPER = jacksonBuilderWithGlobalConfigs()
                .serializationInclusion(JsonInclude.Include.ALWAYS)
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
                JsonMapper.builder().addModules(defaultGlobalModules()).defaultTimeZone(defaultGlobalTimeZone());
        addDefaultGlobalFeaturesTo(builder);
        return builder;
    }

    public static TimeZone defaultGlobalTimeZone() {
        return GLOBAL_TIME_ZONE;
    }

    public static Map<Object, Boolean> defaultGlobalFeatures() {
        Map<Object, Boolean> features = new HashMap<>();
        features.put(PROPAGATE_TRANSIENT_MARKER, true);
        features.put(FAIL_ON_UNKNOWN_PROPERTIES, false);
        return features;
    }

    public static List<Module> defaultGlobalModules() {
        return Arrays.asList(
                new Jdk8Module(),
                new JavaTimeModule()
                        .addSerializer(
                                LocalDateTime.class, new LocalDateTimeSerializer(ofPattern(GLOBAL_DATE_TIME_FORMAT)))
                        .addDeserializer(
                                LocalDateTime.class,
                                new LocalDateTimeDeserializer(ofPattern(GLOBAL_DATE_TIME_FORMAT))));
    }

    private static void addDefaultGlobalFeaturesTo(JsonMapper.Builder builder) {
        defaultGlobalFeatures().forEach((feature, enable) -> {
            if (enable) {
                if (feature instanceof MapperFeature) {
                    builder.enable((MapperFeature) feature);
                } else if (feature instanceof DeserializationFeature) {
                    builder.enable((DeserializationFeature) feature);
                }
            } else {
                if (feature instanceof MapperFeature) {
                    builder.disable((MapperFeature) feature);
                } else if (feature instanceof DeserializationFeature) {
                    builder.disable((DeserializationFeature) feature);
                }
            }
        });
    }
}
