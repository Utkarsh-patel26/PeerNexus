package com.example.jtorrent.logging;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Advanced log store with per-torrent isolation and filtering capabilities.
 *
 * <p>
 * Features:
 * <ul>
 * <li>Per-torrent log isolation
 * <li>Global log aggregation
 * <li>Circular buffer with configurable size
 * <li>Level-based filtering
 * <li>Category-based filtering
 * <li>Live log listeners
 * <li>Export capabilities
 * </ul>
 */
public class LogStore {

    /** Singleton instance. */
    private static final LogStore INSTANCE = new LogStore();

    /** Default max entries per buffer. */
    private static final int DEFAULT_MAX_ENTRIES = 5000;

    /** Global log buffer. */
    private final CircularLogBuffer globalBuffer;

    /** Per-torrent log buffers. */
    private final Map<String, CircularLogBuffer> torrentBuffers;

    /** Live log listeners. */
    private final List<Consumer<LogEntry>> listeners;

    /** Maximum entries per buffer. */
    private final int maxEntries;

    /**
     * Get singleton instance.
     *
     * @return log store instance
     */
    public static LogStore getInstance() {
        return INSTANCE;
    }

    private LogStore() {
        this(DEFAULT_MAX_ENTRIES);
    }

    /**
     * Create log store with custom buffer size.
     *
     * @param maxEntries max entries per buffer
     */
    public LogStore(int maxEntries) {
        this.maxEntries = maxEntries;
        this.globalBuffer = new CircularLogBuffer(maxEntries);
        this.torrentBuffers = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
    }

    /**
     * Add a log entry.
     *
     * @param entry log entry
     */
    public void addEntry(LogEntry entry) {
        // Always add to global buffer
        globalBuffer.add(entry);

        // Add to torrent-specific buffer if applicable
        if (entry.getTorrentId() != null) {
            CircularLogBuffer torrentBuffer = torrentBuffers.computeIfAbsent(
                    entry.getTorrentId(), k -> new CircularLogBuffer(maxEntries));
            torrentBuffer.add(entry);
        }

        // Notify listeners
        for (Consumer<LogEntry> listener : listeners) {
            try {
                listener.accept(entry);
            } catch (Exception e) {
                // Don't let listener exceptions break logging
            }
        }
    }

    /**
     * Get all global logs.
     *
     * @return list of log entries
     */
    public List<LogEntry> getGlobalLogs() {
        return globalBuffer.getAll();
    }

    /**
     * Get logs for a specific torrent.
     *
     * @param torrentId torrent identifier
     * @return list of log entries
     */
    public List<LogEntry> getTorrentLogs(String torrentId) {
        CircularLogBuffer buffer = torrentBuffers.get(torrentId);
        return buffer != null ? buffer.getAll() : new ArrayList<>();
    }

    /**
     * Get filtered logs.
     *
     * @param torrentId      torrent ID (null for global)
     * @param minLevel       minimum log level
     * @param categoryFilter category substring filter (null for all)
     * @param textFilter     message text filter (null for all)
     * @return filtered list
     */
    public List<LogEntry> getFilteredLogs(
            String torrentId,
            LogEntry.LogLevel minLevel,
            String categoryFilter,
            String textFilter) {
        List<LogEntry> source = torrentId != null ? getTorrentLogs(torrentId) : getGlobalLogs();

        return source.stream()
                .filter(e -> e.getLevel().isAtLeast(minLevel))
                .filter(e -> categoryFilter == null
                        || e.getCategory().toLowerCase().contains(categoryFilter.toLowerCase()))
                .filter(e -> textFilter == null
                        || e.getMessage().toLowerCase().contains(textFilter.toLowerCase()))
                .collect(Collectors.toList());
    }

    /**
     * Add a live log listener.
     *
     * @param listener consumer for new log entries
     */
    public void addListener(Consumer<LogEntry> listener) {
        listeners.add(listener);
    }

    /**
     * Remove a log listener.
     *
     * @param listener listener to remove
     */
    public void removeListener(Consumer<LogEntry> listener) {
        listeners.remove(listener);
    }

    /**
     * Clear all logs.
     */
    public void clearAll() {
        globalBuffer.clear();
        torrentBuffers.values().forEach(CircularLogBuffer::clear);
    }

    /**
     * Clear logs for a specific torrent.
     *
     * @param torrentId torrent identifier
     */
    public void clearTorrentLogs(String torrentId) {
        CircularLogBuffer buffer = torrentBuffers.get(torrentId);
        if (buffer != null) {
            buffer.clear();
        }
    }

    /**
     * Remove torrent-specific buffer.
     *
     * @param torrentId torrent identifier
     */
    public void removeTorrent(String torrentId) {
        torrentBuffers.remove(torrentId);
    }

    /**
     * Get all tracked torrent IDs.
     *
     * @return set of torrent IDs
     */
    public java.util.Set<String> getTrackedTorrents() {
        return torrentBuffers.keySet();
    }

    /**
     * Get statistics.
     *
     * @return statistics map
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new java.util.LinkedHashMap<>();
        stats.put("globalLogCount", globalBuffer.size());
        stats.put("torrentCount", torrentBuffers.size());
        stats.put("listenerCount", listeners.size());
        stats.put("maxEntriesPerBuffer", maxEntries);

        Map<String, Integer> torrentCounts = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, CircularLogBuffer> entry : torrentBuffers.entrySet()) {
            torrentCounts.put(entry.getKey(), entry.getValue().size());
        }
        stats.put("torrentLogCounts", torrentCounts);

        return stats;
    }

    /**
     * Export logs to string.
     *
     * @param torrentId torrent ID (null for global)
     * @return formatted log string
     */
    public String exportToString(String torrentId) {
        List<LogEntry> logs = torrentId != null ? getTorrentLogs(torrentId) : getGlobalLogs();
        StringBuilder sb = new StringBuilder();
        for (LogEntry entry : logs) {
            sb.append(entry.format()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Circular buffer for log entries.
     */
    private static class CircularLogBuffer {
        private final LogEntry[] buffer;
        private int head = 0;
        private int count = 0;
        private final Object lock = new Object();

        CircularLogBuffer(int capacity) {
            this.buffer = new LogEntry[capacity];
        }

        void add(LogEntry entry) {
            synchronized (lock) {
                buffer[head] = entry;
                head = (head + 1) % buffer.length;
                if (count < buffer.length) {
                    count++;
                }
            }
        }

        List<LogEntry> getAll() {
            synchronized (lock) {
                List<LogEntry> result = new ArrayList<>(count);
                if (count < buffer.length) {
                    // Not wrapped yet
                    for (int i = 0; i < count; i++) {
                        result.add(buffer[i]);
                    }
                } else {
                    // Wrapped - start from head
                    for (int i = 0; i < buffer.length; i++) {
                        result.add(buffer[(head + i) % buffer.length]);
                    }
                }
                return result;
            }
        }

        int size() {
            synchronized (lock) {
                return count;
            }
        }

        void clear() {
            synchronized (lock) {
                head = 0;
                count = 0;
                for (int i = 0; i < buffer.length; i++) {
                    buffer[i] = null;
                }
            }
        }
    }
}
