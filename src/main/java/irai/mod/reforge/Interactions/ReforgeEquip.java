package irai.mod.reforge.Interactions;

import java.io.File;
import java.util.regex.Pattern;

import org.bson.BsonDocument;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
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
import irai.mod.reforge.Util.DynamicTooltipUtils;
import irai.mod.reforge.Util.NameResolver;


/**
 * Weapon and armor reforging system using item ID-based level tracking.
 * Upgrade level is determined by the item ID suffix (e.g., Weapon_Axe_Cobalt1 = +1, Armor_Chest_Iron1 = +1).
 * No persistent tracking needed - level is embedded in the item ID.
 *
 * Merged functionality from WeaponUpgradeTracker and WeaponTooltip.
 * Supports both weapons (Weapon_ prefix) and armors (Armor_ prefix).
 */
@SuppressWarnings("removal")
public class ReforgeEquip extends SimpleInteraction {

    public static final BuilderCodec<ReforgeEquip> CODEC =
            BuilderCodec.builder(ReforgeEquip.class, ReforgeEquip::new, SimpleInteraction.CODEC).build();

    private static final String MATERIAL_ID = "Refinement_Glob";
    private static final int MATERIAL_COST = 3;
    private static final int MAX_UPGRADE_LEVEL = 3;
    private static final double[] BREAK_CHANCES = {
            0.010,  // 0 → 1: 10% break chance
            0.050,  // 1 → 2: 15% break chance
            0.075,  // 2 → 3: 20% break chance
    };

    private static final Pattern WEAPON_PATTERN = Pattern.compile("^(?!.*Arrow).*[Ww]eapon.*$");
    private static final Pattern ARMOR_PATTERN  = Pattern.compile("^.*[Aa]rmor.*$");

    // Static cache for item category/structure checks to avoid repeated reflection
    private static final java.util.Map<String, Boolean> weaponCheckCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, Boolean> armorCheckCache  = new java.util.concurrent.ConcurrentHashMap<>();
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

    // Armor upgrade level display names
    private static final String[] ARMOR_UPGRADE_NAMES = {
            "",           // Base
            "Sturdy",     // +1
            "Fortified",  // +2
            "Impenetrable" // +3
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
        boolean isWeaponItem = isWeapon(heldItem);
        boolean isArmorItem  = !isWeaponItem && isArmor(heldItem);

        if (!isWeaponItem && !isArmorItem) {
            player.sendMessage(Message.raw("This item cannot be reforged"));
            return;
        }

        String itemId = heldItem.getItemId();
        short slot = context.getHeldItemSlot();
        int currentLevel = getLevelFromItem(heldItem); // Check metadata first, then ID suffix

        if (currentLevel >= MAX_UPGRADE_LEVEL) {
            player.sendMessage(Message.raw((isArmorItem ? "Armor" : "Weapon") + " is already at max level"));
            showDetailedStats(player, heldItem, slot);
            return;
        }

        if (!hasEnoughMaterial(player, MATERIAL_ID, MATERIAL_COST)) {
            player.sendMessage(Message.raw("Not enough refinement globs (need " + MATERIAL_COST + ")"));
            return;
        }

        sfxConfig.playReforgeStart(player);

        if (!consumeMaterial(player, MATERIAL_ID, MATERIAL_COST)) {
            player.sendMessage(Message.raw("Failed to consume materials"));
            return;
        }

        // Check for item break chance - use config if available, otherwise use defaults
        double breakChance;
        if (refinementConfig != null) {
            breakChance = isArmorItem
                    ? refinementConfig.getArmorBreakChance(currentLevel)
                    : refinementConfig.getBreakChance(currentLevel);
        } else {
            breakChance = BREAK_CHANCES[Math.min(currentLevel, BREAK_CHANCES.length - 1)];
        }
        if (Math.random() < breakChance) {
            player.getInventory().getHotbar().removeItemStackFromSlot(slot, 1, false, false);
            sfxConfig.playShatter(player);
            showItemShatter(player, isArmorItem);
            return;
        }

        // Roll for upgrade outcome
        ReforgeOutcome outcome = rollReforgeOutcome(currentLevel);
        int newLevel = Math.max(0, Math.min(currentLevel + outcome.levelChange, MAX_UPGRADE_LEVEL));

        // Create the upgraded item with new ID
        String baseItemId = getBaseItemId(itemId);

        // Try to create the upgraded item
        ItemStack upgradedItem = createUpgradedItem(heldItem, baseItemId, newLevel);
        if (upgradedItem == null) {
            player.sendMessage(Message.raw("Failed to upgrade " + (isArmorItem ? "armor" : "weapon")));
            return;
        }

        // Replace the item in inventory
        player.getInventory().getHotbar().removeItemStackFromSlot(slot, 1, false, false);
        player.getInventory().getHotbar().addItemStackToSlot(slot, upgradedItem);

        // Register dynamic tooltips for the reforged item
        registerReforgeTooltip(upgradedItem, newLevel);

        // Play outcome sound and show feedback
        playOutcomeSound(player, outcome);
        showOutcomeFeedback(player, outcome, currentLevel, newLevel, upgradedItem, slot, isArmorItem);
    }

