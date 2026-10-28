package com.apogames.tiwanaku.engine;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class MinimalDifficultyTest {
    @Test
    void everySquarePresetAndRegionMaximumSupportsBothMinimalModes() {
        int[][] presets = {{3, 3}, {4, 4}, {5, 5}, {7, 5}, {9, 5}, {15, 10}, {20, 12}};
        for (int[] preset : presets) {
            for (int max = 5; max <= 9; max++) {
                for (Difficulty difficulty : new Difficulty[]{Difficulty.EXTREME, Difficulty.GODLIKE}) {
                    GeneratedPuzzle result = generate(TileShape.SQUARE, preset[0], preset[1], max, difficulty, 0);
                    String label = preset[0] + "x" + preset[1] + " " + difficulty + " max=" + max;
                    assertEquals(GenerationStatus.GENERATED, result.status(), label + " " + result.termination());
                    assertMinimal(result.puzzle(), difficulty, label);
                    assertTrue(BoardRules.isValidCompleteBoard(result.puzzle(), result.solution()), label);
                    for (int region = 0; region < result.puzzle().regionCount(); region++) {
                        assertTrue(result.puzzle().regionSize(region) <= max, label);
                    }
                }
            }
        }
    }

    @Test
    void minimalModesNeverAcceptAnUnfinishedProof() {
        for (Difficulty difficulty : new Difficulty[]{Difficulty.EXTREME, Difficulty.GODLIKE}) {
            for (boolean cancelled : new boolean[]{false, true}) {
                CancellationToken token = new CancellationToken();
                if (cancelled) token.cancel();
                GeneratedPuzzle result = new PuzzleGenerator().generate(new GenerationSpec(
                        5, 5, 5, difficulty, 0, 200, 1, Duration.ofSeconds(2), token));
                assertEquals(GenerationStatus.UNKNOWN, result.status());
                assertEquals(cancelled ? SearchTermination.CANCELLED : SearchTermination.NODE_BUDGET_EXCEEDED,
                        result.termination());
                assertNull(result.puzzle());
            }
        }
    }

    @Test
    void extremeAndGodlikeAreUniqueAndIndividuallyClueMinimalAcrossShapes() {
        for (TileShape shape : TileShape.values()) {
            for (Difficulty difficulty : new Difficulty[]{Difficulty.EXTREME, Difficulty.GODLIKE}) {
                for (int max = 5; max <= 9; max++) {
                    GeneratedPuzzle result = generate(shape, 5, 5, max, difficulty, 2026);
                    String label = shape + " " + difficulty + " max=" + max;
                    assertEquals(GenerationStatus.GENERATED, result.status(), label + " " + result.termination());
                    assertMinimal(result.puzzle(), difficulty, label);
                    assertTrue(BoardRules.isValidCompleteBoard(result.puzzle(), result.solution()), label);
                }
            }
        }
    }

    @Test
    void titanExtremeAndGodlikeCompleteWithinOneMinuteWithTheirFullGuarantees() {
        for (Difficulty difficulty : new Difficulty[]{Difficulty.EXTREME, Difficulty.GODLIKE}) {
            for (int max : new int[]{5, 6, 9}) {
                long started = System.nanoTime();
                GeneratedPuzzle result = generate(TileShape.SQUARE, 20, 12, max, difficulty, 7);
                long millis = (System.nanoTime() - started) / 1_000_000;
                String label = "Titan " + difficulty + " max=" + max;
                System.out.printf("%s status=%s termination=%s timeMs=%d nodes=%d clues=%d%n",
                        label, result.status(), result.termination(), millis, result.visitedNodes(),
                        result.puzzle() == null ? -1
                                : Arrays.stream(result.puzzle().givens()).filter(v -> v != 0).count());
                assertEquals(GenerationStatus.GENERATED, result.status(), label);
                assertTrue(millis < 60_000, label);
                assertMinimal(result.puzzle(), difficulty, label);
            }
        }
    }

    private static GeneratedPuzzle generate(TileShape shape, int columns, int rows, int max,
                                            Difficulty difficulty, long seed) {
        long started = System.nanoTime();
        long totalNanos = Duration.ofSeconds(60).toNanos();
        long trySeed = seed;
        int tries = 0;
        GeneratedPuzzle result;
        do {
            long remaining = totalNanos - (System.nanoTime() - started);
            result = new PuzzleGenerator().generate(new GenerationSpec(shape, columns, rows, max, difficulty,
                    trySeed, 200, 4_000_000, Duration.ofNanos(Math.max(1,
                            Math.min(Duration.ofSeconds(20).toNanos(), remaining))), new CancellationToken()));
            tries++;
            if (result.status() == GenerationStatus.GENERATED) {
                if (tries > 1) {
                    System.out.printf("Retried %s %dx%d max=%d %s seed=%d tries=%d timeMs=%d%n",
                            shape, columns, rows, max, difficulty, seed, tries,
                            (System.nanoTime() - started) / 1_000_000);
                }
                return result;
            }
            trySeed = seed ^ (tries * 0x9E3779B97F4A7C15L);
        } while (System.nanoTime() - started < totalNanos - Duration.ofSeconds(2).toNanos());
        return result;
    }

    private static void assertMinimal(PuzzleDefinition puzzle, Difficulty difficulty, String label) {
        assertEquals(UniquenessStatus.PROVEN_UNIQUE,
                new ExactCoverSolver().solve(puzzle, options()).status(), label);
        if (difficulty == Difficulty.EXTREME) {
            LogicalSolver.Result logical = new LogicalSolver().solve(puzzle, options());
            assertEquals(LogicalSolver.Status.SOLVED, logical.status(), label);
            assertTrue(BoardRules.isValidCompleteBoard(puzzle, logical.values()), label);
            if (logical.contradictionEliminations() > 0) {
                System.out.printf("%s boundedContradictions=%d%n", label, logical.contradictionEliminations());
            }
        }
        int[] givens = puzzle.givens();
        for (int cell = 0; cell < givens.length; cell++) {
            if (givens[cell] == 0) continue;
            int previous = givens[cell];
            givens[cell] = 0;
            PuzzleDefinition candidate = new PuzzleDefinition(
                    puzzle.shape(), puzzle.columns(), puzzle.rows(), puzzle.regionIds(), givens);
            SearchResult uniqueness = new ExactCoverSolver().solve(candidate, options());
            assertNotEquals(UniquenessStatus.UNKNOWN, uniqueness.status(), label + " cell=" + cell);
            if (uniqueness.status() == UniquenessStatus.PROVEN_UNIQUE) {
                assertEquals(Difficulty.EXTREME, difficulty, label + " removable clue " + cell);
                assertEquals(LogicalSolver.Status.STUCK, new LogicalSolver().solve(candidate, options()).status(),
                        label + " logically removable clue " + cell);
            } else {
                assertEquals(UniquenessStatus.MULTIPLE_SOLUTIONS, uniqueness.status(), label);
            }
            givens[cell] = previous;
        }
    }

    private static SolverOptions options() {
        return new SolverOptions(8_000_000, Duration.ofSeconds(30), 7, new CancellationToken());
    }
}
