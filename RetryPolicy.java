package com.lasya.txengine.engine;

import java.time.Duration;

public final class RetryPolicy {
    private final int maxRetries;
    private final Duration baseDelay;

    public RetryPolicy(int maxRetries, Duration baseDelay) {
        this.maxRetries = maxRetries;
        this.baseDelay = baseDelay;
    }

    public int maxRetries() { return maxRetries; }

    public Duration backoffForAttempt(int attempt) {
        // attempt: 1..N
        long multiplier = 1L << Math.max(0, attempt - 1); // 1,2,4,8...
        long millis = Math.min(baseDelay.toMillis() * multiplier, 10_000); // cap at 10s
        return Duration.ofMillis(millis);
    }
}
