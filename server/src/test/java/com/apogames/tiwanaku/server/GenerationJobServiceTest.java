package com.apogames.tiwanaku.server;

import com.apogames.tiwanaku.engine.Difficulty;
import com.apogames.tiwanaku.engine.TileShape;
import com.apogames.tiwanaku.engine.UniquenessStatus;
import com.apogames.tiwanaku.engine.ExactCoverSolver;
import com.apogames.tiwanaku.engine.LogicalSolver;
import com.apogames.tiwanaku.engine.SolverOptions;
import com.apogames.tiwanaku.engine.CancellationToken;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GenerationJobServiceTest {
    @Test
    void titanJobsRespectTheOneMinuteBudgetWithoutWeakeningTheirGuarantees() throws Exception {
        try (GenerationJobService jobs = new GenerationJobService()) {
            for (Difficulty difficulty : new Difficulty[]{Difficulty.HARD, Difficulty.EXTREME, Difficulty.GODLIKE}) {
                for (int[] sample : new int[][]{{5, 0}, {5, 2026}, {5, 3045}, {6, 0}}) {
                    int max = sample[0];
                    long seed = sample[1];
                    var submitted = jobs.submit(new GenerationRequest(20, 12, max, difficulty, seed, TileShape.SQUARE));
                    var job = submitted;
                    long deadline = System.nanoTime() + Duration.ofSeconds(65).toNanos();
                    while (Set.of("QUEUED", "CANCELLING").contains(job.status()) && System.nanoTime() < deadline) {
                        Thread.sleep(20);
                        job = jobs.get(submitted.jobId());
                    }
                    String label = difficulty + " max=" + max + " seed=" + seed;
                    System.out.printf("Titan job %s status=%s tries=%d timeMs=%d%n",
                            label, job.status(), job.tryNumber(), job.elapsedMillis());
                    assertEquals("GENERATED", job.status(), label);
                    assertTrue(job.elapsedMillis() < 61_000, label);
                    var result = jobs.result(job.jobId());
                    var options = new SolverOptions(8_000_000, Duration.ofSeconds(30), seed, new CancellationToken());
                    assertEquals(UniquenessStatus.PROVEN_UNIQUE,
                            new ExactCoverSolver().solve(result.puzzle(), options).status(), label);
                    if (difficulty == Difficulty.EXTREME) {
                        assertEquals(LogicalSolver.Status.SOLVED,
                                new LogicalSolver().solve(result.puzzle(), options).status(), label);
                    }
                    assertEquals(difficulty.name(), jobs.settings(job.jobId()).difficulty());
                    assertEquals(Long.toString(result.seed()), jobs.settings(job.jobId()).seed());
                    if (difficulty == Difficulty.EXTREME && max == 6 && seed == 0) {
                        assertTrue(job.tryNumber() > 1, "The budget-exhausting seed must be retried");
                    }
                }
            }
        }
    }
}
