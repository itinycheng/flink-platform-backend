package com.flink.platform.web.quartz;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.util.DateUtil;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.common.util.NumberUtil;
import com.flink.platform.dao.entity.ExecutionConfig;
import com.flink.platform.dao.entity.JobFlow;
import com.flink.platform.dao.service.JobFlowService;
import com.flink.platform.web.common.SpringContext;
import com.flink.platform.web.service.FlowRunTriggerService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.quartz.Job;
import org.quartz.JobExecutionContext;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.flink.platform.common.constants.JobConstant.CONFIG;
import static com.flink.platform.common.constants.JobConstant.SCHEDULE_TIME;
import static com.flink.platform.common.constants.JobConstant.USER_ID;
import static com.flink.platform.common.enums.JobFlowStatus.ONLINE;
import static com.flink.platform.common.enums.JobFlowStatus.SCHEDULING;
import static java.time.temporal.ChronoUnit.SECONDS;

/** submit job flow. */
@Slf4j
public class JobFlowRunner implements Job {

    private static final Map<String, Object> PARALLEL_LOCK_MAP = new ConcurrentHashMap<>();

    private final JobFlowService jobFlowService = SpringContext.getBean(JobFlowService.class);

    private final FlowRunTriggerService triggerService = SpringContext.getBean(FlowRunTriggerService.class);

    // TODO: handle exceptions ?
    @Override
    public void execute(JobExecutionContext context) {
        var detail = context.getJobDetail();
        var key = detail.getKey();
        var code = key.getName();
        var dataMap = detail.getJobDataMap();

        synchronized (getProcessLock(code)) {
            // Get job flow info.
            var jobFlow = jobFlowService.getOne(new QueryWrapper<JobFlow>()
                    .lambda()
                    .eq(JobFlow::getCode, code)
                    .in(JobFlow::getStatus, ONLINE, SCHEDULING));
            if (jobFlow == null) {
                log.warn("The job flow: {} isn't exists or not in scheduling status", code);
                return;
            }

            var userId = NumberUtil.toLong(dataMap.get(USER_ID));
            var config = JsonUtil.toBean(dataMap.getString(CONFIG), ExecutionConfig.class);
            var scheduleTime = ObjectUtils.firstNonNull(
                            DateUtil.parseNullable(dataMap.getString(SCHEDULE_TIME)),
                            DateUtil.toLocalDateTime(context.getScheduledFireTime()),
                            LocalDateTime.now())
                    .truncatedTo(SECONDS);
            triggerService.handle(jobFlow, config, userId, scheduleTime);
        }
    }

    private Object getProcessLock(String code) {
        var newLock = new Object();
        var lock = PARALLEL_LOCK_MAP.putIfAbsent(code, newLock);
        if (lock == null) {
            lock = newLock;
        }
        return lock;
    }
}
