package com.flink.platform.common.graph;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** Directed acyclic graph. */
@Slf4j
@Getter
public class DAG<VId, V extends Vertex<VId>, E extends Edge<V>> {

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private final Set<V> vertices;

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
                log.error(
                        "Add edge({} -> {}) is invalid, cause cycle.",
                        edge.getFromVertex(),
                        edge.getToVertex());
                return false;
            }

            edges.add(edge);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Collection<V> getBeginVertices() {
        Set<V> startVertices = new HashSet<>();
        Set<V> endVertices = new HashSet<>();
        for (E edge : edges) {
            startVertices.add(edge.getFromVertex());
            endVertices.add(edge.getToVertex());
        }
        return CollectionUtils.subtract(startVertices, endVertices);
    }

    public Collection<V> getEndVertices() {
        Set<V> startVertices = new HashSet<>();
        Set<V> endVertices = new HashSet<>();
        for (E edge : edges) {
            startVertices.add(edge.getFromVertex());
            endVertices.add(edge.getToVertex());
        }
        return CollectionUtils.subtract(endVertices, startVertices);
    }

    public Collection<V> getPreVertices(V vertex) {
        Set<V> pre = new HashSet<>();
        for (E edge : edges) {
            if (vertex.equals(edge.getToVertex())) {
                pre.add(edge.getFromVertex());
            }
        }
        return pre;
    }

    public Collection<V> getNextVertices(V vertex) {
        Set<V> next = new HashSet<>();
        for (E edge : edges) {
            if (vertex.equals(edge.getFromVertex())) {
                next.add(edge.getToVertex());
            }
        }
        return next;
    }

    private boolean isLegalEdge(E edge) {
        if (!vertices.contains(edge.getFromVertex())) {
            log.error("Edge fromVertex[{}] not found", edge.getFromVertex());
            return false;
        }

        if (!vertices.contains(edge.getToVertex())) {
            log.error("Edge toVertex[{}] not found", edge.getToVertex());
            return false;
        }

        if (edge.getFromVertex().equals(edge.getToVertex())) {
            log.error(
                    "Edge fromNode({}) can't equals toNode({})",
                    edge.getFromVertex(),
                    edge.getToVertex());
            return false;
        }

        // Determine whether the DAG has cycled.
        V fromVertex = edge.getFromVertex();
        Queue<V> queue = new LinkedList<>();
        queue.add(edge.getToVertex());
        while (!queue.isEmpty()) {
            V key = queue.poll();
            for (E e : edges) {
                if (key.equals(e.getFromVertex())) {
                    if (fromVertex.equals(e.getToVertex())) {
                        return false;
                    }

                    queue.add(e.getToVertex());
                }
            }
        }

        return true;
    }
}
