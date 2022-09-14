package com.flink.platform.web.controller;

import com.flink.platform.web.entity.response.ResultInfo;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronExpression;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.flink.platform.common.enums.ResponseStatus.INVALID_CRONTAB_EXPR;
import static com.flink.platform.common.util.DateUtil.GLOBAL_DATE_TIME_FORMAT;
import static com.flink.platform.common.util.DateUtil.format;
import static com.flink.platform.web.entity.response.ResultInfo.failure;
import static com.flink.platform.web.entity.response.ResultInfo.success;

/** Quartz controller. */
@Slf4j
@RestController
@RequestMapping("/quartz")
public class QuartzController {

    @GetMapping(value = "/parseExpr")
    public ResultInfo<List<String>> parseExpr(@RequestParam(name = "cron") String cron) {
        try {
            CronExpression cronExpression = new CronExpression(cron);
            Date fromDate = new Date();
            int num = 5;
            List<String> validTimeList = new ArrayList<>(num);
            for (int i = 0; i < num; i++) {
                Date validTime = cronExpression.getNextValidTimeAfter(fromDate);
                validTimeList.add(format(validTime.getTime(), GLOBAL_DATE_TIME_FORMAT));
                fromDate = validTime;
            }
            return success(validTimeList);
        } catch (Exception e) {
            return failure(INVALID_CRONTAB_EXPR);
        }
    }
}
