package irai.mod.reforge.Common.UI;

import java.util.Locale;
import java.util.function.Predicate;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import irai.mod.reforge.Util.NameResolver;
import irai.mod.reforge.Util.LangLoader;

/**
 * Shared item helpers used by bench UIs.
 */
public final class UIItemUtils {

    private static final String UNKNOWN_ITEM = "Unknown Item";

    public static final class HammerWearResult {
        private final boolean ok;
        private final boolean consumed;

        public HammerWearResult(boolean ok, boolean consumed) {
            this.ok = ok;
            this.consumed = consumed;
        }

        public boolean ok() {
            return ok;
        }

        public boolean consumed() {
            return consumed;
        }
    }

    private UIItemUtils() {}

    public static String displayNameOrItemId(ItemStack itemStack) {
        return displayNameOrItemId(itemStack, LangLoader.getDefaultUILanguage());
    }

    public static String displayNameOrItemId(ItemStack itemStack, Object player) {
        return displayNameOrItemId(itemStack, LangLoader.getPlayerLanguage(player));
    }

    public static String displayNameOrItemId(ItemStack itemStack, String langCode) {
        if (itemStack == null || itemStack.isEmpty()) {
            return UNKNOWN_ITEM;
        }
        String itemId = itemStack.getItemId();
        String displayName = NameResolver.getDisplayName(itemStack, langCode);
        String essenceFallback = resolveEssenceDisplayName(itemId, langCode);
        if (displayName == null || displayName.isBlank() || UNKNOWN_ITEM.equals(displayName)) {
            if (itemId == null || itemId.isBlank()) {
                return UNKNOWN_ITEM;
            }
            return essenceFallback != null ? essenceFallback : itemId;
        }
        if (itemId != null && !itemId.isBlank()) {
            if (displayName.equalsIgnoreCase(itemId) || looksLikeTranslationKey(displayName)) {
                if (essenceFallback != null) {
                    return essenceFallback;
                }
            }
        }
        return displayName;
    }

    private static boolean looksLikeTranslationKey(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (!lower.contains(".")) {
            return false;
        }
        if (lower.endsWith(".name")) {
            return true;
        }
        return lower.contains(".items.") || lower.contains(".item.") || lower.contains(".entity.");
    }

    private static String resolveEssenceDisplayName(String itemId, String langCode) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        String id = itemId.trim();
        String lower = id.toLowerCase(Locale.ROOT);
        if (!lower.contains("essence")) {
            return null;
        }

        boolean concentrated = lower.contains("concentrated");
        String base = id;
        if (base.startsWith("Ingredient_")) {
            base = base.substring("Ingredient_".length());
        }
        if (base.endsWith("_Essence")) {
            base = base.substring(0, base.length() - "_Essence".length());
        } else if (base.endsWith("_Essence_Concentrated")) {
            base = base.substring(0, base.length() - "_Essence_Concentrated".length());
        }
        if (base.endsWith("_Concentrated")) {
            base = base.substring(0, base.length() - "_Concentrated".length());
        }

        base = base.replace('_', ' ').trim();
        if (base.isBlank()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        String[] parts = base.split("\\s+");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
            if (i < parts.length - 1) {
                sb.append(' ');
            }
        }

        String name = sb.toString();
        if (name.isBlank()) {
            return null;
        }
        String essenceLabel = LangLoader.getTranslationForLanguage("ui.essence_bench.essence_generic", langCode);
        if (essenceLabel == null || essenceLabel.isBlank() || essenceLabel.equals("ui.essence_bench.essence_generic")) {
            essenceLabel = "Essence";
        }
        if (concentrated) {
            String suffix = LangLoader.getTranslationForLanguage("ui.essence_bench.essence_concentrated_suffix", langCode);
            if (suffix == null || suffix.isBlank() || suffix.equals("ui.essence_bench.essence_concentrated_suffix")) {
                suffix = "(Concentrated)";
            }
            return name + " " + essenceLabel + " " + suffix;
        }
        return name + " " + essenceLabel;
    }

    public static String normalizeItemId(String itemId) {
        return itemId == null ? "" : itemId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public static boolean isIronHammerItem(String itemId, String canonicalHammerId) {
        if (itemId == null || itemId.isEmpty()) {
            return false;
        }
        if (canonicalHammerId != null && canonicalHammerId.equalsIgnoreCase(itemId)) {
            return true;
        }
        String normalized = normalizeItemId(itemId);
        return normalized.contains("toolhammeriron") || normalized.contains("hammeriron");
    }

    public static HammerWearResult applyHammerWear(ItemContainer container,
                                                   short slot,
                                                   double durabilityFraction,
                                                   Predicate<String> hammerMatcher) {
        if (container == null || hammerMatcher == null) {
            return new HammerWearResult(false, false);
        }
        ItemStack hammer = container.getItemStack(slot);
        if (hammer == null || hammer.isEmpty() || !hammerMatcher.test(hammer.getItemId())) {
            return new HammerWearResult(false, false);
        }

        double max = hammer.getMaxDurability();
        double cur = hammer.getDurability();
        if (max <= 0.0d) {
            container.removeItemStackFromSlot(slot, 1, false, false);
            return new HammerWearResult(true, true);
        }

        double fraction = Math.max(0.0d, durabilityFraction);
        double loss = Math.max(1.0d, max * fraction);
        double next = Math.max(0.0d, cur - loss);
        if (next <= 0.0d) {
            container.removeItemStackFromSlot(slot, 1, false, false);
            return new HammerWearResult(true, true);
        }

        container.setItemStackForSlot(slot, hammer.withDurability(next));
        return new HammerWearResult(true, false);
    }
}
