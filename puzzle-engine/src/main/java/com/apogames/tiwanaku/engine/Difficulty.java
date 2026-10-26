package com.apogames.tiwanaku.engine;

public enum Difficulty {
    EASY(0.35),
    MEDIUM(0.30),
    HARD(0.24);

    private final double clueFraction;

    Difficulty(double clueFraction) {
        this.clueFraction = clueFraction;
    }

    int targetClues(int cellCount) {
        return Math.max(1, (int) Math.floor(cellCount * clueFraction));
    }
}
