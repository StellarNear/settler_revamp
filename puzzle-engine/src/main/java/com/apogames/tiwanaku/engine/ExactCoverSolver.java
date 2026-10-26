package com.apogames.tiwanaku.engine;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

public final class ExactCoverSolver {
    private static final int MAX_SOLUTION_COUNT = 2;

    public SearchResult solve(PuzzleDefinition puzzle, SolverOptions options) {
        long startedAt = System.nanoTime();
        long timeoutNanos = toNanosSaturated(options.timeout());
        Search search = new Search(puzzle, options, startedAt, timeoutNanos);
        search.run();
        return search.result();
    }

    private static long toNanosSaturated(Duration timeout) {
        try {
            return timeout.toNanos();
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static final class Search {
        private final PuzzleDefinition puzzle;
        private final SolverOptions options;
        private final long startedAt;
        private final long timeoutNanos;
        private final Matrix matrix;
        private final int[] currentValues;
        private final List<int[]> solutions = new ArrayList<>(MAX_SOLUTION_COUNT);
        private long visitedNodes;
        private SearchTermination termination;

        private Search(PuzzleDefinition puzzle, SolverOptions options, long startedAt, long timeoutNanos) {
            this.puzzle = puzzle;
            this.options = options;
            this.startedAt = startedAt;
            this.timeoutNanos = timeoutNanos;
            this.matrix = Matrix.create(puzzle, options.seed());
            this.currentValues = new int[puzzle.cellCount()];
        }

        private void run() {
            if (options.cancellationToken().isCancelled()) {
                termination = SearchTermination.CANCELLED;
                return;
            }
            if (Thread.currentThread().isInterrupted()) {
                termination = SearchTermination.THREAD_INTERRUPTED;
                return;
            }
            search(0);
            if (termination == null) {
                termination = SearchTermination.EXHAUSTED;
            }
        }

        private boolean search(int depth) {
            if (solutions.size() == MAX_SOLUTION_COUNT) {
                termination = SearchTermination.TWO_SOLUTIONS_FOUND;
                return true;
            }
            if (options.cancellationToken().isCancelled()) {
                termination = SearchTermination.CANCELLED;
                return true;
            }
            if (Thread.currentThread().isInterrupted()) {
                termination = SearchTermination.THREAD_INTERRUPTED;
                return true;
            }
            if (visitedNodes >= options.maxSearchNodes()) {
                termination = SearchTermination.NODE_BUDGET_EXCEEDED;
                return true;
            }
            if (System.nanoTime() - startedAt >= timeoutNanos) {
                termination = SearchTermination.DEADLINE_EXCEEDED;
                return true;
            }
            visitedNodes++;

            if (matrix.remainingPrimary == 0) {
                solutions.add(currentValues.clone());
                if (solutions.size() == MAX_SOLUTION_COUNT) {
                    termination = SearchTermination.TWO_SOLUTIONS_FOUND;
                    return true;
                }
                return false;
            }

            int column = matrix.selectPrimaryColumn();
            if (column == 0 || matrix.columnSize[column] == 0) {
                return false;
            }
            matrix.cover(column);
            int row = matrix.down[column];
            while (row != column) {
                int assignment = matrix.rowAssignment[row];
                int cell = assignment / 10;
                currentValues[cell] = assignment % 10;
                for (int node = matrix.right[row]; node != row; node = matrix.right[node]) {
                    matrix.cover(matrix.column[node]);
                }
                boolean stop = search(depth + 1);
                for (int node = matrix.left[row]; node != row; node = matrix.left[node]) {
                    matrix.uncover(matrix.column[node]);
                }
                currentValues[cell] = 0;

                if (stop) {
                    break;
                }
                row = matrix.down[row];
            }
            matrix.uncover(column);
            return termination == SearchTermination.TWO_SOLUTIONS_FOUND
                    || termination == SearchTermination.CANCELLED
                    || termination == SearchTermination.NODE_BUDGET_EXCEEDED
                    || termination == SearchTermination.DEADLINE_EXCEEDED
                    || termination == SearchTermination.THREAD_INTERRUPTED;
        }

        private SearchResult result() {
            UniquenessStatus status;
            if (solutions.size() >= MAX_SOLUTION_COUNT) {
                status = UniquenessStatus.MULTIPLE_SOLUTIONS;
            } else if (termination != SearchTermination.EXHAUSTED) {
                status = UniquenessStatus.UNKNOWN;
            } else if (solutions.isEmpty()) {
                status = UniquenessStatus.NO_SOLUTION;
            } else {
                status = UniquenessStatus.PROVEN_UNIQUE;
            }
            return new SearchResult(status, termination, visitedNodes, solutions);
        }
    }

    private static final class Matrix {
        private final int[] left;
        private final int[] right;
        private final int[] up;
        private final int[] down;
        private final int[] column;
        private final int[] columnSize;
        private final int[] rowAssignment;
        private final boolean[] primary;
        private final int root;
        private int nextNode;
        private int remainingPrimary;

        private Matrix(int columnCount, int nodeCapacity, int primaryColumnCount) {
            this.root = 0;
            this.left = new int[nodeCapacity];
            this.right = new int[nodeCapacity];
            this.up = new int[nodeCapacity];
            this.down = new int[nodeCapacity];
            this.column = new int[nodeCapacity];
            this.columnSize = new int[columnCount + 1];
            this.rowAssignment = new int[nodeCapacity];
            this.primary = new boolean[columnCount + 1];
            this.remainingPrimary = primaryColumnCount;

            for (int header = 1; header <= columnCount; header++) {
                left[header] = header - 1;
                right[header] = header + 1;
                up[header] = header;
                down[header] = header;
                column[header] = header;
                primary[header] = header - 1 < primaryColumnCount;
            }
            right[root] = columnCount == 0 ? root : 1;
            left[root] = columnCount == 0 ? root : columnCount;
            right[columnCount] = root;
            left[1] = root;
            nextNode = columnCount + 1;
        }

        private static Matrix create(PuzzleDefinition puzzle, long seed) {
            int cellCount = puzzle.cellCount();
            int[] regionOffsets = new int[puzzle.regionCount()];
            int regionValueColumns = 0;
            for (int region = 0; region < puzzle.regionCount(); region++) {
                regionOffsets[region] = regionValueColumns;
                regionValueColumns += puzzle.regionSize(region);
            }

            List<int[]> edges = new ArrayList<>();
            List<List<Integer>> edgesByCell = new ArrayList<>(cellCount);
            for (int cell = 0; cell < cellCount; cell++) {
                edgesByCell.add(new ArrayList<>(8));
            }
            for (int cell = 0; cell < cellCount; cell++) {
                int x = cell % puzzle.columns();
                int y = cell / puzzle.columns();
                for (int neighborY = Math.max(0, y - 1); neighborY <= Math.min(puzzle.rows() - 1, y + 1); neighborY++) {
                    for (int neighborX = Math.max(0, x - 1); neighborX <= Math.min(puzzle.columns() - 1, x + 1); neighborX++) {
                        int neighbor = neighborY * puzzle.columns() + neighborX;
                        if (neighbor > cell) {
                            int edgeIndex = edges.size();
                            edges.add(new int[]{cell, neighbor});
                            edgesByCell.get(cell).add(edgeIndex);
                            edgesByCell.get(neighbor).add(edgeIndex);
                        }
                    }
                }
            }

            int primaryColumnCount = 2 * cellCount;
            int columnCount = primaryColumnCount + 9 * edges.size();
            List<int[]> constraintsByRow = new ArrayList<>();
            List<Integer> assignments = new ArrayList<>();
            SplittableRandom random = new SplittableRandom(seed);
            for (int cell = 0; cell < cellCount; cell++) {
                int region = puzzle.regionIdAt(cell);
                int regionSize = puzzle.regionSize(region);
                int given = puzzle.givenAt(cell);
                int firstValue = given == 0 ? 1 : given;
                int lastValue = given == 0 ? regionSize : given;
                int[] valueOrder = new int[lastValue - firstValue + 1];
                for (int index = 0; index < valueOrder.length; index++) {
                    valueOrder[index] = firstValue + index;
                }
                for (int index = valueOrder.length - 1; index > 0; index--) {
                    int swapWith = random.nextInt(index + 1);
                    int value = valueOrder[index];
                    valueOrder[index] = valueOrder[swapWith];
                    valueOrder[swapWith] = value;
                }
                for (int value : valueOrder) {
                    int[] constraints = new int[2 + edgesByCell.get(cell).size()];
                    constraints[0] = cell;
                    constraints[1] = cellCount + regionOffsets[region] + value - 1;
                    int index = 2;
                    for (int edgeIndex : edgesByCell.get(cell)) {
                        constraints[index++] = primaryColumnCount + edgeIndex * 9 + value - 1;
                    }
                    constraintsByRow.add(constraints);
                    assignments.add(cell * 10 + value);
                }
            }

            int nodeCount = 1 + columnCount;
            for (int[] constraints : constraintsByRow) {
                nodeCount += constraints.length;
            }
            Matrix matrix = new Matrix(columnCount, nodeCount, primaryColumnCount);
            for (int rowIndex = 0; rowIndex < constraintsByRow.size(); rowIndex++) {
                matrix.addRow(constraintsByRow.get(rowIndex), assignments.get(rowIndex));
            }
            return matrix;
        }

        private void addRow(int[] constraints, int assignment) {
            int firstNode = -1;
            for (int constraint : constraints) {
                int header = constraint + 1;
                int node = nextNode++;
                column[node] = header;
                rowAssignment[node] = assignment;
                up[node] = up[header];
                down[node] = header;
                down[up[header]] = node;
                up[header] = node;
                columnSize[header]++;
                if (firstNode < 0) {
                    firstNode = node;
                    left[node] = node;
                    right[node] = node;
                } else {
                    left[node] = left[firstNode];
                    right[node] = firstNode;
                    right[left[firstNode]] = node;
                    left[firstNode] = node;
                }
            }
        }

        private int selectPrimaryColumn() {
            int best = 0;
            int bestSize = Integer.MAX_VALUE;
            for (int header = right[root]; header != root; header = right[header]) {
                if (primary[header] && columnSize[header] < bestSize) {
                    best = header;
                    bestSize = columnSize[header];
                    if (bestSize == 0) {
                        break;
                    }
                }
            }
            return best;
        }

        private void cover(int header) {
            if (primary[header]) {
                remainingPrimary--;
            }
            right[left[header]] = right[header];
            left[right[header]] = left[header];
            for (int row = down[header]; row != header; row = down[row]) {
                for (int node = right[row]; node != row; node = right[node]) {
                    down[up[node]] = down[node];
                    up[down[node]] = up[node];
                    columnSize[column[node]]--;
                }
            }
        }

        private void uncover(int header) {
            for (int row = up[header]; row != header; row = up[row]) {
                for (int node = left[row]; node != row; node = left[node]) {
                    columnSize[column[node]]++;
                    down[up[node]] = node;
                    up[down[node]] = node;
                }
            }
            right[left[header]] = header;
            left[right[header]] = header;
            if (primary[header]) {
                remainingPrimary++;
            }
        }
    }
}
