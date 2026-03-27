package irai.mod.reforge.Common;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.DamageEntityInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.combat.DamageCalculator;

import irai.mod.reforge.Interactions.ReforgeEquip;
import irai.mod.reforge.Socket.EssenceEffect;
import irai.mod.reforge.Socket.SocketData;
import irai.mod.reforge.Socket.SocketManager;

/**
 * Shared tooltip math for deriving equipment stats from item assets and metadata.
 */
public final class EquipmentDamageTooltipMath {

    private static final int MAX_INTERACTION_VISITS = 512;
    private static final ConcurrentMap<String, Double> BASE_DAMAGE_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Double> CHARGED_DAMAGE_CACHE = new ConcurrentHashMap<>();

    private EquipmentDamageTooltipMath() {}

    public static final class StatSummary {
        private final double baseValue;
        private final double buffedValue;

        public StatSummary(double baseValue, double buffedValue) {
            this.baseValue = Math.max(0.0, baseValue);
            this.buffedValue = Math.max(0.0, buffedValue);
        }

        public double getBaseValue() {
            return baseValue;
        }

        public double getBuffedValue() {
            return buffedValue;
        }
    }

    /**
     * Computes the "Damage : X -> Y" summary using interaction base damage and current buffs.
     */
    public static StatSummary computeWeaponDamageSummary(String itemId,
                                                         int reforgeLevel,
                                                         SocketData socketData,
                                                         double partsDamageMultiplier) {
        double base = getAverageBaseDamageFromInteractionVars(itemId);
        double buffed = computeBuffedWeaponDamage(itemId, base, reforgeLevel, socketData, partsDamageMultiplier);
        return new StatSummary(base, buffed);
    }

    /**
     * Computes the "Defense : X -> Y" summary using armor base defense and current buffs.
     */
    public static StatSummary computeArmorDefenseSummary(String itemId,
                                                         int reforgeLevel,
                                                         SocketData socketData) {
        double base = getBaseArmorDefense(itemId);
        double buffed = computeBuffedArmorDefense(base, reforgeLevel, socketData);
        return new StatSummary(base, buffed);
    }

