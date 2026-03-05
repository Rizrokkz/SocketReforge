package irai.mod.reforge.Commands;

import javax.annotation.Nonnull;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;

import irai.mod.reforge.UI.WeaponPartsUI;

/**
 * Opens the modular weapon-parts bench UI for the executing player.
 */
public class WeaponPartsCommand extends CommandBase {

    public WeaponPartsCommand(@NonNullDecl String name, @NonNullDecl String description, boolean requiresConfirmation) {
        super(name, description, requiresConfirmation);
        this.requirePermission(HytalePermissions.fromCommand("op.add"));
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
        WeaponPartsUI.open(player);
    }
}
