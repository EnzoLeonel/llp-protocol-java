package com.flamingo.comm.llp.util;

import java.util.concurrent.atomic.AtomicLong;

/**
 * LLP parser statistics.
 * Thread-safe with AtomicLong.
 */
public class Statistics {
    private final AtomicLong framesOk = new AtomicLong(0);
    private final AtomicLong framesError = new AtomicLong(0);
    private final AtomicLong timeouts = new AtomicLong(0);
    private final long createdAt = System.currentTimeMillis();

    public void recordSuccess() {
        framesOk.incrementAndGet();
    }

    public void recordError() {
        framesError.incrementAndGet();
    }

    public void recordTimeout() {
        timeouts.incrementAndGet();
    }

    public long getFramesOk() {
        return framesOk.get();
    }

    public long getFramesError() {
        return framesError.get();
    }

    public long getTimeouts() {
        return timeouts.get();
    }

    public long getTotalFrames() {
        return framesOk.get() + framesError.get();
    }

    public double getSuccessRate() {
        long total = getTotalFrames();
        return total == 0 ? 0.0 : (double) framesOk.get() / total * 100.0;
    }

    public long getUptimeMs() {
        return System.currentTimeMillis() - createdAt;
    }

    public void reset() {
        framesOk.set(0);
        framesError.set(0);
        timeouts.set(0);
    }

    @Override
    public String toString() {
        return String.format(
                "Statistics{framesOk=%d, framesError=%d, timeouts=%d, successRate=%.2f%%, uptimeMs=%d}",
                getFramesOk(), getFramesError(), getTimeouts(), getSuccessRate(), getUptimeMs()
        );
    }
}