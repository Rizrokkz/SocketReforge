package irai.mod.reforge.Commands;

import javax.annotation.Nonnull;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;

/**
 * Command to check the translation name of the held item.
 */
public class CheckNameCommand extends CommandBase {

    public CheckNameCommand(@NonNullDecl String name, @NonNullDecl String description, boolean requiresConfirmation) {
        super(name, description, requiresConfirmation);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        // Use CommandUtils to get player
        Player player = CommandUtils.getPlayer(context, true);
        if (player == null) {
            return;
        }

        // Use CommandUtils to get selected item
        ItemStack itemStack = CommandUtils.getSelectedItem(context, player, true);
        if (itemStack == null) {
            return;
        }

        // Get the translation name
        String translationName = CommandUtils.getItemDisplayName(itemStack);

        if (translationName == null || translationName.isEmpty()) {
            context.sendMessage(Message.raw("§cNo translation name found for this item."));
        } else {
            context.sendMessage(Message.raw("§aTranslation name: " + translationName));
        }
    }

}
