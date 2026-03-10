package irai.mod.reforge.Entity.Events;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.builtin.weather.resources.WeatherResource;

import it.unimi.dsi.fastutil.Pair;

/**
 * Spawns Spirit_Thunder NPCs near each player while it is raining for them.
 * Spawn rate and limits are managed per-player.
 */
public class SpiritThunderRainSystem extends EntityTickingSystem<EntityStore> {

    private static volatile String spiritRoleId = "Spirit_Thunder";
    private static volatile float minSpawnIntervalS = 12.0f;
    private static volatile float maxSpawnIntervalS = 20.0f;
    private static volatile int maxSpiritsPerPlayer = 6;
    private static volatile double minSpawnDistance = 8.0d;
    private static volatile double maxSpawnDistance = 30.0d;
    private static volatile int minSpawnsPerInterval = 1;
    private static volatile int maxSpawnsPerInterval = 3;
    private static volatile float despawnAfterRainEndS = 30.0f;
    private static volatile String[] rainKeywords = {"rain", "storm", "thunder"};
    private static final long LOG_COOLDOWN_MS = 10000L;

    private final Map<UUID, Float> spawnCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, List<Ref<EntityStore>>> activeSpirits = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastLogAt = new ConcurrentHashMap<>();
    private final Map<UUID, Float> rainEndTimers = new ConcurrentHashMap<>();

    public static void setConfig(irai.mod.reforge.Config.WeatherEventConfig config) {
        if (config == null) {
            return;
        }

        String role = config.getSpiritRoleId();
        spiritRoleId = role == null || role.isBlank() ? "Spirit_Thunder" : role.trim();

        float minInterval = (float) Math.max(0.1d, config.getMinSpawnIntervalSeconds());
        float maxInterval = (float) Math.max(minInterval, config.getMaxSpawnIntervalSeconds());
        minSpawnIntervalS = minInterval;
        maxSpawnIntervalS = maxInterval;

        maxSpiritsPerPlayer = Math.max(1, config.getMaxSpiritsPerPlayer());

        double minDist = Math.max(0.0d, config.getMinSpawnDistance());
        double maxDist = Math.max(minDist, config.getMaxSpawnDistance());
        minSpawnDistance = minDist;
        maxSpawnDistance = maxDist;

        int minSpawns = Math.max(1, config.getMinSpawnsPerInterval());
        int maxSpawns = Math.max(minSpawns, config.getMaxSpawnsPerInterval());
        minSpawnsPerInterval = minSpawns;
        maxSpawnsPerInterval = maxSpawns;

        despawnAfterRainEndS = (float) Math.max(0.0d, config.getDespawnAfterRainEndSeconds());

        String[] keywords = config.getRainKeywords();
        if (keywords == null || keywords.length == 0) {
            rainKeywords = new String[]{"rain", "storm", "thunder"};
        } else {
            String[] copy = new String[keywords.length];
            int count = 0;
            for (String raw : keywords) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                copy[count++] = raw.trim().toLowerCase(Locale.ROOT);
            }
            if (count == 0) {
                rainKeywords = new String[]{"rain", "storm", "thunder"};
            } else if (count == copy.length) {
                rainKeywords = copy;
            } else {
                String[] trimmed = new String[count];
                System.arraycopy(copy, 0, trimmed, 0, count);
                rainKeywords = trimmed;
            }
        }
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public boolean isParallel(int from, int to) {
        return false;
    }

    @Override
    public void tick(float time,
                     int index,
                     ArchetypeChunk<EntityStore> chunk,
                     Store<EntityStore> store,
                     CommandBuffer<EntityStore> commandBuffer) {
        if (time <= 0f) {
            return;
        }

        Ref<EntityStore> playerRef = chunk.getReferenceTo(index);
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null || player.getWorld() == null) {
            return;
        }

        World world = player.getWorld();
        UUID playerId = player.getUuid();
        if (playerId == null) {
            return;
        }

        TransformComponent anchorTransform = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (anchorTransform == null || anchorTransform.getPosition() == null) {
            return;
        }

