package irai.mod.reforge.Common;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.meta.MetaKey;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import irai.mod.reforge.Entity.Events.EquipmentRefineEST;
import irai.mod.reforge.Interactions.ReforgeEquip;
import irai.mod.reforge.Lore.LoreDamageUtils;
import irai.mod.reforge.Socket.Essence;
import irai.mod.reforge.Socket.ResonanceSystem;
import irai.mod.reforge.Socket.SocketData;
import irai.mod.reforge.Socket.SocketManager;

/**
 * Runtime-only enemy elemental shield pools. Shields absorb elemental payload damage before HP.
 */
@SuppressWarnings("removal")
public final class EnemyElementalShieldUtils {
    private static final boolean DEBUG_SHIELD_LOG = Boolean.parseBoolean(
            System.getProperty("socketreforge.debug.elementShield", "false"));
    private static final double NON_ELEMENTAL_SHIELDED_HP_MULTIPLIER = 0.001d;
    private static final double SHIELD_SUNDER_DAMAGE_MULTIPLIER = 2.0d;
    private static final double SHIELD_SUNDER_HP_PIERCE_RATIO = 0.10d;
    private static final long MAX_LAZY_RECHARGE_STEP_MS = 1000L;
    public static final MetaKey<Double> META_SHIELD_ABSORBED_DAMAGE =
            Damage.META_REGISTRY.registerMetaObject(d -> 0.0d, false,
                    "socketreforge:elemental_shield_absorbed_damage", Codec.DOUBLE);
    public static final MetaKey<Boolean> META_SHIELD_ACTIVE_HIT =
            Damage.META_REGISTRY.registerMetaObject(d -> Boolean.FALSE, false,
                    "socketreforge:elemental_shield_active_hit", Codec.BOOLEAN);
    private static final MetaKey<Double> META_SHIELD_PROCESSED_ELEMENTAL_DAMAGE =
            Damage.META_REGISTRY.registerMetaObject(d -> 0.0d, false,
                    "socketreforge:elemental_shield_processed_damage", Codec.DOUBLE);
    private static final MetaKey<Double> META_SHIELD_HP_REDUCTION_APPLIED =
            Damage.META_REGISTRY.registerMetaObject(d -> 0.0d, false,
                    "socketreforge:elemental_shield_hp_reduction_applied", Codec.DOUBLE);
    private static final MetaKey<Double> META_SHIELD_PROCESSED_HP_DAMAGE =
            Damage.META_REGISTRY.registerMetaObject(d -> 0.0d, false,
                    "socketreforge:elemental_shield_processed_hp_damage", Codec.DOUBLE);
    private static final Map<String, ShieldState> SHIELDS = new ConcurrentHashMap<>();

    private EnemyElementalShieldUtils() {}

