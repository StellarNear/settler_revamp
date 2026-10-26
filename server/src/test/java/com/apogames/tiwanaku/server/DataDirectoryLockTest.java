package com.apogames.tiwanaku.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataDirectoryLockTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void locksCanonicalDirectoryAndAllowsReacquisitionAfterRelease() throws Exception {
        Path requestedDirectory = temporaryDirectory.resolve("nested").resolve("data");

        DataDirectoryLock first = DataDirectoryLock.acquire(requestedDirectory);
        try {
            assertEquals(requestedDirectory.toRealPath(), first.directory());
            assertTrue(first.jdbcUrl().startsWith("jdbc:h2:file:"));
            assertTrue(first.jdbcUrl().contains(";AUTO_SERVER=FALSE;WRITE_DELAY=0"));
            assertThrows(IllegalStateException.class, () -> DataDirectoryLock.acquire(requestedDirectory));
        } finally {
            first.close();
        }

        try (DataDirectoryLock second = DataDirectoryLock.acquire(requestedDirectory)) {
            assertEquals(first.directory(), second.directory());
        }
    }

    @Test
    void rejectsRelativeDataDirectories() {
        assertThrows(IllegalArgumentException.class, () -> DataDirectoryLock.acquire(Path.of("relative")));
    }
}
