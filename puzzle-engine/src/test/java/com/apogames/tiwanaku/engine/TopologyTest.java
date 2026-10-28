package com.apogames.tiwanaku.engine;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopologyTest {
    private static final int[][] PRESETS = {{1, 1}, {2, 1}, {3, 3}, {4, 4}, {5, 5}, {7, 5}, {9, 5}, {15, 10}, {20, 12}};
    private static final TileShape[] OTHER_SHAPES = {TileShape.HEXAGON, TileShape.TRIANGLE, TileShape.PENTAGON};

    @Test
    void squareTopologyKeepsTheOriginalGridNeighbourhoods() {
        Topology topology = Topology.of(TileShape.SQUARE, 3, 3);

        assertArrayEquals(new int[]{3, 5, 1, 7}, topology.regionNeighbors(4));
        assertArrayEquals(new int[]{0, 1, 2, 3, 5, 6, 7, 8}, topology.conflictNeighbors(4));
        assertArrayEquals(new int[]{1, 3}, topology.regionNeighbors(0));
        assertArrayEquals(new int[]{1, 3, 4}, topology.conflictNeighbors(0));
        assertSame(topology, Topology.of(TileShape.SQUARE, 3, 3));
    }

    @Test
    void everyShapeBuildsACompactConnectedSymmetricBoardOfTheRequestedSize() {
        for (TileShape shape : TileShape.values()) {
            for (int[] preset : PRESETS) {
                Topology topology = Topology.of(shape, preset[0], preset[1]);
                String label = shape + " " + preset[0] + "x" + preset[1];
                int cells = preset[0] * preset[1];
                assertEquals(cells, topology.cellCount(), label);
                for (int cell = 0; cell < cells; cell++) {
                    for (int side : topology.regionNeighbors(cell)) {
                        assertTrue(contains(topology.regionNeighbors(side), cell), label + " side symmetry " + cell);
                        assertTrue(contains(topology.conflictNeighbors(cell), side), label + " sides conflict " + cell);
                    }
                    for (int other : topology.conflictNeighbors(cell)) {
                        assertTrue(other != cell, label);
                        assertTrue(contains(topology.conflictNeighbors(other), cell), label + " conflict symmetry");
                    }
                    double[] polygon = topology.polygon(cell);
                    assertEquals(sidesOf(shape) * 2, polygon.length, label);
                    assertTrue(area(polygon) > 0.1, label + " area " + cell);
                }
                assertEquals(cells, reachable(topology), label + " connected");
                if (cells >= 25) {
                    assertTrue(averageSides(topology) > sidesOf(shape) * 0.6, label + " compact");
                }
            }
        }
    }

    @Test
    void generatesUniqueValidPuzzlesOnEveryShapePresetAndMaximumRegionSize() {
        for (TileShape shape : OTHER_SHAPES) {
            for (int[] preset : new int[][]{{3, 3}, {7, 5}, {20, 12}}) {
                for (int maxRegionSize : new int[]{5, 9}) {
                    for (Difficulty difficulty : new Difficulty[]{Difficulty.EASY, Difficulty.HARD}) {
                        GenerationSpec spec = new GenerationSpec(
                                shape, preset[0], preset[1], maxRegionSize, difficulty, 2026,
                                200, 2_000_000, Duration.ofSeconds(30), new CancellationToken());
                        GeneratedPuzzle result = new PuzzleGenerator().generate(spec);
                        String label = shape + " " + preset[0] + "x" + preset[1] + " max " + maxRegionSize
                                + " " + difficulty;

                        assertEquals(GenerationStatus.GENERATED, result.status(), label);
                        assertEquals(shape, result.puzzle().shape(), label);
                        assertTrue(BoardRules.isValidCompleteBoard(result.puzzle(), result.solution()), label);
                        for (int region = 0; region < result.puzzle().regionCount(); region++) {
                            assertTrue(result.puzzle().regionSize(region) <= maxRegionSize, label);
                        }
                        assertEquals(UniquenessStatus.PROVEN_UNIQUE, new ExactCoverSolver().solve(
                                result.puzzle(),
                                new SolverOptions(2_000_000, Duration.ofSeconds(30), 1, new CancellationToken()))
                                .status(), label);
                    }
                }
            }
        }
    }

    @Test
    void puzzleDefinitionRejectsRegionsThatAreNotConnectedThroughSides() {
        Topology topology = Topology.of(TileShape.HEXAGON, 3, 3);
        int[] regions = new int[9];
        Arrays.fill(regions, 1);
        int lonely = 0;
        int far = -1;
        for (int cell = 1; cell < 9 && far < 0; cell++) {
            if (!contains(topology.regionNeighbors(lonely), cell)) {
                far = cell;
            }
        }
        regions[lonely] = 0;
        regions[far] = 0;
        int farCell = far;
        assertThrows(IllegalArgumentException.class,
                () -> new PuzzleDefinition(TileShape.HEXAGON, 3, 3, regions, new int[9]), "cell " + farCell);
    }

    private static int sidesOf(TileShape shape) {
        return switch (shape) {
            case SQUARE -> 4;
            case HEXAGON -> 6;
            case TRIANGLE -> 3;
            case PENTAGON -> 5;
        };
    }

    private static double averageSides(Topology topology) {
        double total = 0;
        for (int cell = 0; cell < topology.cellCount(); cell++) {
            total += topology.regionNeighbors(cell).length;
        }
        return total / topology.cellCount();
    }

    private static int reachable(Topology topology) {
        boolean[] seen = new boolean[topology.cellCount()];
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        pending.add(0);
        seen[0] = true;
        int count = 0;
        while (!pending.isEmpty()) {
            int cell = pending.poll();
            count++;
            for (int side : topology.regionNeighbors(cell)) {
                if (!seen[side]) {
                    seen[side] = true;
                    pending.add(side);
                }
            }
        }
        return count;
    }

    private static double area(double[] polygon) {
        double sum = 0;
        for (int i = 0; i < polygon.length; i += 2) {
            int j = (i + 2) % polygon.length;
            sum += polygon[i] * polygon[j + 1] - polygon[j] * polygon[i + 1];
        }
        return Math.abs(sum) / 2;
    }

    private static boolean contains(int[] values, int value) {
        for (int candidate : values) {
            if (candidate == value) {
                return true;
            }
        }
        return false;
    }
}
