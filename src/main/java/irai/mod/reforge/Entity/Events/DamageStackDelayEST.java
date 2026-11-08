package irai.mod.reforge.Entity.Events;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
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
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.meta.IMetaRegistry;
import com.hypixel.hytale.server.core.meta.MetaKey;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Lore.LoreWorldTasks;

/** Delays burst hits before they enter damage modifiers, then lets each hit traverse the pipeline once. */
public final class DamageStackDelayEST extends DamageEventSystem {
    private static final boolean DAMAGE_CALCULATION_DEBUG = Boolean.parseBoolean(
            System.getProperty("socketreforge.debug.damageCalculation", "false"));
    private static final long HIT_SPACING_MILLIS = readPositiveLong(
            "socketreforge.damageStackSpacingMs", 45L);
    private static final long STATE_RETENTION_MILLIS = 5_000L;

    private static final MetaKey<Boolean> META_ALREADY_SPACED =
            Damage.META_REGISTRY.registerMetaObject(damage -> Boolean.FALSE, false,
                    "socketreforge:damage_stack_already_spaced", Codec.BOOLEAN);
    public static final MetaKey<Long> META_TRACE_ID =
            Damage.META_REGISTRY.registerMetaObject(damage -> 0L, false,
                    "socketreforge:damage_calculation_trace_id", Codec.LONG);
    public static final MetaKey<Float> META_RAW_AMOUNT =
            Damage.META_REGISTRY.registerMetaObject(damage -> 0.0f, false,
                    "socketreforge:damage_calculation_raw_amount", Codec.FLOAT);
    /** Shared pre-bonus damage basis. Frozen Shatter may update this before bonuses run. */
    public static final MetaKey<Float> META_BONUS_BASE_AMOUNT =
            Damage.META_REGISTRY.registerMetaObject(damage -> 0.0f, false,
                    "socketreforge:damage_bonus_base_amount", Codec.FLOAT);
    public static final MetaKey<Boolean> META_DEFERRED =
            Damage.META_REGISTRY.registerMetaObject(damage -> Boolean.FALSE, false,
                    "socketreforge:damage_calculation_deferred", Codec.BOOLEAN);
    public static final MetaKey<Long> META_QUEUED_AT_MILLIS =
            Damage.META_REGISTRY.registerMetaObject(damage -> 0L, false,
                    "socketreforge:damage_calculation_queued_at", Codec.LONG);

    private static final ConcurrentHashMap<TargetKey, QueueState> TARGET_QUEUES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Ref<EntityStore>, CoreRuneCastTrace> ACTIVE_CORE_RUNE_CASTS =
            new ConcurrentHashMap<>();
    private static final AtomicLong NEXT_TRACE_ID = new AtomicLong();

    public static boolean isDamageCalculationDebugEnabled() {
        return DAMAGE_CALCULATION_DEBUG;
    }

