package irai.mod.reforge.Common;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import irai.mod.reforge.Config.AscensionConfig;
import irai.mod.reforge.Config.RefinementConfig;
import irai.mod.reforge.Interactions.ReforgeEquip;
import irai.mod.reforge.Util.MetadataKeys;

public final class AscensionUtils {

    private static volatile AscensionConfig config = new AscensionConfig();

    private AscensionUtils() {}

    public static void setConfig(AscensionConfig newConfig) {
        config = newConfig == null ? new AscensionConfig() : newConfig;
    }

    public static AscensionConfig getConfig() {
        return config;
    }

    public static int getItemAscensionTier(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return 0;
        }
        Integer tier = item.getFromMetadataOrNull(MetadataKeys.ASCENSION_TIER, Codec.INTEGER);
        return tier == null ? 0 : Math.max(0, tier);
    }

    public static ItemStack setItemAscensionTier(ItemStack item, int tier) {
        if (item == null || item.isEmpty()) {
            return item;
        }
        return item.withMetadata(MetadataKeys.ASCENSION_TIER, Codec.INTEGER, Math.max(0, tier));
    }

    public static boolean isMaxRefinement(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        int currentLevel = ReforgeEquip.getLevelFromItem(item);
        int maxLevel = ReforgeEquip.getMaxUpgradeLevel();
        return currentLevel >= maxLevel;
    }

    public static boolean canAscend(ItemStack item) {
        if (!config.isEnabled()) {
            return false;
        }
        if (item == null || item.isEmpty()) {
            return false;
        }
        if (!ReforgeEquip.isWeapon(item) && !ReforgeEquip.isArmor(item)) {
            return false;
        }
        if (!isMaxRefinement(item)) {
            return false;
        }
        if (CorruptionUtils.isCorrupted(item)) {
            return false;
        }
        int currentTier = getItemAscensionTier(item);
        return currentTier < config.getMaxTier();
    }

    public static int getNextTier(ItemStack item) {
        int currentTier = getItemAscensionTier(item);
        return currentTier + 1;
    }

    public static String getRequiredMaterial(ItemStack item) {
        int nextTier = getNextTier(item);
        return config.getTierCost(nextTier);
    }

    public static double getAscensionBaseMultiplier(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return 1.0;
        }
        Double multiplier = item.getFromMetadataOrNull(MetadataKeys.ASCENSION_BASE_MULTIPLIER, Codec.DOUBLE);
        return multiplier == null ? 1.0 : Math.max(1.0, multiplier);
    }

    public static ItemStack setAscensionBaseMultiplier(ItemStack item, double multiplier) {
        if (item == null || item.isEmpty()) {
            return item;
        }
        return item.withMetadata(MetadataKeys.ASCENSION_BASE_MULTIPLIER, Codec.DOUBLE, Math.max(1.0, multiplier));
    }

    public static double getTotalMultiplier(ItemStack item, boolean isArmor) {
        int level = ReforgeEquip.getLevelFromItem(item);
        double refinementMultiplier = isArmor
                ? ReforgeEquip.getDefenseMultiplier(level)
                : ReforgeEquip.getDamageMultiplier(level);
        double ascensionMultiplier = getAscensionBaseMultiplier(item);
        return refinementMultiplier * ascensionMultiplier;
    }

    public static ItemStack ascendItem(ItemStack item) {
        if (!canAscend(item)) {
            return item;
        }
        int currentTier = getItemAscensionTier(item);
        int newTier = currentTier + 1;

        int currentLevel = ReforgeEquip.getLevelFromItem(item);

        boolean isArmor = ReforgeEquip.isArmor(item);
        double currentMultiplier = isArmor
                ? ReforgeEquip.getDefenseMultiplier(currentLevel)
                : ReforgeEquip.getDamageMultiplier(currentLevel);
        double previousAscensionMultiplier = getAscensionBaseMultiplier(item);

        double refinementBonus = currentMultiplier - 1.0;
        double previousBonus = previousAscensionMultiplier - 1.0;
        double carryOverPercent = config.getTierCarryOver(newTier);
        double newBonus = previousBonus + (refinementBonus * carryOverPercent);
        double newAscensionMultiplier = 1.0 + newBonus;

        ItemStack reset = ReforgeEquip.withUpgradeLevel(item, 0);
        reset = setItemAscensionTier(reset, newTier);
        reset = setAscensionBaseMultiplier(reset, newAscensionMultiplier);

        return reset;
    }

    public static String getTierLabel(int tier) {
        return switch (tier) {
            case 1 -> "[ TIER 1 ]";
            case 2 -> "[ TIER 2 ]";
            case 3 -> "[ TIER 3 ]";
            case 4 -> "[ TIER 4 ]";
            case 5 -> "[ TIER 5 ]";
            default -> "[ TIER " + tier + " ]";
        };
    }
}
