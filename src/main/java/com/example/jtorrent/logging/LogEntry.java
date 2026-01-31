package com.example.jtorrent.logging;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Represents a single log entry with all metadata.
 */
public class LogEntry {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    private final Instant timestamp;
    private final LogLevel level;
    private final String category;
    private final String message;
    private final String torrentId; // null for global logs
    private final String threadName;
    private final Throwable exception;

    /**
     * Create a log entry.
     *
     * @param level     log level
     * @param category  logger category/class name
     * @param message   formatted message
     * @param torrentId torrent identifier (null for global)
     * @param exception optional exception
     */
    public LogEntry(
            LogLevel level,
            String category,
            String message,
            String torrentId,
            Throwable exception) {
        this.timestamp = Instant.now();
        this.level = level;
        this.category = category;
        this.message = message;
        this.torrentId = torrentId;
        this.threadName = Thread.currentThread().getName();
        this.exception = exception;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getFormattedTime() {
        return TIME_FORMATTER.format(timestamp);
    }

    public LogLevel getLevel() {
        return level;
    }

    public String getCategory() {
        return category;
    }

    public String getShortCategory() {
        int lastDot = category.lastIndexOf('.');
        return lastDot >= 0 ? category.substring(lastDot + 1) : category;
    }

    public String getMessage() {
        return message;
    }

    public String getTorrentId() {
        return torrentId;
    }

    public boolean isGlobal() {
        return torrentId == null;
    }

    public String getThreadName() {
        return threadName;
    }

    public Throwable getException() {
        return exception;
    }

    public boolean hasException() {
        return exception != null;
    }

    /**
     * Format as standard log line.
     *
     * @return formatted log line
     */
    public String format() {
        StringBuilder sb = new StringBuilder();
        sb.append(getFormattedTime());
        sb.append(" [").append(level.name()).append("]");
        sb.append(" [").append(getShortCategory()).append("]");
        if (torrentId != null) {
            sb.append(" [").append(torrentId.length() > 8 ? torrentId.substring(0, 8) : torrentId).append("]");
        }
        sb.append(" ").append(message);

        if (exception != null) {
            sb.append("\n").append(formatException(exception));
        }

        return sb.toString();
    }

    /**
     * Format exception with stack trace.
     */
    private String formatException(Throwable t) {
        StringBuilder sb = new StringBuilder();
        sb.append(t.getClass().getName()).append(": ").append(t.getMessage());
        for (StackTraceElement element : t.getStackTrace()) {
            sb.append("\n    at ").append(element.toString());
        }
        if (t.getCause() != null) {
            sb.append("\nCaused by: ").append(formatException(t.getCause()));
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return format();
    }

    /**
     * Log level enum.
     */
    public enum LogLevel {
        TRACE(0),
        DEBUG(1),
        INFO(2),
        WARN(3),
        ERROR(4);

        private final int severity;

        LogLevel(int severity) {
            this.severity = severity;
        }

        public int getSeverity() {
            return severity;
        }

        public boolean isAtLeast(LogLevel other) {
            return this.severity >= other.severity;
        }
    }
}
