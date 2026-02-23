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

import irai.mod.reforge.Config.SocketConfig;
import irai.mod.reforge.Socket.SocketData;
import irai.mod.reforge.Socket.SocketManager;
import irai.mod.reforge.Socket.SocketManager.PunchResult;
import irai.mod.reforge.Socket.SocketManager.SupportMaterial;

/**
 * Custom interaction for the Socket Punch Bench.
 * Handles socket punching when players use the bench.
 * 
 * Slot layout:
 * - Slot 0: Equipment (weapon/armor) to punch sockets into
 * - Slot 1: Main material (Socket_Puncher) - required for punching
 * - Slot 2: Supporting material (Socket_Stabilizer) - optional, increases success chance
 * - Slot 3 (output): Result item with new socket
 */
@SuppressWarnings("removal")
public class SocketPunchBench extends SimpleInteraction {

    public static final BuilderCodec<SocketPunchBench> CODEC =
            BuilderCodec.builder(SocketPunchBench.class, SocketPunchBench::new, SimpleInteraction.CODEC).build();

    // Item IDs for materials
    private static final String MAIN_MATERIAL_ID = "Socket_Puncher";
    private static final String SUPPORT_MATERIAL_ID = "Socket_Stabilizer";
    
    // Default costs (1 of each material)
    private static final int MAIN_MATERIAL_COST = 1;
    private static final int SUPPORT_MATERIAL_COST = 1;
    
    private SocketConfig config;

    public SocketPunchBench() {
        this.config = SocketManager.getConfig();
    }

    @Override
    protected void tick0(boolean firstRun, float time, @NonNullDecl InteractionType type,
                         @NonNullDecl InteractionContext context, @NonNullDecl CooldownHandler cooldownHandler) {
        super.tick0(firstRun, time, type, context, cooldownHandler);
        
        if (!firstRun || type != InteractionType.Use) return;

        Player player = getPlayerFromContext(context);
        if (player == null) return;

        // For now, use the player's inventory since we can't easily access the bench container
        // This interaction works with the held item
        ItemStack heldItem = context.getHeldItem();
        if (heldItem == null || heldItem.isEmpty()) {
            player.sendMessage(Message.raw("Â§eHold an equipment item to punch sockets"));
            return;
        }

        processSocketPunch(player, heldItem, context);
    }

