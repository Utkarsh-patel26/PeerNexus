package com.example.jtorrent.logging;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * File-based logger with rotation support.
 * 
 * Features:
 * - Automatic log rotation based on file size
 * - Configurable max file size and backup count
 * - Async writing to avoid blocking
 * - Thread-safe operation
 */
public class RotatingFileLogger {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /** Default configuration. */
    private static final long DEFAULT_MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB
    private static final int DEFAULT_MAX_BACKUP_COUNT = 5;

    private final Path logFile;
    private final long maxFileSize;
    private final int maxBackupCount;

    private BufferedWriter writer;
    private final AtomicLong currentFileSize = new AtomicLong(0);
    private final Object writeLock = new Object();

    /** Async write queue. */
    private final BlockingQueue<String> writeQueue = new LinkedBlockingQueue<>(10000);
    private final ExecutorService writerExecutor;
    private volatile boolean running = true;

    /** Statistics. */
    private final AtomicLong messagesWritten = new AtomicLong(0);
    private final AtomicLong rotationCount = new AtomicLong(0);

    /**
     * Create a rotating file logger with default settings.
     *
     * @param logFile path to the log file
     */
    public RotatingFileLogger(Path logFile) {
        this(logFile, DEFAULT_MAX_FILE_SIZE, DEFAULT_MAX_BACKUP_COUNT);
    }

