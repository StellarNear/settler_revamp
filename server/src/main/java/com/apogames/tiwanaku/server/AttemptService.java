package com.apogames.tiwanaku.server;

import com.apogames.tiwanaku.engine.BoardRules;
import com.apogames.tiwanaku.engine.GeneratedPuzzle;
import com.apogames.tiwanaku.engine.PuzzleDefinition;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public final class AttemptService {
    private static final long EDITOR_LEASE_SECONDS = 15;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final GenerationJobService jobs;
    private final ObjectMapper objectMapper;

    public AttemptService(
            JdbcTemplate jdbc,
            TransactionTemplate transactions,
            GenerationJobService jobs,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.jobs = jobs;
        this.objectMapper = objectMapper;
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

        if (request.generationJobId() == null) {
            throw ApiException.badRequest("A completed generation job is required");
        }
        GeneratedPuzzle generated = jobs.result(request.generationJobId());
        if (generated == null || generated.puzzle() == null || generated.solution() == null) {
            throw ApiException.conflict("The generation job has no certified puzzle");
        }
        UUID attemptId = UUID.randomUUID();
        UUID puzzleId = UUID.randomUUID();
        PuzzleDefinition puzzle = generated.puzzle();
        int[] values = puzzle.givens();
        Instant leaseExpiresAt = Instant.now().plusSeconds(EDITOR_LEASE_SECONDS);

        return transactions.execute(status -> {
            jdbc.update(
                    """
                    INSERT INTO puzzles (puzzle_id, columns_count, rows_count, region_ids, givens, solution)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    puzzleId.toString(),
                    puzzle.columns(),
                    puzzle.rows(),
                    json(puzzle.regionIds()),
                    json(puzzle.givens()),
                    json(generated.solution()));
            jdbc.update(
                    """
                    INSERT INTO attempts
                      (attempt_id, puzzle_id, values_snapshot, status, revision, editor_session_id,
                       editor_lease_expires_at, move_count)
                    VALUES (?, ?, ?, 'READY', 0, ?, ?, 0)
                    """,
                    attemptId.toString(),
                    puzzleId.toString(),
                    json(values),
                    request.editorSessionId(),
                    Timestamp.from(leaseExpiresAt));
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
        AttemptSnapshot snapshot = load(attemptId);
        if (snapshot.editorSessionId().equals(editorSessionId)) {
            refreshLease(attemptId, editorSessionId);
            return load(attemptId);
        }
        return snapshot;
    }

    public AttemptSnapshot command(UUID attemptId, AttemptCommand command) {
        String payloadHash = hash(command);
        return transactions.execute(status -> {
            Map<String, Object> attempt = lockAttempt(attemptId);
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
                    || !leaseExpiry.isAfter(Instant.now()))) {
                throw ApiException.conflict("The editor lease expired or belongs to another tab");
            }
            long currentRevision = ((Number) attempt.get("REVISION")).longValue();
            if (command.expectedRevision() != currentRevision) {
                throw ApiException.conflict("The attempt revision changed; reload the latest snapshot");
            }

            PuzzleRow puzzle = loadPuzzle((String) attempt.get("PUZZLE_ID"));
            int[] values = intArray((String) attempt.get("VALUES_SNAPSHOT"));
            String currentStatus = (String) attempt.get("STATUS");
            String nextStatus = currentStatus;
            String nextEditor = currentEditor;
            int moveCount = ((Number) attempt.get("MOVE_COUNT")).intValue();
            boolean changed = false;
            boolean complete = false;

            switch (command.type()) {
                case SET_VALUE -> {
                    requireMutable(currentStatus);
                    if (command.cellIndex() == null || command.value() == null
                            || command.cellIndex() >= values.length) {
                        throw ApiException.badRequest("A valid cell and value are required");
                    }
                    int cell = command.cellIndex();
                    if (puzzle.definition().givenAt(cell) != 0) {
                        throw ApiException.conflict("Given cells cannot be changed");
                    }
                    if (command.value() > puzzle.definition().regionSizeAt(cell)) {
                        throw ApiException.badRequest("The value exceeds this region's size");
                    }
                    values[cell] = command.value();
                    if (!BoardRules.isValidPartialBoard(puzzle.definition(), values)) {
                        throw ApiException.conflict("The move violates a puzzle rule");
                    }
                    nextStatus = "PLAYING";
                    moveCount++;
                    changed = true;
                    complete = BoardRules.isValidCompleteBoard(puzzle.definition(), values);
                    if (complete) {
                        nextStatus = "COMPLETED";
                    }
                }
                case PAUSE -> {
                    requireMutable(currentStatus);
                    nextStatus = "SUSPENDED";
                    changed = true;
                }
                case RESUME -> {
                    if (!"SUSPENDED".equals(currentStatus) && !"READY".equals(currentStatus)) {
                        throw ApiException.conflict("Only a ready or suspended attempt can be resumed");
                    }
                    nextStatus = "PLAYING";
                    changed = true;
                }
                case TAKEOVER -> {
                    nextEditor = command.editorSessionId();
                    changed = !currentEditor.equals(nextEditor);
                }
            }

            long nextRevision = currentRevision + (changed ? 1 : 0);
            Instant nextLeaseExpiry = Instant.now().plusSeconds(EDITOR_LEASE_SECONDS);
            jdbc.update(
                    """
                    UPDATE attempts
                    SET values_snapshot = ?, status = ?, revision = ?, editor_session_id = ?,
                        editor_lease_expires_at = ?, move_count = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE attempt_id = ?
                    """,
                    json(values),
                    nextStatus,
                    nextRevision,
                    nextEditor,
                    Timestamp.from(nextLeaseExpiry),
                    moveCount,
                    attemptId.toString());

            if (complete) {
                jdbc.update(
                        """
                        INSERT INTO results (result_id, attempt_id, values_snapshot, move_count)
                        VALUES (?, ?, ?, ?)
                        """,
                        UUID.randomUUID().toString(),
                        attemptId.toString(),
                        json(values),
                        moveCount);
            }
            AttemptSnapshot response = load(attemptId);
            jdbc.update(
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
                (String) row.get("PLAYER_TAG"));
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
        AttemptSnapshot saved = fromJson((String) receipt.get("RESPONSE_JSON"), AttemptSnapshot.class);
        AttemptSnapshot current = load(attemptId);
        if (!saved.editorSessionId().equals(editorSessionId)
                || !current.editorSessionId().equals(editorSessionId)) {
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
                Timestamp.from(Instant.now().plusSeconds(EDITOR_LEASE_SECONDS)),
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
                           editor_lease_expires_at, move_count
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
                       editor_lease_expires_at, move_count
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
                String.valueOf(attempt.get("EDITOR_LEASE_EXPIRES_AT")),
                ((Number) attempt.get("MOVE_COUNT")).intValue());
    }

    private PuzzleRow loadPuzzle(String puzzleId) {
        Map<String, Object> row = jdbc.queryForMap(
                """
                SELECT columns_count, rows_count, region_ids, givens
                FROM puzzles WHERE puzzle_id = ?
                """,
                puzzleId);
        PuzzleDefinition definition = new PuzzleDefinition(
                ((Number) row.get("COLUMNS_COUNT")).intValue(),
                ((Number) row.get("ROWS_COUNT")).intValue(),
                intArray(text(row.get("REGION_IDS"))),
                intArray(text(row.get("GIVENS"))));
        return new PuzzleRow(definition);
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
        if (value instanceof java.sql.Clob clob) {
            try {
                return clob.getSubString(1, Math.toIntExact(clob.length()));
            } catch (java.sql.SQLException exception) {
                throw new IllegalStateException("Unable to read persisted puzzle data", exception);
            }
        }
        return (String) value;
    }

    private String hash(Object value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(json(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize local attempt state", exception);
        }
    }

    private <T> T fromJson(String value, Class<T> targetType) {
        try {
            return objectMapper.readValue(value, targetType);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Persisted attempt state could not be read", exception);
        }
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

    private record PuzzleRow(PuzzleDefinition definition) {
    }
}
