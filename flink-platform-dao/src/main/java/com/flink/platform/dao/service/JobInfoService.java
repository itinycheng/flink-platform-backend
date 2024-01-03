package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.mapper.JobInfoMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.flink.platform.dao.entity.JobInfo.LARGE_FIELDS;

/** job config info. */
@Service
@DS("master_platform")
public class JobInfoService extends ServiceImpl<JobInfoMapper, JobInfo> {

    public List<JobInfo> listWithoutLargeFields(Collection<Long> jobIds) {
        if (CollectionUtils.isEmpty(jobIds)) {
            return Collections.emptyList();
        }

        return super.list(new QueryWrapper<JobInfo>()
                .lambda()
                .select(JobInfo.class, field -> !LARGE_FIELDS.contains(field.getProperty()))
                .in(JobInfo::getId, jobIds));
    }
}
