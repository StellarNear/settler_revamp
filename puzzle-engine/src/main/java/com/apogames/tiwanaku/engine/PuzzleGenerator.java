package com.apogames.tiwanaku.engine;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SplittableRandom;

public final class PuzzleGenerator {
    private static final long PARTITION_SEARCH_NODE_LIMIT = 20_000;
    private final ExactCoverSolver solver;

    public PuzzleGenerator() {
        this(new ExactCoverSolver());
    }

    PuzzleGenerator(ExactCoverSolver solver) {
        this.solver = solver;
    }

    public GeneratedPuzzle generate(GenerationSpec spec) {
        long startedAt = System.nanoTime();
        long timeoutNanos = toNanosSaturated(spec.timeout());
        long visitedNodes = 0;
        SplittableRandom random = new SplittableRandom(spec.seed());

        for (int attempt = 1; attempt <= spec.maxPartitionAttempts(); attempt++) {
            if (cancelledOrExpired(spec, startedAt, timeoutNanos) || visitedNodes >= spec.maxSearchNodes()) {
                return failed(spec, startedAt, timeoutNanos, attempt - 1, visitedNodes);
            }
            int[] regionIds = createRegionPartition(spec, startedAt, timeoutNanos, random.split());
            if (regionIds == null) {
                continue;
            }
            PuzzleDefinition regionLayout = new PuzzleDefinition(
                    spec.columns(), spec.rows(), regionIds, new int[spec.columns() * spec.rows()]);
            long partitionBudget = Math.min(PARTITION_SEARCH_NODE_LIMIT, spec.maxSearchNodes() - visitedNodes);
            SearchResult initial = solve(regionLayout, spec, startedAt, timeoutNanos, partitionBudget);
            visitedNodes += initial.visitedNodes();
            if (initial.status() == UniquenessStatus.UNKNOWN) {
                if (initial.termination() == SearchTermination.NODE_BUDGET_EXCEEDED
                        && visitedNodes < spec.maxSearchNodes()) {
                    continue;
                }
                return unknown(initial.termination(), attempt, visitedNodes, spec.seed());
            }
            if (initial.status() == UniquenessStatus.NO_SOLUTION) {
                continue;
            }

            int[] solution = initial.solutions().getFirst();
            diversifyRegions(regionIds, solution, spec, random.split());
            regionLayout = new PuzzleDefinition(
                    spec.columns(), spec.rows(), regionIds, new int[spec.columns() * spec.rows()]);
            if (!BoardRules.isValidCompleteBoard(regionLayout, solution)) {
                continue;
            }
            ClueRemovalResult removal = removeClues(
                    regionLayout, solution, spec, startedAt, timeoutNanos, random.split(), visitedNodes);
            visitedNodes = removal.visitedNodes();
            if (removal.termination() != null) {
                return unknown(removal.termination(), attempt, visitedNodes, spec.seed());
            }

            int[] givens = removal.givens();
            PuzzleDefinition puzzle = new PuzzleDefinition(spec.columns(), spec.rows(), regionIds, givens);
            SearchResult proof = solve(puzzle, spec, startedAt, timeoutNanos, spec.maxSearchNodes() - visitedNodes);
            visitedNodes += proof.visitedNodes();
            if (proof.status() == UniquenessStatus.PROVEN_UNIQUE) {
                return GeneratedPuzzle.generated(puzzle, solution, attempt, visitedNodes, spec.seed());
            }
            if (proof.status() == UniquenessStatus.UNKNOWN) {
                return unknown(proof.termination(), attempt, visitedNodes, spec.seed());
            }
        }
        return failed(spec, startedAt, timeoutNanos, spec.maxPartitionAttempts(), visitedNodes);
    }

