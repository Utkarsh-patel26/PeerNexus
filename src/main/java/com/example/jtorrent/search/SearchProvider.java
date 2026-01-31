package com.example.jtorrent.search;

import java.util.List;

/**
 * Search provider interface for torrent search engines.
 * Implementations should support concurrent searches.
 */
public interface SearchProvider {

    /**
     * Get provider name.
     */
    String getName();

    /**
     * Get provider URL.
     */
    String getUrl();

    /**
     * Check if provider is enabled.
     */
    boolean isEnabled();

    /**
     * Enable or disable provider.
     */
    void setEnabled(boolean enabled);

    /**
     * Search for torrents.
     *
     * @param query      search query
     * @param maxResults maximum results to return
     * @return list of search results
     * @throws Exception if search fails
     */
    List<SearchResult> search(String query, int maxResults) throws Exception;

    /**
     * Test if provider is reachable.
     *
     * @return true if provider responds
     */
    boolean testConnectivity();
}
