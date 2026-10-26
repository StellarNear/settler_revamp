package com.apogames.tiwanaku.engine;

import java.util.Arrays;
import java.util.ArrayDeque;
import java.util.Queue;

public final class PuzzleDefinition {
    public static final int MAX_CELLS = 240;
    public static final int MAX_REGION_SIZE = 9;

    private final int columns;
    private final int rows;
    private final int[] regionIds;
    private final int[] givens;
    private final int[] regionSizes;

    public PuzzleDefinition(int columns, int rows, int[] regionIds, int[] givens) {
        if (columns <= 0 || rows <= 0 || (long) columns * rows > MAX_CELLS) {
            throw new IllegalArgumentException("Board dimensions must contain between 1 and 240 cells");
        }
        int cellCount = columns * rows;
        if (regionIds == null || regionIds.length != cellCount || givens == null || givens.length != cellCount) {
            throw new IllegalArgumentException("Region and given arrays must match the board dimensions");
        }

        this.columns = columns;
        this.rows = rows;
        this.regionIds = regionIds.clone();
        this.givens = givens.clone();
        this.regionSizes = validateAndCountRegions();
        validateGivens();
    }

    private int[] validateAndCountRegions() {
        int regionCount = 0;
        for (int regionId : regionIds) {
            if (regionId < 0 || regionId >= regionIds.length) {
                throw new IllegalArgumentException("Region identifiers must be nonnegative and normalized");
            }
            regionCount = Math.max(regionCount, regionId + 1);
        }
        if (regionCount == 0) {
            throw new IllegalArgumentException("A puzzle must contain at least one region");
        }

        int[] sizes = new int[regionCount];
        for (int regionId : regionIds) {
            sizes[regionId]++;
        }
        for (int size : sizes) {
            if (size == 0 || size > MAX_REGION_SIZE) {
                throw new IllegalArgumentException("Every region must contain between 1 and 9 cells");
            }
        }
        validateConnectedRegions(sizes);
        return sizes;
    }

    private void validateConnectedRegions(int[] regionSizes) {
        boolean[] visited = new boolean[regionIds.length];
        for (int regionId = 0; regionId < regionSizes.length; regionId++) {
            int start = firstCellOfRegion(regionId);
            Queue<Integer> pending = new ArrayDeque<>();
            pending.add(start);
            visited[start] = true;
            int reached = 0;
            while (!pending.isEmpty()) {
                int current = pending.remove();
                reached++;
                int x = current % columns;
                int y = current / columns;
                visitNeighbor(current - 1, x > 0, regionId, visited, pending);
                visitNeighbor(current + 1, x + 1 < columns, regionId, visited, pending);
                visitNeighbor(current - columns, y > 0, regionId, visited, pending);
                visitNeighbor(current + columns, y + 1 < rows, regionId, visited, pending);
            }
            if (reached != regionSizes[regionId]) {
                throw new IllegalArgumentException("Each region must be orthogonally connected");
            }
        }
    }

    private void visitNeighbor(
            int neighbor,
            boolean inside,
            int regionId,
            boolean[] visited,
            Queue<Integer> pending) {
        if (inside && !visited[neighbor] && regionIds[neighbor] == regionId) {
            visited[neighbor] = true;
            pending.add(neighbor);
        }
    }

    private int firstCellOfRegion(int regionId) {
        for (int cell = 0; cell < regionIds.length; cell++) {
            if (regionIds[cell] == regionId) {
                return cell;
            }
        }
        throw new IllegalArgumentException("Region identifiers must be normalized");
    }

    private void validateGivens() {
        for (int cell = 0; cell < givens.length; cell++) {
            int value = givens[cell];
            if (value < 0 || value > regionSizes[regionIds[cell]]) {
                throw new IllegalArgumentException("Given values must be zero or valid for their region");
            }
        }
    }

    public int columns() {
        return columns;
    }

    public int rows() {
        return rows;
    }

    public int cellCount() {
        return regionIds.length;
    }

    public int regionCount() {
        return regionSizes.length;
    }

    public int regionIdAt(int cell) {
        return regionIds[cell];
    }

    public int regionSize(int regionId) {
        return regionSizes[regionId];
    }

    public int regionSizeAt(int cell) {
        return regionSizes[regionIds[cell]];
    }

    public int givenAt(int cell) {
        return givens[cell];
    }

    public int[] regionIds() {
        return regionIds.clone();
    }

    public int[] givens() {
        return givens.clone();
    }

    @Override
    public String toString() {
        return "PuzzleDefinition[" + columns + "x" + rows + ", regions="
                + regionSizes.length + ", givens=" + Arrays.toString(givens) + "]";
    }
}
