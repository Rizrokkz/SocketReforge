package irai.mod.reforge.Config;

import static com.hypixel.hytale.codec.Codec.BOOLEAN;
import static com.hypixel.hytale.codec.Codec.DOUBLE_ARRAY;
import static com.hypixel.hytale.codec.Codec.STRING;
import static com.hypixel.hytale.codec.Codec.STRING_ARRAY;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Configuration for refinement rates.
 * Allows server operators to override damage/defense multipliers, break chances, and reforge weights
 * for both weapons and armor.
 */
@SuppressWarnings("removal")
public class RefinementConfig {
    private static final int DEFAULT_MAX_LEVEL = 15;
    private static final String DEFAULT_REFINEMENT_PREFIX = " +";
    private static final String DEFAULT_REFINEMENT_SUFFIX = "";
    private static final String[] DEFAULT_REFINEMENT_GRADE_LETTERS = {"E", "D", "C", "B", "A", "S"};
    private static final String[] DEFAULT_WEAPON_PREFIX_LABELS = {
            "",
            "Sharp",
            "Deadly",
            "Legendary",
            "Mythic",
            "Epic",
            "Ancient",
            "Relic",
            "Exalted",
            "Ascended",
            "Divine",
            "Celestial",
            "Eternal",
            "Godlike",
            "Supreme",
            "Transcendent"
    };
    private static final String[] DEFAULT_ARMOR_PREFIX_LABELS = {
            "",
            "Sturdy",
            "Fortified",
            "Impenetrable",
            "Mythic",
            "Epic",
            "Ancient",
            "Relic",
            "Exalted",
            "Ascended",
            "Divine",
            "Celestial",
            "Eternal",
            "Godlike",
            "Supreme",
            "Transcendent"
    };
    private static final boolean DEFAULT_REFINEMENT_LEVEL_USE_PREFIX = false;
    private static final double[] DEFAULT_WEIGHTS_0_TO_1 = {0.00, 0.65, 0.34, 0.01};
    private static final double[] DEFAULT_WEIGHTS_1_TO_2 = {0.35, 0.45, 0.19, 0.01};
    private static final double[] DEFAULT_WEIGHTS_2_TO_3 = {0.60, 0.30, 0.095, 0.005};

    public static final BuilderCodec<RefinementConfig> CODEC = BuilderCodec.<RefinementConfig>builder(RefinementConfig.class, RefinementConfig::new)
            .append(
                    new KeyedCodec<>("MAX_REFINEMENT_LEVEL", DOUBLE_ARRAY),
                    (config, values) -> {
                        if (values != null && values.length > 0) {
                            config.setMaxLevel((int) Math.round(values[0]));
                        }
                    },
                    config -> new double[] {config.maxLevel}
            ).add()
            .append(
                    new KeyedCodec<>("REFINEMENT_MATERIAL_TIERS", com.hypixel.hytale.codec.Codec.STRING_ARRAY),
                    (config, values) -> config.setMaterialTierEntries(values),
                    config -> config.materialTierEntries == null ? new String[0] : config.materialTierEntries
            ).add()
            .append(
                    new KeyedCodec<>("REFINEMENT_LEVEL_PREFIX", STRING),
                    (config, value) -> config.setRefinementLevelPrefix(value),
                    RefinementConfig::getRefinementLevelPrefix
            ).add()
            .append(
                    new KeyedCodec<>("REFINEMENT_LEVEL_SUFFIX", STRING),
                    (config, value) -> config.setRefinementLevelSuffix(value),
                    RefinementConfig::getRefinementLevelSuffix
            ).add()
            .append(
                    new KeyedCodec<>("REFINEMENT_LEVEL_USE_PREFIX", BOOLEAN),
                    (config, value) -> config.setRefinementLevelUsePrefix(value),
                    RefinementConfig::isRefinementLevelUsePrefix
            ).add()
            .append(
                    new KeyedCodec<>("REFINEMENT_LEVEL_LABELS", STRING_ARRAY),
                    (config, value) -> config.setRefinementLevelLabels(value),
                    RefinementConfig::getRefinementLevelLabels
            ).add()
            .append(
                    new KeyedCodec<>("REFINEMENT_LEVEL_LABELS_ARMOR", STRING_ARRAY),
                    (config, value) -> config.setRefinementLevelLabelsArmor(value),
                    RefinementConfig::getRefinementLevelLabelsArmor
            ).add()
            // Damage multipliers per upgrade level (0..max) - weapons
            .append(
                    new KeyedCodec<>("DAMAGE_MULTIPLIERS", DOUBLE_ARRAY),
                    (config, multipliers) -> config.damageMultipliers = multipliers,
                    RefinementConfig::getDamageMultipliers
            ).add()
            // Defense multipliers per upgrade level (0..max) - armor
            .append(
                    new KeyedCodec<>("DEFENSE_MULTIPLIERS", DOUBLE_ARRAY),
                    (config, multipliers) -> config.defenseMultipliers = multipliers,
                    RefinementConfig::getDefenseMultipliers
            ).add()
            // Break chances per upgrade transition - weapons
            .append(
                    new KeyedCodec<>("BREAK_CHANCES", DOUBLE_ARRAY),
                    (config, chances) -> config.breakChances = chances,
                    RefinementConfig::getBreakChances
            ).add()
            // Break chances per upgrade transition - armor
            .append(
                    new KeyedCodec<>("ARMOR_BREAK_CHANCES", DOUBLE_ARRAY),
                    (config, chances) -> config.armorBreakChances = chances,
                    RefinementConfig::getArmorBreakChances
            ).add()
            // Reforge weights for 0->1 transition (degrade, same, upgrade, jackpot)
            .append(
                    new KeyedCodec<>("WEIGHTS_0_TO_1", DOUBLE_ARRAY),
                    (config, weights) -> config.weights0to1 = weights,
                    RefinementConfig::getWeights0to1
            ).add()
            // Reforge weights for 1->2 transition
            .append(
                    new KeyedCodec<>("WEIGHTS_1_TO_2", DOUBLE_ARRAY),
                    (config, weights) -> config.weights1to2 = weights,
                    RefinementConfig::getWeights1to2
            ).add()
            // Reforge weights for 2->3 transition
            .append(
                    new KeyedCodec<>("WEIGHTS_2_TO_3", DOUBLE_ARRAY),
                    (config, weights) -> config.weights2to3 = weights,
                    RefinementConfig::getWeights2to3
            ).add()
            .append(
                    new KeyedCodec<>("WEIGHTS_BY_LEVEL", DOUBLE_ARRAY),
                    (config, weights) -> config.setWeightsByLevel(weights),
                    RefinementConfig::getWeightsByLevel
            ).add()
            .build();

