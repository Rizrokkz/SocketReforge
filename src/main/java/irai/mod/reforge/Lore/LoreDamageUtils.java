package irai.mod.reforge.Lore;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.meta.MetaKey;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.entity.entities.Player;

import irai.mod.reforge.Common.EquipmentDamageTooltipMath;
import irai.mod.reforge.Common.PlayerInventoryUtils;
import irai.mod.reforge.Entity.Events.EquipmentRefineEST;
import irai.mod.reforge.Lore.LoreTargetingUtils;
import irai.mod.reforge.Interactions.ReforgeEquip;
import irai.mod.reforge.Socket.SocketData;
import irai.mod.reforge.Socket.SocketManager;
import irai.mod.reforge.Util.DamageNumberFormatter;
import irai.mod.DynamicFloatingDamageFormatter.DamageNumberMeta;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Shared damage calculations and application helpers for lore systems.
 */
public final class LoreDamageUtils {
    private static final String META_PARTS_DAMAGE_MULTIPLIER = "SocketReforge.Parts.DamageMultiplier";
    private static final double LORE_DAMAGE_MIN_WEAPON_PCT = 0.04d;
    public static final MetaKey<Boolean> META_LORE_DAMAGE =
            Damage.META_REGISTRY.registerMetaObject(d -> Boolean.FALSE, false,
                    "socketreforge:lore_damage", Codec.BOOLEAN);
    private static final boolean DEBUG_SIGNATURE_ENERGY = Boolean.parseBoolean(
            System.getProperty("socketreforge.debug.signatureEnergy", "false"));
    private static final boolean DEBUG_SIGNATURE_ENERGY_STACK = Boolean.parseBoolean(
            System.getProperty("socketreforge.debug.signatureEnergyStack", "false"));
    private static final long SIGNATURE_TRACE_COOLDOWN_MS = 500L;
    private static final Map<UUID, Float> SIGNATURE_LAST_VALUE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> SIGNATURE_LAST_LOG_AT = new ConcurrentHashMap<>();

    private LoreDamageUtils() {}

    public static final class DamageBaseResult {
        public final float amount;
        public final boolean skipRefine;

        public DamageBaseResult(float amount, boolean skipRefine) {
            this.amount = amount;
            this.skipRefine = skipRefine;
        }
    }

    public static void applyCombatDamage(Store<EntityStore> store,
                                         Ref<EntityStore> sourceRef,
                                         Ref<EntityStore> targetRef,
                                         float rawDamage,
                                         boolean skipRefine) {
        applyCombatDamage(store, sourceRef, targetRef, rawDamage, skipRefine, true);
    }

    public static void applyLoreDamage(Store<EntityStore> store,
                                       Ref<EntityStore> sourceRef,
                                       Ref<EntityStore> targetRef,
                                       float rawDamage,
                                       boolean skipRefine) {
        applyLoreDamage(store, sourceRef, targetRef, rawDamage, skipRefine, 0);
    }

    public static void applyLoreDamage(Store<EntityStore> store,
                                       Ref<EntityStore> sourceRef,
                                       Ref<EntityStore> targetRef,
                                       float rawDamage,
                                       boolean skipRefine,
                                       int feedTier) {
        applyCombatDamage(store, sourceRef, targetRef, rawDamage, skipRefine, true, true, feedTier, null, false);
    }

    public static void applyLoreDamage(Store<EntityStore> store,
                                       Ref<EntityStore> sourceRef,
                                       Ref<EntityStore> targetRef,
                                       float rawDamage,
                                       boolean skipRefine,
                                       DamageNumberFormatter.DamageKind kind) {
        applyLoreDamage(store, sourceRef, targetRef, rawDamage, skipRefine, kind, false, 0);
    }

    public static void applyLoreDamage(Store<EntityStore> store,
                                       Ref<EntityStore> sourceRef,
                                       Ref<EntityStore> targetRef,
                                       float rawDamage,
                                       boolean skipRefine,
                                       DamageNumberFormatter.DamageKind kind,
                                       int feedTier) {
        applyCombatDamage(store, sourceRef, targetRef, rawDamage, skipRefine, true, true, feedTier, kind, false);
    }

