package com.example.jtorrent.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages torrent labels/tags for organization and categorization.
 *
 * <p>
 * Features:
 * <ul>
 * <li>Create and manage custom labels
 * <li>Assign multiple labels to torrents
 * <li>Color coding for visual identification
 * <li>Filter torrents by label
 * <li>Label statistics
 * </ul>
 */
public class LabelManager {

    /** Singleton instance. */
    private static final LabelManager INSTANCE = new LabelManager();

    /** All defined labels. */
    private final Map<String, Label> labels;

    /** Torrent to labels mapping. */
    private final Map<String, Set<String>> torrentLabels;

    /** Default label colors. */
    private static final String[] DEFAULT_COLORS = {
            "#FF6B6B", "#4ECDC4", "#45B7D1", "#96CEB4",
            "#FFEAA7", "#DDA0DD", "#98D8C8", "#F7DC6F",
            "#BB8FCE", "#85C1E9", "#F8B500", "#58D68D"
    };

    private int colorIndex = 0;

    /**
     * Get singleton instance.
     *
     * @return label manager instance
     */
    public static LabelManager getInstance() {
        return INSTANCE;
    }

    private LabelManager() {
        this.labels = new ConcurrentHashMap<>();
        this.torrentLabels = new ConcurrentHashMap<>();

        // Create some default labels
        createLabel("Movies", "#FF6B6B");
        createLabel("TV Shows", "#4ECDC4");
        createLabel("Music", "#45B7D1");
        createLabel("Games", "#96CEB4");
        createLabel("Software", "#FFEAA7");
        createLabel("Books", "#DDA0DD");
    }

    /**
     * Create a new label.
     *
     * @param name  label name
     * @param color hex color code
     * @return the created label
     */
    public Label createLabel(String name, String color) {
        Label label = new Label(name, color);
        labels.put(name.toLowerCase(), label);
        return label;
    }

    /**
     * Create a new label with auto-assigned color.
     *
     * @param name label name
     * @return the created label
     */
    public Label createLabel(String name) {
        String color = DEFAULT_COLORS[colorIndex % DEFAULT_COLORS.length];
        colorIndex++;
        return createLabel(name, color);
    }

    /**
     * Get a label by name.
     *
     * @param name label name (case-insensitive)
     * @return label or null if not found
     */
    public Label getLabel(String name) {
        return labels.get(name.toLowerCase());
    }

    /**
     * Get all defined labels.
     *
     * @return collection of labels
     */
    public Collection<Label> getAllLabels() {
        return new ArrayList<>(labels.values());
    }

    /**
     * Delete a label.
     *
     * @param name label name
     */
    public void deleteLabel(String name) {
        labels.remove(name.toLowerCase());
        // Remove from all torrents
        for (Set<String> labelSet : torrentLabels.values()) {
            labelSet.remove(name.toLowerCase());
        }
    }

    /**
     * Assign a label to a torrent.
     *
     * @param infoHashHex torrent info hash (hex)
     * @param labelName   label name
     * @return true if label was added
     */
    public boolean addLabelToTorrent(String infoHashHex, String labelName) {
        if (getLabel(labelName) == null) {
            return false;
        }
        Set<String> labelSet = torrentLabels.computeIfAbsent(
                infoHashHex, k -> ConcurrentHashMap.newKeySet());
        return labelSet.add(labelName.toLowerCase());
    }

    /**
     * Remove a label from a torrent.
     *
     * @param infoHashHex torrent info hash (hex)
     * @param labelName   label name
     * @return true if label was removed
     */
    public boolean removeLabelFromTorrent(String infoHashHex, String labelName) {
        Set<String> labelSet = torrentLabels.get(infoHashHex);
        if (labelSet != null) {
            return labelSet.remove(labelName.toLowerCase());
        }
        return false;
    }

    /**
     * Get all labels for a torrent.
     *
     * @param infoHashHex torrent info hash (hex)
     * @return set of labels
     */
    public Set<Label> getLabelsForTorrent(String infoHashHex) {
        Set<String> labelNames = torrentLabels.get(infoHashHex);
        if (labelNames == null) {
            return Collections.emptySet();
        }
        Set<Label> result = new LinkedHashSet<>();
        for (String name : labelNames) {
            Label label = labels.get(name);
            if (label != null) {
                result.add(label);
            }
        }
        return result;
    }

    /**
     * Get all torrents with a specific label.
     *
     * @param labelName label name
     * @return set of torrent info hashes
     */
    public Set<String> getTorrentsByLabel(String labelName) {
        String normalizedName = labelName.toLowerCase();
        Set<String> result = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : torrentLabels.entrySet()) {
            if (entry.getValue().contains(normalizedName)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * Check if torrent has a specific label.
     *
     * @param infoHashHex torrent info hash
     * @param labelName   label name
     * @return true if has label
     */
    public boolean torrentHasLabel(String infoHashHex, String labelName) {
        Set<String> labelSet = torrentLabels.get(infoHashHex);
        return labelSet != null && labelSet.contains(labelName.toLowerCase());
    }

    /**
     * Clear all labels from a torrent.
     *
     * @param infoHashHex torrent info hash
     */
    public void clearTorrentLabels(String infoHashHex) {
        torrentLabels.remove(infoHashHex);
    }

    /**
     * Get label statistics.
     *
     * @return map of label name to torrent count
     */
    public Map<String, Integer> getStatistics() {
        Map<String, Integer> stats = new LinkedHashMap<>();
        for (Label label : labels.values()) {
            stats.put(label.getName(), getTorrentsByLabel(label.getName()).size());
        }
        return stats;
    }

    /**
     * Label definition.
     */
    public static class Label {
        private final String name;
        private String color;
        private String description;

        public Label(String name, String color) {
            this.name = name;
            this.color = color;
        }

        public String getName() {
            return name;
        }

        public String getColor() {
            return color;
        }

        public void setColor(String color) {
            this.color = color;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null || getClass() != obj.getClass())
                return false;
            Label other = (Label) obj;
            return name.equalsIgnoreCase(other.name);
        }

        @Override
        public int hashCode() {
            return name.toLowerCase().hashCode();
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
