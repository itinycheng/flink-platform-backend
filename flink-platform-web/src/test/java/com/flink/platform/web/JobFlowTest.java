package com.flink.platform.web;

import com.flink.platform.common.model.JobEdge;
import com.flink.platform.common.model.JobVertex;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.common.util.UuidGenerator;
import com.flink.platform.dao.entity.JobFlowDag;
import com.flink.platform.dao.entity.alert.AlertConfigList;
import com.flink.platform.web.entity.request.JobFlowRequest;
import org.apache.commons.lang3.ObjectUtils;
import org.junit.Test;

import java.util.concurrent.PriorityBlockingQueue;

import static com.flink.platform.common.enums.ExecutionStatus.SUCCESS;
import static com.flink.platform.common.enums.JobFlowStatus.OFFLINE;

/** job flow manager test. */
public class JobFlowTest {

    @Test
    public void test1() {
        JobFlowDag dag = new JobFlowDag();
        JobVertex jobVertex1 = new JobVertex(19L, 19L);
        JobVertex jobVertex2 = new JobVertex(20L, 20L);
        dag.addVertex(jobVertex1);
        dag.addVertex(jobVertex2);
        JobEdge jobEdge = new JobEdge(19L, 20L, SUCCESS);
        dag.addEdge(jobEdge);

        JobFlowRequest jobFlowRequest = new JobFlowRequest();
        jobFlowRequest.setCode(UuidGenerator.generateShortUuid());
        jobFlowRequest.setName("test_1");
        jobFlowRequest.setUserId(0L);
        jobFlowRequest.setDescription("description");
        jobFlowRequest.setCronExpr("0 0/10 * * * ?");
        jobFlowRequest.setFlow(dag);
        jobFlowRequest.setPriority(8);
        jobFlowRequest.setAlerts(new AlertConfigList());
        jobFlowRequest.setStatus(OFFLINE);

        String json = JsonUtil.toJsonString(jobFlowRequest.getJobFlow());
        System.out.println(json);
    }

    @Test
    public void test2() {
        PriorityBlockingQueue<Integer> queue = new PriorityBlockingQueue<>(5, (o1, o2) -> ObjectUtils.compare(o2, o1));
        queue.offer(3);
        queue.offer(2);
        queue.offer(1);
        queue.offer(4);
        queue.offer(5);
        for (int i = 0; i < 5; i++) {
            System.out.println(queue.poll());
        }
    }
}
