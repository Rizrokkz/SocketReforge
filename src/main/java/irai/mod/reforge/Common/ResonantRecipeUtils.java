package irai.mod.reforge.Common;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import irai.mod.reforge.Socket.Essence;
import irai.mod.reforge.Socket.ResonanceSystem;

/**
 * Helpers for resonant recipe items and their partial-pattern metadata.
 */
@SuppressWarnings("removal")
public final class ResonantRecipeUtils {
    public static final String RECIPE_ITEM_ID = "Resonant_Recipe";
    public static final String META_RECIPE_NAME = "SocketReforge.Recipe.ResonanceName";
    public static final String META_RECIPE_PATTERN = "SocketReforge.Recipe.Pattern";
    public static final String META_RECIPE_TYPE = "SocketReforge.Recipe.Type";
    public static final String META_RECIPE_USAGES = "SocketReforge.Recipe.Usages";
    public static final String DEFAULT_RECIPE_USAGES = "3/3";

    private ResonantRecipeUtils() {}

    public static boolean isResonantRecipeItem(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && RECIPE_ITEM_ID.equalsIgnoreCase(stack.getItemId());
    }

    public static String getRecipeName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return stack.getFromMetadataOrNull(META_RECIPE_NAME, Codec.STRING);
    }

    public static String getRecipePattern(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return stack.getFromMetadataOrNull(META_RECIPE_PATTERN, Codec.STRING);
    }

    public static String getRecipeType(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return stack.getFromMetadataOrNull(META_RECIPE_TYPE, Codec.STRING);
    }

    public static String getRecipeUsages(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return stack.getFromMetadataOrNull(META_RECIPE_USAGES, Codec.STRING);
    }

    public static boolean isRecipeComplete(ItemStack stack) {
        if (!isResonantRecipeItem(stack)) {
            return false;
        }
        PatternStats stats = getPatternStats(getRecipePattern(stack));
        return stats.isComplete();
    }

    public static UsageState getUsageState(ItemStack stack) {
        return parseUsages(getRecipeUsages(stack));
    }

    public static UsageState parseUsages(String raw) {
        if (raw == null || raw.isBlank()) {
            return new UsageState(0, 0);
        }
        String[] parts = raw.trim().split("/");
        if (parts.length == 0) {
            return new UsageState(0, 0);
        }
        try {
            int remaining = Integer.parseInt(parts[0].trim());
            int max = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : remaining;
            remaining = Math.max(0, remaining);
            max = Math.max(0, max);
            if (max > 0 && remaining > max) {
                remaining = max;
            }
            return new UsageState(remaining, max);
        } catch (NumberFormatException ignored) {
            return new UsageState(0, 0);
        }
    }

    public static String formatUsages(UsageState usage) {
        if (usage == null) {
            return "";
        }
        return usage.remaining() + "/" + usage.max();
    }

    public static ItemStack decrementUsage(ItemStack stack) {
        if (!isResonantRecipeItem(stack)) {
            return stack;
        }
        UsageState usage = getUsageState(stack);
        if (usage.max() <= 0) {
            return stack;
        }
        int remaining = Math.max(0, usage.remaining() - 1);
        return withRecipeUsages(stack, remaining + "/" + usage.max());
    }

    public static ItemStack withRecipePattern(ItemStack stack, String pattern) {
        if (stack == null || stack.isEmpty() || pattern == null) {
            return stack;
        }
        return stack.withMetadata(META_RECIPE_PATTERN, Codec.STRING, pattern);
    }

    public static ItemStack withRecipeUsages(ItemStack stack, String usages) {
        if (stack == null || stack.isEmpty() || usages == null) {
            return stack;
        }
        return stack.withMetadata(META_RECIPE_USAGES, Codec.STRING, usages);
    }

    public static ItemStack ensureRecipeUsages(ItemStack stack) {
        if (!isResonantRecipeItem(stack)) {
            return stack;
        }
        PatternStats stats = getPatternStats(getRecipePattern(stack));
        if (!stats.isComplete()) {
            return stack;
        }
        String usages = getRecipeUsages(stack);
        if (usages == null || usages.isBlank()) {
            return withRecipeUsages(stack, DEFAULT_RECIPE_USAGES);
        }
        return stack;
    }

    public static String normalizeRecipeName(String recipeName) {
        if (recipeName == null) {
            return "";
        }
        return recipeName.trim().toLowerCase(Locale.ROOT);
    }

    public static PatternStats getPatternStats(String pattern) {
        List<String> tokens = parsePatternTokens(pattern);
        int total = tokens.size();
        int known = 0;
        for (String token : tokens) {
            if (!isUnknownToken(token)) {
                known++;
            }
        }
        return new PatternStats(total, known);
    }

    public static String mergePatterns(String basePattern, String otherPattern) {
        List<String> baseTokens = parsePatternTokens(basePattern);
        List<String> otherTokens = parsePatternTokens(otherPattern);
        int length = Math.max(baseTokens.size(), otherTokens.size());
        if (length == 0) {
            return "";
        }

        List<String> merged = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            String base = i < baseTokens.size() ? baseTokens.get(i) : "x";
            String other = i < otherTokens.size() ? otherTokens.get(i) : "x";
            String selected = !isUnknownToken(base) ? base : other;
            merged.add(normalizeToken(selected));
        }
        return formatPatternTokens(merged);
    }

    public static String mergePatterns(List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return "";
        }
        String merged = "";
        for (String pattern : patterns) {
            merged = mergePatterns(merged, pattern);
        }
        return merged;
    }

    public static ItemStack remapResonantRecipePattern(ItemStack stack) {
        if (!isResonantRecipeItem(stack)) {
            return stack;
        }
        String recipeName = getRecipeName(stack);
        if (recipeName == null || recipeName.isBlank()) {
            return stack;
        }
        String oldPattern = getRecipePattern(stack);
        if (oldPattern == null || oldPattern.isBlank()) {
            return stack;
        }
        Essence.Type[] newPattern = ResonanceSystem.getPatternForRecipeName(recipeName);
        if (newPattern == null || newPattern.length == 0) {
            return stack;
        }
        boolean[] revealMask = revealMaskFromPattern(oldPattern);
        String remapped = buildPatternFromEssence(newPattern, revealMask);
        if (remapped.isBlank() || remapped.equals(oldPattern)) {
            return stack;
        }
        return stack.withMetadata(META_RECIPE_PATTERN, Codec.STRING, remapped);
    }

    public record PatternStats(int totalSlots, int revealedSlots) {
        public boolean isComplete() {
            return totalSlots > 0 && revealedSlots >= totalSlots;
        }
    }

    public record UsageState(int remaining, int max) {
        public boolean hasRemaining() {
            return remaining > 0;
        }
    }

    public static boolean[] revealMaskFromPattern(String pattern) {
        List<String> tokens = parsePatternTokens(pattern);
        boolean[] mask = new boolean[tokens.size()];
        for (int i = 0; i < tokens.size(); i++) {
            mask[i] = !isUnknownToken(tokens.get(i));
        }
        return mask;
    }

    public interface MergeCandidate {
        String getPattern();
        boolean[] getRevealMask();
    }

    public static <T extends MergeCandidate> List<T> selectBestMergeCandidates(String basePattern, List<T> candidates) {
        List<T> remaining = new ArrayList<>(candidates);
        List<T> selected = new ArrayList<>();
        boolean[] currentMask = revealMaskFromPattern(basePattern);
        String mergedPattern = basePattern == null ? "" : basePattern;

        while (!remaining.isEmpty()) {
            int bestIndex = -1;
            int bestGain = 0;
            for (int i = 0; i < remaining.size(); i++) {
                T candidate = remaining.get(i);
                int gain = countNewReveals(currentMask, candidate.getRevealMask());
                if (gain > bestGain) {
                    bestGain = gain;
                    bestIndex = i;
                }
            }
            if (bestIndex < 0 || bestGain <= 0) break;

            T chosen = remaining.remove(bestIndex);
            selected.add(chosen);
            mergedPattern = mergePatterns(mergedPattern, chosen.getPattern());
            currentMask = mergeMasks(currentMask, chosen.getRevealMask());
            if (getPatternStats(mergedPattern).isComplete()) break;
        }
        return selected;
    }

    public static int countNewReveals(boolean[] currentMask, boolean[] candidateMask) {
        int length = Math.max(
                currentMask == null ? 0 : currentMask.length,
                candidateMask == null ? 0 : candidateMask.length);
        int added = 0;
        for (int i = 0; i < length; i++) {
            boolean current = currentMask != null && i < currentMask.length && currentMask[i];
            boolean candidate = candidateMask != null && i < candidateMask.length && candidateMask[i];
            if (candidate && !current) added++;
        }
        return added;
    }

    public static boolean[] mergeMasks(boolean[] baseMask, boolean[] addMask) {
        int length = Math.max(
                baseMask == null ? 0 : baseMask.length,
                addMask == null ? 0 : addMask.length);
        boolean[] merged = new boolean[length];
        for (int i = 0; i < length; i++) {
            boolean base = baseMask != null && i < baseMask.length && baseMask[i];
            boolean add = addMask != null && i < addMask.length && addMask[i];
            merged[i] = base || add;
        }
        return merged;
    }

    public static String buildPatternFromEssence(Essence.Type[] pattern, boolean[] revealMask) {
        if (pattern == null || pattern.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pattern.length; i++) {
            boolean reveal = revealMask == null || (i < revealMask.length && revealMask[i]);
            String token = reveal ? formatEssenceToken(pattern[i]) : "x";
            sb.append('[').append(token).append(']');
        }
        return sb.toString();
    }

    private static List<String> parsePatternTokens(String pattern) {
        List<String> tokens = new ArrayList<>();
        if (pattern == null || pattern.isBlank()) {
            return tokens;
        }

        int idx = 0;
        while (idx < pattern.length()) {
            int start = pattern.indexOf('[', idx);
            if (start < 0) {
                break;
            }
            int end = pattern.indexOf(']', start + 1);
            if (end < 0) {
                break;
            }
            String token = pattern.substring(start + 1, end).trim();
            tokens.add(token.isEmpty() ? "x" : token);
            idx = end + 1;
        }

        if (!tokens.isEmpty()) {
            return tokens;
        }

        // Fallback: split on commas if pattern is in a legacy format.
        String[] parts = pattern.split(",");
        for (String part : parts) {
            String token = part == null ? "" : part.trim();
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static String formatPatternTokens(List<String> tokens) {
        StringBuilder sb = new StringBuilder();
        for (String token : tokens) {
            sb.append('[').append(normalizeToken(token)).append(']');
        }
        return sb.toString();
    }

    private static boolean isUnknownToken(String token) {
        if (token == null) {
            return true;
        }
        String trimmed = token.trim();
        return trimmed.isEmpty() || "x".equalsIgnoreCase(trimmed);
    }

    private static String normalizeToken(String token) {
        if (isUnknownToken(token)) {
            return "x";
        }
        String trimmed = token.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String formatEssenceToken(Essence.Type type) {
        String raw = type == null ? "" : type.name().toLowerCase(Locale.ROOT);
        if (raw.isEmpty()) {
            return "x";
        }
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }
}
