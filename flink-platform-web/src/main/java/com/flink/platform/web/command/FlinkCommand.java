package com.flink.platform.web.command;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.flink.platform.common.constants.Constant.LINE_SEPARATOR;

/** job command. */
@Data
@NoArgsConstructor
public class FlinkCommand implements JobCommand {

    private String prefix;

    private final Map<String, Object> configs = new LinkedHashMap<>();

    private String mainArgs;

    private String mainClass;

    private String mainJar;

    private List<URL> classpaths;

    @Override
    public String toCommandString() {
        StringBuilder command = new StringBuilder(prefix + LINE_SEPARATOR);
        configs.forEach((k, v) -> command.append(String.format("-D%s=%s" + LINE_SEPARATOR, k, v)));
        classpaths.forEach(
                classpath -> command.append(String.format("-C %s" + LINE_SEPARATOR, classpath)));
        command.append(String.format("-c %s" + LINE_SEPARATOR, mainClass))
                .append(String.format("%s" + LINE_SEPARATOR, mainJar))
                .append(String.format(" %s ", mainArgs));
        return command.toString();
    }
}
