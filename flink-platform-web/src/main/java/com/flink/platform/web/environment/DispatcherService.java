package com.flink.platform.web.environment;

import com.flink.platform.common.enums.DeployMode;
import com.flink.platform.common.util.FileUtil;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.web.common.SpringContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

import static com.flink.platform.common.constants.Constant.DOT;
import static com.flink.platform.common.constants.Constant.OS_FILE_SEPARATOR;
import static com.flink.platform.common.constants.Constant.TMP;
import static com.flink.platform.common.constants.JobConstant.JOB_RUN_DIR;
import static com.flink.platform.web.util.PathUtil.getLocalWorkRootPath;

/**
 * data dispatcher service.
 */
@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DispatcherService {

    private final EnvironmentFileService fileService;

    public String buildLocalEnvFilePath(JobRunInfo jobRun, String fileSuffix) {
        var fileName = String.join(DOT, jobRun.getJobCode(), fileSuffix);
        var applicationName = SpringContext.getApplicationName();
        var deployMode = jobRun.getDeployMode();
        return switch (deployMode) {
            case RUN_LOCAL, FLINK_YARN_PER ->
                String.join(OS_FILE_SEPARATOR, getLocalWorkRootPath(), TMP, JOB_RUN_DIR, fileName);
            case FLINK_YARN_SESSION, FLINK_YARN_RUN_APPLICATION ->
                fileService.buildTempPath(applicationName, JOB_RUN_DIR, fileName);
        };
    }

    public void writeToLocalEnv(DeployMode deployMode, String filePath, String content) throws Exception {
        switch (deployMode) {
            case RUN_LOCAL:
            case FLINK_YARN_PER:
                FileUtil.rewriteFile(Path.of(filePath), content);
                break;
            case FLINK_YARN_SESSION:
            case FLINK_YARN_RUN_APPLICATION:
                fileService.writeToFilePath(filePath, content);
                break;
            default:
                throw new IllegalArgumentException("Unsupported deploy mode: " + deployMode);
        }
    }
}
