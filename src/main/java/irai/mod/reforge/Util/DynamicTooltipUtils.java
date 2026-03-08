package irai.mod.reforge.Util;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import irai.mod.reforge.Common.EquipmentDamageTooltipMath;
import irai.mod.reforge.Common.ItemTypeUtils;
import irai.mod.reforge.Socket.Essence;
import irai.mod.reforge.Socket.SocketData;
import irai.mod.reforge.Socket.SocketManager;

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
    private static final String COLOR_RED = "<color is=\"#ff0000\">";
    private static final String COLOR_YELLOW = "<color is=\"#FFFF55\">";
    private static final String COLOR_ORANGE = "<color is=\"#FFAA00\">";
    
    // Dark gray for empty sockets
    private static final String COLOR_DARK_GRAY = "<color is=\"#555555\">";
    
    // ASCII socket symbols - always work in any font
    private static final String SOCKET_FILLED = "[o]";
    private static final String SOCKET_GREATER = "[0]";
    private static final String SOCKET_EMPTY = "[ ]";
    private static final String SOCKET_LOCKED = "[x]";

    // Parts metadata keys
    private static final String META_PARTS_PROFILE_TYPE = "SocketReforge.Parts.ProfileType";
    private static final String META_PARTS_WEAPON_TYPE = "SocketReforge.Parts.WeaponType";
    private static final String META_PART1_TIER = "SocketReforge.Parts.Part1Tier";
    private static final String META_PART2_TIER = "SocketReforge.Parts.Part2Tier";
    private static final String META_PART3_TIER = "SocketReforge.Parts.Part3Tier";
    private static final String META_PARTS_DAMAGE_MULTIPLIER = "SocketReforge.Parts.DamageMultiplier";
    private static final String META_ESSENCE_EFFECT_LINES = "SocketReforge.Essence.EffectLines";
    private static final String BLOOD_PACT_PREFIX = "Blood Pact ";
    private static final String META_RESONANCE_NAME = "SocketReforge.Resonance.Name";
    private static final String META_RESONANCE_EFFECT = "SocketReforge.Resonance.Effect";
    private static final String META_RESONANCE_QUALITY = "SocketReforge.Resonance.Quality";
    
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
     * Ensures the dynamic tooltip provider is registered even when no tooltip lines
     * have been pre-cached this session.
     */
    public static void ensureProviderRegistered() {
        if (!isAvailable || tooltipApi == null) {
            return;
        }
        if (!providerRegistered) {
            registerTooltipProvider();
        }
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
     * Register the equipment tooltip provider for showing metadata on inventory items
     */
    public static void registerEquipmentTooltipProvider() {
        if (!isAvailable || tooltipApi == null) {
            return;
        }
        
        try {
            // Get the TooltipProvider class
            Class<?> providerClass = Class.forName("org.herolias.tooltips.api.TooltipProvider");
            Class<?> tooltipDataClass = Class.forName("org.herolias.tooltips.api.TooltipData");
            
            // Create instance of our provider using reflection
            Object provider = new EquipmentTooltipProvider();
            
            // Get the registerProvider method
            Method registerMethod = tooltipApi.getClass().getMethod("registerProvider", providerClass);
            registerMethod.invoke(tooltipApi, provider);
            
            logger.info("Equipment tooltip provider registered successfully");
        } catch (Exception e) {
            logger.warn("Failed to register equipment tooltip provider: " + e.getMessage());
        }
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
     * Register socket tooltip with placeholder display
     * Shows all slots as ◆ representing max socket capacity
     */
    public static void registerSocketTooltip(String baseItemId, String metadata, int socketCount, int filledSockets) {
        registerSocketTooltip(baseItemId, metadata, socketCount, filledSockets, 0);
    }
    
    /**
     * Register socket tooltip - shows total max sockets as ◆
     * Filled sockets are shown as ◆, locked as ✕
     */
    public static void registerSocketTooltip(String baseItemId, String metadata, int socketCount, int filledSockets, int lockedSockets) {
        if (!isAvailable) return;
        
        // Build socket display based on current socket count: ◆◆ format
        StringBuilder socketDisplay = new StringBuilder();
        
        // Show only current socket slots as ◆ (filled, white color)
        for (int i = 0; i < socketCount; i++) {
            socketDisplay.append(COLOR_WHITE + SOCKET_FILLED);
        }
        
        String line = COLOR_CYAN + "Sockets: " + COLOR_WHITE + socketDisplay.toString();
        
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
     * Register socket tooltip with color-coded essence display.
     * Uses simple text characters that Hytale can render.
     * @param baseItemId The item ID
     * @param metadata The metadata (can be null)
     * @param socketCount Total max sockets
     * @param socketColors Array of color codes for each socket (null for empty)
     * @param brokenSockets Array indicating which sockets are broken (null means no broken sockets)
     */
    public static void registerColoredSocketTooltip(String baseItemId, String metadata, int socketCount, String[] socketColors, boolean[] brokenSockets) {
        if (!isAvailable) return;
        
        StringBuilder socketDisplay = new StringBuilder();
        
        for (int i = 0; i < socketCount; i++) {
            // Check if this socket is broken
            if (brokenSockets != null && i < brokenSockets.length && brokenSockets[i]) {
                // Broken socket - show as ✕ in red
                socketDisplay.append("<color is=\"#FF5555\">" + SOCKET_LOCKED + "</color>");
            } else if (socketColors != null && i < socketColors.length && socketColors[i] != null) {
                // Filled socket with color - use simple asterisk
                socketDisplay.append(socketColors[i]).append("*");
            } else {
                // Empty socket - use lowercase o
                socketDisplay.append("<color is=\"#555555\">o</color>");
            }
        }
        
        String line = "<color is=\"#55FFFF\">Sockets: </color>" + socketDisplay.toString();
        
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
        
        // Parse socket data from metadata
        int socketMax = extractSocketMax(metadata);
        int socketFilled = extractSocketFilled(metadata);

        // Parse modular parts metadata
        String partsProfileType = extractStringValue(metadata, META_PARTS_PROFILE_TYPE);
        if (partsProfileType == null || partsProfileType.isBlank()) {
            partsProfileType = extractStringValue(metadata, META_PARTS_WEAPON_TYPE);
        }
        int part1Tier = extractIntValue(metadata, META_PART1_TIER);
        int part2Tier = extractIntValue(metadata, META_PART2_TIER);
        int part3Tier = extractIntValue(metadata, META_PART3_TIER);
        double partsDamageMultiplier = extractDoubleValue(metadata, META_PARTS_DAMAGE_MULTIPLIER, 1.0);
        String resonanceName = extractStringValue(metadata, META_RESONANCE_NAME);
        String resonanceEffect = extractStringValue(metadata, META_RESONANCE_EFFECT);
        String resonanceQuality = extractStringValue(metadata, META_RESONANCE_QUALITY);
        boolean hasResonance = resonanceName != null && !resonanceName.isBlank();
        String baseItemId = extractBaseItemId(metadata);
        String effectiveItemId = baseItemId != null && !baseItemId.isBlank() ? baseItemId : itemId;
        boolean isEquipmentItem = ItemTypeUtils.isEquipmentItemId(effectiveItemId);
        
        // If no supported metadata is present, return null
        if (reforgeLevel <= 0 && socketMax <= 0 && socketFilled <= 0 && partsProfileType == null && !hasResonance && !isEquipmentItem) {
            return null;
        }
        
        List<String> tooltipLines = new ArrayList<>();
        String displayName = extractDisplayName(metadata);
        boolean shouldBloodPrefix = shouldPrefixBloodPact(itemId, baseItemId, metadata);
        if ((shouldBloodPrefix || hasResonance) && (displayName == null || displayName.isEmpty())) {
            displayName = buildFallbackDisplayName(baseItemId, itemId);
        }
        if (displayName != null && !displayName.isEmpty()
                && shouldBloodPrefix
                && !displayName.startsWith(BLOOD_PACT_PREFIX)) {
            displayName = BLOOD_PACT_PREFIX + displayName;
        }
        if (displayName != null && !displayName.isEmpty()
                && hasResonance
                && !displayName.startsWith(resonanceName + " ")) {
            displayName = resonanceName + " " + displayName;
        }
        
        boolean isArmorItem = isArmorType(baseItemId, itemId);
        String[] socketEntries = extractSocketEntries(metadata);
        SocketData parsedSocketData = buildSocketDataFromMetadata(socketMax, socketFilled, socketEntries);
        boolean hasRefineOrSocketedEssence = reforgeLevel > 0 || hasSocketedEssence(socketEntries);

        if (isEquipmentItem && !isArmorItem) {
            EquipmentDamageTooltipMath.StatSummary summary = EquipmentDamageTooltipMath.computeWeaponDamageSummary(
                    effectiveItemId,
                    reforgeLevel,
                    parsedSocketData,
                    partsDamageMultiplier
            );
            if (hasRefineOrSocketedEssence) {
                tooltipLines.add(
                        COLOR_WHITE + "Damage : "
                                + COLOR_RED + formatDamageValue(summary.getBaseValue())
                                + COLOR_WHITE + " -> "
                                + COLOR_GREEN + formatDamageValue(summary.getBuffedValue())
                );
            } else {
                tooltipLines.add(
                        COLOR_WHITE + "Damage : "
                                + COLOR_GREEN + formatDamageValue(summary.getBaseValue())
                );
            }
        }

        // Add reforge line if present
        if (reforgeLevel > 0) {
            String upgradeName = isArmorItem ? getArmorUpgradeName(reforgeLevel) : getUpgradeName(reforgeLevel);
            double multiplier = isArmorItem ? getDefenseMultiplier(reforgeLevel) : getDamageMultiplier(reforgeLevel);
            int percentBonus = (int) ((multiplier - 1.0) * 100);
            
            ReforgeLevel reforgeLevelEnum = ReforgeLevel.fromLevel(reforgeLevel);
            String statType = isArmorItem ? "defense" : "damage";
            String line = COLOR_WHITE + "Refine Grade: " + reforgeLevelEnum.getColor() + upgradeName 
                    + " (" + COLOR_GREEN + "+" + percentBonus + "% " + statType + COLOR_WHITE + ")";
            tooltipLines.add(line);
        }
        
        // Add socket line if present
        if (socketMax > 0 || socketFilled > 0) {
            // Prefer values-array length since it reflects the actual serialized slot count.
            int actualSocketCount = socketFilled > 0 ? socketFilled : socketMax;
            
            StringBuilder socketDisplay = new StringBuilder();
            if (socketEntries.length == 0 && actualSocketCount > 0) {
                for (int i = 0; i < actualSocketCount; i++) {
                    socketDisplay.append(COLOR_DARK_GRAY).append(SOCKET_EMPTY);
                }
            } else {
                for (String entry : socketEntries) {
                    String trimmed = normalizeSocketEntry(entry);
                    if (trimmed.equals("x")) {
                        socketDisplay.append(COLOR_RED).append(SOCKET_LOCKED);
                    } else if (!trimmed.isEmpty()) {
                        // Apply color based on essence type
                        String symbol = isGreaterEssenceEntry(trimmed) ? SOCKET_GREATER : SOCKET_FILLED;
                        socketDisplay.append(getEssenceTooltipColor(trimmed)).append(symbol).append("</color>");
                    } else {
                        socketDisplay.append(COLOR_DARK_GRAY).append(SOCKET_EMPTY);
                    }
                }
            }
            String socketLine = COLOR_CYAN + "Sockets: " + COLOR_WHITE + socketDisplay.toString();
            tooltipLines.add(socketLine);
        }

        if (hasResonance) {
            String shownEffect = resonanceEffect != null && !resonanceEffect.isBlank() ? resonanceEffect : resonanceName;
            tooltipLines.add(COLOR_ORANGE + "Resonance: " + COLOR_WHITE + shownEffect);
        }
        if (resonanceQuality != null && !resonanceQuality.isBlank()) {
            tooltipLines.add(COLOR_YELLOW + "Quality: " + COLOR_WHITE + resonanceQuality);
        }

        // Add modular parts line if present
        String partsLine = buildPartsTooltipLine(partsProfileType, part1Tier, part2Tier, part3Tier);
        if (partsLine != null) {
            tooltipLines.add(partsLine);
        }
        
        // Add essence effects from the current socket entries so tooltip math always
        // matches live gameplay math.
        java.util.Map<Essence.Type, Integer> liveTiers = SocketManager.calculateConsecutiveTiers(parsedSocketData);
        if (!liveTiers.isEmpty()) {
            for (java.util.Map.Entry<Essence.Type, Integer> entry : liveTiers.entrySet()) {
                Essence.Type type = entry.getKey();
                if (type == null) {
                    continue;
                }
                int tier = entry.getValue() == null ? 0 : entry.getValue();
                if (tier <= 0) {
                    continue;
                }
                String effectType = type.name();
                String color = getEssenceTooltipColor("Essence_" + effectType);
                String effectDesc = SocketManager.describeEssenceEffect(type, tier, !isArmorItem, parsedSocketData);
                String essenceLine = color + effectType + " T" + tier + "</color> " + COLOR_GRAY + effectDesc;
                tooltipLines.add(essenceLine);
            }
        }
        
        // If no lines, return null
        if (tooltipLines.isEmpty()) {
            return null;
        }
        
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
            
            // Add all tooltip lines
            Method addLineMethod = builder.getClass().getMethod("addLine", String.class);
            for (String tooltipLine : tooltipLines) {
                addLineMethod.invoke(builder, tooltipLine);
            }
            
            // Build the result
            Method buildMethod = builder.getClass().getMethod("build");
            Object result = buildMethod.invoke(builder);
            
            logger.info("[TOOLTIP] Created tooltip for " + itemId + " with " + tooltipLines.size() + " lines");
            
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
     * Extract socket max count from JSON metadata
     */
    private static int extractSocketMax(String metadata) {
        return extractFirstIntegerNearKey(metadata, "SocketReforge.Socket.Max");
    }
    
    /**
     * Extract socket count from Values array in JSON metadata
     */
    private static int extractSocketFilled(String metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return 0;
        }
        
        try {
            String searchKey = "SocketReforge.Socket.Values";
            int keyIndex = metadata.indexOf(searchKey);
            if (keyIndex < 0) {
                return 0;
            }
            
            // Find the array bracket
            int bracketStart = metadata.indexOf("[", keyIndex);
            int bracketEnd = metadata.indexOf("]", bracketStart);
            if (bracketStart < 0 || bracketEnd < 0) {
                return 0;
            }
            
            String arrayContent = metadata.substring(bracketStart + 1, bracketEnd);
            // Count entries (both empty and non-empty)
            if (arrayContent.trim().isEmpty()) {
                return 0;
            }
            String[] entries = arrayContent.split(",");
            return entries.length;
        } catch (Exception e) {
            // Ignore
        }
        
        return 0;
    }

    private static String[] extractSocketEntries(String metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return new String[0];
        }
        try {
            String searchKey = "SocketReforge.Socket.Values";
            int keyIndex = metadata.indexOf(searchKey);
            if (keyIndex < 0) {
                return new String[0];
            }
            int bracketStart = metadata.indexOf("[", keyIndex);
            int bracketEnd = metadata.indexOf("]", bracketStart);
            if (bracketStart < 0 || bracketEnd < 0) {
                return new String[0];
            }
            String arrayContent = metadata.substring(bracketStart + 1, bracketEnd);
            if (arrayContent.trim().isEmpty()) {
                return new String[0];
            }
            return arrayContent.split(",");
        } catch (Exception e) {
            return new String[0];
        }
    }

    private static String normalizeSocketEntry(String entry) {
        if (entry == null) {
            return "";
        }
        return entry.trim().replace("\"", "");
    }

    private static boolean isGreaterEssenceEntry(String normalizedSocketEntry) {
        if (normalizedSocketEntry == null || normalizedSocketEntry.isBlank()) {
            return false;
        }
        return SocketManager.isGreaterEssenceId(normalizedSocketEntry);
    }

    private static boolean hasSocketedEssence(String[] socketEntries) {
        if (socketEntries == null || socketEntries.length == 0) {
            return false;
        }
        for (String entry : socketEntries) {
            String normalized = normalizeSocketEntry(entry);
            if (!normalized.isEmpty() && !"x".equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private static SocketData buildSocketDataFromMetadata(int socketMax, int socketFilled, String[] socketEntries) {
        int resolvedMax = Math.max(0, socketMax);
        int entryCount = socketEntries == null ? 0 : socketEntries.length;
        int maxSockets = Math.max(resolvedMax, Math.max(socketFilled, entryCount));
        SocketData socketData = new SocketData(maxSockets);
        if (entryCount == 0) {
            return socketData;
        }

        for (int i = 0; i < entryCount; i++) {
            socketData.addSocket();
            String entry = normalizeSocketEntry(socketEntries[i]);
            if (entry.isEmpty()) {
                continue;
            }
            if ("x".equals(entry)) {
                socketData.getSockets().get(i).setBroken(true);
                continue;
            }
            socketData.setEssenceAt(i, entry);
        }
        return socketData;
    }

    /**
     * Extracts the first positive integer near a metadata key.
     * Works with both plain values and BSON extended JSON wrappers.
     */
    private static int extractFirstIntegerNearKey(String metadata, String key) {
        if (metadata == null || metadata.isEmpty() || key == null || key.isEmpty()) {
            return 0;
        }
        int keyIndex = metadata.indexOf(key);
        if (keyIndex < 0) {
            return 0;
        }

        int start = Math.min(metadata.length(), keyIndex + key.length());
        int end = Math.min(metadata.length(), start + 96);
        for (int i = start; i < end; i++) {
            char c = metadata.charAt(i);
            if (!Character.isDigit(c)) {
                continue;
            }
            int numberEnd = i + 1;
            while (numberEnd < end && Character.isDigit(metadata.charAt(numberEnd))) {
                numberEnd++;
            }
            try {
                return Integer.parseInt(metadata.substring(i, numberEnd));
            } catch (Exception ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static String[] extractEssenceEffectLines(String metadata) {
        return extractStringArray(metadata, META_ESSENCE_EFFECT_LINES);
    }
    
    /**
     * Extract essence effects from JSON metadata
     */
    private static String[] extractEssenceEffects(String metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        
        try {
            String searchKey = "SocketReforge.Essence.Effects";
            int keyIndex = metadata.indexOf(searchKey);
            if (keyIndex < 0) {
                return null;
            }
            
            int bracketStart = metadata.indexOf("[", keyIndex);
            int bracketEnd = metadata.indexOf("]", bracketStart);
            if (bracketStart < 0 || bracketEnd < 0) {
                return null;
            }
            
            String arrayContent = metadata.substring(bracketStart + 1, bracketEnd);
            if (arrayContent.trim().isEmpty()) {
                return null;
            }
            
            String[] entries = arrayContent.split(",");
            for (int i = 0; i < entries.length; i++) {
                entries[i] = entries[i].trim().replace("\"", "");
            }
            return entries;
        } catch (Exception e) {
            // Ignore
        }
        
        return null;
    }
    
    /**
     * Extract essence tiers from JSON metadata
     */
    private static String[] extractEssenceTiers(String metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        
        try {
            String searchKey = "SocketReforge.Essence.TierMap";
            int keyIndex = metadata.indexOf(searchKey);
            if (keyIndex < 0) {
                return null;
            }
            
            int bracketStart = metadata.indexOf("[", keyIndex);
            int bracketEnd = metadata.indexOf("]", bracketStart);
            if (bracketStart < 0 || bracketEnd < 0) {
                return null;
            }
            
            String arrayContent = metadata.substring(bracketStart + 1, bracketEnd);
            if (arrayContent.trim().isEmpty()) {
                return null;
            }
            
            String[] entries = arrayContent.split(",");
            for (int i = 0; i < entries.length; i++) {
                entries[i] = entries[i].trim().replace("\"", "");
            }
            return entries;
        } catch (Exception e) {
            // Ignore
        }
        
        return null;
    }

    private static String buildFallbackDisplayName(String baseItemId, String itemId) {
        String source = baseItemId;
        if (source == null || source.isBlank()) {
            source = itemId;
        }
        if (source == null || source.isBlank()) {
            return null;
        }

        String normalizedId = source.trim();
        String[] keyCandidates = {
                "items." + normalizedId + ".name",
                "server.items." + normalizedId + ".name",
                "wanmine.items." + normalizedId + ".name"
        };

        for (String key : keyCandidates) {
            String localized = LangLoader.getTranslation(key);
            if (localized != null && !localized.isBlank()) {
                return localized;
            }
        }

        // Fallback through NameResolver path to keep behavior close to refinement resolution.
        String resolved = NameResolver.resolveTranslationKey("items." + normalizedId + ".name");
        if (resolved != null && !resolved.isBlank()) {
            return resolved;
        }

        String replaced = normalizedId.replace('_', ' ').trim();
        if (!replaced.isEmpty()) {
            return replaced;
        }
        return null;
    }

    private static boolean shouldPrefixBloodPact(String itemId, String baseItemId, String metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return false;
        }
        String base = baseItemId;
        if (base == null || base.isBlank()) {
            base = itemId;
        }
        if (!ItemTypeUtils.isWeaponItemId(base)) {
            return false;
        }

        String[] effects = extractEssenceEffects(metadata);
        String[] tiers = extractEssenceTiers(metadata);
        if (effects == null || tiers == null) {
            return false;
        }
        int count = Math.min(effects.length, tiers.length);
        for (int i = 0; i < count; i++) {
            String effect = effects[i];
            if (effect == null || !"VOID".equalsIgnoreCase(effect.trim())) {
                continue;
            }
            String tierRaw = tiers[i];
            if (tierRaw == null) {
                continue;
            }
            try {
                if (Integer.parseInt(tierRaw.trim()) >= 5) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return false;
    }

    /**
     * Get effect description for an essence type and tier
     */
    private static String getEssenceEffectDescription(String effectType,
                                                      int tier,
                                                      String itemId,
                                                      String metadata,
                                                      SocketData socketData) {
        try {
            Essence.Type type = Essence.Type.valueOf(effectType.toUpperCase(java.util.Locale.ROOT));
            boolean isArmor = ItemTypeUtils.isArmorItemId(itemId);
            SocketData safeSocketData = socketData != null ? socketData : new SocketData(0);
            return SocketManager.describeEssenceEffect(type, tier, !isArmor, safeSocketData);
        } catch (Exception e) {
            return "Error";
        }
    }

    private static String[] extractStringArray(String metadata, String searchKey) {
        try {
            int keyIndex = metadata.indexOf(searchKey);
            if (keyIndex < 0) return null;
            int bracketStart = metadata.indexOf("[", keyIndex);
            int bracketEnd = metadata.indexOf("]", bracketStart);
            if (bracketStart < 0 || bracketEnd < 0) return null;
            String arrayContent = metadata.substring(bracketStart + 1, bracketEnd);
            if (arrayContent.trim().isEmpty()) return new String[0];
            String[] entries = arrayContent.split(",");
            for (int i = 0; i < entries.length; i++) {
                entries[i] = entries[i].trim().replace("\"", "");
            }
            return entries;
        } catch (Exception e) {
            return null;
        }
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

    private static String extractStringValue(String metadata, String searchKey) {
        if (metadata == null || metadata.isEmpty() || searchKey == null || searchKey.isBlank()) {
            return null;
        }
        try {
            int keyIndex = metadata.indexOf(searchKey);
            if (keyIndex < 0) return null;
            int colonIndex = metadata.indexOf(":", keyIndex);
            if (colonIndex < 0) return null;
            int quoteStart = metadata.indexOf("\"", colonIndex);
            if (quoteStart < 0) return null;
            int quoteEnd = metadata.indexOf("\"", quoteStart + 1);
            if (quoteEnd < 0) return null;
            String value = metadata.substring(quoteStart + 1, quoteEnd).trim();
            return value.isEmpty() ? null : value;
        } catch (Exception e) {
            return null;
        }
    }

    private static int extractIntValue(String metadata, String searchKey) {
        if (metadata == null || metadata.isEmpty() || searchKey == null || searchKey.isBlank()) {
            return 0;
        }
        try {
            int keyIndex = metadata.indexOf(searchKey);
            if (keyIndex < 0) return 0;
            int colonIndex = metadata.indexOf(":", keyIndex);
            if (colonIndex < 0) return 0;
            int numberStart = colonIndex + 1;
            while (numberStart < metadata.length() &&
                    (metadata.charAt(numberStart) == ' ' || metadata.charAt(numberStart) == '"')) {
                numberStart++;
            }
            int numberEnd = numberStart;
            if (numberEnd < metadata.length() && metadata.charAt(numberEnd) == '-') {
                numberEnd++;
            }
            while (numberEnd < metadata.length() && Character.isDigit(metadata.charAt(numberEnd))) {
                numberEnd++;
            }
            if (numberEnd > numberStart) {
                return Integer.parseInt(metadata.substring(numberStart, numberEnd));
            }
        } catch (Exception e) {
            return 0;
        }
        return 0;
    }

    private static double extractDoubleValue(String metadata, String searchKey, double defaultValue) {
        if (metadata == null || metadata.isEmpty() || searchKey == null || searchKey.isBlank()) {
            return defaultValue;
        }
        try {
            int keyIndex = metadata.indexOf(searchKey);
            if (keyIndex < 0) return defaultValue;
            int colonIndex = metadata.indexOf(":", keyIndex);
            if (colonIndex < 0) return defaultValue;
            int numberStart = colonIndex + 1;
            while (numberStart < metadata.length()
                    && (metadata.charAt(numberStart) == ' ' || metadata.charAt(numberStart) == '"')) {
                numberStart++;
            }
            int numberEnd = numberStart;
            if (numberEnd < metadata.length() && metadata.charAt(numberEnd) == '-') {
                numberEnd++;
            }
            while (numberEnd < metadata.length()) {
                char c = metadata.charAt(numberEnd);
                if (Character.isDigit(c) || c == '.') {
                    numberEnd++;
                    continue;
                }
                break;
            }
            if (numberEnd > numberStart) {
                return Double.parseDouble(metadata.substring(numberStart, numberEnd));
            }
        } catch (Exception ignored) {
            // Keep default
        }
        return defaultValue;
    }

    private static String formatDamageValue(double value) {
        double safe = Math.max(0.0, value);
        long rounded = Math.round(safe);
        if (Math.abs(safe - rounded) < 0.05) {
            return Long.toString(rounded);
        }
        return String.format(java.util.Locale.ROOT, "%.1f", safe);
    }

    private static String buildPartsTooltipLine(String profileType, int t1, int t2, int t3) {
        if (profileType == null || profileType.isBlank()) {
            return null;
        }

        String normalized = profileType.trim().toUpperCase(java.util.Locale.ROOT);
        String[] glyphs = getPartGlyphs(normalized);

        StringBuilder sb = new StringBuilder();
        sb.append(COLOR_YELLOW).append("Parts: ").append(COLOR_WHITE)
          .append(getTierColorTag(t1)).append(glyphs[0])
          .append(COLOR_WHITE).append("")
          .append(getTierColorTag(t2)).append(glyphs[1])
          .append(COLOR_WHITE).append("")
          .append(getTierColorTag(t3)).append(glyphs[2]);

        return sb.toString();
    }

    private static String getTierColorTag(int tier) {
        switch (tier) {
            case 1:
                return "<color is=\"#6B7280\">";
            case 2:
                return "<color is=\"#22C55E\">";
            case 3:
                return "<color is=\"#3B82F6\">";
            case 4:
                return "<color is=\"#A855F7\">";
            case 5:
                return "<color is=\"#F59E0B\">";
            default:
                return COLOR_DARK_GRAY;
        }
    }

    private static String[] getPartGlyphs(String profileType) {
        switch (profileType) {
            case "SWORD":
                return new String[] {"o=", "|", "===>"};
            case "AXE":
            case "HATCHET":
                return new String[] {"o=", "===", "[==]"};
            case "MACE":
                return new String[] {"o=", "===", "[*]"};
            case "DAGGER":
                return new String[] {"o=", ":-", "==>"};
            case "BOW":
                return new String[] {")=", "==", "=>"};
            case "STAFF":
                return new String[] {"o=", "====", "Q"};
            case "PICKAXE":
                return new String[] {"o=", "||", "T>"};
            case "SHOVEL":
                return new String[] {"o=", "||", "[_]"}; 
            case "HOE":
                return new String[] {"o=", "||", "_|"};
            case "SICKLE":
                return new String[] {"o=", "o", ")>"};
            case "SHEARS":
                return new String[] {"o", "X", "o"};
            case "MULTITOOL":
                return new String[] {"o=", "<>", "[#]"};
            default:
                return new String[] {"o=", "|", "=="};
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
            case 2: return 1.15;
            case 3: return 1.25;
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
    
    /**
     * Get color based on max sockets (health indicator)
     */
    private static String getSocketHealthColor(int maxSockets) {
        if (maxSockets >= 4) return COLOR_GREEN;    // Healthy
        if (maxSockets == 3) return COLOR_YELLOW;   // Warning
        if (maxSockets == 2) return COLOR_ORANGE;  // Danger
        return COLOR_RED;                           // Critical
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
     * Triggers DynamicTooltipsLib to refresh tooltips for all connected players.
     */
    public static void refreshAllPlayers() {
        if (!isAvailable || tooltipApi == null) {
            return;
        }

        try {
            if (refreshAllPlayersMethod == null) {
                refreshAllPlayersMethod = tooltipApi.getClass().getMethod("refreshAllPlayers");
            }
            refreshAllPlayersMethod.setAccessible(true);
            refreshAllPlayersMethod.invoke(tooltipApi);
        } catch (NoSuchMethodException e) {
            if (debugMode) {
                logger.warn("refreshAllPlayers method not found in loaded tooltip API");
            }
        } catch (Exception e) {
            if (debugMode) {
                logger.warn("Failed to refresh tooltips: " + e.getMessage());
            }
        }
    }

    private static boolean isArmorType(String baseItemId, String itemId) {
        if (ItemTypeUtils.isArmorItemId(baseItemId)) {
            return true;
        }
        return ItemTypeUtils.isArmorItemId(itemId);
    }
    
    /**
     * Gets the color for an essence type in tooltips.
     * @param essenceId The essence ID (e.g., "Essence_Fire", "Essence_Ice")
     * @return The color code
     */
    private static String getEssenceTooltipColor(String essenceId) {
        if (essenceId == null) return COLOR_WHITE;
        
        String upper = essenceId.toUpperCase();
        if (upper.contains("FIRE")) return COLOR_ORANGE;      // Orange
        if (upper.contains("ICE")) return COLOR_CYAN;         // Cyan
        if (upper.contains("LIFE")) return COLOR_GREEN;       // Green
        if (upper.contains("LIGHTNING")) return COLOR_YELLOW; // Yellow
        if (upper.contains("VOID")) return COLOR_PURPLE;       // Purple
        if (upper.contains("WATER")) return COLOR_BLUE;        // Blue
        
        return COLOR_WHITE;
    }
}

