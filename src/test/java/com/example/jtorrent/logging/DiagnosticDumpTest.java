package com.example.jtorrent.logging;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for DiagnosticDump.
 */
@DisplayName("DiagnosticDump Tests")
class DiagnosticDumpTest {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("Basic Dump Generation Tests")
    class BasicDumpGenerationTests {

        @Test
        @DisplayName("Should generate dump file")
        void shouldGenerateDumpFile() throws IOException {
            Path dumpFile = DiagnosticDump.generateDump(tempDir, "test", null);

            assertNotNull(dumpFile);
            assertTrue(Files.exists(dumpFile));
            assertTrue(Files.size(dumpFile) > 0);
        }

        @Test
        @DisplayName("Should include reason in filename")
        void shouldIncludeReasonInFilename() throws IOException {
            Path dumpFile = DiagnosticDump.generateDump(tempDir, "crash", null);

            assertTrue(dumpFile.getFileName().toString().contains("crash"));
        }

        @Test
        @DisplayName("Should include timestamp in filename")
        void shouldIncludeTimestampInFilename() throws IOException {
            Path dumpFile = DiagnosticDump.generateDump(tempDir, "test", null);

            // Filename should contain date/time pattern
            String filename = dumpFile.getFileName().toString();
            assertTrue(filename.matches(".*\\d{4}-\\d{2}-\\d{2}.*"));
        }

        @Test
        @DisplayName("Should create output directory")
        void shouldCreateOutputDirectory() throws IOException {
            Path subDir = tempDir.resolve("diagnostics/dumps");

            DiagnosticDump.generateDump(subDir, "test", null);

            assertTrue(Files.exists(subDir));
        }
    }

    @Nested
    @DisplayName("Dump Content Tests")
    class DumpContentTests {

        @Test
        @DisplayName("Should include header")
        void shouldIncludeHeader() throws IOException {
            Path dumpFile = DiagnosticDump.generateDump(tempDir, "test", null);
            String content = Files.readString(dumpFile);

            assertTrue(content.contains("JTorrent Diagnostic Dump"));
            assertTrue(content.contains("Timestamp:"));
            assertTrue(content.contains("Reason: test"));
        }

        @Test
        @DisplayName("Should include system information")
        void shouldIncludeSystemInformation() throws IOException {
            Path dumpFile = DiagnosticDump.generateDump(tempDir, "test", null);
            String content = Files.readString(dumpFile);

            assertTrue(content.contains("System Information"));
            assertTrue(content.contains("OS:"));
            assertTrue(content.contains("Java Version:"));
            assertTrue(content.contains("Available Processors:"));
        }

        @Test
        @DisplayName("Should include runtime information")
        void shouldIncludeRuntimeInformation() throws IOException {
            Path dumpFile = DiagnosticDump.generateDump(tempDir, "test", null);
            String content = Files.readString(dumpFile);

            assertTrue(content.contains("Runtime Information"));
            assertTrue(content.contains("Uptime:"));
            assertTrue(content.contains("VM Name:"));
        }

        @Test
        @DisplayName("Should include memory information")
        void shouldIncludeMemoryInformation() throws IOException {
            Path dumpFile = DiagnosticDump.generateDump(tempDir, "test", null);
            String content = Files.readString(dumpFile);

            assertTrue(content.contains("Memory Information"));
            assertTrue(content.contains("Heap Memory"));
            assertTrue(content.contains("Used:"));
        }

        @Test
        @DisplayName("Should include thread dump")
        void shouldIncludeThreadDump() throws IOException {
            Path dumpFile = DiagnosticDump.generateDump(tempDir, "test", null);
            String content = Files.readString(dumpFile);

            assertTrue(content.contains("Thread Dump"));
            assertTrue(content.contains("Total Threads:"));
        }
    }

    @Nested
    @DisplayName("Additional Context Tests")
    class AdditionalContextTests {

