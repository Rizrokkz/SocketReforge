package irai.mod.reforge.Config;

import static com.hypixel.hytale.codec.Codec.DOUBLE_ARRAY;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Configuration for refinement rates.
 * Allows server operators to override damage/defense multipliers, break chances, and reforge weights
 * for both weapons and armor.
 */
@SuppressWarnings("removal")
public class RefinementConfig {

    public static final BuilderCodec<RefinementConfig> CODEC = BuilderCodec.<RefinementConfig>builder(RefinementConfig.class, RefinementConfig::new)
            // Damage multipliers per upgrade level (0..3) - weapons
            .append(
                    new KeyedCodec<>("DAMAGE_MULTIPLIERS", DOUBLE_ARRAY),
                    (config, multipliers) -> config.damageMultipliers = multipliers,
                    RefinementConfig::getDamageMultipliers
            ).add()
            // Defense multipliers per upgrade level (0..3) - armor
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
            .build();

    // ── Weapon Configuration ──────────────────────────────────────────────────

    // Damage multipliers per upgrade level (weapons)
    // Level 0 = base (no +), Level 1 = +1, Level 2 = +2, Level 3 = +3
    public double[] damageMultipliers = {1.0, 1.10, 1.15, 1.25};

    // Break chances per upgrade transition for weapons (0->1, 1->2, 2->3)
    public double[] breakChances = {0.01, 0.05, 0.075};

    // ── Armor Configuration ───────────────────────────────────────────────────

    // Defense multipliers per upgrade level (armor)
    // Level 0 = base (no +), Level 1 = +1, Level 2 = +2, Level 3 = +3
    public double[] defenseMultipliers = {1.0, 1.08, 1.12, 1.20};

    // Break chances per upgrade transition for armor (0->1, 1->2, 2->3)
    public double[] armorBreakChances = {0.01, 0.04, 0.065};

    // ── Shared Reforge Weights ────────────────────────────────────────────────

    // Reforge weights for 0->1 transition: [degrade, same, upgrade, jackpot]
    public double[] weights0to1 = {0.00, 0.65, 0.34, 0.01};

    // Reforge weights for 1->2 transition: [degrade, same, upgrade, jackpot]
    public double[] weights1to2 = {0.35, 0.45, 0.19, 0.01};

    // Reforge weights for 2->3 transition: [degrade, same, upgrade, jackpot]
    public double[] weights2to3 = {0.60, 0.30, 0.095, 0.005};

    // ── Getters ───────────────────────────────────────────────────────────────

    public double[] getDamageMultipliers()  { return damageMultipliers; }
    public double[] getDefenseMultipliers() { return defenseMultipliers; }
    public double[] getBreakChances()       { return breakChances; }
    public double[] getArmorBreakChances()  { return armorBreakChances; }
    public double[] getWeights0to1()        { return weights0to1; }
    public double[] getWeights1to2()        { return weights1to2; }
    public double[] getWeights2to3()        { return weights2to3; }

    // ── Helper Methods ────────────────────────────────────────────────────────

    /**
     * Gets the damage multiplier for a given weapon upgrade level.
     * @param level The upgrade level (0-3)
     * @return The damage multiplier, or 1.0 if invalid
     */
    public double getDamageMultiplier(int level) {
        if (level < 0 || level >= damageMultipliers.length) return 1.0;
        return damageMultipliers[level];
    }

    /**
     * Gets the defense multiplier for a given armor upgrade level.
     * @param level The upgrade level (0-3)
     * @return The defense multiplier, or 1.0 if invalid
     */
    public double getDefenseMultiplier(int level) {
        if (level < 0 || level >= defenseMultipliers.length) return 1.0;
        return defenseMultipliers[level];
    }

    /**
     * Gets the break chance for a given weapon current level.
     * @param currentLevel The current upgrade level (0-2)
     * @return The break chance, or 0.0 if invalid
     */
    public double getBreakChance(int currentLevel) {
        if (currentLevel < 0 || currentLevel >= breakChances.length) return 0.0;
        return breakChances[currentLevel];
    }

    /**
     * Gets the break chance for a given armor current level.
     * @param currentLevel The current upgrade level (0-2)
     * @return The armor break chance, or 0.0 if invalid
     */
    public double getArmorBreakChance(int currentLevel) {
        if (currentLevel < 0 || currentLevel >= armorBreakChances.length) return 0.0;
        return armorBreakChances[currentLevel];
    }

    /**
     * Gets the reforge weights for a given current level.
     * @param currentLevel The current upgrade level (0-2)
     * @return The weights array [degrade, same, upgrade, jackpot], or null if invalid
     */
    public double[] getReforgeWeights(int currentLevel) {
        switch (currentLevel) {
            case 0: return weights0to1;
            case 1: return weights1to2;
            case 2: return weights2to3;
            default: return null;
        }
    }
}
