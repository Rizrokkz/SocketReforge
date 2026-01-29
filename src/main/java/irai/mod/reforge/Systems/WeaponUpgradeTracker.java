package irai.mod.reforge.Systems;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced weapon upgrade tracker with proper unique weapon instance tracking.
 */
@SuppressWarnings("removal")
public class WeaponUpgradeTracker  {

    // Thread-safe in-memory storage: "playerUUID:uniqueWeaponID" -> WeaponData
    private static final Map<String, WeaponData> weaponUpgrades = new ConcurrentHashMap<>();

    // Store weapon instance UUIDs: "playerUUID:itemID:slot" -> instanceUUID
    private static final Map<String, UUID> weaponInstances = new ConcurrentHashMap<>();

    // Upgrade level display names
    private static final String[] UPGRADE_NAMES = {
            "",           // Base
            "Sharp",      // +1
            "Deadly",     // +2
            "Legendary"   // +3
    };

    // Auto-save settings
    private static File saveDirectory;
    private static boolean autoSaveEnabled = true;
    private static int changesSinceLastSave = 0;
    private static final int AUTO_SAVE_THRESHOLD = 10;

    // ══════════════════════════════════════════════════════════════════════════════
    // Initialization & Persistence
    // ══════════════════════════════════════════════════════════════════════════════

