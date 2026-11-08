package irai.mod.reforge.UI;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;

/** Opens the character path using the server's native custom UI page system. */
public final class CharacterPathUI {
    private CharacterPathUI() {}

    /** Kept as a lifecycle hook for plugin setup; the native page has no optional UI dependency. */
    public static void initialize() {}

    public static boolean isAvailable() { return true; }

    public static void open(Player player) {
        if (player == null) return;
        PlayerRef playerRef = player.getPlayerRef();
        player.getWorld().execute(() -> CharacterPathPage.open(playerRef, null, 100));
    }

    /** Opens the operator authoring canvas. */
    public static void openEditor(Player player) {
        if (player == null) return;
        PlayerRef playerRef = player.getPlayerRef();
        player.getWorld().execute(() -> CharacterPathEditorPage.open(playerRef));
    }

    /** Branch arguments are retained for existing command/UI callers; the editor opens on the blank canvas. */
    public static void open(Player player, irai.mod.reforge.Progression.CharacterPathService.Category ignored) {
        open(player);
    }

    /** Native page state is owned by PageManager and is discarded with the player session. */
    public static void closeForDisconnect(PlayerRef playerRef) {}
}
