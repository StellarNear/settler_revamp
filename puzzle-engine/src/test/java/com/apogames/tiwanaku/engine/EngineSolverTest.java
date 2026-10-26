package com.apogames.tiwanaku.engine;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EngineSolverTest {
    private final ExactCoverSolver solver = new ExactCoverSolver();

    @Test
    public void provesUniqueSolutionWhenSearchIsExhaustive() {
        PuzzleDefinition puzzle = puzzle(1, 1, new int[]{0}, new int[]{0});

        SearchResult result = solver.solve(puzzle, options(7));

        assertEquals(UniquenessStatus.PROVEN_UNIQUE, result.status());
        assertEquals(SearchTermination.EXHAUSTED, result.termination());
        assertArrayEquals(new int[]{1}, result.solutions().get(0));
        assertTrue(BoardRules.isValidCompleteBoard(puzzle, result.solutions().get(0)));
    }

    @Test
    public void stopsAfterTwoSolutionsAndNeverRetainsMoreThanTwo() {
        PuzzleDefinition puzzle = puzzle(2, 2, new int[]{0, 0, 0, 0}, new int[]{0, 0, 0, 0});

        SearchResult result = solver.solve(puzzle, options(19));

        assertEquals(UniquenessStatus.MULTIPLE_SOLUTIONS, result.status());
        assertEquals(SearchTermination.TWO_SOLUTIONS_FOUND, result.termination());
        assertEquals(2, result.solutions().size());
        assertEquals(24, countSolutionsIndependently(puzzle));
        assertTrue(result.solutions().stream().allMatch(values -> isCompleteIndependently(puzzle, values)));
    }

    @Test
    public void reportsUnsatisfiableAdjacentSingletonRegions() {
        PuzzleDefinition puzzle = puzzle(2, 1, new int[]{0, 1}, new int[]{0, 0});

        SearchResult result = solver.solve(puzzle, options(1));

        assertEquals(UniquenessStatus.NO_SOLUTION, result.status());
        assertEquals(SearchTermination.EXHAUSTED, result.termination());
        assertTrue(result.solutions().isEmpty());
        assertEquals(0, countSolutionsIndependently(puzzle));
    }

    @Test
    public void returnsUnknownWhenCancellationIsRequestedBeforeSearch() {
        CancellationToken cancellation = new CancellationToken();
        cancellation.cancel();

        SearchResult result = solver.solve(
                puzzle(1, 1, new int[]{0}, new int[]{0}),
                new SolverOptions(100, Duration.ofSeconds(1), 1, cancellation));

        assertEquals(UniquenessStatus.UNKNOWN, result.status());
        assertEquals(SearchTermination.CANCELLED, result.termination());
        assertTrue(result.solutions().isEmpty());
    }

    @Test
    public void returnsUnknownWhenNodeBudgetExpiresInsideRecursiveSearch() {
        SearchResult result = solver.solve(
                puzzle(2, 2, new int[]{0, 0, 0, 0}, new int[]{0, 0, 0, 0}),
                new SolverOptions(1, Duration.ofSeconds(1), 2, new CancellationToken()));

        assertEquals(UniquenessStatus.UNKNOWN, result.status());
        assertEquals(SearchTermination.NODE_BUDGET_EXCEEDED, result.termination());
        assertEquals(1, result.visitedNodes());
        assertTrue(result.solutions().isEmpty());
    }

    @Test
    public void seedRepeatsSearchOrderingAndSolutions() {
        PuzzleDefinition puzzle = puzzle(2, 2, new int[]{0, 0, 0, 0}, new int[]{0, 0, 0, 0});

        SearchResult first = solver.solve(puzzle, options(2026));
        SearchResult second = solver.solve(puzzle, options(2026));

        assertEquals(first.status(), second.status());
        assertArrayEquals(first.solutions().getFirst(), second.solutions().getFirst());
        assertArrayEquals(first.solutions().get(1), second.solutions().get(1));
    }

    @Test
    public void validatesCompletionAndRejectsMutationOfReturnedArrays() {
        PuzzleDefinition puzzle = puzzle(2, 1, new int[]{0, 0}, new int[]{0, 0});
        assertFalse(BoardRules.isValidCompleteBoard(puzzle, new int[]{1, 1}));
        assertFalse(BoardRules.isValidPartialBoard(puzzle, new int[]{1, 1}));
        assertTrue(BoardRules.isValidCompleteBoard(puzzle, new int[]{1, 2}));
        assertEquals(2, countSolutionsIndependently(puzzle));

        SearchResult result = solver.solve(puzzle, options(5));
        int[] exposed = result.solutions().get(0);
        int[] expected = exposed.clone();
        exposed[0] = 99;
        assertArrayEquals(expected, result.solutions().get(0));
    }

    @Test
    public void rejectsDisconnectedRegionsAndPreservesDefinitionInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> puzzle(3, 1, new int[]{0, 1, 0}, new int[]{0, 0, 0}));

        int[] regionIds = {0};
        int[] givens = {1};
        PuzzleDefinition puzzle = puzzle(1, 1, regionIds, givens);
        regionIds[0] = 5;
        givens[0] = 0;
        assertArrayEquals(new int[]{0}, puzzle.regionIds());
        assertArrayEquals(new int[]{1}, puzzle.givens());
    }

    @Test
    public void attemptStateSeparatesMutableValuesFromImmutablePuzzleAndEnforcesNotes() {
        PuzzleDefinition puzzle = puzzle(2, 1, new int[]{0, 0}, new int[]{1, 0});
        AttemptState initial = AttemptState.start(puzzle);
        AttemptState entered = initial.withValue(1, 2).withNoteMask(1, 1);

        assertEquals(1, initial.valueAt(0));
        assertEquals(0, initial.valueAt(1));
        assertEquals(2, entered.valueAt(1));
        assertEquals(1, entered.noteMaskAt(1));
        assertEquals(2, entered.revision());
        assertThrows(IllegalArgumentException.class, () -> initial.withValue(0, 2));
        assertThrows(IllegalArgumentException.class, () -> initial.withNoteMask(1, 4));
        assertFalse(Arrays.equals(initial.values(), entered.values()));
    }

    private static PuzzleDefinition puzzle(int columns, int rows, int[] regionIds, int[] givens) {
        return new PuzzleDefinition(columns, rows, regionIds, givens);
    }

    private static SolverOptions options(long seed) {
        return new SolverOptions(100_000, Duration.ofSeconds(2), seed, new CancellationToken());
    }

    private static int countSolutionsIndependently(PuzzleDefinition puzzle) {
        return countSolutionsIndependently(puzzle, new int[puzzle.cellCount()], 0);
    }

    private static int countSolutionsIndependently(PuzzleDefinition puzzle, int[] values, int cell) {
        if (cell == values.length) {
            return isCompleteIndependently(puzzle, values) ? 1 : 0;
        }
        int firstValue = puzzle.givenAt(cell) == 0 ? 1 : puzzle.givenAt(cell);
        int lastValue = puzzle.givenAt(cell) == 0 ? puzzle.regionSizeAt(cell) : puzzle.givenAt(cell);
        int count = 0;
        for (int value = firstValue; value <= lastValue; value++) {
            values[cell] = value;
            if (isPartialIndependently(puzzle, values, cell)) {
                count += countSolutionsIndependently(puzzle, values, cell + 1);
            }
        }
        values[cell] = 0;
        return count;
    }

    private static boolean isCompleteIndependently(PuzzleDefinition puzzle, int[] values) {
        if (values.length != puzzle.cellCount()) {
            return false;
        }
        for (int value : values) {
            if (value == 0) {
                return false;
            }
        }
        return isPartialIndependently(puzzle, values, values.length - 1);
    }

    private static boolean isPartialIndependently(PuzzleDefinition puzzle, int[] values, int lastAssignedCell) {
        for (int cell = 0; cell <= lastAssignedCell; cell++) {
            int value = values[cell];
            if (value == 0) {
                continue;
            }
            int region = puzzle.regionIdAt(cell);
            if (value < 1 || value > puzzle.regionSize(region)
                    || (puzzle.givenAt(cell) != 0 && puzzle.givenAt(cell) != value)) {
                return false;
            }
            for (int previous = 0; previous < cell; previous++) {
                if (values[previous] == value
                        && (puzzle.regionIdAt(previous) == region || areKingNeighbors(puzzle, previous, cell))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean areKingNeighbors(PuzzleDefinition puzzle, int first, int second) {
        int firstX = first % puzzle.columns();
        int firstY = first / puzzle.columns();
        int secondX = second % puzzle.columns();
        int secondY = second / puzzle.columns();
        return Math.abs(firstX - secondX) <= 1 && Math.abs(firstY - secondY) <= 1;
    }
}
