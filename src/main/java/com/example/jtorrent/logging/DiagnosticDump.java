package com.example.jtorrent.logging;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Diagnostic dump utility for crash/error analysis.
 * Phase 1.4.1: Logging & Diagnostics
 */
public class DiagnosticDump {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final Logger logger = Logger.getLogger(DiagnosticDump.class);

    /**
     * Generate a diagnostic dump file.
     *
     * @param outputDir         directory to write dump file
     * @param reason            reason for dump (e.g., "crash", "user-requested")
     * @param additionalContext additional context to include
     * @return path to dump file
     * @throws IOException if dump fails
     */
    public static Path generateDump(
            Path outputDir,
            String reason,
            Map<String, Object> additionalContext) throws IOException {

        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String filename = String.format("diagnostic-dump_%s_%s.txt", reason, timestamp);
        Path dumpFile = outputDir.resolve(filename);

        StringBuilder dump = new StringBuilder();

        // Header
        dump.append("========================================\n");
        dump.append("JTorrent Diagnostic Dump\n");
        dump.append("========================================\n");
        dump.append("Timestamp: ").append(LocalDateTime.now()).append("\n");
        dump.append("Reason: ").append(reason).append("\n\n");

        // System Information
        dump.append("========================================\n");
        dump.append("System Information\n");
        dump.append("========================================\n");
        appendSystemInfo(dump);
        dump.append("\n");

        // Runtime Information
        dump.append("========================================\n");
        dump.append("Runtime Information\n");
        dump.append("========================================\n");
        appendRuntimeInfo(dump);
        dump.append("\n");

        // Memory Information
        dump.append("========================================\n");
        dump.append("Memory Information\n");
        dump.append("========================================\n");
        appendMemoryInfo(dump);
        dump.append("\n");

        // Thread Dump
        dump.append("========================================\n");
        dump.append("Thread Dump\n");
        dump.append("========================================\n");
        appendThreadDump(dump);
        dump.append("\n");

        // Additional Context
        if (additionalContext != null && !additionalContext.isEmpty()) {
            dump.append("========================================\n");
            dump.append("Additional Context\n");
            dump.append("========================================\n");
            for (Map.Entry<String, Object> entry : additionalContext.entrySet()) {
                dump.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
            dump.append("\n");
        }

        // Write to file
        Files.createDirectories(outputDir);
        Files.writeString(dumpFile, dump.toString(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

        logger.info("Diagnostic dump created: %s", dumpFile);
        return dumpFile;
    }

    /**
     * Append system information.
     */
    private static void appendSystemInfo(StringBuilder dump) {
        dump.append("OS: ").append(System.getProperty("os.name")).append(" ")
                .append(System.getProperty("os.version")).append("\n");
        dump.append("Arch: ").append(System.getProperty("os.arch")).append("\n");
        dump.append("Java Version: ").append(System.getProperty("java.version")).append("\n");
        dump.append("Java Vendor: ").append(System.getProperty("java.vendor")).append("\n");
        dump.append("Java Home: ").append(System.getProperty("java.home")).append("\n");
        dump.append("User Dir: ").append(System.getProperty("user.dir")).append("\n");
        dump.append("Available Processors: ").append(Runtime.getRuntime().availableProcessors())
                .append("\n");
    }

    /**
     * Append runtime information.
     */
    private static void appendRuntimeInfo(StringBuilder dump) {
        RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
        dump.append("Uptime: ").append(runtimeMxBean.getUptime()).append(" ms\n");
        dump.append("Start Time: ").append(runtimeMxBean.getStartTime()).append("\n");
        dump.append("VM Name: ").append(runtimeMxBean.getVmName()).append("\n");
        dump.append("VM Vendor: ").append(runtimeMxBean.getVmVendor()).append("\n");
        dump.append("VM Version: ").append(runtimeMxBean.getVmVersion()).append("\n");

        // JVM Arguments
        dump.append("JVM Arguments:\n");
        for (String arg : runtimeMxBean.getInputArguments()) {
            dump.append("  ").append(arg).append("\n");
        }

        // System Properties
        dump.append("System Properties:\n");
        System.getProperties().forEach((key, value) -> {
            dump.append("  ").append(key).append("=").append(value).append("\n");
        });
    }

    /**
     * Append memory information.
     */
    private static void appendMemoryInfo(StringBuilder dump) {
        MemoryMXBean memoryMxBean = ManagementFactory.getMemoryMXBean();
        Runtime runtime = Runtime.getRuntime();

        dump.append("Heap Memory Usage:\n");
        dump.append("  Used: ").append(formatBytes(memoryMxBean.getHeapMemoryUsage().getUsed()))
                .append("\n");
        dump.append("  Committed: ")
                .append(formatBytes(memoryMxBean.getHeapMemoryUsage().getCommitted())).append("\n");
        dump.append("  Max: ").append(formatBytes(memoryMxBean.getHeapMemoryUsage().getMax()))
                .append("\n");

        dump.append("Non-Heap Memory Usage:\n");
        dump.append("  Used: ").append(formatBytes(memoryMxBean.getNonHeapMemoryUsage().getUsed()))
                .append("\n");
        dump.append("  Committed: ")
                .append(formatBytes(memoryMxBean.getNonHeapMemoryUsage().getCommitted())).append("\n");

        dump.append("Runtime Memory:\n");
        dump.append("  Total: ").append(formatBytes(runtime.totalMemory())).append("\n");
        dump.append("  Free: ").append(formatBytes(runtime.freeMemory())).append("\n");
        dump.append("  Max: ").append(formatBytes(runtime.maxMemory())).append("\n");
        dump.append("  Used: ")
                .append(formatBytes(runtime.totalMemory() - runtime.freeMemory())).append("\n");
    }

    /**
     * Append thread dump.
     */
    private static void appendThreadDump(StringBuilder dump) {
        ThreadMXBean threadMxBean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] threadInfos = threadMxBean.dumpAllThreads(true, true);

        dump.append("Total Threads: ").append(threadMxBean.getThreadCount()).append("\n");
        dump.append("Daemon Threads: ").append(threadMxBean.getDaemonThreadCount()).append("\n");
        dump.append("Peak Threads: ").append(threadMxBean.getPeakThreadCount()).append("\n");
        dump.append("Total Started Threads: ").append(threadMxBean.getTotalStartedThreadCount())
                .append("\n\n");

        // Group threads by state
        Map<Thread.State, Long> stateCount = java.util.Arrays.stream(threadInfos)
                .collect(Collectors.groupingBy(ThreadInfo::getThreadState, Collectors.counting()));
        dump.append("Thread States:\n");
        for (Map.Entry<Thread.State, Long> entry : stateCount.entrySet()) {
            dump.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        dump.append("\n");

        // Detailed thread information
        dump.append("Thread Details:\n");
        for (ThreadInfo threadInfo : threadInfos) {
            dump.append("----------------------------------------\n");
            dump.append("Thread: ").append(threadInfo.getThreadName()).append(" (ID: ")
                    .append(threadInfo.getThreadId()).append(")\n");
            dump.append("State: ").append(threadInfo.getThreadState()).append("\n");

            if (threadInfo.getLockName() != null) {
                dump.append("Waiting on: ").append(threadInfo.getLockName()).append("\n");
            }
            if (threadInfo.getLockOwnerName() != null) {
                dump.append("Lock Owner: ").append(threadInfo.getLockOwnerName()).append(" (ID: ")
                        .append(threadInfo.getLockOwnerId()).append(")\n");
            }

            dump.append("Stack Trace:\n");
            for (StackTraceElement stackTrace : threadInfo.getStackTrace()) {
                dump.append("  at ").append(stackTrace).append("\n");
            }
            dump.append("\n");
        }
    }

    /**
     * Format bytes to human-readable string.
     */
    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char unit = "KMGTPE".charAt(exp - 1);
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), unit);
    }

    /**
     * Install an uncaught exception handler that generates diagnostic dumps.
     *
     * @param dumpDir directory to write dumps
     */
    public static void installCrashHandler(Path dumpDir) {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            logger.error("Uncaught exception in thread %s", throwable, thread.getName());

            try {
                Map<String, Object> context = Map.of(
                        "thread", thread.getName(),
                        "exception", throwable.getClass().getName(),
                        "message", throwable.getMessage() != null ? throwable.getMessage() : "null");

                Path dumpFile = generateDump(dumpDir, "crash", context);
                System.err.println("Diagnostic dump created: " + dumpFile);
            } catch (Exception e) {
                logger.error("Failed to generate diagnostic dump", e);
            }

            // Re-throw to allow normal exception handling
            if (throwable instanceof RuntimeException) {
                throw (RuntimeException) throwable;
            } else if (throwable instanceof Error) {
                throw (Error) throwable;
            }
        });

        logger.info("Crash handler installed, dumps will be written to: %s", dumpDir);
    }
}
