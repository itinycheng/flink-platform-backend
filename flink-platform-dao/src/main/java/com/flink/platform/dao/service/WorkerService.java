package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.dao.entity.Worker;
import com.flink.platform.dao.mapper.WorkerMapper;
import org.springframework.stereotype.Service;

/** job config info. */
@Service
@DS("master_platform")
public class WorkerService extends ServiceImpl<WorkerMapper, Worker> {}
