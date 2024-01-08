package com.flink.platform.storage.base;

import jakarta.annotation.Nonnull;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/** Spring context. */
@Component
public class SpringContext2 implements ApplicationContextAware {

    /** Spring application context. */
    private static ApplicationContext applicationContext;

    /** set application context. */
    @Override
    public void setApplicationContext(@Nonnull ApplicationContext applicationContext) {
        SpringContext2.applicationContext = applicationContext;
    }

    /** get bean from applicationContext. */
    public static Object getBean(String name) throws BeansException {
        return applicationContext.getBean(name);
    }

    public static <T> T getBean(Class<T> t) throws BeansException {
        return applicationContext.getBean(t);
    }
}
