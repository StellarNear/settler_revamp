package com.apogames.tiwanaku.server;

import org.flywaydb.core.Flyway;
import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import java.util.zip.ZipFile;

final class StoreBackups {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(StoreBackups.class);
    static void migrate(Flyway flyway, Path directory) {
        Path marker = directory.resolve("upgrade-incomplete.txt");
        if (Files.exists(marker)) throw new IllegalStateException(
                "A previous schema upgrade did not finish. Preserve this directory and restore its pre-upgrade ZIP into a separate directory. See " + marker);
        var information = flyway.info();
        var resolved = java.util.Arrays.stream(information.all()).filter(info -> info.getState().isResolved() && info.getVersion() != null).toList();
        var maximum = resolved.stream().map(info -> info.getVersion()).max(Comparator.naturalOrder()).orElseThrow();
        if (java.util.Arrays.stream(information.applied()).anyMatch(info -> info.getVersion() != null && info.getVersion().compareTo(maximum) > 0))
            throw new IllegalStateException("This data directory has a newer schema. Use a matching newer JAR; no downgrade/reset was attempted.");
        Flyway.configure().configuration(flyway.getConfiguration()).ignoreMigrationPatterns("*:pending").load().validate();
        if (information.pending().length == 0) return;
        boolean existing = information.applied().length > 0;
        Path backup = null;
        try {
            if (existing) {
                String from = information.current().getVersion().toString();
                backup = create(flyway.getConfiguration().getDataSource(), directory, "auto-v" + from + "-to-v" + maximum);
                LOGGER.info("Pre-upgrade backup verified fromVersion={} toVersion={} path={}", from, maximum, backup);
                Files.writeString(marker, "Upgrade started " + Instant.now() + System.lineSeparator()
                        + "Pre-upgrade backup: " + backup + System.lineSeparator()
                        + "If this marker remains, do not delete data or repair automatically. Restore into a separate directory.",
                        StandardOpenOption.CREATE_NEW);
            }
            flyway.migrate();
            if (existing) {
                Files.delete(marker);
                rotate(directory.resolve("backups"));
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Schema upgrade stopped. Preserve the directory and backup: " + backup, exception);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Schema upgrade failed; H2 DDL may be partially applied. Preserve the directory and restore into a separate directory from: " + backup, exception);
        }
    }

    static Path create(DataSource source, Path directory, String prefix) {
        try {
            Path backups = directory.resolve("backups");
            Files.createDirectories(backups);
            Path backup = backups.resolve(prefix + "-" + Instant.now().toEpochMilli() + "-" + UUID.randomUUID() + ".zip");
            try (var connection = source.getConnection(); var statement = connection.prepareStatement("BACKUP TO ?")) {
                statement.setString(1, backup.toString());
                statement.execute();
            }
            try (var archive = new ZipFile(backup.toFile())) {
                var entry = archive.getEntry("tiwanaku.mv.db");
                if (entry == null || entry.getSize() == 0) throw new IOException("H2 backup contains no database");
                try (var input = archive.getInputStream(entry)) { input.transferTo(java.io.OutputStream.nullOutputStream()); }
            }
            LOGGER.info("Consistent H2 backup created path={}", backup);
            return backup;
        } catch (IOException exception) {
            throw new UncheckedIOException("Backup failed; no upgrade may proceed", exception);
        } catch (SQLException exception) {
            throw new org.springframework.jdbc.UncategorizedSQLException(
                    "H2 backup failed; check free space and directory permissions", "BACKUP TO", exception);
        }
    }

    private static void rotate(Path backups) throws IOException {
        try (var files = Files.list(backups)) {
            var snapshots = files.filter(path -> path.getFileName().toString().startsWith("auto-") && path.toString().endsWith(".zip"))
                    .toList();
            var byAge = new java.util.ArrayList<>(snapshots);
            byAge.sort(Comparator.comparingLong(path -> {
                try { return Files.getLastModifiedTime(path).toMillis(); }
                catch (IOException exception) { throw new UncheckedIOException(exception); }
            }));
            for (int i = 0; i < byAge.size() - 5; i++) Files.delete(byAge.get(i));
        }
    }
}
