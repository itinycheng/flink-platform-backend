package com.flink.platform.web.util;

import jakarta.annotation.Nonnull;

import static com.flink.platform.common.constants.Constant.FILE_SEPARATOR;
import static com.flink.platform.common.constants.Constant.ROOT_DIR;

/** Env path. */
public class PathUtil {

    private static final String WORK_DIR_NAME = "platform";

    private static final String JOB_ROOT_DIR_NAME = "job";

    private static final String JOB_WORK_DIR_NAME = "job_%d";

    private static final String USER_DIR_NAME = "user_%d";

    public static String getUserDirPath(@Nonnull Long userId) {
        String userDirName = String.format(USER_DIR_NAME, userId);
        return String.join(FILE_SEPARATOR, ROOT_DIR, WORK_DIR_NAME, userDirName);
    }

    public static String getExecJobDirPath(@Nonnull Long userId, @Nonnull Long jobId) {
        String userDirPath = getUserDirPath(userId);
        String jobExecDir = String.format(JOB_WORK_DIR_NAME, jobId);
        return String.join(FILE_SEPARATOR, userDirPath, JOB_ROOT_DIR_NAME, jobExecDir);
    }
}
