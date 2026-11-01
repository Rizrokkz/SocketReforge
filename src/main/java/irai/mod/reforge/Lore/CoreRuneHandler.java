package irai.mod.reforge.Lore;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Rotation3f;
import org.joml.Vector3d;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.AllLegacyLivingEntityTypesQuery;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.physics.SimplePhysicsProvider;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.projectile.ProjectileModule;
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig;
import com.hypixel.hytale.server.core.util.TargetUtil;
import irai.mod.reforge.Entity.VolleyHomingArrowEntity;

import static irai.mod.reforge.Lore.LoreProcHandler.CORE_RUNE_MAX_NPC_CHECKS;
import static irai.mod.reforge.Lore.LoreProcHandler.CORE_RUNE_MAX_TARGETS;
import static irai.mod.reforge.Lore.LoreProcHandler.DEBUG_LORE_PROCS;
import static irai.mod.reforge.Lore.LoreProcHandler.HEAL_TOTEM_EFFECT_IDS;
import static irai.mod.reforge.Lore.LoreProcHandler.OMNISLASH_SCHEDULER;
import static irai.mod.reforge.Lore.LoreProcHandler.SIGNATURE_BIG_ARROW_PARTICLE_IDS;
import static irai.mod.reforge.Lore.LoreProcHandler.SIGNATURE_VORTEXSTRIKE_PARTICLE_IDS;
import static irai.mod.reforge.Lore.LoreProcHandler.applyCoreAbilitySupportRunestones;
import static irai.mod.reforge.Lore.LoreProcHandler.applyCoreRuneDamage;
import static irai.mod.reforge.Lore.LoreProcHandler.applyCoreRuneVelocity;
import static irai.mod.reforge.Lore.LoreProcHandler.applyOctaslashSupportRunestonesOnHit;
import static irai.mod.reforge.Lore.LoreProcHandler.isEntityAlive;
import static irai.mod.reforge.Lore.LoreProcHandler.isEntityDeadOrDying;
import static irai.mod.reforge.Lore.LoreProcHandler.queueAbilityHitWeaponLores;
import static irai.mod.reforge.Lore.LoreProcHandler.resolveLookRotation;
import static irai.mod.reforge.Lore.LoreProcHandler.resolveProjectileConfig;
import static irai.mod.reforge.Lore.LoreProcHandler.restoreSignatureEnergy;

/** Core ability rune cast, projectile, and support behavior. */
public final class CoreRuneHandler {
    private static final Map<Ref<EntityStore>, VolleyRuneBuff> ACTIVE_VOLLEY_RUNE_BUFFS =
            new ConcurrentHashMap<>();
    private static final Map<Ref<EntityStore>, VolleyHomingArrow> ACTIVE_VOLLEY_PROJECTILE_ARROWS =
            new ConcurrentHashMap<>();
    private static final Map<Ref<EntityStore>, BigArrowRuneShot> ACTIVE_BIG_ARROW_RUNE_SHOTS =
            new ConcurrentHashMap<>();
    private static final Map<Ref<EntityStore>, RazorstrikeRuneShot> ACTIVE_RAZORSTRIKE_RUNE_SHOTS =
            new ConcurrentHashMap<>();
    private static final Map<UUID, Long> BERSERK_RUNE_UNTIL = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> CRITICAL_CHARGE_HITS = new ConcurrentHashMap<>();
    private static final Map<Ref<EntityStore>, Long> VORTEXSTRIKE_AIR_HOLD_UNTIL = new ConcurrentHashMap<>();
    private static final Set<Ref<EntityStore>> ACTIVE_VORTEXSTRIKE_AIR_HOLDS = ConcurrentHashMap.newKeySet();

    private static final long VOLLEY_RUNE_BUFF_DURATION_MS = 5000L;
    private static final long VOLLEY_ARROW_GATHER_MS = 350L;
    private static final long VOLLEY_ARROW_TICK_MS = 80L;
    private static final long VOLLEY_ARROW_MAX_AGE_MS = 12000L;
    private static final double VOLLEY_ARROW_ORBIT_RADIUS = 2.0d;
    private static final double VOLLEY_ARROW_SPEED = 28.0d;
    private static final double VOLLEY_ARROW_BOUNCE_RADIUS = 18.0d;
    private static final int VOLLEY_ARROW_MAX_COUNT = 6;
    private static final int VOLLEY_ARROW_MAX_FORK_COUNT = 6;
    private static final int VOLLEY_ARROW_MAX_BOUNCES = 8;

    private static final double CORE_VORTEXSTRIKE_PULL_RADIUS = 6.0d;
    private static final long CORE_VORTEXSTRIKE_PUSH_DELAY_MS = 900L;
    private static final long CORE_VORTEXSTRIKE_PUSH_TRAIL_DURATION_MS = 450L;
    private static final String CORE_VORTEXSTRIKE_PUSH_TRAIL_PARTICLE = "Ability_GroundSlam_Line";
    private static final long CORE_VORTEXSTRIKE_PUSH_TRAIL_INTERVAL_MS = 45L;
    private static final float CORE_VORTEXSTRIKE_PUSH_TRAIL_SCALE = 1.2f;
    private static final float CORE_VORTEXSTRIKE_PHASE_DAMAGE_MULTIPLIER = 0.275f;
    private static final float CORE_VORTEXSTRIKE_MAX_PUSH_BONUS_MULTIPLIER = 1.0f;
    private record VortexstrikePulledTarget(Ref<EntityStore> targetRef, double pullDistance) {}

    private static final String BIG_ARROW_IMPLOSION_PARTICLE_ID = "Lore_Big_Arrow_Implosion";
    private static final double BIG_ARROW_IMPLOSION_RADIUS = 4.5d;

    private static final class VolleyRuneBuff {
        final Store<EntityStore> store;
        final Ref<EntityStore> attackerRef;
        final int maxArrows;
        final int bounceCount;
        final int forkCount;
        final int feedTier;
        final float arrowDamage;
        final float splitChildDamageMultiplier;
        final float projectileSpeedMultiplier;
        final List<LoreSocketManager.SocketedModifierRunestone> supportRunestones;
        final long startedAtMs;
        final long expiresAtMs;
        final List<VolleyHomingArrow> arrows = new ArrayList<>();
        boolean launched;
        boolean tickScheduled;

        VolleyRuneBuff(Store<EntityStore> store,
                       Ref<EntityStore> attackerRef,
                       int maxArrows,
                       int bounceCount,
                       int forkCount,
                       int feedTier,
                       float arrowDamage,
                       float splitChildDamageMultiplier,
                       float projectileSpeedMultiplier,
                       List<LoreSocketManager.SocketedModifierRunestone> supportRunestones,
                       long startedAtMs) {
            this.store = store;
            this.attackerRef = attackerRef;
            this.maxArrows = maxArrows;
            this.bounceCount = bounceCount;
            this.forkCount = forkCount;
            this.feedTier = feedTier;
            this.arrowDamage = arrowDamage;
            this.splitChildDamageMultiplier = splitChildDamageMultiplier;
            this.projectileSpeedMultiplier = projectileSpeedMultiplier;
            this.supportRunestones = supportRunestones == null || supportRunestones.isEmpty()
                    ? List.of() : List.copyOf(supportRunestones);
            this.startedAtMs = startedAtMs;
            this.expiresAtMs = startedAtMs + VOLLEY_RUNE_BUFF_DURATION_MS;
        }
    }

    private static final class BigArrowRuneShot {
        final long expiresAtMs;
        final List<LoreSocketManager.SocketedModifierRunestone> supportRunestones;
        final Set<Ref<EntityStore>> hitTargets = ConcurrentHashMap.newKeySet();

        BigArrowRuneShot(long expiresAtMs,
                         List<LoreSocketManager.SocketedModifierRunestone> supportRunestones) {
            this.expiresAtMs = expiresAtMs;
            this.supportRunestones = supportRunestones == null || supportRunestones.isEmpty()
                    ? List.of() : List.copyOf(supportRunestones);
        }
    }

    private static final class RazorstrikeRuneShot {
        final long expiresAtMs;
        final List<LoreSocketManager.SocketedModifierRunestone> supportRunestones;
        final Set<Ref<EntityStore>> hitTargets = ConcurrentHashMap.newKeySet();

        RazorstrikeRuneShot(long expiresAtMs,
                            List<LoreSocketManager.SocketedModifierRunestone> supportRunestones) {
            this.expiresAtMs = expiresAtMs;
            this.supportRunestones = supportRunestones == null || supportRunestones.isEmpty()
                    ? List.of() : List.copyOf(supportRunestones);
        }
    }

    private static final class VolleyHomingArrow {
        final Ref<EntityStore> attackerRef;
        final Vector3d startPosition;
        final Vector3d orbitOffset;
        float damage;
        final long createdAtMs;
        final Set<Ref<EntityStore>> hitTargets = new HashSet<>();
        VolleyHomingArrowEntity visualEntity;
        Vector3d position;
        Ref<EntityStore> sourceTargetRef;
        Ref<EntityStore> targetRef;
        Ref<EntityStore> projectileRef;
        int bouncesRemaining;
        final int forksRemaining;

        VolleyHomingArrow(Ref<EntityStore> attackerRef,
                          Vector3d startPosition,
                          Vector3d orbitOffset,
                          float damage,
                          long createdAtMs,
                          Ref<EntityStore> sourceTargetRef,
                          int bouncesRemaining,
                          int forksRemaining) {
            this.attackerRef = attackerRef;
            this.startPosition = startPosition;
            this.orbitOffset = orbitOffset;
            this.damage = damage;
            this.createdAtMs = createdAtMs;
            this.sourceTargetRef = sourceTargetRef;
            this.position = new Vector3d(startPosition);
            this.bouncesRemaining = bouncesRemaining;
            this.forksRemaining = forksRemaining;
        }
    }


