package com.flink.platform.web.controller;

import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.web.entity.response.ResultInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

/** Only for test. */
@RestController
@RequestMapping("/testApi")
public class TestController {

    @Autowired private RestTemplate restTemplate;

    @GetMapping(value = "/test")
    public ResultInfo<JobRunInfo> test() {
        JobRunInfo forObject =
                restTemplate.getForObject(
                        "http://127.0.0.1:9104/internal/process/1/2", JobRunInfo.class);

        return ResultInfo.success(forObject);
    }
}
