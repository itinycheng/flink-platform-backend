package com.flink.platform.web.util;

import com.flink.platform.common.util.OSUtil;
import com.flink.platform.dao.entity.result.ShellCallback;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

import static com.flink.platform.common.constants.Constant.LINE_SEPARATOR;
import static com.flink.platform.common.constants.Constant.SPACE;
import static com.flink.platform.web.util.CollectLogRunnable.CmdOutType.ERR;
import static com.flink.platform.web.util.CollectLogRunnable.CmdOutType.STD;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Command util. <br>
 * TODO: get processId, exitValue and save log to HDFS.
 */
@Slf4j
public class CommandUtil {

    public static final int EXIT_CODE_SUCCESS = 0;

    public static final int EXIT_CODE_FAILURE = 1;

    public static final int EXIT_CODE_KILLED = 137;

    private static final int MAX_LOG_ROWS = 50000;

    public static ShellCallback exec(String command, String[] envProps, long timeoutMills)
            throws IOException, InterruptedException {
        List<String> stdList = Collections.synchronizedList(new ArrayList<>());
        List<String> errList = Collections.synchronizedList(new ArrayList<>());

        ShellCallback callback = exec(command, envProps, timeoutMills, (inputType, value) -> {
            switch (inputType) {
                case STD:
                    if (stdList.size() <= MAX_LOG_ROWS) {
                        stdList.add(value);
                    }
                    break;
                case ERR:
                    if (errList.size() <= MAX_LOG_ROWS) {
                        errList.add(value);
                    }
                    break;
                default:
                    log.error("unknown command log type: {}", inputType);
            }
        });

        callback.setStdMsg(String.join(LINE_SEPARATOR, stdList));
        callback.setErrMsg(String.join(LINE_SEPARATOR, errList));
        return callback;
    }

    public static ShellCallback exec(
            String command,
            String[] envProps,
            long timeoutMills,
            BiConsumer<CollectLogRunnable.CmdOutType, String> logConsumer)
            throws IOException, InterruptedException {
        log.info("Exec command: {}, env properties: {}", command, envProps);
        Process process = Runtime.getRuntime().exec(command, envProps);
        Long processId = getProcessId(process);

        try (InputStream stdStream = process.getInputStream();
                InputStream errStream = process.getErrorStream()) {
            Thread stdThread = Thread.ofVirtual().unstarted(new CollectLogRunnable(stdStream, STD, logConsumer));
            Thread errThread = Thread.ofVirtual().unstarted(new CollectLogRunnable(errStream, ERR, logConsumer));

            try {
                stdThread.start();
                errThread.start();
            } catch (Exception e) {
                log.error("Start log collection thread failed", e);
            }

            boolean status = process.waitFor(timeoutMills, MILLISECONDS);
            int exitValue = status ? process.exitValue() : EXIT_CODE_FAILURE;

            try {
                stdThread.join(2000);
                stdThread.interrupt();
            } catch (Exception e) {
                log.error("interrupt std log collection thread failed", e);
            }

            try {
                errThread.join(2000);
                errThread.interrupt();
            } catch (Exception e) {
                log.error("interrupt err log collection thread failed", e);
            }

            return new ShellCallback(status, exitValue, processId);
        } finally {
            process.destroy();
        }
    }

    public static void forceKill(Long processId) {
        if (processId == null || processId <= 0) {
            log.warn("kill process failed, invalid pid: {}", processId);
            return;
        }

        ProcessHandle.of(processId).ifPresent(CommandUtil::recursiveForceKill);
    }

    private static void recursiveForceKill(ProcessHandle handle) {
        ProcessHandle[] children = handle.children().toArray(ProcessHandle[]::new);

        try {
            handle.destroyForcibly();
        } catch (Throwable t) {
            log.error("Destroy process: {} failed", handle.pid(), t);
        }

        for (ProcessHandle child : children) {
            recursiveForceKill(child);
        }
    }

    /** Get process id. */
    public static Long getProcessId(Process process) {
        try {
            return process.pid();
        } catch (Throwable e) {
            log.error("Get process id failed", e);
            return null;
        }
    }

    public static Long[] getSubprocessIds(Process process) {
        return process.children().map(ProcessHandle::pid).toArray(Long[]::new);
    }

    public static String commandDriver() {
        return OSUtil.isWindows() ? "cmd.exe" : "sh";
    }

    public static String commandType() {
        return OSUtil.isWindows() ? "bat" : "sh";
    }

    public static String getShellCommand(String commandFile) {
        return String.join(SPACE, commandDriver(), commandFile);
    }
}
