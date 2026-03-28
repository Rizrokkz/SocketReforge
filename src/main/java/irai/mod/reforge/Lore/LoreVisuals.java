package irai.mod.reforge.Lore;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.component.spatial.SpatialStructure;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.math.vector.Vector3d;

/**
 * Visual helper utilities for lore procs (particles, effects, sounds).
 */
public final class LoreVisuals {
    private static final boolean DEBUG_LORE_PROCS = LoreDebug.ENABLED;
    private static volatile ScheduledExecutorService scheduler;

    private LoreVisuals() {}

    public static void setScheduler(ScheduledExecutorService service) {
        scheduler = service;
    }

    public static void tryApplyVisualEffect(Store<EntityStore> store, Ref<EntityStore> targetRef, String... effectIds) {
        if (store == null || targetRef == null || effectIds == null || effectIds.length == 0) {
            LoreDebug.log("vfx.skip", "reason=invalidTargetOrEffects");
            return;
        }
        if (store.isShutdown()) {
            return;
        }
        EntityStore entityStore = store.getExternalData();
        Runnable task = () -> applyVisualEffectInternal(store, targetRef, effectIds);
        if (entityStore != null && entityStore.getWorld() != null) {
            entityStore.getWorld().execute(task);
        } else {
            task.run();
        }
    }

    public static void tryApplyTimedVisualEffect(Store<EntityStore> store,
                                                 Ref<EntityStore> targetRef,
                                                 long fallbackDurationMs,
                                                 String... effectIds) {
        if (store == null || targetRef == null || effectIds == null || effectIds.length == 0) {
            LoreDebug.log("vfx.timed.skip", "reason=invalidTargetOrEffects");
            return;
        }
        if (store.isShutdown()) {
            return;
        }
        EntityStore entityStore = store.getExternalData();
        Runnable task = () -> applyTimedVisualEffectInternal(store, targetRef, fallbackDurationMs, effectIds);
        if (entityStore != null && entityStore.getWorld() != null) {
            entityStore.getWorld().execute(task);
        } else {
            task.run();
        }
    }

    public static void tryApplyTimedVisualEffectOverride(Store<EntityStore> store,
                                                         Ref<EntityStore> targetRef,
                                                         long durationMs,
                                                         String... effectIds) {
        if (store == null || targetRef == null || effectIds == null || effectIds.length == 0) {
            LoreDebug.log("vfx.timed.skip", "reason=invalidTargetOrEffects");
            return;
        }
        if (durationMs <= 0L) {
            return;
        }
        if (store.isShutdown()) {
            return;
        }
        EntityStore entityStore = store.getExternalData();
        Runnable task = () -> applyTimedVisualEffectOverrideInternal(store, targetRef, durationMs, effectIds);
        if (entityStore != null && entityStore.getWorld() != null) {
            entityStore.getWorld().execute(task);
        } else {
            task.run();
        }
    }

    private static void applyVisualEffectInternal(Store<EntityStore> store,
                                                  Ref<EntityStore> targetRef,
                                                  String... effectIds) {
        try {
            EntityEffect effect = resolveEntityEffect(effectIds);
            if (effect == null) {
                LoreDebug.logKv("vfx.missing", "ids", String.join(",", effectIds));
                return;
            }
            EffectControllerComponent controller =
                    store.ensureAndGetComponent(targetRef, EffectControllerComponent.getComponentType());
            if (controller == null) {
                LoreDebug.log("vfx.skip", "reason=noEffectController");
                return;
            }
            controller.addEffect(targetRef, effect, store);
            LoreDebug.logKv("vfx.apply", "id", effect.getId());
        } catch (Throwable ignored) {
            // Optional.
        }
    }

