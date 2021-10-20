package com.flink.platform.web.command;

import com.flink.platform.web.config.FlinkConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Flink 1.12 command builder. */
@Slf4j
@Component("flink112CommandBuilder")
public class Flink112CommandBuilder extends FlinkCommandBuilder {

    @Autowired
    public Flink112CommandBuilder(@Qualifier("flink112") FlinkConfig flinkConfig) {
        super(flinkConfig);
    }
}