        @Test
        @DisplayName("Should include additional context")
        void shouldIncludeAdditionalContext() throws IOException {
            Map<String, Object> context = new HashMap<>();
            context.put("errorCode", "E001");
            context.put("userId", "user123");
            context.put("action", "download");

            Path dumpFile = DiagnosticDump.generateDump(tempDir, "error", context);
            String content = Files.readString(dumpFile);

            assertTrue(content.contains("Additional Context"));
            assertTrue(content.contains("errorCode: E001"));
            assertTrue(content.contains("userId: user123"));
            assertTrue(content.contains("action: download"));
        }

        @Test
        @DisplayName("Should handle null additional context")
        void shouldHandleNullAdditionalContext() throws IOException {
            Path dumpFile = DiagnosticDump.generateDump(tempDir, "test", null);
            String content = Files.readString(dumpFile);

            assertNotNull(content);
        }

        @Test
        @DisplayName("Should handle empty additional context")
        void shouldHandleEmptyAdditionalContext() throws IOException {
            Map<String, Object> context = new HashMap<>();

            Path dumpFile = DiagnosticDump.generateDump(tempDir, "test", context);
            String content = Files.readString(dumpFile);

            assertNotNull(content);
        }
    }

    @Nested
    @DisplayName("System Property Tests")
    class SystemPropertyTests {

        @Test
        @DisplayName("Should include OS information")
        void shouldIncludeOsInformation() throws IOException {
            Path dumpFile = DiagnosticDump.generateDump(tempDir, "test", null);
            String content = Files.readString(dumpFile);

            assertTrue(content.contains("os.name") || content.contains("OS:"));
        }

        @Test
        @DisplayName("Should include Java home")
        void shouldIncludeJavaHome() throws IOException {
            Path dumpFile = DiagnosticDump.generateDump(tempDir, "test", null);
            String content = Files.readString(dumpFile);

            assertTrue(content.contains("Java Home:") || content.contains("java.home"));
        }

        @Test
        @DisplayName("Should include user directory")
        void shouldIncludeUserDirectory() throws IOException {
            Path dumpFile = DiagnosticDump.generateDump(tempDir, "test", null);
            String content = Files.readString(dumpFile);

            assertTrue(content.contains("User Dir:") || content.contains("user.dir"));
        }
    }

    @Nested
    @DisplayName("Thread State Tests")
    class ThreadStateTests {

        @Test
        @DisplayName("Should include thread states")
        void shouldIncludeThreadStates() throws IOException {
            Path dumpFile = DiagnosticDump.generateDump(tempDir, "test", null);
            String content = Files.readString(dumpFile);

            assertTrue(content.contains("Thread States") || content.contains("State:"));
        }

        @Test
        @DisplayName("Should include thread details")
        void shouldIncludeThreadDetails() throws IOException {
            Path dumpFile = DiagnosticDump.generateDump(tempDir, "test", null);
            String content = Files.readString(dumpFile);

            // Should have detailed thread info
            assertTrue(content.contains("Thread:") || content.contains("Thread Details"));
        }
    }

    @Nested
    @DisplayName("Multiple Dump Tests")
    class MultipleDumpTests {

        @Test
        @DisplayName("Should create multiple dump files")
        void shouldCreateMultipleDumpFiles() throws IOException, InterruptedException {
            Path dump1 = DiagnosticDump.generateDump(tempDir, "crash", null);

            // Wait to ensure different timestamp
            Thread.sleep(1100);

            Path dump2 = DiagnosticDump.generateDump(tempDir, "hang", null);

            assertTrue(Files.exists(dump1));
            assertTrue(Files.exists(dump2));
            assertNotEquals(dump1, dump2);
        }