    /**
     * Gets the upgrade level from an item ID based on server.lang pattern.
     * Pattern: Item_Name (base=0), Item_Name1 (+1), Item_Name2 (+2), Item_Name3 (+3)
     * Works for both weapons (Weapon_*) and armors (Armor_*).
     * E.g., "Weapon_Axe_Cobalt" = 0, "Weapon_Axe_Cobalt1" = 1
     *       "Armor_Chest_Iron"  = 0, "Armor_Chest_Iron1"  = 1
     */
    public static int getLevelFromItemId(String itemId) {
        if (itemId == null) return 0;

        if (itemId.length() > 0) {
            char lastChar = itemId.charAt(itemId.length() - 1);
            if (lastChar == '1') return 1;
            if (lastChar == '2') return 2;
            if (lastChar == '3') return 3;
        }
        return 0; // Base level (no suffix)
    }
    
    private static final String META_REFINEMENT_LEVEL = "SocketReforge.Refinement.Level";
    private static final String META_BASE_ITEM_ID = "SocketReforge.Refinement.BaseItemId";
    private static final String META_REFINEMENT_NAME = "SocketReforge.Refinement.DisplayName";
    
    /**
     * Gets refinement level from item metadata.
     * Falls back to legacy ID suffix parsing when metadata is not present.
     */
    public static int getLevelFromItem(ItemStack item) {
        if (item == null || item.isEmpty()) return 0;

        Integer levelFromMetadata = item.getFromMetadataOrNull(META_REFINEMENT_LEVEL, Codec.INTEGER);
        if (levelFromMetadata != null) {
            return Math.max(0, Math.min(levelFromMetadata, 3));
        }

        return getLevelFromItemId(item.getItemId());
    }

    /**
     * Checks if the item has refinement metadata.
     */
    public static boolean hasRefinementMetadata(ItemStack item) {
        if (item == null || item.isEmpty()) return false;
        return item.getFromMetadataOrNull(META_REFINEMENT_LEVEL, Codec.INTEGER) != null;
    }
    
    /**
     * Applies the upgrade level to an item's metadata.
     * Stores the RESOLVED display name (not the translation key).
     */
    public static ItemStack withUpgradeLevel(ItemStack item, int level) {
        if (item == null || item.isEmpty()) {
            return item;
        }

        String baseId = getBaseItemId(item.getItemId());
        int clampedLevel = Math.max(0, Math.min(level, 3));
        ItemStack updated = item.withMetadata(META_REFINEMENT_LEVEL, Codec.INTEGER, clampedLevel);
        if (baseId != null) {
            updated = updated.withMetadata(META_BASE_ITEM_ID, Codec.STRING, baseId);
        }
        
        // Store the RESOLVED display name in metadata (not the translation key)
        // Get the translation key and resolve it to the actual localized name
        String translationKey = NameResolver.getTranslationKey(item);
        String resolvedName;
        if (translationKey != null && !translationKey.isEmpty()) {
            // Resolve the translation key to get the actual localized name
            resolvedName = NameResolver.resolveTranslationKey(translationKey);
        } else {
            // Fall back to item ID
            resolvedName = item.getItemId();
        }
        
        // Append refinement level if > 0
        if (clampedLevel > 0 && resolvedName != null) {
            resolvedName = resolvedName + " +" + clampedLevel;
        }
        
        if (resolvedName != null) {
            updated = updated.withMetadata(META_REFINEMENT_NAME, Codec.STRING, resolvedName);
        }
        
        return updated;
    }
    
