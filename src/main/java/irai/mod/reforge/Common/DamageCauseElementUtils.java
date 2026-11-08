package irai.mod.reforge.Common;

import java.util.Locale;

import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;

/** Identifies native fire and burn damage causes. */
public final class DamageCauseElementUtils {
    private DamageCauseElementUtils() {}

    public static boolean isFireDamageCause(DamageCause cause) {
        if (cause == null || cause.getId() == null) {
            return false;
        }
        String id = cause.getId().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        return containsElementToken(id, "fire", "burn", "burning", "flame");
    }

    private static boolean containsElementToken(String id, String... tokens) {
        for (String token : tokens) {
            if (id.equals(token) || id.endsWith("_" + token) || id.contains("_" + token + "_")) {
                return true;
            }
        }
        return false;
    }
}
