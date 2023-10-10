package com.flink.platform.web.command.flink;

import com.flink.platform.web.config.FlinkConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Flink 1.17 command builder. */
@Component("flink117CommandBuilder")
public class Flink117CommandBuilder extends FlinkCommandBuilder {

    @Autowired
    public Flink117CommandBuilder(@Qualifier("flink117") FlinkConfig flinkConfig) {
        super(flinkConfig);
    }
}
