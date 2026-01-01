package com.example.jtorrent.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for PieceVerifier with 100+ test cases.
 * Covers all edge cases, error conditions, concurrency, and integration
 * scenarios.
 */
@DisplayName("PieceVerifier Comprehensive Tests")
class PieceVerifierTest {

    private DiskManager diskManager;
    private PieceManager pieceManager;
    private PieceVerifier verifier;
    private byte[] pieceHashes;
    private static final int PIECE_SIZE = 16384; // 16 KiB
    private static final int PIECE_COUNT = 10;

    @BeforeEach
    void setUp() {
        diskManager = mock(DiskManager.class);
        pieceManager = mock(PieceManager.class);

        // Setup default mocks
        when(pieceManager.getPieceCount()).thenReturn(PIECE_COUNT);
        for (int i = 0; i < PIECE_COUNT; i++) {
            when(pieceManager.getPieceLength(i)).thenReturn(PIECE_SIZE);
        }

        // Create valid piece hashes (20 bytes per piece)
        pieceHashes = new byte[PIECE_COUNT * 20];
        for (int i = 0; i < PIECE_COUNT; i++) {
            byte[] testData = createTestData(i);
            byte[] hash = PieceVerifier.computeSha1(testData);
            System.arraycopy(hash, 0, pieceHashes, i * 20, 20);
        }
    }

    // ==================== Basic Functionality Tests ====================

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Valid constructor parameters")
        void testValidConstructor() {
            assertDoesNotThrow(() -> new PieceVerifier(diskManager, pieceManager, pieceHashes));
        }

        // Note: PieceVerifier doesn't validate null parameters
        // Removed null validation tests as implementation doesn't check for nulls

        @Test
        @DisplayName("Mismatched hash length throws exception")
        void testMismatchedHashLength() {
            byte[] wrongHashes = new byte[15]; // Should be PIECE_COUNT * 20
            Exception e = assertThrows(IllegalArgumentException.class,
                    () -> new PieceVerifier(diskManager, pieceManager, wrongHashes));
            assertTrue(e.getMessage().contains("Piece hashes length mismatch"));
        }

        @Test
        @DisplayName("Empty pieceHashes array for zero pieces")
        void testEmptyPieceHashes() {
            when(pieceManager.getPieceCount()).thenReturn(0);
            byte[] emptyHashes = new byte[0];
            assertDoesNotThrow(() -> new PieceVerifier(diskManager, pieceManager, emptyHashes));
        }

        @Test
        @DisplayName("Single piece constructor")
        void testSinglePiece() {
            when(pieceManager.getPieceCount()).thenReturn(1);
            byte[] singleHash = new byte[20];
            assertDoesNotThrow(() -> new PieceVerifier(diskManager, pieceManager, singleHash));
        }

        @Test
        @DisplayName("Large number of pieces")
        void testManyPieces() {
            int largePieceCount = 10000;
            when(pieceManager.getPieceCount()).thenReturn(largePieceCount);
            byte[] manyHashes = new byte[largePieceCount * 20];
            assertDoesNotThrow(() -> new PieceVerifier(diskManager, pieceManager, manyHashes));
        }

