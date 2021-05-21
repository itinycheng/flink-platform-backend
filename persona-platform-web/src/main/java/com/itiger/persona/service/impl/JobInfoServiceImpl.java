package com.itiger.persona.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itiger.persona.entity.JobInfo;
import com.itiger.persona.common.enums.ResponseStatus;
import com.itiger.persona.common.exception.DefinitionException;
import com.itiger.persona.service.IJobInfoService;
import com.itiger.persona.mapper.JobInfoMapper;
import com.itiger.persona.service.JobInfoQuartzService;
import org.quartz.impl.QuartzServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

/**
 * <p>
 * job config info 服务实现类
 * </p>
 *
 * @author shik
 * @since 2021-04-14
 */
@Service
@DS("master_platform")
public class JobInfoServiceImpl extends ServiceImpl<JobInfoMapper, JobInfo> implements IJobInfoService {

    @Resource
    public JobInfoQuartzService jobInfoQuartzService;

    @Transactional(rollbackFor = RuntimeException.class)
    @Override
    public boolean save(JobInfo jobInfo) {
        boolean save = super.save(jobInfo);
        if (!save) {
            throw new DefinitionException(ResponseStatus.SERVICE_ERROR);
        }
        return true;
//        return jobInfoQuartzService.addJobToQuartz(jobInfo);
    }

    @Transactional(rollbackFor = RuntimeException.class)
    @Override
    public boolean updateById(JobInfo jobInfo) {
        boolean update = super.updateById(jobInfo);
        if (!update) {
            throw new DefinitionException(ResponseStatus.SERVICE_ERROR);
        }
        // TODO 修改job detail
        return true;
    }

    @Override
    public boolean stopJob(JobInfo jobInfo) {
        return false;
    }
}
