package com.flink.platform.environment.hadoop;

import com.flink.platform.common.environment.EnvironmentSpec;
import com.flink.platform.common.environment.EnvironmentType;
import com.flink.platform.environment.EnvironmentClientFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.springframework.stereotype.Component;

/** Builds {@link YarnClient}s for YARN-typed environments. */
@Slf4j
@Component
public class YarnClientFactory implements EnvironmentClientFactory<YarnClient> {

    @Override
    public EnvironmentType supportedType() {
        return EnvironmentType.YARN;
    }

    @Override
    public YarnClient create(EnvironmentSpec spec) {
        var conf = HadoopConfDiscovery.getHadoopConfiguration();
        var client = YarnClient.createYarnClient();
        client.init(new YarnConfiguration(conf));
        client.start();
        log.info("YarnClient started for env spec: {}", spec);
        return client;
    }

    @Override
    public void close(YarnClient client) {
        client.stop();
    }

    @Override
    public boolean healthy(YarnClient client) {
        try {
            client.getYarnClusterMetrics();
            return true;
        } catch (Exception e) {
            log.warn("YarnClient health probe failed", e);
            return false;
        }
    }
}
