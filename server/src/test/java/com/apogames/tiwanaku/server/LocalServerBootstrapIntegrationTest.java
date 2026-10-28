package com.apogames.tiwanaku.server;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LocalServerBootstrapIntegrationTest {
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void servesBootstrapFromTheLoopbackOnlySpringServer() throws Exception {
        HttpResponse<String> response = send("GET", "/api/v1/bootstrap", null, null);

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"apiVersion\":\"v1\""));
        assertTrue(response.body().contains("\"requestToken\":\""));
        assertFalse(response.body().contains("\"requestToken\":\"\""));
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
    }

    @Test
    void servesOnlyKnownSpaRoutesWithoutMaskingApiAndAssetErrors() throws Exception {
        for (String route : new String[]{"/setup", "/play", "/history", "/settings"}) {
            HttpResponse<String> response = send("GET", route, null, null);
            assertEquals(200, response.statusCode(), route);
            assertTrue(response.body().contains("id=\"app\""));
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        }
        for (String route : new String[]{"/api/v1/missing", "/assets/missing.js", "/unknown-page"}) {
            assertEquals(404, send("GET", route, null, null).statusCode(), route);
        }
        HttpResponse<String> conditionalRoot = CLIENT.send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/"))
                .header("If-Modified-Since", java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)
                        .plusDays(1).format(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, conditionalRoot.statusCode());
        assertTrue(conditionalRoot.body().contains("id=\"app\""));
    }

    @Test
    void rejectsUnknownV1RequestProperties() throws Exception {
        assertEquals(400, send("POST", "/api/v1/generation-jobs",
                """
                {"columns":3,"rows":3,"maxRegionSize":5,"difficulty":"EASY","seed":1,"unexpected":true}
                """, bootstrapToken()).statusCode());
    }

    @Test
    void blocksMutationWithoutLocalOriginAndToken() throws Exception {
        HttpResponse<String> response = send("POST", "/api/v1/attempts", "{}", null);

        assertEquals(403, response.statusCode());
    }

    @Test
    void cancelsLargeGenerationWithoutCertifyingThePartialSearch() throws Exception {
        String token = bootstrapToken();
        Map<?, ?> submitted = json(send(
                "POST",
                "/api/v1/generation-jobs",
                """
                {"columns":20,"rows":12,"maxRegionSize":9,"difficulty":"HARD","seed":3045}
                """,
                token));
        String jobId = (String) submitted.get("jobId");
        HttpResponse<String> cancellation = send(
                "DELETE",
                "/api/v1/generation-jobs/" + jobId,
                "{}",
                token);
        assertEquals(200, cancellation.statusCode());
        assertTrue(java.util.Set.of("CANCELLING", "CANCELLED").contains(json(cancellation).get("status")));

        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        Map<?, ?> state;
        do {
            Thread.sleep(25);
            state = json(send("GET", "/api/v1/generation-jobs/" + jobId, null, null));
        } while (!"CANCELLED".equals(state.get("status")) && System.nanoTime() < deadline);
        assertEquals("CANCELLED", state.get("status"));
    }

    @Test
    void generatesHexagonBoardsWithTheirTileGeometry() throws Exception {
        String token = bootstrapToken();
        String jobId = (String) json(send("POST", "/api/v1/generation-jobs",
                """
                {"columns":4,"rows":4,"maxRegionSize":5,"difficulty":"EASY","seed":7,"shape":"HEXAGON"}
                """, token)).get("jobId");
        Map<?, ?> job;
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        do {
            Thread.sleep(25);
            job = json(send("GET", "/api/v1/generation-jobs/" + jobId, null, null));
        } while (!"GENERATED".equals(job.get("status")) && System.nanoTime() < deadline);
        assertEquals("GENERATED", job.get("status"));

        HttpResponse<String> created = send("POST", "/api/v1/attempts", JSON.writeValueAsString(Map.of(
                "requestId", UUID.randomUUID().toString(),
                "generationJobId", jobId,
                "editorSessionId", UUID.randomUUID().toString())), token);
        assertEquals(200, created.statusCode(), created.body());
        AttemptSnapshot attempt = JSON.readValue(created.body(), AttemptSnapshot.class);
        assertEquals("HEXAGON", attempt.puzzleSettings().shape());
        assertEquals(16, attempt.cellPolygons().length);
        for (double[] polygon : attempt.cellPolygons()) {
            assertEquals(12, polygon.length);
        }

        HttpResponse<String> reloaded = send("GET", "/api/v1/attempts/" + attempt.attemptId()
                + "?editorSessionId=" + UUID.randomUUID(), null, null);
        assertEquals(200, reloaded.statusCode());
        assertEquals(16, JSON.readValue(reloaded.body(), AttemptSnapshot.class).cellPolygons().length);

        assertEquals(400, send("POST", "/api/v1/generation-jobs",
                """
                {"columns":4,"rows":4,"maxRegionSize":5,"difficulty":"EASY","seed":7,"shape":"OCTAGON"}
                """, token).statusCode());
    }
    @Test
    void generatesPersistsAndDeduplicatesACompletedAttempt() throws Exception {
        String token = bootstrapToken();
        Map<?, ?> jobResponse = json(send(
                "POST",
                "/api/v1/generation-jobs",
                """
                {"columns":2,"rows":1,"maxRegionSize":5,"difficulty":"EASY","seed":3045}
                """,
                token));
        String jobId = (String) jobResponse.get("jobId");
        Map<?, ?> job;
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        do {
            Thread.sleep(25);
            job = json(send("GET", "/api/v1/generation-jobs/" + jobId, null, null));
        } while (!"GENERATED".equals(job.get("status")) && System.nanoTime() < deadline);
        assertEquals("GENERATED", job.get("status"));
        assertEquals(100, job.get("progressPercent"));
        assertEquals(1, job.get("tryNumber"));

        String editor = UUID.randomUUID().toString();
        HttpResponse<String> createResponse = send(
                "POST",
                "/api/v1/attempts",
                JSON.writeValueAsString(Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "generationJobId", jobId,
                        "editorSessionId", editor)),
                token);
        assertEquals(200, createResponse.statusCode(), createResponse.body());
        AttemptSnapshot created = JSON.readValue(createResponse.body(), AttemptSnapshot.class);
        assertEquals("SQUARE", created.puzzleSettings().shape());
        assertNull(created.cellPolygons());
        String attemptId = created.attemptId();
        int[] givens = created.givens();
        assertEquals(1, countGivens(givens));
        int emptyCell = givens[0] == 0 ? 0 : 1;
        int givenCell = 1 - emptyCell;
        int answer = givens[givenCell] == 1 ? 2 : 1;

        String secondEditor = UUID.randomUUID().toString();
        HttpResponse<String> observerSnapshot = send(
                "GET",
                "/api/v1/attempts/" + attemptId + "?editorSessionId=" + secondEditor,
                null,
                null);
        assertEquals(200, observerSnapshot.statusCode());
        assertEquals(0, json(observerSnapshot).get("revision"));

        HttpResponse<String> takeover = send(
                "POST",
                "/api/v1/attempts/" + attemptId + "/commands",
                command("takeover", 0, secondEditor, "TAKEOVER", null, null),
                token);
        assertEquals(200, takeover.statusCode(), takeover.body());
        assertEquals(1, json(takeover).get("revision"));

        HttpResponse<String> oldEditorMutation = send(
                "POST",
                "/api/v1/attempts/" + attemptId + "/commands",
                command("old-tab", 0, editor, "SET_VALUE", emptyCell, answer),
                token);
        assertEquals(409, oldEditorMutation.statusCode());
        assertEquals("The editor lease expired or belongs to another tab",
                json(oldEditorMutation).get("detail"));

        HttpResponse<String> resumed = send("POST", "/api/v1/attempts/" + attemptId + "/commands",
                command("resume", 1, secondEditor, "RESUME", null, null), token);
        assertEquals(200, resumed.statusCode(), resumed.body());
        assertEquals("READY", json(resumed).get("status"));
        String moveCommand = command("complete", 2, secondEditor, "SET_VALUE", emptyCell, answer);
        HttpResponse<String> completed = send(
                "POST",
                "/api/v1/attempts/" + attemptId + "/commands",
                moveCommand,
                token);
        assertEquals(200, completed.statusCode(), completed.body());
        AttemptSnapshot completedSnapshot = JSON.readValue(completed.body(), AttemptSnapshot.class);
        assertEquals("COMPLETED", completedSnapshot.status());
        assertEquals(3, completedSnapshot.revision());
        assertEquals(answer, completedSnapshot.values()[emptyCell]);

        HttpResponse<String> retry = send(
                "POST",
                "/api/v1/attempts/" + attemptId + "/commands",
                moveCommand,
                token);
        assertEquals(200, retry.statusCode(), retry.body());
        assertEquals("COMPLETED", json(retry).get("status"));
        assertEquals(1, jdbcCount("results", attemptId));
        HttpResponse<String> savedResult = send("GET", "/api/v1/attempts/" + attemptId + "/result", null, null);
        assertEquals(200, savedResult.statusCode());
        Map<?, ?> result = json(savedResult);
        assertEquals(java.util.Set.of("resultId", "attemptId", "values", "moveCount", "completedAt", "playerTag", "progress"),
                result.keySet());
        assertEquals(attemptId, result.get("attemptId"));
        assertEquals(1, result.get("moveCount"));
        assertTrue(result.get("completedAt") instanceof String);

        HttpResponse<String> changedPayload = send(
                "POST",
                "/api/v1/attempts/" + attemptId + "/commands",
                command("complete", 1, secondEditor, "SET_VALUE", emptyCell, 0),
                token);
        assertEquals(409, changedPayload.statusCode());

        HttpResponse<String> staleRevision = send(
                "POST",
                "/api/v1/attempts/" + attemptId + "/commands",
                command("stale", 1, secondEditor, "PAUSE", null, null),
                token);
        assertEquals(409, staleRevision.statusCode());
        assertEquals("The attempt revision changed; reload the latest snapshot",
                json(staleRevision).get("detail"));
    }

    private String bootstrapToken() throws Exception {
        Map<?, ?> response = json(send("GET", "/api/v1/bootstrap", null, null));
        return (String) response.get("requestToken");
    }

    private HttpResponse<String> send(String method, String path, String body, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + path));
        if (body == null) {
            request.GET();
        } else {
            request.header("Origin", "http://127.0.0.1:" + port)
                    .header("Content-Type", "application/json");
            if (token != null) request.header("X-Tiwanaku-Token", token);
            request.method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String command(
            String id,
            long revision,
            String editor,
            String type,
            Integer cell,
            Integer value) throws Exception {
        Map<String, Object> command = new java.util.HashMap<>();
        command.put("commandId", id);
        command.put("expectedRevision", revision);
        command.put("editorSessionId", editor);
        command.put("type", type);
        command.put("cellIndex", cell);
        command.put("value", value);
        return JSON.writeValueAsString(command);
    }

    private static Map<?, ?> json(HttpResponse<String> response) throws Exception {
        return JSON.readValue(response.body(), Map.class);
    }

    private static int countGivens(int[] givens) {
        int count = 0;
        for (int given : givens) if (given != 0) count++;
        return count;
    }

    private int jdbcCount(String table, String attemptId) {
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE attempt_id = ?";
        return jdbc.queryForObject(sql, Integer.class, attemptId);
    }
}
