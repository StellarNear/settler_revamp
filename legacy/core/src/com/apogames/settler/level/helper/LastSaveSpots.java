package com.apogames.settler.level.helper;

public class LastSaveSpots {

    private final byte[][] saveNumbersSpots;
    private final byte[][] saveSpots;
    private byte[][] solution;

    private final int solutions;
    private final int start;
    private final int max;

    private int valuesSet;

    public LastSaveSpots(byte[][] saveSpots, byte[][] saveNumbersSpots, int solutions, int start, int max) {
        this.saveNumbersSpots = saveNumbersSpots;
        this.saveSpots = saveSpots;
        this.solutions = solutions;
        this.start = start;
        this.max = max;
        this.valuesSet = 0;
    }

    public byte[][] getSaveNumbersSpots() {
        return saveNumbersSpots;
    }

    public byte[][] getSaveSpots() {
        return saveSpots;
    }

    public int getSolutions() {
        return solutions;
    }

    public int getStart() {
        return start;
    }

    public int getMax() {
        return max;
    }

    public int getValuesSet() {
        if (this.valuesSet == 0) {
            this.valuesSet = getAmountBiggerThanOne(this.saveSpots);
        }
        return this.valuesSet;
    }

    public static byte getAmountBiggerThanOne(byte[][] saveSpots) {
        byte value = 0;
        for (byte y = 0; y < saveSpots.length; y++) {
            for (byte x = 0; x < saveSpots[0].length; x++) {
                if (saveSpots[y][x] > 0) {
                    value += 1;
                }
            }
        }
        return value;
    }

    public byte[][] getSolution() {
        return solution;
    }

    public void setSolution(byte[][] solution) {
        this.solution = solution;
    }
}
