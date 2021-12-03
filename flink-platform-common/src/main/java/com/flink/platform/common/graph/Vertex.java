package com.flink.platform.common.graph;

import com.flink.platform.common.util.Preconditions;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

/** Base class for vertex. */
@Data
@NoArgsConstructor
public class Vertex<ID> {

    private ID id;

    public Vertex(ID id) {
        this.id = Preconditions.checkNotNull(id);
    }

    @Override
    public boolean equals(Object o) {
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
    public int hashCode() {
        return Objects.hash(id);
    }
}
