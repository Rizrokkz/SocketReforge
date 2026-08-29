package irai.mod.reforge.Common;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;

import irai.mod.reforge.Config.ElementalAffinityConfig;
import irai.mod.reforge.Socket.Essence;

/**
 * Shared elemental matchup helpers for essence-derived affinity damage.
 */
@SuppressWarnings("removal")
public final class ElementalAffinityUtils {
    public static final double DEFAULT_WEAKNESS_MULTIPLIER = 1.25d;
    public static final double DEFAULT_RESISTANCE_MULTIPLIER = 0.15d;
    public static final double DEFAULT_NEUTRAL_MULTIPLIER = 1.0d;

    private static volatile boolean enabled = true;
    private static volatile double weaknessMultiplier = DEFAULT_WEAKNESS_MULTIPLIER;
    private static volatile double resistanceMultiplier = DEFAULT_RESISTANCE_MULTIPLIER;
    private static volatile double shieldedHpDamageMultiplier = 0.01d;
    private static volatile double nonElementalShieldDamageMultiplier = 0.10d;
    private static volatile double shieldRechargeDelaySeconds = 8.0d;
    private static volatile double shieldRechargeRatePerSecond = 0.20d;
    private static volatile double shieldRechargeDurationSeconds = 5.0d;
    private static volatile List<AffinityRule> affinityRules = List.of();
    private static volatile List<AffinityRule> customAffinityRules = List.of();
    private static volatile List<MultiplierRule> multiplierRules = List.of();
    private static volatile List<ShieldRule> shieldRules = List.of();
    private static final Map<String, Double> RESOLVED_SHIELD_AMOUNTS = new ConcurrentHashMap<>();

    static {
        setConfig(new ElementalAffinityConfig());
    }

    private ElementalAffinityUtils() {}

    public static void setConfig(ElementalAffinityConfig config) {
        ElementalAffinityConfig safeConfig = config == null ? new ElementalAffinityConfig() : config;
        enabled = safeConfig.isEnabled();
        weaknessMultiplier = safePositive(safeConfig.getWeaknessMultiplier(), DEFAULT_WEAKNESS_MULTIPLIER);
        resistanceMultiplier = safePositive(safeConfig.getResistanceMultiplier(), DEFAULT_RESISTANCE_MULTIPLIER);
        shieldedHpDamageMultiplier = clamp(safeConfig.getShieldedHpDamageMultiplier(), 0.0d, 1.0d);
        nonElementalShieldDamageMultiplier = clamp(safeConfig.getNonElementalShieldDamageMultiplier(), 0.0d, 1.0d);
        shieldRechargeDelaySeconds = clamp(safeConfig.getShieldRechargeDelaySeconds(), 0.0d, 3600.0d);
        shieldRechargeRatePerSecond = clamp(safeConfig.getShieldRechargeRatePerSecond(), 0.0d, 1000.0d);
        shieldRechargeDurationSeconds = clamp(safeConfig.getShieldRechargeDurationSeconds(), 0.0d, 3600.0d);
        affinityRules = parseAffinityRules(safeConfig.getRoleAffinities());
        customAffinityRules = parseAffinityRules(safeConfig.getCustomRoleAffinities());
        multiplierRules = parseMultiplierRules(safeConfig.getElementMultipliers());
        shieldRules = parseShieldRules(safeConfig.getElementShields());
    }

    public static Essence.Type resolveTargetAffinity(Store<EntityStore> store, Ref<EntityStore> targetRef) {
        if (store == null || targetRef == null || NPCEntity.getComponentType() == null) {
            return null;
        }
        NPCEntity npc = store.getComponent(targetRef, NPCEntity.getComponentType());
        if (npc == null) {
            return null;
        }
        return resolveNpcAffinity(npc);
    }

    public static Essence.Type resolveNpcAffinity(NPCEntity npc) {
        if (!enabled || npc == null) {
            return null;
        }
        TargetDescriptor descriptor = describe(npc);
        Essence.Type customType = resolveAffinity(descriptor, customAffinityRules);
        return customType != null ? customType : resolveAffinity(descriptor, affinityRules);
    }

    public static double effectivenessMultiplier(Essence.Type attackType, Store<EntityStore> store, Ref<EntityStore> targetRef) {
        if (!enabled || attackType == null || store == null || targetRef == null || NPCEntity.getComponentType() == null) {
            return DEFAULT_NEUTRAL_MULTIPLIER;
        }
        NPCEntity npc = store.getComponent(targetRef, NPCEntity.getComponentType());
        if (npc == null) {
            return DEFAULT_NEUTRAL_MULTIPLIER;
        }
        TargetDescriptor descriptor = describe(npc);
        Double explicitMultiplier = resolveExplicitMultiplier(descriptor, attackType);
        if (explicitMultiplier != null) {
            return explicitMultiplier;
        }
        return effectivenessMultiplier(attackType, resolveNpcAffinity(npc));
    }

