package irai.mod.reforge.Common;

import java.util.HashMap;
import java.util.Map;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.server.core.inventory.ItemStack;

public final class ResonantCompendiumUtils {
    public static final String COMPENDIUM_ITEM_ID = "Resonant_Compendium";
    public static final String META_COMPENDIUM_DATA = "SocketReforge.Compendium.Data";

    private ResonantCompendiumUtils() {}

    public static class CompendiumEntry {
        public String name;
        public String pattern;
        public String usages;
        public int quantity;

        public CompendiumEntry(String name, String pattern, String usages, int quantity) {
            this.name = name == null ? "" : name;
            this.pattern = pattern == null ? "" : pattern;
            this.usages = usages == null ? "" : usages;
            this.quantity = Math.max(1, quantity);
        }
    }

    public enum CombineFailure {
        NONE,
        ENTRY_MISSING,
        SAME_ENTRY,
        NAME_MISMATCH,
        BASE_COMPLETE,
        NO_NEW_DATA
    }

    public record CombineResult(
            CombineFailure failure,
            int gainedSlots,
            int revealedSlots,
            int totalSlots) {
        public boolean succeeded() {
            return failure == CombineFailure.NONE;
        }
    }

    public static boolean isCompendiumItem(ItemStack stack) {
        return stack != null && !stack.isEmpty() && COMPENDIUM_ITEM_ID.equalsIgnoreCase(stack.getItemId());
    }

    public static Map<String, CompendiumEntry> getCompendiumData(ItemStack stack) {
        Map<String, CompendiumEntry> data = new HashMap<>();
        if (!isCompendiumItem(stack)) return data;

        String raw = stack.getFromMetadataOrNull(META_COMPENDIUM_DATA, Codec.STRING);
        if (raw == null || raw.isBlank()) return data;

        String[] lines = raw.split("\n");
        for (String line : lines) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\t");
            if (parts.length >= 2) {
                String name = parts[0];
                String pattern = ResonantRecipeUtils.normalizePattern(parts[1]);
                String usages = parts.length > 2 ? ResonantRecipeUtils.normalizeUsages(parts[2]) : "";
                int quantity = 1;
                if (parts.length > 3) {
                    try {
                        quantity = Integer.parseInt(parts[3].trim());
                    } catch (NumberFormatException ignored) {
                        quantity = 1;
                    }
                }
                CompendiumEntry entry = new CompendiumEntry(name, pattern, usages, quantity);
                String key = buildEntryKey(entry.name, entry.pattern, entry.usages);
                CompendiumEntry existing = data.get(key);
                if (existing == null) {
                    data.put(key, entry);
                } else {
                    existing.quantity = Math.max(1, existing.quantity) + entry.quantity;
                }
            }
        }
        return data;
    }

    public static ItemStack saveCompendiumData(ItemStack stack, Map<String, CompendiumEntry> data) {
        if (!isCompendiumItem(stack)) return stack;

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, CompendiumEntry> entry : data.entrySet()) {
            CompendiumEntry value = entry.getValue();
            if (value == null || value.quantity <= 0) {
                continue;
            }
            sb.append(value.name).append("\t")
              .append(value.pattern).append("\t")
              .append(value.usages != null ? value.usages : "").append("\t")
              .append(value.quantity).append("\n");
        }

        return stack.withMetadata(META_COMPENDIUM_DATA, Codec.STRING, sb.toString());
    }

    /**
     * Merges a recipe shard into the compendium.
     * Returns true if it contributed new slots or combined effectively.
     */
    public static boolean addShardToCompendium(Map<String, CompendiumEntry> data, String recipeName, String pattern, String usages, int quantity) {
        String normalizedName = ResonantRecipeUtils.normalizeRecipeName(recipeName);
        if (normalizedName.isEmpty()) return false;
        int safeQty = Math.max(1, quantity);
        String incomingPattern = ResonantRecipeUtils.normalizePattern(pattern);
        String incomingUsages = ResonantRecipeUtils.normalizeUsages(usages);

        String key = buildEntryKey(recipeName, incomingPattern, incomingUsages);
        CompendiumEntry existing = data.get(key);
        if (existing == null) {
            data.put(key, new CompendiumEntry(recipeName, incomingPattern, incomingUsages, safeQty));
        } else {
            existing.quantity = Math.max(1, existing.quantity) + safeQty;
        }

        return true;
    }

    /** Combines two recipe stacks already stored in the same compendium. */
    public static CombineResult combineEntries(
            Map<String, CompendiumEntry> data,
            String baseKey,
            String mergeKey) {
        if (data == null || baseKey == null || mergeKey == null) {
            return failed(CombineFailure.ENTRY_MISSING);
        }
        if (baseKey.equals(mergeKey)) {
            return failed(CombineFailure.SAME_ENTRY);
        }

        CompendiumEntry base = data.get(baseKey);
        CompendiumEntry merge = data.get(mergeKey);
        if (base == null || merge == null) {
            return failed(CombineFailure.ENTRY_MISSING);
        }
        if (!ResonantRecipeUtils.normalizeRecipeName(base.name)
                .equals(ResonantRecipeUtils.normalizeRecipeName(merge.name))) {
            return failed(CombineFailure.NAME_MISMATCH);
        }

        ResonantRecipeUtils.PatternStats before = ResonantRecipeUtils.getPatternStats(base.pattern);
        if (before.isComplete()) {
            return new CombineResult(CombineFailure.BASE_COMPLETE, 0,
                    before.revealedSlots(), before.totalSlots());
        }

        String mergedPattern = ResonantRecipeUtils.mergePatterns(base.pattern, merge.pattern);
        ResonantRecipeUtils.PatternStats after = ResonantRecipeUtils.getPatternStats(mergedPattern);
        int gained = Math.max(0, after.revealedSlots() - before.revealedSlots());
        if (gained <= 0) {
            return new CombineResult(CombineFailure.NO_NEW_DATA, 0,
                    before.revealedSlots(), before.totalSlots());
        }

        if (base.quantity > 1) {
            base.quantity--;
            addShardToCompendium(data, base.name, mergedPattern, base.usages, 1);
        } else {
            base.pattern = mergedPattern;
        }

        if (merge.quantity > 1) {
            merge.quantity--;
        } else {
            data.remove(mergeKey);
        }

        return new CombineResult(CombineFailure.NONE, gained,
                after.revealedSlots(), after.totalSlots());
    }

    private static CombineResult failed(CombineFailure failure) {
        return new CombineResult(failure, 0, 0, 0);
    }

    private static String buildEntryKey(String name, String pattern, String usages) {
        String safeName = name == null ? "" : ResonantRecipeUtils.normalizeRecipeName(name);
        String safePattern = ResonantRecipeUtils.normalizePattern(pattern);
        String safeUsages = ResonantRecipeUtils.normalizeUsages(usages);
        return safeName + "\u001F" + safePattern + "\u001F" + safeUsages;
    }
}
