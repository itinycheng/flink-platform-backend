package com.itiger.persona.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itiger.persona.entity.TJobHistory;
import com.itiger.persona.service.ITJobHistoryService;
import com.itiger.persona.service.mapper.TJobHistoryMapper;
import org.springframework.stereotype.Service;

/**
 * <p>
 * job modify info 服务实现类
 * </p>
 *
 * @author shik
 * @since 2021-04-14
 */
@Service
@DS("master_platform")
public class TJobHistoryServiceImpl extends ServiceImpl<TJobHistoryMapper, TJobHistory> implements ITJobHistoryService {

}
