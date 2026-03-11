package irai.mod.reforge.Interactions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

import irai.mod.reforge.Common.ResonantRecipeUtils;
import irai.mod.reforge.Config.SFXConfig;
import irai.mod.reforge.Socket.Essence;
import irai.mod.reforge.Socket.Essence.Type;
import irai.mod.reforge.Socket.EssenceRegistry;
import irai.mod.reforge.Socket.ResonanceSystem;
import irai.mod.reforge.Socket.SocketData;
import irai.mod.reforge.Socket.SocketManager;
import irai.mod.reforge.UI.EssenceBenchUI;
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
        "Ingredient_Water_Essence",
        "Ingredient_Fire_Essence_Concentrated",
        "Ingredient_Ice_Essence_Concentrated",
        "Ingredient_Life_Essence_Concentrated",
        "Ingredient_Lightning_Essence_Concentrated",
        "Ingredient_Void_Essence_Concentrated",
        "Ingredient_Water_Essence_Concentrated"
    };
    
    // Map essence item IDs to essence types
    private static final Map<String, String> ESSENCE_TYPE_MAP = new HashMap<>();
    static {
        ESSENCE_TYPE_MAP.put("Ingredient_Fire_Essence", "FIRE");
        ESSENCE_TYPE_MAP.put("Ingredient_Ice_Essence", "ICE");
        ESSENCE_TYPE_MAP.put("Ingredient_Life_Essence", "LIFE");
        ESSENCE_TYPE_MAP.put("Ingredient_Lightning_Essence", "LIGHTNING");
        ESSENCE_TYPE_MAP.put("Ingredient_Void_Essence", "VOID");
        ESSENCE_TYPE_MAP.put("Ingredient_Water_Essence", "WATER");
        ESSENCE_TYPE_MAP.put("Ingredient_Fire_Essence_Concentrated", "FIRE");
        ESSENCE_TYPE_MAP.put("Ingredient_Ice_Essence_Concentrated", "ICE");
        ESSENCE_TYPE_MAP.put("Ingredient_Life_Essence_Concentrated", "LIFE");
        ESSENCE_TYPE_MAP.put("Ingredient_Lightning_Essence_Concentrated", "LIGHTNING");
        ESSENCE_TYPE_MAP.put("Ingredient_Void_Essence_Concentrated", "VOID");
        ESSENCE_TYPE_MAP.put("Ingredient_Water_Essence_Concentrated", "WATER");
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

        if (EssenceBenchUI.isAvailable()) {
            EssenceBenchUI.open(player);
            return;
        }

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
        String foundEssenceItemId = findOneEssenceInInventory(player);
        
        if (foundEssenceItemId == null) {
            player.sendMessage(Message.raw("No essences found in inventory!"));
            player.sendMessage(Message.raw("Add an essence (Fire, Ice, Life, Lightning, Void, Water) to your inventory"));
            return;
        }

        // Display current socket status
        displaySocketStatus(player, socketData);
        
        // Socket only ONE essence at a time
        String essenceType = SocketManager.resolveEssenceTypeFromItemId(foundEssenceItemId);
        String essenceId = SocketManager.resolveEssenceIdFromItemId(foundEssenceItemId);
        if (essenceType == null || essenceId == null) {
            player.sendMessage(Message.raw("Invalid essence selected: " + foundEssenceItemId));
            return;
        }
        
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
        consumeOneEssence(player, foundEssenceItemId);

        // Update the equipment with socketed data
        short heldSlot = context.getHeldItemSlot();
        ResonanceSystem.ResonanceResult rawResonance = ResonanceSystem.evaluate(equipment, socketData);
        boolean rawActive = rawResonance != null && rawResonance.active();
        ItemStack baseItem = equipment;
        if (rawActive) {
            String resonanceName = rawResonance.name();
            boolean alreadyUnlocked = SocketManager.isResonanceUnlocked(equipment, resonanceName);
            if (!alreadyUnlocked) {
                RecipeSlot recipeSlot = findMatchingRecipe(player, resonanceName);
                if (recipeSlot != null) {
                    ItemStack updatedRecipe = ResonantRecipeUtils.decrementUsage(recipeSlot.stack);
                    if (updatedRecipe != recipeSlot.stack) {
                        recipeSlot.container.setItemStackForSlot(recipeSlot.slot, updatedRecipe);
                    }
                    baseItem = SocketManager.withResonanceUnlock(equipment, resonanceName);
                    player.sendMessage(Message.raw("Resonance unlocked. 1 recipe usage consumed."));
                } else {
                    player.sendMessage(Message.raw("Resonance locked: completed recipe required."));
                }
            } else {
                baseItem = SocketManager.withResonanceUnlock(equipment, resonanceName);
            }
        }

        ItemStack updatedItem = SocketManager.withSocketData(baseItem, socketData);
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
        displayTieredEffects(player, updatedItem, socketData, isWeapon);
        
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

    private RecipeSlot findMatchingRecipe(Player player, String resonanceName) {
        if (player == null || resonanceName == null || resonanceName.isBlank()) {
            return null;
        }
        String normalized = ResonantRecipeUtils.normalizeRecipeName(resonanceName);
        RecipeSlot fromHotbar = findMatchingRecipeInContainer(player.getInventory().getHotbar(), normalized);
        if (fromHotbar != null) {
            return fromHotbar;
        }
        return findMatchingRecipeInContainer(player.getInventory().getStorage(), normalized);
    }

    private RecipeSlot findMatchingRecipeInContainer(ItemContainer container, String normalizedName) {
        if (container == null || normalizedName == null || normalizedName.isBlank()) {
            return null;
        }
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (!ResonantRecipeUtils.isResonantRecipeItem(stack)) {
                continue;
            }
            if (!ResonantRecipeUtils.isRecipeComplete(stack)) {
                continue;
            }
            ResonantRecipeUtils.UsageState usage = ResonantRecipeUtils.getUsageState(stack);
            if (!usage.hasRemaining()) {
                continue;
            }
            String recipeName = ResonantRecipeUtils.getRecipeName(stack);
            if (recipeName == null || recipeName.isBlank()) {
                continue;
            }
            if (!ResonantRecipeUtils.normalizeRecipeName(recipeName).equals(normalizedName)) {
                continue;
            }
            return new RecipeSlot(container, slot, stack);
        }
        return null;
    }

    private static final class RecipeSlot {
        final ItemContainer container;
        final short slot;
        final ItemStack stack;

        private RecipeSlot(ItemContainer container, short slot, ItemStack stack) {
            this.container = container;
            this.slot = slot;
            this.stack = stack;
        }
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
        // Collect available essence item IDs from inventory
        List<String> availableEssences = new ArrayList<>();
        
        // Check hotbar
        ItemContainer hotbar = player.getInventory().getHotbar();
        for (short i = 0; i < hotbar.getCapacity(); i++) {
            ItemStack stack = hotbar.getItemStack(i);
            if (stack != null && !stack.isEmpty()) {
                String essenceItemId = stack.getItemId();
                String essenceType = getEssenceTypeFromItem(essenceItemId);
                if (essenceType != null && !availableEssences.contains(essenceItemId)) {
                    availableEssences.add(essenceItemId);
                }
            }
        }
        
        // Check storage
        ItemContainer storage = player.getInventory().getStorage();
        for (short i = 0; i < storage.getCapacity(); i++) {
            ItemStack stack = storage.getItemStack(i);
            if (stack != null && !stack.isEmpty()) {
                String essenceItemId = stack.getItemId();
                String essenceType = getEssenceTypeFromItem(essenceItemId);
                if (essenceType != null && !availableEssences.contains(essenceItemId)) {
                    availableEssences.add(essenceItemId);
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
    private void displayTieredEffects(Player player, ItemStack itemWithMetadata, SocketData socketData, boolean isWeapon) {
        // Calculate tiered effects using SocketManager
        Map<String, Integer> tierMap = getTierMapFromSocketData(socketData);
        
        if (!tierMap.isEmpty()) {
            player.sendMessage(Message.raw("Active Effects:"));
            for (Map.Entry<String, Integer> entry : tierMap.entrySet()) {
                String essenceType = entry.getKey();
                int tier = entry.getValue();
                player.sendMessage(Message.raw("  " + essenceType + " T" + tier + " (" + getEffectDescription(itemWithMetadata, essenceType, tier, isWeapon) + ")"));
            }
        }
    }

    /**
     * Converts SocketManager's tier map (Essence.Type -> Integer) to String -> Integer for display.
     */
    private Map<String, Integer> getTierMapFromSocketData(SocketData socketData) {
        Map<Type, Integer> typeTierMap = SocketManager.calculateConsecutiveTiers(socketData);
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<Type, Integer> entry : typeTierMap.entrySet()) {
            result.put(entry.getKey().name(), entry.getValue());
        }
        return result;
    }

    /**
     * Gets the effect description for an essence type and tier.
     */
    private String getEffectDescription(ItemStack itemWithMetadata, String essenceType, int tier, boolean isWeapon) {
        try {
            Type type = Type.valueOf(essenceType);
            SocketData socketData = SocketManager.getSocketData(itemWithMetadata);
            if (socketData == null) {
                socketData = new SocketData(0);
            }
            return SocketManager.describeEssenceEffect(type, tier, isWeapon, socketData);
        } catch (Exception e) {
            return "Error";
        }
    }

    /**
     * Finds essence types in player's inventory (just finds, doesn't consume).
     */
    private List<String> findEssenceTypesInInventory(Player player) {
        List<String> foundEssences = new ArrayList<>();
        
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
     * Consumes one essence item from player's inventory.
     */
    private void consumeOneEssence(Player player, String essenceItemId) {
        if (essenceItemId == null || essenceItemId.isBlank()) return;
        
        // Try hotbar first
        ItemContainer hotbar = player.getInventory().getHotbar();
        for (short i = 0; i < hotbar.getCapacity(); i++) {
            ItemStack stack = hotbar.getItemStack(i);
            if (stack != null && !stack.isEmpty() && stack.getItemId().equals(essenceItemId)) {
                hotbar.removeItemStackFromSlot(i, 1, false, false);
                return;
            }
        }
        
        // Then storage
        ItemContainer storage = player.getInventory().getStorage();
        for (short i = 0; i < storage.getCapacity(); i++) {
            ItemStack stack = storage.getItemStack(i);
            if (stack != null && !stack.isEmpty() && stack.getItemId().equals(essenceItemId)) {
                storage.removeItemStackFromSlot(i, 1, false, false);
                return;
            }
        }
    }

    /**
     * Gets the essence type from an item ID.
     */
    private String getEssenceTypeFromItem(String itemId) {
        String fromMap = ESSENCE_TYPE_MAP.get(itemId);
        if (fromMap != null) {
            return fromMap;
        }
        return SocketManager.resolveEssenceTypeFromItemId(itemId);
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
