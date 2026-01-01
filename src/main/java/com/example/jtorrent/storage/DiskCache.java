package com.example.jtorrent.storage;

import com.example.jtorrent.logging.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * LRU disk cache for piece data.
 */
public class DiskCache {

    private static final Logger logger = Logger.getLogger(DiskCache.class);

    private final long maxSizeBytes;
    private final Map<CacheKey, byte[]> cache;
    private final ReadWriteLock lock;

    // Statistics
    private long cacheHits = 0;
    private long cacheMisses = 0;
    private long currentSizeBytes = 0;

    public DiskCache(int maxSizeMB) {
        this.maxSizeBytes = (long) maxSizeMB * 1024 * 1024;
        this.lock = new ReentrantReadWriteLock();

        // LinkedHashMap with access-order for LRU
        this.cache = new LinkedHashMap<CacheKey, byte[]>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<CacheKey, byte[]> eldest) {
                if (currentSizeBytes > maxSizeBytes) {
                    currentSizeBytes -= eldest.getValue().length;
                    logger.debug("Evicted cache entry: " + eldest.getKey() + " (LRU policy)");
                    return true;
                }
                return false;
            }
        };

        logger.info("Disk cache initialized with " + maxSizeMB + " MB capacity");
    }

    public byte[] get(int pieceIndex) {
        CacheKey key = new CacheKey(pieceIndex);

        lock.readLock().lock();
        try {
            byte[] data = cache.get(key);
            if (data != null) {
                cacheHits++;
                logger.debug("Cache HIT for piece " + pieceIndex);
                // Return a copy to prevent external modification
                return data.clone();
            } else {
                cacheMisses++;
                logger.debug("Cache MISS for piece " + pieceIndex);
                return null;
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    public void put(int pieceIndex, byte[] data) {
        if (data == null) {
            return;
        }

        CacheKey key = new CacheKey(pieceIndex);

        lock.writeLock().lock();
        try {
            // Store a copy to prevent external modification
            byte[] cachedData = data.clone();
            byte[] oldData = cache.put(key, cachedData);

            // Update size tracking
            if (oldData != null) {
                currentSizeBytes -= oldData.length;
            }
            currentSizeBytes += cachedData.length;

            logger.debug("Cached piece " + pieceIndex + " (" + cachedData.length + " bytes)");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Remove piece data from cache.
     *
     * @param pieceIndex the piece index
     */
    public void remove(int pieceIndex) {
        CacheKey key = new CacheKey(pieceIndex);

        lock.writeLock().lock();
        try {
            byte[] data = cache.remove(key);
            if (data != null) {
                currentSizeBytes -= data.length;
                logger.debug("Removed piece " + pieceIndex + " from cache");
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Clear all cached data.
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            cache.clear();
            currentSizeBytes = 0;
            logger.info("Cache cleared");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get cache hit rate.
     *
     * @return hit rate (0.0 to 1.0)
     */
    public double getHitRate() {
        long total = cacheHits + cacheMisses;
        return total == 0 ? 0.0 : (double) cacheHits / total;
    }

    /**
     * Get cache statistics.
     *
     * @return statistics string
     */
    public String getStatistics() {
        lock.readLock().lock();
        try {
            double hitRate = getHitRate() * 100;
            double usageMB = currentSizeBytes / (1024.0 * 1024.0);
            double maxMB = maxSizeBytes / (1024.0 * 1024.0);

            return String.format(
                    "Cache: %.2f MB / %.2f MB (%.1f%% full), " +
                            "Hits: %d, Misses: %d, Hit Rate: %.1f%%",
                    usageMB, maxMB, (usageMB / maxMB) * 100,
                    cacheHits, cacheMisses, hitRate);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get the number of cached pieces.
     *
     * @return number of pieces in cache
     */
    public int getCachedPieceCount() {
        lock.readLock().lock();
        try {
            return cache.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get current cache size in bytes.
     *
     * @return cache size
     */
    public long getCurrentSizeBytes() {
        lock.readLock().lock();
        try {
            return currentSizeBytes;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Reset statistics.
     */
    public void resetStatistics() {
        lock.writeLock().lock();
        try {
            cacheHits = 0;
            cacheMisses = 0;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Cache key for piece identification.
     */
    private static class CacheKey {
        private final int pieceIndex;

        CacheKey(int pieceIndex) {
            this.pieceIndex = pieceIndex;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (!(obj instanceof CacheKey))
                return false;
            CacheKey other = (CacheKey) obj;
            return pieceIndex == other.pieceIndex;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(pieceIndex);
        }

        @Override
        public String toString() {
            return "Piece[" + pieceIndex + "]";
        }
    }
}