    private static void applyTimedVisualEffectInternal(Store<EntityStore> store,
                                                       Ref<EntityStore> targetRef,
                                                       long fallbackDurationMs,
                                                       String... effectIds) {
        try {
            EntityEffect effect = resolveEntityEffect(effectIds);
            if (effect == null) {
                LoreDebug.logKv("vfx.timed.missing", "ids", String.join(",", effectIds));
                return;
            }
            var assetMap = EntityEffect.getAssetMap();
            int effectIndex = assetMap != null ? assetMap.getIndex(effect.getId()) : -1;
            EffectControllerComponent controller =
                    store.ensureAndGetComponent(targetRef, EffectControllerComponent.getComponentType());
            if (controller == null) {
                LoreDebug.log("vfx.timed.skip", "reason=noEffectController");
                return;
            }
            if (effectIndex >= 0) {
                controller.addEffect(targetRef, effectIndex, effect, store);
            } else {
                controller.addEffect(targetRef, effect, store);
            }
            LoreDebug.logKv("vfx.timed.apply", "id", effect.getId());
            long durationMs = resolveEffectDurationMs(effect, fallbackDurationMs);
            if (effectIndex >= 0 && durationMs > 0L) {
                queueEffectRemoval(store, targetRef, effectIndex, durationMs);
            }
        } catch (Throwable ignored) {
            // Optional.
        }
    }

    private static void applyTimedVisualEffectOverrideInternal(Store<EntityStore> store,
                                                               Ref<EntityStore> targetRef,
                                                               long durationMs,
                                                               String... effectIds) {
        if (durationMs <= 0L) {
            return;
        }
        try {
            EntityEffect effect = resolveEntityEffect(effectIds);
            if (effect == null) {
                LoreDebug.logKv("vfx.timed.missing", "ids", String.join(",", effectIds));
                return;
            }
            var assetMap = EntityEffect.getAssetMap();
            int effectIndex = assetMap != null ? assetMap.getIndex(effect.getId()) : -1;
            EffectControllerComponent controller =
                    store.ensureAndGetComponent(targetRef, EffectControllerComponent.getComponentType());
            if (controller == null) {
                LoreDebug.log("vfx.timed.skip", "reason=noEffectController");
                return;
            }
            if (effectIndex >= 0) {
                controller.addEffect(targetRef, effectIndex, effect, store);
            } else {
                controller.addEffect(targetRef, effect, store);
            }
            LoreDebug.logKv("vfx.timed.apply", "id", effect.getId());
            if (effectIndex >= 0) {
                queueEffectRemoval(store, targetRef, effectIndex, durationMs);
            }
        } catch (Throwable ignored) {
            // Optional.
        }
    }

    public static void tryRemoveVisualEffect(Store<EntityStore> store,
                                             Ref<EntityStore> targetRef,
                                             String... effectIds) {
        if (store == null || targetRef == null || effectIds == null || effectIds.length == 0) {
            return;
        }
        try {
            EntityEffect effect = resolveEntityEffect(effectIds);
            if (effect == null) {
                return;
            }
            var assetMap = EntityEffect.getAssetMap();
            int effectIndex = assetMap != null ? assetMap.getIndex(effect.getId()) : -1;
            EffectControllerComponent controller =
                    store.getComponent(targetRef, EffectControllerComponent.getComponentType());
            if (controller == null) {
                return;
            }
            if (effectIndex >= 0) {
                controller.removeEffect(targetRef, effectIndex, store);
                controller.tryResetModelChange(targetRef, effectIndex, store);
                LoreDebug.logKv("vfx.remove", "id", effect.getId());
            }
        } catch (Throwable ignored) {
            // Optional.
        }
    }

    public static void tryRemoveVisualEffectsById(Store<EntityStore> store,
                                                  Ref<EntityStore> targetRef,
                                                  String... effectIds) {
        if (store == null || targetRef == null || effectIds == null || effectIds.length == 0) {
            return;
        }
        try {
            EffectControllerComponent controller =
                    store.getComponent(targetRef, EffectControllerComponent.getComponentType());
            if (controller == null) {
                return;
            }
            var assetMap = EntityEffect.getAssetMap();
            if (assetMap == null) {
                return;
            }
            java.util.Set<String> matches = new java.util.HashSet<>();
            for (String id : effectIds) {
                if (id == null || id.isBlank()) {
                    continue;
                }
                matches.add(id.trim().toLowerCase(Locale.ROOT));
            }
            if (matches.isEmpty()) {
                return;
            }
            var active = controller.getActiveEffects();
            if (active == null || active.isEmpty()) {
                return;
            }
            for (var entry : active.int2ObjectEntrySet()) {
                int index = entry.getIntKey();
                EntityEffect effect = assetMap.getAsset(index);
                if (effect == null || effect.getId() == null) {
                    continue;
                }
                String id = effect.getId().toLowerCase(Locale.ROOT);
                if (!matches.contains(id)) {
                    continue;
                }
                controller.removeEffect(targetRef, index, store);
                controller.tryResetModelChange(targetRef, index, store);
                LoreDebug.logKv("vfx.remove", "id", effect.getId());
            }
        } catch (Throwable ignored) {
            // Optional.
        }
    }

