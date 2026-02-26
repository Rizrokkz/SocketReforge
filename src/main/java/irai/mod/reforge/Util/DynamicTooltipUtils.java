package irai.mod.reforge.Util;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.hypixel.hytale.server.core.entity.entities.Player;

/**
 * Utility class for interacting with DynamicTooltipsLib.
 * Uses Provider-based approach for per-item metadata tooltips.
 */
public class DynamicTooltipUtils {

    // ==================== Constants ====================
    
    private static final String MOD_PREFIX = "[SocketReforge]";
    private static final String PROVIDER_ID = "irai:SocketReforge";
    
    // Possible class names for DynamicTooltipsLib
    private static final String[] LIB_CLASS_NAMES = {
        "org.herolias.tooltips.api.DynamicTooltipsApiProvider",
        "org.herolias.tooltips.DynamicTooltipsLib",
        "org.herolias.tooltips.api.DynamicTooltipsApi"
    };
    
    // Tooltip color constants - DynamicTooltipsLib format
    private static final String COLOR_CYAN = "<color is=\"#55FFFF\">";
    private static final String COLOR_WHITE = "<color is=\"#FFFFFF\">";
    private static final String COLOR_GREEN = "<color is=\"#55FF55\">";
    private static final String COLOR_BLUE = "<color is=\"#5555FF\">";
    private static final String COLOR_PURPLE = "<color is=\"#FF55FF\">";
    private static final String COLOR_GRAY = "<color is=\"#AAAAAA\">";
    
    // ==================== Reforge Level ====================
    
    private enum ReforgeLevel {
        COMMON(1, COLOR_GREEN),
        UNCOMMON(2, COLOR_BLUE),
        LEGENDARY(3, COLOR_PURPLE);
        
        private final int level;
        private final String color;
        
        ReforgeLevel(int level, String color) {
            this.level = level;
            this.color = color;
        }
        
        public int getLevel() { return level; }
        public String getColor() { return color; }
        
        public static ReforgeLevel fromLevel(int level) {
            for (ReforgeLevel rl : values()) {
                if (rl.level == level) return rl;
            }
            return COMMON;
        }
    }

    // ==================== Logger ====================
    
    public interface Logger {
        void info(String message);
        void warn(String message);
        void error(String message);
    }
    
    private static final Logger DEFAULT_LOGGER = new Logger() {
        @Override public void info(String message) { System.out.println(MOD_PREFIX + " " + message); }
        @Override public void warn(String message) { System.out.println(MOD_PREFIX + " [WARNING] " + message); }
        @Override public void error(String message) { System.err.println(MOD_PREFIX + " [ERROR] " + message); }
    };
    
    // ==================== State ====================
    
    private static volatile boolean isAvailable = false;
    private static volatile boolean initialized = false;
    private static volatile boolean providerRegistered = false;
    private static final Object INITIALIZATION_LOCK = new Object();
    
    // API objects
    private static Object tooltipApi = null;
    private static Method registerProviderMethod = null;
    private static Method refreshAllPlayersMethod = null;
    private static Method tooltipDataBuilderMethod = null;
    private static Class<?> tooltipDataClass = null;
    private static Class<?> tooltipProviderClass = null;
    
    // Tooltip data storage - maps item ID + metadata -> tooltip lines
    private static final ConcurrentMap<String, String[]> tooltipDataCache = new ConcurrentHashMap<>();
    
    private static Logger logger = DEFAULT_LOGGER;
    private static boolean debugMode = false;
    private static String loadedClassName = null;

    // ==================== Initialization ====================
    
    static {
        initialize();
    }
    
    /**
     * Initialize DynamicTooltipUtils. This is automatically called when the class is loaded,
     * but can also be called explicitly to ensure initialization.
     */
    public static void init() {
        initialize();
    }
    
