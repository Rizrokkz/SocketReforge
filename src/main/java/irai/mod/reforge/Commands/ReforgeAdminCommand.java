package irai.mod.reforge.Commands;

import java.util.Locale;

import javax.annotation.Nonnull;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;

import irai.mod.reforge.Common.PlayerInventoryUtils;
import irai.mod.reforge.Interactions.ReforgeEquip;
import irai.mod.reforge.Socket.SocketData;
import irai.mod.reforge.Socket.SocketManager;
import irai.mod.reforge.Util.NameResolver;

/**
 * OP-only admin command for editing held-item refinement and socket state.
 *
 * Usage:
 * /reforgeadmin refine <level>
 * /reforgeadmin sockets <current> [max]
 * /reforgeadmin addmax <amount>
 */
public class ReforgeAdminCommand extends CommandBase {
    private final RequiredArg<String> actionArg;
    private final RequiredArg<Integer> valueArg;
    private final OptionalArg<Integer> maxArg;

    public ReforgeAdminCommand(@NonNullDecl String name, @NonNullDecl String description, boolean requiresConfirmation) {
        super(name, description, requiresConfirmation);
        this.addAliases("rfadmin");
        this.requirePermission(HytalePermissions.fromCommand("op.add"));

        this.actionArg = this.withRequiredArg("action", "refine|sockets|addmax", ArgTypes.STRING);
        this.valueArg = this.withRequiredArg("value", "refine level or socket count", ArgTypes.INTEGER);
        this.maxArg = this.withOptionalArg("max", "max sockets (for sockets action)", ArgTypes.INTEGER);
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

        ItemStack heldItem = CommandUtils.getSelectedItem(context, player, true);
        if (!CommandUtils.validateItem(heldItem, context, true)) {
            return;
        }

        String action = actionArg.get(context);
        if (action == null) {
            sendUsage(context);
            return;
        }

        String normalized = action.toLowerCase(Locale.ROOT);
        int value = valueArg.get(context);

        switch (normalized) {
            case "refine":
            case "refinement":
            case "level":
                setRefinementLevel(context, player, heldItem, value);
                return;
            case "sockets":
            case "socket":
                int maxSockets = maxArg.provided(context) ? maxArg.get(context) : value;
                setSockets(context, player, heldItem, value, maxSockets);
                return;
            case "addmax":
            case "incmax":
            case "increasemax":
                increaseMaxSockets(context, player, heldItem, value);
                return;
            default:
                sendUsage(context);
        }
    }

    private void setRefinementLevel(CommandContext context, Player player, ItemStack heldItem, int level) {
        if (level < 0 || level > 3) {
            context.sendMessage(Message.raw("Invalid refinement level. Use 0..3."));
            return;
        }

        ItemStack updated = ReforgeEquip.withUpgradeLevel(heldItem, level);
        setHeldItem(player, updated);

        String displayName = NameResolver.getDisplayName(updated);
        context.sendMessage(Message.raw("Set refinement to +" + level + " for " + displayName));
    }

    private void setSockets(CommandContext context, Player player, ItemStack heldItem, int currentSockets, int maxSockets) {
        SocketData existingData = SocketManager.getSocketData(heldItem);
        if (existingData == null) {
            context.sendMessage(Message.raw("Held item is not socket-compatible (weapon/armor only)."));
            return;
        }

        if (currentSockets < 0 || maxSockets < 0) {
            context.sendMessage(Message.raw("Socket values must be >= 0."));
            return;
        }
        if (currentSockets > maxSockets) {
            context.sendMessage(Message.raw("Current sockets cannot be greater than max sockets."));
            return;
        }

        boolean isWeapon = ReforgeEquip.isWeapon(heldItem);
        int maxAllowed = isWeapon
                ? SocketManager.getConfig().getMaxSocketsWeapon()
                : SocketManager.getConfig().getMaxSocketsArmor();
        if (maxSockets > maxAllowed) {
            context.sendMessage(Message.raw("Max sockets for this item type is " + maxAllowed + "."));
            return;
        }

        SocketData updatedData = new SocketData(maxSockets);
        for (int i = 0; i < currentSockets; i++) {
            updatedData.addSocket();
        }

        ItemStack updatedItem = SocketManager.withSocketData(heldItem, updatedData);
        setHeldItem(player, updatedItem);
        updatedData.registerTooltips(updatedItem, updatedItem.getItemId(), isWeapon);

        context.sendMessage(Message.raw("Set sockets to " + currentSockets + " / " + maxSockets + " for held item."));
    }

    private void increaseMaxSockets(CommandContext context, Player player, ItemStack heldItem, int amount) {
        SocketData existingData = SocketManager.getSocketData(heldItem);
        if (existingData == null) {
            context.sendMessage(Message.raw("Held item is not socket-compatible (weapon/armor only)."));
            return;
        }
        if (amount <= 0) {
            context.sendMessage(Message.raw("Increase amount must be > 0."));
            return;
        }

        int oldMax = existingData.getMaxSockets();
        int newMax = oldMax + amount;
        if (newMax <= oldMax) {
            context.sendMessage(Message.raw("Failed to increase max sockets."));
            return;
        }

        existingData.setMaxSockets(newMax);
        ItemStack updatedItem = SocketManager.withSocketData(heldItem, existingData);
        setHeldItem(player, updatedItem);

        boolean isWeapon = ReforgeEquip.isWeapon(updatedItem);
        existingData.registerTooltips(updatedItem, updatedItem.getItemId(), isWeapon);

        context.sendMessage(Message.raw("Increased max sockets: " + oldMax + " -> " + newMax));
    }

    private void setHeldItem(Player player, ItemStack itemStack) {
        PlayerInventoryUtils.setSelectedHotbarItem(player, itemStack);
    }

    private void sendUsage(CommandContext context) {
        context.sendMessage(Message.raw("Usage: /reforgeadmin refine <0-3>"));
        context.sendMessage(Message.raw("Usage: /reforgeadmin sockets <current> [max]"));
        context.sendMessage(Message.raw("Usage: /reforgeadmin addmax <amount>"));
    }
}
