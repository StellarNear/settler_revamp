package com.apogames.tiwanaku.server;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayablePersistenceTest {
    @TempDir Path directory;

    @Test
    void upgradesV1AndRetainsBoardNotesHistoryCheckpointAndCompletionAcrossDatabaseReopen() {
        String url = "jdbc:h2:file:" + directory.resolve("playable") + ";DB_CLOSE_ON_EXIT=FALSE";
        Flyway.configure().dataSource(url, "sa", "").target("1").load().migrate();
        var source = new DriverManagerDataSource(url, "sa", "");
        var jdbc = new JdbcTemplate(source);
        String editor = "persisted-editor-session";
        UUID id = UUID.randomUUID();
        UUID puzzle = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO puzzles(puzzle_id,columns_count,rows_count,region_ids,givens,solution)
                VALUES (?,3,1,'[0,0,0]','[0,0,3]','[1,2,3]')
                """, puzzle.toString());
        jdbc.update("""
                INSERT INTO attempts(attempt_id,puzzle_id,values_snapshot,status,editor_session_id,editor_lease_expires_at)
                VALUES (?,?,'[0,0,3]','READY',?,?)
                """, id.toString(), puzzle.toString(), editor,
                Timestamp.from(Instant.now().plusSeconds(15)));
        Flyway.configure().dataSource(url, "sa", "").load().migrate();
        var clock = new PlayableAttemptTest.FakeClock();
        var service = service(source, clock);
        send(service, id, editor, "TOGGLE_NOTE", 1, 2);
        send(service, id, editor, "SET_VALUE", 0, 1);
        send(service, id, editor, "CHECKPOINT", null, null);
        clock.advance(5_000);
        service.command(id, new AttemptCommand("saved-heartbeat", service.get(id, editor).revision(),
                editor, AttemptCommand.CommandType.HEARTBEAT, null, null, 1L, 5_000L));
        jdbc.execute("SHUTDOWN");

        var reopenedSource = new DriverManagerDataSource(url + ";IFEXISTS=TRUE", "sa", "");
        var reopened = service(reopenedSource, new GameplayClock());
        reopened.invalidatePreviousProcessLeases();
        AttemptSnapshot restored = reopened.get(id, editor);
        assertEquals("SUSPENDED", restored.status());
        assertEquals(5_000, restored.progress().elapsedMillis());
        assertArrayEquals(new int[]{1, 0, 3}, restored.values());
        assertArrayEquals(new int[]{0, 2, 0}, restored.progress().notes());
        assertTrue(restored.progress().hasCheckpoint());
        assertTrue(restored.progress().canUndo());
        send(reopened, id, editor, "RESUME", null, null);
        send(reopened, id, editor, "UNDO", null, null);
        assertArrayEquals(new int[]{0, 0, 3}, reopened.get(id, editor).values());
        send(reopened, id, editor, "RESTORE_CHECKPOINT", null, null);
        AttemptCommand finalMove = new AttemptCommand("persisted-final-move", reopened.get(id, editor).revision(),
                editor, AttemptCommand.CommandType.SET_VALUE, 1, 2);
        assertEquals("COMPLETED", reopened.command(id, finalMove).status());
        new JdbcTemplate(reopenedSource).execute("SHUTDOWN");

        var completed = service(new DriverManagerDataSource(url + ";IFEXISTS=TRUE", "sa", ""), new GameplayClock());
        assertEquals("COMPLETED", completed.command(id, finalMove).status());
        assertEquals(id.toString(), completed.result(id).resultId());
        assertEquals(5_000, completed.result(id).progress().elapsedMillis());
        assertEquals(2, completed.result(id).moveCount());
    }

    private AttemptService service(DriverManagerDataSource source, GameplayClock clock) {
        return new AttemptService(new JdbcTemplate(source),
                new TransactionTemplate(new DataSourceTransactionManager(source)),
                null, new ObjectMapper(), clock);
    }

    private void send(AttemptService service, UUID id, String editor, String type, Integer cell, Integer value) {
        service.command(id, new AttemptCommand(UUID.randomUUID().toString(), service.get(id, editor).revision(),
                editor, AttemptCommand.CommandType.valueOf(type), cell, value));
    }
}
