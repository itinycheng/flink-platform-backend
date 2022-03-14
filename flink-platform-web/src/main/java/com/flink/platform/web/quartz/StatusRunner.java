package com.flink.platform.web.quartz;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.common.SpringContext;
import com.flink.platform.web.entity.response.ResultInfo;
import com.flink.platform.web.util.HttpUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

/** Status runner. */
@Slf4j
public class StatusRunner implements Job {

    private static final String REST_UPDATE_STATUS = "/internal/updateStatus";

    private static final List<ExecutionStatus> NON_TERMINAL_STATUS_LIST =
            ExecutionStatus.getNonTerminals();

    private final JobRunInfoService jobRunInfoService =
            SpringContext.getBean(JobRunInfoService.class);

    private final RestTemplate restTemplate = SpringContext.getBean(RestTemplate.class);

    @Override
    public void execute(JobExecutionContext context) {
        List<JobRunInfo> jobRunList =
                jobRunInfoService.list(
                        new QueryWrapper<JobRunInfo>()
                                .lambda()
                                .in(JobRunInfo::getStatus, NON_TERMINAL_STATUS_LIST));

        Map<String, List<JobRunInfo>> groupedJobRunList =
                jobRunList.stream()
                        .collect(
                                groupingBy(
                                        jobRunInfo ->
                                                StringUtils.defaultString(
                                                        jobRunInfo.getRouteUrl())));

        for (Entry<String, List<JobRunInfo>> entry : groupedJobRunList.entrySet()) {
            String routeUrl = HttpUtil.getUrlOrDefault(entry.getKey());
            List<Long> ids = entry.getValue().stream().map(JobRunInfo::getId).collect(toList());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<List<Long>> requestEntity = new HttpEntity<>(ids, headers);
            ResultInfo<Object> response =
                    restTemplate
                            .exchange(
                                    routeUrl + REST_UPDATE_STATUS,
                                    HttpMethod.POST,
                                    requestEntity,
                                    new ParameterizedTypeReference<ResultInfo<Object>>() {})
                            .getBody();

            log.info("The job run id in : {} are processed, result: {}", ids, response);
        }
    }
}
