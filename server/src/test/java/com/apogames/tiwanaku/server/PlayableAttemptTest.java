package com.apogames.tiwanaku.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class PlayableAttemptTest {
    @Autowired AttemptService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired FakeClock clock;
    private UUID id;
    private static final String EDITOR = "playable-test-editor";

    @TestConfiguration
    static class Configuration {
        @Bean @Primary FakeClock fakeClock() {
            return new FakeClock();
        }
    }

    static class FakeClock extends GameplayClock {
        private long millis;
        private String process = "test-process";
        @Override public Instant now() { return Instant.parse("2026-10-27T12:00:00Z").plusMillis(millis); }
        @Override public long nanos() { return millis * 1_000_000; }
        @Override public String processId() { return process; }
        void advance(long delta) { millis += delta; }
    }

    @BeforeEach
    void fixture() {
        clock.millis = 0;
        clock.process = "test-process";
        UUID puzzle = UUID.randomUUID();
        id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO puzzles(puzzle_id,columns_count,rows_count,region_ids,givens,solution)
                VALUES (?,3,1,'[0,0,0]','[0,0,3]','[1,2,3]')
                """, puzzle.toString());
        jdbc.update("""
                INSERT INTO attempts(attempt_id,puzzle_id,values_snapshot,status,editor_session_id,editor_lease_expires_at)
                VALUES (?,?,'[0,0,3]','READY',?,?)
                """, id.toString(), puzzle.toString(), EDITOR, java.sql.Timestamp.from(clock.now().plusSeconds(15)));
    }

    private AttemptSnapshot current() { return service.get(id, EDITOR); }
    private AttemptSnapshot send(String type, Integer cell, Integer value) {
        return service.command(id, new AttemptCommand(UUID.randomUUID().toString(), current().revision(),
                EDITOR, AttemptCommand.CommandType.valueOf(type), cell, value));
    }
    private AttemptSnapshot timed(String type, long sequence, long elapsed) {
        return service.command(id, new AttemptCommand(UUID.randomUUID().toString(), current().revision(),
                EDITOR, AttemptCommand.CommandType.valueOf(type), null, null, sequence, elapsed));
    }

    @Test
    void crashLossIsOnlyUnacknowledgedActiveTimeAndStartupInvalidatesLeases() {
        send("SET_VALUE", 0, 1);
        clock.advance(5_000);
        timed("HEARTBEAT", 1, 5_000);
        clock.advance(4_000);
        clock.process = "restarted-process";
        service.invalidatePreviousProcessLeases();
        clock.advance(3600_000);
        AttemptSnapshot restored = current();
        assertEquals("SUSPENDED", restored.status());
        assertEquals(5_000, restored.progress().elapsedMillis());
        assertEquals(4_000, 9_000 - restored.progress().elapsedMillis());
        send("RESUME", null, null);
        clock.advance(2_000);
        assertEquals(7_000, timed("HEARTBEAT", 1, 7_000).progress().elapsedMillis());
    }

    @Test
    void boundedHistoryAndContinuousAssistanceSurviveReloadWithoutRollingBackCounters() {
        send("SHOW_CONFLICTS", null, 1);
        send("SHOW_CONFLICTS", null, 0);
        for (int i = 0; i < 110; i++) send("TOGGLE_NOTE", 0, 1);
        for (int i = 0; i < 100; i++) send("UNDO", null, null);
        assertThrows(ApiException.class, () -> send("UNDO", null, null));
        AttemptSnapshot saved = current();
        assertTrue(saved.progress().continuousConflictsUsed());
        assertFalse(saved.progress().showConflicts());
        assertFalse(saved.progress().started());
        assertThrows(ApiException.class, () -> timed("HEARTBEAT", 1, 0));
    }

    @Test
    void notesHelpAndUnstartedResumeDoNotStartTiming() {
        AttemptSnapshot notes = send("TOGGLE_NOTE", 0, 2);
        assertEquals(2, notes.progress().notes()[0]);
        assertFalse(notes.progress().started());
        assertEquals(0, notes.moveCount());
        send("CANDIDATES", 0, null);
        send("EXPLAIN", null, null);
        send("PAUSE", null, null);
        clock.advance(60_000);
        AttemptSnapshot resumed = send("RESUME", null, null);
        assertEquals("READY", resumed.status());
        assertEquals(0, resumed.progress().elapsedMillis());
        assertEquals(1, resumed.progress().candidateRequests());
        assertEquals(1, resumed.progress().explanationRequests());
    }

    @Test
    void undoRedoAndCheckpointRestoreOnlyBoardAndNotesNotCountersOrTime() {
        send("SET_VALUE", 0, 2);
        timed("HEARTBEAT", 1, 0);
        send("CHECKPOINT", null, null);
        clock.advance(4_000);
        timed("HEARTBEAT", 2, 4_000);
        send("SET_VALUE", 0, 1);
        send("TOGGLE_NOTE", 1, 2);
        AttemptSnapshot restored = send("RESTORE_CHECKPOINT", null, null);
        assertArrayEquals(new int[]{2, 0, 3}, restored.values());
        assertArrayEquals(new int[]{0, 0, 0}, restored.progress().notes());
        assertEquals(2, restored.moveCount());
        assertEquals(4_000, restored.progress().elapsedMillis());
        AttemptSnapshot undone = send("UNDO", null, null);
        assertArrayEquals(new int[]{1, 0, 3}, undone.values());
        assertEquals(2, undone.progress().notes()[1]);
        assertEquals(2, undone.moveCount());
        assertArrayEquals(restored.values(), send("REDO", null, null).values());
    }

    @Test
    void conflictingAndCanonicalWrongEntriesAreDistinctAndGivensAreImmutable() {
        AttemptSnapshot wrong = send("SET_VALUE", 0, 3);
        assertEquals("PLAYING", wrong.status());
        assertNull(wrong.progress().wrongEntryCount());
        assertArrayEquals(new int[0], wrong.progress().conflicts());
        AttemptSnapshot checked = send("CHECK", null, null);
        assertArrayEquals(new int[]{0, 2}, checked.progress().conflicts());
        assertThrows(ApiException.class, () -> send("SET_VALUE", 2, 1));
        assertThrows(ApiException.class, () -> send("SET_VALUE", 0, 4));
        assertThrows(ApiException.class, () -> send("TOGGLE_NOTE", 0, 1));
        send("SET_VALUE", 0, 0);
        assertThrows(ApiException.class, () -> send("TOGGLE_NOTE", 0, 4));
        assertEquals(1, current().moveCount());
    }

    @Test
    void cycleWrapsBothDirectionsAndCountsOnlyChangedNonzeroEntries() {
        assertEquals(3, send("CYCLE_VALUE", 0, -1).values()[0]);
        assertEquals(0, send("CYCLE_VALUE", 0, 1).values()[0]);
        assertEquals(1, send("CYCLE_VALUE", 0, 1).values()[0]);
        assertEquals(2, current().moveCount());
        send("SET_VALUE", 0, 1);
        assertEquals(2, current().moveCount());
    }

    @Test
    void legacyClickAccuracyUsesTheCheapestDirectionAndCountsWastedClueClicks() {
        assertEquals(100, current().progress().legacyAccuracy());
        AttemptSnapshot fromMaximum = send("CYCLE_VALUE", 0, -1);
        assertEquals(3, fromMaximum.values()[0]);
        assertEquals(1, fromMaximum.progress().totalClicks());
        assertEquals(0, fromMaximum.progress().totalMissClicks());
        assertEquals(100, fromMaximum.progress().legacyAccuracy());

        AttemptSnapshot cleared = send("CYCLE_VALUE", 0, 1);
        assertEquals(0, cleared.values()[0]);
        assertEquals(2, cleared.progress().totalClicks());
        assertEquals(2, cleared.progress().totalMissClicks());
        assertEquals(0, cleared.progress().legacyAccuracy());

        AttemptSnapshot clueClick = send("CYCLE_VALUE", 2, -1);
        assertEquals(3, clueClick.values()[2]);
        assertEquals(3, clueClick.progress().totalClicks());
        assertEquals(3, clueClick.progress().totalMissClicks());
        assertEquals(0, clueClick.progress().legacyAccuracy());
    }

    @Test
    void clickAccuracyIsSavedInHistoryWhenTheMouseCycleCompletesThePuzzle() {
        send("CYCLE_VALUE", 0, 1);
        send("CYCLE_VALUE", 1, -1);
        AttemptSnapshot completed = send("CYCLE_VALUE", 1, -1);

        assertEquals("COMPLETED", completed.status());
        assertEquals(3, completed.progress().totalClicks());
        assertEquals(0, completed.progress().totalMissClicks());
        assertEquals(100, completed.progress().legacyAccuracy());
        assertEquals("legacy-clicks-v1", jdbc.queryForObject(
                "SELECT scoring_version FROM history_entries WHERE record_id=?",
                String.class, id.toString()));
        assertEquals(100, jdbc.queryForObject(
                "SELECT legacy_accuracy FROM history_entries WHERE record_id=?",
                Integer.class, id.toString()));
        assertEquals(3L, jdbc.queryForObject(
                "SELECT total_clicks FROM history_entries WHERE record_id=?",
                Long.class, id.toString()));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT total_miss_clicks FROM history_entries WHERE record_id=?",
                Long.class, id.toString()));
    }

    @Test
    void directValueEntryDoesNotClaimLegacyClickAccuracy() {
        AttemptSnapshot direct = send("SET_VALUE", 0, 1);

        assertNull(direct.progress().legacyAccuracy());
        assertTrue(direct.progress().directValueEntryUsed());
    }

    @Test
    void timerHeartbeatIsMonotonicBoundedIdempotentAndDoesNotAdvanceGameplayRevision() {
        long revision = send("SET_VALUE", 0, 1).revision();
        clock.advance(5_000);
        assertEquals(revision, timed("HEARTBEAT", 1, 5_000).revision());
        assertEquals(5_000, timed("HEARTBEAT", 1, 500).progress().elapsedMillis());
        assertThrows(ApiException.class, () -> timed("HEARTBEAT", 2, 4_000));
        assertThrows(ApiException.class, () -> timed("HEARTBEAT", 2, 6_001));
        assertEquals(6_000, timed("HEARTBEAT", 2, 6_000).progress().elapsedMillis());
        assertEquals(revision, current().revision());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM command_receipts WHERE attempt_id=?", Integer.class, id.toString()));
    }

    @Test
    void pauseClosedTimeExpiryAndProcessRestartNeverAccrueWallTime() {
        send("SET_VALUE", 0, 1);
        clock.advance(4_000);
        timed("PAUSE", 1, 4_000);
        for (String type : new String[]{"SET_VALUE", "UNDO", "RESTORE_CHECKPOINT", "CANDIDATES", "CHECK"}) {
            assertThrows(ApiException.class, () -> send(type, 0, 2), type);
        }
        clock.advance(3600_000);
        assertEquals(4_000, current().progress().elapsedMillis());
        send("RESUME", null, null);
        clock.advance(2_000);
        timed("HEARTBEAT", 1, 6_000);
        clock.advance(16_000);
        assertEquals("SUSPENDED", current().status());
        assertThrows(ApiException.class, () -> timed("HEARTBEAT", 2, 7_000));
        send("RESUME", null, null);
        clock.process = "new-process";
        assertEquals("SUSPENDED", current().status());
        assertEquals(6_000, current().progress().elapsedMillis());
    }

    @Test
    void simultaneousRetriesOfTheFinalMoveCommitOneResult() throws Exception {
        send("SET_VALUE", 0, 1);
        AttemptCommand finalMove = new AttemptCommand("concurrent-final-move", current().revision(), EDITOR,
                AttemptCommand.CommandType.SET_VALUE, 1, 2);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.command(id, finalMove));
            var second = executor.submit(() -> service.command(id, finalMove));
            assertEquals("COMPLETED", first.get().status());
            assertEquals("COMPLETED", second.get().status());
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM results WHERE attempt_id=?", Integer.class, id.toString()));
        assertEquals(2, current().moveCount());
    }

    @Test
    void completionAndLostResponseRetryPersistExactlyOneImmutableResult() {
        send("SET_VALUE", 0, 2);
        send("SET_VALUE", 0, 1);
        clock.advance(5_000);
        AttemptCommand finalMove = new AttemptCommand("lost-response-command", current().revision(), EDITOR,
                AttemptCommand.CommandType.SET_VALUE, 1, 2, 1L, 5_000L);
        service.command(id, finalMove);
        AttemptSnapshot retried = service.command(id, finalMove);
        assertEquals("COMPLETED", retried.status());
        assertEquals(5_000, retried.progress().elapsedMillis());
        assertEquals(1, retried.progress().wrongEntryCount());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM results WHERE attempt_id=?", Integer.class, id.toString()));
        ResultSnapshot result = service.result(id);
        assertEquals(id.toString(), result.resultId());
        assertEquals(3, result.moveCount());
        assertEquals(5_000, result.progress().elapsedMillis());
        for (String type : new String[]{"UNDO", "PAUSE", "TOGGLE_NOTE", "CHECK", "REVEAL", "HEARTBEAT"}) {
            assertThrows(ApiException.class, () -> send(type, 0, 1), type);
        }
    }

    @Test
    void restartCreatesAnIdempotentReplayWithoutChangingTheOriginal() {
        send("SET_VALUE", 0, 1);
        send("CHECKPOINT", null, null);
        CreateAttemptRequest restart = new CreateAttemptRequest("restart-" + id, null, EDITOR, id);
        AttemptSnapshot replay = service.create(restart);
        assertEquals(replay.attemptId(), service.create(restart).attemptId());
        assertEquals(current().puzzleId(), replay.puzzleId());
        assertNotEquals(id.toString(), replay.attemptId());
        assertTrue(replay.progress().replay());
        assertFalse(replay.progress().hasCheckpoint());
        assertFalse(replay.progress().canUndo());
        assertArrayEquals(new int[]{0, 0, 3}, replay.values());
        assertEquals(0, replay.progress().elapsedMillis());
        assertArrayEquals(new int[]{1, 0, 3}, current().values());
    }

    @Test
    void takeoverSuspendsAndRequiresExplicitResumeAndRejectsOldEditor() {
        send("SET_VALUE", 0, 1);
        String other = "different-editor-session";
        AttemptSnapshot taken = service.command(id, new AttemptCommand("take-" + id, current().revision(), other,
                AttemptCommand.CommandType.TAKEOVER, null, null));
        assertEquals("SUSPENDED", taken.status());
        assertThrows(ApiException.class, () -> send("SET_VALUE", 1, 2));
        assertThrows(ApiException.class, () -> service.command(id, new AttemptCommand("other-move", taken.revision(), other,
                AttemptCommand.CommandType.SET_VALUE, 1, 2)));
    }

    @Test
    void helpDistinguishesManualNotesCandidatesExplanationAndReveal() {
        send("TOGGLE_NOTE", 0, 3);
        AttemptSnapshot help = send("CANDIDATES", 0, null);
        assertArrayEquals(new int[]{1, 2}, help.progress().candidates());
        assertEquals(4, help.progress().notes()[0]);
        assertEquals(1, help.progress().candidateRequests());
        assertTrue(send("EXPLAIN", null, null).progress().helpMessage().contains("No single"));
        AttemptSnapshot revealed = send("REVEAL", 0, null);
        assertEquals(1, revealed.values()[0]);
        assertEquals(1, revealed.progress().reveals());
        assertTrue(send("EXPLAIN", null, null).progress().helpMessage().contains("Cell 2"));
    }
}
