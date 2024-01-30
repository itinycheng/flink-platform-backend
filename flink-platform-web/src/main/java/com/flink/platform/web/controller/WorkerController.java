package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flink.platform.common.constants.Constant;
import com.flink.platform.common.enums.WorkerStatus;
import com.flink.platform.dao.entity.User;
import com.flink.platform.dao.entity.Worker;
import com.flink.platform.dao.service.WorkerService;
import com.flink.platform.web.entity.request.WorkerRequest;
import com.flink.platform.web.entity.response.ResultInfo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

import static com.flink.platform.common.enums.ResponseStatus.ERROR_PARAMETER;
import static com.flink.platform.common.enums.ResponseStatus.USER_HAVE_NO_PERMISSION;
import static com.flink.platform.common.enums.UserType.ADMIN;
import static com.flink.platform.common.enums.WorkerStatus.INACTIVE;
import static com.flink.platform.web.entity.response.ResultInfo.failure;
import static com.flink.platform.web.entity.response.ResultInfo.success;

/** Worker controller. */
@RestController
@RequestMapping("/worker")
public class WorkerController {

    @Autowired
    private WorkerService workerService;

    @GetMapping(value = "/get/{workerId}")
    public ResultInfo<Worker> get(@PathVariable Long workerId) {
        Worker worker = workerService.getById(workerId);
        return success(worker);
    }

    @PostMapping(value = "/create")
    public ResultInfo<Long> create(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser, @RequestBody WorkerRequest workerRequest) {
        String errorMsg = workerRequest.validateOnCreate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        if (loginUser.getType() != ADMIN) {
            return failure(USER_HAVE_NO_PERMISSION);
        }

        Worker worker = workerRequest.getWorker();
        worker.setId(null);
        workerService.save(worker);
        return success(worker.getId());
    }

    @PostMapping(value = "/update")
    public ResultInfo<Long> update(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser, @RequestBody WorkerRequest workerRequest) {
        String errorMsg = workerRequest.validateOnUpdate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        if (loginUser.getType() != ADMIN) {
            return failure(USER_HAVE_NO_PERMISSION);
        }

        Worker worker = workerRequest.getWorker();
        workerService.updateById(worker);
        return success(worker.getId());
    }

    @GetMapping(value = "/page")
    public ResultInfo<IPage<Worker>> page(
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "20") Integer size,
            @RequestParam(name = "role", required = false) WorkerStatus role,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "ip", required = false) String ip) {
        Page<Worker> pager = new Page<>(page, size);
        LambdaQueryWrapper<Worker> queryWrapper = new QueryWrapper<Worker>()
                .lambda()
                .like(Objects.nonNull(name), Worker::getName, name)
                .like(Objects.nonNull(ip), Worker::getIp, ip);

        if (role != null) {
            queryWrapper.eq(Worker::getRole, role);
        } else {
            queryWrapper.ne(Worker::getRole, INACTIVE).or().isNull(Worker::getRole);
        }

        IPage<Worker> iPage = workerService.page(pager, queryWrapper);
        return success(iPage);
    }

    @GetMapping(value = "/list")
    public ResultInfo<List<Worker>> list() {
        return success(workerService.list());
    }

    @GetMapping(value = "/purge/{workerId}")
    public ResultInfo<Long> purge(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser, @PathVariable long workerId) {
        Worker worker = workerService.getById(workerId);
        if (worker == null) {
            return failure(ERROR_PARAMETER);
        }

        if (loginUser.getType() != ADMIN) {
            return failure(USER_HAVE_NO_PERMISSION);
        }

        workerService.removeById(workerId);
        return success(workerId);
    }
}
