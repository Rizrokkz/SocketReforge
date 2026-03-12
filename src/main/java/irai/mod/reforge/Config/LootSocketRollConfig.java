package irai.mod.reforge.Config;

import static com.hypixel.hytale.codec.Codec.DOUBLE_ARRAY;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Configuration for world-loot socket rolls used by treasure chests and NPC drops.
 */
@SuppressWarnings("removal")
public class LootSocketRollConfig {

    public static final BuilderCodec<LootSocketRollConfig> CODEC = BuilderCodec.<LootSocketRollConfig>builder(LootSocketRollConfig.class, LootSocketRollConfig::new)
            .append(
                    new KeyedCodec<>("CHEST_THREE_SOCKET_CHANCE", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.chestThreeSocketChance = v[0]; },
                    cfg -> new double[]{cfg.chestThreeSocketChance}
            ).add()
            .append(
                    new KeyedCodec<>("CHEST_RESONANCE_CHANCE", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.chestResonanceChance = v[0]; },
                    cfg -> new double[]{cfg.chestResonanceChance}
            ).add()
            .append(
                    new KeyedCodec<>("CHEST_FOUR_SOCKET_CHANCE", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.chestFourSocketChance = v[0]; },
                    cfg -> new double[]{cfg.chestFourSocketChance}
            ).add()
            .append(
                    new KeyedCodec<>("CHEST_FIVE_SOCKET_CHANCE", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.chestFiveSocketChance = v[0]; },
                    cfg -> new double[]{cfg.chestFiveSocketChance}
            ).add()
            .append(
                    new KeyedCodec<>("CHEST_THREE_TO_FOUR_CHANCE", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.chestThreeToFourChance = v[0]; },
                    cfg -> new double[]{cfg.chestThreeToFourChance}
            ).add()
            .append(
                    new KeyedCodec<>("DROP_THREE_SOCKET_CHANCE", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.dropThreeSocketChance = v[0]; },
                    cfg -> new double[]{cfg.dropThreeSocketChance}
            ).add()
            .append(
                    new KeyedCodec<>("DROP_FOUR_SOCKET_CHANCE", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.dropFourSocketChance = v[0]; },
                    cfg -> new double[]{cfg.dropFourSocketChance}
            ).add()
            .append(
                    new KeyedCodec<>("DROP_FIVE_SOCKET_CHANCE", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.dropFiveSocketChance = v[0]; },
                    cfg -> new double[]{cfg.dropFiveSocketChance}
            ).add()
            .append(
                    new KeyedCodec<>("DROP_THREE_TO_FOUR_CHANCE", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.dropThreeToFourChance = v[0]; },
                    cfg -> new double[]{cfg.dropThreeToFourChance}
            ).add()
            .append(
                    new KeyedCodec<>("DROP_RESONANCE_CHANCE", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.dropResonanceChance = v[0]; },
                    cfg -> new double[]{cfg.dropResonanceChance}
            ).add()
            .append(
                    new KeyedCodec<>("CHEST_SOCKETED_ESSENCE_CHANCE", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.chestSocketedEssenceChance = v[0]; },
                    cfg -> new double[]{cfg.chestSocketedEssenceChance}
            ).add()
            .append(
                    new KeyedCodec<>("DROP_SOCKETED_ESSENCE_CHANCE", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.dropSocketedEssenceChance = v[0]; },
                    cfg -> new double[]{cfg.dropSocketedEssenceChance}
            ).add()
            .append(
                    new KeyedCodec<>("GREATER_ESSENCE_CHANCE", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.greaterEssenceChance = v[0]; },
                    cfg -> new double[]{cfg.greaterEssenceChance}
            ).add()
            .append(
                    new KeyedCodec<>("CROP_WATER_ESSENCE_CHANCE", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.cropWaterEssenceChance = v[0]; },
                    cfg -> new double[]{cfg.cropWaterEssenceChance}
            ).add()
            .append(
                    new KeyedCodec<>("CROP_LIGHTNING_ESSENCE_CHANCE", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.cropLightningEssenceChance = v[0]; },
                    cfg -> new double[]{cfg.cropLightningEssenceChance}
            ).add()
            .append(
                    new KeyedCodec<>("NPC_WATER_ESSENCE_CHANCE", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.npcWaterEssenceChance = v[0]; },
                    cfg -> new double[]{cfg.npcWaterEssenceChance}
            ).add()
            .append(
                    new KeyedCodec<>("NPC_LIGHTNING_ESSENCE_CHANCE", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.npcLightningEssenceChance = v[0]; },
                    cfg -> new double[]{cfg.npcLightningEssenceChance}
            ).add()
            .append(
                    new KeyedCodec<>("CROP_WATER_ESSENCE_MIN", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.cropWaterEssenceMinQuantity = (int) Math.round(v[0]); },
                    cfg -> new double[]{cfg.cropWaterEssenceMinQuantity}
            ).add()
            .append(
                    new KeyedCodec<>("CROP_WATER_ESSENCE_MAX", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.cropWaterEssenceMaxQuantity = (int) Math.round(v[0]); },
                    cfg -> new double[]{cfg.cropWaterEssenceMaxQuantity}
            ).add()
            .append(
                    new KeyedCodec<>("CROP_LIGHTNING_ESSENCE_MIN", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.cropLightningEssenceMinQuantity = (int) Math.round(v[0]); },
                    cfg -> new double[]{cfg.cropLightningEssenceMinQuantity}
            ).add()
            .append(
                    new KeyedCodec<>("CROP_LIGHTNING_ESSENCE_MAX", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.cropLightningEssenceMaxQuantity = (int) Math.round(v[0]); },
                    cfg -> new double[]{cfg.cropLightningEssenceMaxQuantity}
            ).add()
            .append(
                    new KeyedCodec<>("NPC_WATER_ESSENCE_MIN", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.npcWaterEssenceMinQuantity = (int) Math.round(v[0]); },
                    cfg -> new double[]{cfg.npcWaterEssenceMinQuantity}
            ).add()
            .append(
                    new KeyedCodec<>("NPC_WATER_ESSENCE_MAX", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.npcWaterEssenceMaxQuantity = (int) Math.round(v[0]); },
                    cfg -> new double[]{cfg.npcWaterEssenceMaxQuantity}
            ).add()
            .append(
                    new KeyedCodec<>("NPC_LIGHTNING_ESSENCE_MIN", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.npcLightningEssenceMinQuantity = (int) Math.round(v[0]); },
                    cfg -> new double[]{cfg.npcLightningEssenceMinQuantity}
            ).add()
            .append(
                    new KeyedCodec<>("NPC_LIGHTNING_ESSENCE_MAX", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.npcLightningEssenceMaxQuantity = (int) Math.round(v[0]); },
                    cfg -> new double[]{cfg.npcLightningEssenceMaxQuantity}
            ).add()
            .append(
                    new KeyedCodec<>("MIN_BROKEN_SOCKETS", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.minBrokenSockets = (int) Math.round(v[0]); },
                    cfg -> new double[]{cfg.minBrokenSockets}
            ).add()
            .append(
                    new KeyedCodec<>("MAX_BROKEN_SOCKETS", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.maxBrokenSockets = (int) Math.round(v[0]); },
                    cfg -> new double[]{cfg.maxBrokenSockets}
            ).add()
            .build();

