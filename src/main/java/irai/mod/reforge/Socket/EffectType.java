package irai.mod.reforge.Socket;

/**
 * Enum defining the type of effect an essence provides.
 * FLAT: Adds a fixed value to the stat (e.g., +10 Attack Speed)
 * PERCENTAGE: Adds a percentage bonus to the stat (e.g., +5% Damage)
 */
public enum EffectType {
    FLAT("Flat", "+"),
    PERCENTAGE("Percentage", "+%");
    
    private final String displayName;
    private final String prefix;
    
    EffectType(String displayName, String prefix) {
        this.displayName = displayName;
        this.prefix = prefix;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getPrefix() {
        return prefix;
    }
    
    /**
     * Formats a value with the appropriate prefix and suffix for this effect type.
     * @param value The numeric value to format
     * @return Formatted string (e.g., "+10" for FLAT or "+5%" for PERCENTAGE)
     */
    public String formatValue(double value) {
        if (this == PERCENTAGE) {
            return prefix + String.format("%.0f", value) + "%";
        } else {
            // Display as integer if whole number, otherwise with decimals
            if (value == (long) value) {
                return prefix + String.format("%.0f", value);
            } else {
                return prefix + String.format("%.1f", value);
            }
        }
    }
}
