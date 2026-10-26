package com.apogames.settler.characterization;

import java.util.ArrayDeque;
import java.util.Queue;

final class PuzzleRulesOracle {
    private static final int[][] REGION_DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private PuzzleRulesOracle() {
    }

    static boolean isValidCompleteBoard(byte[][] regions, byte[][] values) {
        Components components = Components.from(regions);
        if (!hasShape(values, regions.length, regions[0].length)) {
            return false;
        }

        boolean[][] usedValues = new boolean[components.sizes.length][];
        for (int component = 1; component < components.sizes.length; component++) {
            usedValues[component] = new boolean[components.sizes[component] + 1];
        }

        for (int y = 0; y < regions.length; y++) {
            for (int x = 0; x < regions[0].length; x++) {
                int component = components.ids[y][x];
                int value = values[y][x];
                if (value < 1 || value > components.sizes[component] || usedValues[component][value]) {
                    return false;
                }
                usedValues[component][value] = true;

                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int neighborX = x + dx;
                        int neighborY = y + dy;
                        if ((dx != 0 || dy != 0)
                                && isInside(neighborX, neighborY, regions[0].length, regions.length)
                                && values[neighborY][neighborX] == value) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    static int countSolutions(byte[][] regions) {
        Components components = Components.from(regions);
        byte[][] values = new byte[regions.length][regions[0].length];
        boolean[][] usedValues = new boolean[components.sizes.length][];
        for (int component = 1; component < components.sizes.length; component++) {
            usedValues[component] = new boolean[components.sizes[component] + 1];
        }
        return countSolutions(regions, components, values, usedValues, 0);
    }

    private static int countSolutions(
            byte[][] regions,
            Components components,
            byte[][] values,
            boolean[][] usedValues,
            int index) {
        int width = regions[0].length;
        if (index == regions.length * width) {
            return 1;
        }

        int x = index % width;
        int y = index / width;
        int component = components.ids[y][x];
        int solutions = 0;
        for (int value = 1; value <= components.sizes[component]; value++) {
            if (usedValues[component][value] || touchesSameValue(values, x, y, value)) {
                continue;
            }
            values[y][x] = (byte) value;
            usedValues[component][value] = true;
            solutions += countSolutions(regions, components, values, usedValues, index + 1);
            usedValues[component][value] = false;
            values[y][x] = 0;
        }
        return solutions;
    }

    private static boolean touchesSameValue(byte[][] values, int x, int y, int value) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int neighborX = x + dx;
                int neighborY = y + dy;
                if ((dx != 0 || dy != 0)
                        && isInside(neighborX, neighborY, values[0].length, values.length)
                        && values[neighborY][neighborX] == value) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasShape(byte[][] values, int height, int width) {
        if (values == null || values.length != height) {
            return false;
        }
        for (byte[] row : values) {
            if (row == null || row.length != width) {
                return false;
            }
        }
        return true;
    }

    private static boolean isInside(int x, int y, int width, int height) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    private static final class Components {
        private final int[][] ids;
        private final int[] sizes;

        private Components(int[][] ids, int[] sizes) {
            this.ids = ids;
            this.sizes = sizes;
        }

        private static Components from(byte[][] regions) {
            if (regions == null || regions.length == 0 || regions[0] == null || regions[0].length == 0) {
                throw new IllegalArgumentException("Regions must be a nonempty rectangular board");
            }
            int height = regions.length;
            int width = regions[0].length;
            for (byte[] row : regions) {
                if (row == null || row.length != width) {
                    throw new IllegalArgumentException("Regions must be rectangular");
                }
            }

            int[][] ids = new int[height][width];
            int[] componentSizes = new int[height * width + 1];
            int componentCount = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (regions[y][x] <= 0) {
                        throw new IllegalArgumentException("Region identifiers must be positive");
                    }
                    if (ids[y][x] != 0) {
                        continue;
                    }

                    componentCount++;
                    Queue<Integer> pending = new ArrayDeque<>();
                    pending.add(y * width + x);
                    ids[y][x] = componentCount;
                    int size = 0;
                    while (!pending.isEmpty()) {
                        int cell = pending.remove();
                        int cellX = cell % width;
                        int cellY = cell / width;
                        size++;
                        for (int[] direction : REGION_DIRECTIONS) {
                            int nextX = cellX + direction[0];
                            int nextY = cellY + direction[1];
                            if (isInside(nextX, nextY, width, height)
                                    && ids[nextY][nextX] == 0
                                    && regions[nextY][nextX] == regions[y][x]) {
                                ids[nextY][nextX] = componentCount;
                                pending.add(nextY * width + nextX);
                            }
                        }
                    }
                    componentSizes[componentCount] = size;
                }
            }

            int[] sizes = new int[componentCount + 1];
            System.arraycopy(componentSizes, 0, sizes, 0, sizes.length);
            return new Components(ids, sizes);
        }
    }
}
