package com.example.jtorrent.core;

import com.example.jtorrent.logging.Logger;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Rate limiter for controlling upload and download speeds.
 * 
 * Uses a token bucket algorithm for smooth rate limiting.
 * Supports both global limits and per-torrent limits.
 */
public class RateLimiter {

    private static final Logger logger = Logger.getLogger(RateLimiter.class);

    // Maximum bytes per second (0 = unlimited)
    private volatile long maxBytesPerSecond;

    // Token bucket
    private long availableTokens;
    private long lastRefillTime;

    // Statistics
    private final AtomicLong totalBytes = new AtomicLong(0);
    private final AtomicLong bytesThisSecond = new AtomicLong(0);
    private volatile long lastSecondBytes = 0;
    private long lastStatsReset = System.currentTimeMillis();

    // Lock for token bucket operations
    private final ReentrantLock lock = new ReentrantLock();

    // Bucket size as multiple of rate (allows burst)
    private static final double BUCKET_SIZE_MULTIPLIER = 1.5;

    // Minimum sleep time in milliseconds
    private static final long MIN_SLEEP_MS = 5;

    /**
     * Create a rate limiter.
     * 
     * @param maxBytesPerSecond maximum bytes per second (0 = unlimited)
     */
    public RateLimiter(long maxBytesPerSecond) {
        this.maxBytesPerSecond = maxBytesPerSecond;
        this.availableTokens = maxBytesPerSecond > 0 ? maxBytesPerSecond : 0;
        this.lastRefillTime = System.nanoTime();
    }

    /**
     * Create an unlimited rate limiter.
     */
    public RateLimiter() {
        this(0);
    }

    /**
     * Set the rate limit.
     * 
     * @param maxBytesPerSecond maximum bytes per second (0 = unlimited)
     */
    public void setLimit(long maxBytesPerSecond) {
        lock.lock();
        try {
            this.maxBytesPerSecond = maxBytesPerSecond;
            if (maxBytesPerSecond > 0) {
                // Reset bucket
                this.availableTokens = maxBytesPerSecond;
                this.lastRefillTime = System.nanoTime();
            }
        } finally {
            lock.unlock();
        }
        logger.debug("Rate limit set to: %d B/s", maxBytesPerSecond);
    }

    /**
     * Get the current rate limit.
     * 
     * @return max bytes per second (0 = unlimited)
     */
    public long getLimit() {
        return maxBytesPerSecond;
    }

    /**
     * Request permission to transfer a number of bytes.
     * Blocks until the transfer is allowed according to the rate limit.
     * 
     * @param bytes number of bytes to transfer
     */
    public void acquire(long bytes) {
        if (maxBytesPerSecond <= 0 || bytes <= 0) {
            // No limit or nothing to acquire
            recordBytes(bytes);
            return;
        }

        long remaining = bytes;

        while (remaining > 0) {
            long acquired = tryAcquire(remaining);
            if (acquired > 0) {
                remaining -= acquired;
                recordBytes(acquired);
            }

            if (remaining > 0) {
                // Need to wait for tokens
                long waitMs = calculateWaitTime(remaining);
                if (waitMs > 0) {
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    /**
     * Try to acquire bytes without blocking.
     * 
     * @param bytes requested bytes
     * @return bytes actually acquired (may be less than requested)
     */
    public long tryAcquire(long bytes) {
        if (maxBytesPerSecond <= 0) {
            recordBytes(bytes);
            return bytes;
        }

        lock.lock();
        try {
            refillTokens();

            if (availableTokens <= 0) {
                return 0;
            }

            long acquired = Math.min(bytes, availableTokens);
            availableTokens -= acquired;
            return acquired;

        } finally {
            lock.unlock();
        }
    }

    /**
     * Check if bytes can be transferred without waiting.
     * 
     * @param bytes bytes to check
     * @return true if transfer would not require waiting
     */
    public boolean canAcquire(long bytes) {
        if (maxBytesPerSecond <= 0) {
            return true;
        }

        lock.lock();
        try {
            refillTokens();
            return availableTokens >= bytes;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Refill tokens based on elapsed time.
     * Must be called while holding lock.
     */
    private void refillTokens() {
        long now = System.nanoTime();
        long elapsedNanos = now - lastRefillTime;

        if (elapsedNanos > 0) {
            // Calculate tokens to add based on elapsed time
            double tokensToAdd = (maxBytesPerSecond * elapsedNanos) / 1_000_000_000.0;

            if (tokensToAdd >= 1) {
                long wholeTokens = (long) tokensToAdd;
                long bucketMax = (long) (maxBytesPerSecond * BUCKET_SIZE_MULTIPLIER);
                availableTokens = Math.min(availableTokens + wholeTokens, bucketMax);
                lastRefillTime = now;
            }
        }
    }

    /**
     * Calculate wait time to get the requested tokens.
     */
    private long calculateWaitTime(long bytes) {
        if (maxBytesPerSecond <= 0) {
            return 0;
        }

        // Time needed to generate required tokens
        double secondsNeeded = (double) bytes / maxBytesPerSecond;
        long msNeeded = (long) (secondsNeeded * 1000);

        return Math.max(MIN_SLEEP_MS, msNeeded);
    }

    /**
     * Record transferred bytes for statistics.
     */
    private void recordBytes(long bytes) {
        if (bytes <= 0)
            return;

        totalBytes.addAndGet(bytes);
        bytesThisSecond.addAndGet(bytes);

        // Check if we need to reset per-second counter
        long now = System.currentTimeMillis();
        if (now - lastStatsReset >= 1000) {
            lastSecondBytes = bytesThisSecond.getAndSet(0);
            lastStatsReset = now;
        }
    }

    /**
     * Get the current transfer rate.
     * 
     * @return bytes per second
     */
    public long getCurrentRate() {
        // Update stats if needed
        long now = System.currentTimeMillis();
        if (now - lastStatsReset >= 1000) {
            lastSecondBytes = bytesThisSecond.getAndSet(0);
            lastStatsReset = now;
        }
        return lastSecondBytes;
    }

    /**
     * Get total bytes transferred.
     * 
     * @return total bytes
     */
    public long getTotalBytes() {
        return totalBytes.get();
    }

    /**
     * Get available tokens (useful for debugging).
     * 
     * @return available tokens
     */
    public long getAvailableTokens() {
        if (maxBytesPerSecond <= 0) {
            return Long.MAX_VALUE;
        }

        lock.lock();
        try {
            refillTokens();
            return availableTokens;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Check if rate limiting is enabled.
     * 
     * @return true if a limit is set
     */
    public boolean isLimited() {
        return maxBytesPerSecond > 0;
    }

    /**
     * Reset all statistics.
     */
    public void resetStats() {
        totalBytes.set(0);
        bytesThisSecond.set(0);
        lastSecondBytes = 0;
        lastStatsReset = System.currentTimeMillis();
    }

    /**
     * Format bytes per second as human-readable string.
     * 
     * @param bytesPerSecond bytes per second
     * @return formatted string (e.g., "1.5 MB/s")
     */
    public static String formatRate(long bytesPerSecond) {
        if (bytesPerSecond <= 0) {
            return "0 B/s";
        }

        String[] units = { "B/s", "KB/s", "MB/s", "GB/s" };
        int unitIndex = 0;
        double rate = bytesPerSecond;

        while (rate >= 1024 && unitIndex < units.length - 1) {
            rate /= 1024;
            unitIndex++;
        }

        if (unitIndex == 0) {
            return String.format("%d %s", bytesPerSecond, units[0]);
        }

        return String.format("%.1f %s", rate, units[unitIndex]);
    }
}
