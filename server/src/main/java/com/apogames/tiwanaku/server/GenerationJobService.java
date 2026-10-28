package com.apogames.tiwanaku.server;

import com.apogames.tiwanaku.engine.CancellationToken;
import com.apogames.tiwanaku.engine.GeneratedPuzzle;
import com.apogames.tiwanaku.engine.GenerationSpec;
import com.apogames.tiwanaku.engine.GenerationStatus;
import com.apogames.tiwanaku.engine.PuzzleGenerator;
import com.apogames.tiwanaku.engine.TileShape;
import com.apogames.tiwanaku.engine.SearchTermination;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
public final class GenerationJobService implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(GenerationJobService.class);
    private static final int MAX_RETAINED_JOBS = 128;
    private static final Duration JOB_RETENTION = Duration.ofHours(1);

    private final PuzzleGenerator generator = new PuzzleGenerator();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1,
            1,
            0,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(2),
            runnable -> {
                Thread thread = new Thread(runnable, "tiwanaku-generation");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy());
    private final Map<UUID, Job> jobs = new ConcurrentHashMap<>();

    public synchronized JobSnapshot submit(GenerationRequest request) {
        purgeExpired();
        if (jobs.size() >= MAX_RETAINED_JOBS) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Generation job capacity is full");
        }
        UUID id = UUID.randomUUID();
        Job job = new Job(id, request);
        GenerationSpec spec;
        try {
            spec = new GenerationSpec(
                    shapeOf(request),
                    request.columns(),
                    request.rows(),
                    request.maxRegionSize(),
                    request.difficulty(),
                    request.seed(),
                    200,
                    2_000_000,
                    Duration.ofSeconds(request.columns() * request.rows() > 45 ? 30 : 10),
                    job.cancellationToken);
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest(exception.getMessage());
        }
        jobs.put(id, job);
        LOGGER.info("Generation submitted job={} shape={} board={}x{} maxRegion={} difficulty={} seed={}",
                id, shapeOf(request), request.columns(), request.rows(), request.maxRegionSize(),
                request.difficulty(), request.seed());
        try {
            executor.execute(() -> run(job, spec));
        } catch (RejectedExecutionException exception) {
            jobs.remove(id);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Generation queue is full; retry later");
        }
        return job.snapshot();
    }

    public JobSnapshot get(UUID id) {
        purgeExpired();
        Job job = jobs.get(id);
        if (job == null) {
            throw ApiException.notFound("Generation job was not found or has expired");
        }
        return job.snapshot();
    }

    public JobSnapshot cancel(UUID id) {
        Job job = jobs.get(id);
        if (job == null) {
            throw ApiException.notFound("Generation job was not found or has expired");
        }
        job.cancel();
        LOGGER.info("Generation cancellation requested job={}", id);
        return job.snapshot();
    }

    public GeneratedPuzzle result(UUID id) {
        JobSnapshot snapshot = get(id);
        if (!snapshot.status().equals(GenerationStatus.GENERATED.name())) {
            throw ApiException.conflict("Only a successfully generated job can be accepted");
        }
        return jobs.get(id).result;
    }

    public HistoryEntry.PuzzleSettings settings(UUID id) {
        result(id);
        var request = jobs.get(id).request;
        return new HistoryEntry.PuzzleSettings(request.columns(), request.rows(), request.maxRegionSize(),
                request.difficulty().name(), Long.toString(request.seed()), "exact-cover-v1", "localhost-generator-v1",
                shapeOf(request).name());
    }

    private static TileShape shapeOf(GenerationRequest request) {
        return request.shape() == null ? TileShape.SQUARE : request.shape();
    }

    private void run(Job job, GenerationSpec spec) {
        job.startedAt = Instant.now();
        try {
            GeneratedPuzzle result = generator.generate(spec, job::reportProgress);
            job.complete(result);
            LOGGER.info("Generation ended job={} status={} termination={} elapsedMs={} nodes={}",
                    job.id, job.snapshot().status(), result.termination(),
                    job.snapshot().elapsedMillis(), result.visitedNodes());
        } catch (RuntimeException exception) {
            LOGGER.error("Generation job {} failed", job.id, exception);
            job.fail(exception);
        }
    }

    private synchronized void purgeExpired() {
        Instant expiry = Instant.now().minus(JOB_RETENTION);
        jobs.values().removeIf(job -> job.completedAt != null && job.completedAt.isBefore(expiry));
    }

    @Override
    public void close() {
        jobs.values().forEach(Job::cancel);
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Generation workers did not stop during shutdown");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while stopping generation workers", exception);
        }
    }

    private static final class Job {
        private final UUID id;
        private final GenerationRequest request;
        private final CancellationToken cancellationToken = new CancellationToken();
        private volatile String status = "QUEUED";
        private volatile boolean cancellationRequested;
        private volatile GeneratedPuzzle result;
        private volatile SearchTermination termination;
        private volatile String error;
        private volatile Instant startedAt;
        private volatile Instant completedAt;
        private volatile int progressPercent;

        private Job(UUID id, GenerationRequest request) {
            this.id = id;
            this.request = request;
        }

        private void reportProgress(int percent) {
            progressPercent = Math.max(progressPercent, Math.min(99, Math.max(0, percent)));
        }

        private void complete(GeneratedPuzzle result) {
            this.result = result;
            this.status = cancellationRequested ? "CANCELLED" : result.status().name();
            this.termination = result.termination();
            this.completedAt = Instant.now();
        }

        private void fail(RuntimeException exception) {
            this.status = "FAILED";
            this.error = "Puzzle generation failed";
            this.completedAt = Instant.now();
        }

        private void cancel() {
            if (completedAt == null && result == null) {
                cancellationRequested = true;
                cancellationToken.cancel();
            }
        }

        private JobSnapshot snapshot() {
            long elapsedMillis = startedAt == null
                    ? 0
                    : Duration.between(startedAt, completedAt == null ? Instant.now() : completedAt).toMillis();
            String visibleStatus = cancellationRequested && completedAt == null ? "CANCELLING" : status;
            return new JobSnapshot(id, visibleStatus, termination == null ? null : termination.name(),
                    error, elapsedMillis, result == null ? 0 : result.visitedNodes(),
                    "GENERATED".equals(status) ? 100 : progressPercent);
        }
    }

    public record JobSnapshot(
            UUID jobId,
            String status,
            String termination,
            String error,
            long elapsedMillis,
            long visitedNodes,
            int progressPercent) {
    }
}
