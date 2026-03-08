package irai.mod.reforge.Common;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class TreeBlockUtils {
    private static final String LEAF_PREFIX = "Plant_Leaves_";
    private static final String SAPLING_PREFIX = "Plant_Sapling_";
    private static final String SEED_PREFIX = "Plant_Seeds_";
    private static final Map<String, String> TREE_LEAF_DROP_OVERRIDES = Map.ofEntries(
            Map.entry("Plant_Leaves_Oak", "Plant_Sapling_Oak"),
            Map.entry("Plant_Leaves_Ash", "Plant_Sapling_Ash"),
            Map.entry("Plant_Leaves_Beech", "Plant_Sapling_Beech"),
            Map.entry("Plant_Leaves_Birch", "Plant_Sapling_Birch"),
            Map.entry("Plant_Leaves_Cedar", "Plant_Sapling_Cedar"),
            Map.entry("Plant_Leaves_Crystal", "Plant_Sapling_Crystal"),
            Map.entry("Plant_Leaves_Dry", "Plant_Sapling_Dry"),
            Map.entry("Plant_Leaves_Aspen", "Plant_Seeds_Aspen"),
            Map.entry("Plant_Leaves_Azure", "Plant_Seeds_Azure"),
            Map.entry("Plant_Leaves_Bottle", "Plant_Seeds_Bottletree"),
            Map.entry("Plant_Leaves_Fire", "Plant_Seeds_Fire"),
            Map.entry("Plant_Leaves_Gumboab", "Plant_Seeds_Gumboab"),
            Map.entry("Plant_Leaves_Maple", "Plant_Seeds_Maple"),
            Map.entry("Plant_Leaves_Palm", "Plant_Sapling_Palm"),
            Map.entry("Plant_Leaves_Palm_Arid", "Plant_Sapling_Palm"),
            Map.entry("Plant_Leaves_Palm_Oasis", "Plant_Sapling_Palm"),
            Map.entry("Plant_Leaves_Poisoned", "Plant_Sapling_Poisoned"),
            Map.entry("Plant_Leaves_Redwood", "Plant_Sapling_Redwood"),
            Map.entry("Plant_Leaves_Windwillow", "Plant_Sapling_Windwillow"),
            Map.entry("Plant_Leaves_Fir", "Plant_Sapling_Spruce"),
            Map.entry("Plant_Leaves_Fir_Red", "Plant_Sapling_Spruce"),
            Map.entry("Plant_Leaves_Fir_Tip", "Plant_Sapling_Spruce"),
            Map.entry("Plant_Leaves_Fir_Snow", "Plant_Sapling_Spruce_Frozen"),
            Map.entry("Plant_Leaves_Fir_Tip_Snow", "Plant_Sapling_Spruce_Frozen"));

    private TreeBlockUtils() {}

    public static Map<String, String> getConfiguredTreeLeafDrops() {
        Map<String, String> configured = new TreeMap<>();
        Map<String, Item> assetMap = getItemAssetMap();
        if (assetMap == null || assetMap.isEmpty()) {
            configured.putAll(TREE_LEAF_DROP_OVERRIDES);
            return configured;
        }

        for (String itemId : assetMap.keySet()) {
            String normalizedItemId = normalizeId(itemId);
            String dropItemId = getDropItemIdForLeaf(normalizedItemId);
            if (normalizedItemId != null && dropItemId != null) {
                configured.put(normalizedItemId, dropItemId);
            }
        }
        return configured;
    }

    public static String normalizeId(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }

        int stateSeparator = candidate.indexOf(':');
        return stateSeparator >= 0 ? candidate.substring(0, stateSeparator) : candidate;
    }

    public static String getDropItemIdForLeaf(String leafId) {
        String normalized = normalizeId(leafId);
        if (normalized == null) {
            return null;
        }

        String explicitMatch = TREE_LEAF_DROP_OVERRIDES.get(normalized);
        if (explicitMatch != null) {
            return explicitMatch;
        }

        if (!normalized.startsWith(LEAF_PREFIX)) {
            return null;
        }

        String suffix = normalized.substring(LEAF_PREFIX.length());
        if (suffix.isBlank()) {
            return null;
        }

        String saplingCandidate = SAPLING_PREFIX + suffix;
        if (itemExists(saplingCandidate)) {
            return saplingCandidate;
        }

        String seedCandidate = SEED_PREFIX + suffix;
        if (itemExists(seedCandidate)) {
            return seedCandidate;
        }

        return null;
    }

    public static boolean isLeafBlock(BlockType blockType) {
        return resolveLeafBlockId(blockType) != null;
    }

    public static boolean isTrunkBlock(BlockType blockType) {
        String normalizedId = normalizeId(blockType == null ? null : blockType.getId());
        if (normalizedId == null) {
            return false;
        }

        String lowerId = normalizedId.toLowerCase(Locale.ROOT);
        if (!lowerId.startsWith("wood_")) {
            return false;
        }
        return lowerId.contains("_trunk")
                || lowerId.contains("_branch")
                || lowerId.contains("_roots");
    }

    public static String resolveLeafBlockId(BlockType blockType) {
        if (blockType == null || blockType == BlockType.UNKNOWN) {
            return null;
        }

        String directMatch = matchLeafId(blockType.getId());
        if (directMatch != null) {
            return directMatch;
        }

        Item item = blockType.getItem();
        if (item == null || item == Item.UNKNOWN) {
            return null;
        }
        return matchLeafId(item.getId());
    }

    private static String matchLeafId(String candidate) {
        String normalized = normalizeId(candidate);
        if (normalized == null) {
            return null;
        }

        if (getDropItemIdForLeaf(normalized) != null) {
            return normalized;
        }
        return null;
    }

    private static boolean itemExists(String itemId) {
        Map<String, Item> assetMap = getItemAssetMap();
        if (assetMap == null || itemId == null) {
            return false;
        }
        Item item = assetMap.get(itemId);
        return item != null && item != Item.UNKNOWN;
    }

    private static Map<String, Item> getItemAssetMap() {
        return Item.getAssetMap() == null ? null : Item.getAssetMap().getAssetMap();
    }
}
