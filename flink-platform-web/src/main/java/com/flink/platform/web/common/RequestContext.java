package com.flink.platform.web.common;

import com.flink.platform.common.exception.DefinitionException;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

import static com.flink.platform.common.enums.ResponseStatus.INVALID_WORKSPACE_ID;

/**
 * ThreadLocal holder for the current execution context: the authenticated user and the workspace
 * whose data the current work is scoped to.
 *
 * <p>The HTTP layer populates it via {@link #set}/{@link #clear}. Background work (scheduler, gRPC,
 * thread pools) can scope itself to a workspace via {@link #runAs}. When no workspace is present the
 * tenant filter is simply not applied (fail-open).
 */
public final class RequestContext {

    /** Authenticated user + selected workspace for the current execution. */
    public record Context(@Nullable Long userId, @Nullable Long workspaceId) {}

    private static final ThreadLocal<@Nullable Context> HOLDER = new ThreadLocal<>();

    private RequestContext() {}

    public static @Nullable Context get() {
        return HOLDER.get();
    }

    public static void set(Context context) {
        HOLDER.set(context);
    }

    public static @Nullable Long getUserId() {
        var context = HOLDER.get();
        return context != null ? context.userId() : null;
    }

    public static @Nullable Long getWorkspaceId() {
        var context = HOLDER.get();
        return context != null ? context.workspaceId() : null;
    }

    public static Long requireWorkspaceId() {
        var context = HOLDER.get();
        if (context == null || context.workspaceId() == null) {
            throw new DefinitionException(INVALID_WORKSPACE_ID);
        }
        return context.workspaceId();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static <T> T runAs(long workspaceId, Supplier<T> action) {
        var previous = HOLDER.get();
        HOLDER.set(new Context(previous != null ? previous.userId() : null, workspaceId));
        try {
            return action.get();
        } finally {
            restore(previous);
        }
    }

    public static void runAs(long workspaceId, Runnable action) {
        var previous = HOLDER.get();
        HOLDER.set(new Context(previous != null ? previous.userId() : null, workspaceId));
        try {
            action.run();
        } finally {
            restore(previous);
        }
    }

    private static void restore(@Nullable Context previous) {
        if (previous != null) {
            HOLDER.set(previous);
        } else {
            HOLDER.remove();
        }
    }
}
