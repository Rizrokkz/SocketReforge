package irai.mod.reforge.Socket;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Singleton registry of all Essence definitions.
 * Populated on plugin startup.
 * 
 * Note: The tier system works based on CONSECUTIVE essences of the same type.
 * For example: "Life", "Life", "Life", "Fire" = Tier 3 Life, Tier 1 Fire
 * The actual tier effects are calculated at runtime in SocketManager.
 */
public class EssenceRegistry {

    private static final EssenceRegistry INSTANCE = new EssenceRegistry();
    private final Map<String, Essence> registry = new HashMap<>();

    private EssenceRegistry() {
        registerDefaults();
    }

    public static EssenceRegistry get() { return INSTANCE; }

    /** Initialize the registry. Called from ReforgePlugin. */
    public static void initialize() {
        // The singleton is already initialized via static INSTANCE
        // This method exists for explicit initialization from plugin
    }

    // ── Lookup ────────────────────────────────────────────────────────────────

    public Essence getById(String id) { return registry.get(id); }
    public boolean exists(String id)  { return registry.containsKey(id); }

    // ── Registration ──────────────────────────────────────────────────────────

    public void register(Essence essence) {
        registry.put(essence.getId(), essence);
    }

    // ── Default essence table ───────────────────────────────────────────────
    // Tier is determined by consecutive count at runtime
    // These are base type registrations for identification

    private void registerDefaults() {
        // FIRE - T1: +1% DMG or +1 Flat, T3: +3% or +3, T5: +5% or +5
        register(new Essence("Essence_Fire", Essence.Tier.T1, Essence.Type.FIRE, List.of(
            new EssenceEffect(EssenceEffect.StatType.DAMAGE, EssenceEffect.EffectType.PERCENTAGE, 1.0),
            new EssenceEffect(EssenceEffect.StatType.DAMAGE, EssenceEffect.EffectType.FLAT, 1.0),
            new EssenceEffect(EssenceEffect.StatType.FIRE_DEFENSE, EssenceEffect.EffectType.PERCENTAGE, 1.0)
        )));

        // ICE - T1: +1% Slow, +1 Cold DMG, T3: +3% Slow, +3 Cold DMG, T5: +5% Slow, +5 Cold DMG
        register(new Essence("Essence_Ice", Essence.Tier.T1, Essence.Type.ICE, List.of(
            new EssenceEffect(EssenceEffect.StatType.MOVEMENT_SPEED, EssenceEffect.EffectType.PERCENTAGE, -1.0),
            new EssenceEffect(EssenceEffect.StatType.DAMAGE, EssenceEffect.EffectType.FLAT, 1.0)
        )));

        // LIGHTNING - T1: +1% ATK Spd, +1% Crit, T3: +3% ATK Spd, +3% Crit, T5: +5% ATK Spd, +5% Crit
        register(new Essence("Essence_Lightning", Essence.Tier.T1, Essence.Type.LIGHTNING, List.of(
            new EssenceEffect(EssenceEffect.StatType.ATTACK_SPEED, EssenceEffect.EffectType.PERCENTAGE, 1.0),
            new EssenceEffect(EssenceEffect.StatType.CRIT_CHANCE, EssenceEffect.EffectType.PERCENTAGE, 1.0),
            new EssenceEffect(EssenceEffect.StatType.EVASION, EssenceEffect.EffectType.PERCENTAGE, 1.0)
        )));

        // LIFE - T1: Weapon +1% Lifesteal OR Armor +1 HP, T3: +3% or +3 HP, T5: +5% or +5 HP
        // We'll handle weapon vs armor in the effect application
        register(new Essence("Essence_Life", Essence.Tier.T1, Essence.Type.LIFE, List.of(
            new EssenceEffect(EssenceEffect.StatType.LIFE_STEAL, EssenceEffect.EffectType.PERCENTAGE, 1.0),
            new EssenceEffect(EssenceEffect.StatType.HEALTH, EssenceEffect.EffectType.FLAT, 1.0)
        )));

        // VOID (Weapon): Crit Damage scales by tier in +5% steps
        // T1:+5%, T2:+10%, T3:+15%, T4:+20%, T5:+25%
        register(new Essence("Essence_Void", Essence.Tier.T1, Essence.Type.VOID, List.of(
            new EssenceEffect(EssenceEffect.StatType.CRIT_DAMAGE, EssenceEffect.EffectType.PERCENTAGE, 1.0),
            new EssenceEffect(EssenceEffect.StatType.DEFENSE, EssenceEffect.EffectType.PERCENTAGE, 1.0)
        )));

        // WATER - T1: +1% Evasion (armor only), T3: +3% Evasion, T5: +5% Evasion
        register(new Essence("Essence_Water", Essence.Tier.T1, Essence.Type.WATER, List.of(
            new EssenceEffect(EssenceEffect.StatType.REGENERATION, EssenceEffect.EffectType.FLAT, 1.0)
        )));
    }

    private static final Random RNG = new Random();

