package irai.mod.reforge.Entity.Events;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.meta.MetaKey;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Makes weapon-affinity damage one native combat-text event without changing health damage.
 * Both contributions are captured in InspectDamageGroup, after Hytale's resistance filters.
 */
public final class NativeAffinityDamageMergeEST extends DamageEventSystem {
    public static final MetaKey<Long> META_AFFINITY_GROUP_ID =
            Damage.META_REGISTRY.registerMetaObject(d -> 0L, false,
                    "socketreforge:native_affinity_group_id", Codec.LONG);
    public static final MetaKey<Integer> META_EXPECTED_ELEMENTAL_COMPONENTS =
            Damage.META_REGISTRY.registerMetaObject(d -> 0, false,
                    "socketreforge:native_affinity_expected_components", Codec.INTEGER);
    public static final MetaKey<Boolean> META_AFFINITY_SHIELD_FEEDBACK =
            Damage.META_REGISTRY.registerMetaObject(d -> Boolean.FALSE, false,
                    "socketreforge:native_affinity_shield_feedback", Codec.BOOLEAN);

    static final MetaKey<Boolean> META_DISPLAY_MUTATED =
            Damage.META_REGISTRY.registerMetaObject(d -> Boolean.FALSE, false,
                    "socketreforge:native_affinity_display_mutated", Codec.BOOLEAN);
    static final MetaKey<Float> META_RESTORE_AMOUNT =
            Damage.META_REGISTRY.registerMetaObject(d -> 0.0f, false,
                    "socketreforge:native_affinity_restore_amount", Codec.FLOAT);
    static final MetaKey<Integer> META_RESTORE_CAUSE_INDEX =
            Damage.META_REGISTRY.registerMetaObject(d -> -1, false,
                    "socketreforge:native_affinity_restore_cause_index", Codec.INTEGER);

    private static final AtomicLong NEXT_GROUP_ID = new AtomicLong();
    private static final ConcurrentHashMap<Long, HitGroup> HIT_GROUPS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, ExpectedComponents> EXPECTED_COMPONENTS = new ConcurrentHashMap<>();
    private static final long GROUP_RETENTION_MILLIS = 5_000L;

    /** Called once for each elemental event dispatched from one physical Damage event. */
    public static long registerElementalComponent(Damage physicalDamage) {
        if (physicalDamage == null) {
            return 0L;
        }
        Long groupIdValue = physicalDamage.getIfPresentMetaObject(META_AFFINITY_GROUP_ID);
        long groupId = groupIdValue == null || groupIdValue <= 0L
                ? NEXT_GROUP_ID.incrementAndGet()
                : groupIdValue;
        int updatedExpected = incrementExpectedComponents(groupId);
        physicalDamage.putMetaObject(META_AFFINITY_GROUP_ID, groupId);
        physicalDamage.putMetaObject(META_EXPECTED_ELEMENTAL_COMPONENTS, updatedExpected);
        return groupId;
    }

    /** Adds a display-only shield contribution to an already grouped affinity hit. */
    public static boolean registerShieldFeedback(Damage affinityDamage, Damage shieldFeedback) {
        if (affinityDamage == null || shieldFeedback == null) {
            return false;
        }
        Long groupId = affinityDamage.getIfPresentMetaObject(META_AFFINITY_GROUP_ID);
        if (groupId == null || groupId <= 0L) {
            return false;
        }
        int updatedExpected = incrementExpectedComponents(groupId);
        affinityDamage.putMetaObject(META_EXPECTED_ELEMENTAL_COMPONENTS, updatedExpected);
        shieldFeedback.putMetaObject(META_AFFINITY_GROUP_ID, groupId);
        shieldFeedback.putMetaObject(META_EXPECTED_ELEMENTAL_COMPONENTS, updatedExpected);
        shieldFeedback.putMetaObject(META_AFFINITY_SHIELD_FEEDBACK, Boolean.TRUE);
        return true;
    }

