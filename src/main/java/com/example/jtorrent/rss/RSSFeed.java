package com.example.jtorrent.rss;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * RSS feed configuration and state.
 */
public class RSSFeed {

    private final String id;
    private String name;
    private URI url;
    private int refreshIntervalMinutes;
    private boolean enabled;
    private long lastFetchTime;
    private String lastError;
    private List<RSSRule> rules;

    public RSSFeed(String name, URI url) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.url = url;
        this.refreshIntervalMinutes = 30; // Default 30 minutes
        this.enabled = true;
        this.lastFetchTime = 0;
        this.rules = new ArrayList<>();
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

    public URI getUrl() {
        return url;
    }

    public void setUrl(URI url) {
        this.url = url;
    }

    public int getRefreshIntervalMinutes() {
        return refreshIntervalMinutes;
    }

    public void setRefreshIntervalMinutes(int refreshIntervalMinutes) {
        this.refreshIntervalMinutes = refreshIntervalMinutes;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getLastFetchTime() {
        return lastFetchTime;
    }

    public void setLastFetchTime(long lastFetchTime) {
        this.lastFetchTime = lastFetchTime;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public List<RSSRule> getRules() {
        return new ArrayList<>(rules);
    }

    public void addRule(RSSRule rule) {
        rules.add(rule);
    }

    public void removeRule(RSSRule rule) {
        rules.remove(rule);
    }

    public void clearRules() {
        rules.clear();
    }

    @Override
    public String toString() {
        return String.format("RSSFeed[%s, %s, %d rules]", name, url, rules.size());
    }
}