    public static void tryRemoveVisualEffectsByContains(Store<EntityStore> store,
                                                        Ref<EntityStore> targetRef,
                                                        String... substrings) {
        if (store == null || targetRef == null || substrings == null || substrings.length == 0) {
            return;
        }
        try {
            EffectControllerComponent controller =
                    store.getComponent(targetRef, EffectControllerComponent.getComponentType());
            if (controller == null) {
                return;
            }
            var assetMap = EntityEffect.getAssetMap();
            if (assetMap == null) {
                return;
            }
            java.util.Set<String> needles = new java.util.HashSet<>();
            for (String part : substrings) {
                if (part == null || part.isBlank()) {
                    continue;
                }
                needles.add(part.trim().toLowerCase(Locale.ROOT));
            }
            if (needles.isEmpty()) {
                return;
            }
            var active = controller.getActiveEffects();
            if (active == null || active.isEmpty()) {
                return;
            }
            for (var entry : active.int2ObjectEntrySet()) {
                int index = entry.getIntKey();
                EntityEffect effect = assetMap.getAsset(index);
                if (effect == null || effect.getId() == null) {
                    continue;
                }
                String id = effect.getId().toLowerCase(Locale.ROOT);
                boolean matches = false;
                for (String needle : needles) {
                    if (id.contains(needle)) {
                        matches = true;
                        break;
                    }
                }
                if (!matches) {
                    continue;
                }
                controller.removeEffect(targetRef, index, store);
                controller.tryResetModelChange(targetRef, index, store);
                LoreDebug.logKv("vfx.remove.match", "id", effect.getId());
            }
        } catch (Throwable ignored) {
            // Optional.
        }
    }

    public static void logActiveEffectIds(Store<EntityStore> store,
                                          Ref<EntityStore> targetRef,
                                          String label) {
        if (!DEBUG_LORE_PROCS) {
            return;
        }
        if (store == null || targetRef == null) {
            return;
        }
        try {
            EffectControllerComponent controller =
                    store.getComponent(targetRef, EffectControllerComponent.getComponentType());
            if (controller == null) {
                LoreDebug.logKv("vfx.active", "label", label, "effects", "<none>");
                return;
            }
            var active = controller.getActiveEffects();
            if (active == null || active.isEmpty()) {
                LoreDebug.logKv("vfx.active", "label", label, "effects", "<empty>");
                return;
            }
            var assetMap = EntityEffect.getAssetMap();
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            boolean first = true;
            for (var entry : active.int2ObjectEntrySet()) {
                int index = entry.getIntKey();
                String id = null;
                if (assetMap != null) {
                    EntityEffect effect = assetMap.getAsset(index);
                    if (effect != null) {
                        id = effect.getId();
                    }
                }
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                if (id == null) {
                    sb.append("#").append(index);
                } else {
                    sb.append(id);
                }
            }
            sb.append("]");
            LoreDebug.logKv("vfx.active", "label", label, "effects", sb.toString());
        } catch (Throwable ignored) {
            // Optional.
        }
    }

    public static void queueSignatureParticles(Store<EntityStore> store,
                                               Vector3d pos,
                                               float scale,
                                               String... particleIds) {
        if (store == null || pos == null) {
            return;
        }
        EntityStore entityStore = store.getExternalData();
        if (entityStore == null || entityStore.getWorld() == null) {
            spawnSignatureParticles(store, pos, scale, particleIds);
            return;
        }
        entityStore.getWorld().execute(() -> spawnSignatureParticles(store, pos, scale, particleIds));
    }

