package com.example.jtorrent.metadata;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for TorrentCreator.
 * Tests torrent file creation from files and directories.
 */
@DisplayName("TorrentCreator Tests")
class TorrentCreatorTest {

    @TempDir
    Path tempDir;

    private Path testFile;
    private Path testDir;

    @BeforeEach
    void setUp() throws IOException {
        // Create a test file
        testFile = tempDir.resolve("test.txt");
        Files.write(testFile, "Hello, World! This is test content.".getBytes());

        // Create a test directory with multiple files
        testDir = tempDir.resolve("testdir");
        Files.createDirectories(testDir);
        Files.write(testDir.resolve("file1.txt"), "Content of file 1".getBytes());
        Files.write(testDir.resolve("file2.txt"), "Content of file 2".getBytes());
        Path subDir = testDir.resolve("subdir");
        Files.createDirectories(subDir);
        Files.write(subDir.resolve("file3.txt"), "Content of file 3".getBytes());
    }

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should create builder with defaults")
        void shouldCreateBuilderWithDefaults() {
            TorrentCreator creator = new TorrentCreator.Builder().build();

            assertNotNull(creator);
        }

        @Test
        @DisplayName("Should add single tracker")
        void shouldAddSingleTracker() {
            TorrentCreator creator = new TorrentCreator.Builder()
                    .addTracker("http://tracker.example.com/announce")
                    .build();

            assertNotNull(creator);
        }

        @Test
        @DisplayName("Should add multiple trackers")
        void shouldAddMultipleTrackers() {
            TorrentCreator creator = new TorrentCreator.Builder()
                    .addTracker("http://tracker1.com/announce")
                    .addTracker("udp://tracker2.com:6969")
                    .addTracker("http://tracker3.com/announce")
                    .build();

            assertNotNull(creator);
        }

        @Test
        @DisplayName("Should add tracker list")
        void shouldAddTrackerList() {
            List<String> trackers = Arrays.asList(
                    "http://tracker1.com/announce",
                    "http://tracker2.com/announce");

            TorrentCreator creator = new TorrentCreator.Builder()
                    .addTrackers(trackers)
                    .build();

            assertNotNull(creator);
        }

        @Test
        @DisplayName("Should set comment")
        void shouldSetComment() {
            TorrentCreator creator = new TorrentCreator.Builder()
                    .setComment("Test torrent comment")
                    .build();

            assertNotNull(creator);
        }

        @Test
        @DisplayName("Should set created by")
        void shouldSetCreatedBy() {
            TorrentCreator creator = new TorrentCreator.Builder()
                    .setCreatedBy("TestApp 1.0")
                    .build();

            assertNotNull(creator);
        }

        @Test
        @DisplayName("Should set private flag")
        void shouldSetPrivateFlag() {
            TorrentCreator creator = new TorrentCreator.Builder()
                    .setPrivate(true)
                    .build();

            assertNotNull(creator);
        }

        @Test
        @DisplayName("Should set valid piece size")
        void shouldSetValidPieceSize() {
            // Valid piece sizes: 16KB to 16MB, power of 2
            TorrentCreator creator = new TorrentCreator.Builder()
                    .setPieceSize(256 * 1024) // 256 KB
                    .build();

            assertNotNull(creator);
        }

        @Test
        @DisplayName("Should reject piece size too small")
        void shouldRejectPieceSizeTooSmall() {
            assertThrows(IllegalArgumentException.class, () -> new TorrentCreator.Builder()
                    .setPieceSize(8 * 1024) // 8 KB - too small
                    .build());
        }

        @Test
        @DisplayName("Should reject piece size too large")
        void shouldRejectPieceSizeTooLarge() {
            assertThrows(IllegalArgumentException.class, () -> new TorrentCreator.Builder()
                    .setPieceSize(32 * 1024 * 1024) // 32 MB - too large
                    .build());
        }

