package com.example.jtorrent.plugin;

import com.example.jtorrent.logging.Logger;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class PluginLoader {

    private static final Logger logger = Logger.getLogger(PluginLoader.class);

    private final Path pluginsDirectory;
    private final PluginContext context;
    private final Map<String, PluginInfo> loadedPlugins = new ConcurrentHashMap<>();
    private final Map<String, URLClassLoader> classLoaders = new ConcurrentHashMap<>();

    public PluginLoader(Path pluginsDirectory, PluginContext context) {
        this.pluginsDirectory = pluginsDirectory;
        this.context = context;
    }

    public void loadAll() throws IOException {
        if (!Files.exists(pluginsDirectory)) {
            Files.createDirectories(pluginsDirectory);
            logger.info("Created plugins directory: %s", pluginsDirectory);
            return;
        }

        Files.list(pluginsDirectory)
                .filter(p -> p.toString().endsWith(".jar"))
                .forEach(this::loadPlugin);

        logger.info("Loaded %d plugin(s)", loadedPlugins.size());
    }

    public void loadPlugin(Path jarPath) {
        try {
            URL jarUrl = jarPath.toUri().toURL();
            URLClassLoader classLoader = new URLClassLoader(
                    new URL[] { jarUrl },
                    getClass().getClassLoader());

            List<Class<?>> pluginClasses = findPluginClasses(jarPath, classLoader);

            for (Class<?> pluginClass : pluginClasses) {
                if (Plugin.class.isAssignableFrom(pluginClass)) {
                    Plugin plugin = (Plugin) pluginClass.getDeclaredConstructor().newInstance();

                    String id = plugin.getId();
                    if (loadedPlugins.containsKey(id)) {
                        logger.warn("Plugin %s already loaded, skipping", id);
                        continue;
                    }

                    plugin.onLoad(context);

                    PluginInfo info = new PluginInfo(plugin, jarPath, false);
                    loadedPlugins.put(id, info);
                    classLoaders.put(id, classLoader);

                    logger.info("Loaded plugin: %s v%s", plugin.getName(), plugin.getVersion());
                }
            }
        } catch (Exception e) {
            logger.error("Failed to load plugin %s: %s", jarPath.getFileName(), e.getMessage());
        }
    }

    private List<Class<?>> findPluginClasses(Path jarPath, ClassLoader classLoader) throws IOException {
        List<Class<?>> classes = new ArrayList<>();

        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                if (name.endsWith(".class") && !name.contains("$")) {
                    String className = name.replace('/', '.').replace(".class", "");
                    try {
                        Class<?> clazz = classLoader.loadClass(className);
                        if (Plugin.class.isAssignableFrom(clazz) && !clazz.isInterface()) {
                            classes.add(clazz);
                        }
                    } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
                    }
                }
            }
        }

        return classes;
    }

    public void enableAll() {
        loadedPlugins.values().forEach(info -> {
            if (!info.enabled) {
                enablePlugin(info.plugin.getId());
            }
        });
    }

    public void enablePlugin(String id) {
        PluginInfo info = loadedPlugins.get(id);
        if (info == null) {
            logger.warn("Plugin not found: %s", id);
            return;
        }

        if (info.enabled) {
            return;
        }

        try {
            info.plugin.onEnable();
            info.enabled = true;
            logger.info("Enabled plugin: %s", info.plugin.getName());
        } catch (Exception e) {
            logger.error("Failed to enable plugin %s: %s", id, e.getMessage());
        }
    }

    public void disablePlugin(String id) {
        PluginInfo info = loadedPlugins.get(id);
        if (info == null || !info.enabled) {
            return;
        }

        try {
            info.plugin.onDisable();
            info.enabled = false;
            logger.info("Disabled plugin: %s", info.plugin.getName());
        } catch (Exception e) {
            logger.error("Failed to disable plugin %s: %s", id, e.getMessage());
        }
    }

    public void disableAll() {
        loadedPlugins.values().forEach(info -> {
            if (info.enabled) {
                disablePlugin(info.plugin.getId());
            }
        });
    }

    public void unloadPlugin(String id) {
        disablePlugin(id);
        loadedPlugins.remove(id);

        URLClassLoader classLoader = classLoaders.remove(id);
        if (classLoader != null) {
            try {
                classLoader.close();
            } catch (IOException ignored) {
            }
        }
    }

    public void unloadAll() {
        new ArrayList<>(loadedPlugins.keySet()).forEach(this::unloadPlugin);
    }

    public List<PluginInfo> getLoadedPlugins() {
        return new ArrayList<>(loadedPlugins.values());
    }

    public PluginInfo getPlugin(String id) {
        return loadedPlugins.get(id);
    }

    public boolean isEnabled(String id) {
        PluginInfo info = loadedPlugins.get(id);
        return info != null && info.enabled;
    }

    public static class PluginInfo {
        public final Plugin plugin;
        public final Path jarPath;
        public volatile boolean enabled;

        PluginInfo(Plugin plugin, Path jarPath, boolean enabled) {
            this.plugin = plugin;
            this.jarPath = jarPath;
            this.enabled = enabled;
        }
    }
}
