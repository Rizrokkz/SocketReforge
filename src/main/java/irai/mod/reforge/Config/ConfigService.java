package irai.mod.reforge.Config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;

import com.hypixel.hytale.server.core.util.Config;

/**
 * Centralized loader for plugin configs.
 * Each registered config is saved, loaded, and then applied via callback.
 */
public final class ConfigService {
    private final Logger logger = Logger.getLogger("System: ");
    private final String pluginTag;
    private final Map<String, ConfigEntry<?>> entries = new LinkedHashMap<>();

    public ConfigService(String pluginTag) {
        this.pluginTag = pluginTag;
    }

    public <T> void register(String name, Config<T> config, Consumer<T> onLoad) {
        entries.put(name, new ConfigEntry<>(name, config, onLoad));
    }

    public void loadAll() {
        for (ConfigEntry<?> entry : entries.values()) {
            loadEntry(entry);
        }
    }

    public void saveAndApply(String name) {
        saveEntry(requireEntry(name));
    }

    public void reloadAndApply(String name) {
        reloadEntry(requireEntry(name));
    }

    public void reloadAll() {
        for (ConfigEntry<?> entry : entries.values()) {
            reloadEntry(entry);
        }
    }

    private <T> void loadEntry(ConfigEntry<T> entry) {
        try {
            entry.config.save().join();
            T loaded = entry.config.get();
            applyLoaded(entry, loaded, "Loaded");
        } catch (Exception e) {
            System.err.println("[" + pluginTag + "] Error loading config '" + entry.name + "': " + e.getMessage());
            e.printStackTrace();
        }
    }

    private <T> void saveEntry(ConfigEntry<T> entry) {
        try {
            applyLoaded(entry, entry.config.get(), "Saved");
            entry.config.save().whenComplete((ignored, throwable) -> {
                if (throwable != null) {
                    System.err.println("[" + pluginTag + "] Error saving config '" + entry.name + "': " + throwable.getMessage());
                    throwable.printStackTrace();
                }
            });
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save config '" + entry.name + "'", e);
        }
    }

    private <T> void reloadEntry(ConfigEntry<T> entry) {
        try {
            T loaded = entry.config.load().join();
            applyLoaded(entry, loaded, "Reloaded");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to reload config '" + entry.name + "'", e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> ConfigEntry<T> requireEntry(String name) {
        ConfigEntry<?> entry = entries.get(name);
        if (entry == null) {
            throw new IllegalArgumentException("Unknown config entry: " + name);
        }
        return (ConfigEntry<T>) entry;
    }

    private <T> void applyLoaded(ConfigEntry<T> entry, T loaded, String action) {
        if (loaded == null) {
            throw new IllegalStateException("Config '" + entry.name + "' returned null.");
        }

        if (entry.onLoad != null) {
            entry.onLoad.accept(loaded);
        }

        logger.info("[" + pluginTag + "] " + action + " config '" + entry.name + "'.");
    }

    private static final class ConfigEntry<T> {
        private final String name;
        private final Config<T> config;
        private final Consumer<T> onLoad;

        private ConfigEntry(String name, Config<T> config, Consumer<T> onLoad) {
            this.name = name;
            this.config = config;
            this.onLoad = onLoad;
        }
    }
}
