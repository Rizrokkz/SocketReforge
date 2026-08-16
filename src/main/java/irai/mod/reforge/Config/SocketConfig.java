package irai.mod.reforge.Config;

import static com.hypixel.hytale.codec.Codec.BOOLEAN;
import static com.hypixel.hytale.codec.Codec.DOUBLE_ARRAY;
import static com.hypixel.hytale.codec.Codec.STRING_ARRAY;

import java.util.Arrays;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Socket punching configuration.
 * Loaded from resources/Server/Config/SocketConfig.json via ReforgePlugin.
 * Also accepted by SocketPunchUI.setConfig().
 */
@SuppressWarnings("removal")
public class SocketConfig implements ConfigDefaultInjector {

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
            .append(
                    new KeyedCodec<>("WEAPON_AFFINITY_APPEARANCE_PATCHING_ENABLED", BOOLEAN),
                    (cfg, v) -> cfg.weaponAffinityAppearancePatchingEnabled = Boolean.TRUE.equals(v),
                    SocketConfig::isWeaponAffinityAppearancePatchingEnabled
            ).add()
            .append(
                    new KeyedCodec<>("RESONANCE_CLASS_MAPPINGS", STRING_ARRAY),
                    (cfg, v) -> cfg.resonanceClassMappings = v == null ? new String[0] : v,
                    SocketConfig::getResonanceClassMappings
            ).add()
            .append(
                    new KeyedCodec<>("RESONANCE_WEAPON_CLASS_HINTS", STRING_ARRAY),
                    (cfg, v) -> cfg.resonanceWeaponClassHints = v == null ? new String[0] : v,
                    SocketConfig::getResonanceWeaponClassHints
            ).add()
            .append(
                    new KeyedCodec<>("CLOCKWORK_AMMO_ITEM_HINTS", STRING_ARRAY),
                    (cfg, v) -> cfg.clockworkAmmoItemHints = v == null ? new String[0] : v,
                    SocketConfig::getClockworkAmmoItemHints
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

    // Opt-in because this exports model/item appearance overrides at startup.
    private Boolean weaponAffinityAppearancePatchingEnabled;

    /**
     * Optional resonance scope overrides.
     * Format examples:
     * Kingsbrand=SWORD
     * Shield Sunder=SWORD,AXE,MACE,DAGGER,BOW,CROSSBOW
     * Prismatic Force=WEAPON
     * Tideguard=ARMOR
     */
    private String[] resonanceClassMappings = new String[] {
            "Clockwork Loader=CROSSBOW,GUN"
    };

    /**
     * Classifier hints used before the built-in id classifier.
     * Format examples:
     * GUN=gun,rifle,pistol
     * GLAIVE=glaive,glaives
     */
    private String[] resonanceWeaponClassHints = new String[] {
            "GUN=gun,rifle,pistol,blunderbuss,firearm",
            "GLAIVE=glaive,glaives",
            "KNUCKLE=knuckle,knuckles,fist,claw,claws"
    };

    /**
     * Item-id tokens treated as ammo-like consumables for Clockwork Loader refunds.
     * These are also excluded from socketable weapon fallback matching.
     */
    private String[] clockworkAmmoItemHints = new String[] {
            "arrow",
            "bolt",
            "projectile",
            "ammo",
            "ammunition",
            "bullet",
            "dart",
            "cartridge",
            "round",
            "shell",
            "rocket",
            "missile",
            "grenade",
            "slug",
            "pellet"
    };

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
    public boolean isWeaponAffinityAppearancePatchingEnabled() {
        return Boolean.TRUE.equals(weaponAffinityAppearancePatchingEnabled);
    }
    public String[] getResonanceClassMappings() {
        return resonanceClassMappings == null ? new String[0] : resonanceClassMappings;
    }
    public String[] getResonanceWeaponClassHints() {
        return resonanceWeaponClassHints == null ? new String[0] : resonanceWeaponClassHints;
    }
    public String[] getClockworkAmmoItemHints() {
        return clockworkAmmoItemHints == null ? new String[0] : clockworkAmmoItemHints;
    }

    // ── Setters (used by config loader) ───────────────────────────────────────

    public void setMaxSocketsWeapon(int v)               { maxSocketsWeapon = v; }
    public void setMaxSocketsArmor(int v)                { maxSocketsArmor  = v; }
    public void setPunchSuccessChances(double[] v)        { punchSuccessChances = v; }
    public void setPunchBreakChances(double[] v)          { punchBreakChances = v; }
    public void setEssenceRemovalSuccessChance(double v)  { essenceRemovalSuccessChance = v; }
    public void setEssenceRemovalDestroyChance(double v)  { essenceRemovalDestroyChance  = v; }
    public void setBonusSocketChance(double v)            { bonusSocketChance = v; }
    public void setMaxReduceChance(double v)              { maxReduceChance = v; }
    public void setWeaponAffinityAppearancePatchingEnabled(boolean v) {
        weaponAffinityAppearancePatchingEnabled = v;
    }
    public void setResonanceClassMappings(String[] v) {
        resonanceClassMappings = v == null ? new String[0] : v;
    }
    public void setResonanceWeaponClassHints(String[] v) {
        resonanceWeaponClassHints = v == null ? new String[0] : v;
    }
    public void setClockworkAmmoItemHints(String[] v) {
        clockworkAmmoItemHints = v == null ? new String[0] : v;
    }

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
        this.weaponAffinityAppearancePatchingEnabled = Boolean.FALSE;
        this.resonanceClassMappings = defaults.resonanceClassMappings.clone();
        this.resonanceWeaponClassHints = defaults.resonanceWeaponClassHints.clone();
        this.clockworkAmmoItemHints = defaults.clockworkAmmoItemHints.clone();
    }