    // Global configuration
    private int maxLevel = DEFAULT_MAX_LEVEL;
    // Format: "min-max=ItemId:Cost"
    private String[] materialTierEntries = buildDefaultMaterialTierEntries(DEFAULT_MAX_LEVEL);
    private String refinementLevelPrefix = DEFAULT_REFINEMENT_PREFIX;
    private String refinementLevelSuffix = DEFAULT_REFINEMENT_SUFFIX;
    private boolean refinementLevelUsePrefix = DEFAULT_REFINEMENT_LEVEL_USE_PREFIX;
    private String[] refinementLevelLabels = buildDefaultRefinementLevelLabels(DEFAULT_MAX_LEVEL, DEFAULT_REFINEMENT_LEVEL_USE_PREFIX, false);
    private String[] refinementLevelLabelsArmor = buildDefaultRefinementLevelLabels(DEFAULT_MAX_LEVEL, DEFAULT_REFINEMENT_LEVEL_USE_PREFIX, true);

    private transient List<MaterialTier> materialTierCache;
    private transient List<String> materialIdCache;

    // ── Weapon Configuration ──────────────────────────────────────────────────

    // Damage multipliers per upgrade level (weapons)
    // Level 0 = base (no +), Level 1 = +1, Level 2 = +2, Level 3 = +3
    public double[] damageMultipliers = buildDefaultDamageMultipliers(maxLevel);

    // Break chances per upgrade transition for weapons (0->1, 1->2, 2->3)
    public double[] breakChances = buildDefaultBreakChances(maxLevel, 0.01, 0.06, 0.16, 0.30);

    // ── Armor Configuration ───────────────────────────────────────────────────

    // Defense multipliers per upgrade level (armor)
    // Level 0 = base (no +), Level 1 = +1, Level 2 = +2, Level 3 = +3
    public double[] defenseMultipliers = buildDefaultDefenseMultipliers(maxLevel);

    // Break chances per upgrade transition for armor (0->1, 1->2, 2->3)
    public double[] armorBreakChances = buildDefaultBreakChances(maxLevel, 0.01, 0.05, 0.13, 0.22);

    // ── Shared Reforge Weights ────────────────────────────────────────────────

    // Reforge weights for 0->1 transition: [degrade, same, upgrade, jackpot]
    public double[] weights0to1 = DEFAULT_WEIGHTS_0_TO_1;

    // Reforge weights for 1->2 transition: [degrade, same, upgrade, jackpot]
    public double[] weights1to2 = DEFAULT_WEIGHTS_1_TO_2;

    // Reforge weights for 2->3 transition: [degrade, same, upgrade, jackpot]
    public double[] weights2to3 = DEFAULT_WEIGHTS_2_TO_3;

    // Per-level weights: flattened array, 4 entries per level (degrade/same/upgrade/jackpot).
    public double[] weightsByLevel = buildDefaultWeightsByLevel(maxLevel);

