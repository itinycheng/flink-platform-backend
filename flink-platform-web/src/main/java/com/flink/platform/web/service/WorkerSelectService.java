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
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;

/** Worker select service. */
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class WorkerSelectService {

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

    public Map<Long, Worker> mapActiveWorkersById() {
        return workerService.listActiveWorkers().stream().collect(Collectors.toMap(Worker::getId, worker -> worker));
    }

    public @Nullable Worker randomWorker(List<Long> workerIds, Map<Long, Worker> activeWorkerMap) {
        if (CollectionUtils.isEmpty(workerIds)) {
            return null;
        }

        var candidates = workerIds.stream()
                .map(activeWorkerMap::get)
                .filter(Objects::nonNull)
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }

        return candidates.get(random.nextInt(candidates.size()));
    }
}
