package com.apogames.tiwanaku.engine;

import java.util.Arrays;

/**
 * Singles, region hidden singles, and failed candidates with at most ten forced
 * placements per hypothesis. Hypotheses never contain another hypothesis.
 */
public final class LogicalSolver {
    public static final int MAX_HYPOTHESIS_STEPS = 10;

    public enum Status { SOLVED, STUCK, CONTRADICTION, UNKNOWN }

    public record Result(Status status, int[] values, long visitedNodes,
                         SearchTermination termination, int contradictionEliminations) {
        public Result {
            values = values.clone();
        }

        @Override
        public int[] values() {
            return values.clone();
        }
    }

    public Result solve(PuzzleDefinition puzzle, SolverOptions options) {
        return new Search(puzzle, options).run();
    }

    private static final class State {
        private final int[] values;
        private final int[] excluded;

        private State(int[] values, int[] excluded) {
            this.values = values;
            this.excluded = excluded;
        }

        private State copy() {
            return new State(values.clone(), excluded.clone());
        }
    }

    private record Step(boolean contradiction, int cell, int value, int[] candidates) { }

    private static final class Search {
        private final PuzzleDefinition puzzle;
        private final SolverOptions options;
        private final long started = System.nanoTime();
        private final long timeoutNanos;
        private final int[][] members;
        private long nodes;
        private int eliminations;
        private SearchTermination termination;

        private Search(PuzzleDefinition puzzle, SolverOptions options) {
            this.puzzle = puzzle;
            this.options = options;
            long nanos;
            try {
                nanos = options.timeout().toNanos();
            } catch (ArithmeticException exception) {
                nanos = Long.MAX_VALUE;
            }
            timeoutNanos = nanos;
            members = new int[puzzle.regionCount()][];
            for (int region = 0; region < members.length; region++) {
                members[region] = new int[puzzle.regionSize(region)];
            }
            int[] counts = new int[members.length];
            for (int cell = 0; cell < puzzle.cellCount(); cell++) {
                int region = puzzle.regionIdAt(cell);
                members[region][counts[region]++] = cell;
            }
        }

        private Result run() {
            State state = new State(puzzle.givens(), new int[puzzle.cellCount()]);
            while (true) {
                Step step = next(state);
                if (termination != null) {
                    return result(Status.UNKNOWN, state);
                }
                if (step.contradiction()) {
                    return result(Status.CONTRADICTION, state);
                }
                if (step.cell() >= 0) {
                    state.values[step.cell()] = step.value();
                    continue;
                }
                if (Arrays.stream(state.values).noneMatch(value -> value == 0)) {
                    return result(Status.SOLVED, state);
                }
                boolean eliminated = false;
                // Small domains first, with stable cell ordering for reproducible verification.
                for (int size = 2; size <= PuzzleDefinition.MAX_REGION_SIZE && !eliminated; size++) {
                    for (int cell = 0; cell < puzzle.cellCount() && !eliminated; cell++) {
                        int choices = step.candidates()[cell];
                        if (state.values[cell] != 0 || Integer.bitCount(choices) != size) {
                            continue;
                        }
                        while (choices != 0 && !eliminated) {
                            int bit = Integer.lowestOneBit(choices);
                            choices &= ~bit;
                            State trial = state.copy();
                            trial.values[cell] = Integer.numberOfTrailingZeros(bit) + 1;
                            if (contradicts(trial)) {
                                state.excluded[cell] |= bit;
                                eliminations++;
                                eliminated = true;
                            }
                            if (termination != null) {
                                return result(Status.UNKNOWN, state);
                            }
                        }
                    }
                }
                if (!eliminated) {
                    return result(Status.STUCK, state);
                }
            }
        }

        private boolean contradicts(State trial) {
            for (int placed = 0; placed <= MAX_HYPOTHESIS_STEPS; placed++) {
                Step step = next(trial);
                if (termination != null) {
                    return false;
                }
                if (step.contradiction()) {
                    return true;
                }
                if (step.cell() < 0 || placed == MAX_HYPOTHESIS_STEPS) {
                    return false;
                }
                trial.values[step.cell()] = step.value();
            }
            return false;
        }

        private Step next(State state) {
            if (options.cancellationToken().isCancelled()) {
                termination = SearchTermination.CANCELLED;
            } else if (Thread.currentThread().isInterrupted()) {
                termination = SearchTermination.THREAD_INTERRUPTED;
            } else if (nodes >= options.maxSearchNodes()) {
                termination = SearchTermination.NODE_BUDGET_EXCEEDED;
            } else if (System.nanoTime() - started >= timeoutNanos) {
                termination = SearchTermination.DEADLINE_EXCEEDED;
            }
            if (termination != null) {
                return new Step(false, -1, 0, null);
            }
            nodes++;
            int[] candidates = new int[puzzle.cellCount()];
            for (int cell = 0; cell < candidates.length; cell++) {
                int mask = (1 << puzzle.regionSizeAt(cell)) - 1;
                mask &= ~state.excluded[cell];
                for (int peer : members[puzzle.regionIdAt(cell)]) {
                    if (peer != cell && state.values[peer] != 0) {
                        mask &= ~(1 << (state.values[peer] - 1));
                    }
                }
                for (int peer : puzzle.topology().conflictNeighbors(cell)) {
                    if (state.values[peer] != 0) {
                        mask &= ~(1 << (state.values[peer] - 1));
                    }
                }
                if (state.values[cell] != 0) {
                    mask &= 1 << (state.values[cell] - 1);
                }
                if (mask == 0) {
                    return new Step(true, -1, 0, candidates);
                }
                candidates[cell] = mask;
            }
            int forcedCell = -1;
            int forcedValue = 0;
            for (int cell = 0; cell < candidates.length; cell++) {
                if (state.values[cell] == 0 && Integer.bitCount(candidates[cell]) == 1) {
                    forcedCell = cell;
                    forcedValue = Integer.numberOfTrailingZeros(candidates[cell]) + 1;
                    break;
                }
            }
            for (int[] region : members) {
                for (int value = 1; value <= region.length; value++) {
                    int count = 0;
                    int position = -1;
                    for (int cell : region) {
                        if ((candidates[cell] & (1 << (value - 1))) != 0) {
                            count++;
                            position = cell;
                        }
                    }
                    if (count == 0) {
                        return new Step(true, -1, 0, candidates);
                    }
                    if (count == 1 && state.values[position] == 0 && forcedCell < 0) {
                        forcedCell = position;
                        forcedValue = value;
                    }
                }
            }
            return new Step(false, forcedCell, forcedValue, candidates);
        }

        private Result result(Status status, State state) {
            return new Result(status, state.values, nodes, termination, eliminations);
        }
    }
}
