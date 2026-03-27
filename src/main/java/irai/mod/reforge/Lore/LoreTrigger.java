package irai.mod.reforge.Lore;

import java.util.Locale;

/**
 * Lore ability trigger types.
 */
public enum LoreTrigger {
    ON_HIT,
    ON_CRIT,
    ON_KILL,
    ON_DAMAGED,
    ON_BLOCK,
    ON_BLOCKED,
    ON_NEAR_DEATH,
    ON_FIRST_KILL,
    ON_LORE_PROC,
    ON_HEAL,
    ON_POTION_USE,
    ON_STATUS_APPLY,
    ON_SPRINT,
    ON_JUMP,
    ON_SNEAK,
    ON_SKILL_USE;

    public static LoreTrigger fromString(String raw, LoreTrigger fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        switch (normalized) {
            case "ON_DAMAGE_TAKEN":
            case "ON_DAMAGE":
            case "ON_HURT":
                return ON_DAMAGED;
            case "ON_BLOCKED_HIT":
                return ON_BLOCKED;
            case "ON_FIRST_KILL":
            case "ON_FIRST_KILL_SPIRIT":
                return ON_FIRST_KILL;
            case "ON_LORE_PROC":
            case "ON_SPIRIT_PROC":
                return ON_LORE_PROC;
            default:
                break;
        }
        try {
            return LoreTrigger.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
