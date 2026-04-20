package com.flink.platform.web.common;

import com.flink.platform.common.exception.DefinitionException;
import org.jspecify.annotations.Nullable;

import static com.flink.platform.common.enums.ResponseStatus.INVALID_WORKSPACE_ID;

/** ThreadLocal holder for per-request context (user id and workspace id). */
public final class RequestContext {

    public record Context(Long userId, @Nullable Long workspaceId) {}

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

    public static Long requireWorkspaceId() {
        var context = HOLDER.get();
        if (context == null || context.workspaceId() == null) {
            throw new DefinitionException(INVALID_WORKSPACE_ID);
        }
        return context.workspaceId();
    }

    public static @Nullable Context get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