    public static void playSignatureSound(Player player, String... soundIds) {
        if (player == null || soundIds == null) {
            return;
        }
        for (String soundId : soundIds) {
            if (soundId == null || soundId.isBlank()) {
                continue;
            }
            int soundIndex = SoundEvent.getAssetMap().getIndex(soundId);
            if (soundIndex < 0) {
                continue;
            }
            try {
                SoundUtil.playSoundEvent2dToPlayer(player.getPlayerRef(), soundIndex, SoundCategory.SFX);
            } catch (Throwable ignored) {
                // best-effort
            }
        }
    }

    public static void spawnSlashParticles(Store<EntityStore> store,
                                           Ref<EntityStore> attackerRef,
                                           Ref<EntityStore> targetRef,
                                           String particleId,
                                           float scale) {
        if (store == null || particleId == null || particleId.isBlank()) {
            return;
        }
        Vector3d pos = LoreTargetingUtils.resolveCenterPosition(store, targetRef, attackerRef);
        if (pos == null) {
            return;
        }
        Vector3d spawnPos = pos.clone();
        spawnPos.add(0.0d, 0.9d, 0.0d);
        try {
            spawnScaledParticle(store, particleId, spawnPos, scale);
        } catch (Throwable ignored) {
            try {
                ParticleUtil.spawnParticleEffect(particleId, spawnPos, store);
            } catch (Throwable ignored2) {
                // best-effort
            }
        }
    }

    public static void spawnBleedParticles(Store<EntityStore> store,
                                           Ref<EntityStore> sourceRef,
                                           Ref<EntityStore> targetRef,
                                           String... particleIds) {
        String particleId = resolveParticleEffectId(particleIds);
        if (particleId == null) {
            return;
        }
        spawnSlashParticles(store, sourceRef, targetRef, particleId, 0.9f);
    }

    public static void spawnScaledParticle(Store<EntityStore> store,
                                           String particleId,
                                           Vector3d pos,
                                           float scale) {
        if (store == null || particleId == null || particleId.isBlank() || pos == null) {
            return;
        }
        List<Ref<EntityStore>> viewers = collectParticleRecipients(store, pos);
        if (scale > 0f) {
            if (viewers == null || viewers.isEmpty()) {
                ParticleUtil.spawnParticleEffect(particleId, pos, store);
                return;
            }
            ParticleUtil.spawnParticleEffect(
                    particleId,
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    0f,
                    0f,
                    0f,
                    scale,
                    null,
                    null,
                    viewers,
                    store
            );
        } else {
            if (viewers == null || viewers.isEmpty()) {
                ParticleUtil.spawnParticleEffect(particleId, pos, store);
                return;
            }
            ParticleUtil.spawnParticleEffect(particleId, pos, viewers, store);
        }
    }

    public static String resolveParticleEffectId(String... particleIds) {
        if (particleIds == null || particleIds.length == 0) {
            return null;
        }
        try {
            var assetMap = ParticleSystem.getAssetMap();
            if (assetMap == null) {
                return null;
            }
            for (String id : particleIds) {
                if (id == null || id.isBlank()) {
                    continue;
                }
                ParticleSystem system = assetMap.getAsset(id);
                if (system != null) {
                    return id;
                }
            }
        } catch (Throwable ignored) {
            return null;
        }
        return null;
    }

    private static void spawnSignatureParticles(Store<EntityStore> store,
                                                Vector3d pos,
                                                float scale,
                                                String... particleIds) {
        if (store == null || pos == null || particleIds == null || particleIds.length == 0) {
            return;
        }
        String particleId = resolveParticleEffectId(particleIds);
        if (particleId == null) {
            LoreDebug.logKv("particles.signature.missing", "ids", String.join(",", particleIds));
            return;
        }
        try {
            spawnScaledParticle(store, particleId, pos, scale);
            LoreDebug.logKv("particles.signature.spawn",
                    "id", particleId,
                    "pos", formatVector(pos),
                    "scale", String.format(Locale.ROOT, "%.2f", scale));
        } catch (Throwable ignored) {
            // best-effort
        }
    }

