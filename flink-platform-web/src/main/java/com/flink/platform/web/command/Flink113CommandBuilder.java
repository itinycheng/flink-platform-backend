package com.flink.platform.web.command;

import com.flink.platform.web.config.FlinkConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * @author tiny.wang
 */
@Component("flink113CommandBuilder")
public class Flink113CommandBuilder extends FlinkCommandBuilder {

    @Autowired
    public Flink113CommandBuilder(@Qualifier("flink113") FlinkConfig flinkConfig) {
        super(flinkConfig);
    }
}
