package com.flink.platform.web.controller;

import com.flink.platform.common.constants.Constant;
import com.flink.platform.web.entity.response.ResultInfo;
import com.flink.platform.web.service.QuartzService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronExpression;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.flink.platform.common.enums.ResponseStatus.INVALID_CRONTAB_EXPR;
import static com.flink.platform.common.util.DateUtil.GLOBAL_DATE_TIME_FORMAT;
import static com.flink.platform.common.util.DateUtil.format;
import static com.flink.platform.web.entity.response.ResultInfo.failure;
import static com.flink.platform.web.entity.response.ResultInfo.success;

/** Quartz controller. */
@Slf4j
@RestController
@RequestMapping("/quartz")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class QuartzController {

    private final QuartzService quartzService;

    @GetMapping(value = "/metadata")
    public ResultInfo<Map<String, String>> metadata() {
        var result = new HashMap<String, String>();
        result.put("hostname", Constant.HOSTNAME);
        result.put("ip", Constant.HOST_IP);
        result.put("quartz", quartzService.quartzMetadata());
        return success(result);
    }

    @GetMapping(value = "/parseExpr")
    public ResultInfo<List<String>> parseExpr(@RequestParam(name = "cron") String cron) {
        try {
            var cronExpression = new CronExpression(cron);
            var fromDate = new Date();
            var num = 5;
            var validTimeList = new ArrayList<String>(num);
            for (int i = 0; i < num; i++) {
                var validTime = cronExpression.getNextValidTimeAfter(fromDate);
                validTimeList.add(format(validTime.getTime(), GLOBAL_DATE_TIME_FORMAT));
                fromDate = validTime;
            }
            return success(validTimeList);
        } catch (Exception e) {
            return failure(INVALID_CRONTAB_EXPR);
        }
    }
}
