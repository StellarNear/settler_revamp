package com.apogames.tiwanaku.engine;

import java.time.Duration;
import java.util.Objects;

public record GenerationSpec(
        TileShape shape,
        int columns,
        int rows,
        int maxRegionSize,
        Difficulty difficulty,
        long seed,
        int maxPartitionAttempts,
        long maxSearchNodes,
        Duration timeout,
        CancellationToken cancellationToken) {
    public GenerationSpec(
            int columns,
            int rows,
            int maxRegionSize,
            Difficulty difficulty,
            long seed,
            int maxPartitionAttempts,
            long maxSearchNodes,
            Duration timeout,
            CancellationToken cancellationToken) {
        this(TileShape.SQUARE, columns, rows, maxRegionSize, difficulty, seed,
                maxPartitionAttempts, maxSearchNodes, timeout, cancellationToken);
    }

    public GenerationSpec {
        Objects.requireNonNull(shape, "shape");
        if (columns <= 0 || rows <= 0 || (long) columns * rows > PuzzleDefinition.MAX_CELLS) {
            throw new IllegalArgumentException("Board dimensions must contain between 1 and 240 cells");
        }
        if (maxRegionSize < 5 || maxRegionSize > PuzzleDefinition.MAX_REGION_SIZE) {
            throw new IllegalArgumentException("Maximum region size must be between 5 and 9");
        }
        Objects.requireNonNull(difficulty, "difficulty");
        if (maxPartitionAttempts <= 0 || maxSearchNodes <= 0) {
            throw new IllegalArgumentException("Generation budgets must be positive");
        }
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Generation timeout must be positive");
        }
    }
}
