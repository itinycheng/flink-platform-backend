package com.flink.platform.web.common;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Time sorted map, thread safety.
 */
public class ValueSortedMap<K, V> {

    private final Map<K, V> kvMap;

    private final TreeSet<V> valSet;

    private final ReadWriteLock lock;

    public ValueSortedMap(Comparator<V> comparator) {
        this.kvMap = new HashMap<>();
        this.valSet = new TreeSet<>(comparator);
        this.lock = new ReentrantReadWriteLock();
    }

    public void put(K key, V value) {
        lock.writeLock().lock();
        try {
            kvMap.put(key, value);
            valSet.add(value);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public V get(K key) {
        lock.readLock().lock();
        try {
            return kvMap.get(key);
        } finally {
            lock.readLock().unlock();
        }
    }

    public V remove(K key) {
        lock.writeLock().lock();
        try {
            V value = kvMap.remove(key);
            if (value != null) {
                valSet.remove(value);
            }
            return value;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public V getFirst() {
        lock.readLock().lock();
        try {
            return valSet.getFirst();
        } finally {
            lock.readLock().unlock();
        }
    }

    public int size() {
        lock.readLock().lock();
        try {
            return kvMap.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    // Low performance, better use remove() instead.
    public V removeFirst() {
        lock.writeLock().lock();
        try {
            V first = valSet.pollFirst();
            if (first != null) {
                kvMap.values().remove(first);
            }
            return first;
        } finally {
            lock.writeLock().unlock();
        }
    }
}
