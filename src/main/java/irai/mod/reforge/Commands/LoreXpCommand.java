package irai.mod.reforge.Commands;

import javax.annotation.Nonnull;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;

import irai.mod.reforge.Common.PlayerInventoryUtils;
import irai.mod.reforge.Lore.LoreSocketData;
import irai.mod.reforge.Lore.LoreSocketManager;
import irai.mod.reforge.Util.DynamicTooltipUtils;

/**
 * OP-only command to grant XP to a lore spirit socket on the held equipment.
 *
 * Usage: /lorexp <slot> <amount>
 */
public class LoreXpCommand extends CommandBase {
    private final RequiredArg<Integer> slotArg;
    private final RequiredArg<Integer> amountArg;

    public LoreXpCommand(@NonNullDecl String name, @NonNullDecl String description, boolean requiresConfirmation) {
        super(name, description, requiresConfirmation);
        this.addAliases("lorexp");
        this.requirePermission(HytalePermissions.fromCommand("op.add"));

        this.slotArg = this.withRequiredArg("slot", "lore socket slot (1-based)", ArgTypes.INTEGER);
        this.amountArg = this.withRequiredArg("amount", "XP to grant", ArgTypes.INTEGER);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        Player player = CommandUtils.getPlayer(context, true);
        if (player == null) {
            return;
        }
        if (!CommandUtils.isOperator(player)) {
            context.sendMessage(Message.raw("OP only command."));
            return;
        }

        int slot = slotArg.get(context);
        int amount = amountArg.get(context);
        if (slot <= 0) {
            context.sendMessage(Message.raw("Slot must be 1 or higher. Usage: /lorexp <slot> <amount>"));
            return;
        }
        if (amount <= 0) {
            context.sendMessage(Message.raw("XP amount must be greater than 0."));
            return;
        }

        ItemStack heldItem = CommandUtils.getSelectedItem(context, player, true);
        if (!CommandUtils.validateItem(heldItem, context, true)) {
            return;
        }
        if (!LoreSocketManager.isEquipment(heldItem)) {
            context.sendMessage(Message.raw("Held item is not lore-compatible equipment."));
            return;
        }

        LoreSocketData data = LoreSocketManager.getLoreSocketData(heldItem);
        if (data == null || slot > data.getSocketCount()) {
            context.sendMessage(Message.raw("Held equipment has no lore socket " + slot + "."));
            return;
        }
        LoreSocketData.LoreSocket socket = data.getSocket(slot - 1);
        if (socket == null || !socket.hasSpirit()) {
            context.sendMessage(Message.raw("Lore socket " + slot + " has no spirit to receive XP."));
            return;
        }
        int maxLevel = LoreSocketManager.getConfig() == null
                ? Integer.MAX_VALUE
                : Math.max(1, LoreSocketManager.getConfig().getMaxLevel());
        if (socket.getLevel() >= maxLevel) {
            context.sendMessage(Message.raw("Lore socket " + slot + " is already at max level."));
            return;
        }

        long updatedXp = (long) socket.getXp() + amount;
        if (updatedXp > Integer.MAX_VALUE) {
            context.sendMessage(Message.raw("That XP amount exceeds the supported socket XP limit."));
            return;
        }

        int oldLevel = socket.getLevel();
        socket.setXp((int) updatedXp);
        LoreSocketManager.applyStoredXp(socket);

        ItemStack updatedItem = LoreSocketManager.withLoreSocketData(heldItem, data);
        PlayerInventoryUtils.HeldItemContext heldContext = PlayerInventoryUtils.getHeldItemContext(player);
        if (heldContext != null && heldContext.getContainer() != null && heldContext.getSlot() >= 0) {
            heldContext.getContainer().setItemStackForSlot(heldContext.getSlot(), updatedItem);
        } else {
            PlayerInventoryUtils.setSelectedHotbarItem(player, updatedItem);
        }
        DynamicTooltipUtils.refreshAllPlayers();

        int level = socket.getLevel();
        String progress = level > oldLevel ? " (level " + oldLevel + " -> " + level + ")" : "";
        String feedStatus = LoreSocketManager.needsFeed(socket) ? "; feed gate reached" : "";
        String levelProgress = level >= maxLevel
                ? "Level " + level + " (max level)"
                : "Level " + level + ", XP " + socket.getXp() + "/"
                        + LoreSocketManager.getXpRequiredForLevel(Math.max(1, level));
        context.sendMessage(Message.raw("Granted " + amount + " XP to lore socket " + slot + ". "
                + levelProgress + progress + feedStatus + "."));
    }
}
