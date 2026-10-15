package irai.mod.reforge.Socket;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.hypixel.hytale.server.core.entity.entities.Player;

/**
 * Per-player server-log tracing for the Rune Delta resonance.
 */
public final class RuneDeltaDebug {
    private static final Set<UUID> ENABLED_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final String PREFIX = "[SocketReforge][RuneDelta]";

    private RuneDeltaDebug() {}

    public static boolean toggle(Player player) {
        UUID playerId = playerId(player);
        if (playerId == null) {
            return false;
        }
        if (!ENABLED_PLAYERS.add(playerId)) {
            ENABLED_PLAYERS.remove(playerId);
            return false;
        }
        return true;
    }

    public static boolean setEnabled(Player player, boolean enabled) {
        UUID playerId = playerId(player);
        if (playerId == null) {
            return false;
        }
        if (enabled) {
            ENABLED_PLAYERS.add(playerId);
        } else {
            ENABLED_PLAYERS.remove(playerId);
        }
        return enabled;
    }

    public static boolean isEnabled(Player player) {
        UUID playerId = playerId(player);
        return playerId != null && ENABLED_PLAYERS.contains(playerId);
    }

    public static void logKv(Player player, String event, Object... keyValues) {
        UUID playerId = playerId(player);
        if (playerId == null || !ENABLED_PLAYERS.contains(playerId)) {
            return;
        }

        StringBuilder line = new StringBuilder(PREFIX)
                .append(" player=").append(playerId);
        if (event != null && !event.isBlank()) {
            line.append(' ').append(event);
        }
        if (keyValues != null) {
            for (int index = 0; index + 1 < keyValues.length; index += 2) {
                line.append(' ').append(keyValues[index])
                        .append('=').append(formatValue(keyValues[index + 1]));
            }
        }
        System.out.println(line);
    }

    private static UUID playerId(Player player) {
        return player == null ? null : player.getUuid();
    }

    private static String formatValue(Object value) {
        if (value instanceof Double number) {
            return String.format(Locale.ROOT, "%.4f", number);
        }
        if (value instanceof Float number) {
            return String.format(Locale.ROOT, "%.4f", number);
        }
        return String.valueOf(value);
    }
}
