package irai.mod.reforge.Interactions;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Config.SFXConfig;
import irai.mod.reforge.Socket.Essence;
import irai.mod.reforge.Socket.EssenceRegistry;
import irai.mod.reforge.Socket.SocketData;
import irai.mod.reforge.Socket.SocketManager;
import irai.mod.reforge.Util.DynamicTooltipUtils;

/**
 * Custom interaction for the Essence Socket Bench.
 * Handles essence socketting when players use the bench.
 * 
 * Slot layout:
 * - Slot 0: Equipment (weapon/armor) to socket essences into
 * - Slot 1-4: Essence items to socket
 * - Output: Updated equipment with socketed essences
 * 
 * Tier system: Consecutive essences of the same type determine the tier.
 * Example: "Life", "Life", "Life", "Fire" = Tier 3 Life, Tier 1 Fire
 */
@SuppressWarnings("removal")
public class EssenceSocketBench extends SimpleInteraction {

    public static final BuilderCodec<EssenceSocketBench> CODEC =
            BuilderCodec.builder(EssenceSocketBench.class, EssenceSocketBench::new, SimpleInteraction.CODEC).build();

    // Valid essence item IDs (base names)
    private static final String[] ESSENCE_ITEM_IDS = {
        "Ingredient_Fire_Essence",
        "Ingredient_Ice_Essence", 
        "Ingredient_Life_Essence",
        "Ingredient_Lightning_Essence",
        "Ingredient_Void_Essence",
        "Ingredient_Water_Essence"
    };
    
    // Map essence item IDs to essence types
    private static final java.util.Map<String, String> ESSENCE_TYPE_MAP = new java.util.HashMap<>();
    static {
        ESSENCE_TYPE_MAP.put("Ingredient_Fire_Essence", "FIRE");
        ESSENCE_TYPE_MAP.put("Ingredient_Ice_Essence", "ICE");
        ESSENCE_TYPE_MAP.put("Ingredient_Life_Essence", "LIFE");
        ESSENCE_TYPE_MAP.put("Ingredient_Lightning_Essence", "LIGHTNING");
        ESSENCE_TYPE_MAP.put("Ingredient_Void_Essence", "VOID");
        ESSENCE_TYPE_MAP.put("Ingredient_Water_Essence", "WATER");
    }
    
    private SFXConfig sfxConfig;

    public EssenceSocketBench() {
        this.sfxConfig = new SFXConfig();
    }

    public void setSfxConfig(SFXConfig sfxConfig) {
        this.sfxConfig = sfxConfig;
    }

    @Override
    protected void tick0(boolean firstRun, float time, @NonNullDecl InteractionType type,
                         @NonNullDecl InteractionContext context, @NonNullDecl CooldownHandler cooldownHandler) {
        super.tick0(firstRun, time, type, context, cooldownHandler);
        
        if (!firstRun || type != InteractionType.Use) return;

        Player player = getPlayerFromContext(context);
        if (player == null) return;

        // For now, use the player's inventory since we can't easily access the bench container
        // This interaction works with the held item and inventory essences
        ItemStack heldItem = context.getHeldItem();
        if (heldItem == null || heldItem.isEmpty()) {
            player.sendMessage(Message.raw("Hold an equipment item to socket essences"));
            return;
        }

        processEssenceSocket(player, heldItem, context);
    }

