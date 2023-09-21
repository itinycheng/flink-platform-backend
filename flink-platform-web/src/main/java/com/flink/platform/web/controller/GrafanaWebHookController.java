package com.flink.platform.web.controller;

import com.flink.platform.alert.AlertSender;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.dao.entity.alert.FeiShuAlert;
import com.flink.platform.web.entity.response.ResultInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static com.flink.platform.web.entity.response.ResultInfo.success;

/** Do something about alarm forwarding. */
@Slf4j
@RestController
@RequestMapping("/webhook")
public class GrafanaWebHookController {

    @Autowired
    private AlertSender alertSender;

    @PostMapping(value = "/forwardToFeiShu")
    public ResultInfo<String> forwardToFeiShu(@RequestBody Map<String, Object> grafanaMap) {
        log.info(JsonUtil.toJsonString(grafanaMap));
        String message = (String) grafanaMap.get("message");
        FeiShuAlert feiShuAlert = JsonUtil.toBean(message, FeiShuAlert.class);
        String result = null;
        if (feiShuAlert != null) {
            result = alertSender.sendToFeiShu(feiShuAlert);
        }
        return success(result);
    }
}
