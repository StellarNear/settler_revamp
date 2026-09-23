package com.apogames.settler.level;

import com.apogames.settler.level.algorithmX.AlgorithmX;
import com.apogames.settler.level.algorithmX.AlgorithmXSolve;
import com.apogames.settler.level.famiru.App;
import com.apogames.settler.level.helper.Difficulty;
import com.apogames.settler.level.helper.Helper;
import com.apogames.settler.level.helper.LastSaveSpots;

import java.util.ArrayList;
import java.util.HashSet;

public class FillAndUniqueCheck {

    private final int MAX_CHECKS = 10;
    private byte[][] myBackground;

    private byte[][] regionSize;

    private ArrayList<byte[][]> solutions = new ArrayList<>();
    private HashSet<byte[][]> allSolutions = new HashSet<>();
    private byte[][][] allSolutionsArray;

    private byte[][] saveNumbersSpots;
    private byte[][] saveSpots;

    private LastSaveSpots lastSaveSpot;
    private LastSaveSpots bestSaveSpot;

    private boolean regionCheck = false;

    private boolean foundSolution = false;
    private boolean found = false;

    private int count = 0;

    private Level level;

    private AlgorithmX algorithmX = new AlgorithmX();

    private Difficulty difficulty;

    public FillAndUniqueCheck(Difficulty difficulty) {
        this.difficulty = difficulty;
        this.init();
    }

    public boolean fillLevel(byte[][] background) {
        return fillLevel(background, null);
    }

    public boolean fillLevel(byte[][] background, byte[][] generatedSolution) {
        if (found) {
            return true;
        }
        this.myBackground = Helper.cloneArray(background);

        byte[][] numbers = new byte[background.length][background[0].length];
        this.saveSpots = new byte[background.length][background[0].length];
        this.regionSize = new byte[background.length][background[0].length];
        for (int y = 0; y < background.length; y++) {
            for (int x = 0; x < background[0].length; x++) {
                this.regionSize[y][x] = getRegionSize(x, y, background[y][x], background, new boolean[background.length][background[0].length]);
                if (this.regionSize[y][x] > 5) {
                    return false;
                }
                if (this.regionSize[y][x] == 0) {
                    this.regionSize[y][x] = 1;
                    numbers[y][x] = 1;
                    if (!areNumbersValid(numbers, x, y)) {
                        return false;
                    }
                    this.saveSpots[y][x] = 1;
                }
            }
        }

//        System.out.println();
//        Helper.printArray(this.regionSize);
//
//        System.out.println();
//        System.out.println("Starte mit fillNumbers in das Level");
//        System.out.println();

        boolean countAllSolutions = false;
        int maxLevel = 1000000;
        if (this.myBackground.length * this.myBackground[0].length > 31) {
            maxLevel = 1;
        }

        //long startTime = System.nanoTime();
//        this.algorithmX.createMatrix(this.myBackground, numbers, this.regionSize);
//        AlgorithmXSolve solver = new AlgorithmXSolve();
//        ArrayList<byte[][]> solutionArray = solver.run(myBackground[0].length, myBackground.length, this.algorithmX.getMatrix(), maxLevel);

        ArrayList<byte[][]> solutionArray;
        if (generatedSolution == null) {
            App solver = new App(this.myBackground);
            solutionArray = solver.run(maxLevel, countAllSolutions);
        } else {
            if (generatedSolution.length != background.length) {
                return false;
            }
            for (int row = 0; row < background.length; row++) {
                if (generatedSolution[row].length != background[row].length) {
                    return false;
                }
            }
            for (int row = 0; row < background.length; row++) {
                for (int column = 0; column < background[row].length; column++) {
                    if (generatedSolution[row][column] < 1
                            || generatedSolution[row][column] > this.regionSize[row][column]
                            || !areNumbersValid(generatedSolution, column, row)) {
                        return false;
                    }
                }
            }
            isRegionValid(generatedSolution);
            if (!this.regionCheck) {
                return false;
            }
            solutionArray = new ArrayList<>();
            solutionArray.add(Helper.cloneArray(generatedSolution));
        }

        //long endTime = System.nanoTime();
        foundSolution = !solutionArray.isEmpty();
        this.solutions = solutionArray;
        //System.out.println("Solutions found: "+this.solutions.size()+" in "+(endTime-startTime)+" ns");

//        this.solutions.clear();
//        byte[][] startLevel = Helper.cloneArray(numbers);
//        startTime = System.nanoTime();
//        Solve solve = new Solve(this.myBackground);
//        solve.setPossibleValues(startLevel);
//        this.solutions = solve.tryToSolveLevel(startLevel, 1, this.difficulty);
//        endTime = System.nanoTime();
//        System.out.println("Solve Solutions found: "+this.solutions.size()+" in "+(endTime-startTime)+" ns");
//        this.solutions.clear();
//        startTime = System.nanoTime();
//        byte start = (byte)(Math.random() * 5);
//        for (byte number = 1; number < 6; number += 1) {
//            byte value = (byte) (start + number);
//            if (value > 5) {
//                value -= 5;
//            }
//            this.fillNumbers(0, 0, numbers, value);
//        }
//        endTime = System.nanoTime();
//        System.out.println("oldschool solutions found: "+solutions.size()+" in "+(endTime-startTime)+" ns");

        if (!foundSolution) {
            System.out.println("Keine Lösung gefunden");
            //Helper.printArray(this.myBackground);
            //System.out.println();
//            System.out.println();
            return false;
        } else {
//            System.out.println();
            System.out.println("Found: "+this.solutions.size());
//            System.out.println();

            //Helper.printArray(this.myBackground);
            this.allSolutions = new HashSet<>(this.solutions);
            this.allSolutionsArray = new byte[this.allSolutions.size()][][];
            //System.out.println("allSolutions: "+allSolutions.size());
            int index = 0;
            for (byte[][] solutions : this.allSolutions) {
                this.allSolutionsArray[index] = solutions;
                index += 1;
            }
            this.count = 0;

            this.createUniqueLevelNew();

//            long start = System.nanoTime();
//            createUniqueLevel();
//            long end = System.nanoTime();
//            System.out.println("DAUER "+(end-start)+" ns");
        }
        return true;
    }

