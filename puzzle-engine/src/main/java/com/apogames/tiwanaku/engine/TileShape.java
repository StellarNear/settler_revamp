package com.apogames.tiwanaku.engine;

/** Tile shape of a board. Square keeps the historic columns × rows grid. */
public enum TileShape {
    SQUARE,
    HEXAGON,
    TRIANGLE,
    PENTAGON;

    public static TileShape parse(String value) {
        return value == null || value.isBlank() ? SQUARE : valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
