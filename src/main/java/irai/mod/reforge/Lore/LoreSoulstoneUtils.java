package irai.mod.reforge.Lore;

import java.util.Locale;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import irai.mod.reforge.Common.UI.UIInventoryUtils;
import irai.mod.reforge.Util.MetadataKeys;

/** Utilities for colored, spirit-infused lore soulstones. */
public final class LoreSoulstoneUtils {
    private static final String INFUSED_PREFIX = "Soulstone_Infused_";
    private static final String RAW_PREFIX = "Soulstone_Raw_";

    public enum InfusionResult {
        SUCCESS,
        MISSING_MATCHING_RAW_SOULSTONE,
        INVENTORY_FULL
    }

    private LoreSoulstoneUtils() {}

    public static String getSpiritId(ItemStack item) {
        if (!isInfusedSoulstone(item)) {
            return null;
        }
        String spiritId = item.getFromMetadataOrNull(MetadataKeys.LORE_SOULSTONE_SPIRIT, Codec.STRING);
        return spiritId == null || spiritId.isBlank() ? null : spiritId.trim();
    }

    public static boolean isInfusedSoulstone(ItemStack item) {
        return item != null && !item.isEmpty()
                && isInfusedSoulstoneId(item.getItemId());
    }

    public static boolean isInfusedSoulstoneId(String itemId) {
        return itemId != null
                && itemId.toLowerCase(Locale.ROOT).startsWith(INFUSED_PREFIX.toLowerCase(Locale.ROOT));
    }

    public static ItemStack createInfusedSoulstone(String spiritId) {
        if (spiritId == null || spiritId.isBlank()) {
            return null;
        }
        String color = LoreGemRegistry.resolveSpiritColor(spiritId);
        if (color == null || color.isBlank()) {
            return null;
        }
        String normalizedColor = color.trim().toLowerCase(Locale.ROOT);
        if (!LoreGemRegistry.getKnownColors().stream().anyMatch(known -> known.equalsIgnoreCase(normalizedColor))) {
            return null;
        }
        String suffix = normalizedColor.substring(0, 1).toUpperCase(Locale.ROOT) + normalizedColor.substring(1);
        String itemId = INFUSED_PREFIX + suffix;
        return new ItemStack(itemId, 1)
                .withMetadata(MetadataKeys.LORE_SOULSTONE_SPIRIT, Codec.STRING, spiritId.trim());
    }

    /**
     * Converts one raw soulstone of the infused stone's color into the infused stone.
     * A single-item raw stack can be replaced in place when the inventory is otherwise full.
     */
    public static InfusionResult infuseWithRawSoulstone(Player player, ItemStack infusedSoulstone) {
        if (player == null || player.getInventory() == null || !isInfusedSoulstone(infusedSoulstone)) {
            return InfusionResult.MISSING_MATCHING_RAW_SOULSTONE;
        }

        String itemId = infusedSoulstone.getItemId();
        String color = itemId.substring(INFUSED_PREFIX.length());
        if (color.isBlank()) {
            return InfusionResult.MISSING_MATCHING_RAW_SOULSTONE;
        }
        String rawItemId = RAW_PREFIX + color;

        ItemContainer[] containers = {
                player.getInventory().getHotbar(),
                player.getInventory().getStorage()
        };
        boolean foundMatchingRaw = false;
        for (ItemContainer container : containers) {
            if (container == null) {
                continue;
            }
            for (short slot = 0; slot < container.getCapacity(); slot++) {
                ItemStack rawStack = container.getItemStack(slot);
                if (rawStack == null || rawStack.isEmpty() || rawStack.getItemId() == null
                        || !rawItemId.equalsIgnoreCase(rawStack.getItemId())) {
                    continue;
                }
                foundMatchingRaw = true;

                // Add first when possible so a failed add never consumes the raw stone.
                if (UIInventoryUtils.addItemToInventory(player, infusedSoulstone)) {
                    container.removeItemStackFromSlot(slot, 1, false, false);
                    return InfusionResult.SUCCESS;
                }

                // Replacing a single raw stone also works when the inventory has no spare slot.
                if (rawStack.getQuantity() == 1) {
                    container.setItemStackForSlot(slot, infusedSoulstone);
                    return InfusionResult.SUCCESS;
                }
            }
        }

        return foundMatchingRaw
                ? InfusionResult.INVENTORY_FULL
                : InfusionResult.MISSING_MATCHING_RAW_SOULSTONE;
    }
}
