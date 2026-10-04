package com.flink.platform.web.quartz;

import com.flink.platform.alert.AlertSendingService;
import com.flink.platform.common.util.DateUtil;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.dao.entity.DependencyConfig;
import com.flink.platform.dao.entity.ExecutionConfig;
import com.flink.platform.dao.entity.JobFlow;
import com.flink.platform.dao.entity.JobFlowDag;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobFlowService;
import com.flink.platform.dao.service.JobInfoService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.common.SpringContext;
import com.flink.platform.web.config.WorkerConfig;
import com.flink.platform.web.service.DependencyService;
import com.flink.platform.web.service.JobFlowRunBizService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;

import static com.flink.platform.common.constants.JobConstant.CONFIG;
import static com.flink.platform.common.enums.JobFlowType.JOB_FLOW;
import static com.flink.platform.web.service.DependencyService.DependencyState.SATISFIED;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobFlowRunnerTest {

    @Mock
    private JobFlowService jobFlowService;

    @Mock
    private JobFlowRunService jobFlowRunService;

    @Mock
    private JobFlowRunBizService flowRunBizService;

    @Mock
    private JobInfoService jobInfoService;

    @Mock
    private JobRunInfoService jobRunService;

    @Mock
    private AlertSendingService alertSendingService;

    @Mock
    private WorkerConfig workerConfig;

    @Mock
    private DependencyService dependencyService;

    private MockedStatic<SpringContext> springContext;

    private JobFlow jobFlow;

    private JobFlowRunner runner;

    @BeforeEach
    void setUp() {
        springContext = mockStatic(SpringContext.class);
        springContext.when(() -> SpringContext.getBean(JobFlowService.class)).thenReturn(jobFlowService);
        springContext.when(() -> SpringContext.getBean(JobFlowRunService.class)).thenReturn(jobFlowRunService);
        springContext
                .when(() -> SpringContext.getBean(JobFlowRunBizService.class))
                .thenReturn(flowRunBizService);
        springContext.when(() -> SpringContext.getBean(JobInfoService.class)).thenReturn(jobInfoService);
        springContext.when(() -> SpringContext.getBean(JobRunInfoService.class)).thenReturn(jobRunService);
        springContext
                .when(() -> SpringContext.getBean(AlertSendingService.class))
                .thenReturn(alertSendingService);
        springContext.when(() -> SpringContext.getBean(WorkerConfig.class)).thenReturn(workerConfig);
        springContext.when(() -> SpringContext.getBean(DependencyService.class)).thenReturn(dependencyService);
        lenient().when(workerConfig.getPerFlowExecThreads()).thenReturn(5);
        lenient().when(workerConfig.getMaxPerFlowExecThreads()).thenReturn(10);

        jobFlow = new JobFlow();
        jobFlow.setId(1L);
        jobFlow.setCode("flow-code");
        jobFlow.setName("flow-name");
        jobFlow.setType(JOB_FLOW);
        jobFlow.setFlow(new JobFlowDag());
        jobFlow.setConfig(new ExecutionConfig());
        when(jobFlowService.getOne(any())).thenReturn(jobFlow);
        when(dependencyService.resolveAndEvaluate(nullable(DependencyConfig.class)))
                .thenReturn(SATISFIED);

        runner = new JobFlowRunner();
    }

    @AfterEach
    void tearDown() {
        springContext.close();
    }

    @Test
    void explicitScheduleTimeWinsAndIsPersistedAtSecondPrecision() {
        var explicitTime = LocalDateTime.of(2026, 10, 5, 10, 0, 0, 123_000_000);
        var config = new ExecutionConfig();
        config.setScheduleTime(explicitTime);

        runner.execute(context(config, Date.from(Instant.parse("2026-10-06T10:00:00Z"))));

        assertSavedScheduleTime(explicitTime.truncatedTo(SECONDS));
    }

    @Test
    void quartzScheduledFireTimeIsUsedWhenNoOverrideExists() {
        var scheduledFireTime = Date.from(Instant.parse("2026-10-06T10:00:00.123Z"));

        runner.execute(context(null, scheduledFireTime));

        assertSavedScheduleTime(DateUtil.toLocalDateTime(scheduledFireTime).truncatedTo(SECONDS));
    }

    @Test
    void maxActiveRunsDefaultsToOne() {
        runner.execute(context(null, new Date()));

        assertEquals(1, captureAdmittedRun().getConfig().getMaxActiveRuns());
    }

    @Test
    void runConfigOverridesFlowConfigAndInheritsUnsetValues() {
        jobFlow.getConfig().setMaxActiveRuns(2);
        jobFlow.getConfig().setMaxJobsPerRun(7);
        var runConfig = new ExecutionConfig();
        runConfig.setMaxActiveRuns(3);

        runner.execute(context(runConfig, new Date()));

        var mergedConfig = captureAdmittedRun().getConfig();
        assertEquals(3, mergedConfig.getMaxActiveRuns());
        assertEquals(7, mergedConfig.getMaxJobsPerRun());
    }

    @Test
    void requestJobConcurrencyIsCappedAtThePlatformLimit() {
        var config = new ExecutionConfig();
        config.setMaxJobsPerRun(100);

        runner.execute(context(config, new Date()));

        assertEquals(10, captureAdmittedRun().getConfig().getMaxJobsPerRun());
    }

    private JobExecutionContext context(ExecutionConfig config, Date scheduledFireTime) {
        var dataMap = new JobDataMap();
        if (config != null) {
            dataMap.put(CONFIG, JsonUtil.toJsonString(config));
        }

        var detail = mock(JobDetail.class);
        when(detail.getKey()).thenReturn(JobKey.jobKey("flow-code"));
        when(detail.getJobDataMap()).thenReturn(dataMap);

        var context = mock(JobExecutionContext.class);
        when(context.getJobDetail()).thenReturn(detail);
        when(context.getScheduledFireTime()).thenReturn(scheduledFireTime);
        return context;
    }

    private void assertSavedScheduleTime(LocalDateTime expected) {
        var saved = captureAdmittedRun();
        assertEquals(expected, saved.getScheduleTime());
        assertEquals(expected, saved.getConfig().getScheduleTime());
    }

    private JobFlowRun captureAdmittedRun() {
        var captor = ArgumentCaptor.forClass(JobFlowRun.class);
        verify(jobFlowRunService).saveOrUpdate(captor.capture());
        return captor.getValue();
    }
}
