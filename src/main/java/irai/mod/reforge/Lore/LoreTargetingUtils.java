package irai.mod.reforge.Lore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.component.spatial.SpatialStructure;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.AllLegacyLivingEntityTypesQuery;
import com.hypixel.hytale.server.core.modules.entity.component.NPCMarkerComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

/**
 * Shared targeting and spatial scanning helpers for lore systems.
 */
public final class LoreTargetingUtils {
    private static final Object NPC_QUERY_LOCK = new Object();
    private static volatile Query<EntityStore> npcQuery;
    private static final Object NPC_MARKER_QUERY_LOCK = new Object();
    private static volatile Query<EntityStore> npcMarkerQuery;
    private static final Object STAT_QUERY_LOCK = new Object();
    private static volatile Query<EntityStore> statQuery;
    private static final Object TRANSFORM_QUERY_LOCK = new Object();
    private static volatile Query<EntityStore> transformQuery;
    private static final Object PLAYER_QUERY_LOCK = new Object();
    private static volatile Query<EntityStore> playerQuery;

    private LoreTargetingUtils() {}

    public static List<Ref<EntityStore>> collectOmnislashTargets(Store<EntityStore> store,
                                                                 Ref<EntityStore> attackerRef,
                                                                 Ref<EntityStore> targetRef,
                                                                 double radius,
                                                                 double radiusSq,
                                                                 String debugLabel,
                                                                 boolean debug,
                                                                 int maxTargets,
                                                                 int maxNpcChecks) {
        List<Ref<EntityStore>> targets = new ArrayList<>();
        if (targetRef != null) {
            targets.add(targetRef);
        }
        Vector3d center = resolveCenterPosition(store, targetRef, attackerRef);
        if (center == null) {
            return targets;
        }

        boolean usedSpatial = collectTargetsFromNpcSpatialResource(store, attackerRef, targetRef, center, radius,
                targets, debugLabel, debug, maxTargets);
        final int[] inspected = new int[]{0};
        if (!usedSpatial) {
            var npcType = NPCEntity.getComponentType();
            var transformType = TransformComponent.getComponentType();
            var markerType = NPCMarkerComponent.getComponentType();
            if (transformType == null) {
                return targets;
            }

            Query<EntityStore> npcEntityQuery = resolveNpcQuery(npcType, transformType);
            Query<EntityStore> markerQuery = resolveNpcMarkerQuery(markerType, transformType);
            Query<EntityStore> statsQuery = resolveStatQuery(EntityStatMap.getComponentType(), transformType);
            Query<EntityStore> transformOnlyQuery = resolveTransformQuery(transformType);
            if (npcEntityQuery != null) {
                collectTargetsForQuery(store, npcEntityQuery, attackerRef, targetRef, center, targets, inspected,
                        transformType, radiusSq, maxTargets, maxNpcChecks);
            }
            if (markerQuery != null && targets.size() < maxTargets && inspected[0] < maxNpcChecks) {
                collectTargetsForQuery(store, markerQuery, attackerRef, targetRef, center, targets, inspected,
                        transformType, radiusSq, maxTargets, maxNpcChecks);
            }
            if (targets.size() < maxTargets && inspected[0] < maxNpcChecks) {
                collectTargetsForLivingQuery(store, attackerRef, targetRef, center, targets, inspected,
                        transformType, radiusSq, maxTargets, maxNpcChecks);
            }
            if (statsQuery != null && targets.size() < maxTargets && inspected[0] < maxNpcChecks) {
                collectTargetsForStatQuery(store, statsQuery, attackerRef, targetRef, center, targets, inspected,
                        transformType, radiusSq, maxTargets, maxNpcChecks);
            }
            if (transformOnlyQuery != null && targets.size() < maxTargets && inspected[0] < maxNpcChecks) {
                collectTargetsForStatQuery(store, transformOnlyQuery, attackerRef, targetRef, center, targets,
                        inspected, transformType, radiusSq, maxTargets, maxNpcChecks);
            }
        }

        if (debug) {
            String logLabel = debugLabel == null || debugLabel.isBlank() ? "aoe" : debugLabel;
            LoreDebug.logKv(logLabel + ".scan", "inspected", inspected[0], "targets", targets.size());
            for (int i = 0; i < targets.size(); i++) {
                Ref<EntityStore> ref = targets.get(i);
                Vector3d pos = getPosition(store, ref);
                String entityLabel = resolveDebugEntityLabel(store, ref);
                LoreDebug.logKv(logLabel + ".target", "index", i, "entity", entityLabel, "pos", formatVector(pos));
            }
        }

        return targets;
    }

