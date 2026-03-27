package irai.mod.reforge.Lore;

import java.util.Locale;

import irai.mod.reforge.Util.LangLoader;

/**
 * Represents a lore ability bound to a spirit.
 */
public final class LoreAbility {
    private static final int OMNISLASH_BASE_HITS = 2;
    private static final int OMNISLASH_MAX_HITS = 8;
    private static final int OCTASLASH_BASE_HITS = 2;
    private static final int OCTASLASH_MAX_HITS = 8;
    private static final int PUMMEL_BASE_HITS = 2;
    private static final int PUMMEL_MAX_HITS = 3;
    private static final int BLOOD_RUSH_BASE_HITS = 3;
    private static final int BLOOD_RUSH_MAX_HITS = 6;
    private static final int CHARGE_ATTACK_BASE_HITS = 1;
    private static final int CHARGE_ATTACK_MAX_HITS = 3;
    public static final double BASE_HEAL_AREA_RADIUS = 6.0d;
    public static final double BASE_OMNISLASH_RADIUS = 12.0d;
    public static final double BASE_VORTEXSTRIKE_RADIUS = 3.5d;
    public static final double BASE_WHIRLWIND_RADIUS = 3.5d;
    public static final int BASE_HOT_TICKS = 4;
    public static final long BASE_HOT_TICK_MS = 1000L;
    public static final long BASE_AREA_HEAL_DURATION_MS = 3000L;
    public static final long BASE_AREA_HEAL_TICK_MS = 1000L;
    public static final long BASE_BERSERK_DURATION_MS = 3000L;
    private static final double STUN_DEFAULT_SECONDS = 1.5d;
    private static final double STUN_MIN_SECONDS = 0.6d;
    private static final double STUN_MAX_SECONDS = 4.0d;
    private static final double FEED_CHANCE_BONUS_PER_TIER = 0.10d;
    private static final double FEED_COOLDOWN_REDUCTION_PER_TIER = 0.05d;
    private static final double FEED_RADIUS_BONUS_PER_TIER = 0.10d;
    private static final double FEED_DURATION_BONUS_PER_TIER = 0.15d;
    private static final double FEED_VALUE_BONUS_PER_TIER = 0.10d;
    private static final double MAX_PROC_CHANCE = 0.75d;
    private static final double MIN_COOLDOWN_FACTOR = 0.40d;
    private static final double MAX_RADIUS_FACTOR = 2.0d;
    private static final double MAX_DURATION_FACTOR = 2.5d;
    private static final long MIN_COOLDOWN_MS = 250L;
    private final String spiritId;
    private final LoreTrigger trigger;
    private final double procChance;
    private final long cooldownMs;
    private final LoreEffectType effectType;
    private final double baseValue;
    private final double perLevel;
    private final String abilityNameKey;
    private final String abilityNameFallback;

    public LoreAbility(String spiritId,
                       LoreTrigger trigger,
                       double procChance,
                       long cooldownMs,
                       LoreEffectType effectType,
                       double baseValue,
                       double perLevel) {
        this(spiritId, trigger, procChance, cooldownMs, effectType, baseValue, perLevel, null, null);
    }

    public LoreAbility(String spiritId,
                       LoreTrigger trigger,
                       double procChance,
                       long cooldownMs,
                       LoreEffectType effectType,
                       double baseValue,
                       double perLevel,
                       String abilityNameKey,
                       String abilityNameFallback) {
        this.spiritId = spiritId;
        this.trigger = trigger;
        this.procChance = procChance;
        this.cooldownMs = cooldownMs;
        this.effectType = effectType;
        this.baseValue = baseValue;
        this.perLevel = perLevel;
        this.abilityNameKey = abilityNameKey;
        this.abilityNameFallback = abilityNameFallback;
    }

    public String getSpiritId() {
        return spiritId;
    }

    public LoreTrigger getTrigger() {
        return trigger;
    }

    public double getProcChance() {
        return procChance;
    }

    public long getCooldownMs() {
        return cooldownMs;
    }

    public LoreEffectType getEffectType() {
        return effectType;
    }

    public double getBaseValue() {
        return baseValue;
    }

    public double getPerLevel() {
        return perLevel;
    }

    public String getAbilityNameKey() {
        return abilityNameKey;
    }

    public String getAbilityNameFallback() {
        return abilityNameFallback;
    }

