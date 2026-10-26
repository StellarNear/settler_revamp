package com.apogames.tiwanaku.engine;

import java.time.Duration;
import java.util.Objects;

public record SolverOptions(long maxSearchNodes, Duration timeout, long seed, CancellationToken cancellationToken) {
    public SolverOptions {
        if (maxSearchNodes <= 0) {
            throw new IllegalArgumentException("Search-node budget must be positive");
        }
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Search timeout must be positive");
        }
    }

    public static SolverOptions defaults(long seed) {
        return new SolverOptions(10_000_000, Duration.ofSeconds(10), seed, new CancellationToken());
    }
}
