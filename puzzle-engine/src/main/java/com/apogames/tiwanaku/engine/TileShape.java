package com.apogames.tiwanaku.engine;

/** Tile geometry and contact rule. Existing enum values preserve their historic rules. */
public enum TileShape {
    SQUARE,
    SQUARE_SIDES_ONLY,
    HEXAGON,
    TRIANGLE,
    PENTAGON,
    PENTAGON_CORNERS;

    public boolean isSquare() {
        return this == SQUARE || this == SQUARE_SIDES_ONLY;
    }

    public boolean includesCornerConflicts() {
        return this == SQUARE || this == PENTAGON_CORNERS;
    }

    public static TileShape parse(String value) {
        return value == null || value.isBlank() ? SQUARE : valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
