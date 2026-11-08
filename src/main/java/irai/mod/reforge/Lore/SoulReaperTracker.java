package irai.mod.reforge.Lore;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Tracks the exact NPC struck by a successful Soul Reaper projectile. */
public final class SoulReaperTracker {
    public static final String RUNE_ITEM_ID = "Rune_Soul_Reaper";
    private static final long PENDING_WINDOW_MS = 8000L;
    private static final Map<UUID, PendingReap> PENDING = new ConcurrentHashMap<>();

    private SoulReaperTracker() {}

    public static void arm(UUID playerId, Ref<EntityStore> playerRef, Ref<EntityStore> targetRef) {
        if (playerId == null || playerRef == null || !playerRef.isValid()
                || targetRef == null || !targetRef.isValid()) {
            return;
        }
        PENDING.put(playerId, new PendingReap(playerId, playerRef, targetRef,
                System.currentTimeMillis() + PENDING_WINDOW_MS));
    }

    public static void clear(UUID playerId) {
        if (playerId != null) {
            PENDING.remove(playerId);
        }
    }

    public static boolean isArmedFor(UUID playerId, Ref<EntityStore> targetRef) {
        if (playerId == null || targetRef == null) {
            return false;
        }
        PendingReap pending = PENDING.get(playerId);
        if (pending == null) {
            return false;
        }
        if (pending.expiresAt < System.currentTimeMillis()) {
            PENDING.remove(playerId, pending);
            return false;
        }
        return targetRef.equals(pending.targetRef);
    }

    /** Removes and returns the player who marked this exact target, if the mark is still active. */
    public static PendingReap consumeForTarget(Ref<EntityStore> targetRef, UUID playerId) {
        if (targetRef == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, PendingReap> entry : PENDING.entrySet()) {
            PendingReap pending = entry.getValue();
            if (pending == null) {
                PENDING.remove(entry.getKey());
                continue;
            }
            if (pending.expiresAt < now) {
                PENDING.remove(entry.getKey(), pending);
                continue;
            }
            if (targetRef.equals(pending.targetRef)
                    && (playerId == null || playerId.equals(pending.playerId))
                    && PENDING.remove(entry.getKey(), pending)) {
                return pending;
            }
        }
        return null;
    }

    public static void consume(UUID playerId, Ref<EntityStore> targetRef) {
        if (isArmedFor(playerId, targetRef)) {
            PENDING.remove(playerId);
        }
    }

    public record PendingReap(UUID playerId,
                              Ref<EntityStore> playerRef,
                              Ref<EntityStore> targetRef,
                              long expiresAt) {}
}
