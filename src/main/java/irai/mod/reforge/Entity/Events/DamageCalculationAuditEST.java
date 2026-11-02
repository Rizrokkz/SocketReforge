package irai.mod.reforge.Entity.Events;

import java.util.UUID;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.damage.ResistanceModifier;
import com.hypixel.hytale.server.core.modules.projectile.component.ImpactModifiers;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;

/** Logs the damage amount before and after the shared damage calculation pipeline. */
public final class DamageCalculationAuditEST extends DamageEventSystem {
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getInspectDamageGroup();
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(int index,
                       ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store,
                       CommandBuffer<EntityStore> commandBuffer,
                       Damage damage) {
        if (!DamageStackDelayEST.isDamageCalculationDebugEnabled() || store == null || damage == null) {
            return;
        }

        Long traceId = damage.getIfPresentMetaObject(DamageStackDelayEST.META_TRACE_ID);
        if (traceId == null || traceId <= 0L) {
            return;
        }

        Ref<EntityStore> targetRef = chunk.getReferenceTo(index);
        UUID targetUuid = getUuid(store, targetRef);
        Float rawAmountMeta = damage.getIfPresentMetaObject(DamageStackDelayEST.META_RAW_AMOUNT);
        float rawAmount = rawAmountMeta == null ? damage.getAmount() : rawAmountMeta;
        float appliedAmount = damage.getAmount();
        double multiplier = rawAmount > 0.0001f ? appliedAmount / (double) rawAmount : 0.0d;
        float initialAmount = damage.getInitialAmount();
        long queuedAt = valueOrZero(damage.getIfPresentMetaObject(DamageStackDelayEST.META_QUEUED_AT_MILLIS));
        long delayMillis = queuedAt <= 0L ? 0L : Math.max(0L, System.currentTimeMillis() - queuedAt);
        String cause = damage.getCause() == null ? "unknown" : damage.getCause().getId();
        String outgoingEffects = describeOutgoingEffects(store, damage);
        String projectileContext = describeProjectileContext(store, damage);
        String coreRuneContext = damage.getSource() instanceof Damage.EntitySource source
                ? DamageStackDelayEST.describeActiveCoreRuneCast(source.getRef())
                : "none";

        System.out.printf(java.util.Locale.ROOT,
                "[SocketReforge][DamageCalc] id=%d stage=applied source=%s target=%s cause=%s causeIndex=%d "
                        + "raw=%.4f initial=%.4f applied=%.4f bonusDelta=%.4f multiplier=%.4f "
                        + "deferred=%s delayMs=%d outgoingEffects=%s projectileContext=%s coreRuneContext=%s%n",
                traceId,
                DamageStackDelayEST.describeSource(store, damage),
                targetUuid == null ? "unknown" : targetUuid,
                cause,
                damage.getDamageCauseIndex(),
                rawAmount,
                initialAmount,
                appliedAmount,
                appliedAmount - rawAmount,
                multiplier,
                Boolean.TRUE.equals(damage.getIfPresentMetaObject(DamageStackDelayEST.META_DEFERRED)),
                delayMillis,
                outgoingEffects,
                projectileContext,
                coreRuneContext);
    }

    /** Includes the projectile's cast-time damage snapshot when the source is a projectile. */
    private static String describeProjectileContext(Store<EntityStore> store, Damage damage) {
        if (store == null || damage == null || !(damage.getSource() instanceof Damage.ProjectileSource source)) {
            return "none";
        }
        Ref<EntityStore> projectileRef = source.getProjectile();
        if (projectileRef == null || !projectileRef.isValid()) {
            return "projectile=invalid";
        }
        UUIDComponent uuidComponent = store.getComponent(projectileRef, UUIDComponent.getComponentType());
        ImpactModifiers modifiers = store.getComponent(projectileRef, ImpactModifiers.getComponentType());
        return "projectile=" + (uuidComponent == null ? projectileRef : uuidComponent.getUuid())
                + ",impactAttributes=" + (modifiers == null ? "none" : modifiers.getAttributes());
    }

    /** Reports active entity-effect outgoing multipliers that Hytale applies after our filters. */
    private static String describeOutgoingEffects(Store<EntityStore> store, Damage damage) {
        if (store == null || damage == null || !(damage.getSource() instanceof Damage.EntitySource source)) {
            return "none";
        }
        Ref<EntityStore> sourceRef = source.getRef();
        if (sourceRef == null || !sourceRef.isValid()) {
            return "none";
        }
        EffectControllerComponent controller = store.getComponent(
                sourceRef, EffectControllerComponent.getComponentType());
        DamageCause damageCause = DamageCause.getAssetMap().getAsset(damage.getDamageCauseIndex());
        if (controller == null || damageCause == null) {
            return "none";
        }

        float totalFlat = 0.0f;
        float totalMultiplier = 0.0f;
        StringBuilder effects = new StringBuilder();
        for (int effectIndex : controller.getActiveEffectIndexes()) {
            EntityEffect effect = EntityEffect.getAssetMap().getAsset(effectIndex);
            if (effect == null || effect.getOutgoingDamageValues() == null) {
                continue;
            }
            float effectFlat = 0.0f;
            float effectMultiplier = 0.0f;
            for (Map.Entry<DamageCause, ResistanceModifier[]> entry
                    : effect.getOutgoingDamageValues().entrySet()) {
                if (entry.getKey() == null || !damageCause.isOrInherits(entry.getKey())
                        || entry.getValue() == null) {
                    continue;
                }
                for (ResistanceModifier modifier : entry.getValue()) {
                    if (modifier == null) {
                        continue;
                    }
                    if ("FLAT".equals(modifier.getCalculationType().name())) {
                        effectFlat += modifier.getAmount();
                    } else {
                        effectMultiplier += modifier.getAmount();
                    }
                }
            }
            if (Math.abs(effectFlat) < 0.0001f && Math.abs(effectMultiplier) < 0.0001f) {
                continue;
            }
            if (effects.length() > 0) {
                effects.append('|');
            }
            effects.append(effect.getId())
                    .append("(flat=").append(String.format(java.util.Locale.ROOT, "%.4f", effectFlat))
                    .append(",mult=").append(String.format(java.util.Locale.ROOT, "%.4f", effectMultiplier))
                    .append(')');
            totalFlat += effectFlat;
            totalMultiplier += effectMultiplier;
        }
        if (effects.length() == 0) {
            return "none";
        }
        return String.format(java.util.Locale.ROOT, "flat=%.4f,mult=%.4f,effects=[%s]",
                totalFlat, totalMultiplier, effects);
    }

    private static UUID getUuid(Store<EntityStore> store, Ref<EntityStore> ref) {
        if (store == null || ref == null || !ref.isValid()) {
            return null;
        }
        UUIDComponent component = store.getComponent(ref, UUIDComponent.getComponentType());
        return component == null ? null : component.getUuid();
    }

    private static long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }
}
