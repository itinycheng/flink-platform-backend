package com.itiger.persona.flink.helper;

import com.itiger.persona.common.job.SqlContext;
import com.itiger.persona.flink.setting.Checkpointing;
import com.itiger.persona.flink.setting.Setting;
import com.itiger.persona.flink.setting.Settings;
import com.itiger.persona.flink.setting.StreamSql;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.api.bridge.java.internal.StreamTableEnvironmentImpl;

/**
 * set checkpoint
 *
 * @author tiny.wang
 */
public class Checkpoints {
    public static void setCheckpointConfigs(TableEnvironment tEnv, SqlContext sqlContext) {
        switch (sqlContext.getExecMode()) {
            case STREAMING:
                StreamTableEnvironmentImpl streamTableEnv = (StreamTableEnvironmentImpl) tEnv;
                StreamExecutionEnvironment streamEnv = streamTableEnv.execEnv();
                Setting setting = Settings.mergeWithSetting(sqlContext.getConfigs());
                StreamSql streamSqlConfigs = setting.getFlink().getStreamSql();
                addConfigsToStreamEnv(streamEnv, streamSqlConfigs);
            case BATCH:
            default:
                break;
        }
    }

    /**
     * TODO config unfinished
     * maybe no use
     */
    private static void addConfigsToStreamEnv(StreamExecutionEnvironment streamEnv, StreamSql config) {
        streamEnv.setParallelism(config.getParallelism());
        streamEnv.setMaxParallelism(config.getMaxParallelism());
        if (config.isObjectReuse()) {
            streamEnv.getConfig().enableObjectReuse();
        }
        if (config.getWatermarkInterval() > 0) {
            streamEnv.getConfig().setAutoWatermarkInterval(config.getWatermarkInterval());
        }

        CheckpointConfig checkpointConfig = streamEnv.getCheckpointConfig();
        Checkpointing checkpointing = config.getCheckpointing();
        checkpointConfig.setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);
    }

}
