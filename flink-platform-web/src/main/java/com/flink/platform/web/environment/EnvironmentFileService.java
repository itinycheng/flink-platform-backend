package com.flink.platform.web.environment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Façade that exposes a unified file-transfer API for job dispatch and routes each call to the
 * first {@link EnvironmentFileAdapter#isAvailable() available} {@link EnvironmentFileAdapter} in
 * Spring {@link org.springframework.core.annotation.Order @Order} priority — HDFS preferred, S3
 * fallback.
 */
@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class EnvironmentFileService {

    private final List<EnvironmentFileAdapter> adapters;

    public void copyIfChanged(String localFile, String remoteFile) throws Exception {
        firstAdapter().copyIfChanged(localFile, remoteFile);
    }

    public void writeToFilePath(String filePath, String content) throws Exception {
        firstAdapter().writeToFilePath(filePath, content);
    }

    public String buildTempPath(String... segments) {
        return firstAdapter().buildTempPath(segments);
    }

    private EnvironmentFileAdapter firstAdapter() {
        if (adapters.isEmpty()) {
            throw new IllegalStateException(
                    "No environment file adapter registered; ensure HDFS or S3 environment is registered");
        }

        var adapter = adapters.getFirst();
        if (!adapter.isAvailable()) {
            throw new IllegalStateException(
                    "First environment adapter is not available, type: " + adapter.supportedType());
        }

        return adapter;
    }
}
