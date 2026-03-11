package irai.mod.reforge.Config;

import static com.hypixel.hytale.codec.Codec.DOUBLE_ARRAY;
import static com.hypixel.hytale.codec.Codec.STRING_ARRAY;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Socket punching configuration.
 * Loaded from resources/Server/Config/SocketConfig.json via ReforgePlugin.
 * Also accepted by SocketPunchUI.setConfig().
 */
@SuppressWarnings("removal")
public class SocketConfig {

    public static final BuilderCodec<SocketConfig> CODEC = BuilderCodec.<SocketConfig>builder(SocketConfig.class, SocketConfig::new)
            // Max sockets configuration stored as int array [weapon, armor]
            .append(
                    new KeyedCodec<>("MAX_SOCKETS", STRING_ARRAY),
                    (cfg, v) -> {
                        // Parse string array to ints
                        if (v != null && v.length >= 2) {
                            try {
                                cfg.maxSocketsWeapon = Integer.parseInt(v[0]);
                                cfg.maxSocketsArmor = Integer.parseInt(v[1]);
                            } catch (NumberFormatException ignored) {}
                        }
                    },
                    cfg -> new String[]{ String.valueOf(cfg.maxSocketsWeapon), String.valueOf(cfg.maxSocketsArmor) }
            ).add()
            // Success chances per current socket count
            .append(
                    new KeyedCodec<>("PUNCH_SUCCESS_CHANCES", DOUBLE_ARRAY),
                    (cfg, v) -> cfg.punchSuccessChances = v,
                    SocketConfig::getPunchSuccessChances
            ).add()
            // Break chances per current socket count
            .append(
                    new KeyedCodec<>("PUNCH_BREAK_CHANCES", DOUBLE_ARRAY),
                    (cfg, v) -> cfg.punchBreakChances = v,
                    SocketConfig::getPunchBreakChances
            ).add()
            // Essence removal success chance
            .append(
                    new KeyedCodec<>("ESSENCE_REMOVAL_SUCCESS", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.essenceRemovalSuccessChance = v[0]; },
                    cfg -> new double[]{ cfg.essenceRemovalSuccessChance }
            ).add()
            // Essence removal destroy chance
            .append(
                    new KeyedCodec<>("ESSENCE_REMOVAL_DESTROY", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.essenceRemovalDestroyChance = v[0]; },
                    cfg -> new double[]{ cfg.essenceRemovalDestroyChance }
            ).add()
            // Bonus chance to add an extra socket when punching near cap
            .append(
                    new KeyedCodec<>("BONUS_SOCKET_CHANCE", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.bonusSocketChance = v[0]; },
                    cfg -> new double[]{ cfg.bonusSocketChance }
            ).add()
            // Chance to reduce max sockets after a break event
            .append(
                    new KeyedCodec<>("MAX_REDUCE_CHANCE", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.maxReduceChance = v[0]; },
                    cfg -> new double[]{ cfg.maxReduceChance }
            ).add()
            .build();

    // ══════════════════════════════════════════════════════════════════════════════
    // Configuration Fields
    // ══════════════════════════════════════════════════════════════════════════════

    // Max sockets by item type
    private int maxSocketsWeapon = 4;
    private int maxSocketsArmor  = 4;

    /**
     * Success chances per current socket count [0-indexed].
     * Index 0 = punching 1st socket, index 3 = punching 4th socket.
     */
    private double[] punchSuccessChances = { 0.90, 0.75, 0.55, 0.35 };

    /**
     * Item destruction chances per current socket count [0-indexed].
     * Progressively increases with each socket to add risk.
     */
    private double[] punchBreakChances = { 0.05, 0.10, 0.20, 0.35 };

    // Essence removal
    private double essenceRemovalSuccessChance = 0.70;
    private double essenceRemovalDestroyChance = 0.30;
    
    // Bonus socket chance (1% chance to add 5th socket when punching 4th)
    private double bonusSocketChance = 0.01;
    
    // Chance to reduce max sockets when breaking (separate from break chance)
    private double maxReduceChance = 0.25;

    // ── Accessors ─────────────────────────────────────────────────────────────

    public int getMaxSocketsWeapon() { return maxSocketsWeapon; }
    public int getMaxSocketsArmor()  { return maxSocketsArmor;  }

    /** Returns the raw punch success chances array. */
    public double[] getPunchSuccessChances() { return punchSuccessChances; }

    /** Returns the raw punch break chances array. */
    public double[] getPunchBreakChances() { return punchBreakChances; }

    /** Success rate for punching the next socket (0.0–1.0). */
    public double getSuccessChance(int currentSocketCount) {
        int idx = Math.min(currentSocketCount, punchSuccessChances.length - 1);
        return punchSuccessChances[idx];
    }

    /** Break (item destruction) rate for punching the next socket (0.0–1.0). */
    public double getBreakChance(int currentSocketCount) {
        int idx = Math.min(currentSocketCount, punchBreakChances.length - 1);
        return punchBreakChances[idx];
    }

    public double getEssenceRemovalSuccessChance() { return essenceRemovalSuccessChance; }
    public double getEssenceRemovalDestroyChance()  { return essenceRemovalDestroyChance;  }
    public double getBonusSocketChance() { return bonusSocketChance; }
    public double getMaxReduceChance() { return maxReduceChance; }

    // ── Setters (used by config loader) ───────────────────────────────────────

    public void setMaxSocketsWeapon(int v)               { maxSocketsWeapon = v; }
    public void setMaxSocketsArmor(int v)                { maxSocketsArmor  = v; }
    public void setPunchSuccessChances(double[] v)        { punchSuccessChances = v; }
    public void setPunchBreakChances(double[] v)          { punchBreakChances = v; }
    public void setEssenceRemovalSuccessChance(double v)  { essenceRemovalSuccessChance = v; }
    public void setEssenceRemovalDestroyChance(double v)  { essenceRemovalDestroyChance  = v; }
    public void setBonusSocketChance(double v)            { bonusSocketChance = v; }
    public void setMaxReduceChance(double v)              { maxReduceChance = v; }

    public void resetToDefaults() {
        SocketConfig defaults = new SocketConfig();
        this.maxSocketsWeapon = defaults.maxSocketsWeapon;
        this.maxSocketsArmor = defaults.maxSocketsArmor;
        this.punchSuccessChances = defaults.punchSuccessChances == null ? null : defaults.punchSuccessChances.clone();
        this.punchBreakChances = defaults.punchBreakChances == null ? null : defaults.punchBreakChances.clone();
        this.essenceRemovalSuccessChance = defaults.essenceRemovalSuccessChance;
        this.essenceRemovalDestroyChance = defaults.essenceRemovalDestroyChance;
        this.bonusSocketChance = defaults.bonusSocketChance;
        this.maxReduceChance = defaults.maxReduceChance;
    }
}
