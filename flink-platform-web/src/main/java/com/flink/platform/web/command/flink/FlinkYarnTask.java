package com.flink.platform.web.command.flink;

import com.flink.platform.common.enums.DeployMode;
import com.flink.platform.web.command.shell.ShellTask;
import com.flink.platform.web.common.SpringContext;
import com.flink.platform.web.external.YarnClientService;
import com.flink.platform.web.util.CollectLogThread;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.InputStream;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;

import static com.flink.platform.common.constants.JobConstant.APP_ID_PATTERN;
import static com.flink.platform.common.constants.JobConstant.JOB_ID_PATTERN;
import static com.flink.platform.common.enums.DeployMode.FLINK_YARN_PER;
import static com.flink.platform.web.util.CollectLogThread.CmdOutType;
import static com.flink.platform.web.util.CollectLogThread.CmdOutType.ERR;
import static com.flink.platform.web.util.CollectLogThread.CmdOutType.STD;
import static com.flink.platform.web.util.CommandUtil.EXIT_CODE_FAILURE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/** Flink yarn task. */
@Slf4j
@Getter
@Setter
public class FlinkYarnTask extends ShellTask {

    private YarnClientService yarnClientService;

    private DeployMode mode;

    private String appId;

    private String jobId;

    public FlinkYarnTask(
            long jobRunId, DeployMode mode, String command, String[] envs, long timeoutMills) {
        super(jobRunId, command, envs, timeoutMills);
        this.mode = mode;
        setLogConsumer(this.extractAppIdAndJobId());
    }

    /** Only for kill job. */
    public FlinkYarnTask(long jobRunId, DeployMode mode) {
        super(jobRunId, null, null, 0);
        this.mode = mode;
        this.yarnClientService = SpringContext.getBean(YarnClientService.class);
    }

    public void run() throws Exception {
        log.info("Exec command: {}, env properties: {}", command, envs);
        this.process = Runtime.getRuntime().exec(command, envs);
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

            this.exited = process.waitFor(timeoutMills, MILLISECONDS);
            this.exitValue = exited ? process.exitValue() : EXIT_CODE_FAILURE;

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
        } finally {
            process.destroy();
        }
    }

    @Override
    public void cancel() {
        // kill shell.
        super.cancel();

        // kill application.
        if (StringUtils.isNotEmpty(appId)) {
            if (FLINK_YARN_PER.equals(mode)) {
                try {
                    yarnClientService.killApplication(appId);
                } catch (Exception e) {
                    log.error("Kill yarn application: {} failed", appId, e);
                }
            } else {
                log.warn("Kill command unsupported deployMode: {}, applicationId: {}", mode, appId);
            }
        }
    }

    public BiConsumer<CmdOutType, String> extractAppIdAndJobId() {
        return (cmdOutType, line) -> {
            if (cmdOutType != STD) {
                return;
            }

            if (StringUtils.isEmpty(appId)) {
                String id = extractApplicationId(line);
                if (StringUtils.isNotEmpty(id)) {
                    appId = id;
                }
            }

            if (StringUtils.isEmpty(jobId)) {
                String id = extractJobId(line);
                if (StringUtils.isNotEmpty(id)) {
                    jobId = id;
                }
            }
        };
    }

    public static String extractApplicationId(String message) {
        Matcher matcher = APP_ID_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return null;
        }
    }

    public static String extractJobId(String message) {
        Matcher matcher = JOB_ID_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return null;
        }
    }
}
