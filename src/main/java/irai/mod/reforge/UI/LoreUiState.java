package irai.mod.reforge.UI;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.hypixel.hytale.server.core.universe.PlayerRef;

/**
 * Tracks which lore UI page is currently active per player to prevent
 * stale refresh tasks from reopening the wrong page after navigation.
 */
final class LoreUiState {
    enum Page {
        SOCKET,
        FEED
    }

    private static final Map<PlayerRef, Page> activePages = new ConcurrentHashMap<>();

    private LoreUiState() {}

    static void setActive(PlayerRef playerRef, Page page) {
        if (playerRef == null || page == null) {
            return;
        }
        activePages.put(playerRef, page);
    }

    static boolean isActive(PlayerRef playerRef, Page page) {
        if (playerRef == null || page == null) {
            return false;
        }
        return page == activePages.get(playerRef);
    }

    static void clear(PlayerRef playerRef) {
        if (playerRef == null) {
            return;
        }
        activePages.remove(playerRef);
    }
}
