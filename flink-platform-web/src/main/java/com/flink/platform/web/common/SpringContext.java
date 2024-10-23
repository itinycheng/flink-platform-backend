package com.flink.platform.web.common;

import jakarta.annotation.Nonnull;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Spring context. */
@Slf4j
@Component
public class SpringContext implements ApplicationContextAware, DisposableBean {

    /** Spring application context. */
    @Getter
    private static ApplicationContext applicationContext;

    /** set application context. */
    @Override
    public void setApplicationContext(@Nonnull ApplicationContext applicationContext) {
        SpringContext.applicationContext = applicationContext;
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

    @Override
    public void destroy() throws Exception {
        log.info("Clean ApplicationContext instance in SpringContext class");
        applicationContext = null;
    }
}
