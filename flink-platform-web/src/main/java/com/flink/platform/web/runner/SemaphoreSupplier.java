package com.flink.platform.web.runner;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

@Slf4j
public class SemaphoreSupplier implements Supplier<JobResponse> {
    private final Semaphore semaphore;

    private final Supplier<JobResponse> supplier;

    public SemaphoreSupplier(Semaphore semaphore, Supplier<JobResponse> supplier) {
        this.semaphore = semaphore;
        this.supplier = supplier;
    }

    @Override
    public JobResponse get() {
        semaphore.acquireUninterruptibly();

        try {
            return supplier.get();
        } finally {
            semaphore.release();
        }
    }
}
