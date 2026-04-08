package irai.mod.reforge.Util;

import org.bson.BsonDocument;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import irai.mod.reforge.Config.RefinementConfig;
import irai.mod.reforge.Interactions.ReforgeEquip;
import irai.mod.reforge.Socket.Essence;
import irai.mod.reforge.Socket.SocketData;
import irai.mod.reforge.Socket.SocketManager;
import irai.mod.reforge.Socket.ResonanceSystem;

/**
 * Utility class for resolving item names from translation properties.
 * Provides standalone methods to get actual localized names from items.
 */
public final class NameResolver {

    // Metadata keys for stored display names
    public static final String KEY_DISPLAY_NAME = "SocketReforge.Refinement.DisplayName";
    public static final String KEY_DISPLAY_NAME_KEY = "SocketReforge.Refinement.DisplayNameKey";
    public static final String KEY_LEVEL = "SocketReforge.Refinement.Level";
    private static final String BLOOD_PACT_PREFIX = "Blood Pact ";
    private static final String FIRE_WARD_PREFIX = "Infernal ";
    private static final String ICE_WARD_PREFIX = "Glacial ";
    private static volatile RefinementConfig refinementConfig;

    private NameResolver() {} // Prevent instantiation

    public static void setRefinementConfig(RefinementConfig config) {
        refinementConfig = config;
    }

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
        return getDisplayName(itemStack, LangLoader.getDefaultUILanguage());
    }

    /**
     * Gets the actual localized display name for an item for a specific player.
     */
    public static String getDisplayName(ItemStack itemStack, Object player) {
        return getDisplayName(itemStack, LangLoader.getPlayerLanguage(player));
    }

    /**
     * Gets the actual localized display name for an item using a specific language code.
     */
    public static String getDisplayName(ItemStack itemStack, String langCode) {
        if (itemStack == null || itemStack.isEmpty()) {
            return "Unknown Item";
        }

        // First check metadata for stored display name / translation key
        String metadataName = getDisplayNameFromMetadata(itemStack);
        String metadataKey = getDisplayNameKeyFromMetadata(itemStack);
        if ((metadataName != null && !metadataName.isEmpty())
                || (metadataKey != null && !metadataKey.isEmpty())) {
            // The stored value could be:
            // 1. "wanmine.items.Weapon_Sword_Gaias_Wrath.name +3" (translation key + level suffix)
            // 2. "wanmine.items.Weapon_Sword_Gaias_Wrath.name" (just translation key, level in separate metadata)
            
            // Check if it contains a level suffix like " +1", " +2", " +15"
            int level = extractLevelSuffix(metadataName);
            String baseKey = stripLevelSuffix(metadataName);
            if (level <= 0) {
                // No level suffix in display name, check separate level metadata
                level = getLevelFromMetadata(itemStack);
            }
            
            String trimmedBase = baseKey == null ? "" : baseKey.trim();
            String trimmedKey = metadataKey == null ? "" : metadataKey.trim();
            String itemId = itemStack.getItemId();
            String normalizedId = normalizeItemId(itemId);

            String translationKey = null;
            if (!trimmedKey.isBlank() && looksLikeTranslationKey(trimmedKey)) {
                translationKey = trimmedKey;
            } else if (!trimmedBase.isBlank() && looksLikeTranslationKey(trimmedBase)) {
                translationKey = trimmedBase;
            }

            String localizedName = null;
            if (translationKey != null) {
                localizedName = resolveTranslationKeyExact(translationKey, langCode);
            }
            if (localizedName == null || localizedName.isBlank()) {
                localizedName = resolveItemIdTranslationNoFallback(itemId, langCode);
            }

            boolean metadataLooksCustom = false;
            if (!trimmedBase.isBlank() && !looksLikeTranslationKey(trimmedBase)) {
                boolean matchesId = itemId != null && !itemId.isBlank()
                        && (trimmedBase.equals(itemId)
                        || (normalizedId != null && trimmedBase.equals(normalizedId)));
                if (!matchesId) {
                    String englishFromKey = translationKey != null ? resolveTranslationKeyExact(translationKey, "en-US") : null;
                    String englishFromId = resolveItemIdTranslationExact(itemId, "en-US");
                    boolean matchesEnglish = false;
                    if (englishFromKey != null && !englishFromKey.isBlank()
                            && trimmedBase.equalsIgnoreCase(englishFromKey)) {
                        matchesEnglish = true;
                    } else if (englishFromId != null && !englishFromId.isBlank()
                            && trimmedBase.equalsIgnoreCase(englishFromId)) {
                        matchesEnglish = true;
                    }
                    metadataLooksCustom = !matchesEnglish;
                }
            }

            if ((localizedName == null || localizedName.isBlank()) && metadataLooksCustom) {
                localizedName = trimmedBase;
            }

            if (localizedName == null || localizedName.isBlank()) {
                if (translationKey != null) {
                    localizedName = resolveTranslationKey(translationKey, langCode);
                }
            }

            if (localizedName == null || localizedName.isBlank()) {
                String fromId = resolveFromItemId(itemId, langCode);
                if (fromId != null && !fromId.isBlank()) {
                    localizedName = fromId;
                } else if (!trimmedBase.isBlank()) {
                    localizedName = trimmedBase;
                } else {
                    localizedName = itemId;
                }
            }

            if (level > 0) {
                boolean isArmor = ReforgeEquip.isArmor(itemStack) && !ReforgeEquip.isWeapon(itemStack);
                return applySpecialPrefix(itemStack, applyRefinementToName(localizedName, level, isArmor), langCode);
            }
            return applySpecialPrefix(itemStack, localizedName, langCode);
        }

        // Get translation key from item
        Item item = itemStack.getItem();
        if (item == null || item == Item.UNKNOWN) {
            String itemIdFallback = resolveFromItemId(itemStack.getItemId(), langCode);
            String fallback = itemIdFallback != null ? itemIdFallback : itemStack.getItemId();
            return applySpecialPrefix(itemStack, fallback, langCode);
        }

        try {
            String translationKey = item.getTranslationProperties().getName();
            if (translationKey != null && !translationKey.isEmpty()) {
                // Resolve the translation key to get the actual localized name
                String localizedName = resolveTranslationKey(translationKey, langCode);
                if (localizedName != null && !localizedName.isEmpty()) {
                    if (!localizedName.equals(translationKey)) {
                        return applySpecialPrefix(itemStack, localizedName, langCode);
                    }
                }
                // Try to resolve from item id when the translation key didn't resolve
                String fromId = resolveFromItemId(itemStack.getItemId(), langCode);
                if (fromId != null && !fromId.isBlank()) {
                    return applySpecialPrefix(itemStack, fromId, langCode);
                }
                // Return translation key as fallback
                return applySpecialPrefix(itemStack, translationKey, langCode);
            }
        } catch (Exception e) {
            // Fall through to item ID fallback
        }

        String itemIdFallback = resolveFromItemId(itemStack.getItemId(), langCode);
        String fallback = itemIdFallback != null ? itemIdFallback : itemStack.getItemId();
        return applySpecialPrefix(itemStack, fallback, langCode);
    }

    /**
     * Gets the base display name without level suffix.
     *
     * @param itemStack The item to get the name for
     * @return The base display name without level suffix
     */
    public static String getBaseDisplayName(ItemStack itemStack) {
        String fullName = getDisplayName(itemStack);
        return stripLevelSuffix(fullName);
    }

    private static int extractLevelSuffix(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        String trimmed = value.trim();
        int configured = extractLevelSuffixConfigured(trimmed);
        if (configured > 0) {
            return configured;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\s\\+(\\d+)$").matcher(trimmed);
        if (!matcher.find()) {
            return 0;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String stripLevelSuffix(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String trimmed = value.trim();
        String stripped = stripLevelSuffixConfigured(trimmed);
        if (stripped != null) {
            return stripped;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\s\\+\\d+$").matcher(trimmed);
        if (matcher.find()) {
            return trimmed.substring(0, matcher.start()).trim();
        }
        return trimmed;
    }

    private static int extractLevelSuffixConfigured(String value) {
        RefinementConfig cfg = refinementConfig;
        if (cfg == null) {
            return 0;
        }
        int fromLabels = matchLevelLabel(value, cfg);
        if (fromLabels > 0) {
            return fromLabels;
        }
        String prefix = cfg.getRefinementLevelPrefix();
        String suffix = cfg.getRefinementLevelSuffix();
        if ((prefix == null || prefix.isEmpty()) && (suffix == null || suffix.isEmpty())) {
            return 0;
        }
        String pattern = java.util.regex.Pattern.quote(prefix == null ? "" : prefix)
                + "(\\d+)"
                + java.util.regex.Pattern.quote(suffix == null ? "" : suffix);
        java.util.regex.Matcher matcher;
        if (cfg.isRefinementLevelUsePrefix()) {
            matcher = java.util.regex.Pattern.compile("^" + pattern).matcher(value);
        } else {
            matcher = java.util.regex.Pattern.compile(pattern + "$").matcher(value);
        }
        if (!matcher.find()) {
            return 0;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String stripLevelSuffixConfigured(String value) {
        RefinementConfig cfg = refinementConfig;
        if (cfg == null) {
            return null;
        }
        String fromLabels = stripLevelLabel(value, cfg);
        if (fromLabels != null) {
            return fromLabels;
        }
        String prefix = cfg.getRefinementLevelPrefix();
        String suffix = cfg.getRefinementLevelSuffix();
        if ((prefix == null || prefix.isEmpty()) && (suffix == null || suffix.isEmpty())) {
            return null;
        }
        String pattern = java.util.regex.Pattern.quote(prefix == null ? "" : prefix)
                + "(\\d+)"
                + java.util.regex.Pattern.quote(suffix == null ? "" : suffix);
        java.util.regex.Matcher matcher;
        if (cfg.isRefinementLevelUsePrefix()) {
            matcher = java.util.regex.Pattern.compile("^" + pattern).matcher(value);
            if (!matcher.find()) {
                return null;
            }
            return value.substring(matcher.end()).trim();
        }
        matcher = java.util.regex.Pattern.compile(pattern + "$").matcher(value);
        if (!matcher.find()) {
            return null;
        }
        return value.substring(0, matcher.start()).trim();
    }

    private static String formatRefinementSuffix(int level) {
        if (level <= 0) {
            return "";
        }
        RefinementConfig cfg = refinementConfig;
        if (cfg != null) {
            return cfg.formatRefinementSuffix(level);
        }
        return " +" + level;
    }

    private static int matchLevelLabel(String value, RefinementConfig cfg) {
        if (value == null || value.isBlank() || cfg == null) {
            return 0;
        }
        boolean usePrefix = cfg.isRefinementLevelUsePrefix();
        int matchedLevel = 0;
        int matchedLen = -1;
        String[][] labelSets = {
                cfg.getRefinementLevelLabels(),
                cfg.getRefinementLevelLabelsArmor()
        };
        for (String[] labels : labelSets) {
            if (labels == null || labels.length == 0) {
                continue;
            }
            int max = Math.min(cfg.getMaxLevel(), labels.length - 1);
            for (int level = 1; level <= max; level++) {
                String label = labels[level];
                if (label == null || label.isBlank()) continue;
                boolean matches = usePrefix ? value.startsWith(label) : value.endsWith(label);
                if (matches && label.length() > matchedLen) {
                    matchedLevel = level;
                    matchedLen = label.length();
                }
            }
        }
        return matchedLevel;
    }

    private static String stripLevelLabel(String value, RefinementConfig cfg) {
        if (value == null || value.isBlank() || cfg == null) {
            return null;
        }
        boolean usePrefix = cfg.isRefinementLevelUsePrefix();
        int matchedLevel = 0;
        int matchedLen = -1;
        String matchedLabel = null;
        String[][] labelSets = {
                cfg.getRefinementLevelLabels(),
                cfg.getRefinementLevelLabelsArmor()
        };
        for (String[] labels : labelSets) {
            if (labels == null || labels.length == 0) {
                continue;
            }
            int max = Math.min(cfg.getMaxLevel(), labels.length - 1);
            for (int level = 1; level <= max; level++) {
                String label = labels[level];
                if (label == null || label.isBlank()) continue;
                boolean matches = usePrefix ? value.startsWith(label) : value.endsWith(label);
                if (matches && label.length() > matchedLen) {
                    matchedLevel = level;
                    matchedLen = label.length();
                    matchedLabel = label;
                }
            }
        }
        if (matchedLevel <= 0 || matchedLabel == null) {
            return null;
        }
        if (usePrefix) {
            return value.substring(matchedLabel.length()).trim();
        }
        return value.substring(0, value.length() - matchedLabel.length()).trim();
    }

    private static String applyRefinementToName(String baseName, int level) {
        return applyRefinementToName(baseName, level, false);
    }

    private static String applyRefinementToName(String baseName, int level, boolean isArmor) {
        if (baseName == null) {
            return null;
        }
        if (level <= 0) {
            return baseName;
        }
        RefinementConfig cfg = refinementConfig;
        if (cfg != null) {
            return cfg.applyRefinementToName(baseName, level, isArmor);
        }
        return baseName + " +" + level;
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
        return resolveTranslationKey(translationKey, LangLoader.getDefaultUILanguage());
    }

    /**
     * Resolves a translation key to its localized value using the given language code.
     */
    public static String resolveTranslationKey(String translationKey, String langCode) {
        if (translationKey == null || translationKey.isEmpty()) {
            return translationKey;
        }

        // Use LangLoader to resolve from .lang files with language fallback
        return LangLoader.resolveTranslation(translationKey, langCode);
    }

    private static String resolveTranslationKeyExact(String translationKey, String langCode) {
        if (translationKey == null || translationKey.isEmpty()) {
            return null;
        }

        String translated = LangLoader.getTranslationExact(translationKey, langCode);
        if (translated != null && !translated.isBlank() && !translated.equals(translationKey)) {
            return translated;
        }

        String[] keyVariations = {
                translationKey,
                translationKey.startsWith("server.") ? translationKey : "server." + translationKey,
                translationKey.startsWith("server.") ? translationKey.substring(7) : translationKey,
                translationKey.replaceFirst("^[^.]+\\.(items\\..*)$", "$1"),
                translationKey.contains(".items.")
                        ? "items." + translationKey.substring(translationKey.indexOf(".items.") + 7)
                        : translationKey
        };

        for (String keyToTry : keyVariations) {
            if (keyToTry == null || keyToTry.isEmpty()) {
                continue;
            }
            translated = LangLoader.getTranslationExact(keyToTry, langCode);
            if (translated != null && !translated.isBlank() && !translated.equals(keyToTry)) {
                return translated;
            }
        }
        return null;
    }

    /**
     * Attempts to resolve a localized name directly from an item id.
     */
    public static String resolveItemIdTranslation(String itemId, String langCode) {
        return resolveFromItemId(itemId, langCode);
    }

    /**
     * Attempts to resolve a localized name for the given item id without falling back
     * to the default language. Returns null when not available for the requested language.
     */
    public static String resolveItemIdTranslationNoFallback(String itemId, String langCode) {
        String normalized = normalizeItemId(itemId);
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        for (String key : buildItemIdKeyCandidates(normalized)) {
            String localized = LangLoader.getTranslationFromCache(key, langCode);
            if (localized != null && !localized.isBlank() && !localized.equals(key)) {
                return localized;
            }
        }
        String built = buildLocalizedNameFromItemId(normalized, langCode);
        if (built != null && !built.isBlank()) {
            return built;
        }
        return null;
    }

    /**
     * Attempts to resolve a localized name for the given item id using the specified language
     * without falling back to other languages, but still consulting the I18n module.
     */
    public static String resolveItemIdTranslationExact(String itemId, String langCode) {
        String normalized = normalizeItemId(itemId);
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        for (String key : buildItemIdKeyCandidates(normalized)) {
            String localized = LangLoader.getTranslationExact(key, langCode);
            if (localized != null && !localized.isBlank() && !localized.equals(key)) {
                return localized;
            }
        }
        return null;
    }

    /**
     * Attempts to resolve a translation key for the given item id.
     * Returns the first key that resolves to a non-key translation value.
     */
    public static String resolveItemIdTranslationKey(String itemId, String langCode) {
        String normalized = normalizeItemId(itemId);
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        for (String key : buildItemIdKeyCandidates(normalized)) {
            String translated = LangLoader.getTranslationForLanguage(key, langCode);
            if (translated != null && !translated.isBlank() && !translated.equals(key)) {
                return key;
            }
        }
        return null;
    }

    /**
     * Attempts to resolve a translation using item id variants.
     */
    private static String resolveFromItemId(String itemId, String langCode) {
        String normalized = normalizeItemId(itemId);
        if (normalized == null || normalized.isBlank()) {
            return null;
        }

        for (String key : buildItemIdKeyCandidates(normalized)) {
            String localized = LangLoader.resolveTranslation(key, langCode);
            if (localized != null && !localized.isBlank() && !localized.equals(key)) {
                return localized;
            }
        }
        String built = buildLocalizedNameFromItemId(normalized, langCode);
        if (built != null && !built.isBlank()) {
            return built;
        }
        return null;
    }

    private static String normalizeItemId(String itemId) {
        if (itemId == null) {
            return null;
        }
        String trimmed = itemId.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        int dttIndex = trimmed.indexOf("__dtt_");
        if (dttIndex > 0) {
            return trimmed.substring(0, dttIndex);
        }
        return trimmed;
    }

    private static java.util.List<String> buildItemIdKeyCandidates(String itemId) {
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();
        if (itemId == null || itemId.isBlank()) {
            return java.util.List.of();
        }

        String normalized = itemId.trim();
        String lower = normalized.toLowerCase(java.util.Locale.ROOT);

        String ns = null;
        String idNoNs = normalized;
        String idNoNsLower = lower;

        int colon = normalized.indexOf(':');
        if (colon > 0 && colon < normalized.length() - 1) {
            ns = normalized.substring(0, colon).toLowerCase(java.util.Locale.ROOT);
            idNoNs = normalized.substring(colon + 1);
            idNoNsLower = idNoNs.toLowerCase(java.util.Locale.ROOT);
        } else {
            int underscore = normalized.indexOf('_');
            if (underscore > 0 && underscore < normalized.length() - 1) {
                ns = normalized.substring(0, underscore).toLowerCase(java.util.Locale.ROOT);
                idNoNs = normalized.substring(underscore + 1);
                idNoNsLower = idNoNs.toLowerCase(java.util.Locale.ROOT);
            }
        }

        java.util.List<String> baseIds = new java.util.ArrayList<>();
        baseIds.add(normalized);
        if (!lower.equals(normalized)) {
            baseIds.add(lower);
        }
        if (!idNoNs.equals(normalized)) {
            baseIds.add(idNoNs);
        }
        if (!idNoNsLower.equals(lower) && !idNoNsLower.equals(idNoNs)) {
            baseIds.add(idNoNsLower);
        }

        for (String base : baseIds) {
            if (base == null || base.isBlank()) {
                continue;
            }
            keys.add("items." + base + ".name");
            keys.add("item." + base + ".name");
            keys.add("server.items." + base + ".name");
            keys.add("server.item." + base + ".name");
            if (ns != null && !ns.isBlank()) {
                keys.add(ns + ".items." + base + ".name");
                keys.add(ns + ".item." + base + ".name");
            }
        }

        return new java.util.ArrayList<>(keys);
    }

    private static String buildLocalizedNameFromItemId(String itemId, String langCode) {
        if (itemId == null || itemId.isBlank() || langCode == null || langCode.isBlank()) {
            return null;
        }

        String base = itemId.trim();
        int colon = base.indexOf(':');
        if (colon >= 0 && colon < base.length() - 1) {
            base = base.substring(colon + 1);
        }

        String[] parts = base.split("_");
        if (parts.length < 3) {
            return null;
        }

        String category = parts[0].toLowerCase(java.util.Locale.ROOT);
        boolean isWeapon = "weapon".equals(category);
        boolean isArmor = "armor".equals(category);
        boolean isTool = "tool".equals(category);
        if (!isWeapon && !isArmor && !isTool) {
            return null;
        }

        if (parts.length > 4) {
            return null;
        }

        String typePart = parts[1];
        String materialPart = parts[2];
        String variantPart = parts.length == 4 ? parts[3] : null;

        PartTranslation type = translatePart(
                isWeapon ? "item.weapon_type." + typePart.toLowerCase(java.util.Locale.ROOT)
                        : isArmor ? "item.armor_slot." + typePart.toLowerCase(java.util.Locale.ROOT)
                        : "item.tool_type." + typePart.toLowerCase(java.util.Locale.ROOT),
                typePart,
                langCode
        );
        PartTranslation material = translatePart("item.material." + materialPart.toLowerCase(java.util.Locale.ROOT),
                materialPart, langCode);
        PartTranslation variant = variantPart == null ? PartTranslation.empty()
                : translatePart("item.variant." + variantPart.toLowerCase(java.util.Locale.ROOT), variantPart, langCode);

        String formatKey;
        if (parts.length == 4) {
            formatKey = isWeapon ? "item.weapon.format_variant"
                    : isArmor ? "item.armor.format_variant"
                    : "item.tool.format_variant";
        } else {
            formatKey = isWeapon ? "item.weapon.format"
                    : isArmor ? "item.armor.format"
                    : "item.tool.format";
        }

        String format = LangLoader.getTranslationFromCache(formatKey, langCode);
        if (format == null || format.isBlank() || format.equals(formatKey)) {
            return null;
        }

        boolean anyTranslated = type.translated || material.translated || variant.translated;
        if (!anyTranslated) {
            return null;
        }

        String result = format;
        result = result.replace("{0}", type.value);
        result = result.replace("{1}", material.value);
        result = result.replace("{2}", variant.value == null ? "" : variant.value);
        return result.replaceAll("\\s{2,}", " ").trim();
    }

    private static PartTranslation translatePart(String key, String fallback, String langCode) {
        String translated = LangLoader.getTranslationFromCache(key, langCode);
        if (translated != null && !translated.isBlank() && !translated.equals(key)) {
            return new PartTranslation(translated, true);
        }
        String humanized = humanizeToken(fallback);
        return new PartTranslation(humanized, false);
    }

    private static String humanizeToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String lower = raw.trim().replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        String[] parts = lower.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
            if (i < parts.length - 1) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    private static final class PartTranslation {
        final String value;
        final boolean translated;

        private PartTranslation(String value, boolean translated) {
            this.value = value == null ? "" : value;
            this.translated = translated;
        }

        static PartTranslation empty() {
            return new PartTranslation("", false);
        }
    }

    private static boolean looksLikeTranslationKey(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        if (!lower.contains(".")) {
            return false;
        }
        if (lower.endsWith(".name")) {
            return true;
        }
        return lower.contains(".items.") || lower.contains(".item.") || lower.contains(".entity.");
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

    private static String getDisplayNameKeyFromMetadata(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }

        BsonDocument meta = itemStack.getMetadata();
        if (meta == null || !meta.containsKey(KEY_DISPLAY_NAME_KEY)) {
            return null;
        }

        try {
            return meta.getString(KEY_DISPLAY_NAME_KEY).getValue();
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

    /**
     * Applies any conditional name prefixes driven by item state.
     */
    private static String applySpecialPrefix(ItemStack itemStack, String resolvedName, String langCode) {
        String base = resolvedName == null ? "" : resolvedName;
        if (itemStack == null || itemStack.isEmpty()) {
            return base;
        }

        String bloodPactPrefix = getPrefixTranslation("name.prefix.blood_pact", BLOOD_PACT_PREFIX, langCode);
        String fireWardPrefix = getPrefixTranslation("name.prefix.infernal", FIRE_WARD_PREFIX, langCode);
        String iceWardPrefix = getPrefixTranslation("name.prefix.glacial", ICE_WARD_PREFIX, langCode);

        if (ReforgeEquip.isWeapon(itemStack)) {
            if (base.startsWith(bloodPactPrefix) || base.startsWith(BLOOD_PACT_PREFIX)) {
                return applyResonancePrefix(itemStack, base, langCode);
            }
            if (hasBloodPactTierFive(itemStack)) {
                return applyResonancePrefix(itemStack, bloodPactPrefix + base, langCode);
            }
            return applyResonancePrefix(itemStack, base, langCode);
        }

        if (ReforgeEquip.isArmor(itemStack)) {
            boolean hasFireTierFive = hasArmorTierFive(itemStack, Essence.Type.FIRE);
            boolean hasIceTierFive = hasArmorTierFive(itemStack, Essence.Type.ICE);

            if (!hasFireTierFive && !hasIceTierFive) {
                return applyResonancePrefix(itemStack, base, langCode);
            }

            StringBuilder prefix = new StringBuilder();
            if (hasFireTierFive && !base.startsWith(fireWardPrefix) && !base.startsWith(FIRE_WARD_PREFIX)) {
                prefix.append(fireWardPrefix);
            }
            if (hasIceTierFive && !base.startsWith(iceWardPrefix) && !base.startsWith(ICE_WARD_PREFIX)) {
                prefix.append(iceWardPrefix);
            }
            if (prefix.isEmpty()) {
                return applyResonancePrefix(itemStack, base, langCode);
            }
            return applyResonancePrefix(itemStack, prefix + base, langCode);
        }

        return applyResonancePrefix(itemStack, base, langCode);
    }

    private static String getPrefixTranslation(String key, String fallback, String langCode) {
        String translated = LangLoader.getTranslationForLanguage(key, langCode);
        if (translated == null || translated.isBlank() || translated.equals(key)) {
            return fallback;
        }
        if (!translated.endsWith(" ")) {
            return translated + " ";
        }
        return translated;
    }

    private static String applyResonancePrefix(ItemStack itemStack, String value, String langCode) {
        if (itemStack == null || itemStack.isEmpty()) {
            return value;
        }
        String base = value == null ? "" : value;
        if (!SocketManager.hasResonance(itemStack)) {
            return base;
        }
        String resonanceName = SocketManager.getResonanceName(itemStack);
        if (resonanceName == null || resonanceName.isBlank()) {
            return base;
        }
        String localized = ResonanceSystem.getLocalizedName(resonanceName, langCode);
        String resolved = localized != null && !localized.isBlank() ? localized : resonanceName;
        String prefixLocalized = resolved.trim() + " ";
        String prefixRaw = resonanceName.trim() + " ";
        if (base.startsWith(prefixLocalized) || base.startsWith(prefixRaw)) {
            return base;
        }
        return prefixLocalized + base;
    }

    /**
     * Blood Pact is active for weapons with VOID essence tier 5.
     */
    private static boolean hasBloodPactTierFive(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }
        if (!ReforgeEquip.isWeapon(itemStack)) {
            return false;
        }

        // Prefer persisted tier metadata first (works even when legacy items
        // have effect metadata but incomplete socket layout metadata).
        if (hasTierFiveFromMetadata(itemStack, Essence.Type.VOID)) {
            return true;
        }

        SocketData socketData = SocketManager.getSocketData(itemStack);
        if (socketData == null || socketData.getCurrentSocketCount() <= 0) {
            return false;
        }
        Integer voidTier = SocketManager.calculateConsecutiveTiers(socketData).get(Essence.Type.VOID);
        return voidTier != null && voidTier >= 5;
    }

    private static boolean hasArmorTierFive(ItemStack itemStack, Essence.Type type) {
        if (itemStack == null || itemStack.isEmpty() || type == null) {
            return false;
        }
        if (!ReforgeEquip.isArmor(itemStack)) {
            return false;
        }
        if (hasTierFiveFromMetadata(itemStack, type)) {
            return true;
        }

        SocketData socketData = SocketManager.getSocketData(itemStack);
        if (socketData == null || socketData.getCurrentSocketCount() <= 0) {
            return false;
        }
        Integer tier = SocketManager.calculateConsecutiveTiers(socketData).get(type);
        return tier != null && tier >= 5;
    }

    private static boolean hasTierFiveFromMetadata(ItemStack itemStack, Essence.Type expectedType) {
        if (itemStack == null || itemStack.isEmpty() || expectedType == null) {
            return false;
        }
        try {
            String[] effectTypes = SocketManager.getEssenceEffects(itemStack);
            String[] effectTiers = SocketManager.getEssenceTiers(itemStack);
            if (effectTypes == null || effectTiers == null) {
                return false;
            }
            int count = Math.min(effectTypes.length, effectTiers.length);
            for (int i = 0; i < count; i++) {
                String type = effectTypes[i];
                if (type == null || !expectedType.name().equalsIgnoreCase(type.trim())) {
                    continue;
                }
                String tierRaw = effectTiers[i];
                if (tierRaw == null) {
                    continue;
                }
                int tier = Integer.parseInt(tierRaw.trim());
                if (tier >= 5) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            // Ignore malformed metadata and fall back to socket decoding.
        }
        return false;
    }
}
