package com.flink.platform.web.util;

import com.flink.platform.common.enums.JobType;
import jakarta.annotation.Nonnull;

import java.nio.file.Paths;

import static com.flink.platform.common.constants.Constant.FILE_SEPARATOR;
import static com.flink.platform.common.constants.Constant.USER_DIR;
import static com.flink.platform.common.enums.JobType.SHELL;

/** Env path. */
public class PathUtil {

    private static final String WORK_DIR = "platform";

    private static final String JOB_ROOT_DIR_NAME = "jobs";

    private static final String JOB_WORK_DIR_NAME = "job_%d";

    private static final String USER_DIR_NAME = "user_%d";

    public static String getExecJobDirPath(@Nonnull Long userId, @Nonnull Long jobId, @Nonnull JobType jobType) {
        String jobRootPath = getExecJobRootPath();
        String userDirName = String.format(USER_DIR_NAME, userId);
        String jobDirName = String.format(JOB_WORK_DIR_NAME, jobId);
        return String.join(
                FILE_SEPARATOR, jobRootPath, userDirName, jobType.name().toLowerCase(), jobDirName);
    }

    public static String getExecJobRootPath() {
        return String.join(FILE_SEPARATOR, getWorkRootPath(), JOB_ROOT_DIR_NAME);
    }

    public static String getWorkRootPath() {
        if (Paths.get(WORK_DIR).isAbsolute()) {
            return WORK_DIR;
        }

        return String.join(FILE_SEPARATOR, USER_DIR, WORK_DIR);
    }

    public static void main(String[] args) {
        String userDirPath = getExecJobDirPath(1L, 12L, SHELL);
        System.out.println(userDirPath);
    }
}
