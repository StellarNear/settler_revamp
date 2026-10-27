package com.apogames.tiwanaku.server;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class HistoryServiceTest {
    @Autowired HistoryService history;
    @Autowired JdbcTemplate jdbc;
    @Autowired StoreJson json;
    private LocalSettings original;

    @BeforeEach void reset() {
        jdbc.update("DELETE FROM history_imports");
        jdbc.update("DELETE FROM history_entries");
        original = history.settings().settings();
    }
    @AfterEach void restore() { history.saveSettings(history.settings().revision(), original); }

    @Test void importsHistoricalFormsKeepsGoodRowsUnknownsRawAndDuplicateOccurrences() {
        String raw = "SMALL|1000|75;SMALL|1000|75;MINI|2000|90|1793110000000;BIG|3000|60|0|<old>;"
                + "TITAN|4000|50|1793110000000|ABC|10|5;BAD;BIG|nan|10;BIG|4|101;BIG|4|10|1|ABC|1|2";
        var preview = history.preview(raw, "legacy");
        assertEquals(5, preview.entries().size());
        assertEquals(4, preview.rejected().size());
        assertNull(preview.entries().getFirst().completedMillis());
        assertNull(preview.entries().getFirst().totalClicks());
        assertNotEquals(preview.entries().get(0).recordId(), preview.entries().get(1).recordId());
        assertNull(preview.entries().get(3).completedMillis());
        assertEquals("<old>", preview.entries().get(3).playerTag());
        assertEquals(5, history.importSource(raw, "legacy", preview.fingerprint(), false).imported());
        assertTrue(history.importSource(raw, "legacy", preview.fingerprint(), false).alreadyImported());
        assertEquals(raw, history.rawImport(preview.fingerprint()));
        var records = history.history("", "", "", "", "date-desc");
        assertEquals(5, records.summary().count());
        assertEquals(11000, records.summary().elapsedSum());
        assertEquals(70, records.summary().legacyAccuracyMean());
        var filtered = history.history("", "SMALL", "legacy-clicks-v1", "unknown", "time-asc");
        assertEquals(2, filtered.summary().count());
        assertEquals(2000, filtered.summary().elapsedSum());
        assertEquals(75, filtered.summary().legacyAccuracyMean());
    }

    @Test void genuineLibgdxDesktopXmlAndSafeEntityHandling() throws Exception {
        String xml;
        try (var stream = getClass().getResourceAsStream("/fixtures/TiwanakuScoreboard.xml")) {
            assertNotNull(stream);
            xml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        var preview = history.preview(xml, "legacy");
        assertEquals(3, preview.entries().size());
        assertEquals("<old>", preview.entries().get(2).playerTag());
        history.importSource(xml, "legacy", preview.fingerprint(), false);
        assertEquals(xml, history.rawImport(preview.fingerprint()));
        assertThrows(ApiException.class, () -> history.preview("""
                <!DOCTYPE properties [<!ENTITY x SYSTEM "file:///unreadable-private-file">]>
                <properties><entry key="scoreboard_results">&x;</entry></properties>
                """, "legacy"));
        assertThrows(ApiException.class, () -> history.preview("<properties>", "legacy"));
    }

    @Test void exportRoundTripPreservesAllKnownAndUnknownMetadataAndDoesNotMixScoring() {
        String raw = "SMALL|1000|80;SMALL|2000|60|1793110000000|ABC|10|4";
        var preview = history.preview(raw, "legacy");
        history.importSource(raw, "legacy", preview.fingerprint(), false);
        var progress = GameProgress.initial(0, true);
        progress.elapsedMillis = 3000; progress.valueEntryCount = 4;
        progress.wrongEntryCount = 1; progress.candidateRequests = 2;
        String directId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO history_entries(record_id,source_attempt_id,map_size,completed_millis,
                scoring_version,value_entries,progress_json) VALUES (?,?,'3x3',1793110000001,'direct-entry-v1',4,?)
                """, directId, UUID.randomUUID().toString(), json.write(progress));
        jdbc.update("""
                INSERT INTO history_entries(record_id,map_size,scoring_version)
                VALUES (?,'unknown','unrecorded-v0')
                """, UUID.randomUUID().toString());
        var before = history.history("", "", "", "", "date-desc");
        assertEquals(4, before.summary().count());
        assertEquals(3, before.summary().knownElapsedCount());
        assertEquals(6000, before.summary().elapsedSum());
        assertEquals(70, before.summary().legacyAccuracyMean());
        assertEquals(2, before.summary().legacyAccuracyCount());
        var archive = history.exportArchive();
        String source = json.write(archive);
        jdbc.update("DELETE FROM history_entries");
        jdbc.update("DELETE FROM history_imports");
        var logical = history.preview(source, "logical");
        assertEquals(4, history.importSource(source, "logical", logical.fingerprint(), true).imported());
        assertEquals(before, history.history("", "", "", "", "date-desc"));
        assertEquals(1, history.history("", "", "", "replay", "date-desc").summary().count());
        history.rename(UUID.fromString(directId), "xy");
        assertEquals("XY", history.history("XY", "", "", "", "date-desc").entries().getFirst().playerTag());
        history.delete(UUID.fromString(directId));
        assertEquals(3, history.history("", "", "", "", "date-desc").summary().count());
        assertEquals(0, history.importSource(source, "logical", logical.fingerprint(), true).imported());
        assertEquals(3, history.history("", "", "", "", "date-desc").summary().count());
    }

    @Test void badChecksumFutureArchiveMalformedValuesAndStaleSettingsAreExplicitlyRejected() {
        var saved = history.settings();
        var next = new LocalSettings("de", "light", true, true, "3x3", 9, "HARD", "ab");
        assertEquals("AB", history.saveSettings(saved.revision(), next).settings().playerTag());
        assertThrows(ApiException.class, () -> history.saveSettings(saved.revision(), next));
        assertThrows(ApiException.class, () -> history.rename(UUID.randomUUID(), "1234"));
        var archive = history.exportArchive();
        assertThrows(ApiException.class, () -> history.preview(json.write(new HistoryService.Archive(2, archive.checksum(), archive.payload())), "logical"));
        assertThrows(ApiException.class, () -> history.preview(json.write(new HistoryService.Archive(1, "wrong", archive.payload())), "logical"));
        assertThrows(ApiException.class, () -> history.importSource("SMALL|1|99", "legacy", "changed", false));
        assertThrows(ApiException.class, () -> history.preview("a".repeat(HistoryService.MAX_SOURCE + 1), "legacy"));
        assertEquals(0, history.history("", "", "", "", "date-desc").summary().count());
    }

    @Test void simultaneousIdenticalImportsDoNotDuplicateRecords() throws Exception {
        String raw = "SMALL|1000|75;SMALL|1000|75";
        String fingerprint = history.preview(raw, "legacy").fingerprint();
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> history.importSource(raw, "legacy", fingerprint, false));
            var second = executor.submit(() -> history.importSource(raw, "legacy", fingerprint, false));
            assertEquals(2, first.get().imported() + second.get().imported());
        }
        assertEquals(2, history.history("", "", "", "", "date-desc").summary().count());
    }

    @Test void rejectsInconsistentScoringAndChangedRestorePolicyWithoutPartialWrites() {
        var invalid = new HistoryEntry(UUID.randomUUID().toString(), null, "SMALL", null, 1000L,
                null, null, null, null, null, null, null, null);
        String payload = json.write(new HistoryService.Payload(history.settings().settings(), java.util.List.of(invalid)));
        String archive = json.write(new HistoryService.Archive(1, StoreJson.checksum(payload), payload));
        assertThrows(ApiException.class, () -> history.preview(archive, "logical"));
        assertEquals(0, history.history("", "", "", "", "date-desc").summary().count());
        var missingAssistance = new HistoryEntry(UUID.randomUUID().toString(), null, "3x3", null, 1000L,
                "", "direct-entry-v1", 1, null, null, null,
                new HistoryEntry.Assistance(0, null, 0, 0, false, false), null);
        String incomplete = json.write(new HistoryService.Payload(history.settings().settings(), java.util.List.of(missingAssistance)));
        assertThrows(ApiException.class, () -> history.preview(
                json.write(new HistoryService.Archive(1, StoreJson.checksum(incomplete), incomplete)), "logical"));
        String valid = json.write(history.exportArchive());
        String fingerprint = history.preview(valid, "logical").fingerprint();
        history.importSource(valid, "logical", fingerprint, false);
        assertThrows(ApiException.class, () -> history.importSource(valid, "logical", fingerprint, true));
        var missingFlag = new LocalSettings("en", "light", null, false, "7x5", 5, "EASY", "");
        assertThrows(ApiException.class, () -> history.saveSettings(history.settings().revision(), missingFlag));
    }
}
