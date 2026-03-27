package irai.mod.reforge.Config;

import static com.hypixel.hytale.codec.Codec.STRING_ARRAY;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Unified configuration for lore gem detection, spirit pools, core colors,
 * and explicit spirit ability mappings.
 */
@SuppressWarnings("removal")
public class LoreMappingConfig {

    public static final BuilderCodec<LoreMappingConfig> CODEC = BuilderCodec.<LoreMappingConfig>builder(LoreMappingConfig.class, LoreMappingConfig::new)
            .append(
                    new KeyedCodec<>("LORE_GEM_COLORS", STRING_ARRAY),
                    (cfg, v) -> cfg.gemColorEntries = v,
                    cfg -> cfg.gemColorEntries == null ? new String[0] : cfg.gemColorEntries
            ).add()
            .append(
                    new KeyedCodec<>("LORE_COLOR_SPIRITS", STRING_ARRAY),
                    (cfg, v) -> cfg.colorSpiritEntries = v,
                    cfg -> cfg.colorSpiritEntries == null ? new String[0] : cfg.colorSpiritEntries
            ).add()
            .append(
                    new KeyedCodec<>("LORE_CORE_COLORS", STRING_ARRAY),
                    (cfg, v) -> cfg.coreColorEntries = v,
                    cfg -> cfg.coreColorEntries == null ? new String[0] : cfg.coreColorEntries
            ).add()
            .append(
                    new KeyedCodec<>("LORE_ABILITY_ENTRIES", STRING_ARRAY),
                    (cfg, v) -> cfg.abilityEntries = v,
                    cfg -> cfg.abilityEntries == null ? new String[0] : cfg.abilityEntries
            ).add()
            .build();

    // Format: "token=color" (token is matched against itemId, case-insensitive)
    private String[] gemColorEntries = new String[] {
            "rock_gem_ruby=red",
            "rock_gem_sapphire=blue",
            "rock_gem_emerald=green",
            "rock_gem_topaz=yellow",
            "rock_gem_diamond=white",
            "rock_gem_voidstone=black",
            "rock_gem_zephyr=cyan"
    };

    // Format: "color=SPIRIT1,SPIRIT2,SPIRIT3"
    // Empty means "all spirits allowed".
    private String[] colorSpiritEntries = new String[] {
            "black=Wraith,Spawn_Void,Horse_Skeleton,Skeleton_Burnt_Praetorian,Skeleton_Burnt_Soldier,Zombie_Burnt,Cow_Undead",
            "blue=Whale_Humpback,Shark_Hammerhead,Trillodon,Crocodile",
            "cyan=Horse,Wolf_Black,Rex_Cave",
            "green=Bison,Tiger_Sabertooth,Mosshorn_Plain,Mosshorn,Toad_Rhino,Bear_Grizzly,Moose_Bull",
            "red=Golem_Firesteel,Emberwulf,Toad_Rhino_Magma",
            "white=Hound_Bleached,Leopard_Snow",
            "yellow=Spirit_Thunder,Camel,Ram,Scorpion"
    };

    // Format: "color"
    // Colors listed here are allowed to roll core lore effects.
    private String[] coreColorEntries = new String[] {
            "red",
            "blue",
            "black"
    };

    // Format: "spiritId=TRIGGER,PROC_CHANCE,COOLDOWN_MS,EFFECT_TYPE,BASE_VALUE,PER_LEVEL"
    private String[] abilityEntries = new String[] {
            "Wraith=ON_HIT,0.06,5200,APPLY_INVISIBLE,0.30,0.02",
            "Spawn_Void=ON_HIT,0.06,7000,APPLY_FEAR,2.0,0.10",
            "Horse_Skeleton=SIGNATURE_RAZORSTRIKE",
            "Skeleton_Burnt_Praetorian=SIGNATURE_WHIRLWIND",
            "Skeleton_Burnt_Soldier=ON_HIT,0.07,5000,APPLY_BURN,2.0,0.10",
            "Zombie_Burnt=ON_HIT,0.07,5200,APPLY_BLEED,2.0,0.10",
            "Cow_Undead=SIGNATURE_SHRAPNEL",
            "Whale_Humpback=SIGNATURE_GROUNDSLAM",
            "Shark_Hammerhead=SIGNATURE_PUMMEL",
            "Trillodon=ON_HIT,0.06,7000,HEAL_AREA_OVER_TIME,1.6,0.08",
            "Crocodile=SIGNATURE_CAUSTIC_FINALE",
            "Horse=SIGNATURE_VOLLEY",
            "Rex_Cave=SIGNATURE_OMNISLASH",
            "Bison=ON_CRIT,0.10,4500,CRIT_CHARGE,0.35,0.02",
            "Tiger_Sabertooth=ON_HIT,0.07,4800,DRAIN_LIFE,2.0,0.10",
            "Mosshorn_Plain=SIGNATURE_AREA_HEAL",
            "Mosshorn=ON_DAMAGED,0.08,5000,HEAL_SELF_OVER_TIME,1.4,0.08",
            "Toad_Rhino=ON_DAMAGED,0.07,6000,APPLY_SHIELD,0.30,0.02",
            "Bear_Grizzly=ON_DAMAGED,0.08,4000,DAMAGE_ATTACKER,2.6,0.12",
            "Moose_Bull=ON_HIT,0.06,5000,APPLY_HASTE,0.30,0.02",
            "Golem_Firesteel=SIGNATURE_OCTASLASH",
            "Emberwulf=SIGNATURE_BLOOD_RUSH",
            "Toad_Rhino_Magma=SIGNATURE_BURN_FINALE",
            "Hound_Bleached=ON_HIT,0.07,4800,APPLY_SLOW,1.6,0.08",
            "Leopard_Snow=ON_HIT,0.06,5200,APPLY_FREEZE,1.4,0.08",
            "Spirit_Thunder=SIGNATURE_VORTEXSTRIKE",
            "Camel=SIGNATURE_BIG_ARROW",
            "Ram=ON_HIT,0.07,5200,APPLY_SHOCK,1.8,0.10",
            "Scorpion=ON_HIT,0.08,4800,APPLY_POISON,2.0,0.10"
    };

    public String[] getGemColorEntries() { return gemColorEntries; }
    public String[] getColorSpiritEntries() { return colorSpiritEntries; }
    public String[] getCoreColorEntries() { return coreColorEntries; }
    public String[] getAbilityEntries() { return abilityEntries; }

    public void setGemColorEntries(String[] v) { this.gemColorEntries = v; }
    public void setColorSpiritEntries(String[] v) { this.colorSpiritEntries = v; }
    public void setCoreColorEntries(String[] v) { this.coreColorEntries = v; }
    public void setAbilityEntries(String[] v) { this.abilityEntries = v; }
}
