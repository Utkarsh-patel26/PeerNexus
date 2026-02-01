package com.example.jtorrent.logging;

import org.junit.jupiter.api.*;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for LogEntry.
 */
@DisplayName("LogEntry Tests")
class LogEntryTest {

    @Nested
    @DisplayName("Construction Tests")
    class ConstructionTests {

        @Test
        @DisplayName("Should create log entry with all fields")
        void shouldCreateLogEntryWithAllFields() {
            LogEntry entry = new LogEntry(
                    LogEntry.LogLevel.INFO,
                    "com.example.Test",
                    "Test message",
                    "torrent123",
                    null);

            assertNotNull(entry);
            assertEquals(LogEntry.LogLevel.INFO, entry.getLevel());
            assertEquals("com.example.Test", entry.getCategory());
            assertEquals("Test message", entry.getMessage());
            assertEquals("torrent123", entry.getTorrentId());
            assertNull(entry.getException());
        }

        @Test
        @DisplayName("Should create global log entry")
        void shouldCreateGlobalLogEntry() {
            LogEntry entry = new LogEntry(
                    LogEntry.LogLevel.WARN,
                    "GlobalLogger",
                    "Global message",
                    null,
                    null);

            assertNull(entry.getTorrentId());
            assertTrue(entry.isGlobal());
        }

        @Test
        @DisplayName("Should capture timestamp on creation")
        void shouldCaptureTimestampOnCreation() {
            Instant before = Instant.now();

            LogEntry entry = new LogEntry(
                    LogEntry.LogLevel.INFO,
                    "Test",
                    "Message",
                    null,
                    null);

            Instant after = Instant.now();

            assertNotNull(entry.getTimestamp());
            assertTrue(entry.getTimestamp().compareTo(before) >= 0);
            assertTrue(entry.getTimestamp().compareTo(after) <= 0);
        }

        @Test
        @DisplayName("Should capture thread name")
        void shouldCaptureThreadName() {
            LogEntry entry = new LogEntry(
                    LogEntry.LogLevel.DEBUG,
                    "Test",
                    "Message",
                    null,
                    null);

            assertEquals(Thread.currentThread().getName(), entry.getThreadName());
        }

        @Test
        @DisplayName("Should store exception")
        void shouldStoreException() {
            Exception exception = new RuntimeException("Test exception");

            LogEntry entry = new LogEntry(
                    LogEntry.LogLevel.ERROR,
                    "Test",
                    "Error occurred",
                    null,
                    exception);

            assertTrue(entry.hasException());
            assertEquals(exception, entry.getException());
        }
    }

    @Nested
    @DisplayName("Log Level Tests")
    class LogLevelTests {

        @Test
        @DisplayName("Should have correct level for TRACE")
        void shouldHaveCorrectLevelForTrace() {
            LogEntry entry = new LogEntry(LogEntry.LogLevel.TRACE, "Test", "msg", null, null);
            assertEquals(LogEntry.LogLevel.TRACE, entry.getLevel());
        }

        @Test
        @DisplayName("Should have correct level for DEBUG")
        void shouldHaveCorrectLevelForDebug() {
            LogEntry entry = new LogEntry(LogEntry.LogLevel.DEBUG, "Test", "msg", null, null);
            assertEquals(LogEntry.LogLevel.DEBUG, entry.getLevel());
        }

        @Test
        @DisplayName("Should have correct level for INFO")
        void shouldHaveCorrectLevelForInfo() {
            LogEntry entry = new LogEntry(LogEntry.LogLevel.INFO, "Test", "msg", null, null);
            assertEquals(LogEntry.LogLevel.INFO, entry.getLevel());
        }

        @Test
        @DisplayName("Should have correct level for WARN")
        void shouldHaveCorrectLevelForWarn() {
            LogEntry entry = new LogEntry(LogEntry.LogLevel.WARN, "Test", "msg", null, null);
            assertEquals(LogEntry.LogLevel.WARN, entry.getLevel());
        }

        @Test
        @DisplayName("Should have correct level for ERROR")
        void shouldHaveCorrectLevelForError() {
            LogEntry entry = new LogEntry(LogEntry.LogLevel.ERROR, "Test", "msg", null, null);
            assertEquals(LogEntry.LogLevel.ERROR, entry.getLevel());
        }
    }

    @Nested
    @DisplayName("Category Tests")
    class CategoryTests {

        @Test
        @DisplayName("Should return full category")
        void shouldReturnFullCategory() {
            LogEntry entry = new LogEntry(
                    LogEntry.LogLevel.INFO,
                    "com.example.mypackage.MyClass",
                    "message",
                    null,
                    null);

            assertEquals("com.example.mypackage.MyClass", entry.getCategory());
        }

        @Test
        @DisplayName("Should return short category")
        void shouldReturnShortCategory() {
            LogEntry entry = new LogEntry(
                    LogEntry.LogLevel.INFO,
                    "com.example.mypackage.MyClass",
                    "message",
                    null,
                    null);

            assertEquals("MyClass", entry.getShortCategory());
        }

        @Test
        @DisplayName("Should handle simple category name")
        void shouldHandleSimpleCategoryName() {
            LogEntry entry = new LogEntry(
                    LogEntry.LogLevel.INFO,
                    "SimpleLogger",
                    "message",
                    null,
                    null);

            assertEquals("SimpleLogger", entry.getCategory());
            assertEquals("SimpleLogger", entry.getShortCategory());
        }
    }

    @Nested
    @DisplayName("Torrent ID Tests")
    class TorrentIdTests {

