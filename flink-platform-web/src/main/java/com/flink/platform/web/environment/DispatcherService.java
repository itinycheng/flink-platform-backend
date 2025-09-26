package com.flink.platform.web.environment;

import com.flink.platform.common.enums.DeployMode;
import com.flink.platform.common.util.FileUtil;
import com.flink.platform.web.common.SpringContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Paths;

import static com.flink.platform.common.constants.Constant.OS_FILE_SEPARATOR;
import static com.flink.platform.common.constants.Constant.SLASH;
import static com.flink.platform.common.constants.Constant.TMP_DIR;
import static com.flink.platform.common.constants.JobConstant.JOB_RUN_DIR;

/**
 * data dispatcher service.
 */
@Slf4j
@Component
public class DispatcherService {

    @Autowired
    public DispatcherService(@Lazy HadoopService hadoopService) {
        this.hadoopService = hadoopService;
    }

    private final HadoopService hadoopService;

    public String writeToExecutionEnv(DeployMode deployMode, String fileName, String content) throws IOException {
        switch (deployMode) {
            case RUN_LOCAL:
            case FLINK_YARN_PER:
                var localTmpFile = String.join(
                        OS_FILE_SEPARATOR, TMP_DIR, SpringContext.getApplicationName(), JOB_RUN_DIR, fileName);
                FileUtil.rewriteFile(Paths.get(localTmpFile), content);
                return localTmpFile;
            case FLINK_YARN_SESSION:
            case FLINK_YARN_RUN_APPLICATION:
                String hdfsFilePath =
                        String.join(SLASH, "hdfs:/tmp", SpringContext.getApplicationName(), JOB_RUN_DIR, fileName);
                hadoopService.writeToFilePath(hdfsFilePath, content);
                return hdfsFilePath;
            default:
                throw new IllegalArgumentException("Unsupported deploy mode: " + deployMode);
        }
    }
}
