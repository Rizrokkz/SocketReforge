package irai.mod.reforge.Config;

import java.util.HashMap;
import java.util.Map;

import static com.hypixel.hytale.codec.Codec.BOOLEAN;
import static com.hypixel.hytale.codec.Codec.DOUBLE;
import static com.hypixel.hytale.codec.Codec.INTEGER;
import static com.hypixel.hytale.codec.Codec.STRING;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

@SuppressWarnings("removal")
public class AscensionConfig implements ConfigDefaultInjector {
    private static final boolean DEFAULT_ENABLED = true;
    private static final int DEFAULT_MAX_TIER = 3;
    private static final int MAX_ASCENSION_TIERS = 10;
    private static final double DEFAULT_TIER_1_CARRY_OVER = 0.10;
    private static final double DEFAULT_TIER_2_CARRY_OVER = 0.15;
    private static final double DEFAULT_TIER_3_CARRY_OVER = 0.25;
    private static final double DEFAULT_CARRY_OVER = 0.25;
    private static final String DEFAULT_TIER_1_COST = "Ingredient_Resonant_Essence";
    private static final String DEFAULT_TIER_2_COST = "Ingredient_Ghastly_Essence";
    private static final String DEFAULT_TIER_3_COST = "Ingredient_Abyss_Essence";

    public static final BuilderCodec<AscensionConfig> CODEC = BuilderCodec.<AscensionConfig>builder(AscensionConfig.class, AscensionConfig::new)
            .append(
                    new KeyedCodec<>("ENABLED", BOOLEAN),
                    (config, value) -> config.enabled = value != null ? value : DEFAULT_ENABLED,
                    config -> config.enabled
            ).add()
            .append(
                    new KeyedCodec<>("MAX_TIER", INTEGER),
                    (config, value) -> config.maxTier = value != null ? Math.max(1, Math.min(MAX_ASCENSION_TIERS, value)) : DEFAULT_MAX_TIER,
                    config -> config.maxTier
            ).add()
            .append(
                    new KeyedCodec<>("TIER_1_CARRY_OVER", DOUBLE),
                    (config, value) -> config.carryOver[0] = value != null ? Math.max(0.0, Math.min(1.0, value)) : DEFAULT_TIER_1_CARRY_OVER,
                    config -> config.carryOver[0]
            ).add()
            .append(
                    new KeyedCodec<>("TIER_2_CARRY_OVER", DOUBLE),
                    (config, value) -> config.carryOver[1] = value != null ? Math.max(0.0, Math.min(1.0, value)) : DEFAULT_TIER_2_CARRY_OVER,
                    config -> config.carryOver[1]
            ).add()
            .append(
                    new KeyedCodec<>("TIER_3_CARRY_OVER", DOUBLE),
                    (config, value) -> config.carryOver[2] = value != null ? Math.max(0.0, Math.min(1.0, value)) : DEFAULT_TIER_3_CARRY_OVER,
                    config -> config.carryOver[2]
            ).add()
            .append(
                    new KeyedCodec<>("TIER_4_CARRY_OVER", DOUBLE),
                    (config, value) -> config.carryOver[3] = value != null ? Math.max(0.0, Math.min(1.0, value)) : DEFAULT_CARRY_OVER,
                    config -> config.carryOver[3]
            ).add()
            .append(
                    new KeyedCodec<>("TIER_5_CARRY_OVER", DOUBLE),
                    (config, value) -> config.carryOver[4] = value != null ? Math.max(0.0, Math.min(1.0, value)) : DEFAULT_CARRY_OVER,
                    config -> config.carryOver[4]
            ).add()
            .append(
                    new KeyedCodec<>("TIER_6_CARRY_OVER", DOUBLE),
                    (config, value) -> config.carryOver[5] = value != null ? Math.max(0.0, Math.min(1.0, value)) : DEFAULT_CARRY_OVER,
                    config -> config.carryOver[5]
            ).add()
            .append(
                    new KeyedCodec<>("TIER_7_CARRY_OVER", DOUBLE),
                    (config, value) -> config.carryOver[6] = value != null ? Math.max(0.0, Math.min(1.0, value)) : DEFAULT_CARRY_OVER,
                    config -> config.carryOver[6]
            ).add()
            .append(
                    new KeyedCodec<>("TIER_8_CARRY_OVER", DOUBLE),
                    (config, value) -> config.carryOver[7] = value != null ? Math.max(0.0, Math.min(1.0, value)) : DEFAULT_CARRY_OVER,
                    config -> config.carryOver[7]
            ).add()
            .append(
                    new KeyedCodec<>("TIER_9_CARRY_OVER", DOUBLE),
                    (config, value) -> config.carryOver[8] = value != null ? Math.max(0.0, Math.min(1.0, value)) : DEFAULT_CARRY_OVER,
                    config -> config.carryOver[8]
            ).add()
            .append(
                    new KeyedCodec<>("TIER_10_CARRY_OVER", DOUBLE),
                    (config, value) -> config.carryOver[9] = value != null ? Math.max(0.0, Math.min(1.0, value)) : DEFAULT_CARRY_OVER,
                    config -> config.carryOver[9]
            ).add()
            .append(
                    new KeyedCodec<>("TIER_1_COST", STRING),
                    (config, value) -> config.tierCosts[0] = value != null && !value.isBlank() ? value : DEFAULT_TIER_1_COST,
                    config -> config.tierCosts[0]
            ).add()
            .append(
                    new KeyedCodec<>("TIER_2_COST", STRING),
                    (config, value) -> config.tierCosts[1] = value != null && !value.isBlank() ? value : DEFAULT_TIER_2_COST,
                    config -> config.tierCosts[1]
            ).add()
            .append(
                    new KeyedCodec<>("TIER_3_COST", STRING),
                    (config, value) -> config.tierCosts[2] = value != null && !value.isBlank() ? value : DEFAULT_TIER_3_COST,
                    config -> config.tierCosts[2]
            ).add()
            .append(
                    new KeyedCodec<>("TIER_4_COST", STRING),
                    (config, value) -> config.tierCosts[3] = value != null && !value.isBlank() ? value : "",
                    config -> config.tierCosts[3]
            ).add()
            .append(
                    new KeyedCodec<>("TIER_5_COST", STRING),
                    (config, value) -> config.tierCosts[4] = value != null && !value.isBlank() ? value : "",
                    config -> config.tierCosts[4]
            ).add()
            .append(
                    new KeyedCodec<>("TIER_6_COST", STRING),
                    (config, value) -> config.tierCosts[5] = value != null && !value.isBlank() ? value : "",
                    config -> config.tierCosts[5]
            ).add()
            .append(
                    new KeyedCodec<>("TIER_7_COST", STRING),
                    (config, value) -> config.tierCosts[6] = value != null && !value.isBlank() ? value : "",
                    config -> config.tierCosts[6]
            ).add()
            .append(
                    new KeyedCodec<>("TIER_8_COST", STRING),
                    (config, value) -> config.tierCosts[7] = value != null && !value.isBlank() ? value : "",
                    config -> config.tierCosts[7]
            ).add()
            .append(
                    new KeyedCodec<>("TIER_9_COST", STRING),
                    (config, value) -> config.tierCosts[8] = value != null && !value.isBlank() ? value : "",
                    config -> config.tierCosts[8]
            ).add()
            .append(
                    new KeyedCodec<>("TIER_10_COST", STRING),
                    (config, value) -> config.tierCosts[9] = value != null && !value.isBlank() ? value : "",
                    config -> config.tierCosts[9]
            ).add()
            .build();

