package com.apogames.settler.level;

import com.apogames.settler.level.helper.Difficulty;
import com.apogames.settler.level.helper.FillHelp;
import com.apogames.settler.level.helper.Helper;
import com.badlogic.gdx.math.GridPoint2;

import java.util.ArrayList;
import java.util.HashSet;

public class Solve {

    private final byte[][] background;
    private byte[][] saveSpots;
    private byte[][] regionSize;

    private FillHelp[][] possibleValues;

    private final ArrayList<byte[][]> allSolutions = new ArrayList<>();

    private boolean onlyValueInRegion = true;

    private boolean withGuessing = false;
    private boolean finished = false;

    public Solve(byte[][] background) {
        this.background = Helper.cloneArray(background);

        this.init();
    }

    private void init() {
        this.saveSpots = new byte[background.length][background[0].length];
        this.regionSize = new byte[background.length][background[0].length];
        this.possibleValues = new FillHelp[background.length][background[0].length];
        for (byte y = 0; y < background.length; y++) {
            for (byte x = 0; x < background[0].length; x++) {
                this.regionSize[y][x] = (byte) getRegionSize(x, y, background[y][x], background, new boolean[background.length][background[0].length]);
                if (this.regionSize[y][x] == 0) {
                    this.regionSize[y][x] = 1;
                    this.saveSpots[y][x] = 1;
                }
            }
        }
        finished = false;
        withGuessing = false;
        this.setPossibleValues(this.saveSpots);
    }

    public void reset() {
        this.allSolutions.clear();
    }

    public byte[][] getRegionSize() {
        return regionSize;
    }

    public ArrayList<byte[][]> getAllSolutions() {
        return allSolutions;
    }

    public byte[][] getSaveSpots() {
        return saveSpots;
    }

    public FillHelp[][] getPossibleValues() {
        return possibleValues;
    }

    private byte getRegionSize(byte x, byte y, byte region, byte[][] background, boolean[][] visited) {
        byte result = 0;
        if (isOk((byte) (x + 1), y, region, background, visited)) {
            visited[y][x+1] = true;
            result += 1 + getRegionSize((byte) (x+1), y, region, background, visited);
        }
        if (isOk((byte) (x - 1), y, region, background, visited)) {
            visited[y][x-1] = true;
            result += 1 + getRegionSize((byte) (x-1), y, region, background, visited);
        }

        if (isOk(x, (byte) (y - 1), region, background, visited)) {
            visited[y - 1][x] = true;
            result += 1 + getRegionSize(x, (byte) (y - 1), region, background, visited);
        }

        if (isOk(x, (byte) (y + 1), region, background, visited)) {
            visited[y + 1][x] = true;
            result += 1 + getRegionSize(x, (byte) (y + 1), region, background, visited);
        }

        return result;
    }

    private boolean isOk(byte x, byte y, byte region, byte[][] background, boolean[][] visitedRegion) {
        if (x < 0 || x >= background[0].length || y < 0 || y >= background.length) {
            return false;
        }
        return background[y][x] == region && !visitedRegion[y][x];
    }

    public void setPossibleValues(byte[][] saveSpots) {
        setPossibleValues(saveSpots, false);
    }

    public void setPossibleValues(byte[][] saveSpots, boolean onlyForThisRegion) {
        for (int y = 0; y < background.length; y++) {
            for (int x = 0; x < background[0].length; x++) {
                if (this.possibleValues[y][x] == null) {
                    this.possibleValues[y][x] = new FillHelp(x, y);
                }
                if (saveSpots[y][x] > 0) {
                    this.possibleValues[y][x].setAllValuesTo(saveSpots[y][x]);
                } else {
                    this.possibleValues[y][x].addAllValuesTo(regionSize[y][x]);
                }
            }
        }
        removePossibilityForThisRegion(saveSpots);

        if (!onlyForThisRegion) {
            removePossibilityAround(saveSpots);
            for (byte y = 0; y < background.length; y++) {
                for (byte x = 0; x < background[0].length; x++) {
                    byte onlyValue = onlyValueInRegion(x, y, background[y][x], saveSpots);
                    if (onlyValue > 0) {
                        this.possibleValues[y][x].setAllValuesTo(onlyValue);
                    }
                }
            }
        }
    }

    private void removePossibilityForThisRegion(byte[][] saveSpots) {
        for (byte y = 0; y < background.length; y++) {
            for (byte x = 0; x < background[0].length; x++) {
                if (saveSpots[y][x] > 0) {
                    boolean[][] visited = new boolean[background.length][background[0].length];
                    visited[y][x] = true;
                    removeFromRegion(x, y, background[y][x], saveSpots[y][x], visited);
                }
            }
        }
    }

