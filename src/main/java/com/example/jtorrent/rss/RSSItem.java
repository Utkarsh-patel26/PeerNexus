package com.example.jtorrent.rss;

import java.net.URI;
import java.util.Date;

/**
 * RSS feed item (torrent entry).
 */
public class RSSItem {

    private final String title;
    private final String description;
    private final URI link;
    private final URI torrentUrl;
    private final Date publishDate;
    private final long size;
    private final String category;

    public RSSItem(String title, String description, URI link,
            URI torrentUrl, Date publishDate, long size, String category) {
        this.title = title;
        this.description = description;
        this.link = link;
        this.torrentUrl = torrentUrl;
        this.publishDate = publishDate;
        this.size = size;
        this.category = category;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public URI getLink() {
        return link;
    }

    public URI getTorrentUrl() {
        return torrentUrl;
    }

    public Date getPublishDate() {
        return publishDate;
    }

    public long getSize() {
        return size;
    }

    public String getCategory() {
        return category;
    }

    @Override
    public String toString() {
        return String.format("RSSItem[%s, %d bytes]", title, size);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof RSSItem))
            return false;
        RSSItem other = (RSSItem) obj;
        // Use torrent URL as unique identifier
        return torrentUrl != null && torrentUrl.equals(other.torrentUrl);
    }

    @Override
    public int hashCode() {
        return torrentUrl != null ? torrentUrl.hashCode() : 0;
    }
}
