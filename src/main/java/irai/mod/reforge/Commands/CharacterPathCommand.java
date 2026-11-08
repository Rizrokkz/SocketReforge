package irai.mod.reforge.Commands;

import javax.annotation.Nonnull;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;

import irai.mod.reforge.UI.CharacterPathUI;

/** Opens the player's persistent character skill path. */
public final class CharacterPathCommand extends CommandBase {
    public CharacterPathCommand(@NonNullDecl String name, @NonNullDecl String description,
                                boolean requiresConfirmation) {
        super(name, description, requiresConfirmation);
        addAliases("skilltree", "pathforge");
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        Player player = CommandUtils.getPlayer(context, true);
        if (player != null) CharacterPathUI.open(player);
    }
}
