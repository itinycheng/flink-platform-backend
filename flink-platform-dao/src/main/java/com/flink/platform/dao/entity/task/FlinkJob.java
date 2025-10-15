package com.flink.platform.dao.entity.task;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/** flink jar or sql job. */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class FlinkJob extends BaseJob {

    /** option arguments. such as: -ynm jobName */
    private String optionArgs;

    /** configs for run job. */
    private Map<String, String> configs;

    /** catalogs. */
    private List<Long> catalogs;

    /** external jars, such as udf jar. */
    private List<Long> extJars;

    /** main args. */
    private String mainArgs;

    /** main class. */
    private String mainClass;

    private transient List<String> extJarPaths;
}