    public static Map<Essence.Type, Double> resolveElementMultipliers(Store<EntityStore> store,
                                                                      Ref<EntityStore> targetRef) {
        Map<Essence.Type, Double> resolved = new EnumMap<>(Essence.Type.class);
        for (Essence.Type type : Essence.Type.values()) {
            resolved.put(type, effectivenessMultiplier(type, store, targetRef));
        }
        return resolved;
    }

    public static double elementShield(Store<EntityStore> store, Ref<EntityStore> targetRef) {
        ElementShieldProfile profile = elementShieldProfile(store, targetRef);
        return profile == null ? 0.0d : profile.amount();
    }

    public static ElementShieldProfile elementShieldProfile(Store<EntityStore> store, Ref<EntityStore> targetRef) {
        if (!enabled || store == null || targetRef == null || NPCEntity.getComponentType() == null) {
            return null;
        }
        NPCEntity npc = store.getComponent(targetRef, NPCEntity.getComponentType());
        if (npc == null) {
            return null;
        }
        TargetDescriptor descriptor = describe(npc);
        return resolveExplicitShield(descriptor);
    }

    public static double shieldedHpDamageMultiplier() {
        return enabled ? shieldedHpDamageMultiplier : 1.0d;
    }

    public static double nonElementalShieldDamageMultiplier() {
        return enabled ? nonElementalShieldDamageMultiplier : 0.0d;
    }

    public static double shieldRechargeDelaySeconds() {
        return enabled ? shieldRechargeDelaySeconds : 0.0d;
    }

    public static double shieldRechargeRatePerSecond() {
        return enabled ? shieldRechargeRatePerSecond : 0.0d;
    }

    public static double shieldRechargeDurationSeconds() {
        return enabled ? shieldRechargeDurationSeconds : 0.0d;
    }

    public static double effectivenessMultiplier(Essence.Type attackType, Essence.Type targetType) {
        if (attackType == null || targetType == null) {
            return DEFAULT_NEUTRAL_MULTIPLIER;
        }
        if (isOpposedTo(attackType, targetType)) {
            return weaknessMultiplier;
        }
        if (attackType == targetType) {
            return resistanceMultiplier;
        }
        return DEFAULT_NEUTRAL_MULTIPLIER;
    }

    public static boolean isOpposedTo(Essence.Type attackType, Essence.Type targetType) {
        if (attackType == null || targetType == null) {
            return false;
        }
        return switch (attackType) {
            case LIFE -> targetType == Essence.Type.VOID;
            case VOID -> targetType == Essence.Type.LIFE;
            case ICE -> targetType == Essence.Type.FIRE;
            case FIRE -> targetType == Essence.Type.WATER;
            case WATER -> targetType == Essence.Type.LIGHTNING;
            case LIGHTNING -> targetType == Essence.Type.ICE;
        };
    }

    private static TargetDescriptor describe(NPCEntity npc) {
        Role role = npc.getRole();
        List<String> exactIds = new ArrayList<>();
        addExact(exactIds, npc.getRoleName());
        addExact(exactIds, role == null ? null : role.getRoleName());
        addExact(exactIds, role == null ? null : role.getNameTranslationKey());
        addExact(exactIds, role == null ? null : role.getAppearanceName());
        addExact(exactIds, role == null ? null : role.getLabel());
        addExact(exactIds, role == null ? null : role.getDropListId());
        return new TargetDescriptor(exactIds, combineFields(exactIds));
    }

    private static Essence.Type resolveAffinity(TargetDescriptor descriptor, List<AffinityRule> rules) {
        for (AffinityRule rule : rules) {
            if (rule.matches(descriptor)) {
                return rule.type();
            }
        }
        return null;
    }

    private static Double resolveExplicitMultiplier(TargetDescriptor descriptor, Essence.Type attackType) {
        Double multiplier = null;
        for (MultiplierRule rule : multiplierRules) {
            if (rule.matches(descriptor) && rule.multipliers().containsKey(attackType)) {
                multiplier = rule.multipliers().get(attackType);
            }
        }
        return multiplier;
    }

    private static ElementShieldProfile resolveExplicitShield(TargetDescriptor descriptor) {
        ElementShieldProfile exactShield = null;
        ElementShieldProfile hintShield = null;
        for (ShieldRule rule : shieldRules) {
            if (rule.matches(descriptor)) {
                if (rule.mode() == RuleMode.EXACT) {
                    exactShield = rule.profile();
                } else if (hintShield == null) {
                    hintShield = rule.profile();
                }
            }
        }
        return exactShield != null ? exactShield : hintShield;
    }

