package com.flink.platform.common.util.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.json.JsonMapper.Builder;
import tools.jackson.databind.module.SimpleModule;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.flink.platform.common.constants.Constant.GLOBAL_TIME_ZONE;
import static com.flink.platform.common.util.DateUtil.GLOBAL_DATE_TIME_FORMAT;
import static java.time.format.DateTimeFormatter.ofPattern;
import static tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static tools.jackson.databind.MapperFeature.PROPAGATE_TRANSIENT_MARKER;

/** json utils. */
@Slf4j
public class Jackson3Mapper implements JacksonBaseMapper<JsonMapper> {

    public final JsonMapper mapper;

    public Jackson3Mapper() {
        mapper = jacksonBuilderWithGlobalConfigs()
                .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.ALWAYS))
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

    public JsonMapper.Builder jacksonBuilderWithGlobalConfigs() {
        Builder builder = JsonMapper.builder()
                .addModule(new SimpleModule()
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
