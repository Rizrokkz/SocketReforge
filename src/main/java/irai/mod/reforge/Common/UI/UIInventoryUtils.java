package irai.mod.reforge.Common.UI;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

/**
 * Shared helpers for hotbar/storage inventory operations used by bench UIs.
 */
public final class UIInventoryUtils {

    private UIInventoryUtils() {}

    public static ItemContainer getContainer(Player player, boolean hotbar) {
        if (player == null || player.getInventory() == null) {
            return null;
        }
        return hotbar ? player.getInventory().getHotbar() : player.getInventory().getStorage();
    }

    public static ItemStack readItem(Player player, boolean hotbar, short slot) {
        ItemContainer container = getContainer(player, hotbar);
        if (container == null) {
            return null;
        }
        return container.getItemStack(slot);
    }

    public static boolean writeItem(Player player, boolean hotbar, short slot, ItemStack stack) {
        if (stack == null) {
            return false;
        }
        ItemContainer container = getContainer(player, hotbar);
        if (container == null) {
            return false;
        }
        container.setItemStackForSlot(slot, stack);
        return true;
    }

    public static boolean removeItem(Player player, boolean hotbar, short slot, int amount) {
        if (amount <= 0) {
            return false;
        }
        ItemContainer container = getContainer(player, hotbar);
        if (container == null) {
            return false;
        }
        container.removeItemStackFromSlot(slot, amount, false, false);
        return true;
    }

    public static boolean hasItemAmount(Player player,
                                        boolean hotbar,
                                        short slot,
                                        String expectedItemId,
                                        int minAmount) {
        if (expectedItemId == null || expectedItemId.isEmpty() || minAmount <= 0) {
            return false;
        }
        ItemStack stack = readItem(player, hotbar, slot);
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (!expectedItemId.equalsIgnoreCase(stack.getItemId())) {
            return false;
        }
        return stack.getQuantity() >= minAmount;
    }

    public static boolean consumeItem(Player player,
                                      boolean hotbar,
                                      short slot,
                                      String expectedItemId,
                                      int amount) {
        if (!hasItemAmount(player, hotbar, slot, expectedItemId, amount)) {
            return false;
        }
        return removeItem(player, hotbar, slot, amount);
    }
}
