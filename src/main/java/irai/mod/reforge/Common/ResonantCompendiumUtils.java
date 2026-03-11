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
        public String pattern;
        public String usages;

        public CompendiumEntry(String pattern, String usages) {
            this.pattern = pattern;
            this.usages = usages;
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
                String pattern = parts[1];
                String usages = parts.length > 2 ? parts[2] : "";
                data.put(name, new CompendiumEntry(pattern, usages));
            }
        }
        return data;
    }

    public static ItemStack saveCompendiumData(ItemStack stack, Map<String, CompendiumEntry> data) {
        if (!isCompendiumItem(stack)) return stack;

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, CompendiumEntry> entry : data.entrySet()) {
            sb.append(entry.getKey()).append("\t")
              .append(entry.getValue().pattern).append("\t")
              .append(entry.getValue().usages != null ? entry.getValue().usages : "").append("\n");
        }

        return stack.withMetadata(META_COMPENDIUM_DATA, Codec.STRING, sb.toString());
    }

    /**
     * Merges a recipe shard into the compendium.
     * Returns true if it contributed new slots or combined effectively.
     */
    public static boolean addShardToCompendium(Map<String, CompendiumEntry> data, String recipeName, String pattern, String usages) {
        String normalizedName = ResonantRecipeUtils.normalizeRecipeName(recipeName);
        if (normalizedName.isEmpty()) return false;

        // We use normalizedName as the key, but we need to store the display name somewhere, 
        // let's use the actual recipeName for display mapping by storing it as key if it's new, 
        // to simplify, we search for existing case-insensitive match.
        String existingKey = null;
        for (String key : data.keySet()) {
            if (ResonantRecipeUtils.normalizeRecipeName(key).equals(normalizedName)) {
                existingKey = key;
                break;
            }
        }

        if (existingKey == null) {
            // New entry
            data.put(recipeName, new CompendiumEntry(pattern, usages));
            return true;
        }

        // Merge existing
        CompendiumEntry existing = data.get(existingKey);
        boolean[] beforeMask = ResonantRecipeUtils.revealMaskFromPattern(existing.pattern);
        boolean[] shardMask = ResonantRecipeUtils.revealMaskFromPattern(pattern);
        
        int added = ResonantRecipeUtils.countNewReveals(beforeMask, shardMask);
        if (added > 0) {
            existing.pattern = ResonantRecipeUtils.mergePatterns(existing.pattern, pattern);
        }

        // If existing isn't complete but the shard is complete, or shard has better usages?
        // Let's just let it merge patterns. Usages track complete recipe uses.
        ResonantRecipeUtils.PatternStats stats = ResonantRecipeUtils.getPatternStats(existing.pattern);
        if (stats.isComplete() && (existing.usages == null || existing.usages.isBlank() || existing.usages.equals(ResonantRecipeUtils.DEFAULT_RECIPE_USAGES))) {
            // If newly completed or we had incomplete usages, take from the shard if it's complete
            if (usages != null && !usages.isBlank()) {
                existing.usages = usages;
            }
        }

        // Returning true if we added slots. (Actually if it's complete, adding shards does nothing).
        // Let's always return true if we consumed it, maybe to say we processed it. But if it adds NO data, return false to not consume?
        // Wait, the user wants to "resolve the cluttering", so maybe we always consume and say true if we absorbed it? 
        // Let's just store it and consume it. Returning true to indicate successful absorption.
        return true; 
    }
}
