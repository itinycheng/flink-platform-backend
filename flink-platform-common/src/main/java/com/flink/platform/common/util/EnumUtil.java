package com.flink.platform.common.util;

import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.toList;

public class EnumUtil {

    public static @NonNull List<? extends Enum<?>> getDeprecatedEnums(Class<? extends Enum<?>> enumClass) {
        return Arrays.stream(enumClass.getEnumConstants())
                .filter(e -> {
                    try {
                        return e.getDeclaringClass().getField(e.name()).isAnnotationPresent(Deprecated.class);
                    } catch (NoSuchFieldException ex) {
                        return false;
                    }
                })
                .collect(toList());
    }
}
