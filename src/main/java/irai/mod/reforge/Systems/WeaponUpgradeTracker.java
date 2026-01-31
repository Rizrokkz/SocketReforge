package irai.mod.reforge.Systems;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import java.io.File;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Weapon tracker using a deterministic hash of weapon properties.
 * This creates a stable ID that persists even when weapons are traded/dropped.
 */
@SuppressWarnings("removal")
public class WeaponUpgradeTracker {

    // Main storage: weaponHash -> WeaponData
    private static final Map<String, WeaponData> weaponUpgrades = new ConcurrentHashMap<>();

    // Reverse lookup: itemId -> List of hashes (for finding weapons)
    private static final Map<String, Set<String>> itemIdIndex = new ConcurrentHashMap<>();

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
    // Initialization
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
                        saveData.instanceUUID
                );

                String hash = entry.getKey();
                weaponUpgrades.put(hash, weaponData);

                // Build index
                itemIdIndex.computeIfAbsent(saveData.itemId, k -> ConcurrentHashMap.newKeySet()).add(hash);
            }

            System.out.println("[WeaponUpgradeTracker] Loaded " + weaponUpgrades.size() + " weapons");
            System.out.println("[WeaponUpgradeTracker] Indexed " + itemIdIndex.size() + " item types");

        } catch (Exception e) {
            System.err.println("[WeaponUpgradeTracker] Init failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Weapon Hash Generation - Creates stable ID for weapon instances
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Creates a deterministic hash for a weapon based on its properties.
     * This hash should be stable enough to track the weapon across trades.
     */
    private static String createWeaponHash(Player player, ItemStack weapon, short slot) {
        if (weapon == null) {
            return null;
        }

        try {
            String itemId = getItemId(weapon);
            int maxDurability = (int) weapon.getMaxDurability();

            // Use player UUID + itemId + slot + max durability as base
            // This creates a unique ID per player's weapon in a specific slot
            UUID playerUUID = player.getPlayerRef().getUuid();

            String baseString = playerUUID.toString() + ":" + itemId + ":" + slot + ":" + maxDurability;

            // Create a shorter hash
            String hash = simpleHash(baseString);

            System.out.println("[WeaponUpgradeTracker] Hash: " + hash + " for " + itemId + " (Player: " +
                    player.getPlayerRef().getUsername() + ", Slot: " + slot + ")");

            return hash;

        } catch (Exception e) {
            System.err.println("[WeaponUpgradeTracker] Hash generation failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Simple hash function for creating weapon IDs.
     */
    private static String simpleHash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < Math.min(8, hash.length); i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Core Tracking Methods
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Gets upgrade level for a weapon.
     */
    public static int getUpgradeLevel(Player player, ItemStack weapon, short slot) {
        if (player == null || weapon == null) {
            return 0;
        }

        try {
            String hash = createWeaponHash(player, weapon, slot);
            if (hash == null) {
                return 0;
            }

            WeaponData data = weaponUpgrades.get(hash);
            if (data != null) {
                System.out.println("[WeaponUpgradeTracker] Found weapon: " + hash + " -> +" + data.level);
                return data.level;
            }

            // Fallback: Search by item ID
            String itemId = getItemId(weapon);
            Set<String> hashes = itemIdIndex.get(itemId);
            if (hashes != null && !hashes.isEmpty()) {
                // Return the first matching weapon's level
                for (String h : hashes) {
                    WeaponData d = weaponUpgrades.get(h);
                    if (d != null) {
                        System.out.println("[WeaponUpgradeTracker] Found by itemId: " + itemId + " -> +" + d.level);
                        return d.level;
                    }
                }
            }

            System.out.println("[WeaponUpgradeTracker] No upgrade found for: " + hash + " (" + itemId + ")");
            return 0;

        } catch (Exception e) {
            System.err.println("[WeaponUpgradeTracker] Error getting level: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Sets upgrade level for a weapon.
     */
    public static void setUpgradeLevel(Player player, ItemStack weapon, short slot, int level) {
        if (player == null || weapon == null || level < 0 || level > 3) {
            return;
        }

        try {
            String hash = createWeaponHash(player, weapon, slot);
            if (hash == null) {
                return;
            }

            String itemId = getItemId(weapon);
            UUID instanceUUID = UUID.randomUUID();

            WeaponData data = new WeaponData(level, itemId, instanceUUID);
            weaponUpgrades.put(hash, data);

            // Update index
            itemIdIndex.computeIfAbsent(itemId, k -> ConcurrentHashMap.newKeySet()).add(hash);

            // Update display name
            updateWeaponName(weapon, level);

            checkAutoSave();

            String playerName = player.getPlayerRef().getUsername();
            System.out.println("[WeaponUpgradeTracker] SET: " + hash + " -> +" + level +
                    " (" + itemId + ", Player: " + playerName + ", Slot: " + slot + ")");

            // Debug: Print current state
            System.out.println("[WeaponUpgradeTracker] Total weapons tracked: " + weaponUpgrades.size());

        } catch (Exception e) {
            System.err.println("[WeaponUpgradeTracker] Failed to set level: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Alternative without slot parameter.
     */
    public static void setUpgradeLevel(Player player, ItemStack weapon, int level) {
        try {
            short slot = player.getInventory().getActiveHotbarSlot();
            setUpgradeLevel(player, weapon, slot, level);
        } catch (Exception e) {
            setUpgradeLevel(player, weapon, (short) 0, level);
        }
    }

    /**
     * Removes a weapon from tracking.
     */
    public static void removeWeapon(Player player, ItemStack weapon, short slot) {
        if (player == null || weapon == null) {
            return;
        }

        try {
            String hash = createWeaponHash(player, weapon, slot);
            if (hash != null) {
                WeaponData removed = weaponUpgrades.remove(hash);
                if (removed != null) {
                    System.out.println("[WeaponUpgradeTracker] Removed: " + hash + " (" + removed.itemId + ")");

                    // Clean up index
                    Set<String> hashes = itemIdIndex.get(removed.itemId);
                    if (hashes != null) {
                        hashes.remove(hash);
                    }

                    checkAutoSave();
                }
            }
        } catch (Exception e) {
            System.err.println("[WeaponUpgradeTracker] Error removing weapon: " + e.getMessage());
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

            // Remove previous upgrade prefix
            for (String prefix : UPGRADE_NAMES) {
                if (!prefix.isEmpty() && currentName.startsWith(prefix + " ")) {
                    currentName = currentName.substring(prefix.length() + 1);
                    break;
                }
            }

            String colorCode = getColorForLevel(level);
            String newName = colorCode + "[+" + level + "] " + upgradeName + " " + currentName;
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
        } catch (Exception e) {
            try {
                if (item.getItem() != null) {
                    return item.getItem().getId();
                }
            } catch (Exception e2) {
                return "unknown";
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
            System.out.println("[WeaponUpgradeTracker] Auto-save triggered");
            saveAll();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Persistence
    // ══════════════════════════════════════════════════════════════════════════════

    public static void createBackup() {
        if (saveDirectory != null) {
            WeaponPersistence.createBackup(saveDirectory);
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
                String hash = entry.getKey();
                WeaponData data = entry.getValue();

                WeaponPersistence.WeaponSaveData save = new WeaponPersistence.WeaponSaveData(
                        data.level,
                        data.itemId,
                        data.instanceUUID,
                        System.currentTimeMillis(),
                        hash // Store hash as playerName field for debugging
                );

                saveData.put(hash, save);
            }

            WeaponPersistence.saveToFile(saveData, saveDirectory);
            changesSinceLastSave = 0;

            System.out.println("[WeaponUpgradeTracker] Saved " + saveData.size() + " weapons");

        } catch (Exception e) {
            System.err.println("[WeaponUpgradeTracker] Save failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Public API
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

    public static Map<String, WeaponData> getPlayerWeapons(UUID playerUUID) {
        // Return all weapons that belong to this player
        Map<String, WeaponData> playerWeapons = new HashMap<>();
        String playerPrefix = playerUUID.toString();

        for (Map.Entry<String, WeaponData> entry : weaponUpgrades.entrySet()) {
            if (entry.getKey().contains(playerPrefix)) {
                playerWeapons.put(entry.getKey(), entry.getValue());
            }
        }

        return playerWeapons;
    }

    public static int getTrackedWeaponCount() {
        return weaponUpgrades.size();
    }

    /**
     * Debug method - prints all tracked weapons.
     */
    public static void debugPrintAll() {
        System.out.println("═══════════════════════════════════════════════");
        System.out.println("Tracked weapons: " + weaponUpgrades.size());
        for (Map.Entry<String, WeaponData> entry : weaponUpgrades.entrySet()) {
            System.out.println("  " + entry.getKey() + " -> " + entry.getValue().itemId + " +" + entry.getValue().level);
        }
        System.out.println("═══════════════════════════════════════════════");
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Inner Classes
    // ══════════════════════════════════════════════════════════════════════════════

    public static class WeaponData {
        public final int level;
        public final String itemId;
        public final UUID instanceUUID;

        public WeaponData(int level, String itemId, UUID instanceUUID) {
            this.level = level;
            this.itemId = itemId;
            this.instanceUUID = instanceUUID;
        }
    }
}