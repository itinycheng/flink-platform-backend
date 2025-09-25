package com.flink.platform.web.environment;

import com.flink.platform.common.enums.DeployMode;
import com.flink.platform.common.util.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Paths;

import static com.flink.platform.common.constants.Constant.OS_FILE_SEPARATOR;
import static com.flink.platform.common.constants.Constant.SLASH;
import static com.flink.platform.common.constants.Constant.USER_DIR;

/**
 * data dispatcher service.
 */
@Slf4j
@Component
public class DataDispatcherService {

    @Autowired
    public DataDispatcherService(@Lazy HadoopService hadoopService) {
        this.hadoopService = hadoopService;
    }

    private final HadoopService hadoopService;

    public String writeToExecutionEnv(DeployMode deployMode, String fileName, String content) throws IOException {
        switch (deployMode) {
            case RUN_LOCAL:
            case FLINK_YARN_PER:
                String localFilePath = String.join(OS_FILE_SEPARATOR, USER_DIR, "tmp", "job_run", fileName);
                FileUtil.rewriteFile(Paths.get(localFilePath), content);
                return localFilePath;
            case FLINK_YARN_SESSION:
            case FLINK_YARN_RUN_APPLICATION:
                String hdfsFilePath = String.join(SLASH, "tmp", "job_run", fileName);
                hadoopService.writeToFilePath(hdfsFilePath, content);
                return hdfsFilePath;
            default:
                throw new IllegalArgumentException("Unsupported deploy mode: " + deployMode);
        }
    }
}
