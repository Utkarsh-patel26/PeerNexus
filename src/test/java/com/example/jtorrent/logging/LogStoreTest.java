package com.example.jtorrent.logging;

import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for LogStore.
 */
@DisplayName("LogStore Tests")
class LogStoreTest {

    private LogStore logStore;

    @BeforeEach
    void setUp() {
        logStore = new LogStore(1000);
    }

    @Nested
    @DisplayName("Singleton Tests")
    class SingletonTests {

        @Test
        @DisplayName("Should return singleton instance")
        void shouldReturnSingletonInstance() {
            LogStore instance1 = LogStore.getInstance();
            LogStore instance2 = LogStore.getInstance();

            assertSame(instance1, instance2);
        }
    }

    @Nested
    @DisplayName("Add Entry Tests")
    class AddEntryTests {

        @Test
        @DisplayName("Should add entry to global buffer")
        void shouldAddEntryToGlobalBuffer() {
            LogEntry entry = new LogEntry(
                    LogEntry.LogLevel.INFO,
                    "Test",
                    "Test message",
                    null,
                    null);

            logStore.addEntry(entry);

            List<LogEntry> logs = logStore.getGlobalLogs();
            assertEquals(1, logs.size());
            assertEquals("Test message", logs.get(0).getMessage());
        }

        @Test
        @DisplayName("Should add entry to torrent buffer")
        void shouldAddEntryToTorrentBuffer() {
            LogEntry entry = new LogEntry(
                    LogEntry.LogLevel.INFO,
                    "Test",
                    "Torrent message",
                    "torrent123",
                    null);

            logStore.addEntry(entry);

            List<LogEntry> logs = logStore.getTorrentLogs("torrent123");
            assertEquals(1, logs.size());
        }

        @Test
        @DisplayName("Should add torrent entry to both buffers")
        void shouldAddTorrentEntryToBothBuffers() {
            LogEntry entry = new LogEntry(
                    LogEntry.LogLevel.INFO,
                    "Test",
                    "Both buffers",
                    "torrent456",
                    null);

            logStore.addEntry(entry);

            assertEquals(1, logStore.getGlobalLogs().size());
            assertEquals(1, logStore.getTorrentLogs("torrent456").size());
        }

        @Test
        @DisplayName("Should return empty list for unknown torrent")
        void shouldReturnEmptyListForUnknownTorrent() {
            List<LogEntry> logs = logStore.getTorrentLogs("nonexistent");

            assertTrue(logs.isEmpty());
        }
    }

    @Nested
    @DisplayName("Circular Buffer Tests")
    class CircularBufferTests {

        @Test
        @DisplayName("Should limit entries to max size")
        void shouldLimitEntriesToMaxSize() {
            LogStore smallStore = new LogStore(10);

            for (int i = 0; i < 20; i++) {
                LogEntry entry = new LogEntry(
                        LogEntry.LogLevel.INFO,
                        "Test",
                        "Message " + i,
                        null,
                        null);
                smallStore.addEntry(entry);
            }

            List<LogEntry> logs = smallStore.getGlobalLogs();
            assertEquals(10, logs.size());
        }

        @Test
        @DisplayName("Should keep newest entries when buffer full")
        void shouldKeepNewestEntriesWhenBufferFull() {
            LogStore smallStore = new LogStore(5);

            for (int i = 0; i < 10; i++) {
                LogEntry entry = new LogEntry(
                        LogEntry.LogLevel.INFO,
                        "Test",
                        "Message " + i,
                        null,
                        null);
                smallStore.addEntry(entry);
            }

            List<LogEntry> logs = smallStore.getGlobalLogs();
            // Should contain messages 5-9 (newest 5)
            assertTrue(logs.get(logs.size() - 1).getMessage().contains("9"));
        }
    }

    @Nested
    @DisplayName("Filtering Tests")
    class FilteringTests {

        @BeforeEach
        void addTestEntries() {
            logStore.addEntry(new LogEntry(LogEntry.LogLevel.TRACE, "TraceLogger", "Trace msg", null, null));
            logStore.addEntry(new LogEntry(LogEntry.LogLevel.DEBUG, "DebugLogger", "Debug msg", null, null));
            logStore.addEntry(new LogEntry(LogEntry.LogLevel.INFO, "InfoLogger", "Info msg", null, null));
            logStore.addEntry(new LogEntry(LogEntry.LogLevel.WARN, "WarnLogger", "Warn msg", null, null));
            logStore.addEntry(new LogEntry(LogEntry.LogLevel.ERROR, "ErrorLogger", "Error msg", null, null));
        }

