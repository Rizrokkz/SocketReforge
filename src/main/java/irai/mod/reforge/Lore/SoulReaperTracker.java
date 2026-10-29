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

    public static void arm(UUID playerId, Ref<EntityStore> targetRef) {
        if (playerId == null || targetRef == null || !targetRef.isValid()) {
            return;
        }
        PENDING.put(playerId, new PendingReap(targetRef, System.currentTimeMillis() + PENDING_WINDOW_MS));
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

    public static void consume(UUID playerId, Ref<EntityStore> targetRef) {
        if (isArmedFor(playerId, targetRef)) {
            PENDING.remove(playerId);
        }
    }

    private record PendingReap(Ref<EntityStore> targetRef, long expiresAt) {}
}
