package com.itiger.persona.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itiger.persona.entity.JobInfo;
import com.itiger.persona.service.IJobInfoService;
import com.itiger.persona.service.mapper.JobInfoMapper;
import org.springframework.stereotype.Service;

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

}
