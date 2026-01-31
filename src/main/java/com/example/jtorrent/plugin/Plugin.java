package com.example.jtorrent.plugin;

public interface Plugin {

    String getId();

    String getName();

    String getVersion();

    void onLoad(PluginContext context);

    void onEnable();

    void onDisable();
}