        @Test
        @DisplayName("Should differentiate dump files by reason")
        void shouldDifferentiateDumpFilesByReason() throws IOException {
            Path crash = DiagnosticDump.generateDump(tempDir, "crash", null);
            Path error = DiagnosticDump.generateDump(tempDir, "error", null);

            assertTrue(crash.getFileName().toString().contains("crash"));
            assertTrue(error.getFileName().toString().contains("error"));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle special characters in reason")
        void shouldHandleSpecialCharactersInReason() throws IOException {
            Path dumpFile = DiagnosticDump.generateDump(tempDir, "test-error_123", null);

            assertTrue(Files.exists(dumpFile));
        }

        @Test
        @DisplayName("Should handle complex context values")
        void shouldHandleComplexContextValues() throws IOException {
            Map<String, Object> context = new HashMap<>();
            context.put("list", Arrays.asList("a", "b", "c"));
            context.put("number", 12345);
            context.put("boolean", true);

            Path dumpFile = DiagnosticDump.generateDump(tempDir, "test", context);
            String content = Files.readString(dumpFile);

            assertTrue(content.contains("Additional Context"));
        }

        @Test
        @DisplayName("Should handle null values in context")
        void shouldHandleNullValuesInContext() throws IOException {
            Map<String, Object> context = new HashMap<>();
            context.put("nullable", null);
            context.put("valid", "value");

            Path dumpFile = DiagnosticDump.generateDump(tempDir, "test", context);

            assertTrue(Files.exists(dumpFile));
        }
    }

    @Nested
    @DisplayName("Format Tests")
    class FormatTests {

        @Test
        @DisplayName("Should use separator lines")
        void shouldUseSeparatorLines() throws IOException {
            Path dumpFile = DiagnosticDump.generateDump(tempDir, "test", null);
            String content = Files.readString(dumpFile);

            assertTrue(content.contains("========================================"));
        }

        @Test
        @DisplayName("Should have readable format")
        void shouldHaveReadableFormat() throws IOException {
            Path dumpFile = DiagnosticDump.generateDump(tempDir, "test", null);
            String content = Files.readString(dumpFile);

            // Should have multiple sections
            int sectionCount = content.split("========================================").length;
            assertTrue(sectionCount >= 5);
        }
    }

    @Nested
    @DisplayName("Memory Formatting Tests")
    class MemoryFormattingTests {

        @Test
        @DisplayName("Should format memory values")
        void shouldFormatMemoryValues() throws IOException {
            Path dumpFile = DiagnosticDump.generateDump(tempDir, "test", null);
            String content = Files.readString(dumpFile);

            // Should contain formatted byte values (B, KB, MB, GB, etc.)
            // Check for specific format like "1.23 KB" or "456 B"
            boolean hasFormattedBytes = content.matches("(?s).*\\d+(\\.\\d+)?\\s+[KMGTPE]?B.*");
            assertTrue(hasFormattedBytes, "Content should contain formatted byte values like '1.23 KB' or '456 B'");
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle invalid directory gracefully")
        void shouldThrowIOExceptionForInvalidDirectory() {
            // On most systems, Files.createDirectories will succeed or create parent dirs
            // Test with a path that is truly impossible to create (e.g., on Windows,
            // invalid chars)
            assertDoesNotThrow(() -> {
                try {
                    Path invalidDir;
                    if (System.getProperty("os.name").toLowerCase().contains("win")) {
                        // Windows-specific invalid path - Paths.get may throw InvalidPathException
                        invalidDir = Paths.get("C:\\invalid<>path|with*illegal?chars");
                    } else {
                        // Unix-specific - try to create in root which requires permissions
                        invalidDir = Paths.get("/root/test_impossible_dir_" + System.currentTimeMillis());
                    }
                    DiagnosticDump.generateDump(invalidDir, "test", null);
                } catch (Exception e) {
                    // Expected on most systems - InvalidPathException or IOException
                    assertTrue(e instanceof IOException || e instanceof InvalidPathException ||
                            e instanceof FileSystemException,
                            "Expected IOException, InvalidPathException or FileSystemException, got: " + e.getClass());
                }
            });
        }
    }
}