    private static void initialize() {
        if (initialized) return;
        
        synchronized (INITIALIZATION_LOCK) {
            if (initialized) return;
            
            for (String className : LIB_CLASS_NAMES) {
                try {
                    logger.info("Trying to load DynamicTooltipsLib: " + className);
                    
                    Class<?> providerClass = Class.forName(className);
                    Method getMethod = providerClass.getMethod("get");
                    tooltipApi = getMethod.invoke(null);
                    
                    if (tooltipApi != null) {
                        isAvailable = true;
                        loadedClassName = className;
                        
                        // Get the refreshAllPlayers method
                        try {
                            refreshAllPlayersMethod = tooltipApi.getClass().getMethod("refreshAllPlayers");
                        } catch (NoSuchMethodException e) {
                            if (debugMode) logger.warn("refreshAllPlayers method not found in " + className);
                        }
                        
                        logger.info("DynamicTooltipsLib loaded from: " + className);
                        break;
                    }
                } catch (Exception e) {
                    if (debugMode) logger.warn("Error loading " + className + ": " + e.getMessage());
                } catch (Throwable t) {
                    if (debugMode) logger.warn("Unexpected error with " + className + ": " + t.getMessage());
                }
            }
            
            if (!isAvailable) {
                logger.warn("DynamicTooltipsLib not found - tooltip features disabled");
                logger.warn("To enable tooltips, install DynamicTooltipsLib in server/mods/");
            }
            
            initialized = true;
        }
    }

    // ==================== Public API ====================
    
    public static boolean isAvailable() {
        return isAvailable;
    }
    
    /**
     * Get a message about DynamicTooltipsLib status - useful for sending to players
     * @return status message
     */
    public static String getStatusMessage() {
        if (isAvailable) {
            return "";
        }
        return "<color=#FF5555>DynamicTooltipsLib not installed - tooltips disabled. Install it in server/mods/ for item tooltips.";
    }
    
    public static String getLoadedClassName() {
        return loadedClassName;
    }
    
    public static void setLogger(Logger customLogger) {
        logger = Objects.requireNonNull(customLogger, "Logger cannot be null");
    }
    
    public static void setDebugMode(boolean enabled) {
        debugMode = enabled;
    }

    /**
     * Register tooltip data for a specific item with metadata
     * This is the main method to call when an item is reforged/socketed
     * 
     * @param baseItemId The base item ID (e.g., "Weapon_Axe_Mithril")
     * @param metadata The item metadata (e.g., "reforge:1")
     * @param lines The tooltip lines to display
     */
    public static void registerTooltip(String baseItemId, String metadata, String... lines) {
        if (!isAvailable) return;
        if (!isValidItemId(baseItemId)) return;
        
        String cacheKey = baseItemId + "::" + (metadata != null ? metadata : "");
        
        if (lines == null || lines.length == 0) {
            tooltipDataCache.remove(cacheKey);
        } else {
            tooltipDataCache.put(cacheKey, lines);
        }
        
        // Register provider if not already done
        if (!providerRegistered) {
            registerTooltipProvider();
        }
        
        if (debugMode) {
            logger.info("Registered tooltip for " + cacheKey + " with " + lines.length + " lines");
        }
    }
    
    /**
     * Register reforge tooltip
     */
    public static void registerReforgeTooltip(String baseItemId, int level, boolean isArmor) {
        if (!isAvailable) return;
        if (!isValidItemId(baseItemId)) return;
        if (level <= 0) return;
        
        // Build metadata string
        String metadata = "reforge:" + level;
        
        String upgradeName = isArmor ? getArmorUpgradeName(level) : getUpgradeName(level);
        double multiplier = isArmor ? getDefenseMultiplier(level) : getDamageMultiplier(level);
        int percentBonus = (int) ((multiplier - 1.0) * 100);
        
        String statType = isArmor ? "defense" : "damage";
        ReforgeLevel reforgeLevel = ReforgeLevel.fromLevel(level);
        
        String line = reforgeLevel.getColor() + upgradeName + " +" + level 
                + " (" + COLOR_GREEN + "+" + percentBonus + "% " + statType + COLOR_WHITE + ")";
        
        registerTooltip(baseItemId, metadata, line);
    }
    
    /**
     * Register socket tooltip
     */
    public static void registerSocketTooltip(String baseItemId, String metadata, int socketCount, int filledSockets) {
        if (!isAvailable) return;
        
        String line = COLOR_CYAN + "Sockets: " + COLOR_WHITE + filledSockets + "/" + socketCount;
        
        String cacheKey = baseItemId + "::" + (metadata != null ? metadata : "");
        String[] existing = tooltipDataCache.get(cacheKey);
        
        String[] combined;
        if (existing != null) {
            combined = new String[existing.length + 1];
            System.arraycopy(existing, 0, combined, 0, existing.length);
            combined[existing.length] = line;
        } else {
            combined = new String[]{line};
        }
        
        registerTooltip(baseItemId, metadata, combined);
    }
    
