package irai.mod.reforge.Entity.Events;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.meta.MetaKey;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.event.InteractionChainStartEvent;
import com.hypixel.hytale.server.core.modules.projectile.component.ImpactModifiers;
import com.hypixel.hytale.server.core.modules.projectile.system.AbilityCastSnapshotSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Applies Rune Delta feed multipliers after Hytale snapshots the ability's base modifiers. */
public final class RuneDeltaCastSnapshotEST extends EntityEventSystem<EntityStore, InteractionChainStartEvent> {
    private static final MetaKey<Float> RUNE_DAMAGE_MULTIPLIER =
            Interaction.CONTEXT_META_REGISTRY.registerMetaObject(
                    context -> 1.0f, false, "socketreforge:rune_delta_damage_multiplier", Codec.FLOAT);
    private static final MetaKey<Float> RUNE_COST_MULTIPLIER =
            Interaction.CONTEXT_META_REGISTRY.registerMetaObject(
                    context -> 1.0f, false, "socketreforge:rune_delta_cost_multiplier", Codec.FLOAT);

    public RuneDeltaCastSnapshotEST() {
        super(InteractionChainStartEvent.class);
    }

    public static void setRuneDeltaMultipliers(InteractionContext context,
                                                float damageMultiplier,
                                                float costMultiplier) {
        if (context == null) {
            return;
        }
        context.getMetaStore().putMetaObject(RUNE_DAMAGE_MULTIPLIER, sanitize(damageMultiplier));
        context.getMetaStore().putMetaObject(RUNE_COST_MULTIPLIER, sanitize(costMultiplier));
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency<>(Order.AFTER, AbilityCastSnapshotSystem.class));
    }

    @Override
    public void handle(int index,
                       ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store,
                       CommandBuffer<EntityStore> commandBuffer,
                       InteractionChainStartEvent event) {
        if (event == null || event.getContext() == null) {
            return;
        }

        InteractionContext context = event.getContext();
        Float damageMultiplier = context.getMetaStore().getIfPresentMetaObject(RUNE_DAMAGE_MULTIPLIER);
        Float costMultiplier = context.getMetaStore().getIfPresentMetaObject(RUNE_COST_MULTIPLIER);
        if (damageMultiplier == null && costMultiplier == null) {
            return;
        }

        ImpactModifiers base = context.getMetaStore().getIfPresentMetaObject(ImpactModifiers.CAST_SNAPSHOT);
        Map<String, Float> attributes = base == null || base.getAttributes() == null
                ? new HashMap<>()
                : new HashMap<>(base.getAttributes());
        float damageScale = damageMultiplier == null ? 1.0f : sanitize(damageMultiplier);
        float costScale = costMultiplier == null ? 1.0f : sanitize(costMultiplier);
        attributes.put("Damage", (base == null ? 1.0f : base.getAttribute("Damage", 1.0f)) * damageScale);
        attributes.put("Cost", (base == null ? 1.0f : base.getAttribute("Cost", 1.0f)) * costScale);

        context.getMetaStore().putMetaObject(ImpactModifiers.CAST_SNAPSHOT,
                new ImpactModifiers(
                        attributes,
                        base == null ? new String[0] : base.getOnHitEffectIds(),
                        base == null ? null : base.getElementalCauseId()));
        context.getMetaStore().removeMetaObject(RUNE_DAMAGE_MULTIPLIER);
        context.getMetaStore().removeMetaObject(RUNE_COST_MULTIPLIER);
    }

    private static float sanitize(float multiplier) {
        return Float.isFinite(multiplier) && multiplier > 0.0f ? multiplier : 1.0f;
    }
}
