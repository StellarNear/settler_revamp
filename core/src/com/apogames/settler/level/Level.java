package com.apogames.settler.level;

import com.apogames.settler.level.helper.Helper;

public class Level {
    private final byte[][] background;
    private final byte[][] startLevel;
    private final byte[][] numbers;

    private byte[][] curNumber;
    private byte[][] curBackground;
    private byte[][] region;
    private int[][] hints;
    private int[][] clickCount;
    private int[][] missCount;
    private int totalClicks;
    private int totalMissClicks;

    public Level(byte[][] background, byte[][] numbers, byte[][] startLevel) {
        this.background = background;
        this.numbers = numbers;
        this.startLevel = startLevel;

        this.init();
    }

    public void init() {
        this.curNumber = Helper.cloneArray(startLevel);
        this.hints = new int[startLevel.length][startLevel[0].length];
        this.resetClickStats();

        this.curBackground = Helper.cloneArray(startLevel);
        for (int y = 0; y < this.curBackground.length; y++) {
            for (int x = 0; x < this.curBackground[0].length; x++) {
                if (this.curBackground[y][x] > 0) {
                    this.curBackground[y][x] = this.background[y][x];
                }
            }
        }
    }

    private void resetClickStats() {
        this.clickCount = new int[this.startLevel.length][this.startLevel[0].length];
        this.missCount = new int[this.startLevel.length][this.startLevel[0].length];
        this.totalClicks = 0;
        this.totalMissClicks = 0;
    }

    /**
     * Records a click that changed a tile. The clicks spent on a tile are compared
     * against the cheapest way of reaching its current value, so a player who
     * right-clicks straight onto a high value is credited with a perfect entry while
     * one who walks up through every intermediate value is not.
     */
    public void registerValueClick(int x, int y) {
        this.clickCount[y][x] += 1;
        this.totalClicks += 1;
        int miss = this.clickCount[y][x];
        if (this.region != null) {
            miss = Math.max(0, this.clickCount[y][x] - getOptimalClickCount(this.curNumber[y][x], this.region[y][x]));
        }
        this.totalMissClicks += miss - this.missCount[y][x];
        this.missCount[y][x] = miss;
    }

    /**
     * Records a click that changed nothing, e.g. a click on a pre-filled clue tile.
     */
    public void registerWastedClick() {
        this.totalClicks += 1;
        this.totalMissClicks += 1;
    }

    /**
     * Fewest clicks that can produce a value: either counting up from empty, or
     * right-clicking down from the tile's region size.
     */
    private static int getOptimalClickCount(byte value, byte max) {
        if (value <= 0) {
            return 0;
        }
        return Math.min(value, max - value + 1);
    }

    public int getTotalClicks() {
        return this.totalClicks;
    }

    public int getTotalMissClicks() {
        return this.totalMissClicks;
    }

    public int getAccuracyPercent() {
        if (this.totalClicks <= 0) {
            return 100;
        }
        return (int) Math.round((this.totalClicks - this.totalMissClicks) * 100.0 / this.totalClicks);
    }

    public byte[][] getBackground() {
        return background;
    }

    public byte[][] getStartLevel() {
        return startLevel;
    }

    public byte[][] getNumbers() {
        return numbers;
    }

    public byte[][] getCurNumber() {
        return curNumber;
    }

    public byte[][] getCurBackground() {
        return curBackground;
    }

    public byte[][] getRegion() {
        return region;
    }

    public void setRegion(byte[][] region) {
        this.region = region;
    }

    public int[][] getHints() {
        return hints;
    }

    public boolean hasHint(int x, int y, int value) {
        return (hints[y][x] & (1 << (value - 1))) != 0;
    }

    public void toggleHint(int x, int y, int value) {
        hints[y][x] ^= (1 << (value - 1));
    }

    public void clearHints(int x, int y) {
        hints[y][x] = 0;
    }

    public boolean isSolved() {
        for (int y = 0; y < this.curNumber.length; y++) {
            for (int x = 0; x < this.curNumber[0].length; x++) {
                if (this.curNumber[y][x] != this.numbers[y][x]) {
                    return false;
                }
            }
        }
        return true;
    }

