package com.apogames.settler.game.tiwanaku;

public enum LevelSize {
    SUPERMINI(3,3,1,0),
    MINI(4,4,1,1),
    SMALL(5,5,3,3),
    MEDIUM(7,5,5,3),
    BIG(9,5,6,5),
    UBER(15,10,10,25),
    TITAN(20,12,16,40);
    //SUPERBIG(9,9,6,5);

    private final int x;
    private final int y;
    private final int fiveCount;
    private final int fourCount;

    LevelSize(int x, int y, int fiveCount, int fourCount) {
        this.x = x;
        this.y = y;
        this.fiveCount = fiveCount;
        this.fourCount = fourCount;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getFiveCount() {
        return fiveCount;
    }

    public int getFourCount() {
        return fourCount;
    }

    public LevelSize next(int add) {
        for (int i = 0; i < values().length; i++) {
            if (LevelSize.values()[i] == this) {
                i += add;
                if (i < 0) {
                    return LevelSize.values()[values().length - 1];
                } else if (i >= values().length) {
                    return LevelSize.values()[0];
                } else {
                    return LevelSize.values()[i];
                }
            }
        }
        return LevelSize.SMALL;
    }
}