    private void putInValue(byte[][] level, boolean[][] visited) {
        int randomX = -1;
        int randomY = -1;
        while (randomX < 0) {
            randomX = (int)(Math.random() * level[0].length);
            randomY = (int)(Math.random() * level.length);
            if (isNeighborFree(randomX, randomY, visited)) {
                level[randomY][randomX] = (byte)(Math.random() * this.regionSize[randomY][randomX]);
                break;
            }
        }
    }

    private boolean isNeighborFree(int x, int y, boolean[][] visited) {
        if (!isFieldOk(x - 1, y - 1, visited) ||
             !isFieldOk(x, y - 1, visited) ||
             !isFieldOk(x + 1, y - 1, visited) ||
                !isFieldOk(x - 1, y, visited) ||
                !isFieldOk(x + 1, y, visited) ||
                !isFieldOk(x - 1, y + 1, visited) ||
                !isFieldOk(x, y + 1, visited) ||
                !isFieldOk(x + 1, y + 1, visited)) {
            return false;
        }
        for (int i = y - 1; i < y + 2; i++) {
            for (int j = x - 1; j < x + 2; j++) {
                if (j < 0 || j >= visited[0].length || i < 0 || i >= visited.length) {
                    continue;
                }
                visited[i][j] = true;
            }
        }
        return true;
    }

    private boolean isFieldOk(int x, int y, boolean[][] visited) {
        if (x < 0 || x >= visited[0].length || y < 0 || y >= visited.length) {
            return true;
        }
        return !visited[y][x];
    }

