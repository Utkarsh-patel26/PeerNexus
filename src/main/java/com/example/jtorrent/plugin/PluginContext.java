package com.example.jtorrent.plugin;

import com.example.jtorrent.config.Config;
import com.example.jtorrent.core.TorrentSessionManager;
import com.example.jtorrent.events.EventBus;
import com.example.jtorrent.events.TorrentEvent;
import com.example.jtorrent.logging.Logger;
import java.nio.file.Path;

public class PluginContext {

    private final EventBus eventBus;
    private final Config config;
    private final TorrentSessionManager sessionManager;
    private final Logger logger;
    private final Path dataDirectory;

    public PluginContext(EventBus eventBus, Config config, TorrentSessionManager sessionManager, Path dataDirectory) {
        this.eventBus = eventBus;
        this.config = config;
        this.sessionManager = sessionManager;
        this.dataDirectory = dataDirectory;
        this.logger = Logger.getLogger("Plugin");
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public Config getConfig() {
        return config;
    }

    public TorrentSessionManager getSessionManager() {
        return sessionManager;
    }

    public Logger getLogger() {
        return logger;
    }

    public Logger getLogger(String name) {
        return Logger.getLogger("Plugin:" + name);
    }

    public Path getDataDirectory() {
        return dataDirectory;
    }

    public <T extends TorrentEvent> void subscribe(Class<T> eventType, java.util.function.Consumer<T> handler) {
        eventBus.subscribe(eventType, handler);
    }

    public void publish(TorrentEvent event) {
        eventBus.publish(event);
    }
}
