package com.apogames.settler.level.helper;

import com.apogames.settler.game.tiwanaku.LevelSize;

public enum Difficulty {
    EASY(0.35f),
    MEDIUM(0.3f),
    HARD(0.24f);

    final float value;

    Difficulty(float value) {
        this.value = value;
    }

    public float getValue() {
        return value;
    }

    public Difficulty next(int add) {
        for (int i = 0; i < values().length; i++) {
            if (Difficulty.values()[i] == this) {
                i += add;
                if (i < 0) {
                    return Difficulty.values()[values().length - 1];
                } else if (i >= values().length) {
                    return Difficulty.values()[0];
                } else {
                    return Difficulty.values()[i];
                }
            }
        }
        return Difficulty.HARD;
    }
}
