package irai.mod.reforge.Config;

import static com.hypixel.hytale.codec.Codec.DOUBLE;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/** Runtime tuning for Character Path XP awarded by bench actions. */
@SuppressWarnings("removal")
public final class CharacterPathProgressionConfig {
    public static final double DEFAULT_SUCCESSFUL_BENCH_XP_SCALE = 0.01d;
    public static final double DEFAULT_FAILED_BENCH_XP_SCALE = 0.04d;

    public static final BuilderCodec<CharacterPathProgressionConfig> CODEC =
            BuilderCodec.<CharacterPathProgressionConfig>builder(CharacterPathProgressionConfig.class, CharacterPathProgressionConfig::new)
                    .append(
                            new KeyedCodec<>("SUCCESSFUL_BENCH_XP_SCALE", DOUBLE),
                            (config, value) -> config.setSuccessfulBenchXpScale(value == null ? DEFAULT_SUCCESSFUL_BENCH_XP_SCALE : value),
                            CharacterPathProgressionConfig::getSuccessfulBenchXpScale
                    ).add()
                    .append(
                            new KeyedCodec<>("FAILED_BENCH_XP_SCALE", DOUBLE),
                            (config, value) -> config.setFailedBenchXpScale(value == null ? DEFAULT_FAILED_BENCH_XP_SCALE : value),
                            CharacterPathProgressionConfig::getFailedBenchXpScale
                    ).add()
                    .build();

    private double successfulBenchXpScale = DEFAULT_SUCCESSFUL_BENCH_XP_SCALE;
    private double failedBenchXpScale = DEFAULT_FAILED_BENCH_XP_SCALE;

    public double getSuccessfulBenchXpScale() {
        return successfulBenchXpScale;
    }

    public double getFailedBenchXpScale() {
        return failedBenchXpScale;
    }

    public void setSuccessfulBenchXpScale(double value) {
        successfulBenchXpScale = clampScale(value);
    }

    public void setFailedBenchXpScale(double value) {
        failedBenchXpScale = clampScale(value);
    }

    public void resetToDefaults() {
        successfulBenchXpScale = DEFAULT_SUCCESSFUL_BENCH_XP_SCALE;
        failedBenchXpScale = DEFAULT_FAILED_BENCH_XP_SCALE;
    }

    private static double clampScale(double value) {
        if (!Double.isFinite(value)) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, value));
    }
}
