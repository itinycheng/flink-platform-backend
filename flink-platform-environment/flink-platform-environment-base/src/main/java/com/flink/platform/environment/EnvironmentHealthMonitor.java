package com.flink.platform.environment;

import com.flink.platform.common.util.ExceptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class EnvironmentHealthMonitor {

    private final EnvironmentRegistry registry;

    @Scheduled(fixedDelay = 600_000)
    public void check() {
        ExceptionUtil.runWithErrorLogging("Environment health check tick failed", registry::checkHealth);
    }
}
