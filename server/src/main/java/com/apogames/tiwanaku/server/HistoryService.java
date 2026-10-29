package com.apogames.tiwanaku.server;

import jakarta.validation.Validator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public final class HistoryService {
    public static final int MAX_SOURCE = 4_000_000;
    public record SettingsSnapshot(long revision, LocalSettings settings) { }
    public record Summary(int count, int knownElapsedCount, long elapsedSum, int legacyAccuracyCount,
                          Double legacyAccuracyMean) { }
    public record History(List<HistoryEntry> entries, Summary summary) { }
    public record Archive(int schemaVersion, String checksum, String payload) { }
    public record Payload(LocalSettings settings, List<HistoryEntry> entries) { }
    public record ImportPreview(String fingerprint, List<HistoryEntry> entries, List<String> rejected, boolean alreadyImported) { }
    public record ImportOutcome(String fingerprint, int imported, List<String> rejected, boolean alreadyImported) { }
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final StoreJson json;
    private final Validator validator;

    public HistoryService(JdbcTemplate jdbc, TransactionTemplate transactions, StoreJson json, Validator validator) {
        this.jdbc = jdbc; this.transactions = transactions; this.json = json; this.validator = validator;
    }

    public SettingsSnapshot settings() {
        return jdbc.queryForObject("SELECT revision,settings_json FROM local_settings WHERE singleton_id=1",
                (rs, row) -> new SettingsSnapshot(rs.getLong(1), json.read(rs.getString(2), LocalSettings.class)));
    }

    public SettingsSnapshot saveSettings(long revision, LocalSettings settings) {
        validateSettings(settings);
        if (jdbc.update("UPDATE local_settings SET revision=revision+1,settings_json=? WHERE singleton_id=1 AND revision=?",
                json.write(settings.normalized()), revision) != 1)
            throw ApiException.conflict("Settings changed in another tab. Reload settings before saving.");
        return settings();
    }

    public History history(String player, String mapSize, String scoring, String assistance, String sort) {
        return history(player, mapSize, scoring, assistance, sort, "");
    }

    public History history(String player, String mapSize, String scoring, String assistance, String sort, String shape) {
        if (!List.of("date-desc", "date-asc", "time-asc", "time-desc").contains(sort))
            throw ApiException.badRequest("Unsupported history sort");
        if (!List.of("", "assisted", "unassisted", "unknown", "replay").contains(assistance))
            throw ApiException.badRequest("Unsupported assistance filter");
        List<HistoryEntry> entries = all().stream()
                .filter(entry -> player.isEmpty() || player.equals(entry.playerTag()))
                .filter(entry -> mapSize.isEmpty() || mapSize.equals(entry.mapSize()))
                .filter(entry -> scoring.isEmpty() || scoring.equals(entry.scoringVersion()))
                .filter(entry -> assistance.isEmpty() || matchesAssistance(entry, assistance))
                .filter(entry -> shape.isEmpty() || shape.equals(shapeOf(entry)))
                .sorted(comparator(sort)).toList();
        long sum = 0, percent = 0;
        int known = 0, legacy = 0;
        for (HistoryEntry entry : entries) {
            if (entry.elapsedMillis() != null) {
                if (entry.elapsedMillis() > 9_007_199_254_740_991L - sum)
                    throw ApiException.conflict("Filtered elapsed sum exceeds the safe numeric range; narrow the filters");
                sum += entry.elapsedMillis(); known++;
            }
            if ("legacy-clicks-v1".equals(entry.scoringVersion()) && entry.legacyAccuracy() != null) {
                percent += entry.legacyAccuracy(); legacy++;
            }
        }
        return new History(entries, new Summary(entries.size(), known, sum, legacy, legacy == 0 ? null : (double) percent / legacy));
    }

    private boolean matchesAssistance(HistoryEntry entry, String filter) {
        var help = entry.assistance();
        if (filter.equals("unknown")) return help == null;
        if (help == null) return false;
        boolean used = help.candidates() > 0 || help.explanations() > 0 || help.reveals() > 0 || help.continuousConflicts();
        return switch (filter) {
            case "assisted" -> used;
            case "unassisted" -> !used;
            case "replay" -> help.replay();
            default -> false;
        };
    }

    private Comparator<HistoryEntry> comparator(String sort) {
        Comparator<Long> order = sort.endsWith("desc") ? Comparator.reverseOrder() : Comparator.naturalOrder();
        return Comparator.comparing((HistoryEntry entry) -> sort.startsWith("time") ? entry.elapsedMillis() : entry.completedMillis(),
                Comparator.nullsLast(order)).thenComparing(HistoryEntry::recordId);
    }

    private List<HistoryEntry> all() {
        List<HistoryEntry> entries = jdbc.query("SELECT * FROM history_entries WHERE deleted=FALSE ORDER BY record_id FETCH FIRST 10001 ROWS ONLY",
                (rs, row) -> {
                    GameProgress progress = rs.getString("progress_json") == null ? null : json.read(rs.getString("progress_json"), GameProgress.class);
                    return new HistoryEntry(rs.getString("record_id"),
                            rs.getString("source_attempt_id") == null ? rs.getString("attempt_id") : rs.getString("source_attempt_id"), rs.getString("map_size"),
                            rs.getObject("completed_millis", Long.class),
                            progress == null ? rs.getObject("elapsed_millis", Long.class) : progress.elapsedMillis,
                            rs.getString("player_tag"), rs.getString("scoring_version"), rs.getObject("value_entries", Integer.class),
                            rs.getObject("legacy_accuracy", Integer.class), rs.getObject("total_clicks", Long.class),
                            rs.getObject("total_miss_clicks", Long.class),
                            progress == null ? null : new HistoryEntry.Assistance(progress.wrongEntryCount, progress.candidateRequests,
                                    progress.explanationRequests, progress.reveals, progress.continuousConflictsUsed, progress.replay),
                            rs.getString("puzzle_settings_json") == null ? null : json.read(rs.getString("puzzle_settings_json"), HistoryEntry.PuzzleSettings.class));
                });
        if (entries.size() > 10_000) throw ApiException.conflict("History exceeds the 10000-record management/export limit; preserve a physical backup.");
        return entries;
    }

    public void rename(UUID id, String tag) {
        if (tag == null || !tag.matches("[A-Za-z]{0,3}")) throw ApiException.badRequest("Player tag must be empty or 1-3 ASCII letters");
        String normalized = tag.toUpperCase(java.util.Locale.ROOT);
        transactions.executeWithoutResult(status -> {
            if (jdbc.update("UPDATE history_entries SET player_tag=? WHERE record_id=? AND deleted=FALSE", normalized, id.toString()) != 1)
                throw ApiException.notFound("History record not found");
            jdbc.update("UPDATE results SET player_tag=? WHERE result_id=?", normalized, id.toString());
        });
    }

    public void delete(UUID id) {
        if (jdbc.update("UPDATE history_entries SET deleted=TRUE WHERE record_id=?", id.toString()) != 1)
            throw ApiException.notFound("History record not found");
    }

    public Archive exportArchive() {
        String payload = json.write(new Payload(settings().settings(), all()));
        Archive archive = new Archive(1, StoreJson.checksum(payload), payload);
        if (json.write(archive).getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_SOURCE)
            throw ApiException.conflict("Logical export exceeds size limit; use a physical backup");
        return archive;
    }

    public ImportPreview preview(String source, String format) {
        if (source == null || source.length() > MAX_SOURCE) throw ApiException.badRequest("Import source exceeds 4 million characters");
        String fingerprint = StoreJson.checksum(format + ":" + source);
        List<HistoryEntry> entries;
        List<String> rejected;
        if ("legacy".equals(format)) {
            var parsed = LegacyScores.parse(source);
            entries = parsed.entries(); rejected = parsed.rejected();
        } else if ("logical".equals(format)) {
            var payload = archivePayload(source);
            entries = payload.entries(); rejected = List.of();
        } else throw ApiException.badRequest("Choose legacy or logical import");
        boolean imported = !jdbc.queryForList("SELECT fingerprint FROM history_imports WHERE fingerprint=?", fingerprint).isEmpty();
        return new ImportPreview(fingerprint, entries, rejected, imported);
    }

    public ImportOutcome importSource(String source, String format, String expectedFingerprint, boolean restoreSettings) {
        var preview = preview(source, format);
        if (!preview.fingerprint().equals(expectedFingerprint)) throw ApiException.conflict("Import source changed after preview");
        return transactions.execute(status -> {
            // Serialize imports/settings so concurrent retries cannot both install the same archive.
            jdbc.queryForMap("SELECT singleton_id FROM local_settings WHERE singleton_id=1 FOR UPDATE");
            var prior = jdbc.queryForList("SELECT restore_settings FROM history_imports WHERE fingerprint=?", preview.fingerprint());
            if (!prior.isEmpty()) {
                if (!Boolean.valueOf(restoreSettings).equals(prior.getFirst().get("RESTORE_SETTINGS")))
                    throw ApiException.conflict("This source was already imported with a different settings-restore choice. Reload saved settings; history was not duplicated.");
                return new ImportOutcome(preview.fingerprint(), 0, preview.rejected(), true);
            }
            int imported = 0;
            for (HistoryEntry entry : preview.entries()) {
                if (!jdbc.queryForList("SELECT record_id FROM history_entries WHERE record_id=?", entry.recordId()).isEmpty()) continue;
                GameProgress progress = null;
                if (entry.assistance() != null) {
                    var help = entry.assistance();
                    progress = GameProgress.initial(0, help.replay());
                    progress.elapsedMillis = entry.elapsedMillis();
                    progress.wrongEntryCount = help.wrongEntries() == null ? 0 : help.wrongEntries();
                    progress.candidateRequests = help.candidates(); progress.explanationRequests = help.explanations();
                    progress.reveals = help.reveals(); progress.continuousConflictsUsed = help.continuousConflicts();
                }
                jdbc.update("""
                        INSERT INTO history_entries(record_id,source_attempt_id,map_size,completed_millis,elapsed_millis,player_tag,
                        scoring_version,value_entries,legacy_accuracy,total_clicks,total_miss_clicks,progress_json,puzzle_settings_json)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                        """, entry.recordId(), entry.attemptId(), entry.mapSize(), entry.completedMillis(), entry.elapsedMillis(), entry.playerTag(),
                        entry.scoringVersion(), entry.valueEntries(), entry.legacyAccuracy(), entry.totalClicks(), entry.totalMissClicks(),
                        progress == null ? null : json.write(progress),
                        entry.puzzleSettings() == null ? null : json.write(entry.puzzleSettings()));
                imported++;
            }
            if (restoreSettings) {
                if (!format.equals("logical")) throw ApiException.badRequest("Only a logical archive contains settings");
                jdbc.update("UPDATE local_settings SET revision=revision+1,settings_json=? WHERE singleton_id=1",
                        json.write(archivePayload(source).settings().normalized()));
            }
            jdbc.update("INSERT INTO history_imports(fingerprint,raw_source,imported_count,rejected_json,restore_settings) VALUES (?,?,?,?,?)",
                    preview.fingerprint(), source, imported, json.write(preview.rejected()), restoreSettings);
            return new ImportOutcome(preview.fingerprint(), imported, preview.rejected(), false);
        });
    }

    public String rawImport(String fingerprint) {
        var rows = jdbc.queryForList("SELECT raw_source FROM history_imports WHERE fingerprint=?", fingerprint);
        if (rows.isEmpty()) throw ApiException.notFound("Import source not found");
        return StoreJson.text(rows.getFirst().get("RAW_SOURCE"));
    }

    private Payload archivePayload(String source) {
        Archive archive = json.input(source, Archive.class);
        if (archive.schemaVersion() != 1 || archive.payload() == null || archive.checksum() == null
                || !archive.checksum().equals(StoreJson.checksum(archive.payload())))
            throw ApiException.badRequest("Unsupported archive version or checksum mismatch");
        Payload payload = json.input(archive.payload(), Payload.class);
        if (payload.entries() == null || payload.entries().size() > 10_000) throw ApiException.badRequest("Invalid archive entry count");
        validateSettings(payload.settings());
        var ids = new java.util.HashSet<String>();
        for (HistoryEntry entry : payload.entries()) {
            if (entry == null || entry.recordId() == null || !ids.add(entry.recordId())) throw ApiException.badRequest("Duplicate or missing archive ID");
            try {
                UUID.fromString(entry.recordId());
                if (entry.attemptId() != null) UUID.fromString(entry.attemptId());
            }
            catch (IllegalArgumentException exception) { throw ApiException.badRequest("Invalid archive record ID"); }
            if (entry.mapSize() == null || entry.mapSize().isBlank() || entry.mapSize().length() > 64
                    || entry.playerTag() != null && entry.playerTag().length() > 128
                    || entry.scoringVersion() == null || !List.of("direct-entry-v1", "legacy-clicks-v1", "unrecorded-v0").contains(entry.scoringVersion())
                    || !safe(entry.elapsedMillis()) || !safe(entry.completedMillis())
                    || !safe(entry.totalClicks()) || !safe(entry.totalMissClicks())
                    || entry.valueEntries() != null && entry.valueEntries() < 0
                    || entry.legacyAccuracy() != null && (entry.legacyAccuracy() < 0 || entry.legacyAccuracy() > 100))
                throw ApiException.badRequest("Invalid archive record values");
            if (entry.totalMissClicks() != null && (entry.totalClicks() == null || entry.totalMissClicks() > entry.totalClicks()))
                throw ApiException.badRequest("Invalid legacy click counters");
            if ("legacy-clicks-v1".equals(entry.scoringVersion())
                    && (entry.legacyAccuracy() == null || entry.assistance() != null || entry.valueEntries() != null || entry.puzzleSettings() != null))
                throw ApiException.badRequest("Legacy scoring fields are inconsistent");
            if ("direct-entry-v1".equals(entry.scoringVersion())
                    && (entry.assistance() == null || entry.elapsedMillis() == null || entry.valueEntries() == null))
                throw ApiException.badRequest("Direct-entry results require recorded time, entries and assistance");
            if ("unrecorded-v0".equals(entry.scoringVersion()) && entry.assistance() != null)
                throw ApiException.badRequest("Unrecorded results cannot claim direct-entry assistance");
            if (!"legacy-clicks-v1".equals(entry.scoringVersion())
                    && (entry.legacyAccuracy() != null || entry.totalClicks() != null || entry.totalMissClicks() != null))
                throw ApiException.badRequest("Direct-entry results cannot contain legacy percentages");
            if (entry.assistance() != null && (entry.elapsedMillis() == null
                    || entry.assistance().wrongEntries() == null || entry.assistance().wrongEntries() < 0
                    || entry.valueEntries() == null || entry.assistance().wrongEntries() > entry.valueEntries()
                    || entry.assistance().candidates() == null || entry.assistance().explanations() == null
                    || entry.assistance().reveals() == null || entry.assistance().continuousConflicts() == null
                    || entry.assistance().replay() == null
                    || entry.assistance().candidates() < 0 || entry.assistance().explanations() < 0 || entry.assistance().reveals() < 0))
                throw ApiException.badRequest("Invalid assistance metadata");
            var puzzle = entry.puzzleSettings();
            if (puzzle != null && (puzzle.columns() < 1 || puzzle.rows() < 1 || (long) puzzle.columns() * puzzle.rows() > 240
                    || puzzle.maxRegionSize() < 5 || puzzle.maxRegionSize() > 9
                    || puzzle.difficulty() == null
                    || !List.of("EASY","MEDIUM","HARD","EXTREME","GODLIKE").contains(puzzle.difficulty())
                    || puzzle.seed() == null || !puzzle.seed().matches("-?[0-9]{1,19}")
                    || puzzle.engineVersion() == null || puzzle.engineVersion().length() > 64
                    || puzzle.generatorVersion() == null || puzzle.generatorVersion().length() > 64
                    || puzzle.shape() != null
                    && !List.of("SQUARE","SQUARE_SIDES_ONLY","HEXAGON","TRIANGLE","PENTAGON","PENTAGON_CORNERS")
                            .contains(puzzle.shape())))
                throw ApiException.badRequest("Invalid puzzle settings metadata");
        }
        return payload;
    }

    /** Records without a recorded shape were played on the original square grid. */
    static String shapeOf(HistoryEntry entry) {
        return entry.puzzleSettings() == null || entry.puzzleSettings().shape() == null
                ? "SQUARE" : entry.puzzleSettings().shape();
    }

    private boolean safe(Long value) { return value == null || value >= 0 && value <= 9_007_199_254_740_991L; }
    private void validateSettings(LocalSettings settings) {
        if (settings == null || !validator.validate(settings).isEmpty()) throw ApiException.badRequest("Invalid settings");
    }
}
