package irai.mod.reforge.Lore;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Lightweight runtime tracker for temporary lore states that affect gameplay.
 */
public final class LoreStatusTracker {
    private static final ConcurrentMap<UUID, Long> BLUR_UNTIL = new ConcurrentHashMap<>();

    private LoreStatusTracker() {}

    public static void applyBlur(UUID playerId, long durationMs) {
        if (playerId == null || durationMs <= 0L) {
            return;
        }
        long now = System.currentTimeMillis();
        long until = now + durationMs;
        BLUR_UNTIL.merge(playerId, until, Math::max);
    }

    public static boolean isBlurActive(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        Long until = BLUR_UNTIL.get(playerId);
        if (until == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now >= until) {
            BLUR_UNTIL.remove(playerId, until);
            return false;
        }
        return true;
    }

    public static long getBlurRemainingMs(UUID playerId) {
        if (playerId == null) {
            return 0L;
        }
        Long until = BLUR_UNTIL.get(playerId);
        if (until == null) {
            return 0L;
        }
        long now = System.currentTimeMillis();
        if (now >= until) {
            BLUR_UNTIL.remove(playerId, until);
            return 0L;
        }
        return Math.max(0L, until - now);
    }

    public static void clearBlur(UUID playerId) {
        if (playerId == null) {
            return;
        }
        BLUR_UNTIL.remove(playerId);
    }
}