        @Test
        @DisplayName("Should filter by minimum level")
        void shouldFilterByMinimumLevel() {
            List<LogEntry> filtered = logStore.getFilteredLogs(null, LogEntry.LogLevel.WARN, null, null);

            assertEquals(2, filtered.size()); // WARN and ERROR
            assertTrue(filtered.stream()
                    .allMatch(e -> e.getLevel() == LogEntry.LogLevel.WARN || e.getLevel() == LogEntry.LogLevel.ERROR));
        }

        @Test
        @DisplayName("Should filter by category")
        void shouldFilterByCategory() {
            List<LogEntry> filtered = logStore.getFilteredLogs(null, LogEntry.LogLevel.TRACE, "Debug", null);

            assertEquals(1, filtered.size());
            assertEquals("DebugLogger", filtered.get(0).getCategory());
        }

        @Test
        @DisplayName("Should filter by text")
        void shouldFilterByText() {
            List<LogEntry> filtered = logStore.getFilteredLogs(null, LogEntry.LogLevel.TRACE, null, "Warn");

            assertEquals(1, filtered.size());
            assertTrue(filtered.get(0).getMessage().contains("Warn"));
        }

        @Test
        @DisplayName("Should filter by torrent ID")
        void shouldFilterByTorrentId() {
            logStore.addEntry(new LogEntry(LogEntry.LogLevel.INFO, "Test", "Torrent msg", "t1", null));
            logStore.addEntry(new LogEntry(LogEntry.LogLevel.INFO, "Test", "Another msg", "t1", null));

            List<LogEntry> filtered = logStore.getFilteredLogs("t1", LogEntry.LogLevel.TRACE, null, null);

            assertEquals(2, filtered.size());
        }

        @Test
        @DisplayName("Should combine multiple filters")
        void shouldCombineMultipleFilters() {
            List<LogEntry> filtered = logStore.getFilteredLogs(null, LogEntry.LogLevel.INFO, "Info", "Info");

            assertEquals(1, filtered.size());
        }

        @Test
        @DisplayName("Should be case insensitive for category filter")
        void shouldBeCaseInsensitiveForCategoryFilter() {
            List<LogEntry> filtered = logStore.getFilteredLogs(null, LogEntry.LogLevel.TRACE, "debug", null);

            assertEquals(1, filtered.size());
        }

        @Test
        @DisplayName("Should be case insensitive for text filter")
        void shouldBeCaseInsensitiveForTextFilter() {
            List<LogEntry> filtered = logStore.getFilteredLogs(null, LogEntry.LogLevel.TRACE, null, "warn");

            assertEquals(1, filtered.size());
        }
    }

    @Nested
    @DisplayName("Listener Tests")
    class ListenerTests {

        @Test
        @DisplayName("Should notify listeners on new entry")
        void shouldNotifyListenersOnNewEntry() {
            List<LogEntry> received = new ArrayList<>();
            logStore.addListener(received::add);

            LogEntry entry = new LogEntry(LogEntry.LogLevel.INFO, "Test", "msg", null, null);
            logStore.addEntry(entry);

            assertEquals(1, received.size());
            assertEquals(entry, received.get(0));
        }

        @Test
        @DisplayName("Should notify multiple listeners")
        void shouldNotifyMultipleListeners() {
            List<LogEntry> received1 = new ArrayList<>();
            List<LogEntry> received2 = new ArrayList<>();
            logStore.addListener(received1::add);
            logStore.addListener(received2::add);

            LogEntry entry = new LogEntry(LogEntry.LogLevel.INFO, "Test", "msg", null, null);
            logStore.addEntry(entry);

            assertEquals(1, received1.size());
            assertEquals(1, received2.size());
        }

        @Test
        @DisplayName("Should handle listener exceptions gracefully")
        void shouldHandleListenerExceptionsGracefully() {
            logStore.addListener(e -> {
                throw new RuntimeException("Listener error");
            });

            List<LogEntry> received = new ArrayList<>();
            logStore.addListener(received::add);

            LogEntry entry = new LogEntry(LogEntry.LogLevel.INFO, "Test", "msg", null, null);

            // Should not throw
            assertDoesNotThrow(() -> logStore.addEntry(entry));

            // Second listener should still receive
            assertEquals(1, received.size());
        }
    }

