package com.apogames.settler.level;

public class LastSaveSpots {

    private final int[][] saveNumbersSpots;
    private final int[][] saveSpots;
    private int[][] solution;

    private final int solutions;
    private final int start;
    private final int max;

    private int valuesSet;

    public LastSaveSpots(int[][] saveSpots, int[][] saveNumbersSpots, int solutions, int start, int max) {
        this.saveNumbersSpots = saveNumbersSpots;
        this.saveSpots = saveSpots;
        this.solutions = solutions;
        this.start = start;
        this.max = max;
        this.valuesSet = 0;
    }

    public int[][] getSaveNumbersSpots() {
        return saveNumbersSpots;
    }

    public int[][] getSaveSpots() {
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

    public static int getAmountBiggerThanOne(int[][] saveSpots) {
        int value = 0;
        for (int y = 0; y < saveSpots.length; y++) {
            for (int x = 0; x < saveSpots[0].length; x++) {
                if (saveSpots[y][x] > 0) {
                    value += 1;
                }
            }
        }
        return value;
    }

    public int[][] getSolution() {
        return solution;
    }

    public void setSolution(int[][] solution) {
        this.solution = solution;
    }
}
