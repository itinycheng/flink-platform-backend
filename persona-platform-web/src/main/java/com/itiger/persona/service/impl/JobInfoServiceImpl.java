package com.itiger.persona.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itiger.persona.entity.JobInfo;
import com.itiger.persona.enums.ResponseStatus;
import com.itiger.persona.exception.DefinitionException;
import com.itiger.persona.service.IJobInfoService;
import com.itiger.persona.service.mapper.JobInfoMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(rollbackFor = DefinitionException.class)
    @Override
    public boolean save(JobInfo jobInfo) {
        boolean save = super.save(jobInfo);
        if (save) {
            // TODO 生成job detail
        }
        throw new DefinitionException(ResponseStatus.SERVICE_ERROR);
    }

    @Transactional(rollbackFor = DefinitionException.class)
    @Override
    public boolean updateById(JobInfo jobInfo) {
        boolean update = super.updateById(jobInfo);
        if (update) {
            // TODO 修改job detail
        }
        throw new DefinitionException(ResponseStatus.SERVICE_ERROR);
    }
}
