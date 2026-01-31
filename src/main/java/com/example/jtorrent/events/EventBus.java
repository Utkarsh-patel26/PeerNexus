package com.example.jtorrent.events;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Thread-safe event bus for decoupling components.
 * Supports synchronous and asynchronous event delivery.
 */
public class EventBus {

    private static final EventBus INSTANCE = new EventBus();

    private final Map<Class<? extends TorrentEvent>, List<Consumer<? extends TorrentEvent>>> subscribers;
    private final ExecutorService asyncExecutor;
    private volatile boolean shutdown = false;

    private EventBus() {
        this.subscribers = new ConcurrentHashMap<>();
        this.asyncExecutor = Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
                r -> {
                    Thread t = new Thread(r, "EventBus-Worker");
                    t.setDaemon(true);
                    return t;
                });
    }

    /**
     * Get the global EventBus instance.
     */
    public static EventBus getInstance() {
        return INSTANCE;
    }

    /**
     * Subscribe to events of a specific type.
     *
     * @param eventType the event class
     * @param handler   the event handler
     * @param <T>       event type
     */
    public <T extends TorrentEvent> void subscribe(Class<T> eventType, Consumer<T> handler) {
        subscribers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                .add((Consumer<? extends TorrentEvent>) handler);
    }

    /**
     * Unsubscribe from events.
     *
     * @param eventType the event class
     * @param handler   the event handler to remove
     * @param <T>       event type
     */
    public <T extends TorrentEvent> void unsubscribe(Class<T> eventType, Consumer<T> handler) {
        List<Consumer<? extends TorrentEvent>> handlers = subscribers.get(eventType);
        if (handlers != null) {
            handlers.remove(handler);
        }
    }

    /**
     * Publish an event synchronously (blocks until all handlers complete).
     *
     * @param event the event to publish
     */
    @SuppressWarnings("unchecked")
    public void publish(TorrentEvent event) {
        if (shutdown) {
            return;
        }

        List<Consumer<? extends TorrentEvent>> handlers = subscribers.get(event.getClass());
        if (handlers != null && !handlers.isEmpty()) {
            for (Consumer<? extends TorrentEvent> handler : handlers) {
                try {
                    ((Consumer<TorrentEvent>) handler).accept(event);
                } catch (Exception e) {
                    System.err.println("Event handler error: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Publish an event asynchronously (non-blocking).
     *
     * @param event the event to publish
     */
    public void publishAsync(TorrentEvent event) {
        if (shutdown) {
            return;
        }

        asyncExecutor.submit(() -> publish(event));
    }

    /**
     * Shutdown the event bus (call on application exit).
     */
    public void shutdown() {
        shutdown = true;
        asyncExecutor.shutdown();
    }

    /**
     * Get subscriber count for an event type (for testing).
     */
    public int getSubscriberCount(Class<? extends TorrentEvent> eventType) {
        List<Consumer<? extends TorrentEvent>> handlers = subscribers.get(eventType);
        return handlers != null ? handlers.size() : 0;
    }
}
