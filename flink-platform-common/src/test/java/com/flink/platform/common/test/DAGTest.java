package com.flink.platform.common.test;

import com.flink.platform.common.graph.DAG;
import com.flink.platform.common.graph.Edge;
import com.flink.platform.common.graph.Vertex;
import org.junit.Test;

/** Junit test for DAG. */
public class DAGTest {

    @Test
    public void test() {
        DAG<Long, Vertex<Long>, Edge<Long>> dag = new DAG<>();
        dag.addVertex(new Vertex<>(1L));
        dag.addVertex(new Vertex<>(2L));
        dag.addVertex(new Vertex<>(3L));
        dag.addVertex(new Vertex<>(4L));
        dag.addVertex(new Vertex<>(5L));
        System.out.println(dag.addEdge(new Edge<>(1L, 2L)));
        System.out.println(dag.addEdge(new Edge<>(1L, 3L)));
        System.out.println(dag.addEdge(new Edge<>(2L, 3L)));
        System.out.println(dag.addEdge(new Edge<>(2L, 5L)));
        System.out.println(dag.addEdge(new Edge<>(3L, 4L)));
        System.out.println(dag.addEdge(new Edge<>(3L, 5L)));
        System.out.println(dag.addEdge(new Edge<>(5L, 1L)));
        System.out.println(dag.getBeginVertices());
        System.out.println(dag.getEndVertices());
        System.out.println(dag.getPreVertices(new Vertex<>(3L)));
        System.out.println(dag.getNextVertices(new Vertex<>(3L)));
        System.out.println("~~~");
    }
}
