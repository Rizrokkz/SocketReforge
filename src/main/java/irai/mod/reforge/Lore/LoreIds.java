package irai.mod.reforge.Lore;

import java.util.Locale;

/**
 * Shared identifier normalization helpers for lore systems.
 */
public final class LoreIds {
    private LoreIds() {}

    public static String normalizeSpiritId(String spiritId) {
        return spiritId.trim().toLowerCase(Locale.ROOT);
    }
}
