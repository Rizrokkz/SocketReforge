package irai.mod.reforge.Interactions;

import java.io.File;
import java.util.regex.Pattern;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Config.RefinementConfig;
import irai.mod.reforge.Config.SFXConfig;


/**
 * Weapon reforging system using weapon ID-based level tracking.
 * Upgrade level is determined by the weapon ID suffix (e.g., Weapon_Axe_Cobalt1 = +1).
 * No persistent tracking needed - level is embedded in the item ID.
 * 
 * Merged functionality from WeaponUpgradeTracker and WeaponTooltip.
 */
@SuppressWarnings("removal")
public class ReforgeEquip extends SimpleInteraction {

    public static final BuilderCodec<ReforgeEquip> CODEC =
            BuilderCodec.builder(ReforgeEquip.class, ReforgeEquip::new, SimpleInteraction.CODEC).build();

    private static final String MATERIAL_ID = "Ingredient_Bar_Iron";
    private static final int MATERIAL_COST = 1;
    private static final int MAX_UPGRADE_LEVEL = 3;
    private static final double[] BREAK_CHANCES = {
            0.010,  // 0 → 1: 10% break chance
            0.050,  // 1 → 2: 15% break chance
            0.075,  // 2 → 3: 20% break chance
    };

    private static final Pattern WEAPON_PATTERN = Pattern.compile("^(?!.*Arrow).*[Ww]eapon.*$");
    
    // Static cache for weapon category/structure checks to avoid repeated reflection
    private static final java.util.Map<String, Boolean> weaponCheckCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CACHE_EXPIRY_MS = 5 * 60 * 1000; // 5 minutes
    private static final java.util.Map<String, Long> cacheTimestamps = new java.util.concurrent.ConcurrentHashMap<>();

    private static final double[][] REFORGE_WEIGHTS = {
            {0.00, 0.65, 0.34, 0.01},   // 0 → 1
            {0.35, 0.45, 0.19, 0.01},   // 1 → 2
            {0.60, 0.30, 0.095, 0.005}, // 2 → 3
    };

    // Upgrade level display names
    private static final String[] UPGRADE_NAMES = {
            "",           // Base
            "Sharp",      // +1
            "Deadly",     // +2
            "Legendary"   // +3
    };

    private SFXConfig sfxConfig;
    private RefinementConfig refinementConfig;

    public ReforgeEquip() {
        this.sfxConfig = new SFXConfig();
    }

    public void setSfxConfig(SFXConfig sfxConfig) {
        this.sfxConfig = sfxConfig;
    }

    public void setRefinementConfig(RefinementConfig refinementConfig) {
        this.refinementConfig = refinementConfig;
    }

    @Override
    protected void tick0(boolean firstRun, float time, @NonNullDecl InteractionType type,
                         @NonNullDecl InteractionContext context, @NonNullDecl CooldownHandler cooldownHandler) {
        super.tick0(firstRun, time, type, context, cooldownHandler);
        if (!firstRun || type != InteractionType.Use) return;

        Player player = getPlayerFromContext(context);
        if (player == null) return;
        //yif (!isValidReforgebench(context)) {return ;}

        ItemStack heldItem = context.getHeldItem();
        if (heldItem == null) return;

        processReforge(player, heldItem, context);
    }