    /**
     * Create a rotating file logger.
     *
     * @param logFile        the log file path
     * @param maxFileSize    maximum file size before rotation
     * @param maxBackupCount number of backup files to keep
     */
    public RotatingFileLogger(Path logFile, long maxFileSize, int maxBackupCount) {
        this.logFile = logFile;
        this.maxFileSize = maxFileSize;
        this.maxBackupCount = maxBackupCount;

        // Create parent directories
        try {
            if (logFile.getParent() != null) {
                Files.createDirectories(logFile.getParent());
            }
        } catch (IOException e) {
            System.err.println("Failed to create log directory: " + e.getMessage());
        }

        // Initialize file size
        if (Files.exists(logFile)) {
            try {
                currentFileSize.set(Files.size(logFile));
            } catch (IOException e) {
                currentFileSize.set(0);
            }
        }

        // Open writer
        openWriter();

        // Start async writer thread
        writerExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "LogWriter-" + logFile.getFileName());
            t.setDaemon(true);
            return t;
        });

        writerExecutor.submit(this::writerLoop);
    }

    /**
     * Open the log file writer.
     */
    private void openWriter() {
        synchronized (writeLock) {
            try {
                if (writer != null) {
                    writer.close();
                }
                writer = new BufferedWriter(new FileWriter(logFile.toFile(), true));
            } catch (IOException e) {
                System.err.println("Failed to open log file: " + e.getMessage());
            }
        }
    }

    /**
     * Async writer loop.
     */
    private void writerLoop() {
        while (running || !writeQueue.isEmpty()) {
            try {
                String message = writeQueue.poll(100, TimeUnit.MILLISECONDS);
                if (message != null) {
                    writeMessage(message);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Flush remaining messages
        while (!writeQueue.isEmpty()) {
            String message = writeQueue.poll();
            if (message != null) {
                writeMessage(message);
            }
        }
    }

    /**
     * Write a message to the log file.
     */
    private void writeMessage(String message) {
        synchronized (writeLock) {
            try {
                if (writer == null) {
                    return;
                }

                writer.write(message);
                writer.newLine();
                writer.flush();

                long messageSize = message.length() + 1;
                long newSize = currentFileSize.addAndGet(messageSize);
                messagesWritten.incrementAndGet();

                // Check for rotation
                if (newSize >= maxFileSize) {
                    rotate();
                }
            } catch (IOException e) {
                System.err.println("Failed to write log message: " + e.getMessage());
            }
        }
    }

    /**
     * Rotate log files.
     */
    private void rotate() {
        synchronized (writeLock) {
            try {
                // Close current writer
                if (writer != null) {
                    writer.close();
                    writer = null;
                }

                // Delete oldest backup if at limit
                Path oldestBackup = getBackupPath(maxBackupCount);
                if (Files.exists(oldestBackup)) {
                    Files.delete(oldestBackup);
                }

                // Rename existing backups
                for (int i = maxBackupCount - 1; i >= 1; i--) {
                    Path older = getBackupPath(i);
                    Path newer = getBackupPath(i + 1);
                    if (Files.exists(older)) {
                        Files.move(older, newer, StandardCopyOption.REPLACE_EXISTING);
                    }
                }

                // Rename current log to backup 1
                if (Files.exists(logFile)) {
                    Files.move(logFile, getBackupPath(1), StandardCopyOption.REPLACE_EXISTING);
                }

                // Reset size and open new file
                currentFileSize.set(0);
                openWriter();
                rotationCount.incrementAndGet();

                System.out.println("Log rotated: " + logFile);
            } catch (IOException e) {
                System.err.println("Log rotation failed: " + e.getMessage());
                openWriter(); // Try to reopen anyway
            }
        }
    }

    /**
     * Get backup file path.
     */
    private Path getBackupPath(int index) {
        String fileName = logFile.getFileName().toString();
        return logFile.resolveSibling(fileName + "." + index);
    }

    /**
     * Log a message (async).
     *
     * @param level      log level
     * @param loggerName logger name
     * @param message    the message
     */
    public void log(Logger.LogLevel level, String loggerName, String message) {
        String formatted = formatEntry(level, loggerName, message);
        if (!writeQueue.offer(formatted)) {
            System.err.println("Log queue full, dropping message");
        }
    }

    /**
     * Log a message with exception (async).
     *
     * @param level      log level
     * @param loggerName logger name
     * @param message    the message
     * @param throwable  the exception
     */
    public void log(Logger.LogLevel level, String loggerName, String message, Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        sb.append(formatEntry(level, loggerName, message));
        if (throwable != null) {
            sb.append("\n");
            StringWriter sw = new StringWriter();
            throwable.printStackTrace(new PrintWriter(sw));
            sb.append(sw);
        }
        if (!writeQueue.offer(sb.toString())) {
            System.err.println("Log queue full, dropping message");
        }
    }

    /**
     * Format a log entry.
     */
    private String formatEntry(Logger.LogLevel level, String loggerName, String message) {
        return String.format("%s [%-5s] [%s] %s",
                LocalDateTime.now().format(TIMESTAMP_FORMAT),
                level.name(),
                loggerName,
                message);
    }

    /**
     * Force a rotation (for testing or manual rotation).
     */
    public void forceRotate() {
        synchronized (writeLock) {
            rotate();
        }
    }

    /**
     * Flush pending writes.
     */
    public void flush() {
        synchronized (writeLock) {
            try {
                if (writer != null) {
                    writer.flush();
                }
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    /**
     * Close the logger.
     */
    public void close() {
        running = false;
        writerExecutor.shutdown();
        try {
            writerExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        synchronized (writeLock) {
            try {
                if (writer != null) {
                    writer.close();
                    writer = null;
                }
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    /**
     * Get statistics.
     */
    public String getStatistics() {
        return String.format("RotatingFileLogger[file=%s, size=%d/%d, messages=%d, rotations=%d, queue=%d]",
                logFile,
                currentFileSize.get(),
                maxFileSize,
                messagesWritten.get(),
                rotationCount.get(),
                writeQueue.size());
    }

    /**
     * Get current log file path.
     */
    public Path getLogFile() {
        return logFile;
    }

    /**
     * Get current file size.
     */
    public long getCurrentFileSize() {
        return currentFileSize.get();
    }

    /**
     * Get rotation count.
     */
    public long getRotationCount() {
        return rotationCount.get();
    }
}
