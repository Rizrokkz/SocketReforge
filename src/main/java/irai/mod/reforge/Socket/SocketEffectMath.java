package irai.mod.reforge.Socket;

import java.util.Locale;
import java.util.Random;

import irai.mod.reforge.Util.LangLoader;

/**
 * Canonical math for essence tier effects and display strings.
 * Keep all tier->value logic here so gameplay and UI stay in sync.
 */
public final class SocketEffectMath {

    private static final double WEAPON_FLAT_DAMAGE_CHANCE = 0.25; // 25% flat, 75% percent

    private SocketEffectMath() {}

    public static int clampTier(int tier) {
        return Math.max(1, Math.min(5, tier));
    }

    public static double weaponIceDamageFlat(int tier) {
        return clampTier(tier);
    }

    public static double weaponLightningAttackSpeedPercent(int tier) {
        return clampTier(tier);
    }

    public static double weaponLightningCritChancePercent(int tier) {
        return clampTier(tier);
    }

    /**
     * Preserves legacy scaling used by current gameplay:
     * T1=1, T2=3, T3=7, T4=9, T5=13.
     */
    public static double weaponLifeStealPercent(int tier) {
        int safeTier = clampTier(tier);
        double percentTier = 1 + (safeTier - 1) * 2.0;
        if (safeTier >= 5) return percentTier + 4.0;
        if (safeTier >= 3) return percentTier + 2.0;
        return percentTier;
    }

    public static double weaponVoidCritDamagePercent(int tier) {
        return clampTier(tier) * 5.0;
    }

    public static double armorFireDefensePercent(int tier) {
        return clampTier(tier);
    }

    public static double armorIceSlowPercent(int tier) {
        return clampTier(tier);
    }

    public static double armorLightningEvasionPercent(int tier) {
        return clampTier(tier);
    }

    public static double armorLifeHealthFlat(int tier) {
        int safeTier = clampTier(tier);
        if (safeTier >= 5) return 50.0;
        if (safeTier >= 3) return 25.0;
        return 10.0;
    }

    public static double armorVoidDefensePercent(int tier) {
        return clampTier(tier);
    }

    public static double armorWaterRegenFlat(int tier) {
        return clampTier(tier);
    }

    /**
     * Split tier points into [% damage points, flat damage points] with lower flat odds.
     * Uses a deterministic seed derived from current socket layout.
     */
    public static double[] splitWeaponDamagePoints(SocketData socketData, Essence.Type type, int tierValue) {
        int safeTier = Math.max(0, tierValue);
        if (safeTier <= 0) {
            return new double[] {0.0, 0.0};
        }

        long seed = 1125899906842597L;
        if (socketData != null) {
            for (Socket socket : socketData.getSockets()) {
                seed = seed * 31 + socket.getSlotIndex();
                seed = seed * 31 + (socket.isBroken() ? 1 : 0);
                String essenceId = socket.getEssenceId();
                seed = seed * 31 + (essenceId != null ? essenceId.hashCode() : 0);
            }
        }
        seed = seed * 31 + (type == null ? 0 : type.ordinal());
        seed = seed * 31 + safeTier;

        Random seeded = new Random(seed);
        int flatPoints = 0;
        int percentPoints = 0;
        for (int i = 0; i < safeTier; i++) {
            if (seeded.nextDouble() < WEAPON_FLAT_DAMAGE_CHANCE) {
                flatPoints++;
            } else {
                percentPoints++;
            }
        }
        return new double[] {percentPoints, flatPoints};
    }

    public static String describeEffect(Essence.Type type, int tier, boolean isWeapon, SocketData socketData) {
        return describeEffect(type, tier, isWeapon, socketData, 1.0);
    }

