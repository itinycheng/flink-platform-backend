package com.flink.platform.common.graph;

import com.flink.platform.common.util.Preconditions;
import lombok.Getter;

import java.util.Objects;

/** Base class for vertex. */
@Getter
public class Vertex<ID> {

    private final ID id;

    public Vertex(ID id) {
        this.id = Preconditions.checkNotNull(id);
    }

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
