package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.common.enums.ResponseStatus;
import com.flink.platform.common.exception.DefinitionException;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.mapper.JobInfoMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** job config info. */
@Service
@DS("master_platform")
public class JobInfoService extends ServiceImpl<JobInfoMapper, JobInfo> {

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
}
