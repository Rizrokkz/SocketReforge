package irai.mod.reforge.Common;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;

/**
 * Shared helpers for weapon/armor/tool detection.
 * Uses item metadata first, then falls back to ID heuristics where needed.
 */
public final class ItemTypeUtils {

    private static final Map<String, Boolean> weaponIdCache = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> armorIdCache = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> toolIdCache = new ConcurrentHashMap<>();
    private static final Set<String> excludedToolIdsLower = Set.of(
            "tool_bark_scraper",
            "tool_capture_crate",
            "tool_feedbag",
            "tool_fertilizer",
            "tool_fishing_trap",
            "tool_growth_potion",
            "tool_hammer_crude",
            "tool_hammer_iron",
            "tool_map",
            "tool_repair_kit_crude",
            "tool_repair_kit_iron",
            "tool_repair_kit_rare",
            "tool_sap_shunt",
            "tool_watering_can",
            "tool_watering_can_full"
    );
    private static final String[] WEAPON_FALLBACK_TOKENS = {
            "sword",
            "greatsword",
            "claymore",
            "axe",
            "battleaxe",
            "dagger",
            "knife",
            "mace",
            "club",
            "hammer",
            "bow",
            "crossbow",
            "staff",
            "spellbook",
            "spell_book",
            "spell-book",
            "tome",
            "grimoire",
            "spear",
            "lance",
            "halberd",
            "scythe",
            "rapier",
            "katana",
            "trident",
            "pike",
            "flail",
            "whip"
    };
    private static final String[] ARMOR_FALLBACK_TOKENS = {
            "helmet",
            "helm",
            "hood",
            "cap",
            "mask",
            "visor",
            "chestplate",
            "breastplate",
            "cuirass",
            "tunic",
            "robe",
            "vest",
            "greaves",
            "leggings",
            "pants",
            "boots",
            "gauntlet",
            "gauntlets",
            "glove",
            "gloves",
            "bracer",
            "bracers",
            "pauldron",
            "pauldrons",
            "shoulder",
            "shoulders",
            "sabatons"
    };
    private static final String[] TOOL_FALLBACK_TOKENS = {
            "pickaxe",
            "pick_axe",
            "shovel",
            "hoe",
            "spade",
            "rake"
    };

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

    public static boolean isTool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        String itemId = stack.getItemId();
        if (isExcludedToolId(itemId)) {
            return false;
        }

        Item item = stack.getItem();
        if (item != null && item != Item.UNKNOWN && item.getTool() != null) {
            return true;
        }

        return isToolFallbackId(itemId);
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

    public static boolean isToolItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }

        String cacheKey = itemId.trim();
        Boolean cached = toolIdCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        boolean result = isToolByMetadata(cacheKey);
        if (!result) {
            result = isToolFallbackId(cacheKey);
        }

        toolIdCache.put(cacheKey, result);
        return result;
    }

    public static boolean isEquipmentItemId(String itemId) {
        return isWeaponItemId(itemId) || isArmorItemId(itemId);
    }

    public static void clearCaches() {
        weaponIdCache.clear();
        armorIdCache.clear();
        toolIdCache.clear();
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

    private static boolean isToolByMetadata(String itemId) {
        if (isExcludedToolId(itemId)) {
            return false;
        }
        Item item = resolveItem(itemId);
        return item != null && item.getTool() != null;
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
        if (lower.startsWith("weapon_") || lower.contains("weapon")) {
            return true;
        }
        if (isToolFallbackId(itemId) || containsAny(lower, TOOL_FALLBACK_TOKENS)) {
            return false;
        }
        return containsAny(lower, WEAPON_FALLBACK_TOKENS);
    }

    private static boolean isArmorFallbackId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        String lower = itemId.toLowerCase(Locale.ROOT);
        if (lower.startsWith("armor_")
                || lower.startsWith("armour_")
                || lower.contains("armor")
                || lower.contains("armour")) {
            return true;
        }
        return containsAny(lower, ARMOR_FALLBACK_TOKENS);
    }

    private static boolean isToolFallbackId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        String lower = itemId.toLowerCase(Locale.ROOT);
        return lower.startsWith("tool_") && !isExcludedToolIdLower(lower);
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

    private static boolean isExcludedToolId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        return isExcludedToolIdLower(itemId.toLowerCase(Locale.ROOT));
    }

    private static boolean isExcludedToolIdLower(String lowerItemId) {
        return excludedToolIdsLower.contains(lowerItemId);
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
