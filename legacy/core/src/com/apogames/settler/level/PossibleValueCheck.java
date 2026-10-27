package com.apogames.settler.level;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class PossibleValueCheck {
    private int[][] regionSize;

    private byte[][] myBackground;

    private int maxValue;

    private ArrayList<Byte>[][] possibleValues;

    public PossibleValueCheck(byte[][] myBackground, int[][] regionSize) {
        this.regionSize = regionSize;
        this.myBackground = myBackground;

        this.init();
    }

    public void init() {
        this.possibleValues = new ArrayList[this.myBackground.length][this.myBackground[0].length];
        for (int y = 0; y < myBackground.length; y++) {
            for (int x = 0; x < myBackground[0].length; x++) {
                if (this.myBackground[y][x] > this.maxValue) {
                    this.maxValue = this.myBackground[y][x];
                }
                this.possibleValues[y][x] = new ArrayList<>();
                addAllValuesTo(this.regionSize[y][x], x, y);
            }
        }
    }

    public boolean isSolveable() {
        for (int y = 0; y < myBackground.length; y++) {
            for (int x = 0; x < myBackground[0].length; x++) {
                if (this.regionSize[y][x] == 1) {
                    this.setAllValuesTo((byte) 1, x, y);
                } else if (this.possibleValues[y][x].size() == 1) {
                    this.setAllValuesTo(this.possibleValues[y][x].get(0), x, y);
                } else {
                    Set<Byte> check = new HashSet<>();
                    check.add(myBackground[y][x]);

                    for (int nextY = -1; nextY <= 1; nextY += 1) {
                        for (int nextX = -1; nextX <= 1; nextX += 1) {
                            if (nextX != 0 || nextY != 0) {
                                if (x + nextX < 0 || x + nextX >= myBackground[0].length || y + nextY < 0 || y + nextY >= myBackground.length) {
                                    continue;
                                }
                                if (check.contains(myBackground[y+nextY][x+nextX])) {
                                    continue;
                                }
                                check.add(myBackground[y+nextY][x+nextX]);
                                if (isCompletelySurrounded(myBackground[y+nextY][x+nextX], x, y)) {
                                    for (int i = 1; i <= this.regionSize[y+nextY][x+nextX]; i++) {
                                        Byte b = (byte)i;
                                        this.possibleValues[y][x].remove(b);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Set<Byte>[] regionValues = new HashSet[maxValue];
        for (int y = 0; y < myBackground.length; y++) {
            for (int x = 0; x < myBackground[0].length; x++) {
                for (int i = 0; i < this.possibleValues[y][x].size(); i++) {
                    if (regionValues[this.myBackground[y][x]-1] == null) {
                        regionValues[this.myBackground[y][x]-1] = new HashSet<>();
                    }
                    regionValues[this.myBackground[y][x]-1].add(this.possibleValues[y][x].get(i));
                }
            }
        }

        for (int y = 0; y < myBackground.length; y++) {
            for (int x = 0; x < myBackground[0].length; x++) {
                if (regionValues[this.myBackground[y][x]-1] == null || regionValues[this.myBackground[y][x]-1].size() != this.regionSize[y][x]) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean isCompletelySurrounded(byte value, int checkX, int checkY) {
        for (int y = 0; y < myBackground.length; y++) {
            for (int x = 0; x < myBackground[0].length; x++) {
                if (myBackground[y][x] == value) {
                    if (Math.abs(checkX - x) > 1 || Math.abs(checkY - y) > 1) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public void addAllValuesTo(int max, int x, int y) {
        this.possibleValues[y][x].clear();
        for (byte i = 1; i <= max; i++) {
            this.possibleValues[y][x].add(i);
        }
    }

    public void setAllValuesTo(byte value, int x, int y) {
        for (int myY = y - 1; myY <= y + 1; myY++) {
            for (int myX = x - 1; myX <= x + 1; myX++) {
                if (myX >= 0 && myX < this.myBackground[0].length &&
                    myY >= 0 && myY < this.myBackground.length) {
                    if (myX != x || myY != y) {
                        Byte b = value;
                        this.possibleValues[myY][myX].remove(b);
                    } else {
                        if (possibleValues[y][x].contains(value)) {
                            this.possibleValues[y][x].clear();
                            this.possibleValues[y][x].add(value);
                        } else {
                            this.possibleValues[y][x].clear();
                        }
                    }
                }
            }
        }
    }
}
