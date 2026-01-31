package com.example.jtorrent.search;

import com.example.jtorrent.logging.Logger;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Manages multiple search providers and performs concurrent searches.
 */
public class SearchManager {

    private final Logger logger;
    private final List<SearchProvider> providers;
    private final ExecutorService executorService;

    public SearchManager(Logger logger) {
        this.logger = logger;
        this.providers = new CopyOnWriteArrayList<>();
        this.executorService = Executors.newFixedThreadPool(
                Math.min(4, Runtime.getRuntime().availableProcessors()),
                r -> {
                    Thread t = new Thread(r, "Search-Worker");
                    t.setDaemon(true);
                    return t;
                });
    }

    /**
     * Add search provider.
     */
    public void addProvider(SearchProvider provider) {
        providers.add(provider);
        logger.info("Added search provider: %s", provider.getName());
    }

    /**
     * Remove search provider.
     */
    public void removeProvider(SearchProvider provider) {
        providers.remove(provider);
        logger.info("Removed search provider: %s", provider.getName());
    }

    /**
     * Get all providers.
     */
    public List<SearchProvider> getProviders() {
        return new ArrayList<>(providers);
    }

    /**
     * Get enabled providers.
     */
    public List<SearchProvider> getEnabledProviders() {
        return providers.stream()
                .filter(SearchProvider::isEnabled)
                .collect(Collectors.toList());
    }

    /**
     * Search all enabled providers concurrently.
     *
     * @param query      search query
     * @param maxResults max results per provider
     * @param timeoutMs  timeout in milliseconds
     * @return aggregated search results
     */
    public List<SearchResult> searchAll(String query, int maxResults, long timeoutMs) {
        List<SearchProvider> enabledProviders = getEnabledProviders();

        if (enabledProviders.isEmpty()) {
            logger.warn("No enabled search providers");
            return Collections.emptyList();
        }

        logger.info("Searching %d providers for: %s", enabledProviders.size(), query);

        // Submit search tasks
        List<Future<List<SearchResult>>> futures = new ArrayList<>();
        for (SearchProvider provider : enabledProviders) {
            Future<List<SearchResult>> future = executorService.submit(() -> {
                try {
                    logger.debug("Searching %s...", provider.getName());
                    List<SearchResult> results = provider.search(query, maxResults);
                    logger.info("%s returned %d results", provider.getName(), results.size());
                    return results;
                } catch (Exception e) {
                    logger.error("Search failed for %s: %s", provider.getName(), e.getMessage());
                    return Collections.emptyList();
                }
            });
            futures.add(future);
        }

        // Collect results with timeout
        List<SearchResult> allResults = new ArrayList<>();
        long deadline = System.currentTimeMillis() + timeoutMs;

        for (Future<List<SearchResult>> future : futures) {
            try {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining > 0) {
                    List<SearchResult> results = future.get(remaining, TimeUnit.MILLISECONDS);
                    allResults.addAll(results);
                } else {
                    future.cancel(true);
                }
            } catch (TimeoutException e) {
                future.cancel(true);
                logger.warn("Search provider timed out");
            } catch (Exception e) {
                logger.error("Search error: %s", e.getMessage());
            }
        }

        logger.info("Search complete: %d total results", allResults.size());
        return allResults;
    }

    /**
     * Search specific provider.
     *
     * @param providerName provider name
     * @param query        search query
     * @param maxResults   max results
     * @return search results
     */
    public List<SearchResult> searchProvider(String providerName, String query, int maxResults) {
        Optional<SearchProvider> provider = providers.stream()
                .filter(p -> p.getName().equalsIgnoreCase(providerName))
                .findFirst();

        if (!provider.isPresent()) {
            logger.error("Provider not found: %s", providerName);
            return Collections.emptyList();
        }

        if (!provider.get().isEnabled()) {
            logger.error("Provider disabled: %s", providerName);
            return Collections.emptyList();
        }

        try {
            return provider.get().search(query, maxResults);
        } catch (Exception e) {
            logger.error("Search failed: %s", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Test connectivity of all providers.
     *
     * @return map of provider name to connectivity status
     */
    public Map<String, Boolean> testAllProviders() {
        Map<String, Boolean> results = new ConcurrentHashMap<>();

        List<Future<?>> futures = new ArrayList<>();
        for (SearchProvider provider : providers) {
            Future<?> future = executorService.submit(() -> {
                boolean reachable = provider.testConnectivity();
                results.put(provider.getName(), reachable);
                logger.info("Provider %s: %s", provider.getName(),
                        reachable ? "reachable" : "unreachable");
            });
            futures.add(future);
        }

        // Wait for all tests
        for (Future<?> future : futures) {
            try {
                future.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                // Timeout or error, already logged
            }
        }

        return results;
    }

    /**
     * Shutdown search manager.
     */
    public void shutdown() {
        logger.info("Shutting down search manager");
        executorService.shutdown();
    }
}
