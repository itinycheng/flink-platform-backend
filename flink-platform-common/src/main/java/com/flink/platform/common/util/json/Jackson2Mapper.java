package com.flink.platform.common.util.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.json.JsonMapper.Builder;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.MapperFeature.PROPAGATE_TRANSIENT_MARKER;
import static com.flink.platform.common.constants.Constant.GLOBAL_TIME_ZONE;
import static com.flink.platform.common.util.DateUtil.GLOBAL_DATE_TIME_FORMAT;
import static java.time.format.DateTimeFormatter.ofPattern;

/** json utils. */
@Slf4j
public class Jackson2Mapper implements JacksonBaseMapper<JsonMapper> {

    public final JsonMapper mapper;

    public Jackson2Mapper() {
        mapper = jacksonBuilderWithGlobalConfigs()
                .defaultPropertyInclusion(JsonInclude.Value.empty().withValueInclusion(JsonInclude.Include.ALWAYS))
                .build();
    }

    @Override
    public JsonMapper getMapper() {
        return mapper;
    }

    @Override
    public Logger getLogger() {
        return log;
    }

    @Override
    public Map<String, String> readValueStrMap(String json) throws Exception {
        return mapper.readValue(json, new TypeReference<Map<String, String>>() {});
    }

    @Override
    public Map<String, Object> readValueMap(String json) throws Exception {
        return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    }

    @Override
    public String writeValueAsString(Object obj) throws Exception {
        return mapper.writeValueAsString(obj);
    }

    @Override
    public <OUT> OUT readValue(InputStream inputStream, Class<OUT> clazz) throws Exception {
        return mapper.readValue(inputStream, clazz);
    }

    @Override
    public <OUT> OUT readValue(String json, Class<OUT> clazz) throws Exception {
        return mapper.readValue(json, clazz);
    }

    @Override
    public <OUT> List<OUT> readValueList(String json, Class<OUT> clazz) throws Exception {
        JavaType javaType = mapper.getTypeFactory().constructCollectionType(List.class, clazz);
        return mapper.readValue(json, javaType);
    }

    // ====================================================
    // ============== jackson global configs ==============
    // ====================================================

    public static JsonMapper.Builder jacksonBuilderWithGlobalConfigs() {
        Builder builder = JsonMapper.builder()
                .addModule(new Jdk8Module())
                .addModule(new JavaTimeModule()
                        .addSerializer(
                                LocalDateTime.class, new LocalDateTimeSerializer(ofPattern(GLOBAL_DATE_TIME_FORMAT)))
                        .addDeserializer(
                                LocalDateTime.class, new LocalDateTimeDeserializer(ofPattern(GLOBAL_DATE_TIME_FORMAT))))
                .defaultTimeZone(GLOBAL_TIME_ZONE);
        builder.configure(PROPAGATE_TRANSIENT_MARKER, true);
        builder.configure(FAIL_ON_UNKNOWN_PROPERTIES, false);
        return builder;
    }
}
