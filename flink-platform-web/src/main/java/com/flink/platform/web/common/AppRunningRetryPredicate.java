package com.flink.platform.web.common;

import com.flink.platform.web.config.AppRunner;
import org.springframework.resilience.retry.MethodRetryPredicate;

import java.lang.reflect.Method;

@SuppressWarnings("unused")
public class AppRunningRetryPredicate implements MethodRetryPredicate {

    @Override
    public boolean shouldRetry(Method method, Throwable throwable) {
        return AppRunner.isRunning();
    }
}
