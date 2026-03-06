package irai.mod.reforge.Common;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;

/**
 * Shared helpers for weapon/armor detection.
 * Uses item metadata first (getWeapon/getArmor), then falls back to ID heuristics.
 */
public final class ItemTypeUtils {

    private static final Map<String, Boolean> weaponIdCache = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> armorIdCache = new ConcurrentHashMap<>();

    private ItemTypeUtils() {}

    public static boolean isWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        Item item = stack.getItem();
        if (item != null && item != Item.UNKNOWN) {
            if (item.getWeapon() != null) {
                return !isNonEquipmentWeaponId(stack.getItemId());
            }
            if (item.getArmor() != null) {
                return false;
            }
        }

        return isWeaponFallbackId(stack.getItemId());
    }

    public static boolean isArmor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        Item item = stack.getItem();
        if (item != null && item != Item.UNKNOWN) {
            if (item.getArmor() != null) {
                return true;
            }
            if (item.getWeapon() != null) {
                return false;
            }
        }

        return isArmorFallbackId(stack.getItemId());
    }

    public static boolean isEquipment(ItemStack stack) {
        return isWeapon(stack) || isArmor(stack);
    }

    public static boolean isWeaponItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }

        String cacheKey = itemId.trim();
        Boolean cached = weaponIdCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        boolean result = isWeaponByMetadata(cacheKey);
        if (!result) {
            result = isWeaponFallbackId(cacheKey);
        }

        weaponIdCache.put(cacheKey, result);
        return result;
    }

    public static boolean isArmorItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }

        String cacheKey = itemId.trim();
        Boolean cached = armorIdCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        boolean result = isArmorByMetadata(cacheKey);
        if (!result) {
            result = isArmorFallbackId(cacheKey);
        }

        armorIdCache.put(cacheKey, result);
        return result;
    }

    public static boolean isEquipmentItemId(String itemId) {
        return isWeaponItemId(itemId) || isArmorItemId(itemId);
    }

    public static void clearCaches() {
        weaponIdCache.clear();
        armorIdCache.clear();
    }

    private static boolean isWeaponByMetadata(String itemId) {
        Item item = resolveItem(itemId);
        return item != null
                && item.getWeapon() != null
                && !isNonEquipmentWeaponId(itemId);
    }

    private static boolean isArmorByMetadata(String itemId) {
        Item item = resolveItem(itemId);
        return item != null && item.getArmor() != null;
    }

    private static Item resolveItem(String itemId) {
        try {
            ItemStack probe = new ItemStack(itemId, 1);
            if (probe == null || probe.isEmpty()) {
                return null;
            }
            Item item = probe.getItem();
            if (item == null || item == Item.UNKNOWN) {
                return null;
            }
            return item;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isWeaponFallbackId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        String lower = itemId.toLowerCase(Locale.ROOT);
        if (isNonEquipmentWeaponIdLower(lower)) {
            return false;
        }
        return lower.startsWith("weapon_") || lower.contains("weapon");
    }

    private static boolean isArmorFallbackId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        String lower = itemId.toLowerCase(Locale.ROOT);
        return lower.startsWith("armor_")
                || lower.startsWith("armour_")
                || lower.contains("armor")
                || lower.contains("armour");
    }

    private static boolean isNonEquipmentWeaponId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        return isNonEquipmentWeaponIdLower(itemId.toLowerCase(Locale.ROOT));
    }

    private static boolean isNonEquipmentWeaponIdLower(String lowerItemId) {
        return containsAny(lowerItemId, "arrow", "bolt", "projectile", "ammo", "ammunition")
                || containsAny(lowerItemId, "_bomb", "bomb_", "_tnt", "tnt_", "_dynamite", "dynamite_", "explosive", "_mine")
                || containsAny(lowerItemId, "deployable", "placeable", "turret", "trap", "totem", "banner", "flag", "ward");
    }

    private static boolean containsAny(String value, String... fragments) {
        if (value == null || fragments == null) {
            return false;
        }
        for (String fragment : fragments) {
            if (fragment != null && !fragment.isEmpty() && value.contains(fragment)) {
                return true;
            }
        }
        return false;
    }
}
