package irai.mod.reforge.Config;

import static com.hypixel.hytale.codec.Codec.DOUBLE_ARRAY;
import static com.hypixel.hytale.codec.Codec.STRING_ARRAY;

import java.util.Arrays;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Configures NPC elemental affinities and per-element effectiveness.
 */
@SuppressWarnings("removal")
public class ElementalAffinityConfig implements ConfigDefaultInjector {
    public static final BuilderCodec<ElementalAffinityConfig> CODEC = BuilderCodec.<ElementalAffinityConfig>builder(
                    ElementalAffinityConfig.class,
                    ElementalAffinityConfig::new
            )
            .append(
                    new KeyedCodec<>("ENABLED", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.enabled = v[0] > 0.0d; },
                    cfg -> new double[] {cfg.enabled ? 1.0d : 0.0d}
            ).add()
            .append(
                    new KeyedCodec<>("WEAKNESS_MULTIPLIER", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.weaknessMultiplier = v[0]; },
                    cfg -> new double[] {cfg.weaknessMultiplier}
            ).add()
            .append(
                    new KeyedCodec<>("RESISTANCE_MULTIPLIER", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.resistanceMultiplier = v[0]; },
                    cfg -> new double[] {cfg.resistanceMultiplier}
            ).add()
            .append(
                    new KeyedCodec<>("ROLE_AFFINITIES", STRING_ARRAY),
                    (cfg, v) -> cfg.roleAffinities = v == null ? new String[0] : v,
                    ElementalAffinityConfig::getRoleAffinities
            ).add()
            .append(
                    new KeyedCodec<>("CUSTOM_ROLE_AFFINITIES", STRING_ARRAY),
                    (cfg, v) -> cfg.customRoleAffinities = v == null ? new String[0] : v,
                    ElementalAffinityConfig::getCustomRoleAffinities
            ).add()
            .append(
                    new KeyedCodec<>("ELEMENT_MULTIPLIERS", STRING_ARRAY),
                    (cfg, v) -> cfg.elementMultipliers = v == null ? new String[0] : v,
                    ElementalAffinityConfig::getElementMultipliers
            ).add()
            .build();

    private boolean enabled = true;
    private double weaknessMultiplier = 1.25d;
    private double resistanceMultiplier = 0.15d;

