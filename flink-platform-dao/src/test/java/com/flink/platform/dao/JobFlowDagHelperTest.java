package com.flink.platform.dao;

import com.flink.platform.common.graph.DAG;
import com.flink.platform.common.model.JobEdge;
import com.flink.platform.common.model.JobVertex;
import com.flink.platform.dao.util.JobFlowDagHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.flink.platform.common.enums.ExecutionCondition.ALL_DONE;
import static com.flink.platform.common.enums.ExecutionCondition.ALL_MATCHED;
import static com.flink.platform.common.enums.ExecutionCondition.ANY_MATCHED;
import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** job flow dag helper test. */
class JobFlowDagHelperTest {

    private DAG<Long, JobVertex, JobEdge> dag;

    @BeforeEach
    void before() {
        dag = new DAG<>();
        var jobVertex1 = new JobVertex(1L, 1L);
        var jobVertex2 = new JobVertex(2L, 2L);
        var jobVertex3 = new JobVertex(3L, 3L);
        var jobVertex4 = new JobVertex(4L, 4L);
        var jobVertex5 = new JobVertex(5L, 5L);
        var jobVertex6 = new JobVertex(6L, 6L);
        jobVertex6.setPrecondition(ANY_MATCHED);
        dag.addVertex(jobVertex1);
        dag.addVertex(jobVertex2);
        dag.addVertex(jobVertex3);
        dag.addVertex(jobVertex4);
        dag.addVertex(jobVertex5);
        dag.addVertex(jobVertex6);
        var jobEdge1 = new JobEdge(1L, 3L, SUCCESS);
        var jobEdge2 = new JobEdge(2L, 3L, SUCCESS);
        var jobEdge3 = new JobEdge(2L, 4L, SUCCESS);
        var jobEdge4 = new JobEdge(3L, 5L, SUCCESS);
        var jobEdge5 = new JobEdge(4L, 5L, SUCCESS);
        var jobEdge6 = new JobEdge(1L, 6L, FAILURE);
        var jobEdge7 = new JobEdge(2L, 6L, FAILURE);
        var jobEdge8 = new JobEdge(3L, 6L, FAILURE);
        var jobEdge9 = new JobEdge(4L, 6L, FAILURE);
        var jobEdge10 = new JobEdge(5L, 6L, FAILURE);
        dag.addEdge(jobEdge1);
        dag.addEdge(jobEdge2);
        dag.addEdge(jobEdge3);
        dag.addEdge(jobEdge4);
        dag.addEdge(jobEdge5);
        dag.addEdge(jobEdge6);
        dag.addEdge(jobEdge7);
        dag.addEdge(jobEdge8);
        dag.addEdge(jobEdge9);
        dag.addEdge(jobEdge10);
    }

    @Test
    void test1() {
        var executableVertices = JobFlowDagHelper.getExecutableVertices(dag);
        System.out.println(executableVertices);
    }

    @Test
    void test2() {
        var executableVertices = JobFlowDagHelper.getExecutableVertices(dag);
        System.out.println(executableVertices);
    }

    @Test
    void test3() {
        var executableVertices = JobFlowDagHelper.getExecutableVertices(dag);
        System.out.println(executableVertices);
    }

    @Test
    void test4() {
        var vertex = dag.getVertex(2L);
        vertex.setJobRunStatus(SUCCESS);

        var executableVertices = JobFlowDagHelper.getExecutableVertices(dag);
        System.out.println(executableVertices);
    }

    @Test
    void test5() {
        var vertex = dag.getVertex(2L);
        vertex.setJobRunStatus(SUCCESS);

        vertex = dag.getVertex(1L);
        vertex.setJobRunStatus(SUCCESS);
        var executableVertices = JobFlowDagHelper.getExecutableVertices(dag);
        System.out.println(executableVertices);
    }

    @Test
    void test6() {
        var vertex = dag.getVertex(2L);
        vertex.setJobRunStatus(SUCCESS);

        vertex = dag.getVertex(1L);
        vertex.setJobRunStatus(SUCCESS);

        var executableVertices = JobFlowDagHelper.getExecutableVertices(dag);
        System.out.println(executableVertices);
    }

    @Test
    void test7() {
        var vertex = dag.getVertex(2L);
        vertex.setJobRunStatus(SUCCESS);

        vertex = dag.getVertex(1L);
        vertex.setJobRunStatus(SUCCESS);

        vertex = dag.getVertex(3L);
        vertex.setJobRunStatus(SUCCESS);

        var executableVertices = JobFlowDagHelper.getExecutableVertices(dag);
        System.out.println(executableVertices);
    }

