package com.flink.platform.web.dto.request;

import java.util.regex.Pattern;

/** Shared validators for request DTOs. */
public final class RequestValidations {

    public static final String NAME_REGEX = "^[a-zA-Z][a-zA-Z0-9._-]{5,64}$";

    public static final Pattern NAME_PATTERN = Pattern.compile(NAME_REGEX);

    public static String requireValidName(String name, String entityKind) {
        String errorMsg = null;
        if (name == null) {
            errorMsg = String.format("The name of %s cannot be null", entityKind);
        } else if (!NAME_PATTERN.matcher(name).matches()) {
            errorMsg = String.format("invalid %s name, regex: `%s`", entityKind, NAME_PATTERN.pattern());
        }
        return errorMsg;
    }
}
