package com.flink.platform.web.service;

import com.flink.platform.common.constants.Constant;
import com.flink.platform.dao.entity.Worker;
import com.flink.platform.dao.service.WorkerService;
import com.flink.platform.web.util.HttpUtil;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

/** Worker apply service. */
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class WorkerApplyService {

    private final WorkerService workerService;

    private final Random random = new Random();

    @Deprecated(since = "Use chooseWorker(List<Long> workerIds) instead. ")
    public String chooseWorker(List<Long> workerIds) {
        if (CollectionUtils.isEmpty(workerIds)) {
            return HttpUtil.getDefaultUrl();
        }

        var workers = workerService.listByIds(workerIds);

        if (CollectionUtils.isEmpty(workers)) {
            return HttpUtil.getDefaultUrl();
        }

        var hostIp = Constant.HOST_IP;
        if (workers.stream().anyMatch(worker -> hostIp.equals(worker.getIp()))) {
            return HttpUtil.getDefaultUrl();
        }

        var idx = random.nextInt(workers.size());
        var worker = workers.get(idx);
        return HttpUtil.buildHttpUrl(worker.getIp(), worker.getPort());
    }

    public @Nullable Worker randomWorker(List<Long> workerIds) {
        if (CollectionUtils.isEmpty(workerIds)) {
            return null;
        }

        var workers = workerService.listActiveWorkersByIds(workerIds);
        if (CollectionUtils.isEmpty(workers)) {
            return null;
        }

        var idx = random.nextInt(workers.size());
        return workers.get(idx);
    }
}