    public static void applyLoreDamage(Store<EntityStore> store,
                                       Ref<EntityStore> sourceRef,
                                       Ref<EntityStore> targetRef,
                                       float rawDamage,
                                       boolean skipRefine,
                                       DamageNumberFormatter.DamageKind kind,
                                       boolean skipCombatText) {
        applyLoreDamage(store, sourceRef, targetRef, rawDamage, skipRefine, kind, skipCombatText, 0);
    }

    public static void applyLoreDamage(Store<EntityStore> store,
                                       Ref<EntityStore> sourceRef,
                                       Ref<EntityStore> targetRef,
                                       float rawDamage,
                                       boolean skipRefine,
                                       DamageNumberFormatter.DamageKind kind,
                                       boolean skipCombatText,
                                       int feedTier) {
        applyCombatDamage(store, sourceRef, targetRef, rawDamage, skipRefine, true, true, feedTier, kind, skipCombatText);
    }

    public static void applyCombatDamage(Store<EntityStore> store,
                                         Ref<EntityStore> sourceRef,
                                         Ref<EntityStore> targetRef,
                                         float rawDamage,
                                         boolean skipRefine,
                                         boolean useEntitySource) {
        applyCombatDamage(store, sourceRef, targetRef, rawDamage, skipRefine, useEntitySource, false, 0, null, false);
    }

    private static void applyCombatDamage(Store<EntityStore> store,
                                          Ref<EntityStore> sourceRef,
                                          Ref<EntityStore> targetRef,
                                          float rawDamage,
                                          boolean skipRefine,
                                          boolean useEntitySource,
                                          boolean isLoreDamage,
                                          DamageNumberFormatter.DamageKind kind) {
        applyCombatDamage(store, sourceRef, targetRef, rawDamage, skipRefine, useEntitySource, isLoreDamage, 0, kind,
                false);
    }

    private static void applyCombatDamage(Store<EntityStore> store,
                                          Ref<EntityStore> sourceRef,
                                          Ref<EntityStore> targetRef,
                                          float rawDamage,
                                          boolean skipRefine,
                                          boolean useEntitySource,
                                          boolean isLoreDamage,
                                          int feedTier,
                                          DamageNumberFormatter.DamageKind kind,
                                          boolean skipCombatText) {
        float adjustedDamage = rawDamage;
        if (isLoreDamage && store != null && sourceRef != null) {
            float weaponBase = resolveWeaponBaseDamage(store, sourceRef);
            if (weaponBase > 0f) {
                float minDamage = (float) (weaponBase * LORE_DAMAGE_MIN_WEAPON_PCT);
                if (minDamage > 0f && feedTier > 0) {
                    minDamage = (float) LoreAbility.scaleEffectValue(minDamage, feedTier);
                }
                if (minDamage > 0f && adjustedDamage < minDamage) {
                    adjustedDamage = minDamage;
                }
            }
        }
        if (store == null || targetRef == null || adjustedDamage <= 0f) {
            LoreDebug.logKv("damage.skip", "reason", "invalid", "rawDamage", rawDamage);
            return;
        }
        if (applyDamageEvent(store, sourceRef, targetRef, adjustedDamage, skipRefine, useEntitySource, isLoreDamage,
                kind, skipCombatText)) {
            return;
        }
        applyDirectHealthLoss(store, targetRef, adjustedDamage);
    }

    public static float resolveDamageBase(boolean selfIsAttacker, Damage damage, float fallback) {
        if (!selfIsAttacker || damage == null) {
            return fallback;
        }
        float amount = damage.getAmount();
        float initial = damage.getInitialAmount();
        float base = Math.max(amount, initial);
        if (base <= 0f) {
            base = amount;
        }
        if (base <= 0f) {
            base = fallback;
        }
        return base;
    }

    public static float resolveLoreDamageBase(Store<EntityStore> store,
                                              Ref<EntityStore> attackerRef,
                                              boolean selfIsAttacker,
                                              Damage damage,
                                              float fallback) {
        float eventBase = resolveDamageBase(selfIsAttacker, damage, fallback);
        if (eventBase > 0f) {
            return eventBase;
        }
        float refined = resolveWeaponRefinedDamage(store, attackerRef, fallback);
        if (refined > 0f) {
            return refined;
        }
        return fallback;
    }

