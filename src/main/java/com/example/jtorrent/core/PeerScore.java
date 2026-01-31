package com.example.jtorrent.core;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

public class PeerScore {

    private final String peerId;
    private final AtomicLong downloadedBytes = new AtomicLong(0);
    private final AtomicLong uploadedBytes = new AtomicLong(0);
    private final AtomicLong downloadRate = new AtomicLong(0);
    private final AtomicLong uploadRate = new AtomicLong(0);
    private final AtomicLong latencyMs = new AtomicLong(0);
    private final AtomicInteger successfulRequests = new AtomicInteger(0);
    private final AtomicInteger failedRequests = new AtomicInteger(0);
    private final AtomicInteger disconnectCount = new AtomicInteger(0);
    private final AtomicLong lastActivityTime = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong connectionTime = new AtomicLong(System.currentTimeMillis());
    private final AtomicInteger snubCount = new AtomicInteger(0);
    private volatile boolean isSnubbed = false;
    private volatile double cachedScore = 0.0;
    private volatile long scoreTimestamp = 0;

    private static final double DOWNLOAD_RATE_WEIGHT = 0.35;
    private static final double UPLOAD_RATE_WEIGHT = 0.20;
    private static final double LATENCY_WEIGHT = 0.15;
    private static final double RELIABILITY_WEIGHT = 0.15;
    private static final double STABILITY_WEIGHT = 0.15;
    private static final long SNUB_THRESHOLD_MS = 60000;
    private static final long SCORE_CACHE_MS = 1000;

    public PeerScore(String peerId) {
        this.peerId = peerId;
    }

    public void recordDownload(long bytes, long rateBytes) {
        downloadedBytes.addAndGet(bytes);
        downloadRate.set(rateBytes);
        lastActivityTime.set(System.currentTimeMillis());
        isSnubbed = false;
    }

    public void recordUpload(long bytes, long rateBytes) {
        uploadedBytes.addAndGet(bytes);
        uploadRate.set(rateBytes);
        lastActivityTime.set(System.currentTimeMillis());
    }

    public void recordLatency(long latency) {
        long current = latencyMs.get();
        latencyMs.set(current == 0 ? latency : (current * 7 + latency) / 8);
    }

    public void recordSuccessfulRequest() {
        successfulRequests.incrementAndGet();
        lastActivityTime.set(System.currentTimeMillis());
    }

    public void recordFailedRequest() {
        failedRequests.incrementAndGet();
    }

    public void recordDisconnect() {
        disconnectCount.incrementAndGet();
    }

    public void checkSnubbed() {
        long timeSinceActivity = System.currentTimeMillis() - lastActivityTime.get();
        if (timeSinceActivity > SNUB_THRESHOLD_MS && !isSnubbed) {
            isSnubbed = true;
            snubCount.incrementAndGet();
        }
    }

    public double calculateScore() {
        long now = System.currentTimeMillis();
        if (now - scoreTimestamp < SCORE_CACHE_MS) {
            return cachedScore;
        }

        checkSnubbed();

        double downloadScore = normalizeRate(downloadRate.get());
        double uploadScore = normalizeRate(uploadRate.get());
        double latencyScore = normalizeLatency(latencyMs.get());
        double reliabilityScore = calculateReliability();
        double stabilityScore = calculateStability();

        if (isSnubbed) {
            downloadScore *= 0.1;
        }

        cachedScore = (downloadScore * DOWNLOAD_RATE_WEIGHT) +
                (uploadScore * UPLOAD_RATE_WEIGHT) +
                (latencyScore * LATENCY_WEIGHT) +
                (reliabilityScore * RELIABILITY_WEIGHT) +
                (stabilityScore * STABILITY_WEIGHT);

        scoreTimestamp = now;
        return cachedScore;
    }

    private double normalizeRate(long rate) {
        if (rate <= 0)
            return 0.0;
        return Math.min(1.0, Math.log10(rate + 1) / 7.0);
    }

    private double normalizeLatency(long latency) {
        if (latency <= 0)
            return 1.0;
        if (latency >= 5000)
            return 0.0;
        return 1.0 - (latency / 5000.0);
    }

    private double calculateReliability() {
        int total = successfulRequests.get() + failedRequests.get();
        if (total == 0)
            return 0.5;
        return (double) successfulRequests.get() / total;
    }

    private double calculateStability() {
        int disconnects = disconnectCount.get();
        long connectionDuration = System.currentTimeMillis() - connectionTime.get();
        double hoursFactor = connectionDuration / (3600000.0);

        if (disconnects == 0)
            return 1.0;
        return Math.max(0.0, 1.0 - (disconnects / (hoursFactor + 1)));
    }

    public String getPeerId() {
        return peerId;
    }

    public long getDownloadedBytes() {
        return downloadedBytes.get();
    }

    public long getUploadedBytes() {
        return uploadedBytes.get();
    }

    public long getDownloadRate() {
        return downloadRate.get();
    }

    public long getUploadRate() {
        return uploadRate.get();
    }

    public long getLatencyMs() {
        return latencyMs.get();
    }

    public int getSuccessfulRequests() {
        return successfulRequests.get();
    }

    public int getFailedRequests() {
        return failedRequests.get();
    }

    public int getDisconnectCount() {
        return disconnectCount.get();
    }

    public int getSnubCount() {
        return snubCount.get();
    }

    public boolean isSnubbed() {
        return isSnubbed;
    }

    public long getConnectionDuration() {
        return System.currentTimeMillis() - connectionTime.get();
    }
}
