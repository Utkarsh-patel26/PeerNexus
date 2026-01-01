package com.example.jtorrent.integration;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

/**
 * Base class for integration tests.
 * Phase 1.3.2: Integration Test Infrastructure
 * 
 * These tests require the Docker test environment to be running:
 * cd test-integration && ./run-integration-tests.sh
 * 
 * Run with: mvn test -Dtest.integration=true -Dtest=*IntegrationTest
 */
public abstract class BaseIntegrationTest {

    protected static final String TRACKER_URL = "http://localhost:6969/announce";
    protected static final InetSocketAddress TRACKER_ADDRESS = new InetSocketAddress("localhost", 6969);
    protected static final InetSocketAddress DHT_NODE = new InetSocketAddress("localhost", 6881);

    protected static final Path TEST_DATA_DIR = Paths.get("test-integration/test-data");
    protected static final Path TEST_OUTPUT_DIR = Paths.get("test-integration/test-output");
    protected static final Path TEST_TORRENT_DIR = TEST_DATA_DIR.resolve("torrents");
    protected static final Path SEED_DATA_DIR = TEST_DATA_DIR.resolve("seed-data");

    protected static final int DEFAULT_TIMEOUT_SECONDS = 120;

    protected Path testDownloadDir;

    @BeforeAll
    static void checkEnvironment() throws Exception {
        // Skip integration tests unless explicitly enabled
        String integrationEnabled = System.getProperty("test.integration", "false");
        Assumptions.assumeTrue(
                "true".equalsIgnoreCase(integrationEnabled),
                "Integration tests disabled. Run with -Dtest.integration=true");

        // Verify test environment is available
        Assumptions.assumeTrue(
                Files.exists(TEST_DATA_DIR),
                "Test environment not set up. Run: cd test-integration && ./run-integration-tests.sh");

        // Check tracker is reachable
        try {
            java.net.Socket socket = new java.net.Socket();
            socket.connect(TRACKER_ADDRESS, 5000);
            socket.close();
        } catch (Exception e) {
            Assumptions.abort(
                    "Test tracker not reachable at " + TRACKER_ADDRESS +
                            ". Ensure Docker containers are running.");
        }
    }

    @BeforeEach
    void setUpTest(TestInfo testInfo) throws Exception {
        // Create unique download directory for each test
        String testName = testInfo.getDisplayName().replaceAll("[^a-zA-Z0-9]", "_");
        testDownloadDir = TEST_OUTPUT_DIR.resolve(testName + "_" + System.currentTimeMillis());
        Files.createDirectories(testDownloadDir);

        System.out.println("Test: " + testInfo.getDisplayName());
        System.out.println("Download dir: " + testDownloadDir);
    }

    @AfterEach
    void tearDownTest() throws Exception {
        // Clean up download directory if test passed
        // (keep on failure for debugging)
    }

    /**
     * Wait for a condition to become true.
     *
     * @param condition      condition to check
     * @param timeoutSeconds maximum time to wait
     * @param message        message for timeout error
     */
    protected void waitFor(
            java.util.concurrent.Callable<Boolean> condition,
            int timeoutSeconds,
            String message) throws Exception {

        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds);

        while (System.currentTimeMillis() < deadline) {
            if (condition.call()) {
                return;
            }
            Thread.sleep(1000);
        }

        throw new AssertionError("Timeout waiting for: " + message);
    }

    /**
     * Calculate SHA-256 checksum of a file.
     */
    protected String calculateSha256(Path file) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] fileBytes = Files.readAllBytes(file);
        byte[] hashBytes = digest.digest(fileBytes);

        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Get expected checksum from test data.
     */
    protected String getExpectedChecksum(String filename) throws Exception {
        Path checksumFile = TEST_DATA_DIR.resolve(filename + ".sha256");
        if (Files.exists(checksumFile)) {
            return Files.readString(checksumFile).trim();
        }
        // Calculate from source file
        return calculateSha256(SEED_DATA_DIR.resolve(filename));
    }
}
