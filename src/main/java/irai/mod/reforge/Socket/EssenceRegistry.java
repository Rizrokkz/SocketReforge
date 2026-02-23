package irai.mod.reforge.Socket;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Singleton registry of all Essence definitions.
 * Populated on plugin startup.
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

    // ── Default essence table (matches architecture plan) ─────────────────────

    private void registerDefaults() {
        // FIRE
        registerFire(1, 2, 3);
        registerFire(2, 4, 5);
        registerFire(3, 6, 8);
        registerFire(4, 9, 11);
        registerFire(5, 12, 15);

        // ICE
        registerIce(1, 2, 2);
        registerIce(2, 3, 4);
        registerIce(3, 5, 6);
        registerIce(4, 7, 9);
        registerIce(5, 10, 12);

        // LIGHTNING
        registerLightning(1, 3, 2);
        registerLightning(2, 5, 3);
        registerLightning(3, 7, 4);
        registerLightning(4, 11, 6);
        registerLightning(5, 15, 8);

        // LIFE
        registerLife(1, 2, 10);
        registerLife(2, 3, 17);
        registerLife(3, 5, 25);
        registerLife(4, 7, 37);
        registerLife(5, 10, 50);

        // SHADOW
        registerShadow(1, 5, 2);
        registerShadow(2, 8, 3);
        registerShadow(3, 12, 5);
        registerShadow(4, 18, 7);
        registerShadow(5, 25, 10);
    }

    private void registerFire(int tier, double dmgPct, double flatDmg) {
        String id = "Essence_Fire_" + tier;
        register(new Essence(id, Essence.Tier.values()[tier - 1], Essence.Type.FIRE, List.of(
            new EssenceEffect(EssenceEffect.StatType.DAMAGE,  EssenceEffect.EffectType.PERCENTAGE, dmgPct),
            new EssenceEffect(EssenceEffect.StatType.DAMAGE,  EssenceEffect.EffectType.FLAT,       flatDmg)
        )));
    }

    private void registerIce(int tier, double slowPct, double coldDmg) {
        String id = "Essence_Ice_" + tier;
        register(new Essence(id, Essence.Tier.values()[tier - 1], Essence.Type.ICE, List.of(
            new EssenceEffect(EssenceEffect.StatType.MOVEMENT_SPEED, EssenceEffect.EffectType.PERCENTAGE, -slowPct),
            new EssenceEffect(EssenceEffect.StatType.DAMAGE,          EssenceEffect.EffectType.FLAT,       coldDmg)
        )));
    }

    private void registerLightning(int tier, double atkSpeedPct, double critPct) {
        String id = "Essence_Lightning_" + tier;
        register(new Essence(id, Essence.Tier.values()[tier - 1], Essence.Type.LIGHTNING, List.of(
            new EssenceEffect(EssenceEffect.StatType.ATTACK_SPEED, EssenceEffect.EffectType.PERCENTAGE, atkSpeedPct),
            new EssenceEffect(EssenceEffect.StatType.CRIT_CHANCE,  EssenceEffect.EffectType.PERCENTAGE, critPct)
        )));
    }

    private void registerLife(int tier, double lifeStealPct, double flatHp) {
        String id = "Essence_Life_" + tier;
        register(new Essence(id, Essence.Tier.values()[tier - 1], Essence.Type.LIFE, List.of(
            new EssenceEffect(EssenceEffect.StatType.LIFE_STEAL, EssenceEffect.EffectType.PERCENTAGE, lifeStealPct),
            new EssenceEffect(EssenceEffect.StatType.HEALTH,     EssenceEffect.EffectType.FLAT,       flatHp)
        )));
    }

    private void registerShadow(int tier, double critDmgPct, double evasionPct) {
        String id = "Essence_Shadow_" + tier;
        register(new Essence(id, Essence.Tier.values()[tier - 1], Essence.Type.SHADOW, List.of(
            new EssenceEffect(EssenceEffect.StatType.CRIT_DAMAGE, EssenceEffect.EffectType.PERCENTAGE, critDmgPct),
            new EssenceEffect(EssenceEffect.StatType.EVASION,     EssenceEffect.EffectType.PERCENTAGE, evasionPct)
        )));
    }
}