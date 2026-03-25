package com.flink.platform.web.command.flink;

import com.flink.platform.common.annotation.VersionRange;
import org.springframework.stereotype.Component;

/** Flink 1.17 command builder. */
@VersionRange(minVersion = "1.12")
@Component("flinkV2CommandBuilder")
public class FlinkV2CommandBuilder extends FlinkCommandBuilder {}
