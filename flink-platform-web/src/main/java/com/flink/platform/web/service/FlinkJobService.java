package com.flink.platform.web.service;

import com.flink.platform.common.enums.DeployMode;
import com.flink.platform.common.exception.DefinitionException;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.config.FlinkConfig;
import com.flink.platform.web.util.CommandUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.text.StringSubstitutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.flink.platform.common.enums.ResponseStatus.OPERATION_NOT_ALLOWED;

/**
 * flink job service.
 */
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class FlinkJobService {

    private static final Map<DeployMode, String> SAVEPOINT_FORMATS;

    private final List<FlinkConfig> flinkConfigs;

    private final JobRunInfoService jobRunService;

    static {
        var formatMap = new HashMap<DeployMode, String>();
        var savepointFormat = "${cmdPath} savepoint -yid ${appId} ${jobId}";
        formatMap.put(DeployMode.FLINK_YARN_PER, savepointFormat);
        formatMap.put(DeployMode.FLINK_YARN_SESSION, savepointFormat);
        formatMap.put(DeployMode.FLINK_YARN_RUN_APPLICATION, savepointFormat);
        SAVEPOINT_FORMATS = formatMap;
    }

    public void savepoint(Long jobRunId) {
        var jobRun = jobRunService.getById(jobRunId);
        var flinkConfig = flinkConfigs.stream()
                .filter(config -> config.getVersion().equals(jobRun.getVersion()))
                .findAny()
                .orElseThrow(() -> new DefinitionException(OPERATION_NOT_ALLOWED));

        var backInfo = jobRun.getBackInfo();
        var argsMap = new HashMap<String, String>();
        argsMap.put("cmdPath", flinkConfig.getCommandPath());
        argsMap.put("appId", backInfo.getAppId());
        argsMap.put("jobId", backInfo.getJobId());

        try {
            var substitutor = new StringSubstitutor(argsMap);
            var command = substitutor.replace(SAVEPOINT_FORMATS.get(jobRun.getDeployMode()));
            CommandUtil.exec(command);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