    // Chest rolls
    private double chestThreeSocketChance = 0.30d;
    private double chestFourSocketChance = 0.10d;
    private double chestFiveSocketChance = 0.01d;
    private double chestThreeToFourChance = 0.50d;
    private double chestResonanceChance = 0.25d;
    private double chestSocketedEssenceChance = 0.05d;

    // NPC drop rolls
    private double dropThreeSocketChance = 0.30d;
    private double dropFourSocketChance = 0.10d;
    private double dropFiveSocketChance = 0.01d;
    private double dropThreeToFourChance = 0.50d;
    private double dropResonanceChance = 0.25d;
    private double dropSocketedEssenceChance = 0.05d;

    // Essence fill tuning
    private double greaterEssenceChance = 0.15d;

    // Crop + NPC essence injections
    private double cropWaterEssenceChance = 0.05d;
    private double cropLightningEssenceChance = 0.15d;
    private double npcWaterEssenceChance = 0.05d;
    private double npcLightningEssenceChance = 0.15d;
    private int cropWaterEssenceMinQuantity = 1;
    private int cropWaterEssenceMaxQuantity = 2;
    private int cropLightningEssenceMinQuantity = 2;
    private int cropLightningEssenceMaxQuantity = 3;
    private int npcWaterEssenceMinQuantity = 1;
    private int npcWaterEssenceMaxQuantity = 2;
    private int npcLightningEssenceMinQuantity = 2;
    private int npcLightningEssenceMaxQuantity = 3;

