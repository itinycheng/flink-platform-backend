package com.itiger.persona.monitor;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.itiger.persona.service.IJobRunInfoService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.itiger.persona.util.HttpUtil;
import com.itiger.persona.common.enums.JobYarnStatusEnum;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EnableScheduling
@Component
@Slf4j
public class JobStateMonitor {
    public static int lock = 0;

    @Autowired
    private IJobRunInfoService iJobRunInfoService;


    @Scheduled(cron = "0/30 * * * * ? ") //每30秒执行一次
    public void updateJobState() {
        // 先看并行锁，如果为0则开始
        if (lock == 0) {
            try {
                //TODO 查数据库，找出running状态的写入队列，添加并行锁
                List<Map<String,Object>> infoList = iJobRunInfoService.listAllRunningJobs();
                ArrayList<Map<String, Object>> jobIdQueue = new ArrayList<>();
                for (Map<String,Object> info: infoList){
                    JSONObject tempJson = JSONObject.parseObject(info.get("back_info").toString());
                    if (tempJson != null) {
                        String tempJobId = tempJson.getString("appId");
                        if (tempJobId != null) {
                            Map<String, Object> tempMap = new HashMap<>();
                            tempMap.put("appId", tempJobId);
                            tempMap.put("id", info.get("id"));
                            tempMap.put("status", info.get("status"));
                            jobIdQueue.add(tempMap);
                        }
                    }
                }
                //TODO 调用其他类---多线程查hdfs接口找出当前状态，更新数据库，解锁
                if (jobIdQueue.size() > 0) {
                    lock = 1;
                    for (Map<String, Object> job : jobIdQueue) {
                        Object tempAppId = job.get("appId");
                        if (tempAppId != null){
                            String appId = tempAppId.toString();
                            Integer preStatus = (Integer) job.get("status");
                            String url = "http://10.8.12.12:8088/ws/v1/cluster/apps/" + appId;
                            log.info("获取Job状态 appId={} url={}", appId, url);
                            String res = HttpUtil.buildRestTemplate(HttpUtil.TIME_OUT_1_M).getForObject(url, String.class);
                            if (StringUtils.isEmpty(res) || JSON.parseObject(res).get("state") == null) {
                                log.error("请求状态失败:返回结果为空或缺少state字段 url={}", url);
                            }
                            Integer newStatus = JobYarnStatusEnum.getCodeByDesc(String.valueOf(JSON.parseObject(res).get("state")));
                            if(newStatus != null && !newStatus.equals(preStatus)){
                                iJobRunInfoService.updateJobState((Integer)job.get("id"), newStatus);
                            }
                        }
                    }
                    lock = 0;
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void killJobByAppId(String appId) throws Exception {
        // curl -u hadoop:123456 -H "Accept: application/json" -H "Content-type: application/json" -v -X PUT -d '{"state": "KILLED"}'
        try (final CloseableHttpClient httpclient = HttpClients.createDefault()) {
            final HttpPut httpput = new HttpPut("http://10.8.12.12:8088/ws/v1/cluster/apps/" + appId + "/state");

            JSONObject params = new JSONObject();

            params.put("state","KILLED");
            StringEntity stringEntity = new StringEntity(params.toString(),  "UTF-8");
            stringEntity.setContentType("application/json");
            httpput.setEntity(stringEntity);

            log.info("执行命令 " + httpput.getMethod() +" URL: "+ httpput.getURI());
            try (final CloseableHttpResponse response = httpclient.execute(httpput)) {
                log.info("返回消息体内容: " + response);

                final HttpEntity resEntity = response.getEntity();
                if (resEntity != null) {
                    log.info("返回消息体大小: " + resEntity.getContentLength());
                }
                try {
                    EntityUtils.consume(resEntity);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