    private static List<Ref<EntityStore>> collectParticleRecipients(Store<EntityStore> store, Vector3d pos) {
        if (store == null || pos == null) {
            return null;
        }
        EntityModule entityModule;
        try {
            entityModule = EntityModule.get();
        } catch (Throwable ignored) {
            return null;
        }
        if (entityModule == null) {
            return null;
        }
        ResourceType<EntityStore, SpatialResource<Ref<EntityStore>, EntityStore>> resourceType;
        try {
            resourceType = entityModule.getPlayerSpatialResourceType();
        } catch (Throwable ignored) {
            return null;
        }
        if (resourceType == null) {
            return null;
        }
        SpatialResource<Ref<EntityStore>, EntityStore> spatial;
        try {
            spatial = store.getResource(resourceType);
        } catch (Throwable ignored) {
            return null;
        }
        if (spatial == null) {
            return null;
        }
        SpatialStructure<Ref<EntityStore>> structure = spatial.getSpatialStructure();
        if (structure == null) {
            return null;
        }
        List<Ref<EntityStore>> nearby = SpatialResource.getThreadLocalReferenceList();
        nearby.clear();
        try {
            structure.collect(pos, ParticleUtil.DEFAULT_PARTICLE_DISTANCE, nearby);
        } catch (Throwable ignored) {
            return null;
        }
        return nearby;
    }

    private static EntityEffect resolveEntityEffect(String... effectIds) {
        if (effectIds == null || effectIds.length == 0) {
            return null;
        }
        try {
            var assetMap = EntityEffect.getAssetMap();
            if (assetMap == null) {
                return null;
            }
            for (String effectId : effectIds) {
                if (effectId == null || effectId.isBlank()) {
                    continue;
                }
                int index = assetMap.getIndex(effectId);
                if (index < 0) {
                    continue;
                }
                EntityEffect effect = assetMap.getAsset(index);
                if (effect != null) {
                    return effect;
                }
            }
        } catch (Throwable ignored) {
            return null;
        }
        return null;
    }

    private static long resolveEffectDurationMs(EntityEffect effect, long fallbackDurationMs) {
        if (effect == null) {
            return fallbackDurationMs;
        }
        if (!effect.isInfinite()) {
            float duration = effect.getDuration();
            if (duration > 0f) {
                return (long) (duration * 1000f);
            }
        }
        return fallbackDurationMs;
    }

    private static void queueEffectRemoval(Store<EntityStore> store,
                                           Ref<EntityStore> targetRef,
                                           int effectIndex,
                                           long delayMs) {
        if (store == null || targetRef == null || delayMs <= 0L) {
            return;
        }
        ScheduledExecutorService service = scheduler;
        if (service == null) {
            return;
        }
        EntityStore entityStore = store.getExternalData();
        if (entityStore == null || entityStore.getWorld() == null) {
            return;
        }
        service.schedule(
                () -> entityStore.getWorld().execute(() -> removeEffectIfPresent(store, targetRef, effectIndex)),
                delayMs,
                TimeUnit.MILLISECONDS);
    }

    private static void removeEffectIfPresent(Store<EntityStore> store,
                                              Ref<EntityStore> targetRef,
                                              int effectIndex) {
        if (store == null || targetRef == null || store.isShutdown()) {
            return;
        }
        try {
            EffectControllerComponent controller =
                    store.getComponent(targetRef, EffectControllerComponent.getComponentType());
            if (controller == null) {
                return;
            }
            controller.removeEffect(targetRef, effectIndex, store);
            controller.tryResetModelChange(targetRef, effectIndex, store);
            LoreDebug.logKv("vfx.timed.remove", "index", effectIndex);
        } catch (Throwable ignored) {
            // Optional.
        }
    }

    private static String formatVector(Vector3d pos) {
        if (pos == null) {
            return "(0.00, 0.00, 0.00)";
        }
        return String.format(Locale.ROOT, "(%.2f, %.2f, %.2f)", pos.getX(), pos.getY(), pos.getZ());
    }
}
