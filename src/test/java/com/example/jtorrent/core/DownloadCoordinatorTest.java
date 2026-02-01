package com.example.jtorrent.core;

import com.example.jtorrent.events.EventBus;
import com.example.jtorrent.events.PieceCompletedEvent;
import com.example.jtorrent.logging.Logger;
import com.example.jtorrent.scheduler.BandwidthLimiter;
import com.example.jtorrent.storage.DiskManager;
import com.example.jtorrent.storage.PieceManager;
import com.example.jtorrent.storage.PieceState;
import com.example.jtorrent.storage.PieceVerifier;
import org.junit.jupiter.api.*;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for DownloadCoordinator.
 */
@DisplayName("DownloadCoordinator Tests")
class DownloadCoordinatorTest {

    private PieceManager pieceManager;
    private DiskManager diskManager;
    private PieceVerifier pieceVerifier;
    private BandwidthLimiter bandwidthLimiter;
    private TransferStats transferStats;
    private Logger logger;
    private byte[] infoHash;
    private DownloadCoordinator coordinator;

    @BeforeEach
    void setUp() {
        pieceManager = mock(PieceManager.class);
        diskManager = mock(DiskManager.class);
        pieceVerifier = mock(PieceVerifier.class);
        bandwidthLimiter = mock(BandwidthLimiter.class);
        transferStats = mock(TransferStats.class);
        logger = mock(Logger.class);
        infoHash = new byte[20];

        // Default mock behavior
        when(bandwidthLimiter.requestDownload(anyString(), anyLong())).thenReturn(Long.MAX_VALUE);
        when(bandwidthLimiter.requestUpload(anyString(), anyLong())).thenReturn(Long.MAX_VALUE);

        coordinator = new DownloadCoordinator(
                pieceManager, diskManager, pieceVerifier,
                bandwidthLimiter, transferStats, infoHash, logger);
    }

    @Nested
    @DisplayName("Write Piece Block Tests")
    class WritePieceBlockTests {

        @Test
        @DisplayName("Should write block to disk")
        void shouldWriteBlockToDisk() throws IOException {
            int pieceIndex = 0;
            int offset = 0;
            byte[] data = new byte[16384];

            when(pieceManager.markBlockReceived(pieceIndex, offset)).thenReturn(false);

            boolean result = coordinator.writePieceBlock(pieceIndex, offset, data);

            assertTrue(result);
            verify(diskManager).writeBlock(pieceIndex, offset, data);
        }

        @Test
        @DisplayName("Should apply bandwidth limiting")
        void shouldApplyBandwidthLimiting() throws IOException {
            int pieceIndex = 0;
            int offset = 0;
            byte[] data = new byte[16384];

            when(pieceManager.markBlockReceived(pieceIndex, offset)).thenReturn(false);

            coordinator.writePieceBlock(pieceIndex, offset, data);

            verify(bandwidthLimiter).requestDownload(eq("global"), eq((long) data.length));
        }

        @Test
        @DisplayName("Should complete piece when all blocks received")
        void shouldCompletePieceWhenAllBlocksReceived() throws IOException {
            int pieceIndex = 0;
            int offset = 0;
            byte[] data = new byte[16384];

            when(pieceManager.markBlockReceived(pieceIndex, offset)).thenReturn(true);
            when(pieceVerifier.verifyPiece(pieceIndex)).thenReturn(true);
            when(pieceManager.getPieceLength(pieceIndex)).thenReturn(16384);

            boolean result = coordinator.writePieceBlock(pieceIndex, offset, data);

            assertTrue(result);
            verify(pieceManager).setPieceState(pieceIndex, PieceState.COMPLETE);
        }

        @Test
        @DisplayName("Should reset piece on hash verification failure")
        void shouldResetPieceOnHashVerificationFailure() throws IOException {
            int pieceIndex = 0;
            int offset = 0;
            byte[] data = new byte[16384];

            when(pieceManager.markBlockReceived(pieceIndex, offset)).thenReturn(true);
            when(pieceVerifier.verifyPiece(pieceIndex)).thenReturn(false);
            when(pieceManager.getPieceLength(pieceIndex)).thenReturn(16384);

            boolean result = coordinator.writePieceBlock(pieceIndex, offset, data);

            assertFalse(result);
            verify(pieceManager).setPieceState(pieceIndex, PieceState.MISSING);
        }
    }

    @Nested
    @DisplayName("Read Piece Block For Upload Tests")
    class ReadPieceBlockForUploadTests {

        @Test
        @DisplayName("Should return null for incomplete piece")
        void shouldReturnNullForIncompletePiece() throws IOException {
            int pieceIndex = 0;
            int offset = 0;
            int length = 16384;

            when(pieceManager.getPieceState(pieceIndex)).thenReturn(PieceState.MISSING);

            byte[] result = coordinator.readPieceBlockForUpload(pieceIndex, offset, length);

            assertNull(result);
        }

