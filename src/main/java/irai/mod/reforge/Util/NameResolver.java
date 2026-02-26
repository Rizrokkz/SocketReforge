package irai.mod.reforge.Util;

import org.bson.BsonDocument;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;

/**
 * Utility class for resolving item names from translation properties.
 * Provides standalone methods to get actual localized names from items.
 */
public final class NameResolver {

    // Metadata keys for stored display names
    public static final String KEY_DISPLAY_NAME = "SocketReforge.Refinement.DisplayName";
    public static final String KEY_LEVEL = "SocketReforge.Refinement.Level";

    private NameResolver() {} // Prevent instantiation

    /**
     * Gets the actual localized display name for an item.
     * 
     * Resolution order:
     * 1. Check metadata for stored display name (from refinement)
     * 2. Parse the translation key from stored name, resolve it, and append level
     * 3. Get translation key from item's TranslationProperties
     * 4. Fall back to item ID if all else fails
     *
     * @param itemStack The item to get the name for
     * @return The localized display name, or item ID if unavailable
     */
    public static String getDisplayName(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return "Unknown Item";
        }

        // First check metadata for stored display name
        String metadataName = getDisplayNameFromMetadata(itemStack);
        if (metadataName != null && !metadataName.isEmpty()) {
            // The stored value could be:
            // 1. "wanmine.items.Weapon_Sword_Gaias_Wrath.name +3" (translation key + level suffix)
            // 2. "wanmine.items.Weapon_Sword_Gaias_Wrath.name" (just translation key, level in separate metadata)
            
            // Check if it contains a level suffix like " +1", " +2", " +3"
            int level = 0;
            String baseKey = metadataName;
            
            if (metadataName.endsWith(" +3")) {
                level = 3;
                baseKey = metadataName.substring(0, metadataName.length() - 3).trim();
            } else if (metadataName.endsWith(" +2")) {
                level = 2;
                baseKey = metadataName.substring(0, metadataName.length() - 3).trim();
            } else if (metadataName.endsWith(" +1")) {
                level = 1;
                baseKey = metadataName.substring(0, metadataName.length() - 3).trim();
            } else {
                // No level suffix in display name, check separate level metadata
                level = getLevelFromMetadata(itemStack);
            }
            
            // Resolve the translation key to get the localized name
            String localizedName = resolveTranslationKey(baseKey.trim());
            
            // Append level if > 0
            if (level > 0) {
                return localizedName + " +" + level;
            }
            return localizedName;
        }

        // Get translation key from item
        Item item = itemStack.getItem();
        if (item == null || item == Item.UNKNOWN) {
            return itemStack.getItemId();
        }

        try {
            String translationKey = item.getTranslationProperties().getName();
            if (translationKey != null && !translationKey.isEmpty()) {
                // Resolve the translation key to get the actual localized name
                String localizedName = resolveTranslationKey(translationKey);
                if (localizedName != null && !localizedName.isEmpty()) {
                    return localizedName;
                }
                // Return translation key as fallback
                return translationKey;
            }
        } catch (Exception e) {
            // Fall through to item ID fallback
        }

        return itemStack.getItemId();
    }

    /**
     * Gets the base display name without level suffix.
     *
     * @param itemStack The item to get the name for
     * @return The base display name without level suffix
     */
    public static String getBaseDisplayName(ItemStack itemStack) {
        String fullName = getDisplayName(itemStack);
        return fullName.replaceFirst("\\s\\+[123]$", "");
    }

    /**
     * Gets the translation key from an item without resolving it.
     *
     * @param itemStack The item to get the translation key for
     * @return The translation key, or null if unavailable
     */
    public static String getTranslationKey(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }

        Item item = itemStack.getItem();
        if (item == null || item == Item.UNKNOWN) {
            return null;
        }

        try {
            return item.getTranslationProperties().getName();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Resolves a translation key to its localized value.
     * This method attempts to resolve the translation key using the LangLoader
     * which reads from .lang files.
     *
     * @param translationKey The translation key to resolve (e.g., "wanmine.items.Weapon_Sword_Gaias_Wrath.name")
     * @return The localized string, or extracted name if resolution fails
     */
    public static String resolveTranslationKey(String translationKey) {
        if (translationKey == null || translationKey.isEmpty()) {
            return translationKey;
        }

        // Use LangLoader to resolve from .lang files
        return LangLoader.resolveTranslation(translationKey);
    }

    /**
     * Gets the display name stored in item metadata.
     *
     * @param itemStack The item to check
     * @return The stored display name, or null if not set
     */
    private static String getDisplayNameFromMetadata(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }

        BsonDocument meta = itemStack.getMetadata();
        if (meta == null || !meta.containsKey(KEY_DISPLAY_NAME)) {
            return null;
        }

        try {
            return meta.getString(KEY_DISPLAY_NAME).getValue();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Gets the refinement level from item metadata.
     *
     * @param itemStack The item to check
     * @return The refinement level (0-3), or 0 if not set
     */
    private static int getLevelFromMetadata(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return 0;
        }

        BsonDocument meta = itemStack.getMetadata();
        if (meta == null || !meta.containsKey(KEY_LEVEL)) {
            return 0;
        }

        try {
            return meta.getInt32(KEY_LEVEL).getValue();
        } catch (Exception e) {
            return 0;
        }
    }
}
