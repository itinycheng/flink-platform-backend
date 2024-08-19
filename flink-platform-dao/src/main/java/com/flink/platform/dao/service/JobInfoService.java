package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.mapper.JobInfoMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.flink.platform.dao.entity.JobInfo.LARGE_FIELDS;

/** job config info. */
@Service
@DS("master_platform")
public class JobInfoService extends ServiceImpl<JobInfoMapper, JobInfo> {

    @Autowired private JobRunInfoService jobRunService;

    public List<JobInfo> listWithoutLargeFields(Collection<Long> jobIds) {
        if (CollectionUtils.isEmpty(jobIds)) {
            return Collections.emptyList();
        }

        return super.list(
                new QueryWrapper<JobInfo>()
                        .lambda()
                        .select(JobInfo.class, field -> !LARGE_FIELDS.contains(field.getProperty()))
                        .in(JobInfo::getId, jobIds));
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteAllById(long jobId) {
        // TODO: Remove jobFlowRun with empty jobRun list?
        jobRunService.remove(
                new QueryWrapper<JobRunInfo>().lambda().in(JobRunInfo::getJobId, jobId));
        remove(new QueryWrapper<JobInfo>().lambda().in(JobInfo::getId, jobId));
    }
}