    /**
     * Exact or hint rules. Format:
     * hint:partial_role_name=ELEMENT
     * role:Exact_Role_ID=ELEMENT
     */
    private String[] roleAffinities = new String[] {
            "hint:void=VOID",
            "hint:undead=VOID",
            "hint:wraith=VOID",
            "hint:skeleton=VOID",
            "hint:zombie=VOID",
            "hint:ghast=VOID",
            "hint:ghoul=VOID",
            "hint:shadow=VOID",
            "hint:spectre=VOID",
            "hint:aberrant=VOID",
            "hint:crawler=VOID",
            "hint:cultist=VOID",
            "hint:spider=VOID",
            "hint:cave=VOID",
            "role:Rat=VOID",
            "role:Mouse=VOID",
            "hint:molerat=VOID",
            "hint:werewolf=VOID",
            "life=LIFE",
            "holy=LIFE",
            "nature=LIFE",
            "healer=LIFE",
            "forest=LIFE",
            "moss=LIFE",
            "mosshorn=LIFE",
            "root=LIFE",
            "rootling=LIFE",
            "sapling=LIFE",
            "seedling=LIFE",
            "sproutling=LIFE",
            "kweebec=LIFE",
            "snapdragon=LIFE",
            "hedera=LIFE",
            "cactee=LIFE",
            "deer=LIFE",
            "cow=LIFE",
            "bison=LIFE",
            "moose=LIFE",
            "mouflon=LIFE",
            "sheep=LIFE",
            "goat=LIFE",
            "horse=LIFE",
            "boar=LIFE",
            "role:Pig=LIFE",
            "pig_=LIFE",
            "piglet=LIFE",
            "warthog=LIFE",
            "bunny=LIFE",
            "rabbit=LIFE",
            "squirrel=LIFE",
            "bear_grizzly=LIFE",
            "fox=LIFE",
            "wolf_black=LIFE",
            "tiger=LIFE",
            "antelope=LIFE",
            "tortoise=LIFE",
            "armadillo=LIFE",
            "ram=LIFE",
            "role:Chicken=LIFE",
            "chicken_chick=LIFE",
            "turkey=LIFE",
            "hyena=LIFE",
            "meerkat=LIFE",
            "gecko=LIFE",
            "larva_silk=LIFE",
            "earth=LIFE",
            "role:Toad_Rhino=LIFE",
            "hint:scarak=LIFE",
            "fire=FIRE",
            "flame=FIRE",
            "ember=FIRE",
            "magma=FIRE",
            "lava=FIRE",
            "burn=FIRE",
            "desert=FIRE",
            "sand=FIRE",
            "scorpion=FIRE",
            "camel=FIRE",
            "rattle=FIRE",
            "cobra=FIRE",
            "lizard_sand=FIRE",
            "snake_rattle=FIRE",
            "chicken_desert=FIRE",
            "water=WATER",
            "aqua=WATER",
            "ocean=WATER",
            "sea=WATER",
            "river=WATER",
            "whale=WATER",
            "shark=WATER",
            "crocodile=WATER",
            "fish=WATER",
            "eel_=WATER",
            "eel_moray=WATER",
            "bluegill=WATER",
            "crab=WATER",
            "lobster=WATER",
            "pike=WATER",
            "piranha=WATER",
            "minnow=WATER",
            "salmon=WATER",
            "trout=WATER",
            "tang=WATER",
            "duck=WATER",
            "flamingo=WATER",
            "frog=WATER",
            "jellyfish=WATER",
            "snapjaw=WATER",
            "trillodon=WATER",
            "trilobite=WATER",
            "skrill=WATER",
            "snake_marsh=WATER",
            "ice=ICE",
            "frost=ICE",
            "snow=ICE",
            "frozen=ICE",
            "glacier=ICE",
            "polar=ICE",
            "penguin=ICE",
            "yeti=ICE",
            "bleached=ICE",
            "white=ICE",
            "lightning=LIGHTNING",
            "thunder=LIGHTNING",
            "storm=LIGHTNING",
            "shock=LIGHTNING",
            "electric=LIGHTNING",
            "spark=LIGHTNING",
            "wind=LIGHTNING",
            "windwalker=LIGHTNING",
            "bird=LIGHTNING",
            "hawk=LIGHTNING",
            "crow=LIGHTNING",
            "raven=LIGHTNING",
            "owl=LIGHTNING",
            "parrot=LIGHTNING",
            "pigeon=LIGHTNING",
            "sparrow=LIGHTNING",
            "finch=LIGHTNING",
            "woodpecker=LIGHTNING",
            "vulture=LIGHTNING",
            "pterodactyl=LIGHTNING",
            "archaeopteryx=LIGHTNING",
            "bat=LIGHTNING",
            "tetrabird=LIGHTNING"
    };

    /**
     * Player/server-added rules checked before ROLE_AFFINITIES, so they override defaults.
     * Format: hint:partial_role_name=ELEMENT or role:Exact_Role_ID=ELEMENT
     */
    private String[] customRoleAffinities = new String[0];