    public static String describeEffect(Essence.Type type, int tier, boolean isWeapon, SocketData socketData, double multiplier) {
        if (type == null) {
            return "Unknown";
        }
        int safeTier = clampTier(tier);
        double safeMultiplier = multiplier <= 0.0 ? 1.0 : multiplier;

        if (isWeapon) {
            switch (type) {
                case FIRE:
                case WATER: {
                    double[] split = splitWeaponDamagePoints(socketData, type, safeTier);
                    double percent = split[0] * safeMultiplier;
                    double flat = split[1] * safeMultiplier;
                    if (percent > 0.0 && flat > 0.0) {
                        return "+" + formatBonus(percent) + "% DMG, +" + formatBonus(flat) + " Flat DMG";
                    }
                    if (percent > 0.0) {
                        return "+" + formatBonus(percent) + "% DMG";
                    }
                    return "+" + formatBonus(flat) + " Flat DMG";
                }
                case ICE:
                    return "+" + formatBonus(weaponIceDamageFlat(safeTier) * safeMultiplier) + " Cold DMG";
                case LIGHTNING:
                    return "+"
                            + formatBonus(weaponLightningAttackSpeedPercent(safeTier) * safeMultiplier)
                            + "% ATK Spd, +"
                            + formatBonus(weaponLightningCritChancePercent(safeTier) * safeMultiplier)
                            + "% Crit";
                case LIFE:
                    return "+" + formatBonus(weaponLifeStealPercent(safeTier) * safeMultiplier) + "% Lifesteal";
                case VOID: {
                    double critDmg = weaponVoidCritDamagePercent(safeTier) * safeMultiplier;
                    if (safeTier >= 5) {
                        return "+" + formatBonus(critDmg) + "% Crit DMG, Blood Pact (1% Max HP per equipped Void essence -> bonus DMG)";
                    }
                    return "+" + formatBonus(critDmg) + "% Crit DMG";
                }
                default:
                    return "Unknown";
            }
        }

        switch (type) {
            case FIRE:
                return "+" + formatBonus(armorFireDefensePercent(safeTier) * safeMultiplier) + "% Fire Defense";
            case ICE:
                return "+" + formatBonus(armorIceSlowPercent(safeTier) * safeMultiplier) + "% Slow";
            case LIGHTNING:
                return "+" + formatBonus(armorLightningEvasionPercent(safeTier) * safeMultiplier) + "% Evasion";
            case LIFE:
                return "+" + formatBonus(armorLifeHealthFlat(safeTier) * safeMultiplier) + " HP";
            case VOID:
                return "+" + formatBonus(armorVoidDefensePercent(safeTier) * safeMultiplier) + "% Defense";
            case WATER:
                return "+" + formatBonus(armorWaterRegenFlat(safeTier) * safeMultiplier) + " Regeneration";
            default:
                return "Unknown";
        }
    }

