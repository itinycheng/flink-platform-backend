package com.flink.platform.web.flowrun;

import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobFlowService;
import com.flink.platform.dao.service.JobInfoService;
import com.flink.platform.dao.service.JobRunInfoService;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** Package-private visibility is the compile-time guard, so the transactional proxy must still apply. */
class PackagePrivateTransactionalBeanTest {

    @Test
    void proxiesPackagePrivateTransactionalBeans() {
        var ctx = new AnnotationConfigApplicationContext();
        var factory = ctx.getBeanFactory();
        factory.registerSingleton("jobFlowService", mock(JobFlowService.class));
        factory.registerSingleton("jobFlowRunService", mock(JobFlowRunService.class));
        factory.registerSingleton("jobInfoService", mock(JobInfoService.class));
        factory.registerSingleton("jobRunInfoService", mock(JobRunInfoService.class));
        factory.registerSingleton("flowRunBizService", mock(FlowRunBizService.class));
        factory.registerSingleton("flowRunStateService", mock(FlowRunStateService.class));
        ctx.register(ProbeConfig.class);
        ctx.register(LegacyJobListService.class);
        ctx.register(FlowRunCreationService.TransactionService.class);
        ctx.refresh();

        assertTrue(AopUtils.isAopProxy(ctx.getBean(LegacyJobListService.class)));
        assertTrue(AopUtils.isAopProxy(ctx.getBean(FlowRunCreationService.TransactionService.class)));
        ctx.close();
    }

    @Test
    void componentScanningFindsPackagePrivateBeans() {
        var scanner = new ClassPathScanningCandidateComponentProvider(true);
        var beanClasses = scanner.findCandidateComponents(FlowRunCreationService.class.getPackageName()).stream()
                .map(BeanDefinition::getBeanClassName)
                .toList();

        assertTrue(beanClasses.contains(LegacyJobListService.class.getName()), beanClasses.toString());
        assertTrue(
                beanClasses.contains(FlowRunCreationService.TransactionService.class.getName()),
                beanClasses.toString());
    }

    @Configuration
    @EnableTransactionManagement
    static class ProbeConfig {

        @Bean
        PlatformTransactionManager transactionManager() {
            return mock(PlatformTransactionManager.class);
        }
    }
}
