package com.apogames.tiwanaku.server;

import org.flywaydb.core.Flyway;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class LegacyStoreMigration {
    private static final Map<String, Set<String>> LEGACY_COLUMNS = Map.of(
            "PUZZLES", Set.of("PUZZLE_ID", "COLUMNS_COUNT", "ROWS_COUNT", "REGION_IDS",
                    "GIVENS", "SOLUTION", "CREATED_AT"),
            "ATTEMPTS", Set.of("ATTEMPT_ID", "PUZZLE_ID", "VALUES_SNAPSHOT", "NOTE_MASKS",
                    "STATUS", "REVISION", "EDITOR_SESSION_ID", "EDITOR_LEASE_EXPIRES_AT",
                    "MOVE_COUNT", "UPDATED_AT"),
            "CREATION_RECEIPTS", Set.of("REQUEST_ID", "PAYLOAD_HASH", "ATTEMPT_ID", "CREATED_AT"),
            "COMMAND_RECEIPTS", Set.of("ATTEMPT_ID", "COMMAND_ID", "PAYLOAD_HASH",
                    "RESPONSE_JSON", "REVISION", "CREATED_AT"),
            "RESULTS", Set.of("RESULT_ID", "ATTEMPT_ID", "VALUES_SNAPSHOT", "MOVE_COUNT",
                    "COMPLETED_AT", "PLAYER_TAG"));

    private LegacyStoreMigration() {
    }

    static Path migrate(DataDirectoryLock directory) throws SQLException, IOException {
        if (!Files.isRegularFile(directory.directory().resolve("tiwanaku.mv.db"))) {
            throw new IllegalStateException("No existing Tiwanaku database to migrate");
        }
        String url = directory.jdbcUrl() + ";IFEXISTS=TRUE";
        try (Connection connection = DriverManager.getConnection(url, "", "")) {
            requireLegacySchema(connection);
            Path backups = directory.directory().resolve("backups");
            Files.createDirectories(backups);
            Path backup = backups.resolve("pre-credentials-migration-" + UUID.randomUUID() + ".zip");
            try (var statement = connection.prepareStatement("BACKUP TO ?")) {
                statement.setString(1, backup.toString());
                statement.execute();
            }
            if (!Files.isRegularFile(backup) || Files.size(backup) == 0) {
                throw new IOException("Migration stopped: H2 backup is missing or empty");
            }
            System.out.println("Pre-migration backup: " + backup);

            // Keep historical notes intact; new attempts do not yet write this legacy column.
            try (var statement = connection.createStatement()) {
                statement.execute("ALTER TABLE attempts ALTER COLUMN note_masks SET DEFAULT '[]'");
            }
            Flyway.configure().dataSource(url, "", "").baselineVersion("1").load().baseline();
            try (var statement = connection.createStatement()) {
                statement.execute("ALTER USER \"\" RENAME TO SA");
                statement.execute("ALTER USER SA SET PASSWORD ''");
            }
            return backup;
        }
    }

    private static void requireLegacySchema(Connection connection) throws SQLException {
        Map<String, Set<String>> columns = new HashMap<>();
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT TABLE_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS
                     WHERE TABLE_SCHEMA = 'PUBLIC'
                     """)) {
            while (result.next()) {
                columns.computeIfAbsent(result.getString(1), ignored -> new HashSet<>()).add(result.getString(2));
            }
        }
        if (!LEGACY_COLUMNS.equals(columns)) {
            throw new IllegalStateException(
                    "Unrecognized database schema; migration refused. No schema changes were made.");
        }
    }
}
