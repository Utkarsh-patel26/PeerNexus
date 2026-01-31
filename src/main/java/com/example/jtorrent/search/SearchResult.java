package com.example.jtorrent.search;

import java.net.URI;

/**
 * Search result entry.
 */
public class SearchResult {

    private final String title;
    private final long size;
    private final int seeders;
    private final int leechers;
    private final URI magnetLink;
    private final URI torrentUrl;
    private final String source;
    private final String category;
    private final String uploader;

    private SearchResult(Builder builder) {
        this.title = builder.title;
        this.size = builder.size;
        this.seeders = builder.seeders;
        this.leechers = builder.leechers;
        this.magnetLink = builder.magnetLink;
        this.torrentUrl = builder.torrentUrl;
        this.source = builder.source;
        this.category = builder.category;
        this.uploader = builder.uploader;
    }

    public String getTitle() {
        return title;
    }

    public long getSize() {
        return size;
    }

    public int getSeeders() {
        return seeders;
    }

    public int getLeechers() {
        return leechers;
    }

    public URI getMagnetLink() {
        return magnetLink;
    }

    public URI getTorrentUrl() {
        return torrentUrl;
    }

    public String getSource() {
        return source;
    }

    public String getCategory() {
        return category;
    }

    public String getUploader() {
        return uploader;
    }

    public static class Builder {
        private String title;
        private long size;
        private int seeders;
        private int leechers;
        private URI magnetLink;
        private URI torrentUrl;
        private String source;
        private String category;
        private String uploader;

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder size(long size) {
            this.size = size;
            return this;
        }

        public Builder seeders(int seeders) {
            this.seeders = seeders;
            return this;
        }

        public Builder leechers(int leechers) {
            this.leechers = leechers;
            return this;
        }

        public Builder magnetLink(URI magnetLink) {
            this.magnetLink = magnetLink;
            return this;
        }

        public Builder torrentUrl(URI torrentUrl) {
            this.torrentUrl = torrentUrl;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder uploader(String uploader) {
            this.uploader = uploader;
            return this;
        }

        public SearchResult build() {
            return new SearchResult(this);
        }
    }

    @Override
    public String toString() {
        return String.format("SearchResult[%s, %d bytes, S:%d L:%d, from %s]",
                title, size, seeders, leechers, source);
    }
}