    private void processReforge(Player player, ItemStack heldItem, InteractionContext context) {
        // Use the ItemStack-based check for proper category detection
        if (!isWeapon(heldItem)) {
            player.sendMessage(Message.raw("This item cannot be reforged"));
            return;
        }

        String itemId = heldItem.getItemId();
        short slot = context.getHeldItemSlot();
        int currentLevel = getLevelFromWeaponId(itemId);

        if (currentLevel >= MAX_UPGRADE_LEVEL) {
            player.sendMessage(Message.raw("Weapon is already at max level"));
            showDetailedStats(player, heldItem, slot);
            return;
        }


        if (!hasEnoughMaterial(player, MATERIAL_ID, MATERIAL_COST)) {
            player.sendMessage(Message.raw("Not enough Iron Bars (need " + MATERIAL_COST + ")"));
            return;
        }

        sfxConfig.playReforgeStart(player);

        if (!consumeMaterial(player, MATERIAL_ID, MATERIAL_COST)) {
            player.sendMessage(Message.raw("Failed to consume materials"));
            return;
        }

        // Check for weapon break chance - use config if available, otherwise use defaults
        double breakChance;
        if (refinementConfig != null) {
            breakChance = refinementConfig.getBreakChance(currentLevel);
        } else {
            breakChance = BREAK_CHANCES[Math.min(currentLevel, BREAK_CHANCES.length - 1)];
        }
        if (Math.random() < breakChance) {

            player.getInventory().getHotbar().removeItemStackFromSlot(slot, 1, false, false);
            sfxConfig.playShatter(player);
            showWeaponShatter(player);
            return;
        }



        // Roll for upgrade outcome
        ReforgeOutcome outcome = rollReforgeOutcome(currentLevel);
        int newLevel = Math.max(0, Math.min(currentLevel + outcome.levelChange, MAX_UPGRADE_LEVEL));
        
        // Create the upgraded weapon with new ID
        String baseWeaponId = getBaseWeaponId(itemId);
        String newWeaponId = baseWeaponId + newLevel;
        
        // Try to create the upgraded weapon
        ItemStack upgradedWeapon = createUpgradedWeapon(heldItem, baseWeaponId, newLevel);
        if (upgradedWeapon == null) {
            player.sendMessage(Message.raw("Failed to upgrade weapon"));
            return;
        }

        // Replace the item in inventory
        player.getInventory().getHotbar().removeItemStackFromSlot(slot, 1, false, false);
        player.getInventory().getHotbar().addItemStackToSlot(slot, upgradedWeapon);

        // Play outcome sound and show feedback
        playOutcomeSound(player, outcome);
        showOutcomeFeedback(player, outcome, currentLevel, newLevel, upgradedWeapon, slot);
    }

    /**
     * Gets the upgrade level from the weapon ID based on server.lang pattern.
     * Pattern: Weapon_Name (base=0), Weapon_Name1 (+1), Weapon_Name2 (+2), Weapon_Name3 (+3)
     * E.g., "Weapon_Axe_Cobalt" = 0, "Weapon_Axe_Cobalt1" = 1, "Weapon_Axe_Cobalt2" = 2, "Weapon_Axe_Cobalt3" = 3
     */
    public static int getLevelFromWeaponId(String weaponId) {
        if (weaponId == null) return 0;
        
        // Check if ends with 1, 2, or 3 (server.lang pattern: base, +1, +2, +3)
        if (weaponId.length() > 0) {
            char lastChar = weaponId.charAt(weaponId.length() - 1);
            if (lastChar == '1') {
                return 1; // Level 1 = suffix "1"
            } else if (lastChar == '2') {
                return 2; // Level 2 = suffix "2"
            } else if (lastChar == '3') {
                return 3; // Level 3 = suffix "3"
            }
        }
        return 0; // Base level (no suffix)
    }

    /**
     * Gets the base weapon ID without the level suffix.
     * E.g., "Weapon_Axe_Cobalt1" -> "Weapon_Axe_Cobalt", "Weapon_Axe_Cobalt2" -> "Weapon_Axe_Cobalt"
     */
    private static String getBaseWeaponId(String weaponId) {
        if (weaponId == null) return null;
        
        // Remove trailing 1, 2, or 3 (server.lang pattern)
        if (weaponId.length() > 0) {
            char lastChar = weaponId.charAt(weaponId.length() - 1);
            if (lastChar == '1' || lastChar == '2' || lastChar == '3') {
                return weaponId.substring(0, weaponId.length() - 1);
            }
        }
        return weaponId; // Already base (no suffix)
    }



