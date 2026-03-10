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
    private double chestResonanceChance = 0.001d;

    // NPC drop rolls
    private double dropThreeSocketChance = 0.30d;
    private double dropFourSocketChance = 0.10d;
    private double dropFiveSocketChance = 0.01d;
    private double dropThreeToFourChance = 0.50d;
    private double dropResonanceChance = 0.001d;

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

    public void setMinBrokenSockets(int value) {
        this.minBrokenSockets = value;
    }

    public void setMaxBrokenSockets(int value) {
        this.maxBrokenSockets = value;
    }
}