    private static List<AffinityRule> parseAffinityRules(String[] entries) {
        List<AffinityRule> rules = new ArrayList<>();
        if (entries == null) {
            return rules;
        }
        for (String entry : entries) {
            ParsedRuleTarget target = parseRuleTarget(entry);
            if (target == null) {
                continue;
            }
            Essence.Type type = parseType(target.value());
            if (type != null) {
                rules.add(new AffinityRule(target.mode(), target.key(), type));
            }
        }
        return List.copyOf(rules);
    }

    private static List<MultiplierRule> parseMultiplierRules(String[] entries) {
        List<MultiplierRule> rules = new ArrayList<>();
        if (entries == null) {
            return rules;
        }
        for (String entry : entries) {
            ParsedRuleTarget target = parseRuleTarget(entry);
            if (target == null) {
                continue;
            }
            Map<Essence.Type, Double> multipliers = new EnumMap<>(Essence.Type.class);
            for (String part : target.value().split(",")) {
                String[] pair = part.split(":", 2);
                if (pair.length != 2) {
                    continue;
                }
                Essence.Type type = parseType(pair[0]);
                double multiplier = parsePositiveDouble(pair[1], Double.NaN);
                if (type != null && !Double.isNaN(multiplier)) {
                    multipliers.put(type, multiplier);
                }
            }
            if (!multipliers.isEmpty()) {
                rules.add(new MultiplierRule(target.mode(), target.key(), Map.copyOf(multipliers)));
            }
        }
        return List.copyOf(rules);
    }

    private static List<ShieldRule> parseShieldRules(String[] entries) {
        List<ShieldRule> rules = new ArrayList<>();
        if (entries == null) {
            return rules;
        }
        for (String entry : entries) {
            ParsedRuleTarget target = parseRuleTarget(entry);
            if (target == null) {
                continue;
            }
            ElementShieldProfile profile = parseShieldProfile(target.value());
            if (profile != null) {
                rules.add(new ShieldRule(target.mode(), target.key(), profile));
            }
        }
        return List.copyOf(rules);
    }

    private static ParsedRuleTarget parseRuleTarget(String entry) {
        if (entry == null || entry.isBlank()) {
            return null;
        }
        String trimmed = entry.trim();
        int separator = trimmed.indexOf('=');
        if (separator <= 0 || separator >= trimmed.length() - 1) {
            return null;
        }
        RuleMode mode = RuleMode.HINT;
        String key = trimmed.substring(0, separator).trim();
        if (key.regionMatches(true, 0, "role:", 0, 5)) {
            mode = RuleMode.EXACT;
            key = key.substring(5).trim();
        } else if (key.regionMatches(true, 0, "id:", 0, 3)) {
            mode = RuleMode.EXACT;
            key = key.substring(3).trim();
        } else if (key.regionMatches(true, 0, "hint:", 0, 5)) {
            key = key.substring(5).trim();
        }
        if (key.isEmpty()) {
            return null;
        }
        return new ParsedRuleTarget(mode, key.toLowerCase(Locale.ROOT), trimmed.substring(separator + 1).trim());
    }

