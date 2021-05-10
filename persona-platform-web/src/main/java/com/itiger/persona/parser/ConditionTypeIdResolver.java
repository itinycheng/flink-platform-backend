package com.itiger.persona.parser;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import com.fasterxml.jackson.databind.type.TypeFactory;

/**
 * @author tiny.wang
 */
public class ConditionTypeIdResolver extends TypeIdResolverBase {

    public ConditionTypeIdResolver() {
        this(TypeFactory.defaultInstance().constructType(Condition.class),
                TypeFactory.defaultInstance());
    }

    public ConditionTypeIdResolver(JavaType baseType, TypeFactory typeFactory) {
        super(baseType, typeFactory);
    }

    @Override
    public String idFromValue(Object value) {
        return null;
    }

    @Override
    public String idFromValueAndType(Object value, Class<?> suggestedType) {
        return null;
    }

    @Override
    public JavaType typeFromId(DatabindContext context, String id) {
        Class<?> clazz = Condition.class;
        if (id == null) {
            clazz = CompositeCondition.class;
        }
        return TypeFactory.defaultInstance().constructType(clazz);
    }

    @Override
    public JsonTypeInfo.Id getMechanism() {
        return JsonTypeInfo.Id.NAME;
    }
}
