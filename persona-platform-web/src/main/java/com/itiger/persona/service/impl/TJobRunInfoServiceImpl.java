package com.itiger.persona.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itiger.persona.entity.TJobRunInfo;
import com.itiger.persona.service.ITJobRunInfoService;
import com.itiger.persona.service.mapper.TJobRunInfoMapper;
import org.springframework.stereotype.Service;

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
public class TJobRunInfoServiceImpl extends ServiceImpl<TJobRunInfoMapper, TJobRunInfo> implements ITJobRunInfoService {

}