    /**
     * Creates an upgraded weapon ItemStack with the new level.
     * Items must be defined in resources (server.lang pattern: Weapon_Name1, Weapon_Name2, Weapon_Name3).
     */
    private ItemStack createUpgradedWeapon(ItemStack original, String baseId, int newLevel) {
        // Validate baseId pattern - should be like "Weapon_Axe_Cobalt"
        if (baseId == null || !baseId.startsWith("Weapon_")) {
            System.err.println("[ReforgeEquip] Invalid base weapon ID: " + baseId);
            return null;
        }
        
        // Create new item ID based on server.lang pattern
        // Level 0 = base (no suffix), Level 1 = "1", Level 2 = "2", Level 3 = "3"
        String newItemId;
        if (newLevel == 0) {
            newItemId = baseId; // Base level, no suffix
        } else {
            newItemId = baseId + newLevel; // Level 1 = "1", Level 2 = "2", Level 3 = "3"
        }

        // Create new ItemStack with the upgraded item ID
        try {
            // Try to create ItemStack using the item ID string constructor
            ItemStack newItemStack = new ItemStack(newItemId, 1);
            System.out.println("[ReforgeEquip] Created upgraded weapon: " + newItemId + " (level " + newLevel + ")");
            return newItemStack;
        } catch (Exception e) {
            System.err.println("[ReforgeEquip] Error creating upgraded weapon '" + newItemId + "': " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }





    private void showOutcomeFeedback(Player player, ReforgeOutcome outcome, int oldLevel, int newLevel, ItemStack weapon, short slot) {
        switch (outcome.type) {
            case DEGRADE:
                showUpgradeFailure(player, oldLevel, newLevel);
                break;
            case SAME:
                player.sendMessage(Message.raw("--------------------"));
                player.sendMessage(Message.raw("   Refine Failed    "));
                player.sendMessage(Message.raw("--------------------"));
                showCompactTooltip(player, weapon, slot);
                break;
            case UPGRADE:
                showUpgradeSuccess(player, oldLevel, newLevel);
                showDetailedStats(player, weapon, slot);
                break;
            case JACKPOT:
                player.sendMessage(Message.raw("**** JACKPOT! ****"));
                showDetailedStats(player, weapon, slot);
                break;
        }
    }


    private void playOutcomeSound(Player player, ReforgeOutcome outcome) {
        switch (outcome.type) {
            case DEGRADE:
                sfxConfig.playFail(player);
                break;
            case SAME:
                sfxConfig.playNoChange(player);
                break;
            case UPGRADE:
                sfxConfig.playSuccess(player);
                break;
            case JACKPOT:
                sfxConfig.playJackpot(player);
                break;
        }
    }

    private boolean isValidReforgebench(InteractionContext context) {
        if (sfxConfig == null || sfxConfig.getBenches() == null) return false;
        BlockPosition target = context.getTargetBlock();
        if (target == null) return false;
        Player player = getPlayerFromContext(context);
        if (player == null) return false;
        World world = player.getWorld();
        if (world == null) return false;
        Ref<ChunkStore> chunk = BlockModule.getBlockEntity(world, target.x, target.y, target.z);
        if (chunk == null) return false;
        BlockState state = BlockState.getBlockState(chunk, chunk.getStore());
        String blockId = state.getBlockType().getId();
        for (String bench : sfxConfig.getBenches()) {
            if (blockId.equals(bench)) return true;
        }
        return false;
    }

    /**
     * Checks if an item can be reforged.
     * Uses proper category check like PatchAssetsCommand:
     * 1. Check if item ID starts with "Weapon_" (existing behavior)
     * 2. Check if item has Categories containing "Items.Weapons"
     * 3. Check if item has Weapon structure (WeaponStats, DamageProperties, etc.)
     * Arrows are always excluded from refinement.
     */
    private boolean isWeapon(String itemId) {
        if (itemId == null) {
            return false;
        }
        
        // Always exclude arrows - they can't be reforged
        if (itemId.contains("Arrow") || itemId.toLowerCase().contains("arrow")) {
            return false;
        }
        
        // Check 1: If it starts with "Weapon_", it's definitely a weapon
        if (itemId.toLowerCase().startsWith("weapon_")) {
            return true;
        }
        
        // Check 2 & 3: Need to check item categories/structure
        // This is handled by isWeapon(ItemStack) which does the full check
        return false;
    }

    /**
     * Checks if an ItemStack is a weapon using proper category checks.
     * This method checks:
     * 1. Item ID starts with "Weapon_"
     * 2. Item has Categories containing "Items.Weapons"
     * 3. Item has Weapon structure (WeaponStats, DamageProperties, etc.)
     * Arrows are always excluded.
     */
    public static boolean isWeapon(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }
        
        String itemId = itemStack.getItemId();
        if (itemId == null) {
            return false;
        }
        
        // Always exclude arrows
        if (itemId.contains("Arrow") || itemId.toLowerCase().contains("arrow")) {
            return false;
        }
        
        // Check cache first
        Boolean cachedResult = getCachedWeaponCheck(itemId);
        if (cachedResult != null) {
            return cachedResult;
        }
        
        boolean isWeapon = false;
        
        // Check 1: Item ID starts with "Weapon_"
        if (itemId.toLowerCase().startsWith("weapon_")) {
            isWeapon = true;
        }
        
        // Check 2: Item has Categories containing "Items.Weapons"
        if (!isWeapon) {
            isWeapon = hasWeaponCategory(itemStack);
        }
        
        // Check 3: Item has Weapon structure
        if (!isWeapon) {
            isWeapon = hasWeaponStructure(itemStack);
        }
        
        // Cache the result
        cacheWeaponCheck(itemId, isWeapon);
        
        return isWeapon;
    }

    /**
     * Checks if the item has "Items.Weapons" category using reflection.
     * Returns true if the item has the proper weapon category.
     */
    private static boolean hasWeaponCategory(ItemStack itemStack) {
        try {
            if (itemStack.getItem() == null) {
                return false;
            }
            
            // Try to get the categories field via reflection
            Object item = itemStack.getItem();
            java.lang.reflect.Field categoriesField = null;
            
            // Try different field names that might contain categories
            for (String fieldName : new String[]{"categories", "Categories", "category", "getCategories"}) {
                try {
                    categoriesField = item.getClass().getField(fieldName);
                    if (categoriesField != null) {
                        break;
                    }
                } catch (NoSuchFieldException ignored) {
                }
            }
            
            if (categoriesField != null) {
                categoriesField.setAccessible(true);
                Object categoriesObj = categoriesField.get(item);
                
                if (categoriesObj instanceof java.util.List) {
                    @SuppressWarnings("unchecked")
                    java.util.List<Object> categories = (java.util.List<Object>) categoriesObj;
                    for (Object cat : categories) {
                        if (cat != null && cat.toString().contains("Items.Weapons")) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Reflection failed, fall back to false
        }
        
        return false;
    }

    /**
     * Checks if the item has weapon structure (WeaponStats, DamageProperties, AttackProperties, WeaponType).
     * This is a fallback check when category check fails.
     */
    private static boolean hasWeaponStructure(ItemStack itemStack) {
        try {
            if (itemStack.getItem() == null) {
                return false;
            }
            
            Object item = itemStack.getItem();
            
            // Check for weapon-related fields via reflection
            String[] weaponFields = {"weaponStats", "WeaponStats", "weapon", "Weapon", 
                                      "damageProperties", "DamageProperties", 
                                      "attackProperties", "AttackProperties",
                                      "weaponType", "WeaponType"};
            
            for (String fieldName : weaponFields) {
                try {
                    java.lang.reflect.Field field = item.getClass().getField(fieldName);
                    if (field != null) {
                        field.setAccessible(true);
                        Object value = field.get(item);
                        if (value != null) {
                            return true;
                        }
                    }
                } catch (NoSuchFieldException ignored) {
                }
            }
        } catch (Exception e) {
            // Reflection failed
        }
        
        return false;
    }

    /**
     * Gets cached weapon check result if still valid.
     */
    private static Boolean getCachedWeaponCheck(String itemId) {
        Long timestamp = cacheTimestamps.get(itemId);
        if (timestamp != null) {
            if (System.currentTimeMillis() - timestamp < CACHE_EXPIRY_MS) {
                return weaponCheckCache.get(itemId);
            } else {
                // Cache expired, remove entries
                weaponCheckCache.remove(itemId);
                cacheTimestamps.remove(itemId);
            }
        }
        return null;
    }

    /**
     * Caches the weapon check result.
     */
    private static void cacheWeaponCheck(String itemId, boolean isWeapon) {
        weaponCheckCache.put(itemId, isWeapon);
        cacheTimestamps.put(itemId, System.currentTimeMillis());
    }

    /**
     * Clears the weapon check cache.
     * Can be called when weapons are patched to refresh cache.
     */
    public static void clearWeaponCache() {
        weaponCheckCache.clear();
        cacheTimestamps.clear();
        System.out.println("[ReforgeEquip] Weapon check cache cleared");
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Merged from WeaponUpgradeTracker - Weapon Tracking Methods
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Initializes the weapon upgrade tracker.
     */
    public static void initialize(File pluginDataFolder) {
        System.out.println("[ReforgeEquip] Weapon tracking initialized");
    }

    /**
     * Gets upgrade level for a weapon from its ID.
     */
    public static int getUpgradeLevel(Player player, ItemStack weapon, short slot) {
        if (weapon == null) {
            return 0;
        }
        return getLevelFromWeaponId(weapon.getItemId());
    }

    /**
     * Sets upgrade level for a weapon.
     */
    public static void setUpgradeLevel(Player player, ItemStack weapon, short slot, int level) {
        // No-op: level is embedded in weapon ID
    }

    /**
     * Removes a weapon from tracking.
     */
    public static void removeWeapon(Player player, ItemStack weapon, short slot) {
        // No-op: no persistent tracking needed
    }

    /**
     * Saves all tracked weapons.
     */
    public static void saveAll() {
        System.out.println("[ReforgeEquip] Save called (no persistence needed)");
    }

    /**
     * Gets the count of tracked weapons.
     */
    public static int getTrackedWeaponCount() {
        return 0;
    }



    /**
     * Gets the upgrade name for a given level.
     */
    public static String getUpgradeName(int level) {
        if (level < 0 || level >= UPGRADE_NAMES.length) {
            return "";
        }
        return UPGRADE_NAMES[level];
    }

    /**
     * Gets the damage multiplier for a given level.
     */
    public static double getDamageMultiplier(int level) {
        double[] multipliers = {1.0, 1.10, 1.15, 1.25};
        if (level < 0 || level >= multipliers.length) {
            return 1.0;
        }
        return multipliers[level];
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Merged from WeaponTooltip - Tooltip and UI Methods
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Generates a formatted tooltip for a weapon.
     */
    public static void showWeaponTooltip(Player player, ItemStack weapon, short slot) {
        int level = getWeaponLevel(player, weapon, slot);

        if (level == 0) {
            // Base weapon - show upgrade potential
            player.sendMessage(Message.raw(""));
            player.sendMessage(Message.raw("[Upgradeable Weapon]"));
            player.sendMessage(Message.raw("Take to a Reforgebench to upgrade!"));
            player.sendMessage(Message.raw(""));
        } else {
            // Upgraded weapon - show stats
            showUpgradedWeaponTooltip(player, level);
        }
    }

    /**
     * Shows detailed tooltip for an upgraded weapon.
     */
    private static void showUpgradedWeaponTooltip(Player player, int level) {
        String upgradeName = getUpgradeName(level);
        double multiplier = getDamageMultiplier(level);
        String color = getColorForLevel(level);
        String stars = getStarsForLevel(level);

        player.sendMessage(Message.raw(""));
        player.sendMessage(Message.raw(color + "============================="));
        player.sendMessage(Message.raw(color + "|  " + stars + " " + upgradeName.toUpperCase() + " WEAPON " + stars + "  |"));
        player.sendMessage(Message.raw(color + "============================="));
        player.sendMessage(Message.raw(color + "| Upgrade Level: " + color + "+" + level + "        " + color + "|"));
        player.sendMessage(Message.raw(color + "| Damage Bonus: §6+" + String.format("%.0f", (multiplier - 1.0) * 100) + "%       " + color + "|"));
        player.sendMessage(Message.raw(color + "============================="));
        player.sendMessage(Message.raw(""));
    }

    /**
     * Shows a compact inline tooltip (for hotbar switching).
     */
    public static void showCompactTooltip(Player player, ItemStack weapon, short slot) {
        int level = getWeaponLevel(player, weapon, slot);

        if (level == 0) {
            return; // Don't show anything for base weapons
        }

        String color = getColorForLevel(level);
        String upgradeName = getUpgradeName(level);
        double multiplier = getDamageMultiplier(level);

        String tooltip = color + "⚔ " + upgradeName + " +" + level + " (+" +
                         String.format("%.0f", (multiplier - 1.0) * 100) + "% damage)";

        player.sendMessage(Message.raw(tooltip));
    }

    /**
     * Shows upgrade success animation.
     */
    public static void showUpgradeSuccess(Player player, int oldLevel, int newLevel) {
        String color = getColorForLevel(newLevel);

        player.sendMessage(Message.raw(""));
        player.sendMessage(Message.raw(color + "++++++++++++++++++++++++++++++++"));
        player.sendMessage(Message.raw(color + "        WEAPON UPGRADED!        "));
        player.sendMessage(Message.raw("      +" + oldLevel + "   ->  " + color + "+" + newLevel));
        player.sendMessage(Message.raw(color + "++++++++++++++++++++++++++++++++"));
        player.sendMessage(Message.raw(""));
    }

    /**
     * Shows upgrade failure animation.
     */
    public static void showUpgradeFailure(Player player, int oldLevel, int newLevel) {
        player.sendMessage(Message.raw(""));
        player.sendMessage(Message.raw("++++++++++++++++++++++++++++++++"));
        player.sendMessage(Message.raw("     WEAPON DEGRADED!           "));
        player.sendMessage(Message.raw("    +" + oldLevel + " ->   +" + newLevel));
        player.sendMessage(Message.raw("++++++++++++++++++++++++++++++++"));
        player.sendMessage(Message.raw(""));
    }

    /**
     * Shows weapon shatter animation.
     */
    public static void showWeaponShatter(Player player) {
        player.sendMessage(Message.raw(""));
        player.sendMessage(Message.raw("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"));
        player.sendMessage(Message.raw("      WEAPON SHATTERED!  "));
        player.sendMessage(Message.raw("    The weapon broke into pieces..."));
        player.sendMessage(Message.raw("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"));
        player.sendMessage(Message.raw(""));
    }

    /**
     * Shows weapon stats in a detailed format.
     */
    public static void showDetailedStats(Player player, ItemStack weapon, short slot) {
        int level = getWeaponLevel(player, weapon, slot);
        String itemId = weapon != null ? weapon.getItemId() : "null";
        String upgradeName = getUpgradeName(level);
        double multiplier = getDamageMultiplier(level);
        String color = getColorForLevel(level);
        String progressBar = createProgressBar(level, 3);

        player.sendMessage(Message.raw(""));
        player.sendMessage(Message.raw("============================="));
        player.sendMessage(Message.raw("  Weapon: " + itemId));
        player.sendMessage(Message.raw("  Upgrade: " + color + upgradeName + " +" + level));
        player.sendMessage(Message.raw("  Progress: " + progressBar + " (" + level + "/3)"));
        player.sendMessage(Message.raw("  Damage: x" + String.format("%.2f", multiplier) + " (" + String.format("%.0f", multiplier * 100) + "%)"));

        if (level < 3) {
            double nextMultiplier = getDamageMultiplier(level + 1);
            player.sendMessage(Message.raw("  Next Level: x" + String.format("%.2f", nextMultiplier) + " (" + String.format("%.0f", nextMultiplier * 100) + "%)"));
        } else {
            player.sendMessage(Message.raw("   MAX LEVEL ACHIEVED   "));
        }

        player.sendMessage(Message.raw("=============================="));
        player.sendMessage(Message.raw(""));
    }

    /**
     * Gets the weapon level, checking both refined and regular weapons.
     */
    private static int getWeaponLevel(Player player, ItemStack weapon, short slot) {
        if (weapon == null) {
            return 0;
        }

        String weaponId = weapon.getItemId();

        if (weaponId != null && weaponId.startsWith("Weapon_")) {
            // Refined weapon - get level from weapon ID suffix
            return getLevelFromWeaponId(weaponId);
        } else {
            // Regular weapon - get level from tracker
            return getUpgradeLevel(player, weapon, slot);
        }

    }

    /**
     * Gets the color code for an upgrade level.
     */
    private static String getColorForLevel(int level) {
        switch (level) {
            case 1: return "§a"; // Green for +1
            case 2: return "§b"; // Aqua for +2
            case 3: return "§6"; // Gold for +3
            default: return "§f"; // White for base
        }
    }

    /**
     * Gets star decoration for an upgrade level.
     */
    private static String getStarsForLevel(int level) {
        switch (level) {
            case 1: return "★";
            case 2: return "★★";
            case 3: return "★★★";
            default: return "";
        }
    }

    /**
     * Creates a progress bar for upgrade level.
     */
    public static String createProgressBar(int level, int maxLevel) {
        StringBuilder bar = new StringBuilder("[");

        for (int i = 0; i < maxLevel; i++) {
            if (i < level) {
                bar.append("+"); // Filled
            } else {
                bar.append(" "); // Empty
            }
        }

        bar.append("]");
        return bar.toString();
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Inner Classes
    // ══════════════════════════════════════════════════════════════════════════════

    private ReforgeOutcome rollReforgeOutcome(int currentLevel) {
        // Use config if available, otherwise use defaults
        double[] weights;
        if (refinementConfig != null) {
            double[] configWeights = refinementConfig.getReforgeWeights(currentLevel);
            weights = configWeights != null ? configWeights : REFORGE_WEIGHTS[Math.min(currentLevel, REFORGE_WEIGHTS.length - 1)];
        } else {
            weights = REFORGE_WEIGHTS[Math.min(currentLevel, REFORGE_WEIGHTS.length - 1)];
        }
        double random = Math.random(), cumulative = 0.0;
        cumulative += weights[0];
        if (random < cumulative) return new ReforgeOutcome(-1, OutcomeType.DEGRADE);
        cumulative += weights[1];
        if (random < cumulative) return new ReforgeOutcome(0, OutcomeType.SAME);
        cumulative += weights[2];
        if (random < cumulative) return new ReforgeOutcome(1, OutcomeType.UPGRADE);
        return new ReforgeOutcome(2, OutcomeType.JACKPOT);
    }

    private Player getPlayerFromContext(InteractionContext context) {
        Ref<EntityStore> owningEntity = context.getOwningEntity();
        if (owningEntity == null) return null;
        Store<EntityStore> store = owningEntity.getStore();
        if (store == null) return null;
        return store.getComponent(owningEntity, Player.getComponentType());
    }

    private boolean hasEnoughMaterial(Player player, String itemId, int requiredAmount) {
        int totalFound = countItemInContainer(player.getInventory().getStorage(), itemId) +
                countItemInContainer(player.getInventory().getHotbar(), itemId);
        return totalFound >= requiredAmount;
    }

    private int countItemInContainer(ItemContainer container, String itemId) {
        int count = 0;
        for (short i = 0; i < container.getCapacity(); i++) {
            ItemStack stack = container.getItemStack(i);
            if (stack != null && stack.getItemId().equalsIgnoreCase(itemId)) {
                count += stack.getQuantity();
            }
        }
        return count;
    }

    private boolean consumeMaterial(Player player, String itemId, int amount) {
        return consumeFromContainer(player.getInventory().getStorage(), itemId, amount) ||
                consumeFromContainer(player.getInventory().getHotbar(), itemId, amount);
    }

    private boolean consumeFromContainer(ItemContainer container, String itemId, int amount) {
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack != null && stack.getItemId().equalsIgnoreCase(itemId) && stack.getQuantity() >= amount) {
                container.removeItemStackFromSlot(slot, amount, false, false);
                return true;
            }
        }
        return false;
    }

    private static class ReforgeOutcome {
        final int levelChange;
        final OutcomeType type;
        ReforgeOutcome(int levelChange, OutcomeType type) {
            this.levelChange = levelChange;
            this.type = type;
        }
    }

    private enum OutcomeType {
        DEGRADE, SAME, UPGRADE, JACKPOT
    }
}
