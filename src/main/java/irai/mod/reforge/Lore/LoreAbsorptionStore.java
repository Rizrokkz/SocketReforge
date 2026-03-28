package irai.mod.reforge.Lore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent store of absorbed lore spirits per player.
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

    public static boolean isAbsorbed(UUID playerId, String spiritId) {
        if (playerId == null || spiritId == null || spiritId.isBlank()) {
            return false;
        }
        Set<String> set = ABSORBED.get(playerId);
        if (set == null || set.isEmpty()) {
            return false;
        }
        return set.contains(LoreIds.normalizeSpiritId(spiritId));
    }

    public static Set<String> getAbsorbed(UUID playerId) {
        if (playerId == null) {
            return Set.of();
        }
        Set<String> set = ABSORBED.get(playerId);
        if (set == null || set.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(set);
    }

    public static boolean addAbsorbed(UUID playerId, String spiritId) {
        if (playerId == null || spiritId == null || spiritId.isBlank()) {
            return false;
        }
        String normalized = LoreIds.normalizeSpiritId(spiritId);
        Set<String> set = ABSORBED.computeIfAbsent(playerId, ignored -> new LinkedHashSet<>());
        boolean added = set.add(normalized);
        if (added) {
            save();
        }
        return added;
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
            props.store(out, "Lore absorbed spirits");
        } catch (Exception ignored) {
            // Best-effort persistence.
        }
    }

    // normalization centralized in LoreIds
}
