package irai.mod.reforge.Socket;

/**
 * Enum defining the elemental/types of essences available.
 * Each essence type has a unique color and theme for its effects.
 */
public enum EssenceType {
    FIRE("Fire", "FF5533", "§c", "Burns with intense heat"),
    ICE("Ice", "55FFFF", "§b", "Glows with frost"),
    LIGHTNING("Lightning", "FFFF55", "§e", "Crackles with energy"),
    LIFE("Life", "55FF55", "§a", "Pulses with vitality"),
    VOID("Void", "AA55FF", "§d", "Echos with darkness"),
    WATER("Water", "5555FF", "§9", "Flows with clarity");
    
    private final String displayName;
    private final String hexColor;
    private final String chatColor;
    private final String description;
    
    EssenceType(String displayName, String hexColor, String chatColor, String description) {
        this.displayName = displayName;
        this.hexColor = hexColor;
        this.chatColor = chatColor;
        this.description = description;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getHexColor() {
        return hexColor;
    }
    
    public String getChatColor() {
        return chatColor;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * Gets an essence type from its name (case-insensitive).
     */
    public static EssenceType fromName(String name) {
        if (name == null) return null;
        for (EssenceType type : values()) {
            if (type.displayName.equalsIgnoreCase(name)) {
                return type;
            }
        }
        return null;
    }
}
