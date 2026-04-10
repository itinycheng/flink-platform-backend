package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.dao.entity.Config;
import com.flink.platform.dao.entity.config.FlinkConfig;
import com.flink.platform.dao.mapper.ConfigMapper;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.flink.platform.common.enums.JobFlowType.FLINK;
import static com.flink.platform.common.enums.Status.ENABLE;

/** Config service. */
@Service
@DS("master_platform")
public class ConfigService extends ServiceImpl<ConfigMapper, Config> {

    public List<Config> getEnabledFlinkConfigs() {
        return this.list(new QueryWrapper<Config>()
                .lambda()
                .eq(Config::getStatus, ENABLE)
                .eq(Config::getType, FLINK)
                .orderByDesc(Config::getId));
    }

    public FlinkConfig findFlinkByVersion(String version) {
        return this.list(new QueryWrapper<Config>()
                        .lambda()
                        .eq(Config::getStatus, ENABLE)
                        .eq(Config::getType, FLINK)
                        .eq(Config::getVersion, version)
                        .orderByDesc(Config::getId))
                .stream()
                .map(Config::getConfig)
                .filter(FlinkConfig.class::isInstance)
                .map(FlinkConfig.class::cast)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Flink config not found for version: " + version));
    }
}
