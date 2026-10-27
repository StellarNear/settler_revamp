package com.apogames.tiwanaku.server;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class LegacyStoreMigrationTest {
    @TempDir
    Path temporary;

    @Test
    void preservesRowsAndNotesBacksUpAndAllowsNormalStartup() throws Exception {
        try (DataDirectoryLock directory = DataDirectoryLock.acquire(temporary)) {
            String schema;
            try (var input = getClass().getResourceAsStream("/db/migration/V1__initial_local_store.sql")) {
                assertNotNull(input);
                schema = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            try (var connection = DriverManager.getConnection(directory.jdbcUrl(), "", "");
                 var statement = connection.createStatement()) {
                statement.execute(schema);
                statement.execute("ALTER TABLE attempts ADD note_masks CLOB NOT NULL");
                statement.execute("""
                        INSERT INTO puzzles(puzzle_id,columns_count,rows_count,region_ids,givens,solution)
                        VALUES ('p',2,1,'[0,0]','[1,0]','[1,2]')
                        """);
                statement.execute("""
                        INSERT INTO attempts(attempt_id,puzzle_id,values_snapshot,status,editor_session_id,note_masks)
                        VALUES ('a','p','[1,0]','READY','editor-session-for-test','[0,4]')
                        """);
            }

            Path backup = LegacyStoreMigration.migrate(directory);
            try (ZipFile zip = new ZipFile(backup.toFile())) {
                assertNotNull(zip.getEntry("tiwanaku.mv.db"));
                Path restored = temporary.resolve("restored");
                Files.createDirectories(restored);
                try (var input = zip.getInputStream(zip.getEntry("tiwanaku.mv.db"))) {
                    Files.copy(input, restored.resolve("tiwanaku.mv.db"));
                }
                try (var connection = DriverManager.getConnection(
                        "jdbc:h2:file:" + restored.resolve("tiwanaku") + ";IFEXISTS=TRUE", "", "");
                     var statement = connection.createStatement();
                     var result = statement.executeQuery("SELECT note_masks FROM attempts WHERE attempt_id='a'")) {
                    assertTrue(result.next());
                    assertEquals("[0,4]", result.getString(1));
                }
            }
            Flyway.configure().dataSource(directory.jdbcUrl(), "sa", "").load().migrate();
            try (var connection = DriverManager.getConnection(directory.jdbcUrl(), "sa", "");
                 var statement = connection.createStatement()) {
                try (var result = statement.executeQuery("SELECT note_masks FROM attempts WHERE attempt_id='a'")) {
                    assertTrue(result.next());
                    assertEquals("[0,4]", result.getString(1));
                }
                statement.execute("""
                        INSERT INTO attempts(attempt_id,puzzle_id,values_snapshot,status,editor_session_id)
                        VALUES ('new','p','[1,0]','READY','another-editor-session')
                        """);
                try (var result = statement.executeQuery("SELECT COUNT(*) FROM attempts")) {
                    assertTrue(result.next());
                    assertEquals(2, result.getInt(1));
                }
            }
        }
    }

    @Test
    void refusesUnknownSchemaWithoutChangingIt() throws Exception {
        try (DataDirectoryLock directory = DataDirectoryLock.acquire(temporary)) {
            try (var connection = DriverManager.getConnection(directory.jdbcUrl(), "", "");
                 var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE unrelated (id INT)");
            }
            assertThrows(IllegalStateException.class, () -> LegacyStoreMigration.migrate(directory));
            assertFalse(Files.exists(temporary.resolve("backups")));
            try (var connection = DriverManager.getConnection(directory.jdbcUrl(), "", "");
                 var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT COUNT(*) FROM unrelated")) {
                assertTrue(result.next());
                assertEquals(0, result.getInt(1));
            }
        }
    }

    @Test
    void refusesMissingDatabaseWithoutCreatingOne() throws Exception {
        try (DataDirectoryLock directory = DataDirectoryLock.acquire(temporary)) {
            assertThrows(IllegalStateException.class, () -> LegacyStoreMigration.migrate(directory));
            assertFalse(Files.exists(temporary.resolve("tiwanaku.mv.db")));
        }
    }
}
