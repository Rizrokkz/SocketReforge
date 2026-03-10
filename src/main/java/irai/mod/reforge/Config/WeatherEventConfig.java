package irai.mod.reforge.Config;

import static com.hypixel.hytale.codec.Codec.DOUBLE_ARRAY;
import static com.hypixel.hytale.codec.Codec.STRING_ARRAY;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Configuration for weather-driven NPC spawns.
 */
@SuppressWarnings("removal")
public class WeatherEventConfig {

    public static final BuilderCodec<WeatherEventConfig> CODEC = BuilderCodec.<WeatherEventConfig>builder(WeatherEventConfig.class, WeatherEventConfig::new)
            .append(
                    new KeyedCodec<>("SPIRIT_ROLE", STRING_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.spiritRoleId = v[0]; },
                    cfg -> new String[]{cfg.spiritRoleId}
            ).add()
            .append(
                    new KeyedCodec<>("MIN_SPAWN_INTERVAL", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.minSpawnIntervalSeconds = v[0]; },
                    cfg -> new double[]{cfg.minSpawnIntervalSeconds}
            ).add()
            .append(
                    new KeyedCodec<>("MAX_SPAWN_INTERVAL", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.maxSpawnIntervalSeconds = v[0]; },
                    cfg -> new double[]{cfg.maxSpawnIntervalSeconds}
            ).add()
            .append(
                    new KeyedCodec<>("MAX_SPIRITS_PER_PLAYER", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.maxSpiritsPerPlayer = (int) Math.round(v[0]); },
                    cfg -> new double[]{cfg.maxSpiritsPerPlayer}
            ).add()
            .append(
                    new KeyedCodec<>("MIN_SPAWN_DISTANCE", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.minSpawnDistance = v[0]; },
                    cfg -> new double[]{cfg.minSpawnDistance}
            ).add()
            .append(
                    new KeyedCodec<>("MAX_SPAWN_DISTANCE", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.maxSpawnDistance = v[0]; },
                    cfg -> new double[]{cfg.maxSpawnDistance}
            ).add()
            .append(
                    new KeyedCodec<>("MIN_SPAWNS_PER_INTERVAL", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.minSpawnsPerInterval = (int) Math.round(v[0]); },
                    cfg -> new double[]{cfg.minSpawnsPerInterval}
            ).add()
            .append(
                    new KeyedCodec<>("MAX_SPAWNS_PER_INTERVAL", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.maxSpawnsPerInterval = (int) Math.round(v[0]); },
                    cfg -> new double[]{cfg.maxSpawnsPerInterval}
            ).add()
            .append(
                    new KeyedCodec<>("DESPAWN_AFTER_RAIN_END", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.despawnAfterRainEndSeconds = v[0]; },
                    cfg -> new double[]{cfg.despawnAfterRainEndSeconds}
            ).add()
            .append(
                    new KeyedCodec<>("RAIN_KEYWORDS", STRING_ARRAY),
                    (cfg, v) -> cfg.rainKeywords = v,
                    cfg -> cfg.rainKeywords
            ).add()
            .build();

    private String spiritRoleId = "Spirit_Thunder";
    private double minSpawnIntervalSeconds = 12.0d;
    private double maxSpawnIntervalSeconds = 20.0d;
    private int maxSpiritsPerPlayer = 6;
    private double minSpawnDistance = 8.0d;
    private double maxSpawnDistance = 30.0d;
    private int minSpawnsPerInterval = 1;
    private int maxSpawnsPerInterval = 3;
    private double despawnAfterRainEndSeconds = 30.0d;
    private String[] rainKeywords = {"rain", "storm", "thunder"};

    public String getSpiritRoleId() { return spiritRoleId; }
    public double getMinSpawnIntervalSeconds() { return minSpawnIntervalSeconds; }
    public double getMaxSpawnIntervalSeconds() { return maxSpawnIntervalSeconds; }
    public int getMaxSpiritsPerPlayer() { return maxSpiritsPerPlayer; }
    public double getMinSpawnDistance() { return minSpawnDistance; }
    public double getMaxSpawnDistance() { return maxSpawnDistance; }
    public int getMinSpawnsPerInterval() { return minSpawnsPerInterval; }
    public int getMaxSpawnsPerInterval() { return maxSpawnsPerInterval; }
    public double getDespawnAfterRainEndSeconds() { return despawnAfterRainEndSeconds; }
    public String[] getRainKeywords() { return rainKeywords; }

    public void setSpiritRoleId(String value) { spiritRoleId = value; }
    public void setMinSpawnIntervalSeconds(double value) { minSpawnIntervalSeconds = value; }
    public void setMaxSpawnIntervalSeconds(double value) { maxSpawnIntervalSeconds = value; }
    public void setMaxSpiritsPerPlayer(int value) { maxSpiritsPerPlayer = value; }
    public void setMinSpawnDistance(double value) { minSpawnDistance = value; }
    public void setMaxSpawnDistance(double value) { maxSpawnDistance = value; }
    public void setMinSpawnsPerInterval(int value) { minSpawnsPerInterval = value; }
    public void setMaxSpawnsPerInterval(int value) { maxSpawnsPerInterval = value; }
    public void setDespawnAfterRainEndSeconds(double value) { despawnAfterRainEndSeconds = value; }
    public void setRainKeywords(String[] value) { rainKeywords = value; }
}
