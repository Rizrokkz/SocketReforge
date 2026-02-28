package irai.mod.reforge.Socket;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        // FIRE - T1 effects: +2% DMG or +3 Flat, T3: +6% or +8, T5: +12% or +15
        register(new Essence("Essence_Fire", Essence.Tier.T1, Essence.Type.FIRE, List.of(
            new EssenceEffect(EssenceEffect.StatType.DAMAGE, EssenceEffect.EffectType.PERCENTAGE, 2.0),
            new EssenceEffect(EssenceEffect.StatType.DAMAGE, EssenceEffect.EffectType.FLAT, 3.0)
        )));

        // ICE - T1: +2% Slow, +2 Cold DMG, T3: +5% Slow, +6 Cold DMG, T5: +5% Freeze, +12 Cold DMG
        register(new Essence("Essence_Ice", Essence.Tier.T1, Essence.Type.ICE, List.of(
            new EssenceEffect(EssenceEffect.StatType.MOVEMENT_SPEED, EssenceEffect.EffectType.PERCENTAGE, -2.0),
            new EssenceEffect(EssenceEffect.StatType.DAMAGE, EssenceEffect.EffectType.FLAT, 2.0)
        )));

        // LIGHTNING - T1: +3% ATK Spd, +2% Crit, T3: +7% ATK Spd, +4% Crit, T5: +15% ATK Spd, +8% Crit
        register(new Essence("Essence_Lightning", Essence.Tier.T1, Essence.Type.LIGHTNING, List.of(
            new EssenceEffect(EssenceEffect.StatType.ATTACK_SPEED, EssenceEffect.EffectType.PERCENTAGE, 3.0),
            new EssenceEffect(EssenceEffect.StatType.CRIT_CHANCE, EssenceEffect.EffectType.PERCENTAGE, 2.0)
        )));

        // LIFE - T1: Weapon +2% Lifesteal OR Armor +10 HP, T3: +5% or +25 HP, T5: +10% or +50 HP
        // We'll handle weapon vs armor in the effect application
        register(new Essence("Essence_Life", Essence.Tier.T1, Essence.Type.LIFE, List.of(
            new EssenceEffect(EssenceEffect.StatType.LIFE_STEAL, EssenceEffect.EffectType.PERCENTAGE, 2.0),
            new EssenceEffect(EssenceEffect.StatType.HEALTH, EssenceEffect.EffectType.FLAT, 10.0)
        )));

        // VOID - T1: +5% Crit DMG, T3: +12% Crit DMG, T5: +25% Crit DMG
        register(new Essence("Essence_Void", Essence.Tier.T1, Essence.Type.VOID, List.of(
            new EssenceEffect(EssenceEffect.StatType.CRIT_DAMAGE, EssenceEffect.EffectType.PERCENTAGE, 5.0)
        )));

        // WATER - T1: +2% Evasion (armor only), T3: +5% Evasion, T5: +10% Evasion
        register(new Essence("Essence_Water", Essence.Tier.T1, Essence.Type.WATER, List.of(
            new EssenceEffect(EssenceEffect.StatType.EVASION, EssenceEffect.EffectType.PERCENTAGE, 2.0)
        )));
    }

    /**
     * Gets the tier effect values for an essence type at a given tier.
     * Tier is calculated based on consecutive count of same-type essences.
     * 
     * @param essenceType The essence type (FIRE, ICE, etc.)
     * @param tier The tier (1-5)
     * @param isWeapon True if the item is a weapon, false if armor
     * @return The effect values [percentBonus, flatBonus]
     */
    public static double[] getTierEffect(Essence.Type essenceType, int tier, boolean isWeapon) {
        switch (essenceType) {
            case FIRE:
                if (tier >= 5) return new double[]{12.0, 15.0};
                if (tier >= 3) return new double[]{6.0, 8.0};
                return new double[]{2.0, 3.0};
                
            case ICE:
                if (tier >= 5) return new double[]{5.0, 12.0}; // Freeze chance + Cold DMG
                if (tier >= 3) return new double[]{5.0, 6.0};
                return new double[]{2.0, 2.0};
                
            case LIGHTNING:
                if (tier >= 5) return new double[]{15.0, 8.0}; // ATK Speed, Crit
                if (tier >= 3) return new double[]{7.0, 4.0};
                return new double[]{3.0, 2.0};
                
            case LIFE:
                if (isWeapon) {
                    if (tier >= 5) return new double[]{10.0, 0.0}; // Lifesteal
                    if (tier >= 3) return new double[]{5.0, 0.0};
                    return new double[]{2.0, 0.0};
                } else {
                    if (tier >= 5) return new double[]{0.0, 50.0}; // HP
                    if (tier >= 3) return new double[]{0.0, 25.0};
                    return new double[]{0.0, 10.0};
                }
                
            case VOID:
                if (tier >= 5) return new double[]{25.0, 0.0};
                if (tier >= 3) return new double[]{12.0, 0.0};
                return new double[]{5.0, 0.0};
                
            case WATER:
                // Water only works on armor
                if (tier >= 5) return new double[]{10.0, 0.0};
                if (tier >= 3) return new double[]{5.0, 0.0};
                return new double[]{2.0, 0.0};
                
            default:
                return new double[]{0.0, 0.0};
        }
    }
}
