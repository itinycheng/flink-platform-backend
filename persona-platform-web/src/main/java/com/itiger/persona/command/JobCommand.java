package com.itiger.persona.command;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.itiger.persona.common.constants.JobConstant.LINE_SEPARATOR;

/**
 * job command
 *
 * @author tiny.wang
 */
@Data
@NoArgsConstructor
public class JobCommand {

    private String prefix;

    private final Map<String, Object> configs = new LinkedHashMap<>();

    private String mainArgs;

    private String mainClass;

    private String mainJar;

    private List<String> classpaths;

    public String toCommandString() {
        StringBuilder command = new StringBuilder(prefix + LINE_SEPARATOR);
        configs.forEach((k, v) -> command.append(String.format("-D%s=%s" + LINE_SEPARATOR, k, v)));
        classpaths.forEach(classpath -> command.append(String.format("-C %s" + LINE_SEPARATOR, classpath)));
        command.append(String.format("-c %s" + LINE_SEPARATOR, mainClass))
                .append(String.format("%s" + LINE_SEPARATOR, mainJar))
                .append(String.format(" %s ", mainArgs));
        return command.toString();
    }

}
