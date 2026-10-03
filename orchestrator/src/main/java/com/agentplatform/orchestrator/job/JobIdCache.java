package com.agentplatform.orchestrator.job;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Bounded, thread-safe, process-local cache mapping exact job ID to Job.
 * Eviction is insertion-order (eldest inserted) when capacity exceeded.
 * This is process-local (in-memory) and does not survive restarts.
 */
final class JobIdCache {
    private static final int DEFAULT_CAPACITY = 1000;

    private final int capacity;
    private final LinkedHashMap<String, Job> map;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    JobIdCache() {
        this(DEFAULT_CAPACITY);
    }

    JobIdCache(int capacity) {
        if (capacity < 1) {
            this.capacity = DEFAULT_CAPACITY;
        } else {
            this.capacity = capacity;
        }
        this.map = new LinkedHashMap<String, Job>(256, 0.75f, false);
    }

    void put(Job job) {
        if (job == null || job.id() == null || job.id().isBlank()) {
            return;
        }
        lock.writeLock().lock();
        try {
            map.put(job.id(), job);
            if (map.size() > capacity) {
                Map.Entry<String, Job> eldest = map.entrySet().iterator().next();
                if (eldest != null) {
                    map.remove(eldest.getKey());
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    void putAll(List<Job> jobs) {
        if (jobs == null || jobs.isEmpty()) {
            return;
        }
        for (Job j : jobs) {
            put(j);
        }
    }

    Job get(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        lock.readLock().lock();
        try {
            return map.get(id);
        } finally {
            lock.readLock().unlock();
        }
    }

    void clear() {
        lock.writeLock().lock();
        try {
            map.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    int size() {
        lock.readLock().lock();
        try {
            return map.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    List<Job> snapshotValues() {
        lock.readLock().lock();
        try {
            return new ArrayList<Job>(map.values());
        } finally {
            lock.readLock().unlock();
        }
    }
}