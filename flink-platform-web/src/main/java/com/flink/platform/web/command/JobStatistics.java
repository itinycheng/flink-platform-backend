package com.flink.platform.web.command;

import lombok.Data;
import lombok.NoArgsConstructor;

/** statistic of submitted jobs. */
@Data
@NoArgsConstructor
public class JobStatistics {

    private long startTime;

    private long endTime;

    private long resultSize;
}