    public static List<Ref<EntityStore>> collectTargetsAtPosition(Store<EntityStore> store,
                                                                  Ref<EntityStore> attackerRef,
                                                                  Vector3d center,
                                                                  double radius,
                                                                  double radiusSq,
                                                                  String debugLabel,
                                                                  boolean debug,
                                                                  int maxTargets,
                                                                  int maxNpcChecks) {
        List<Ref<EntityStore>> targets = new ArrayList<>();
        if (store == null || center == null) {
            return targets;
        }

        boolean usedSpatial = collectTargetsFromNpcSpatialResource(store, attackerRef, null, center, radius, targets,
                debugLabel, debug, maxTargets);
        final int[] inspected = new int[]{0};
        if (!usedSpatial) {
            var npcType = NPCEntity.getComponentType();
            var transformType = TransformComponent.getComponentType();
            var markerType = NPCMarkerComponent.getComponentType();
            if (transformType == null) {
                return targets;
            }

            Query<EntityStore> npcEntityQuery = resolveNpcQuery(npcType, transformType);
            Query<EntityStore> markerQuery = resolveNpcMarkerQuery(markerType, transformType);
            Query<EntityStore> statsQuery = resolveStatQuery(EntityStatMap.getComponentType(), transformType);
            Query<EntityStore> transformOnlyQuery = resolveTransformQuery(transformType);
            if (npcEntityQuery != null) {
                collectTargetsForQuery(store, npcEntityQuery, attackerRef, null, center, targets, inspected,
                        transformType, radiusSq, maxTargets, maxNpcChecks);
            }
            if (markerQuery != null && targets.size() < maxTargets && inspected[0] < maxNpcChecks) {
                collectTargetsForQuery(store, markerQuery, attackerRef, null, center, targets, inspected,
                        transformType, radiusSq, maxTargets, maxNpcChecks);
            }
            if (targets.size() < maxTargets && inspected[0] < maxNpcChecks) {
                collectTargetsForLivingQuery(store, attackerRef, null, center, targets, inspected,
                        transformType, radiusSq, maxTargets, maxNpcChecks);
            }
            if (statsQuery != null && targets.size() < maxTargets && inspected[0] < maxNpcChecks) {
                collectTargetsForStatQuery(store, statsQuery, attackerRef, null, center, targets, inspected,
                        transformType, radiusSq, maxTargets, maxNpcChecks);
            }
            if (transformOnlyQuery != null && targets.size() < maxTargets && inspected[0] < maxNpcChecks) {
                collectTargetsForStatQuery(store, transformOnlyQuery, attackerRef, null, center, targets, inspected,
                        transformType, radiusSq, maxTargets, maxNpcChecks);
            }
        }

        if (debug) {
            String logLabel = debugLabel == null || debugLabel.isBlank() ? "aoe" : debugLabel;
            LoreDebug.logKv(logLabel + ".scan", "inspected", inspected[0], "targets", targets.size());
            for (int i = 0; i < targets.size(); i++) {
                Ref<EntityStore> ref = targets.get(i);
                Vector3d pos = getPosition(store, ref);
                String entityLabel = resolveDebugEntityLabel(store, ref);
                LoreDebug.logKv(logLabel + ".target", "index", i, "entity", entityLabel, "pos", formatVector(pos));
            }
        }

        return targets;
    }

    public static List<Ref<EntityStore>> collectNearbyPlayers(Store<EntityStore> store,
                                                              Ref<EntityStore> sourceRef,
                                                              double radiusSq,
                                                              int maxPlayerChecks) {
        List<Ref<EntityStore>> targets = new ArrayList<>();
        if (store == null || sourceRef == null) {
            return targets;
        }
        Vector3d center = getPosition(store, sourceRef);
        if (center == null) {
            return targets;
        }
        Query<EntityStore> query = resolvePlayerQuery();
        if (query == null) {
            return targets;
        }
        var playerType = Player.getComponentType();
        var transformType = TransformComponent.getComponentType();
        if (playerType == null || transformType == null) {
            return targets;
        }
        final int[] inspected = new int[]{0};
        store.forEachChunk(query, (chunk, commandBuffer) -> {
            int size = chunk.size();
            for (int i = 0; i < size; i++) {
                if (inspected[0]++ >= maxPlayerChecks) {
                    return false;
                }
                Player player = chunk.getComponent(i, playerType);
                if (player == null) {
                    continue;
                }
                Ref<EntityStore> ref = chunk.getReferenceTo(i);
                if (ref == null) {
                    continue;
                }
                TransformComponent playerTransform = chunk.getComponent(i, transformType);
                if (playerTransform == null) {
                    continue;
                }
                Vector3d pos = playerTransform.getPosition();
                if (pos == null || distanceSquared(center, pos) > radiusSq) {
                    continue;
                }
                if (containsRef(targets, ref)) {
                    continue;
                }
                targets.add(ref);
            }
            return true;
        });
        return targets;
    }

