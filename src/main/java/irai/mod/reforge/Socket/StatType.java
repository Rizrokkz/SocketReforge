package irai.mod.reforge.Socket;

/**
 * Enum defining all available stat types that can be modified by essences.
 * Stats are categorized into Offensive, Defensive, and Utility types.
 */
public enum StatType {
    // Offensive Stats
    ATTACK_SPEED("Attack Speed", "ATK SPD"),
    DAMAGE("Damage", "DMG"),
    CRIT_CHANCE("Critical Chance", "CRIT"),
    CRIT_DAMAGE("Critical Damage", "CRIT DMG"),
    
    // Defensive Stats
    HEALTH("Health", "HP"),
    DEFENSE("Defense", "DEF"),
    EVASION("Evasion", "EVA"),
    REGENERATION("Regeneration", "REGEN"),
    FIRE_DEFENSE("Fire Defense", "FIRE DEF"),
    
    // Utility Stats
    LIFE_STEAL("Life Steal", "LIFESTEAL"),
    MOVEMENT_SPEED("Movement Speed", "MOV SPD"),
    LUCK("Luck", "LUCK"),
    
    // Elemental Stats
    COLD_DAMAGE("Cold Damage", "COLD DMG"),
    SLOW_EFFECT("Slow Effect", "SLOW"),
    FIRE_DAMAGE("Fire Damage", "FIRE DMG"),
    FIRE_RESISTANCE("Fire Resistance", "FIRE RES"),
    LIGHTNING_DAMAGE("Lightning Damage", "LIGHT DMG");
    
    private final String displayName;
    private final String shortName;
    
    StatType(String displayName, String shortName) {
        this.displayName = displayName;
        this.shortName = shortName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getShortName() {
        return shortName;
    }
    
    /**
     * Checks if this stat is an offensive stat.
     */
    public boolean isOffensive() {
        return this == ATTACK_SPEED || this == DAMAGE || 
               this == CRIT_CHANCE || this == CRIT_DAMAGE;
    }
    
    /**
     * Checks if this stat is a defensive stat.
     */
    public boolean isDefensive() {
        return this == HEALTH || this == DEFENSE || this == EVASION;
    }
    
    /**
     * Checks if this stat is a utility stat.
     */
    public boolean isUtility() {
        return this == LIFE_STEAL || this == MOVEMENT_SPEED || this == LUCK;
    }
    
    /**
     * Checks if this stat is an elemental stat.
     */
    public boolean isElemental() {
        return this == COLD_DAMAGE || this == SLOW_EFFECT || 
               this == FIRE_DAMAGE || this == LIGHTNING_DAMAGE;
    }
}
