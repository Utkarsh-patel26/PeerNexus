package com.example.jtorrent.storage;

/**
 * Priority levels for file downloads.
 * Controls which files are downloaded and in what order.
 */
public enum FilePriority {
    /**
     * Do not download this file.
     */
    SKIP(0),

    /**
     * Low priority - download after normal priority files.
     */
    LOW(1),

    /**
     * Normal priority - default for all files.
     */
    NORMAL(2),

    /**
     * High priority - download before normal priority files.
     */
    HIGH(3);

    private final int value;

    FilePriority(int value) {
        this.value = value;
    }

    /**
     * Get the numeric priority value.
     *
     * @return priority value (higher = more important)
     */
    public int getValue() {
        return value;
    }

    /**
     * Check if this file should be downloaded.
     *
     * @return true if priority is not SKIP
     */
    public boolean shouldDownload() {
        return this != SKIP;
    }

    /**
     * Get FilePriority from integer value.
     *
     * @param value the priority value
     * @return corresponding FilePriority
     */
    public static FilePriority fromValue(int value) {
        for (FilePriority priority : values()) {
            if (priority.value == value) {
                return priority;
            }
        }
        return NORMAL;
    }
}