    public static double applyToDamage(Store<EntityStore> store, Ref<EntityStore> targetRef, Damage damage) {
        if (store == null || targetRef == null || damage == null || damage.getAmount() <= 0f) {
            return 0.0d;
        }
        NPCEntity npc = NPCEntity.getComponentType() == null
                ? null
                : store.getComponent(targetRef, NPCEntity.getComponentType());
        if (npc == null) {
            return 0.0d;
        }

        ElementalAffinityUtils.ElementShieldProfile profile = ElementalAffinityUtils.elementShieldProfile(store, targetRef);
        double maxShield = profile == null ? 0.0d : profile.amount();
        String key = key(targetRef, npc);
        if (maxShield <= 0.0d) {
            SHIELDS.remove(key);
            return 0.0d;
        }

        double originalDamage = damage.getAmount();
        ShieldState state = stateFor(key, maxShield);
        long now = System.currentTimeMillis();
        rechargeIfEligible(state, profile, now);
        boolean shieldActiveBeforeHit = state.current > 0.0001d;
        if (shieldActiveBeforeHit) {
            damage.putMetaObject(META_SHIELD_ACTIVE_HIT, Boolean.TRUE);
        }
        state.lastDamageAtMs = now;
        double absorbed = 0.0d;

        double totalElementalDamage = totalElementalDamage(damage);
        double processed = safeDouble(damage.getIfPresentMetaObject(META_SHIELD_PROCESSED_ELEMENTAL_DAMAGE));
        double newElementalDamage = Math.max(0.0d, totalElementalDamage - processed);
        boolean hasNewElementalDamage = newElementalDamage > 0.0001d;
        boolean hasShieldSunder = hasShieldSunder(store, damage);
        if (shieldActiveBeforeHit && newElementalDamage > 0.0001d) {
            absorbed = Math.min(state.current, newElementalDamage);
            state.current = Math.max(0.0d, state.current - absorbed);
        }

        double processedHpDamage = safeDouble(damage.getIfPresentMetaObject(META_SHIELD_PROCESSED_HP_DAMAGE));
        double unprocessedHpPathDamage = Math.max(0.0d, originalDamage - processedHpDamage);
        double shieldDrainDamage = hasNewElementalDamage || hasShieldSunder
                ? Math.max(newElementalDamage, unprocessedHpPathDamage)
                : unprocessedHpPathDamage * ElementalAffinityUtils.nonElementalShieldDamageMultiplier();
        if (hasShieldSunder) {
            shieldDrainDamage *= SHIELD_SUNDER_DAMAGE_MULTIPLIER;
        }
        if (shieldActiveBeforeHit && shieldDrainDamage > 0.0001d) {
            double fullHitAbsorb = Math.min(state.current, shieldDrainDamage);
            if (fullHitAbsorb > absorbed) {
                double extraAbsorb = fullHitAbsorb - absorbed;
                absorbed = fullHitAbsorb;
                state.current = Math.max(0.0d, state.current - extraAbsorb);
            }
        }

        double hpDamageFromThisPass = Math.max(0.0d, unprocessedHpPathDamage - absorbed);
        if (shieldActiveBeforeHit) {
            double multiplier = hasNewElementalDamage
                    ? ElementalAffinityUtils.shieldedHpDamageMultiplier()
                    : NON_ELEMENTAL_SHIELDED_HP_MULTIPLIER;
            double shieldedHpDamage = hpDamageFromThisPass * multiplier;
            if (hasShieldSunder) {
                double pierceDamage = unprocessedHpPathDamage * SHIELD_SUNDER_HP_PIERCE_RATIO;
                shieldedHpDamage = Math.max(shieldedHpDamage, pierceDamage);
            }
            hpDamageFromThisPass = shieldedHpDamage;
            damage.putMetaObject(META_SHIELD_HP_REDUCTION_APPLIED, 1.0d);
        }
        double hpDamage = processedHpDamage + hpDamageFromThisPass;
        damage.setAmount((float) hpDamage);
        damage.putMetaObject(META_SHIELD_PROCESSED_HP_DAMAGE, hpDamage);

        if (DEBUG_SHIELD_LOG && shieldActiveBeforeHit) {
            System.out.println("[SocketReforge][ELEMENT_SHIELD] target=" + targetRef
                    + " max=" + maxShield
                    + " remaining=" + state.current
                    + " incoming=" + originalDamage
                    + " elementalNew=" + newElementalDamage
                    + " shieldSunder=" + hasShieldSunder
                    + " absorbed=" + absorbed
                    + " hpOut=" + hpDamage);
        }

        if (newElementalDamage > 0.0001d) {
            damage.putMetaObject(META_SHIELD_PROCESSED_ELEMENTAL_DAMAGE, totalElementalDamage);
        }
        if (absorbed > 0.0001d) {
            damage.putMetaObject(META_SHIELD_ABSORBED_DAMAGE,
                    safeDouble(damage.getIfPresentMetaObject(META_SHIELD_ABSORBED_DAMAGE)) + absorbed);
        }
        updateDepletionState(state, now);

        cleanupIfDead(store, targetRef, key);
        return absorbed;
    }

