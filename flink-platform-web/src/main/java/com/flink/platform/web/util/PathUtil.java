package com.flink.platform.web.util;

import java.nio.file.Path;

import static com.flink.platform.common.constants.Constant.OS_FILE_SEPARATOR;
import static com.flink.platform.common.constants.Constant.USER_DIR;
import static com.flink.platform.common.constants.JobConstant.JOB_RUN_DIR;

/** Env path. */
public class PathUtil {

    public static final String WORK_DIR = "platform";

    public static String getExecJobRootPath() {
        return String.join(OS_FILE_SEPARATOR, getLocalWorkRootPath(), JOB_RUN_DIR);
    }

    public static String getLocalWorkRootPath() {
        if (Path.of(WORK_DIR).isAbsolute()) {
            return WORK_DIR;
        }

        return String.join(OS_FILE_SEPARATOR, USER_DIR, WORK_DIR);
    }
}
