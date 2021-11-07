package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.mapper.JobRunInfoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** job run info. */
@Service
@DS("master_platform")
public class JobRunInfoService extends ServiceImpl<JobRunInfoMapper, JobRunInfo> {

    @Autowired private JobRunInfoMapper jobRunInfoMapper;

    public JobRunInfo getLatestByJobId(Long jobId) {
        return jobRunInfoMapper.selectLatestByJobId(jobId);
    }
}