    /**
     * Gets the tier effect values for an essence type at a given tier.
     * Tier is calculated based on consecutive count of same-type essences.
     * 
     * @param essenceType The essence type (FIRE, ICE, etc.)
     * @param tier The tier (1-5)
     * @param isWeapon True if the item is a weapon, false if armor
     * @return The effect values [percentBonus, flatBonus] with randomized increments
     */
    public static double[] getTierEffect(Essence.Type essenceType, int tier, boolean isWeapon) {
        // Randomize: Choose between Flat or Percentage damage type
        boolean useFlat = RNG.nextBoolean();

        // Percentage increment: Fixed +2% per tier (1% -> 3% -> 5%)
        double percentTier = 1 + (tier - 1) * 2.0;
        double percentFixed = percentTier;

        // Helper to calculate base + random increments
        java.util.function.DoubleBinaryOperator calcP = (base, inc) -> base + (tier - 1) * inc;
        java.util.function.DoubleBinaryOperator calcF = (base, inc) -> base + (tier - 1) * 1.0; // Flat increments are 1.0 per tier (fixed)

        // Only apply stats appropriate for the item type (weapon vs armor)
        if (isWeapon) {
            // Weapon Sockets: Damage, Crit, Lifesteal, Attack Speed, etc.
            switch (essenceType) {
                case FIRE:
                    // Fire: Randomly choose between DAMAGE % or DAMAGE flat
                    if (tier >= 5) return useFlat 
                        ? new double[]{ 0.0, calcF.applyAsDouble(5.0, 1.0) }
                        : new double[]{ calcP.applyAsDouble(5.0, percentFixed), 0.0 };
                    
                case ICE:
                    // Ice: COLD DAMAGE flat (Offensive)
                    if (tier >= 5) return new double[]{ 0.0, calcF.applyAsDouble(5.0, 1.0) };
                    if (tier >= 3) return new double[]{ 0.0, calcF.applyAsDouble(3.0, 1.0) };
                    return new double[]{ 0.0, calcF.applyAsDouble(1.0, 1.0) };
                    
                case LIGHTNING:
                    // Lightning: Randomly choose between ATK_SPEED % or FLAT
                    if (tier >= 5) return useFlat
                        ? new double[]{ 0.0, calcF.applyAsDouble(5.0, 1.0) }
                        : new double[]{ calcP.applyAsDouble(5.0, percentFixed), 0.0 };
                    if (tier >= 3) return useFlat
                        ? new double[]{ 0.0, calcF.applyAsDouble(3.0, 1.0) }
                        : new double[]{ calcP.applyAsDouble(3.0, percentFixed), 0.0 };
                    return useFlat
                        ? new double[]{ 0.0, calcF.applyAsDouble(1.0, 1.0) }
                        : new double[]{ calcP.applyAsDouble(1.0, percentFixed), 0.0 };
                    
                case LIFE:
                    // Life: Lifesteal % (Offensive)
                    if (tier >= 5) return new double[]{ percentFixed + 4.0, 0.0 };
                    if (tier >= 3) return new double[]{ percentFixed + 2.0, 0.0 };
                    return new double[]{ percentFixed, 0.0 };
                    
                case VOID:
                    // Void: Crit Damage % (Offensive)
                    return new double[]{ Math.min(25.0, Math.max(1, tier) * 5.0), 0.0 };
                    
                case WATER:
                    // Water: DAMAGE % or FLAT (Offensive)
                    if (tier >= 5) return useFlat
                        ? new double[]{ 0.0, calcF.applyAsDouble(5.0, 1.0) }
                        : new double[]{ percentFixed + 4.0, 0.0 };
                    if (tier >= 3) return useFlat
                        ? new double[]{ 0.0, calcF.applyAsDouble(3.0, 1.0) }
                        : new double[]{ percentFixed + 2.0, 0.0 };
                    return useFlat
                        ? new double[]{ 0.0, calcF.applyAsDouble(1.0, 1.0) }
                        : new double[]{ percentFixed, 0.0 };
                    
                default:
                    return new double[]{0.0, 0.0};
            }
        } else {
            // Armor Sockets: Health, Defense, Evasion, Slow (debuff)
            switch (essenceType) {
                case FIRE:
                    // Fire: FIRE DAMAGE REDUCTION % (Defensive)
                    if (tier >= 5) return new double[]{ percentFixed + 4.0, 0.0 };
                    if (tier >= 3) return new double[]{ percentFixed + 2.0, 0.0 };
                    return new double[]{ percentFixed, 0.0 };
                    
                case ICE:
                    // Ice: SLOW % (Debuff to enemies) - treat as defensive utility
                    if (tier >= 5) return new double[]{ calcP.applyAsDouble(5.0, percentFixed), 0.0 };
                    if (tier >= 3) return new double[]{ calcP.applyAsDouble(3.0, percentFixed), 0.0 };
                    return new double[]{ calcP.applyAsDouble(1.0, percentFixed), 0.0 };
                    
                case LIGHTNING:
                    // Lightning: EVASION % (Defensive)
                    if (tier >= 5) return new double[]{ percentFixed + 4.0, 0.0 };
                    if (tier >= 3) return new double[]{ percentFixed + 2.0, 0.0 };
                    return new double[]{ percentFixed, 0.0 };
                    
                case LIFE:
                    // Life: Health flat (Defensive)
                    if (tier >= 5) return new double[]{ 0.0, calcF.applyAsDouble(5.0, 1.0) };
                    if (tier >= 3) return new double[]{ 0.0, calcF.applyAsDouble(3.0, 1.0) };
                    return new double[]{ 0.0, calcF.applyAsDouble(1.0, 1.0) };
                    
                case VOID:
                    // Void: DEFENSE % (Defensive)
                    if (tier >= 5) return new double[]{ percentFixed + 4.0, 0.0 };
                    if (tier >= 3) return new double[]{ percentFixed + 2.0, 0.0 };
                    return new double[]{ percentFixed, 0.0 };
                    
                case WATER:
                    // Water: REGENERATION (Flat HP) for Armor
                    if (tier >= 5) return new double[]{ 0.0, calcF.applyAsDouble(5.0, 1.0) };
                    if (tier >= 3) return new double[]{ 0.0, calcF.applyAsDouble(3.0, 1.0) };
                    return new double[]{ 0.0, calcF.applyAsDouble(1.0, 1.0) };
                    
                default:
                    return new double[]{0.0, 0.0};
            }
        }
    }
}