    @Nested
    @DisplayName("Torrent Isolation Tests")
    class TorrentIsolationTests {

        @Test
        @DisplayName("Should isolate logs per torrent")
        void shouldIsolateLogsPerTorrent() {
            logStore.addEntry(new LogEntry(LogEntry.LogLevel.INFO, "Test", "T1 msg", "t1", null));
            logStore.addEntry(new LogEntry(LogEntry.LogLevel.INFO, "Test", "T2 msg", "t2", null));
            logStore.addEntry(new LogEntry(LogEntry.LogLevel.INFO, "Test", "T1 msg2", "t1", null));

            assertEquals(2, logStore.getTorrentLogs("t1").size());
            assertEquals(1, logStore.getTorrentLogs("t2").size());
        }

        @Test
        @DisplayName("Should not mix torrent logs")
        void shouldNotMixTorrentLogs() {
            logStore.addEntry(new LogEntry(LogEntry.LogLevel.INFO, "Test", "T1 specific", "t1", null));

            List<LogEntry> t2Logs = logStore.getTorrentLogs("t2");

            assertTrue(t2Logs.isEmpty());
        }
    }

    @Nested
    @DisplayName("Thread Safety Tests")
    class ThreadSafetyTests {

        @Test
        @DisplayName("Should handle concurrent writes")
        void shouldHandleConcurrentWrites() throws InterruptedException {
            int threadCount = 10;
            int entriesPerThread = 100;
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int t = 0; t < threadCount; t++) {
                final int threadNum = t;
                new Thread(() -> {
                    for (int i = 0; i < entriesPerThread; i++) {
                        logStore.addEntry(new LogEntry(
                                LogEntry.LogLevel.INFO,
                                "Thread" + threadNum,
                                "Message " + i,
                                null,
                                null));
                    }
                    latch.countDown();
                }).start();
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS));

            // All entries should be present (up to buffer limit)
            assertTrue(logStore.getGlobalLogs().size() <= 1000);
        }

        @Test
        @DisplayName("Should handle concurrent reads and writes")
        void shouldHandleConcurrentReadsAndWrites() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(20);

            // Writers
            for (int t = 0; t < 10; t++) {
                new Thread(() -> {
                    for (int i = 0; i < 50; i++) {
                        logStore.addEntry(new LogEntry(
                                LogEntry.LogLevel.INFO, "Writer", "msg", null, null));
                    }
                    latch.countDown();
                }).start();
            }

            // Readers
            for (int t = 0; t < 10; t++) {
                new Thread(() -> {
                    for (int i = 0; i < 50; i++) {
                        logStore.getGlobalLogs();
                        logStore.getFilteredLogs(null, LogEntry.LogLevel.INFO, null, null);
                    }
                    latch.countDown();
                }).start();
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle empty buffer")
        void shouldHandleEmptyBuffer() {
            List<LogEntry> logs = logStore.getGlobalLogs();

            assertNotNull(logs);
            assertTrue(logs.isEmpty());
        }

        @Test
        @DisplayName("Should handle filter on empty buffer")
        void shouldHandleFilterOnEmptyBuffer() {
            List<LogEntry> filtered = logStore.getFilteredLogs(null, LogEntry.LogLevel.ERROR, "test", "msg");

            assertNotNull(filtered);
            assertTrue(filtered.isEmpty());
        }

        @Test
        @DisplayName("Should handle null category filter")
        void shouldHandleNullCategoryFilter() {
            logStore.addEntry(new LogEntry(LogEntry.LogLevel.INFO, "Test", "msg", null, null));

            List<LogEntry> filtered = logStore.getFilteredLogs(null, LogEntry.LogLevel.TRACE, null, null);

            assertEquals(1, filtered.size());
        }

        @Test
        @DisplayName("Should handle null text filter")
        void shouldHandleNullTextFilter() {
            logStore.addEntry(new LogEntry(LogEntry.LogLevel.INFO, "Test", "msg", null, null));

            List<LogEntry> filtered = logStore.getFilteredLogs(null, LogEntry.LogLevel.TRACE, "Test", null);

            assertEquals(1, filtered.size());
        }
    }
}