    /**
     * Gets the display name from metadata, or falls back to translation properties.
     * Uses NameResolver for consistent name resolution across the mod.
     */
    public static String getDisplayNameFromMetadata(ItemStack item) {
        if (item == null || item.isEmpty()) return null;
        return NameResolver.getDisplayName(item);
    }
    
    /**
     * Gets the refined display name for an item based on its level.
     * Uses NameResolver to get the actual localized name.
     */
    private static String getRefinedDisplayName(ItemStack item, int level) {
        if (item == null || item.isEmpty()) return null;
        
        // Use NameResolver to get the actual localized display name
        String baseName = NameResolver.getBaseDisplayName(item);
        
        // Avoid stacking suffixes like "Sword +1 +2"
        baseName = baseName.replaceFirst("\\s\\+[123]$", "");
        
        // Append refinement level if > 0
        if (level > 0) {
            return baseName + " +" + level;
        }
        return baseName;
    }

    /**
     * Attempts to read an item's display name from metadata.
     */
    private static String getItemMetadataDisplayName(ItemStack item) {
        if (item == null || item.isEmpty()) return null;

        String[] candidateKeys = {
                META_REFINEMENT_NAME,
                "DisplayName",
                "displayName",
                "Name",
                "name",
                "Item.Name"
        };

        for (String key : candidateKeys) {
            String value = item.getFromMetadataOrNull(key, Codec.STRING);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }

        return null;
    }

    /**
     * Backwards-compatible alias for {@link #getLevelFromItemId(String)}.
     * @deprecated Use {@link #getLevelFromItemId(String)} instead.
     */
    @Deprecated
    public static int getLevelFromWeaponId(String weaponId) {
        return getLevelFromItemId(weaponId);
    }

    /**
     * Gets the base item ID without the level suffix.
     * Works for both weapons and armors.
     * E.g., "Weapon_Axe_Cobalt1" -> "Weapon_Axe_Cobalt"
     *       "Armor_Chest_Iron2"  -> "Armor_Chest_Iron"
     */
    public static String getBaseItemId(String itemId) {
        if (itemId == null) return null;

        if (itemId.length() > 0) {
            char lastChar = itemId.charAt(itemId.length() - 1);
            if (lastChar == '1' || lastChar == '2' || lastChar == '3') {
                return itemId.substring(0, itemId.length() - 1);
            }
        }
        return itemId; // Already base (no suffix)
    }

    /**
     * Backwards-compatible alias for {@link #getBaseItemId(String)}.
     * @deprecated Use {@link #getBaseItemId(String)} instead.
     */
    @Deprecated
    private static String getBaseWeaponId(String weaponId) {
        return getBaseItemId(weaponId);
    }