    /**
     * Register essence tooltip
     */
    public static void registerEssenceTooltip(String baseItemId, String metadata, String essenceName, String effectLine) {
        if (!isAvailable) return;
        
        String line1 = COLOR_PURPLE + "Essence: " + COLOR_WHITE + essenceName;
        
        String cacheKey = baseItemId + "::" + (metadata != null ? metadata : "");
        String[] existing = tooltipDataCache.get(cacheKey);
        
        int startIdx = existing != null ? existing.length : 0;
        String[] lines;
        
        if (effectLine != null && !effectLine.isEmpty()) {
            lines = new String[existing != null ? existing.length + 2 : 2];
            if (existing != null) {
                System.arraycopy(existing, 0, lines, 0, existing.length);
            }
            lines[startIdx] = line1;
            lines[startIdx + 1] = COLOR_GRAY + effectLine;
        } else {
            lines = new String[existing != null ? existing.length + 1 : 1];
            if (existing != null) {
                System.arraycopy(existing, 0, lines, 0, existing.length);
            }
            lines[startIdx] = line1;
        }
        
        registerTooltip(baseItemId, metadata, lines);
    }
    
    /**
     * Clear tooltip for an item
     */
    public static void clearTooltip(String baseItemId, String metadata) {
        String cacheKey = baseItemId + "::" + (metadata != null ? metadata : "");
        tooltipDataCache.remove(cacheKey);
    }
    
    /**
     * Get cached tooltip data
     */
    public static String[] getTooltipData(String baseItemId, String metadata) {
        String cacheKey = baseItemId + "::" + (metadata != null ? metadata : "");
        return tooltipDataCache.get(cacheKey);
    }

    // ==================== Provider Implementation ====================
    
    /**
     * Register the tooltip provider with DynamicTooltipsLib
     */
    private static void registerTooltipProvider() {
        if (!isAvailable || tooltipApi == null || providerRegistered) return;
        
        try {
            // Try to find TooltipProvider class
            try {
                tooltipProviderClass = Class.forName("org.herolias.tooltips.api.TooltipProvider");
            } catch (ClassNotFoundException e) {
                // Try alternate name
                try {
                    tooltipProviderClass = Class.forName("herolias.tooltips.api.TooltipProvider");
                } catch (ClassNotFoundException e2) {
                    logger.warn("TooltipProvider class not found");
                    return;
                }
            }
            
            // Create dynamic proxy that implements TooltipProvider
            Object provider = Proxy.newProxyInstance(
                tooltipProviderClass.getClassLoader(),
                new Class<?>[]{tooltipProviderClass},
                new TooltipProviderInvocationHandler()
            );
            
            // Find and call registerProvider
            try {
                registerProviderMethod = tooltipApi.getClass().getMethod("registerProvider", tooltipProviderClass);
            } catch (NoSuchMethodException e) {
                // Try alternate
                try {
                    registerProviderMethod = tooltipApi.getClass().getMethod("registerProvider", Object.class);
                } catch (NoSuchMethodException e2) {
                    logger.warn("registerProvider method not found");
                    return;
                }
            }
            
            registerProviderMethod.setAccessible(true);
            registerProviderMethod.invoke(tooltipApi, provider);
            
            providerRegistered = true;
            logger.info("Registered SocketReforge tooltip provider - ID: " + PROVIDER_ID);
            
        } catch (Exception e) {
            logger.error("Failed to register tooltip provider: " + e.getMessage());
            if (debugMode) e.printStackTrace();
        }
    }
    
    /**
     * Invocation handler for the dynamic TooltipProvider proxy
     */
    private static class TooltipProviderInvocationHandler implements InvocationHandler {
        
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            
            if (debugMode) {
                logger.info("TooltipProvider method called: " + methodName);
            }
            