    /**
     * Returns the average base damage across all damage interactions referenced by getInteractionVars().
     */
    public static double getAverageBaseDamageFromInteractionVars(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return 0.0;
        }
        return BASE_DAMAGE_CACHE.computeIfAbsent(itemId, key -> {
            Item item = resolveItem(key);
            return getAverageBaseDamageFromInteractionVars(item);
        });
    }

    /**
     * Returns the average base damage across "charged" damage interactions.
     */
    public static double getChargedBaseDamageFromInteractionVars(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return 0.0;
        }
        return CHARGED_DAMAGE_CACHE.computeIfAbsent(itemId, key -> {
            Item item = resolveItem(key);
            return getChargedBaseDamageFromInteractionVars(item);
        });
    }

    /**
     * Returns the average base damage across all damage interactions referenced by getInteractionVars().
     */
    public static double getAverageBaseDamageFromInteractionVars(Item item) {
        if (item == null || item == Item.UNKNOWN) {
            return 0.0;
        }

        Map<String, String> interactionVars = item.getInteractionVars();
        if (interactionVars == null || interactionVars.isEmpty()) {
            return 0.0;
        }

        Set<String> rootIds = new LinkedHashSet<>();
        for (String rootId : interactionVars.values()) {
            if (rootId != null && !rootId.isBlank()) {
                rootIds.add(rootId);
            }
        }
        if (rootIds.isEmpty()) {
            return 0.0;
        }

        List<Double> damageSamples = new ArrayList<>();
        Set<String> visitedInteractions = new HashSet<>();

        for (String rootId : rootIds) {
            RootInteraction root = getRootInteraction(rootId);
            if (root == null) {
                continue;
            }
            String[] interactionIds = root.getInteractionIds();
            if (interactionIds == null || interactionIds.length == 0) {
                continue;
            }
            for (String interactionId : interactionIds) {
                collectInteractionDamage(interactionId, visitedInteractions, damageSamples);
            }
        }

        if (damageSamples.isEmpty()) {
            return 0.0;
        }

        double total = 0.0;
        for (double sample : damageSamples) {
            total += sample;
        }
        return total / damageSamples.size();
    }

    /**
     * Returns the average base damage across charged damage interactions referenced by getInteractionVars().
     */
    public static double getChargedBaseDamageFromInteractionVars(Item item) {
        if (item == null || item == Item.UNKNOWN) {
            return 0.0;
        }

        Map<String, String> interactionVars = item.getInteractionVars();
        if (interactionVars == null || interactionVars.isEmpty()) {
            return 0.0;
        }

        Set<String> rootIds = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : interactionVars.entrySet()) {
            String key = entry.getKey();
            String rootId = entry.getValue();
            if (!isChargedDamageKey(key) && !isChargedDamageKey(rootId)) {
                continue;
            }
            if (rootId != null && !rootId.isBlank()) {
                rootIds.add(rootId);
            }
        }
        if (rootIds.isEmpty()) {
            return 0.0;
        }

        List<Double> damageSamples = new ArrayList<>();
        Set<String> visitedInteractions = new HashSet<>();

        for (String rootId : rootIds) {
            RootInteraction root = getRootInteraction(rootId);
            if (root == null) {
                continue;
            }
            String[] interactionIds = root.getInteractionIds();
            if (interactionIds == null || interactionIds.length == 0) {
                continue;
            }
            for (String interactionId : interactionIds) {
                collectInteractionDamage(interactionId, visitedInteractions, damageSamples);
            }
        }

        if (damageSamples.isEmpty()) {
            return 0.0;
        }

        double total = 0.0;
        for (double sample : damageSamples) {
            total += sample;
        }
        return total / damageSamples.size();
    }

    /**
     * Mirrors deterministic parts of runtime damage math:
     * refinement multiplier + socket damage bonuses + parts multiplier + attack speed multiplier.
     */
    public static double computeBuffedWeaponDamage(String itemId,
                                                   double baseDamage,
                                                   int reforgeLevel,
                                                   SocketData socketData,
                                                   double partsDamageMultiplier) {
        if (baseDamage <= 0.0) {
            return 0.0;
        }

        SocketData safeSocketData = socketData != null ? socketData : new SocketData(0);
        int safeLevel = Math.max(0, Math.min(reforgeLevel, 3));
        double refinementMultiplier = ReforgeEquip.getDamageMultiplier(safeLevel);
        double[] damageBonus;
        double[] attackSpeedBonus;

        ItemStack previewStack = createPreviewWeaponStack(itemId, safeSocketData);
        if (previewStack != null) {
            // Match runtime damage event math: includes tier, greater weighting, and resonance stats.
            damageBonus = SocketManager.getStoredStatBonus(previewStack, EssenceEffect.StatType.DAMAGE);
            attackSpeedBonus = SocketManager.getStoredStatBonus(previewStack, EssenceEffect.StatType.ATTACK_SPEED);
        } else {
            damageBonus = SocketManager.calculateTieredBonus(safeSocketData, EssenceEffect.StatType.DAMAGE, true);
            attackSpeedBonus = SocketManager.calculateTieredBonus(safeSocketData, EssenceEffect.StatType.ATTACK_SPEED, true);
        }

        double socketFlat = damageBonus[0];
        double socketPercentMultiplier = 1.0 + (damageBonus[1] / 100.0);
        double attackSpeedMultiplier = 1.0 + (attackSpeedBonus[1] / 100.0);
        double clampedPartsMultiplier = clamp(partsDamageMultiplier, 0.5, 2.0);

        double result = (baseDamage * refinementMultiplier * socketPercentMultiplier * clampedPartsMultiplier) + socketFlat;
        result *= attackSpeedMultiplier;
        return Math.max(0.0, result);
    }

    private static ItemStack createPreviewWeaponStack(String itemId, SocketData socketData) {
        if (itemId == null || itemId.isBlank() || socketData == null) {
            return null;
        }
        try {
            ItemStack base = new ItemStack(itemId, 1);
            if (base == null || base.isEmpty()) {
                return null;
            }
            return SocketManager.withSocketData(base, socketData);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Mirrors deterministic armor tooltip math:
     * refine multiplier + socket defense bonuses.
     */
    public static double computeBuffedArmorDefense(double baseDefense,
                                                   int reforgeLevel,
                                                   SocketData socketData) {
        if (baseDefense <= 0.0) {
            return 0.0;
        }

        SocketData safeSocketData = socketData != null ? socketData : new SocketData(0);
        int safeLevel = Math.max(0, Math.min(reforgeLevel, 3));
        double refinementMultiplier = ReforgeEquip.getDefenseMultiplier(safeLevel);
        double[] defenseBonus = SocketManager.calculateTieredBonus(safeSocketData, EssenceEffect.StatType.DEFENSE, false);

        double flatDefense = defenseBonus[0];
        double percentMultiplier = 1.0 + (defenseBonus[1] / 100.0);

        double result = (baseDefense * refinementMultiplier * percentMultiplier) + flatDefense;
        return Math.max(0.0, result);
    }

    public static void clearCache() {
        BASE_DAMAGE_CACHE.clear();
        CHARGED_DAMAGE_CACHE.clear();
    }

    private static boolean isChargedDamageKey(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (!lower.contains("charge")) {
            return false;
        }
        return lower.contains("damage");
    }

    /**
     * Returns base defense as defined in armor config.
     */
    public static double getBaseArmorDefense(String itemId) {
        Item item = resolveItem(itemId);
        if (item == null || item == Item.UNKNOWN || item.getArmor() == null) {
            return 0.0;
        }
        try {
            return Math.max(0.0, item.getArmor().getBaseDamageResistance());
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    private static void collectInteractionDamage(String interactionId,
                                                 Set<String> visitedInteractions,
                                                 List<Double> damageSamples) {
        if (interactionId == null || interactionId.isBlank()) {
            return;
        }
        if (visitedInteractions.size() >= MAX_INTERACTION_VISITS) {
            return;
        }
        if (!visitedInteractions.add(interactionId)) {
            return;
        }

        Interaction interaction = getInteraction(interactionId);
        if (interaction == null) {
            return;
        }

        Set<String> chainedIds = new LinkedHashSet<>();
        collectGenericChainedIds(interaction, chainedIds);

        if (interaction instanceof DamageEntityInteraction damageInteraction) {
            addCalculatorSample(readCalculatorField(damageInteraction, "damageCalculator"), damageSamples);

            Object[] angledDamage = readObjectArrayField(damageInteraction, "angledDamage");
            for (Object angledEntry : angledDamage) {
                addCalculatorSample(readCalculatorField(angledEntry, "damageCalculator"), damageSamples);
                addStringField(angledEntry, "next", chainedIds);
            }

            Object targetedDamageObj = readFieldValue(damageInteraction, "targetedDamage");
            if (targetedDamageObj instanceof Map<?, ?> targetedDamage) {
                for (Object targetedEntry : targetedDamage.values()) {
                    addCalculatorSample(readCalculatorField(targetedEntry, "damageCalculator"), damageSamples);
                    addStringField(targetedEntry, "next", chainedIds);
                }
            }
        }

        for (String chainedId : chainedIds) {
            if (chainedId == null || chainedId.isBlank()) {
                continue;
            }
            if (getInteraction(chainedId) == null) {
                continue;
            }
            collectInteractionDamage(chainedId, visitedInteractions, damageSamples);
        }
    }

    private static void collectGenericChainedIds(Object source, Set<String> out) {
        if (source == null) {
            return;
        }
        for (Field field : getAllFields(source.getClass())) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object value = field.get(source);
                if (value instanceof String stringValue) {
                    if (!stringValue.isBlank()) {
                        out.add(stringValue);
                    }
                } else if (value instanceof String[] arrayValue) {
                    for (String entry : arrayValue) {
                        if (entry != null && !entry.isBlank()) {
                            out.add(entry);
                        }
                    }
                } else if (value instanceof Map<?, ?> mapValue) {
                    for (Object key : mapValue.keySet()) {
                        if (key instanceof String stringKey && !stringKey.isBlank()) {
                            out.add(stringKey);
                        }
                    }
                    for (Object mapEntryValue : mapValue.values()) {
                        if (mapEntryValue instanceof String stringMapValue && !stringMapValue.isBlank()) {
                            out.add(stringMapValue);
                        }
                    }
                }
            } catch (Exception ignored) {
                // Ignore inaccessible/invalid fields.
            }
        }
    }

    private static void addStringField(Object source, String fieldName, Set<String> out) {
        Object value = readFieldValue(source, fieldName);
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            out.add(stringValue);
        }
    }

    private static Object[] readObjectArrayField(Object source, String fieldName) {
        Object raw = readFieldValue(source, fieldName);
        if (raw == null || !raw.getClass().isArray()) {
            return new Object[0];
        }
        int length = java.lang.reflect.Array.getLength(raw);
        Object[] values = new Object[length];
        for (int i = 0; i < length; i++) {
            values[i] = java.lang.reflect.Array.get(raw, i);
        }
        return values;
    }

    private static DamageCalculator readCalculatorField(Object source, String fieldName) {
        Object value = readFieldValue(source, fieldName);
        return value instanceof DamageCalculator calculator ? calculator : null;
    }

    private static Object readFieldValue(Object source, String fieldName) {
        if (source == null || fieldName == null || fieldName.isBlank()) {
            return null;
        }
        Class<?> current = source.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(source);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private static void addCalculatorSample(DamageCalculator calculator, List<Double> damageSamples) {
        if (calculator == null) {
            return;
        }

        Object baseDamageRaw = readFieldValue(calculator, "baseDamageRaw");
        if (!(baseDamageRaw instanceof Map<?, ?> baseDamageMap) || baseDamageMap.isEmpty()) {
            return;
        }

        double total = 0.0;
        for (Object value : baseDamageMap.values()) {
            if (value instanceof Number number) {
                total += number.doubleValue();
            }
        }

        if (total > 0.0) {
            damageSamples.add(total);
        }
    }

    private static RootInteraction getRootInteraction(String id) {
        try {
            RootInteraction root = RootInteraction.getAssetMap().getAsset(id);
            return root;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Interaction getInteraction(String id) {
        try {
            Interaction interaction = Interaction.getAssetMap().getAsset(id);
            return interaction != null && !interaction.isUnknown() ? interaction : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Item resolveItem(String itemId) {
        try {
            ItemStack probe = new ItemStack(itemId, 1);
            if (probe == null || probe.isEmpty()) {
                return null;
            }
            Item item = probe.getItem();
            if (item == null || item == Item.UNKNOWN) {
                return null;
            }
            return item;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static List<Field> getAllFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            Field[] declared = current.getDeclaredFields();
            for (Field field : declared) {
                fields.add(field);
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