    private static int incrementExpectedComponents(long groupId) {
        long now = System.currentTimeMillis();
        ExpectedComponents updated = EXPECTED_COMPONENTS.compute(groupId, (ignored, current) ->
                new ExpectedComponents((current == null ? 0 : current.count) + 1, now));
        return updated.count;
    }

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
                new SystemDependency<>(Order.AFTER, DamageCalculationAuditEST.class),
                new SystemDependency<>(Order.AFTER, DamageNumberEST.class),
                new SystemDependency<>(Order.AFTER, NativeShieldDamageFeedbackEST.class),
                new SystemDependency<>(Order.BEFORE, DamageSystems.EntityUIEvents.class));
    }

    @Override
    public void handle(int index,
                       ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store,
                       CommandBuffer<EntityStore> commandBuffer,
                       Damage damage) {
        if (damage == null || DamageNumberEST.isCustomCombatTextEnabled()) {
            return;
        }

        Long groupIdValue = damage.getIfPresentMetaObject(META_AFFINITY_GROUP_ID);
        if (groupIdValue == null || groupIdValue <= 0L) {
            return;
        }

        long now = System.currentTimeMillis();
        HIT_GROUPS.entrySet().removeIf(entry -> now - entry.getValue().lastUpdatedAt > GROUP_RETENTION_MILLIS);
        EXPECTED_COMPONENTS.entrySet().removeIf(entry -> now - entry.getValue().updatedAt > GROUP_RETENTION_MILLIS);

        Ref<EntityStore> targetRef = chunk.getReferenceTo(index);
        boolean elementalComponent = Boolean.TRUE.equals(
                damage.getIfPresentMetaObject(EquipmentRefineEST.META_SOCKET_ELEMENTAL_COMPONENT));
        boolean shieldFeedback = Boolean.TRUE.equals(
                damage.getIfPresentMetaObject(META_AFFINITY_SHIELD_FEEDBACK));
        Integer expectedValue = damage.getIfPresentMetaObject(META_EXPECTED_ELEMENTAL_COMPONENTS);
        ExpectedComponents expectedState = EXPECTED_COMPONENTS.get(groupIdValue);
        int expectedComponents = Math.max(
                expectedValue == null ? 0 : Math.max(0, expectedValue),
                expectedState == null ? 0 : expectedState.count);
        float actualAmount = finiteNonNegative(damage.getAmount());
        int originalCauseIndex = damage.getDamageCauseIndex();

        HitGroup hitGroup = HIT_GROUPS.computeIfAbsent(groupIdValue, ignored -> new HitGroup());
        synchronized (hitGroup) {
            hitGroup.lastUpdatedAt = now;
            hitGroup.expectedElementalComponents = Math.max(
                    hitGroup.expectedElementalComponents, expectedComponents);
            if (elementalComponent || shieldFeedback) {
                hitGroup.elementalAmount += actualAmount;
                hitGroup.seenElementalComponents++;
                // Native combat text chooses its color from the event's damage cause. Keep the
                // cause that contributed the most post-resistance elemental damage so the merged
                // number uses the dominant affinity's native color.
                if (actualAmount > hitGroup.largestElementalContribution) {
                    hitGroup.largestElementalContribution = actualAmount;
                    hitGroup.elementalCauseIndex = originalCauseIndex;
                }
            } else {
                hitGroup.physicalAmount += actualAmount;
                hitGroup.physicalSeen = true;
                hitGroup.physicalCauseIndex = originalCauseIndex;
            }

            damage.putMetaObject(META_DISPLAY_MUTATED, Boolean.TRUE);
            damage.putMetaObject(META_RESTORE_AMOUNT, damage.getAmount());
            damage.putMetaObject(META_RESTORE_CAUSE_INDEX, originalCauseIndex);

            boolean complete = hitGroup.physicalSeen
                    && hitGroup.seenElementalComponents >= hitGroup.expectedElementalComponents;
            if (!complete) {
                // Keep native EntityUIEvents from displaying an intermediate physical or element
                // number. Cleanup restores this value after the native renderer has run.
                damage.setAmount(0.0f);
                return;
            }

            HIT_GROUPS.remove(groupIdValue, hitGroup);
            EXPECTED_COMPONENTS.remove(groupIdValue);
            float combinedAmount = finiteNonNegative(hitGroup.physicalAmount + hitGroup.elementalAmount);
            if (combinedAmount <= 0.0001f) {
                damage.setAmount(0.0f);
                return;
            }

            damage.setAmount(combinedAmount);
            int displayCauseIndex = hitGroup.elementalCauseIndex >= 0
                    ? hitGroup.elementalCauseIndex
                    : hitGroup.physicalCauseIndex;
            if (displayCauseIndex >= 0) {
                damage.setDamageCauseIndex(displayCauseIndex);
            }
        }
    }

    private static float finiteNonNegative(float value) {
        return Float.isFinite(value) ? Math.max(0.0f, value) : 0.0f;
    }

    private static final class HitGroup {
        private int expectedElementalComponents;
        private int seenElementalComponents;
        private float physicalAmount;
        private float elementalAmount;
        private float largestElementalContribution;
        private int physicalCauseIndex = -1;
        private int elementalCauseIndex = -1;
        private boolean physicalSeen;
        private volatile long lastUpdatedAt = System.currentTimeMillis();
    }

    private static final class ExpectedComponents {
        private final int count;
        private final long updatedAt;

        private ExpectedComponents(int count, long updatedAt) {
            this.count = count;
            this.updatedAt = updatedAt;
        }
    }
}