    public static float resolveWeaponRefinedDamage(Store<EntityStore> store,
                                                   Ref<EntityStore> attackerRef,
                                                   float fallback) {
        if (store == null || attackerRef == null) {
            return fallback;
        }
        Player player = store.getComponent(attackerRef, Player.getComponentType());
        if (player == null) {
            return fallback;
        }
        ItemStack stack = PlayerInventoryUtils.getHeldItem(player);
        if (stack == null || stack.isEmpty() || !ReforgeEquip.isWeapon(stack)) {
            return fallback;
        }
        Item item = stack.getItem();
        if (item == null || item == Item.UNKNOWN) {
            return fallback;
        }
        double base = EquipmentDamageTooltipMath.getAverageBaseDamageFromInteractionVars(item);
        if (base <= 0.0d) {
            return fallback;
        }
        SocketData socketData = SocketManager.getSocketData(stack);
        int level = ReforgeEquip.getLevelFromItem(stack);
        double partsMultiplier = resolvePartsDamageMultiplier(stack);
        double refined = EquipmentDamageTooltipMath.computeBuffedWeaponDamage(stack.getItemId(),
                base, level, socketData, partsMultiplier);
        if (refined <= 0.0d) {
            return fallback;
        }
        return (float) refined;
    }

    public static float resolveWeaponBaseDamage(Store<EntityStore> store,
                                                Ref<EntityStore> attackerRef) {
        if (store == null || attackerRef == null) {
            return 0f;
        }
        Player player = store.getComponent(attackerRef, Player.getComponentType());
        if (player == null) {
            return 0f;
        }
        ItemStack stack = PlayerInventoryUtils.getHeldItem(player);
        if (stack == null || stack.isEmpty() || !ReforgeEquip.isWeapon(stack)) {
            return 0f;
        }
        Item item = stack.getItem();
        if (item == null || item == Item.UNKNOWN) {
            return 0f;
        }
        double base = EquipmentDamageTooltipMath.getAverageBaseDamageFromInteractionVars(item);
        if (base <= 0.0d) {
            return 0f;
        }
        return (float) base;
    }

    public static float resolveSignatureDamageBase(Store<EntityStore> store,
                                                   Ref<EntityStore> attackerRef,
                                                   boolean selfIsAttacker,
                                                   Damage damage,
                                                   float fallbackAmount) {
        return resolveLoreDamageBase(store, attackerRef, selfIsAttacker, damage, fallbackAmount);
    }

    public static float resolveWeaponDamageBaseForLoreHits(Store<EntityStore> store,
                                                           Ref<EntityStore> attackerRef,
                                                           boolean selfIsAttacker,
                                                           Damage damage,
                                                           float fallbackAmount) {
        if (selfIsAttacker && damage != null) {
            float eventBase = resolveDamageBase(true, damage, fallbackAmount);
            if (eventBase > 0f) {
                return eventBase;
            }
        }
        return resolveWeaponRefinedDamage(store, attackerRef, fallbackAmount);
    }

    public static DamageBaseResult resolveLoreDamageBaseResult(Store<EntityStore> store,
                                                               Ref<EntityStore> attackerRef,
                                                               boolean selfIsAttacker,
                                                               Damage damage,
                                                               float fallbackAmount) {
        if (selfIsAttacker) {
            float rawWeapon = resolveWeaponBaseDamage(store, attackerRef);
            if (rawWeapon > 0f) {
                return new DamageBaseResult(rawWeapon, false);
            }
            float eventBase = resolveDamageBase(true, damage, fallbackAmount);
            if (eventBase > 0f) {
                return new DamageBaseResult(eventBase, true);
            }
            return new DamageBaseResult(fallbackAmount, true);
        }
        float eventBase = resolveDamageBase(false, damage, fallbackAmount);
        if (eventBase > 0f) {
            return new DamageBaseResult(eventBase, true);
        }
        return new DamageBaseResult(fallbackAmount, true);
    }

    public static float resolveMaxHealth(Store<EntityStore> store, Ref<EntityStore> targetRef) {
        if (store == null || targetRef == null) {
            return 0f;
        }
        EntityStatMap statMap = store.getComponent(targetRef, EntityStatMap.getComponentType());
        if (statMap == null) {
            return 0f;
        }
        int healthStatIndex = getHealthStatIndex(statMap);
        if (healthStatIndex < 0) {
            return 0f;
        }
        EntityStatValue health = statMap.get(healthStatIndex);
        if (health == null) {
            return 0f;
        }
        return Math.max(0f, health.getMax());
    }