    // Clamp range used after rolling
    private int minBrokenSockets = 3;
    private int maxBrokenSockets = 5;

    public double getChestThreeSocketChance() {
        return chestThreeSocketChance;
    }

    public double getChestFourSocketChance() {
        return chestFourSocketChance;
    }

    public double getChestFiveSocketChance() {
        return chestFiveSocketChance;
    }

    public double getChestThreeToFourChance() {
        return chestThreeToFourChance;
    }

    public double getChestResonanceChance() {
        return chestResonanceChance;
    }

    public double getDropThreeSocketChance() {
        return dropThreeSocketChance;
    }

    public double getDropFourSocketChance() {
        return dropFourSocketChance;
    }

    public double getDropFiveSocketChance() {
        return dropFiveSocketChance;
    }

    public double getDropThreeToFourChance() {
        return dropThreeToFourChance;
    }

    public double getDropResonanceChance() {
        return dropResonanceChance;
    }

    public double getChestSocketedEssenceChance() {
        return chestSocketedEssenceChance;
    }

    public double getDropSocketedEssenceChance() {
        return dropSocketedEssenceChance;
    }

    public double getGreaterEssenceChance() {
        return greaterEssenceChance;
    }

    public double getCropWaterEssenceChance() {
        return cropWaterEssenceChance;
    }

    public double getCropLightningEssenceChance() {
        return cropLightningEssenceChance;
    }

    public double getNpcWaterEssenceChance() {
        return npcWaterEssenceChance;
    }

    public double getNpcLightningEssenceChance() {
        return npcLightningEssenceChance;
    }

    public int getCropWaterEssenceMinQuantity() {
        return cropWaterEssenceMinQuantity;
    }

    public int getCropWaterEssenceMaxQuantity() {
        return cropWaterEssenceMaxQuantity;
    }

    public int getCropLightningEssenceMinQuantity() {
        return cropLightningEssenceMinQuantity;
    }

    public int getCropLightningEssenceMaxQuantity() {
        return cropLightningEssenceMaxQuantity;
    }

    public int getNpcWaterEssenceMinQuantity() {
        return npcWaterEssenceMinQuantity;
    }

    public int getNpcWaterEssenceMaxQuantity() {
        return npcWaterEssenceMaxQuantity;
    }

    public int getNpcLightningEssenceMinQuantity() {
        return npcLightningEssenceMinQuantity;
    }

    public int getNpcLightningEssenceMaxQuantity() {
        return npcLightningEssenceMaxQuantity;
    }

    public int getMinBrokenSockets() {
        return minBrokenSockets;
    }

    public int getMaxBrokenSockets() {
        return maxBrokenSockets;
    }

    public void setChestThreeSocketChance(double value) {
        this.chestThreeSocketChance = value;
    }

    public void setChestFourSocketChance(double value) {
        this.chestFourSocketChance = value;
    }

    public void setChestFiveSocketChance(double value) {
        this.chestFiveSocketChance = value;
    }

    public void setChestThreeToFourChance(double value) {
        this.chestThreeToFourChance = value;
    }

    public void setChestResonanceChance(double value) {
        this.chestResonanceChance = value;
    }

    public void setDropThreeSocketChance(double value) {
        this.dropThreeSocketChance = value;
    }

    public void setDropFourSocketChance(double value) {
        this.dropFourSocketChance = value;
    }

    public void setDropFiveSocketChance(double value) {
        this.dropFiveSocketChance = value;
    }

    public void setDropThreeToFourChance(double value) {
        this.dropThreeToFourChance = value;
    }

    public void setDropResonanceChance(double value) {
        this.dropResonanceChance = value;
    }

    public void setChestSocketedEssenceChance(double value) {
        this.chestSocketedEssenceChance = value;
    }

