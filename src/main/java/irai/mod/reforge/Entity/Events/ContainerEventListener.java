package irai.mod.reforge.Entity.Events;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import irai.mod.reforge.Systems.WeaponUpgradeTracker;

import java.util.regex.Pattern;

@SuppressWarnings("removal")
public class ContainerEventListener {

    private static final Pattern WEAPON_PATTERN = Pattern.compile(".*[Ww]eapon.*");

    /**
     * Scans a container and ensures all upgraded weapons have correct display names.
     * Call this when opening a container or periodically.
     *
     * @param player The player who owns the weapons
     * @param container The container to scan (chest, storage, etc.)
     */
    public static void syncContainerWeapons(Player player, ItemContainer container) {
        if (player == null || container == null) {
            return;
        }

        int syncedCount = 0;

        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack item = container.getItemStack(slot);

            if (item == null) {
                continue;
            }

            // Check if it's a weapon
            if (!isWeapon(item.getItemId())) {
                continue;
            }

            // Get upgrade level
            int level = WeaponUpgradeTracker.getUpgradeLevel(player, item, slot);

            if (level > 0) {
                // Weapon has upgrades - ensure display name is correct
                WeaponUpgradeTracker.setUpgradeLevel(player, item, level);
                syncedCount++;
            }
        }

        if (syncedCount > 0 && false) { // Set to true for debug
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw(
                    "Synced " + syncedCount + " upgraded weapons"
            ));
        }
    }

    /**
     * Checks if a player's inventory contains any upgraded weapons.
     * Useful for deciding when to sync or save.
     */
    public static boolean hasUpgradedWeapons(Player player) {
        if (player == null) {
            return false;
        }

        return !WeaponUpgradeTracker.getPlayerWeapons(player.getPlayerRef().getUuid()).isEmpty();
    }

    /**
     * Syncs all weapons in player's inventory (hotbar + storage).
     */
    public static void syncPlayerInventory(Player player) {
        if (player == null) {
            return;
        }

        // Sync hotbar
        syncContainerWeapons(player, player.getInventory().getHotbar());

        // Sync storage
        syncContainerWeapons(player, player.getInventory().getStorage());
    }

    /**
     * Called when a player picks up an item.
     * Ensures the weapon's upgrade data is preserved.
     */
    public static void onItemPickup(Player player, ItemStack item) {
        if (player == null || item == null) {
            return;
        }

        if (!isWeapon(item.getItemId())) {
            return;
        }
        short slot = player.getInventory().getActiveHotbarSlot();
        // Check if this weapon has upgrades
        int level = WeaponUpgradeTracker.getUpgradeLevel(player, item, slot);

        if (level > 0) {
            // Refresh display name
            WeaponUpgradeTracker.setUpgradeLevel(player, item, level);
        }
    }

    /**
     * Called when a player drops an item.
     * Weapon keeps its upgrade data for when it's picked back up.
     */
    public static void onItemDrop(Player player, ItemStack item) {
        if (player == null || item == null) {
            return;
        }

        if (!isWeapon(item.getItemId())) {
            return;
        }
        short slot = player.getInventory().getActiveHotbarSlot();
        // Data stays in tracker - will be restored when picked up
        int level = WeaponUpgradeTracker.getUpgradeLevel(player, item, slot);

        if (level > 0 && false) { // Set to true for debug
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw(
                    "Dropped upgraded weapon: +" + level
            ));
        }
    }

    /**
     * Checks if an item is a weapon.
     */
    private static boolean isWeapon(String itemId) {
        return itemId != null && WEAPON_PATTERN.matcher(itemId).matches();
    }
}