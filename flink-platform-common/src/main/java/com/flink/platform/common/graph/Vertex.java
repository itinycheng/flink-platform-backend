package com.flink.platform.common.graph;

import lombok.Data;

import java.util.Objects;

/** Base class for vertex. */
@Data
public class Vertex<ID> {

    private final ID id;

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Vertex<?> vertex = (Vertex<?>) o;
        return id.equals(vertex.id);
    }

    @Override
    public final int hashCode() {
        return Objects.hash(id);
    }
}