    private void removeFromRegion(byte x, byte y, byte region, Byte value, boolean[][] visited) {
        if (isOk((byte) (x + 1), y, region, background, visited)) {
            visited[y][x+1] = true;
            this.possibleValues[y][x+1].getPossibleValues().remove(value);
            removeFromRegion((byte) (x+1), y, region, value, visited);
        }
        if (isOk((byte) (x - 1), y, region, background, visited)) {
            visited[y][x-1] = true;
            this.possibleValues[y][x-1].getPossibleValues().remove(value);
            removeFromRegion((byte) (x-1), y, region, value, visited);
        }

        if (isOk(x, (byte) (y - 1), region, background, visited)) {
            visited[y - 1][x] = true;
            this.possibleValues[y-1][x].getPossibleValues().remove(value);
            removeFromRegion(x, (byte) (y-1), region, value, visited);
        }

        if (isOk(x, (byte) (y + 1), region, background, visited)) {
            visited[y + 1][x] = true;
            this.possibleValues[y+1][x].getPossibleValues().remove(value);
            removeFromRegion(x, (byte) (y+1), region, value, visited);
        }
    }

    private void removePossibilityAround(byte[][] saveSpots) {
        for (byte y = 0; y < background.length; y++) {
            for (byte x = 0; x < background[0].length; x++) {
                this.possibleValues[y][x].getPossibleValues().remove(removePossibilityAround((byte) (x-1), (byte) (y - 1), saveSpots));
                this.possibleValues[y][x].getPossibleValues().remove(removePossibilityAround(x, (byte) (y - 1), saveSpots));
                this.possibleValues[y][x].getPossibleValues().remove(removePossibilityAround((byte) (x+1), (byte) (y - 1), saveSpots));

                this.possibleValues[y][x].getPossibleValues().remove(removePossibilityAround((byte) (x-1), y, saveSpots));
                this.possibleValues[y][x].getPossibleValues().remove(removePossibilityAround((byte) (x+1), y, saveSpots));

                this.possibleValues[y][x].getPossibleValues().remove(removePossibilityAround((byte) (x-1), (byte) (y + 1), saveSpots));
                this.possibleValues[y][x].getPossibleValues().remove(removePossibilityAround(x, (byte) (y + 1), saveSpots));
                this.possibleValues[y][x].getPossibleValues().remove(removePossibilityAround((byte) (x+1), (byte) (y + 1), saveSpots));
            }
        }
    }

    private Byte removePossibilityAround(byte x, byte y, byte[][] saveSpots) {
        if (x < 0 || x >= background[0].length || y < 0 || y >= background.length) {
            return 0;
        }
        return saveSpots[y][x];
    }

    public void setSpotValues(byte[][] saveSpots) {
        for (int y = 0; y < saveSpots.length; y++) {
            for (int x = 0; x < saveSpots[0].length; x++) {
                this.saveSpots[y][x] = 0;
                if (saveSpots[y][x] > 0) {
                    this.saveSpots[y][x] = saveSpots[y][x];
                }
            }
        }
        this.setPossibleValues(this.saveSpots);
    }

    private void addSolution(byte[][] nextSolution) {
        for (byte[][] solution : this.allSolutions) {
            if (Helper.areEqual(solution, nextSolution)) {
                return;
            }
        }
        this.allSolutions.add(Helper.cloneArray(nextSolution));
    }

    public ArrayList<byte[][]> tryToSolveLevel(byte[][] saveSpots) {
        return tryToSolveLevel(saveSpots, 10000000);
    }

    public ArrayList<byte[][]> tryToSolveLevel(byte[][] saveSpots, int max) {
        return tryToSolveLevel(saveSpots, max, Difficulty.HARD);
    }

    public ArrayList<byte[][]> tryToSolveLevel(byte[][] saveSpots, int max, Difficulty difficulty) {
//        if (this.finished) {
//            return this.allSolutions;
//        }
        if (this.allSolutions.size() > max) {
            return this.allSolutions;
        }
        if (isSolved(saveSpots)) {
            this.saveSpots = Helper.cloneArray(saveSpots);
            this.addSolution(saveSpots);
            return this.allSolutions;
        }
        for (byte y = 0; y < saveSpots.length; y++) {
            for (byte x = 0; x < saveSpots[0].length; x++) {
                if (this.possibleValues[y][x].getPossibleValues().size() == 0) {
                    return this.allSolutions;
                }
                int onlyValue = onlyValueInRegion(x, y, background[y][x], saveSpots);
                if (saveSpots[y][x] == 0 && (this.possibleValues[y][x].getPossibleValues().size() == 1 || onlyValue > 0)) {
                    saveSpots[y][x] = (byte) (onlyValue > 0 ? onlyValue : this.possibleValues[y][x].getPossibleValues().get(0));
                    this.setPossibleValues(saveSpots);
                    return tryToSolveLevel(saveSpots, max, difficulty);
                }
            }
        }

        if (difficulty == Difficulty.EASY) {
            return this.allSolutions;
        }

        this.withGuessing = true;

        ArrayList<byte[][]> solutions = guessTheNumber(saveSpots, max, difficulty);
        for (byte[][] solution : solutions) {
            this.addSolution(solution);
        }

        return this.allSolutions;
    }

