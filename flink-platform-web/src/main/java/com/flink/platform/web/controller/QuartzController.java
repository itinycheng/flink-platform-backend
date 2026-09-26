package com.flink.platform.web.controller;

import com.flink.platform.common.constants.Constant;
import com.flink.platform.web.annotation.RequirePermission;
import com.flink.platform.web.dto.ResultInfo;
import com.flink.platform.web.quartz.QuartzService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.flink.platform.common.enums.Permission.WORKSPACE_MANAGE;
import static com.flink.platform.common.enums.Permission.WORKSPACE_VIEW;
import static com.flink.platform.common.enums.ResponseStatus.INVALID_CRONTAB_EXPR;
import static com.flink.platform.common.util.DateUtil.GLOBAL_DATE_TIME_FORMAT;
import static com.flink.platform.common.util.DateUtil.format;
import static com.flink.platform.web.dto.ResultInfo.failure;
import static com.flink.platform.web.dto.ResultInfo.success;

/** Quartz controller. */
@Slf4j
@RestController
@RequestMapping("/quartz")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class QuartzController {

    private final QuartzService quartzService;

    @RequirePermission(WORKSPACE_MANAGE)
    @GetMapping(value = "/metadata")
    public ResultInfo<Map<String, String>> metadata() {
        var result = new HashMap<String, String>();
        result.put("hostname", Constant.HOSTNAME);
        result.put("ip", Constant.HOST_IP);
        result.put("quartz", quartzService.quartzMetadata());
        return success(result);
    }

    @RequirePermission(WORKSPACE_VIEW)
    @GetMapping(value = "/parseExpr")
    public ResultInfo<List<String>> parseExpr(@RequestParam(name = "cron") String cron) {
        try {
            var num = 5;
            var triggerTimes = new ArrayList<String>(num);
            var fromTime = LocalDateTime.now();
            for (int i = 0; i < num; i++) {
                var optionalNext = quartzService.nextTriggerTimeAfter(cron, fromTime);
                if (optionalNext.isEmpty()) {
                    break;
                }

                var nextTriggerTime = optionalNext.get();
                triggerTimes.add(format(nextTriggerTime, GLOBAL_DATE_TIME_FORMAT));
                fromTime = nextTriggerTime;
            }
            return success(triggerTimes);
        } catch (Exception e) {
            return failure(INVALID_CRONTAB_EXPR);
        }
    }
}
