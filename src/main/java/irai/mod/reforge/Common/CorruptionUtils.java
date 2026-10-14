package irai.mod.reforge.Common;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import irai.mod.reforge.Config.CorruptionConfig;
import irai.mod.reforge.Interactions.ReforgeEquip;
import irai.mod.reforge.Socket.EssenceEffect;
import irai.mod.reforge.Socket.ResonanceSystem;
import irai.mod.reforge.Util.MetadataKeys;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class CorruptionUtils {

    private static volatile CorruptionConfig config = new CorruptionConfig();
    private static final boolean DEBUG_CORRUPTION_ROLLS = false;

    private static final List<EssenceEffect.StatType> WEAPON_STATS = List.of(
            EssenceEffect.StatType.ATTACK_SPEED,
            EssenceEffect.StatType.DAMAGE,
            EssenceEffect.StatType.CRIT_CHANCE,
            EssenceEffect.StatType.CRIT_DAMAGE,
            EssenceEffect.StatType.LIFE_STEAL
    );

    private static final List<EssenceEffect.StatType> ARMOR_STATS = List.of(
            EssenceEffect.StatType.HEALTH,
            EssenceEffect.StatType.DEFENSE,
            EssenceEffect.StatType.EVASION,
            EssenceEffect.StatType.REGENERATION,
            EssenceEffect.StatType.FIRE_DEFENSE,
            EssenceEffect.StatType.BLOCK_CHANCE,
            EssenceEffect.StatType.MOVEMENT_SPEED
    );

    private static final List<EssenceEffect.StatType> UTILITY_STATS = List.of(
            EssenceEffect.StatType.LUCK,
            EssenceEffect.StatType.MOVEMENT_SPEED
    );

    private CorruptionUtils() {}

    public static void setConfig(CorruptionConfig newConfig) {
        config = newConfig == null ? new CorruptionConfig() : newConfig;
    }

    public static CorruptionConfig getConfig() {
        return config;
    }

    public static boolean isCorrupted(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        Boolean corrupted = item.getFromMetadataOrNull(MetadataKeys.CORRUPTED, Codec.BOOLEAN);
        return corrupted != null && corrupted;
    }

    public static int getCorruptionCount(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return 0;
        }
        Integer count = item.getFromMetadataOrNull(MetadataKeys.CORRUPTION_COUNT, Codec.INTEGER);
        return count == null ? 0 : Math.max(0, count);
    }

    public static boolean canCorrupt(ItemStack item) {
        if (!config.isEnabled()) {
            return false;
        }
        if (item == null || item.isEmpty()) {
            return false;
        }
        int count = getCorruptionCount(item);
        return count < config.getMaxCorruptions();
    }

    public static ItemStack corruptItem(ItemStack item) {
        if (!canCorrupt(item)) {
            return item;
        }

        int corruptionCount = getCorruptionCount(item);
        int newCount = corruptionCount + 1;

        ItemStack result = applyCorruptionEffects(item, newCount);

        result = result.withMetadata(MetadataKeys.CORRUPTION_COUNT, Codec.INTEGER, newCount);
        result = result.withMetadata(MetadataKeys.CORRUPTION_MAX, Codec.DOUBLE, config.getMaxCorruptions());
        result = result.withMetadata(MetadataKeys.CORRUPTED, Codec.BOOLEAN, true);

        return result;
    }

    private static ItemStack applyCorruptionEffects(ItemStack item, int corruptionTier) {
        boolean ignore = ThreadLocalRandom.current().nextDouble() < 0.50;

        if (ignore) {
            debug("tier=" + corruptionTier + " item=" + safeItemId(item) + " outcome=IGNORE_EFFECT");
            return item;
        }

        double tierMultiplier = corruptionTier >= 2 ? 1.5 : 1.0;

        double minMod = config.getMinStatModifier();
        double maxMod = config.getMaxStatModifier();
        double modifier = minMod + ThreadLocalRandom.current().nextDouble() * (maxMod - minMod);
        modifier *= tierMultiplier;

        ItemStack result = item;

        if (corruptionTier >= 2 && hasAvailableNewStat(result)) {
            return applyTierTwoDistinctOutcome(result, modifier, corruptionTier);
        }

        boolean hasIncrease = false;
        boolean hasDecrease = false;
        boolean hasInvert = false;

        double roll = ThreadLocalRandom.current().nextDouble();
        double cumulative = 0.0;

        cumulative += config.getStatIncreaseChance();
        if (roll < cumulative) {
            result = applyStatIncrease(result, modifier);
            hasIncrease = true;
            debug("tier=" + corruptionTier + " item=" + safeItemId(item) + " outcome=STAT_INCREASE roll=" + formatChance(roll));
        }

        roll = ThreadLocalRandom.current().nextDouble();
        cumulative = 0.0;
        cumulative += config.getStatDecreaseChance();
        if (roll < cumulative) {
            result = applyStatDecrease(result, modifier);
            hasDecrease = true;
            debug("tier=" + corruptionTier + " item=" + safeItemId(item) + " outcome=STAT_DECREASE roll=" + formatChance(roll));
        }

        roll = ThreadLocalRandom.current().nextDouble();
        cumulative = 0.0;
        cumulative += config.getStatInvertChance();
        if (roll < cumulative) {
            result = applyStatInvert(result);
            hasInvert = true;
            debug("tier=" + corruptionTier + " item=" + safeItemId(item) + " outcome=STAT_INVERT roll=" + formatChance(roll));
        }

        if (!hasIncrease && !hasDecrease && !hasInvert) {
            roll = ThreadLocalRandom.current().nextDouble();
            if (roll < 0.60) {
                result = applyStatIncrease(result, modifier);
                debug("tier=" + corruptionTier + " item=" + safeItemId(item) + " outcome=FALLBACK_STAT_INCREASE roll=" + formatChance(roll));
            } else if (roll < 0.85) {
                result = applyAddNewStat(result, modifier, corruptionTier);
                debug("tier=" + corruptionTier + " item=" + safeItemId(item) + " outcome=FALLBACK_ADD_NEW_STAT roll=" + formatChance(roll));
            } else {
                result = applyResonantStat(result, modifier);
                debug("tier=" + corruptionTier + " item=" + safeItemId(item) + " outcome=FALLBACK_RESONANCE roll=" + formatChance(roll));
            }
        }

        if (corruptionTier >= 2) {
            roll = ThreadLocalRandom.current().nextDouble();
            cumulative = 0.0;
            cumulative += config.getStatRemoveChance();
            if (roll < cumulative) {
                result = applyStatRemove(result);
                debug("tier=" + corruptionTier + " item=" + safeItemId(item) + " outcome=STAT_REMOVE roll=" + formatChance(roll));
            }
        }

        roll = ThreadLocalRandom.current().nextDouble();
        cumulative = 0.0;
        cumulative += config.getAddNewStatChance();
        if (roll < cumulative && !hasIncrease) {
            result = applyAddNewStat(result, modifier, corruptionTier);
            debug("tier=" + corruptionTier + " item=" + safeItemId(item) + " outcome=BONUS_ADD_NEW_STAT roll=" + formatChance(roll));
        }

        roll = ThreadLocalRandom.current().nextDouble();
        cumulative = 0.0;
        cumulative += config.getResonantStatChance();
        if (roll < cumulative) {
            result = applyResonantStat(result, modifier);
            debug("tier=" + corruptionTier + " item=" + safeItemId(item) + " outcome=BONUS_RESONANCE roll=" + formatChance(roll));
        }

        return result;
    }

    private static ItemStack applyTierTwoDistinctOutcome(ItemStack item, double modifier, int corruptionTier) {
        double addNewWeight = Math.max(0.60, config.getAddNewStatChance() * 3.0);
        double resonantWeight = hasAvailableResonance(item)
                ? Math.max(0.25, config.getResonantStatChance() * 2.0)
                : 0.0;
        double decreaseWeight = config.getStatDecreaseChance() * 0.50;
        double invertWeight = config.getStatInvertChance() * 0.50;
        double removeWeight = config.getStatRemoveChance() * 0.25;

        double totalWeight = addNewWeight + resonantWeight + decreaseWeight + invertWeight + removeWeight;
        if (totalWeight <= 0.0) {
            debug("tier=" + corruptionTier + " item=" + safeItemId(item) + " outcome=TIER2_ADD_NEW_STAT totalWeight=0");
            return applyAddNewStat(item, modifier, corruptionTier);
        }

        double roll = ThreadLocalRandom.current().nextDouble(totalWeight);
        double originalRoll = roll;
        debug("tier=" + corruptionTier
                + " item=" + safeItemId(item)
                + " table=TIER2_DISTINCT"
                + " roll=" + formatChance(originalRoll)
                + " total=" + formatChance(totalWeight)
                + " addNew=" + formatChance(addNewWeight)
                + " resonance=" + formatChance(resonantWeight)
                + " decrease=" + formatChance(decreaseWeight)
                + " invert=" + formatChance(invertWeight)
                + " remove=" + formatChance(removeWeight));
        if (roll < addNewWeight) {
            debug("tier=" + corruptionTier + " item=" + safeItemId(item) + " outcome=TIER2_ADD_NEW_STAT");
            return applyAddNewStat(item, modifier, corruptionTier);
        }

        roll -= addNewWeight;
        if (roll < resonantWeight) {
            debug("tier=" + corruptionTier + " item=" + safeItemId(item) + " outcome=TIER2_RESONANCE");
            return applyResonantStat(item, modifier);
        }

        roll -= resonantWeight;
        if (roll < decreaseWeight) {
            debug("tier=" + corruptionTier + " item=" + safeItemId(item) + " outcome=TIER2_STAT_DECREASE");
            return applyStatDecrease(item, modifier);
        }

        roll -= decreaseWeight;
        if (roll < invertWeight) {
            debug("tier=" + corruptionTier + " item=" + safeItemId(item) + " outcome=TIER2_STAT_INVERT");
            return applyStatInvert(item);
        }

        debug("tier=" + corruptionTier + " item=" + safeItemId(item) + " outcome=TIER2_STAT_REMOVE");
        return applyStatRemove(item);
    }

    private static boolean hasAvailableNewStat(ItemStack item) {
        boolean isWeapon = ReforgeEquip.isWeapon(item);
        List<EssenceEffect.StatType> existing = getExistingStats(item);
        List<EssenceEffect.StatType> categoryStats = isWeapon ? WEAPON_STATS : ARMOR_STATS;

        for (EssenceEffect.StatType stat : categoryStats) {
            if (!existing.contains(stat)) {
                return true;
            }
        }

        for (EssenceEffect.StatType stat : UTILITY_STATS) {
            if (!existing.contains(stat)) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasAvailableResonance(ItemStack item) {
        if (hasCorruptionResonance(item)) {
            return false;
        }

        boolean isWeapon = ReforgeEquip.isWeapon(item);
        List<ResonanceSystem.ResonanceResult> resonances = ResonanceSystem.getResonancesByScope(isWeapon);
        if (resonances.isEmpty()) {
            resonances = ResonanceSystem.getAllResonanceResults();
        }

        for (ResonanceSystem.ResonanceResult resonance : resonances) {
            if (resonance != null && resonance.active()) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasCorruptionResonance(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }

        String corruptionResonance = item.getFromMetadataOrNull(MetadataKeys.CORRUPTION_RESONANCE_NAME, Codec.STRING);
        if (corruptionResonance != null && !corruptionResonance.isBlank()) {
            return true;
        }

        String legacyResonance = item.getFromMetadataOrNull(ResonanceSystem.META_RESONANCE_NAME, Codec.STRING);
        String socketRecipe = item.getFromMetadataOrNull(MetadataKeys.RESONANCE_RECIPE_NAME, Codec.STRING);
        return legacyResonance != null && !legacyResonance.isBlank()
                && (socketRecipe == null || socketRecipe.isBlank())
                && isCorrupted(item);
    }

    private static ItemStack applyStatIncrease(ItemStack item, double modifier) {
        List<EssenceEffect.StatType> stats = getExistingStats(item);
        if (stats.isEmpty()) {
            return applyAddNewStat(item, modifier, 1);
        }

        int numStatsToIncrease = 1 + ThreadLocalRandom.current().nextInt(Math.min(3, stats.size()));
        List<EssenceEffect.StatType> shuffled = new ArrayList<>(stats);
        java.util.Collections.shuffle(shuffled);
        List<EssenceEffect.StatType> chosenStats = shuffled.subList(0, numStatsToIncrease);

        ItemStack result = item;
        for (EssenceEffect.StatType stat : chosenStats) {
            double[] bonuses = getCorruptionBonus(result, stat);
            double flatBonus = bonuses[0];
            double percentBonus = bonuses[1];

            double increaseAmount = 1.0 + modifier;

            if (flatBonus != 0.0) {
                flatBonus *= increaseAmount;
            }
            if (percentBonus != 0.0) {
                percentBonus *= increaseAmount;
            }
            if (flatBonus == 0.0 && percentBonus == 0.0) {
                flatBonus = modifier * 15;
                percentBonus = modifier * 5;
            }

            result = setCorruptionBonus(result, stat, flatBonus, percentBonus);
        }

        return result;
    }

    private static ItemStack applyStatDecrease(ItemStack item, double modifier) {
        List<EssenceEffect.StatType> stats = getExistingStats(item);
        if (stats.isEmpty()) {
            return item;
        }

        EssenceEffect.StatType stat = stats.get(ThreadLocalRandom.current().nextInt(stats.size()));
        double[] bonuses = getCorruptionBonus(item, stat);
        double flatBonus = bonuses[0];
        double percentBonus = bonuses[1];

        double decreaseAmount = 1.0 - modifier;

        if (flatBonus != 0.0) {
            flatBonus *= decreaseAmount;
        }
        if (percentBonus != 0.0) {
            percentBonus *= decreaseAmount;
        }

        if (flatBonus == 0.0 && percentBonus == 0.0) {
            flatBonus = -modifier * 10;
            percentBonus = -modifier * 3;
            return setCorruptionBonus(item, stat, flatBonus, percentBonus);
        }

        return setCorruptionBonus(item, stat, flatBonus, percentBonus);
    }

    private static ItemStack applyStatInvert(ItemStack item) {
        List<EssenceEffect.StatType> stats = getExistingStats(item);
        if (stats.isEmpty()) {
            return item;
        }

        EssenceEffect.StatType stat = stats.get(ThreadLocalRandom.current().nextInt(stats.size()));
        double[] bonuses = getCorruptionBonus(item, stat);
        double flatBonus = bonuses[0];
        double percentBonus = bonuses[1];

        return setCorruptionBonus(item, stat, percentBonus, flatBonus);
    }

    private static ItemStack applyStatRemove(ItemStack item) {
        List<EssenceEffect.StatType> stats = getExistingStats(item);
        if (stats.isEmpty()) {
            return item;
        }

        EssenceEffect.StatType stat = stats.get(ThreadLocalRandom.current().nextInt(stats.size()));
        return removeCorruptionBonus(item, stat);
    }

    private static ItemStack applyAddNewStat(ItemStack item, double modifier, int corruptionTier) {
        boolean isWeapon = ReforgeEquip.isWeapon(item);
        List<EssenceEffect.StatType> existing = getExistingStats(item);

        List<EssenceEffect.StatType> categoryStats = isWeapon ? WEAPON_STATS : ARMOR_STATS;
        List<EssenceEffect.StatType> available = new ArrayList<>();

        for (EssenceEffect.StatType stat : categoryStats) {
            if (!existing.contains(stat)) {
                available.add(stat);
            }
        }

        if (available.isEmpty()) {
            for (EssenceEffect.StatType stat : UTILITY_STATS) {
                if (!existing.contains(stat)) {
                    available.add(stat);
                }
            }
        }

        if (available.isEmpty()) {
            return item;
        }

        EssenceEffect.StatType newStat = available.get(ThreadLocalRandom.current().nextInt(available.size()));
        debug("tier=" + corruptionTier
                + " item=" + safeItemId(item)
                + " addedStat=" + newStat.name()
                + " available=" + available.size()
                + " existing=" + existing.size());
        double flatBonus = 0.0;
        double percentBonus = 0.0;

        double basePercent = 5.0 + ThreadLocalRandom.current().nextDouble() * 20.0;
        double baseFlat = 1.0 + ThreadLocalRandom.current().nextDouble() * 9.0;

        if (newStat == EssenceEffect.StatType.DAMAGE) {
            percentBonus = basePercent;
            flatBonus = baseFlat;
        } else if (newStat == EssenceEffect.StatType.HEALTH || newStat == EssenceEffect.StatType.DEFENSE) {
            flatBonus = baseFlat * 5;
        } else if (newStat == EssenceEffect.StatType.REGENERATION) {
            flatBonus = baseFlat * 2;
        } else if (newStat == EssenceEffect.StatType.MOVEMENT_SPEED || newStat == EssenceEffect.StatType.EVASION) {
            percentBonus = basePercent * 0.5;
        } else if (newStat == EssenceEffect.StatType.ATTACK_SPEED || newStat == EssenceEffect.StatType.CRIT_CHANCE) {
            percentBonus = basePercent * 0.6;
        } else if (newStat == EssenceEffect.StatType.CRIT_DAMAGE) {
            percentBonus = basePercent * 1.5;
        } else if (newStat == EssenceEffect.StatType.LIFE_STEAL) {
            percentBonus = basePercent * 0.4;
        } else if (newStat == EssenceEffect.StatType.BLOCK_CHANCE || newStat == EssenceEffect.StatType.FIRE_DEFENSE) {
            percentBonus = basePercent * 0.5;
        } else if (newStat == EssenceEffect.StatType.LUCK) {
            flatBonus = baseFlat;
            percentBonus = basePercent * 0.3;
        }

        return setCorruptionBonus(item, newStat, flatBonus, percentBonus);
    }

    private static ItemStack applyResonantStat(ItemStack item, double modifier) {
        boolean isWeapon = ReforgeEquip.isWeapon(item);
        List<ResonanceSystem.ResonanceResult> resonances = ResonanceSystem.getResonancesByScope(isWeapon);
        int scopedCount = resonances.size();
        if (resonances.isEmpty()) {
            resonances = ResonanceSystem.getAllResonanceResults();
        }
        if (resonances.isEmpty()) {
            debug("item=" + safeItemId(item) + " resonance=NONE scopedCandidates=" + scopedCount + " fallbackCandidates=0");
            return item;
        }

        ResonanceSystem.ResonanceResult chosen = resonances.get(ThreadLocalRandom.current().nextInt(resonances.size()));
        if (chosen == null || !chosen.active()) {
            debug("item=" + safeItemId(item)
                    + " resonance=INACTIVE_OR_NULL"
                    + " scopedCandidates=" + scopedCount
                    + " fallbackCandidates=" + resonances.size());
            return item;
        }

        String effect = ResonanceSystem.buildDetailedEffect(chosen, isWeapon);
        debug("item=" + safeItemId(item)
                + " resonance=APPLIED"
                + " name=\"" + chosen.name() + "\""
                + " type=" + chosen.type().name()
                + " scopedCandidates=" + scopedCount
                + " fallbackCandidates=" + resonances.size());

        return item
                .withMetadata(MetadataKeys.CORRUPTION_RESONANCE_NAME, Codec.STRING, chosen.name())
                .withMetadata(MetadataKeys.CORRUPTION_RESONANCE_EFFECT, Codec.STRING, effect)
                .withMetadata(MetadataKeys.CORRUPTION_RESONANCE_TYPE, Codec.STRING, chosen.type().name())
                .withMetadata(MetadataKeys.CORRUPTION_RESONANCE_QUALITY, Codec.STRING, ResonanceSystem.LEGENDARY_QUALITY);
    }

    private static List<EssenceEffect.StatType> getExistingStats(ItemStack item) {
        List<EssenceEffect.StatType> stats = new ArrayList<>();
        String[] statKeys = item.getFromMetadataOrNull(MetadataKeys.CORRUPTION_BONUS_STATS, Codec.STRING_ARRAY);
        if (statKeys != null) {
            for (String key : statKeys) {
                try {
                    stats.add(EssenceEffect.StatType.valueOf(key));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return stats;
    }

    private static double[] getCorruptionBonus(ItemStack item, EssenceEffect.StatType stat) {
        String[] statKeys = item.getFromMetadataOrNull(MetadataKeys.CORRUPTION_BONUS_STATS, Codec.STRING_ARRAY);
        String[] flatValues = item.getFromMetadataOrNull(MetadataKeys.CORRUPTION_BONUS_FLAT, Codec.STRING_ARRAY);
        String[] percentValues = item.getFromMetadataOrNull(MetadataKeys.CORRUPTION_BONUS_PERCENT, Codec.STRING_ARRAY);

        if (statKeys != null && flatValues != null && percentValues != null) {
            int count = Math.min(statKeys.length, Math.min(flatValues.length, percentValues.length));
            for (int i = 0; i < count; i++) {
                if (stat.name().equals(statKeys[i])) {
                    double flat = parseDoubleOrZero(flatValues[i]);
                    double percent = parseDoubleOrZero(percentValues[i]);
                    return new double[]{flat, percent};
                }
            }
        }
        return new double[]{0.0, 0.0};
    }

    private static ItemStack setCorruptionBonus(ItemStack item, EssenceEffect.StatType stat, double flat, double percent) {
        List<String> statKeys = new ArrayList<>();
        List<String> flatValues = new ArrayList<>();
        List<String> percentValues = new ArrayList<>();

        String[] existingKeys = item.getFromMetadataOrNull(MetadataKeys.CORRUPTION_BONUS_STATS, Codec.STRING_ARRAY);
        String[] existingFlat = item.getFromMetadataOrNull(MetadataKeys.CORRUPTION_BONUS_FLAT, Codec.STRING_ARRAY);
        String[] existingPercent = item.getFromMetadataOrNull(MetadataKeys.CORRUPTION_BONUS_PERCENT, Codec.STRING_ARRAY);

        boolean found = false;
        if (existingKeys != null && existingFlat != null && existingPercent != null) {
            int count = Math.min(existingKeys.length, Math.min(existingFlat.length, existingPercent.length));
            for (int i = 0; i < count; i++) {
                if (stat.name().equals(existingKeys[i])) {
                    statKeys.add(stat.name());
                    flatValues.add(String.valueOf(flat));
                    percentValues.add(String.valueOf(percent));
                    found = true;
                } else {
                    statKeys.add(existingKeys[i]);
                    flatValues.add(existingFlat[i]);
                    percentValues.add(existingPercent[i]);
                }
            }
        }

        if (!found) {
            statKeys.add(stat.name());
            flatValues.add(String.valueOf(flat));
            percentValues.add(String.valueOf(percent));
        }

        return item
                .withMetadata(MetadataKeys.CORRUPTION_BONUS_STATS, Codec.STRING_ARRAY, statKeys.toArray(new String[0]))
                .withMetadata(MetadataKeys.CORRUPTION_BONUS_FLAT, Codec.STRING_ARRAY, flatValues.toArray(new String[0]))
                .withMetadata(MetadataKeys.CORRUPTION_BONUS_PERCENT, Codec.STRING_ARRAY, percentValues.toArray(new String[0]));
    }

    private static ItemStack removeCorruptionBonus(ItemStack item, EssenceEffect.StatType stat) {
        List<String> statKeys = new ArrayList<>();
        List<String> flatValues = new ArrayList<>();
        List<String> percentValues = new ArrayList<>();

        String[] existingKeys = item.getFromMetadataOrNull(MetadataKeys.CORRUPTION_BONUS_STATS, Codec.STRING_ARRAY);
        String[] existingFlat = item.getFromMetadataOrNull(MetadataKeys.CORRUPTION_BONUS_FLAT, Codec.STRING_ARRAY);
        String[] existingPercent = item.getFromMetadataOrNull(MetadataKeys.CORRUPTION_BONUS_PERCENT, Codec.STRING_ARRAY);

        if (existingKeys != null && existingFlat != null && existingPercent != null) {
            int count = Math.min(existingKeys.length, Math.min(existingFlat.length, existingPercent.length));
            for (int i = 0; i < count; i++) {
                if (!stat.name().equals(existingKeys[i])) {
                    statKeys.add(existingKeys[i]);
                    flatValues.add(existingFlat[i]);
                    percentValues.add(existingPercent[i]);
                }
            }
        }

        return item
                .withMetadata(MetadataKeys.CORRUPTION_BONUS_STATS, Codec.STRING_ARRAY, statKeys.toArray(new String[0]))
                .withMetadata(MetadataKeys.CORRUPTION_BONUS_FLAT, Codec.STRING_ARRAY, flatValues.toArray(new String[0]))
                .withMetadata(MetadataKeys.CORRUPTION_BONUS_PERCENT, Codec.STRING_ARRAY, percentValues.toArray(new String[0]));
    }

    private static double parseDoubleOrZero(String value) {
        if (value == null || value.isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private static String safeItemId(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return "<empty>";
        }
        String itemId = item.getItemId();
        return itemId == null || itemId.isBlank() ? "<unknown>" : itemId;
    }

    private static String formatChance(double value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value);
    }

    private static void debug(String message) {
        if (DEBUG_CORRUPTION_ROLLS) {
            System.out.println("[SocketReforge][CORRUPTION_ROLL] " + message);
        }
    }

    public static String getRequiredMaterial(int corruptionCount) {
        return corruptionCount == 0 ? config.getTier1Cost() : config.getTier2Cost();
    }

    public static boolean isMaxCorrupted(ItemStack item) {
        return getCorruptionCount(item) >= config.getMaxCorruptions();
    }
}