    private ClueRemovalResult removeClues(
            PuzzleDefinition regionLayout,
            int[] solution,
            GenerationSpec spec,
            long startedAt,
            long timeoutNanos,
            SplittableRandom random,
            long alreadyVisitedNodes) {
        int[] givens = solution.clone();
        int targetClues = spec.difficulty().targetClues(solution.length);
        List<Integer> cells = new ArrayList<>(solution.length);
        for (int cell = 0; cell < solution.length; cell++) {
            cells.add(cell);
        }
        for (int index = cells.size() - 1; index > 0; index--) {
            Collections.swap(cells, index, random.nextInt(index + 1));
        }

        long visitedNodes = alreadyVisitedNodes;
        for (int cell : cells) {
            if (countClues(givens) <= targetClues) {
                break;
            }
            if (cancelledOrExpired(spec, startedAt, timeoutNanos) || visitedNodes >= spec.maxSearchNodes()) {
                return new ClueRemovalResult(
                        givens,
                        terminationFor(spec, startedAt, timeoutNanos, visitedNodes),
                        visitedNodes);
            }
            givens[cell] = 0;
            PuzzleDefinition candidate = new PuzzleDefinition(
                    regionLayout.columns(),
                    regionLayout.rows(),
                    regionLayout.regionIds(),
                    givens);
            SearchResult result = solve(candidate, spec, startedAt, timeoutNanos, spec.maxSearchNodes() - visitedNodes);
            visitedNodes += result.visitedNodes();
            if (result.status() == UniquenessStatus.PROVEN_UNIQUE) {
                continue;
            }
            givens[cell] = solution[cell];
            if (result.status() == UniquenessStatus.UNKNOWN) {
                return new ClueRemovalResult(givens, result.termination(), visitedNodes);
            }
        }
        return new ClueRemovalResult(givens, null, visitedNodes);
    }

    private SearchResult solve(
            PuzzleDefinition puzzle,
            GenerationSpec spec,
            long startedAt,
            long timeoutNanos,
            long remainingNodes) {
        if (remainingNodes <= 0 || cancelledOrExpired(spec, startedAt, timeoutNanos)) {
            return new SearchResult(
                    UniquenessStatus.UNKNOWN,
                    spec.cancellationToken().isCancelled()
                            ? SearchTermination.CANCELLED
                            : Thread.currentThread().isInterrupted()
                                    ? SearchTermination.THREAD_INTERRUPTED
                                    : remainingNodes <= 0
                                            ? SearchTermination.NODE_BUDGET_EXCEEDED
                                            : SearchTermination.DEADLINE_EXCEEDED,
                    0,
                    List.of());
        }
        long remainingNanos = timeoutNanos - (System.nanoTime() - startedAt);
        return solver.solve(
                puzzle,
                new SolverOptions(
                        remainingNodes,
                        Duration.ofNanos(Math.max(1, remainingNanos)),
                        spec.seed(),
                        spec.cancellationToken()));
    }

    private int[] createRegionPartition(
            GenerationSpec spec,
            long startedAt,
            long timeoutNanos,
            SplittableRandom random) {
        int cellCount = spec.columns() * spec.rows();
        if (spec.columns() > 1 && spec.rows() > 1 && cellCount >= 8 && spec.maxRegionSize() < 8) {
            return createSnakePartition(spec, random);
        }
        int[] regionIds = new int[cellCount];
        java.util.Arrays.fill(regionIds, -1);
        if (cellCount == 1) {
            return new int[]{0};
        }
        List<Integer> regionSizes = new ArrayList<>();
        if (spec.columns() == 1 || spec.rows() == 1) {
            int lineLength = Math.max(spec.columns(), spec.rows());
            int cell = 0;
            while (cell + 1 < lineLength) {
                int next = cell + 1;
                addRegion(regionIds, regionSizes, cell, next);
                cell += 2;
            }
            if (cell < lineLength) {
                int previousRegion = regionIds[cell - 1];
                regionIds[cell] = previousRegion;
                regionSizes.set(previousRegion, regionSizes.get(previousRegion) + 1);
            }
        } else {
            int evenColumns = spec.columns() - spec.columns() % 2;
            int evenRows = spec.rows() - spec.rows() % 2;
            for (int y = 0; y < evenRows; y += 2) {
                for (int x = 0; x < evenColumns; x += 2) {
                    addRegion(regionIds, regionSizes,
                            y * spec.columns() + x,
                            y * spec.columns() + x + 1,
                            (y + 1) * spec.columns() + x,
                            (y + 1) * spec.columns() + x + 1);
                }
            }
            if (evenColumns < spec.columns()) {
                int x = spec.columns() - 1;
                for (int y = 0; y < evenRows; y += 2) {
                    addRegion(regionIds, regionSizes,
                            y * spec.columns() + x,
                            (y + 1) * spec.columns() + x);
                }
            }
            if (evenRows < spec.rows()) {
                int y = spec.rows() - 1;
                for (int x = 0; x < evenColumns; x += 2) {
                    addRegion(regionIds, regionSizes,
                            y * spec.columns() + x,
                            y * spec.columns() + x + 1);
                }
            }
            for (int cell = 0; cell < cellCount; cell++) {
                if (regionIds[cell] >= 0) {
                    continue;
                }
                int targetRegion = -1;
                List<Integer> candidates = new ArrayList<>();
                for (int neighbor : neighbors(cell, spec.columns(), spec.rows())) {
                    int adjacentRegion = regionIds[neighbor];
                    if (adjacentRegion >= 0
                            && regionSizes.get(adjacentRegion) < spec.maxRegionSize()
                            && !candidates.contains(adjacentRegion)) {
                        candidates.add(adjacentRegion);
                    }
                }
                if (!candidates.isEmpty()) {
                    targetRegion = candidates.get(random.nextInt(candidates.size()));
                }
                if (targetRegion < 0) {
                    return null;
                }
                regionIds[cell] = targetRegion;
                regionSizes.set(targetRegion, regionSizes.get(targetRegion) + 1);
            }
        }
        if (cancelledOrExpired(spec, startedAt, timeoutNanos)) {
            return null;
        }
        if (spec.maxRegionSize() == PuzzleDefinition.MAX_REGION_SIZE) {
            createNineCellRegion(regionIds, regionSizes, spec, random);
        }
        mergeAdjacentRegions(regionIds, regionSizes, spec, random);
        return normalizeRegionIds(regionIds, regionSizes);
    }

