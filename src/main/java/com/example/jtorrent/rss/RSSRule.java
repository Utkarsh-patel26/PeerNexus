package com.example.jtorrent.rss;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * RSS filter rule for automatic torrent downloads.
 */
public class RSSRule {

    private final String id;
    private String name;
    private String titlePattern;
    private Pattern compiledTitlePattern;
    private Long minSize;
    private Long maxSize;
    private String categoryFilter;
    private String customSavePath;
    private boolean enabled;
    private int priority; // 1=low, 2=normal, 3=high

    public RSSRule(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.enabled = true;
        this.priority = 2; // Normal by default
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTitlePattern() {
        return titlePattern;
    }

    /**
     * Set title regex pattern.
     *
     * @param pattern regex pattern (case-insensitive)
     */
    public void setTitlePattern(String pattern) {
        this.titlePattern = pattern;
        if (pattern != null && !pattern.isEmpty()) {
            this.compiledTitlePattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        } else {
            this.compiledTitlePattern = null;
        }
    }

    public Long getMinSize() {
        return minSize;
    }

    public void setMinSize(Long minSize) {
        this.minSize = minSize;
    }

    public Long getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(Long maxSize) {
        this.maxSize = maxSize;
    }

    public String getCategoryFilter() {
        return categoryFilter;
    }

    public void setCategoryFilter(String categoryFilter) {
        this.categoryFilter = categoryFilter;
    }

    public String getCustomSavePath() {
        return customSavePath;
    }

    public void setCustomSavePath(String customSavePath) {
        this.customSavePath = customSavePath;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = Math.max(1, Math.min(3, priority)); // Clamp to 1-3
    }

    /**
     * Check if an RSS item matches this rule.
     *
     * @param item the RSS item to test
     * @return true if item matches all filters
     */
    public boolean matches(RSSItem item) {
        if (!enabled) {
            return false;
        }

        // Title pattern
        if (compiledTitlePattern != null) {
            if (item.getTitle() == null || !compiledTitlePattern.matcher(item.getTitle()).find()) {
                return false;
            }
        }

        // Size filters
        if (minSize != null && item.getSize() < minSize) {
            return false;
        }
        if (maxSize != null && item.getSize() > maxSize) {
            return false;
        }

        // Category filter
        if (categoryFilter != null && !categoryFilter.isEmpty()) {
            if (item.getCategory() == null || !item.getCategory().equalsIgnoreCase(categoryFilter)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public String toString() {
        return String.format("RSSRule[%s, pattern=%s]", name, titlePattern);
    }
}