    /**
     * Process the essence socket operation using held item and inventory materials.
     * Only sockets ONE essence per interaction.
     */
    private void processEssenceSocket(Player player, ItemStack equipment, InteractionContext context) {
        // Validate equipment type
        boolean isWeapon = ReforgeEquip.isWeapon(equipment);
        boolean isArmor = ReforgeEquip.isArmor(equipment);
        
        if (!isWeapon && !isArmor) {
            player.sendMessage(Message.raw("This item cannot have sockets"));
            return;
        }

        // Get socket data from equipment
        SocketData socketData = SocketManager.getSocketData(equipment);
        if (socketData == null) {
            socketData = SocketData.fromDefaults(isWeapon ? "weapon" : "armor");
        }
        
        // Check if item has punched sockets (sockets list not empty)
        if (socketData.getSockets().isEmpty()) {
            player.sendMessage(Message.raw("This item has no sockets. Use Socket Punch Bench to add sockets first."));
            return;
        }
        
        // Check if there are any sockets available
        if (socketData.getMaxSockets() == 0) {
            player.sendMessage(Message.raw("This item has no sockets. Use Socket Punch Bench first."));
            return;
        }
        
        // Check if there are any empty sockets (not broken)
        if (!socketData.hasEmptySocket()) {
            player.sendMessage(Message.raw("All sockets are filled!"));
            // Show broken socket info if any
            if (socketData.hasBrokenSocket()) {
                player.sendMessage(Message.raw("Use Ingredient_Voidheart to repair broken sockets"));
            }
            return;
        }
        
        // If there's a broken socket and we have voidheart, auto-repair while filling
        // This consumes both voidheart and the essence
        if (socketData.hasBrokenSocket() && hasVoidheart(player)) {
            // Consume voidheart to repair
            consumeVoidheart(player);
            // Repair the broken socket
            socketData.repairBrokenSocket();
            // Now there's an empty socket available, proceed to fill it
        }
        // If broken socket but no voidheart, will skip it and fill the next empty socket

        // Find ONE essence in inventory (just find, don't consume yet)
        String foundEssence = findOneEssenceInInventory(player);
        
        if (foundEssence == null) {
            player.sendMessage(Message.raw("No essences found in inventory!"));
            player.sendMessage(Message.raw("Add an essence (Fire, Ice, Life, Lightning, Void, Water) to your inventory"));
            return;
        }

        // Display current socket status
        displaySocketStatus(player, socketData);
        
        // Socket only ONE essence at a time
        String essenceType = foundEssence;
        String essenceId = "Essence_" + capitalize(essenceType);
        
        // Debug: show what we're looking for
        player.sendMessage(Message.raw("Looking for: " + essenceId));
        
        // Check if essence type exists in registry
        if (!EssenceRegistry.get().exists(essenceId)) {
            player.sendMessage(Message.raw("Essence type not found in registry: " + essenceId));
            return;
        }
        
        // Try to socket the essence
        if (!SocketManager.socketEssence(socketData, essenceId)) {
            player.sendMessage(Message.raw("Could not socket the essence. Try again."));
            return;
        }
        
        // Consume the essence after successful socket
        consumeOneEssence(player, essenceType);

        // Update the equipment with socketed data
        short heldSlot = context.getHeldItemSlot();
        ItemStack updatedItem = SocketManager.withSocketData(equipment, socketData);
        player.getInventory().getHotbar().setItemStackForSlot(heldSlot, updatedItem);
        
        // Register tooltips for the socketed item using the updated item (has metadata)
        String itemId = equipment.getItemId();
        socketData.registerTooltips(updatedItem, itemId, isWeapon);
        DynamicTooltipUtils.refreshAllPlayers();
        
        // Play success sound
        sfxConfig.playSuccess(player);
        
        // Display results
        player.sendMessage(Message.raw(""));
        player.sendMessage(Message.raw("============================="));
        player.sendMessage(Message.raw("    ESSENCE SOCKETED!        "));
        player.sendMessage(Message.raw("  1 essence added!          "));
        
        // Display tiered effects
        displayTieredEffects(player, socketData, isWeapon);
        
        player.sendMessage(Message.raw("  Sockets: " + socketData.getCurrentSocketCount() + "/" + socketData.getMaxSockets() + "    "));
        player.sendMessage(Message.raw("============================="));
    }
    
