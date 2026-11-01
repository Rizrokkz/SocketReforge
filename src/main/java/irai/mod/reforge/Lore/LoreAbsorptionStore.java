package irai.mod.reforge.Lore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads legacy absorbed-lore records only so they can be removed when a player joins.
 */
public final class LoreAbsorptionStore {
    private static final Map<UUID, Set<String>> ABSORBED = new ConcurrentHashMap<>();
    private static File storeFile;

    private LoreAbsorptionStore() {}

    public static void initialize(File dataFolder) {
        if (dataFolder == null) {
            dataFolder = new File(".");
        }
        storeFile = new File(dataFolder, "lore_absorbed.properties");
        load();
    }

    /** Clears deprecated absorbed-lores state for a player and persists only when something changed. */
    public static int clearAbsorbed(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        Set<String> removed = ABSORBED.remove(playerId);
        if (removed == null || removed.isEmpty()) {
            return 0;
        }
        int clearedCount = removed.size();
        save();
        return clearedCount;
    }

    private static void load() {
        ABSORBED.clear();
        if (storeFile == null || !storeFile.exists()) {
            return;
        }
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(storeFile)) {
            props.load(in);
        } catch (Exception ignored) {
            return;
        }
        for (String key : props.stringPropertyNames()) {
            try {
                UUID playerId = UUID.fromString(key);
                String value = props.getProperty(key, "");
                Set<String> spirits = new LinkedHashSet<>();
                if (value != null && !value.isBlank()) {
                    Arrays.stream(value.split(","))
                            .map(String::trim)
                            .filter(v -> !v.isEmpty())
                            .map(LoreIds::normalizeSpiritId)
                            .forEach(spirits::add);
                }
                if (!spirits.isEmpty()) {
                    ABSORBED.put(playerId, spirits);
                }
            } catch (IllegalArgumentException ignored) {
                // Skip invalid UUID keys.
            }
        }
    }

    private static synchronized void save() {
        if (storeFile == null) {
            return;
        }
        Properties props = new Properties();
        for (Map.Entry<UUID, Set<String>> entry : ABSORBED.entrySet()) {
            UUID playerId = entry.getKey();
            Set<String> spirits = entry.getValue();
            if (playerId == null || spirits == null || spirits.isEmpty()) {
                continue;
            }
            String joined = String.join(",", spirits);
            props.setProperty(playerId.toString(), joined);
        }
        try (FileOutputStream out = new FileOutputStream(storeFile)) {
            props.store(out, "Legacy absorbed-lore data pending player cleanup");
        } catch (Exception ignored) {
            // Best-effort persistence.
        }
    }

    // normalization centralized in LoreIds
}
