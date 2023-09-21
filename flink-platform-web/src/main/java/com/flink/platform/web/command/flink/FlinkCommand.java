package com.flink.platform.web.command.flink;

import com.flink.platform.common.enums.DeployMode;
import com.flink.platform.web.command.JobCommand;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.flink.platform.common.constants.Constant.LINE_SEPARATOR;

/** job command. */
@Setter
@Getter
public class FlinkCommand extends JobCommand {

    private DeployMode mode;

    private String prefix;

    private String optionArgs;

    private final Map<String, Object> configs = new LinkedHashMap<>();

    private String mainArgs;

    private String mainClass;

    private String mainJar;

    private List<URL> classpaths;

    public FlinkCommand(long jobRunId, DeployMode mode) {
        super(jobRunId);
        this.mode = mode;
    }

    @Override
    public String toCommandString() {
        StringBuilder command = new StringBuilder(prefix + LINE_SEPARATOR);
        if (StringUtils.isNotBlank(optionArgs)) {
            command.append(optionArgs).append(LINE_SEPARATOR);
        }
        configs.forEach((k, v) -> command.append(String.format("-D%s=%s" + LINE_SEPARATOR, k, v)));
        classpaths.forEach(classpath -> command.append(String.format("-C %s" + LINE_SEPARATOR, classpath)));
        command.append(String.format("-c %s" + LINE_SEPARATOR, mainClass))
                .append(String.format("%s" + LINE_SEPARATOR, mainJar))
                .append(String.format(" %s ", StringUtils.defaultString(mainArgs, "")));
        return command.toString();
    }
}
