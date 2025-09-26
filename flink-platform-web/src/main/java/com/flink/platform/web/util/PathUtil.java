package com.flink.platform.web.util;

import com.flink.platform.common.enums.JobType;
import jakarta.annotation.Nonnull;

import java.nio.file.Paths;

import static com.flink.platform.common.constants.Constant.OS_FILE_SEPARATOR;
import static com.flink.platform.common.constants.Constant.USER_DIR;
import static com.flink.platform.common.constants.JobConstant.JOB_RUN_DIR;

/** Env path. */
public class PathUtil {

    public static final String WORK_DIR = "platform";

    public static final String JOB_DIR_FORMAT = "job_%d";

    public static final String USER_DIR_FORMAT = "user_%d";

    public static String getExecJobDirPath(@Nonnull Long userId, @Nonnull Long jobId, @Nonnull JobType jobType) {
        String jobRootPath = getExecJobRootPath();
        String userDirName = String.format(USER_DIR_FORMAT, userId);
        String jobDirName = String.format(JOB_DIR_FORMAT, jobId);
        return String.join(
                OS_FILE_SEPARATOR, jobRootPath, userDirName, jobType.name().toLowerCase(), jobDirName);
    }

    public static String getExecJobRootPath() {
        return String.join(OS_FILE_SEPARATOR, getLocalWorkRootPath(), JOB_RUN_DIR);
    }

    public static String getLocalWorkRootPath() {
        if (Paths.get(WORK_DIR).isAbsolute()) {
            return WORK_DIR;
        }

        return String.join(OS_FILE_SEPARATOR, USER_DIR, WORK_DIR);
    }
}
