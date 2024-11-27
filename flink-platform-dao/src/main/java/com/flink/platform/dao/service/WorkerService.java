package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.dao.entity.Worker;
import com.flink.platform.dao.mapper.WorkerMapper;
import org.springframework.stereotype.Service;

import static com.flink.platform.common.constants.Constant.HOST_IP;

/** job config info. */
@Service
@DS("master_platform")
public class WorkerService extends ServiceImpl<WorkerMapper, Worker> {

    public Worker getCurrentWorker() {
        return getOne(new QueryWrapper<Worker>()
                .lambda()
                .select(Worker::getId)
                .eq(Worker::getIp, HOST_IP)
                .last("LIMIT 1"));
    }
}
