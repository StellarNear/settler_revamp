package com.apogames.tiwanaku.engine;

public enum Difficulty {
    EASY(0.35),
    MEDIUM(0.30),
    HARD(0.24),
    EXTREME(0),
    GODLIKE(0);

    private final double clueFraction;

    Difficulty(double clueFraction) {
        this.clueFraction = clueFraction;
    }

    int targetClues(int cellCount) {
        if (minimalClues()) {
            return 0;
        }
        return Math.max(1, (int) Math.floor(cellCount * clueFraction));
    }

    public boolean minimalClues() {
        return this == EXTREME || this == GODLIKE;
    }
}