    public static Vector3d resolveCenterPosition(Store<EntityStore> store,
                                                 Ref<EntityStore> primary,
                                                 Ref<EntityStore> fallback) {
        Vector3d pos = getPosition(store, primary);
        if (pos == null) {
            pos = getPosition(store, fallback);
        }
        return pos;
    }

    public static Vector3d getPosition(Store<EntityStore> store, Ref<EntityStore> ref) {
        if (store == null || ref == null) {
            return null;
        }
        try {
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) {
                return null;
            }
            Vector3d pos = transform.getPosition();
            return pos == null ? null : pos.clone();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean containsRef(List<Ref<EntityStore>> refs, Ref<EntityStore> ref) {
        if (refs == null || refs.isEmpty() || ref == null) {
            return false;
        }
        for (Ref<EntityStore> existing : refs) {
            if (ref.equals(existing)) {
                return true;
            }
        }
        return false;
    }

    public static double distanceSquared(Vector3d a, Vector3d b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return (dx * dx) + (dy * dy) + (dz * dz);
    }

    public static String formatVector(Vector3d pos) {
        if (pos == null) {
            return "null";
        }
        return String.format(Locale.ROOT, "(%.2f, %.2f, %.2f)", pos.getX(), pos.getY(), pos.getZ());
    }

    public static String resolveDebugEntityLabel(Store<EntityStore> store, Ref<EntityStore> ref) {
        if (store == null || ref == null) {
            return "unknown";
        }
        try {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                String name = player.getDisplayName();
                if (name == null || name.isBlank()) {
                    return "Player:" + String.valueOf(player.getPlayerRef());
                }
                return "Player:" + name;
            }
        } catch (Throwable ignored) {
            // best-effort
        }
        try {
            NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
            if (npc != null) {
                String type = npc.getNPCTypeId();
                if (type != null && !type.isBlank()) {
                    return "NPC:" + type;
                }
            }
        } catch (Throwable ignored) {
            // best-effort
        }
        return String.valueOf(ref);
    }

    private static boolean collectTargetsFromNpcSpatialResource(Store<EntityStore> store,
                                                                Ref<EntityStore> attackerRef,
                                                                Ref<EntityStore> targetRef,
                                                                Vector3d center,
                                                                double radius,
                                                                List<Ref<EntityStore>> targets,
                                                                String debugLabel,
                                                                boolean debug,
                                                                int maxTargets) {
        if (store == null || center == null || targets == null) {
            return false;
        }
        NPCPlugin npcPlugin;
        try {
            npcPlugin = NPCPlugin.get();
        } catch (Throwable ignored) {
            return false;
        }
        if (npcPlugin == null) {
            return false;
        }
        var resourceType = npcPlugin.getNpcSpatialResource();
        if (resourceType == null) {
            return false;
        }
        SpatialResource<Ref<EntityStore>, EntityStore> spatial;
        try {
            spatial = store.getResource(resourceType);
        } catch (Throwable ignored) {
            return false;
        }
        if (spatial == null) {
            return false;
        }
        SpatialStructure<Ref<EntityStore>> structure = spatial.getSpatialStructure();
        if (structure == null) {
            return false;
        }
        List<Ref<EntityStore>> nearby = SpatialResource.getThreadLocalReferenceList();
        nearby.clear();
        try {
            structure.collect(center, radius, nearby);
        } catch (Throwable ignored) {
            return false;
        }
        for (Ref<EntityStore> ref : nearby) {
            if (targets.size() >= maxTargets) {
                break;
            }
            if (ref == null || ref.equals(attackerRef)) {
                continue;
            }
            if (targetRef != null && ref.equals(targetRef)) {
                continue;
            }
            if (containsRef(targets, ref)) {
                continue;
            }
            targets.add(ref);
        }
        if (debug) {
            String label = debugLabel == null || debugLabel.isBlank() ? "aoe" : debugLabel;
            LoreDebug.logKv(label + ".spatial", "hits", nearby.size());
        }
        return true;
    }

