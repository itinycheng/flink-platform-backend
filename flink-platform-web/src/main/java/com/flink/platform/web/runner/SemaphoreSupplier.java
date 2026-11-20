package com.flink.platform.web.runner;

import com.flink.platform.web.lifecycle.AppRunner;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

@Slf4j
public class SemaphoreSupplier implements Supplier<JobResponse> {

    private static final long ACQUIRE_POLL_SECONDS = 1;

    private final Semaphore semaphore;

    private final BooleanSupplier aborted;

    private final Supplier<JobResponse> supplier;

    public SemaphoreSupplier(Semaphore semaphore, BooleanSupplier aborted, Supplier<JobResponse> supplier) {
        this.semaphore = semaphore;
        this.aborted = aborted;
        this.supplier = supplier;
    }

    @Override
    public JobResponse get() {
        try {
            while (!semaphore.tryAcquire(ACQUIRE_POLL_SECONDS, TimeUnit.SECONDS)) {
                if (isStopped()) {
                    return JobResponse.ABORTED;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return JobResponse.ABORTED;
        }

        try {
            if (isStopped()) {
                return JobResponse.ABORTED;
            }

            return supplier.get();
        } finally {
            semaphore.release();
        }
    }

    private boolean isStopped() {
        return AppRunner.isStopped() || aborted.getAsBoolean();
    }
}