    private void createUniqueLevelNew() {

        //long start = System.nanoTime();
        byte[][] realSolution = this.solutions.get((int) (Math.random() * this.solutions.size()));
        byte[][] startLevel;
        int counter = 1;
        byte[][] startLevelBest = null;
        int min = 100000;
        int max = (int)(this.difficulty.getValue() * myBackground.length * myBackground[0].length);
        // Large levels (Uber/Titan) are expensive to generate; don't spend time
        // trying to hit the exact clue count over and over again.
        int maxAttempts = (myBackground.length * myBackground[0].length > 45) ? 2 : 10;
        do {
            UniqueSolution solution = new UniqueSolution();
            solution.setValues(this.myBackground, realSolution);
            startLevel = solution.getStartLevel(this.difficulty);
            counter += 1;
            int cur = getSolvedLevelOk(startLevel);
            if (cur < min || startLevelBest == null) {
                min = cur;
                startLevelBest = startLevel;
            }
        } while (min != max && counter < maxAttempts);
        //long end = System.nanoTime();

        //System.out.println("LÖSUNG in "+(end-start)+" ns");

//        System.out.println();
//        Helper.printArray(this.myBackground);
//        System.out.println();
//        Helper.printArray(this.regionSize);
//        System.out.println();
//        Helper.printArray(realSolution);
//        System.out.println();
//        Helper.printArray(startLevel);

        this.level = new Level(Helper.cloneArray(this.myBackground), Helper.cloneArray(realSolution), Helper.cloneArray(startLevelBest));
        this.level.setRegion(Helper.cloneArray(this.regionSize));
    }

    private int getSolvedLevelOk(byte[][] startLevel) {
        int valueCount = 0;
        for (int y = 0; y < startLevel.length; y++) {
            for (int x = 0; x < startLevel[0].length; x++) {
                if (startLevel[y][x] > 0) {
                    valueCount += 1;
                }
            }
        }
        return valueCount;
    }

    private void createUniqueLevel() {
        this.saveSpots = new byte[this.myBackground.length][this.myBackground[0].length];
        this.saveNumbersSpots = new byte[this.myBackground.length][this.myBackground[0].length];
        for (int y = 0; y < myBackground.length; y++) {
            for (int x = 0; x < myBackground[0].length; x++) {
                if (this.regionSize[y][x] == 0) {
                    this.regionSize[y][x] = 1;
                    this.saveNumbersSpots[y][x] = 1;
                }
            }
        }

        int max = this.myBackground.length * this.myBackground[0].length / 5;
        findAndPrintSolution(0, max);

        if (isLevelOk() || this.count > MAX_CHECKS) {
//            System.out.println();
//            Helper.printArray(this.myBackground);
//            System.out.println();
//            Helper.printArray(this.regionSize);
//            System.out.println();
//            Helper.printArray(this.bestSaveSpot.getSolution());
//            System.out.println();
//            Helper.printArray(this.bestSaveSpot.getSaveSpots());

            //long startTime = System.nanoTime();
            Solve solve = new Solve(this.myBackground);
            byte[][] saveSpotsSolve = Helper.cloneArray(this.bestSaveSpot.getSaveSpots());
            solve.setPossibleValues(saveSpotsSolve);
            ArrayList<byte[][]> solverSolution = solve.tryToSolveLevel(saveSpotsSolve);
            //long endTime = System.nanoTime();

            //System.out.println((endTime - startTime));
//            Helper.printArray(solverSolution.get(0));

            this.level = new Level(Helper.cloneArray(this.myBackground), Helper.cloneArray(this.bestSaveSpot.getSolution()), Helper.cloneArray(this.bestSaveSpot.getSaveSpots()));
            this.level.setRegion(Helper.cloneArray(solve.getRegionSize()));
            found = true;
        } else {
            //System.out.println("Zu viele Lösungen, muss neue Suchen");
            this.count += 1;
            createUniqueLevel();
            //createUniqueLevel(new ArrayList<>(this.allSolutions).get((int)(Math.random() * this.allSolutions.size())));
        }
    }

    public Level getLevel() {
        return level;
    }

