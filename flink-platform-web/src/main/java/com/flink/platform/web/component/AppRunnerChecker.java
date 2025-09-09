package com.flink.platform.web.component;

import com.flink.platform.web.config.AppRunner;
import org.springframework.stereotype.Component;

@SuppressWarnings("unused")
@Component("appRunnerChecker")
public class AppRunnerChecker {

    public boolean shouldRetry(Throwable exception) {
        return AppRunner.isRunning();
    }
}
