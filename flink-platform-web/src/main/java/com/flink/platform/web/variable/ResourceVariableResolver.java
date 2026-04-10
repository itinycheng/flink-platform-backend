package com.flink.platform.web.variable;

import com.flink.platform.common.util.FileUtil;
import com.flink.platform.dao.entity.JobRunInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static com.flink.platform.common.constants.JobConstant.RESOURCE_PATTERN;
import static com.flink.platform.common.enums.JobType.SHELL;
import static com.flink.platform.web.util.ResourceUtil.copyFromStorageToLocal;
import static com.flink.platform.web.util.ResourceUtil.getAbsoluteStoragePath;

/**
 * Resource variable resolver.
 * Resolves ${resource:file} placeholders and copies files from storage to
 * local.
 */
@Slf4j
@Order(2)
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ResourceVariableResolver implements VariableResolver {

    @Override
    public Map<String, Object> resolve(@Nullable JobRunInfo jobRun, String content) {
        try {
            var result = new HashMap<String, Object>();
            var matcher = RESOURCE_PATTERN.matcher(content);
            while (matcher.find()) {
                var variable = matcher.group();
                var fileOrId = matcher.group("file");
                if (StringUtils.isBlank(fileOrId)) {
                    throw new RuntimeException("Resource path not found, variable:" + variable);
                }

                var storage = matcher.group("storage");
                var absoluteStoragePath = getAbsoluteStoragePath(fileOrId);
                if ("local".equalsIgnoreCase(storage)) {
                    var localPath = copyFromStorageToLocal(absoluteStoragePath);
                    result.put(variable, localPath);

                    // Make the local file readable and executable.
                    setPermissionsIfNeeded(jobRun, localPath);
                } else {
                    result.put(variable, absoluteStoragePath);
                }
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to resolve resource variables", e);
            throw new RuntimeException("Failed to resolve resource variables", e);
        }
    }

    private void setPermissionsIfNeeded(@Nullable JobRunInfo jobRun, String localPath) {
        if (jobRun != null && SHELL.equals(jobRun.getType())) {
            try {
                FileUtil.setPermissions(Path.of(localPath), "rwxr--r--");
            } catch (Exception e) {
                log.error("Failed to set file permissions", e);
            }
        }
    }
}