    /**
     * Creates an upgraded item (weapon or armor) ItemStack with the new level stored in metadata.
     * The item ID remains unchanged - refinement level is stored in metadata only.
     */
    private ItemStack createUpgradedItem(ItemStack original, String baseId, int newLevel) {
        if (baseId == null) {
            System.err.println("[ReforgeEquip] Invalid base item ID: null");
            return null;
        }

        if (original == null || original.isEmpty()) {
            System.err.println("[ReforgeEquip] Invalid original item for refinement");
            return null;
        }

        try {
            // Keep the same item ID - store refinement level in metadata only
            ItemStack upgraded = withUpgradeLevel(original, newLevel);
            return upgraded;
        } catch (Exception e) {
            System.err.println("[ReforgeEquip] Error creating upgraded item '" + baseId + "': " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Backwards-compatible alias for {@link #createUpgradedItem(ItemStack, String, int)}.
     * @deprecated Use {@link #createUpgradedItem(ItemStack, String, int)} instead.
     */
    @Deprecated
    private ItemStack createUpgradedWeapon(ItemStack original, String baseId, int newLevel) {
        return createUpgradedItem(original, baseId, newLevel);
    }





    private void showOutcomeFeedback(Player player, ReforgeOutcome outcome, int oldLevel, int newLevel, ItemStack item, short slot, boolean isArmor) {
        switch (outcome.type) {
            case DEGRADE:
                showUpgradeFailure(player, oldLevel, newLevel, isArmor);
                break;
            case SAME:
                player.sendMessage(Message.raw("--------------------"));
                player.sendMessage(Message.raw("   Refine Failed    "));
                player.sendMessage(Message.raw("--------------------"));
                showCompactTooltip(player, item, slot);
                break;
            case UPGRADE:
                showUpgradeSuccess(player, oldLevel, newLevel, isArmor);
                showDetailedStats(player, item, slot);
                break;
            case JACKPOT:
                player.sendMessage(Message.raw("**** JACKPOT! ****"));
                showDetailedStats(player, item, slot);
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
     * Checks if an item ID belongs to a weapon.
     * Arrows are always excluded from refinement.
     */
    private boolean isWeapon(String itemId) {
        if (itemId == null) return false;
        if (itemId.contains("Arrow") || itemId.toLowerCase().contains("arrow")) return false;
        if (itemId.toLowerCase().startsWith("weapon_")) return true;
        return false;
    }

    /**
     * Checks if an ItemStack is a weapon using proper category checks.
     * This method checks if the item has a weapon configuration.
     * Excludes arrows, projectiles, bombs, deployables, and other non-weapon items.
     */
    public static boolean isWeapon(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) return false;
        
        String itemId = itemStack.getItemId();
        Item item = itemStack.getItem();
        if (item == null || item == Item.UNKNOWN) return false;
        
        // Check getWeapon() - returns non-null for weapons
        Object weapon = item.getWeapon();
        
        // If getWeapon() returns non-null, apply arrow filters
        if (weapon != null) {
            if (itemId != null) {
                String itemIdLower = itemId.toLowerCase();
                // Exclude arrows and projectiles (whole word check)
                if (itemIdLower.contains("arrow") || itemIdLower.contains("bolt") || 
                    itemIdLower.contains("projectile") || itemIdLower.contains("ammo") ||
                    itemIdLower.contains("ammunition")) {
                    return false;
                }
                // Exclude bombs and explosives - use word boundaries
                if (itemIdLower.contains("_bomb") || itemIdLower.contains("bomb_") ||
                    itemIdLower.contains("_tnt") || itemIdLower.contains("tnt_") ||
                    itemIdLower.contains("_dynamite") || itemIdLower.contains("dynamite_") ||
                    itemIdLower.contains("explosive") || itemIdLower.contains("_mine")) {
                    return false;
                }
                // Exclude deployables and placeables
                if (itemIdLower.contains("deployable") || itemIdLower.contains("placeable") ||
                    itemIdLower.contains("turret") || itemIdLower.contains("trap") ||
                    itemIdLower.contains("totem") || itemIdLower.contains("banner") ||
                    itemIdLower.contains("flag") || itemIdLower.contains("ward")) {
                    return false;
                }
            }
            // getWeapon() is non-null and item passed filters
            return true;
        }
        
        // getWeapon() returned null - pass through to other checks (armor, category, etc.)
        return false;
    }

    /**
     * Checks if an ItemStack is a piece of armor using proper category checks.
     * This method checks if the item has an armor configuration.
     */
    public static boolean isArmor(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) return false;
        Item item = itemStack.getItem();
        if (item == null || item == Item.UNKNOWN) return false;
        
        // Check getArmor() first
        if (item.getArmor() != null) return true;
        
        // Fallback: check category metadata (handles modded items)
        return hasArmorCategory(itemStack);
    }

    /**
     * Checks if the item has "Items.Weapons" category using reflection.
     * Also checks getWeapon() method and getCategories() method directly.
     * Returns true if the item has the proper weapon category.
     */
    private static boolean hasWeaponCategory(ItemStack itemStack) {
        try {
            if (itemStack.getItem() == null) {
                return false;
            }
            
            Object item = itemStack.getItem();
            
            // Method 1: Call getCategories() directly - returns String[]
            try {
                java.lang.reflect.Method getCategoriesMethod = item.getClass().getMethod("getCategories");
                Object categoriesResult = getCategoriesMethod.invoke(item);
                if (categoriesResult instanceof String[]) {
                    String[] categories = (String[]) categoriesResult;
                    for (String cat : categories) {
                        if (cat != null) {
                            String catLower = cat.toLowerCase();
                            if (catLower.contains("items.weapon") || catLower.equals("weapon")) {
                                return true;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
            
            // Method 2: Check getWeapon() - returns non-null for weapons
            try {
                java.lang.reflect.Method getWeaponMethod = item.getClass().getMethod("getWeapon");
                Object weaponResult = getWeaponMethod.invoke(item);
                if (weaponResult != null) {
                    return true;
                }
            } catch (Exception ignored) {}
            
            // Method 3: Try to get the categories field via reflection from item object
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
                
                // Handle String array
                if (categoriesObj instanceof String[]) {
                    String[] categories = (String[]) categoriesObj;
                    for (String cat : categories) {
                        if (cat != null) {
                            String catLower = cat.toLowerCase();
                            if (catLower.contains("items.weapon") || catLower.equals("weapon")) {
                                return true;
                            }
                        }
                    }
                }
                // Handle List
                else if (categoriesObj instanceof java.util.List) {
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
     * Checks if the item has "Items.Armor" category using reflection.
     * Also checks getArmor() method and getCategories() method directly.
     */
    private static boolean hasArmorCategory(ItemStack itemStack) {
        try {
            if (itemStack.getItem() == null) return false;
            Object item = itemStack.getItem();
            
            // Method 1: Call getCategories() directly - returns String[]
            try {
                java.lang.reflect.Method getCategoriesMethod = item.getClass().getMethod("getCategories");
                Object categoriesResult = getCategoriesMethod.invoke(item);
                if (categoriesResult instanceof String[]) {
                    String[] categories = (String[]) categoriesResult;
                    for (String cat : categories) {
                        if (cat != null) {
                            String catLower = cat.toLowerCase();
                            if (catLower.contains("items.armor") || catLower.contains("items.armour") || catLower.equals("armor")) {
                                return true;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
            
            // Method 2: Check getArmor() - returns non-null for armor
            try {
                java.lang.reflect.Method getArmorMethod = item.getClass().getMethod("getArmor");
                Object armorResult = getArmorMethod.invoke(item);
                if (armorResult != null) {
                    return true;
                }
            } catch (Exception ignored) {}
            
            // Method 3: Try to get the categories field via reflection from item object
            java.lang.reflect.Field categoriesField = null;
            for (String fieldName : new String[]{"categories", "Categories", "category"}) {
                try {
                    categoriesField = item.getClass().getField(fieldName);
                    if (categoriesField != null) break;
                } catch (NoSuchFieldException ignored) {}
            }
            if (categoriesField != null) {
                categoriesField.setAccessible(true);
                Object categoriesObj = categoriesField.get(item);
                // Handle String array
                if (categoriesObj instanceof String[]) {
                    String[] categories = (String[]) categoriesObj;
                    for (String cat : categories) {
                        if (cat != null) {
                            String catLower = cat.toLowerCase();
                            if (catLower.contains("items.armor") || catLower.contains("items.armour") || catLower.equals("armor")) {
                                return true;
                            }
                        }
                    }
                }
                // Handle List
                else if (categoriesObj instanceof java.util.List) {
                    @SuppressWarnings("unchecked")
                    java.util.List<Object> categories = (java.util.List<Object>) categoriesObj;
                    for (Object cat : categories) {
                        if (cat != null && (cat.toString().contains("Items.Armor") || cat.toString().contains("Items.Armour"))) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Reflection failed
        }
        return false;
    }

    /**
     * Checks if the item has armor structure (ArmorStats, DefenseProperties, etc.).
     * This is a fallback check when category check fails.
     */
    private static boolean hasArmorStructure(ItemStack itemStack) {
        try {
            if (itemStack.getItem() == null) return false;
            Object item = itemStack.getItem();
            String[] armorFields = {"armorStats", "ArmorStats", "armor", "Armor",
                                    "defenseProperties", "DefenseProperties",
                                    "armorType", "ArmorType",
                                    "protection", "Protection"};
            for (String fieldName : armorFields) {
                try {
                    java.lang.reflect.Field field = item.getClass().getField(fieldName);
                    if (field != null) {
                        field.setAccessible(true);
                        Object value = field.get(item);
                        if (value != null) return true;
                    }
                } catch (NoSuchFieldException ignored) {}
            }
        } catch (Exception e) {
            // Reflection failed
        }
        return false;
    }

    /**
     * Gets cached check result from the given cache map if still valid.
     */
    private static Boolean getCachedCheck(java.util.Map<String, Boolean> cache, String itemId) {
        Long timestamp = cacheTimestamps.get(itemId);
        if (timestamp != null) {
            if (System.currentTimeMillis() - timestamp < CACHE_EXPIRY_MS) {
                return cache.get(itemId);
            } else {
                cache.remove(itemId);
                cacheTimestamps.remove(itemId);
            }
        }
        return null;
    }

    /**
     * Caches a check result in the given cache map.
     */
    private static void cacheCheck(java.util.Map<String, Boolean> cache, String itemId, boolean value) {
        cache.put(itemId, value);
        cacheTimestamps.put(itemId, System.currentTimeMillis());
    }

    /**
     * Clears all item check caches (weapon and armor).
     * Can be called when items are patched to refresh cache.
     */
    public static void clearWeaponCache() {
        weaponCheckCache.clear();
        armorCheckCache.clear();
        cacheTimestamps.clear();
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Merged from WeaponUpgradeTracker - Weapon Tracking Methods
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Initializes the weapon upgrade tracker.
     */
    public static void initialize(File pluginDataFolder) {
        // No-op: no persistent tracking needed
    }

    /**
     * Gets upgrade level for a weapon from its metadata or ID.
     */
    public static int getUpgradeLevel(Player player, ItemStack weapon, short slot) {
        if (weapon == null) {
            return 0;
        }
        return getLevelFromItem(weapon); // Check metadata first, then ID suffix
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
        // No-op: no persistence needed
    }

    /**
     * Gets the count of tracked weapons.
     */
    public static int getTrackedWeaponCount() {
        return 0;
    }



    /**
     * Gets the upgrade name for a given level (weapon).
     */
    public static String getUpgradeName(int level) {
        if (level < 0 || level >= UPGRADE_NAMES.length) return "";
        return UPGRADE_NAMES[level];
    }

    /**
     * Gets the armor upgrade name for a given level.
     */
    public static String getArmorUpgradeName(int level) {
        if (level < 0 || level >= ARMOR_UPGRADE_NAMES.length) return "";
        return ARMOR_UPGRADE_NAMES[level];
    }

    /**
     * Gets the damage multiplier for a given level.
     */
    public static double getDamageMultiplier(int level) {
        double[] multipliers = {1.0, 1.10, 1.15, 1.25};
        if (level < 0 || level >= multipliers.length) return 1.0;
        return multipliers[level];
    }

    /**
     * Gets the defense multiplier for a given armor level.
     * Defaults: +0%/+8%/+12%/+20% defense.
     */
    public static double getDefenseMultiplier(int level) {
        double[] multipliers = {1.0, 1.08, 1.12, 1.20};
        if (level < 0 || level >= multipliers.length) return 1.0;
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
            player.sendMessage(Message.raw(""));
            player.sendMessage(Message.raw("[Upgradeable Weapon]"));
            player.sendMessage(Message.raw("Take to a Reforgebench to upgrade!"));
            player.sendMessage(Message.raw(""));
        } else {
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
        player.sendMessage(Message.raw(color + "| Damage Bonus: +" + String.format("%.0f", (multiplier - 1.0) * 100) + "%       " + color + "|"));
        player.sendMessage(Message.raw(color + "============================="));
        player.sendMessage(Message.raw(""));
    }

    /**
     * Shows a compact inline tooltip (for hotbar switching).
     * Handles both weapons and armor.
     */
    public static void showCompactTooltip(Player player, ItemStack item, short slot) {
        int level = getWeaponLevel(player, item, slot);

        if (level == 0) return; // Don't show anything for base items

        String color = getColorForLevel(level);
        boolean isArmorItem = isArmor(item);

        if (isArmorItem) {
            String upgradeName = getArmorUpgradeName(level);
            double multiplier = getDefenseMultiplier(level);
            String tooltip = color + "🛡 " + upgradeName + " +" + level + " (+" +
                             String.format("%.0f", (multiplier - 1.0) * 100) + "% defense)";
            player.sendMessage(Message.raw(tooltip));
        } else {
            String upgradeName = getUpgradeName(level);
            double multiplier = getDamageMultiplier(level);
            String tooltip = color + "⚔ " + upgradeName + " +" + level + " (+" +
                             String.format("%.0f", (multiplier - 1.0) * 100) + "% damage)";
            player.sendMessage(Message.raw(tooltip));
        }
    }

    /**
     * Shows upgrade success animation.
     * @param isArmor true if the upgraded item is armor, false for weapons
     */
    public static void showUpgradeSuccess(Player player, int oldLevel, int newLevel, boolean isArmor) {
        String color = getColorForLevel(newLevel);
        String label = isArmor ? "ARMOR UPGRADED!" : "WEAPON UPGRADED!";

        player.sendMessage(Message.raw(""));
        player.sendMessage(Message.raw(color + "++++++++++++++++++++++++++++++++"));
        player.sendMessage(Message.raw(color + "        " + label + "        "));
        player.sendMessage(Message.raw("      +" + oldLevel + "   ->  " + color + "+" + newLevel));
        player.sendMessage(Message.raw(color + "++++++++++++++++++++++++++++++++"));
        player.sendMessage(Message.raw(""));
    }

    /**
     * Backwards-compatible overload for {@link #showUpgradeSuccess(Player, int, int, boolean)}.
     */
    public static void showUpgradeSuccess(Player player, int oldLevel, int newLevel) {
        showUpgradeSuccess(player, oldLevel, newLevel, false);
    }

    /**
     * Shows upgrade failure (degrade) animation.
     * @param isArmor true if the item is armor, false for weapons
     */
    public static void showUpgradeFailure(Player player, int oldLevel, int newLevel, boolean isArmor) {
        String label = isArmor ? "ARMOR DEGRADED!" : "WEAPON DEGRADED!";
        player.sendMessage(Message.raw(""));
        player.sendMessage(Message.raw("++++++++++++++++++++++++++++++++"));
        player.sendMessage(Message.raw("     " + label + "           "));
        player.sendMessage(Message.raw("    +" + oldLevel + " ->   +" + newLevel));
        player.sendMessage(Message.raw("++++++++++++++++++++++++++++++++"));
        player.sendMessage(Message.raw(""));
    }

    /**
     * Backwards-compatible overload for {@link #showUpgradeFailure(Player, int, int, boolean)}.
     */
    public static void showUpgradeFailure(Player player, int oldLevel, int newLevel) {
        showUpgradeFailure(player, oldLevel, newLevel, false);
    }

    /**
     * Shows item shatter animation (weapon or armor).
     * @param isArmor true if the shattered item is armor, false for weapons
     */
    public static void showItemShatter(Player player, boolean isArmor) {
        String label = isArmor ? "ARMOR SHATTERED!" : "WEAPON SHATTERED!";
        String detail = isArmor ? "The armor broke into pieces..." : "The weapon broke into pieces...";
        player.sendMessage(Message.raw(""));
        player.sendMessage(Message.raw("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"));
        player.sendMessage(Message.raw("      " + label + "  "));
        player.sendMessage(Message.raw("    " + detail));
        player.sendMessage(Message.raw("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"));
        player.sendMessage(Message.raw(""));
    }

    /**
     * Backwards-compatible alias for {@link #showItemShatter(Player, boolean)}.
     */
    public static void showWeaponShatter(Player player) {
        showItemShatter(player, false);
    }

    /**
     * Shows item stats in a detailed format.
     * Handles both weapons and armor.
     */
    public static void showDetailedStats(Player player, ItemStack item, short slot) {
        int level = getWeaponLevel(player, item, slot);
        String itemId = item != null ? item.getItemId() : "null";
        boolean isArmorItem = isArmor(item);

        String upgradeName = isArmorItem ? getArmorUpgradeName(level) : getUpgradeName(level);
        double multiplier   = isArmorItem ? getDefenseMultiplier(level) : getDamageMultiplier(level);
        String statLabel    = isArmorItem ? "Defense" : "Damage";
        String typeLabel    = isArmorItem ? "Armor" : "Weapon";
        String color = getColorForLevel(level);
        String progressBar = createProgressBar(level, 3);

        player.sendMessage(Message.raw(""));
        player.sendMessage(Message.raw("============================="));
        player.sendMessage(Message.raw("  " + typeLabel + ": " + itemId));
        player.sendMessage(Message.raw("  Upgrade: " + color + upgradeName + " +" + level));
        player.sendMessage(Message.raw("  Progress: " + progressBar + " (" + level + "/3)"));
        player.sendMessage(Message.raw("  " + statLabel + ": x" + String.format("%.2f", multiplier) + " (" + String.format("%.0f", multiplier * 100) + "%)"));

        if (level < 3) {
            double nextMultiplier = isArmorItem ? getDefenseMultiplier(level + 1) : getDamageMultiplier(level + 1);
            player.sendMessage(Message.raw("  Next Level: x" + String.format("%.2f", nextMultiplier) + " (" + String.format("%.0f", nextMultiplier * 100) + "%)"));
        } else {
            player.sendMessage(Message.raw("   MAX LEVEL ACHIEVED   "));
        }

        player.sendMessage(Message.raw("=============================="));
        player.sendMessage(Message.raw(""));
    }

    /**
     * Gets the item level, checking metadata first, then ID suffix.
     */
    private static int getWeaponLevel(Player player, ItemStack item, short slot) {
        if (item == null) return 0;

        // Check metadata first, then fall back to ID suffix
        return getLevelFromItem(item);
    }

    /**
     * Gets the color code for an upgrade level.
     */
    private static String getColorForLevel(int level) {
        switch (level) {
            case 1: return ""; // Green for +1
            case 2: return ""; // Aqua for +2
            case 3: return ""; // Gold for +3
            default: return ""; // White for base
        }
    }

    /**
     * Gets star decoration for an upgrade level.
     */
    private static String getStarsForLevel(int level) {
        switch (level) {
            case 1: return "*";
            case 2: return "**";
            case 3: return "***";
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

    /**
     * Registers dynamic tooltips for a reforged item using DynamicTooltipsLib.
     */
    private static void registerReforgeTooltip(ItemStack item, int level) {
        if (level <= 0) return;
        
        String itemId = item.getItemId();
        boolean isArmor = itemId != null && ARMOR_PATTERN.matcher(itemId).matches();
        
        // Use utility class for DynamicTooltipsLib integration
        if (!DynamicTooltipUtils.isAvailable()) {
            return;
        }
        
        // Add upgrade tier tooltip
        String upgradeName = isArmor ? getArmorUpgradeName(level) : getUpgradeName(level);
        double multiplier = isArmor ? getDefenseMultiplier(level) : getDamageMultiplier(level);
        int percentBonus = (int) ((multiplier - 1.0) * 100);
        
        DynamicTooltipUtils.addReforgeTooltip(itemId, upgradeName, level, percentBonus, isArmor);
        DynamicTooltipUtils.refreshAllPlayers();
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
