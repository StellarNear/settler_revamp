package com.apogames.settler.level;

import com.apogames.settler.level.helper.Difficulty;
import com.apogames.settler.level.helper.Helper;

import java.util.ArrayList;

public class UniqueSolution {

    private byte[][] background;

    private byte[][] solution;

    private Difficulty difficulty;

    private Solve solve;

    private boolean found = false;
    private byte[][] workingSolution;
    private byte[][] blacklist;
    private int deleteValues;
    private int removedValues;
    private int completedChecks;

    public static void main(String[] args) {
        UniqueSolution uniqueSolution = new UniqueSolution();

//        byte[][] background = new byte[][] {
//                {1, 1, 3, 4, 4},
//                {1, 1, 1, 4, 4},
//                {2, 2, 2, 2, 4},
//                {4, 4, 4, 1, 1},
//                {3, 4, 4, 1, 1},
//        };
//
//        byte[][] solution = new byte[][] {
//                {2, 5, 1, 3, 5},
//                {1, 3, 4, 2, 1},
//                {4, 2, 1, 3, 4},
//                {3, 5, 4, 2, 1},
//                {1, 2, 1, 3, 4},
//        };
        byte[][] background = new byte[][] {
                {4, 4, 4, 2, 2, 4, 3, 1, 2},
                {3, 4, 4, 2, 2, 2, 3, 1, 1},
                {3, 2, 1, 1, 3, 4, 3, 3, 1},
                {3, 2, 2, 1, 1, 4, 2, 2, 1},
                {3, 2, 2, 4, 4, 4, 2, 2, 2},
        };

        byte[][] solution = new byte[][] {
                {1, 3, 4, 1, 4, 1, 2, 3, 1},
                {2, 5, 2, 3, 2, 5, 4, 5, 2},
                {1, 4, 1, 4, 1, 3, 1, 3, 1},
                {3, 2, 3, 2, 3, 5, 4, 2, 4},
                {4, 1, 5, 4, 1, 2, 1, 3, 5},
        };


        uniqueSolution.setValues(background, solution);
        byte[][] startLevel = uniqueSolution.getStartLevel(Difficulty.HARD);
        Helper.printArray(startLevel);
    }

    public UniqueSolution() {

    }

    public void setValues(byte[][] background, byte[][] solution) {
        this.background = Helper.cloneArray(background);
        this.solution = Helper.cloneArray(solution);
        this.solve = new Solve(this.background);

//        Helper.printArray(this.background);
//        System.out.println();
//        Helper.printArray(this.solution);
//        System.out.println();
    }

    public byte[][] getStartLevel(Difficulty difficulty) {
        beginRemoval(difficulty);
        while (!stepRemoval()) {
        }
        return this.workingSolution;
    }

    public void beginRemoval(Difficulty difficulty) {
        this.found = false;
        this.difficulty = difficulty;
        int totalCells = this.background.length * this.background[0].length;
        this.deleteValues = Math.max(1, totalCells > 45 ? totalCells / 8 : totalCells / 2);
        this.removedValues = 0;
        this.completedChecks = 0;
        this.workingSolution = Helper.cloneArray(this.solution);
        this.blacklist = new byte[this.background.length][this.background[0].length];
    }

    public byte[][] getStartLevel(int deleteValues, int placedValues, byte[][] solution, byte[][] blacklist) {
        this.deleteValues = Math.max(1, deleteValues);
        this.removedValues = placedValues;
        this.workingSolution = Helper.cloneArray(solution);
        this.blacklist = Helper.cloneArray(blacklist);
        this.completedChecks = 0;
        while (!stepRemoval()) {
        }
        return this.workingSolution;
    }

    public boolean stepRemoval() {
        if (this.found) {
            return true;
        }
        int totalCells = this.background.length * this.background[0].length;
        if (this.removedValues >= (1 - this.difficulty.getValue()) * totalCells
                || canNotPlaceMore(this.workingSolution, this.blacklist)) {
            this.found = true;
            return true;
        }
        ArrayList<Integer> candidates = new ArrayList<>();
        int width = this.background[0].length;
        for (int row = 0; row < this.background.length; row++) {
            for (int column = 0; column < width; column++) {
                if (this.workingSolution[row][column] != 0 && this.blacklist[row][column] == 0) {
                    candidates.add(row * width + column);
                }
            }
        }
        byte[][] candidate = Helper.cloneArray(this.workingSolution);
        int removalCount = Math.min(this.deleteValues, candidates.size());
        for (int index = 0; index < removalCount; index++) {
            int cell = candidates.remove((int) (Math.random() * candidates.size()));
            this.blacklist[cell / width][cell % width] = 1;
            candidate[cell / width][cell % width] = 0;
        }
        byte[][] saveSpots = Helper.cloneArray(candidate);
        this.solve.reset();
        this.solve.setPossibleValues(saveSpots);
        ArrayList<byte[][]> solutions = this.solve.tryToSolveLevel(saveSpots, 1, this.difficulty);
        this.completedChecks += 1;
        if (solutions.size() != 1) {
            if (this.deleteValues > 1) {
                this.deleteValues = Math.max(1, this.deleteValues / 2);
                this.blacklist = new byte[this.background.length][width];
            }
        } else {
            this.workingSolution = candidate;
            this.removedValues += removalCount;
            this.deleteValues = 1;
            this.blacklist = new byte[this.background.length][width];
        }
        return false;
    }

    public byte[][] getCurrentStartLevel() {
        return this.workingSolution;
    }

    public int getCompletedChecks() {
        return this.completedChecks;
    }

    public int getRemovedValues() {
        return this.removedValues;
    }

    private boolean canNotPlaceMore(byte[][] solution, byte[][] blacklist) {
        for (int y = 0; y < solution.length; y++) {
            for (int x = 0; x < solution[0].length; x++) {
                if (solution[y][x] != 0 && blacklist[y][x] == 0) {
                    return false;
                }
            }
        }
        return true;
    }

}
