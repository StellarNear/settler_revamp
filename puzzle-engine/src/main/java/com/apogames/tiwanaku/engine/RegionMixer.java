package com.apogames.tiwanaku.engine;

import java.util.Arrays;
import java.util.SplittableRandom;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/**
 * Resamples connected patches without geometric templates. Exterior values stay fixed;
 * a patch is committed only when its new regions have a compatible complete filling.
 */
final class RegionMixer {
    private static final int SEARCH_NODES_PER_PATCH = 2_000;

    record Result(int acceptedPatches, long visitedNodes) { }

    static Result mix(int[] regions, int[] values, GenerationSpec spec, SplittableRandom random,
                      long maxNodes, BooleanSupplier stopped, IntConsumer progress) {
        Topology topology = Topology.of(spec.shape(), spec.columns(), spec.rows());
        int rounds = regions.length * 40;
        int accepted = 0;
        long visitedNodes = 0;
        for (int round = 0; round < rounds && visitedNodes < maxNodes && !stopped.getAsBoolean(); round++) {
            if (round % regions.length == 0) {
                progress.accept(1 + 8 * round / rounds);
            }
            int first = random.nextInt(regions.length);
            int[] sides = topology.regionNeighbors(first);
            if (sides.length == 0) {
                continue;
            }
            int second = sides[random.nextInt(sides.length)];
            int a = regions[first];
            int b = regions[second];
            if (a == b) {
                continue;
            }
            int[] patch = new int[2 * spec.maxRegionSize()];
            int length = 0;
            for (int cell = 0; cell < regions.length; cell++) {
                if (regions[cell] == a || regions[cell] == b) {
                    patch[length++] = cell;
                }
            }
            int minSize = Math.max(1, length - spec.maxRegionSize());
            int sizeA = minSize + random.nextInt(Math.min(spec.maxRegionSize(), length - 1) - minSize + 1);
            int[] candidate = regions.clone();
            for (int i = 0; i < length; i++) {
                candidate[patch[i]] = -1;
            }
            int seed = patch[random.nextInt(length)];
            candidate[seed] = a;
            int[] frontier = new int[length];
            boolean grown = true;
            for (int size = 1; size < sizeA; size++) {
                int count = 0;
                for (int i = 0; i < length; i++) {
                    int cell = patch[i];
                    if (candidate[cell] != -1) {
                        continue;
                    }
                    for (int neighbor : topology.regionNeighbors(cell)) {
                        if (candidate[neighbor] == a) {
                            frontier[count++] = cell;
                            break;
                        }
                    }
                }
                if (count == 0) {
                    grown = false;
                    break;
                }
                candidate[frontier[random.nextInt(count)]] = a;
            }
            if (!grown) {
                continue;
            }
            boolean changed = false;
            for (int i = 0; i < length; i++) {
                int cell = patch[i];
                if (candidate[cell] == -1) {
                    candidate[cell] = b;
                }
                changed |= candidate[cell] != regions[cell];
            }
            if (!changed || !connected(candidate, patch, length, b, topology)) {
                continue;
            }
            int[] filling = values.clone();
            for (int i = 0; i < length; i++) {
                filling[patch[i]] = 0;
            }
            PatchSearch search = new PatchSearch(candidate, filling, patch, length, topology,
                    a, sizeA, length - sizeA, random, Math.min(SEARCH_NODES_PER_PATCH, maxNodes - visitedNodes), stopped);
            if (search.fill(length)) {
                System.arraycopy(candidate, 0, regions, 0, regions.length);
                System.arraycopy(filling, 0, values, 0, values.length);
                accepted++;
            }
            visitedNodes += search.nodes;
        }
        return new Result(accepted, visitedNodes);
    }

    private static boolean connected(int[] regions, int[] patch, int length, int region, Topology topology) {
        int start = -1;
        int expected = 0;
        for (int i = 0; i < length; i++) {
            if (regions[patch[i]] == region) {
                start = patch[i];
                expected++;
            }
        }
        int[] queue = new int[length];
        boolean[] seen = new boolean[regions.length];
        queue[0] = start;
        seen[start] = true;
        int tail = 1;
        for (int head = 0; head < tail; head++) {
            for (int neighbor : topology.regionNeighbors(queue[head])) {
                if (!seen[neighbor] && regions[neighbor] == region) {
                    seen[neighbor] = true;
                    queue[tail++] = neighbor;
                }
            }
        }
        return tail == expected;
    }

    private static final class PatchSearch {
        private final int[] regions;
        private final int[] values;
        private final int[] patch;
        private final int length;
        private final Topology topology;
        private final int a;
        private final int[] masks;
        private final SplittableRandom random;
        private final BooleanSupplier stopped;
        private final long maxNodes;
        private int nodes;

        private PatchSearch(int[] regions, int[] values, int[] patch, int length, Topology topology,
                            int a, int sizeA, int sizeB, SplittableRandom random,
                            long maxNodes, BooleanSupplier stopped) {
            this.regions = regions;
            this.values = values;
            this.patch = Arrays.copyOf(patch, length);
            this.length = length;
            this.topology = topology;
            this.a = a;
            this.masks = new int[]{(1 << sizeA) - 1, (1 << sizeB) - 1};
            this.random = random;
            this.stopped = stopped;
            this.maxNodes = maxNodes;
        }

        private boolean fill(int left) {
            if (nodes >= maxNodes || (nodes % 64 == 0 && stopped.getAsBoolean())) {
                return false;
            }
            nodes++;
            if (left == 0) {
                return true;
            }
            int best = -1;
            int choices = 0;
            int count = Integer.MAX_VALUE;
            for (int i = 0; i < length; i++) {
                int cell = patch[i];
                if (values[cell] != 0) {
                    continue;
                }
                int available = masks[regions[cell] == a ? 0 : 1];
                for (int neighbor : topology.conflictNeighbors(cell)) {
                    if (values[neighbor] != 0) {
                        available &= ~(1 << (values[neighbor] - 1));
                    }
                }
                int size = Integer.bitCount(available);
                if (size == 0) {
                    return false;
                }
                if (size < count) {
                    best = cell;
                    choices = available;
                    count = size;
                }
            }
            int group = regions[best] == a ? 0 : 1;
            while (choices != 0) {
                int offset = random.nextInt(Integer.bitCount(choices));
                int bit = choices;
                for (int i = 0; i < offset; i++) {
                    bit &= bit - 1;
                }
                bit = Integer.lowestOneBit(bit);
                choices &= ~bit;
                values[best] = Integer.numberOfTrailingZeros(bit) + 1;
                masks[group] &= ~bit;
                if (fill(left - 1)) {
                    return true;
                }
                masks[group] |= bit;
                values[best] = 0;
            }
            return false;
        }
    }
}
