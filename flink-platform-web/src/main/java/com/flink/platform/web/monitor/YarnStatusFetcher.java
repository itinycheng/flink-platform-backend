package com.flink.platform.web.monitor;

import com.flink.platform.common.enums.DeployMode;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.dao.entity.result.JobCallback;
import com.flink.platform.grpc.JobStatusReply;
import com.flink.platform.grpc.JobStatusRequest;
import com.flink.platform.web.external.YarnClientService;
import com.flink.platform.web.util.YarnHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.exceptions.ApplicationNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static com.flink.platform.common.enums.ExecutionStatus.NOT_EXIST;

/** status monitor. */
@Slf4j
@Order(1)
@Component
public class YarnStatusFetcher implements StatusFetcher {

    @Lazy
    @Autowired
    private YarnClientService yarnClientService;

    @Override
    public boolean isSupported(DeployMode deployMode) {
        return switch (deployMode) {
            case FLINK_YARN_PER, FLINK_YARN_SESSION, FLINK_YARN_RUN_APPLICATION -> true;
            default -> false;
        };
    }

    @Override
    public JobStatusReply getStatus(JobStatusRequest request) {
        long currentTimeMillis = System.currentTimeMillis();
        JobCallback jobCallback = JsonUtil.toBean(request.getBackInfo(), JobCallback.class);
        if (jobCallback == null || StringUtils.isEmpty(jobCallback.getAppId())) {
            return newJobStatusReply(NOT_EXIST.getCode(), currentTimeMillis, currentTimeMillis);
        }

        String applicationId = jobCallback.getAppId();
        try {
            ApplicationReport applicationReport = yarnClientService.getApplicationReport(jobCallback.getAppId());
            return newJobStatusReply(
                    YarnHelper.getStatus(applicationReport).getCode(),
                    applicationReport.getStartTime(),
                    applicationReport.getFinishTime());
        } catch (ApplicationNotFoundException e) {
            log.warn("Application: {} not found.", applicationId, e);
            return newJobStatusReply(NOT_EXIST.getCode(), currentTimeMillis, currentTimeMillis);
        } catch (Exception e) {
            log.error("Use yarn client to get ApplicationReport failed, application: {}", applicationId, e);
            throw new RuntimeException(e);
        }
    }

    private JobStatusReply newJobStatusReply(int status, long startTime, long endTime) {
        return JobStatusReply.newBuilder()
                .setStatus(status)
                .setStartTime(startTime)
                .setEndTime(endTime)
                .build();
    }
}
