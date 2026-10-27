package com.apogames.tiwanaku.server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DataDirectoryLock implements AutoCloseable {
    private final Path directory;
    private final FileChannel channel;
    private final FileLock lock;
    private final AtomicBoolean closed = new AtomicBoolean();

    private DataDirectoryLock(Path directory, FileChannel channel, FileLock lock) {
        this.directory = directory;
        this.channel = channel;
        this.lock = lock;
    }

    public static DataDirectoryLock acquireFromEnvironment() throws IOException {
        String configuredDirectory = System.getenv("TIWANAKU_DATA_DIR");
        Path requestedDirectory;
        if (configuredDirectory == null) {
            requestedDirectory = Path.of(System.getProperty("user.home"), ".tiwanaku");
        } else {
            if (configuredDirectory.isBlank()) {
                throw new IllegalArgumentException("TIWANAKU_DATA_DIR must not be blank");
            }
            requestedDirectory = Path.of(configuredDirectory);
            if (!requestedDirectory.isAbsolute()) {
                throw new IllegalArgumentException("TIWANAKU_DATA_DIR must be an absolute path");
            }
        }

        return acquire(requestedDirectory);
    }

    static DataDirectoryLock acquire(Path requestedDirectory) throws IOException {
        if (!requestedDirectory.isAbsolute()) {
            throw new IllegalArgumentException("The data directory must be an absolute path");
        }
        Files.createDirectories(requestedDirectory);
        Path directory = requestedDirectory.toRealPath();
        FileChannel channel = FileChannel.open(
                directory.resolve("app.lock"),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
        FileLock lock;
        try {
            lock = channel.tryLock();
        } catch (OverlappingFileLockException exception) {
            IllegalStateException error = alreadyRunning(directory, exception);
            closeAfterFailure(channel, error);
            throw error;
        } catch (IOException | RuntimeException exception) {
            closeAfterFailure(channel, exception);
            throw exception;
        }
        if (lock == null) {
            IllegalStateException error = alreadyRunning(directory, null);
            closeAfterFailure(channel, error);
            throw error;
        }
        try {
            byte[] metadata = ("pid=" + ProcessHandle.current().pid()
                    + System.lineSeparator()
                    + "instance=" + UUID.randomUUID()
                    + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
            channel.truncate(0);
            channel.position(0);
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(metadata);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        } catch (IOException | RuntimeException exception) {
            try {
                lock.release();
            } catch (IOException releaseError) {
                exception.addSuppressed(releaseError);
            }
            try {
                channel.close();
            } catch (IOException closeError) {
                exception.addSuppressed(closeError);
            }
            throw exception;
        }
        return new DataDirectoryLock(directory, channel, lock);
    }

    private static IllegalStateException alreadyRunning(Path directory, Throwable cause) {
        return new IllegalStateException(
                "Another Tiwanaku server already holds the data directory lock: " + directory,
                cause);
    }

    private static void closeAfterFailure(FileChannel channel, Throwable original) {
        try {
            channel.close();
        } catch (IOException closeError) {
            original.addSuppressed(closeError);
        }
    }

    public Path directory() {
        return directory;
    }

    public String jdbcUrl() {
        String databasePath = directory.resolve("tiwanaku").toString().replace('\\', '/');
        return "jdbc:h2:file:" + databasePath + ";AUTO_SERVER=FALSE;WRITE_DELAY=0";
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        IOException failure = null;
        try {
            lock.release();
        } catch (IOException exception) {
            failure = exception;
        }
        try {
            channel.close();
        } catch (IOException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw new UncheckedIOException("Unable to release the Tiwanaku data-directory lock", failure);
        }
    }
}
