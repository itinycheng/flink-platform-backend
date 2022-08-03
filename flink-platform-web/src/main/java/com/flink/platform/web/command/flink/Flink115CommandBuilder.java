package com.flink.platform.web.command.flink;

import com.flink.platform.web.config.FlinkConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Flink 1.15 command builder. */
@Component("flink115CommandBuilder")
public class Flink115CommandBuilder extends FlinkCommandBuilder {

    @Autowired
    public Flink115CommandBuilder(@Qualifier("flink115") FlinkConfig flinkConfig) {
        super(flinkConfig);
    }
}