    /**
     * Check if player has Ingredient_Voidheart in inventory.
     */
    private boolean hasVoidheart(Player player) {
        // Check hotbar
        ItemContainer hotbar = player.getInventory().getHotbar();
        for (short i = 0; i < hotbar.getCapacity(); i++) {
            ItemStack stack = hotbar.getItemStack(i);
            if (stack != null && !stack.isEmpty() && stack.getItemId().equals("Ingredient_Voidheart")) {
                return true;
            }
        }
        
        // Check storage
        ItemContainer storage = player.getInventory().getStorage();
        for (short i = 0; i < storage.getCapacity(); i++) {
            ItemStack stack = storage.getItemStack(i);
            if (stack != null && !stack.isEmpty() && stack.getItemId().equals("Ingredient_Voidheart")) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Repair a broken socket using Ingredient_Voidheart.
     */
    private void repairBrokenSocket(Player player, ItemStack equipment, InteractionContext context, 
                                     SocketData socketData, boolean isArmor) {
        // Consume the voidheart
        consumeVoidheart(player);
        
        // Repair the first broken socket
        socketData.repairBrokenSocket();
        
        // Update the equipment with repaired socket data
        short heldSlot = context.getHeldItemSlot();
        ItemStack updatedItem = SocketManager.withSocketData(equipment, socketData);
        player.getInventory().getHotbar().setItemStackForSlot(heldSlot, updatedItem);
        
        // Register tooltips for the item using the updated item (has metadata)
        String itemId = equipment.getItemId();
        socketData.registerTooltips(updatedItem, itemId, isArmor);
        DynamicTooltipUtils.refreshAllPlayers();
        
        // Play success sound
        sfxConfig.playSuccess(player);
        
        // Display results
        player.sendMessage(Message.raw(""));
        player.sendMessage(Message.raw("============================="));
        player.sendMessage(Message.raw("   SOCKET REPAIRED!         "));
        player.sendMessage(Message.raw("  +1 socket restored!       "));
        player.sendMessage(Message.raw("  Sockets: " + socketData.getCurrentSocketCount() + "/" + socketData.getMaxSockets() + "    "));
        player.sendMessage(Message.raw("============================="));
    }
    
    /**
     * Consumes one Ingredient_Voidheart from player's inventory.
     */
    private void consumeVoidheart(Player player) {
        // Try hotbar first
        ItemContainer hotbar = player.getInventory().getHotbar();
        for (short i = 0; i < hotbar.getCapacity(); i++) {
            ItemStack stack = hotbar.getItemStack(i);
            if (stack != null && !stack.isEmpty() && stack.getItemId().equals("Ingredient_Voidheart")) {
                hotbar.removeItemStackFromSlot(i, 1, false, false);
                return;
            }
        }
        
        // Then storage
        ItemContainer storage = player.getInventory().getStorage();
        for (short i = 0; i < storage.getCapacity(); i++) {
            ItemStack stack = storage.getItemStack(i);
            if (stack != null && !stack.isEmpty() && stack.getItemId().equals("Ingredient_Voidheart")) {
                storage.removeItemStackFromSlot(i, 1, false, false);
                return;
            }
        }
    }
    
    /**
     * Finds ONE random essence type from player's inventory (doesn't consume).
     * Returns a random essence if multiple are available.
     */
    private String findOneEssenceInInventory(Player player) {
        // Collect all available essence types from inventory
        java.util.List<String> availableEssences = new java.util.ArrayList<>();
        
        // Check hotbar
        ItemContainer hotbar = player.getInventory().getHotbar();
        for (short i = 0; i < hotbar.getCapacity(); i++) {
            ItemStack stack = hotbar.getItemStack(i);
            if (stack != null && !stack.isEmpty()) {
                String essenceType = getEssenceTypeFromItem(stack.getItemId());
                if (essenceType != null && !availableEssences.contains(essenceType)) {
                    availableEssences.add(essenceType);
                }
            }
        }
        
        // Check storage
        ItemContainer storage = player.getInventory().getStorage();
        for (short i = 0; i < storage.getCapacity(); i++) {
            ItemStack stack = storage.getItemStack(i);
            if (stack != null && !stack.isEmpty()) {
                String essenceType = getEssenceTypeFromItem(stack.getItemId());
                if (essenceType != null && !availableEssences.contains(essenceType)) {
                    availableEssences.add(essenceType);
                }
            }
        }
        
        if (availableEssences.isEmpty()) {
            return null;
        }
        
        // Return a random essence from the available ones
        int randomIndex = (int) (Math.random() * availableEssences.size());
        return availableEssences.get(randomIndex);
    }

    /**
     * Displays the current socket status of the equipment.
     */
    private void displaySocketStatus(Player player, SocketData socketData) {
        player.sendMessage(Message.raw(""));
        player.sendMessage(Message.raw("=== ESSENCE SOCKETING ==="));
        player.sendMessage(Message.raw("Current Sockets: " + socketData.getCurrentSocketCount() + "/" + socketData.getMaxSockets()));
        
        if (socketData.getCurrentSocketCount() > 0) {
            StringBuilder socketInfo = new StringBuilder("Filled: ");
            boolean first = true;
            for (irai.mod.reforge.Socket.Socket socket : socketData.getSockets()) {
                if (!first) socketInfo.append(", ");
                if (socket.isEmpty()) {
                    socketInfo.append("[Empty]");
                } else if (socket.isBroken()) {
                    socketInfo.append("[Broken]");
                } else {
                    String essenceId = socket.getEssenceId();
                    Essence essence = EssenceRegistry.get().getById(essenceId);
                    if (essence != null) {
                        socketInfo.append(essence.getType().name());
                    } else {
                        socketInfo.append(essenceId);
                    }
                }
                first = false;
            }
            player.sendMessage(Message.raw(socketInfo.toString()));
        }
    }

    /**
     * Displays the tiered effects based on consecutive essence types.
     */
    private void displayTieredEffects(Player player, SocketData socketData, boolean isWeapon) {
        // Calculate tiered effects
        java.util.Map<String, Integer> tierMap = calculateConsecutiveTiers(socketData);
        
        if (!tierMap.isEmpty()) {
            player.sendMessage(Message.raw("Active Effects:"));
            for (java.util.Map.Entry<String, Integer> entry : tierMap.entrySet()) {
                String essenceType = entry.getKey();
                int tier = entry.getValue();
                player.sendMessage(Message.raw("  " + essenceType + " T" + tier + " (" + getEffectDescription(essenceType, tier, isWeapon) + ")"));
            }
        }
    }

    /**
     * Calculates the tier for each essence type based on consecutive count.
     * Returns a map of essence type -> tier.
     */
    private java.util.Map<String, Integer> calculateConsecutiveTiers(SocketData socketData) {
        java.util.Map<String, Integer> tierMap = new java.util.LinkedHashMap<>();
        
        String currentType = null;
        int consecutiveCount = 0;
        
        for (irai.mod.reforge.Socket.Socket socket : socketData.getSockets()) {
            if (socket.isEmpty() || socket.isBroken()) {
                // Reset on empty/broken socket
                if (currentType != null && consecutiveCount > 0) {
                    tierMap.put(currentType, Math.min(consecutiveCount, 5));
                }
                currentType = null;
                consecutiveCount = 0;
                continue;
            }
            
            String essenceId = socket.getEssenceId();
            Essence essence = EssenceRegistry.get().getById(essenceId);
            if (essence == null) continue;
            
            String essenceType = essence.getType().name();
            
            if (essenceType.equals(currentType)) {
                consecutiveCount++;
            } else {
                // Save previous type if any
                if (currentType != null && consecutiveCount > 0) {
                    tierMap.put(currentType, Math.min(consecutiveCount, 5));
                }
                currentType = essenceType;
                consecutiveCount = 1;
            }
        }
        
        // Don't forget the last sequence
        if (currentType != null && consecutiveCount > 0) {
            tierMap.put(currentType, Math.min(consecutiveCount, 5));
        }
        
        return tierMap;
    }

    /**
     * Gets the effect description for an essence type and tier.
     */
    private String getEffectDescription(String essenceType, int tier, boolean isWeapon) {
        switch (essenceType) {
            case "FIRE":
                if (tier >= 5) return "+12% DMG or +15 Flat";
                if (tier >= 3) return "+6% DMG or +8 Flat";
                return "+2% DMG or +3 Flat";
            case "ICE":
                if (tier >= 5) return "+5% Freeze, +12 Cold DMG";
                if (tier >= 3) return "+5% Slow, +6 Cold DMG";
                return "+2% Slow, +2 Cold DMG";
            case "LIGHTNING":
                if (tier >= 5) return "+15% ATK Spd, +8% Crit";
                if (tier >= 3) return "+7% ATK Spd, +4% Crit";
                return "+3% ATK Spd, +2% Crit";
            case "LIFE":
                if (isWeapon) {
                    if (tier >= 5) return "+10% Lifesteal";
                    if (tier >= 3) return "+5% Lifesteal";
                    return "+2% Lifesteal";
                } else {
                    if (tier >= 5) return "+50 HP";
                    if (tier >= 3) return "+25 HP";
                    return "+10 HP";
                }
            case "VOID":
                if (tier >= 5) return "+25% Crit DMG";
                if (tier >= 3) return "+12% Crit DMG";
                return "+5% Crit DMG";
            case "WATER":
                // Water only works on armor
                if (tier >= 5) return "+10% Evasion";
                if (tier >= 3) return "+5% Evasion";
                return "+2% Evasion";
            default:
                return "Unknown";
        }
    }

    /**
     * Finds essence types in player's inventory (just finds, doesn't consume).
     */
    private java.util.List<String> findEssenceTypesInInventory(Player player) {
        java.util.List<String> foundEssences = new java.util.ArrayList<>();
        
        // Check hotbar
        ItemContainer hotbar = player.getInventory().getHotbar();
        for (short i = 0; i < hotbar.getCapacity(); i++) {
            ItemStack stack = hotbar.getItemStack(i);
            if (stack != null && !stack.isEmpty()) {
                String essenceType = getEssenceTypeFromItem(stack.getItemId());
                if (essenceType != null && !foundEssences.contains(essenceType)) {
                    foundEssences.add(essenceType);
                }
            }
        }
        
        // Check storage
        ItemContainer storage = player.getInventory().getStorage();
        for (short i = 0; i < storage.getCapacity(); i++) {
            ItemStack stack = storage.getItemStack(i);
            if (stack != null && !stack.isEmpty()) {
                String essenceType = getEssenceTypeFromItem(stack.getItemId());
                if (essenceType != null && !foundEssences.contains(essenceType)) {
                    foundEssences.add(essenceType);
                }
            }
        }
        
        return foundEssences;
    }

    /**
     * Consumes one essence of the specified type from player's inventory.
     */
    private void consumeOneEssence(Player player, String essenceType) {
        String itemId = getItemIdFromEssenceType(essenceType);
        if (itemId == null) return;
        
        // Try hotbar first
        ItemContainer hotbar = player.getInventory().getHotbar();
        for (short i = 0; i < hotbar.getCapacity(); i++) {
            ItemStack stack = hotbar.getItemStack(i);
            if (stack != null && !stack.isEmpty() && stack.getItemId().equals(itemId)) {
                hotbar.removeItemStackFromSlot(i, 1, false, false);
                return;
            }
        }
        
        // Then storage
        ItemContainer storage = player.getInventory().getStorage();
        for (short i = 0; i < storage.getCapacity(); i++) {
            ItemStack stack = storage.getItemStack(i);
            if (stack != null && !stack.isEmpty() && stack.getItemId().equals(itemId)) {
                storage.removeItemStackFromSlot(i, 1, false, false);
                return;
            }
        }
    }

    /**
     * Gets the item ID from essence type.
     */
    private String getItemIdFromEssenceType(String essenceType) {
        for (java.util.Map.Entry<String, String> entry : ESSENCE_TYPE_MAP.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(essenceType)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Gets the essence type from an item ID.
     */
    private String getEssenceTypeFromItem(String itemId) {
        return ESSENCE_TYPE_MAP.get(itemId);
    }

    /**
     * Capitalizes the first letter of a string.
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    /**
     * Gets the player from the interaction context.
     */
    private Player getPlayerFromContext(InteractionContext context) {
        Ref<EntityStore> owningEntity = context.getOwningEntity();
        if (owningEntity == null) return null;
        Store<EntityStore> store = owningEntity.getStore();
        if (store == null) return null;
        return store.getComponent(owningEntity, Player.getComponentType());
    }
}
