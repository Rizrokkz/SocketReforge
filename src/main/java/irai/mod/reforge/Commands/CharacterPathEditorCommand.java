package irai.mod.reforge.Commands;

import javax.annotation.Nonnull;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;

import irai.mod.reforge.UI.CharacterPathUI;

/** OP-only access to the authoring canvas for the player skill tree. */
public final class CharacterPathEditorCommand extends CommandBase {
    public CharacterPathEditorCommand(@NonNullDecl String name, @NonNullDecl String description,
                                      boolean requiresConfirmation) {
        super(name, description, requiresConfirmation);
        addAliases("skilltreeedit", "characterpathedit");
        requirePermission(HytalePermissions.fromCommand("op.add"));
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        Player player = CommandUtils.getPlayer(context, true);
        if (player == null) return;
        if (!CommandUtils.isOperator(player)) {
            context.sendMessage(Message.raw("OP only command."));
            return;
        }
        CharacterPathUI.openEditor(player);
    }
}
