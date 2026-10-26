package com.apogames.tiwanaku.engine;

import java.util.Arrays;

public final class GeneratedPuzzle {
    private final GenerationStatus status;
    private final SearchTermination termination;
    private final PuzzleDefinition puzzle;
    private final int[] solution;
    private final int partitionAttempts;
    private final long visitedNodes;
    private final long seed;

    private GeneratedPuzzle(
            GenerationStatus status,
            SearchTermination termination,
            PuzzleDefinition puzzle,
            int[] solution,
            int partitionAttempts,
            long visitedNodes,
            long seed) {
        this.status = status;
        this.termination = termination;
        this.puzzle = puzzle;
        this.solution = solution == null ? null : solution.clone();
        this.partitionAttempts = partitionAttempts;
        this.visitedNodes = visitedNodes;
        this.seed = seed;
    }

    static GeneratedPuzzle generated(
            PuzzleDefinition puzzle,
            int[] solution,
            int partitionAttempts,
            long visitedNodes,
            long seed) {
        return new GeneratedPuzzle(
                GenerationStatus.GENERATED,
                SearchTermination.EXHAUSTED,
                puzzle,
                solution,
                partitionAttempts,
                visitedNodes,
                seed);
    }

    static GeneratedPuzzle failed(
            GenerationStatus status,
            SearchTermination termination,
            int partitionAttempts,
            long visitedNodes,
            long seed) {
        return new GeneratedPuzzle(status, termination, null, null, partitionAttempts, visitedNodes, seed);
    }

    public GenerationStatus status() {
        return status;
    }

    public SearchTermination termination() {
        return termination;
    }

    public PuzzleDefinition puzzle() {
        return puzzle;
    }

    public int[] solution() {
        return solution == null ? null : solution.clone();
    }

    public int partitionAttempts() {
        return partitionAttempts;
    }

    public long visitedNodes() {
        return visitedNodes;
    }

    public long seed() {
        return seed;
    }

    @Override
    public String toString() {
        return "GeneratedPuzzle[status=" + status + ", termination=" + termination
                + ", puzzle=" + puzzle + ", solution="
                + (solution == null ? "null" : Arrays.toString(solution)) + "]";
    }
}
