package irai.mod.reforge.Common;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

/**
 * Shared helpers for reading and updating player inventory state.
 */
public final class PlayerInventoryUtils {

    private PlayerInventoryUtils() {}

    public static short getSelectedHotbarSlot(Player player) {
        if (player == null || player.getInventory() == null) {
            return -1;
        }
        try {
            return player.getInventory().getActiveHotbarSlot();
        } catch (Exception ignored) {
            return -1;
        }
    }

    public static ItemStack getSelectedHotbarItem(Player player) {
        if (player == null || player.getInventory() == null) {
            return null;
        }
        ItemContainer hotbar;
        try {
            hotbar = player.getInventory().getHotbar();
        } catch (Exception ignored) {
            return null;
        }
        if (hotbar == null) {
            return null;
        }
        short slot = getSelectedHotbarSlot(player);
        if (slot < 0 || slot >= hotbar.getCapacity()) {
            return null;
        }
        try {
            return hotbar.getItemStack(slot);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static boolean setSelectedHotbarItem(Player player, ItemStack itemStack) {
        if (player == null || itemStack == null || player.getInventory() == null) {
            return false;
        }
        ItemContainer hotbar;
        try {
            hotbar = player.getInventory().getHotbar();
        } catch (Exception ignored) {
            return false;
        }
        if (hotbar == null) {
            return false;
        }
        short slot = getSelectedHotbarSlot(player);
        if (slot < 0 || slot >= hotbar.getCapacity()) {
            return false;
        }
        try {
            hotbar.setItemStackForSlot(slot, itemStack);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static ItemStack findFirstInHotbar(Player player, Predicate<ItemStack> matcher) {
        if (player == null || matcher == null || player.getInventory() == null) {
            return null;
        }
        ItemContainer hotbar;
        try {
            hotbar = player.getInventory().getHotbar();
        } catch (Exception ignored) {
            return null;
        }
        if (hotbar == null) {
            return null;
        }

        short capacity = hotbar.getCapacity();
        short selectedSlot = getSelectedHotbarSlot(player);
        if (selectedSlot >= 0 && selectedSlot < capacity) {
            try {
                ItemStack selectedItem = hotbar.getItemStack(selectedSlot);
                if (isMatching(selectedItem, matcher)) {
                    return selectedItem;
                }
            } catch (Exception ignored) {
                // Fall through to full hotbar scan.
            }
        }

        for (short slot = 0; slot < capacity; slot++) {
            if (slot == selectedSlot) {
                continue;
            }
            try {
                ItemStack stack = hotbar.getItemStack(slot);
                if (isMatching(stack, matcher)) {
                    return stack;
                }
            } catch (Exception ignored) {
                // Continue scanning remaining slots.
            }
        }

        return null;
    }

    public static List<ItemStack> getEquippedArmor(Player player, Predicate<ItemStack> matcher) {
        List<ItemStack> equipped = new ArrayList<>();
        if (player == null || matcher == null || player.getInventory() == null) {
            return equipped;
        }
        try {
            ItemContainer armorContainer = player.getInventory().getArmor();
            if (armorContainer == null) {
                return equipped;
            }
            for (short slot = 0; slot < armorContainer.getCapacity(); slot++) {
                ItemStack stack = armorContainer.getItemStack(slot);
                if (isMatching(stack, matcher)) {
                    equipped.add(stack);
                }
            }
        } catch (Exception ignored) {
            return equipped;
        }
        return equipped;
    }

    private static boolean isMatching(ItemStack stack, Predicate<ItemStack> matcher) {
        return stack != null && !stack.isEmpty() && matcher.test(stack);
    }
}
