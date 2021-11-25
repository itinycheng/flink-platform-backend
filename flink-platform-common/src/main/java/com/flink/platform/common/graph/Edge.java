package com.flink.platform.common.graph;

import com.flink.platform.common.util.Preconditions;
import lombok.Getter;

import java.util.Objects;

/** edge. */
@Getter
public class Edge<VId> {

    private final VId fromVId;

    private final VId toVId;

    public Edge(VId fromVId, VId toVId) {
        this.fromVId = Preconditions.checkNotNull(fromVId);
        this.toVId = Preconditions.checkNotNull(toVId);
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
