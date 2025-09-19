package com.flink.platform.web.util;

import com.flink.platform.common.enums.JobType;
import com.flink.platform.common.util.DateUtil;
import com.flink.platform.dao.entity.JobRunInfo;
import jakarta.annotation.Nonnull;

import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;

import static com.flink.platform.common.constants.Constant.OS_FILE_SEPARATOR;
import static com.flink.platform.common.constants.Constant.USER_DIR;
import static com.flink.platform.common.util.DateUtil.DATE_FORMAT;

/** Env path. */
public class PathUtil {

    private static final String WORK_DIR = "platform";

    private static final String JOB_ROOT_DIR = "job_run";

    private static final String JOB_DIR_FORMAT = "job_%d";

    private static final String USER_DIR_FORMAT = "user_%d";

    // e.g.: job_run/20250910/user_1/flink_sql/job_328
    public static String getJobRunRelativePath(JobRunInfo jobRun) {
        DateTimeFormatter formatter = DateUtil.getFormatter(DATE_FORMAT);
        return String.join(
                OS_FILE_SEPARATOR,
                JOB_ROOT_DIR,
                jobRun.getCreateTime().format(formatter),
                String.format(USER_DIR_FORMAT, jobRun.getUserId()),
                jobRun.getType().name().toLowerCase(),
                String.format(JOB_DIR_FORMAT, jobRun.getJobId()));
    }

    public static String getExecJobDirPath(@Nonnull Long userId, @Nonnull Long jobId, @Nonnull JobType jobType) {
        String jobRootPath = getExecJobRootPath();
        String userDirName = String.format(USER_DIR_FORMAT, userId);
        String jobDirName = String.format(JOB_DIR_FORMAT, jobId);
        return String.join(
                OS_FILE_SEPARATOR, jobRootPath, userDirName, jobType.name().toLowerCase(), jobDirName);
    }

    public static String getExecJobRootPath() {
        return String.join(OS_FILE_SEPARATOR, getLocalWorkRootPath(), JOB_ROOT_DIR);
    }

    public static String getLocalWorkRootPath() {
        if (Paths.get(WORK_DIR).isAbsolute()) {
            return WORK_DIR;
        }

        return String.join(OS_FILE_SEPARATOR, USER_DIR, WORK_DIR);
    }
}