    private boolean enabled = DEFAULT_ENABLED;
    private int maxTier = DEFAULT_MAX_TIER;
    private final double[] carryOver = new double[MAX_ASCENSION_TIERS];
    private final String[] tierCosts = new String[MAX_ASCENSION_TIERS];

    public AscensionConfig() {
        carryOver[0] = DEFAULT_TIER_1_CARRY_OVER;
        carryOver[1] = DEFAULT_TIER_2_CARRY_OVER;
        carryOver[2] = DEFAULT_TIER_3_CARRY_OVER;
        for (int i = 3; i < MAX_ASCENSION_TIERS; i++) {
            carryOver[i] = DEFAULT_CARRY_OVER;
        }
        tierCosts[0] = DEFAULT_TIER_1_COST;
        tierCosts[1] = DEFAULT_TIER_2_COST;
        tierCosts[2] = DEFAULT_TIER_3_COST;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxTier() {
        return maxTier;
    }

    public void setMaxTier(int maxTier) {
        this.maxTier = Math.max(1, Math.min(10, maxTier));
    }

    public double getTierCarryOver(int tier) {
        if (tier < 1 || tier > MAX_ASCENSION_TIERS) {
            return DEFAULT_CARRY_OVER;
        }
        return carryOver[tier - 1];
    }

    public void setTierCarryOver(int tier, double value) {
        if (tier < 1 || tier > MAX_ASCENSION_TIERS) {
            return;
        }
        carryOver[tier - 1] = Math.max(0.0, Math.min(1.0, value));
    }

    public String getTierCost(int tier) {
        if (tier < 1 || tier > MAX_ASCENSION_TIERS) {
            return "";
        }
        return tierCosts[tier - 1] == null ? "" : tierCosts[tier - 1];
    }

    public void setTierCost(int tier, String itemId) {
        if (tier < 1 || tier > MAX_ASCENSION_TIERS) {
            return;
        }
        tierCosts[tier - 1] = itemId;
    }

    public Map<Integer, String> getAllTierCosts() {
        Map<Integer, String> costs = new HashMap<>();
        for (int i = 0; i < maxTier; i++) {
            costs.put(i + 1, tierCosts[i] == null ? "" : tierCosts[i]);
        }
        return costs;
    }

    @Override
    public boolean injectMissingDefaults() {
        return false;
    }

    public void resetToDefaults() {
        this.enabled = DEFAULT_ENABLED;
        this.maxTier = DEFAULT_MAX_TIER;
        carryOver[0] = DEFAULT_TIER_1_CARRY_OVER;
        carryOver[1] = DEFAULT_TIER_2_CARRY_OVER;
        carryOver[2] = DEFAULT_TIER_3_CARRY_OVER;
        for (int i = 3; i < MAX_ASCENSION_TIERS; i++) {
            carryOver[i] = DEFAULT_CARRY_OVER;
            tierCosts[i] = "";
        }
        tierCosts[0] = DEFAULT_TIER_1_COST;
        tierCosts[1] = DEFAULT_TIER_2_COST;
        tierCosts[2] = DEFAULT_TIER_3_COST;
    }
}
