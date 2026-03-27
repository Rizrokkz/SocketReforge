package irai.mod.reforge.Config;

import static com.hypixel.hytale.codec.Codec.BOOLEAN;
import static com.hypixel.hytale.codec.Codec.INTEGER;
import static com.hypixel.hytale.codec.Codec.STRING;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * One-time world repair options for legacy data migrations.
 */
@SuppressWarnings("removal")
public class WorldRepairConfig {
    public static final BuilderCodec<WorldRepairConfig> CODEC =
            BuilderCodec.<WorldRepairConfig>builder(WorldRepairConfig.class, WorldRepairConfig::new)
                    .append(
                            new KeyedCodec<>("AUTO_FIX_EMPTY_DROPLISTS_ON_START", BOOLEAN),
                            (cfg, v) -> cfg.autoFixEmptyDroplistsOnStart = v != null && v,
                            cfg -> cfg.autoFixEmptyDroplistsOnStart
                    ).add()
                    .append(
                            new KeyedCodec<>("AUTO_FIX_EMPTY_DROPLISTS_DONE", BOOLEAN),
                            (cfg, v) -> cfg.autoFixEmptyDroplistsDone = v != null && v,
                            cfg -> cfg.autoFixEmptyDroplistsDone
                    ).add()
                    .append(
                            new KeyedCodec<>("AUTO_REGEN_REGION_ON_START", BOOLEAN),
                            (cfg, v) -> cfg.autoRegenRegionOnStart = v != null && v,
                            cfg -> cfg.autoRegenRegionOnStart
                    ).add()
                    .append(
                            new KeyedCodec<>("AUTO_REGEN_REGION_DONE", BOOLEAN),
                            (cfg, v) -> cfg.autoRegenRegionDone = v != null && v,
                            cfg -> cfg.autoRegenRegionDone
                    ).add()
                    .append(
                            new KeyedCodec<>("AUTO_REGEN_REGION_FILE", STRING),
                            (cfg, v) -> cfg.autoRegenRegionFile = v,
                            cfg -> cfg.autoRegenRegionFile
                    ).add()
                    .append(
                            new KeyedCodec<>("AUTO_REMOVE_CHUNK_ON_START", BOOLEAN),
                            (cfg, v) -> cfg.autoRemoveChunkOnStart = v != null && v,
                            cfg -> cfg.autoRemoveChunkOnStart
                    ).add()
                    .append(
                            new KeyedCodec<>("AUTO_REMOVE_CHUNK_DONE", BOOLEAN),
                            (cfg, v) -> cfg.autoRemoveChunkDone = v != null && v,
                            cfg -> cfg.autoRemoveChunkDone
                    ).add()
                    .append(
                            new KeyedCodec<>("AUTO_REMOVE_CHUNK_X", INTEGER),
                            (cfg, v) -> cfg.autoRemoveChunkX = v == null ? cfg.autoRemoveChunkX : v,
                            cfg -> cfg.autoRemoveChunkX
                    ).add()
                    .append(
                            new KeyedCodec<>("AUTO_REMOVE_CHUNK_Z", INTEGER),
                            (cfg, v) -> cfg.autoRemoveChunkZ = v == null ? cfg.autoRemoveChunkZ : v,
                            cfg -> cfg.autoRemoveChunkZ
                    ).add()
                    .build();

    private boolean autoFixEmptyDroplistsOnStart = false;
    private boolean autoFixEmptyDroplistsDone = false;
    private boolean autoRegenRegionOnStart = false;
    private boolean autoRegenRegionDone = false;
    private String autoRegenRegionFile = "universe/worlds/default/chunks/0.0.region.bin";
    private boolean autoRemoveChunkOnStart = false;
    private boolean autoRemoveChunkDone = false;
    private int autoRemoveChunkX = 31;
    private int autoRemoveChunkZ = 8;

    public boolean isAutoFixEmptyDroplistsOnStart() {
        return autoFixEmptyDroplistsOnStart;
    }

    public boolean isAutoFixEmptyDroplistsDone() {
        return autoFixEmptyDroplistsDone;
    }

    public void setAutoFixEmptyDroplistsOnStart(boolean value) {
        this.autoFixEmptyDroplistsOnStart = value;
    }

    public void setAutoFixEmptyDroplistsDone(boolean value) {
        this.autoFixEmptyDroplistsDone = value;
    }

    public boolean isAutoRegenRegionOnStart() {
        return autoRegenRegionOnStart;
    }

    public boolean isAutoRegenRegionDone() {
        return autoRegenRegionDone;
    }

    public String getAutoRegenRegionFile() {
        return autoRegenRegionFile;
    }

    public void setAutoRegenRegionOnStart(boolean value) {
        this.autoRegenRegionOnStart = value;
    }

    public void setAutoRegenRegionDone(boolean value) {
        this.autoRegenRegionDone = value;
    }

    public void setAutoRegenRegionFile(String value) {
        this.autoRegenRegionFile = value;
    }

    public boolean isAutoRemoveChunkOnStart() {
        return autoRemoveChunkOnStart;
    }

    public boolean isAutoRemoveChunkDone() {
        return autoRemoveChunkDone;
    }

    public int getAutoRemoveChunkX() {
        return autoRemoveChunkX;
    }

    public int getAutoRemoveChunkZ() {
        return autoRemoveChunkZ;
    }

    public void setAutoRemoveChunkOnStart(boolean value) {
        this.autoRemoveChunkOnStart = value;
    }

    public void setAutoRemoveChunkDone(boolean value) {
        this.autoRemoveChunkDone = value;
    }

    public void setAutoRemoveChunkX(int value) {
        this.autoRemoveChunkX = value;
    }

    public void setAutoRemoveChunkZ(int value) {
        this.autoRemoveChunkZ = value;
    }
}
