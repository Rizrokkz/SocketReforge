package irai.mod.reforge.Util;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

/**
 * Utility methods for inventory item search and consumption.
 * Eliminates duplicate inventory search logic across interaction classes.
 */
public final class InventoryItemUtils {
    private InventoryItemUtils() {} // Prevent instantiation

    /**
     * Checks if a player has a specific item in their inventory or hotbar.
     *
     * @param player the player to check
     * @param itemId the item ID to search for
     * @return true if the item exists, false otherwise
     */
    public static boolean hasItem(Player player, String itemId) {
        return findItemSlot(player, itemId) != null;
    }

    /**
     * Consumes a specified amount of an item from player inventory/hotbar.
     * Searches hotbar first, then main inventory.
     *
     * @param player the player
     * @param itemId the item ID to consume
     * @param amount the quantity to consume
     * @return the amount actually consumed
     */
    public static int consumeItem(Player player, String itemId, int amount) {
        if (player == null || player.getInventory() == null || amount <= 0) {
            return 0;
        }

        int consumed = 0;
        int remaining = amount;

        // Try hotbar first
        ItemContainer hotbar = player.getInventory().getHotbar();
        if (hotbar != null && remaining > 0) {
            for (short i = 0; i < hotbar.getCapacity(); i++) {
                ItemStack stack = hotbar.getItemStack(i);
                if (stack != null && !stack.isEmpty() && itemId.equals(stack.getItemId())) {
                    int taken = Math.min(remaining, stack.getQuantity());
                    if (taken < stack.getQuantity()) {
                        // Partial consumption - create new stack with remaining quantity
                        hotbar.setItemStackForSlot(i, stack.withQuantity(stack.getQuantity() - taken));
                    } else {
                        // Full consumption - remove the item
                        hotbar.setItemStackForSlot(i, null);
                    }
                    consumed += taken;
                    remaining -= taken;
                    if (remaining <= 0) return consumed;
                }
            }
        }

        // Then main inventory (storage)
        ItemContainer storage = player.getInventory().getStorage();
        if (storage != null && remaining > 0) {
            for (short i = 0; i < storage.getCapacity(); i++) {
                ItemStack stack = storage.getItemStack(i);
                if (stack != null && !stack.isEmpty() && itemId.equals(stack.getItemId())) {
                    int taken = Math.min(remaining, stack.getQuantity());
                    if (taken < stack.getQuantity()) {
                        // Partial consumption - create new stack with remaining quantity
                        storage.setItemStackForSlot(i, stack.withQuantity(stack.getQuantity() - taken));
                    } else {
                        // Full consumption - remove the item
                        storage.setItemStackForSlot(i, null);
                    }
                    consumed += taken;
                    remaining -= taken;
                    if (remaining <= 0) return consumed;
                }
            }
        }

        return consumed;
    }

    /**
     * Finds the first slot containing a specific item in player inventory/hotbar.
     * Searches hotbar first, then main inventory.
     *
     * @param player the player
     * @param itemId the item ID to find
     * @return an {@code InventorySlot} with location info, or null if not found
     */
    private static InventorySlot findItemSlot(Player player, String itemId) {
        if (player == null || player.getInventory() == null) {
            return null;
        }

        // Check hotbar
        ItemContainer hotbar = player.getInventory().getHotbar();
        if (hotbar != null) {
            for (short i = 0; i < hotbar.getCapacity(); i++) {
                ItemStack stack = hotbar.getItemStack(i);
                if (stack != null && !stack.isEmpty() && itemId.equals(stack.getItemId())) {
                    return new InventorySlot(true, (short) i);
                }
            }
        }

        // Check main inventory (storage)
        ItemContainer storage = player.getInventory().getStorage();
        if (storage != null) {
            for (short i = 0; i < storage.getCapacity(); i++) {
                ItemStack stack = storage.getItemStack(i);
                if (stack != null && !stack.isEmpty() && itemId.equals(stack.getItemId())) {
                    return new InventorySlot(false, (short) i);
                }
            }
        }

        return null;
    }

    /**
     * Record for tracking inventory location (hotbar vs main).
     */
    public static final class InventorySlot {
        public final boolean isHotbar;
        public final short slot;

        InventorySlot(boolean isHotbar, short slot) {
            this.isHotbar = isHotbar;
            this.slot = slot;
        }
    }
}