    public static double currentShield(Store<EntityStore> store, Ref<EntityStore> targetRef) {
        ElementalAffinityUtils.ElementShieldProfile profile = ElementalAffinityUtils.elementShieldProfile(store, targetRef);
        double maxShield = profile == null ? 0.0d : profile.amount();
        if (maxShield <= 0.0d || targetRef == null) {
            return 0.0d;
        }
        NPCEntity npc = NPCEntity.getComponentType() == null
                ? null
                : store.getComponent(targetRef, NPCEntity.getComponentType());
        if (npc == null) {
            return 0.0d;
        }
        ShieldState state = stateFor(key(targetRef, npc), maxShield);
        rechargeIfEligible(state, profile, System.currentTimeMillis());
        return state.current;
    }

    public static double shieldRatio(Store<EntityStore> store, Ref<EntityStore> targetRef) {
        double maxShield = ElementalAffinityUtils.elementShield(store, targetRef);
        if (maxShield <= 0.0d) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, currentShield(store, targetRef) / maxShield));
    }

    private static ShieldState stateFor(String key, double maxShield) {
        return SHIELDS.compute(key, (ignored, existing) -> {
            if (existing == null) {
                return new ShieldState(maxShield, maxShield);
            }
            if (Math.abs(existing.max - maxShield) > 0.0001d) {
                existing.current = Math.min(existing.current, maxShield);
                existing.max = maxShield;
            }
            return existing;
        });
    }

    private static void rechargeIfEligible(ShieldState state, ElementalAffinityUtils.ElementShieldProfile profile, long now) {
        if (state == null || state.max <= 0.0d) {
            return;
        }
        if (state.current > 0.0001d && state.rechargeStartedAtMs <= 0L) {
            return;
        }
        double delaySeconds = profile == null ? ElementalAffinityUtils.shieldRechargeDelaySeconds() : profile.rechargeDelaySeconds();
        double ratePerSecond = profile == null ? ElementalAffinityUtils.shieldRechargeRatePerSecond() : profile.rechargeRatePerSecond();
        double durationSeconds = profile == null ? ElementalAffinityUtils.shieldRechargeDurationSeconds() : profile.rechargeDurationSeconds();
        if (ratePerSecond <= 0.0d || durationSeconds <= 0.0d) {
            return;
        }
        long delayMs = Math.max(0L, Math.round(delaySeconds * 1000.0d));
        long durationMs = Math.max(1L, Math.round(durationSeconds * 1000.0d));
        long idleReadyAt = state.lastDamageAtMs + delayMs;
        if (now < idleReadyAt) {
            return;
        }
        if (state.rechargeStartedAtMs <= 0L || state.rechargeStartedAtMs < idleReadyAt) {
            state.rechargeStartedAtMs = idleReadyAt;
            state.lastRechargeTickAtMs = idleReadyAt;
            state.rechargeElapsedMs = 0L;
        }
        long elapsedStepMs = Math.max(0L, now - state.lastRechargeTickAtMs);
        if (elapsedStepMs <= 0L) {
            return;
        }
        long remainingDurationMs = Math.max(0L, durationMs - state.rechargeElapsedMs);
        long rechargeStepMs = Math.min(Math.min(elapsedStepMs, remainingDurationMs), MAX_LAZY_RECHARGE_STEP_MS);
        state.lastRechargeTickAtMs = now;
        if (rechargeStepMs <= 0L) {
            stopRecharge(state);
            return;
        }
        state.rechargeElapsedMs += rechargeStepMs;
        state.current = Math.min(state.max, state.current + state.max * ratePerSecond * (rechargeStepMs / 1000.0d));
        if (state.current >= state.max - 0.0001d) {
            state.current = state.max;
            state.depletedAtMs = 0L;
            stopRecharge(state);
        } else if (state.rechargeElapsedMs >= durationMs) {
            stopRecharge(state);
        }
    }

    private static void stopRecharge(ShieldState state) {
        state.rechargeStartedAtMs = 0L;
        state.lastRechargeTickAtMs = 0L;
        state.rechargeElapsedMs = 0L;
    }

    private static void updateDepletionState(ShieldState state, long now) {
        if (state == null) {
            return;
        }
        if (state.current <= 0.0001d) {
            state.current = 0.0d;
            if (state.depletedAtMs <= 0L) {
                state.depletedAtMs = now;
            }
            return;
        }
        state.depletedAtMs = 0L;
    }

    private static double totalElementalDamage(Damage damage) {
        Map<Essence.Type, Double> elementDamage = WeaponElementalDamageUtils.decodeElementDamage(
                damage.getIfPresentMetaObject(EquipmentRefineEST.META_WEAPON_ELEMENTAL_DAMAGE));
        double total = 0.0d;
        for (Double value : elementDamage.values()) {
            if (value != null && value > 0.0d) {
                total += value;
            }
        }
        return total;
    }

    private static boolean hasShieldSunder(Store<EntityStore> store, Damage damage) {
        if (store == null || damage == null) {
            return false;
        }
        Damage.Source source = damage.getSource();
        if (!(source instanceof Damage.EntitySource entitySource)) {
            return false;
        }
        Player attacker = store.getComponent(entitySource.getRef(), Player.getComponentType());
        if (attacker == null) {
            return false;
        }
        ItemStack weapon = PlayerInventoryUtils.findFirstInHotbar(attacker, ReforgeEquip::isWeapon);
        if (weapon == null || weapon.isEmpty()) {
            return false;
        }
        try {
            SocketData socketData = SocketManager.getSocketData(weapon);
            if (socketData != null) {
                ResonanceSystem.ResonanceResult result = SocketManager.evaluateAllowedResonance(weapon, socketData);
                if (result != null && result.active() && result.type() == ResonanceSystem.ResonanceType.SHIELD_SUNDER) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // Fall back to persisted metadata below.
        }
        String rawType = SocketManager.getResonanceType(weapon);
        return rawType != null && rawType.equalsIgnoreCase(ResonanceSystem.ResonanceType.SHIELD_SUNDER.name());
    }

    private static void cleanupIfDead(Store<EntityStore> store, Ref<EntityStore> targetRef, String key) {
        if (LoreDamageUtils.resolveCurrentHealth(store, targetRef) <= 0.0f) {
            SHIELDS.remove(key);
        }
    }

    private static double safeDouble(Double value) {
        return value == null || !Double.isFinite(value) ? 0.0d : value;
    }

    private static String key(Ref<EntityStore> targetRef, NPCEntity npc) {
        String spawnInstant = "";
        try {
            spawnInstant = npc.getSpawnInstant() == null ? "" : npc.getSpawnInstant().toString();
        } catch (Throwable ignored) {
            // Fall back below when spawn identity is unavailable.
        }
        if (!spawnInstant.isBlank()) {
            return System.identityHashCode(targetRef.getStore()) + ":"
                    + targetRef.getIndex() + ":"
                    + safeKey(npc.getRoleName()) + ":"
                    + safeKey(npc.getNPCTypeId()) + ":"
                    + spawnInstant;
        }
        return System.identityHashCode(targetRef.getStore()) + ":"
                + targetRef.getIndex() + ":"
                + safeKey(npc.getRoleName()) + ":"
                + safeKey(npc.getNPCTypeId());
    }

    private static String safeKey(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static final class ShieldState {
        private double max;
        private double current;
        private long lastDamageAtMs;
        private long depletedAtMs;
        private long rechargeStartedAtMs;
        private long lastRechargeTickAtMs;
        private long rechargeElapsedMs;

        private ShieldState(double max, double current) {
            this.max = max;
            this.current = current;
            this.lastDamageAtMs = System.currentTimeMillis();
        }
    }
}
