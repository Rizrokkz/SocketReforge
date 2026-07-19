package irai.mod.reforge.Common;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

/**
 * Utility helpers for injecting additional random loot into an item container.
 */
@SuppressWarnings("removal")
public final class LootInjectionUtils {
    private LootInjectionUtils() {}

    /**
     * Applies each rule once. If a rule procs and the item fits in the container,
     * the rolled quantity is injected.
     *
     * @return map of itemId to injected quantity (only for successful injections)
     */
    public static Map<String, Integer> injectByRules(ItemContainer container, List<LootInjectionRule> rules) {
        if (container == null || rules == null || rules.isEmpty()) {
            return Map.of();
        }

        Map<String, Integer> injected = new LinkedHashMap<>();
        for (LootInjectionRule rule : rules) {
            if (rule == null || !rule.shouldInject()) {
                continue;
            }

            int quantity = rule.rollQuantity();
            if (quantity <= 0) {
                continue;
            }

            ItemStack stack = new ItemStack(rule.itemId(), quantity);
            if (!container.canAddItemStack(stack)) {
                continue;
            }

            container.addItemStack(stack);
            injected.merge(rule.itemId(), quantity, Integer::sum);
        }

        return injected.isEmpty() ? Map.of() : injected;
    }

    /**
     * Applies each rule once to a drop list. If a rule procs, the rolled quantity
     * is appended as a new stack.
     *
     * @return map of itemId to injected quantity (only for successful injections)
     */
    public static Map<String, Integer> injectByRules(List<ItemStack> drops, List<LootInjectionRule> rules) {
        if (drops == null || rules == null || rules.isEmpty()) {
            return Map.of();
        }

        Map<String, Integer> injected = new LinkedHashMap<>();
        for (LootInjectionRule rule : rules) {
            if (rule == null || !rule.shouldInject()) {
                continue;
            }

            int quantity = rule.rollQuantity();
            if (quantity <= 0) {
                continue;
            }

            drops.add(new ItemStack(rule.itemId(), quantity));
            injected.merge(rule.itemId(), quantity, Integer::sum);
        }

        return injected.isEmpty() ? Map.of() : injected;
    }

    public static LootInjectionRule rule(String itemId, double chance, int minQuantity, int maxQuantity) {
        return rule(itemId, chance, minQuantity, maxQuantity, "");
    }

    public static LootInjectionRule rule(String itemId, double chance, int minQuantity, int maxQuantity, String targetId) {
        return new LootInjectionRule(itemId, chance, minQuantity, maxQuantity, targetId);
    }

    public static List<LootInjectionRule> rulesFromEntries(String[] entries) {
        if (entries == null || entries.length == 0) {
            return List.of();
        }
        List<LootInjectionRule> rules = new ArrayList<>();
        for (String entry : entries) {
            LootInjectionRule rule = ruleFromEntry(entry);
            if (rule != null) {
                rules.add(rule);
            }
        }
        return rules.isEmpty() ? List.of() : List.copyOf(rules);
    }

    public static LootInjectionRule ruleFromEntry(String entry) {
        if (entry == null || entry.isBlank()) {
            return null;
        }
        String[] split = entry.trim().split("=", 2);
        String itemId = split[0].trim();
        if (itemId.isBlank()) {
            return null;
        }
        String[] params = split.length > 1 ? split[1].split(",") : new String[0];
        double chance = params.length > 0 ? parseChance(params[0], 0.0d) : 0.0d;
        int min = params.length > 1 ? parseInt(params[1], 1) : 1;
        int max = params.length > 2 ? parseInt(params[2], min) : min;
        String targetId = params.length > 3 ? params[3].trim() : "";
        return rule(itemId, chance, min, max, targetId);
    }

    public static String formatEntry(String itemId, double chance, int minQuantity, int maxQuantity) {
        return formatEntry(itemId, chance, minQuantity, maxQuantity, "");
    }

    public static String formatEntry(String itemId, double chance, int minQuantity, int maxQuantity, String targetId) {
        String safeItemId = itemId == null ? "" : itemId.trim();
        double safeChance = clamp01(chance);
        int safeMin = Math.max(0, minQuantity);
        int safeMax = Math.max(safeMin, maxQuantity);
        String safeTargetId = targetId == null ? "" : targetId.trim();
        String base = safeItemId + "=" + formatChance(safeChance) + "," + safeMin + "," + safeMax;
        return safeTargetId.isBlank() ? base : base + "," + safeTargetId;
    }

    public static final class LootInjectionRule {
        private final String itemId;
        private final double chance;
        private final int minQuantity;
        private final int maxQuantity;
        private final String targetId;

        private LootInjectionRule(String itemId, double chance, int minQuantity, int maxQuantity, String targetId) {
            this.itemId = itemId == null ? "" : itemId;
            this.chance = clamp01(chance);
            int safeMin = Math.max(0, minQuantity);
            int safeMax = Math.max(safeMin, maxQuantity);
            this.minQuantity = safeMin;
            this.maxQuantity = safeMax;
            this.targetId = targetId == null ? "" : targetId.trim();
        }

        public String itemId() {
            return itemId;
        }

        public String targetId() {
            return targetId;
        }

        public boolean shouldInject() {
            if (itemId.isBlank() || chance <= 0.0d) {
                return false;
            }
            return ThreadLocalRandom.current().nextDouble() < chance;
        }

        public int rollQuantity() {
            if (minQuantity >= maxQuantity) {
                return minQuantity;
            }
            return ThreadLocalRandom.current().nextInt(minQuantity, maxQuantity + 1);
        }
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static double parseChance(String raw, double fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            double parsed = Double.parseDouble(raw.trim());
            if (parsed > 1.0d) {
                parsed /= 100.0d;
            }
            return clamp01(parsed);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String formatChance(double value) {
        return String.format(Locale.ROOT, "%.4f", clamp01(value))
                .replaceAll("0+$", "")
                .replaceAll("\\.$", ".0");
    }

    private static double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }
}
