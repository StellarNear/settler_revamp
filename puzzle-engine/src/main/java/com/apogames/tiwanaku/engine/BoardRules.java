package com.apogames.tiwanaku.engine;

public final class BoardRules {
    private BoardRules() {
    }

    public static boolean isValidCompleteBoard(PuzzleDefinition puzzle, int[] values) {
        if (puzzle == null || values == null || values.length != puzzle.cellCount()) {
            return false;
        }

        int[][] seen = new int[puzzle.regionCount()][];
        for (int region = 0; region < seen.length; region++) {
            seen[region] = new int[puzzle.regionSize(region) + 1];
        }
        for (int cell = 0; cell < values.length; cell++) {
            int value = values[cell];
            int region = puzzle.regionIdAt(cell);
            if ((puzzle.givenAt(cell) != 0 && value != puzzle.givenAt(cell))
                    || value < 1
                    || value > puzzle.regionSize(region)
                    || ++seen[region][value] != 1) {
                return false;
            }

            int x = cell % puzzle.columns();
            int y = cell / puzzle.columns();
            for (int neighborY = Math.max(0, y - 1); neighborY <= Math.min(puzzle.rows() - 1, y + 1); neighborY++) {
                for (int neighborX = Math.max(0, x - 1); neighborX <= Math.min(puzzle.columns() - 1, x + 1); neighborX++) {
                    int neighbor = neighborY * puzzle.columns() + neighborX;
                    if (neighbor < cell && values[neighbor] == value) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public static boolean isValidPartialBoard(PuzzleDefinition puzzle, int[] values) {
        if (puzzle == null || values == null || values.length != puzzle.cellCount()) {
            return false;
        }
        int[][] seen = new int[puzzle.regionCount()][];
        for (int region = 0; region < seen.length; region++) {
            seen[region] = new int[puzzle.regionSize(region) + 1];
        }
        for (int cell = 0; cell < values.length; cell++) {
            int value = values[cell];
            if (value == 0) {
                if (puzzle.givenAt(cell) != 0) {
                    return false;
                }
                continue;
            }
            int region = puzzle.regionIdAt(cell);
            if ((puzzle.givenAt(cell) != 0 && value != puzzle.givenAt(cell))
                    || value < 1
                    || value > puzzle.regionSize(region)
                    || ++seen[region][value] != 1) {
                return false;
            }
            int x = cell % puzzle.columns();
            int y = cell / puzzle.columns();
            for (int neighborY = Math.max(0, y - 1); neighborY <= Math.min(puzzle.rows() - 1, y + 1); neighborY++) {
                for (int neighborX = Math.max(0, x - 1); neighborX <= Math.min(puzzle.columns() - 1, x + 1); neighborX++) {
                    int neighbor = neighborY * puzzle.columns() + neighborX;
                    if (neighbor < cell && values[neighbor] == value) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
