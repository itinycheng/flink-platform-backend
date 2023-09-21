package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.common.model.JobVertex;
import com.flink.platform.common.util.UuidGenerator;
import com.flink.platform.dao.entity.JobFlow;
import com.flink.platform.dao.entity.JobFlowDag;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.mapper.JobFlowMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.flink.platform.common.enums.JobFlowStatus.OFFLINE;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

/** job config info. */
@Service
@DS("master_platform")
public class JobFlowService extends ServiceImpl<JobFlowMapper, JobFlow> {

    @Autowired
    private JobInfoService jobInfoService;

    @Autowired
    private JobFlowRunService jobFlowRunService;

    @Autowired
    private JobRunInfoService jobRunInfoService;

    @Transactional
    public JobFlow cloneJobFlow(long flowId) {
        // clone jobFlow.
        JobFlow jobFlow = getById(flowId);
        jobFlow.setId(null);
        jobFlow.setName(format("%s-copy_%d", jobFlow.getName(), System.currentTimeMillis()));
        jobFlow.setCode(UuidGenerator.generateShortUuid());
        jobFlow.setStatus(OFFLINE);
        save(jobFlow);

        // clone jobs.
        JobFlowDag flow = jobFlow.getFlow();
        for (JobVertex vertex : flow.getVertices()) {
            JobInfo jobInfo = jobInfoService.getById(vertex.getJobId());
            jobInfo.setId(null);
            jobInfoService.save(jobInfo);
        }

        return jobFlow;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteAllById(long flowId, long userId) {
        List<JobInfo> jobInfoList = jobInfoService.list(new QueryWrapper<JobInfo>()
                .lambda()
                .eq(JobInfo::getFlowId, flowId)
                .eq(JobInfo::getUserId, userId));
        if (CollectionUtils.isNotEmpty(jobInfoList)) {
            List<Long> jobIds = jobInfoList.stream().map(JobInfo::getId).collect(toList());
            jobRunInfoService.remove(new QueryWrapper<JobRunInfo>().lambda().in(JobRunInfo::getJobId, jobIds));
            jobInfoService.remove(new QueryWrapper<JobInfo>().lambda().in(JobInfo::getId, jobIds));
        }
        jobFlowRunService.remove(new QueryWrapper<JobFlowRun>()
                .lambda()
                .eq(JobFlowRun::getFlowId, flowId)
                .eq(JobFlowRun::getUserId, userId));
        remove(new QueryWrapper<JobFlow>().lambda().eq(JobFlow::getId, flowId).eq(JobFlow::getUserId, userId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateFlowById(JobFlow origin) {
        if (origin.getId() == null) {
            return;
        }

        JobFlow newJobFlow = new JobFlow();
        newJobFlow.setId(origin.getId());
        if (origin.getFlow() != null) {
            newJobFlow.setFlow(origin.getFlow());
        } else {
            newJobFlow.setFlow(new JobFlowDag());
        }
        updateById(newJobFlow);
    }

    public JobFlowRun copyToJobFlowRun(JobFlow jobFlow) {
        JobFlowRun jobFlowRun = new JobFlowRun();
        jobFlowRun.setName(jobFlow.getName());
        jobFlowRun.setFlowId(jobFlow.getId());
        jobFlowRun.setUserId(jobFlow.getUserId());
        jobFlowRun.setFlow(jobFlow.getFlow());
        jobFlowRun.setPriority(jobFlow.getPriority());
        jobFlowRun.setTags(jobFlow.getTags());
        jobFlowRun.setAlerts(jobFlow.getAlerts());
        return jobFlowRun;
    }
}
