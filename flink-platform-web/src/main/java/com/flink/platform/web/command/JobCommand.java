package com.flink.platform.web.command;

/**
 * job command
 *
 * @author tiny.wang
 */
public interface JobCommand {

    /**
     * build a command
     *
     * @return command to execute
     */
    String toCommandString();

}
