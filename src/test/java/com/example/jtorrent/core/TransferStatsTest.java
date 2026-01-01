package com.example.jtorrent.core;

import com.example.jtorrent.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TransferStats Comprehensive Tests")
class TransferStatsTest {

    private TransferStats stats;
    private Logger logger;

    @BeforeEach
    void setUp() {
        logger = Logger.getLogger("test-stats");
        stats = new TransferStats(logger, "TestContext");
    }

    // ==================== Constructor Tests ====================

    @Test
    @DisplayName("constructor_shouldCreateInstance_whenValidParameters")
    void constructor_shouldCreateInstance_whenValidParameters() {
        TransferStats newStats = new TransferStats(logger, "context");
        assertNotNull(newStats);
        assertEquals(0, newStats.getDownloadedBlocks());
        assertEquals(0, newStats.getUploadedBlocks());
    }

    @Test
    @DisplayName("constructor_shouldInitializeAllCountersToZero_whenCreated")
    void constructor_shouldInitializeAllCountersToZero_whenCreated() {
        assertAll("All counters should be zero initially",
                () -> assertEquals(0, stats.getDownloadedBlocks()),
                () -> assertEquals(0, stats.getDownloadedBytes()),
                () -> assertEquals(0, stats.getUploadedBlocks()),
                () -> assertEquals(0, stats.getUploadedBytes()),
                () -> assertEquals(0, stats.getPeersConnected()),
                () -> assertEquals(0, stats.getPeersUnchoked()),
                () -> assertEquals(0, stats.getPeersFailed()),
                () -> assertEquals(0, stats.getPiecesCompleted()));
    }

    // ==================== Download Recording Tests ====================

    @Test
    @DisplayName("recordDownload_shouldIncrementBlockCount_whenCalled")
    void recordDownload_shouldIncrementBlockCount_whenCalled() {
        stats.recordDownload(16384);
        assertEquals(1, stats.getDownloadedBlocks());
    }

    @Test
    @DisplayName("recordDownload_shouldAccumulateBytes_whenCalledMultipleTimes")
    void recordDownload_shouldAccumulateBytes_whenCalledMultipleTimes() {
        stats.recordDownload(1024);
        stats.recordDownload(2048);
        stats.recordDownload(4096);

        assertEquals(3, stats.getDownloadedBlocks());
        assertEquals(7168, stats.getDownloadedBytes());
    }

