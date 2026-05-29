package com.flink.platform.web.environment;

import com.flink.platform.common.environment.EnvironmentType;
import com.flink.platform.environment.EnvironmentRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Internal adapter backing {@link EnvironmentFileService}: ensures resources from the platform's
 * storage are present on the worker-visible environment before a job submission, and supports
 * direct content writes.
 */
@Slf4j
public abstract class EnvironmentFileAdapter {

    protected final EnvironmentRegistry registry;

    private final String primaryClusterIdFilePath;

    private volatile boolean onPrimaryCluster;

    protected EnvironmentFileAdapter(EnvironmentRegistry registry, String primaryClusterIdFilePath) {
        this.registry = registry;
        this.primaryClusterIdFilePath = primaryClusterIdFilePath;
    }

    @PostConstruct
    public final void detectPrimaryCluster() {
        if (!isAvailable()) {
            log.info("{} environment not registered; skipping primary cluster check.", supportedType());
            return;
        }

        try {
            onPrimaryCluster = checkOnPrimaryCluster(primaryClusterIdFilePath);
            log.info("{} primary cluster check: onPrimaryCluster={}", supportedType(), onPrimaryCluster);
        } catch (Exception e) {
            throw new RuntimeException("check primary cluster failed for " + supportedType(), e);
        }
    }

    /** The environment type this adapter binds to. */
    public abstract EnvironmentType supportedType();

    /** True if the underlying environment is registered and the client is reachable. */
    public final boolean isAvailable() {
        return registry.hasClient(supportedType());
    }

    /**
     * Subclass-specific check for whether the given storage path resides on this environment and
     * the marker file exists.
     */
    protected abstract boolean checkOnPrimaryCluster(String primaryClusterIdFilePath) throws Exception;

    /**
     * Copy {@code localFile} to {@code remoteFile} when the remote does not exist or differs (size
     * or mtime). No-op if this node is on the primary cluster — files already live there.
     */
    public final void copyIfChanged(String localFile, String remoteFile) throws Exception {
        if (onPrimaryCluster) {
            return;
        }
        doCopyIfChanged(localFile, remoteFile);
    }

    protected abstract void doCopyIfChanged(String localFile, String remoteFile) throws Exception;

    /** Write {@code content} to {@code filePath}, overwriting if it exists. */
    public abstract void writeToFilePath(String filePath, String content) throws Exception;

    /**
     * Build a fully-qualified temp path under this environment for job-run working files.
     */
    public abstract String buildTempPath(String... segments);
}
