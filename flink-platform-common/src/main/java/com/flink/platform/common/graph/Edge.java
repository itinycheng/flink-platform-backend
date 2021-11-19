package com.flink.platform.common.graph;

import com.flink.platform.common.util.Preconditions;
import lombok.Getter;

import java.util.Objects;

/** edge. */
@Getter
public class Edge<Vertex> {

    private final Vertex fromVertex;

    private final Vertex toVertex;

    public Edge(Vertex fromVertex, Vertex toVertex) {
        this.fromVertex = Preconditions.checkNotNull(fromVertex);
        this.toVertex = Preconditions.checkNotNull(toVertex);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Edge)) {
            return false;
        }
        Edge<?> edge = (Edge<?>) o;
        return fromVertex.equals(edge.fromVertex) && toVertex.equals(edge.toVertex);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromVertex, toVertex);
    }
}
