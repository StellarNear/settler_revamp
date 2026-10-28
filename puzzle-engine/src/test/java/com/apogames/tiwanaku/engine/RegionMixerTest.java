package com.apogames.tiwanaku.engine;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RegionMixerTest {
    @Test
    void titanLayoutsLoseStripSeamsAndIncludeWideBranchedRegions() {
        int broad = 0;
        int branches = 0;
        int seams = 0;
        for (long seed = 0; seed < 4; seed++) {
            long start = System.nanoTime();
            GeneratedPuzzle result = new PuzzleGenerator().generate(new GenerationSpec(
                    20, 12, 5, Difficulty.EASY, seed, 200, 2_000_000,
                    Duration.ofSeconds(30), new CancellationToken()));
            assertEquals(GenerationStatus.GENERATED, result.status(), "seed " + seed);
            PuzzleDefinition puzzle = result.puzzle();
            assertTrue(BoardRules.isValidCompleteBoard(puzzle, result.solution()));
            int[] ids = puzzle.regionIds();
            for (int region = 0; region < puzzle.regionCount(); region++) {
                Set<Integer> xs = new HashSet<>();
                Set<Integer> ys = new HashSet<>();
                for (int cell = 0; cell < ids.length; cell++) {
                    if (ids[cell] != region) {
                        continue;
                    }
                    xs.add(cell % 20);
                    ys.add(cell / 20);
                    int degree = 0;
                    for (int side : puzzle.topology().regionNeighbors(cell)) {
                        if (ids[side] == region) {
                            degree++;
                        }
                    }
                    if (degree >= 3) {
                        branches++;
                    }
                }
                if (xs.size() >= 3 && ys.size() >= 3) {
                    broad++;
                }
            }
            for (int y = 0; y < 11; y++) {
                boolean seam = true;
                for (int x = 0; x < 20; x++) {
                    seam &= ids[y * 20 + x] != ids[(y + 1) * 20 + x];
                }
                if (seam) seams++;
            }
            for (int x = 0; x < 19; x++) {
                boolean seam = true;
                for (int y = 0; y < 12; y++) {
                    seam &= ids[y * 20 + x] != ids[y * 20 + x + 1];
                }
                if (seam) seams++;
            }
            System.out.printf("organic seed=%d timeMs=%d clues=%d%n", seed,
                    (System.nanoTime() - start) / 1_000_000,
                    java.util.Arrays.stream(puzzle.givens()).filter(v -> v != 0).count());
        }
        System.out.printf("organic broad=%d branches=%d fullSeams=%d%n", broad, branches, seams);
        assertTrue(broad >= 4, "Must admit regions spanning at least 3 rows AND 3 columns");
        assertTrue(branches >= 4, "Must admit branched shapes, not just paths");
        assertTrue(seams <= 4, "Full-board seams across four maps: " + seams);
    }
}
