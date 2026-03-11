package irai.mod.reforge.Commands;

import javax.annotation.Nonnull;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

import irai.mod.reforge.Common.WorldContainerScanUtils;
import irai.mod.reforge.Common.WorldContainerScanUtils.WorldContainerScanResult;

/**
 * Scans loaded item containers in the main world and migrates resonant recipes/equipment.
 *
 * Usage:
 * /resonanceworldscan
 */
public class ResonanceWorldScanCommand extends CommandBase {

    public ResonanceWorldScanCommand(@NonNullDecl String name, @NonNullDecl String description, boolean requiresConfirmation) {
        super(name, description, requiresConfirmation);
        this.addAliases("resonancescan", "resworldscan");
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

        Universe universe = Universe.get();
        if (universe == null) {
            context.sendMessage(Message.raw("Universe is not available."));
            return;
        }

        World mainWorld = universe.getDefaultWorld();
        if (mainWorld == null) {
            context.sendMessage(Message.raw("Main world is not available."));
            return;
        }

        World playerWorld = player.getWorld();
        if (playerWorld != null && playerWorld != mainWorld) {
            context.sendMessage(Message.raw("Scanning main world only: " + mainWorld.getName()));
        }

        WorldContainerScanResult result = WorldContainerScanUtils.scanWorldContainers(mainWorld);
        if (!result.isSuccess()) {
            context.sendMessage(Message.raw("Resonance scan skipped: " + result.error()));
            return;
        }

        context.sendMessage(Message.raw(
                "Resonance scan complete for main world '" + mainWorld.getName()
                        + "': containers=" + result.containersScanned()
                        + ", updated=" + result.containersUpdated()
                        + ", itemsMigrated=" + result.itemsMigrated() + "."));
    }
}
