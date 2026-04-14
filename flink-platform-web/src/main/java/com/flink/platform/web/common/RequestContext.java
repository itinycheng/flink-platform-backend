package com.flink.platform.web.common;

import org.jspecify.annotations.Nullable;

/** ThreadLocal holder for per-request context (user id and workspace id). */
public final class RequestContext {

    public record Context(Long userId, Long workspaceId) {}

    private static final ThreadLocal<@Nullable Context> HOLDER = new ThreadLocal<>();

    private RequestContext() {}

    public static void set(Context context) {
        HOLDER.set(context);
    }

    public static @Nullable Long getUserId() {
        var context = HOLDER.get();
        return context != null ? context.userId : null;
    }

    public static @Nullable Long getWorkspaceId() {
        var context = HOLDER.get();
        return context != null ? context.workspaceId : null;
    }

    public static @Nullable Context get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