    private static void collectTargetsForQuery(Store<EntityStore> store,
                                               Query<EntityStore> query,
                                               Ref<EntityStore> attackerRef,
                                               Ref<EntityStore> targetRef,
                                               Vector3d center,
                                               List<Ref<EntityStore>> targets,
                                               int[] inspected,
                                               com.hypixel.hytale.component.ComponentType<EntityStore, TransformComponent> transformType,
                                               double radiusSq,
                                               int maxTargets,
                                               int maxNpcChecks) {
        if (store == null || query == null || center == null || targets == null || inspected == null) {
            return;
        }
        store.forEachChunk(query, (chunk, commandBuffer) -> {
            int size = chunk.size();
            for (int i = 0; i < size; i++) {
                if (targets.size() >= maxTargets) {
                    return false;
                }
                if (inspected[0]++ >= maxNpcChecks) {
                    return false;
                }
                Ref<EntityStore> ref = chunk.getReferenceTo(i);
                if (ref == null || ref.equals(attackerRef)) {
                    continue;
                }
                if (targetRef != null && ref.equals(targetRef)) {
                    continue;
                }
                TransformComponent npcTransform = chunk.getComponent(i, transformType);
                if (npcTransform == null) {
                    continue;
                }
                Vector3d pos = npcTransform.getPosition();
                if (pos == null || distanceSquared(center, pos) > radiusSq) {
                    continue;
                }
                if (containsRef(targets, ref)) {
                    continue;
                }
                targets.add(ref);
            }
            return true;
        });
    }

    private static void collectTargetsForLivingQuery(Store<EntityStore> store,
                                                     Ref<EntityStore> attackerRef,
                                                     Ref<EntityStore> targetRef,
                                                     Vector3d center,
                                                     List<Ref<EntityStore>> targets,
                                                     int[] inspected,
                                                     com.hypixel.hytale.component.ComponentType<EntityStore, TransformComponent> transformType,
                                                     double radiusSq,
                                                     int maxTargets,
                                                     int maxNpcChecks) {
        if (store == null || center == null || targets == null || inspected == null || transformType == null) {
            return;
        }
        Query<EntityStore> query = AllLegacyLivingEntityTypesQuery.INSTANCE;
        store.forEachChunk(query, (chunk, commandBuffer) -> {
            int size = chunk.size();
            for (int i = 0; i < size; i++) {
                if (targets.size() >= maxTargets) {
                    return false;
                }
                if (inspected[0]++ >= maxNpcChecks) {
                    return false;
                }
                Ref<EntityStore> ref = chunk.getReferenceTo(i);
                if (ref == null || ref.equals(attackerRef)) {
                    continue;
                }
                if (targetRef != null && ref.equals(targetRef)) {
                    continue;
                }
                if (chunk.getComponent(i, Player.getComponentType()) != null) {
                    continue;
                }
                TransformComponent npcTransform = chunk.getComponent(i, transformType);
                if (npcTransform == null) {
                    continue;
                }
                Vector3d pos = npcTransform.getPosition();
                if (pos == null || distanceSquared(center, pos) > radiusSq) {
                    continue;
                }
                if (containsRef(targets, ref)) {
                    continue;
                }
                targets.add(ref);
            }
            return true;
        });
    }