    private boolean isLevelOk() {
        int counter = 0;
        int max = (int)((saveSpots.length * saveSpots[0].length) * 0.24f);
        for (int y = 0; y < saveSpots.length; y++) {
            for (int x = 0; x < saveSpots[0].length; x++) {
                if (this.saveSpots[y][x] > 0) {
                    counter += 1;

                    if (counter > max) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private void findAndPrintSolution(int start, int max) {
        int solution = (int)(Math.random() * this.solutions.size());
        findAndPrintSolution(start, max, this.solutions.get(solution));
    }

    private void findAndPrintSolution(int start, int max, byte[][] solution) {
        this.lastSaveSpot = new LastSaveSpots(Helper.cloneArray(this.saveSpots), Helper.cloneArray(this.saveNumbersSpots), this.solutions.size(), start, max);

        int count = start;
        int counter = 0;
        while (count < max && counter < 100) {
            counter += 1;
            int x = (int)(Math.random() * this.myBackground[0].length);
            int y = (int)(Math.random() * this.myBackground.length);
            if (this.saveSpots[y][x] <= 0) {
                count += 1;
                this.saveSpots[y][x] = solution[y][x];
                this.saveNumbersSpots[y][x] = this.saveSpots[y][x];
            }
        }

        this.solutions.clear();
        this.foundSolution = false;

//        byte[][] numbers = Helper.cloneArray(this.saveNumbersSpots);
//        //long startTime = System.nanoTime();
//        this.algorithmX.createMatrix(this.myBackground, numbers, this.regionSize);
//        AlgorithmXSolve solver = new AlgorithmXSolve();
//        this.solutions = solver.run(myBackground[0].length, myBackground.length, this.algorithmX.getMatrix());
//        //long endTime = System.nanoTime();
//        foundSolution = solutions.size() != 0;
//        //System.out.println("unique Solutions found: "+solutionArray.size()+" in "+(endTime-startTime)+" ns");
//        if (this.solutions.size() == 0) {
//            Helper.printArray(myBackground);
//            System.out.println();
//            Helper.printArray(numbers);
//        }

//        this.solutions.clear();
//        long timeFillStart = System.nanoTime();
        byte[][] numbers = Helper.cloneArray(this.saveNumbersSpots);
        byte startRandom = (byte)(Math.random() * 5);
        for (byte number = 1; number < 6; number += 1) {
            byte value = (byte) (startRandom + number);
            if (value > 5) {
                value -= 5;
            }
            this.fillNumbers(0, 0, numbers, value);
        }
//        long timeFillEnd = System.nanoTime();
//        System.out.println("this.solutions= "+this.solutions.size());
//        System.out.println();

//        long timeSolveStart = System.nanoTime();
//        Solve solve = new Solve(myBackground);
//        solve.tryToSolveLevel(Helper.cloneArray(this.saveNumbersSpots));
//        long timeSolveEnd = System.nanoTime();
//
//        System.out.println((timeFillEnd-timeFillStart)+" zu solve "+(timeSolveEnd - timeSolveStart)+" and solutions: "+this.solutions.size()+" zu "+solve.getAllSolutions().size());
//
//        if (this.solutions.size() != solve.getAllSolutions().size()) {
//            System.out.println();
//        }

        if (this.solutions.size() != 1) {
            if (this.bestSaveSpot != null && this.bestSaveSpot.getValuesSet() <= LastSaveSpots.getAmountBiggerThanOne(this.saveSpots)) {
                return;
            }
            //System.out.println("uff mehr "+this.solutions.size());
            if (this.lastSaveSpot.getSolutions() == this.solutions.size() || this.solutions.size() == 0) {
                this.saveSpots = this.lastSaveSpot.getSaveSpots();
                this.saveNumbersSpots = this.lastSaveSpot.getSaveNumbersSpots();
                this.findAndPrintSolution(this.lastSaveSpot.getStart(), this.lastSaveSpot.getMax());
            } else {
                this.findAndPrintSolution(start, start + 1);
            }
        } else {
            LastSaveSpots bestSpot = new LastSaveSpots(Helper.cloneArray(this.saveSpots), Helper.cloneArray(this.saveNumbersSpots), this.solutions.size(), 0, 0);
            if (this.bestSaveSpot == null || this.bestSaveSpot.getValuesSet() > bestSpot.getValuesSet()) {
                this.bestSaveSpot = bestSpot;
                this.bestSaveSpot.setSolution(Helper.cloneArray(this.solutions.get(0)));
            }
        }
    }

    private void init() {
        this.foundSolution = false;
    }

    private void fillNumbers(int x, int y, byte[][] numbersClone, byte value) {
        if (this.foundSolution) {
            //return;
        }

        if (x < 0 || x >= numbersClone[0].length || y < 0 || y >= numbersClone.length) {
            return;
        }

        if (this.saveSpots[y][x] > 0 && value != this.saveSpots[y][x]) {
            return;
        }

        if (value > this.regionSize[y][x]) {
            return;
        }

        byte[][] numbers = Helper.cloneArray(numbersClone);
        numbers[y][x] = value;

        this.regionCheck = true;
        this.isRegionValid(numbers);
        boolean validNumbers = this.areNumbersValid(numbers, x, y);
        if (!this.regionCheck || !validNumbers) {
            numbers[y][x] = 0;
            return;
        }

        int nextX = x + 1;
        int nextY = y;
        if (nextX >= this.myBackground[0].length) {
            nextX = 0;
            nextY += 1;
            if (nextY >= this.myBackground.length) {
                this.foundSolution = true;
                this.solutions.add(Helper.cloneArray(numbers));
                //if (this.solutions.size() == 1 || this.solutions.size() % 1000000 == 0) {
                    //System.out.println("solutions: " + this.solutions.size());
                    //Helper.printArray(this.solutions.get(this.solutions.size() - 1));
                //}
                return;
            }
        }

        byte start = (byte)(Math.random() * 5);
        for (byte number = 1; number < 6; number += 1) {
            byte valueR = (byte) (start + number);
            if (valueR > 5) {
                valueR -= 5;
            }
            fillNumbers(nextX, nextY, numbers, valueR);
        }

        numbers[y][x] = 0;
    }

    private byte getRegionSize(int x, int y, int region, byte[][] background, boolean[][] visited) {
        byte result = 0;
        if (isOk(x + 1, y, region, background, visited)) {
            visited[y][x+1] = true;
            result += 1 + getRegionSize(x+1, y, region, background, visited);
        }
        if (isOk(x - 1, y, region, background, visited)) {
            visited[y][x-1] = true;
            result += 1 + getRegionSize(x-1, y, region, background, visited);
        }

        if (isOk(x, y - 1, region, background, visited)) {
            visited[y - 1][x] = true;
            result += 1 + getRegionSize(x, y - 1, region, background, visited);
        }

        if (isOk(x, y + 1, region, background, visited)) {
            visited[y + 1][x] = true;
            result += 1 + getRegionSize(x, y + 1, region, background, visited);
        }

        return result;
    }

    private void isRegionValid(byte[][] numbers) {
        this.regionCheck = true;
        boolean[][] visited = new boolean[myBackground.length][myBackground[0].length];
        ArrayList<Byte> values = new ArrayList<>();
        for (int y = 0; y < myBackground.length; y++) {
            for (int x = 0; x < myBackground[0].length; x++) {
                int region = myBackground[y][x];
                if (!visited[y][x]) {
                    //isRegionValid(x, y, region, new boolean[this.myBackground.length][this.myBackground[0].length], numbers, new ArrayList<Integer>());
                    values.clear();
                    isRegionValid(x, y, region, visited, numbers, values);

                    if (!this.regionCheck) {
                        return;
                    }
                }
            }
        }
    }

    private void isRegionValid(int x, int y, int region, boolean[][] visitedRegion, byte[][] numbers, ArrayList<Byte> values) {
        if (values.contains(numbers[y][x]) && !visitedRegion[y][x]) {
            this.regionCheck = false;
            return;
        }

        if (numbers[y][x] != 0) {
            values.add(numbers[y][x]);
        }
        visitedRegion[y][x] = true;

        checkRegion(x+1, y, region, visitedRegion, numbers, values);
        checkRegion(x-1, y, region, visitedRegion, numbers, values);
        checkRegion(x, y+1, region, visitedRegion, numbers, values);
        checkRegion(x, y-1, region, visitedRegion, numbers, values);
    }

    private void checkRegion(int x, int y, int region, boolean[][] visitedRegion, byte[][] numbers, ArrayList<Byte> values) {
        if (isOk(x, y, region, this.myBackground, visitedRegion)) {
            isRegionValid(x, y, region, visitedRegion, numbers, values);
        }
    }

    private boolean isOk(int x, int y, int region, byte[][] background, boolean[][] visitedRegion) {
        if (x < 0 || x >= background[0].length || y < 0 || y >= background.length) {
            return false;
        }
        return background[y][x] == region && !visitedRegion[y][x];
    }

    private boolean areNumbersValid(byte[][] numbers, int x, int y) {
//        for (int y = 0; y < myBackground.length; y++) {
//            for (int x = 0; x < myBackground[0].length; x++) {
        //numbersVisited[y][x] = true;
        if (numbers[y][x] == 0) {
            return true;
        }
        if (!Helper.canPlaceValue(x, y, numbers[y][x], numbers)) {
            return false;
        }
//            }
//        }
        return true;
    }

}
