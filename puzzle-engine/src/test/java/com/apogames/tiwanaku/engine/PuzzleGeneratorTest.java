package com.apogames.tiwanaku.engine;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PuzzleGeneratorTest {
    @Test
    void generatesAndCertifiesAUniquePuzzleDeterministically() {
        PuzzleGenerator generator = new PuzzleGenerator();
        GenerationSpec spec = spec(4, 4, 5, 2026, new CancellationToken());

        GeneratedPuzzle first = generator.generate(spec);
        GeneratedPuzzle second = generator.generate(spec);

        assertEquals(GenerationStatus.GENERATED, first.status());
        assertEquals(GenerationStatus.GENERATED, second.status());
        assertEquals(UniquenessStatus.PROVEN_UNIQUE,
                new ExactCoverSolver().solve(first.puzzle(), solverOptions(2026)).status());
        assertArrayEquals(first.puzzle().regionIds(), second.puzzle().regionIds());
        assertArrayEquals(first.puzzle().givens(), second.puzzle().givens());
        assertArrayEquals(first.solution(), second.solution());
        assertTrue(BoardRules.isValidCompleteBoard(first.puzzle(), first.solution()));
        assertTrue(countClues(first.puzzle()) > 0);
        assertTrue(first.visitedNodes() <= spec.maxSearchNodes());
    }

    @Test
    void returnsUnknownInsteadOfClaimingSuccessWhenCancelled() {
        CancellationToken cancellation = new CancellationToken();
        cancellation.cancel();

        GeneratedPuzzle result = new PuzzleGenerator().generate(
                spec(4, 4, 5, 8, cancellation));

        assertEquals(GenerationStatus.UNKNOWN, result.status());
        assertEquals(SearchTermination.CANCELLED, result.termination());
        assertNull(result.puzzle());
    }

    @Test
    void generatesTheSingleCellBoundaryWithoutExceedingItsRegionSize() {
        GeneratedPuzzle result = new PuzzleGenerator().generate(
                spec(1, 1, 5, 4, new CancellationToken()));

        assertEquals(GenerationStatus.GENERATED, result.status());
        assertNotNull(result.puzzle());
        assertEquals(1, result.puzzle().regionSize(0));
        assertArrayEquals(new int[]{1}, result.solution());
    }

    @Test
    void generatesEveryLegacyPresetWithARegionOfSizeNine() {
        int[][] presets = {
                {3, 3}, {4, 4}, {5, 5}, {7, 5}, {9, 5}, {15, 10}, {20, 12}
        };

        for (int[] preset : presets) {
            GenerationSpec spec = new GenerationSpec(
                    preset[0],
                    preset[1],
                    9,
                    Difficulty.EASY,
                    2026,
                    100,
                    2_000_000,
                    Duration.ofSeconds(30),
                    new CancellationToken());
            GeneratedPuzzle result = new PuzzleGenerator().generate(spec);

            assertEquals(GenerationStatus.GENERATED, result.status(), preset[0] + "x" + preset[1]);
            assertEquals(9, largestRegion(result.puzzle()), preset[0] + "x" + preset[1]);
            assertTrue(BoardRules.isValidCompleteBoard(result.puzzle(), result.solution()));
        }
    }

    @Test
    void generatesSupportedRegionSizesAcrossEveryPresetAndMultipleSeeds() {
        int[][] presets = {{3, 3}, {4, 4}, {5, 5}, {7, 5}, {9, 5}, {15, 10}, {20, 12}};
        for (int[] preset : presets) {
            for (int maxRegionSize = 5; maxRegionSize <= 9; maxRegionSize++) {
                for (Difficulty difficulty : new Difficulty[]{Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD}) {
                    for (long seed : new long[]{0, 2026, 3045}) {
                        GeneratedPuzzle result = new PuzzleGenerator().generate(new GenerationSpec(
                                preset[0], preset[1], maxRegionSize, difficulty, seed,
                                200, 2_000_000, Duration.ofSeconds(30), new CancellationToken()));
                        String label = preset[0] + "x" + preset[1]
                                + " maxRegion " + maxRegionSize
                                + " difficulty " + difficulty + " seed " + seed;
                        assertEquals(GenerationStatus.GENERATED, result.status(), label);
                        assertRegionSizesWithinBounds(result.puzzle(), maxRegionSize, label);
                        assertTrue(BoardRules.isValidCompleteBoard(result.puzzle(), result.solution()), label);
                        assertTrue(countClues(result.puzzle()) >= difficulty.targetClues(result.puzzle().cellCount()), label);
                        assertEquals(UniquenessStatus.PROVEN_UNIQUE,
                                new ExactCoverSolver().solve(result.puzzle(), solverOptions(seed)).status(), label);
                    }
                }
            }
        }
    }

    @Test
    void generatesSixCellRegionsOnAnEvenTitanBoardWhenMaximumIsSix() {
        GeneratedPuzzle result = new PuzzleGenerator().generate(
                new GenerationSpec(
                        20, 12, 6, Difficulty.EASY, 2026,
                        200, 2_000_000, Duration.ofSeconds(30), new CancellationToken()));

        assertEquals(GenerationStatus.GENERATED, result.status());
        assertEquals(6, largestRegion(result.puzzle()));
    }

    @Test
    void includesFiveCellRegionsOnTitanWhenMaximumIsFive() {
        for (long seed : new long[]{0, 2026, 3045}) {
            GeneratedPuzzle result = new PuzzleGenerator().generate(new GenerationSpec(
                    20, 12, 5, Difficulty.EASY, seed,
                    200, 2_000_000, Duration.ofSeconds(30), new CancellationToken()));

            assertEquals(GenerationStatus.GENERATED, result.status(), "seed " + seed);
            assertEquals(5, largestRegion(result.puzzle()), "seed " + seed);
            assertTrue(BoardRules.isValidCompleteBoard(result.puzzle(), result.solution()), "seed " + seed);
        }
    }

    @Test
    void squareRegionBordersRunInBothDirectionsWithoutStraightBars() {
        int horizontal = 0;
        int vertical = 0;
        int bars = 0;
        int regions = 0;
        for (long seed = 0; seed < 4; seed++) {
            GeneratedPuzzle result = new PuzzleGenerator().generate(new GenerationSpec(
                    20, 12, 5, Difficulty.EASY, seed,
                    200, 2_000_000, Duration.ofSeconds(30), new CancellationToken()));
            assertEquals(GenerationStatus.GENERATED, result.status(), "seed " + seed);
            int[] ids = result.puzzle().regionIds();
            for (int cell = 0; cell < ids.length; cell++) {
                if (cell % 20 < 19 && ids[cell] != ids[cell + 1]) {
                    vertical++;
                }
                if (cell + 20 < ids.length && ids[cell] != ids[cell + 20]) {
                    horizontal++;
                }
            }
            for (int region = 0; region < result.puzzle().regionCount(); region++) {
                java.util.Set<Integer> xs = new java.util.HashSet<>();
                java.util.Set<Integer> ys = new java.util.HashSet<>();
                for (int cell = 0; cell < ids.length; cell++) {
                    if (ids[cell] == region) {
                        xs.add(cell % 20);
                        ys.add(cell / 20);
                    }
                }
                regions++;
                if (xs.size() + ys.size() > 2 && (xs.size() == 1 || ys.size() == 1)) {
                    bars++;
                }
            }
        }
        assertTrue(horizontal > 0 && vertical > 0);
        double ratio = (horizontal / (20.0 * 11)) / (vertical / (19.0 * 12));
        assertTrue(ratio > 0.7 && ratio < 1.4, "horizontal/vertical border ratio " + ratio);
        assertTrue(bars * 10 < regions, bars + " straight bars out of " + regions + " regions");
    }

    @Test
    void neverCertifiesWhenTheGlobalNodeBudgetIsExhausted() {
        GeneratedPuzzle result = new PuzzleGenerator().generate(new GenerationSpec(
                7, 5, 5, Difficulty.EASY, 0,
                200, 1, Duration.ofSeconds(5), new CancellationToken()));
        assertEquals(GenerationStatus.UNKNOWN, result.status());
        assertEquals(SearchTermination.NODE_BUDGET_EXCEEDED, result.termination());
        assertNull(result.puzzle());
        assertNull(result.solution());
        assertTrue(result.visitedNodes() <= 1);
    }

    @Test
    void respectsEverySupportedMaximumRegionSize() {
        for (int maxRegionSize = 5; maxRegionSize <= 9; maxRegionSize++) {
            GeneratedPuzzle result = new PuzzleGenerator().generate(
                    spec(4, 4, maxRegionSize, 2026, new CancellationToken()));

            assertEquals(GenerationStatus.GENERATED, result.status(), "max region " + maxRegionSize);
            assertTrue(largestRegion(result.puzzle()) <= maxRegionSize, "max region " + maxRegionSize);
        }
    }

    private static GenerationSpec spec(
            int columns,
            int rows,
            int maxRegionSize,
            long seed,
            CancellationToken cancellation) {
        return new GenerationSpec(
                columns,
                rows,
                maxRegionSize,
                Difficulty.EASY,
                seed,
                40,
                1_000_000,
                Duration.ofSeconds(4),
                cancellation);
    }

    private static SolverOptions solverOptions(long seed) {
        return new SolverOptions(1_000_000, Duration.ofSeconds(2), seed, new CancellationToken());
    }

    private static int countClues(PuzzleDefinition puzzle) {
        int count = 0;
        for (int given : puzzle.givens()) {
            if (given != 0) {
                count++;
            }
        }
        return count;
    }

    private static int largestRegion(PuzzleDefinition puzzle) {
        int largest = 0;
        for (int region = 0; region < puzzle.regionCount(); region++) {
            largest = Math.max(largest, puzzle.regionSize(region));
        }
        return largest;
    }

    private static void assertRegionSizesWithinBounds(PuzzleDefinition puzzle, int maxRegionSize, String label) {
        for (int region = 0; region < puzzle.regionCount(); region++) {
            assertTrue(puzzle.regionSize(region) >= 1, label + " region " + region + " is empty");
            assertTrue(puzzle.regionSize(region) <= maxRegionSize, label + " region " + region + " exceeds maximum");
        }
    }
}