    public static void applyHeal(Store<EntityStore> store, Ref<EntityStore> targetRef, float amount) {
        if (store == null || targetRef == null || amount <= 0f) {
            return;
        }
        EntityStatMap statMap = store.getComponent(targetRef, EntityStatMap.getComponentType());
        if (statMap == null) {
            return;
        }
        int healthStatIndex = getHealthStatIndex(statMap);
        if (healthStatIndex < 0) {
            return;
        }
        EntityStatValue health = statMap.get(healthStatIndex);
        if (health == null || health.getMax() <= 0f) {
            return;
        }
        float missing = health.getMax() - health.get();
        if (missing <= 0f) {
            return;
        }
        statMap.addStatValue(healthStatIndex, Math.min(missing, amount));
    }

    public static int getHealthStatIndex(EntityStatMap statMap) {
        int byDefault = DefaultEntityStatTypes.getHealth();
        if (byDefault >= 0) {
            EntityStatValue value = statMap.get(byDefault);
            if (value != null) return value.getIndex();
        }
        String[] aliases = {"health", "Health", "HP", "hp"};
        for (String alias : aliases) {
            EntityStatValue value = statMap.get(alias);
            if (value != null) return value.getIndex();
        }
        return -1;
    }

    public static int getSignatureEnergyStatIndex(EntityStatMap statMap) {
        int byDefault = DefaultEntityStatTypes.getSignatureEnergy();
        if (byDefault >= 0) {
            EntityStatValue value = statMap.get(byDefault);
            if (value != null) return value.getIndex();
        }
        String[] aliases = {"SignatureEnergy", "signatureEnergy", "signature_energy", "signature"};
        for (String alias : aliases) {
            EntityStatValue value = statMap.get(alias);
            if (value != null) return value.getIndex();
        }
        return -1;
    }