    /**
     * Process the socket punch operation using held item and inventory materials.
     */
    private void processSocketPunch(Player player, ItemStack equipment, InteractionContext context) {
        // Validate equipment type
        boolean isWeapon = ReforgeEquip.isWeapon(equipment);
        boolean isArmor = ReforgeEquip.isArmor(equipment);
        
        if (!isWeapon && !isArmor) {
            player.sendMessage(Message.raw("Â§cThis item cannot have sockets"));
            return;
        }

        // Check current socket count
        SocketData socketData = SocketManager.getSocketData(equipment);
        if (socketData == null) {
            socketData = SocketData.fromDefaults(isWeapon ? "weapon" : "armor");
        }
        
        int currentSockets = socketData.getCurrentSocketCount();
        int maxSockets = socketData.getMaxSockets();

        if (currentSockets >= maxSockets) {
            player.sendMessage(Message.raw("Â§cThis item has the maximum number of sockets (" + maxSockets + ")"));
            return;
        }

        // Check for main material in inventory
        if (!hasEnoughMaterial(player, MAIN_MATERIAL_ID, MAIN_MATERIAL_COST)) {
            player.sendMessage(Message.raw("Â§cNot enough Socket Punchers (need " + MAIN_MATERIAL_COST + ")"));
            player.sendMessage(Message.raw("Â§7Craft Socket Punchers at a workbench"));
            return;
        }

        // Check for support material (optional)
        boolean hasSupport = hasEnoughMaterial(player, SUPPORT_MATERIAL_ID, SUPPORT_MATERIAL_COST);

        // Calculate success and break chances
        double baseSuccessChance = config != null ? config.getSuccessChance(currentSockets) : 0.75;
        double baseBreakChance = config != null ? config.getBreakChance(currentSockets) : 0.10;

        // Support material bonuses
        double supportBonus = hasSupport ? 0.15 : 0.0; // +15% success chance
        double breakReduction = hasSupport ? 0.05 : 0.0; // -5% break chance

        double finalSuccessChance = Math.min(1.0, baseSuccessChance + supportBonus);
        double finalBreakChance = Math.max(0.0, baseBreakChance - breakReduction);

        // Show chances to player
        player.sendMessage(Message.raw(""));
        player.sendMessage(Message.raw("Â§6â•â•â•â•â•â•â•â• Socket Punch â•â•â•â•â•â•â•â•"));
        player.sendMessage(Message.raw("Â§7Equipment: Â§f" + equipment.getItemId()));
        player.sendMessage(Message.raw("Â§7Current Sockets: Â§f" + currentSockets + "/" + maxSockets));
        player.sendMessage(Message.raw("Â§7Success Chance: Â§a" + String.format("%.0f%%", finalSuccessChance * 100)));
        player.sendMessage(Message.raw("Â§7Break Chance: Â§c" + String.format("%.0f%%", finalBreakChance * 100)));
        if (hasSupport) {
            player.sendMessage(Message.raw("Â§7Stabilizer Bonus: Â§e+" + String.format("%.0f%%", supportBonus * 100) + " success, -" + String.format("%.0f%%", breakReduction * 100) + " break"));
        } else {
            player.sendMessage(Message.raw("Â§7Tip: Â§eAdd Socket Stabilizer for better chances"));
        }
        player.sendMessage(Message.raw("Â§6â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•"));

        // Consume main material
        if (!consumeMaterial(player, MAIN_MATERIAL_ID, MAIN_MATERIAL_COST)) {
            player.sendMessage(Message.raw("Â§cFailed to consume materials"));
            return;
        }
        
        // Consume support material if present
        if (hasSupport) {
            consumeMaterial(player, SUPPORT_MATERIAL_ID, SUPPORT_MATERIAL_COST);
        }

        // Use SocketManager to process the punch
        SupportMaterial supportMaterial = hasSupport ? SupportMaterial.SOCKET_STABILIZER : SupportMaterial.NONE;
        PunchResult result = SocketManager.punchSocket(socketData, supportMaterial);

        // Handle result
        switch (result) {
            case SUCCESS:
                short successSlot = context.getHeldItemSlot();
                ItemStack updatedItem = SocketManager.withSocketData(equipment, socketData);
                player.getInventory().getHotbar().setItemStackForSlot(successSlot, updatedItem);
                int newSocketCount = socketData.getCurrentSocketCount();
                player.sendMessage(Message.raw(""));
                player.sendMessage(Message.raw("Â§aâ•”â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•—"));
                player.sendMessage(Message.raw("Â§aâ•‘     SOCKET PUNCHED!          â•‘"));
                player.sendMessage(Message.raw("Â§aâ•‘   New socket added!          â•‘"));
                player.sendMessage(Message.raw("Â§aâ•‘   Sockets: " + newSocketCount + "/" + maxSockets));
                player.sendMessage(Message.raw("Â§aâ•šâ•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•"));
                player.sendMessage(Message.raw(""));
                break;
                
            case FAIL:
                player.sendMessage(Message.raw(""));
                player.sendMessage(Message.raw("Â§eâ•”â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•—"));
                player.sendMessage(Message.raw("Â§eâ•‘     PUNCH FAILED             â•‘"));
                player.sendMessage(Message.raw("Â§eâ•‘  The socket punch failed...  â•‘"));
                player.sendMessage(Message.raw("Â§eâ•‘  Materials were consumed.    â•‘"));
                player.sendMessage(Message.raw("Â§eâ•šâ•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•"));
                player.sendMessage(Message.raw(""));
                break;
                
            case BREAK:
                // Remove the item from player's hand
                short slot = context.getHeldItemSlot();
                player.getInventory().getHotbar().removeItemStackFromSlot(slot, 1, false, false);
                player.sendMessage(Message.raw(""));
                player.sendMessage(Message.raw("Â§câ•”â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•—"));
                player.sendMessage(Message.raw("Â§câ•‘     ITEM SHATTERED!          â•‘"));
                player.sendMessage(Message.raw("Â§câ•‘   The equipment broke...     â•‘"));
                player.sendMessage(Message.raw("Â§câ•šâ•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•"));
                player.sendMessage(Message.raw(""));
                break;
        }
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

    /**
     * Checks if player has enough of the specified material.
     */
    private boolean hasEnoughMaterial(Player player, String itemId, int requiredAmount) {
        int totalFound = countItemInContainer(player.getInventory().getStorage(), itemId) +
                countItemInContainer(player.getInventory().getHotbar(), itemId);
        return totalFound >= requiredAmount;
    }

    /**
     * Counts items of a specific type in a container.
     */
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

    /**
     * Consumes material from player's inventory.
     */
    private boolean consumeMaterial(Player player, String itemId, int amount) {
        // Try storage first, then hotbar
        if (consumeFromContainer(player.getInventory().getStorage(), itemId, amount)) {
            return true;
        }
        return consumeFromContainer(player.getInventory().getHotbar(), itemId, amount);
    }

    /**
     * Consumes items from a specific container.
     */
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
}

