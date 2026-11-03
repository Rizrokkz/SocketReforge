package irai.mod.reforge.Entity.Events;

import java.util.Set;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
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

import irai.mod.reforge.Common.EnemyElementalShieldUtils;

/** Restores shielded elemental damage only for Hytale's built-in combat-text event. */
public final class NativeShieldDamageFeedbackEST extends DamageEventSystem {
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
        return Set.of(new SystemDependency<>(Order.BEFORE, DamageSystems.EntityUIEvents.class));
    }

    @Override
    public void handle(int index,
                       ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store,
                       CommandBuffer<EntityStore> commandBuffer,
                       Damage damage) {
        if (damage == null || !Boolean.TRUE.equals(
                damage.getIfPresentMetaObject(EnemyElementalShieldUtils.META_NATIVE_SHIELD_FEEDBACK_EVENT))) {
            return;
        }
        Double displayAmount = damage.getIfPresentMetaObject(
                EnemyElementalShieldUtils.META_NATIVE_SHIELD_FEEDBACK_AMOUNT);
        if (displayAmount == null || !Double.isFinite(displayAmount) || displayAmount <= 0.0001d) {
            return;
        }

        // ApplyDamage has already processed this event at zero, so this affects only the native
        // EntityUIEvents renderer that follows this system in the inspect group.
        damage.setAmount(displayAmount.floatValue());
    }
}
