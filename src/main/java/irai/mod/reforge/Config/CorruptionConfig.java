package irai.mod.reforge.Config;

import java.util.HashMap;
import java.util.Map;

import static com.hypixel.hytale.codec.Codec.BOOLEAN;
import static com.hypixel.hytale.codec.Codec.DOUBLE;
import static com.hypixel.hytale.codec.Codec.STRING;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

@SuppressWarnings("removal")
public class CorruptionConfig implements ConfigDefaultInjector {
    private static final boolean DEFAULT_ENABLED = true;
    private static final String DEFAULT_TIER_1_COST = "Ingredient_Voidheart";
    private static final String DEFAULT_TIER_2_COST = "Ingredient_Abyss_Essence";
    private static final double DEFAULT_MAX_CORRUPTIONS = 2;
    private static final double DEFAULT_TIER_2_ITEM_LOSS = 0.50;
    private static final double DEFAULT_STAT_INCREASE_CHANCE = 0.35;
    private static final double DEFAULT_STAT_DECREASE_CHANCE = 0.20;
    private static final double DEFAULT_STAT_INVERT_CHANCE = 0.10;
    private static final double DEFAULT_STAT_REMOVE_CHANCE = 0.10;
    private static final double DEFAULT_ADD_NEW_STAT_CHANCE = 0.10;
    private static final double DEFAULT_RESONANT_STAT_CHANCE = 0.15;
    private static final double DEFAULT_MIN_STAT_MODIFIER = 0.10;
    private static final double DEFAULT_MAX_STAT_MODIFIER = 0.50;

    public static final BuilderCodec<CorruptionConfig> CODEC = BuilderCodec.<CorruptionConfig>builder(CorruptionConfig.class, CorruptionConfig::new)
            .append(new KeyedCodec<>("ENABLED", BOOLEAN),
                    (config, value) -> config.enabled = value != null ? value : DEFAULT_ENABLED,
                    config -> config.enabled).add()
            .append(new KeyedCodec<>("TIER_1_COST", STRING),
                    (config, value) -> config.tier1Cost = value != null && !value.isBlank() ? value : DEFAULT_TIER_1_COST,
                    config -> config.tier1Cost).add()
            .append(new KeyedCodec<>("TIER_2_COST", STRING),
                    (config, value) -> config.tier2Cost = value != null && !value.isBlank() ? value : DEFAULT_TIER_2_COST,
                    config -> config.tier2Cost).add()
            .append(new KeyedCodec<>("MAX_CORRUPTIONS", DOUBLE),
                    (config, value) -> config.maxCorruptions = value != null ? Math.max(1, Math.min(5, value)) : DEFAULT_MAX_CORRUPTIONS,
                    config -> config.maxCorruptions).add()
            .append(new KeyedCodec<>("TIER_2_ITEM_LOSS", DOUBLE),
                    (config, value) -> config.tier2ItemLoss = value != null ? Math.max(0.0, Math.min(1.0, value)) : DEFAULT_TIER_2_ITEM_LOSS,
                    config -> config.tier2ItemLoss).add()
            .append(new KeyedCodec<>("STAT_INCREASE_CHANCE", DOUBLE),
                    (config, value) -> config.statIncreaseChance = value != null ? Math.max(0.0, Math.min(1.0, value)) : DEFAULT_STAT_INCREASE_CHANCE,
                    config -> config.statIncreaseChance).add()
            .append(new KeyedCodec<>("STAT_DECREASE_CHANCE", DOUBLE),
                    (config, value) -> config.statDecreaseChance = value != null ? Math.max(0.0, Math.min(1.0, value)) : DEFAULT_STAT_DECREASE_CHANCE,
                    config -> config.statDecreaseChance).add()
            .append(new KeyedCodec<>("STAT_INVERT_CHANCE", DOUBLE),
                    (config, value) -> config.statInvertChance = value != null ? Math.max(0.0, Math.min(1.0, value)) : DEFAULT_STAT_INVERT_CHANCE,
                    config -> config.statInvertChance).add()
            .append(new KeyedCodec<>("STAT_REMOVE_CHANCE", DOUBLE),
                    (config, value) -> config.statRemoveChance = value != null ? Math.max(0.0, Math.min(1.0, value)) : DEFAULT_STAT_REMOVE_CHANCE,
                    config -> config.statRemoveChance).add()
            .append(new KeyedCodec<>("ADD_NEW_STAT_CHANCE", DOUBLE),
                    (config, value) -> config.addNewStatChance = value != null ? Math.max(0.0, Math.min(1.0, value)) : DEFAULT_ADD_NEW_STAT_CHANCE,
                    config -> config.addNewStatChance).add()
            .append(new KeyedCodec<>("RESONANT_STAT_CHANCE", DOUBLE),
                    (config, value) -> config.resonantStatChance = value != null ? Math.max(0.0, Math.min(1.0, value)) : DEFAULT_RESONANT_STAT_CHANCE,
                    config -> config.resonantStatChance).add()
            .append(new KeyedCodec<>("MIN_STAT_MODIFIER", DOUBLE),
                    (config, value) -> config.minStatModifier = value != null ? Math.max(0.0, Math.min(1.0, value)) : DEFAULT_MIN_STAT_MODIFIER,
                    config -> config.minStatModifier).add()
            .append(new KeyedCodec<>("MAX_STAT_MODIFIER", DOUBLE),
                    (config, value) -> config.maxStatModifier = value != null ? Math.max(0.0, Math.min(1.0, value)) : DEFAULT_MAX_STAT_MODIFIER,
                    config -> config.maxStatModifier).add()
            .build();

