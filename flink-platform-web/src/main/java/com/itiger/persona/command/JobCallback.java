package com.itiger.persona.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * call back info from the command line
 *
 * @author tiny.wang
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobCallback {

    private String jobId;

    private String appId;

    private String message;
}
