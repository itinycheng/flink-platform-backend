package com.flink.platform.web.command.flink;

import com.flink.platform.common.enums.DeployMode;
import com.flink.platform.web.command.shell.ShellTask;
import com.flink.platform.web.common.SpringContext;
import com.flink.platform.web.external.LocalHadoopService;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;

import static com.flink.platform.common.constants.JobConstant.APP_ID_PATTERN;
import static com.flink.platform.common.constants.JobConstant.JOB_ID_PATTERN;
import static com.flink.platform.common.enums.DeployMode.FLINK_YARN_PER;
import static com.flink.platform.web.util.CollectLogRunnable.CmdOutType;
import static com.flink.platform.web.util.CollectLogRunnable.CmdOutType.STD;

/** Flink yarn task. */
@Slf4j
@Getter
@Setter
public class FlinkYarnTask extends ShellTask {

    private LocalHadoopService localHadoopService;

    private DeployMode mode;

    private String appId;

    private String jobId;

    public FlinkYarnTask(long jobRunId, DeployMode mode, String command, Map<String, String> envp, long timeoutMills) {
        super(jobRunId, command, envp, timeoutMills);
        this.mode = mode;
        setLogConsumer(this.extractAppIdAndJobId());
    }

    /** Only for kill job. */
    public FlinkYarnTask(long jobRunId, DeployMode mode) {
        super(jobRunId, null, null, 0);
        this.mode = mode;
        this.localHadoopService = SpringContext.getBean(LocalHadoopService.class);
    }

    public void run() throws Exception {
        super.run();
    }

    @Override
    public void cancel() {
        // kill shell.
        super.cancel();

        // kill application.
        if (StringUtils.isNotEmpty(appId)) {
            if (FLINK_YARN_PER.equals(mode)) {
                try {
                    localHadoopService.killApplication(appId);
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
