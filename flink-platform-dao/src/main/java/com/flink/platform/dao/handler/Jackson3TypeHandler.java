package com.flink.platform.dao.handler;

import com.baomidou.mybatisplus.core.toolkit.Assert;
import com.baomidou.mybatisplus.extension.handlers.AbstractJsonTypeHandler;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.type.TypeFactory;

import java.lang.reflect.Field;

/**
 * Bypass errors in `com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler`.
 * Undo changes if MyBatis-Plus 3.5.15+ released.
 */
public class Jackson3TypeHandler extends AbstractJsonTypeHandler<Object> {

    private static ObjectMapper objectMapper;

    public Jackson3TypeHandler(Class<?> type) {
        super(type);
    }

    public Jackson3TypeHandler(Class<?> type, Field field) {
        super(type, field);
    }

    @Override
    public Object parse(String json) {
        ObjectMapper objectMapper = getObjectMapper();
        TypeFactory typeFactory = objectMapper.getTypeFactory();
        JavaType javaType = typeFactory.constructType(getFieldType());
        return objectMapper.readValue(json, javaType);
    }

    @Override
    public String toJson(Object obj) {
        return getObjectMapper().writeValueAsString(obj);
    }

    public static ObjectMapper getObjectMapper() {
        return objectMapper == null ? Instance.OBJECT_MAPPER : objectMapper;
    }

    public static void setObjectMapper(ObjectMapper objectMapper) {
        Assert.notNull(objectMapper, "ObjectMapper should not be null");
        Jackson3TypeHandler.objectMapper = objectMapper;
    }

    private static class Instance {

        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    }
}