    /** Tags outgoing damage briefly after a native core-rune cast for damage-log attribution. */
    public static void markCoreRuneCast(Ref<EntityStore> ownerRef,
                                        String abilityId,
                                        String runeItemId,
                                        long activeWindowMillis) {
        if (!DAMAGE_CALCULATION_DEBUG || ownerRef == null || !ownerRef.isValid()) {
            return;
        }
        long now = System.currentTimeMillis();
        long windowMillis = Math.max(1000L, Math.min(10000L, activeWindowMillis));
        ACTIVE_CORE_RUNE_CASTS.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis <= now);
        CoreRuneCastTrace trace = new CoreRuneCastTrace(
                abilityId == null || abilityId.isBlank() ? "unknown" : abilityId,
                runeItemId == null || runeItemId.isBlank() ? "unknown" : runeItemId,
                now + windowMillis);
        ACTIVE_CORE_RUNE_CASTS.put(ownerRef, trace);
        CompletableFuture.delayedExecutor(windowMillis, TimeUnit.MILLISECONDS)
                .execute(() -> ACTIVE_CORE_RUNE_CASTS.remove(ownerRef, trace));
    }

    static String describeActiveCoreRuneCast(Ref<EntityStore> sourceRef) {
        if (sourceRef == null || !sourceRef.isValid()) {
            return "none";
        }
        CoreRuneCastTrace trace = ACTIVE_CORE_RUNE_CASTS.get(sourceRef);
        if (trace == null) {
            return "none";
        }
        if (trace.expiresAtMillis <= System.currentTimeMillis()) {
            ACTIVE_CORE_RUNE_CASTS.remove(sourceRef, trace);
            return "none";
        }
        return "ability=" + trace.abilityId + ",item=" + trace.runeItemId;
    }

    public static float getBonusBaseDamage(Damage damage, float fallback) {
        if (damage == null) {
            return Math.max(0.0f, fallback);
        }
        Float base = damage.getIfPresentMetaObject(META_BONUS_BASE_AMOUNT);
        if (base == null || base <= 0.0f) {
            base = damage.getIfPresentMetaObject(META_RAW_AMOUNT);
        }
        return base == null || base <= 0.0f ? Math.max(0.0f, fallback) : base;
    }

    public static void logDamageStage(String stage, Damage damage, float before, float after, String details) {
        if (!DAMAGE_CALCULATION_DEBUG || damage == null) {
            return;
        }
        Long traceId = damage.getIfPresentMetaObject(META_TRACE_ID);
        if (traceId == null || traceId <= 0L) {
            return;
        }
        double multiplier = Math.abs(before) > 0.0001f ? after / (double) before : 0.0d;
        System.out.printf(java.util.Locale.ROOT,
                "[SocketReforge][DamageCalc] id=%d stage=filter:%s before=%.4f after=%.4f "
                        + "delta=%.4f multiplier=%.4f details=%s%n",
                traceId, stage, before, after, after - before, multiplier,
                details == null || details.isBlank() ? "none" : details);
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getGatherDamageGroup();
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency<>(Order.BEFORE, DamageModule.OrderGatherFilter.class));
    }

    @Override
    public void handle(int index,
                       ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store,
                       CommandBuffer<EntityStore> commandBuffer,
                       Damage damage) {
        if (store == null || damage == null
                || damage.getAmount() <= 0.0f
                || Boolean.TRUE.equals(damage.getIfPresentMetaObject(META_ALREADY_SPACED))) {
            return;
        }

        irai.mod.reforge.Interactions.RazorstrikeProjectileDamage.normalizeEarthImpact(damage, store);

        if (damage.getIfPresentMetaObject(META_TRACE_ID) == null) {
            damage.putMetaObject(META_TRACE_ID, NEXT_TRACE_ID.incrementAndGet());
            damage.putMetaObject(META_RAW_AMOUNT, damage.getAmount());
            damage.putMetaObject(META_BONUS_BASE_AMOUNT, damage.getAmount());
        }

        Ref<EntityStore> targetRef = chunk.getReferenceTo(index);
        UUIDComponent uuidComponent = store.getComponent(targetRef, UUIDComponent.getComponentType());
        if (uuidComponent == null || uuidComponent.getUuid() == null) {
            return;
        }

        TargetKey key = new TargetKey(store, uuidComponent.getUuid());
        QueueState state = TARGET_QUEUES.computeIfAbsent(key, ignored -> new QueueState());
        long now = System.currentTimeMillis();
        long applyAt = state.reserve(now);
        scheduleCleanup(key, state);
        if (applyAt <= now) {
            return;
        }

        Damage deferredDamage = copyDamage(damage);
        deferredDamage.putMetaObject(META_ALREADY_SPACED, Boolean.TRUE);
        deferredDamage.putMetaObject(META_DEFERRED, Boolean.TRUE);
        deferredDamage.putMetaObject(META_QUEUED_AT_MILLIS, now);
        long delayMillis = applyAt - now;
        try {
            CompletableFuture.delayedExecutor(delayMillis, TimeUnit.MILLISECONDS).execute(() -> {
                if (store.isShutdown() || !targetRef.isValid()) {
                    return;
                }
                LoreWorldTasks.queue(store, () -> {
                    if (!store.isShutdown() && targetRef.isValid()) {
                        store.invoke(targetRef, deferredDamage);
                    }
                });
            });
            damage.setCancelled(true);
            if (DAMAGE_CALCULATION_DEBUG) {
                System.out.printf(java.util.Locale.ROOT,
                        "[SocketReforge][DamageCalc] id=%d stage=queued source=%s target=%s cause=%s "
                                + "raw=%.4f delayMs=%d%n",
                        damage.getIfPresentMetaObject(META_TRACE_ID),
                        describeSource(store, damage),
                        uuidComponent.getUuid(),
                        damage.getCause() == null ? "unknown" : damage.getCause().getId(),
                        damage.getIfPresentMetaObject(META_RAW_AMOUNT),
                        delayMillis);
            }
        } catch (RuntimeException schedulingFailure) {
            // Keep the original event alive if the deferred task could not be scheduled.
        }
    }

    private static Damage copyDamage(Damage original) {
        Damage copy = new Damage(original.getSource(), original.getCause(), original.getAmount());
        copy.setDamageCauseIndex(original.getDamageCauseIndex());
        original.getMetaStore().getRegistry().forEachMetaEntry(
                original,
                new IMetaRegistry.MetaEntryConsumer() {
                    @Override
                    public <T> void accept(MetaKey<T> key, T value) {
                        copy.putMetaObject(key, value);
                    }
                });
        return copy;
    }

    private static void scheduleCleanup(TargetKey key, QueueState state) {
        if (!state.beginCleanup()) {
            return;
        }
        CompletableFuture.delayedExecutor(STATE_RETENTION_MILLIS, TimeUnit.MILLISECONDS).execute(() -> {
            if (state.isIdleLongerThan(STATE_RETENTION_MILLIS)) {
                TARGET_QUEUES.remove(key, state);
                state.finishCleanup();
                return;
            }
            state.finishCleanup();
            scheduleCleanup(key, state);
        });
    }

    private static long readPositiveLong(String property, long fallback) {
        try {
            return Math.max(1L, Long.parseLong(System.getProperty(property, Long.toString(fallback))));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    static String describeSource(Store<EntityStore> store, Damage damage) {
        Damage.Source source = damage == null ? null : damage.getSource();
        if (source == null) {
            return "unknown";
        }
        String label = source.getClass().getSimpleName();
        if (source instanceof Damage.EntitySource entitySource) {
            Ref<EntityStore> sourceRef = entitySource.getRef();
            if (store != null && sourceRef != null && sourceRef.isValid()) {
                UUIDComponent sourceUuid = store.getComponent(sourceRef, UUIDComponent.getComponentType());
                if (sourceUuid != null) {
                    return label + ":" + sourceUuid.getUuid();
                }
            }
        }
        return label;
    }

    private record CoreRuneCastTrace(String abilityId, String runeItemId, long expiresAtMillis) {}

    private record TargetKey(Store<EntityStore> store, UUID targetUuid) {}

    private static final class QueueState {
        private long nextApplyAt;
        private long lastActivityAt;
        private boolean cleanupScheduled;

        synchronized long reserve(long now) {
            lastActivityAt = now;
            long applyAt = Math.max(now, nextApplyAt);
            nextApplyAt = applyAt + HIT_SPACING_MILLIS;
            return applyAt;
        }

        synchronized boolean beginCleanup() {
            if (cleanupScheduled) {
                return false;
            }
            cleanupScheduled = true;
            return true;
        }

        synchronized void finishCleanup() {
            cleanupScheduled = false;
        }

        synchronized boolean isIdleLongerThan(long millis) {
            return System.currentTimeMillis() - lastActivityAt >= millis;
        }
    }
}
