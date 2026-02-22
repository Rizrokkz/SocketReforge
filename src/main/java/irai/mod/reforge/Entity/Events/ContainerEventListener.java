package irai.mod.reforge.Entity.Events;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import irai.mod.reforge.Interactions.ReforgeEquip;

@SuppressWarnings("removal")
public class ContainerEventListener {

    /**
     * Scans a container and ensures all upgraded weapons/armor have correct display names.
     * Call this when opening a container or periodically.
     *
     * @param player    The player who owns the items
     * @param container The container to scan (chest, storage, etc.)
     */
    public static void syncContainerWeapons(Player player, ItemContainer container) {
        if (player == null || container == null) return;

        int syncedCount = 0;

        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack item = container.getItemStack(slot);
            if (item == null) continue;

            // Check if it's a weapon or armor
            if (!isReforgeable(item)) continue;

            // Get upgrade level
            int level = ReforgeEquip.getUpgradeLevel(player, item, slot);

            if (level > 0) {
                // Item has upgrades - ensure display name is correct
                ReforgeEquip.setUpgradeLevel(player, item, slot, level);
                syncedCount++;
            }
        }

        if (syncedCount > 0 && false) { // Set to true for debug
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw(
                    "Synced " + syncedCount + " upgraded items"
            ));
        }
    }

    /**
     * Checks if a player's inventory contains any upgraded weapons or armor.
     * Useful for deciding when to sync or save.
     */
    public static boolean hasUpgradedWeapons(Player player) {
        if (player == null) return false;

        short currentSlot = player.getInventory().getActiveHotbarSlot();
        ItemStack currentItem = player.getInventory().getHotbar().getItemStack(currentSlot);
        return currentItem != null && ReforgeEquip.getUpgradeLevel(player, currentItem, currentSlot) > 0;
    }

    /**
     * Syncs all weapons and armor in player's inventory (hotbar + storage).
     */
    public static void syncPlayerInventory(Player player) {
        if (player == null) return;

        syncContainerWeapons(player, player.getInventory().getHotbar());
        syncContainerWeapons(player, player.getInventory().getStorage());
    }

    /**
     * Called when a player picks up an item.
     * Ensures the weapon/armor upgrade data is preserved.
     */
    public static void onItemPickup(Player player, ItemStack item) {
        if (player == null || item == null) return;
        if (!isReforgeable(item)) return;

        short slot = player.getInventory().getActiveHotbarSlot();
        int level = ReforgeEquip.getUpgradeLevel(player, item, slot);

        if (level > 0) {
            ReforgeEquip.setUpgradeLevel(player, item, slot, level);
        }
    }

    /**
     * Called when a player drops an item.
     * Item keeps its upgrade data for when it's picked back up.
     */
    public static void onItemDrop(Player player, ItemStack item) {
        if (player == null || item == null) return;
        if (!isReforgeable(item)) return;

        short slot = player.getInventory().getActiveHotbarSlot();
        int level = ReforgeEquip.getUpgradeLevel(player, item, slot);

        if (level > 0 && false) { // Set to true for debug
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw(
                    "Dropped upgraded item: +" + level
            ));
        }
    }

    /**
     * Checks if an item is reforgeable (weapon or armor).
     */
    private static boolean isReforgeable(ItemStack item) {
        return ReforgeEquip.isWeapon(item) || ReforgeEquip.isArmor(item);
    }
}
