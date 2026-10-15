package irai.mod.reforge.Commands;

import java.util.Locale;

import javax.annotation.Nonnull;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;

import irai.mod.reforge.Socket.RuneDeltaDebug;

/**
 * Toggles per-player server-log tracing for Rune Delta ability procs.
 */
public class RuneDeltaDebugCommand extends CommandBase {

    public RuneDeltaDebugCommand(@NonNullDecl String name,
                                 @NonNullDecl String description,
                                 boolean requiresConfirmation) {
        super(name, description, requiresConfirmation);
        this.addAliases("rundebug");
        this.requirePermission(HytalePermissions.fromCommand("help"));
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        Player player = CommandUtils.getPlayer(context, true);
        if (player == null) {
            return;
        }

        String mode = parseMode(context.getInputString());
        boolean enabled;
        if ("on".equals(mode) || "enable".equals(mode) || "enabled".equals(mode)) {
            enabled = RuneDeltaDebug.setEnabled(player, true);
        } else if ("off".equals(mode) || "disable".equals(mode) || "disabled".equals(mode)) {
            enabled = RuneDeltaDebug.setEnabled(player, false);
        } else if ("status".equals(mode) || "state".equals(mode)) {
            enabled = RuneDeltaDebug.isEnabled(player);
        } else {
            enabled = RuneDeltaDebug.toggle(player);
        }

        context.sendMessage(Message.raw("Rune Delta debug logging: " + (enabled ? "enabled" : "disabled")));
    }

    private static String parseMode(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String[] parts = input.trim().split("\\s+");
        return parts.length < 2 ? "" : parts[1].toLowerCase(Locale.ROOT);
    }
}
