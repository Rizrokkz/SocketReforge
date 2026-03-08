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
        // FIRE - T1: +1% DMG or +1 Flat, T3: +3% or +3, T5: +5% or +5
        registerWithGreater("Essence_Fire", Essence.Type.FIRE, List.of(
            new EssenceEffect(EssenceEffect.StatType.DAMAGE, EssenceEffect.EffectType.PERCENTAGE, 1.0),
            new EssenceEffect(EssenceEffect.StatType.DAMAGE, EssenceEffect.EffectType.FLAT, 1.0),
            new EssenceEffect(EssenceEffect.StatType.FIRE_DEFENSE, EssenceEffect.EffectType.PERCENTAGE, 1.0)
        ));

        // ICE - T1: +1% Slow, +1 Cold DMG, T3: +3% Slow, +3 Cold DMG, T5: +5% Slow, +5 Cold DMG
        registerWithGreater("Essence_Ice", Essence.Type.ICE, List.of(
            new EssenceEffect(EssenceEffect.StatType.MOVEMENT_SPEED, EssenceEffect.EffectType.PERCENTAGE, -1.0),
            new EssenceEffect(EssenceEffect.StatType.DAMAGE, EssenceEffect.EffectType.FLAT, 1.0)
        ));

        // LIGHTNING - T1: +1% ATK Spd, +1% Crit, T3: +3% ATK Spd, +3% Crit, T5: +5% ATK Spd, +5% Crit
        registerWithGreater("Essence_Lightning", Essence.Type.LIGHTNING, List.of(
            new EssenceEffect(EssenceEffect.StatType.ATTACK_SPEED, EssenceEffect.EffectType.PERCENTAGE, 1.0),
            new EssenceEffect(EssenceEffect.StatType.CRIT_CHANCE, EssenceEffect.EffectType.PERCENTAGE, 1.0),
            new EssenceEffect(EssenceEffect.StatType.EVASION, EssenceEffect.EffectType.PERCENTAGE, 1.0)
        ));

        // LIFE - Weapon lifesteal and Armor health scaling by tier bands.
        // We'll handle weapon vs armor in the effect application
        registerWithGreater("Essence_Life", Essence.Type.LIFE, List.of(
            new EssenceEffect(EssenceEffect.StatType.LIFE_STEAL, EssenceEffect.EffectType.PERCENTAGE, 1.0),
            new EssenceEffect(EssenceEffect.StatType.HEALTH, EssenceEffect.EffectType.FLAT, 1.0)
        ));

        // VOID (Weapon): Crit Damage scales by tier in +5% steps
        // T1:+5%, T2:+10%, T3:+15%, T4:+20%, T5:+25%
        registerWithGreater("Essence_Void", Essence.Type.VOID, List.of(
            new EssenceEffect(EssenceEffect.StatType.CRIT_DAMAGE, EssenceEffect.EffectType.PERCENTAGE, 1.0),
            new EssenceEffect(EssenceEffect.StatType.DEFENSE, EssenceEffect.EffectType.PERCENTAGE, 1.0)
        ));

        // WATER - T1: +1% Evasion (armor only), T3: +3% Evasion, T5: +5% Evasion
        registerWithGreater("Essence_Water", Essence.Type.WATER, List.of(
            new EssenceEffect(EssenceEffect.StatType.REGENERATION, EssenceEffect.EffectType.FLAT, 1.0)
        ));
    }

    private void registerWithGreater(String baseId, Essence.Type type, List<EssenceEffect> effects) {
        register(new Essence(baseId, Essence.Tier.T1, type, effects));
        register(new Essence(baseId + "_Concentrated", Essence.Tier.T1, type, effects));
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
        int safeTier = SocketEffectMath.clampTier(tier);
        if (essenceType == null) {
            return new double[] {0.0, 0.0};
        }

        if (isWeapon) {
            return switch (essenceType) {
                case ICE -> new double[] {0.0, SocketEffectMath.weaponIceDamageFlat(safeTier)};
                case LIGHTNING -> new double[] {
                        SocketEffectMath.weaponLightningAttackSpeedPercent(safeTier),
                        SocketEffectMath.weaponLightningCritChancePercent(safeTier)
                };
                case LIFE -> new double[] {SocketEffectMath.weaponLifeStealPercent(safeTier), 0.0};
                case VOID -> new double[] {SocketEffectMath.weaponVoidCritDamagePercent(safeTier), 0.0};
                case FIRE, WATER -> new double[] {(double) safeTier, (double) safeTier};
            };
        }

        return switch (essenceType) {
            case FIRE -> new double[] {SocketEffectMath.armorFireDefensePercent(safeTier), 0.0};
            case ICE -> new double[] {SocketEffectMath.armorIceSlowPercent(safeTier), 0.0};
            case LIGHTNING -> new double[] {SocketEffectMath.armorLightningEvasionPercent(safeTier), 0.0};
            case LIFE -> new double[] {0.0, SocketEffectMath.armorLifeHealthFlat(safeTier)};
            case VOID -> new double[] {SocketEffectMath.armorVoidDefensePercent(safeTier), 0.0};
            case WATER -> new double[] {0.0, SocketEffectMath.armorWaterRegenFlat(safeTier)};
        };
    }
}
