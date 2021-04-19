package com.itiger.persona.flink.setting;

import lombok.Data;

/**
 * setting config
 * checkpointing node
 *
 * @author tiny.wang
 */
@Data
public class Checkpointing {

    private String mode;

    private int interval;

    private int timeout;

    private int minPause;

    private int maxConcurrent;

    private int tolerableFailed;

}
