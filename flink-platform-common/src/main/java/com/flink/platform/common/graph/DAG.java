package com.flink.platform.common.graph;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.util.stream.Collectors.toList;

/** Directed acyclic graph. */
@Slf4j
public class DAG<VId, V extends Vertex<VId>, E extends Edge<VId>> {

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    @Getter
    private final Set<V> vertices;

    @Getter
    private final Set<E> edges;

    public DAG() {
        vertices = new HashSet<>();
        edges = new HashSet<>();
    }

    public void addVertex(V vertex) {
        lock.writeLock().lock();

        try {
            vertices.add(vertex);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean addEdge(E edge) {
        lock.writeLock().lock();

        try {
            // Whether an edge can be successfully added(fromNode -> toNode)
            if (!isLegalEdge(edge)) {
                log.error("Add edge({} -> {}) is invalid, cause cycle.", edge.getFromVId(), edge.getToVId());
                return false;
            }

            edges.add(edge);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public V getVertex(VId vertexId) {
        for (V vertex : vertices) {
            if (vertexId.equals(vertex.getId())) {
                return vertex;
            }
        }

        throw new IllegalArgumentException("Vertex not found, vertexId = " + vertexId);
    }

    public Collection<E> getEdgesFromVertex(V fromVertex) {
        lock.readLock().lock();

        try {
            List<E> vertices = new ArrayList<>();
            for (E edge : edges) {
                if (fromVertex.getId().equals(edge.getFromVId())) {
                    vertices.add(edge);
                }
            }

            return vertices;
        } finally {
            lock.readLock().unlock();
        }
    }

    @JsonIgnore
    public Collection<V> getBeginVertices() {
        lock.readLock().lock();

        try {
            Set<VId> endVertices = new HashSet<>();
            for (E edge : edges) {
                endVertices.add(edge.getToVId());
            }
            List<VId> allVertices = this.vertices.stream().map(Vertex::getId).collect(toList());
            return CollectionUtils.subtract(allVertices, endVertices).stream()
                    .map(this::getVertex)
                    .collect(toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    @JsonIgnore
    public Collection<V> getEndVertices() {
        lock.readLock().lock();

        try {
            Set<VId> startVertices = new HashSet<>();
            Set<VId> endVertices = new HashSet<>();
            for (E edge : edges) {
                startVertices.add(edge.getFromVId());
                endVertices.add(edge.getToVId());
            }
            return CollectionUtils.subtract(endVertices, startVertices).stream()
                    .map(this::getVertex)
                    .collect(toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    public Collection<V> getPreVertices(V vertex) {
        lock.readLock().lock();

        try {
            Set<VId> pre = new HashSet<>();
            for (E edge : edges) {
                if (vertex.getId().equals(edge.getToVId())) {
                    pre.add(edge.getFromVId());
                }
            }
            return pre.stream().map(this::getVertex).collect(toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    public Collection<V> getNextVertices(V vertex) {
        lock.readLock().lock();

        try {
            if (vertex == null) {
                return getBeginVertices();
            }

            Set<VId> next = new HashSet<>();
            for (E edge : edges) {
                if (vertex.getId().equals(edge.getFromVId())) {
                    next.add(edge.getToVId());
                }
            }
            return next.stream().map(this::getVertex).collect(toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    public E getEdge(V fromVertex, V toVertex) {
        return getEdge(fromVertex.getId(), toVertex.getId());
    }

    public E getEdge(VId fromVertexId, VId toVertexId) {
        for (E edge : edges) {
            if (edge.getFromVId().equals(fromVertexId) && edge.getToVId().equals(toVertexId)) {
                return edge;
            }
        }

        throw new IllegalArgumentException("No edge found");
    }

    private boolean isLegalEdge(E edge) {
        if (!vertices.contains(getVertex(edge.getFromVId()))) {
            log.error("Edge fromVertex[{}] not found", edge.getFromVId());
            return false;
        }

        if (!vertices.contains(getVertex(edge.getToVId()))) {
            log.error("Edge toVertex[{}] not found", edge.getToVId());
            return false;
        }

        if (edge.getFromVId().equals(edge.getToVId())) {
            log.error("Edge fromNode({}) can't equals toNode({})", edge.getFromVId(), edge.getToVId());
            return false;
        }

        // Determine whether the DAG has cycled.
        VId fromVertex = edge.getFromVId();
        Queue<VId> queue = new LinkedList<>();
        queue.add(edge.getToVId());
        while (!queue.isEmpty()) {
            VId key = queue.poll();
            for (E e : edges) {
                if (key.equals(e.getFromVId())) {
                    if (fromVertex.equals(e.getToVId())) {
                        return false;
                    }

                    queue.add(e.getToVId());
                }
            }
        }

        return true;
    }

    @JsonIgnore
    public boolean isValid() {
        return this.getEdges().stream().allMatch(this::isLegalEdge);
    }
}
