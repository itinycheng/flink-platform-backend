package com.flink.platform.common.graph;

import lombok.Data;

/** edge. */
@Data
public class Edge<Vertex> {

    private final Vertex fromVertex;

    private final Vertex toVertex;
}
