package irai.mod.reforge.Common;

import java.util.LinkedHashMap;
import java.util.List;
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
        return new LootInjectionRule(itemId, chance, minQuantity, maxQuantity);
    }

    public static final class LootInjectionRule {
        private final String itemId;
        private final double chance;
        private final int minQuantity;
        private final int maxQuantity;

        private LootInjectionRule(String itemId, double chance, int minQuantity, int maxQuantity) {
            this.itemId = itemId == null ? "" : itemId;
            this.chance = clamp01(chance);
            int safeMin = Math.max(0, minQuantity);
            int safeMax = Math.max(safeMin, maxQuantity);
            this.minQuantity = safeMin;
            this.maxQuantity = safeMax;
        }

        public String itemId() {
            return itemId;
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

        private static double clamp01(double value) {
            return Math.max(0.0d, Math.min(1.0d, value));
        }
    }
}
