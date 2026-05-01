package irai.mod.reforge.Config;

import static com.hypixel.hytale.codec.Codec.BOOLEAN;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

public class CrossModConfig {
    private static final boolean DEFAULT_LOOT4EVERYONE_CHEST_COMPAT = true;

    public static final BuilderCodec<CrossModConfig> CODEC =
            BuilderCodec.<CrossModConfig>builder(CrossModConfig.class, CrossModConfig::new)
                    .append(
                            new KeyedCodec<>("LOOT4EVERYONE_CHEST_COMPAT", BOOLEAN),
                            (config, value) -> config.setLoot4EveryoneChestCompatEnabled(value),
                            CrossModConfig::isLoot4EveryoneChestCompatEnabled
                    ).add()
                    .build();

    private boolean loot4EveryoneChestCompatEnabled = DEFAULT_LOOT4EVERYONE_CHEST_COMPAT;

    public boolean isLoot4EveryoneChestCompatEnabled() {
        return loot4EveryoneChestCompatEnabled;
    }

    public void setLoot4EveryoneChestCompatEnabled(boolean value) {
        this.loot4EveryoneChestCompatEnabled = value;
    }

    public void resetToDefaults() {
        this.loot4EveryoneChestCompatEnabled = DEFAULT_LOOT4EVERYONE_CHEST_COMPAT;
    }
}