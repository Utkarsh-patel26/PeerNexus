package com.example.jtorrent.logging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Logger Tests")
class LoggerTest {

    private Logger logger;

    @BeforeEach
    void setUp() {
        logger = Logger.getLogger("test");
    }

    @Test
    @DisplayName("Create logger with name")
    void testCreateLogger() {
        Logger logger = Logger.getLogger("test-logger");
        assertNotNull(logger);
    }

    @Test
    @DisplayName("Log info message")
    void testLogInfo() {
        assertDoesNotThrow(() -> logger.info("Test info message"));
    }

    @Test
    @DisplayName("Log debug message")
    void testLogDebug() {
        assertDoesNotThrow(() -> logger.debug("Test debug message"));
    }

    @Test
    @DisplayName("Log warning message")
    void testLogWarning() {
        assertDoesNotThrow(() -> logger.warn("Test warning message"));
    }

    @Test
    @DisplayName("Log error message")
    void testLogError() {
        assertDoesNotThrow(() -> logger.error("Test error message"));
    }

    @Test
    @DisplayName("Log error with exception")
    void testLogErrorWithException() {
        Exception ex = new Exception("Test exception");
        assertDoesNotThrow(() -> logger.error("Error occurred", ex));
    }

    @Test
    @DisplayName("Logger can log")
    void testLoggerCanLog() {
        assertDoesNotThrow(() -> logger.info("test"));
    }

    @Test
    @DisplayName("Set global log level")
    void testSetGlobalLevel() {
        assertDoesNotThrow(() -> Logger.setGlobalLevel(Logger.LogLevel.DEBUG));
    }

    @Test
    @DisplayName("LogLevel enum values")
    void testLevelEnumValues() {
        assertNotNull(Logger.LogLevel.DEBUG);
        assertNotNull(Logger.LogLevel.INFO);
        assertNotNull(Logger.LogLevel.WARN);
        assertNotNull(Logger.LogLevel.ERROR);
    }

    @Test
    @DisplayName("Format message")
    void testFormatMessage() {
        assertDoesNotThrow(() -> logger.info("Message with %s", "parameter"));
    }

    @Test
    @DisplayName("Log with multiple parameters")
    void testLogMultipleParams() {
        assertDoesNotThrow(() -> logger.info("Value: %d, Text: %s", 42, "test"));
    }
}
