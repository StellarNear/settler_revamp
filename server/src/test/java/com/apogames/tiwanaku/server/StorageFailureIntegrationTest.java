package com.apogames.tiwanaku.server;

import org.h2.api.Trigger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.net.URI;
import java.net.http.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StorageFailureIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired AttemptService attempts;
    @Autowired HistoryService history;
    @Autowired StoreJson json;
    @LocalServerPort int port;
    public static class DiskFull implements Trigger {
        @Override public void fire(Connection connection, Object[] oldRow, Object[] newRow) throws SQLException {
            throw new SQLException("Simulated disk-full write failure", "HY000", 90128);
        }
    }

    @Test void storageFailureReturns503AndRollsBackCompletionAndAllReceiptsUntilSameIdRetry() throws Exception {
        UUID puzzle = UUID.randomUUID(), id = UUID.randomUUID();
        String editor = "storage-failure-editor";
        jdbc.update("""
                INSERT INTO puzzles(puzzle_id,columns_count,rows_count,region_ids,givens,solution)
                VALUES (?,2,1,'[0,0]','[0,2]','[1,2]')
                """, puzzle.toString());
        jdbc.update("""
                INSERT INTO attempts(attempt_id,puzzle_id,values_snapshot,status,editor_session_id,editor_lease_expires_at)
                VALUES (?,?,'[0,2]','READY',?,?)
                """, id.toString(), puzzle.toString(), editor, java.sql.Timestamp.from(Instant.now().plusSeconds(15)));
        var client = HttpClient.newHttpClient();
        var bootstrap = json.read(client.send(HttpRequest.newBuilder(URI.create(url("/api/v1/bootstrap"))).build(),
                HttpResponse.BodyHandlers.ofString()).body(), java.util.Map.class);
        var command = new AttemptCommand("disk-full-final", 0, editor, AttemptCommand.CommandType.SET_VALUE, 0, 1);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url("/api/v1/attempts/" + id + "/commands")))
                .header("Content-Type", "application/json").header("Origin", "http://localhost:" + port)
                .header("X-Tiwanaku-Token", bootstrap.get("requestToken").toString())
                .POST(HttpRequest.BodyPublishers.ofString(json.write(command))).build();
        jdbc.execute("CREATE TRIGGER fail_history BEFORE INSERT ON history_entries FOR EACH ROW CALL '" + DiskFull.class.getName() + "'");
        try {
            var failure = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(503, failure.statusCode());
            assertTrue(failure.body().contains("No successful save is acknowledged"));
            assertEquals("READY", attempts.get(id, editor).status());
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM results WHERE attempt_id=?", Integer.class, id.toString()));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM command_receipts WHERE attempt_id=?", Integer.class, id.toString()));
            String raw = "SMALL|1000|75";
            var preview = history.preview(raw, "legacy");
            assertThrows(org.springframework.dao.DataAccessException.class,
                    () -> history.importSource(raw, "legacy", preview.fingerprint(), false));
            assertFalse(history.preview(raw, "legacy").alreadyImported());
        } finally { jdbc.execute("DROP TRIGGER fail_history"); }
        assertEquals(200, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(200, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM results WHERE attempt_id=?", Integer.class, id.toString()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM history_entries WHERE attempt_id=?", Integer.class, id.toString()));
    }
    private String url(String path) { return "http://localhost:" + port + path; }
}
