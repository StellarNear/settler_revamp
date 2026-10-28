package com.apogames.tiwanaku.engine;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class LogicalSolverTest {
    @Test
    void solvesUsingContradictionEliminationsWithoutAnExactCoverFallback() {
        GeneratedPuzzle generated = new PuzzleGenerator().generate(new GenerationSpec(
                5, 5, 5, Difficulty.EXTREME, 2026, 200, 2_000_000,
                Duration.ofSeconds(10), new CancellationToken()));
        assertEquals(GenerationStatus.GENERATED, generated.status());
        LogicalSolver.Result result = new LogicalSolver().solve(
                generated.puzzle(), options(100_000, new CancellationToken()));
        assertEquals(LogicalSolver.Status.SOLVED, result.status());
        assertTrue(result.contradictionEliminations() > 0);
        assertArrayEquals(generated.solution(), result.values());
        assertEquals(10, LogicalSolver.MAX_HYPOTHESIS_STEPS);
    }

    @Test
    void solvesForcedCellsButDoesNotGuessAnAmbiguousBoard() {
        PuzzleDefinition forced = new PuzzleDefinition(2, 2, new int[]{0, 0, 0, 0}, new int[]{1, 2, 3, 0});
        LogicalSolver.Result result = new LogicalSolver().solve(forced, options(1000, new CancellationToken()));
        assertEquals(LogicalSolver.Status.SOLVED, result.status());
        assertArrayEquals(new int[]{1, 2, 3, 4}, result.values());
        int[] exposed = result.values();
        exposed[0] = 9;
        assertEquals(1, result.values()[0]);
        PuzzleDefinition ambiguous = new PuzzleDefinition(2, 2, new int[]{0, 0, 0, 0}, new int[4]);
        assertEquals(LogicalSolver.Status.STUCK,
                new LogicalSolver().solve(ambiguous, options(1000, new CancellationToken())).status());
    }

    @Test
    void neverCertifiesContradictionsOrIncompleteSearches() {
        PuzzleDefinition impossible = new PuzzleDefinition(2, 1, new int[]{0, 1}, new int[2]);
        assertEquals(LogicalSolver.Status.CONTRADICTION,
                new LogicalSolver().solve(impossible, options(1000, new CancellationToken())).status());
        CancellationToken token = new CancellationToken();
        token.cancel();
        LogicalSolver.Result cancelled = new LogicalSolver().solve(impossible, options(1000, token));
        assertEquals(LogicalSolver.Status.UNKNOWN, cancelled.status());
        assertEquals(SearchTermination.CANCELLED, cancelled.termination());
        PuzzleDefinition forced = new PuzzleDefinition(2, 1, new int[]{0, 0}, new int[]{1, 0});
        LogicalSolver.Result budget = new LogicalSolver().solve(forced, options(1, new CancellationToken()));
        assertEquals(LogicalSolver.Status.UNKNOWN, budget.status());
        assertEquals(SearchTermination.NODE_BUDGET_EXCEEDED, budget.termination());
        LogicalSolver.Result timed = new LogicalSolver().solve(forced,
                new SolverOptions(1000, Duration.ofNanos(1), 0, new CancellationToken()));
        assertEquals(LogicalSolver.Status.UNKNOWN, timed.status());
        assertEquals(SearchTermination.DEADLINE_EXCEEDED, timed.termination());
    }

    @Test
    void everySolvedSmallClueSubsetIsIndependentlyUnique() {
        for (int subset = 0; subset < 16; subset++) {
            int[] givens = new int[4];
            for (int cell = 0; cell < 4; cell++) {
                if ((subset & (1 << cell)) != 0) givens[cell] = cell + 1;
            }
            PuzzleDefinition puzzle = new PuzzleDefinition(2, 2, new int[]{0, 0, 0, 0}, givens);
            LogicalSolver.Result result = new LogicalSolver().solve(puzzle, options(10_000, new CancellationToken()));
            SearchResult exact = new ExactCoverSolver().solve(puzzle, options(10_000, new CancellationToken()));
            if (result.status() == LogicalSolver.Status.SOLVED) {
                assertEquals(UniquenessStatus.PROVEN_UNIQUE, exact.status());
                assertTrue(BoardRules.isValidCompleteBoard(puzzle, result.values()));
            } else {
                assertEquals(LogicalSolver.Status.STUCK, result.status());
                assertEquals(UniquenessStatus.MULTIPLE_SOLUTIONS, exact.status());
            }
        }
    }

    private static SolverOptions options(long nodes, CancellationToken token) {
        return new SolverOptions(nodes, Duration.ofSeconds(2), 0, token);
    }
}