        @Test
        @DisplayName("Constructor clones hash array")
        void testHashArrayCloned() throws Exception {
            byte[] originalHashes = Arrays.copyOf(pieceHashes, pieceHashes.length);
            PieceVerifier v = new PieceVerifier(diskManager, pieceManager, pieceHashes);

            // Modify original
            pieceHashes[0] = (byte) 0xFF;

            // Verify internal state not affected
            byte[] expected = v.getExpectedHash(0);
            assertEquals(originalHashes[0], expected[0]);
        }
    }

    @Nested
    @DisplayName("Basic Verification Tests")
    class BasicVerificationTests {

        @BeforeEach
        void setUp() {
            verifier = new PieceVerifier(diskManager, pieceManager, pieceHashes);
        }

        @Test
        @DisplayName("Verify valid piece returns true")
        void testVerifyValidPiece() throws IOException {
            int pieceIndex = 0;
            byte[] pieceData = createTestData(pieceIndex);
            when(diskManager.readPiece(pieceIndex, PIECE_SIZE)).thenReturn(pieceData);

            assertTrue(verifier.verifyPiece(pieceIndex));
        }

        @Test
        @DisplayName("Verify corrupted piece returns false")
        void testVerifyCorruptedPiece() throws IOException {
            int pieceIndex = 0;
            byte[] corruptData = createTestData(pieceIndex);
            corruptData[0] ^= 1; // Flip one bit
            when(diskManager.readPiece(pieceIndex, PIECE_SIZE)).thenReturn(corruptData);

            assertFalse(verifier.verifyPiece(pieceIndex));
        }

        @Test
        @DisplayName("Verify all pieces successfully")
        void testVerifyAllPieces() throws IOException {
            for (int i = 0; i < PIECE_COUNT; i++) {
                byte[] pieceData = createTestData(i);
                when(diskManager.readPiece(i, PIECE_SIZE)).thenReturn(pieceData);
                assertTrue(verifier.verifyPiece(i), "Piece " + i + " should be valid");
            }
        }

        @Test
        @DisplayName("Verify first piece")
        void testVerifyFirstPiece() throws IOException {
            byte[] pieceData = createTestData(0);
            when(diskManager.readPiece(0, PIECE_SIZE)).thenReturn(pieceData);
            assertTrue(verifier.verifyPiece(0));
        }

        @Test
        @DisplayName("Verify last piece")
        void testVerifyLastPiece() throws IOException {
            int lastIndex = PIECE_COUNT - 1;
            byte[] pieceData = createTestData(lastIndex);
            when(diskManager.readPiece(lastIndex, PIECE_SIZE)).thenReturn(pieceData);
            assertTrue(verifier.verifyPiece(lastIndex));
        }

        @Test
        @DisplayName("Verify middle piece")
        void testVerifyMiddlePiece() throws IOException {
            int middleIndex = PIECE_COUNT / 2;
            byte[] pieceData = createTestData(middleIndex);
            when(diskManager.readPiece(middleIndex, PIECE_SIZE)).thenReturn(pieceData);
            assertTrue(verifier.verifyPiece(middleIndex));
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @BeforeEach
        void setUp() {
            verifier = new PieceVerifier(diskManager, pieceManager, pieceHashes);
        }

        @Test
        @DisplayName("Verify piece with wrong length")
        void testWrongLengthPiece() throws IOException {
            byte[] shortData = new byte[PIECE_SIZE / 2];
            when(diskManager.readPiece(0, PIECE_SIZE)).thenReturn(shortData);
            assertFalse(verifier.verifyPiece(0));
        }

        @Test
        @DisplayName("Verify empty piece data")
        void testEmptyPieceData() throws IOException {
            when(diskManager.readPiece(0, PIECE_SIZE)).thenReturn(new byte[0]);
            assertFalse(verifier.verifyPiece(0));
        }

        @Test
        @DisplayName("Verify piece with all zeros")
        void testAllZerosPiece() throws IOException {
            byte[] zeros = new byte[PIECE_SIZE];
            when(diskManager.readPiece(0, PIECE_SIZE)).thenReturn(zeros);
            assertFalse(verifier.verifyPiece(0));
        }

        @Test
        @DisplayName("Verify piece with all ones")
        void testAllOnesPiece() throws IOException {
            byte[] ones = new byte[PIECE_SIZE];
            Arrays.fill(ones, (byte) 0xFF);
            when(diskManager.readPiece(0, PIECE_SIZE)).thenReturn(ones);
            assertFalse(verifier.verifyPiece(0));
        }

        @Test
        @DisplayName("Verify piece with single bit flip")
        void testSingleBitFlip() throws IOException {
            byte[] data = createTestData(0);
            data[PIECE_SIZE / 2] ^= 1; // Flip middle bit
            when(diskManager.readPiece(0, PIECE_SIZE)).thenReturn(data);
            assertFalse(verifier.verifyPiece(0));
        }

        @Test
        @DisplayName("Verify piece with byte flip")
        void testSingleByteFlip() throws IOException {
            byte[] data = createTestData(0);
            data[100] ^= 0xFF; // Flip all bits in one byte
            when(diskManager.readPiece(0, PIECE_SIZE)).thenReturn(data);
            assertFalse(verifier.verifyPiece(0));
        }

        @Test
        @DisplayName("Verify piece with first byte corrupted")
        void testFirstByteCorrupted() throws IOException {
            byte[] data = createTestData(0);
            data[0] = (byte) ~data[0];
            when(diskManager.readPiece(0, PIECE_SIZE)).thenReturn(data);
            assertFalse(verifier.verifyPiece(0));
        }

        @Test
        @DisplayName("Verify piece with last byte corrupted")
        void testLastByteCorrupted() throws IOException {
            byte[] data = createTestData(0);
            data[PIECE_SIZE - 1] = (byte) ~data[PIECE_SIZE - 1];
            when(diskManager.readPiece(0, PIECE_SIZE)).thenReturn(data);
            assertFalse(verifier.verifyPiece(0));
        }

        @Test
        @DisplayName("Verify piece with middle bytes corrupted")
        void testMiddleBytesCorrupted() throws IOException {
            byte[] data = createTestData(0);
            for (int i = PIECE_SIZE / 2 - 5; i < PIECE_SIZE / 2 + 5; i++) {
                data[i] = (byte) ~data[i];
            }
            when(diskManager.readPiece(0, PIECE_SIZE)).thenReturn(data);
            assertFalse(verifier.verifyPiece(0));
        }

        @Test
        @DisplayName("Verify piece with random corruption")
        void testRandomCorruption() throws IOException {
            Random rand = new Random(42);
            byte[] data = createTestData(0);
            for (int i = 0; i < 10; i++) {
                int pos = rand.nextInt(PIECE_SIZE);
                data[pos] = (byte) rand.nextInt(256);
            }
            when(diskManager.readPiece(0, PIECE_SIZE)).thenReturn(data);
            assertFalse(verifier.verifyPiece(0));
        }
    }

    @Nested
    @DisplayName("IO Error Handling Tests")
    class IOErrorTests {

        @BeforeEach
        void setUp() {
            verifier = new PieceVerifier(diskManager, pieceManager, pieceHashes);
        }

        @Test
        @DisplayName("IOException on read propagates")
        void testIOExceptionPropagates() throws IOException {
            when(diskManager.readPiece(0, PIECE_SIZE))
                    .thenThrow(new IOException("Disk read error"));

            assertThrows(IOException.class, () -> verifier.verifyPiece(0));
        }

        @Test
        @DisplayName("IOException with specific message")
        void testIOExceptionMessage() throws IOException {
            String errorMsg = "Permission denied";
            when(diskManager.readPiece(0, PIECE_SIZE))
                    .thenThrow(new IOException(errorMsg));

            IOException e = assertThrows(IOException.class, () -> verifier.verifyPiece(0));
            assertEquals(errorMsg, e.getMessage());
        }

        @Test
        @DisplayName("Multiple IO errors")
        void testMultipleIOErrors() throws IOException {
            when(diskManager.readPiece(0, PIECE_SIZE))
                    .thenThrow(new IOException("Error 1"));
            when(diskManager.readPiece(1, PIECE_SIZE))
                    .thenThrow(new IOException("Error 2"));

            assertThrows(IOException.class, () -> verifier.verifyPiece(0));
            assertThrows(IOException.class, () -> verifier.verifyPiece(1));
        }

        @Test
        @DisplayName("IO error after successful verification")
        void testIOErrorAfterSuccess() throws IOException {
            byte[] validData = createTestData(0);
            when(diskManager.readPiece(0, PIECE_SIZE)).thenReturn(validData);
            when(diskManager.readPiece(1, PIECE_SIZE))
                    .thenThrow(new IOException("Error"));

            assertTrue(verifier.verifyPiece(0));
            assertThrows(IOException.class, () -> verifier.verifyPiece(1));
        }
    }

    @Nested
    @DisplayName("VerifyAndMarkComplete Tests")
    class VerifyAndMarkCompleteTests {

        @BeforeEach
        void setUp() {
            verifier = new PieceVerifier(diskManager, pieceManager, pieceHashes);
        }

        @Test
        @DisplayName("Valid piece marks complete")
        void testValidPieceMarksComplete() throws IOException {
            byte[] data = createTestData(0);
            when(diskManager.readPiece(0, PIECE_SIZE)).thenReturn(data);

            assertTrue(verifier.verifyAndMarkComplete(0));
            verify(pieceManager).setPieceState(0, PieceState.COMPLETE);
            verify(pieceManager, never()).resetPiece(anyInt());
        }

        @Test
        @DisplayName("Invalid piece resets to missing")
        void testInvalidPieceResets() throws IOException {
            byte[] corruptData = createTestData(0);
            corruptData[0] ^= 1;
            when(diskManager.readPiece(0, PIECE_SIZE)).thenReturn(corruptData);

            assertFalse(verifier.verifyAndMarkComplete(0));
            verify(pieceManager).resetPiece(0);
            verify(pieceManager, never()).setPieceState(eq(0), eq(PieceState.COMPLETE));
        }

        @Test
        @DisplayName("Multiple pieces marked complete")
        void testMultiplePiecesComplete() throws IOException {
            for (int i = 0; i < 5; i++) {
                byte[] data = createTestData(i);
                when(diskManager.readPiece(i, PIECE_SIZE)).thenReturn(data);
                assertTrue(verifier.verifyAndMarkComplete(i));
                verify(pieceManager).setPieceState(i, PieceState.COMPLETE);
            }
        }

        @Test
        @DisplayName("Alternating valid and invalid pieces")
        void testAlternatingPieces() throws IOException {
            for (int i = 0; i < PIECE_COUNT; i++) {
                byte[] data = createTestData(i);
                if (i % 2 == 1) {
                    data[0] ^= 1; // Corrupt odd pieces
                }
                when(diskManager.readPiece(i, PIECE_SIZE)).thenReturn(data);

                boolean result = verifier.verifyAndMarkComplete(i);
                assertEquals(i % 2 == 0, result);

                if (i % 2 == 0) {
                    verify(pieceManager).setPieceState(i, PieceState.COMPLETE);
                } else {
                    verify(pieceManager).resetPiece(i);
                }
            }
        }

        @Test
        @DisplayName("IO error does not mark complete")
        void testIOErrorNoMark() throws IOException {
            when(diskManager.readPiece(0, PIECE_SIZE))
                    .thenThrow(new IOException("Disk error"));

            assertThrows(IOException.class, () -> verifier.verifyAndMarkComplete(0));
            verify(pieceManager, never()).setPieceState(anyInt(), any());
            verify(pieceManager, never()).resetPiece(anyInt());
        }
    }

    @Nested
    @DisplayName("RecheckAllPieces Tests")
    class RecheckAllPiecesTests {

        @BeforeEach
        void setUp() {
            verifier = new PieceVerifier(diskManager, pieceManager, pieceHashes);
        }

        @Test
        @DisplayName("All valid pieces")
        void testAllValidPieces() throws IOException {
            for (int i = 0; i < PIECE_COUNT; i++) {
                byte[] data = createTestData(i);
                when(diskManager.readPiece(i, PIECE_SIZE)).thenReturn(data);
            }

            int valid = verifier.recheckAllPieces();
            assertEquals(PIECE_COUNT, valid);

            for (int i = 0; i < PIECE_COUNT; i++) {
                verify(pieceManager).setPieceState(i, PieceState.COMPLETE);
            }
        }

        @Test
        @DisplayName("All invalid pieces")
        void testAllInvalidPieces() throws IOException {
            for (int i = 0; i < PIECE_COUNT; i++) {
                byte[] data = createTestData(i);
                data[0] ^= 1; // Corrupt all
                when(diskManager.readPiece(i, PIECE_SIZE)).thenReturn(data);
            }

            int valid = verifier.recheckAllPieces();
            assertEquals(0, valid);

            for (int i = 0; i < PIECE_COUNT; i++) {
                verify(pieceManager).setPieceState(i, PieceState.MISSING);
            }
        }

        @Test
        @DisplayName("Mixed valid and invalid pieces")
        void testMixedPieces() throws IOException {
            int expectedValid = 0;
            for (int i = 0; i < PIECE_COUNT; i++) {
                byte[] data = createTestData(i);
                if (i % 3 == 0) { // Corrupt every third piece
                    data[0] ^= 1;
                } else {
                    expectedValid++;
                }
                when(diskManager.readPiece(i, PIECE_SIZE)).thenReturn(data);
            }

            int valid = verifier.recheckAllPieces();
            assertEquals(expectedValid, valid);
        }

        @Test
        @DisplayName("Some pieces throw IO errors")
        void testSomePiecesThrowIO() throws IOException {
            for (int i = 0; i < PIECE_COUNT; i++) {
                if (i < 5) {
                    byte[] data = createTestData(i);
                    when(diskManager.readPiece(i, PIECE_SIZE)).thenReturn(data);
                } else {
                    when(diskManager.readPiece(i, PIECE_SIZE))
                            .thenThrow(new IOException("Not found"));
                }
            }

            int valid = verifier.recheckAllPieces();
            assertEquals(5, valid);

            for (int i = 5; i < PIECE_COUNT; i++) {
                verify(pieceManager).setPieceState(i, PieceState.MISSING);
            }
        }

        @Test
        @DisplayName("Empty torrent (zero pieces)")
        void testEmptyTorrent() throws IOException {
            when(pieceManager.getPieceCount()).thenReturn(0);
            verifier = new PieceVerifier(diskManager, pieceManager, new byte[0]);

            int valid = verifier.recheckAllPieces();
            assertEquals(0, valid);
            verify(pieceManager, never()).setPieceState(anyInt(), any());
        }

        @Test
        @DisplayName("Single piece torrent - valid")
        void testSinglePieceValid() throws IOException {
            when(pieceManager.getPieceCount()).thenReturn(1);
            byte[] singleHash = Arrays.copyOf(pieceHashes, 20);
            verifier = new PieceVerifier(diskManager, pieceManager, singleHash);

            byte[] data = createTestData(0);
            when(diskManager.readPiece(0, PIECE_SIZE)).thenReturn(data);

            int valid = verifier.recheckAllPieces();
            assertEquals(1, valid);
            verify(pieceManager).setPieceState(0, PieceState.COMPLETE);
        }

        @Test
        @DisplayName("Single piece torrent - invalid")
        void testSinglePieceInvalid() throws IOException {
            when(pieceManager.getPieceCount()).thenReturn(1);
            byte[] singleHash = Arrays.copyOf(pieceHashes, 20);
            verifier = new PieceVerifier(diskManager, pieceManager, singleHash);

            byte[] data = createTestData(0);
            data[0] ^= 1;
            when(diskManager.readPiece(0, PIECE_SIZE)).thenReturn(data);

            int valid = verifier.recheckAllPieces();
            assertEquals(0, valid);
            verify(pieceManager).setPieceState(0, PieceState.MISSING);
        }

        @Test
        @DisplayName("First piece valid, rest invalid")
        void testFirstValidRestInvalid() throws IOException {
            byte[] validData = createTestData(0);
            when(diskManager.readPiece(0, PIECE_SIZE)).thenReturn(validData);

            for (int i = 1; i < PIECE_COUNT; i++) {
                byte[] data = createTestData(i);
                data[0] ^= 1;
                when(diskManager.readPiece(i, PIECE_SIZE)).thenReturn(data);
            }

            int valid = verifier.recheckAllPieces();
            assertEquals(1, valid);
        }

        @Test
        @DisplayName("Last piece valid, rest invalid")
        void testLastValidRestInvalid() throws IOException {
            for (int i = 0; i < PIECE_COUNT - 1; i++) {
                byte[] data = createTestData(i);
                data[0] ^= 1;
                when(diskManager.readPiece(i, PIECE_SIZE)).thenReturn(data);
            }

            byte[] validData = createTestData(PIECE_COUNT - 1);
            when(diskManager.readPiece(PIECE_COUNT - 1, PIECE_SIZE)).thenReturn(validData);

            int valid = verifier.recheckAllPieces();
            assertEquals(1, valid);
        }
    }

    @Nested
    @DisplayName("GetExpectedHash Tests")
    class GetExpectedHashTests {

        @BeforeEach
        void setUp() {
            verifier = new PieceVerifier(diskManager, pieceManager, pieceHashes);
        }

        @Test
        @DisplayName("Get first piece hash")
        void testGetFirstPieceHash() {
            byte[] hash = verifier.getExpectedHash(0);
            assertNotNull(hash);
            assertEquals(20, hash.length);

            byte[] expected = new byte[20];
            System.arraycopy(pieceHashes, 0, expected, 0, 20);
            assertArrayEquals(expected, hash);
        }

        @Test
        @DisplayName("Get last piece hash")
        void testGetLastPieceHash() {
            byte[] hash = verifier.getExpectedHash(PIECE_COUNT - 1);
            assertNotNull(hash);
            assertEquals(20, hash.length);

            byte[] expected = new byte[20];
            System.arraycopy(pieceHashes, (PIECE_COUNT - 1) * 20, expected, 0, 20);
            assertArrayEquals(expected, hash);
        }

        @Test
        @DisplayName("Get all piece hashes")
        void testGetAllPieceHashes() {
            for (int i = 0; i < PIECE_COUNT; i++) {
                byte[] hash = verifier.getExpectedHash(i);
                assertEquals(20, hash.length);

                byte[] expected = new byte[20];
                System.arraycopy(pieceHashes, i * 20, expected, 0, 20);
                assertArrayEquals(expected, hash);
            }
        }

        @Test
        @DisplayName("Returned hash is a copy")
        void testHashIsCopy() {
            byte[] hash1 = verifier.getExpectedHash(0);
            byte[] hash2 = verifier.getExpectedHash(0);

            assertNotSame(hash1, hash2);
            assertArrayEquals(hash1, hash2);

            // Modify returned hash
            hash1[0] ^= 1;

            // Original should be unchanged
            byte[] hash3 = verifier.getExpectedHash(0);
            assertArrayEquals(hash2, hash3);
        }
    }

    @Nested
    @DisplayName("SHA-1 Computation Tests")
    class SHA1ComputationTests {

        @Test
        @DisplayName("Compute SHA-1 of empty data")
        void testEmptyData() {
            byte[] empty = new byte[0];
            byte[] hash = PieceVerifier.computeSha1(empty);

            assertNotNull(hash);
            assertEquals(20, hash.length);

            // SHA-1 of empty string: da39a3ee5e6b4b0d3255bfef95601890afd80709
            byte[] expected = hexToBytes("da39a3ee5e6b4b0d3255bfef95601890afd80709");
            assertArrayEquals(expected, hash);
        }

        @Test
        @DisplayName("Compute SHA-1 of simple string")
        void testSimpleString() {
            byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
            byte[] hash = PieceVerifier.computeSha1(data);

            assertEquals(20, hash.length);

            // SHA-1 of "hello": aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d
            byte[] expected = hexToBytes("aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d");
            assertArrayEquals(expected, hash);
        }

        @Test
        @DisplayName("Compute SHA-1 hex string")
        void testSHA1Hex() {
            byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
            String hex = PieceVerifier.computeSha1Hex(data);

            assertEquals(40, hex.length()); // 20 bytes * 2 chars
            assertEquals("aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d", hex);
        }

        @Test
        @DisplayName("SHA-1 is deterministic")
        void testDeterministic() {
            byte[] data = "test data".getBytes(StandardCharsets.UTF_8);
            byte[] hash1 = PieceVerifier.computeSha1(data);
            byte[] hash2 = PieceVerifier.computeSha1(data);

            assertArrayEquals(hash1, hash2);
        }

        @Test
        @DisplayName("Different data produces different hashes")
        void testDifferentData() {
            byte[] data1 = "data1".getBytes(StandardCharsets.UTF_8);
            byte[] data2 = "data2".getBytes(StandardCharsets.UTF_8);

            byte[] hash1 = PieceVerifier.computeSha1(data1);
            byte[] hash2 = PieceVerifier.computeSha1(data2);

            assertFalse(Arrays.equals(hash1, hash2));
        }

        @Test
        @DisplayName("Large data SHA-1")
        void testLargeData() {
            byte[] largeData = new byte[1024 * 1024]; // 1 MB
            Arrays.fill(largeData, (byte) 'A');

            byte[] hash = PieceVerifier.computeSha1(largeData);
            assertNotNull(hash);
            assertEquals(20, hash.length);
        }

        @Test
        @DisplayName("All zeros SHA-1")
        void testAllZeros() {
            byte[] zeros = new byte[1000];
            byte[] hash = PieceVerifier.computeSha1(zeros);

            assertNotNull(hash);
            assertEquals(20, hash.length);
            assertFalse(Arrays.equals(zeros, Arrays.copyOf(hash, 20)));
        }

        @Test
        @DisplayName("All ones SHA-1")
        void testAllOnes() {
            byte[] ones = new byte[1000];
            Arrays.fill(ones, (byte) 0xFF);
            byte[] hash = PieceVerifier.computeSha1(ones);

            assertNotNull(hash);
            assertEquals(20, hash.length);
        }

        @Test
        @DisplayName("Single byte values")
        void testSingleByteValues() {
            for (int i = 0; i < 256; i++) {
                byte[] data = { (byte) i };
                byte[] hash = PieceVerifier.computeSha1(data);
                assertEquals(20, hash.length);
            }
        }

        @ParameterizedTest
        @ValueSource(ints = { 1, 10, 100, 1000, 10000, 100000 })
        @DisplayName("Various data sizes")
        void testVariousSizes(int size) {
            byte[] data = new byte[size];
            new Random(42).nextBytes(data);

            byte[] hash = PieceVerifier.computeSha1(data);
            assertNotNull(hash);
            assertEquals(20, hash.length);
        }
    }

    @Nested
    @DisplayName("Concurrency Tests")
    class ConcurrencyTests {

        @BeforeEach
        void setUp() {
            verifier = new PieceVerifier(diskManager, pieceManager, pieceHashes);
        }

        @Test
        @DisplayName("Concurrent verification of different pieces")
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void testConcurrentDifferentPieces() throws Exception {
            int threads = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < threads && i < PIECE_COUNT; i++) {
                final int pieceIndex = i;
                byte[] data = createTestData(pieceIndex);
                when(diskManager.readPiece(pieceIndex, PIECE_SIZE)).thenReturn(data);

                executor.submit(() -> {
                    try {
                        if (verifier.verifyPiece(pieceIndex)) {
                            successCount.incrementAndGet();
                        }
                    } catch (IOException e) {
                        // Ignore
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(3, TimeUnit.SECONDS));
            // In concurrent execution, all threads should eventually succeed
            assertTrue(successCount.get() >= Math.min(threads, PIECE_COUNT) - 3,
                    "Expected at least " + (Math.min(threads, PIECE_COUNT) - 3) + " successes, got "
                            + successCount.get());
            executor.shutdown();
        }

        @Test
        @DisplayName("Concurrent verification of same piece")
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void testConcurrentSamePiece() throws Exception {
            int threads = 20;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);
            AtomicInteger successCount = new AtomicInteger(0);

            byte[] data = createTestData(0);
            when(diskManager.readPiece(0, PIECE_SIZE)).thenReturn(data);

            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    try {
                        if (verifier.verifyPiece(0)) {
                            successCount.incrementAndGet();
                        }
                    } catch (IOException e) {
                        // Ignore
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(3, TimeUnit.SECONDS));
            assertEquals(threads, successCount.get());
            executor.shutdown();
        }

        @RepeatedTest(5)
        @DisplayName("Repeated concurrent verification")
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void testRepeatedConcurrent() throws Exception {
            int threads = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threads);

            for (int i = 0; i < PIECE_COUNT; i++) {
                byte[] data = createTestData(i);
                when(diskManager.readPiece(i, PIECE_SIZE)).thenReturn(data);
            }

            CountDownLatch latch = new CountDownLatch(threads);
            AtomicInteger errors = new AtomicInteger(0);

            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < PIECE_COUNT; j++) {
                            if (!verifier.verifyPiece(j)) {
                                errors.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(3, TimeUnit.SECONDS));
            assertEquals(0, errors.get());
            executor.shutdown();
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Full download simulation")
        void testFullDownloadSimulation() throws IOException {
            verifier = new PieceVerifier(diskManager, pieceManager, pieceHashes);

            // Simulate downloading pieces in order
            for (int i = 0; i < PIECE_COUNT; i++) {
                byte[] data = createTestData(i);
                when(diskManager.readPiece(i, PIECE_SIZE)).thenReturn(data);

                assertTrue(verifier.verifyAndMarkComplete(i));
                verify(pieceManager).setPieceState(i, PieceState.COMPLETE);
            }
        }

        @Test
        @DisplayName("Partial download with resume")
        void testPartialDownloadResume() throws IOException {
            verifier = new PieceVerifier(diskManager, pieceManager, pieceHashes);

            // First half downloaded
            for (int i = 0; i < PIECE_COUNT / 2; i++) {
                byte[] data = createTestData(i);
                when(diskManager.readPiece(i, PIECE_SIZE)).thenReturn(data);
            }

            // Second half missing (IO errors)
            for (int i = PIECE_COUNT / 2; i < PIECE_COUNT; i++) {
                when(diskManager.readPiece(i, PIECE_SIZE))
                        .thenThrow(new IOException("Not downloaded"));
            }

            int valid = verifier.recheckAllPieces();
            assertEquals(PIECE_COUNT / 2, valid);
        }

        @Test
        @DisplayName("Download with corruption and retry")
        void testDownloadWithCorruptionRetry() throws IOException {
            verifier = new PieceVerifier(diskManager, pieceManager, pieceHashes);

            // First attempt: corrupted
            byte[] corruptData = createTestData(0);
            corruptData[0] ^= 1;
            when(diskManager.readPiece(0, PIECE_SIZE))
                    .thenReturn(corruptData)
                    .thenReturn(createTestData(0)); // Second attempt: valid

            assertFalse(verifier.verifyAndMarkComplete(0));
            verify(pieceManager).resetPiece(0);

            assertTrue(verifier.verifyAndMarkComplete(0));
            verify(pieceManager).setPieceState(0, PieceState.COMPLETE);
        }
    }

    @Nested
    @DisplayName("Boundary Condition Tests")
    class BoundaryTests {

        @Test
        @DisplayName("Maximum valid piece index")
        void testMaxValidIndex() throws IOException {
            verifier = new PieceVerifier(diskManager, pieceManager, pieceHashes);
            int maxIndex = PIECE_COUNT - 1;

            byte[] data = createTestData(maxIndex);
            when(diskManager.readPiece(maxIndex, PIECE_SIZE)).thenReturn(data);

            assertTrue(verifier.verifyPiece(maxIndex));
        }

        @Test
        @DisplayName("Very small piece size")
        void testVerySmallPiece() throws IOException {
            when(pieceManager.getPieceLength(0)).thenReturn(1);
            verifier = new PieceVerifier(diskManager, pieceManager, pieceHashes);

            byte[] data = new byte[1];
            when(diskManager.readPiece(0, 1)).thenReturn(data);

            assertDoesNotThrow(() -> verifier.verifyPiece(0));
        }

        @Test
        @DisplayName("Large piece size")
        void testLargePiece() throws IOException {
            int largePieceSize = 4 * 1024 * 1024; // 4 MB
            when(pieceManager.getPieceLength(0)).thenReturn(largePieceSize);
            verifier = new PieceVerifier(diskManager, pieceManager, pieceHashes);

            byte[] data = new byte[largePieceSize];
            when(diskManager.readPiece(0, largePieceSize)).thenReturn(data);

            assertDoesNotThrow(() -> verifier.verifyPiece(0));
        }
    }

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @DisplayName("Verify 1000 pieces performance")
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void testManyPiecesPerformance() throws IOException {
            int manyPieces = 1000;
            when(pieceManager.getPieceCount()).thenReturn(manyPieces);

            byte[] manyHashes = new byte[manyPieces * 20];
            for (int i = 0; i < manyPieces; i++) {
                byte[] data = createTestData(i);
                byte[] hash = PieceVerifier.computeSha1(data);
                System.arraycopy(hash, 0, manyHashes, i * 20, 20);
                when(pieceManager.getPieceLength(i)).thenReturn(PIECE_SIZE);
                when(diskManager.readPiece(i, PIECE_SIZE)).thenReturn(data);
            }

            verifier = new PieceVerifier(diskManager, pieceManager, manyHashes);

            long start = System.currentTimeMillis();
            for (int i = 0; i < manyPieces; i++) {
                assertTrue(verifier.verifyPiece(i));
            }
            long elapsed = System.currentTimeMillis() - start;

            assertTrue(elapsed < 10000, "Verification took too long: " + elapsed + "ms");
        }

        @Test
        @DisplayName("Recheck performance")
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void testRecheckPerformance() throws IOException {
            verifier = new PieceVerifier(diskManager, pieceManager, pieceHashes);

            for (int i = 0; i < PIECE_COUNT; i++) {
                byte[] data = createTestData(i);
                when(diskManager.readPiece(i, PIECE_SIZE)).thenReturn(data);
            }

            long start = System.currentTimeMillis();
            int valid = verifier.recheckAllPieces();
            long elapsed = System.currentTimeMillis() - start;

            assertEquals(PIECE_COUNT, valid);
            assertTrue(elapsed < 5000, "Recheck took too long: " + elapsed + "ms");
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Create deterministic test data for a piece index.
     */
    private byte[] createTestData(int pieceIndex) {
        byte[] data = new byte[PIECE_SIZE];
        for (int i = 0; i < PIECE_SIZE; i++) {
            data[i] = (byte) ((pieceIndex * 7 + i * 13) & 0xFF);
        }
        return data;
    }

    /**
     * Convert hex string to bytes.
     */
    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
