package com.flink.platform.common.graph;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

import static com.flink.platform.common.util.Preconditions.checkNotNull;

/** edge. */
@Data
@NoArgsConstructor
public class Edge<VId> {

    private VId fromVId;

    private VId toVId;

    public Edge(VId fromVId, VId toVId) {
        this.fromVId = checkNotNull(fromVId);
        this.toVId = checkNotNull(toVId);
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
        return fromVId.equals(edge.fromVId) && toVId.equals(edge.toVId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromVId, toVId);
    }

    public <T> T unwrap(Class<T> clazz) {
        return clazz.isInstance(this) ? clazz.cast(this) : null;
    }
}
