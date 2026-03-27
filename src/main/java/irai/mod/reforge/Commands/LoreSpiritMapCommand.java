package irai.mod.reforge.Commands;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;

import irai.mod.reforge.Lore.LoreGemRegistry;

/**
 * OP-only command to dump the lore spirit -> gem color mapping.
 */
public class LoreSpiritMapCommand extends CommandBase {

    public LoreSpiritMapCommand(@NonNullDecl String name, @NonNullDecl String description, boolean requiresConfirmation) {
        super(name, description, requiresConfirmation);
        this.addAliases("lorespiritmap");
        this.addAliases("lorecolors");
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

        Map<String, String> map = LoreGemRegistry.getSpiritColorMapSnapshot();
        if (map.isEmpty()) {
            context.sendMessage(Message.raw("Lore spirit color map is empty. (NPC spawn data not loaded yet.)"));
            return;
        }

        List<Map.Entry<String, String>> entries = new ArrayList<>(map.entrySet());
        entries.sort(Comparator.comparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER));

        List<String> lines = new ArrayList<>();
        lines.add("Lore spirit color map (" + entries.size() + " entries)");
        for (Map.Entry<String, String> entry : entries) {
            lines.add(entry.getKey() + " = " + entry.getValue());
        }

        Path out = Path.of("lore_spirit_color_map.txt");
        try {
            Files.write(out, lines, StandardCharsets.UTF_8);
            context.sendMessage(Message.raw("Wrote lore spirit color map to: " + out.toAbsolutePath()));
        } catch (Exception e) {
            context.sendMessage(Message.raw("Failed to write lore spirit color map: " + e.getMessage()));
        }
    }
}
