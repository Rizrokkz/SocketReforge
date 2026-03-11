package irai.mod.reforge.Commands;

import java.util.List;

import javax.annotation.Nonnull;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;

import irai.mod.reforge.Socket.ResonanceSystem;

/**
 * OP-only command to list seeded resonance combinations.
 */
public class ResonanceListCommand extends CommandBase {

    public ResonanceListCommand(@NonNullDecl String name, @NonNullDecl String description, boolean requiresConfirmation) {
        super(name, description, requiresConfirmation);
        this.addAliases("resonancecombos");
        this.addAliases("resonancelist");
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

        List<ResonanceSystem.RecipeDisplay> combos = ResonanceSystem.getSeededRecipeDisplays();
        if (combos.isEmpty()) {
            context.sendMessage(Message.raw("No resonance combinations found."));
            return;
        }

        String seedLabel = ResonanceSystem.isResonanceSeedConfigured() ? "seeded" : "default";
        context.sendMessage(Message.raw("Resonance combinations (" + seedLabel + "): " + combos.size()));
        int idx = 1;
        for (ResonanceSystem.RecipeDisplay combo : combos) {
            String appliesTo = combo.appliesTo() == null ? "Unknown" : combo.appliesTo();
            String pattern = combo.pattern() == null ? "" : combo.pattern();
            context.sendMessage(Message.raw(idx + ". [" + appliesTo + "] " + combo.name() + " = " + pattern));
            idx++;
        }
    }
}