    private static Essence.Type parseType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Essence.Type.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static double parsePositiveDouble(String value, double fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            double parsed = Double.parseDouble(value.trim());
            return parsed > 0.0d ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double safePositive(double value, double fallback) {
        return value > 0.0d ? value : fallback;
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static double parseShieldAmount(String value, double fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
            String inner = trimmed.substring(1, trimmed.length() - 1);
            String[] bounds = inner.split(",");
            if (bounds.length == 2) {
                try {
                    double min = Double.parseDouble(bounds[0].trim());
                    double max = Double.parseDouble(bounds[1].trim());
                    if (!Double.isFinite(min) || !Double.isFinite(max)) {
                        return fallback;
                    }
                    double lo = Math.min(min, max);
                    double hi = Math.max(min, max);
                    if (lo == hi) {
                        return Math.max(0.0d, Math.min(1_000_000.0d, lo));
                    }
                    return lo + ThreadLocalRandom.current().nextDouble() * (hi - lo);
                } catch (NumberFormatException ignored) {
                    return fallback;
                }
            }
            return fallback;
        }
        try {
            double parsed = Double.parseDouble(trimmed);
            if (!Double.isFinite(parsed)) {
                return fallback;
            }
            return Math.max(0.0d, Math.min(1_000_000.0d, parsed));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public static double resolveShieldAmount(String mobKey, ElementShieldProfile profile) {
        if (profile == null) {
            return 0.0d;
        }
        Double cached = RESOLVED_SHIELD_AMOUNTS.get(mobKey);
        if (cached != null) {
            return cached;
        }
        double amount = profile.isRange()
                ? profile.rangeMin() + ThreadLocalRandom.current().nextDouble() * (profile.rangeMax() - profile.rangeMin())
                : profile.amount();
        Double existing = RESOLVED_SHIELD_AMOUNTS.putIfAbsent(mobKey, amount);
        return existing != null ? existing : amount;
    }

    public static void clearResolvedShieldAmount(String mobKey) {
        if (mobKey != null) {
            RESOLVED_SHIELD_AMOUNTS.remove(mobKey);
        }
    }

    public static void clearAllResolvedShieldAmounts() {
        RESOLVED_SHIELD_AMOUNTS.clear();
    }

    private static ElementShieldProfile parseShieldProfile(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        double amount;
        double rangeMin = 0.0d;
        double rangeMax = 0.0d;
        String remaining;
        if (trimmed.startsWith("(")) {
            int closeParen = trimmed.indexOf(')');
            if (closeParen <= 1) {
                return null;
            }
            String rangePart = trimmed.substring(0, closeParen + 1);
            String inner = rangePart.substring(1, rangePart.length() - 1);
            String[] bounds = inner.split(",");
            if (bounds.length == 2) {
                try {
                    double min = Double.parseDouble(bounds[0].trim());
                    double max = Double.parseDouble(bounds[1].trim());
                    if (Double.isFinite(min) && Double.isFinite(max)) {
                        rangeMin = Math.max(0.0d, Math.min(1_000_000.0d, Math.min(min, max)));
                        rangeMax = Math.max(0.0d, Math.min(1_000_000.0d, Math.max(min, max)));
                        amount = rangeMin;
                    } else {
                        return null;
                    }
                } catch (NumberFormatException ignored) {
                    return null;
                }
            } else {
                return null;
            }
            remaining = trimmed.substring(closeParen + 1);
            if (remaining.startsWith(",")) {
                remaining = remaining.substring(1);
            }
        } else {
            String[] parts = trimmed.split(",", 2);
            amount = parseShieldAmount(parts.length > 0 ? parts[0] : "", Double.NaN);
            if (Double.isNaN(amount)) {
                return null;
            }
            remaining = parts.length > 1 ? parts[1] : "";
        }
        String[] rest = remaining.split(",");
        double delay = rest.length > 0 && !rest[0].isBlank()
                ? parseNonNegativeDouble(rest[0], shieldRechargeDelaySeconds)
                : shieldRechargeDelaySeconds;
        double rate = rest.length > 1 && !rest[1].isBlank()
                ? parseNonNegativeDouble(rest[1], shieldRechargeRatePerSecond)
                : shieldRechargeRatePerSecond;
        double duration = rest.length > 2 && !rest[2].isBlank()
                ? parseNonNegativeDouble(rest[2], shieldRechargeDurationSeconds)
                : shieldRechargeDurationSeconds;
        return new ElementShieldProfile(amount, delay, rate, duration, rangeMin, rangeMax);
    }

    private static double parseNonNegativeDouble(String value, double fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            double parsed = Double.parseDouble(value.trim());
            if (!Double.isFinite(parsed)) {
                return fallback;
            }
            return Math.max(0.0d, parsed);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static void addExact(List<String> exactIds, String value) {
        if (value != null && !value.isBlank()) {
            exactIds.add(value.trim().toLowerCase(Locale.ROOT));
        }
    }

    private static String combineFields(List<String> values) {
        StringBuilder sb = new StringBuilder();
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(value);
        }
        return sb.toString();
    }

    private enum RuleMode {
        HINT,
        EXACT
    }

    private record TargetDescriptor(List<String> exactIds, String combined) {}

    private record ParsedRuleTarget(RuleMode mode, String key, String value) {}

    private record AffinityRule(RuleMode mode, String key, Essence.Type type) {
        boolean matches(TargetDescriptor descriptor) {
            return switch (mode) {
                case EXACT -> descriptor.exactIds().contains(key);
                case HINT -> descriptor.combined().contains(key);
            };
        }
    }

    private record MultiplierRule(RuleMode mode, String key, Map<Essence.Type, Double> multipliers) {
        boolean matches(TargetDescriptor descriptor) {
            return switch (mode) {
                case EXACT -> descriptor.exactIds().contains(key);
                case HINT -> descriptor.combined().contains(key);
            };
        }
    }

    public record ElementShieldProfile(
            double amount,
            double rechargeDelaySeconds,
            double rechargeRatePerSecond,
            double rechargeDurationSeconds,
            double rangeMin,
            double rangeMax) {
        public boolean isRange() {
            return rangeMax > rangeMin;
        }
    }

    private record ShieldRule(RuleMode mode, String key, ElementShieldProfile profile) {
        boolean matches(TargetDescriptor descriptor) {
            return switch (mode) {
                case EXACT -> descriptor.exactIds().contains(key);
                case HINT -> descriptor.combined().contains(key);
            };
        }
    }
}
