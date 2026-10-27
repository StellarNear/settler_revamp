package com.apogames.tiwanaku.server;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.*;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

class StoreBackupsTest {
    @TempDir Path temporary;
    private DriverManagerDataSource source(Path directory) {
        return new DriverManagerDataSource("jdbc:h2:file:" + directory.resolve("tiwanaku") + ";WRITE_DELAY=0", "sa", "");
    }
    @Test void preUpgradeBackupActuallyRestoresV1AndRotationKeepsFiveAutomaticSnapshots() throws Exception {
        var source = source(temporary);
        Flyway.configure().dataSource(source).target("1").load().migrate();
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("INSERT INTO puzzles(puzzle_id,columns_count,rows_count,region_ids,givens,solution) VALUES ('preserved',2,1,'[0,0]','[1,0]','[1,2]')");
            statement.execute("""
                    INSERT INTO attempts(attempt_id,puzzle_id,values_snapshot,status,editor_session_id,move_count)
                    VALUES ('old-attempt','preserved','[1,2]','COMPLETED','legacy-editor',2)
                    """);
            statement.execute("""
                    INSERT INTO results(result_id,attempt_id,values_snapshot,move_count,player_tag)
                    VALUES ('old-result','old-attempt','[1,2]',2,'<old>')
                    """);
        }
        for (int i = 0; i < 6; i++) {
            Path file = StoreBackups.create(source, temporary, "auto-fixture");
            Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(i * 1000L));
        }
        Path manual = StoreBackups.create(source, temporary, "manual");
        StoreBackups.migrate(Flyway.configure().dataSource(source).load(), temporary);
        Path backup;
        try (var files = Files.list(temporary.resolve("backups"))) {
            backup = files.filter(path -> path.getFileName().toString().startsWith("auto-v1-to-v3")).findFirst().orElseThrow();
        }
        try (var files = Files.list(temporary.resolve("backups"))) {
            assertEquals(5, files.filter(path -> path.getFileName().toString().startsWith("auto-")).count());
        }
        assertTrue(Files.exists(manual));
        assertFalse(Files.exists(temporary.resolve("upgrade-incomplete.txt")));
        Path restored = temporary.resolve("restored");
        Files.createDirectory(restored);
        try (var zip = new ZipFile(backup.toFile()); var input = zip.getInputStream(zip.getEntry("tiwanaku.mv.db"))) {
            Files.copy(input, restored.resolve("tiwanaku.mv.db"));
        }
        var old = source(restored);
        assertEquals("1", Flyway.configure().dataSource(old).load().info().current().getVersion().toString());
        try (var connection = old.getConnection(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT COUNT(*) FROM puzzles WHERE puzzle_id='preserved'")) {
            assertTrue(rows.next()); assertEquals(1, rows.getInt(1));
        }
        StoreBackups.migrate(Flyway.configure().dataSource(old).load(), restored);
        assertEquals("3", Flyway.configure().dataSource(old).load().info().current().getVersion().toString());
        try (var connection = old.getConnection(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT * FROM history_entries WHERE record_id='old-result'")) {
            assertTrue(rows.next());
            assertEquals("unrecorded-v0", rows.getString("scoring_version"));
            assertNull(rows.getObject("elapsed_millis"));
            assertNull(rows.getString("progress_json"));
            assertNull(rows.getString("puzzle_settings_json"));
            assertEquals("<old>", rows.getString("player_tag"));
            assertEquals(2, rows.getInt("value_entries"));
        }
    }

    @Test void failedBackupStopsUpgradeAndFutureSchemaIsNeverDowngraded() throws Exception {
        var source = source(temporary);
        Flyway.configure().dataSource(source).target("1").load().migrate();
        Files.writeString(temporary.resolve("backups"), "not a directory");
        assertThrows(RuntimeException.class, () -> StoreBackups.migrate(Flyway.configure().dataSource(source).load(), temporary));
        assertEquals("1", Flyway.configure().dataSource(source).load().info().current().getVersion().toString());
        assertFalse(Files.exists(temporary.resolve("upgrade-incomplete.txt")));
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO "flyway_schema_history"("installed_rank","version","description","type","script","installed_by","execution_time","success")
                    VALUES (99,'99','future','SQL','V99__future.sql','SA',0,TRUE)
                    """);
        }
        assertThrows(IllegalStateException.class, () -> StoreBackups.migrate(Flyway.configure().dataSource(source).load(), temporary));
        assertEquals("99", Flyway.configure().dataSource(source).load().info().current().getVersion().toString());
    }

    @Test void partialH2DdlFailureLeavesBackupAndBlocksAutomaticRepair() throws Exception {
        var source = source(temporary);
        Flyway.configure().dataSource(source).target("1").load().migrate();
        Path migrations = temporary.resolve("bad-migrations");
        Files.createDirectory(migrations);
        Files.writeString(migrations.resolve("V2__failing.sql"), "CREATE TABLE partial_marker(id INT); INSERT INTO missing_table VALUES(1);");
        var broken = Flyway.configure().dataSource(source).locations("classpath:db/migration", "filesystem:" + migrations)
                .ignoreMigrationPatterns("*:missing").load();
        // Use a unique later migration so the released V2/V3 are not replaced.
        Files.move(migrations.resolve("V2__failing.sql"), migrations.resolve("V4__failing.sql"));
        assertThrows(IllegalStateException.class, () -> StoreBackups.migrate(broken, temporary));
        assertTrue(Files.exists(temporary.resolve("upgrade-incomplete.txt")));
        try (var connection = source.getConnection(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT COUNT(*) FROM partial_marker")) {
            assertTrue(rows.next()); assertEquals(0, rows.getInt(1));
        }
        assertThrows(IllegalStateException.class, () -> StoreBackups.migrate(Flyway.configure().dataSource(source).load(), temporary));
        try (var files = Files.list(temporary.resolve("backups"))) { assertEquals(1, files.count()); }
    }

    @Test void genuinelyReadOnlyH2RejectsWritesWithoutResettingRows() throws Exception {
        var writable = source(temporary);
        Flyway.configure().dataSource(writable).load().migrate();
        var readonly = new DriverManagerDataSource("jdbc:h2:file:" + temporary.resolve("tiwanaku") + ";ACCESS_MODE_DATA=r;IFEXISTS=TRUE", "sa", "");
        try (var connection = readonly.getConnection(); var statement = connection.createStatement()) {
            assertThrows(java.sql.SQLException.class, () -> statement.execute("UPDATE local_settings SET revision=99"));
            try (var rows = statement.executeQuery("SELECT revision FROM local_settings")) {
                assertTrue(rows.next()); assertEquals(0, rows.getLong(1));
            }
        }
    }
}
