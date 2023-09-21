package com.flink.platform.web.service;

import com.flink.platform.common.constants.Constant;
import com.flink.platform.dao.entity.Worker;
import com.flink.platform.dao.service.WorkerService;
import com.flink.platform.web.util.HttpUtil;
import jakarta.annotation.Nullable;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

/** Worker apply service. */
@Service
public class WorkerApplyService {

    @Autowired
    private WorkerService workerService;

    public String chooseWorker(List<Long> workerIds) {
        if (CollectionUtils.isEmpty(workerIds)) {
            return HttpUtil.getDefaultUrl();
        }

        List<Worker> workers = workerService.listByIds(workerIds);

        if (CollectionUtils.isEmpty(workers)) {
            return HttpUtil.getDefaultUrl();
        }

        String hostIp = Constant.HOST_IP;
        if (workers.stream().anyMatch(worker -> hostIp.equals(worker.getIp()))) {
            return HttpUtil.getDefaultUrl();
        }

        int idx = new Random().nextInt(workers.size());
        Worker worker = workers.get(idx);
        return HttpUtil.buildHttpUrl(worker.getIp(), worker.getPort());
    }

    public @Nullable Worker randomWorker(List<Long> workerIds) {
        if (CollectionUtils.isEmpty(workerIds)) {
            return null;
        }

        List<Worker> workers = workerService.listByIds(workerIds);
        if (CollectionUtils.isEmpty(workers)) {
            return null;
        }

        int idx = new Random().nextInt(workers.size());
        return workers.get(idx);
    }
}