            switch (methodName) {
                case "getProviderId":
                    logger.info("[PROVIDER] getProviderId called");
                    return PROVIDER_ID;
                    
                case "getPriority":
                    logger.info("[PROVIDER] getPriority called");
                    return 100; // Default priority
                    
                case "getTooltipData":
                    // getTooltipData(String itemId, String metadata)
                    // or getTooltipData(String itemId, String metadata, String locale)
                    logger.info("[PROVIDER] getTooltipData called with itemId=" + (args != null && args.length > 0 ? args[0] : "null") + ", metadata=" + (args != null && args.length > 1 ? args[1] : "null"));
                    
                    if (args == null || args.length < 2) {
                        return null;
                    }
                    
                    String itemId = (String) args[0];
                    String metadata = (String) args[1];
                    // String locale = args.length > 2 ? (String) args[2] : "en-US";
                    
                    return getTooltipDataForItem(itemId, metadata);
                    
                default:
                    return null;
            }
        }
    }
    
    /**
     * Get TooltipData for an item using reflection
     */
    private static Object getTooltipDataForItem(String itemId, String metadata) {
        // Parse the JSON metadata to extract reforge level
        int reforgeLevel = extractReforgeLevel(metadata);
        String baseItemId = extractBaseItemId(metadata);
        String displayName = extractDisplayName(metadata);
        
        if (reforgeLevel <= 0) {
            return null;
        }
        
        // Determine if it's armor or weapon based on base item ID
        boolean isArmor = baseItemId != null && baseItemId.startsWith("Armor_");
        
        String upgradeName = isArmor ? getArmorUpgradeName(reforgeLevel) : getUpgradeName(reforgeLevel);
        double multiplier = isArmor ? getDefenseMultiplier(reforgeLevel) : getDamageMultiplier(reforgeLevel);
        int percentBonus = (int) ((multiplier - 1.0) * 100);
        
        ReforgeLevel reforgeLevelEnum = ReforgeLevel.fromLevel(reforgeLevel);
        String statType = isArmor ? "defense" : "damage";
        // Format: "Refine Grade: Legendary (+XX% damage)" - name already has the +level
        String line = COLOR_WHITE + "Refine Grade: " + reforgeLevelEnum.getColor() + upgradeName 
                + " (" + COLOR_GREEN + "+" + percentBonus + "% " + statType + COLOR_WHITE + ")";
        
        try {
            // Try to create TooltipData using builder pattern
            // TooltipData.builder().hashInput(...).nameOverride(...).addLine(...).build()
            
            // Find TooltipData class
            Class<?> tooltipDataClass;
            try {
                tooltipDataClass = Class.forName("org.herolias.tooltips.api.TooltipData");
            } catch (ClassNotFoundException e) {
                try {
                    tooltipDataClass = Class.forName("herolias.tooltips.api.TooltipData");
                } catch (ClassNotFoundException e2) {
                    if (debugMode) logger.warn("TooltipData class not found");
                    return null;
                }
            }
            
            // Find builder() method
            Method builderMethod = tooltipDataClass.getMethod("builder");
            Object builder = builderMethod.invoke(null);
            
            // Set hashInput - required for caching - use the metadata as-is
            String hashInput = itemId + ":" + (metadata != null ? metadata.hashCode() : "none");
            Method hashInputMethod = builder.getClass().getMethod("hashInput", String.class);
            hashInputMethod.invoke(builder, hashInput);
            
            // Set name override if we have a display name
            if (displayName != null && !displayName.isEmpty()) {
                try {
                    Method nameOverrideMethod = builder.getClass().getMethod("nameOverride", String.class);
                    nameOverrideMethod.invoke(builder, displayName);
                } catch (NoSuchMethodException e) {
                    // Name override not available
                }
            }
            
            // Add the line
            Method addLineMethod = builder.getClass().getMethod("addLine", String.class);
            addLineMethod.invoke(builder, line);
            
            // Build the result
            Method buildMethod = builder.getClass().getMethod("build");
            Object result = buildMethod.invoke(builder);
            
            logger.info("[TOOLTIP] Created tooltip for " + itemId + " level " + reforgeLevel + ": " + line);
            
            return result;
            
        } catch (Exception e) {
            logger.error("Error creating tooltip data: " + e.getMessage());
            if (debugMode) e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Extract reforge level from JSON metadata
     */
    private static int extractReforgeLevel(String metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return 0;
        }
        
        // Look for "SocketReforge.Refinement.Level": X pattern
        try {
            // Simple string search for the level value
            String searchKey = "SocketReforge.Refinement.Level";
            int keyIndex = metadata.indexOf(searchKey);
            if (keyIndex < 0) {
                // Try alternate key
                searchKey = "Refinement.Level";
                keyIndex = metadata.indexOf(searchKey);
            }
            if (keyIndex < 0) {
                return 0;
            }
            
            // Find the colon after the key
            int colonIndex = metadata.indexOf(":", keyIndex);
            if (colonIndex < 0) {
                return 0;
            }
            
            // Find the number after the colon
            int numberStart = colonIndex + 1;
            while (numberStart < metadata.length() && 
                   (metadata.charAt(numberStart) == ' ' || metadata.charAt(numberStart) == '"')) {
                numberStart++;
            }
            
            // Parse the number
            int numberEnd = numberStart;
            while (numberEnd < metadata.length() && 
                   Character.isDigit(metadata.charAt(numberEnd))) {
                numberEnd++;
            }
            
            if (numberEnd > numberStart) {
                return Integer.parseInt(metadata.substring(numberStart, numberEnd));
            }
        } catch (Exception e) {
            if (debugMode) {
                logger.warn("Error parsing metadata: " + e.getMessage());
            }
        }
        
        return 0;
    }
    
    /**
     * Extract base item ID from JSON metadata
     */
    private static String extractBaseItemId(String metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        
        try {
            String searchKey = "SocketReforge.Refinement.BaseItemId";
            int keyIndex = metadata.indexOf(searchKey);
            if (keyIndex < 0) return null;
            
            int colonIndex = metadata.indexOf(":", keyIndex);
            if (colonIndex < 0) return null;
            
            // Find the opening quote
            int quoteStart = metadata.indexOf("\"", colonIndex);
            if (quoteStart < 0) return null;
            
            // Find the closing quote
            int quoteEnd = metadata.indexOf("\"", quoteStart + 1);
            if (quoteEnd < 0) return null;
            
            return metadata.substring(quoteStart + 1, quoteEnd);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Extract display name from JSON metadata
     */
    private static String extractDisplayName(String metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        
        try {
            String searchKey = "SocketReforge.Refinement.DisplayName";
            int keyIndex = metadata.indexOf(searchKey);
            if (keyIndex < 0) return null;
            
            int colonIndex = metadata.indexOf(":", keyIndex);
            if (colonIndex < 0) return null;
            
            // Find the opening quote
            int quoteStart = metadata.indexOf("\"", colonIndex);
            if (quoteStart < 0) return null;
            
            // Find the closing quote
            int quoteEnd = metadata.indexOf("\"", quoteStart + 1);
            if (quoteEnd < 0) return null;
            
            return metadata.substring(quoteStart + 1, quoteEnd);
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== Helper Methods ====================
    
    private static boolean isValidItemId(String itemId) {
        return itemId != null && !itemId.trim().isEmpty();
    }
    
    private static String getUpgradeName(int level) {
        switch (level) {
            case 1: return "Sharp";
            case 2: return "Deadly";
            case 3: return "Legendary";
            default: return "Ancient";
        }
    }
    
    private static String getArmorUpgradeName(int level) {
        switch (level) {
            case 1: return "Protective";
            case 2: return "Resistant";
            case 3: return "Fortified";
            default: return "Ancient";
        }
    }
    
    private static double getDamageMultiplier(int level) {
        switch (level) {
            case 1: return 1.10;
            case 2: return 1.25;
            case 3: return 1.50;
            default: return 1.0;
        }
    }
    
    private static double getDefenseMultiplier(int level) {
        switch (level) {
            case 1: return 1.10;
            case 2: return 1.20;
            case 3: return 1.35;
            default: return 1.0;
        }
    }

    // ==================== Legacy Compatibility Methods ====================
    
    /**
     * Legacy method - use registerReforgeTooltip instead
     */
    public static void addReforgeTooltip(String baseItemId, String upgradeName, int level, 
            int percentBonus, boolean isArmor) {
        if (!isAvailable) return;
        registerReforgeTooltip(baseItemId, level, isArmor);
    }
    
    /**
     * Legacy method - use registerSocketTooltip instead
     */
    public static void addSocketTooltip(String baseItemId, int socketCount, int filledSockets) {
        if (!isAvailable) return;
        // For legacy, use empty metadata
        registerSocketTooltip(baseItemId, "sockets:" + socketCount, socketCount, filledSockets);
    }
    
    /**
     * Legacy method - use registerEssenceTooltip instead
     */
    public static void addEssenceTooltip(String baseItemId, String essenceName, String effectLine) {
        if (!isAvailable) return;
        registerEssenceTooltip(baseItemId, "essence:" + essenceName, essenceName, effectLine);
    }
    
    /**
     * Legacy method
     */
    public static void addStatTooltip(String baseItemId, String statName, double boostValue) {
        // Not supported in provider approach without metadata
    }
    
    /**
     * Legacy method - no longer used
     */
    public static void addGlobalLine(String baseItemId, String line) {
        // No-op - use registerTooltip instead
    }
    
    /**
     * Legacy method - no longer used
     */
    public static void replaceGlobalTooltip(String baseItemId, String... lines) {
        // No-op - use registerTooltip instead
    }
    
    /**
     * Legacy method - no longer used
     */
    public static void refreshAllPlayers() {
        // Provider approach handles this automatically
    }
}
