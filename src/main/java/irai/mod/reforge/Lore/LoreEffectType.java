package irai.mod.reforge.Lore;

import java.util.Locale;

/**
 * Lore ability effect types.
 */
public enum LoreEffectType {
    DAMAGE_TARGET,
    DAMAGE_ATTACKER,
    HEAL_SELF,
    HEAL_DEFENDER,
    HEAL_SELF_OVER_TIME,
    HEAL_AREA,
    HEAL_AREA_OVER_TIME,
    LIFESTEAL,
    APPLY_BURN,
    APPLY_FREEZE,
    APPLY_SHOCK,
    APPLY_BLEED,
    APPLY_POISON,
    APPLY_SLOW,
    APPLY_WEAKNESS,
    APPLY_BLIND,
    APPLY_ROOT,
    APPLY_STUN,
    APPLY_FEAR,
    APPLY_HASTE,
    APPLY_INVISIBLE,
    APPLY_SHIELD,
    DOUBLE_CAST,
    MULTI_HIT,
    VORTEXSTRIKE,
    CRIT_CHARGE,
    BERSERK,
    SUMMON_WOLF_PACK,
    CHARGE_ATTACK,
    OMNISLASH,
    OCTASLASH,
    PUMMEL,
    BLOOD_RUSH,
    CAUSTIC_FINALE,
    SHRAPNEL_FINALE,
    BURN_FINALE,
    DRAIN_LIFE;

    public static LoreEffectType fromString(String raw, LoreEffectType fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        switch (normalized) {
            case "BLEED":
                return APPLY_BLEED;
            case "POISON":
                return APPLY_POISON;
            case "SLOW":
            case "SLOWNESS":
                return APPLY_SLOW;
            case "WEAK":
            case "WEAKNESS":
                return APPLY_WEAKNESS;
            case "BLIND":
            case "BLINDNESS":
                return APPLY_BLIND;
            case "ROOT":
                return APPLY_ROOT;
            case "STUN":
                return APPLY_STUN;
            case "FEAR":
                return APPLY_FEAR;
            case "HASTE":
            case "SPEED":
                return APPLY_HASTE;
            case "INVISIBLE":
            case "INVISIBILITY":
                return APPLY_INVISIBLE;
            case "SHIELD":
            case "BARRIER":
                return APPLY_SHIELD;
            case "LIFE_STEAL":
            case "LIFESTEAL":
                return LIFESTEAL;
            case "HEAL_SELF_HOT":
            case "HEAL_SELF_OVER_TIME":
            case "SELF_HOT":
            case "HOT_SELF":
                return HEAL_SELF_OVER_TIME;
            case "HEAL_AREA":
            case "AREA_HEAL":
            case "HEAL_AOE":
            case "AOE_HEAL":
            case "HEAL_NEARBY":
                return HEAL_AREA;
            case "HEAL_AREA_HOT":
            case "HEAL_AREA_OVER_TIME":
            case "AREA_HOT":
            case "AOE_HOT":
                return HEAL_AREA_OVER_TIME;
            case "DOUBLE":
            case "DOUBLE_CAST":
            case "DOUBLE_STRIKE":
            case "ECHO":
            case "ECHO_CAST":
                return DOUBLE_CAST;
            case "MULTI":
            case "MULTI_HIT":
            case "MULTIHIT":
                return MULTI_HIT;
            case "VORTEXSTRIKE":
            case "VORTEX_STRIKE":
                return VORTEXSTRIKE;
            case "CRIT_CHARGE":
            case "CRIT_BOOST":
            case "CRIT_DAMAGE":
            case "CRIT":
                return CRIT_CHARGE;
            case "BERSERK":
            case "RAGE":
                return BERSERK;
            case "WOLF_PACK":
            case "WOLFPACK":
            case "SUMMON_WOLF":
            case "SUMMON_WOLFPACK":
            case "SUMMON_WOLF_PACK":
                return SUMMON_WOLF_PACK;
            case "CHARGE_ATTACK":
            case "CHARGED_ATTACK":
            case "CHARGE_STRIKE":
            case "CHARGED_STRIKE":
            case "CHARGE_SWING":
                return CHARGE_ATTACK;
            case "OMNISLASH":
            case "OMNI_SLASH":
                return OMNISLASH;
            case "OCTASLASH":
            case "OCTA_SLASH":
                return OCTASLASH;
            case "PUMMEL":
                return PUMMEL;
            case "BLOOD_RUSH":
            case "BLOODRUSH":
                return BLOOD_RUSH;
            case "CAUSTIC":
            case "CAUSTIC_FINALE":
                return CAUSTIC_FINALE;
            case "SHRAPNEL":
            case "SHRAPNEL_FINALE":
                return SHRAPNEL_FINALE;
            case "BURN_FINALE":
            case "INCINERATE":
            case "INCINERATION":
                return BURN_FINALE;
            case "DRAIN":
            case "DRAIN_LIFE":
            case "LIFE_DRAIN":
            case "LIFEDRAIN":
            case "VAMPIRIC":
                return DRAIN_LIFE;
            default:
                break;
        }
        try {
            return LoreEffectType.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