    public byte[][] getError() {
        byte[][] error = new byte[this.curNumber.length][this.curNumber[0].length];
        for (int y = 0; y < this.curNumber.length; y++) {
            for (int x = 0; x < this.curNumber[0].length; x++) {
                if (this.curNumber[y][x] != 0) {
                    markNeighborSameValue(x, y, error);
                    markSameValueInBiome(x, y, error);
                }
            }
        }
        return error;
    }

    private void markSameValueInBiome(int startX, int startY, byte[][] error) {
        byte biome = this.background[startY][startX];
        if (biome <= 0) {
            return;
        }
        byte value = this.curNumber[startY][startX];
        boolean[][] visited = new boolean[this.curNumber.length][this.curNumber[0].length];
        visited[startY][startX] = true;
        visitBiome(startX, startY, biome, value, startX, startY, visited, error);
    }

    private void visitBiome(int x, int y, byte biome, byte value, int startX, int startY, boolean[][] visited, byte[][] error) {
        stepBiome(x + 1, y, biome, value, startX, startY, visited, error);
        stepBiome(x - 1, y, biome, value, startX, startY, visited, error);
        stepBiome(x, y + 1, biome, value, startX, startY, visited, error);
        stepBiome(x, y - 1, biome, value, startX, startY, visited, error);
    }

    private void stepBiome(int x, int y, byte biome, byte value, int startX, int startY, boolean[][] visited, byte[][] error) {
        if (x < 0 || x >= this.curNumber[0].length || y < 0 || y >= this.curNumber.length) {
            return;
        }
        if (visited[y][x] || this.background[y][x] != biome) {
            return;
        }
        visited[y][x] = true;
        if (this.curNumber[y][x] == value) {
            error[startY][startX] = 1;
            error[y][x] = 1;
        }
        visitBiome(x, y, biome, value, startX, startY, visited, error);
    }

    public boolean hasClueInBiomeOf(int x, int y, int value) {
        byte biome = this.background[y][x];
        if (biome <= 0) {
            return false;
        }
        boolean[][] visited = new boolean[this.background.length][this.background[0].length];
        return visitForClue(x, y, biome, (byte) value, visited);
    }

    private boolean visitForClue(int x, int y, byte biome, byte value, boolean[][] visited) {
        if (x < 0 || y < 0 || x >= this.background[0].length || y >= this.background.length) {
            return false;
        }
        if (visited[y][x] || this.background[y][x] != biome) {
            return false;
        }
        visited[y][x] = true;
        if (this.startLevel[y][x] == value) {
            return true;
        }
        return visitForClue(x + 1, y, biome, value, visited)
            || visitForClue(x - 1, y, biome, value, visited)
            || visitForClue(x, y + 1, biome, value, visited)
            || visitForClue(x, y - 1, biome, value, visited);
    }

    public boolean isFull() {
        for (int y = 0; y < this.curNumber.length; y++) {
            for (int x = 0; x < this.curNumber[0].length; x++) {
                if (this.background[y][x] > 0 && this.curNumber[y][x] == 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private void markNeighborSameValue(int startX, int startY, byte[][] error) {
        markIfNeighborSame(startX, startY, -1, -1, error);
        markIfNeighborSame(startX, startY, -1, 0, error);
        markIfNeighborSame(startX, startY, -1, +1, error);
        markIfNeighborSame(startX, startY, 0, -1, error);
        markIfNeighborSame(startX, startY, 0, +1, error);
        markIfNeighborSame(startX, startY, +1, -1, error);
        markIfNeighborSame(startX, startY, +1, 0, error);
        markIfNeighborSame(startX, startY, +1, +1, error);
    }

    private void markIfNeighborSame(int startX, int startY, int addX, int addY, byte[][] error) {
        if (startX + addX >= 0 && startX + addX < this.curNumber[0].length && startY + addY >= 0 && startY + addY < this.curNumber.length) {
            if (this.curNumber[startY + addY][startX + addX] == this.curNumber[startY][startX]) {
                error[startY][startX] = 1;
                error[startY + addY][startX + addX] = 1;
            }
        }
    }

    public void restart() {
        this.curNumber = Helper.cloneArray(this.startLevel);
        this.hints = new int[this.startLevel.length][this.startLevel[0].length];
        this.resetClickStats();
    }
}