    @Test
    void test8() {
        var vertex = dag.getVertex(2L);
        vertex.setJobRunStatus(SUCCESS);

        vertex = dag.getVertex(1L);
        vertex.setJobRunStatus(SUCCESS);

        vertex = dag.getVertex(3L);
        vertex.setJobRunStatus(SUCCESS);

        vertex = dag.getVertex(4L);
        vertex.setJobRunStatus(SUCCESS);

        var executableVertices = JobFlowDagHelper.getExecutableVertices(dag);
        System.out.println(executableVertices);
    }

    @Test
    void test9() {
        var vertex = dag.getVertex(2L);
        vertex.setJobRunStatus(SUCCESS);

        vertex = dag.getVertex(1L);
        vertex.setJobRunStatus(SUCCESS);

        vertex = dag.getVertex(3L);
        vertex.setJobRunStatus(SUCCESS);

        vertex = dag.getVertex(4L);
        vertex.setJobRunStatus(SUCCESS);

        vertex = dag.getVertex(5L);
        vertex.setJobRunStatus(SUCCESS);

        var executableVertices = JobFlowDagHelper.getExecutableVertices(dag);
        System.out.println(executableVertices);
    }

    @Test
    void test10() {
        var vertex = dag.getVertex(2L);
        vertex.setJobRunStatus(FAILURE);

        var executableVertices = JobFlowDagHelper.getExecutableVertices(dag);
        System.out.println(executableVertices);
    }

    @Test
    void test11() {
        var vertex = dag.getVertex(2L);
        vertex.setJobRunStatus(FAILURE);

        vertex = dag.getVertex(1L);
        vertex.setJobRunStatus(FAILURE);

        var executableVertices = JobFlowDagHelper.getExecutableVertices(dag);
        System.out.println(executableVertices);
    }

    @Test
    void test12() {
        var vertex = dag.getVertex(2L);
        vertex.setJobRunStatus(FAILURE);

        vertex = dag.getVertex(1L);
        vertex.setJobRunStatus(SUCCESS);

        var executableVertices = JobFlowDagHelper.getExecutableVertices(dag);
        System.out.println(executableVertices);
    }

    @Test
    void test13() {
        var vertex = dag.getVertex(2L);
        vertex.setJobRunStatus(FAILURE);

        vertex = dag.getVertex(1L);
        vertex.setJobRunStatus(FAILURE);

        vertex = dag.getVertex(6L);
        vertex.setJobRunStatus(SUCCESS);

        var executableVertices = JobFlowDagHelper.getExecutableVertices(dag);
        System.out.println(executableVertices);
    }

    /** Fan-in join: v1(SUCCESS), v2(FAILURE) both point to v3 via SUCCESS edges. */
    private static DAG<Long, JobVertex, JobEdge> fanIn(JobVertex v1, JobVertex v2, JobVertex v3) {
        var join = new DAG<Long, JobVertex, JobEdge>();
        join.addVertex(v1);
        join.addVertex(v2);
        join.addVertex(v3);
        join.addEdge(new JobEdge(v1.getId(), v3.getId(), SUCCESS));
        join.addEdge(new JobEdge(v2.getId(), v3.getId(), SUCCESS));
        return join;
    }

    @Test
    void allDoneRunsDespitePartialFailure() {
        var v1 = new JobVertex(1L, 1L);
        var v2 = new JobVertex(2L, 2L);
        var v3 = new JobVertex(3L, 3L);
        var join = fanIn(v1, v2, v3);
        v1.setJobRunStatus(SUCCESS);
        v2.setJobRunStatus(FAILURE);
        v3.setPrecondition(ALL_MATCHED);
        assertFalse(JobFlowDagHelper.isPreconditionSatisfied(v3, join));

        v3.setPrecondition(ANY_MATCHED);
        assertTrue(JobFlowDagHelper.isPreconditionSatisfied(v3, join));

        v3.setPrecondition(ALL_DONE);
        assertTrue(JobFlowDagHelper.isPreconditionSatisfied(v3, join));
    }

    @Test
    void allDoneDiscoveredWhenAllUpstreamsFailed() {
        var v1 = new JobVertex(1L, 1L);
        var v2 = new JobVertex(2L, 2L);
        var v3 = new JobVertex(3L, 3L);
        var join = fanIn(v1, v2, v3);
        v1.setJobRunStatus(FAILURE);
        v2.setJobRunStatus(FAILURE);
        v3.setPrecondition(ALL_DONE);
        assertTrue(JobFlowDagHelper.getExecutableVertices(join).contains(v3));
    }
}