        @Test
        @DisplayName("Should indicate global entry")
        void shouldIndicateGlobalEntry() {
            LogEntry entry = new LogEntry(LogEntry.LogLevel.INFO, "Test", "msg", null, null);

            assertTrue(entry.isGlobal());
        }

        @Test
        @DisplayName("Should indicate torrent-specific entry")
        void shouldIndicateTorrentSpecificEntry() {
            LogEntry entry = new LogEntry(LogEntry.LogLevel.INFO, "Test", "msg", "torrent123", null);

            assertFalse(entry.isGlobal());
            assertEquals("torrent123", entry.getTorrentId());
        }
    }

    @Nested
    @DisplayName("Time Formatting Tests")
    class TimeFormattingTests {

        @Test
        @DisplayName("Should format time")
        void shouldFormatTime() {
            LogEntry entry = new LogEntry(LogEntry.LogLevel.INFO, "Test", "msg", null, null);

            String formattedTime = entry.getFormattedTime();

            assertNotNull(formattedTime);
            // Format is HH:mm:ss.SSS
            assertTrue(formattedTime.matches("\\d{2}:\\d{2}:\\d{2}\\.\\d{3}"));
        }
    }

    @Nested
    @DisplayName("Format Method Tests")
    class FormatMethodTests {

        @Test
        @DisplayName("Should format log entry")
        void shouldFormatLogEntry() {
            LogEntry entry = new LogEntry(
                    LogEntry.LogLevel.INFO,
                    "TestLogger",
                    "Test message",
                    null,
                    null);

            String formatted = entry.format();

            assertNotNull(formatted);
            assertTrue(formatted.contains("INFO"));
            assertTrue(formatted.contains("Test message"));
        }

        @Test
        @DisplayName("Should include level in format")
        void shouldIncludeLevelInFormat() {
            LogEntry entry = new LogEntry(LogEntry.LogLevel.WARN, "Test", "Warning message", null, null);

            String formatted = entry.format();

            assertTrue(formatted.contains("WARN"));
        }
    }

    @Nested
    @DisplayName("Exception Tests")
    class ExceptionTests {

        @Test
        @DisplayName("Should indicate no exception")
        void shouldIndicateNoException() {
            LogEntry entry = new LogEntry(LogEntry.LogLevel.INFO, "Test", "msg", null, null);

            assertFalse(entry.hasException());
            assertNull(entry.getException());
        }

        @Test
        @DisplayName("Should indicate has exception")
        void shouldIndicateHasException() {
            Exception ex = new NullPointerException("Test");
            LogEntry entry = new LogEntry(LogEntry.LogLevel.ERROR, "Test", "msg", null, ex);

            assertTrue(entry.hasException());
            assertNotNull(entry.getException());
        }

        @Test
        @DisplayName("Should preserve exception details")
        void shouldPreserveExceptionDetails() {
            Exception ex = new IllegalArgumentException("Invalid argument");
            LogEntry entry = new LogEntry(LogEntry.LogLevel.ERROR, "Test", "Error", null, ex);

            assertEquals("Invalid argument", entry.getException().getMessage());
            assertEquals(IllegalArgumentException.class, entry.getException().getClass());
        }
    }

    @Nested
    @DisplayName("Thread Safety Tests")
    class ThreadSafetyTests {

        @Test
        @DisplayName("Should capture correct thread name from different threads")
        void shouldCaptureCorrectThreadNameFromDifferentThreads() throws InterruptedException {
            LogEntry[] entries = new LogEntry[5];
            Thread[] threads = new Thread[5];

            for (int i = 0; i < threads.length; i++) {
                final int idx = i;
                threads[i] = new Thread(() -> {
                    entries[idx] = new LogEntry(LogEntry.LogLevel.INFO, "Test", "msg", null, null);
                }, "TestThread-" + i);
            }

            for (Thread t : threads) {
                t.start();
            }

            for (Thread t : threads) {
                t.join();
            }

            for (int i = 0; i < entries.length; i++) {
                assertEquals("TestThread-" + i, entries[i].getThreadName());
            }
        }
    }

    @Nested
    @DisplayName("LogLevel Enum Tests")
    class LogLevelEnumTests {

        @Test
        @DisplayName("Should have correct severity ordering")
        void shouldHaveCorrectSeverityOrdering() {
            assertTrue(LogEntry.LogLevel.TRACE.getSeverity() < LogEntry.LogLevel.DEBUG.getSeverity());
            assertTrue(LogEntry.LogLevel.DEBUG.getSeverity() < LogEntry.LogLevel.INFO.getSeverity());
            assertTrue(LogEntry.LogLevel.INFO.getSeverity() < LogEntry.LogLevel.WARN.getSeverity());
            assertTrue(LogEntry.LogLevel.WARN.getSeverity() < LogEntry.LogLevel.ERROR.getSeverity());
        }

        @Test
        @DisplayName("Should check isAtLeast correctly")
        void shouldCheckIsAtLeastCorrectly() {
            assertTrue(LogEntry.LogLevel.ERROR.isAtLeast(LogEntry.LogLevel.TRACE));
            assertTrue(LogEntry.LogLevel.ERROR.isAtLeast(LogEntry.LogLevel.ERROR));
            assertTrue(LogEntry.LogLevel.INFO.isAtLeast(LogEntry.LogLevel.DEBUG));
            assertFalse(LogEntry.LogLevel.DEBUG.isAtLeast(LogEntry.LogLevel.INFO));
        }
    }
}