    private void diversifyRegions(
            int[] regionIds,
            int[] solution,
            GenerationSpec spec,
            SplittableRandom random) {
        int[] regionSizes = new int[regionIds.length];
        for (int regionId : regionIds) {
            regionSizes[regionId]++;
        }

        for (int attempt = 0; attempt < regionIds.length * 100; attempt++) {
            int cell = random.nextInt(regionIds.length);
            List<Integer> adjacentCells = neighbors(cell, spec.columns(), spec.rows());
            if (adjacentCells.isEmpty()) {
                continue;
            }
            int adjacentCell = adjacentCells.get(random.nextInt(adjacentCells.size()));
            int sourceRegion = regionIds[cell];
            int targetRegion = regionIds[adjacentCell];
            int sourceSize = regionSizes[sourceRegion];
            int targetSize = regionSizes[targetRegion];
            if (sourceRegion == targetRegion
                    || sourceSize <= 1
                    || targetSize >= spec.maxRegionSize()
                    || solution[cell] != sourceSize
                    || !canPlaceValue(cell, targetSize + 1, solution, spec)
                    || !regionRemainsConnectedAfterRemoval(
                            regionIds, sourceRegion, cell, sourceSize - 1, spec)) {
                continue;
            }

            regionIds[cell] = targetRegion;
            solution[cell] = targetSize + 1;
            regionSizes[sourceRegion]--;
            regionSizes[targetRegion]++;
        }
    }

