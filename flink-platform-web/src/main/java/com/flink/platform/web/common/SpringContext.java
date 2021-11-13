package com.flink.platform.web.common;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Spring context. */
@Component
public class SpringContext implements ApplicationContextAware {

    /** Spring application context. */
    private static ApplicationContext applicationContext;

    /** set application context. */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        SpringContext.applicationContext = applicationContext;
    }

    /** return ApplicationContext. */
    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    /** get bean from applicationContext. */
    public static Object getBean(String name) throws BeansException {
        return applicationContext.getBean(name);
    }

    public static <T> T getBean(Class<T> t) throws BeansException {
        return applicationContext.getBean(t);
    }

    public static <T> T getBean(String name, Class<T> requiredType) {
        return applicationContext.getBean(name, requiredType);
    }

    public static <T> List<T> getBeansOfType(Class<T> t) {
        return new ArrayList<>(applicationContext.getBeansOfType(t).values());
    }

    public static String getEnv() {
        return applicationContext.getEnvironment().getActiveProfiles()[0];
    }

    public static String getServerPort() {
        return applicationContext.getEnvironment().getProperty("server.port");
    }
}