    @Override
    public boolean injectMissingDefaults() {
        SocketConfig defaults = new SocketConfig();
        boolean changed = false;

        double[] mergedSuccessChances = ConfigMergeUtils.extendDoubleArray(punchSuccessChances, defaults.punchSuccessChances);
        if (!Arrays.equals(punchSuccessChances, mergedSuccessChances)) {
            this.punchSuccessChances = mergedSuccessChances;
            changed = true;
        }

        double[] mergedBreakChances = ConfigMergeUtils.extendDoubleArray(punchBreakChances, defaults.punchBreakChances);
        if (!Arrays.equals(punchBreakChances, mergedBreakChances)) {
            this.punchBreakChances = mergedBreakChances;
            changed = true;
        }

        if (weaponAffinityAppearancePatchingEnabled == null) {
            weaponAffinityAppearancePatchingEnabled = Boolean.FALSE;
            changed = true;
        }

        if (resonanceClassMappings == null) {
            resonanceClassMappings = defaults.resonanceClassMappings.clone();
            changed = true;
        } else {
            String[] mergedMappings = ConfigMergeUtils.mergeMissingByKey(resonanceClassMappings, defaults.resonanceClassMappings, '=');
            if (!Arrays.equals(resonanceClassMappings, mergedMappings)) {
                resonanceClassMappings = mergedMappings;
                changed = true;
            }
        }

        if (resonanceWeaponClassHints == null) {
            resonanceWeaponClassHints = defaults.resonanceWeaponClassHints.clone();
            changed = true;
        } else {
            String[] mergedHints = ConfigMergeUtils.mergeMissingByKey(resonanceWeaponClassHints, defaults.resonanceWeaponClassHints, '=');
            if (!Arrays.equals(resonanceWeaponClassHints, mergedHints)) {
                resonanceWeaponClassHints = mergedHints;
                changed = true;
            }
        }

        if (clockworkAmmoItemHints == null) {
            clockworkAmmoItemHints = defaults.clockworkAmmoItemHints.clone();
            changed = true;
        } else {
            String[] mergedAmmoHints = ConfigMergeUtils.mergeUniqueValues(clockworkAmmoItemHints, defaults.clockworkAmmoItemHints);
            if (!Arrays.equals(clockworkAmmoItemHints, mergedAmmoHints)) {
                clockworkAmmoItemHints = mergedAmmoHints;
                changed = true;
            }
        }

        if (resonanceClassMappings == null) {
            resonanceClassMappings = new String[0];
            changed = true;
        }
        if (resonanceWeaponClassHints == null) {
            resonanceWeaponClassHints = new String[0];
            changed = true;
        }
        if (clockworkAmmoItemHints == null) {
            clockworkAmmoItemHints = new String[0];
            changed = true;
        }

        return changed;
    }
}