    private boolean isSolved(byte[][] saveSpots) {
        for (int y = 0; y < saveSpots.length; y++) {
            for (int x = 0; x < saveSpots[0].length; x++) {
                if (saveSpots[y][x] == 0) return false;
            }
        }
        return true;
    }

    private ArrayList<byte[][]> guessTheNumber(byte[][] saveSpots, int max, Difficulty difficulty) {
        GridPoint2 point = new GridPoint2(-1,-1);
        int min = 6;
        for (int y = 0; y < saveSpots.length; y++) {
            for (int x = 0; x < saveSpots[0].length; x++) {
                if (saveSpots[y][x] == 0 && this.possibleValues[y][x].getPossibleValues().size() > 1) {
                    if (this.possibleValues[y][x].getPossibleValues().size() < min) {
                        min = this.possibleValues[y][x].getPossibleValues().size();
                        point.x = x;
                        point.y = y;
                    }
                }
            }
        }
        if (point.x >= 0) {
            int x = point.x;
            int y = point.y;

            int random = (int)(Math.random() * 1000);

            for (int i = 0; i < this.possibleValues[y][x].getPossibleValues().size(); i++) {
                Byte value = this.possibleValues[y][x].getPossibleValues().get(i);
                this.setPossibleValues(saveSpots);

                ArrayList<byte[][]> checkNext = checkNextStepPossible(x, y, Helper.cloneArray(saveSpots), value, random, max, difficulty);
                if (checkNext.size() >= 1) {
                //if (isSolved(checkNext)) {
                    //System.out.println("LÖSUNG: "+x+" "+y+" "+checkNext.size());
                    //Helper.printArray(saveSpots);
                    for (byte[][] solution : checkNext) {
                        //saveSpots = solution;
                        //this.setPossibleValues(saveSpots);
                        //this.saveSpots = saveSpots;
                        this.addSolution(solution);
                        if (this.allSolutions.size() > max) {
                            return this.allSolutions;
                        }
                    }
                    // this.allSolutions;
                } else {
                    this.setPossibleValues(saveSpots);
                }
            }
        }
        return this.allSolutions;
    }



    private ArrayList<byte[][]> checkNextStepPossible(int xCheck, int yCheck, byte[][] saveSpots, Byte value, int random, int max, Difficulty difficulty) {
        Solve newSolve = new Solve(this.background);
        saveSpots[yCheck][xCheck] = value;
        newSolve.saveSpots = saveSpots;
        newSolve.setPossibleValues(saveSpots);
        newSolve.tryToSolveLevel(saveSpots, max, difficulty);

        return newSolve.allSolutions;
    }

    private byte onlyValueInRegion(byte x, byte y, byte region, byte[][] saveSpots) {
        if (this.possibleValues[y][x].getPossibleValues().size() > 1) {
            for (int i = 0; i < this.possibleValues[y][x].getPossibleValues().size(); i++) {
                byte value = this.possibleValues[y][x].getPossibleValues().get(i);
                boolean[][] visited = new boolean[background.length][background[0].length];
                visited[y][x] = true;
                onlyValueInRegion = true;
                onlyValueInRegion(x, y, region, value, saveSpots, visited);
                if (onlyValueInRegion) {
                    return value;
                }
            }
            return 0;
        }
        return 0;
    }

    private void onlyValueInRegion(byte x, byte y, byte region, Byte value, byte[][] saveSpots, boolean[][] visited) {
        if (!onlyValueInRegion) {
            return;
        }
        if (isOk((byte) (x + 1), y, region, background, visited)) {
            visited[y][x+1] = true;
            if (possibleValues[y][x+1].getPossibleValues().contains(value) || saveSpots[y][x+1] == value) {
                onlyValueInRegion = false;
            }
            onlyValueInRegion((byte) (x+1), y, region, value, saveSpots, visited);
        }
        if (isOk((byte) (x - 1), y, region, background, visited)) {
            visited[y][x-1] = true;
            if (possibleValues[y][x-1].getPossibleValues().contains(value) || saveSpots[y][x-1] == value) {
                onlyValueInRegion = false;
            }
            onlyValueInRegion((byte) (x-1), y, region, value, saveSpots, visited);
        }

        if (isOk(x, (byte) (y - 1), region, background, visited)) {
            visited[y - 1][x] = true;
            if (possibleValues[y-1][x].getPossibleValues().contains(value) || saveSpots[y-1][x] == value) {
                onlyValueInRegion = false;
            }
            onlyValueInRegion(x, (byte) (y-1), region, value, saveSpots, visited);
        }

        if (isOk(x, (byte) (y + 1), region, background, visited)) {
            visited[y + 1][x] = true;
            if (possibleValues[y+1][x].getPossibleValues().contains(value) || saveSpots[y+1][x] == value) {
                onlyValueInRegion = false;
            }
            onlyValueInRegion(x, (byte) (y+1), region, value, saveSpots, visited);
        }
    }
}
