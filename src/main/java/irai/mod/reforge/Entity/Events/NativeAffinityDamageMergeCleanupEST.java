package irai.mod.reforge.Entity.Events;

import java.util.Set;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Restores the applied damage event after Hytale's native combat text has consumed its display value. */
public final class NativeAffinityDamageMergeCleanupEST extends DamageEventSystem {
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getInspectDamageGroup();
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(
                new SystemDependency<>(Order.AFTER, DamageSystems.EntityUIEvents.class),
                new SystemDependency<>(Order.AFTER, NativeAffinityDamageMergeEST.class));
    }

    @Override
    public void handle(int index,
                       ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store,
                       CommandBuffer<EntityStore> commandBuffer,
                       Damage damage) {
        if (damage == null || !Boolean.TRUE.equals(
                damage.getIfPresentMetaObject(NativeAffinityDamageMergeEST.META_DISPLAY_MUTATED))) {
            return;
        }
        Float originalAmount = damage.getIfPresentMetaObject(NativeAffinityDamageMergeEST.META_RESTORE_AMOUNT);
        Integer originalCauseIndex = damage.getIfPresentMetaObject(NativeAffinityDamageMergeEST.META_RESTORE_CAUSE_INDEX);
        if (originalAmount != null) {
            damage.setAmount(originalAmount);
        }
        if (originalCauseIndex != null && originalCauseIndex >= 0) {
            damage.setDamageCauseIndex(originalCauseIndex);
        }
        damage.getMetaStore().removeMetaObject(NativeAffinityDamageMergeEST.META_DISPLAY_MUTATED);
        damage.getMetaStore().removeMetaObject(NativeAffinityDamageMergeEST.META_RESTORE_AMOUNT);
        damage.getMetaStore().removeMetaObject(NativeAffinityDamageMergeEST.META_RESTORE_CAUSE_INDEX);
    }
}
