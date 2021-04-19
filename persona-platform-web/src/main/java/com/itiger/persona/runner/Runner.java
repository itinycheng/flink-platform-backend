package com.itiger.persona.runner;

import com.itiger.persona.service.IJobInfoService;
import com.itiger.persona.quartz.QuartzService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * @author gengshikun
 * @date 2016/11/25
 */
@Component
@Slf4j
public class Runner implements CommandLineRunner {

    @Autowired
    private IJobInfoService iJobInfoService;

    @Autowired
    private QuartzService quartzService;

    /**
     * @param strings
     * @throws Exception
     */
    @Override
    public void run(String... strings) throws Exception {

        this.initSignatureInfo();
    }

    private void initSignatureInfo() {

//        List<JobInfo> list = this.iJobInfoService.list();
//
//        list.stream().forEach(item -> {
//            quartzService.addOrFailQuartzJob(item);
//        });

    }

}
