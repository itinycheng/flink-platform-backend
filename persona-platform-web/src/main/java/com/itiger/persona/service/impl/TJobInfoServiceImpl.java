package com.itiger.persona.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itiger.persona.entity.TJobInfo;
import com.itiger.persona.service.ITJobInfoService;
import com.itiger.persona.service.mapper.TJobInfoMapper;
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
public class TJobInfoServiceImpl extends ServiceImpl<TJobInfoMapper, TJobInfo> implements ITJobInfoService {

}
