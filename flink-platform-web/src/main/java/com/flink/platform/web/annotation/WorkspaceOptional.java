package com.flink.platform.web.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method as not requiring a workspace context.
 *
 * <p>By default, all protected endpoints must supply a valid {@code X-Workspace-Id} header.
 * Annotate the rare bootstrap endpoints (e.g. {@code /user/info}, {@code /workspace/list})
 * that are called before a workspace is selected with this annotation to opt out.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface WorkspaceOptional {}