        Store<EntityStore> worldStore = world.getEntityStore() != null
                ? world.getEntityStore().getStore()
                : store;
        if (worldStore == null) {
            return;
        }

        if (!isRainingForWorld(world, worldStore, anchorTransform)) {
            handleRainEnded(playerId, world, time);
            pruneActive(playerId, worldStore);
            return;
        }
        rainEndTimers.remove(playerId);

        if (pruneActive(playerId, worldStore) >= maxSpiritsPerPlayer) {
            return;
        }

        float remaining = spawnCooldowns.getOrDefault(playerId, 0f) - time;
        if (remaining > 0f) {
            spawnCooldowns.put(playerId, remaining);
            return;
        }

        int spawnAttempts = rollSpawnCount();
        int spawned = 0;
        for (int i = 0; i < spawnAttempts; i++) {
            if (pruneActive(playerId, worldStore) >= maxSpiritsPerPlayer) {
                break;
            }
            if (spawnSpiritNearTransform(world, worldStore, anchorTransform, playerId)) {
                spawned++;
            }
        }

        spawnCooldowns.put(playerId, spawned > 0 ? rollCooldown() : minSpawnIntervalS);
    }

    private float rollCooldown() {
        float delta = maxSpawnIntervalS - minSpawnIntervalS;
        if (delta <= 0f) {
            return minSpawnIntervalS;
        }
        return minSpawnIntervalS + (ThreadLocalRandom.current().nextFloat() * delta);
    }

    private int rollSpawnCount() {
        if (maxSpawnsPerInterval <= minSpawnsPerInterval) {
            return minSpawnsPerInterval;
        }
        return ThreadLocalRandom.current().nextInt(minSpawnsPerInterval, maxSpawnsPerInterval + 1);
    }

    private int pruneActive(UUID playerId, Store<EntityStore> store) {
        List<Ref<EntityStore>> refs = activeSpirits.get(playerId);
        if (refs == null || refs.isEmpty()) {
            return 0;
        }
        refs.removeIf(ref -> {
            if (ref == null) {
                return true;
            }
            if (!ref.isValid()) {
                return true;
            }
            try {
                return store.getComponent(ref, NPCEntity.getComponentType()) == null;
            } catch (Throwable ignored) {
                return true;
            }
        });
        if (refs.isEmpty()) {
            activeSpirits.remove(playerId);
            return 0;
        }
        return refs.size();
    }

    private void handleRainEnded(UUID playerId, World world, float time) {
        if (playerId == null || world == null || time <= 0f) {
            return;
        }
        float remaining = rainEndTimers.getOrDefault(playerId, despawnAfterRainEndS) - time;
        if (remaining > 0f) {
            rainEndTimers.put(playerId, remaining);
            return;
        }
        rainEndTimers.remove(playerId);
        despawnActive(playerId, world);
    }

    private void despawnActive(UUID playerId, World world) {
        if (playerId == null || world == null) {
            return;
        }
        List<Ref<EntityStore>> refs = activeSpirits.remove(playerId);
        if (refs == null || refs.isEmpty()) {
            return;
        }
        world.execute(() -> {
            Store<EntityStore> worldStore = world.getEntityStore() != null ? world.getEntityStore().getStore() : null;
            if (worldStore == null) {
                return;
            }
            for (Ref<EntityStore> ref : refs) {
                if (ref == null || !ref.isValid()) {
                    continue;
                }
                try {
                    worldStore.removeEntity(ref, RemoveReason.UNLOAD);
                } catch (Throwable ignored) {
                    // Best effort cleanup.
                }
            }
        });
    }

    private boolean spawnSpiritNearTransform(World world,
                                             Store<EntityStore> store,
                                             TransformComponent transform,
                                             UUID playerId) {
        if (world == null || store == null || transform == null || playerId == null) {
            return false;
        }
        if (transform.getPosition() == null) {
            return false;
        }

        Vector3d basePos = transform.getPosition().clone();
        double angle = ThreadLocalRandom.current().nextDouble(0d, Math.PI * 2d);
        double distance = rollSpawnDistance();
        Vector3d spawnPos = basePos.add(Math.cos(angle) * distance, 0.0d, Math.sin(angle) * distance);
        Vector3f spawnRot = transform.getRotation() != null
                ? transform.getRotation().clone()
                : new Vector3f(0f, 0f, 0f);

        if (world.getEntityStore() == null || world.getEntityStore().getStore() == null) {
            return false;
        }

        world.execute(() -> {
            Store<EntityStore> worldStore = world.getEntityStore() != null ? world.getEntityStore().getStore() : null;
            if (worldStore == null) {
                return;
            }
            NPCPlugin npcPlugin = NPCPlugin.get();
            if (npcPlugin == null) {
                logThrottled(playerId, "NPC plugin not available; Spirit_Thunder spawns skipped.", world);
                return;
            }
            Pair<Ref<EntityStore>, com.hypixel.hytale.server.core.universe.world.npc.INonPlayerCharacter> pair;
            try {
                String roleId = spiritRoleId;
                if (roleId == null || roleId.isBlank()) {
                    logThrottled(playerId, "Spirit role id is not configured; spawn skipped.", world);
                    return;
                }
                pair = npcPlugin.spawnNPC(worldStore, roleId, null, spawnPos, spawnRot);
            } catch (Throwable t) {
                logThrottled(playerId, "Failed to spawn Spirit_Thunder (exception).", t, world);
                return;
            }
            if (pair == null || pair.first() == null) {
                logThrottled(playerId, "Failed to spawn Spirit_Thunder (no role or null result).", world);
                return;
            }
            activeSpirits.computeIfAbsent(playerId, key -> new ArrayList<>()).add(pair.first());
        });

        return true;
    }

    private double rollSpawnDistance() {
        if (maxSpawnDistance <= minSpawnDistance) {
            return minSpawnDistance;
        }
        return ThreadLocalRandom.current().nextDouble(minSpawnDistance, maxSpawnDistance);
    }

    private boolean isRainingForWorld(World world, Store<EntityStore> store, TransformComponent transform) {
        if (world == null || store == null || transform == null) {
            return false;
        }

        var resourceType = WeatherResource.getResourceType();
        if (resourceType == null) {
            return false;
        }
        WeatherResource resource = store.getResource(resourceType);
        if (resource == null) {
            return false;
        }

        int weatherIndex = resource.getForcedWeatherIndex();
        if (weatherIndex == 0) {
            Ref<ChunkStore> chunkRef = transform.getChunkRef();
            if (chunkRef == null || !chunkRef.isValid()) {
                return false;
            }

            ChunkStore chunkStore = world.getChunkStore();
            if (chunkStore == null || chunkStore.getStore() == null) {
                return false;
            }

            BlockChunk chunk = chunkStore.getStore().getComponent(chunkRef, BlockChunk.getComponentType());
            if (chunk == null) {
                return false;
            }
            int environmentId = chunk.getEnvironment(transform.getPosition());
            weatherIndex = resource.getWeatherIndexForEnvironment(environmentId);
        }

        if (weatherIndex == 0 || weatherIndex == Integer.MIN_VALUE) {
            return false;
        }

        Weather weather = Weather.getAssetMap().getAsset(weatherIndex);
        if (weather == null || weather.getId() == null) {
            return false;
        }

        String id = weather.getId().toLowerCase(Locale.ROOT);
        String[] keywords = rainKeywords;
        for (String token : keywords) {
            if (id.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private void logThrottled(UUID playerId, String message, World world) {
        logThrottled(playerId, message, null, world);
    }

    private void logThrottled(UUID playerId, String message, Throwable error, World world) {
        if (playerId == null || message == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastLogAt.get(playerId);
        if (last != null && (now - last) < LOG_COOLDOWN_MS) {
            return;
        }
        lastLogAt.put(playerId, now);
        String worldName = world != null && world.getName() != null && !world.getName().isBlank()
                ? world.getName()
                : "unknown";
        System.out.println("[SocketReforge] " + message + " (world=" + worldName + ", player=" + playerId + ")");
        if (error != null) {
            System.out.println("[SocketReforge] Spawn exception: " + error.getClass().getSimpleName() + ": " + error.getMessage());
            error.printStackTrace();
        }
    }
}
