package com.itiger.persona.command;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * statistic of submitted jobs
 *
 * @author tiny.wang
 */
@Data
@NoArgsConstructor
public class JobStatistics {

    private long startTime;

    private long endTime;

    private long resultSize;
}
