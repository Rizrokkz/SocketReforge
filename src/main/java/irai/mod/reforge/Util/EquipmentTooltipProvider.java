package irai.mod.reforge.Util;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TooltipProvider for DynamicTooltipsLib that provides custom tooltips for equipment items.
 * Uses reflection to load DynamicTooltipsLib classes at runtime to support optional dependency.
 * 
 * This class acts as a bridge between DynamicTooltipsLib and our custom tooltip data.
 * Tooltip data is registered via DynamicTooltipUtils when equipment is viewed.
 */
public class EquipmentTooltipProvider {

    // Cache for tooltip data keyed by item ID
    private static final Map<String, List<String>> tooltipCache = new ConcurrentHashMap<>();
    
    private static boolean initialized = false;
    private static final Object INIT_LOCK = new Object();

    /**
     * Initialize the TooltipProvider
     */
    public static void initialize() {
        if (initialized) return;
        
        synchronized (INIT_LOCK) {
            if (initialized) return;
            
            // Check if DynamicTooltipsLib is available
            if (!DynamicTooltipUtils.isAvailable()) {
                System.out.println("[SocketReforge] DynamicTooltipsLib not available - equipment tooltips disabled");
                initialized = true;
                return;
            }
            
            System.out.println("[SocketReforge] EquipmentTooltipProvider initialized successfully");
            initialized = true;
        }
    }

    /**
     * Register tooltip data for an item
     * @param itemId The item ID
     * @param lines The tooltip lines to display
     */
    public static void registerTooltip(String itemId, List<String> lines) {
        if (lines != null && !lines.isEmpty()) {
            tooltipCache.put(itemId, lines);
        }
    }

    /**
     * Get cached tooltip data for an item
     * @param itemId The item ID
     * @return List of tooltip lines, or null if not found
     */
    public static List<String> getCachedTooltip(String itemId) {
        return tooltipCache.get(itemId);
    }

    /**
     * Clear all cached tooltip data
     */
    public static void clearCache() {
        tooltipCache.clear();
    }

    /**
     * Ensure the provider is initialized
     */
    public static void ensureInitialized() {
        if (!initialized) {
            initialize();
        }
    }
}
