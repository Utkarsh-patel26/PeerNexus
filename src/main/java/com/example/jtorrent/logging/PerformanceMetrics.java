package com.example.jtorrent.logging;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Performance metrics collection and periodic logging.
 * Phase 1.4.1: Logging & Diagnostics
 */
public class PerformanceMetrics implements AutoCloseable {

    private static final Logger logger = Logger.getLogger(PerformanceMetrics.class);

    private final Map<String, LongAdder> counters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> gauges = new ConcurrentHashMap<>();
    private final Map<String, Histogram> histograms = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler;
    private final long logIntervalSeconds;

    /**
     * Create a performance metrics collector.
     *
     * @param logIntervalSeconds interval between periodic log outputs (0 to
     *                           disable)
     */
    public PerformanceMetrics(long logIntervalSeconds) {
        this.logIntervalSeconds = logIntervalSeconds;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PerformanceMetrics-Logger");
            t.setDaemon(true);
            return t;
        });

        if (logIntervalSeconds > 0) {
            scheduler.scheduleAtFixedRate(
                    this::logMetrics,
                    logIntervalSeconds,
                    logIntervalSeconds,
                    TimeUnit.SECONDS);
        }
    }

    /**
     * Create a performance metrics collector with 60-second logging interval.
     */
    public PerformanceMetrics() {
        this(60);
    }

    /**
     * Increment a counter.
     *
     * @param name counter name
     */
    public void incrementCounter(String name) {
        incrementCounter(name, 1);
    }

    /**
     * Increment a counter by a specific amount.
     *
     * @param name   counter name
     * @param amount amount to add
     */
    public void incrementCounter(String name, long amount) {
        counters.computeIfAbsent(name, k -> new LongAdder()).add(amount);
    }

    /**
     * Set a gauge value.
     *
     * @param name  gauge name
     * @param value gauge value
     */
    public void setGauge(String name, long value) {
        gauges.computeIfAbsent(name, k -> new AtomicLong()).set(value);
    }

    /**
     * Update gauge by adding to current value.
     *
     * @param name  gauge name
     * @param delta delta to add (can be negative)
     */
    public void updateGauge(String name, long delta) {
        gauges.computeIfAbsent(name, k -> new AtomicLong()).addAndGet(delta);
    }

    /**
     * Record a duration in a histogram.
     *
     * @param name       histogram name
     * @param durationMs duration in milliseconds
     */
    public void recordDuration(String name, long durationMs) {
        histograms.computeIfAbsent(name, k -> new Histogram()).record(durationMs);
    }

    /**
     * Get counter value.
     *
     * @param name counter name
     * @return counter value or 0
     */
    public long getCounter(String name) {
        LongAdder counter = counters.get(name);
        return counter != null ? counter.sum() : 0;
    }

    /**
     * Get gauge value.
     *
     * @param name gauge name
     * @return gauge value or 0
     */
    public long getGauge(String name) {
        AtomicLong gauge = gauges.get(name);
        return gauge != null ? gauge.get() : 0;
    }

    /**
     * Get histogram statistics.
     *
     * @param name histogram name
     * @return histogram or null
     */
    public Histogram getHistogram(String name) {
        return histograms.get(name);
    }

    /**
     * Log all metrics.
     */
    public void logMetrics() {
        StringBuilder sb = new StringBuilder();
        sb.append("Performance Metrics:");

        // Log counters
        if (!counters.isEmpty()) {
            sb.append("\n  Counters:");
            counters.forEach((name, value) -> sb.append("\n    ").append(name).append(": ").append(value.sum()));
        }

        // Log gauges
        if (!gauges.isEmpty()) {
            sb.append("\n  Gauges:");
            gauges.forEach((name, value) -> sb.append("\n    ").append(name).append(": ").append(value.get()));
        }

        // Log histograms
        if (!histograms.isEmpty()) {
            sb.append("\n  Histograms:");
            histograms.forEach((name, histogram) -> {
                sb.append("\n    ").append(name).append(": ");
                sb.append("count=").append(histogram.getCount());
                sb.append(", min=").append(histogram.getMin());
                sb.append(", max=").append(histogram.getMax());
                sb.append(", avg=").append(String.format("%.2f", histogram.getAverage()));
                sb.append(", p50=").append(histogram.getPercentile(50));
                sb.append(", p99=").append(histogram.getPercentile(99));
            });
        }

        logger.info(sb.toString());
    }

    /**
     * Reset all metrics.
     */
    public void reset() {
        counters.clear();
        gauges.clear();
        histograms.clear();
    }

    @Override
    public void close() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Simple histogram for tracking distributions.
     */
    public static class Histogram {
        private final LongAdder count = new LongAdder();
        private final LongAdder sum = new LongAdder();
        private final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong max = new AtomicLong(Long.MIN_VALUE);
        private final long[] buckets = new long[100]; // For percentile calculation
        private final Object bucketLock = new Object();

        /**
         * Record a value.
         *
         * @param value value to record
         */
        public void record(long value) {
            count.increment();
            sum.add(value);

            // Update min
            long currentMin;
            do {
                currentMin = min.get();
                if (value >= currentMin) {
                    break;
                }
            } while (!min.compareAndSet(currentMin, value));

            // Update max
            long currentMax;
            do {
                currentMax = max.get();
                if (value <= currentMax) {
                    break;
                }
            } while (!max.compareAndSet(currentMax, value));

            // Update bucket for percentile (values 0-99 go into respective buckets, >=100
            // go into last)
            int bucket = (int) Math.min(value, buckets.length - 1);
            synchronized (bucketLock) {
                buckets[bucket]++;
            }
        }

        public long getCount() {
            return count.sum();
        }

        public long getMin() {
            long v = min.get();
            return v == Long.MAX_VALUE ? 0 : v;
        }

        public long getMax() {
            long v = max.get();
            return v == Long.MIN_VALUE ? 0 : v;
        }

        public double getAverage() {
            long c = count.sum();
            return c > 0 ? (double) sum.sum() / c : 0.0;
        }

        /**
         * Get percentile value.
         *
         * @param percentile percentile (0-100)
         * @return approximate percentile value
         */
        public long getPercentile(int percentile) {
            if (percentile < 0 || percentile > 100) {
                throw new IllegalArgumentException("Percentile must be 0-100");
            }

            long totalCount = count.sum();
            if (totalCount == 0) {
                return 0;
            }

            long targetCount = (long) (totalCount * percentile / 100.0);
            long cumulative = 0;

            synchronized (bucketLock) {
                for (int i = 0; i < buckets.length; i++) {
                    cumulative += buckets[i];
                    if (cumulative >= targetCount) {
                        return i;
                    }
                }
            }

            return buckets.length - 1;
        }
    }

    // ========================================
    // Predefined metric names for consistency
    // ========================================

    public static final String PIECES_DOWNLOADED = "pieces.downloaded";
    public static final String PIECES_VERIFIED = "pieces.verified";
    public static final String PIECES_FAILED = "pieces.failed";
    public static final String BYTES_DOWNLOADED = "bytes.downloaded";
    public static final String BYTES_UPLOADED = "bytes.uploaded";
    public static final String PEERS_CONNECTED = "peers.connected";
    public static final String PEERS_DISCONNECTED = "peers.disconnected";
    public static final String TRACKER_ANNOUNCES = "tracker.announces";
    public static final String DHT_QUERIES = "dht.queries";
    public static final String DISK_READS = "disk.reads";
    public static final String DISK_WRITES = "disk.writes";
    public static final String PIECE_DOWNLOAD_TIME_MS = "piece.download.time.ms";
    public static final String TRACKER_RESPONSE_TIME_MS = "tracker.response.time.ms";

    // Gauges
    public static final String ACTIVE_PEERS = "active.peers";
    public static final String ACTIVE_TORRENTS = "active.torrents";
    public static final String DOWNLOAD_RATE_BPS = "download.rate.bps";
    public static final String UPLOAD_RATE_BPS = "upload.rate.bps";
    public static final String DISK_CACHE_SIZE = "disk.cache.size";
    public static final String PENDING_REQUESTS = "pending.requests";
}
