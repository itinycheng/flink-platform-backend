package com.itiger.persona.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itiger.persona.entity.JobRunInfo;
import com.itiger.persona.mapper.JobRunInfoMapper;
import com.itiger.persona.service.IJobRunInfoService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * <p>
 * job run info 服务实现类
 * </p>
 *
 * @author shik
 * @since 2021-04-14
 */
@Service
@DS("master_platform")
public class JobRunInfoServiceImpl extends ServiceImpl<JobRunInfoMapper, JobRunInfo> implements IJobRunInfoService {

    @Resource
    private JobRunInfoMapper jobRunInfoMapper;

    @Override
    public JobRunInfo getLatestByJobId(Long jobId) {
        return jobRunInfoMapper.selectLatestByJobId(jobId);
    }

}