    private static boolean applyDamageEvent(Store<EntityStore> store,
                                            Ref<EntityStore> sourceRef,
                                            Ref<EntityStore> targetRef,
                                            float rawDamage,
                                            boolean skipRefine,
                                            boolean useEntitySource,
                                            boolean isLoreDamage,
                                            DamageNumberFormatter.DamageKind kind,
                                            boolean skipCombatText) {
        try {
            float sigBefore = DEBUG_SIGNATURE_ENERGY ? getSignatureEnergy(store, sourceRef) : -1f;
            Damage.Source source = (useEntitySource && sourceRef != null)
                    ? new Damage.EntitySource(sourceRef)
                    : Damage.NULL_SOURCE;
            Damage damage = new Damage(source, DamageCause.PHYSICAL, rawDamage);
            if (isLoreDamage) {
                damage.putMetaObject(META_LORE_DAMAGE, Boolean.TRUE);
            }
            if (skipRefine) {
                damage.putMetaObject(EquipmentRefineEST.META_SKIP_REFORGE, Boolean.TRUE);
            }
            if (kind != null && kind != DamageNumberFormatter.DamageKind.FLAT) {
                DamageNumberMeta.markKind(damage, kind);
            }
            if (skipCombatText) {
                DamageNumberMeta.markSkipCombatText(damage);
            }
            store.invoke(targetRef, damage);
            LoreDebug.logKv("damage.event", "amount", rawDamage);
            if (DEBUG_SIGNATURE_ENERGY && sigBefore > 0.01f) {
                float sigAfter = getSignatureEnergy(store, sourceRef);
                if (sigAfter <= 0.01f) {
                    logSignatureEnergyZero(store, sourceRef, targetRef, sigBefore, sigAfter, rawDamage, useEntitySource);
                }
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void applyDirectHealthLoss(Store<EntityStore> store, Ref<EntityStore> targetRef, float rawDamage) {
        if (store == null || targetRef == null || rawDamage <= 0f) {
            LoreDebug.logKv("damage.direct.skip", "reason", "invalid", "rawDamage", rawDamage);
            return;
        }
        EntityStatMap statMap = store.getComponent(targetRef, EntityStatMap.getComponentType());
        if (statMap == null) {
            LoreDebug.logKv("damage.direct.skip", "reason", "noStatMap");
            return;
        }
        int healthStatIndex = getHealthStatIndex(statMap);
        if (healthStatIndex < 0) {
            LoreDebug.logKv("damage.direct.skip", "reason", "noHealthStat");
            return;
        }
        EntityStatValue health = statMap.get(healthStatIndex);
        if (health == null || health.get() <= 0f) {
            LoreDebug.logKv("damage.direct.skip", "reason", "targetDead");
            return;
        }
        float maxSpendable = Math.max(0f, health.get() - 0.1f);
        float applied = Math.min(rawDamage, maxSpendable);
        if (applied <= 0f) {
            LoreDebug.logKv("damage.direct.skip", "reason", "applied<=0", "maxSpendable", maxSpendable);
            return;
        }
        statMap.addStatValue(healthStatIndex, -applied);
        LoreDebug.logKv("damage.direct", "applied", applied);
    }

    private static double resolvePartsDamageMultiplier(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 1.0d;
        }
        Double value = stack.getFromMetadataOrNull(META_PARTS_DAMAGE_MULTIPLIER, Codec.DOUBLE);
        if (value == null) {
            return 1.0d;
        }
        return Math.max(0.5d, Math.min(2.0d, value.doubleValue()));
    }

    public static float getSignatureEnergy(Store<EntityStore> store, Ref<EntityStore> ref) {
        if (store == null || ref == null) {
            return -1f;
        }
        EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap == null) {
            return -1f;
        }
        int signatureIndex = getSignatureEnergyStatIndex(statMap);
        if (signatureIndex < 0) {
            return -1f;
        }
        EntityStatValue value = statMap.get(signatureIndex);
        if (value == null) {
            return -1f;
        }
        return value.get();
    }

    public static void logSignatureEnergyZero(Store<EntityStore> store,
                                              Ref<EntityStore> sourceRef,
                                              Ref<EntityStore> targetRef,
                                              float before,
                                              float after,
                                              float rawDamage,
                                              boolean useEntitySource) {
        logSignatureEnergyZero(store, sourceRef, targetRef, before, after, rawDamage, useEntitySource, "damageEvent");
    }

    public static void traceSignatureEnergy(Store<EntityStore> store,
                                            Ref<EntityStore> sourceRef,
                                            Ref<EntityStore> targetRef,
                                            String context) {
        if (!DEBUG_SIGNATURE_ENERGY || store == null || sourceRef == null) {
            return;
        }
        UUID playerId = null;
        try {
            Player player = store.getComponent(sourceRef, Player.getComponentType());
            if (player != null) {
                playerId = player.getUuid();
            }
        } catch (Throwable ignored) {
            // best-effort
        }
        if (playerId == null) {
            return;
        }
        float current = getSignatureEnergy(store, sourceRef);
        Float previous = SIGNATURE_LAST_VALUE.put(playerId, current);
        if (previous == null) {
            return;
        }
        if (previous > 0.01f && current <= 0.01f) {
            long now = System.currentTimeMillis();
            Long lastLog = SIGNATURE_LAST_LOG_AT.get(playerId);
            if (lastLog != null && now - lastLog < SIGNATURE_TRACE_COOLDOWN_MS) {
                return;
            }
            SIGNATURE_LAST_LOG_AT.put(playerId, now);
            logSignatureEnergyZero(store, sourceRef, targetRef, previous, current, 0f, false, context);
        }
    }

    public static void logSignatureEnergyZero(Store<EntityStore> store,
                                              Ref<EntityStore> sourceRef,
                                              Ref<EntityStore> targetRef,
                                              float before,
                                              float after,
                                              float rawDamage,
                                              boolean useEntitySource,
                                              String context) {
        if (!DEBUG_SIGNATURE_ENERGY) {
            return;
        }
        String sourceLabel = LoreTargetingUtils.resolveDebugEntityLabel(store, sourceRef);
        String targetLabel = LoreTargetingUtils.resolveDebugEntityLabel(store, targetRef);
        LoreDebug.logKv("signature.energy.zero",
                "before", before,
                "after", after,
                "rawDamage", rawDamage,
                "useEntitySource", useEntitySource,
                "context", context,
                "source", sourceLabel,
                "target", targetLabel,
                "thread", Thread.currentThread().getName());
        if (DEBUG_SIGNATURE_ENERGY_STACK) {
            new Exception("signature.energy.zero").printStackTrace(System.out);
        }
    }
}
