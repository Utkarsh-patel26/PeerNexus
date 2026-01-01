package com.example.jtorrent.logging;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Structured logging system.
 */
public class Logger {

  private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

  private static final ConcurrentMap<String, Logger> LOGGERS = new ConcurrentHashMap<>();
  private static volatile LogLevel globalLevel = LogLevel.INFO;
  private static volatile Path logFile = null;
  private static volatile boolean consoleEnabled = true;

  private final String name;

  /**
   * Log levels in order of severity.
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

    public static LogLevel fromString(String level) {
      try {
        return LogLevel.valueOf(level.toUpperCase());
      } catch (IllegalArgumentException e) {
        return INFO;
      }
    }
  }

  public static Logger getLogger(Class<?> clazz) {
    return getLogger(clazz.getSimpleName());
  }

  public static Logger getLogger(String name) {
    return LOGGERS.computeIfAbsent(name, Logger::new);
  }

  public static void setGlobalLevel(LogLevel level) {
    globalLevel = level;
  }

  public static void setGlobalLevel(String level) {
    globalLevel = LogLevel.fromString(level);
  }

  public static void setLogFile(Path path) {
    logFile = path;
    // Create parent directories if needed
    try {
      if (path != null && path.getParent() != null) {
        Files.createDirectories(path.getParent());
      }
    } catch (IOException e) {
      System.err.println("Failed to create log directory: " + e.getMessage());
    }
  }

  public static void setConsoleEnabled(boolean enabled) {
    consoleEnabled = enabled;
  }

  private Logger(String name) {
    this.name = name;
  }

  public void trace(String message, Object... args) {
    log(LogLevel.TRACE, message, null, args);
  }

  public void debug(String message, Object... args) {
    log(LogLevel.DEBUG, message, null, args);
  }

  public void info(String message, Object... args) {
    log(LogLevel.INFO, message, null, args);
  }

  public void warn(String message, Object... args) {
    log(LogLevel.WARN, message, null, args);
  }

  public void error(String message, Object... args) {
    log(LogLevel.ERROR, message, null, args);
  }

  public void error(String message, Throwable throwable, Object... args) {
    log(LogLevel.ERROR, message, throwable, args);
  }

  public boolean isTraceEnabled() {
    return globalLevel.getSeverity() <= LogLevel.TRACE.getSeverity();
  }

  public boolean isDebugEnabled() {
    return globalLevel.getSeverity() <= LogLevel.DEBUG.getSeverity();
  }

  /**
   * Check if info logging is enabled.
   *
   * @return true if enabled
   */
  public boolean isInfoEnabled() {
    return globalLevel.getSeverity() <= LogLevel.INFO.getSeverity();
  }

  /**
   * Internal log method.
   */
  private void log(LogLevel level, String message, Throwable throwable, Object... args) {
    if (level.getSeverity() < globalLevel.getSeverity()) {
      return;
    }

    String formattedMessage = formatMessage(message, args);
    String logEntry = formatLogEntry(level, formattedMessage, throwable);

    // Write to console
    if (consoleEnabled) {
      if (level.getSeverity() >= LogLevel.WARN.getSeverity()) {
        System.err.println(logEntry);
      } else {
        System.out.println(logEntry);
      }
    }

    // Write to file
    if (logFile != null) {
      writeToFile(logEntry);
    }
  }

  /**
   * Format log message with arguments.
   */
  private String formatMessage(String message, Object... args) {
    if (args.length == 0) {
      return message;
    }
    try {
      return String.format(message, args);
    } catch (Exception e) {
      return message + " [FORMAT ERROR: " + e.getMessage() + "]";
    }
  }

  /**
   * Format complete log entry.
   */
  private String formatLogEntry(LogLevel level, String message, Throwable throwable) {
    StringBuilder sb = new StringBuilder();

    // Timestamp
    sb.append(LocalDateTime.now().format(TIMESTAMP_FORMAT));
    sb.append(" ");

    // Level
    sb.append(String.format("[%-5s]", level.name()));
    sb.append(" ");

    // Logger name
    sb.append(String.format("[%s]", name));
    sb.append(" ");

    // Message
    sb.append(message);

    // Exception
    if (throwable != null) {
      sb.append("\n");
      sb.append(getStackTrace(throwable));
    }

    return sb.toString();
  }

  /**
   * Get stack trace as string.
   */
  private String getStackTrace(Throwable throwable) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    throwable.printStackTrace(pw);
    return sw.toString();
  }

  /**
   * Write log entry to file.
   */
  private void writeToFile(String logEntry) {
    try {
      Files.writeString(logFile, logEntry + "\n",
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (IOException e) {
      System.err.println("Failed to write to log file: " + e.getMessage());
    }
  }

  /**
   * Create a metrics logger for performance tracking.
   *
   * @return metrics logger
   */
  public MetricsLogger metrics() {
    return new MetricsLogger(this);
  }

  /**
   * Metrics logger for performance tracking.
   */
  public static class MetricsLogger {
    private final Logger logger;
    private final long startTime;

    MetricsLogger(Logger logger) {
      this.logger = logger;
      this.startTime = System.nanoTime();
    }

    /**
     * Log operation completion with duration.
     *
     * @param operation operation name
     */
    public void log(String operation) {
      long duration = System.nanoTime() - startTime;
      double durationMs = duration / 1_000_000.0;
      logger.debug("METRIC: %s completed in %.2f ms", operation, durationMs);
    }

    /**
     * Log operation completion with custom message.
     *
     * @param operation operation name
     * @param message   additional message
     * @param args      format arguments
     */
    public void log(String operation, String message, Object... args) {
      long duration = System.nanoTime() - startTime;
      double durationMs = duration / 1_000_000.0;
      String formatted = String.format(message, args);
      logger.debug("METRIC: %s completed in %.2f ms - %s", operation, durationMs, formatted);
    }
  }
}
