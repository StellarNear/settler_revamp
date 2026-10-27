package com.apogames.tiwanaku.server;

import com.apogames.tiwanaku.engine.BoardRules;
import com.apogames.tiwanaku.engine.GeneratedPuzzle;
import com.apogames.tiwanaku.engine.PuzzleDefinition;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public final class AttemptService {
    private static final long EDITOR_LEASE_SECONDS = 15;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final GenerationJobService jobs;
    private final StoreJson store;
    private final GameplayClock clock;

    public AttemptService(
            JdbcTemplate jdbc,
            TransactionTemplate transactions,
            GenerationJobService jobs,
            ObjectMapper objectMapper,
            GameplayClock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.jobs = jobs;
        this.store = new StoreJson(objectMapper);
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void invalidatePreviousProcessLeases() {
        jdbc.update("""
                UPDATE attempts SET editor_lease_expires_at = ?,
                    revision = revision + CASE WHEN status = 'PLAYING' THEN 1 ELSE 0 END,
                    status = CASE WHEN status = 'PLAYING' THEN 'SUSPENDED' ELSE status END
                WHERE status <> 'COMPLETED'
                """, Timestamp.from(clock.now()));
    }

    public synchronized AttemptSnapshot create(CreateAttemptRequest request) {
        String payloadHash = hash(request);
        Map<String, Object> existing = queryOptional(
                "SELECT payload_hash, attempt_id FROM creation_receipts WHERE request_id = ?",
                request.requestId());
        if (existing != null) {
            if (!payloadHash.equals(existing.get("PAYLOAD_HASH"))) {
                throw ApiException.conflict("The request ID was already used with a different payload");
            }
            return load(UUID.fromString((String) existing.get("ATTEMPT_ID")));
        }

        if ((request.generationJobId() == null) == (request.restartAttemptId() == null)) {
            throw ApiException.badRequest("Choose exactly one generation job or attempt to restart");
        }
        AttemptSnapshot original = request.restartAttemptId() == null ? null : load(request.restartAttemptId());
        GeneratedPuzzle generated = original == null ? jobs.result(request.generationJobId()) : null;
        UUID attemptId = UUID.randomUUID();
        UUID puzzleId = original == null ? UUID.randomUUID() : UUID.fromString(original.puzzleId());
        PuzzleDefinition puzzle = original == null ? generated.puzzle() : loadPuzzle(original.puzzleId()).definition();
        int[] values = puzzle.givens();
        Instant leaseExpiresAt = clock.now().plusSeconds(EDITOR_LEASE_SECONDS);
        GameProgress progress = GameProgress.initial(values.length, original != null);
        LocalSettings defaults = jdbc.queryForObject("SELECT settings_json FROM local_settings WHERE singleton_id=1",
                (rs, row) -> fromJson(rs.getString(1), LocalSettings.class));
        progress.showConflicts = defaults.showConflicts();
        progress.continuousConflictsUsed = defaults.showConflicts();

        return transactions.execute(status -> {
            if (original == null) jdbc.update(
                    """
                    INSERT INTO puzzles (puzzle_id, columns_count, rows_count, region_ids, givens, solution, generation_json)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    puzzleId.toString(),
                    puzzle.columns(),
                    puzzle.rows(),
                    json(puzzle.regionIds()),
                    json(puzzle.givens()),
                    json(generated.solution()),
                    json(jobs.settings(request.generationJobId())));
            jdbc.update(
                    """
                    INSERT INTO attempts
                      (attempt_id, puzzle_id, values_snapshot, status, revision, editor_session_id,
                       editor_lease_expires_at, move_count, progress_json)
                    VALUES (?, ?, ?, 'READY', 0, ?, ?, 0, ?)
                    """,
                    attemptId.toString(),
                    puzzleId.toString(),
                    json(values),
                    request.editorSessionId(),
                    Timestamp.from(leaseExpiresAt),
                    json(progress));
            jdbc.update(
                    """
                    INSERT INTO creation_receipts (request_id, payload_hash, attempt_id)
                    VALUES (?, ?, ?)
                    """,
                    request.requestId(),
                    payloadHash,
                    attemptId.toString());
            return load(attemptId);
        });
    }

    public AttemptSnapshot get(UUID attemptId, String editorSessionId) {
        return transactions.execute(status -> {
            Map<String, Object> row = lockAttempt(attemptId);
            suspendExpired(attemptId, row);
            if (row.get("EDITOR_SESSION_ID").equals(editorSessionId)) {
                refreshLease(attemptId, editorSessionId);
            }
            return load(attemptId);
        });
    }

    public AttemptSnapshot command(UUID attemptId, AttemptCommand command) {
        String payloadHash = hash(command);
        return transactions.execute(status -> {
            Map<String, Object> attempt = lockAttempt(attemptId);
            suspendExpired(attemptId, attempt);
            Map<String, Object> receipt = queryOptional(
                    """
                    SELECT payload_hash, response_json
                    FROM command_receipts
                    WHERE attempt_id = ? AND command_id = ?
                    """,
                    attemptId.toString(),
                    command.commandId());
            if (receipt != null) {
                if (!payloadHash.equals(receipt.get("PAYLOAD_HASH"))) {
                    throw ApiException.conflict("The command ID was already used with a different payload");
                }
                AttemptSnapshot current = load(attemptId);
                if (!current.editorSessionId().equals(command.editorSessionId())) {
                    throw ApiException.conflict("This tab no longer owns the active editor lease");
                }
                return current;
            }

            String currentEditor = (String) attempt.get("EDITOR_SESSION_ID");
            Instant leaseExpiry = timestamp(attempt.get("EDITOR_LEASE_EXPIRES_AT"));
            if (command.type() != AttemptCommand.CommandType.TAKEOVER
                    && (!currentEditor.equals(command.editorSessionId())
                    || leaseExpiry == null
                    || !leaseExpiry.isAfter(clock.now()))) {
                throw ApiException.conflict("The editor lease expired or belongs to another tab");
            }
            long currentRevision = ((Number) attempt.get("REVISION")).longValue();
            if (command.expectedRevision() != currentRevision) {
                throw ApiException.conflict("The attempt revision changed; reload the latest snapshot");
            }

            PuzzleRow puzzle = loadPuzzle((String) attempt.get("PUZZLE_ID"));
            int[] values = intArray((String) attempt.get("VALUES_SNAPSHOT"));
            GameProgress progress = progress(attempt, values.length);
            String currentStatus = (String) attempt.get("STATUS");
            String nextStatus = currentStatus;
            String nextEditor = currentEditor;
            int moveCount = ((Number) attempt.get("MOVE_COUNT")).intValue();
            boolean changed = false;
            boolean complete = false;

            if ("PLAYING".equals(currentStatus)
                    && command.type() != AttemptCommand.CommandType.TAKEOVER) {
                progress.acceptTime(command.timingSequence(), command.elapsedMillis(), clock);
            } else if (command.type() == AttemptCommand.CommandType.HEARTBEAT) {
                throw ApiException.conflict("Only a playing attempt accepts timing heartbeats");
            }
            if (command.type() == AttemptCommand.CommandType.HEARTBEAT
                    && (command.timingSequence() == null || command.elapsedMillis() == null)) {
                throw ApiException.badRequest("A heartbeat requires a timing sequence and elapsed proposal");
            }

            switch (command.type()) {
                case SET_VALUE, CYCLE_VALUE, TOGGLE_NOTE, UNDO, REDO, CHECKPOINT,
                        RESTORE_CHECKPOINT, CHECK, SHOW_CONFLICTS, CANDIDATES, EXPLAIN, REVEAL -> {
                    requireMutable(currentStatus);
                    int[] previousValues = values.clone();
                    progress.edit(puzzle.definition(), puzzle.solution(), values, command);
                    if (!java.util.Arrays.equals(previousValues, values) && !progress.started) {
                        progress.startTimer(clock);
                    }
                    nextStatus = progress.started ? "PLAYING" : "READY";
                    moveCount = progress.valueEntryCount;
                    changed = true;
                    complete = BoardRules.isValidCompleteBoard(puzzle.definition(), values);
                    if (complete) {
                        nextStatus = "COMPLETED";
                    }
                }
                case PAUSE -> {
                    if ("COMPLETED".equals(currentStatus)) {
                        throw ApiException.conflict("Completed attempts cannot be paused");
                    }
                    nextStatus = "SUSPENDED";
                    changed = !"SUSPENDED".equals(currentStatus);
                    progress.timerProcessId = null;
                }
                case RESUME -> {
                    if (!"SUSPENDED".equals(currentStatus) && !"READY".equals(currentStatus)) {
                        throw ApiException.conflict("Only a ready or suspended attempt can be resumed");
                    }
                    nextStatus = progress.started ? "PLAYING" : "READY";
                    if (progress.started) progress.startTimer(clock);
                    changed = true;
                }
                case TAKEOVER -> {
                    nextEditor = command.editorSessionId();
                    changed = !currentEditor.equals(nextEditor);
                    if (changed && !"COMPLETED".equals(currentStatus)) {
                        nextStatus = "SUSPENDED";
                        progress.timerProcessId = null;
                    }
                }
                case HEARTBEAT -> { }
            }

            long nextRevision = currentRevision + (changed ? 1 : 0);
            Instant nextLeaseExpiry = clock.now().plusSeconds(EDITOR_LEASE_SECONDS);
            jdbc.update(
                    """
                    UPDATE attempts
                    SET values_snapshot = ?, status = ?, revision = ?, editor_session_id = ?,
                        editor_lease_expires_at = ?, move_count = ?, progress_json = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE attempt_id = ?
                    """,
                    json(values),
                    nextStatus,
                    nextRevision,
                    nextEditor,
                    Timestamp.from(nextLeaseExpiry),
                    moveCount,
                    json(progress),
                    attemptId.toString());

            if (complete) {
                jdbc.update(
                        """
                        INSERT INTO results (result_id, attempt_id, values_snapshot, move_count, progress_json)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                        attemptId.toString(),
                        attemptId.toString(),
                        json(values),
                        moveCount,
                        json(progress));
                jdbc.update("""
                        INSERT INTO history_entries(record_id,attempt_id,map_size,completed_millis,elapsed_millis,
                                                    player_tag,scoring_version,value_entries,progress_json,puzzle_settings_json)
                        SELECT r.result_id,r.attempt_id,?,DATEDIFF('MILLISECOND',
                            TIMESTAMP WITH TIME ZONE '1970-01-01 00:00:00+00',r.completed_at),
                            ?,?, 'direct-entry-v1',?,?,p.generation_json
                        FROM results r JOIN attempts a ON a.attempt_id=r.attempt_id
                        JOIN puzzles p ON p.puzzle_id=a.puzzle_id WHERE r.attempt_id=?
                        """, puzzle.definition().columns() + "x" + puzzle.definition().rows(),
                        progress.elapsedMillis,
                        jdbc.queryForObject("SELECT settings_json FROM local_settings WHERE singleton_id=1",
                                (rs, row) -> fromJson(rs.getString(1), LocalSettings.class).playerTag()),
                        moveCount, json(progress), attemptId.toString());
                jdbc.update("UPDATE results SET player_tag=(SELECT player_tag FROM history_entries WHERE record_id=?) WHERE attempt_id=?",
                        attemptId.toString(), attemptId.toString());
            }
            AttemptSnapshot response = load(attemptId);
            if (command.type() != AttemptCommand.CommandType.HEARTBEAT) jdbc.update(
                    """
                    INSERT INTO command_receipts
                      (attempt_id, command_id, payload_hash, response_json, revision)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    attemptId.toString(),
                    command.commandId(),
                    payloadHash,
                    json(response),
                    nextRevision);
            return response;
        });
    }

    public ResultSnapshot result(UUID attemptId) {
        Map<String, Object> row = queryOptional(
                "SELECT * FROM results WHERE attempt_id = ?", attemptId.toString());
        if (row == null) {
            throw ApiException.notFound("No completed result exists for this attempt");
        }
        return new ResultSnapshot(
                (String) row.get("RESULT_ID"),
                (String) row.get("ATTEMPT_ID"),
                intArray((String) row.get("VALUES_SNAPSHOT")),
                ((Number) row.get("MOVE_COUNT")).intValue(),
                timestamp(row.get("COMPLETED_AT")).toString(),
                (String) row.get("PLAYER_TAG"),
                row.get("PROGRESS_JSON") == null ? null
                        : snapshot(fromJson(text(row.get("PROGRESS_JSON")), GameProgress.class), true));
    }

    public AttemptSnapshot receipt(UUID attemptId, String commandId, String editorSessionId) {
        Map<String, Object> receipt = queryOptional(
                """
                SELECT payload_hash, response_json
                FROM command_receipts
                WHERE attempt_id = ? AND command_id = ?
                """,
                attemptId.toString(),
                commandId);
        if (receipt == null) {
            throw ApiException.notFound("No committed receipt exists for this command");
        }
        AttemptSnapshot current = load(attemptId);
        if (!current.editorSessionId().equals(editorSessionId)) {
            throw ApiException.conflict("This tab does not own the active editor lease");
        }
        return current;
    }

    private synchronized void refreshLease(UUID attemptId, String editorSessionId) {
        int updated = jdbc.update(
                """
                UPDATE attempts
                SET editor_lease_expires_at = ?, updated_at = CURRENT_TIMESTAMP
                WHERE attempt_id = ? AND editor_session_id = ?
                """,
                Timestamp.from(clock.now().plusSeconds(EDITOR_LEASE_SECONDS)),
                attemptId.toString(),
                editorSessionId);
        if (updated != 1) {
            throw ApiException.conflict("This tab no longer owns the active editor lease");
        }
    }

    private void requireMutable(String status) {
        if ("SUSPENDED".equals(status) || "COMPLETED".equals(status)) {
            throw ApiException.conflict("This attempt is not accepting board edits");
        }
    }

    private Map<String, Object> lockAttempt(UUID attemptId) {
        try {
            return jdbc.queryForMap(
                    """
                    SELECT attempt_id, puzzle_id, values_snapshot, status, revision, editor_session_id,
                           editor_lease_expires_at, move_count, progress_json
                    FROM attempts WHERE attempt_id = ? FOR UPDATE
                    """,
                    attemptId.toString());
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw ApiException.notFound("Attempt was not found");
        }
    }

    private AttemptSnapshot load(UUID attemptId) {
        Map<String, Object> attempt = queryOptional(
                """
                SELECT attempt_id, puzzle_id, values_snapshot, status, revision, editor_session_id,
                       editor_lease_expires_at, move_count, progress_json
                FROM attempts WHERE attempt_id = ?
                """,
                attemptId.toString());
        if (attempt == null) {
            throw ApiException.notFound("Attempt was not found");
        }
        PuzzleRow puzzle = loadPuzzle((String) attempt.get("PUZZLE_ID"));
        return new AttemptSnapshot(
                (String) attempt.get("ATTEMPT_ID"),
                (String) attempt.get("PUZZLE_ID"),
                puzzle.definition().columns(),
                puzzle.definition().rows(),
                puzzle.definition().regionIds(),
                puzzle.definition().givens(),
                intArray((String) attempt.get("VALUES_SNAPSHOT")),
                (String) attempt.get("STATUS"),
                ((Number) attempt.get("REVISION")).longValue(),
                (String) attempt.get("EDITOR_SESSION_ID"),
                timestamp(attempt.get("EDITOR_LEASE_EXPIRES_AT")) == null ? null
                        : timestamp(attempt.get("EDITOR_LEASE_EXPIRES_AT")).toString(),
                ((Number) attempt.get("MOVE_COUNT")).intValue(),
                snapshot(progress(attempt, puzzle.definition().cellCount()), "COMPLETED".equals(attempt.get("STATUS"))));
    }

    private PuzzleRow loadPuzzle(String puzzleId) {
        Map<String, Object> row = jdbc.queryForMap(
                """
                SELECT columns_count, rows_count, region_ids, givens, solution
                FROM puzzles WHERE puzzle_id = ?
                """,
                puzzleId);
        PuzzleDefinition definition = new PuzzleDefinition(
                ((Number) row.get("COLUMNS_COUNT")).intValue(),
                ((Number) row.get("ROWS_COUNT")).intValue(),
                intArray(text(row.get("REGION_IDS"))),
                intArray(text(row.get("GIVENS"))));
        return new PuzzleRow(definition, intArray(text(row.get("SOLUTION"))));
    }

    private GameProgress progress(Map<String, Object> row, int cells) {
        Object saved = row.get("PROGRESS_JSON");
        if (saved != null) return fromJson(text(saved), GameProgress.class);
        GameProgress initial = GameProgress.initial(cells, false);
        initial.valueEntryCount = ((Number) row.get("MOVE_COUNT")).intValue();
        initial.started = !"READY".equals(row.get("STATUS"));
        return initial;
    }

    private AttemptSnapshot.ProgressSnapshot snapshot(GameProgress progress, boolean completed) {
        return new AttemptSnapshot.ProgressSnapshot(
                progress.notes, progress.elapsedMillis, progress.started, progress.replay,
                progress.valueEntryCount, completed ? progress.wrongEntryCount : null, progress.candidateRequests,
                progress.explanationRequests, progress.reveals, progress.continuousConflictsUsed, progress.showConflicts,
                !progress.undo.isEmpty(), !progress.redo.isEmpty(), progress.checkpoint != null,
                progress.conflicts, progress.candidates, progress.helpMessage, progress.timingSequence);
    }

    private void suspendExpired(UUID id, Map<String, Object> row) {
        if (!"PLAYING".equals(row.get("STATUS"))) return;
        GameProgress progress = progress(row, intArray(text(row.get("VALUES_SNAPSHOT"))).length);
        Instant expiry = timestamp(row.get("EDITOR_LEASE_EXPIRES_AT"));
        if (!progress.timerBelongsTo(clock) || expiry == null || !expiry.isAfter(clock.now())) {
            long revision = ((Number) row.get("REVISION")).longValue() + 1;
            progress.timerProcessId = null;
            jdbc.update("UPDATE attempts SET status='SUSPENDED', revision=?, progress_json=? WHERE attempt_id=?",
                    revision, json(progress), id.toString());
            row.put("STATUS", "SUSPENDED");
            row.put("REVISION", revision);
            row.put("PROGRESS_JSON", json(progress));
        }
    }

    private Map<String, Object> queryOptional(String sql, Object... args) {
        return jdbc.query(sql, resultSet -> {
            if (!resultSet.next()) {
                return null;
            }
            java.sql.ResultSetMetaData metadata = resultSet.getMetaData();
            Map<String, Object> row = new HashMap<>();
            for (int column = 1; column <= metadata.getColumnCount(); column++) {
                Object value = resultSet.getObject(column);
                if (value instanceof java.sql.Clob clob) {
                    value = clob.getSubString(1, Math.toIntExact(clob.length()));
                }
                row.put(metadata.getColumnLabel(column).toUpperCase(java.util.Locale.ROOT), value);
            }
            return row;
        }, args);
    }

    private String text(Object value) {
        return StoreJson.text(value);
    }

    private String hash(Object value) {
        return StoreJson.checksum(json(value));
    }

    private String json(Object value) {
        return store.write(value);
    }

    private <T> T fromJson(String value, Class<T> targetType) {
        return store.read(value, targetType);
    }

    private int[] intArray(String json) {
        return fromJson(json, int[].class);
    }

    private Instant timestamp(Object value) {
        return switch (value) {
            case null -> null;
            case Timestamp timestamp -> timestamp.toInstant();
            case java.time.OffsetDateTime offsetDateTime -> offsetDateTime.toInstant();
            case Instant instant -> instant;
            default -> throw new IllegalStateException("Unsupported persisted editor-lease timestamp type");
        };
    }

    private record PuzzleRow(PuzzleDefinition definition, int[] solution) {
    }
}
