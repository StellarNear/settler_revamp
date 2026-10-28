package com.apogames.tiwanaku.server;

import com.apogames.tiwanaku.engine.Difficulty;
import com.apogames.tiwanaku.engine.TileShape;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record GenerationRequest(
        @Min(1) @Max(240) int columns,
        @Min(1) @Max(240) int rows,
        @Min(5) @Max(9) int maxRegionSize,
        @NotNull Difficulty difficulty,
        long seed,
        TileShape shape) {
}