    public double getValueForLevel(int level) {
        int safeLevel = Math.max(0, level);
        return baseValue + (perLevel * safeLevel);
    }

    public static double scaleProcChance(double baseChance, int feedTier) {
        if (baseChance <= 0.0d) {
            return 0.0d;
        }
        int tier = Math.max(0, feedTier);
        double scaled = baseChance * (1.0d + (FEED_CHANCE_BONUS_PER_TIER * tier));
        return Math.min(MAX_PROC_CHANCE, Math.max(0.0d, scaled));
    }

    public static long scaleCooldownMs(long baseCooldownMs, int feedTier) {
        if (baseCooldownMs <= 0L) {
            return 0L;
        }
        int tier = Math.max(0, feedTier);
        double factor = 1.0d - (FEED_COOLDOWN_REDUCTION_PER_TIER * tier);
        factor = Math.max(MIN_COOLDOWN_FACTOR, factor);
        long scaled = Math.round(baseCooldownMs * factor);
        return Math.max(MIN_COOLDOWN_MS, scaled);
    }

    public static double scaleRadius(double baseRadius, int feedTier) {
        double base = Math.max(0.0d, baseRadius);
        int tier = Math.max(0, feedTier);
        double factor = 1.0d + (FEED_RADIUS_BONUS_PER_TIER * tier);
        factor = Math.min(MAX_RADIUS_FACTOR, factor);
        return base * factor;
    }

    public static double scaleDurationFactor(int feedTier) {
        int tier = Math.max(0, feedTier);
        double factor = 1.0d + (FEED_DURATION_BONUS_PER_TIER * tier);
        return Math.min(MAX_DURATION_FACTOR, factor);
    }

    public static long scaleDurationMs(long baseDurationMs, int feedTier) {
        if (baseDurationMs <= 0L) {
            return 0L;
        }
        double factor = scaleDurationFactor(feedTier);
        long scaled = Math.round(baseDurationMs * factor);
        return Math.max(1L, scaled);
    }

    public static int resolveHotTicks(int feedTier) {
        double factor = scaleDurationFactor(feedTier);
        int ticks = (int) Math.round(BASE_HOT_TICKS * factor);
        return Math.max(1, ticks);
    }

    public static long resolveHotDurationMs(int feedTier) {
        return (long) resolveHotTicks(feedTier) * BASE_HOT_TICK_MS;
    }

    public static long resolveAreaHealDurationMs(int feedTier) {
        return scaleDurationMs(BASE_AREA_HEAL_DURATION_MS, feedTier);
    }

    public static int resolveAreaHealTicks(int feedTier) {
        long duration = resolveAreaHealDurationMs(feedTier);
        long tickMs = Math.max(1L, BASE_AREA_HEAL_TICK_MS);
        int ticks = (int) Math.ceil((double) duration / (double) tickMs);
        return Math.max(1, ticks);
    }

    public static long resolveBerserkDurationMs(int feedTier) {
        return BASE_BERSERK_DURATION_MS;
    }

    public static long resolveStunFreezeDurationMs(double value, int feedTier) {
        double seconds = value > 0.0d ? value : STUN_DEFAULT_SECONDS;
        seconds = Math.max(STUN_MIN_SECONDS, Math.min(STUN_MAX_SECONDS, seconds));
        long baseMs = Math.round(seconds * 1000.0d);
        return scaleDurationMs(baseMs, feedTier);
    }

    public static double scaleEffectValue(double value, int feedTier) {
        int tier = Math.max(0, feedTier);
        double factor = 1.0d + (FEED_VALUE_BONUS_PER_TIER * tier);
        return value * factor;
    }

    public String describe(String langCode, int level) {
        return describe(langCode, level, 0);
    }

    public String describe(String langCode, int level, int feedTier) {
        String effect = describeEffect(langCode, level, feedTier);
        String abilityName = resolveAbilityName(langCode);
        if (abilityName != null && !abilityName.isBlank()) {
            effect = abilityName + ": " + effect;
        }
        String chance = formatPercent(scaleProcChance(procChance, feedTier) * 100.0);
        String cooldown = formatSeconds(scaleCooldownMs(cooldownMs, feedTier));
        String procTemplate = LangLoader.getTranslationForLanguage(
                "tooltip.lore.proc",
                langCode
        );
        if (procTemplate == null || procTemplate.isBlank() || procTemplate.equals("tooltip.lore.proc")) {
            procTemplate = "{0}% chance, {1}s CD";
        }
        String proc = procTemplate
                .replace("{0}", chance)
                .replace("{1}", cooldown);
        String triggerLabel = LangLoader.getTranslationForLanguage("tooltip.lore.trigger_label", langCode);
        if (triggerLabel == null || triggerLabel.isBlank() || triggerLabel.equals("tooltip.lore.trigger_label")) {
            triggerLabel = "Trigger";
        }
        String triggerText = describeTrigger(langCode);
        return effect + " (" + proc + ", " + triggerLabel + ": " + triggerText + ")";
    }

