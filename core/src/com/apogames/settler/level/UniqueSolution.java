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
        this.found = false;
        this.difficulty = difficulty;
        //System.out.println(this.difficulty);
        int totalCells = this.background.length * this.background[0].length;
        int deleteValues = totalCells / 2;
        // For large levels (Uber/Titan) removing half the cells at once makes the
        // uniqueness check recurse through too many expensive solve attempts.
        // Start with a smaller chunk so generation finishes quickly.
        if (totalCells > 45) {
            deleteValues = Math.max(1, totalCells / 8);
        }
        return getStartLevel(deleteValues, 0, this.solution, new byte[this.background.length][this.background[0].length]);
    }

    public byte[][] getStartLevel(int deleteValues, int placedValues, byte[][] solution, byte[][] blacklist) {
        if (found) {
            return solution;
        }
        byte[][] orgSolution = Helper.cloneArray(solution);
        int orgPlacedValues = placedValues;

        if (placedValues > 0 && placedValues >= (1 - this.difficulty.getValue()) * background.length * background[0].length) {
            return solution;
        }
        if (deleteValues == 1 && canNotPlaceMore(solution, blacklist)) {
            return solution;
        }
        int randomX = -1;
        int randomY = -1;
        for (int i = 0; i < deleteValues; i++) {
            do {
                randomX = (int)(Math.random() * solution[0].length);
                randomY = (int)(Math.random() * solution.length);
            } while (blacklist[randomY][randomX] == 1 || solution[randomY][randomX] == 0);

            placedValues += 1;
            blacklist[randomY][randomX] = 1;
            solution[randomY][randomX] = 0;
        }

        //this.solve = new Solve(this.background);
        byte[][] saveSpots = Helper.cloneArray(solution);
        this.solve.reset();
        this.solve.setPossibleValues(saveSpots);
        ArrayList<byte[][]> solutions = this.solve.tryToSolveLevel(saveSpots, 1, this.difficulty);
        if (solutions.size() != 1) {
            if (deleteValues > 1) {
                deleteValues = Math.max(1, deleteValues / 2);
                blacklist = new byte[this.background.length][this.background[0].length];
            }
            return getStartLevel(deleteValues, orgPlacedValues, orgSolution, blacklist);
        } else {
            return getStartLevel(1, placedValues, solution, new byte[this.background.length][this.background[0].length]);
        }
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