    private static void collectTargetsForStatQuery(Store<EntityStore> store,
                                                   Query<EntityStore> query,
                                                   Ref<EntityStore> attackerRef,
                                                   Ref<EntityStore> targetRef,
                                                   Vector3d center,
                                                   List<Ref<EntityStore>> targets,
                                                   int[] inspected,
                                                   com.hypixel.hytale.component.ComponentType<EntityStore, TransformComponent> transformType,
                                                   double radiusSq,
                                                   int maxTargets,
                                                   int maxNpcChecks) {
        if (store == null || query == null || center == null || targets == null || inspected == null) {
            return;
        }
        var statType = EntityStatMap.getComponentType();
        if (statType == null) {
            return;
        }
        store.forEachChunk(query, (chunk, commandBuffer) -> {
            int size = chunk.size();
            for (int i = 0; i < size; i++) {
                if (targets.size() >= maxTargets) {
                    return false;
                }
                if (inspected[0]++ >= maxNpcChecks) {
                    return false;
                }
                Ref<EntityStore> ref = chunk.getReferenceTo(i);
                if (ref == null || ref.equals(attackerRef)) {
                    continue;
                }
                if (targetRef != null && ref.equals(targetRef)) {
                    continue;
                }
                if (chunk.getComponent(i, Player.getComponentType()) != null) {
                    continue;
                }
                TransformComponent npcTransform = chunk.getComponent(i, transformType);
                if (npcTransform == null) {
                    continue;
                }
                Vector3d pos = npcTransform.getPosition();
                if (pos == null || distanceSquared(center, pos) > radiusSq) {
                    continue;
                }
                EntityStatMap statMap = chunk.getComponent(i, statType);
                if (statMap == null || LoreDamageUtils.getHealthStatIndex(statMap) < 0) {
                    continue;
                }
                if (containsRef(targets, ref)) {
                    continue;
                }
                targets.add(ref);
            }
            return true;
        });
    }

    private static Query<EntityStore> resolveNpcQuery(
            com.hypixel.hytale.component.ComponentType<EntityStore, NPCEntity> npcType,
            com.hypixel.hytale.component.ComponentType<EntityStore, TransformComponent> transformType) {
        Query<EntityStore> cached = npcQuery;
        if (cached != null) {
            return cached;
        }
        synchronized (NPC_QUERY_LOCK) {
            cached = npcQuery;
            if (cached != null) {
                return cached;
            }
            if (npcType == null || transformType == null) {
                return null;
            }
            try {
                cached = Archetype.of(npcType, transformType);
                npcQuery = cached;
                return cached;
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    private static Query<EntityStore> resolveNpcMarkerQuery(
            com.hypixel.hytale.component.ComponentType<EntityStore, NPCMarkerComponent> markerType,
            com.hypixel.hytale.component.ComponentType<EntityStore, TransformComponent> transformType) {
        Query<EntityStore> cached = npcMarkerQuery;
        if (cached != null) {
            return cached;
        }
        synchronized (NPC_MARKER_QUERY_LOCK) {
            cached = npcMarkerQuery;
            if (cached != null) {
                return cached;
            }
            if (markerType == null || transformType == null) {
                return null;
            }
            try {
                cached = Archetype.of(markerType, transformType);
                npcMarkerQuery = cached;
                return cached;
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    private static Query<EntityStore> resolveStatQuery(
            com.hypixel.hytale.component.ComponentType<EntityStore, EntityStatMap> statType,
            com.hypixel.hytale.component.ComponentType<EntityStore, TransformComponent> transformType) {
        Query<EntityStore> cached = statQuery;
        if (cached != null) {
            return cached;
        }
        synchronized (STAT_QUERY_LOCK) {
            cached = statQuery;
            if (cached != null) {
                return cached;
            }
            if (statType == null || transformType == null) {
                return null;
            }
            try {
                cached = Archetype.of(statType, transformType);
                statQuery = cached;
                return cached;
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    private static Query<EntityStore> resolveTransformQuery(
            com.hypixel.hytale.component.ComponentType<EntityStore, TransformComponent> transformType) {
        Query<EntityStore> cached = transformQuery;
        if (cached != null) {
            return cached;
        }
        synchronized (TRANSFORM_QUERY_LOCK) {
            cached = transformQuery;
            if (cached != null) {
                return cached;
            }
            if (transformType == null) {
                return null;
            }
            try {
                cached = Archetype.of(transformType);
                transformQuery = cached;
                return cached;
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    public static Query<EntityStore> resolvePlayerQuery() {
        Query<EntityStore> cached = playerQuery;
        if (cached != null) {
            return cached;
        }
        synchronized (PLAYER_QUERY_LOCK) {
            cached = playerQuery;
            if (cached != null) {
                return cached;
            }
            var playerType = Player.getComponentType();
            var transformType = TransformComponent.getComponentType();
            if (playerType == null || transformType == null) {
                return null;
            }
            try {
                cached = Archetype.of(playerType, transformType);
                playerQuery = cached;
                return cached;
            } catch (Throwable ignored) {
                return null;
            }
        }
    }
}