    public String describeEffectOnly(String langCode, int level, int feedTier) {
        return describeEffect(langCode, level, feedTier);
    }

    public String describeTriggerText(String langCode) {
        return describeTrigger(langCode);
    }

    public String resolveAbilityName(String langCode) {
        if (abilityNameKey == null || abilityNameKey.isBlank()) {
            return null;
        }
        String localized = LangLoader.getTranslationForLanguage(abilityNameKey, langCode);
        if (localized == null || localized.isBlank() || localized.equals(abilityNameKey)) {
            return abilityNameFallback;
        }
        return localized;
    }

    private String describeTrigger(String langCode) {
        String key = trigger == null ? null : "tooltip.lore.trigger." + trigger.name().toLowerCase(Locale.ROOT);
        String fallback = "On Hit";
        if (trigger != null) {
            switch (trigger) {
                case ON_CRIT -> fallback = "On Crit";
                case ON_KILL -> fallback = "On Kill";
                case ON_DAMAGED -> fallback = "On Damaged";
                case ON_BLOCK -> fallback = "On Block";
                case ON_BLOCKED -> fallback = "On Blocked";
                case ON_NEAR_DEATH -> fallback = "Near Death";
                case ON_FIRST_KILL -> fallback = "First Kill";
                case ON_LORE_PROC -> fallback = "On Lore Proc";
                case ON_HEAL -> fallback = "On Heal";
                case ON_POTION_USE -> fallback = "On Potion Use";
                case ON_STATUS_APPLY -> fallback = "On Status Apply";
                case ON_SPRINT -> fallback = "On Sprint";
                case ON_JUMP -> fallback = "On Jump";
                case ON_SNEAK -> fallback = "On Sneak";
                case ON_SKILL_USE -> fallback = "On Skill Use";
                case ON_HIT -> fallback = "On Hit";
            }
        }
        if (key == null) {
            return fallback;
        }
        String localized = LangLoader.getTranslationForLanguage(key, langCode);
        if (localized == null || localized.isBlank() || localized.equals(key)) {
            return fallback;
        }
        return localized;
    }