    public RefinementConfig() {
        this.weightsByLevel = buildTieredWeightsByLevel();
        syncLegacyWeightsFromByLevel(this.weightsByLevel);
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public int getMaxLevel() { return Math.max(1, maxLevel); }
    public double[] getDamageMultipliers()  { return damageMultipliers; }
    public double[] getDefenseMultipliers() { return defenseMultipliers; }
    public double[] getBreakChances()       { return breakChances; }
    public double[] getArmorBreakChances()  { return armorBreakChances; }
    public double[] getWeights0to1()        { return weights0to1; }
    public double[] getWeights1to2()        { return weights1to2; }
    public double[] getWeights2to3()        { return weights2to3; }
    public double[] getWeightsByLevel()     { return weightsByLevel; }
    public String[] getMaterialTierEntries() { return materialTierEntries == null || materialTierEntries.length == 0
            ? buildDefaultMaterialTierEntries(getMaxLevel())
            : materialTierEntries; }
    public String getRefinementLevelPrefix() { return refinementLevelPrefix == null ? DEFAULT_REFINEMENT_PREFIX : refinementLevelPrefix; }
    public String getRefinementLevelSuffix() { return refinementLevelSuffix == null ? DEFAULT_REFINEMENT_SUFFIX : refinementLevelSuffix; }
    public boolean isRefinementLevelUsePrefix() { return refinementLevelUsePrefix; }
    public String[] getRefinementLevelLabels() {
        String[] labels = refinementLevelLabels;
        int required = Math.max(1, getMaxLevel()) + 1;
        if (labels == null || labels.length == 0) {
            labels = buildDefaultRefinementLevelLabels(getMaxLevel(), refinementLevelUsePrefix, false);
            refinementLevelLabels = labels;
        } else if (labels.length < required) {
            labels = extendStringArray(labels, required, buildDefaultRefinementLevelLabels(getMaxLevel(), refinementLevelUsePrefix, false));
            refinementLevelLabels = labels;
        }
        return labels;
    }

    public String[] getRefinementLevelLabelsArmor() {
        String[] labels = refinementLevelLabelsArmor;
        int required = Math.max(1, getMaxLevel()) + 1;
        if (labels == null || labels.length == 0) {
            labels = buildDefaultRefinementLevelLabels(getMaxLevel(), refinementLevelUsePrefix, true);
            refinementLevelLabelsArmor = labels;
        } else if (labels.length < required) {
            labels = extendStringArray(labels, required, buildDefaultRefinementLevelLabels(getMaxLevel(), refinementLevelUsePrefix, true));
            refinementLevelLabelsArmor = labels;
        }
        return labels;
    }

    public String[] getRefinementLevelLabels(boolean isArmor) {
        return isArmor ? getRefinementLevelLabelsArmor() : getRefinementLevelLabels();
    }
    public void setDamageMultipliers(double[] values)   { this.damageMultipliers = values; }
    public void setDefenseMultipliers(double[] values)  { this.defenseMultipliers = values; }
    public void setBreakChances(double[] values)        { this.breakChances = values; }
    public void setArmorBreakChances(double[] values)   { this.armorBreakChances = values; }
    public void setWeights0to1(double[] values)         { this.weights0to1 = values; }
    public void setWeights1to2(double[] values)         { this.weights1to2 = values; }
    public void setWeights2to3(double[] values)         { this.weights2to3 = values; }
    public void setWeightsByLevel(double[] values)      { this.weightsByLevel = values; }
    public void setRefinementLevelPrefix(String value) { this.refinementLevelPrefix = value == null ? DEFAULT_REFINEMENT_PREFIX : value; }
    public void setRefinementLevelSuffix(String value) { this.refinementLevelSuffix = value == null ? DEFAULT_REFINEMENT_SUFFIX : value; }
    public void setRefinementLevelUsePrefix(boolean value) { this.refinementLevelUsePrefix = value; }
    public void setRefinementLevelLabels(String[] values) {
        if (values == null || values.length == 0) {
            this.refinementLevelLabels = buildDefaultRefinementLevelLabels(getMaxLevel(), refinementLevelUsePrefix, false);
        } else {
            this.refinementLevelLabels = values;
        }
    }

    public void setRefinementLevelLabelsArmor(String[] values) {
        if (values == null || values.length == 0) {
            this.refinementLevelLabelsArmor = buildDefaultRefinementLevelLabels(getMaxLevel(), refinementLevelUsePrefix, true);
        } else {
            this.refinementLevelLabelsArmor = values;
        }
    }

    public void setRefinementLevelLabel(int level, String label) {
        setRefinementLevelLabel(level, label, false);
    }

    public void setRefinementLevelLabel(int level, String label, boolean isArmor) {
        if (level < 0) return;
        int required = Math.max(getMaxLevel(), level) + 1;
        String[] labels = getRefinementLevelLabels(isArmor);
        if (labels.length < required) {
            labels = extendStringArray(labels, required, buildDefaultRefinementLevelLabels(required - 1, refinementLevelUsePrefix, isArmor));
        }
        labels[level] = label == null ? "" : label;
        if (isArmor) {
            refinementLevelLabelsArmor = labels;
        } else {
            refinementLevelLabels = labels;
        }
    }

    public String getRefinementLevelLabel(int level) {
        return getRefinementLevelLabel(level, false);
    }

    public String getRefinementLevelLabel(int level, boolean isArmor) {
        if (level <= 0) {
            return "";
        }
        String[] labels = getRefinementLevelLabels(isArmor);
        if (level < labels.length) {
            String value = labels[level];
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return getRefinementLevelPrefix() + level + getRefinementLevelSuffix();
    }

    public String formatRefinementSuffix(int level) {
        return formatRefinementSuffix(level, false);
    }

    public String formatRefinementSuffix(int level, boolean isArmor) {
        if (level <= 0) {
            return "";
        }
        return getRefinementLevelLabel(level, isArmor);
    }

    public String applyRefinementToName(String baseName, int level) {
        return applyRefinementToName(baseName, level, false);
    }

    public String applyRefinementToName(String baseName, int level, boolean isArmor) {
        if (baseName == null) {
            return null;
        }
        String tag = formatRefinementSuffix(level, isArmor);
        if (tag.isEmpty()) {
            return baseName;
        }
        return refinementLevelUsePrefix ? (tag + baseName) : (baseName + tag);
    }

    public void applyDefaultRefinementLevelLabels() {
        this.refinementLevelLabels = buildDefaultRefinementLevelLabels(getMaxLevel(), refinementLevelUsePrefix, false);
        this.refinementLevelLabelsArmor = buildDefaultRefinementLevelLabels(getMaxLevel(), refinementLevelUsePrefix, true);
    }

    public void setMaxLevel(int level) {
        int clamped = Math.max(1, level);
        this.maxLevel = clamped;
        invalidateMaterialCache();
        this.weightsByLevel = extendArray(this.weightsByLevel, clamped * 4, buildTieredWeightsByLevel());
        enforceNoJackpot(this.weightsByLevel, Math.max(0, (clamped - 1) * 4));
        this.damageMultipliers = extendArray(this.damageMultipliers, clamped + 1, buildDefaultDamageMultipliers(clamped));
        this.defenseMultipliers = extendArray(this.defenseMultipliers, clamped + 1, buildDefaultDefenseMultipliers(clamped));
        this.breakChances = extendArray(this.breakChances, clamped, buildDefaultBreakChances(clamped, 0.01, 0.06, 0.16, 0.30));
        this.armorBreakChances = extendArray(this.armorBreakChances, clamped, buildDefaultBreakChances(clamped, 0.01, 0.05, 0.13, 0.22));
        this.refinementLevelLabels = extendStringArray(this.refinementLevelLabels, clamped + 1, buildDefaultRefinementLevelLabels(clamped, refinementLevelUsePrefix, false));
        this.refinementLevelLabelsArmor = extendStringArray(this.refinementLevelLabelsArmor, clamped + 1, buildDefaultRefinementLevelLabels(clamped, refinementLevelUsePrefix, true));
    }

    public void setMaterialTierEntries(String[] values) {
        if (values == null || values.length == 0) {
            this.materialTierEntries = buildDefaultMaterialTierEntries(getMaxLevel());
        } else {
            this.materialTierEntries = values;
        }
        invalidateMaterialCache();
    }

    public void applyDefaultMultipliersAndWeights() {
        int safeMax = getMaxLevel();
        this.weightsByLevel = buildTieredWeightsByLevel();
        syncLegacyWeightsFromByLevel(this.weightsByLevel);
        this.damageMultipliers = buildDefaultDamageMultipliers(safeMax);
        this.defenseMultipliers = buildDefaultDefenseMultipliers(safeMax);
    }

    public void resetMaterialTiersToDefault() {
        this.materialTierEntries = buildDefaultMaterialTierEntries(getMaxLevel());
        invalidateMaterialCache();
    }

    public void resetToDefaults() {
        RefinementConfig defaults = new RefinementConfig();
        this.damageMultipliers = defaults.damageMultipliers == null ? null : defaults.damageMultipliers.clone();
        this.defenseMultipliers = defaults.defenseMultipliers == null ? null : defaults.defenseMultipliers.clone();
        this.breakChances = defaults.breakChances == null ? null : defaults.breakChances.clone();
        this.armorBreakChances = defaults.armorBreakChances == null ? null : defaults.armorBreakChances.clone();
        this.weights0to1 = defaults.weights0to1 == null ? null : defaults.weights0to1.clone();
        this.weights1to2 = defaults.weights1to2 == null ? null : defaults.weights1to2.clone();
        this.weights2to3 = defaults.weights2to3 == null ? null : defaults.weights2to3.clone();
        this.weightsByLevel = defaults.weightsByLevel == null ? null : defaults.weightsByLevel.clone();
        this.maxLevel = defaults.maxLevel;
        this.materialTierEntries = defaults.materialTierEntries == null ? null : defaults.materialTierEntries.clone();
        this.refinementLevelPrefix = defaults.refinementLevelPrefix;
        this.refinementLevelSuffix = defaults.refinementLevelSuffix;
        this.refinementLevelUsePrefix = defaults.refinementLevelUsePrefix;
        this.refinementLevelLabels = defaults.refinementLevelLabels == null ? null : defaults.refinementLevelLabels.clone();
        this.refinementLevelLabelsArmor = defaults.refinementLevelLabelsArmor == null ? null : defaults.refinementLevelLabelsArmor.clone();
        invalidateMaterialCache();
    }

    // ── Helper Methods ────────────────────────────────────────────────────────

    /**
     * Gets the damage multiplier for a given weapon upgrade level.
     * @param level The upgrade level (0-3)
     * @return The damage multiplier, or 1.0 if invalid
     */
    public double getDamageMultiplier(int level) {
        return sample(damageMultipliers, level, 1.0);
    }

    /**
     * Gets the defense multiplier for a given armor upgrade level.
     * @param level The upgrade level (0-3)
     * @return The defense multiplier, or 1.0 if invalid
     */
    public double getDefenseMultiplier(int level) {
        return sample(defenseMultipliers, level, 1.0);
    }

    /**
     * Gets the break chance for a given weapon current level.
     * @param currentLevel The current upgrade level (0-2)
     * @return The break chance, or 0.0 if invalid
     */
    public double getBreakChance(int currentLevel) {
        return sample(breakChances, currentLevel, 0.0);
    }

    /**
     * Gets the break chance for a given armor current level.
     * @param currentLevel The current upgrade level (0-2)
     * @return The armor break chance, or 0.0 if invalid
     */
    public double getArmorBreakChance(int currentLevel) {
        return sample(armorBreakChances, currentLevel, 0.0);
    }

    /**
     * Gets the reforge weights for a given current level.
     * @param currentLevel The current upgrade level (0-2)
     * @return The weights array [degrade, same, upgrade, jackpot], or null if invalid
     */
    public double[] getReforgeWeights(int currentLevel) {
        int safeLevel = Math.max(0, Math.min(currentLevel, Math.max(0, getMaxLevel() - 1)));
        double[] fromByLevel = extractWeightsByLevel(safeLevel);
        if (fromByLevel != null) {
            if (currentLevel + 2 > getMaxLevel()) {
                double[] adjusted = fromByLevel.clone();
                enforceNoJackpot(adjusted, 0);
                return adjusted;
            }
            return fromByLevel;
        }
        double[] fallback;
        switch (safeLevel) {
            case 0:
                fallback = weights0to1;
                break;
            case 1:
                fallback = weights1to2;
                break;
            case 2:
                fallback = weights2to3;
                break;
            default:
                fallback = weights2to3;
                break;
        }
        if (currentLevel + 2 > getMaxLevel()) {
            double[] adjusted = fallback == null ? null : fallback.clone();
            if (adjusted != null) {
                enforceNoJackpot(adjusted, 0);
            }
            return adjusted;
        }
        return fallback;
    }

    public List<MaterialTier> getMaterialTiers() {
        if (materialTierCache != null) {
            return materialTierCache;
        }
        List<MaterialTier> parsed = parseMaterialTiers(materialTierEntries, getMaxLevel());
        if (parsed.isEmpty()) {
            parsed.add(new MaterialTier(0, getMaxLevel(), "Refinement_Glob", 3));
        }
        materialTierCache = Collections.unmodifiableList(parsed);
        return materialTierCache;
    }

    public List<String> getMaterialItemIds() {
        if (materialIdCache != null) {
            return materialIdCache;
        }
        List<String> ids = new ArrayList<>();
        for (MaterialTier tier : getMaterialTiers()) {
            if (tier.itemId == null || tier.itemId.isBlank()) continue;
            if (!ids.contains(tier.itemId)) {
                ids.add(tier.itemId);
            }
        }
        materialIdCache = Collections.unmodifiableList(ids);
        return materialIdCache;
    }

    public MaterialTier getMaterialTierForLevel(int level) {
        int safe = Math.max(0, level);
        for (MaterialTier tier : getMaterialTiers()) {
            if (tier.matches(safe)) {
                return tier;
            }
        }
        List<MaterialTier> tiers = getMaterialTiers();
        return tiers.isEmpty() ? null : tiers.get(0);
    }

    private void invalidateMaterialCache() {
        materialTierCache = null;
        materialIdCache = null;
    }

    private static double sample(double[] values, int index, double fallback) {
        if (values == null || values.length == 0) return fallback;
        if (index < 0) return values[0];
        if (index >= values.length) return values[values.length - 1];
        return values[index];
    }

    private static double[] extendArray(double[] source, int length, double[] defaults) {
        if (source != null && source.length >= length) {
            return source;
        }
        double[] out = new double[length];
        double fallback = 0.0;
        if (defaults != null && defaults.length > 0) {
            fallback = defaults[defaults.length - 1];
        }
        for (int i = 0; i < length; i++) {
            out[i] = defaults != null && i < defaults.length ? defaults[i] : fallback;
        }
        if (source != null) {
            System.arraycopy(source, 0, out, 0, Math.min(source.length, length));
        }
        return out;
    }

    private void syncLegacyWeightsFromByLevel(double[] byLevel) {
        if (byLevel == null || byLevel.length < 4) {
            return;
        }
        int sets = byLevel.length / 4;
        if (sets <= 0) {
            return;
        }
        this.weights0to1 = sliceWeights(byLevel, 0, sets);
        this.weights1to2 = sliceWeights(byLevel, 1, sets);
        this.weights2to3 = sliceWeights(byLevel, 2, sets);
    }

    private static double[] sliceWeights(double[] byLevel, int level, int sets) {
        int safeLevel = Math.max(0, Math.min(level, sets - 1));
        int base = safeLevel * 4;
        double[] out = new double[4];
        System.arraycopy(byLevel, base, out, 0, 4);
        return out;
    }

    private static String[] extendStringArray(String[] source, int length, String[] defaults) {
        if (source != null && source.length >= length) {
            return source;
        }
        String[] out = new String[length];
        String fallback = "";
        if (defaults != null && defaults.length > 0) {
            fallback = defaults[defaults.length - 1];
        }
        for (int i = 0; i < length; i++) {
            out[i] = defaults != null && i < defaults.length ? defaults[i] : fallback;
        }
        if (source != null) {
            System.arraycopy(source, 0, out, 0, Math.min(source.length, length));
        }
        return out;
    }

    private double[] extractWeightsByLevel(int level) {
        if (weightsByLevel == null || weightsByLevel.length < 4) return null;
        int sets = weightsByLevel.length / 4;
        if (sets <= 0) return null;
        int safeLevel = Math.min(level, sets - 1);
        int base = safeLevel * 4;
        double[] out = new double[4];
        System.arraycopy(weightsByLevel, base, out, 0, 4);
        return out;
    }

    private static List<MaterialTier> parseMaterialTiers(String[] entries, int maxLevel) {
        if (entries == null || entries.length == 0) {
            return new ArrayList<>();
        }
        List<MaterialTier> tiers = new ArrayList<>();
        for (String raw : entries) {
            if (raw == null) continue;
            String entry = raw.trim();
            if (entry.isEmpty()) continue;
            int eq = entry.indexOf('=');
            if (eq <= 0 || eq >= entry.length() - 1) continue;
            String rangePart = entry.substring(0, eq).trim();
            String itemPart = entry.substring(eq + 1).trim();
            if (itemPart.isEmpty()) continue;

            int min = 0;
            int max = maxLevel;
            if (rangePart.endsWith("+")) {
                String minText = rangePart.substring(0, rangePart.length() - 1).trim();
                min = parseInt(minText, 0);
                max = maxLevel;
            } else if (rangePart.contains("-")) {
                String[] pieces = rangePart.split("-", 2);
                min = parseInt(pieces[0].trim(), 0);
                max = parseInt(pieces[1].trim(), maxLevel);
            } else if (!rangePart.isEmpty()) {
                int single = parseInt(rangePart, 0);
                min = single;
                max = single;
            }

            String itemId = itemPart;
            int cost = 1;
            int colon = itemPart.lastIndexOf(':');
            if (colon > 0 && colon < itemPart.length() - 1) {
                itemId = itemPart.substring(0, colon).trim();
                cost = Math.max(1, parseInt(itemPart.substring(colon + 1).trim(), 1));
            }
            if (itemId.isEmpty()) continue;
            if (max < min) {
                int swap = min;
                min = max;
                max = swap;
            }
            tiers.add(new MaterialTier(min, max, itemId, cost));
        }
        return tiers;
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double[] buildDefaultDamageMultipliers(int maxLevel) {
        int safeMax = Math.max(1, maxLevel);
        double[] out = new double[safeMax + 1];
        for (int i = 0; i < out.length; i++) {
            out[i] = lerp(1.0, 2.0, i / (double) safeMax);
        }
        return out;
    }

    private static double[] buildDefaultDefenseMultipliers(int maxLevel) {
        int safeMax = Math.max(1, maxLevel);
        double[] out = new double[safeMax + 1];
        for (int i = 0; i < out.length; i++) {
            out[i] = lerp(1.0, 2.0, i / (double) safeMax);
        }
        return out;
    }

    private static double[] buildDefaultBreakChances(int maxLevel, double start, double mid1, double mid2, double end) {
        int transitions = Math.max(1, maxLevel);
        double[] out = new double[transitions];
        int lastIndex = transitions - 1;
        int tier1End = Math.max(0, (int) Math.floor(lastIndex * 0.33));
        int tier2End = Math.max(tier1End, (int) Math.floor(lastIndex * 0.66));
        for (int i = 0; i < transitions; i++) {
            if (i <= tier1End) {
                out[i] = lerp(start, mid1, tier1End == 0 ? 1.0 : (i / (double) tier1End));
            } else if (i <= tier2End) {
                int span = Math.max(1, tier2End - tier1End);
                out[i] = lerp(mid1, mid2, (i - tier1End) / (double) span);
            } else {
                int span = Math.max(1, lastIndex - tier2End);
                out[i] = lerp(mid2, end, (i - tier2End) / (double) span);
            }
        }
        return out;
    }

    private static String[] buildDefaultRefinementLevelLabels(int maxLevel, boolean usePrefix) {
        return buildDefaultRefinementLevelLabels(maxLevel, usePrefix, false);
    }

    private static String[] buildDefaultRefinementLevelLabels(int maxLevel, boolean usePrefix, boolean isArmor) {
        int safeMax = Math.max(1, maxLevel);
        String[] out = new String[safeMax + 1];
        out[0] = "";
        for (int level = 1; level <= safeMax; level++) {
            if (usePrefix) {
                String name = defaultPrefixLabel(level, isArmor);
                out[level] = name + " ";
            } else {
                String grade = resolveDefaultGradeLetter(level, safeMax);
                if (grade != null) {
                    out[level] = " " + grade;
                } else {
                    out[level] = DEFAULT_REFINEMENT_PREFIX + level + DEFAULT_REFINEMENT_SUFFIX;
                }
            }
        }
        return out;
    }

    private static String defaultPrefixLabel(int level, boolean isArmor) {
        if (level <= 0) {
            return "";
        }
        String[] labels = isArmor ? DEFAULT_ARMOR_PREFIX_LABELS : DEFAULT_WEAPON_PREFIX_LABELS;
        if (level < labels.length) {
            String name = labels[level];
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        return "Refined " + level;
    }

    private static String resolveDefaultGradeLetter(int level, int maxLevel) {
        if (level <= 0 || maxLevel < 10) {
            return null;
        }
        int start = Math.max(1, maxLevel - (DEFAULT_REFINEMENT_GRADE_LETTERS.length - 1));
        int offset = level - start;
        if (offset >= 0 && offset < DEFAULT_REFINEMENT_GRADE_LETTERS.length) {
            return DEFAULT_REFINEMENT_GRADE_LETTERS[offset];
        }
        return null;
    }

    private static double[] buildDefaultWeightsByLevel(int maxLevel) {
        int levels = Math.max(1, maxLevel);
        double[] out = new double[levels * 4];
        int lastLevel = Math.max(0, levels - 1);
        for (int level = 0; level < levels; level++) {
            double degrade;
            double same;
            double upgrade;
            double jackpot;
            if (level == 0) {
                degrade = DEFAULT_WEIGHTS_0_TO_1[0];
                same = DEFAULT_WEIGHTS_0_TO_1[1];
                upgrade = DEFAULT_WEIGHTS_0_TO_1[2];
                jackpot = DEFAULT_WEIGHTS_0_TO_1[3];
            } else if (level == 1) {
                degrade = DEFAULT_WEIGHTS_1_TO_2[0];
                same = DEFAULT_WEIGHTS_1_TO_2[1];
                upgrade = DEFAULT_WEIGHTS_1_TO_2[2];
                jackpot = DEFAULT_WEIGHTS_1_TO_2[3];
            } else if (level == 2) {
                degrade = DEFAULT_WEIGHTS_2_TO_3[0];
                same = DEFAULT_WEIGHTS_2_TO_3[1];
                upgrade = DEFAULT_WEIGHTS_2_TO_3[2];
                jackpot = DEFAULT_WEIGHTS_2_TO_3[3];
            } else {
                double t = lastLevel == 2 ? 1.0 : (level - 2) / (double) (lastLevel - 2);
                double targetDegrade = 0.70;
                double targetSame = 0.25;
                double targetUpgrade = 0.045;
                double targetJackpot = 0.005;
                degrade = lerp(DEFAULT_WEIGHTS_2_TO_3[0], targetDegrade, t);
                same = lerp(DEFAULT_WEIGHTS_2_TO_3[1], targetSame, t);
                upgrade = lerp(DEFAULT_WEIGHTS_2_TO_3[2], targetUpgrade, t);
                jackpot = lerp(DEFAULT_WEIGHTS_2_TO_3[3], targetJackpot, t);
            }
            double sum = degrade + same + upgrade + jackpot;
            if (sum <= 0) {
                degrade = 0.60;
                same = 0.30;
                upgrade = 0.095;
                jackpot = 0.005;
                sum = degrade + same + upgrade + jackpot;
            }
            // Normalize to keep totals at 1.0, keeping same as the adjuster.
            double diff = 1.0 - sum;
            same += diff;
            int base = level * 4;
            out[base] = Math.max(0.0, degrade);
            out[base + 1] = Math.max(0.0, same);
            out[base + 2] = Math.max(0.0, upgrade);
            out[base + 3] = Math.max(0.0, jackpot);
            if (level >= lastLevel) {
                enforceNoJackpot(out, base);
            }
        }
        return out;
    }

    private double[] buildTieredWeightsByLevel() {
        int levels = Math.max(1, getMaxLevel());
        List<MaterialTier> tiers = getMaterialTiers();
        if (tiers.isEmpty()) {
            return buildDefaultWeightsByLevel(levels);
        }
        tiers = new ArrayList<>(tiers);
        tiers.sort(java.util.Comparator.comparingInt(t -> t.minLevel));
        int tierCount = tiers.size();
        double[] out = new double[levels * 4];
        for (int level = 0; level < levels; level++) {
            int tierIndex = tierCount - 1;
            MaterialTier tier = tiers.get(tierIndex);
            for (int i = 0; i < tierCount; i++) {
                MaterialTier current = tiers.get(i);
                if (level >= current.minLevel && level <= current.maxLevel) {
                    tierIndex = i;
                    tier = current;
                    break;
                }
            }
            double tierSpan = Math.max(1, tier.maxLevel - tier.minLevel);
            double tierT = Math.max(0.0, Math.min(1.0, (level - tier.minLevel) / tierSpan));
            double difficulty = (tierIndex + tierT) / Math.max(1.0, tierCount);

            double degrade = lerp(0.05, 0.70, difficulty);
            double same = 0.25;
            double upgrade = lerp(0.65, 0.045, difficulty);
            double jackpot = lerp(0.05, 0.005, difficulty);

            double sum = degrade + same + upgrade + jackpot;
            if (sum <= 0) {
                degrade = 0.60;
                same = 0.30;
                upgrade = 0.095;
                jackpot = 0.005;
                sum = degrade + same + upgrade + jackpot;
            }
            same += (1.0 - sum);
            int base = level * 4;
            out[base] = Math.max(0.0, degrade);
            out[base + 1] = Math.max(0.0, same);
            out[base + 2] = Math.max(0.0, upgrade);
            out[base + 3] = Math.max(0.0, jackpot);
            if (level >= levels - 1) {
                enforceNoJackpot(out, base);
            }
        }
        return out;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * Math.max(0.0, Math.min(1.0, t));
    }

    private static void enforceNoJackpot(double[] weights, int base) {
        if (weights == null || base < 0 || base + 3 >= weights.length) return;
        double degrade = Math.max(0.0, weights[base]);
        double same = Math.max(0.0, weights[base + 1]);
        double upgrade = Math.max(0.0, weights[base + 2]);
        double sum = degrade + same + upgrade;
        if (sum <= 0.0) {
            degrade = 0.0;
            same = 1.0;
            upgrade = 0.0;
        } else {
            double scale = 1.0 / sum;
            degrade *= scale;
            same *= scale;
            upgrade *= scale;
        }
        weights[base] = degrade;
        weights[base + 1] = same;
        weights[base + 2] = upgrade;
        weights[base + 3] = 0.0;
    }

    private static String[] buildDefaultMaterialTierEntries(int maxLevel) {
        int safeMax = Math.max(1, maxLevel);
        if (safeMax <= 2) {
            return new String[] {"0-" + safeMax + "=Refinement_Glob:3"};
        }
        int tier1Max = Math.max(1, (int) Math.floor(safeMax / 3.0));
        int tier2Max = Math.max(tier1Max + 1, (int) Math.floor((2.0 * safeMax) / 3.0));
        if (tier2Max >= safeMax) {
            tier2Max = safeMax - 1;
        }
        if (tier2Max <= tier1Max) {
            return new String[] {
                    "0-" + tier1Max + "=Refinement_Glob:3",
                    (tier1Max + 1) + "-" + safeMax + "=Refinement_Glob_Plus:3"
            };
        }
        return new String[] {
                "0-" + tier1Max + "=Refinement_Glob:3",
                (tier1Max + 1) + "-" + tier2Max + "=Refinement_Glob_Plus:3",
                (tier2Max + 1) + "-" + safeMax + "=Resonant_Glob:3"
        };
    }

    public static final class MaterialTier {
        public final int minLevel;
        public final int maxLevel;
        public final String itemId;
        public final int cost;

        public MaterialTier(int minLevel, int maxLevel, String itemId, int cost) {
            this.minLevel = Math.max(0, minLevel);
            this.maxLevel = Math.max(this.minLevel, maxLevel);
            this.itemId = itemId;
            this.cost = Math.max(1, cost);
        }

        public boolean matches(int level) {
            return level >= minLevel && level <= maxLevel;
        }
    }
}
