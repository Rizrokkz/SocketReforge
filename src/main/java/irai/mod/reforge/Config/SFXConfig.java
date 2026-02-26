package irai.mod.reforge.Config;

import static com.hypixel.hytale.codec.Codec.STRING;
import static com.hypixel.hytale.codec.Codec.STRING_ARRAY;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;

/**
 * Configuration for reforge sound effects.
 * Handles sound playback for all reforge events.
 */
@SuppressWarnings("removal")
public class SFXConfig {

    public static final BuilderCodec<SFXConfig> CODEC = BuilderCodec.<SFXConfig>builder(SFXConfig.class, SFXConfig::new)
            .append(
                    new KeyedCodec<>("BENCHES", STRING_ARRAY),
                    (sfxConfig, benches) -> sfxConfig.Benches = benches,
                    SFXConfig::getBenches
            ).add()
            .append(
                    new KeyedCodec<>("SFX_START", STRING),
                    (sfxConfig, sfxstart) -> sfxConfig.SFX_START = sfxstart,
                    SFXConfig::getSFX_START
            ).add()
            .append(
                    new KeyedCodec<>("SFX_SUCCESS", STRING),
                    (sfxConfig, sfxSuccess) -> sfxConfig.SFX_SUCCESS = sfxSuccess,
                    SFXConfig::getSFX_SUCCESS
            ).add()
            .append(
                    new KeyedCodec<>("SFX_JACKPOT", STRING),
                    (sfxConfig, sfxJackpot) -> sfxConfig.SFX_JACKPOT = sfxJackpot,
                    SFXConfig::getSFX_JACKPOT
            ).add()
            .append(
                    new KeyedCodec<>("SFX_FAIL", STRING),
                    (sfxConfig, sfxFail) -> sfxConfig.SFX_FAIL = sfxFail,
                    SFXConfig::getSFX_FAIL
            ).add()
            .append(
                    new KeyedCodec<>("SFX_NO_CHANGE", STRING),
                    (sfxConfig, sfxNoChange) -> sfxConfig.SFX_NO_CHANGE = sfxNoChange,
                    SFXConfig::getSFX_NO_CHANGE
            ).add()
            .append(
                    new KeyedCodec<>("SFX_SHATTER", STRING),
                    (sfxConfig, sfxShatter) -> sfxConfig.SFX_SHATTER = sfxShatter,
                    SFXConfig::getSFX_SHATTER
            ).add()
            .build();

    // ══════════════════════════════════════════════════════════════════════════════
    // Configuration Fields
    // ══════════════════════════════════════════════════════════════════════════════

    public String[] Benches = {"Reforgebench"};

    // Sound event IDs - matching SFXConfig.json
    public String SFX_START = "SFX_Mace_T1_Block_Impact";
    public String SFX_SUCCESS = "SFX_Weapon_Bench_Craft";
    public String SFX_JACKPOT = "SFX_Workbench_Upgrade_Complete_Default";
    public String SFX_FAIL = "SFX_Armour_Bench_Craft";
    public String SFX_NO_CHANGE = "SFX_Workbench_Craft";
    public String SFX_SHATTER = "SFX_Door_Temple_Light_Open";

    // ══════════════════════════════════════════════════════════════════════════════
    // Sound Playback Methods
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Plays a sound to a player using a sound event ID string.
     */
    public void playSound(Player player, String soundEventIdStr) {
        if (player == null || soundEventIdStr == null || soundEventIdStr.isEmpty()) {
            return;
        }

        try {
            int soundIndex = SoundEvent.getAssetMap().getIndex(soundEventIdStr);
            
            if (soundIndex < 0) {
                return;
            }
            
            SoundUtil.playSoundEvent2dToPlayer(
                    player.getPlayerRef(),
                    soundIndex,
                    SoundCategory.SFX
            );
            
        } catch (Exception e) {
            System.err.println("[SFXConfig] Failed to play sound '" + soundEventIdStr + "': " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void playReforgeStart(Player player) {
        playSound(player, SFX_START);
    }

    public void playSuccess(Player player) {
        playSound(player, SFX_SUCCESS);
    }

    public void playJackpot(Player player) {
        playSound(player, SFX_JACKPOT);
    }

    public void playFail(Player player) {
        playSound(player, SFX_FAIL);
    }

    public void playNoChange(Player player) {
        playSound(player, SFX_NO_CHANGE);
    }

    public void playShatter(Player player) {
        playSound(player, SFX_SHATTER);
    }

    // Getters and Setters
    public String getSFX_START() { return SFX_START; }
    public void setSFX_START(String SFX_START) { this.SFX_START = SFX_START; }
    public String getSFX_SUCCESS() { return SFX_SUCCESS; }
    public void setSFX_SUCCESS(String SFX_SUCCESS) { this.SFX_SUCCESS = SFX_SUCCESS; }
    public String getSFX_JACKPOT() { return SFX_JACKPOT; }
    public void setSFX_JACKPOT(String SFX_JACKPOT) { this.SFX_JACKPOT = SFX_JACKPOT; }
    public String getSFX_FAIL() { return SFX_FAIL; }
    public void setSFX_FAIL(String SFX_FAIL) { this.SFX_FAIL = SFX_FAIL; }
    public String getSFX_NO_CHANGE() { return SFX_NO_CHANGE; }
    public void setSFX_NO_CHANGE(String SFX_NO_CHANGE) { this.SFX_NO_CHANGE = SFX_NO_CHANGE; }
    public String getSFX_SHATTER() { return SFX_SHATTER; }
    public void setSFX_SHATTER(String SFX_SHATTER) { this.SFX_SHATTER = SFX_SHATTER; }
    public String[] getBenches() { return Benches; }
    public void setBenches(String[] benches) { Benches = benches; }
}