    private String describeEffect(String langCode, int level, int feedTier) {
        double value = getValueForLevel(level);
        String formatted = formatValue(value);
        int safeFeedTier = Math.max(0, feedTier);
        String key;
        String fallback;
        switch (effectType) {
            case DAMAGE_TARGET -> {
                key = "tooltip.lore.effect.damage_target";
                fallback = "Deals {0} bonus damage";
                return formatEffect(langCode, key, fallback, formatted);
            }
            case DAMAGE_ATTACKER -> {
                key = "tooltip.lore.effect.damage_attacker";
                fallback = "Thorns: {0} damage";
                return formatEffect(langCode, key, fallback, formatted);
            }
            case HEAL_SELF -> {
                key = "tooltip.lore.effect.heal_self";
                fallback = "Heals {0} HP";
                return formatEffect(langCode, key, fallback, formatted);
            }
            case HEAL_DEFENDER -> {
                key = "tooltip.lore.effect.heal_defender";
                fallback = "Heals {0} HP";
                return formatEffect(langCode, key, fallback, formatted);
            }
            case HEAL_SELF_OVER_TIME -> {
                key = "tooltip.lore.effect.heal_self_hot";
                fallback = "Heals {0} HP over time";
                String effect = formatEffect(langCode, key, fallback, formatted);
                long durationMs = resolveHotDurationMs(safeFeedTier);
                return effect + " (" + formatDurationSuffix(langCode, durationMs) + ")";
            }
            case HEAL_AREA -> {
                key = "tooltip.lore.effect.heal_area";
                fallback = "Heals nearby allies for {0} HP";
                double scaledValue = scaleEffectValue(value, safeFeedTier);
                String effect = formatEffect(langCode, key, fallback, formatValue(scaledValue));
                double radius = scaleRadius(BASE_HEAL_AREA_RADIUS, safeFeedTier);
                long durationMs = resolveAreaHealDurationMs(safeFeedTier);
                return effect + " (" + formatRadiusSuffix(langCode, radius) + ", "
                        + formatDurationSuffix(langCode, durationMs) + ")";
            }
            case HEAL_AREA_OVER_TIME -> {
                key = "tooltip.lore.effect.heal_area_hot";
                fallback = "Heals nearby allies for {0} HP over time";
                double scaledValue = scaleEffectValue(value, safeFeedTier);
                String effect = formatEffect(langCode, key, fallback, formatValue(scaledValue));
                double radius = scaleRadius(BASE_HEAL_AREA_RADIUS, safeFeedTier);
                long durationMs = resolveHotDurationMs(safeFeedTier);
                return effect + " (" + formatRadiusSuffix(langCode, radius) + ", "
                        + formatDurationSuffix(langCode, durationMs) + ")";
            }
            case LIFESTEAL -> {
                double percentValue = value <= 1.0 ? value * 100.0 : value;
                String percent = formatPercent(percentValue);
                key = "tooltip.lore.effect.lifesteal";
                fallback = "Lifesteal {0}%";
                return formatEffect(langCode, key, fallback, percent);
            }
            case APPLY_BURN -> {
                key = "tooltip.lore.effect.burn";
                fallback = "Burns target";
                return formatEffect(langCode, key, fallback, formatted);
            }
            case APPLY_FREEZE -> {
                key = "tooltip.lore.effect.freeze";
                fallback = "Freezes target";
                return formatEffect(langCode, key, fallback, formatted);
            }
            case APPLY_SHOCK -> {
                key = "tooltip.lore.effect.shock";
                fallback = "Shocks target";
                return formatEffect(langCode, key, fallback, formatted);
            }
            case APPLY_BLEED -> {
                key = "tooltip.lore.effect.bleed";
                fallback = "Bleeds target";
                return formatEffect(langCode, key, fallback, formatted);
            }
            case APPLY_POISON -> {
                key = "tooltip.lore.effect.poison";
                fallback = "Poisons target";
                return formatEffect(langCode, key, fallback, formatted);
            }
            case APPLY_SLOW -> {
                key = "tooltip.lore.effect.slow";
                fallback = "Slows target";
                return formatEffect(langCode, key, fallback, formatted);
            }
            case APPLY_WEAKNESS -> {
                key = "tooltip.lore.effect.weakness";
                fallback = "Weakens target";
                return formatEffect(langCode, key, fallback, formatted);
            }
            case APPLY_BLIND -> {
                key = "tooltip.lore.effect.blind";
                fallback = "Blinds target";
                return formatEffect(langCode, key, fallback, formatted);
            }
            case APPLY_ROOT -> {
                key = "tooltip.lore.effect.root";
                fallback = "Roots target";
                return formatEffect(langCode, key, fallback, formatted);
            }
            case APPLY_STUN -> {
                key = "tooltip.lore.effect.stun";
                fallback = "Stuns target";
                String effect = formatEffect(langCode, key, fallback, formatted);
                long durationMs = resolveStunFreezeDurationMs(value, safeFeedTier);
                return effect + " (" + formatDurationSuffix(langCode, durationMs) + ")";
            }
            case APPLY_FEAR -> {
                key = "tooltip.lore.effect.fear";
                fallback = "Fears target";
                return formatEffect(langCode, key, fallback, formatted);
            }
            case APPLY_HASTE -> {
                key = "tooltip.lore.effect.haste";
                fallback = "Gain haste";
                return formatEffect(langCode, key, fallback, formatted);
            }
            case APPLY_INVISIBLE -> {
                key = "tooltip.lore.effect.invisible";
                fallback = "Become invisible";
                return formatEffect(langCode, key, fallback, formatted);
            }
            case APPLY_SHIELD -> {
                key = "tooltip.lore.effect.shield";
                fallback = "Gain a shield";
                return formatEffect(langCode, key, fallback, formatted);
            }
            case DOUBLE_CAST -> {
                double scaledValue = scaleEffectValue(value, safeFeedTier);
                String percent = formatPercent(toPercent(scaledValue));
                key = "tooltip.lore.effect.double_cast";
                fallback = "Double cast: +{0}% damage";
                return formatEffect(langCode, key, fallback, percent);
            }
            case MULTI_HIT -> {
                int extraHits = Math.max(1, Math.min(3, calcExtraHits(value) + safeFeedTier));
                String percent = formatPercent(toPercent(value));
                key = "tooltip.lore.effect.multi_hit";
                fallback = "Multihit: {0} extra hits at {1}% damage";
                String template = LangLoader.getTranslationForLanguage(key, langCode);
                if (template == null || template.isBlank() || template.equals(key)) {
                    template = fallback;
                }
                return template
                        .replace("{0}", String.valueOf(extraHits))
                        .replace("{1}", percent);
            }
            case VORTEXSTRIKE -> {
                String percent = formatPercent(toPercent(value));
                key = "tooltip.lore.effect.vortexstrike";
                fallback = "Vortexstrike: lunge and hit nearby enemies for {0}% damage";
                String effect = formatEffect(langCode, key, fallback, percent);
                double radius = scaleRadius(BASE_VORTEXSTRIKE_RADIUS, safeFeedTier);
                return effect + " (" + formatRadiusSuffix(langCode, radius) + ")";
            }
            case CRIT_CHARGE -> {
                String percent = formatPercent(toPercent(value));
                key = "tooltip.lore.effect.crit_charge";
                fallback = "Critical charge: +{0}% damage";
                return formatEffect(langCode, key, fallback, percent);
            }
            case BERSERK -> {
                if ("tooltip.lore.signature.whirlwind".equals(abilityNameKey)) {
                    String percent = formatPercent(toPercent(scaleEffectValue(value, safeFeedTier)));
                    long durationMs = resolveBerserkDurationMs(safeFeedTier);
                    double radius = scaleRadius(BASE_WHIRLWIND_RADIUS, safeFeedTier);
                    key = "tooltip.lore.effect.whirlwind";
                    fallback = "Whirlwind: spin for {0}s, dealing {1}% damage per second";
                    String template = LangLoader.getTranslationForLanguage(key, langCode);
                    if (template == null || template.isBlank() || template.equals(key)) {
                        template = fallback;
                    }
                    String effect = template
                            .replace("{0}", formatSeconds(durationMs))
                            .replace("{1}", percent);
                    return effect + " (" + formatRadiusSuffix(langCode, radius) + ")";
                }
                key = "tooltip.lore.effect.berserk";
                fallback = "Berserk: haste for {0}s";
                long durationMs = resolveBerserkDurationMs(safeFeedTier);
                return formatEffect(langCode, key, fallback, formatSeconds(durationMs));
            }
            case SUMMON_WOLF_PACK -> {
                int cap = Math.max(1, level + safeFeedTier);
                key = "tooltip.lore.effect.summon_wolf_pack";
                fallback = "Summons a wolf pack (max {0})";
                return formatEffect(langCode, key, fallback, String.valueOf(cap));
            }
            case CHARGE_ATTACK -> {
                key = "tooltip.lore.effect.charge_attack";
                fallback = "Charge attack: strikes a target {0} times";
                int hits = resolveFeedScaledHits(CHARGE_ATTACK_BASE_HITS, CHARGE_ATTACK_MAX_HITS, safeFeedTier);
                return formatEffect(langCode, key, fallback, String.valueOf(hits));
            }
            case OMNISLASH -> {
                key = "tooltip.lore.effect.omnislash";
                fallback = "Omnislash: strikes nearby enemies {0} times";
                int hits = resolveFeedScaledHits(OMNISLASH_BASE_HITS, OMNISLASH_MAX_HITS, safeFeedTier);
                String effect = formatEffect(langCode, key, fallback, String.valueOf(hits));
                double radius = scaleRadius(BASE_OMNISLASH_RADIUS, safeFeedTier);
                return effect + " (" + formatRadiusSuffix(langCode, radius) + ")";
            }
            case OCTASLASH -> {
                key = "tooltip.lore.effect.octaslash";
                fallback = "Octaslash: strikes a target {0} times";
                int hits = resolveFeedScaledHits(OCTASLASH_BASE_HITS, OCTASLASH_MAX_HITS, safeFeedTier);
                return formatEffect(langCode, key, fallback, String.valueOf(hits));
            }
            case PUMMEL -> {
                key = "tooltip.lore.effect.pummel";
                fallback = "Pummel: strikes a target {0} times";
                int hits = resolveFeedScaledHits(PUMMEL_BASE_HITS, PUMMEL_MAX_HITS, safeFeedTier);
                return formatEffect(langCode, key, fallback, String.valueOf(hits));
            }
            case BLOOD_RUSH -> {
                key = "tooltip.lore.effect.blood_rush";
                fallback = "Blood Rush: strikes a target {0} times";
                int hits = resolveFeedScaledHits(BLOOD_RUSH_BASE_HITS, BLOOD_RUSH_MAX_HITS, safeFeedTier);
                return formatEffect(langCode, key, fallback, String.valueOf(hits));
            }
            case CAUSTIC_FINALE -> {
                key = "tooltip.lore.effect.caustic_finale";
                fallback = "Caustic Finale: poisoned targets explode";
                return formatEffect(langCode, key, fallback, formatted);
            }
            case SHRAPNEL_FINALE -> {
                key = "tooltip.lore.effect.shrapnel_finale";
                fallback = "Shrapnel: bleeding targets explode";
                return formatEffect(langCode, key, fallback, formatted);
            }
            case BURN_FINALE -> {
                key = "tooltip.lore.effect.burn_finale";
                fallback = "Inferno: burning targets explode";
                return formatEffect(langCode, key, fallback, formatted);
            }
            case DRAIN_LIFE -> {
                key = "tooltip.lore.effect.drain_life";
                fallback = "Drains {0} HP over time";
                String effect = formatEffect(langCode, key, fallback, formatted);
                long durationMs = LoreAbility.scaleDurationMs(3000L, safeFeedTier);
                return effect + " (" + formatDurationSuffix(langCode, durationMs) + ")";
            }
            default -> {
                return formatEffect(langCode, "tooltip.lore.effect.unknown", "Lore ability", formatted);
            }
        }
    }

