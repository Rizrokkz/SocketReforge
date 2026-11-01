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
import irai.mod.reforge.Lore.LoreAbility;
import irai.mod.reforge.Lore.LoreAbilityRegistry;
import irai.mod.reforge.Lore.LoreGemRegistry;
import irai.mod.reforge.Lore.LoreSoulstoneUtils;
import irai.mod.reforge.Lore.LoreSocketData;
import irai.mod.reforge.Lore.LoreSocketManager;
import irai.mod.reforge.Util.DynamicTooltipUtils;

/**
 * OP-only command to apply a specific lore spirit to the held weapon for testing.
 *
 * Usage: /loreapply <spirit> [slot]
 */
public class LoreApplyCommand extends CommandBase {
    private final RequiredArg<String> spiritArg;
    private final OptionalArg<Integer> slotArg;

    public LoreApplyCommand(@NonNullDecl String name, @NonNullDecl String description, boolean requiresConfirmation) {
        super(name, description, requiresConfirmation);
        this.addAliases("loreapply");
        this.requirePermission(HytalePermissions.fromCommand("op.add"));

        this.spiritArg = this.withRequiredArg("spirit", "spirit id", ArgTypes.STRING);
        this.slotArg = this.withOptionalArg("slot", "socket slot (1-based)", ArgTypes.INTEGER);
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
        if (!LoreSocketManager.isEquipment(heldItem)) {
            context.sendMessage(Message.raw("Held item is not lore-compatible (weapon only)."));
            return;
        }

        LoreSocketData data = LoreSocketManager.getLoreSocketData(heldItem);
        if (data == null) {
            data = new LoreSocketData(0);
        }

        String spiritRaw = spiritArg.get(context);
        if (spiritRaw == null || spiritRaw.isBlank()) {
            context.sendMessage(Message.raw("Usage: /loreapply <spirit> [slot]"));
            return;
        }
        String spiritId = resolveSpiritId(spiritRaw);
        if (spiritId == null || spiritId.isBlank()) {
            context.sendMessage(Message.raw("Invalid spirit id."));
            return;
        }
        ItemStack infusedSoulstone = LoreSoulstoneUtils.createInfusedSoulstone(spiritId);
        if (infusedSoulstone == null || infusedSoulstone.isEmpty()) {
            context.sendMessage(Message.raw("That spirit has no mapped infused soulstone color."));
            return;
        }

        String spiritColor = LoreGemRegistry.resolveSpiritColor(spiritId);
        if (spiritColor == null || spiritColor.isBlank()) {
            context.sendMessage(Message.raw("That spirit has no mapped soulstone color."));
            return;
        }

        if (data.getSocketCount() == 0) {
            data.setMaxSockets(1);
            data.ensureSocketCount(1);
        }

        int slotIndex = resolveSlotIndex(data, context, spiritColor);
        if (slotIndex < 0 || slotIndex >= data.getSocketCount()) {
            return;
        }

        LoreSocketData.LoreSocket socket = data.getSocket(slotIndex);
        if (socket == null) {
            context.sendMessage(Message.raw("Invalid socket index."));
            return;
        }
        if (socket.isLocked()) {
            context.sendMessage(Message.raw("That socket is locked. Clear it first if needed."));
            return;
        }
        if (socket.hasSpirit()) {
            context.sendMessage(Message.raw("That socket already has a spirit. Clear it first."));
            return;
        }

        boolean emptySocket = socket.isEmpty();
        String socketColor = socket.getColor();
        String gemColor = LoreGemRegistry.resolveColor(socket.getGemItemId());
        if (gemColor != null && !gemColor.isBlank()) {
            socketColor = gemColor;
        }
        if (!emptySocket && (socketColor == null || socketColor.isBlank())) {
            context.sendMessage(Message.raw("Socket color is unknown. Insert a lore gem first."));
            return;
        }
        if (socketColor != null && !socketColor.isBlank()
                && !spiritColor.equalsIgnoreCase(socketColor)) {
            context.sendMessage(Message.raw("Spirit color mismatch. Spirit=" + spiritColor + ", Socket=" + socketColor));
            return;
        }
        if (emptySocket && (socketColor == null || socketColor.isBlank())) {
            socket.setColor(spiritColor.toLowerCase(Locale.ROOT));
        } else if (socketColor != null && !socketColor.isBlank()
                && (socket.getColor() == null || socket.getColor().isBlank())) {
            socket.setColor(socketColor.toLowerCase(Locale.ROOT));
        }
        // Match the normal socket UI: keep the infused soulstone item ID on the
        // socket so soulstone-specific trigger and rune-mapping behavior applies.
        socket.setGemItemId(infusedSoulstone.getItemId());
        socket.setSpiritId(spiritId);
        socket.setEffectOverride("");
        socket.setLevel(Math.max(1, socket.getLevel()));
        socket.setXp(0);
        socket.setFeedTier(0);
        socket.setLocked(true);

        if (data.getMaxSockets() < data.getSocketCount()) {
            data.setMaxSockets(data.getSocketCount());
        }
        LoreSocketManager.syncSocketColors(heldItem, data);
        ItemStack updated = LoreSocketManager.withLoreSocketData(heldItem, data);

        PlayerInventoryUtils.HeldItemContext heldContext = PlayerInventoryUtils.getHeldItemContext(player);
        if (heldContext != null && heldContext.getContainer() != null && heldContext.getSlot() >= 0) {
            heldContext.getContainer().setItemStackForSlot(heldContext.getSlot(), updated);
        } else {
            PlayerInventoryUtils.setSelectedHotbarItem(player, updated);
        }
        DynamicTooltipUtils.refreshAllPlayers();

        LoreAbility ability = LoreAbilityRegistry.getAbility(spiritId);
        String effectLabel = ability == null || ability.getEffectType() == null
                ? "unknown"
                : ability.getEffectType().name();
        String mappedRune = LoreSocketManager.resolveCoreAbilityRunestoneItemId(spiritId,
                Math.max(0, socket.getFeedTier()));
        String detail = mappedRune == null ? effectLabel : effectLabel + ", mapped rune=" + mappedRune;
        context.sendMessage(Message.raw("Applied infused soulstone " + spiritId + " to socket " + (slotIndex + 1)
                + " (" + detail + ")."));
    }

