package irai.mod.reforge.Common.UI;

import java.util.Locale;
import java.util.function.Predicate;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import irai.mod.reforge.Util.NameResolver;

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
        if (itemStack == null || itemStack.isEmpty()) {
            return UNKNOWN_ITEM;
        }
        String itemId = itemStack.getItemId();
        String displayName = NameResolver.getDisplayName(itemStack);
        if (displayName == null || displayName.isBlank() || UNKNOWN_ITEM.equals(displayName)) {
            if (itemId == null || itemId.isBlank()) {
                return UNKNOWN_ITEM;
            }
            return itemId;
        }
        return displayName;
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