    private boolean canPlaceValue(int cell, int value, int[] solution, GenerationSpec spec) {
        int x = cell % spec.columns();
        int y = cell / spec.columns();
        for (int neighborY = Math.max(0, y - 1); neighborY <= Math.min(spec.rows() - 1, y + 1); neighborY++) {
            for (int neighborX = Math.max(0, x - 1); neighborX <= Math.min(spec.columns() - 1, x + 1); neighborX++) {
                int neighbor = neighborY * spec.columns() + neighborX;
                if (neighbor != cell && solution[neighbor] == value) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean regionRemainsConnectedAfterRemoval(
            int[] regionIds,
            int regionId,
            int removedCell,
            int expectedSize,
            GenerationSpec spec) {
        int start = -1;
        for (int neighbor : neighbors(removedCell, spec.columns(), spec.rows())) {
            if (regionIds[neighbor] == regionId) {
                start = neighbor;
                break;
            }
        }
        if (start < 0) {
            return false;
        }

        boolean[] visited = new boolean[regionIds.length];
        int[] pending = new int[expectedSize];
        int head = 0;
        int tail = 0;
        pending[tail++] = start;
        visited[start] = true;
        while (head < tail) {
            int current = pending[head++];
            for (int neighbor : neighbors(current, spec.columns(), spec.rows())) {
                if (neighbor != removedCell && !visited[neighbor] && regionIds[neighbor] == regionId) {
                    visited[neighbor] = true;
                    pending[tail++] = neighbor;
                }
            }
        }
        return tail == expectedSize;
    }

    private int[] createSnakePartition(GenerationSpec spec, SplittableRandom random) {
        int cellCount = spec.columns() * spec.rows();
        boolean[] partitionable = new boolean[cellCount + 1];
        partitionable[0] = true;
        for (int count = 4; count <= cellCount; count++) {
            for (int size = 4; size <= spec.maxRegionSize() && size <= count; size++) {
                partitionable[count] |= partitionable[count - size];
            }
        }
        if (!partitionable[cellCount]) {
            return null;
        }
        // Avoid the unsatisfiable touching perimeter dominoes of odd-sized block layouts.
        boolean transpose = random.nextBoolean();
        boolean reverse = random.nextBoolean();
        int width = transpose ? spec.rows() : spec.columns();
        int[] regionIds = new int[cellCount];
        int position = 0;
        int region = 0;
        while (position < cellCount) {
            List<Integer> sizes = new ArrayList<>();
            for (int size = 4; size <= spec.maxRegionSize() && size <= cellCount - position; size++) {
                if (partitionable[cellCount - position - size]) {
                    sizes.add(size);
                }
            }
            int size = sizes.get(random.nextInt(sizes.size()));
            for (int offset = 0; offset < size; offset++) {
                int step = reverse ? cellCount - 1 - position : position;
                int y = step / width;
                int x = y % 2 == 0 ? step % width : width - 1 - step % width;
                int cell = transpose ? x * spec.columns() + y : y * spec.columns() + x;
                regionIds[cell] = region;
                position++;
            }
            region++;
        }
        return regionIds;
    }

    private void addRegion(int[] regionIds, List<Integer> regionSizes, int... cells) {
        int regionId = regionSizes.size();
        for (int cell : cells) {
            regionIds[cell] = regionId;
        }
        regionSizes.add(cells.length);
    }

    private void createNineCellRegion(
            int[] regionIds,
            List<Integer> regionSizes,
            GenerationSpec spec,
            SplittableRandom random) {
        List<int[]> pairs = adjacentRegionPairs(regionIds, spec);
        for (int pairIndex = pairs.size() - 1; pairIndex > 0; pairIndex--) {
            Collections.swap(pairs, pairIndex, random.nextInt(pairIndex + 1));
        }
        for (int[] pair : pairs) {
            int donor = pair[0];
            int receiver = pair[1];
            if (regionSizes.get(donor) != 4 || regionSizes.get(receiver) != 4) {
                continue;
            }
            for (int cell = 0; cell < regionIds.length; cell++) {
                if (regionIds[cell] != donor
                        || neighbors(cell, spec.columns(), spec.rows()).stream()
                                .noneMatch(neighbor -> regionIds[neighbor] == receiver)
                        || !regionRemainsConnectedAfterRemoval(regionIds, donor, cell, spec)) {
                    continue;
                }
                regionIds[cell] = receiver;
                regionSizes.set(donor, 3);
                regionSizes.set(receiver, 5);
                for (int[] adjacent : adjacentRegionPairs(regionIds, spec)) {
                    int other = adjacent[0] == receiver ? adjacent[1]
                            : adjacent[1] == receiver ? adjacent[0] : -1;
                    if (other >= 0 && regionSizes.get(other) == 4) {
                        mergeRegions(regionIds, regionSizes, receiver, other);
                        return;
                    }
                }
                regionIds[cell] = donor;
                regionSizes.set(donor, 4);
                regionSizes.set(receiver, 4);
            }
        }
    }

    private boolean regionRemainsConnectedAfterRemoval(
            int[] regionIds,
            int regionId,
            int removedCell,
            GenerationSpec spec) {
        int remaining = 0;
        int start = -1;
        for (int cell = 0; cell < regionIds.length; cell++) {
            if (cell != removedCell && regionIds[cell] == regionId) {
                remaining++;
                start = cell;
            }
        }
        if (remaining == 0) {
            return false;
        }
        boolean[] visited = new boolean[regionIds.length];
        List<Integer> pending = new ArrayList<>();
        pending.add(start);
        visited[start] = true;
        int reached = 0;
        while (!pending.isEmpty()) {
            int current = pending.removeLast();
            reached++;
            for (int neighbor : neighbors(current, spec.columns(), spec.rows())) {
                if (neighbor != removedCell && !visited[neighbor] && regionIds[neighbor] == regionId) {
                    visited[neighbor] = true;
                    pending.add(neighbor);
                }
            }
        }
        return reached == remaining;
    }

    private void mergeAdjacentRegions(
            int[] regionIds,
            List<Integer> regionSizes,
            GenerationSpec spec,
            SplittableRandom random) {
        while (true) {
            List<int[]> candidates = new ArrayList<>();
            for (int[] pair : adjacentRegionPairs(regionIds, spec)) {
                if (regionSizes.get(pair[0]) + regionSizes.get(pair[1]) <= spec.maxRegionSize()) {
                    candidates.add(pair);
                }
            }
            if (candidates.isEmpty()) {
                return;
            }
            int[] selected = candidates.get(random.nextInt(candidates.size()));
            mergeRegions(regionIds, regionSizes, selected[0], selected[1]);
        }
    }

    private List<int[]> adjacentRegionPairs(int[] regionIds, GenerationSpec spec) {
        List<int[]> pairs = new ArrayList<>();
        for (int cell = 0; cell < regionIds.length; cell++) {
            int region = regionIds[cell];
            for (int neighbor : neighbors(cell, spec.columns(), spec.rows())) {
                int adjacent = regionIds[neighbor];
                if (adjacent >= 0 && region != adjacent) {
                    int first = Math.min(region, adjacent);
                    int second = Math.max(region, adjacent);
                    if (pairs.stream().noneMatch(pair -> pair[0] == first && pair[1] == second)) {
                        pairs.add(new int[]{first, second});
                    }
                }
            }
        }
        return pairs;
    }

    private void mergeRegions(int[] regionIds, List<Integer> regionSizes, int target, int source) {
        for (int cell = 0; cell < regionIds.length; cell++) {
            if (regionIds[cell] == source) {
                regionIds[cell] = target;
            }
        }
        regionSizes.set(target, regionSizes.get(target) + regionSizes.get(source));
        regionSizes.set(source, 0);
    }

    private int[] normalizeRegionIds(int[] regionIds, List<Integer> regionSizes) {
        int[] normalized = new int[regionSizes.size()];
        int next = 0;
        for (int region = 0; region < regionSizes.size(); region++) {
            if (regionSizes.get(region) == 0) {
                normalized[region] = -1;
            } else {
                normalized[region] = next++;
            }
        }
        for (int cell = 0; cell < regionIds.length; cell++) {
            regionIds[cell] = normalized[regionIds[cell]];
        }
        return regionIds;
    }

    private List<Integer> neighbors(int cell, int columns, int rows) {
        int x = cell % columns;
        int y = cell / columns;
        List<Integer> neighbors = new ArrayList<>(4);
        if (x > 0) neighbors.add(cell - 1);
        if (x + 1 < columns) neighbors.add(cell + 1);
        if (y > 0) neighbors.add(cell - columns);
        if (y + 1 < rows) neighbors.add(cell + columns);
        return neighbors;
    }

    private boolean cancelledOrExpired(GenerationSpec spec, long startedAt, long timeoutNanos) {
        return spec.cancellationToken().isCancelled()
                || Thread.currentThread().isInterrupted()
                || System.nanoTime() - startedAt >= timeoutNanos;
    }

    private SearchTermination terminationFor(
            GenerationSpec spec,
            long startedAt,
            long timeoutNanos,
            long visitedNodes) {
        if (spec.cancellationToken().isCancelled()) {
            return SearchTermination.CANCELLED;
        }
        if (Thread.currentThread().isInterrupted()) {
            return SearchTermination.THREAD_INTERRUPTED;
        }
        if (visitedNodes >= spec.maxSearchNodes()) {
            return SearchTermination.NODE_BUDGET_EXCEEDED;
        }
        return System.nanoTime() - startedAt >= timeoutNanos
                ? SearchTermination.DEADLINE_EXCEEDED
                : SearchTermination.NODE_BUDGET_EXCEEDED;
    }

    private GeneratedPuzzle failed(
            GenerationSpec spec,
            long startedAt,
            long timeoutNanos,
            int attempts,
            long visitedNodes) {
        if (cancelledOrExpired(spec, startedAt, timeoutNanos) || visitedNodes >= spec.maxSearchNodes()) {
            return unknown(
                    terminationFor(spec, startedAt, timeoutNanos, visitedNodes),
                    attempts,
                    visitedNodes,
                    spec.seed());
        }
        return GeneratedPuzzle.failed(
                GenerationStatus.EXHAUSTED,
                SearchTermination.EXHAUSTED,
                attempts,
                visitedNodes,
                spec.seed());
    }

    private GeneratedPuzzle unknown(
            SearchTermination termination,
            int attempts,
            long visitedNodes,
            long seed) {
        return GeneratedPuzzle.failed(GenerationStatus.UNKNOWN, termination, attempts, visitedNodes, seed);
    }

    private int countClues(int[] givens) {
        int count = 0;
        for (int given : givens) {
            if (given != 0) {
                count++;
            }
        }
        return count;
    }

    private static long toNanosSaturated(Duration duration) {
        try {
            return duration.toNanos();
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private record ClueRemovalResult(int[] givens, SearchTermination termination, long visitedNodes) {
    }
}
