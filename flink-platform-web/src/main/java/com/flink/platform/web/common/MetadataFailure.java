package com.flink.platform.web.common;

import io.grpc.Status.Code;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.dao.PermissionDeniedDataAccessException;
import org.springframework.transaction.TransactionException;

/**
 * Classifies orchestration failures by root exception family instead of individual types.
 *
 * <p>All Spring data-access and transaction failures are treated as metadata unavailable by
 * default, so new or unanticipated infrastructure exceptions need no extra handling. Only deterministic data/code
 * errors are excluded, because retrying them cannot succeed.
 */
public final class MetadataFailure {

    private MetadataFailure() {}

    public static boolean isUnavailable(@Nullable Throwable error) {
        boolean unavailable = false;
        for (Throwable cause : ExceptionUtils.getThrowableList(error)) {
            if (cause instanceof DataIntegrityViolationException
                    || cause instanceof InvalidDataAccessResourceUsageException
                    || cause instanceof InvalidDataAccessApiUsageException
                    || cause instanceof PermissionDeniedDataAccessException
                    || cause instanceof DataRetrievalFailureException) {
                return false;
            }

            // db unavailable.
            if (cause instanceof DataAccessException
                    || cause instanceof TransactionException
                    || isUnavailableGrpc(cause)) {
                unavailable = true;
            }
        }
        return unavailable;
    }

    public static void rethrowIfUnavailable(@Nullable Throwable error) {
        if (isUnavailable(error)) {
            throw new UnavailableException(error);
        }
    }

    private static boolean isUnavailableGrpc(Throwable cause) {
        if (cause instanceof StatusRuntimeException statusRuntimeException) {
            return isUnavailableCode(statusRuntimeException.getStatus().getCode());
        }
        if (cause instanceof StatusException statusException) {
            return isUnavailableCode(statusException.getStatus().getCode());
        }
        return false;
    }

    private static boolean isUnavailableCode(Code code) {
        return code == Code.UNAVAILABLE || code == Code.DEADLINE_EXCEEDED;
    }

    private static final class UnavailableException extends RuntimeException {
        private UnavailableException(@Nullable Throwable cause) {
            super(cause);
        }
    }
}
