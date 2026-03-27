package irai.mod.reforge.Config;

import static com.hypixel.hytale.codec.Codec.DOUBLE_ARRAY;
import static com.hypixel.hytale.codec.Codec.STRING_ARRAY;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Configuration for Lore sockets, leveling, and feeding.
 */
@SuppressWarnings("removal")
public class LoreConfig {

    public static final BuilderCodec<LoreConfig> CODEC = BuilderCodec.<LoreConfig>builder(LoreConfig.class, LoreConfig::new)
            .append(
                    new KeyedCodec<>("LORE_SOCKET_CHEST_CHANCE", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.chestLoreSocketChance = v[0]; },
                    cfg -> new double[]{cfg.chestLoreSocketChance}
            ).add()
            .append(
                    new KeyedCodec<>("LORE_SOCKET_DROP_CHANCE", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.dropLoreSocketChance = v[0]; },
                    cfg -> new double[]{cfg.dropLoreSocketChance}
            ).add()
            .append(
                    new KeyedCodec<>("LORE_SOCKET_MIN", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.minLoreSockets = (int) Math.round(v[0]); },
                    cfg -> new double[]{cfg.minLoreSockets}
            ).add()
            .append(
                    new KeyedCodec<>("LORE_SOCKET_MAX", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.maxLoreSockets = (int) Math.round(v[0]); },
                    cfg -> new double[]{cfg.maxLoreSockets}
            ).add()
            .append(
                    new KeyedCodec<>("LORE_LEVEL_MAX", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.maxLevel = (int) Math.round(v[0]); },
                    cfg -> new double[]{cfg.maxLevel}
            ).add()
            .append(
                    new KeyedCodec<>("LORE_XP_PER_PROC", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.xpPerProc = (int) Math.round(v[0]); },
                    cfg -> new double[]{cfg.xpPerProc}
            ).add()
            .append(
                    new KeyedCodec<>("LORE_BASE_XP_PER_LEVEL", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.baseXpPerLevel = (int) Math.round(v[0]); },
                    cfg -> new double[]{cfg.baseXpPerLevel}
            ).add()
            .append(
                    new KeyedCodec<>("LORE_XP_GROWTH_PER_LEVEL", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.xpGrowthPerLevel = v[0]; },
                    cfg -> new double[]{cfg.xpGrowthPerLevel}
            ).add()
            .append(
                    new KeyedCodec<>("LORE_FEED_INTERVAL", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.feedInterval = (int) Math.round(v[0]); },
                    cfg -> new double[]{cfg.feedInterval}
            ).add()
            .append(
                    new KeyedCodec<>("LORE_FEED_BASE", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.feedBase = (int) Math.round(v[0]); },
                    cfg -> new double[]{cfg.feedBase}
            ).add()
            .append(
                    new KeyedCodec<>("LORE_FEED_MULTIPLIER", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.feedMultiplier = (int) Math.round(v[0]); },
                    cfg -> new double[]{cfg.feedMultiplier}
            ).add()
            .append(
                    new KeyedCodec<>("LORE_FEED_ITEM_IDS", STRING_ARRAY),
                    (cfg, v) -> cfg.feedItemIds = v,
                    cfg -> cfg.feedItemIds == null ? new String[0] : cfg.feedItemIds
            ).add()
            .append(
                    new KeyedCodec<>("LORE_CLEAR_ITEM_IDS", STRING_ARRAY),
                    (cfg, v) -> cfg.clearItemIds = v,
                    cfg -> cfg.clearItemIds == null ? new String[0] : cfg.clearItemIds
            ).add()
            .build();

    private double chestLoreSocketChance = 0.0d;
    private double dropLoreSocketChance = 0.0d;
    private int minLoreSockets = 1;
    private int maxLoreSockets = 3;

    private int maxLevel = 30;
    private int xpPerProc = 1;
    private int baseXpPerLevel = 10;
    private double xpGrowthPerLevel = 2.0d;

    private int feedInterval = 3;
    private int feedBase = 1;
    private int feedMultiplier = 2;
    private String[] feedItemIds = new String[]{"Ingredient_Resonant_Essence"};
    private String[] clearItemIds = new String[]{"Ingredient_Ghastly_Essence"};

    public double getChestLoreSocketChance() { return chestLoreSocketChance; }
    public double getDropLoreSocketChance() { return dropLoreSocketChance; }
    public int getMinLoreSockets() { return minLoreSockets; }
    public int getMaxLoreSockets() { return maxLoreSockets; }
    public int getMaxLevel() { return maxLevel; }
    public int getXpPerProc() { return xpPerProc; }
    public int getBaseXpPerLevel() { return baseXpPerLevel; }
    public double getXpGrowthPerLevel() { return xpGrowthPerLevel; }
    public int getFeedInterval() { return feedInterval; }
    public int getFeedBase() { return feedBase; }
    public int getFeedMultiplier() { return feedMultiplier; }
    public String[] getFeedItemIds() { return feedItemIds; }
    public String[] getClearItemIds() { return clearItemIds; }

    public void setChestLoreSocketChance(double v) { this.chestLoreSocketChance = v; }
    public void setDropLoreSocketChance(double v) { this.dropLoreSocketChance = v; }
    public void setMinLoreSockets(int v) { this.minLoreSockets = Math.max(0, v); }
    public void setMaxLoreSockets(int v) { this.maxLoreSockets = Math.max(this.minLoreSockets, v); }
    public void setMaxLevel(int v) { this.maxLevel = Math.max(1, v); }
    public void setXpPerProc(int v) { this.xpPerProc = Math.max(1, v); }
    public void setBaseXpPerLevel(int v) { this.baseXpPerLevel = Math.max(1, v); }
    public void setXpGrowthPerLevel(double v) { this.xpGrowthPerLevel = Math.max(0.0d, v); }
    public void setFeedInterval(int v) { this.feedInterval = Math.max(1, v); }
    public void setFeedBase(int v) { this.feedBase = Math.max(0, v); }
    public void setFeedMultiplier(int v) { this.feedMultiplier = Math.max(1, v); }
    public void setFeedItemIds(String[] v) { this.feedItemIds = v; }
    public void setClearItemIds(String[] v) { this.clearItemIds = v; }
}
