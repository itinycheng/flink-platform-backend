package com.flink.platform.web.util;

import com.flink.platform.common.enums.JobType;
import jakarta.annotation.Nonnull;

import java.nio.file.Paths;

import static com.flink.platform.common.constants.Constant.FILE_SEPARATOR;
import static com.flink.platform.common.constants.Constant.ROOT_DIR;
import static com.flink.platform.common.enums.JobType.SHELL;

/** Env path. */
public class PathUtil {

    private static final String WORK_DIR = "platform";

    private static final String JOB_ROOT_DIR_NAME = "jobs";

    private static final String JOB_WORK_DIR_NAME = "job_%d";

    private static final String USER_DIR_NAME = "user_%d";

    public static String getExecJobDirPath(@Nonnull Long userId, @Nonnull Long jobId, @Nonnull JobType jobType) {
        String userDirPath = getUserDirPath(userId);
        String jobExecDir = String.format(JOB_WORK_DIR_NAME, jobId);
        return String.join(
                FILE_SEPARATOR, userDirPath, JOB_ROOT_DIR_NAME, jobType.name().toLowerCase(), jobExecDir);
    }

    public static String getUserDirPath(@Nonnull Long userId) {
        String userDirName = String.format(USER_DIR_NAME, userId);
        return String.join(FILE_SEPARATOR, getWorkRootPath(), userDirName);
    }

    public static String getWorkRootPath() {
        if (Paths.get(WORK_DIR).isAbsolute()) {
            return WORK_DIR;
        }

        return String.join(FILE_SEPARATOR, ROOT_DIR, WORK_DIR);
    }

    public static void main(String[] args) {
        String userDirPath = getExecJobDirPath(1L, 12L, SHELL);
        System.out.println(userDirPath);
    }
}
