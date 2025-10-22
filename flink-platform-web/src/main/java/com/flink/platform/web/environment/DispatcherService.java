package com.flink.platform.web.environment;

import com.flink.platform.common.enums.DeployMode;
import com.flink.platform.common.util.FileUtil;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.web.common.SpringContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;

import static com.flink.platform.common.constants.Constant.DOT;
import static com.flink.platform.common.constants.Constant.OS_FILE_SEPARATOR;
import static com.flink.platform.common.constants.Constant.SLASH;
import static com.flink.platform.common.constants.Constant.TMP;
import static com.flink.platform.common.constants.JobConstant.JOB_RUN_DIR;
import static com.flink.platform.web.util.PathUtil.getLocalWorkRootPath;

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

    public String buildLocalEnvFilePath(JobRunInfo jobRun, String fileSuffix) {
        var fileName = String.join(DOT, jobRun.getJobCode(), fileSuffix);
        var applicationName = SpringContext.getApplicationName();
        var deployMode = jobRun.getDeployMode();
        return switch (deployMode) {
            case RUN_LOCAL, FLINK_YARN_PER -> String.join(
                    OS_FILE_SEPARATOR, getLocalWorkRootPath(), TMP, JOB_RUN_DIR, fileName);
            case FLINK_YARN_SESSION, FLINK_YARN_RUN_APPLICATION -> String.join(
                    SLASH, "hdfs:/tmp", applicationName, JOB_RUN_DIR, fileName);
            default -> throw new IllegalArgumentException("Unsupported deploy mode: " + deployMode);
        };
    }

    public void writeToLocalEnv(DeployMode deployMode, String filePath, String content) throws IOException {
        switch (deployMode) {
            case RUN_LOCAL:
            case FLINK_YARN_PER:
                FileUtil.rewriteFile(Path.of(filePath), content);
                break;
            case FLINK_YARN_SESSION:
            case FLINK_YARN_RUN_APPLICATION:
                hadoopService.writeToFilePath(filePath, content);
                break;
            default:
                throw new IllegalArgumentException("Unsupported deploy mode: " + deployMode);
        }
    }
}
