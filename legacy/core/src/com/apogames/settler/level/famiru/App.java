package com.apogames.settler.level.famiru;

import com.apogames.settler.level.helper.Helper;

import java.util.*;

public class App {

    private static final List<String> LEVEL = Arrays.asList(
            "443334441",
            "442344211",
            "422111214",
            "322411244",
            "333442244"
    );
    private final List<String> level;
    private final int[][] biomeIndices;
    private final Map<Integer, Integer> biomeSizes;
    private final Map<Integer, Integer> biomeIndexOffsets;
    private final int maxBiomeSize;
    private final int width;
    private final int height;
    private final int numberOfFieldFilledConstraints;

    public App(byte[][] level) {
        this(Helper.getStringList(level));
    }

    public App(List<String> level) {
        this.level = level;
        width = getWidth();
        height = getHeight();
        biomeIndices = new int[height][width];
        biomeSizes = new HashMap<>();
        biomeIndexOffsets = new HashMap<>();
        maxBiomeSize = findMaxBiomeSize();
        numberOfFieldFilledConstraints = width * height;
        determineBiomeIndicesAndSizes();
    }

    public static void main(String[] args) {
        //long start = System.nanoTime();
        new App(LEVEL).run();
        //long durationInNs = System.nanoTime() - start;
        //System.out.println("Took "+durationInNs+" ns.");
    }

    private int findMaxBiomeSize() {
        int maxSize = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int size = findBiomeSize(x, y);
                if (size > maxSize) {
                    maxSize = size;
                }
            }
        }
        return maxSize;
    }

    private int findBiomeSize(int x, int y) {
        boolean[][] visited = new boolean[height][width];
        char biomeType = level.get(y).charAt(x);
        return findBiomeSize(x, y, visited, biomeType);
    }

    private int findBiomeSize(int x, int y, boolean[][] visited, char biomeType) {
        if (visited[y][x]) {
            return 0;
        }
        visited[y][x] = true;
        if (level.get(y).charAt(x) != biomeType) {
            return 0;
        }
        int fields = 1;
        if (x > 0) {
            fields += findBiomeSize(x - 1, y, visited, biomeType);
        }
        if (x < width - 1) {
            fields += findBiomeSize(x + 1, y, visited, biomeType);
        }
        if (y > 0) {
            fields += findBiomeSize(x, y - 1, visited, biomeType);
        }
        if (y < height - 1) {
            fields += findBiomeSize(x, y + 1, visited, biomeType);
        }
        return fields;
    }

    private void determineBiomeIndicesAndSizes() {
        int maxIndex = 0;
        int offset = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (biomeIndices[y][x] == 0) {
                    maxIndex++;
                    int biomeSize = findBiomeSize(x, y);
                    biomeSizes.put(maxIndex, biomeSize);
                    biomeIndexOffsets.put(maxIndex, offset);
                    offset += biomeSize;
                    markBiomeIndices(x, y, level.get(y).charAt(x), maxIndex);
                }
            }
        }
    }

    private void markBiomeIndices(int x, int y, char biomeType, int biomeIndex) {
        if (level.get(y).charAt(x) != biomeType || biomeIndices[y][x] != 0) {
            return;
        }
        biomeIndices[y][x] = biomeIndex;
        if (x > 0) {
            markBiomeIndices(x - 1, y, biomeType, biomeIndex);
        }
        if (x < width - 1) {
            markBiomeIndices(x + 1, y, biomeType, biomeIndex);
        }
        if (y > 0) {
            markBiomeIndices(x, y - 1, biomeType, biomeIndex);
        }
        if (y < height - 1) {
            markBiomeIndices(x, y + 1, biomeType, biomeIndex);
        }
    }

    private int getWidth() {
        return level.get(0).length();
    }

    private int getHeight() {
        return level.size();
    }

    public ArrayList<byte[][]> run() {
        return run(10, true);
    }

    public ArrayList<byte[][]> run(int maxNumberOfSolutionsToStore, boolean countAllSolutions) {
        int secondaryConstraints = (width - 1) * (height - 1) * maxBiomeSize;
        Dlx<ChoiceInfo> dlx = new Dlx<>(numberOfFieldFilledConstraints * 2, secondaryConstraints, maxNumberOfSolutionsToStore, countAllSolutions, 1000000);
        createChoices(dlx);
        dlx.solve();

        ArrayList<byte[][]> result = new ArrayList<>();
        for (List<ChoiceInfo> currentLevel : dlx.getSolutions()) {
            byte[][] level = new byte[height][width];
            for (ChoiceInfo info : currentLevel) {
                level[info.y][info.x] = (byte)info.number;
            }
            result.add(level);
        }

        printSolutions(dlx);

        return result;
    }

    private void printSolutions(Dlx<ChoiceInfo> dlx) {
        if (dlx.getSolutions().isEmpty()) {
            return;
        }

        System.out.println();
        for (String level : this.level) {
            System.out.println(level);
        }
        System.out.println();
        List<List<ChoiceInfo>> solutions = dlx.getSolutions();
        int[][] sol = new int[height][width];
        for (ChoiceInfo info : solutions.get(0)) {
            sol[info.y][info.x] = info.number;
        }
        Helper.printArray(sol);
    }

    private void createChoices(Dlx<ChoiceInfo> dlx) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                for (int number = 0; number < biomeSizes.get(biomeIndices[y][x]); number++) {
                    List<Integer> constraintIndices = new ArrayList<>(6);

                    // field filled constraint
                    constraintIndices.add(y * width + x);

                    // number filled in biome with corresponding index
                    constraintIndices.add(numberOfFieldFilledConstraints + biomeIndexOffsets.get(biomeIndices[y][x]) + number);

                    // secondary constraints preventing same neighbor numbers
                    if (x > 0) {
                        if (y > 0) {
                            constraintIndices.add(2 * numberOfFieldFilledConstraints + (y - 1) * (width - 1) * maxBiomeSize + (x - 1) * maxBiomeSize + number);
                        }
                        if (y < height - 1) {
                            constraintIndices.add(2 * numberOfFieldFilledConstraints + y * (width - 1) * maxBiomeSize + (x - 1) * maxBiomeSize + number);
                        }
                    }
                    if (x < width - 1) {
                        if (y > 0) {
                            constraintIndices.add(2 * numberOfFieldFilledConstraints + (y - 1) * (width - 1) * maxBiomeSize + x * maxBiomeSize + number);
                        }
                        if (y < height - 1) {
                            constraintIndices.add(2 * numberOfFieldFilledConstraints + y * (width - 1) * maxBiomeSize + x * maxBiomeSize + number);
                        }
                    }
                    constraintIndices.sort(Comparator.naturalOrder());
                    dlx.addChoice(new ChoiceInfo(x, y, number + 1), constraintIndices);
                }
            }
        }
    }
}