    private int resolveSlotIndex(LoreSocketData data, CommandContext context, String spiritColor) {
        if (data == null || data.getSocketCount() == 0) {
            return -1;
        }
        if (slotArg.provided(context)) {
            int slot = slotArg.get(context);
            if (slot <= 0) {
                context.sendMessage(Message.raw("Slot must be 1 or higher."));
                return -1;
            }
            if (slot > data.getSocketCount()) {
                if (slot != data.getSocketCount() + 1) {
                    context.sendMessage(Message.raw("Only the next socket can be added by this command."));
                    return -1;
                }
                data.ensureSocketCount(slot);
                data.setMaxSockets(Math.max(data.getMaxSockets(), data.getSocketCount()));
            }
            return slot - 1;
        }

        for (int i = 0; i < data.getSocketCount(); i++) {
            LoreSocketData.LoreSocket socket = data.getSocket(i);
            if (socket == null || socket.isLocked() || socket.hasSpirit() || socket.hasRune()) {
                continue;
            }
            String color = socket.getColor();
            String gemColor = LoreGemRegistry.resolveColor(socket.getGemItemId());
            if (gemColor != null && !gemColor.isBlank()) {
                color = gemColor;
            }
            if (!socket.isEmpty() && (color == null || color.isBlank())) {
                continue;
            }
            if (color != null && !color.isBlank()
                    && spiritColor != null && !spiritColor.equalsIgnoreCase(color)) {
                continue;
            }
            return i;
        }
        // The command is an administrative apply helper: if every existing
        // socket is occupied or color-incompatible, add one socket and return
        // that new slot so the lore is written directly into it below.
        int newIndex = data.getSocketCount();
        data.ensureSocketCount(newIndex + 1);
        data.setMaxSockets(Math.max(data.getMaxSockets(), data.getSocketCount()));
        return newIndex;
    }

    private String resolveSpiritId(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String trimmed = input.trim();
        String resolved = LoreGemRegistry.resolveSpawnableSpiritId(trimmed);
        return resolved != null && !resolved.isBlank() ? resolved : trimmed;
    }
}
