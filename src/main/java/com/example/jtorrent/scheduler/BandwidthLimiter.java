package com.example.jtorrent.scheduler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token-bucket bandwidth limiter.
 */
public class BandwidthLimiter {

  private final long globalUploadBytesPerSec;
  private final long globalDownloadBytesPerSec;
  private final long perSocketBytesPerSec;

  private final TokenBucket globalUploadBucket;
  private final TokenBucket globalDownloadBucket;
  private final Map<String, TokenBucket> perSocketUploadBuckets;
  private final Map<String, TokenBucket> perSocketDownloadBuckets;

  public BandwidthLimiter(
      long globalUploadBytesPerSec, long globalDownloadBytesPerSec, long perSocketBytesPerSec) {
    this.globalUploadBytesPerSec = globalUploadBytesPerSec;
    this.globalDownloadBytesPerSec = globalDownloadBytesPerSec;
    this.perSocketBytesPerSec = perSocketBytesPerSec;

    // Global buckets (capacity = 1 second worth of tokens)
    this.globalUploadBucket = globalUploadBytesPerSec > 0
        ? new TokenBucket(globalUploadBytesPerSec, globalUploadBytesPerSec)
        : null;
    this.globalDownloadBucket = globalDownloadBytesPerSec > 0
        ? new TokenBucket(globalDownloadBytesPerSec, globalDownloadBytesPerSec)
        : null;

    this.perSocketUploadBuckets = new ConcurrentHashMap<>();
    this.perSocketDownloadBuckets = new ConcurrentHashMap<>();
  }

  /** Returns number of bytes allowed to send (may be less than requested). */
  public long requestUpload(String socketId, long bytes) {
    // Check per-socket limit first
    long allowed = bytes;
    if (perSocketBytesPerSec > 0) {
      TokenBucket socketBucket = perSocketUploadBuckets.computeIfAbsent(
          socketId, k -> new TokenBucket(perSocketBytesPerSec, perSocketBytesPerSec));
      allowed = Math.min(allowed, socketBucket.consume(allowed));
    }

    // Then check global limit
    if (globalUploadBucket != null && allowed > 0) {
      allowed = globalUploadBucket.consume(allowed);
    }

    return allowed;
  }

  /** Returns number of bytes allowed to receive (may be less than requested). */
  public long requestDownload(String socketId, long bytes) {
    // Check per-socket limit first
    long allowed = bytes;
    if (perSocketBytesPerSec > 0) {
      TokenBucket socketBucket = perSocketDownloadBuckets.computeIfAbsent(
          socketId, k -> new TokenBucket(perSocketBytesPerSec, perSocketBytesPerSec));
      allowed = Math.min(allowed, socketBucket.consume(allowed));
    }

    // Then check global limit
    if (globalDownloadBucket != null && allowed > 0) {
      allowed = globalDownloadBucket.consume(allowed);
    }

    return allowed;
  }

  public void removeSocket(String socketId) {
    perSocketUploadBuckets.remove(socketId);
    perSocketDownloadBuckets.remove(socketId);
  }

  /**
   * Get global upload limit.
   *
   * @return bytes per second (0 = unlimited)
   */
  public long getGlobalUploadLimit() {
    return globalUploadBytesPerSec;
  }

  /**
   * Get global download limit.
   *
   * @return bytes per second (0 = unlimited)
   */
  public long getGlobalDownloadLimit() {
    return globalDownloadBytesPerSec;
  }

  /**
   * Get per-socket limit.
   *
   * @return bytes per second (0 = unlimited)
   */
  public long getPerSocketLimit() {
    return perSocketBytesPerSec;
  }

  /**
   * Token bucket implementation for rate limiting.
   */
  public static class TokenBucket {
    private final long capacity;
    private final long refillRate;
    private long tokens;
    private long lastRefillTime;

    /**
     * Create a token bucket.
     *
     * @param capacity            maximum tokens (bucket size)
     * @param refillRatePerSecond tokens added per second
     */
    public TokenBucket(long capacity, long refillRatePerSecond) {
      this.capacity = capacity;
      this.refillRate = refillRatePerSecond;
      this.tokens = capacity; // Start full
      this.lastRefillTime = System.nanoTime();
    }

    /**
     * Try to consume tokens from the bucket.
     *
     * @param requested number of tokens requested
     * @return number of tokens actually consumed
     */
    public synchronized long consume(long requested) {
      refill();

      long consumed = Math.min(requested, tokens);
      tokens -= consumed;
      return consumed;
    }

    /**
     * Refill tokens based on elapsed time.
     */
    private void refill() {
      long now = System.nanoTime();
      long elapsedNanos = now - lastRefillTime;

      // Calculate tokens to add based on elapsed time
      long tokensToAdd = (elapsedNanos * refillRate) / 1_000_000_000L;

      if (tokensToAdd > 0) {
        tokens = Math.min(capacity, tokens + tokensToAdd);
        // Update last refill time, accounting for remainder
        lastRefillTime = now - (elapsedNanos % (1_000_000_000L / refillRate));
      }
    }

    /**
     * Get current token count.
     *
     * @return available tokens
     */
    public synchronized long getTokens() {
      refill();
      return tokens;
    }

    /**
     * Get bucket capacity.
     *
     * @return capacity
     */
    public long getCapacity() {
      return capacity;
    }

    /**
     * Get refill rate.
     *
     * @return tokens per second
     */
    public long getRefillRate() {
      return refillRate;
    }

    /**
     * Set tokens directly (for testing).
     *
     * @param newTokens new token count
     */
    public synchronized void setTokens(long newTokens) {
      this.tokens = Math.min(capacity, newTokens);
    }
  }
}
