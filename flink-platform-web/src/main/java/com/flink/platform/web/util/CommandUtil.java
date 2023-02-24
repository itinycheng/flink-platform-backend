package com.flink.platform.web.util;

import com.flink.platform.common.constants.Constant;
import com.flink.platform.common.util.OSUtil;
import com.sun.jna.platform.win32.Kernel32;
import lombok.extern.slf4j.Slf4j;
import oshi.jna.platform.windows.WinNT;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

import static com.flink.platform.common.constants.Constant.LINE_SEPARATOR;
import static com.flink.platform.common.constants.Constant.SPACE;
import static com.flink.platform.web.util.CollectLogThread.CmdOutType.ERR;
import static com.flink.platform.web.util.CollectLogThread.CmdOutType.STD;
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

        ShellCallback callback =
                exec(
                        command,
                        envProps,
                        timeoutMills,
                        (inputType, value) -> {
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
            BiConsumer<CollectLogThread.CmdOutType, String> logConsumer)
            throws IOException, InterruptedException {
        log.info("Exec command: {}, env properties: {}", command, envProps);
        Process process = Runtime.getRuntime().exec(command, envProps);
        Integer processId = getProcessId(process);

        try (InputStream stdStream = process.getInputStream();
                InputStream errStream = process.getErrorStream()) {
            CollectLogThread stdThread = new CollectLogThread(stdStream, STD, logConsumer);
            CollectLogThread errThread = new CollectLogThread(errStream, ERR, logConsumer);

            try {
                stdThread.start();
                errThread.start();
            } catch (Exception e) {
                log.error("Start log collection thread failed", e);
            }

            boolean status = process.waitFor(timeoutMills, MILLISECONDS);
            int exitValue = status ? process.exitValue() : EXIT_CODE_FAILURE;

            try {
                stdThread.interrupt();
                stdThread.join();
            } catch (Exception e) {
                log.error("interrupt std log collection thread failed", e);
            }

            try {
                errThread.interrupt();
                errThread.join();
            } catch (Exception e) {
                log.error("interrupt err log collection thread failed", e);
            }

            return new ShellCallback(status, exitValue, processId);
        } finally {
            process.destroy();
        }
    }

    public static void forceKill(Integer processId, String[] envProps) {
        if (processId == null || processId <= 0) {
            log.warn("kill process failed, invalid pid: {}", processId);
            return;
        }

        Process process = null;
        try {
            String command = String.format("kill -9 %d", processId);
            process = Runtime.getRuntime().exec(command, envProps);
            process.waitFor();
        } catch (Exception e) {
            log.error("force kill process {} failed, envs: {}", processId, envProps);
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    /** Get process id. */
    public static Integer getProcessId(Process process) {
        try {
            Field f = process.getClass().getDeclaredField(Constant.PID);
            f.setAccessible(true);

            int processId;
            if (OSUtil.isWindows()) {
                WinNT.HANDLE handle = (WinNT.HANDLE) f.get(process);
                processId = Kernel32.INSTANCE.GetProcessId(handle);
            } else {
                processId = f.getInt(process);
            }

            return processId;
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
            return null;
        }
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
