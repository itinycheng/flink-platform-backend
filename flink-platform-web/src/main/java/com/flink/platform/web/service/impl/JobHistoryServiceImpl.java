package com.flink.platform.web.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.web.entity.JobHistory;
import com.flink.platform.web.service.IJobHistoryService;
import com.flink.platform.web.mapper.JobHistoryMapper;
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
public class JobHistoryServiceImpl extends ServiceImpl<JobHistoryMapper, JobHistory> implements IJobHistoryService {

}