    public void setDropSocketedEssenceChance(double value) {
        this.dropSocketedEssenceChance = value;
    }

    public void setGreaterEssenceChance(double value) {
        this.greaterEssenceChance = value;
    }

    public void setCropWaterEssenceChance(double value) {
        this.cropWaterEssenceChance = value;
    }

    public void setCropLightningEssenceChance(double value) {
        this.cropLightningEssenceChance = value;
    }

    public void setNpcWaterEssenceChance(double value) {
        this.npcWaterEssenceChance = value;
    }

    public void setNpcLightningEssenceChance(double value) {
        this.npcLightningEssenceChance = value;
    }

    public void setCropWaterEssenceMinQuantity(int value) {
        this.cropWaterEssenceMinQuantity = value;
    }

    public void setCropWaterEssenceMaxQuantity(int value) {
        this.cropWaterEssenceMaxQuantity = value;
    }

    public void setCropLightningEssenceMinQuantity(int value) {
        this.cropLightningEssenceMinQuantity = value;
    }

    public void setCropLightningEssenceMaxQuantity(int value) {
        this.cropLightningEssenceMaxQuantity = value;
    }

    public void setNpcWaterEssenceMinQuantity(int value) {
        this.npcWaterEssenceMinQuantity = value;
    }

    public void setNpcWaterEssenceMaxQuantity(int value) {
        this.npcWaterEssenceMaxQuantity = value;
    }

    public void setNpcLightningEssenceMinQuantity(int value) {
        this.npcLightningEssenceMinQuantity = value;
    }

    public void setNpcLightningEssenceMaxQuantity(int value) {
        this.npcLightningEssenceMaxQuantity = value;
    }

    public void setMinBrokenSockets(int value) {
        this.minBrokenSockets = value;
    }

    public void setMaxBrokenSockets(int value) {
        this.maxBrokenSockets = value;
    }

    public void resetToDefaults() {
        LootSocketRollConfig defaults = new LootSocketRollConfig();
        this.chestThreeSocketChance = defaults.chestThreeSocketChance;
        this.chestFourSocketChance = defaults.chestFourSocketChance;
        this.chestFiveSocketChance = defaults.chestFiveSocketChance;
        this.chestThreeToFourChance = defaults.chestThreeToFourChance;
        this.chestResonanceChance = defaults.chestResonanceChance;
        this.chestSocketedEssenceChance = defaults.chestSocketedEssenceChance;
        this.dropThreeSocketChance = defaults.dropThreeSocketChance;
        this.dropFourSocketChance = defaults.dropFourSocketChance;
        this.dropFiveSocketChance = defaults.dropFiveSocketChance;
        this.dropThreeToFourChance = defaults.dropThreeToFourChance;
        this.dropResonanceChance = defaults.dropResonanceChance;
        this.dropSocketedEssenceChance = defaults.dropSocketedEssenceChance;
        this.greaterEssenceChance = defaults.greaterEssenceChance;
        this.cropWaterEssenceChance = defaults.cropWaterEssenceChance;
        this.cropLightningEssenceChance = defaults.cropLightningEssenceChance;
        this.npcWaterEssenceChance = defaults.npcWaterEssenceChance;
        this.npcLightningEssenceChance = defaults.npcLightningEssenceChance;
        this.cropWaterEssenceMinQuantity = defaults.cropWaterEssenceMinQuantity;
        this.cropWaterEssenceMaxQuantity = defaults.cropWaterEssenceMaxQuantity;
        this.cropLightningEssenceMinQuantity = defaults.cropLightningEssenceMinQuantity;
        this.cropLightningEssenceMaxQuantity = defaults.cropLightningEssenceMaxQuantity;
        this.npcWaterEssenceMinQuantity = defaults.npcWaterEssenceMinQuantity;
        this.npcWaterEssenceMaxQuantity = defaults.npcWaterEssenceMaxQuantity;
        this.npcLightningEssenceMinQuantity = defaults.npcLightningEssenceMinQuantity;
        this.npcLightningEssenceMaxQuantity = defaults.npcLightningEssenceMaxQuantity;
        this.minBrokenSockets = defaults.minBrokenSockets;
        this.maxBrokenSockets = defaults.maxBrokenSockets;
    }
}