    public static void activateCoreAbilityRune(Store<EntityStore> store,
                                               Ref<EntityStore> attackerRef,
                                               Ref<EntityStore> targetRef,
                                               String abilityId,
                                               int feedTier,
                                               Map<String, Float> abilityAttributes,
                                               List<LoreSocketManager.SocketedModifierRunestone> supportRunestones) {
        if (store == null || store.isShutdown() || attackerRef == null || !attackerRef.isValid()
                || abilityId == null || abilityId.isBlank()) {
            return;
        }
        Map<String, Float> attributeSnapshot = abilityAttributes == null || abilityAttributes.isEmpty()
                ? Map.of() : Map.copyOf(abilityAttributes);
        List<LoreSocketManager.SocketedModifierRunestone> supportRunestoneSnapshot =
                supportRunestones == null || supportRunestones.isEmpty()
                        ? List.of() : List.copyOf(supportRunestones);
        Runnable cast = () -> activateCoreAbilityRuneOnWorldThread(
                store, attackerRef, targetRef, abilityId, Math.max(0, feedTier),
                attributeSnapshot, supportRunestoneSnapshot);
        if (!LoreWorldTasks.queue(store, cast)) {
            cast.run();
        }
    }

    /** Applies support runestones for abilities whose gameplay is authored in interaction assets. */
    public static void activateCoreAbilityRuneSupport(
            Store<EntityStore> store,
            Ref<EntityStore> attackerRef,
            Ref<EntityStore> targetRef,
            List<LoreSocketManager.SocketedModifierRunestone> supportRunestones,
            String abilityId,
            int feedTier) {
        if (store == null || store.isShutdown() || attackerRef == null || !attackerRef.isValid()) {
            return;
        }
        List<LoreSocketManager.SocketedModifierRunestone> supportSnapshot =
                supportRunestones == null || supportRunestones.isEmpty()
                        ? List.of() : List.copyOf(supportRunestones);
        String normalizedAbility = abilityId == null ? "" : abilityId.trim().toLowerCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_');
        if (normalizedAbility.equals("razorstrike") || normalizedAbility.equals("razor_strike")) {
            RazorstrikeRuneShot shot = new RazorstrikeRuneShot(
                    System.currentTimeMillis() + 3000L, supportSnapshot);
            ACTIVE_RAZORSTRIKE_RUNE_SHOTS.put(attackerRef, shot);
            OMNISLASH_SCHEDULER.schedule(
                    () -> ACTIVE_RAZORSTRIKE_RUNE_SHOTS.remove(attackerRef, shot),
                    3000L, TimeUnit.MILLISECONDS);
        }
        Runnable apply = () -> {
            if (store.isShutdown() || !isEntityAlive(store, attackerRef)) {
                return;
            }
            Ref<EntityStore> validTarget = targetRef != null && targetRef.isValid()
                    && isEntityAlive(store, targetRef) ? targetRef : null;
            applyCoreAbilitySupportRunestones(store, attackerRef, validTarget, supportSnapshot,
                    LoreTrigger.ON_SKILL_USE);
        };
        if (!LoreWorldTasks.queue(store, apply)) {
            apply.run();
        }
    }

    /** Records a normal player hit for an active Volley rune without mutating ECS state in its damage system. */
    public static void recordVolleyRuneHit(Store<EntityStore> store,
                                           Ref<EntityStore> attackerRef,
                                           Ref<EntityStore> targetRef) {
        if (store == null || store.isShutdown() || attackerRef == null || targetRef == null
                || !attackerRef.isValid() || !targetRef.isValid()) {
            return;
        }
        LoreWorldTasks.queue(store, () -> captureVolleyRuneArrow(store, attackerRef, targetRef));
    }

    private static void activateCoreAbilityRuneOnWorldThread(Store<EntityStore> store,
                                                              Ref<EntityStore> attackerRef,
                                                              Ref<EntityStore> targetRef,
                                                              String abilityId,
                                                              int feedTier,
                                                              Map<String, Float> abilityAttributes,
                                                              List<LoreSocketManager.SocketedModifierRunestone> supportRunestones) {
        if (store == null || store.isShutdown() || attackerRef == null || !isEntityAlive(store, attackerRef)) {
            return;
        }
        Player player = store.getComponent(attackerRef, Player.getComponentType());
        if (player == null) {
            return;
        }
        if (targetRef != null && (!targetRef.isValid() || !isEntityAlive(store, targetRef))) {
            targetRef = null;
        }
        String normalizedAbility = abilityId.trim().toLowerCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_');
        applyCoreAbilitySupportRunestones(store, attackerRef, targetRef, supportRunestones,
                LoreTrigger.ON_SKILL_USE);
        switch (normalizedAbility) {
            case "whirlwind" -> castWhirlwindRune(store, attackerRef, feedTier, supportRunestones);
            case "vortexstrike", "vortex_strike" ->
                    castVortexstrikeRune(store, attackerRef, feedTier, supportRunestones, false);
            case "vortexstrike_push", "vortex_strike_push" ->
                    castVortexstrikeRune(store, attackerRef, feedTier, supportRunestones, true);
            case "big_arrow", "bigarrow", "big_arrow_1", "big_arrow_2", "big_arrow_3" ->
                    castBigArrowRune(store, attackerRef, feedTier, supportRunestones);
            case "volley", "volley_3", "volley_4", "volley_5", "volley_6" ->
                    castVolleyRune(store, attackerRef, targetRef, feedTier,
                            parseCoreRuneTier(normalizedAbility, "volley"), abilityAttributes, supportRunestones);
            case "berserk" -> castBerserkRune(store, attackerRef);
            case "mana_font", "manafont", "mana_restore", "mana_font_1", "mana_font_2",
                    "mana_font_3", "mana_font_4" -> castManaFontRune(store, attackerRef, feedTier,
                    parseCoreRuneTier(normalizedAbility, "mana_font"));
            default -> LoreDebug.logKv("coreRune.skip", "reason", "unknownAbility", "ability", abilityId);
        }
    }