    private boolean enabled = DEFAULT_ENABLED;
    private String tier1Cost = DEFAULT_TIER_1_COST;
    private String tier2Cost = DEFAULT_TIER_2_COST;
    private double maxCorruptions = DEFAULT_MAX_CORRUPTIONS;
    private double tier2ItemLoss = DEFAULT_TIER_2_ITEM_LOSS;
    private double statIncreaseChance = DEFAULT_STAT_INCREASE_CHANCE;
    private double statDecreaseChance = DEFAULT_STAT_DECREASE_CHANCE;
    private double statInvertChance = DEFAULT_STAT_INVERT_CHANCE;
    private double statRemoveChance = DEFAULT_STAT_REMOVE_CHANCE;
    private double addNewStatChance = DEFAULT_ADD_NEW_STAT_CHANCE;
    private double resonantStatChance = DEFAULT_RESONANT_STAT_CHANCE;
    private double minStatModifier = DEFAULT_MIN_STAT_MODIFIER;
    private double maxStatModifier = DEFAULT_MAX_STAT_MODIFIER;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getTier1Cost() { return tier1Cost; }
    public void setTier1Cost(String tier1Cost) { this.tier1Cost = tier1Cost; }
    public String getTier2Cost() { return tier2Cost; }
    public void setTier2Cost(String tier2Cost) { this.tier2Cost = tier2Cost; }
    public double getMaxCorruptions() { return maxCorruptions; }
    public void setMaxCorruptions(double maxCorruptions) { this.maxCorruptions = Math.max(1, Math.min(5, maxCorruptions)); }
    public double getTier2ItemLoss() { return tier2ItemLoss; }
    public void setTier2ItemLoss(double tier2ItemLoss) { this.tier2ItemLoss = Math.max(0.0, Math.min(1.0, tier2ItemLoss)); }
    public double getStatIncreaseChance() { return statIncreaseChance; }
    public void setStatIncreaseChance(double v) { this.statIncreaseChance = Math.max(0.0, Math.min(1.0, v)); }
    public double getStatDecreaseChance() { return statDecreaseChance; }
    public void setStatDecreaseChance(double v) { this.statDecreaseChance = Math.max(0.0, Math.min(1.0, v)); }
    public double getStatInvertChance() { return statInvertChance; }
    public void setStatInvertChance(double v) { this.statInvertChance = Math.max(0.0, Math.min(1.0, v)); }
    public double getStatRemoveChance() { return statRemoveChance; }
    public void setStatRemoveChance(double v) { this.statRemoveChance = Math.max(0.0, Math.min(1.0, v)); }
    public double getAddNewStatChance() { return addNewStatChance; }
    public void setAddNewStatChance(double v) { this.addNewStatChance = Math.max(0.0, Math.min(1.0, v)); }
    public double getResonantStatChance() { return resonantStatChance; }
    public void setResonantStatChance(double v) { this.resonantStatChance = Math.max(0.0, Math.min(1.0, v)); }
    public double getMinStatModifier() { return minStatModifier; }
    public void setMinStatModifier(double v) { this.minStatModifier = Math.max(0.0, Math.min(1.0, v)); }
    public double getMaxStatModifier() { return maxStatModifier; }
    public void setMaxStatModifier(double v) { this.maxStatModifier = Math.max(0.0, Math.min(1.0, v)); }

    @Override
    public boolean injectMissingDefaults() { return false; }

    public void resetToDefaults() {
        this.enabled = DEFAULT_ENABLED;
        this.tier1Cost = DEFAULT_TIER_1_COST;
        this.tier2Cost = DEFAULT_TIER_2_COST;
        this.maxCorruptions = DEFAULT_MAX_CORRUPTIONS;
        this.tier2ItemLoss = DEFAULT_TIER_2_ITEM_LOSS;
        this.statIncreaseChance = DEFAULT_STAT_INCREASE_CHANCE;
        this.statDecreaseChance = DEFAULT_STAT_DECREASE_CHANCE;
        this.statInvertChance = DEFAULT_STAT_INVERT_CHANCE;
        this.statRemoveChance = DEFAULT_STAT_REMOVE_CHANCE;
        this.addNewStatChance = DEFAULT_ADD_NEW_STAT_CHANCE;
        this.resonantStatChance = DEFAULT_RESONANT_STAT_CHANCE;
        this.minStatModifier = DEFAULT_MIN_STAT_MODIFIER;
        this.maxStatModifier = DEFAULT_MAX_STAT_MODIFIER;
    }
}