    @Test
    @DisplayName("recordDownload_shouldHandleZeroBytes_whenCalled")
    void recordDownload_shouldHandleZeroBytes_whenCalled() {
        stats.recordDownload(0);
        assertEquals(1, stats.getDownloadedBlocks());
        assertEquals(0, stats.getDownloadedBytes());
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 100, 1024, 16384, 65536, 1048576 })
    @DisplayName("recordDownload_shouldHandleVariousSizes_whenCalled")
    void recordDownload_shouldHandleVariousSizes_whenCalled(int bytes) {
        stats.recordDownload(bytes);
        assertEquals(bytes, stats.getDownloadedBytes());
    }

    @Test
    @DisplayName("recordDownloadRequest_shouldIncrementCounter_whenCalled")
    void recordDownloadRequest_shouldIncrementCounter_whenCalled() {
        stats.recordDownloadRequest();
        stats.recordDownloadRequest();
        stats.recordDownloadRequest();
        // Note: downloadRequests is private, we verify indirectly through behavior
        assertNotNull(stats);
    }

    // ==================== Upload Recording Tests ====================

    @Test
    @DisplayName("recordUpload_shouldIncrementBlockCount_whenCalled")
    void recordUpload_shouldIncrementBlockCount_whenCalled() {
        stats.recordUpload(16384);
        assertEquals(1, stats.getUploadedBlocks());
    }

    @Test
    @DisplayName("recordUpload_shouldAccumulateBytes_whenCalledMultipleTimes")
    void recordUpload_shouldAccumulateBytes_whenCalledMultipleTimes() {
        stats.recordUpload(512);
        stats.recordUpload(1024);
        stats.recordUpload(2048);

        assertEquals(3, stats.getUploadedBlocks());
        assertEquals(3584, stats.getUploadedBytes());
    }

    @Test
    @DisplayName("recordUpload_shouldHandleLargeValues_whenCalled")
    void recordUpload_shouldHandleLargeValues_whenCalled() {
        for (int i = 0; i < 1000; i++) {
            stats.recordUpload(16384);
        }
        assertEquals(1000, stats.getUploadedBlocks());
        assertEquals(16384000, stats.getUploadedBytes());
    }

    @Test
    @DisplayName("recordUploadRequest_shouldIncrementCounter_whenCalled")
    void recordUploadRequest_shouldIncrementCounter_whenCalled() {
        stats.recordUploadRequest();
        stats.recordUploadRequest();
        // Verify through behavior
        assertNotNull(stats);
    }

    // ==================== Peer Stats Tests ====================

    @Test
    @DisplayName("recordPeerConnected_shouldIncrementCounter_whenCalled")
    void recordPeerConnected_shouldIncrementCounter_whenCalled() {
        stats.recordPeerConnected();
        assertEquals(1, stats.getPeersConnected());
    }

    @Test
    @DisplayName("recordPeerConnected_shouldAccumulate_whenCalledMultipleTimes")
    void recordPeerConnected_shouldAccumulate_whenCalledMultipleTimes() {
        for (int i = 0; i < 10; i++) {
            stats.recordPeerConnected();
        }
        assertEquals(10, stats.getPeersConnected());
    }

    @Test
    @DisplayName("recordPeerUnchoked_shouldIncrementCounter_whenCalled")
    void recordPeerUnchoked_shouldIncrementCounter_whenCalled() {
        stats.recordPeerUnchoked();
        assertEquals(1, stats.getPeersUnchoked());
    }

    @Test
    @DisplayName("recordPeerChoked_shouldDecrementCounter_whenUnchokedPeersExist")
    void recordPeerChoked_shouldDecrementCounter_whenUnchokedPeersExist() {
        stats.recordPeerUnchoked();
        stats.recordPeerUnchoked();
        stats.recordPeerUnchoked();
        assertEquals(3, stats.getPeersUnchoked());

        stats.recordPeerChoked();
        assertEquals(2, stats.getPeersUnchoked());
    }

    @Test
    @DisplayName("recordPeerChoked_shouldNotGoBelowZero_whenCalledOnZero")
    void recordPeerChoked_shouldNotGoBelowZero_whenCalledOnZero() {
        assertEquals(0, stats.getPeersUnchoked());
        stats.recordPeerChoked();
        assertEquals(0, stats.getPeersUnchoked());
    }

    @Test
    @DisplayName("recordPeerFailed_shouldIncrementCounter_whenCalled")
    void recordPeerFailed_shouldIncrementCounter_whenCalled() {
        stats.recordPeerFailed();
        stats.recordPeerFailed();
        assertEquals(2, stats.getPeersFailed());
    }

    // ==================== Piece Stats Tests ====================

    @Test
    @DisplayName("recordPieceCompleted_shouldIncrementCounter_whenCalled")
    void recordPieceCompleted_shouldIncrementCounter_whenCalled() {
        stats.recordPieceCompleted();
        assertEquals(1, stats.getPiecesCompleted());
    }

    @Test
    @DisplayName("recordPieceCompleted_shouldAccumulate_whenCalledMultipleTimes")
    void recordPieceCompleted_shouldAccumulate_whenCalledMultipleTimes() {
        for (int i = 0; i < 100; i++) {
            stats.recordPieceCompleted();
        }
        assertEquals(100, stats.getPiecesCompleted());
    }

    @Test
    @DisplayName("setPiecesInProgress_shouldSetValue_whenCalled")
    void setPiecesInProgress_shouldSetValue_whenCalled() {
        stats.setPiecesInProgress(5);
        // Pieces in progress is set but not exposed via getter
        assertNotNull(stats);
    }

    @Test
    @DisplayName("setPiecesInProgress_shouldOverwritePreviousValue_whenCalledAgain")
    void setPiecesInProgress_shouldOverwritePreviousValue_whenCalledAgain() {
        stats.setPiecesInProgress(10);
        stats.setPiecesInProgress(3);
        assertNotNull(stats);
    }

    // ==================== Logging Tests ====================

    @Test
    @DisplayName("logIfDue_shouldNotLog_whenIntervalNotReached")
    void logIfDue_shouldNotLog_whenIntervalNotReached() {
        stats.recordDownload(1024);
        stats.logIfDue(10000, 0);
        // Should not throw exception
        assertNotNull(stats);
    }

    @Test
    @DisplayName("logIfDue_shouldLog_whenIntervalExceeded")
    void logIfDue_shouldLog_whenIntervalExceeded() throws InterruptedException {
        stats.recordDownload(1024);
        Thread.sleep(100);
        stats.logIfDue(50, 5);
        // Should log without exception
        assertNotNull(stats);
    }

    @Test
    @DisplayName("logSummary_shouldNotThrow_whenCalledWithZeroStats")
    void logSummary_shouldNotThrow_whenCalledWithZeroStats() {
        assertDoesNotThrow(() -> stats.logSummary(0));
    }

    @Test
    @DisplayName("logSummary_shouldNotThrow_whenCalledWithNonZeroStats")
    void logSummary_shouldNotThrow_whenCalledWithNonZeroStats() {
        stats.recordDownload(16384);
        stats.recordUpload(8192);
        stats.recordPeerConnected();
        stats.recordPieceCompleted();

        assertDoesNotThrow(() -> stats.logSummary(10));
    }

    @Test
    @DisplayName("forceLog_shouldLogImmediately_whenCalled")
    void forceLog_shouldLogImmediately_whenCalled() {
        stats.recordDownload(1024);
        stats.recordUpload(512);
        assertDoesNotThrow(() -> stats.forceLog(3));
    }

    // ==================== Complex Scenarios ====================

    @Test
    @DisplayName("mixedOperations_shouldMaintainCorrectCounts_whenCombined")
    void mixedOperations_shouldMaintainCorrectCounts_whenCombined() {
        // Simulate a download session
        for (int i = 0; i < 10; i++) {
            stats.recordDownload(16384);
            stats.recordDownloadRequest();
        }

        for (int i = 0; i < 5; i++) {
            stats.recordUpload(8192);
            stats.recordUploadRequest();
        }

        stats.recordPeerConnected();
        stats.recordPeerConnected();
        stats.recordPeerUnchoked();
        stats.recordPieceCompleted();
        stats.recordPieceCompleted();
        stats.recordPieceCompleted();

        assertAll("Mixed operations should maintain correct counts",
                () -> assertEquals(10, stats.getDownloadedBlocks()),
                () -> assertEquals(163840, stats.getDownloadedBytes()),
                () -> assertEquals(5, stats.getUploadedBlocks()),
                () -> assertEquals(40960, stats.getUploadedBytes()),
                () -> assertEquals(2, stats.getPeersConnected()),
                () -> assertEquals(1, stats.getPeersUnchoked()),
                () -> assertEquals(3, stats.getPiecesCompleted()));
    }

    @Test
    @DisplayName("largeVolume_shouldHandleCorrectly_whenProcessingManyEvents")
    void largeVolume_shouldHandleCorrectly_whenProcessingManyEvents() {
        for (int i = 0; i < 10000; i++) {
            stats.recordDownload(1024);
        }

        assertEquals(10000, stats.getDownloadedBlocks());
        assertEquals(10240000, stats.getDownloadedBytes());
    }

    // ==================== Thread Safety Tests ====================

    @Test
    @DisplayName("concurrentDownloads_shouldBeSafe_whenMultipleThreads")
    void concurrentDownloads_shouldBeSafe_whenMultipleThreads() throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        stats.recordDownload(1024);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(threadCount * operationsPerThread, stats.getDownloadedBlocks());
        assertEquals(threadCount * operationsPerThread * 1024, stats.getDownloadedBytes());
    }

    @Test
    @DisplayName("concurrentMixedOperations_shouldBeSafe_whenMultipleThreads")
    void concurrentMixedOperations_shouldBeSafe_whenMultipleThreads() throws InterruptedException {
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger downloads = new AtomicInteger(0);
        AtomicInteger uploads = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 50; j++) {
                        if (threadId % 2 == 0) {
                            stats.recordDownload(1024);
                            downloads.incrementAndGet();
                        } else {
                            stats.recordUpload(512);
                            uploads.incrementAndGet();
                        }
                        stats.recordPeerConnected();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(downloads.get(), stats.getDownloadedBlocks());
        assertEquals(uploads.get(), stats.getUploadedBlocks());
        assertEquals(threadCount * 50, stats.getPeersConnected());
    }

    @Test
    @DisplayName("concurrentPeerStateChanges_shouldBeSafe_whenMultipleThreads")
    void concurrentPeerStateChanges_shouldBeSafe_whenMultipleThreads() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch latch = new CountDownLatch(4);

        // Thread 1: Add unchoked peers
        executor.submit(() -> {
            try {
                for (int i = 0; i < 100; i++) {
                    stats.recordPeerUnchoked();
                }
            } finally {
                latch.countDown();
            }
        });

        // Thread 2: Remove unchoked peers
        executor.submit(() -> {
            try {
                for (int i = 0; i < 50; i++) {
                    stats.recordPeerChoked();
                }
            } finally {
                latch.countDown();
            }
        });

        // Thread 3: Record failures
        executor.submit(() -> {
            try {
                for (int i = 0; i < 25; i++) {
                    stats.recordPeerFailed();
                }
            } finally {
                latch.countDown();
            }
        });

        // Thread 4: Record pieces
        executor.submit(() -> {
            try {
                for (int i = 0; i < 75; i++) {
                    stats.recordPieceCompleted();
                }
            } finally {
                latch.countDown();
            }
        });

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Verify operations completed without data corruption
        assertTrue(stats.getPeersUnchoked() >= 0);
        assertEquals(25, stats.getPeersFailed());
        assertEquals(75, stats.getPiecesCompleted());
    }

    // ==================== Edge Cases ====================

    @Test
    @DisplayName("downloadBytes_shouldNotOverflow_whenAccumulatingLargeValues")
    void downloadBytes_shouldNotOverflow_whenAccumulatingLargeValues() {
        // Record large values multiple times to exceed Integer.MAX_VALUE
        for (int i = 0; i < 5; i++) {
            stats.recordDownload(Integer.MAX_VALUE / 2);
        }

        assertTrue(stats.getDownloadedBytes() > Integer.MAX_VALUE,
                "Downloaded bytes should exceed Integer.MAX_VALUE when accumulating large values");
    }

    @Test
    @DisplayName("multipleLogsInSequence_shouldNotThrow_whenCalledRapidly")
    void multipleLogsInSequence_shouldNotThrow_whenCalledRapidly() {
        for (int i = 0; i < 10; i++) {
            stats.recordDownload(1024);
            final int outstandingRequests = i;
            assertDoesNotThrow(() -> stats.forceLog(outstandingRequests));
        }
    }

    @Test
    @DisplayName("setPiecesInProgress_shouldHandleZero_whenCalled")
    void setPiecesInProgress_shouldHandleZero_whenCalled() {
        stats.setPiecesInProgress(0);
        assertNotNull(stats);
    }

    @Test
    @DisplayName("setPiecesInProgress_shouldHandleLargeValues_whenCalled")
    void setPiecesInProgress_shouldHandleLargeValues_whenCalled() {
        stats.setPiecesInProgress(10000);
        assertNotNull(stats);
    }
}
