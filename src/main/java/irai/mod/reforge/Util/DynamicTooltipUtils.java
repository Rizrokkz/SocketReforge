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
import irai.mod.reforge.Config.RefinementConfig;
import irai.mod.reforge.Lore.LoreAbility;
import irai.mod.reforge.Lore.LoreAbilityRegistry;
import irai.mod.reforge.Lore.LoreEffectType;
import irai.mod.reforge.Lore.LoreGemRegistry;
import irai.mod.reforge.Socket.Essence;
import irai.mod.reforge.Socket.ResonanceSystem;
import irai.mod.reforge.Socket.SocketData;
import irai.mod.reforge.Socket.SocketManager;
import irai.mod.reforge.Util.LangLoader;
import irai.mod.reforge.Util.NameResolver;

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
    private static final String LORE_SOCKET_FILLED = "{o}";
    private static final String LORE_SOCKET_EMPTY = "{ }";
    private static final String LORE_SOCKET_LOCKED = "{x}";

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
    private static final String META_RESONANCE_RECIPE_NAME = "SocketReforge.Resonance.RecipeName";
    private static final String META_RECIPE_PATTERN = "SocketReforge.Recipe.Pattern";
    private static final String META_RECIPE_TYPE = "SocketReforge.Recipe.Type";
    private static final String META_RECIPE_USAGES = "SocketReforge.Recipe.Usages";
    private static final String META_RECIPE_NAME = "SocketReforge.Recipe.ResonanceName";
    private static final String META_REFINEMENT_NAME_KEY = "SocketReforge.Refinement.DisplayNameKey";
    private static final String META_LORE_SOCKET_MAX = "SocketReforge.Lore.Socket.Max";
    private static final String META_LORE_SOCKET_VALUES = "SocketReforge.Lore.Socket.Values";
    private static final String META_LORE_SOCKET_SPIRITS = "SocketReforge.Lore.Socket.Spirits";
    private static final String META_LORE_SOCKET_LEVELS = "SocketReforge.Lore.Socket.Levels";
    private static final String META_LORE_SOCKET_FEED_TIERS = "SocketReforge.Lore.Socket.FeedTiers";
    private static final String META_LORE_SOCKET_COLORS = "SocketReforge.Lore.Socket.Colors";
    private static final String META_LORE_SOCKET_LOCKED = "SocketReforge.Lore.Socket.Locked";
    private static final String META_LORE_SOCKET_EFFECTS = "SocketReforge.Lore.Socket.Effects";
    
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
            if (level >= LEGENDARY.level) return LEGENDARY;
            if (level >= UNCOMMON.level) return UNCOMMON;
            if (level >= COMMON.level) return COMMON;
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
    private static volatile boolean languageResolverRegistered = false;
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
    private static volatile RefinementConfig refinementConfig;

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

    public static void setRefinementConfig(RefinementConfig config) {
        refinementConfig = config;
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
                        registerLanguageResolver();
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
        
        String line = reforgeLevel.getColor() + formatRefineGradeLabel(upgradeName, level, isArmor)
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
                    if (debugMode) {
                        logger.info("[PROVIDER] getProviderId called");
                    }
                    return PROVIDER_ID;
                    
                case "getPriority":
                    if (debugMode) {
                        logger.info("[PROVIDER] getPriority called");
                    }
                    return 100; // Default priority
                    
                case "getTooltipData":
                    // getTooltipData(String itemId, String metadata)
                    // or getTooltipData(String itemId, String metadata, String locale)
                    if (debugMode) {
                        logger.info("[PROVIDER] getTooltipData called with itemId="
                                + (args != null && args.length > 0 ? args[0] : "null")
                                + ", metadata="
                                + (args != null && args.length > 1 ? args[1] : "null"));
                    }
                    
                    if (args == null || args.length < 2) {
                        return null;
                    }
                    
                    String itemId = (String) args[0];
                    String metadata = (String) args[1];
                    String locale = args.length > 2 ? (String) args[2] : null;
                    
                    return getTooltipDataForItem(itemId, metadata, locale);
                    
                default:
                    return null;
            }
        }
    }
    
    /**
     * Get TooltipData for an item using reflection
     */
    private static Object getTooltipDataForItem(String itemId, String metadata, String locale) {
        String langCode = resolveLangCode(locale);
        String rawItemId = itemId;
        String normalizedItemId = normalizeItemId(itemId);
        if (normalizedItemId != null && !normalizedItemId.isBlank()) {
            itemId = normalizedItemId;
        }
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
        String resonanceRecipeName = extractStringValue(metadata, META_RESONANCE_RECIPE_NAME);
        String resonanceEffect = extractStringValue(metadata, META_RESONANCE_EFFECT);
        String resonanceQuality = extractStringValue(metadata, META_RESONANCE_QUALITY);
        boolean hasResonance = resonanceName != null && !resonanceName.isBlank()
                && resonanceRecipeName != null && !resonanceRecipeName.isBlank();
        String recipePattern = extractStringValue(metadata, META_RECIPE_PATTERN);
        boolean hasRecipePattern = recipePattern != null && !recipePattern.isBlank();
        String recipeType = extractStringValue(metadata, META_RECIPE_TYPE);
        boolean hasRecipeType = recipeType != null && !recipeType.isBlank();
        String recipeUsages = extractStringValue(metadata, META_RECIPE_USAGES);
        boolean hasRecipeUsages = recipeUsages != null && !recipeUsages.isBlank();
        String recipeName = extractStringValue(metadata, META_RECIPE_NAME);
        boolean hasRecipeName = recipeName != null && !recipeName.isBlank();
        String baseItemId = extractBaseItemId(metadata);
        String effectiveItemId = baseItemId != null && !baseItemId.isBlank() ? baseItemId : itemId;
        boolean isEquipmentItem = ItemTypeUtils.isEquipmentItemId(effectiveItemId);
        boolean isRecipeItem = "Resonant_Recipe".equalsIgnoreCase(effectiveItemId);
        
        // If no supported metadata is present, return null
        if (reforgeLevel <= 0 && socketMax <= 0 && socketFilled <= 0 && partsProfileType == null
                && !hasResonance && !hasRecipePattern && !hasRecipeType && !hasRecipeUsages
                && !hasRecipeName && !isEquipmentItem) {
            return null;
        }
        
        List<String> tooltipLines = new ArrayList<>();
        String metadataName = extractDisplayName(metadata);
        String metadataNameKey = extractDisplayNameKey(metadata);
        boolean shouldBloodPrefix = shouldPrefixBloodPact(itemId, baseItemId, metadata);
        boolean hasStoredName = metadataName != null && !metadataName.isBlank();
        boolean hasStoredKey = metadataNameKey != null && !metadataNameKey.isBlank();

        boolean isCustomName = false;
        if (!hasStoredKey && hasStoredName && !looksLikeTranslationKey(metadataName)) {
            String defaultLang = "en-US";
            String metaBase = stripLevelSuffix(metadataName).trim();
            String defaultName = NameResolver.resolveItemIdTranslationExact(baseItemId, defaultLang);
            if (defaultName == null || defaultName.isBlank()) {
                defaultName = NameResolver.resolveItemIdTranslationExact(itemId, defaultLang);
            }
            if (defaultName == null || defaultName.isBlank()) {
                defaultName = NameResolver.resolveItemIdTranslation(baseItemId, defaultLang);
                if (defaultName == null || defaultName.isBlank()) {
                    defaultName = NameResolver.resolveItemIdTranslation(itemId, defaultLang);
                }
            }
            if (defaultName == null || defaultName.isBlank()
                    || !defaultName.equalsIgnoreCase(metaBase)) {
                isCustomName = true;
            }
        }

        boolean translationAvailable = false;
        if (hasStoredKey && looksLikeTranslationKey(metadataNameKey)) {
            String translated = LangLoader.getTranslationExact(metadataNameKey, langCode);
            translationAvailable = translated != null && !translated.isBlank() && !translated.equals(metadataNameKey);
        } else if (hasStoredName && looksLikeTranslationKey(metadataName)) {
            String translated = LangLoader.getTranslationExact(metadataName, langCode);
            translationAvailable = translated != null && !translated.isBlank() && !translated.equals(metadataName);
        }
        if (!translationAvailable) {
            String translated = NameResolver.resolveItemIdTranslationNoFallback(baseItemId, langCode);
            if (translated == null || translated.isBlank()) {
                translated = NameResolver.resolveItemIdTranslationNoFallback(itemId, langCode);
            }
            translationAvailable = translated != null && !translated.isBlank();
        }
        boolean shouldOverrideName = hasStoredName || hasStoredKey || isCustomName
                || translationAvailable || shouldBloodPrefix || hasResonance
                || extractLevelSuffix(metadataName) > 0;
        String displayName = null;
        if (shouldOverrideName) {
            displayName = localizeDisplayName(metadataName, metadataNameKey, baseItemId, itemId, langCode);
            if ((shouldBloodPrefix || hasResonance) && (displayName == null || displayName.isEmpty())) {
                displayName = buildFallbackDisplayName(baseItemId, itemId, langCode);
            }
        }
        if (displayName != null && !displayName.isEmpty() && shouldBloodPrefix) {
            String bloodPrefix = getPrefixTranslation("name.prefix.blood_pact", BLOOD_PACT_PREFIX, langCode);
            displayName = applyPrefix(displayName, bloodPrefix, BLOOD_PACT_PREFIX);
        }
        if (displayName != null && !displayName.isEmpty() && hasResonance) {
            String localizedResonanceName = ResonanceSystem.getLocalizedName(resonanceName, langCode);
            String desired = (localizedResonanceName == null || localizedResonanceName.isBlank())
                    ? resonanceName
                    : localizedResonanceName;
            if (desired != null && !desired.isBlank()) {
                displayName = applyPrefix(displayName, desired + " ", resonanceName + " ", localizedResonanceName + " ");
            }
        }
        String localizedRecipeName = null;
        if (hasRecipeName) {
            localizedRecipeName = ResonanceSystem.getLocalizedName(recipeName, langCode);
            if (localizedRecipeName == null || localizedRecipeName.isBlank()) {
                localizedRecipeName = recipeName;
            }
        }
        if (hasRecipeName && isRecipeItem && localizedRecipeName != null && !localizedRecipeName.isBlank()) {
            String recipeDisplayName = LangLoader.formatTranslation(
                    "ui.compendium.recipe_display_name",
                    langCode,
                    localizedRecipeName
            );
            if (recipeDisplayName == null || recipeDisplayName.isBlank()
                    || recipeDisplayName.equals("ui.compendium.recipe_display_name")) {
                recipeDisplayName = "Resonance Recipe: " + localizedRecipeName;
            }
            displayName = recipeDisplayName;
            shouldOverrideName = true;
        }
        boolean isArmorItem = isArmorType(baseItemId, itemId);
        String[] socketEntries = extractSocketEntries(metadata);
        SocketData parsedSocketData = buildSocketDataFromMetadata(socketMax, socketFilled, socketEntries);
        boolean hasRefineOrSocketedEssence = reforgeLevel > 0 || hasSocketedEssence(socketEntries);

        String damageLabel = tr(langCode, "tooltip.damage_label", "Damage");
        if (isEquipmentItem && !isArmorItem) {
            EquipmentDamageTooltipMath.StatSummary summary = EquipmentDamageTooltipMath.computeWeaponDamageSummary(
                    effectiveItemId,
                    reforgeLevel,
                    parsedSocketData,
                    partsDamageMultiplier
            );
            if (hasRefineOrSocketedEssence) {
                tooltipLines.add(
                        COLOR_WHITE + damageLabel + " : "
                                + COLOR_RED + formatDamageValue(summary.getBaseValue())
                                + COLOR_WHITE + " -> "
                                + COLOR_GREEN + formatDamageValue(summary.getBuffedValue())
                );
            } else {
                tooltipLines.add(
                        COLOR_WHITE + damageLabel + " : "
                                + COLOR_GREEN + formatDamageValue(summary.getBaseValue())
                );
            }
        }

        // Add reforge line if present
        if (reforgeLevel > 0) {
            String upgradeName = isArmorItem ? getArmorUpgradeName(reforgeLevel, langCode) : getUpgradeName(reforgeLevel, langCode);
            double multiplier = isArmorItem ? getDefenseMultiplier(reforgeLevel) : getDamageMultiplier(reforgeLevel);
            int percentBonus = (int) ((multiplier - 1.0) * 100);
            
            ReforgeLevel reforgeLevelEnum = ReforgeLevel.fromLevel(reforgeLevel);
            String statType = isArmorItem
                    ? tr(langCode, "tooltip.refine_stat_defense", "defense")
                    : tr(langCode, "tooltip.refine_stat_damage", "damage");
            String refineLabel = tr(langCode, "tooltip.refine_grade", "Refine Grade");
            String gradeLabel = formatRefineGradeLabel(upgradeName, reforgeLevel, isArmorItem);
            String line = COLOR_WHITE + refineLabel + ": " + reforgeLevelEnum.getColor() + gradeLabel
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
            String socketsLabel = tr(langCode, "tooltip.sockets_label", "Sockets");
            String socketLine = COLOR_CYAN + socketsLabel + ": " + COLOR_WHITE + socketDisplay.toString();
            tooltipLines.add(socketLine);
        }

        int loreMax = extractLoreSocketMax(metadata);
        String[] loreEntries = extractLoreSocketEntries(metadata);
        String[] loreColors = extractLoreSocketColors(metadata);
        String[] loreSpirits = extractLoreSocketSpirits(metadata);
        String[] loreEffects = extractLoreSocketEffects(metadata);
        int[] loreLevels = extractLoreSocketLevels(metadata);
        int[] loreFeedTiers = extractLoreSocketFeedTiers(metadata);
        int[] loreLocked = extractLoreSocketLocked(metadata);
        int loreCount = maxArrayLength(loreEntries, loreColors, loreSpirits, loreEffects, loreLevels, loreFeedTiers, loreLocked);
        if (loreMax > loreCount) {
            loreCount = loreMax;
        }

        if (loreCount > 0) {
            StringBuilder loreDisplay = new StringBuilder();
            for (int i = 0; i < loreCount; i++) {
                boolean isLocked = loreLocked != null && i < loreLocked.length && loreLocked[i] > 0;
                String entry = loreEntries != null && i < loreEntries.length ? loreEntries[i] : "";
                String color = loreColors != null && i < loreColors.length ? loreColors[i] : "";
                String spiritId = loreSpirits != null && i < loreSpirits.length ? loreSpirits[i] : "";
                String resolvedColor = resolveLoreSocketColor(color, entry, spiritId, itemId, i);
                String colorTag = getLoreColorTag(resolvedColor);

                boolean hasEntry = entry != null && !entry.isBlank();
                boolean hasSpirit = spiritId != null && !spiritId.isBlank();
                boolean hasColor = resolvedColor != null && !resolvedColor.isBlank();

                if (hasEntry || hasSpirit) {
                    loreDisplay.append(colorTag).append(LORE_SOCKET_FILLED).append("</color>");
                } else if (isLocked) {
                    loreDisplay.append(COLOR_RED).append(LORE_SOCKET_LOCKED).append("</color>");
                } else if (hasColor) {
                    loreDisplay.append(colorTag).append(LORE_SOCKET_EMPTY).append("</color>");
                } else {
                    loreDisplay.append(COLOR_DARK_GRAY).append(LORE_SOCKET_EMPTY).append("</color>");
                }
            }

            String loreSocketsLabel = tr(langCode, "tooltip.lore_sockets_label", "Lore Sockets");
            tooltipLines.add(COLOR_PURPLE + loreSocketsLabel + ": " + COLOR_WHITE + loreDisplay.toString());

            String loreLabel = tr(langCode, "tooltip.lore_label", "Lore");
            String unawakened = tr(langCode, "tooltip.lore_unawakened", "Unawakened");
            String levelLabel = tr(langCode, "tooltip.lore_level_short", "Lv");
            int unawakenedCount = 0;
            for (int i = 0; i < loreCount; i++) {
                String spiritId = loreSpirits != null && i < loreSpirits.length ? loreSpirits[i] : "";
                String entry = loreEntries != null && i < loreEntries.length ? loreEntries[i] : "";
                boolean hasSpirit = spiritId != null && !spiritId.isBlank();
                boolean hasEntry = entry != null && !entry.isBlank();
                if (!hasSpirit && !hasEntry) {
                    continue;
                }
                if (!hasSpirit && hasEntry) {
                    unawakenedCount++;
                    continue;
                }
                int level = loreLevels != null && i < loreLevels.length ? loreLevels[i] : 1;
                String spiritName = localizeLoreSpiritName(spiritId, langCode);
                tooltipLines.add(COLOR_PURPLE + loreLabel + ": " + COLOR_WHITE + spiritName
                        + " " + levelLabel + " " + Math.max(1, level));

                LoreAbility ability = LoreAbilityRegistry.getAbility(spiritId);
                String effectOverride = loreEffects != null && i < loreEffects.length ? loreEffects[i] : "";
                ability = applyEffectOverride(ability, effectOverride);
                if (ability != null) {
                    int feedTier = loreFeedTiers != null && i < loreFeedTiers.length ? loreFeedTiers[i] : 0;
                    if (feedTier < 0) {
                        feedTier = 0;
                    }
                    int safeLevel = Math.max(1, level);
                    tooltipLines.add(COLOR_GRAY + ability.describeEffectOnly(langCode, safeLevel, feedTier));
                }
            }
            if (unawakenedCount > 0) {
                String countSuffix = unawakenedCount > 1 ? " x" + unawakenedCount : "";
                tooltipLines.add(COLOR_PURPLE + loreLabel + ": " + COLOR_GRAY + unawakened + countSuffix);
            }
        }

        if (hasResonance) {
            String localizedEffect = "";
            ResonanceSystem.ResonanceResult resonanceResult = ResonanceSystem.getResultForRecipeName(resonanceName);
            if (resonanceResult != null && resonanceResult.active()) {
                resonanceResult = ResonanceSystem.applyGreaterEssenceScaling(resonanceResult, parsedSocketData);
                localizedEffect = ResonanceSystem.buildDetailedEffect(resonanceResult, !isArmorItem, langCode);
            }
            if (localizedEffect == null || localizedEffect.isBlank()) {
                localizedEffect = ResonanceSystem.getLocalizedEffect(resonanceName, resonanceEffect, langCode);
            }
            if (localizedEffect == null || localizedEffect.isBlank()) {
                localizedEffect = resonanceEffect != null ? resonanceEffect : resonanceName;
            }
            String resonanceLabel = tr(langCode, "tooltip.resonance_label", "Resonance");
            tooltipLines.add(COLOR_ORANGE + resonanceLabel + ": " + COLOR_WHITE + localizedEffect);
        }
        if (resonanceQuality != null && !resonanceQuality.isBlank()) {
            String qualityLabel = tr(langCode, "tooltip.quality_label", "Quality");
            String qualityText = localizeQuality(resonanceQuality, langCode);
            tooltipLines.add(COLOR_YELLOW + qualityLabel + ": " + COLOR_WHITE + qualityText);
        }
        if (hasRecipeName && localizedRecipeName != null && !localizedRecipeName.isBlank()) {
            String resonanceLabel = tr(langCode, "tooltip.resonance_label", "Resonance");
            tooltipLines.add(COLOR_ORANGE + resonanceLabel + ": " + COLOR_WHITE + localizedRecipeName);
        }
        if (hasRecipeType) {
            String typeLabel = tr(langCode, "tooltip.type_label", "Type");
            String localizedType = ResonanceSystem.localizeAppliesTo(recipeType, langCode);
            tooltipLines.add(COLOR_ORANGE + typeLabel + ": " + COLOR_WHITE + localizedType);
        }
        if (hasRecipePattern) {
            String recipeLabel = tr(langCode, "tooltip.recipe_label", "Recipe");
            tooltipLines.add(COLOR_ORANGE + recipeLabel + " : " + COLOR_WHITE + recipePattern);
        }
        if (hasRecipeUsages) {
            String usagesLabel = tr(langCode, "tooltip.usages_label", "Usages");
            tooltipLines.add(COLOR_ORANGE + usagesLabel + ": " + COLOR_WHITE + recipeUsages);
        }

        // Add modular parts line if present
        String partsLine = buildPartsTooltipLine(partsProfileType, part1Tier, part2Tier, part3Tier, langCode);
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
                String essenceName = localizeEssenceType(type, langCode);
                String essenceTier = tr(langCode, "tooltip.essence_tier",
                        essenceName + " T" + tier,
                        essenceName, tier);
                String effectDesc = SocketManager.describeEssenceEffect(type, tier, !isArmorItem, parsedSocketData, langCode);
                String essenceLine = color + essenceTier + "</color> " + COLOR_GRAY + effectDesc;
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
            String hashInput = (rawItemId != null ? rawItemId : itemId) + ":" + (metadata != null ? metadata.hashCode() : "none");
            Method hashInputMethod = builder.getClass().getMethod("hashInput", String.class);
            hashInputMethod.invoke(builder, hashInput);
            
            // Set name override only when we should override the client-localized name
            if (shouldOverrideName && displayName != null && !displayName.isEmpty()) {
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
            
            if (debugMode) {
                logger.info("[TOOLTIP] Created tooltip for " + itemId + " with " + tooltipLines.size() + " lines");
            }
            
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

    private static String buildFallbackDisplayName(String baseItemId, String itemId, String langCode) {
        String source = baseItemId;
        if (source == null || source.isBlank()) {
            source = itemId;
        }
        if (source == null || source.isBlank()) {
            return null;
        }

        String normalizedId = source.trim();
        String localized = NameResolver.resolveItemIdTranslation(normalizedId, langCode);
        if (localized != null && !localized.isBlank()) {
            return localized;
        }

        // Fallback through NameResolver path to keep behavior close to refinement resolution.
        String resolved = NameResolver.resolveTranslationKey("items." + normalizedId + ".name", langCode);
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
            boolean isArmor = !ItemTypeUtils.isWeaponItemId(itemId) && ItemTypeUtils.isArmorItemId(itemId);
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

    private static void registerLanguageResolver() {
        if (!isAvailable || tooltipApi == null || languageResolverRegistered) {
            return;
        }
        try {
            Class<?> resolverClass = Class.forName("org.herolias.tooltips.api.DynamicTooltipsApi$LanguageResolver");
            Object resolver = Proxy.newProxyInstance(
                    resolverClass.getClassLoader(),
                    new Class<?>[]{resolverClass},
                    (proxy, method, args) -> {
                        if ("resolveLanguage".equals(method.getName()) && args != null && args.length > 0) {
                            if (args[0] instanceof java.util.UUID uuid) {
                                return LangLoader.getLanguageForUuid(uuid);
                            }
                            return LangLoader.getFallbackLanguage();
                        }
                        return null;
                    }
            );
            Method setResolver = tooltipApi.getClass().getMethod("setLanguageResolver", resolverClass);
            setResolver.invoke(tooltipApi, resolver);
            languageResolverRegistered = true;
            if (debugMode) {
                logger.info("Registered DynamicTooltipsLib language resolver");
            }
        } catch (Exception e) {
            if (debugMode) {
                logger.warn("Failed to register language resolver: " + e.getMessage());
            }
        }
    }

    private static int[] extractIntArray(String metadata, String searchKey) {
        try {
            int keyIndex = metadata.indexOf(searchKey);
            if (keyIndex < 0) return null;
            int bracketStart = metadata.indexOf("[", keyIndex);
            int bracketEnd = metadata.indexOf("]", bracketStart);
            if (bracketStart < 0 || bracketEnd < 0) return null;
            String arrayContent = metadata.substring(bracketStart + 1, bracketEnd);
            if (arrayContent.trim().isEmpty()) return new int[0];
            String[] entries = arrayContent.split(",");
            int[] values = new int[entries.length];
            for (int i = 0; i < entries.length; i++) {
                String entry = entries[i] == null ? "" : entries[i].trim();
                entry = entry.replace("\"", "");
                try {
                    values[i] = Integer.parseInt(entry);
                } catch (NumberFormatException ignored) {
                    try {
                        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("-?\\d+").matcher(entry);
                        if (matcher.find()) {
                            values[i] = Integer.parseInt(matcher.group());
                        } else {
                            values[i] = 0;
                        }
                    } catch (Exception ignoredToo) {
                        values[i] = 0;
                    }
                }
            }
            return values;
        } catch (Exception e) {
            return null;
        }
    }

    private static int maxArrayLength(String[] a, String[] b, String[] c, String[] d, int[] e, int[] f, int[] g) {
        int max = 0;
        if (a != null) max = Math.max(max, a.length);
        if (b != null) max = Math.max(max, b.length);
        if (c != null) max = Math.max(max, c.length);
        if (d != null) max = Math.max(max, d.length);
        if (e != null) max = Math.max(max, e.length);
        if (f != null) max = Math.max(max, f.length);
        if (g != null) max = Math.max(max, g.length);
        return max;
    }

    private static int extractLoreSocketMax(String metadata) {
        return extractFirstIntegerNearKey(metadata, META_LORE_SOCKET_MAX);
    }

    private static String[] extractLoreSocketEntries(String metadata) {
        String[] entries = extractStringArray(metadata, META_LORE_SOCKET_VALUES);
        return entries == null ? new String[0] : entries;
    }

    private static String[] extractLoreSocketColors(String metadata) {
        String[] entries = extractStringArray(metadata, META_LORE_SOCKET_COLORS);
        return entries == null ? new String[0] : entries;
    }

    private static String[] extractLoreSocketSpirits(String metadata) {
        String[] entries = extractStringArray(metadata, META_LORE_SOCKET_SPIRITS);
        return entries == null ? new String[0] : entries;
    }

    private static String[] extractLoreSocketEffects(String metadata) {
        String[] entries = extractStringArray(metadata, META_LORE_SOCKET_EFFECTS);
        return entries == null ? new String[0] : entries;
    }

    private static int[] extractLoreSocketLevels(String metadata) {
        int[] values = extractIntArray(metadata, META_LORE_SOCKET_LEVELS);
        return values == null ? new int[0] : values;
    }

    private static int[] extractLoreSocketFeedTiers(String metadata) {
        int[] values = extractIntArray(metadata, META_LORE_SOCKET_FEED_TIERS);
        return values == null ? new int[0] : values;
    }

    private static int[] extractLoreSocketLocked(String metadata) {
        int[] values = extractIntArray(metadata, META_LORE_SOCKET_LOCKED);
        return values == null ? new int[0] : values;
    }

    private static String extractDisplayNameKey(String metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            String searchKey = META_REFINEMENT_NAME_KEY;
            int keyIndex = metadata.indexOf(searchKey);
            if (keyIndex < 0) return null;

            int colonIndex = metadata.indexOf(":", keyIndex);
            if (colonIndex < 0) return null;

            int quoteStart = metadata.indexOf("\"", colonIndex);
            if (quoteStart < 0) return null;

            int quoteEnd = metadata.indexOf("\"", quoteStart + 1);
            if (quoteEnd < 0) return null;

            return metadata.substring(quoteStart + 1, quoteEnd);
        } catch (Exception e) {
            return null;
        }
    }

    private static String normalizeItemId(String itemId) {
        if (itemId == null) {
            return null;
        }
        String trimmed = itemId.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        int dttIndex = trimmed.indexOf("__dtt_");
        if (dttIndex > 0) {
            return trimmed.substring(0, dttIndex);
        }
        return trimmed;
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

    private static String resolveLangCode(String locale) {
        String fallback = LangLoader.getFallbackLanguage();
        if (locale == null || locale.isBlank()) {
            return fallback;
        }
        String normalized = LangLoader.normalizeLanguage(locale);
        if (normalized == null || normalized.isBlank()) {
            return fallback;
        }
        // DynamicTooltipsLib can supply en-US even when the player is on another language.
        // If we already know a non-English fallback, prefer it to keep tooltips localized.
        if (fallback != null && !fallback.isBlank()
                && !fallback.equalsIgnoreCase(normalized)
                && "en-US".equalsIgnoreCase(normalized)) {
            return fallback;
        }
        return normalized;
    }

    private static void appendLoreScalingLines(List<String> tooltipLines,
                                               String langCode,
                                               LoreAbility ability,
                                               int level,
                                               int feedTier) {
        if (tooltipLines == null || ability == null) {
            return;
        }
        String header = tr(langCode, "ui.lore_feed.scaling_header", "Scaling");
        tooltipLines.add(COLOR_GRAY + header);

        String chanceTemplate = tr(langCode, "ui.lore_feed.scaling_chance", "Proc Chance: {0}%");
        String cooldownTemplate = tr(langCode, "ui.lore_feed.scaling_cooldown", "Cooldown: {0}s");
        String radiusTemplate = tr(langCode, "ui.lore_feed.scaling_radius", "Radius: {0}m");
        String durationTemplate = tr(langCode, "ui.lore_feed.scaling_duration", "Duration: {0}s");
        String capTemplate = tr(langCode, "ui.lore_feed.scaling_cap", "Summon Cap: {0}");
        String doubleCastTemplate = tr(langCode, "ui.lore_feed.scaling_double_cast", "Double Cast Bonus: {0}%");
        String berserkTemplate = tr(langCode, "ui.lore_feed.scaling_berserk", "Berserk Bonus: {0}%");

        double chance = LoreAbility.scaleProcChance(ability.getProcChance(), feedTier) * 100.0d;
        long cooldownMs = LoreAbility.scaleCooldownMs(ability.getCooldownMs(), feedTier);
        tooltipLines.add(COLOR_GRAY + chanceTemplate.replace("{0}", formatPercent(chance)));
        tooltipLines.add(COLOR_GRAY + cooldownTemplate.replace("{0}", formatSeconds(cooldownMs)));

        LoreEffectType effectType = ability.getEffectType();
        if (effectType == null) {
            return;
        }

        if (effectType == LoreEffectType.HEAL_AREA
                || effectType == LoreEffectType.HEAL_AREA_OVER_TIME
                || effectType == LoreEffectType.OMNISLASH) {
            double baseRadius = effectType == LoreEffectType.OMNISLASH
                    ? LoreAbility.BASE_OMNISLASH_RADIUS
                    : LoreAbility.BASE_HEAL_AREA_RADIUS;
            double radius = LoreAbility.scaleRadius(baseRadius, feedTier);
            tooltipLines.add(COLOR_GRAY + radiusTemplate.replace("{0}", formatValue(radius)));
        }

        if (effectType == LoreEffectType.HEAL_SELF_OVER_TIME
                || effectType == LoreEffectType.HEAL_AREA_OVER_TIME) {
            long duration = LoreAbility.resolveHotDurationMs(feedTier);
            tooltipLines.add(COLOR_GRAY + durationTemplate.replace("{0}", formatSeconds(duration)));
        }

        if (effectType == LoreEffectType.HEAL_AREA) {
            long duration = LoreAbility.resolveAreaHealDurationMs(feedTier);
            tooltipLines.add(COLOR_GRAY + durationTemplate.replace("{0}", formatSeconds(duration)));
        }

        if (effectType == LoreEffectType.APPLY_STUN) {
            double value = ability.getValueForLevel(level);
            long duration = LoreAbility.resolveStunFreezeDurationMs(value, feedTier);
            tooltipLines.add(COLOR_GRAY + durationTemplate.replace("{0}", formatSeconds(duration)));
        }

        if (effectType == LoreEffectType.SUMMON_WOLF_PACK) {
            int cap = Math.max(1, level + Math.max(0, feedTier));
            tooltipLines.add(COLOR_GRAY + capTemplate.replace("{0}", String.valueOf(cap)));
        }

        if (effectType == LoreEffectType.DOUBLE_CAST) {
            double scaledValue = LoreAbility.scaleEffectValue(ability.getValueForLevel(level), feedTier);
            double pct = clampPercent(scaledValue, 0.25d, 1.0d) * 100.0d;
            tooltipLines.add(COLOR_GRAY + doubleCastTemplate.replace("{0}", formatPercent(pct)));
        }

        if (effectType == LoreEffectType.BERSERK) {
            long duration = LoreAbility.resolveBerserkDurationMs(feedTier);
            tooltipLines.add(COLOR_GRAY + berserkTemplate.replace("{0}", formatSeconds(duration)));
        }
    }

    private static String formatValue(double value) {
        double rounded = Math.round(value * 10.0d) / 10.0d;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.0001d) {
            return String.format(java.util.Locale.ROOT, "%.0f", rounded);
        }
        return String.format(java.util.Locale.ROOT, "%.1f", rounded);
    }

    private static String formatPercent(double percent) {
        double rounded = Math.round(percent * 10.0d) / 10.0d;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.0001d) {
            return String.format(java.util.Locale.ROOT, "%.0f", rounded);
        }
        return String.format(java.util.Locale.ROOT, "%.1f", rounded);
    }

    private static String formatSeconds(long millis) {
        double seconds = Math.max(0.0d, millis / 1000.0d);
        double rounded = Math.round(seconds * 10.0d) / 10.0d;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.0001d) {
            return String.format(java.util.Locale.ROOT, "%.0f", rounded);
        }
        return String.format(java.util.Locale.ROOT, "%.1f", rounded);
    }

    private static double clampPercent(double value, double min, double max) {
        double pct = value <= 1.0d ? value : value / 100.0d;
        if (pct < min) {
            return min;
        }
        if (pct > max) {
            return max;
        }
        return pct;
    }

    private static String tr(String langCode, String key, String fallback, Object... params) {
        String template = LangLoader.getTranslationForLanguage(key, langCode);
        if (template == null || template.isBlank() || template.equals(key)) {
            template = fallback;
        }
        if (params.length == 0) {
            return template;
        }
        for (int i = 0; i < params.length; i++) {
            template = template.replace("{" + i + "}", String.valueOf(params[i]));
        }
        return template;
    }

    private static String localizeQuality(String raw, String langCode) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String key = raw.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (key.isBlank()) {
            return raw;
        }
        String translated = LangLoader.getTranslationForLanguage("resonance.quality." + key, langCode);
        if (translated == null || translated.isBlank() || translated.equals("resonance.quality." + key)) {
            return raw;
        }
        return translated;
    }

    private static String localizeEssenceType(Essence.Type type, String langCode) {
        if (type == null) {
            return tr(langCode, "tooltip.essence.unknown", "Unknown");
        }
        String key = switch (type) {
            case FIRE -> "essence.type.fire";
            case WATER -> "essence.type.water";
            case ICE -> "essence.type.ice";
            case LIGHTNING -> "essence.type.lightning";
            case LIFE -> "essence.type.life";
            case VOID -> "essence.type.void";
        };
        String translated = LangLoader.getTranslationForLanguage(key, langCode);
        if (translated == null || translated.isBlank() || translated.equals(key)) {
            String raw = type.name().toLowerCase(java.util.Locale.ROOT);
            return raw.isEmpty() ? type.name() : Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
        }
        return translated;
    }

    private static String localizeDisplayName(String metadataName, String metadataNameKey, String baseItemId, String itemId, String langCode) {
        int level = extractLevelSuffix(metadataName);
        String baseName = stripLevelSuffix(metadataName);

        String localized = null;
        if (metadataNameKey != null && !metadataNameKey.isBlank() && looksLikeTranslationKey(metadataNameKey)) {
            String translated = LangLoader.getTranslationExact(metadataNameKey.trim(), langCode);
            if (translated != null && !translated.isBlank() && !translated.equals(metadataNameKey)
                    && !isEnglishFallback(translated, metadataNameKey, langCode)) {
                localized = translated;
            }
        } else if (baseName != null && looksLikeTranslationKey(baseName)) {
            String translated = LangLoader.getTranslationExact(baseName.trim(), langCode);
            if (translated != null && !translated.isBlank() && !translated.equals(baseName)
                    && !isEnglishFallback(translated, baseName, langCode)) {
                localized = translated;
            }
        }

        if (localized == null || localized.isBlank() || localized.equals(baseName)) {
            localized = NameResolver.resolveItemIdTranslationNoFallback(baseItemId, langCode);
            if (localized == null || localized.isBlank()) {
                localized = NameResolver.resolveItemIdTranslationNoFallback(itemId, langCode);
            }
        }
        if (localized == null || localized.isBlank()) {
            localized = tryTranslateItemId(baseItemId, langCode);
        }
        if ((localized == null || localized.isBlank()) && itemId != null) {
            localized = tryTranslateItemId(itemId, langCode);
        }
        if (localized == null || localized.isBlank()) {
            localized = baseName;
        }
        if (localized == null || localized.isBlank()) {
            localized = baseItemId != null && !baseItemId.isBlank() ? baseItemId : itemId;
        }
        if (localized == null || localized.isBlank()) {
            return null;
        }
        if (level > 0) {
            boolean isArmor = isArmorType(baseItemId, itemId);
            localized = applyRefinementToName(localized, level, isArmor);
        }
        return localized;
    }

    private static int extractLevelSuffix(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        String trimmed = value.trim();
        int configured = extractLevelSuffixConfigured(trimmed);
        if (configured > 0) {
            return configured;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\s\\+(\\d+)$").matcher(trimmed);
        if (!matcher.find()) {
            return 0;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String stripLevelSuffix(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String trimmed = value.trim();
        String stripped = stripLevelSuffixConfigured(trimmed);
        if (stripped != null) {
            return stripped;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\s\\+\\d+$").matcher(trimmed);
        if (matcher.find()) {
            return trimmed.substring(0, matcher.start()).trim();
        }
        return trimmed;
    }

    private static int extractLevelSuffixConfigured(String value) {
        RefinementConfig cfg = refinementConfig;
        if (cfg == null) {
            return 0;
        }
        int fromLabels = matchLevelLabel(value, cfg);
        if (fromLabels > 0) {
            return fromLabels;
        }
        String prefix = cfg.getRefinementLevelPrefix();
        String suffix = cfg.getRefinementLevelSuffix();
        if ((prefix == null || prefix.isEmpty()) && (suffix == null || suffix.isEmpty())) {
            return 0;
        }
        String pattern = java.util.regex.Pattern.quote(prefix == null ? "" : prefix)
                + "(\\d+)"
                + java.util.regex.Pattern.quote(suffix == null ? "" : suffix);
        java.util.regex.Matcher matcher;
        if (cfg.isRefinementLevelUsePrefix()) {
            matcher = java.util.regex.Pattern.compile("^" + pattern).matcher(value);
        } else {
            matcher = java.util.regex.Pattern.compile(pattern + "$").matcher(value);
        }
        if (!matcher.find()) {
            return 0;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String stripLevelSuffixConfigured(String value) {
        RefinementConfig cfg = refinementConfig;
        if (cfg == null) {
            return null;
        }
        String fromLabels = stripLevelLabel(value, cfg);
        if (fromLabels != null) {
            return fromLabels;
        }
        String prefix = cfg.getRefinementLevelPrefix();
        String suffix = cfg.getRefinementLevelSuffix();
        if ((prefix == null || prefix.isEmpty()) && (suffix == null || suffix.isEmpty())) {
            return null;
        }
        String pattern = java.util.regex.Pattern.quote(prefix == null ? "" : prefix)
                + "(\\d+)"
                + java.util.regex.Pattern.quote(suffix == null ? "" : suffix);
        java.util.regex.Matcher matcher;
        if (cfg.isRefinementLevelUsePrefix()) {
            matcher = java.util.regex.Pattern.compile("^" + pattern).matcher(value);
            if (!matcher.find()) {
                return null;
            }
            return value.substring(matcher.end()).trim();
        }
        matcher = java.util.regex.Pattern.compile(pattern + "$").matcher(value);
        if (!matcher.find()) {
            return null;
        }
        return value.substring(0, matcher.start()).trim();
    }

    private static int matchLevelLabel(String value, RefinementConfig cfg) {
        if (value == null || value.isBlank() || cfg == null) {
            return 0;
        }
        boolean usePrefix = cfg.isRefinementLevelUsePrefix();
        int matchedLevel = 0;
        int matchedLen = -1;
        String[][] labelSets = {
                cfg.getRefinementLevelLabels(),
                cfg.getRefinementLevelLabelsArmor()
        };
        for (String[] labels : labelSets) {
            if (labels == null || labels.length == 0) {
                continue;
            }
            int max = Math.min(cfg.getMaxLevel(), labels.length - 1);
            for (int level = 1; level <= max; level++) {
                String label = labels[level];
                if (label == null || label.isBlank()) continue;
                boolean matches = usePrefix ? value.startsWith(label) : value.endsWith(label);
                if (matches && label.length() > matchedLen) {
                    matchedLevel = level;
                    matchedLen = label.length();
                }
            }
        }
        return matchedLevel;
    }

    private static String stripLevelLabel(String value, RefinementConfig cfg) {
        if (value == null || value.isBlank() || cfg == null) {
            return null;
        }
        boolean usePrefix = cfg.isRefinementLevelUsePrefix();
        int matchedLevel = 0;
        int matchedLen = -1;
        String matchedLabel = null;
        String[][] labelSets = {
                cfg.getRefinementLevelLabels(),
                cfg.getRefinementLevelLabelsArmor()
        };
        for (String[] labels : labelSets) {
            if (labels == null || labels.length == 0) {
                continue;
            }
            int max = Math.min(cfg.getMaxLevel(), labels.length - 1);
            for (int level = 1; level <= max; level++) {
                String label = labels[level];
                if (label == null || label.isBlank()) continue;
                boolean matches = usePrefix ? value.startsWith(label) : value.endsWith(label);
                if (matches && label.length() > matchedLen) {
                    matchedLevel = level;
                    matchedLen = label.length();
                    matchedLabel = label;
                }
            }
        }
        if (matchedLevel <= 0 || matchedLabel == null) {
            return null;
        }
        if (usePrefix) {
            return value.substring(matchedLabel.length()).trim();
        }
        return value.substring(0, value.length() - matchedLabel.length()).trim();
    }

    private static String formatRefinementSuffix(int level) {
        return formatRefinementSuffix(level, false);
    }

    private static String formatRefinementSuffix(int level, boolean isArmor) {
        if (level <= 0) {
            return "";
        }
        RefinementConfig cfg = refinementConfig;
        if (cfg != null) {
            return cfg.formatRefinementSuffix(level, isArmor);
        }
        return " +" + level;
    }

    private static String formatRefineGradeLabel(String upgradeName, int level, boolean isArmor) {
        if (level <= 0) {
            return upgradeName == null ? "" : upgradeName;
        }
        RefinementConfig cfg = refinementConfig;
        if (cfg == null) {
            return applyRefinementToName(upgradeName, level, isArmor);
        }
        if (cfg.isRefinementLevelUsePrefix()) {
            String prefix = cfg.getRefinementLevelPrefix();
            String suffix = cfg.getRefinementLevelSuffix();
            if ((prefix == null || prefix.isEmpty()) && (suffix == null || suffix.isEmpty())) {
                return " +" + level;
            }
            return (prefix == null ? "" : prefix) + level + (suffix == null ? "" : suffix);
        }
        String label = cfg.getRefinementLevelLabel(level, isArmor);
        if (label != null) {
            label = label.trim();
        }
        if (label != null && !label.isEmpty()) {
            return label;
        }
        return upgradeName == null ? "" : upgradeName;
    }

    private static String applyRefinementToName(String baseName, int level) {
        return applyRefinementToName(baseName, level, false);
    }

    private static String applyRefinementToName(String baseName, int level, boolean isArmor) {
        if (baseName == null) {
            return null;
        }
        if (level <= 0) {
            return baseName;
        }
        RefinementConfig cfg = refinementConfig;
        if (cfg != null) {
            return cfg.applyRefinementToName(baseName, level, isArmor);
        }
        return baseName + " +" + level;
    }

    private static String tryTranslateItemId(String itemId, String langCode) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        String normalizedId = itemId.trim();
        String localized = NameResolver.resolveItemIdTranslation(normalizedId, langCode);
        if (localized != null && !localized.isBlank()) {
            return localized;
        }
        return null;
    }

    private static boolean looksLikeTranslationKey(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        if (!lower.contains(".")) {
            return false;
        }
        if (lower.endsWith(".name")) {
            return true;
        }
        return lower.contains(".items.") || lower.contains(".item.") || lower.contains(".entity.");
    }

    private static boolean isEnglishFallback(String translated, String translationKey, String langCode) {
        if (translated == null || translated.isBlank() || translationKey == null || translationKey.isBlank()) {
            return false;
        }
        if (langCode == null || langCode.isBlank() || "en-US".equalsIgnoreCase(langCode)) {
            return false;
        }
        String english = LangLoader.getTranslationExact(translationKey, "en-US");
        return english != null && !english.isBlank() && translated.equalsIgnoreCase(english);
    }

    private static String getPrefixTranslation(String key, String fallback, String langCode) {
        String translated = LangLoader.getTranslationForLanguage(key, langCode);
        if (translated == null || translated.isBlank() || translated.equals(key)) {
            return fallback;
        }
        if (!translated.endsWith(" ")) {
            return translated + " ";
        }
        return translated;
    }

    private static String applyPrefix(String value, String desiredPrefix, String... knownPrefixes) {
        if (value == null || value.isBlank() || desiredPrefix == null || desiredPrefix.isBlank()) {
            return value;
        }
        if (knownPrefixes != null) {
            for (String prefix : knownPrefixes) {
                if (prefix == null || prefix.isBlank()) {
                    continue;
                }
                if (value.startsWith(prefix)) {
                    if (value.startsWith(desiredPrefix)) {
                        return value;
                    }
                    return desiredPrefix + value.substring(prefix.length());
                }
            }
        }
        if (value.startsWith(desiredPrefix)) {
            return value;
        }
        return desiredPrefix + value;
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
                double result = Double.parseDouble(metadata.substring(numberStart, numberEnd));
                // Check for Infinity or NaN and return default
                if (Double.isInfinite(result) || Double.isNaN(result)) {
                    return defaultValue;
                }
                return result;
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

    private static String buildPartsTooltipLine(String profileType, int t1, int t2, int t3, String langCode) {
        if (profileType == null || profileType.isBlank()) {
            return null;
        }

        String normalized = profileType.trim().toUpperCase(java.util.Locale.ROOT);
        String[] glyphs = getPartGlyphs(normalized);

        StringBuilder sb = new StringBuilder();
        String partsLabel = tr(langCode, "tooltip.parts_label", "Parts");
        sb.append(COLOR_YELLOW).append(partsLabel).append(": ").append(COLOR_WHITE)
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
        if (level <= 0) return "Ancient";
        switch (level) {
            case 1: return "Sharp";
            case 2: return "Deadly";
            case 3: return "Legendary";
            default: return "Refined";
        }
    }

    private static String getUpgradeName(int level, String langCode) {
        String key = "tooltip.reforge.weapon_grade." + level;
        return tr(langCode, key, getUpgradeName(level));
    }
    
    private static String getArmorUpgradeName(int level) {
        if (level <= 0) return "Ancient";
        switch (level) {
            case 1: return "Protective";
            case 2: return "Resistant";
            case 3: return "Fortified";
            default: return "Refined";
        }
    }

    private static String getArmorUpgradeName(int level, String langCode) {
        String key = "tooltip.reforge.armor_grade." + level;
        return tr(langCode, key, getArmorUpgradeName(level));
    }
    
    private static double getDamageMultiplier(int level) {
        if (refinementConfig != null) {
            return refinementConfig.getDamageMultiplier(level);
        }
        switch (level) {
            case 1: return 1.10;
            case 2: return 1.15;
            case 3: return 1.25;
            default: return 1.0;
        }
    }
    
    private static double getDefenseMultiplier(int level) {
        if (refinementConfig != null) {
            return refinementConfig.getDefenseMultiplier(level);
        }
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
        if (ItemTypeUtils.isWeaponItemId(baseItemId) || ItemTypeUtils.isWeaponItemId(itemId)) {
            return false;
        }
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

    private static String getLoreColorTag(String color) {
        if (color == null || color.isBlank()) {
            return COLOR_WHITE;
        }
        String lower = color.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("red") || lower.contains("ruby")) return "<color is=\"#FF5555\">";
        if (lower.contains("blue") || lower.contains("sapphire")) return "<color is=\"#5599FF\">";
        if (lower.contains("green") || lower.contains("emerald")) return "<color is=\"#55FF77\">";
        if (lower.contains("purple") || lower.contains("amethyst")) return "<color is=\"#AA55FF\">";
        if (lower.contains("yellow") || lower.contains("topaz")) return "<color is=\"#FFFF55\">";
        if (lower.contains("orange")) return "<color is=\"#FFAA00\">";
        if (lower.contains("black") || lower.contains("onyx")) return "<color is=\"#555555\">";
        if (lower.contains("white") || lower.contains("diamond")) return "<color is=\"#FFFFFF\">";
        if (lower.contains("cyan") || lower.contains("opal")) return "<color is=\"#55FFFF\">";
        return COLOR_WHITE;
    }

    private static String resolveLoreSocketColor(String color, String entry, String spiritId, String itemId, int index) {
        if (color != null && !color.isBlank()) {
            return color.trim();
        }
        if (entry != null && !entry.isBlank()) {
            String gemColor = LoreGemRegistry.resolveColor(entry);
            if (gemColor != null && !gemColor.isBlank()) {
                return gemColor;
            }
        }
        if (spiritId != null && !spiritId.isBlank()) {
            String spiritColor = LoreGemRegistry.resolveSpiritColor(spiritId);
            if (spiritColor != null && !spiritColor.isBlank()) {
                return spiritColor;
            }
        }
        List<String> known = LoreGemRegistry.getKnownColors();
        if (known != null && !known.isEmpty() && itemId != null && !itemId.isBlank()) {
            String seed = itemId.trim() + ":" + index;
            int idx = Math.floorMod(seed.hashCode(), known.size());
            String picked = known.get(idx);
            if (picked != null && !picked.isBlank()) {
                return picked;
            }
        }
        return color;
    }

    private static String localizeLoreSpiritName(String spiritId, String langCode) {
        if (spiritId == null || spiritId.isBlank()) {
            return "";
        }
        String trimmed = spiritId.trim();
        if (looksLikeTranslationKey(trimmed)) {
            String translated = LangLoader.getTranslationForLanguage(trimmed, langCode);
            if (translated != null && !translated.isBlank() && !translated.equals(trimmed)) {
                return translated;
            }
        }
        String raw = trimmed.contains(".") ? trimmed.substring(trimmed.lastIndexOf('.') + 1) : trimmed;
        return humanizeToken(raw);
    }

    private static LoreAbility applyEffectOverride(LoreAbility ability, String overrideRaw) {
        if (ability == null) {
            return null;
        }
        if (overrideRaw == null || overrideRaw.isBlank()) {
            return ability;
        }
        LoreEffectType override = LoreEffectType.fromString(overrideRaw, null);
        if (override == null || override == ability.getEffectType()) {
            return ability;
        }
        return new LoreAbility(
                ability.getSpiritId(),
                ability.getTrigger(),
                ability.getProcChance(),
                ability.getCooldownMs(),
                override,
                ability.getBaseValue(),
                ability.getPerLevel(),
                ability.getAbilityNameKey(),
                ability.getAbilityNameFallback()
        );
    }

    private static String humanizeToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String lower = raw.trim().replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        String[] parts = lower.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
            if (i < parts.length - 1) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }
}

