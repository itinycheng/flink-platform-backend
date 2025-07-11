package com.flink.platform.web.command.shell;

import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.dao.entity.result.ShellCallback;
import com.flink.platform.web.command.AbstractTask;
import com.flink.platform.web.util.CollectLogRunnable;
import com.flink.platform.web.util.CommandUtil;
import com.flink.platform.web.util.SystemUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.Map;
import java.util.function.BiConsumer;

import static com.flink.platform.common.constants.Constant.LINE_SEPARATOR;
import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.KILLABLE;
import static com.flink.platform.common.enums.ExecutionStatus.KILLED;
import static com.flink.platform.common.enums.ExecutionStatus.SUCCESS;
import static com.flink.platform.web.util.CollectLogRunnable.CmdOutType;
import static com.flink.platform.web.util.CollectLogRunnable.CmdOutType.ERR;
import static com.flink.platform.web.util.CollectLogRunnable.CmdOutType.STD;
import static com.flink.platform.web.util.CommandUtil.EXIT_CODE_FAILURE;
import static com.flink.platform.web.util.CommandUtil.EXIT_CODE_KILLED;
import static com.flink.platform.web.util.CommandUtil.EXIT_CODE_SUCCESS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/** Flink yarn task. */
@Slf4j
@Getter
@Setter
public class ShellTask extends AbstractTask {

    protected String command;

    protected Map<String, String> envp;

    protected long timeoutMills;

    protected BiConsumer<CmdOutType, String> logConsumer;

    protected Process process;

    protected Long processId;

    protected Long[] subprocessIds;

    protected boolean exited;

    protected int exitValue;

    protected final StringBuffer stdMsg = new StringBuffer();

    protected final StringBuffer errMsg = new StringBuffer();

    public ShellTask(long id, String command, Map<String, String> envp, long timeoutMills) {
        super(id);
        this.command = command;
        this.envp = envp;
        this.timeoutMills = timeoutMills;
        this.logConsumer = newLogBuffer(null);
    }

    /** Only for kill command process. */
    public ShellTask(long id, Long processId) {
        super(id);
        this.processId = processId;
    }

    @Override
    public void run() throws Exception {
        var merged = SystemUtil.mergeEnv(envp);
        log.info("Exec command: {}, with user-defined env vars: {}", command, envp);
        this.process = Runtime.getRuntime().exec(command, SystemUtil.toEnvArray(merged));
        this.processId = CommandUtil.getProcessId(process);
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

            this.exited = process.waitFor(timeoutMills, MILLISECONDS);
            this.exitValue = exited ? process.exitValue() : EXIT_CODE_FAILURE;
            this.subprocessIds = CommandUtil.getSubprocessIds(process);

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
        } finally {
            process.destroy();
        }
    }

    @Override
    public void cancel() {
        if (processId != null) {
            CommandUtil.forceKill(processId);
        }

        if (subprocessIds != null) {
            for (Long subprocessId : subprocessIds) {
                CommandUtil.forceKill(subprocessId);
            }
        }
    }

    public ShellCallback buildShellCallback() {
        ShellCallback callback = new ShellCallback(exited, exitValue, processId);
        callback.setStdMsg(getStdMsg());
        callback.setErrMsg(getErrMsg());
        return callback;
    }

    public ExecutionStatus finalStatus() {
        if (exited) {
            if (exitValue == EXIT_CODE_SUCCESS) {
                return SUCCESS;
            } else if (exitValue == EXIT_CODE_KILLED) {
                return KILLED;
            }
            return FAILURE;
        }

        return KILLABLE;
    }

    public BiConsumer<CmdOutType, String> newLogBuffer(BiConsumer<CmdOutType, String> consumer) {
        return (type, line) -> {
            // call accept method of subclass.
            if (consumer != null) {
                consumer.accept(type, line);
            }

            // buffer message.
            if (type == STD) {
                stdMsg.append(line);
                stdMsg.append(LINE_SEPARATOR);
            } else if (type == ERR) {
                errMsg.append(line);
                errMsg.append(LINE_SEPARATOR);
            }
        };
    }

    public void setLogConsumer(BiConsumer<CmdOutType, String> logConsumer) {
        this.logConsumer = newLogBuffer(logConsumer);
    }

    public String getStdMsg() {
        return stdMsg.toString();
    }

    public String getErrMsg() {
        return errMsg.toString();
    }
}
