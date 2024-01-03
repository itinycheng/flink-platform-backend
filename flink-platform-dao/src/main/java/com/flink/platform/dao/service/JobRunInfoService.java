package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.mapper.JobRunInfoMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/** job run info. */
@Service
@DS("master_platform")
public class JobRunInfoService extends ServiceImpl<JobRunInfoMapper, JobRunInfo> {

    public List<JobRunInfo> listLastWithoutLargeFields(Long flowRunId, List<Long> jobIds) {
        return this.baseMapper.lastJobRunList(flowRunId, jobIds);
    }
}
