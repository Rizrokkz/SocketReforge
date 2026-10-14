package irai.mod.reforge.Entity.Events;

import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.World;
import org.joml.Vector3i;

import irai.mod.reforge.Common.AscensionUtils;
import irai.mod.reforge.Common.WorldBlockUtils;
import irai.mod.reforge.Config.AscensionConfig;
import irai.mod.reforge.Interactions.ReforgeEquip;

public final class AscensionStatueListener {

    private static final String STATUE_BLOCK_ID = "Furniture_Temple_Light_Statue";

    private AscensionStatueListener() {}

    public static void onPlayerInteract(PlayerInteractEvent event) {
        if (event == null) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null || player.getWorld() == null) {
            return;
        }

        InteractionType action = event.getActionType();
        if (action != InteractionType.Use) {
            return;
        }

        if (event.getTargetBlock() == null) {
            return;
        }

        World world = player.getWorld();
        Vector3i targetPos = event.getTargetBlock();
        BlockType blockType = getBlockType(world, targetPos.x, targetPos.y, targetPos.z);
        String blockId = blockType != null ? blockType.getId() : null;

        if (blockId == null || (!blockId.equals(STATUE_BLOCK_ID) && !blockId.toLowerCase().contains("statue"))) {
            return;
        }

        short selectedSlot = player.getInventory() != null ? player.getInventory().getActiveHotbarSlot() : -1;
        ItemStack heldItem = null;
        if (selectedSlot >= 0) {
            ItemContainer hotbar = player.getInventory().getHotbar();
            if (hotbar != null) {
                heldItem = hotbar.getItemStack(selectedSlot);
            }
        }
        if (heldItem == null || heldItem.isEmpty()) {
            player.getPlayerRef().sendMessage(Message.raw("Hold a maxed refinement weapon or armor to ascend."));
            return;
        }

        AscensionConfig config = AscensionUtils.getConfig();
        if (!config.isEnabled()) {
            player.getPlayerRef().sendMessage(Message.raw("Ascension is currently disabled."));
            return;
        }

        if (!ReforgeEquip.isWeapon(heldItem) && !ReforgeEquip.isArmor(heldItem)) {
            player.getPlayerRef().sendMessage(Message.raw("Only weapons and armor can be ascended."));
            return;
        }

        if (!AscensionUtils.isMaxRefinement(heldItem)) {
            int currentLevel = ReforgeEquip.getLevelFromItem(heldItem);
            int maxLevel = ReforgeEquip.getMaxUpgradeLevel();
            player.getPlayerRef().sendMessage(Message.raw("Equipment must be at max refinement level (" + maxLevel + ") to ascend. Current: " + currentLevel));
            return;
        }

        int currentTier = AscensionUtils.getItemAscensionTier(heldItem);
        int maxTier = config.getMaxTier();
        if (currentTier >= maxTier) {
            player.getPlayerRef().sendMessage(Message.raw("Equipment is already at max ascension tier (" + AscensionUtils.getTierLabel(maxTier) + ")."));
            return;
        }

        int nextTier = AscensionUtils.getNextTier(heldItem);
        String requiredMaterial = AscensionUtils.getRequiredMaterial(heldItem);
        if (requiredMaterial == null || requiredMaterial.isBlank()) {
            player.getPlayerRef().sendMessage(Message.raw("No material configured for tier " + AscensionUtils.getTierLabel(nextTier) + "."));
            return;
        }

        if (!hasItem(player, requiredMaterial)) {
            player.getPlayerRef().sendMessage(Message.raw("You need " + requiredMaterial + " to ascend to " + AscensionUtils.getTierLabel(nextTier) + "."));
            return;
        }

        removeItem(player, requiredMaterial);
        ItemStack ascended = AscensionUtils.ascendItem(heldItem);

        ItemContainer hotbar = player.getInventory().getHotbar();
        hotbar.setItemStackForSlot(selectedSlot, ascended);

        double ascensionMultiplier = AscensionUtils.getAscensionBaseMultiplier(ascended);
        int bonusPercent = (int) ((ascensionMultiplier - 1.0) * 100);
        player.getPlayerRef().sendMessage(Message.raw("Equipment ascended to " + AscensionUtils.getTierLabel(nextTier) + "! Refinement reset with +" + bonusPercent + "% base bonus."));
    }

    private static boolean hasItem(Player player, String itemId) {
        if (player == null || player.getInventory() == null || itemId == null || itemId.isBlank()) {
            return false;
        }
        ItemContainer hotbar = player.getInventory().getHotbar();
        ItemContainer storage = player.getInventory().getStorage();
        return containerHasItem(hotbar, itemId) || containerHasItem(storage, itemId);
    }

    private static boolean containerHasItem(ItemContainer container, String itemId) {
        if (container == null) {
            return false;
        }
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack != null && !stack.isEmpty() && itemId.equalsIgnoreCase(stack.getItemId())) {
                return true;
            }
        }
        return false;
    }

    private static void removeItem(Player player, String itemId) {
        if (player == null || player.getInventory() == null || itemId == null || itemId.isBlank()) {
            return;
        }
        ItemContainer hotbar = player.getInventory().getHotbar();
        ItemContainer storage = player.getInventory().getStorage();
        if (removeFromContainer(hotbar, itemId)) {
            return;
        }
        removeFromContainer(storage, itemId);
    }

    private static boolean removeFromContainer(ItemContainer container, String itemId) {
        if (container == null) {
            return false;
        }
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack != null && !stack.isEmpty() && itemId.equalsIgnoreCase(stack.getItemId())) {
                int qty = stack.getQuantity();
                if (qty <= 1) {
                    container.removeItemStackFromSlot(slot, 1, false, false);
                } else {
                    container.setItemStackForSlot(slot, stack.withQuantity(qty - 1));
                }
                return true;
            }
        }
        return false;
    }

    private static BlockType getBlockType(World world, int x, int y, int z) {
        return WorldBlockUtils.getBlockType(world, x, y, z);
    }
}