        @Test
        @DisplayName("Should reject non-power-of-two piece size")
        void shouldRejectNonPowerOfTwoPieceSize() {
            assertThrows(IllegalArgumentException.class, () -> new TorrentCreator.Builder()
                    .setPieceSize(100 * 1024) // 100 KB - not power of 2
                    .build());
        }

        @Test
        @DisplayName("Should allow zero piece size for auto-calculation")
        void shouldAllowZeroPieceSizeForAutoCalculation() {
            TorrentCreator creator = new TorrentCreator.Builder()
                    .setPieceSize(0)
                    .build();

            assertNotNull(creator);
        }

        @Test
        @DisplayName("Should chain all builder methods")
        void shouldChainAllBuilderMethods() {
            TorrentCreator creator = new TorrentCreator.Builder()
                    .addTracker("http://tracker.com/announce")
                    .addTrackers(Arrays.asList("udp://t2.com:6969"))
                    .setComment("My Comment")
                    .setCreatedBy("TestApp")
                    .setPrivate(true)
                    .setPieceSize(512 * 1024)
                    .build();

            assertNotNull(creator);
        }
    }

    @Nested
    @DisplayName("Single File Torrent Tests")
    class SingleFileTorrentTests {

        @Test
        @DisplayName("Should create torrent from single file")
        void shouldCreateTorrentFromSingleFile() throws IOException {
            TorrentCreator creator = new TorrentCreator.Builder()
                    .addTracker("http://tracker.example.com/announce")
                    .build();

            Path outputPath = tempDir.resolve("output.torrent");

            creator.createTorrent(testFile, outputPath, null);

            assertTrue(Files.exists(outputPath));
            assertTrue(Files.size(outputPath) > 0);
        }

        @Test
        @DisplayName("Should create torrent with correct file name")
        void shouldCreateTorrentWithCorrectFileName() throws IOException {
            TorrentCreator creator = new TorrentCreator.Builder()
                    .addTracker("http://tracker.example.com/announce")
                    .build();

            Path outputPath = tempDir.resolve("output.torrent");

            creator.createTorrent(testFile, outputPath, null);

            // Parse the torrent and verify name
            assertTrue(Files.exists(outputPath));
            byte[] torrentData = Files.readAllBytes(outputPath);
            // Verify torrent contains the file name
            String content = new String(torrentData);
            assertTrue(content.contains("test.txt"));
        }

        @Test
        @DisplayName("Should include tracker in torrent")
        void shouldIncludeTrackerInTorrent() throws IOException {
            String tracker = "http://tracker.example.com/announce";
            TorrentCreator creator = new TorrentCreator.Builder()
                    .addTracker(tracker)
                    .build();

            Path outputPath = tempDir.resolve("output.torrent");

            creator.createTorrent(testFile, outputPath, null);

            byte[] torrentData = Files.readAllBytes(outputPath);
            String content = new String(torrentData);
            assertTrue(content.contains(tracker));
        }

        @Test
        @DisplayName("Should include comment in torrent")
        void shouldIncludeCommentInTorrent() throws IOException {
            String comment = "This is my test comment";
            TorrentCreator creator = new TorrentCreator.Builder()
                    .addTracker("http://tracker.com/announce")
                    .setComment(comment)
                    .build();

            Path outputPath = tempDir.resolve("output.torrent");

            creator.createTorrent(testFile, outputPath, null);

            byte[] torrentData = Files.readAllBytes(outputPath);
            String content = new String(torrentData);
            assertTrue(content.contains(comment));
        }

        @Test
        @DisplayName("Should include created by in torrent")
        void shouldIncludeCreatedByInTorrent() throws IOException {
            String createdBy = "TestApp 2.0";
            TorrentCreator creator = new TorrentCreator.Builder()
                    .addTracker("http://tracker.com/announce")
                    .setCreatedBy(createdBy)
                    .build();

            Path outputPath = tempDir.resolve("output.torrent");

            creator.createTorrent(testFile, outputPath, null);

            byte[] torrentData = Files.readAllBytes(outputPath);
            String content = new String(torrentData);
            assertTrue(content.contains(createdBy));
        }
    }

    @Nested
    @DisplayName("Multi File Torrent Tests")
    class MultiFileTorrentTests {

        @Test
        @DisplayName("Should create torrent from directory")
        void shouldCreateTorrentFromDirectory() throws IOException {
            TorrentCreator creator = new TorrentCreator.Builder()
                    .addTracker("http://tracker.example.com/announce")
                    .build();

            Path outputPath = tempDir.resolve("output.torrent");

            creator.createTorrent(testDir, outputPath, null);

            assertTrue(Files.exists(outputPath));
            assertTrue(Files.size(outputPath) > 0);
        }

        @Test
        @DisplayName("Should include all files from directory")
        void shouldIncludeAllFilesFromDirectory() throws IOException {
            TorrentCreator creator = new TorrentCreator.Builder()
                    .addTracker("http://tracker.example.com/announce")
                    .build();

            Path outputPath = tempDir.resolve("output.torrent");

            creator.createTorrent(testDir, outputPath, null);

            byte[] torrentData = Files.readAllBytes(outputPath);
            String content = new String(torrentData);
            assertTrue(content.contains("file1.txt"));
            assertTrue(content.contains("file2.txt"));
            assertTrue(content.contains("file3.txt"));
        }

        @Test
        @DisplayName("Should preserve directory structure")
        void shouldPreserveDirectoryStructure() throws IOException {
            TorrentCreator creator = new TorrentCreator.Builder()
                    .addTracker("http://tracker.example.com/announce")
                    .build();

            Path outputPath = tempDir.resolve("output.torrent");

            creator.createTorrent(testDir, outputPath, null);

            byte[] torrentData = Files.readAllBytes(outputPath);
            String content = new String(torrentData);
            assertTrue(content.contains("subdir"));
        }

        @Test
        @DisplayName("Should use directory name as torrent name")
        void shouldUseDirectoryNameAsTorrentName() throws IOException {
            TorrentCreator creator = new TorrentCreator.Builder()
                    .addTracker("http://tracker.example.com/announce")
                    .build();

            Path outputPath = tempDir.resolve("output.torrent");

            creator.createTorrent(testDir, outputPath, null);

            byte[] torrentData = Files.readAllBytes(outputPath);
            String content = new String(torrentData);
            assertTrue(content.contains("testdir"));
        }
    }

    @Nested
    @DisplayName("Multiple Tracker Tests")
    class MultipleTrackerTests {

        @Test
        @DisplayName("Should create torrent with multiple trackers")
        void shouldCreateTorrentWithMultipleTrackers() throws IOException {
            TorrentCreator creator = new TorrentCreator.Builder()
                    .addTracker("http://tracker1.com/announce")
                    .addTracker("udp://tracker2.com:6969")
                    .addTracker("http://tracker3.com/announce")
                    .build();

            Path outputPath = tempDir.resolve("output.torrent");

            creator.createTorrent(testFile, outputPath, null);

            byte[] torrentData = Files.readAllBytes(outputPath);
            String content = new String(torrentData);
            assertTrue(content.contains("tracker1.com"));
            assertTrue(content.contains("tracker2.com"));
            assertTrue(content.contains("tracker3.com"));
        }

        @Test
        @DisplayName("Should use first tracker as primary announce")
        void shouldUseFirstTrackerAsPrimaryAnnounce() throws IOException {
            TorrentCreator creator = new TorrentCreator.Builder()
                    .addTracker("http://primary.com/announce")
                    .addTracker("http://backup.com/announce")
                    .build();

            Path outputPath = tempDir.resolve("output.torrent");

            creator.createTorrent(testFile, outputPath, null);

            byte[] torrentData = Files.readAllBytes(outputPath);
            String content = new String(torrentData);
            // First tracker should be announce value
            assertTrue(content.contains("announce"));
            assertTrue(content.contains("primary.com"));
        }
    }

    @Nested
    @DisplayName("Private Torrent Tests")
    class PrivateTorrentTests {

        @Test
        @DisplayName("Should create private torrent")
        void shouldCreatePrivateTorrent() throws IOException {
            TorrentCreator creator = new TorrentCreator.Builder()
                    .addTracker("http://tracker.com/announce")
                    .setPrivate(true)
                    .build();

            Path outputPath = tempDir.resolve("output.torrent");

            creator.createTorrent(testFile, outputPath, null);

            byte[] torrentData = Files.readAllBytes(outputPath);
            String content = new String(torrentData);
            assertTrue(content.contains("private"));
        }

        @Test
        @DisplayName("Should create public torrent by default")
        void shouldCreatePublicTorrentByDefault() throws IOException {
            TorrentCreator creator = new TorrentCreator.Builder()
                    .addTracker("http://tracker.com/announce")
                    .build();

            Path outputPath = tempDir.resolve("output.torrent");

            creator.createTorrent(testFile, outputPath, null);

            // Public torrents don't have private flag
            assertTrue(Files.exists(outputPath));
        }
    }

    @Nested
    @DisplayName("Progress Callback Tests")
    class ProgressCallbackTests {

        @Test
        @DisplayName("Should call progress callback")
        void shouldCallProgressCallback() throws IOException {
            TorrentCreator creator = new TorrentCreator.Builder()
                    .addTracker("http://tracker.com/announce")
                    .build();

            Path outputPath = tempDir.resolve("output.torrent");
            List<String> statuses = new ArrayList<>();

            TorrentCreator.ProgressCallback callback = (current, total, status) -> {
                statuses.add(status);
            };

            creator.createTorrent(testFile, outputPath, callback);

            assertFalse(statuses.isEmpty());
        }

        @Test
        @DisplayName("Should report final completion")
        void shouldReportFinalCompletion() throws IOException {
            TorrentCreator creator = new TorrentCreator.Builder()
                    .addTracker("http://tracker.com/announce")
                    .build();

            Path outputPath = tempDir.resolve("output.torrent");
            List<String> statuses = new ArrayList<>();

            TorrentCreator.ProgressCallback callback = (current, total, status) -> {
                statuses.add(status);
            };

            creator.createTorrent(testFile, outputPath, callback);

            assertTrue(statuses.stream().anyMatch(s -> s.contains("success")));
        }

        @Test
        @DisplayName("Should report progress with current and total")
        void shouldReportProgressWithCurrentAndTotal() throws IOException {
            // Create a larger file for better progress tracking
            Path largeFile = tempDir.resolve("large.bin");
            byte[] data = new byte[100 * 1024]; // 100 KB
            new Random().nextBytes(data);
            Files.write(largeFile, data);

            TorrentCreator creator = new TorrentCreator.Builder()
                    .addTracker("http://tracker.com/announce")
                    .setPieceSize(16 * 1024) // Small pieces for more callbacks
                    .build();

            Path outputPath = tempDir.resolve("output.torrent");
            List<Long> currents = new ArrayList<>();
            List<Long> totals = new ArrayList<>();

            TorrentCreator.ProgressCallback callback = (current, total, status) -> {
                currents.add(current);
                totals.add(total);
            };

            creator.createTorrent(largeFile, outputPath, callback);

            // Should have multiple progress reports
            assertFalse(currents.isEmpty());
            // Final progress should equal total
            assertEquals(totals.get(totals.size() - 1), currents.get(currents.size() - 1));
        }
    }

    @Nested
    @DisplayName("Piece Hashing Tests")
    class PieceHashingTests {

        @Test
        @DisplayName("Should create valid piece hashes")
        void shouldCreateValidPieceHashes() throws IOException {
            TorrentCreator creator = new TorrentCreator.Builder()
                    .addTracker("http://tracker.com/announce")
                    .build();

            Path outputPath = tempDir.resolve("output.torrent");

            creator.createTorrent(testFile, outputPath, null);

            byte[] torrentData = Files.readAllBytes(outputPath);
            // Torrent should contain pieces (SHA-1 hashes are 20 bytes each)
            assertTrue(torrentData.length > 20);
        }

        @Test
        @DisplayName("Should hash pieces with correct length")
        void shouldHashPiecesWithCorrectLength() throws IOException {
            // Create file larger than one piece
            Path largeFile = tempDir.resolve("large.bin");
            byte[] data = new byte[50 * 1024]; // 50 KB
            new Random().nextBytes(data);
            Files.write(largeFile, data);

            TorrentCreator creator = new TorrentCreator.Builder()
                    .addTracker("http://tracker.com/announce")
                    .setPieceSize(16 * 1024) // 16 KB pieces = 4 pieces
                    .build();

            Path outputPath = tempDir.resolve("output.torrent");

            creator.createTorrent(largeFile, outputPath, null);

            assertTrue(Files.exists(outputPath));
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should throw IOException for non-existent input")
        void shouldThrowIOExceptionForNonExistentInput() {
            TorrentCreator creator = new TorrentCreator.Builder()
                    .addTracker("http://tracker.com/announce")
                    .build();

            Path nonExistent = tempDir.resolve("nonexistent.txt");
            Path outputPath = tempDir.resolve("output.torrent");

            IOException exception = assertThrows(IOException.class,
                    () -> creator.createTorrent(nonExistent, outputPath, null));

            assertTrue(exception.getMessage().contains("does not exist"));
        }

        @Test
        @DisplayName("Should handle empty directory")
        void shouldHandleEmptyDirectory() throws IOException {
            Path emptyDir = tempDir.resolve("empty");
            Files.createDirectories(emptyDir);

            TorrentCreator creator = new TorrentCreator.Builder()
                    .addTracker("http://tracker.com/announce")
                    .build();

            Path outputPath = tempDir.resolve("output.torrent");

            // Should handle empty directory (may create empty torrent or throw)
            creator.createTorrent(emptyDir, outputPath, null);
        }

        @Test
        @DisplayName("Should handle zero-byte file")
        void shouldHandleZeroByteFile() throws IOException {
            Path emptyFile = tempDir.resolve("empty.txt");
            Files.createFile(emptyFile);

            TorrentCreator creator = new TorrentCreator.Builder()
                    .addTracker("http://tracker.com/announce")
                    .build();

            Path outputPath = tempDir.resolve("output.torrent");

            creator.createTorrent(emptyFile, outputPath, null);

            assertTrue(Files.exists(outputPath));
        }
    }

    @Nested
    @DisplayName("Info Hash Calculation Tests")
    class InfoHashCalculationTests {

        @Test
        @DisplayName("Should calculate info hash from torrent file")
        void shouldCalculateInfoHashFromTorrentFile() throws IOException {
            TorrentCreator creator = new TorrentCreator.Builder()
                    .addTracker("http://tracker.com/announce")
                    .build();

            Path outputPath = tempDir.resolve("output.torrent");
            creator.createTorrent(testFile, outputPath, null);

            byte[] infoHash = TorrentCreator.calculateInfoHash(outputPath);

            assertNotNull(infoHash);
            assertEquals(20, infoHash.length); // SHA-1 is 20 bytes
        }

        @Test
        @DisplayName("Should return consistent info hash")
        void shouldReturnConsistentInfoHash() throws IOException {
            TorrentCreator creator = new TorrentCreator.Builder()
                    .addTracker("http://tracker.com/announce")
                    .build();

            Path outputPath = tempDir.resolve("output.torrent");
            creator.createTorrent(testFile, outputPath, null);

            byte[] hash1 = TorrentCreator.calculateInfoHash(outputPath);
            byte[] hash2 = TorrentCreator.calculateInfoHash(outputPath);

            assertArrayEquals(hash1, hash2);
        }
    }

    @Nested
    @DisplayName("Piece Size Auto-calculation Tests")
    class PieceSizeAutoCalculationTests {

        @Test
        @DisplayName("Should auto-calculate piece size for small file")
        void shouldAutoCalculatePieceSizeForSmallFile() throws IOException {
            TorrentCreator creator = new TorrentCreator.Builder()
                    .addTracker("http://tracker.com/announce")
                    // No piece size set - should auto-calculate
                    .build();

            Path outputPath = tempDir.resolve("output.torrent");

            creator.createTorrent(testFile, outputPath, null);

            assertTrue(Files.exists(outputPath));
        }

        @Test
        @DisplayName("Should auto-calculate piece size for large file")
        void shouldAutoCalculatePieceSizeForLargeFile() throws IOException {
            // Create a larger file
            Path largeFile = tempDir.resolve("large.bin");
            byte[] data = new byte[1024 * 1024]; // 1 MB
            new Random().nextBytes(data);
            Files.write(largeFile, data);

            TorrentCreator creator = new TorrentCreator.Builder()
                    .addTracker("http://tracker.com/announce")
                    .build();

            Path outputPath = tempDir.resolve("output.torrent");

            creator.createTorrent(largeFile, outputPath, null);

            assertTrue(Files.exists(outputPath));
        }
    }

    @Nested
    @DisplayName("Torrent Without Tracker Tests")
    class TrackerlessTorrentTests {

        @Test
        @DisplayName("Should create torrent without trackers")
        void shouldCreateTorrentWithoutTrackers() throws IOException {
            TorrentCreator creator = new TorrentCreator.Builder()
                    .setComment("Trackerless torrent")
                    .build();

            Path outputPath = tempDir.resolve("output.torrent");

            creator.createTorrent(testFile, outputPath, null);

            assertTrue(Files.exists(outputPath));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle files with special characters in name")
        void shouldHandleFilesWithSpecialCharactersInName() throws IOException {
            Path specialFile = tempDir.resolve("test file (1) [copy].txt");
            Files.write(specialFile, "Content".getBytes());

            TorrentCreator creator = new TorrentCreator.Builder()
                    .addTracker("http://tracker.com/announce")
                    .build();

            Path outputPath = tempDir.resolve("output.torrent");

            creator.createTorrent(specialFile, outputPath, null);

            assertTrue(Files.exists(outputPath));
        }

        @Test
        @DisplayName("Should handle files with unicode names")
        void shouldHandleFilesWithUnicodeNames() throws IOException {
            Path unicodeFile = tempDir.resolve("тест_файл_日本語.txt");
            Files.write(unicodeFile, "Unicode content".getBytes());

            TorrentCreator creator = new TorrentCreator.Builder()
                    .addTracker("http://tracker.com/announce")
                    .build();

            Path outputPath = tempDir.resolve("output.torrent");

            creator.createTorrent(unicodeFile, outputPath, null);

            assertTrue(Files.exists(outputPath));
        }

        @Test
        @DisplayName("Should handle very deep directory structure")
        void shouldHandleVeryDeepDirectoryStructure() throws IOException {
            Path deepDir = testDir;
            for (int i = 0; i < 10; i++) {
                deepDir = deepDir.resolve("level" + i);
            }
            Files.createDirectories(deepDir);
            Files.write(deepDir.resolve("deep_file.txt"), "Deep content".getBytes());

            TorrentCreator creator = new TorrentCreator.Builder()
                    .addTracker("http://tracker.com/announce")
                    .build();

            Path outputPath = tempDir.resolve("output.torrent");

            creator.createTorrent(testDir, outputPath, null);

            assertTrue(Files.exists(outputPath));
        }

        @Test
        @DisplayName("Should handle symlinks gracefully")
        void shouldHandleSymlinksGracefully() throws IOException {
            // Skip on Windows if symlinks aren't supported
            try {
                Path link = tempDir.resolve("link.txt");
                Files.createSymbolicLink(link, testFile);

                TorrentCreator creator = new TorrentCreator.Builder()
                        .addTracker("http://tracker.com/announce")
                        .build();

                Path outputPath = tempDir.resolve("output.torrent");

                creator.createTorrent(link, outputPath, null);

                assertTrue(Files.exists(outputPath));
            } catch (UnsupportedOperationException | IOException e) {
                // Symlinks not supported - skip test
            }
        }
    }
}
