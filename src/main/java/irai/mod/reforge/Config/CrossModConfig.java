package irai.mod.reforge.Config;

import static com.hypixel.hytale.codec.Codec.BOOLEAN;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Optional cross-mod compatibility flags.
 * Keeps third-party integration toggles separate from core gameplay configs.
 */
public class CrossModConfig {
    private static final boolean DEFAULT_ENDLESS_LEVELING_CRIT_SYNC = true;
    private static final boolean DEFAULT_LOOT4EVERYONE_CHEST_COMPAT = true;

    public static final BuilderCodec<CrossModConfig> CODEC =
            BuilderCodec.<CrossModConfig>builder(CrossModConfig.class, CrossModConfig::new)
                    .append(
                            new KeyedCodec<>("ENDLESS_LEVELING_CRIT_SYNC", BOOLEAN),
                            (config, value) -> config.setEndlessLevelingCritSyncEnabled(value),
                            CrossModConfig::isEndlessLevelingCritSyncEnabled
                    ).add()
                    .append(
                            new KeyedCodec<>("LOOT4EVERYONE_CHEST_COMPAT", BOOLEAN),
                            (config, value) -> config.setLoot4EveryoneChestCompatEnabled(value),
                            CrossModConfig::isLoot4EveryoneChestCompatEnabled
                    ).add()
                    .build();

    private boolean endlessLevelingCritSyncEnabled = DEFAULT_ENDLESS_LEVELING_CRIT_SYNC;
    private boolean loot4EveryoneChestCompatEnabled = DEFAULT_LOOT4EVERYONE_CHEST_COMPAT;

    public boolean isEndlessLevelingCritSyncEnabled() {
        return endlessLevelingCritSyncEnabled;
    }

    public void setEndlessLevelingCritSyncEnabled(boolean value) {
        this.endlessLevelingCritSyncEnabled = value;
    }

    public boolean isLoot4EveryoneChestCompatEnabled() {
        return loot4EveryoneChestCompatEnabled;
    }

    public void setLoot4EveryoneChestCompatEnabled(boolean value) {
        this.loot4EveryoneChestCompatEnabled = value;
    }

    public void resetToDefaults() {
        this.endlessLevelingCritSyncEnabled = DEFAULT_ENDLESS_LEVELING_CRIT_SYNC;
        this.loot4EveryoneChestCompatEnabled = DEFAULT_LOOT4EVERYONE_CHEST_COMPAT;
    }
}
