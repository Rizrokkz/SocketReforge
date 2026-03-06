package irai.mod.reforge.Config;

import java.util.ArrayList;
import java.util.List;
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
    private final List<ConfigEntry<?>> entries = new ArrayList<>();

    public ConfigService(String pluginTag) {
        this.pluginTag = pluginTag;
    }

    public <T> void register(String name, Config<T> config, Consumer<T> onLoad) {
        entries.add(new ConfigEntry<>(name, config, onLoad));
    }

    public void loadAll() {
        for (ConfigEntry<?> entry : entries) {
            loadEntry(entry);
        }
    }

    private <T> void loadEntry(ConfigEntry<T> entry) {
        try {
            entry.config.save();
            T loaded = entry.config.get();
            if (loaded == null) {
                System.err.println("[" + pluginTag + "] Config '" + entry.name + "' returned null.");
                return;
            }

            if (entry.onLoad != null) {
                entry.onLoad.accept(loaded);
            }

            logger.info("[" + pluginTag + "] Loaded config '" + entry.name + "'.");
        } catch (Exception e) {
            System.err.println("[" + pluginTag + "] Error loading config '" + entry.name + "': " + e.getMessage());
            e.printStackTrace();
        }
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