    private static int parseCoreRuneTier(String abilityId, String baseName) {
        if (abilityId == null || baseName == null || !abilityId.startsWith(baseName + "_")) {
            return 0;
        }
        try {
            return Integer.parseInt(abilityId.substring(baseName.length() + 1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static void castWhirlwindRune(Store<EntityStore> store,
                                         Ref<EntityStore> attackerRef,
                                         int feedTier,
                                         List<LoreSocketManager.SocketedModifierRunestone> supportRunestones) {
        int pulses = 6;
        float perPulse = resolveCoreRuneWeaponDamage(store, attackerRef) * 0.22f;
        double radius = Math.min(6.0d, 3.5d + feedTier * 0.2d);
        for (int pulse = 0; pulse < pulses; pulse++) {
            final int hitIndex = pulse;
            OMNISLASH_SCHEDULER.schedule(() -> LoreWorldTasks.queue(store, () -> {
                if (!isEntityAlive(store, attackerRef)) {
                    return;
                }
                Vector3d center = LoreTargetingUtils.getPosition(store, attackerRef);
                if (center == null) {
                    return;
                }
                List<Ref<EntityStore>> targets = LoreTargetingUtils.collectTargetsAtPosition(
                        store, attackerRef, center, radius, radius * radius,
                        "core.whirlwind", DEBUG_LORE_PROCS, CORE_RUNE_MAX_TARGETS, CORE_RUNE_MAX_NPC_CHECKS);
                for (Ref<EntityStore> target : targets) {
                    if (target != null && !target.equals(attackerRef) && isEntityAlive(store, target)) {
                        applyCoreRuneDamage(store, attackerRef, target, perPulse, feedTier, supportRunestones);
                    }
                }
                LoreAnimationUtils.playSwingAnimation(store, attackerRef, hitIndex);
                LoreVisuals.queueSignatureParticles(store, center, (float) radius, SIGNATURE_VORTEXSTRIKE_PARTICLE_IDS);
            }), pulse * 425L, TimeUnit.MILLISECONDS);
        }
    }

    private static void castVortexstrikeRune(Store<EntityStore> store,
                                             Ref<EntityStore> attackerRef,
                                             int feedTier,
                                             List<LoreSocketManager.SocketedModifierRunestone> supportRunestones,
                                             boolean addPushPhase) {
        Vector3d center = LoreTargetingUtils.getPosition(store, attackerRef);
        if (center == null) {
            return;
        }
        List<Ref<EntityStore>> targets = LoreTargetingUtils.collectTargetsAtPosition(
                store, attackerRef, center,
                CORE_VORTEXSTRIKE_PULL_RADIUS, CORE_VORTEXSTRIKE_PULL_RADIUS * CORE_VORTEXSTRIKE_PULL_RADIUS,
                "core.vortexstrike", DEBUG_LORE_PROCS, CORE_RUNE_MAX_TARGETS, CORE_RUNE_MAX_NPC_CHECKS);
        List<VortexstrikePulledTarget> pulledTargets = new ArrayList<>();
        float phaseDamage = resolveCoreRuneWeaponDamage(store, attackerRef)
                * CORE_VORTEXSTRIKE_PHASE_DAMAGE_MULTIPLIER;
        for (Ref<EntityStore> target : targets) {
            if (target == null || target.equals(attackerRef) || !isEntityAlive(store, target)) {
                continue;
            }
            Vector3d targetPosition = LoreTargetingUtils.getPosition(store, target);
            if (targetPosition == null) {
                continue;
            }
            double distance = Math.sqrt(center.distanceSquared(targetPosition));
            applyCoreAbilitySupportRunestones(store, attackerRef, target,
                    supportRunestones, LoreTrigger.ON_HIT);
            pulledTargets.add(new VortexstrikePulledTarget(target, distance));
        }
        if (pulledTargets.isEmpty()) {
            return;
        }

        if (!addPushPhase) {
            return;
        }

        List<VortexstrikePulledTarget> capturedTargets = List.copyOf(pulledTargets);
        OMNISLASH_SCHEDULER.schedule(() -> LoreWorldTasks.queue(store, () -> {
            if (store.isShutdown() || !isEntityAlive(store, attackerRef)) {
                return;
            }
            Vector3d pushCenter = LoreTargetingUtils.getPosition(store, attackerRef);
            if (pushCenter == null) {
                return;
            }
            for (VortexstrikePulledTarget pulledTarget : capturedTargets) {
                Ref<EntityStore> target = pulledTarget.targetRef();
                if (target == null || target.equals(attackerRef) || !isEntityAlive(store, target)) {
                    continue;
                }
                Vector3d targetPosition = LoreTargetingUtils.getPosition(store, target);
                if (targetPosition == null) {
                    continue;
                }
                Vector3d pushDirection = new Vector3d(targetPosition).sub(pushCenter);
                pushDirection.y = 0.0d;
                if (pushDirection.lengthSquared() < 1.0e-5d) {
                    pushDirection.set(1.0d, 0.0d, 0.0d);
                }
                pushDirection.normalize();
                // Scale the bonus from zero near the caster up to +100% at the pull radius.
                double distanceScale = Math.max(0.0d, Math.min(1.0d,
                        pulledTarget.pullDistance() / CORE_VORTEXSTRIKE_PULL_RADIUS));
                float pushDamage = phaseDamage * (1.0f
                        + (float) (distanceScale * CORE_VORTEXSTRIKE_MAX_PUSH_BONUS_MULTIPLIER));
                applyCoreRuneDamage(store, attackerRef, target, pushDamage, feedTier, supportRunestones);
                scheduleVortexstrikePushTrail(store, target, pushCenter, pushDirection);
            }
        }), CORE_VORTEXSTRIKE_PUSH_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    /** Counters target gravity briefly so the Vortexstrike IV airborne combo can connect. */
    public static void holdVortexstrikeTargetGravity(Store<EntityStore> store,
                                                      Ref<EntityStore> targetRef,
                                                      long durationMs) {
        if (store == null || store.isShutdown() || targetRef == null || !targetRef.isValid()
                || !isEntityAlive(store, targetRef)) {
            return;
        }
        long duration = Math.max(100L, Math.min(2000L, durationMs));
        VORTEXSTRIKE_AIR_HOLD_UNTIL.merge(targetRef, System.currentTimeMillis() + duration, Math::max);
        if (ACTIVE_VORTEXSTRIKE_AIR_HOLDS.add(targetRef)) {
            scheduleVortexstrikeGravityHoldTick(store, targetRef);
        }
    }

    /** Ends the brief target lift so the final slam can send the enemy back down. */
    public static void releaseVortexstrikeTargetGravity(Ref<EntityStore> targetRef) {
        if (targetRef == null) {
            return;
        }
        VORTEXSTRIKE_AIR_HOLD_UNTIL.remove(targetRef);
        ACTIVE_VORTEXSTRIKE_AIR_HOLDS.remove(targetRef);
    }

    private static void scheduleVortexstrikeGravityHoldTick(Store<EntityStore> store,
                                                             Ref<EntityStore> targetRef) {
        OMNISLASH_SCHEDULER.schedule(() -> {
            boolean queued = LoreWorldTasks.queue(store, () -> {
                Long holdUntil = VORTEXSTRIKE_AIR_HOLD_UNTIL.get(targetRef);
                if (store.isShutdown() || !targetRef.isValid()
                        || !isEntityAlive(store, targetRef) || holdUntil == null
                        || holdUntil <= System.currentTimeMillis()) {
                    VORTEXSTRIKE_AIR_HOLD_UNTIL.remove(targetRef);
                    ACTIVE_VORTEXSTRIKE_AIR_HOLDS.remove(targetRef);
                    return;
                }
                Velocity velocity = store.ensureAndGetComponent(targetRef, Velocity.getComponentType());
                if (velocity != null) {
                    velocity.setY(0.0d);
                    velocity.setClient(velocity.getX(), 0.0d, velocity.getZ());
                }
                scheduleVortexstrikeGravityHoldTick(store, targetRef);
            });
            if (!queued) {
                VORTEXSTRIKE_AIR_HOLD_UNTIL.remove(targetRef);
                ACTIVE_VORTEXSTRIKE_AIR_HOLDS.remove(targetRef);
            }
        }, 40L, TimeUnit.MILLISECONDS);
    }

    /** Leaves the same ground line used by Razorstrike along each target's actual push movement. */
    private static void scheduleVortexstrikePushTrail(Store<EntityStore> store,
                                                       Ref<EntityStore> targetRef,
                                                       Vector3d pushCenter,
                                                       Vector3d fallbackDirection) {
        if (store == null || targetRef == null || pushCenter == null || fallbackDirection == null) {
            return;
        }
        Vector3d origin = new Vector3d(pushCenter);
        Vector3d fallback = new Vector3d(fallbackDirection).normalize();
        int samples = (int) Math.ceil(
                CORE_VORTEXSTRIKE_PUSH_TRAIL_DURATION_MS / (double) CORE_VORTEXSTRIKE_PUSH_TRAIL_INTERVAL_MS);
        for (int sample = 0; sample <= samples; sample++) {
            long delayMs = Math.min(CORE_VORTEXSTRIKE_PUSH_TRAIL_DURATION_MS,
                    sample * CORE_VORTEXSTRIKE_PUSH_TRAIL_INTERVAL_MS);
            OMNISLASH_SCHEDULER.schedule(() -> LoreWorldTasks.queue(store, () -> {
                if (store.isShutdown() || !isEntityAlive(store, targetRef)) {
                    return;
                }
                Vector3d trailPosition = LoreTargetingUtils.getPosition(store, targetRef);
                if (trailPosition == null) {
                    return;
                }
                Vector3d direction = new Vector3d(trailPosition).sub(origin);
                direction.y = 0.0d;
                if (direction.lengthSquared() < 1.0e-5d) {
                    direction.set(fallback);
                } else {
                    direction.normalize();
                }
                trailPosition.add(0.0d, 0.12d, 0.0d);
                LoreVisuals.spawnRotatedScaledParticle(store, CORE_VORTEXSTRIKE_PUSH_TRAIL_PARTICLE,
                        trailPosition, Rotation3f.lookAt(direction), CORE_VORTEXSTRIKE_PUSH_TRAIL_SCALE);
            }), delayMs, TimeUnit.MILLISECONDS);
        }
    }

    private static void castBigArrowRune(Store<EntityStore> store,
                                         Ref<EntityStore> attackerRef,
                                         int feedTier,
                                         List<LoreSocketManager.SocketedModifierRunestone> supportRunestones) {
        TransformComponent transform = store.getComponent(attackerRef, TransformComponent.getComponentType());
        Transform look = null;
        try {
            look = TargetUtil.getLook(attackerRef, store);
        } catch (Throwable ignored) {
            // Fall back to the player's transform and head rotation below.
        }
        Vector3d start = look == null || look.getPosition() == null
                ? LoreTargetingUtils.getPosition(store, attackerRef)
                : new Vector3d(look.getPosition());
        Vector3d direction = look == null || look.getDirection() == null
                ? resolvePlayerLookDirection(store, attackerRef, transform)
                : new Vector3d(look.getDirection());
        if (direction != null && direction.lengthSquared() > 1.0e-6d) {
            direction.normalize();
        } else {
            direction = null;
        }
        if (start == null || direction == null) {
            return;
        }
        double distance = 24.0d;
        BigArrowRuneShot shot = new BigArrowRuneShot(
                System.currentTimeMillis() + 4500L, supportRunestones);
        ACTIVE_BIG_ARROW_RUNE_SHOTS.put(attackerRef, shot);
        OMNISLASH_SCHEDULER.schedule(
                () -> ACTIVE_BIG_ARROW_RUNE_SHOTS.remove(attackerRef, shot),
                4500L, TimeUnit.MILLISECONDS);
        scheduleBigArrowTrail(store, start, direction, distance, feedTier);
        scheduleBigArrowImplosion(store, attackerRef, start, direction, distance, feedTier, supportRunestones);
    }

    public static void handleCoreRuneProjectileHit(Store<EntityStore> store,
                                                   Ref<EntityStore> attackerRef,
                                                   Ref<EntityStore> projectileRef,
                                                   Ref<EntityStore> targetRef,
                                                   String ability) {
        if (store == null || store.isShutdown()) {
            return;
        }
        String normalizedAbility = ability == null ? "" : ability.trim().toLowerCase(Locale.ROOT);
        if (!normalizedAbility.equals("volley")
                && (attackerRef == null || !attackerRef.isValid())) {
            return;
        }
        LoreWorldTasks.queue(store, () -> {
            if (normalizedAbility.equals("big_arrow") || normalizedAbility.equals("bigarrow")) {
                applyBigArrowProjectileHit(store, attackerRef, targetRef);
            } else if (normalizedAbility.equals("volley")) {
                applyVolleyProjectileHit(store, attackerRef, projectileRef, targetRef);
            } else if (normalizedAbility.equals("octaslash")) {
                applyOctaslashSupportRunestonesOnHit(store, attackerRef, targetRef, null, null);
            } else if (normalizedAbility.equals("razorstrike") || normalizedAbility.equals("razor_strike")) {
                applyRazorstrikeRuneProjectileHit(store, attackerRef, targetRef);
            }
        });
    }

    private static void applyRazorstrikeRuneProjectileHit(Store<EntityStore> store,
                                                          Ref<EntityStore> attackerRef,
                                                          Ref<EntityStore> targetRef) {
        RazorstrikeRuneShot shot = ACTIVE_RAZORSTRIKE_RUNE_SHOTS.get(attackerRef);
        if (shot == null) {
            RazorstrikeRuneShot fallback = new RazorstrikeRuneShot(
                    System.currentTimeMillis() + 3000L, List.of());
            RazorstrikeRuneShot existing = ACTIVE_RAZORSTRIKE_RUNE_SHOTS.putIfAbsent(attackerRef, fallback);
            shot = existing == null ? fallback : existing;
            if (existing == null) {
                OMNISLASH_SCHEDULER.schedule(
                        () -> ACTIVE_RAZORSTRIKE_RUNE_SHOTS.remove(attackerRef, fallback),
                        3000L, TimeUnit.MILLISECONDS);
            }
        }
        if (System.currentTimeMillis() >= shot.expiresAtMs) {
            ACTIVE_RAZORSTRIKE_RUNE_SHOTS.remove(attackerRef, shot);
            return;
        }
        if (targetRef == null || !targetRef.isValid() || targetRef.equals(attackerRef)
                || !isEntityAlive(store, targetRef) || !shot.hitTargets.add(targetRef)) {
            return;
        }
        applyCoreAbilitySupportRunestones(store, attackerRef, targetRef,
                shot.supportRunestones, LoreTrigger.ON_HIT);
    }

    public static void handleCoreRuneProjectileMiss(Store<EntityStore> store,
                                                    Ref<EntityStore> attackerRef,
                                                    Ref<EntityStore> projectileRef,
                                                    String ability) {
        if (store == null || store.isShutdown()
                || ability == null || !ability.trim().equalsIgnoreCase("Volley")) {
            return;
        }
        LoreWorldTasks.queue(store,
                () -> retryVolleyProjectileAfterMiss(store, attackerRef, projectileRef));
    }

    private static void applyBigArrowProjectileHit(Store<EntityStore> store,
                                                   Ref<EntityStore> attackerRef,
                                                   Ref<EntityStore> targetRef) {
        BigArrowRuneShot shot = ACTIVE_BIG_ARROW_RUNE_SHOTS.get(attackerRef);
        if (shot == null) {
            return;
        }
        if (System.currentTimeMillis() >= shot.expiresAtMs) {
            ACTIVE_BIG_ARROW_RUNE_SHOTS.remove(attackerRef, shot);
            return;
        }
        if (targetRef == null || !targetRef.isValid() || !isEntityAlive(store, targetRef)
                || !shot.hitTargets.add(targetRef)) {
            return;
        }
        applyCoreAbilitySupportRunestones(store, attackerRef, targetRef,
                shot.supportRunestones, LoreTrigger.ON_HIT);
    }

    private static void applyVolleyProjectileHit(Store<EntityStore> store,
                                                 Ref<EntityStore> attackerRef,
                                                 Ref<EntityStore> projectileRef,
                                                 Ref<EntityStore> targetRef) {
        VolleyHomingArrow trackedArrow = projectileRef == null
                ? null : ACTIVE_VOLLEY_PROJECTILE_ARROWS.get(projectileRef);
        if (targetRef == null && trackedArrow != null) {
            targetRef = trackedArrow.targetRef;
        }
        Ref<EntityStore> resolvedAttackerRef = trackedArrow == null
                ? attackerRef : trackedArrow.attackerRef;
        VolleyRuneBuff buff = resolvedAttackerRef == null
                ? null : ACTIVE_VOLLEY_RUNE_BUFFS.get(resolvedAttackerRef);
        if (buff == null || !buff.launched || targetRef == null || !targetRef.isValid()
                || !isEntityAlive(store, targetRef)) {
            return;
        }
        if (store.getComponent(targetRef, Player.getComponentType()) != null) {
            retryVolleyProjectileAfterMiss(store, resolvedAttackerRef, projectileRef);
            return;
        }
        VolleyHomingArrow arrow = findVolleyProjectileArrow(buff, projectileRef, targetRef);
        if (arrow == null) {
            return;
        }
        Ref<EntityStore> oldProjectileRef = arrow.projectileRef;
        forgetVolleyProjectile(arrow);
        removeVolleyProjectileEntity(store, oldProjectileRef);
        if (!arrow.hitTargets.add(targetRef)) {
            finishVolleyArrow(buff, arrow);
            return;
        }

        Vector3d impactPosition = LoreTargetingUtils.getPosition(store, targetRef);
        if (impactPosition == null) {
            finishVolleyArrow(buff, arrow);
            return;
        }
        impactPosition.add(0.0d, 0.9d, 0.0d);
        LoreDamageUtils.applyLoreWeaponAbilityDamage(
                store, buff.attackerRef, targetRef, arrow.damage, buff.feedTier);
        queueAbilityHitWeaponLores(store, buff.attackerRef, targetRef);
        applyVolleySupportRunestones(store, buff, targetRef);
        String particle = LoreVisuals.resolveParticleEffectId(SIGNATURE_BIG_ARROW_PARTICLE_IDS);
        if (particle != null) {
            LoreVisuals.spawnScaledParticle(store, particle, impactPosition, 0.7f);
        }

        if (arrow.forksRemaining >= 2) {
            forkVolleyArrow(store, buff, arrow, targetRef, impactPosition);
        }

        arrow.bouncesRemaining--;
        Ref<EntityStore> nextTarget = arrow.bouncesRemaining < 0
                ? null : chooseVolleyTarget(store, buff, arrow, impactPosition);
        if (nextTarget == null) {
            if (arrow.bouncesRemaining >= 0) {
                System.out.println("[SocketReforge] Volley rebound stopped: no alternate NPC within "
                        + (int) VOLLEY_ARROW_BOUNCE_RADIUS + " blocks.");
            }
            finishVolleyArrow(buff, arrow);
            return;
        }
        System.out.println("[SocketReforge] Volley rebound acquired "
                + LoreTargetingUtils.resolveDebugEntityLabel(store, nextTarget)
                + "; remaining=" + arrow.bouncesRemaining);
        arrow.damage *= buff.splitChildDamageMultiplier;
        arrow.targetRef = nextTarget;
        arrow.position = new Vector3d(impactPosition);
        if (!launchVolleyArrowProjectile(store, buff, arrow, nextTarget, arrow.position)) {
            finishVolleyArrow(buff, arrow);
        }
    }

    private static void applyVolleySupportRunestones(Store<EntityStore> store,
                                                     VolleyRuneBuff buff,
                                                     Ref<EntityStore> targetRef) {
        if (buff == null) {
            return;
        }
        applyCoreAbilitySupportRunestones(store, buff.attackerRef, targetRef,
                buff.supportRunestones, LoreTrigger.ON_HIT);
    }

    private static int forkVolleyArrow(Store<EntityStore> store,
                                       VolleyRuneBuff buff,
                                       VolleyHomingArrow parentArrow,
                                       Ref<EntityStore> hitTargetRef,
                                       Vector3d impactPosition) {
        if (store == null || buff == null || parentArrow == null || impactPosition == null) {
            return 0;
        }
        int childCount = Math.min(VOLLEY_ARROW_MAX_FORK_COUNT, parentArrow.forksRemaining);
        Set<Ref<EntityStore>> reservedTargets = new HashSet<>(parentArrow.hitTargets);
        long now = System.currentTimeMillis();
        int launchedChildren = 0;
        for (int i = 0; i < childCount; i++) {
            VolleyHomingArrow child = new VolleyHomingArrow(
                    parentArrow.attackerRef,
                    impactPosition,
                    new Vector3d(),
                    parentArrow.damage * buff.splitChildDamageMultiplier,
                    now,
                    hitTargetRef,
                    parentArrow.bouncesRemaining,
                    1);
            child.hitTargets.addAll(reservedTargets);
            Ref<EntityStore> childTargetRef = chooseVolleyTarget(store, buff, child, impactPosition);
            if (childTargetRef == null) {
                continue;
            }
            child.targetRef = childTargetRef;
            child.position = new Vector3d(impactPosition);
            buff.arrows.add(child);
            if (launchVolleyArrowProjectile(store, buff, child, childTargetRef, impactPosition)) {
                reservedTargets.add(childTargetRef);
                launchedChildren++;
            } else {
                buff.arrows.remove(child);
            }
        }
        System.out.println("[SocketReforge] Volley fork split: requested=" + childCount
                + ", launched=" + launchedChildren + ", parentReboundsRemaining="
                + parentArrow.bouncesRemaining + ".");
        return launchedChildren;
    }

    private static VolleyHomingArrow findVolleyProjectileArrow(VolleyRuneBuff buff,
                                                               Ref<EntityStore> projectileRef,
                                                               Ref<EntityStore> targetRef) {
        if (buff == null) {
            return null;
        }
        if (projectileRef != null) {
            VolleyHomingArrow arrow = ACTIVE_VOLLEY_PROJECTILE_ARROWS.remove(projectileRef);
            if (arrow != null) {
                return arrow;
            }
        }
        for (VolleyHomingArrow arrow : buff.arrows) {
            if (arrow.projectileRef != null && arrow.targetRef != null
                    && arrow.targetRef.equals(targetRef)) {
                return arrow;
            }
        }
        return null;
    }

    private static void retryVolleyProjectileAfterMiss(Store<EntityStore> store,
                                                       Ref<EntityStore> attackerRef,
                                                       Ref<EntityStore> projectileRef) {
        VolleyHomingArrow arrow = projectileRef == null
                ? null : ACTIVE_VOLLEY_PROJECTILE_ARROWS.remove(projectileRef);
        Ref<EntityStore> resolvedAttackerRef = arrow == null ? attackerRef : arrow.attackerRef;
        VolleyRuneBuff buff = resolvedAttackerRef == null
                ? null : ACTIVE_VOLLEY_RUNE_BUFFS.get(resolvedAttackerRef);
        if (buff == null || !buff.launched) {
            return;
        }
        if (arrow == null) {
            for (VolleyHomingArrow candidate : buff.arrows) {
                if (candidate.projectileRef != null) {
                    arrow = candidate;
                    break;
                }
            }
        }
        if (arrow == null) {
            return;
        }
        Ref<EntityStore> actualProjectileRef = arrow.projectileRef;
        forgetVolleyProjectile(arrow);
        arrow.bouncesRemaining--;
        Vector3d currentPosition = actualProjectileRef == null
                ? new Vector3d(arrow.position)
                : LoreTargetingUtils.getPosition(store, actualProjectileRef);
        if (currentPosition == null) {
            currentPosition = new Vector3d(arrow.position);
        }
        removeVolleyProjectileEntity(store, actualProjectileRef);
        Ref<EntityStore> nextTarget = arrow.bouncesRemaining < 0
                ? null : chooseVolleyTarget(store, buff, arrow, currentPosition);
        if (nextTarget == null) {
            finishVolleyArrow(buff, arrow);
            return;
        }
        arrow.targetRef = nextTarget;
        arrow.position = new Vector3d(currentPosition);
        if (!launchVolleyArrowProjectile(store, buff, arrow, nextTarget, currentPosition)) {
            finishVolleyArrow(buff, arrow);
        }
    }

    private static void forgetVolleyProjectile(VolleyHomingArrow arrow) {
        if (arrow == null || arrow.projectileRef == null) {
            return;
        }
        ACTIVE_VOLLEY_PROJECTILE_ARROWS.remove(arrow.projectileRef, arrow);
        arrow.projectileRef = null;
    }

    private static void removeVolleyProjectileEntity(Store<EntityStore> store,
                                                      Ref<EntityStore> projectileRef) {
        if (store == null || store.isShutdown() || projectileRef == null || !projectileRef.isValid()) {
            return;
        }
        try {
            store.removeEntity(projectileRef, RemoveReason.REMOVE);
        } catch (Throwable ignored) {
            // Projectile collision callbacks can already have removed the entity.
        }
    }

    private static void finishVolleyArrow(VolleyRuneBuff buff, VolleyHomingArrow arrow) {
        if (buff == null || arrow == null) {
            return;
        }
        Ref<EntityStore> projectileRef = arrow.projectileRef;
        forgetVolleyProjectile(arrow);
        removeVolleyProjectileEntity(buff.store, projectileRef);
        removeVolleyArrowVisual(arrow);
        buff.arrows.remove(arrow);
        if (buff.arrows.isEmpty() && buff.launched) {
            ACTIVE_VOLLEY_RUNE_BUFFS.remove(buff.attackerRef, buff);
        }
    }

    private static boolean launchVolleyArrowProjectile(Store<EntityStore> store,
                                                       VolleyRuneBuff buff,
                                                       VolleyHomingArrow arrow,
                                                       Ref<EntityStore> targetRef,
                                                       Vector3d startPosition) {
        if (store == null || buff == null || arrow == null || targetRef == null
                || !targetRef.isValid() || startPosition == null) {
            return false;
        }
        TransformComponent targetTransform = store.getComponent(
                targetRef, TransformComponent.getComponentType());
        if (targetTransform == null || targetTransform.getPosition() == null) {
            return false;
        }
        Vector3d targetPosition = new Vector3d(targetTransform.getPosition()).add(0.0d, 0.9d, 0.0d);
        Vector3d direction = targetPosition.sub(new Vector3d(startPosition));
        if (direction.lengthSquared() <= 1.0e-6d) {
            return false;
        }
        direction.normalize();
        ProjectileConfig config = resolveProjectileConfig(
                "Projectile_Config_Core_Rune_Volley_Arrow_Aim_Center");
        ProjectileModule module = ProjectileModule.get();
        if (config == null || module == null) {
            System.err.println("[SocketReforge] Volley arrow could not launch: projectile config or module is missing"
                    + " (config=" + (config != null) + ", module=" + (module != null) + ")");
            return false;
        }
        double configuredLaunchForce = config.getLaunchForce();
        float launchVelocityScale = configuredLaunchForce > 0.01d
                ? (float) (VOLLEY_ARROW_SPEED * buff.projectileSpeedMultiplier / configuredLaunchForce)
                : 1.0f;

        boolean[] spawnQueued = {false};
        @SuppressWarnings("unchecked")
        Ref<EntityStore>[] spawnedProjectile = new Ref[1];
        int[] attempts = {0};
        Throwable[] spawnError = {null};
        try {
            // The player ref has no parent table of its own, so the ref-scoped overload can
            // legitimately visit zero chunks. Use the store-wide traversal to obtain its
            // command buffer, while still passing the player as projectile owner.
            store.forEachChunk(Query.any(), (chunk, commandBuffer) -> {
                if (spawnQueued[0] || commandBuffer == null) {
                    return;
                }
                attempts[0]++;
                try {
                    Ref<EntityStore> projectileRef = module.spawnProjectile(
                            buff.attackerRef, commandBuffer, config,
                            new Vector3d(startPosition), new Vector3d(direction), 1.0f, launchVelocityScale);
                    if (projectileRef != null) {
                        // CommandBuffer.addEntity returns a pending Ref. It is not valid until
                        // the store applies the queued spawn after this traversal completes.
                        spawnedProjectile[0] = projectileRef;
                        spawnQueued[0] = true;
                    }
                } catch (Throwable error) {
                    spawnError[0] = error;
                }
            });
        } catch (Throwable error) {
            spawnError[0] = error;
        }
        Ref<EntityStore> projectileRef = spawnedProjectile[0];
        if (projectileRef == null) {
            String reason = spawnError[0] == null ? "no projectile returned"
                    : spawnError[0].getClass().getSimpleName() + ": " + spawnError[0].getMessage();
            System.err.println("[SocketReforge] Volley arrow spawn failed: attempts=" + attempts[0]
                    + ", reason=" + reason);
            return false;
        }
        arrow.projectileRef = projectileRef;
        arrow.targetRef = targetRef;
        arrow.position = new Vector3d(startPosition);
        ACTIVE_VOLLEY_PROJECTILE_ARROWS.put(projectileRef, arrow);
        removeVolleyArrowVisual(arrow);
        return true;
    }

    /** Emits bow projectile sparks at timed positions along the arrow's expected flight path. */
    private static void scheduleBigArrowTrail(Store<EntityStore> store,
                                               Vector3d playerPosition,
                                               Vector3d direction,
                                               double distance,
                                               int feedTier) {
        if (store == null || playerPosition == null || direction == null || distance <= 0.0d) {
            return;
        }
        String particleId = LoreVisuals.resolveParticleEffectId(
                "Lore_Big_Arrow_Trail", "LoreRunes/Core/Lore_Big_Arrow_Trail");
        if (particleId == null) {
            return;
        }

        double speed = resolveBigArrowSpeed();
        long travelTimeMs = Math.max(1L, (long) Math.ceil(distance / speed * 1000.0d));
        int samples = Math.max(2, Math.min(32, (int) Math.ceil(distance / 0.8d) + 1));
        float scale = 1.2f + Math.min(0.8f, feedTier * 0.1f);
        Rotation3f particleRotation = Rotation3f.lookAt(new Vector3d(direction));
        for (int sample = 0; sample < samples; sample++) {
            double progress = sample / (double) (samples - 1);
            long delayMs = Math.round(travelTimeMs * progress);
            Vector3d particlePosition = new Vector3d(playerPosition)
                    .fma(distance * progress, direction);
            OMNISLASH_SCHEDULER.schedule(
                    () -> LoreWorldTasks.queue(store, () -> {
                        if (!store.isShutdown()) {
                            LoreVisuals.spawnRotatedScaledParticle(
                                    store, particleId, particlePosition, particleRotation, scale);
                        }
                    }),
                    delayMs,
                    TimeUnit.MILLISECONDS);
        }
    }

    /** Resolves the arrow's endpoint after flight, then pulls and damages nearby enemies. */
    private static void scheduleBigArrowImplosion(Store<EntityStore> store,
                                                   Ref<EntityStore> attackerRef,
                                                   Vector3d start,
                                                   Vector3d direction,
                                                   double distance,
                                                   int feedTier,
                                                   List<LoreSocketManager.SocketedModifierRunestone> supportRunestones) {
        if (store == null || attackerRef == null || start == null || direction == null || distance <= 0.0d) {
            return;
        }
        double speed = resolveBigArrowSpeed();
        long impactDelayMs = Math.max(1L, (long) Math.ceil(distance / speed * 1000.0d));
        Vector3d impactPosition = new Vector3d(start).fma(distance, new Vector3d(direction).normalize());
        OMNISLASH_SCHEDULER.schedule(
                () -> LoreWorldTasks.queue(store, () -> triggerBigArrowImplosion(
                        store, attackerRef, impactPosition, feedTier, supportRunestones)),
                impactDelayMs,
                TimeUnit.MILLISECONDS);
    }

    private static void triggerBigArrowImplosion(Store<EntityStore> store,
                                                 Ref<EntityStore> attackerRef,
                                                 Vector3d center,
                                                 int feedTier,
                                                 List<LoreSocketManager.SocketedModifierRunestone> supportRunestones) {
        if (store == null || store.isShutdown() || attackerRef == null || !isEntityAlive(store, attackerRef)
                || center == null) {
            return;
        }
        String particleId = LoreVisuals.resolveParticleEffectId(BIG_ARROW_IMPLOSION_PARTICLE_ID);
        if (particleId != null) {
            LoreVisuals.spawnScaledParticle(store, particleId, center,
                    1.0f + Math.min(0.6f, Math.max(0, feedTier) * 0.1f));
        }
        LoreVisuals.playSignatureSound(
                store.getComponent(attackerRef, Player.getComponentType()),
                "SFX_Staff_Flame_Fireball_Impact");

        double radius = BIG_ARROW_IMPLOSION_RADIUS;
        double radiusSquared = radius * radius;
        float damage = resolveCoreRuneWeaponDamage(store, attackerRef) * 0.25f;
        List<Ref<EntityStore>> targets = LoreTargetingUtils.collectTargetsAtPosition(
                store, attackerRef, center, radius, radiusSquared,
                "core.bigArrow.implosion", DEBUG_LORE_PROCS,
                CORE_RUNE_MAX_TARGETS, CORE_RUNE_MAX_NPC_CHECKS);
        for (Ref<EntityStore> target : targets) {
            if (target == null || target.equals(attackerRef) || !isEntityAlive(store, target)) {
                continue;
            }
            applyCoreRuneDamage(store, attackerRef, target, damage, feedTier, supportRunestones);
            applyCoreRuneVelocity(store, target, center, -7.0d, 1.0d);
        }
    }

    private static double resolveBigArrowSpeed() {
        ProjectileConfig projectileConfig = resolveProjectileConfig("Projectile_Config_Core_Rune_Big_Arrow");
        double speed = projectileConfig == null ? 0.0d : projectileConfig.getMuzzleVelocity();
        if (speed <= 0.01d && projectileConfig != null) {
            speed = projectileConfig.getLaunchForce();
        }
        return speed <= 0.01d ? 22.5d : speed;
    }

    /** Uses the player's current view direction so core projectile paths match the native launcher aim. */
    private static Vector3d resolvePlayerLookDirection(Store<EntityStore> store,
                                                        Ref<EntityStore> playerRef,
                                                        TransformComponent transform) {
        if (store == null || playerRef == null) {
            return null;
        }
        try {
            HeadRotation headRotation = store.getComponent(playerRef, HeadRotation.getComponentType());
            Vector3d headDirection = headRotation == null ? null : headRotation.getDirection();
            if (headDirection != null && headDirection.lengthSquared() > 1.0e-6d) {
                return new Vector3d(headDirection).normalize();
            }
        } catch (Throwable ignored) {
            // Fall back to the transform's look orientation when head rotation is unavailable.
        }
        Rotation3f rotation = resolveLookRotation(transform);
        if (rotation == null) {
            return null;
        }
        try {
            Vector3d direction = new Transform(new Vector3d(), rotation).getDirection();
            if (direction != null && direction.lengthSquared() > 1.0e-6d) {
                return direction.normalize();
            }
        } catch (Throwable ignored) {
            // Retain the horizontal yaw fallback below.
        }
        double yawRadians = Math.toRadians(rotation.yaw());
        Vector3d direction = new Vector3d(-Math.sin(yawRadians), 0.0d, Math.cos(yawRadians));
        return direction.lengthSquared() > 1.0e-6d ? direction.normalize() : null;
    }

    private static void castVolleyRune(Store<EntityStore> store,
                                       Ref<EntityStore> attackerRef,
                                       Ref<EntityStore> targetRef,
                                       int feedTier,
                                       int abilityTier,
                                       Map<String, Float> abilityAttributes,
                                       List<LoreSocketManager.SocketedModifierRunestone> supportRunestones) {
        if (store == null || store.isShutdown() || attackerRef == null || !attackerRef.isValid()) {
            return;
        }
        VolleyRuneBuff previous = ACTIVE_VOLLEY_RUNE_BUFFS.remove(attackerRef);
        if (previous != null) {
            removeVolleyArrowVisuals(previous);
        }
        int arrowCount = abilityTier >= 3
                ? Math.max(3, Math.min(VOLLEY_ARROW_MAX_COUNT, abilityTier))
                : Math.max(3, Math.min(VOLLEY_ARROW_MAX_COUNT, 3 + Math.max(0, feedTier)));
        int bounceCount = abilityTier >= 3
                ? Math.max(0, Math.min(3, arrowCount - 3))
                : Math.max(1, Math.min(3, arrowCount - 3));
        int ricochetModifier = Math.max(0, Math.round(resolveVolleyAttribute(
                abilityAttributes, "Ricochet", 1.0f)) - 1);
        bounceCount = Math.max(0, Math.min(VOLLEY_ARROW_MAX_BOUNCES, bounceCount + ricochetModifier));
        int forkCount = Math.max(1, Math.min(VOLLEY_ARROW_MAX_FORK_COUNT,
                Math.round(resolveVolleyAttribute(abilityAttributes, "Fork", 1.0f))));
        float splitChildDamageMultiplier = clampVolleyAttribute(resolveVolleyAttribute(
                abilityAttributes, "SplitChildDamage", 1.0f), 0.0f, 5.0f);
        float projectileSpeedMultiplier = clampVolleyAttribute(resolveVolleyAttribute(
                abilityAttributes, "Speed", 1.0f), 0.1f, 5.0f);
        long now = System.currentTimeMillis();
        VolleyRuneBuff buff = new VolleyRuneBuff(store, attackerRef, arrowCount, bounceCount,
                forkCount, feedTier, resolveCoreRuneWeaponDamage(store, attackerRef) * 0.25f,
                splitChildDamageMultiplier, projectileSpeedMultiplier, supportRunestones, now);
        ACTIVE_VOLLEY_RUNE_BUFFS.put(attackerRef, buff);
        seedVolleyRuneArrows(store, buff, targetRef);
        System.out.println("[SocketReforge] Volley behavior: arrows=" + arrowCount
                + ", forks=" + forkCount + ", rebounds=" + bounceCount
                + ", splitChildDamage=" + splitChildDamageMultiplier
                + ", speed=" + projectileSpeedMultiplier
                + ", supportRunestones=" + buff.supportRunestones.size());
        OMNISLASH_SCHEDULER.schedule(
                () -> LoreWorldTasks.queue(store, () -> releaseVolleyRuneBuff(buff)),
                VOLLEY_ARROW_GATHER_MS, TimeUnit.MILLISECONDS);
        LoreDebug.logKv("core.volley.activate", "arrows", arrowCount,
                "bounces", bounceCount, "forks", forkCount,
                "splitChildDamage", splitChildDamageMultiplier,
                "speed", projectileSpeedMultiplier,
                "supportRunestones", supportRunestones == null ? 0 : supportRunestones.size(),
                "releaseDelayMs", VOLLEY_ARROW_GATHER_MS);
    }

    private static float resolveVolleyAttribute(Map<String, Float> attributes,
                                                String attribute,
                                                float fallback) {
        if (attributes == null || attribute == null) {
            return fallback;
        }
        Float value = attributes.get(attribute);
        return value == null || !Float.isFinite(value) ? fallback : value;
    }

    private static float clampVolleyAttribute(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void seedVolleyRuneArrows(Store<EntityStore> store,
                                              VolleyRuneBuff buff,
                                              Ref<EntityStore> targetRef) {
        if (store == null || buff == null) {
            return;
        }
        List<Ref<EntityStore>> candidates = new ArrayList<>();
        if (targetRef != null && targetRef.isValid() && !targetRef.equals(buff.attackerRef)
                && store.getComponent(targetRef, Player.getComponentType()) == null
                && isEntityAlive(store, targetRef) && !isEntityDeadOrDying(store, targetRef)) {
            candidates.add(targetRef);
        } else {
            Vector3d center = LoreTargetingUtils.getPosition(store, buff.attackerRef);
            if (center != null) {
                List<Ref<EntityStore>> nearbyTargets = LoreTargetingUtils.collectTargetsAtPosition(
                        store, buff.attackerRef, center, VOLLEY_ARROW_BOUNCE_RADIUS,
                        VOLLEY_ARROW_BOUNCE_RADIUS * VOLLEY_ARROW_BOUNCE_RADIUS,
                        "core.volley.seed", false, CORE_RUNE_MAX_TARGETS, CORE_RUNE_MAX_NPC_CHECKS);
                if (nearbyTargets != null) {
                    candidates.addAll(nearbyTargets);
                }
            }
        }
        List<Ref<EntityStore>> targets = new ArrayList<>();
        for (Ref<EntityStore> target : candidates) {
            if (target != null && target.isValid() && !target.equals(buff.attackerRef)
                    && store.getComponent(target, Player.getComponentType()) == null
                    && isEntityAlive(store, target) && !isEntityDeadOrDying(store, target)) {
                targets.add(target);
            }
        }
        if (targets.isEmpty()) {
            return;
        }
        for (int i = 0; i < buff.maxArrows; i++) {
            captureVolleyRuneArrow(store, buff.attackerRef, targets.get(i % targets.size()));
        }
    }

    static void captureVolleyRuneArrow(Store<EntityStore> store,
                                               Ref<EntityStore> attackerRef,
                                               Ref<EntityStore> targetRef) {
        if (store == null || store.isShutdown() || attackerRef == null || targetRef == null
                || !attackerRef.isValid() || !targetRef.isValid() || attackerRef.equals(targetRef)
                || !isEntityAlive(store, attackerRef) || !isEntityAlive(store, targetRef)) {
            return;
        }
        VolleyRuneBuff buff = ACTIVE_VOLLEY_RUNE_BUFFS.get(attackerRef);
        long now = System.currentTimeMillis();
        if (buff == null || buff.launched || now >= buff.expiresAtMs
                || buff.arrows.size() >= buff.maxArrows) {
            return;
        }
        Vector3d targetPosition = LoreTargetingUtils.getPosition(store, targetRef);
        if (targetPosition == null) {
            return;
        }
        targetPosition.add(0.0d, 0.9d, 0.0d);
        int index = buff.arrows.size();
        int pointCount = Math.max(1, buff.maxArrows);
        double vertical = 1.0d - 2.0d * (index + 0.5d) / pointCount;
        double ringRadius = Math.sqrt(Math.max(0.0d, 1.0d - vertical * vertical));
        double angle = index * 2.399963229728653d;
        Vector3d orbitOffset = new Vector3d(
                Math.cos(angle) * ringRadius * VOLLEY_ARROW_ORBIT_RADIUS,
                vertical * VOLLEY_ARROW_ORBIT_RADIUS,
                Math.sin(angle) * ringRadius * VOLLEY_ARROW_ORBIT_RADIUS);
        VolleyHomingArrow arrow = new VolleyHomingArrow(attackerRef, targetPosition, orbitOffset,
                buff.arrowDamage, now, targetRef, buff.bounceCount, buff.forkCount);
        buff.arrows.add(arrow);
        spawnVolleyArrowVisual(store, buff, arrow);
        scheduleVolleyRuneTick(buff);
        LoreDebug.logKv("core.volley.capture", "arrow", buff.arrows.size(),
                "max", buff.maxArrows,
                "target", LoreTargetingUtils.resolveDebugEntityLabel(store, targetRef));
    }

    private static void spawnVolleyArrowVisual(Store<EntityStore> store,
                                               VolleyRuneBuff buff,
                                               VolleyHomingArrow arrow) {
        if (store == null || buff == null || arrow == null || arrow.startPosition == null) {
            return;
        }
        try {
            EntityStore entityStore = store.getExternalData();
            World world = entityStore == null ? null : entityStore.getWorld();
            if (world == null) {
                return;
            }
            Vector3d facing = LoreTargetingUtils.getPosition(store, buff.attackerRef);
            facing = facing == null
                    ? new Vector3d(0.0d, 0.0d, 1.0d)
                    : new Vector3d(arrow.startPosition).sub(facing);
            Rotation3f rotation = facing.lengthSquared() > 1.0e-6d
                    ? Rotation3f.lookAt(facing.normalize()) : new Rotation3f();
            VolleyHomingArrowEntity visual = new VolleyHomingArrowEntity(world);
            if (!visual.hasModel()) {
                return;
            }
            world.spawnEntity(visual, new Vector3d(arrow.startPosition), rotation);
            arrow.visualEntity = visual;
        } catch (Throwable ignored) {
            // The homing damage must still work if the optional arrow model is unavailable.
        }
    }

    private static void moveVolleyArrowVisual(Store<EntityStore> store,
                                              VolleyHomingArrow arrow,
                                              Vector3d position,
                                              Vector3d direction) {
        if (store == null || arrow == null || arrow.visualEntity == null || position == null) {
            return;
        }
        try {
            Ref<EntityStore> visualRef = arrow.visualEntity.getReference();
            if (visualRef == null || !visualRef.isValid()) {
                return;
            }
            TransformComponent transform = store.getComponent(visualRef, TransformComponent.getComponentType());
            if (transform == null) {
                return;
            }
            transform.teleportPosition(new Vector3d(position));
            if (direction != null && direction.lengthSquared() > 1.0e-6d) {
                transform.teleportRotation(Rotation3f.lookAt(new Vector3d(direction).normalize()));
            }
        } catch (Throwable ignored) {
            // Best-effort visual update; arrow targeting and damage continue independently.
        }
    }

    private static void removeVolleyArrowVisual(VolleyHomingArrow arrow) {
        if (arrow == null || arrow.visualEntity == null) {
            return;
        }
        try {
            if (!arrow.visualEntity.wasRemoved()) {
                arrow.visualEntity.remove();
            }
        } catch (Throwable ignored) {
            // Cleanup is best-effort while a world is unloading.
        }
        arrow.visualEntity = null;
    }

    private static void removeVolleyArrowVisuals(VolleyRuneBuff buff) {
        if (buff == null) {
            return;
        }
        for (VolleyHomingArrow arrow : new ArrayList<>(buff.arrows)) {
            Ref<EntityStore> projectileRef = arrow.projectileRef;
            if (projectileRef != null && projectileRef.isValid() && buff.store != null
                    && !buff.store.isShutdown()) {
                try {
                    buff.store.removeEntity(projectileRef, RemoveReason.REMOVE);
                } catch (Throwable ignored) {
                    // Best-effort cleanup if the projectile was already removed by its hit interaction.
                }
            }
            finishVolleyArrow(buff, arrow);
        }
    }

    private static void releaseVolleyRuneBuff(VolleyRuneBuff buff) {
        if (buff == null || buff.store == null || buff.store.isShutdown()
                || ACTIVE_VOLLEY_RUNE_BUFFS.get(buff.attackerRef) != buff || buff.launched) {
            return;
        }
        if (!isEntityAlive(buff.store, buff.attackerRef)) {
            removeVolleyArrowVisuals(buff);
            ACTIVE_VOLLEY_RUNE_BUFFS.remove(buff.attackerRef, buff);
            return;
        }
        buff.launched = true;
        for (int i = buff.arrows.size() - 1; i >= 0; i--) {
            VolleyHomingArrow arrow = buff.arrows.get(i);
            Ref<EntityStore> target = arrow.sourceTargetRef;
            if (target == null || !isEntityAlive(buff.store, target) || isEntityDeadOrDying(buff.store, target)) {
                target = chooseVolleyTarget(buff.store, buff, arrow, arrow.position);
            }
            arrow.targetRef = target;
            if (target == null) {
                finishVolleyArrow(buff, arrow);
                continue;
            }
            Vector3d launchPosition = new Vector3d(arrow.position);
            if (!launchVolleyArrowProjectile(buff.store, buff, arrow, target, launchPosition)) {
                finishVolleyArrow(buff, arrow);
            }
        }
        if (buff.arrows.isEmpty()) {
            ACTIVE_VOLLEY_RUNE_BUFFS.remove(buff.attackerRef, buff);
            return;
        }
        LoreVisuals.playSignatureSound(
                buff.store.getComponent(buff.attackerRef, Player.getComponentType()),
                "SFX_Bow_T2_Shoot_Local");
        LoreDebug.logKv("core.volley.release", "arrows", buff.arrows.size());
        OMNISLASH_SCHEDULER.schedule(
                () -> LoreWorldTasks.queue(buff.store, () -> expireVolleyRuneProjectiles(buff)),
                VOLLEY_ARROW_MAX_AGE_MS, TimeUnit.MILLISECONDS);
        scheduleVolleyRuneTick(buff);
    }

    private static void expireVolleyRuneProjectiles(VolleyRuneBuff buff) {
        if (buff == null || buff.store == null || buff.store.isShutdown()
                || ACTIVE_VOLLEY_RUNE_BUFFS.get(buff.attackerRef) != buff) {
            return;
        }
        removeVolleyArrowVisuals(buff);
        ACTIVE_VOLLEY_RUNE_BUFFS.remove(buff.attackerRef, buff);
    }

    private static Ref<EntityStore> chooseVolleyTarget(Store<EntityStore> store,
                                                       VolleyRuneBuff buff,
                                                       VolleyHomingArrow arrow,
                                                       Vector3d center) {
        if (store == null || buff == null || arrow == null || center == null) {
            return null;
        }
        List<Ref<EntityStore>> targets = LoreTargetingUtils.collectTargetsAtPosition(
                store, buff.attackerRef, center, VOLLEY_ARROW_BOUNCE_RADIUS,
                VOLLEY_ARROW_BOUNCE_RADIUS * VOLLEY_ARROW_BOUNCE_RADIUS,
                "core.volley.target", false, CORE_RUNE_MAX_TARGETS, CORE_RUNE_MAX_NPC_CHECKS);
        Ref<EntityStore> closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Ref<EntityStore> target : targets) {
            if (target == null || target.equals(buff.attackerRef) || !target.isValid()
                    || store.getComponent(target, Player.getComponentType()) != null
                    || arrow.hitTargets.contains(target) || !isEntityAlive(store, target)
                    || isEntityDeadOrDying(store, target)) {
                continue;
            }
            Vector3d targetPosition = LoreTargetingUtils.getPosition(store, target);
            if (targetPosition == null) {
                continue;
            }
            double distance = targetPosition.distanceSquared(center);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = target;
            }
        }
        if (closest != null) {
            return closest;
        }

        // The NPC spatial index can return an empty list while valid living entities are still
        // present in the ECS store. Scan the living-entity query as a fallback so a bounce can
        // still find a nearby target in that case.
        final Ref<EntityStore>[] fallbackTarget = new Ref[1];
        final double[] fallbackDistance = {Double.MAX_VALUE};
        final int[] inspected = {0};
        store.forEachChunk(AllLegacyLivingEntityTypesQuery.INSTANCE, (chunk, commandBuffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                if (inspected[0]++ >= CORE_RUNE_MAX_NPC_CHECKS) {
                    return false;
                }
                Ref<EntityStore> target = chunk.getReferenceTo(i);
                if (target == null || target.equals(buff.attackerRef)
                        || chunk.getComponent(i, Player.getComponentType()) != null
                        || arrow.hitTargets.contains(target) || !isEntityAlive(store, target)
                        || isEntityDeadOrDying(store, target)) {
                    continue;
                }
                TransformComponent targetTransform = chunk.getComponent(
                        i, TransformComponent.getComponentType());
                if (targetTransform == null || targetTransform.getPosition() == null) {
                    continue;
                }
                Vector3d targetPosition = targetTransform.getPosition();
                double distance = targetPosition.distanceSquared(center);
                if (distance > VOLLEY_ARROW_BOUNCE_RADIUS * VOLLEY_ARROW_BOUNCE_RADIUS
                        || distance >= fallbackDistance[0]) {
                    continue;
                }
                fallbackTarget[0] = target;
                fallbackDistance[0] = distance;
            }
            return true;
        });
        return fallbackTarget[0];
    }

    private static void scheduleVolleyRuneTick(VolleyRuneBuff buff) {
        if (buff == null || buff.store == null || buff.store.isShutdown()
                || buff.tickScheduled || buff.arrows.isEmpty()
                || ACTIVE_VOLLEY_RUNE_BUFFS.get(buff.attackerRef) != buff) {
            return;
        }
        buff.tickScheduled = true;
        OMNISLASH_SCHEDULER.schedule(
                () -> LoreWorldTasks.queue(buff.store, () -> tickVolleyRuneArrows(buff)),
                VOLLEY_ARROW_TICK_MS, TimeUnit.MILLISECONDS);
    }

    private static void tickVolleyRuneArrows(VolleyRuneBuff buff) {
        if (buff == null || buff.store == null || buff.store.isShutdown()
                || ACTIVE_VOLLEY_RUNE_BUFFS.get(buff.attackerRef) != buff) {
            return;
        }
        buff.tickScheduled = false;
        Store<EntityStore> store = buff.store;
        if (!isEntityAlive(store, buff.attackerRef)) {
            removeVolleyArrowVisuals(buff);
            ACTIVE_VOLLEY_RUNE_BUFFS.remove(buff.attackerRef, buff);
            return;
        }
        TransformComponent attackerTransform = store.getComponent(
                buff.attackerRef, TransformComponent.getComponentType());
        if (attackerTransform == null || attackerTransform.getPosition() == null) {
            removeVolleyArrowVisuals(buff);
            ACTIVE_VOLLEY_RUNE_BUFFS.remove(buff.attackerRef, buff);
            return;
        }

        long now = System.currentTimeMillis();
        if (buff.launched) {
            for (int i = buff.arrows.size() - 1; i >= 0; i--) {
                VolleyHomingArrow arrow = buff.arrows.get(i);
                if (now - arrow.createdAtMs > VOLLEY_ARROW_MAX_AGE_MS
                        || !updateVolleyArrowProjectileHoming(store, buff, arrow)) {
                    finishVolleyArrow(buff, arrow);
                }
            }
            if (buff.arrows.isEmpty()) {
                ACTIVE_VOLLEY_RUNE_BUFFS.remove(buff.attackerRef, buff);
                return;
            }
            scheduleVolleyRuneTick(buff);
            return;
        }

        Vector3d orbitCenter = new Vector3d(attackerTransform.getPosition()).add(0.0d, 1.25d, 0.0d);
        for (int i = buff.arrows.size() - 1; i >= 0; i--) {
            VolleyHomingArrow arrow = buff.arrows.get(i);
            if (now - arrow.createdAtMs > VOLLEY_ARROW_MAX_AGE_MS) {
                finishVolleyArrow(buff, arrow);
                continue;
            }
            double gatherProgress = Math.min(1.0d,
                    Math.max(0.0d, (double) (now - arrow.createdAtMs) / VOLLEY_ARROW_GATHER_MS));
            Vector3d orbitPosition = new Vector3d(orbitCenter).add(arrow.orbitOffset);
            Vector3d nextPosition = new Vector3d(arrow.startPosition).lerp(orbitPosition, gatherProgress);
            Vector3d direction = new Vector3d(nextPosition).sub(arrow.position);
            arrow.position = nextPosition;
            moveVolleyArrowVisual(store, arrow, arrow.position, direction);
        }

        if (buff.arrows.isEmpty()) {
            ACTIVE_VOLLEY_RUNE_BUFFS.remove(buff.attackerRef, buff);
            return;
        }
        scheduleVolleyRuneTick(buff);
    }

    private static boolean updateVolleyArrowProjectileHoming(Store<EntityStore> store,
                                                             VolleyRuneBuff buff,
                                                             VolleyHomingArrow arrow) {
        if (store == null || buff == null || arrow == null || arrow.projectileRef == null
                || !arrow.projectileRef.isValid()) {
            return false;
        }
        Ref<EntityStore> targetRef = arrow.targetRef;
        if (targetRef == null || !targetRef.isValid() || !isEntityAlive(store, targetRef)
                || isEntityDeadOrDying(store, targetRef)) {
            targetRef = chooseVolleyTarget(store, buff, arrow, arrow.position);
            arrow.targetRef = targetRef;
        }
        if (targetRef == null) {
            return false;
        }
        TransformComponent projectileTransform = store.getComponent(
                arrow.projectileRef, TransformComponent.getComponentType());
        TransformComponent targetTransform = store.getComponent(
                targetRef, TransformComponent.getComponentType());
        Velocity projectileVelocity = store.getComponent(arrow.projectileRef, Velocity.getComponentType());
        ProjectileComponent projectileComponent = store.getComponent(
                arrow.projectileRef, ProjectileComponent.getComponentType());
        if (projectileTransform == null || projectileTransform.getPosition() == null
                || targetTransform == null || targetTransform.getPosition() == null
                || projectileVelocity == null) {
            return false;
        }
        Vector3d previousPosition = arrow.position == null
                ? new Vector3d(projectileTransform.getPosition())
                : new Vector3d(arrow.position);
        Vector3d projectilePosition = new Vector3d(projectileTransform.getPosition());
        Vector3d targetPosition = new Vector3d(targetTransform.getPosition()).add(0.0d, 0.9d, 0.0d);
        if (distanceSquaredToSegment(targetPosition, previousPosition, projectilePosition) <= 1.25d * 1.25d) {
            applyVolleyProjectileHit(store, buff.attackerRef, arrow.projectileRef, targetRef);
            return true;
        }
        arrow.position = projectilePosition;
        Vector3d toTarget = targetPosition.sub(arrow.position);
        if (toTarget.lengthSquared() > 1.0e-6d) {
            Vector3d homingVelocity = toTarget.normalize().mul(
                    VOLLEY_ARROW_SPEED * buff.projectileSpeedMultiplier);
            projectileVelocity.set(homingVelocity);
            SimplePhysicsProvider physicsProvider = projectileComponent == null
                    ? null : projectileComponent.getSimplePhysicsProvider();
            if (physicsProvider != null) {
                physicsProvider.setVelocity(homingVelocity);
            }
        }
        return true;
    }

    private static double distanceSquaredToSegment(Vector3d point, Vector3d start, Vector3d end) {
        Vector3d segment = new Vector3d(end).sub(start);
        double segmentLengthSquared = segment.lengthSquared();
        if (segmentLengthSquared <= 1.0e-8d) {
            return point.distanceSquared(start);
        }
        double progress = new Vector3d(point).sub(start).dot(segment) / segmentLengthSquared;
        progress = Math.max(0.0d, Math.min(1.0d, progress));
        Vector3d closestPoint = new Vector3d(start).fma(progress, segment);
        return point.distanceSquared(closestPoint);
    }

    private static void castBerserkRune(Store<EntityStore> store, Ref<EntityStore> attackerRef) {
        Player player = store.getComponent(attackerRef, Player.getComponentType());
        UUID playerId = player == null ? null : player.getUuid();
        if (playerId == null) {
            return;
        }
        BERSERK_RUNE_UNTIL.put(playerId, System.currentTimeMillis() + 15_000L);
        LoreVisuals.tryApplyTimedVisualEffectOverride(store, attackerRef, 15_000L,
                new String[]{"Lore/Berserk_Haste", "Berserk_Haste", "Lore_Berserk_Haste"});
    }

    /** Adds the low-health Berserk bonus to real player attack events while the rune buff is active. */
    public static void applyBerserkRuneDamageBonus(Store<EntityStore> store,
                                                   Ref<EntityStore> attackerRef,
                                                   Damage damage) {
        if (store == null || attackerRef == null || damage == null) {
            return;
        }
        Player player = store.getComponent(attackerRef, Player.getComponentType());
        UUID playerId = player == null ? null : player.getUuid();
        Long until = playerId == null ? null : BERSERK_RUNE_UNTIL.get(playerId);
        long now = System.currentTimeMillis();
        if (until == null) {
            return;
        }
        if (now >= until) {
            BERSERK_RUNE_UNTIL.remove(playerId, until);
            return;
        }
        float maxHealth = LoreDamageUtils.resolveMaxHealth(store, attackerRef);
        float currentHealth = LoreDamageUtils.resolveCurrentHealth(store, attackerRef);
        if (maxHealth <= 0.0f || currentHealth <= 0.0f) {
            return;
        }
        double healthRatio = currentHealth / maxHealth;
        if (healthRatio > 0.25d) {
            return;
        }
        double scale = Math.max(0.0d, Math.min(1.0d, (0.25d - healthRatio) / 0.24d));
        damage.setAmount(damage.getAmount() * (float) (1.10d + scale * 0.20d));
    }

    public static boolean consumeCriticalChargeGuarantee(Player player, ItemStack weapon) {
        if (player == null || player.getUuid() == null
                || !LoreSocketManager.hasSocketedModifierRunestone(weapon, "Critical_Charge")) {
            return false;
        }
        UUID playerId = player.getUuid();
        int charge = CRITICAL_CHARGE_HITS.getOrDefault(playerId, 0);
        if (charge < 7) {
            return false;
        }
        CRITICAL_CHARGE_HITS.remove(playerId);
        return true;
    }

    public static void addCriticalChargeFromHit(Player player, ItemStack weapon) {
        if (player == null || player.getUuid() == null
                || !LoreSocketManager.hasSocketedModifierRunestone(weapon, "Critical_Charge")) {
            return;
        }
        CRITICAL_CHARGE_HITS.compute(player.getUuid(), (ignored, hits) -> Math.min(8, (hits == null ? 0 : hits) + 1));
    }

    private static void castManaFontRune(Store<EntityStore> store,
                                         Ref<EntityStore> attackerRef,
                                         int feedTier,
                                         int abilityTier) {
        int maxFeedTier = Math.max(1, LoreSocketManager.getMaxRuneFeedThresholds());
        double feedProgress = Math.min(maxFeedTier, Math.max(0, feedTier)) / (double) maxFeedTier;
        int durationMs = abilityTier >= 1
                ? 15_000 * Math.min(4, abilityTier)
                : 15_000 + (int) Math.round(45_000.0d * feedProgress);
        EntityStatMap stats = store.getComponent(attackerRef, EntityStatMap.getComponentType());
        if (stats == null) {
            return;
        }
        int statIndex = LoreDamageUtils.getSignatureEnergyStatIndex(stats);
        if (statIndex < 0) {
            return;
        }
        EntityStatValue mana = stats.get(statIndex);
        if (mana == null) {
            return;
        }
        float perSecond = Math.max(1.0f, mana.getMax() * 0.04f);
        LoreVisuals.tryApplyTimedVisualEffectOverride(store, attackerRef, durationMs, HEAL_TOTEM_EFFECT_IDS);
        int ticks = Math.max(1, durationMs / 1000);
        for (int tick = 1; tick <= ticks; tick++) {
            OMNISLASH_SCHEDULER.schedule(() -> LoreWorldTasks.queue(store,
                    () -> restoreSignatureEnergy(store, attackerRef, perSecond)),
                    tick * 1000L, TimeUnit.MILLISECONDS);
        }
    }

    private static float resolveCoreRuneWeaponDamage(Store<EntityStore> store, Ref<EntityStore> attackerRef) {
        float damage = LoreDamageUtils.resolveWeaponRefinedDamage(store, attackerRef, 10.0f);
        return Math.max(1.0f, damage);
    }

    /** Keeps Socket Reforge support runestones and weapon lore procs on native Grand Slam hits. */
    public static void applyGrandSlamRuneHitSupport(
            Store<EntityStore> store,
            Ref<EntityStore> attackerRef,
            Ref<EntityStore> targetRef,
            List<LoreSocketManager.SocketedModifierRunestone> supportRunestones) {
        applyCoreAbilityRuneHitSupport(store, attackerRef, targetRef, supportRunestones);
    }

    /** Applies Socket Reforge modifier runestones to a native core-rune hit interaction. */
    public static void applyCoreAbilityRuneHitSupport(
            Store<EntityStore> store,
            Ref<EntityStore> attackerRef,
            Ref<EntityStore> targetRef,
            List<LoreSocketManager.SocketedModifierRunestone> supportRunestones) {
        if (store == null || store.isShutdown() || attackerRef == null || targetRef == null
                || !attackerRef.isValid() || !targetRef.isValid()) {
            return;
        }
        List<LoreSocketManager.SocketedModifierRunestone> supportSnapshot =
                supportRunestones == null || supportRunestones.isEmpty()
                        ? List.of() : List.copyOf(supportRunestones);
        Runnable apply = () -> {
            if (store.isShutdown() || !isEntityAlive(store, attackerRef)
                    || !isEntityAlive(store, targetRef) || attackerRef.equals(targetRef)) {
                return;
            }
            applyCoreAbilitySupportRunestones(store, attackerRef, targetRef,
                    supportSnapshot, LoreTrigger.ON_HIT);
        };
        if (!LoreWorldTasks.queue(store, apply)) {
            apply.run();
        }
    }


}