    private String formatEffect(String langCode, String key, String fallback, String value) {
        String template = LangLoader.getTranslationForLanguage(key, langCode);
        if (template == null || template.isBlank() || template.equals(key)) {
            template = fallback;
        }
        return template.replace("{0}", value);
    }

    private String formatRadiusSuffix(String langCode, double radius) {
        String label = LangLoader.getTranslationForLanguage("tooltip.lore.radius_label", langCode);
        if (label == null || label.isBlank() || label.equals("tooltip.lore.radius_label")) {
            label = "Radius";
        }
        return label + ": " + formatValue(radius) + "m";
    }

    private String formatDurationSuffix(String langCode, long durationMs) {
        String label = LangLoader.getTranslationForLanguage("tooltip.lore.duration_label", langCode);
        if (label == null || label.isBlank() || label.equals("tooltip.lore.duration_label")) {
            label = "Duration";
        }
        return label + ": " + formatSeconds(durationMs) + "s";
    }

    private static String formatValue(double value) {
        double rounded = Math.round(value * 10.0) / 10.0;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.0001) {
            return String.format(Locale.ROOT, "%.0f", rounded);
        }
        return String.format(Locale.ROOT, "%.1f", rounded);
    }

    private static String formatPercent(double percent) {
        double rounded = Math.round(percent * 10.0) / 10.0;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.0001) {
            return String.format(Locale.ROOT, "%.0f", rounded);
        }
        return String.format(Locale.ROOT, "%.1f", rounded);
    }

    private static String formatSeconds(long millis) {
        double seconds = Math.max(0.0, millis / 1000.0);
        double rounded = Math.round(seconds * 10.0) / 10.0;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.0001) {
            return String.format(Locale.ROOT, "%.0f", rounded);
        }
        return String.format(Locale.ROOT, "%.1f", rounded);
    }

    private static double toPercent(double value) {
        double pct = value <= 1.0 ? value * 100.0 : value;
        return Math.max(0.0, pct);
    }

    private static int calcExtraHits(double value) {
        int hits = 1 + (int) Math.floor(Math.max(0.0, value));
        return Math.max(1, Math.min(3, hits));
    }

    private static int resolveFeedScaledHits(int baseHits, int maxHits, int feedTier) {
        int base = Math.max(1, baseHits);
        int max = Math.max(base, maxHits);
        int hits = base + Math.max(0, feedTier);
        return Math.max(1, Math.min(max, hits));
    }
}