    /**
     * Explicit effectiveness rules. Format:
     * hint:partial_role_name=ELEMENT:multiplier,ELEMENT:multiplier
     * role:Exact_Role_ID=ELEMENT:multiplier,ELEMENT:multiplier
     */
    private String[] elementMultipliers = new String[] {
            "void=FIRE:0.5,WATER:0.45,ICE:0.45,LIGHTNING:0.55,LIFE:1.3,VOID:0.15",
            "undead=FIRE:0.55,WATER:0.45,ICE:0.45,LIGHTNING:0.5,LIFE:1.3,VOID:0.15",
            "wraith=FIRE:0.45,WATER:0.45,ICE:0.5,LIGHTNING:0.6,LIFE:1.3,VOID:0.15",
            "skeleton=FIRE:0.6,WATER:0.45,ICE:0.4,LIGHTNING:0.5,LIFE:1.3,VOID:0.15",
            "zombie=FIRE:0.6,WATER:0.45,ICE:0.45,LIGHTNING:0.45,LIFE:1.3,VOID:0.15",
            "ghast=FIRE:0.5,WATER:0.4,ICE:0.45,LIGHTNING:0.6,LIFE:1.3,VOID:0.15",
            "life=FIRE:0.5,WATER:0.45,ICE:0.5,LIGHTNING:0.45,LIFE:0.15,VOID:1.3",
            "holy=FIRE:0.45,WATER:0.5,ICE:0.5,LIGHTNING:0.45,LIFE:0.15,VOID:1.3",
            "nature=FIRE:1.25,WATER:0.4,ICE:0.6,LIGHTNING:0.45,LIFE:0.2,VOID:1.25",
            "fire=FIRE:0.15,WATER:0.55,ICE:1.25,LIGHTNING:0.45,LIFE:0.5,VOID:0.5",
            "flame=FIRE:0.15,WATER:0.55,ICE:1.25,LIGHTNING:0.45,LIFE:0.5,VOID:0.5",
            "ember=FIRE:0.15,WATER:0.5,ICE:1.25,LIGHTNING:0.45,LIFE:0.5,VOID:0.5",
            "magma=FIRE:0.12,WATER:0.6,ICE:1.25,LIGHTNING:0.4,LIFE:0.45,VOID:0.5",
            "lava=FIRE:0.12,WATER:0.6,ICE:1.25,LIGHTNING:0.4,LIFE:0.45,VOID:0.5",
            "water=FIRE:1.25,WATER:0.15,ICE:0.45,LIGHTNING:0.6,LIFE:0.5,VOID:0.5",
            "fish=FIRE:1.25,WATER:0.15,ICE:0.45,LIGHTNING:0.6,LIFE:0.5,VOID:0.5",
            "jellyfish=FIRE:1.15,WATER:0.15,ICE:0.4,LIGHTNING:1.25,LIFE:0.5,VOID:0.5",
            "ice=FIRE:0.6,WATER:0.45,ICE:0.15,LIGHTNING:1.25,LIFE:0.5,VOID:0.5",
            "frost=FIRE:0.6,WATER:0.45,ICE:0.15,LIGHTNING:1.25,LIFE:0.5,VOID:0.5",
            "snow=FIRE:0.55,WATER:0.45,ICE:0.15,LIGHTNING:1.25,LIFE:0.5,VOID:0.5",
            "lightning=FIRE:0.45,WATER:1.25,ICE:0.6,LIGHTNING:0.15,LIFE:0.5,VOID:0.5",
            "thunder=FIRE:0.45,WATER:1.25,ICE:0.6,LIGHTNING:0.15,LIFE:0.5,VOID:0.5",
            "storm=FIRE:0.5,WATER:1.25,ICE:0.55,LIGHTNING:0.15,LIFE:0.5,VOID:0.5",
            "hint:scarak=FIRE:1.3,WATER:0.55,ICE:0.45,LIGHTNING:0.5,LIFE:0.2,VOID:0.5"
    };

    public boolean isEnabled() {
        return enabled;
    }

    public double getWeaknessMultiplier() {
        return weaknessMultiplier;
    }

    public double getResistanceMultiplier() {
        return resistanceMultiplier;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setWeaknessMultiplier(double weaknessMultiplier) {
        this.weaknessMultiplier = weaknessMultiplier;
    }

    public void setResistanceMultiplier(double resistanceMultiplier) {
        this.resistanceMultiplier = resistanceMultiplier;
    }

    public String[] getRoleAffinities() {
        return roleAffinities == null ? new String[0] : roleAffinities;
    }

    public String[] getCustomRoleAffinities() {
        return customRoleAffinities == null ? new String[0] : customRoleAffinities;
    }

    public String[] getElementMultipliers() {
        return elementMultipliers == null ? new String[0] : elementMultipliers;
    }

    public void setRoleAffinities(String[] roleAffinities) {
        this.roleAffinities = roleAffinities == null ? new String[0] : roleAffinities;
    }

    public void setCustomRoleAffinities(String[] customRoleAffinities) {
        this.customRoleAffinities = customRoleAffinities == null ? new String[0] : customRoleAffinities;
    }

    public void setElementMultipliers(String[] elementMultipliers) {
        this.elementMultipliers = elementMultipliers == null ? new String[0] : elementMultipliers;
    }

    public void resetToDefaults() {
        ElementalAffinityConfig defaults = new ElementalAffinityConfig();
        this.enabled = defaults.enabled;
        this.weaknessMultiplier = defaults.weaknessMultiplier;
        this.resistanceMultiplier = defaults.resistanceMultiplier;
        this.roleAffinities = defaults.roleAffinities.clone();
        this.customRoleAffinities = defaults.customRoleAffinities.clone();
        this.elementMultipliers = defaults.elementMultipliers.clone();
    }

    @Override
    public boolean injectMissingDefaults() {
        ElementalAffinityConfig defaults = new ElementalAffinityConfig();
        boolean changed = false;

        String[] mergedRoleAffinities = ConfigMergeUtils.mergeMissingByKey(roleAffinities, defaults.roleAffinities, '=');
        if (!Arrays.equals(roleAffinities, mergedRoleAffinities)) {
            roleAffinities = mergedRoleAffinities;
            changed = true;
        }

        String[] mergedElementMultipliers = ConfigMergeUtils.mergeMissingByKey(elementMultipliers, defaults.elementMultipliers, '=');
        if (!Arrays.equals(elementMultipliers, mergedElementMultipliers)) {
            elementMultipliers = mergedElementMultipliers;
            changed = true;
        }

        return changed;
    }
}
