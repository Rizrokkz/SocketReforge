package irai.mod.reforge.Socket;

public class EssenceEffect {

    public enum StatType {
        // Offensive
        ATTACK_SPEED, DAMAGE, CRIT_CHANCE, CRIT_DAMAGE,
        // Defensive
        HEALTH, DEFENSE, EVASION,
        // Utility
        LIFE_STEAL, MOVEMENT_SPEED, LUCK
    }

    public enum EffectType {
        FLAT,       // e.g. +10 Attack Speed
        PERCENTAGE  // e.g. +5% Attack Speed
    }

    private final StatType   stat;
    private final EffectType type;
    private final double     value;

    public EssenceEffect(StatType stat, EffectType type, double value) {
        this.stat  = stat;
        this.type  = type;
        this.value = value;
    }

    public StatType   getStat()  { return stat; }
    public EffectType getType()  { return type; }
    public double     getValue() { return value; }

    /** e.g. "+6% Damage (Fire Essence)" */
    public String getDisplayLine(String essenceName) {
        String sign   = value >= 0 ? "+" : "";
        String suffix = type == EffectType.PERCENTAGE ? "%" : "";
        String label  = stat.name().replace("_", " ");
        return sign + (int) value + suffix + " " + label + " (" + essenceName + ")";
    }
}