        @Test
        @DisplayName("Should read block for complete piece")
        void shouldReadBlockForCompletePiece() throws IOException {
            int pieceIndex = 0;
            int offset = 0;
            int length = 16384;
            byte[] expectedData = new byte[length];

            when(pieceManager.getPieceState(pieceIndex)).thenReturn(PieceState.COMPLETE);
            when(diskManager.readBlock(pieceIndex, offset, length)).thenReturn(expectedData);

            byte[] result = coordinator.readPieceBlockForUpload(pieceIndex, offset, length);

            assertNotNull(result);
            assertArrayEquals(expectedData, result);
        }

        @Test
        @DisplayName("Should apply bandwidth limiting for upload")
        void shouldApplyBandwidthLimitingForUpload() throws IOException {
            int pieceIndex = 0;
            int offset = 0;
            int length = 16384;
            byte[] data = new byte[length];

            when(pieceManager.getPieceState(pieceIndex)).thenReturn(PieceState.COMPLETE);
            when(diskManager.readBlock(pieceIndex, offset, length)).thenReturn(data);

            coordinator.readPieceBlockForUpload(pieceIndex, offset, length);

            verify(bandwidthLimiter).requestUpload(eq("global"), eq((long) length));
        }

        @Test
        @DisplayName("Should record upload bytes")
        void shouldRecordUploadBytes() throws IOException {
            int pieceIndex = 0;
            int offset = 0;
            int length = 16384;
            byte[] data = new byte[length];

            when(pieceManager.getPieceState(pieceIndex)).thenReturn(PieceState.COMPLETE);
            when(diskManager.readBlock(pieceIndex, offset, length)).thenReturn(data);

            coordinator.readPieceBlockForUpload(pieceIndex, offset, length);

            verify(transferStats).recordUpload(length);
        }
    }

    @Nested
    @DisplayName("Upload Statistics Tests")
    class UploadStatisticsTests {

        @Test
        @DisplayName("Should track total uploaded bytes")
        void shouldTrackTotalUploadedBytes() {
            coordinator.recordUpload(1000);
            coordinator.recordUpload(2000);

            assertEquals(3000, coordinator.getTotalUploaded());
        }

        @Test
        @DisplayName("Should calculate upload rate")
        void shouldCalculateUploadRate() throws InterruptedException {
            // Record some uploads
            coordinator.recordUpload(1000);

            // Wait a bit and check rate
            Thread.sleep(10);
            long rate = coordinator.getUploadRate();

            // Rate should be non-negative
            assertTrue(rate >= 0);
        }
    }

    @Nested
    @DisplayName("Verification Failure Tests")
    class VerificationFailureTests {

        @Test
        @DisplayName("Should track verification failures")
        void shouldTrackVerificationFailures() throws IOException {
            int pieceIndex = 0;
            byte[] data = new byte[16384];

            when(pieceManager.markBlockReceived(anyInt(), anyInt())).thenReturn(true);
            when(pieceVerifier.verifyPiece(pieceIndex)).thenReturn(false);
            when(pieceManager.getPieceLength(anyInt())).thenReturn(16384);

            coordinator.writePieceBlock(pieceIndex, 0, data);

            assertEquals(1, coordinator.getVerificationFailures());
        }

        @Test
        @DisplayName("Should increment failures on each failure")
        void shouldIncrementFailuresOnEachFailure() throws IOException {
            byte[] data = new byte[16384];

            when(pieceManager.markBlockReceived(anyInt(), anyInt())).thenReturn(true);
            when(pieceVerifier.verifyPiece(anyInt())).thenReturn(false);
            when(pieceManager.getPieceLength(anyInt())).thenReturn(16384);

            coordinator.writePieceBlock(0, 0, data);
            coordinator.writePieceBlock(1, 0, data);
            coordinator.writePieceBlock(2, 0, data);

            assertEquals(3, coordinator.getVerificationFailures());
        }
    }

    @Nested
    @DisplayName("Recheck Tests")
    class RecheckTests {

        @Test
        @DisplayName("Should recheck all pieces")
        void shouldRecheckAllPieces() throws IOException {
            when(pieceVerifier.recheckAllPieces()).thenReturn(50);
            when(pieceManager.getPieceCount()).thenReturn(100);

            int validCount = coordinator.recheckAllPieces();

            assertEquals(50, validCount);
            verify(pieceVerifier).recheckAllPieces();
        }
    }

    @Nested
    @DisplayName("Null Bandwidth Limiter Tests")
    class NullBandwidthLimiterTests {

        @Test
        @DisplayName("Should work without bandwidth limiter")
        void shouldWorkWithoutBandwidthLimiter() throws IOException {
            DownloadCoordinator coordinatorNoLimiter = new DownloadCoordinator(
                    pieceManager, diskManager, pieceVerifier,
                    null, transferStats, infoHash, logger);

            int pieceIndex = 0;
            byte[] data = new byte[16384];

            when(pieceManager.markBlockReceived(anyInt(), anyInt())).thenReturn(false);

            boolean result = coordinatorNoLimiter.writePieceBlock(pieceIndex, 0, data);

            assertTrue(result);
            verify(diskManager).writeBlock(pieceIndex, 0, data);
        }
    }
}
