package irai.mod.reforge.Common;

import java.util.Set;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;

/**
 * Shared permission helpers for command and interaction validation.
 */
public final class PermissionUtils {

    private static final String OP_GROUP = "OP";

    private PermissionUtils() {}

    /**
     * Returns true if the player is OP (via group or op command permissions).
     */
    public static boolean isOperator(Player player) {
        if (player == null) {
            return false;
        }
        try {
            PermissionsModule permissionsModule = PermissionsModule.get();
            if (permissionsModule != null) {
                Set<String> groups = permissionsModule.getGroupsForUser(player.getUuid());
                if (groups != null) {
                    for (String group : groups) {
                        if (OP_GROUP.equalsIgnoreCase(group)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Fall back to explicit permission checks below.
        }

        try {
            return player.hasPermission(HytalePermissions.fromCommand("op.add"))
                    || player.hasPermission(HytalePermissions.fromCommand("op.remove"))
                    || player.hasPermission(HytalePermissions.fromCommand("op.self"));
        } catch (Exception ignored) {
            return false;
        }
    }
}
