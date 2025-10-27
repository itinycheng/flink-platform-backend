package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.dao.entity.Worker;
import com.flink.platform.dao.mapper.WorkerMapper;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.flink.platform.common.constants.Constant.HOST_IP;
import static com.flink.platform.common.enums.WorkerStatus.FOLLOWER;
import static com.flink.platform.common.enums.WorkerStatus.LEADER;

/** job config info. */
@Service
@DS("master_platform")
public class WorkerService extends ServiceImpl<WorkerMapper, Worker> {

    public Worker getCurWorkerIdAndRole() {
        return getOne(new QueryWrapper<Worker>()
                .lambda()
                .select(Worker::getId, Worker::getRole)
                .eq(Worker::getIp, HOST_IP)
                .last("LIMIT 1"));
    }

    public List<Worker> listActiveWorkersByIds(List<Long> ids) {
        List<Worker> workers =
                list(new QueryWrapper<Worker>().lambda().in(Worker::getId, ids).in(Worker::getRole, LEADER, FOLLOWER));
        return workers.stream().filter(Worker::isActive).toList();
    }
}
