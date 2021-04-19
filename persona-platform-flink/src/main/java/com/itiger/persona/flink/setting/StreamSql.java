package com.itiger.persona.flink.setting;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * setting config
 * stream-sql node
 *
 * @author tiny.wang
 */
@Data
@NoArgsConstructor
public class StreamSql {

    private int parallelism;

    private int maxParallelism;

    private boolean objectReuse;

    /**
     * unit: ms
     */
    private int watermarkInterval;

    private String restartStrategy;

    private String stateBackend;

    private String stateCheckpointDir;

    private Checkpointing checkpointing;

}