    public static void initialize(File pluginDataFolder) {
        saveDirectory = pluginDataFolder;

        try {
            Map<String, WeaponPersistence.WeaponSaveData> savedData =
                    WeaponPersistence.loadFromFile(saveDirectory);

            for (Map.Entry<String, WeaponPersistence.WeaponSaveData> entry : savedData.entrySet()) {
                WeaponPersistence.WeaponSaveData saveData = entry.getValue();
                WeaponData weaponData = new WeaponData(
                        saveData.level,
                        saveData.itemId,
                        saveData.instanceUUID  // NEW: Store instance UUID
                );
                weaponUpgrades.put(entry.getKey(), weaponData);
            }

            System.out.println("[WeaponUpgradeTracker] Initialized with " +
                    weaponUpgrades.size() + " weapons");
        } catch (Exception e) {
            System.err.println("[WeaponUpgradeTracker] Failed to initialize: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Unique Weapon Instance Management
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Generates or retrieves a unique ID for a weapon instance.
     * This ensures each physical weapon item gets its own upgrades.
     */
    private static UUID getOrCreateWeaponInstanceId(Player player, ItemStack weapon, short slot) {
        if (player == null || weapon == null) {
            return null;
        }

        UUID playerUUID = player.getPlayerRef().getUuid();
        String itemId = getItemId(weapon);

        // Create a composite key: player + item + slot
        String instanceKey = playerUUID + ":" + itemId + ":" + slot + ":" +
                System.identityHashCode(weapon);

        // Check if we already have an instance ID for this weapon
        UUID instanceId = weaponInstances.get(instanceKey);

        if (instanceId == null) {
            // Generate new unique ID for this weapon instance
            instanceId = UUID.randomUUID();
            weaponInstances.put(instanceKey, instanceId);

            System.out.println("[WeaponUpgradeTracker] Created new weapon instance: " +
                    instanceId + " for " + itemId + " in slot " + slot);
        }

        return instanceId;
    }

    /**
     * Creates a unique key for a weapon using player UUID + instance UUID.
     */
    private static String createWeaponKey(UUID playerUUID, UUID instanceUUID) {
        if (playerUUID == null || instanceUUID == null) {
            return null;
        }
        return playerUUID.toString() + ":" + instanceUUID.toString();
    }

    /**
     * Creates a key from an ItemStack (when you have the actual item).
     */
    private static String createWeaponKey(Player player, ItemStack weapon, short slot) {
        if (player == null || weapon == null) {
            return null;
        }

        UUID playerUUID = player.getPlayerRef().getUuid();
        UUID instanceUUID = getOrCreateWeaponInstanceId(player, weapon, slot);

        return createWeaponKey(playerUUID, instanceUUID);
    }

    /**
     * Finds a weapon key by scanning all player weapons for matching item ID.
     * Used when we don't have the original slot/instance.
     */
    private static String findWeaponKeyByItem(UUID playerUUID, String itemId) {
        String prefix = playerUUID.toString() + ":";

        for (Map.Entry<String, WeaponData> entry : weaponUpgrades.entrySet()) {
            if (entry.getKey().startsWith(prefix) &&
                    entry.getValue().itemId.equals(itemId)) {
                return entry.getKey();
            }
        }

        return null;
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Core Tracking Methods
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Gets the upgrade level for a specific weapon instance.
     */
    public static int getUpgradeLevel(Player player, ItemStack weapon, short slot) {
        if (player == null || weapon == null) {
            return 0;
        }

        try {
            // Try to get the current slot
            slot = player.getInventory().getActiveHotbarSlot();
            String key = createWeaponKey(player, weapon, slot);

            if (key == null) {
                return 0;
            }

            WeaponData data = weaponUpgrades.get(key);
            if (data != null) {
                return data.level;
            }

            // Fallback: search by item ID
            String itemId = getItemId(weapon);
            String foundKey = findWeaponKeyByItem(player.getPlayerRef().getUuid(), itemId);

            if (foundKey != null) {
                data = weaponUpgrades.get(foundKey);
                return data != null ? data.level : 0;
            }

            return 0;

        } catch (Exception e) {
            System.err.println("[WeaponUpgradeTracker] Error getting upgrade level: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Sets the upgrade level for a specific weapon instance.
     */
    public static void setUpgradeLevel(Player player, ItemStack weapon, short slot, int level) {
        if (player == null || weapon == null || level < 0 || level > 3) {
            return;
        }

        try {
            String key = createWeaponKey(player, weapon, slot);
            if (key == null) {
                return;
            }

            String itemId = getItemId(weapon);
            UUID instanceUUID = getOrCreateWeaponInstanceId(player, weapon, slot);

            WeaponData data = new WeaponData(level, itemId, instanceUUID);
            weaponUpgrades.put(key, data);

            // Update the display name
            updateWeaponName(weapon, level);

            // Trigger auto-save
            checkAutoSave();

            System.out.println("[WeaponUpgradeTracker] Set upgrade level +" + level +
                    " for " + itemId + " in slot " + slot + " (Instance: " + instanceUUID + ")");

        } catch (Exception e) {
            System.err.println("[WeaponUpgradeTracker] Failed to set upgrade level: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Alternative method that automatically detects the slot.
     */
    public static void setUpgradeLevel(Player player, ItemStack weapon, int level) {
        try {
            short slot = player.getInventory().getActiveHotbarSlot();
            setUpgradeLevel(player, weapon, slot, level);
        } catch (Exception e) {
            System.err.println("[WeaponUpgradeTracker] Failed to detect slot: " + e.getMessage());
        }
    }

    /**
     * Removes a specific weapon instance from tracking.
     */
    public static void removeWeapon(Player player, ItemStack weapon, short slot) {
        if (player == null || weapon == null) {
            return;
        }

        String key = createWeaponKey(player, weapon, slot);
        if (key != null) {
            WeaponData removed = weaponUpgrades.remove(key);
            if (removed != null) {
                System.out.println("[WeaponUpgradeTracker] Removed weapon: " +
                        removed.itemId + " from slot " + slot);
                checkAutoSave();
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Display Name Management
    // ══════════════════════════════════════════════════════════════════════════════

    private static void updateWeaponName(ItemStack weapon, int level) {
        if (level <= 0 || level >= UPGRADE_NAMES.length) {
            return;
        }

        try {
            String upgradeName = UPGRADE_NAMES[level];
            if (upgradeName.isEmpty()) {
                return;
            }

            String currentName = weapon.getItem().getTranslationProperties().getName();
            if (currentName == null || currentName.isEmpty()) {
                currentName = getItemId(weapon);
            }

            // Remove previous upgrade prefix if present
            for (String prefix : UPGRADE_NAMES) {
                if (!prefix.isEmpty() && currentName.startsWith(prefix + " ")) {
                    currentName = currentName.substring(prefix.length() + 1);
                    break;
                }
            }

            String colorCode = getColorForLevel(level);
            String newName = colorCode + upgradeName + " " + currentName;
            weapon.getItem().getTranslationProperties().getName().concat(newName);

        } catch (Exception e) {
            System.err.println("[WeaponUpgradeTracker] Cannot update name: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Utility Methods
    // ══════════════════════════════════════════════════════════════════════════════

    private static String getItemId(ItemStack item) {
        if (item == null) return null;

        try {
            return item.getItemId();
        } catch (Exception e1) {
            try {
                if (item != null) {
                    return item.getItemId();
                }
            } catch (Exception e2) {
                try {
                    if (item.getItem() != null) {
                        return item.getItem().getId();
                    }
                } catch (Exception e3) {
                    try {
                        return item.getItemId();
                    } catch (Exception e4) {
                        return "unknown";
                    }
                }
            }
        }
        return null;
    }

    private static String getColorForLevel(int level) {
        return switch (level) {
            case 1 -> ""; // Green
            case 2 -> ""; // Aqua
            case 3 -> ""; // Gold
            default -> ""; // White
        };
    }

    private static void checkAutoSave() {
        if (!autoSaveEnabled) return;

        changesSinceLastSave++;
        if (changesSinceLastSave >= AUTO_SAVE_THRESHOLD) {
            System.out.println("[WeaponUpgradeTracker] Auto-saving (" +
                    changesSinceLastSave + " changes)");
            saveAll();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Save/Load Updates
    // ══════════════════════════════════════════════════════════════════════════════
    public static void createBackup() {
        if (saveDirectory == null) {
            System.err.println("[WeaponUpgradeTracker] Cannot create backup: not initialized!");
            return;
        }

        try {
            System.out.println("[WeaponUpgradeTracker] Creating backup...");

            // Call the persistence layer's backup method
            WeaponPersistence.createBackup(saveDirectory);

            System.out.println("[WeaponUpgradeTracker] ✓ Backup created successfully");

        } catch (Exception e) {
            System.err.println("[WeaponUpgradeTracker] Failed to create backup: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void saveAll() {
        if (saveDirectory == null) {
            System.err.println("[WeaponUpgradeTracker] Cannot save: not initialized!");
            return;
        }

        try {
            Map<String, WeaponPersistence.WeaponSaveData> saveData = new HashMap<>();

            for (Map.Entry<String, WeaponData> entry : weaponUpgrades.entrySet()) {
                String key = entry.getKey();
                WeaponData data = entry.getValue();

                // Extract player UUID from key
                String[] parts = key.split(":");
                String playerUUID = parts.length > 0 ? parts[0] : "unknown";

                WeaponPersistence.WeaponSaveData weaponSaveData =
                        new WeaponPersistence.WeaponSaveData(
                                data.level,
                                data.itemId,
                                data.instanceUUID,  // NEW: Save instance UUID
                                System.currentTimeMillis(),
                                playerUUID
                        );

                saveData.put(key, weaponSaveData);
            }

            WeaponPersistence.saveToFile(saveData, saveDirectory);
            changesSinceLastSave = 0;
            System.out.println("[WeaponUpgradeTracker] Saved " +
                    saveData.size() + " weapon instances");

        } catch (Exception e) {
            System.err.println("[WeaponUpgradeTracker] Failed to save: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Additional Utility Methods
    // ══════════════════════════════════════════════════════════════════════════════

    public static String getUpgradeName(int level) {
        if (level < 0 || level >= UPGRADE_NAMES.length) {
            return "";
        }
        return UPGRADE_NAMES[level];
    }

    public static double getDamageMultiplier(int level) {
        double[] multipliers = {1.0, 1.10, 1.15, 1.25};
        if (level < 0 || level >= multipliers.length) {
            return 1.0;
        }
        return multipliers[level];
    }

    /**
     * Gets all weapons owned by a player.
     */
    public static Map<String, WeaponData> getPlayerWeapons(UUID playerUUID) {
        if (playerUUID == null) {
            return new HashMap<>();
        }

        String prefix = playerUUID.toString() + ":";
        Map<String, WeaponData> playerWeapons = new HashMap<>();

        for (Map.Entry<String, WeaponData> entry : weaponUpgrades.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                playerWeapons.put(entry.getKey(), entry.getValue());
            }
        }

        System.out.println("[WeaponUpgradeTracker] Found " + playerWeapons.size() +
                " weapons for player " + playerUUID);

        return playerWeapons;
    }

    public static int getTrackedWeaponCount() {
        return weaponUpgrades.size();
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Inner Classes
    // ══════════════════════════════════════════════════════════════════════════════

    public static class WeaponData {
        public final int level;
        public final String itemId;
        public final UUID instanceUUID;  // NEW: Unique instance identifier

        public WeaponData(int level, String itemId, UUID instanceUUID) {
            this.level = level;
            this.itemId = itemId;
            this.instanceUUID = instanceUUID;
        }
    }
}