    public static String describeEffect(Essence.Type type, int tier, boolean isWeapon, SocketData socketData, double multiplier, String langCode) {
        if (type == null) {
            return translate(langCode, "tooltip.essence.unknown", "Unknown");
        }
        int safeTier = clampTier(tier);
        double safeMultiplier = multiplier <= 0.0 ? 1.0 : multiplier;

        if (isWeapon) {
            switch (type) {
                case FIRE:
                case WATER: {
                    double[] split = splitWeaponDamagePoints(socketData, type, safeTier);
                    double percent = split[0] * safeMultiplier;
                    double flat = split[1] * safeMultiplier;
                    if (percent > 0.0 && flat > 0.0) {
                        return translate(langCode, "tooltip.essence.weapon.damage_both",
                                "+" + formatBonus(percent) + "% DMG, +" + formatBonus(flat) + " Flat DMG",
                                formatBonus(percent), formatBonus(flat));
                    }
                    if (percent > 0.0) {
                        return translate(langCode, "tooltip.essence.weapon.damage_percent",
                                "+" + formatBonus(percent) + "% DMG",
                                formatBonus(percent));
                    }
                    return translate(langCode, "tooltip.essence.weapon.damage_flat",
                            "+" + formatBonus(flat) + " Flat DMG",
                            formatBonus(flat));
                }
                case ICE:
                    return translate(langCode, "tooltip.essence.weapon.ice",
                            "+" + formatBonus(weaponIceDamageFlat(safeTier) * safeMultiplier) + " Cold DMG",
                            formatBonus(weaponIceDamageFlat(safeTier) * safeMultiplier));
                case LIGHTNING:
                    return translate(langCode, "tooltip.essence.weapon.lightning",
                            "+" + formatBonus(weaponLightningAttackSpeedPercent(safeTier) * safeMultiplier)
                                    + "% ATK Spd, +" + formatBonus(weaponLightningCritChancePercent(safeTier) * safeMultiplier) + "% Crit",
                            formatBonus(weaponLightningAttackSpeedPercent(safeTier) * safeMultiplier),
                            formatBonus(weaponLightningCritChancePercent(safeTier) * safeMultiplier));
                case LIFE:
                    return translate(langCode, "tooltip.essence.weapon.life",
                            "+" + formatBonus(weaponLifeStealPercent(safeTier) * safeMultiplier) + "% Lifesteal",
                            formatBonus(weaponLifeStealPercent(safeTier) * safeMultiplier));
                case VOID: {
                    double critDmg = weaponVoidCritDamagePercent(safeTier) * safeMultiplier;
                    if (safeTier >= 5) {
                        String suffix = translate(langCode, "tooltip.essence.weapon.void_blood_pact_suffix",
                                "Blood Pact (1% Max HP per equipped Void essence -> bonus DMG)");
                        return translate(langCode, "tooltip.essence.weapon.void_blood_pact",
                                "+" + formatBonus(critDmg) + "% Crit DMG, " + suffix,
                                formatBonus(critDmg), suffix);
                    }
                    return translate(langCode, "tooltip.essence.weapon.void",
                            "+" + formatBonus(critDmg) + "% Crit DMG",
                            formatBonus(critDmg));
                }
                default:
                    return translate(langCode, "tooltip.essence.unknown", "Unknown");
            }
        }

        switch (type) {
            case FIRE:
                return translate(langCode, "tooltip.essence.armor.fire",
                        "+" + formatBonus(armorFireDefensePercent(safeTier) * safeMultiplier) + "% Fire Defense",
                        formatBonus(armorFireDefensePercent(safeTier) * safeMultiplier));
            case ICE:
                return translate(langCode, "tooltip.essence.armor.ice",
                        "+" + formatBonus(armorIceSlowPercent(safeTier) * safeMultiplier) + "% Slow",
                        formatBonus(armorIceSlowPercent(safeTier) * safeMultiplier));
            case LIGHTNING:
                return translate(langCode, "tooltip.essence.armor.lightning",
                        "+" + formatBonus(armorLightningEvasionPercent(safeTier) * safeMultiplier) + "% Evasion",
                        formatBonus(armorLightningEvasionPercent(safeTier) * safeMultiplier));
            case LIFE:
                return translate(langCode, "tooltip.essence.armor.life",
                        "+" + formatBonus(armorLifeHealthFlat(safeTier) * safeMultiplier) + " HP",
                        formatBonus(armorLifeHealthFlat(safeTier) * safeMultiplier));
            case VOID:
                return translate(langCode, "tooltip.essence.armor.void",
                        "+" + formatBonus(armorVoidDefensePercent(safeTier) * safeMultiplier) + "% Defense",
                        formatBonus(armorVoidDefensePercent(safeTier) * safeMultiplier));
            case WATER:
                return translate(langCode, "tooltip.essence.armor.water",
                        "+" + formatBonus(armorWaterRegenFlat(safeTier) * safeMultiplier) + " Regeneration",
                        formatBonus(armorWaterRegenFlat(safeTier) * safeMultiplier));
            default:
                return translate(langCode, "tooltip.essence.unknown", "Unknown");
        }
    }

    private static String translate(String langCode, String key, String fallback, Object... params) {
        String template = LangLoader.getTranslationForLanguage(key, langCode);
        if (template == null || template.isBlank() || template.equals(key)) {
            template = fallback;
        }
        if (params.length == 0) {
            return template;
        }
        for (int i = 0; i < params.length; i++) {
            template = template.replace("{" + i + "}", String.valueOf(params[i]));
        }
        return template;
    }

    public static String formatBonus(double value) {
        if (Math.abs(value - Math.rint(value)) < 1e-9) {
            return String.valueOf((int) Math.rint(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
