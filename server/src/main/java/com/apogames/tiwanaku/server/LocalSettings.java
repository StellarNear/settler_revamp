package com.apogames.tiwanaku.server;

import jakarta.validation.constraints.*;

public record LocalSettings(
        @NotNull @Pattern(regexp="en|de") String locale,
        @NotNull @Pattern(regexp="dark|light|high-contrast") String theme,
        @NotNull Boolean reducedDetail, @NotNull Boolean showConflicts,
        @NotNull @Pattern(regexp="3x3|4x4|5x5|7x5|9x5|15x10|20x12") String preset,
        @Min(5) @Max(9) int maxRegionSize,
        @NotNull @Pattern(regexp="EASY|MEDIUM|HARD") String difficulty,
        @NotNull @Pattern(regexp="[A-Za-z]{0,3}") String playerTag) {
    public LocalSettings normalized() {
        return new LocalSettings(locale, theme, reducedDetail, showConflicts, preset, maxRegionSize,
                difficulty, playerTag.toUpperCase(java.util.Locale.ROOT));
    }
}
