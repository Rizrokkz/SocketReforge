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
import irai.mod.reforge.Config.SocketConfig;
import irai.mod.reforge.Socket.Socket;
import irai.mod.reforge.Socket.SocketData;
import irai.mod.reforge.Socket.SocketManager;
import irai.mod.reforge.Socket.SocketManager.PunchResult;
import irai.mod.reforge.Socket.SocketManager.SupportMaterial;
import irai.mod.reforge.UI.SocketBenchUI;
import irai.mod.reforge.Util.DynamicTooltipUtils;

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
    private static final String[] SUPPORT_MATERIAL_IDS = {
            "Socket_Diffuser",
            "Socket_Guarantor",
            "Socket_Reinforcer",
            "Socket_Stabilizer",
            "Socket_Expander"
    };
    
    // Default costs (1 of each material)
    private static final int MAIN_MATERIAL_COST = 1;
    private static final int SUPPORT_MATERIAL_COST = 1;
    
    private SocketConfig config;

    public SocketPunchBench() {
        this.config = SocketManager.getConfig();
        this.sfxConfig = new SFXConfig();
    }
    private SFXConfig sfxConfig;

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

        // Prefer HyUI bench flow when available.
        // Keeps old interaction behavior as automatic fallback.
        if (SocketBenchUI.isAvailable()) {
            SocketBenchUI.open(player);
            return;
        }

        // For now, use the player's inventory since we can't easily access the bench container
        // This interaction works with the held item
        ItemStack heldItem = context.getHeldItem();
        if (heldItem == null || heldItem.isEmpty()) {
            player.sendMessage(Message.raw("Hold an equipment item to punch sockets"));
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
            player.sendMessage(Message.raw("This item cannot have sockets"));
            return;
        }

        // Check current socket count
        SocketData socketData = SocketManager.getSocketData(equipment);
        if (socketData == null) {
            socketData = SocketData.fromDefaults(isWeapon ? "weapon" : "armor");
        }
        
        // Check for main material in inventory
        if (!hasEnoughMaterial(player, MAIN_MATERIAL_ID, MAIN_MATERIAL_COST)) {
            player.sendMessage(Message.raw("Not enough Socket Punchers (need " + MAIN_MATERIAL_COST + ")"));
            player.sendMessage(Message.raw("Craft Socket Punchers at a workbench"));
            return;
        }

        // Check for support material (optional)
        String supportItemId = findSupportMaterialId(player);
        SupportMaterial supportMaterial = SocketManager.resolveSupportMaterial(supportItemId);
        boolean hasSupport = supportMaterial != SupportMaterial.NONE;

        // Calculate success and break chances
        int currentSockets = socketData.getCurrentSocketCount();
        int maxSockets = socketData.getMaxSockets();
        int previewMax = maxSockets;
        int previewCurrent = currentSockets;
        int baseMax = config != null
                ? (isWeapon ? config.getMaxSocketsWeapon() : config.getMaxSocketsArmor())
                : maxSockets;
        if (supportMaterial == SupportMaterial.SOCKET_EXPANDER) {
            int cap = baseMax > 0 ? baseMax + 1 : maxSockets + 1;
            previewMax = maxSockets >= cap ? maxSockets : Math.min(maxSockets + 1, cap);
        } else if (supportMaterial == SupportMaterial.SOCKET_DIFFUSER) {
            previewMax = Math.max(1, maxSockets - 1);
            previewCurrent = Math.min(previewCurrent, previewMax);
        }
        if (previewCurrent >= previewMax && previewMax == maxSockets) {
            player.sendMessage(Message.raw("This item has the maximum number of sockets (" + maxSockets + ")"));
            return;
        }

        double baseSuccessChance = config != null ? config.getSuccessChance(currentSockets) : 0.75;
        double baseBreakChance = config != null ? config.getBreakChance(currentSockets) : 0.10;

        // Support material bonuses
        double finalSuccessChance = baseSuccessChance;
        double finalBreakChance = baseBreakChance;
        switch (supportMaterial) {
            case SOCKET_STABILIZER -> finalBreakChance *= 0.50;
            case SOCKET_REINFORCER -> finalSuccessChance = Math.min(1.0, finalSuccessChance + 0.20);
            case SOCKET_GUARANTOR -> {
                if (currentSockets == 0) {
                    finalSuccessChance = 1.0;
                }
            }
            default -> {
                // NONE / SOCKET_EXPANDER / SOCKET_DIFFUSER have no success modifier
            }
        }

        // Show chances to player
        player.sendMessage(Message.raw(""));
        player.sendMessage(Message.raw("Equipment: " + equipment.getItemId()));
        player.sendMessage(Message.raw("Current Sockets: " + currentSockets + "/" + maxSockets));
        player.sendMessage(Message.raw("Success Chance: " + String.format("%.0f%%", finalSuccessChance * 100)));
        player.sendMessage(Message.raw("Break Chance: " + String.format("%.0f%%", finalBreakChance * 100)));
        if (hasSupport) {
            player.sendMessage(Message.raw("Support Material: " + supportItemId));
        } else {
            player.sendMessage(Message.raw("Tip: Add a Socket support material for improved effects"));
        }

        // Consume main material
        if (!consumeMaterial(player, MAIN_MATERIAL_ID, MAIN_MATERIAL_COST)) {
            player.sendMessage(Message.raw("Failed to consume materials"));
            return;
        }
        
        // Consume support material if present
        if (hasSupport) {
            consumeMaterial(player, supportItemId, SUPPORT_MATERIAL_COST);
        }

        boolean supportAdjusted = SocketManager.applySupportSocketLimit(socketData, supportMaterial, isWeapon);
        currentSockets = socketData.getCurrentSocketCount();
        maxSockets = socketData.getMaxSockets();
        if (currentSockets >= maxSockets) {
            if (supportAdjusted) {
                short adjustedSlot = context.getHeldItemSlot();
                ItemStack updatedItem = SocketManager.withSocketData(equipment, socketData);
                player.getInventory().getHotbar().setItemStackForSlot(adjustedSlot, updatedItem);
                socketData.registerTooltips(updatedItem, updatedItem.getItemId(), isWeapon);
                DynamicTooltipUtils.refreshAllPlayers();
                player.sendMessage(Message.raw("Support material applied. Max sockets updated to " + maxSockets + "."));
            } else {
                player.sendMessage(Message.raw("This item has the maximum number of sockets (" + maxSockets + ")"));
            }
            return;
        }

        // Use SocketManager to process the punch
        PunchResult result = SocketManager.punchSocket(socketData, supportMaterial);

        // Handle result
        switch (result) {
            case SUCCESS:
                short successSlot = context.getHeldItemSlot();
                ItemStack updatedItem = SocketManager.withSocketData(equipment, socketData);
                player.getInventory().getHotbar().setItemStackForSlot(successSlot, updatedItem);
                int newSocketCount = socketData.getCurrentSocketCount();
                sfxConfig.playSuccess(player);
                // Check for bonus 5th socket (1% chance when punching 4th socket)
                boolean bonusSocket = false;
                if (currentSockets == 3 && maxSockets == 4) {
                    // Only check once when punching the 4th socket
                    double bonusChance = config != null ? config.getBonusSocketChance() : 0.01;
                    if (Math.random() < bonusChance) {
                        socketData.setMaxSockets(5);
                        socketData.addSocket();
                        bonusSocket = true;
                        updatedItem = SocketManager.withSocketData(equipment, socketData);
                        player.getInventory().getHotbar().setItemStackForSlot(successSlot, updatedItem);
                    }
                }
                
                // Register tooltip for the socketed item
                socketData.registerTooltips(updatedItem, updatedItem.getItemId(), isWeapon);
                DynamicTooltipUtils.refreshAllPlayers();
                //SFXConfig.playSuccess(player);
                player.sendMessage(Message.raw(""));
                player.sendMessage(Message.raw("-----------------------------"));
                player.sendMessage(Message.raw("       SOCKET PUNCHED!       "));
                if (bonusSocket) {
                    player.sendMessage(Message.raw("   BONUS: 5th socket added!  "));
                } else {
                    player.sendMessage(Message.raw("      New socket added!      "));
                }
                player.sendMessage(Message.raw("   Sockets: " + socketData.getCurrentSocketCount() + "/" + socketData.getMaxSockets() + "    "));
                player.sendMessage(Message.raw("-----------------------------"));
                break;
                
            case BREAK:
                // Mark socket as broken (does not reduce max sockets)
                socketData.breakSocket();
                short brokenSlot = context.getHeldItemSlot();
                sfxConfig.playShatter(player);
                // Separate chance to also reduce max sockets (25%)
                boolean maxReduced = false;
                double maxReduceChance = config != null ? config.getMaxReduceChance() : 0.25;
                if (Math.random() < maxReduceChance && socketData.getMaxSockets() > 1) {
                    socketData.reduceMaxSockets();
                    maxReduced = true;
                }
                
                ItemStack brokenItem = SocketManager.withSocketData(equipment, socketData);
                player.getInventory().getHotbar().setItemStackForSlot(brokenSlot, brokenItem);
                
                // Update tooltip
                socketData.registerTooltips(brokenItem, brokenItem.getItemId(), isWeapon);
                DynamicTooltipUtils.refreshAllPlayers();
                
                int brokenCount = (int) socketData.getSockets().stream().filter(Socket::isBroken).count();
                player.sendMessage(Message.raw(""));
                player.sendMessage(Message.raw("---------------------------------"));
                player.sendMessage(Message.raw("  Socket punched but was broken  "));
                if (maxReduced) {
                    player.sendMessage(Message.raw("  Max Sockets reduced to " + socketData.getMaxSockets() + "  "));
                }
                player.sendMessage(Message.raw("  Broken: " + brokenCount + " | Total: " + socketData.getCurrentSocketCount() + "  "));
                player.sendMessage(Message.raw("---------------------------------"));
                break;
                
            case FAIL:
                sfxConfig.playFail(player);
                player.sendMessage(Message.raw(""));
                player.sendMessage(Message.raw("---------------------------------"));
                player.sendMessage(Message.raw("     Socket Punching Failed!     "));
                player.sendMessage(Message.raw("---------------------------------"));
                if (supportAdjusted) {
                    short failSlot = context.getHeldItemSlot();
                    ItemStack failItem = SocketManager.withSocketData(equipment, socketData);
                    player.getInventory().getHotbar().setItemStackForSlot(failSlot, failItem);
                    socketData.registerTooltips(failItem, failItem.getItemId(), isWeapon);
                    DynamicTooltipUtils.refreshAllPlayers();
                }
                break;
        }
    }

    private String findSupportMaterialId(Player player) {
        if (player == null) {
            return null;
        }
        for (String itemId : SUPPORT_MATERIAL_IDS) {
            if (hasEnoughMaterial(player, itemId, SUPPORT_MATERIAL_COST)) {
                return itemId;
            }
        }
        return null;
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
