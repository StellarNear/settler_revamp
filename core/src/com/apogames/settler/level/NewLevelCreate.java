package com.apogames.settler.level;

import com.apogames.settler.level.helper.Difficulty;
import com.apogames.settler.level.helper.Helper;
import com.badlogic.gdx.math.GridPoint2;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class NewLevelCreate {

    // Randomized but always valid solution for the 2x2 block area. The backtracking
    // solver places one block permutation at a time and only keeps placements that
    // keep the whole board a legal king-graph colouring, so the produced solution
    // never needs to be rejected by FillAndUniqueCheck (which would restart the
    // whole generation and look like a freeze). The budget and the restart count
    // keep the solver bounded; the deterministic pattern is the last resort.
    private static final int SOLUTION_PLACEMENT_BUDGET = 2000;
    private static final int SOLUTION_RESTARTS = 6;

    // Upper limit for the recursive biome colouring of a single attempt. It runs
    // on the render thread, so a pathological board must not be able to block it.
    private static final long BIOME_COLORING_TIMEOUT_MS = 2500;

    // All 24 permutations of {1,2,3,4}, used as the candidates for a 2x2 block.
    private static final byte[][] BLOCK_PERMUTATIONS = createBlockPermutations();

    private byte[][] myBackground;
    private byte[][] generatedSolution;

    private boolean found = false;
    private boolean finished = false;
    private Level level;

    private Difficulty difficulty;
    private final int maxRegionSize;
    private FillAndUniqueCheck pendingFill;

    // Deadline for the recursive biome colouring. It runs on the render thread,
    // so a pathological board must never be able to block it indefinitely.
    private long biomeColoringDeadline;

    // State for the step-based generation API (see beginGeneration/stepGeneration),
    // which lets a caller spread attempts across multiple frames instead of
    // blocking the render thread until a level is found.
    private int genXSize;
    private int genYSize;
    private int genFiveCount;
    private int genFourCount;
    private int genMaxRetries;
    private int genAttemptCount;
    private boolean genActive;
    private boolean genSuccess;
    private boolean genFailed;
    private boolean genBackgroundReady;
    private int[][] regionSize;

    public static void main(String[] args) {
//        NewLevelCreate levelCreate = new NewLevelCreate(Difficulty.HARD);
//        long t = System.nanoTime();
//        for (int i = 0; i < 100; i++) {
//            //levelCreate.createLevel(5, 5, 9, 7);
//            levelCreate.createLevel(7, 5, 15, 15);
//        }
//        System.out.println(System.nanoTime()-t);
//        System.out.println();
//
//        LevelCreate oldLevelCreate = new LevelCreate(Difficulty.HARD);
//        t = System.nanoTime();
//        for (int i = 0; i < 100; i++) {
//            //oldLevelCreate.createLevel(5, 5, 9, 7);
//            oldLevelCreate.createLevel(7, 5, 15, 15);
//        }
//        System.out.println(System.nanoTime()-t);
    }

    public NewLevelCreate(Difficulty difficulty) {
        this(difficulty, 5);
    }

    public NewLevelCreate(Difficulty difficulty, int maxRegionSize) {
        if (maxRegionSize < 5 || maxRegionSize > 9) {
            throw new IllegalArgumentException("Maximum region size must be between 5 and 9");
        }
        this.maxRegionSize = maxRegionSize;
        this.difficulty = difficulty;
    }

    private void init() {
        this.finished = false;
        this.found = false;
    }

    public Level getLevel() {
        return level;
    }

    public void createLevel(int xSize, int ySize, int fiveCount, int fourCount) {
        this.init();
        // Large boards (Uber/Titan) need more attempts than small ones, but the
        // retry count must stay bounded or this call never returns (UI freeze).
        int maxRetries = xSize * ySize > 45 ? 300 : 50;
        for (int retry = 0; retry < maxRetries; retry++) {
            //long t = System.nanoTime();
            if (!this.setBackground(xSize, ySize, fiveCount, fourCount)) {
                continue;
            }
            //System.out.println("Nur Erstellung des Levels: "+(System.nanoTime()-t)+" ns");
            //Helper.printArray(this.myBackground);
            //System.out.println();

            FillAndUniqueCheck fill = new FillAndUniqueCheck(this.difficulty, this.maxRegionSize);
            if (fill.fillLevel(myBackground, this.generatedSolution)) {
                this.level = fill.getLevel();
                this.finished = true;
                //System.out.println("FOUND");
                return;
            }
        }
        throw new IllegalStateException("Unable to generate level " + xSize + "x" + ySize);
    }

    /**
     * Start a new level generation that proceeds one (or a few) attempt(s) at a
     * time via {@link #stepGeneration(int)}, instead of blocking until finished.
     */
    public void beginGeneration(int xSize, int ySize, int fiveCount, int fourCount) {
        this.init();
        this.genXSize = xSize;
        this.genYSize = ySize;
        this.genFiveCount = fiveCount;
        this.genFourCount = fourCount;
        this.genMaxRetries = xSize * ySize > 45 ? 300 : 50;
        this.genAttemptCount = 0;
        this.genActive = true;
        this.genSuccess = false;
        this.genFailed = false;
        this.genBackgroundReady = false;
        this.pendingFill = null;
    }

    /**
    * Perform up to {@code attemptsThisStep} generation steps.
     *
     * @return true once generation has finished, either successfully or because
     *         the retry budget was exhausted (see {@link #isGenerationSuccessful()}).
     */
    public boolean stepGeneration(int attemptsThisStep) {
        if (!this.genActive) {
            return true;
        }
        for (int i = 0; i < attemptsThisStep && (this.pendingFill != null || this.genBackgroundReady || this.genAttemptCount < this.genMaxRetries); i++) {
            if (this.pendingFill != null) {
                if (!this.pendingFill.stepFill()) {
                    return false;
                }
                this.level = this.pendingFill.getLevel();
                this.finished = true;
                this.genSuccess = true;
                this.genActive = false;
                return true;
            }
            if (!this.genBackgroundReady) {
                this.genAttemptCount += 1;
                if (!this.setBackground(this.genXSize, this.genYSize, this.genFiveCount, this.genFourCount)) {
                    continue;
                }
                this.genBackgroundReady = true;
                return false;
            }

            FillAndUniqueCheck fill = new FillAndUniqueCheck(this.difficulty, this.maxRegionSize);
            this.genBackgroundReady = false;
            if (fill.beginFill(myBackground, this.generatedSolution)) {
                this.pendingFill = fill;
                return false;
            }
        }
        if (this.genAttemptCount >= this.genMaxRetries) {
            this.genFailed = true;
            this.genActive = false;
            return true;
        }
        return false;
    }

    public boolean isGenerating() {
        return this.genActive;
    }

    public boolean isGenerationSuccessful() {
        return this.genSuccess;
    }

    public boolean isGenerationFailed() {
        return this.genFailed;
    }

    public int getAttemptCount() {
        return this.genAttemptCount;
    }

    public int getFailedAttemptCount() {
        return this.genSuccess || this.genBackgroundReady || this.pendingFill != null ? this.genAttemptCount - 1 : this.genAttemptCount;
    }

    public String getGenerationStatus() {
        if (this.pendingFill != null) {
            return "Clue checks: " + this.pendingFill.getCompletedClueChecks()
                    + "   |   Clues remaining: " + this.pendingFill.getRemainingClues();
        }
        if (this.genBackgroundReady) {
            return "Finding and checking a solution...";
        }
        return "Building and shaping regions... (attempt " + this.genAttemptCount + "/" + this.genMaxRetries + ")";
    }

    public int getMaxRetries() {
        return this.genMaxRetries;
    }

    private boolean setBackground(int xSize, int ySize, int fiveCount, int fourCount) {
        this.generatedSolution = null;
        if (xSize * ySize > 45 && xSize >= 5 && ySize % 2 == 0) {
            return setLargeBackground(xSize, ySize, fiveCount);
        }
        int maxAttempts = xSize * ySize > 45 ? 200 : 50;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int startCount = 1;
            this.found = false;
            byte[][] background = new byte[ySize][xSize];
            boolean[][] visited = new boolean[ySize][xSize];
            this.myBackground = new byte[ySize][xSize];

            if (xSize * ySize > 45) {
                // Large levels (Uber/Titan): place most regions as 5s and 4s with random
                // shapes, then fill any remaining space with random 1-5 sized regions
                // so biomes have varied sizes instead of uniform 5-cell stripes.
                int reducedFive = Math.max(0, (int) (fiveCount * 0.8));
                int reducedFour = Math.max(0, (int) (fourCount * 0.8));

                for (int i = 0; i < reducedFive; i++) {
                    if (setInFive(startCount, 5, visited, background)) {
                        startCount += 1;
                    }
                }
                for (int i = 0; i < reducedFour; i++) {
                    if (setInFive(startCount, 4, visited, background)) {
                        startCount += 1;
                    }
                }

                while (hasEmptyCell(background)) {
                    int size = (int) (Math.random() * 5) + 1;
                    if (setInFive(startCount, size, visited, background)) {
                        startCount += 1;
                    } else {
                        boolean placed = false;
                        for (int s = 4; s >= 1 && !placed; s--) {
                            placed = setInFive(startCount, s, visited, background);
                        }
                        if (!placed) {
                            break;
                        }
                        startCount += 1;
                    }
                }
            } else {
                if (this.maxRegionSize > 5) {
                    int largerRegions = Math.max(1, xSize * ySize / (this.maxRegionSize * 2));
                    for (int regionIndex = 0; regionIndex < largerRegions; regionIndex++) {
                        int size = 6 + (int) (Math.random() * (this.maxRegionSize - 5));
                        if (setInFive(startCount, size, visited, background)) {
                            startCount += 1;
                        }
                    }
                }
                for (int countFive = 0; countFive < fiveCount; countFive++) {
                    if (setInFive(startCount, 5, visited, background)) {
                        startCount += 1;
                    }
                }

                for (int countFour = 0; countFour < fourCount; countFour++) {
                    if (setInFive(startCount, 4, visited, background)) {
                        startCount += 1;
                    }
                }
            }

            for (int y = 0; y < background.length; y++) {
                for (int x = 0; x < background[0].length; x++) {
                    myBackground[y][x] = background[y][x];
                }
            }

            int maxNumber = fillMissingNumbers(startCount);

            if (!checkIfPossible(startCount)) {
                //System.out.println("Nicht lösbar");
                continue;
            }

            this.found = false;
            byte[][] newBackground = new byte[myBackground.length][myBackground[0].length];
            correctNumbersToBiom(newBackground, 1 + (int)(Math.random() * maxNumber), maxNumber);
            if (!this.found) {
                continue;
            }

            for (int y = 0; y < newBackground.length; y++) {
                for (int x = 0; x < newBackground[0].length; x++) {
                    myBackground[y][x] = newBackground[y][x];
                }
            }
            return true;
        }
        return false;
    }

    private boolean setLargeBackground(int xSize, int ySize, int fiveCount) {
        this.myBackground = new byte[ySize][xSize];
        byte[][] solution = new byte[ySize][xSize];
        int[] sizes = new int[xSize * ySize + 1];
        int evenWidth = xSize % 2 == 0 ? xSize : xSize - 5;
        int region = 0;

        for (int blockY = 0; blockY < ySize; blockY += 2) {
            for (int blockX = 0; blockX < evenWidth; blockX += 2) {
                region += 1;
                sizes[region] = 4;
                for (int offsetY = 0; offsetY < 2; offsetY++) {
                    for (int offsetX = 0; offsetX < 2; offsetX++) {
                        this.myBackground[blockY + offsetY][blockX + offsetX] = (byte) region;
                    }
                }
            }
        }

        int placedFives = 0;
        if (evenWidth < xSize) {
            for (int blockY = 0; blockY < ySize; blockY += 2) {
                int firstRegion = ++region;
                int secondRegion = ++region;
                sizes[firstRegion] = 5;
                sizes[secondRegion] = 5;
                placedFives += 2;
                boolean bent = Math.random() < 0.5;
                for (int offsetX = 0; offsetX < 5; offsetX++) {
                    solution[blockY][evenWidth + offsetX] = (byte) (offsetX + 1);
                    solution[blockY + 1][evenWidth + offsetX] = (byte) ((offsetX + 2) % 5 + 1);
                    this.myBackground[blockY][evenWidth + offsetX] = (byte) (!bent || offsetX < 2 ? firstRegion : secondRegion);
                    this.myBackground[blockY + 1][evenWidth + offsetX] = (byte) (bent && offsetX < 3 ? firstRegion : secondRegion);
                }
            }
        }

        // The block area is solved after the strip, so the strip acts as a fixed
        // constraint for the block placement.
        fillRandomSolution(solution, evenWidth, ySize, xSize);

        int targetFives = Math.max(fiveCount, placedFives + evenWidth * ySize / 16);
        for (int attempt = 0; attempt < xSize * ySize * 4 && placedFives < targetFives; attempt++) {
            int sourceX = 2 * (int) (Math.random() * (evenWidth / 2)) + 1;
            int sourceY = 2 * (int) (Math.random() * (ySize / 2)) + 1;
            boolean moveRight = Math.random() < 0.5;
            int targetX = sourceX + (moveRight ? 1 : 0);
            int targetY = sourceY + (moveRight ? 0 : 1);
            if (sourceX >= evenWidth || targetX >= evenWidth || targetY >= ySize) {
                continue;
            }
            int sourceRegion = this.myBackground[sourceY][sourceX];
            int targetRegion = this.myBackground[targetY][targetX];
            if (sizes[sourceRegion] != 4 || sizes[targetRegion] != 4
                    || solution[sourceY][sourceX] != 4
                    || !Helper.canPlaceValue(sourceX, sourceY, (byte) 5, solution)) {
                continue;
            }
            this.myBackground[sourceY][sourceX] = (byte) targetRegion;
            solution[sourceY][sourceX] = 5;
            sizes[sourceRegion] = 3;
            sizes[targetRegion] = 5;
            placedFives += 1;
        }

        diversifyRegions(solution, sizes);

        this.found = false;
        byte[][] coloredBackground = new byte[ySize][xSize];
        this.biomeColoringDeadline = System.currentTimeMillis() + BIOME_COLORING_TIMEOUT_MS;
        correctNumbersToBiom(coloredBackground, 1 + (int) (Math.random() * region), region);
        this.biomeColoringDeadline = 0;
        if (!this.found) {
            return false;
        }
        boolean flipX = Math.random() < 0.5;
        boolean flipY = Math.random() < 0.5;
        this.generatedSolution = new byte[ySize][xSize];
        for (int row = 0; row < ySize; row++) {
            for (int column = 0; column < xSize; column++) {
                int sourceX = flipX ? xSize - 1 - column : column;
                int sourceY = flipY ? ySize - 1 - row : row;
                this.myBackground[row][column] = coloredBackground[sourceY][sourceX];
                this.generatedSolution[row][column] = solution[sourceY][sourceX];
            }
        }
        return true;
    }

    private void diversifyRegions(byte[][] solution, int[] sizes) {
        int width = solution[0].length;
        int height = solution.length;
        for (int attempt = 0; attempt < width * height * 100; attempt++) {
            int sourceX = (int) (Math.random() * width);
            int sourceY = (int) (Math.random() * height);
            GridPoint2 direction = getForDirection((int) (Math.random() * 4));
            int targetX = sourceX + direction.x;
            int targetY = sourceY + direction.y;
            if (targetX < 0 || targetX >= width || targetY < 0 || targetY >= height) {
                continue;
            }
            int sourceRegion = this.myBackground[sourceY][sourceX];
            int targetRegion = this.myBackground[targetY][targetX];
            int sourceSize = sizes[sourceRegion];
            int targetSize = sizes[targetRegion];
            if (sourceRegion == targetRegion || sourceSize <= 1 || targetSize >= this.maxRegionSize
                    || solution[sourceY][sourceX] != sourceSize
                    || !Helper.canPlaceValue(sourceX, sourceY, targetSize + 1, solution)) {
                continue;
            }

            this.myBackground[sourceY][sourceX] = (byte) targetRegion;
            boolean connected = false;
            for (int neighbor = 0; neighbor < 4; neighbor++) {
                GridPoint2 offset = getForDirection(neighbor);
                int neighborX = sourceX + offset.x;
                int neighborY = sourceY + offset.y;
                if (neighborX < 0 || neighborX >= width || neighborY < 0 || neighborY >= height
                        || this.myBackground[neighborY][neighborX] != sourceRegion) {
                    continue;
                }
                boolean[][] visited = new boolean[height][width];
                visited[neighborY][neighborX] = true;
                connected = 1 + getRegionSize(neighborX, neighborY, sourceRegion, this.myBackground, visited) == sourceSize - 1;
                break;
            }
            if (!connected) {
                this.myBackground[sourceY][sourceX] = (byte) sourceRegion;
                continue;
            }
            solution[sourceY][sourceX] = (byte) (targetSize + 1);
            sizes[sourceRegion] -= 1;
            sizes[targetRegion] += 1;
        }
    }

    private boolean hasEmptyCell(byte[][] background) {
        for (int y = 0; y < background.length; y++) {
            for (int x = 0; x < background[0].length; x++) {
                if (background[y][x] == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkIfPossible(int startCount) {
        this.regionSize = new int[myBackground.length][myBackground[0].length];
        for (int y = 0; y < myBackground.length; y++) {
            for (int x = 0; x < myBackground[0].length; x++) {
                if (this.regionSize[y][x] == 0) {
                    boolean[][] visited = new boolean[myBackground.length][myBackground[0].length];
                    this.regionSize[y][x] = getRegionSize(x, y, myBackground[y][x], myBackground, visited);
                    if (this.regionSize[y][x] > this.maxRegionSize) {
                        return false;
                    }
                    if (this.regionSize[y][x] == 0) {
                        this.regionSize[y][x] = 1;
                    }
                    for (int regionY = 0; regionY < myBackground.length; regionY++) {
                        for (int regionX = 0; regionX < myBackground[0].length; regionX++) {
                            if (visited[regionY][regionX]) {
                                this.regionSize[regionY][regionX] = this.regionSize[y][x];
                            }
                        }
                    }
                }
            }
        }

        for (int y = 0; y < myBackground.length; y++) {
            for (int x = 0; x < myBackground[0].length; x++) {
                boolean[][] visited = new boolean[myBackground.length][myBackground[0].length];
                visited[y][x] = true;
                fillVisitedWithMyValues(myBackground[y][x], visited, x, y);
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
                            if (fillVisitedWithMyValues(myBackground[y+nextY][x+nextX], visited, x, y) && (regionSize[y][x] == 1 || regionSize[y+nextY][x+nextX] >= regionSize[y][x])) {
                                return false;
                            }
                        }
                    }
                }

            }
        }

        PossibleValueCheck value = new PossibleValueCheck(myBackground, regionSize);
        boolean result = value.isSolveable();

        return result;
    }

    private boolean fillVisitedWithMyValues(byte value, boolean[][] visited, int checkX, int checkY) {
        boolean result = true;
        for (int y = 0; y < myBackground.length; y++) {
            for (int x = 0; x < myBackground[0].length; x++) {
                if (myBackground[y][x] == value) {
                    visited[y][x] = true;
                    if (Math.abs(checkX - x) > 1 || Math.abs(checkY - y) > 1) {
                        result = false;
                    }
                }
            }
        }
        return result;
    }

    private int getRegionSize(int x, int y, int region, byte[][] background, boolean[][] visited) {
        int result = 0;
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

    private boolean isOk(int x, int y, int region, byte[][] background, boolean[][] visitedRegion) {
        if (x < 0 || x >= background[0].length || y < 0 || y >= background.length) {
            return false;
        }
        return background[y][x] == region && !visitedRegion[y][x];
    }

    private boolean isOnlyAround(int x, int y, int addX, int addY, boolean[][] visited, int value) {
        if (x + addX < 0 || x + addX >= myBackground[0].length || y + addY < 0 || y + addY >= myBackground.length) {
            return false;
        }
        if (visited[y+addY][x+addX]) {
            return true;
        }
        int myValue = value;
        if (myValue < 0) {
            myValue = myBackground[y+addY][x+addX];
        }
        visited[y+addY][x+addX] = true;
        if (myValue != myBackground[y+addY][x+addX]) {
            return true;
        }
        if (Math.abs(addX) > 1 || Math.abs(addY) > 1) {
            return false;
        }

        boolean result = isOnlyAround(x, y, addX - 1, addY, visited, myValue);
        result &= isOnlyAround(x, y, addX + 1, addY, visited, myValue);
        result &= isOnlyAround(x, y, addX, addY - 1, visited, myValue);
        result &= isOnlyAround(x, y, addX, addY + 1, visited, myValue);

        return result;
    }

    private byte getValue(int x, int y, byte[][] background) {
        if (x < 0 || x >= background[0].length || y < 0 || y >= background.length) {
            return -1;
        }
        return background[y][x];
    }

    private void correctNumbersToBiom(byte[][] newBackground, int startValueToReplace, int maxValue) {
        if (found) {
            return;
        }
        if (this.biomeColoringDeadline > 0 && System.currentTimeMillis() > this.biomeColoringDeadline) {
            return;
        }
        if (checkFound(newBackground)) {
            return;
        }

        boolean[][] visitedNow = new boolean[myBackground.length][myBackground[0].length];

        for (int y = 0; y < myBackground.length; y++) {
            for (int x = 0; x < myBackground[0].length; x++) {
                if (myBackground[y][x] == startValueToReplace && !visitedNow[y][x]) {
                    byte value = (byte)(Math.random() * 4 + 1);
                    for (int v = 0; v < 4; v++) {
                        fillWithNumber(x, y, value, visitedNow, newBackground, startValueToReplace);
                        if (checkIfValid(value, visitedNow, newBackground)) {
                            int nextNumber = startValueToReplace + 1;
                            if (nextNumber > maxValue) nextNumber = 1;
                            correctNumbersToBiom(newBackground, nextNumber, maxValue);

                            if (found) {
                                return;
                            }
                            checkFound(newBackground);
                        }

                        if (found) {
                            return;
                        }
                        resetValues(x, y, (byte) 0, newBackground, startValueToReplace);
                        visitedNow = new boolean[myBackground.length][myBackground[0].length];

                        value += 1;
                        if (value > 4) {
                            value = (byte)(1);
                        }
                    }
                    return;
                }
            }
        }
    }

    private boolean checkFound(byte[][] newBackground) {
        boolean found = true;
        this.found = false;
        for (int y = 0; y < newBackground.length; y++) {
            for (int x = 0; x < newBackground[0].length; x++) {
                if (newBackground[y][x] == 0) {
                    found = false;
                    break;
                }
            }
            if (!found) {
                break;
            }
        }
        if (found) {
            this.found = true;
        }
        return this.found;
    }

    private ArrayList<Byte> getPossibleValues(int x, int y, boolean[][] visitedNow, byte[][] background) {
        HashSet<Byte> allValues = new HashSet<>();
        allValues.add((byte) 1);
        allValues.add((byte) 2);
        allValues.add((byte) 3);
        allValues.add((byte) 4);

        allValues.remove(getValue(x, y - 1, background, visitedNow));

        allValues.remove(getValue(x - 1, y, background, visitedNow));
        allValues.remove(getValue(x + 1, y, background, visitedNow));

        allValues.remove(getValue(x, y + 1, background, visitedNow));

        return new ArrayList<>(allValues);
    }

    private byte getValue(int x, int y, byte[][] background, boolean[][] visitedNow) {
        if (x < 0 || x >= background[0].length || y < 0 || y >= background.length || visitedNow[y][x]) {
            return -1;
        }
        return background[y][x];
    }

    private int fillMissingNumbers(int number) {
        this.finished = false;

        byte myNumber = (byte)number;

        for (int y = 0; y < myBackground.length; y++) {
            for (int x = 0; x < myBackground[0].length; x++) {
                if (myBackground[y][x] == 0) {
                    boolean[][] visitedNow = new boolean[myBackground.length][myBackground[0].length];

                    fillWithNumber(x, y, myNumber, visitedNow, this.myBackground,-1);
                    myNumber += 1;
                }
            }
        }
        this.finished = true;
        return (myNumber - 1);
    }

    private boolean resetValues(int x, int y, byte num, byte[][] background, int checkValue) {
        if (x < 0 || y < 0 || x >= this.myBackground[0].length || y >= this.myBackground.length) {
            return true;
        }
        if (checkValue > 0 && this.myBackground[y][x] != checkValue) {
            return false;
        }
        if (background[y][x] == num) {
            return false;
        }

        background[y][x] = num;

        boolean fill = this.resetValues(x - 1, y, num, background, checkValue);
        fill &= this.resetValues(x + 1, y, num, background, checkValue);
        fill &= this.resetValues(x, y - 1, num, background, checkValue);
        fill &= this.resetValues(x, y + 1, num, background, checkValue);

        return fill;
    }

    private boolean fillWithNumber(int x, int y, byte num, boolean[][] visitedNow, byte[][] background, int checkValue) {
        if (x < 0 || y < 0 || x >= this.myBackground[0].length || y >= this.myBackground.length) {
            return true;
        }
        if (background[y][x] != 0 && (checkValue < 0 || num != 0)) {
            return true;
        }
        if (checkValue > 0 && this.myBackground[y][x] != checkValue) {
            return false;
        }

        background[y][x] = num;
        visitedNow[y][x] = true;

        boolean fill = this.fillWithNumber(x - 1, y, num, visitedNow, background, checkValue);
        fill &= this.fillWithNumber(x + 1, y, num, visitedNow, background, checkValue);
        fill &= this.fillWithNumber(x, y - 1, num, visitedNow, background, checkValue);
        fill &= this.fillWithNumber(x, y + 1, num, visitedNow, background, checkValue);

        return fill;
    }

    private boolean checkIfValid(byte value, boolean[][] visitedNow, byte[][] background) {
        for (int y = 0; y < visitedNow.length; y++) {
            for (int x = 0; x < visitedNow[0].length; x++) {
                if (visitedNow[y][x]) {
                    if (!getPossibleValues(x, y, visitedNow, background).contains(value)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean setInFive(int value, int size, boolean[][] visited, byte[][] background) {
        int startX = (int)(Math.random() * background[0].length);
        int startY = (int)(Math.random() * background.length);

        int count = 0;
        while ((count < 100) && (background[startY][startX] != 0 || visited[startY][startX])) {
            startX = (int)(Math.random() * background[0].length);
            startY = (int)(Math.random() * background.length);
            count += 1;
        }
        if (count >= 100) {
            //System.out.println("Nichts gefunden");
            found = false;
            return false;
        }

        found = false;
        tryPlaceValue(startX, startY, (byte) value, (int)(Math.random() * 4), size - 1, visited, background);
        if (found) {
            count = 0;
            for (int y = 0; y < background.length; y++) {
                for (int x = 0; x < background[0].length; x++) {
                    if (background[y][x] == value && !visited[y][x]) {
                        visited[y][x] = true;
                        count += 1;
                    }
                }
            }
            if (count > size) {
                return false;
            }
        } else {
            for (int y = 0; y < background.length; y++) {
                for (int x = 0; x < background[0].length; x++) {
                    if (background[y][x] != 0 && !visited[y][x]) {
                        background[y][x] = 0;
                    }
                }
            }
        }
        return this.found;
    }

    private void tryPlaceValue(int x, int y, byte value, int direction, int size, boolean[][] visited, byte[][] background) {
        if (size < 0 || this.found) {
            this.found = true;
            return;
        }
        if (x < 0 || x >= background[0].length || y < 0 || y >= background.length) {
            return;
        }

        if (background[y][x] != 0 || visited[y][x]) {
            return;
        }
        background[y][x] = value;

        int curDirection = direction;
        for (int i = 0; i < 4; i++) {
            GridPoint2 forDirection = getForDirection(curDirection);
            int addX = forDirection.x;
            int addY = forDirection.y;

            tryPlaceValue(x + addX, y + addY, value, (int)(Math.random() * 4), size - 1, visited, background);

            if (this.found) {
                return;
            }

            curDirection += 1;
            if (curDirection > 3) {
                curDirection = 0;
            }
        }

        background[y][x] = 0;
    }

    private GridPoint2 getForDirection(int direction) {
        if (direction == 1) {
            return new GridPoint2(1, 0);
        } else if (direction == 2) {
            return new GridPoint2(0, 1);
        } else if (direction == 3) {
            return new GridPoint2(-1, 0);
        }
        return new GridPoint2(0, -1);
    }

    /**
     * Fills the 2x2 block area of the solution with a randomized but always legal
     * pattern. Every block holds one cell of each value 1-4 (required so that each
     * region can be solved), but the arrangement inside the block is randomized
     * instead of the repeating 1/2/3/4 stamp, so the clues that survive the
     * clue removal no longer form a visible lattice.
     *
     * The blocks are placed with a backtracking search that only accepts
     * placements keeping the board a valid king-graph colouring (no two equal
     * values in the 8 neighbouring cells). The search is bounded by
     * SOLUTION_PLACEMENT_BUDGET and SOLUTION_RESTARTS; if it cannot find a
     * layout the deterministic pattern is used, which is always valid.
     */
    private void fillRandomSolution(byte[][] solution, int blockWidth, int ySize, int xSize) {
        int blocksX = blockWidth / 2;
        int blocksY = ySize / 2;
        if (blocksX <= 0 || blocksY <= 0) {
            return;
        }
        for (int restart = 0; restart < SOLUTION_RESTARTS; restart++) {
            byte[][] candidate = new byte[ySize][xSize];
            for (int y = 0; y < ySize; y++) {
                System.arraycopy(solution[y], 0, candidate[y], 0, xSize);
            }
            int[] budget = new int[]{SOLUTION_PLACEMENT_BUDGET};
            if (placeBlocks(candidate, 0, 0, blocksX, blocksY, xSize, ySize, budget)) {
                for (int y = 0; y < ySize; y++) {
                    System.arraycopy(candidate[y], 0, solution[y], 0, xSize);
                }
                return;
            }
        }
        for (int blockY = 0; blockY < ySize; blockY += 2) {
            for (int blockX = 0; blockX < blockWidth; blockX += 2) {
                solution[blockY][blockX] = 1;
                solution[blockY][blockX + 1] = 2;
                solution[blockY + 1][blockX] = 3;
                solution[blockY + 1][blockX + 1] = 4;
            }
        }
    }

    /**
     * Places the 2x2 blocks in row major order, trying a random permutation of
     * {1,2,3,4} for every block. Returns false when the block cannot be placed
     * or the placement budget is exhausted, in which case the caller restarts
     * with a fresh random order.
     */
    private boolean placeBlocks(byte[][] solution, int blockX, int blockY, int blocksX, int blocksY, int xSize, int ySize, int[] budget) {
        if (blockY >= blocksY) {
            return true;
        }
        if (blockX >= blocksX) {
            return placeBlocks(solution, 0, blockY + 1, blocksX, blocksY, xSize, ySize, budget);
        }
        if (budget[0] <= 0) {
            return false;
        }
        budget[0] -= 1;

        int x = blockX * 2;
        int y = blockY * 2;
        int[] order = new int[BLOCK_PERMUTATIONS.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        shuffle(order);

        for (int i = 0; i < order.length; i++) {
            byte[] permutation = BLOCK_PERMUTATIONS[order[i]];
            solution[y][x] = permutation[0];
            solution[y][x + 1] = permutation[1];
            solution[y + 1][x] = permutation[2];
            solution[y + 1][x + 1] = permutation[3];
            if (isBlockValid(solution, x, y, xSize, ySize)
                    && placeBlocks(solution, blockX + 1, blockY, blocksX, blocksY, xSize, ySize, budget)) {
                return true;
            }
        }

        solution[y][x] = 0;
        solution[y][x + 1] = 0;
        solution[y + 1][x] = 0;
        solution[y + 1][x + 1] = 0;
        return false;
    }

    /**
     * Checks whether the four cells of the 2x2 block at (x, y) keep the board a
     * valid king-graph colouring. Cells of the same block are mutually distinct
     * because they hold a permutation, so only the cells outside the block are
     * checked. Cells that are not placed yet are still 0 and are skipped - they
     * are checked against this block when they get placed.
     */
    private boolean isBlockValid(byte[][] solution, int x, int y, int xSize, int ySize) {
        for (int offsetY = 0; offsetY < 2; offsetY++) {
            for (int offsetX = 0; offsetX < 2; offsetX++) {
                int cellX = x + offsetX;
                int cellY = y + offsetY;
                byte value = solution[cellY][cellX];
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) {
                            continue;
                        }
                        int nx = cellX + dx;
                        int ny = cellY + dy;
                        if (nx < 0 || nx >= xSize || ny < 0 || ny >= ySize) {
                            continue;
                        }
                        if (nx >= x && nx < x + 2 && ny >= y && ny < y + 2) {
                            continue;
                        }
                        if (solution[ny][nx] == 0) {
                            continue;
                        }
                        if (solution[ny][nx] == value) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private void shuffle(int[] values) {
        for (int i = values.length - 1; i > 0; i--) {
            int j = (int) (Math.random() * (i + 1));
            int tmp = values[i];
            values[i] = values[j];
            values[j] = tmp;
        }
    }

    private static byte[][] createBlockPermutations() {
        ArrayList<byte[]> permutations = new ArrayList<>();
        for (int a = 1; a <= 4; a++) {
            for (int b = 1; b <= 4; b++) {
                if (b == a) {
                    continue;
                }
                for (int c = 1; c <= 4; c++) {
                    if (c == a || c == b) {
                        continue;
                    }
                    int d = 10 - a - b - c;
                    permutations.add(new byte[]{(byte) a, (byte) b, (byte) c, (byte) d});
                }
            }
        }
        return permutations.toArray(new byte[permutations.size()][]);
    }
}
