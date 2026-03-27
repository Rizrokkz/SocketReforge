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

import irai.mod.reforge.Lore.LoreAbility;
import irai.mod.reforge.Lore.LoreAbilityRegistry;
import irai.mod.reforge.Lore.LoreGemRegistry;

/**
 * OP-only command to dump the lore spirit -> ability mapping.
 */
public class LoreAbilityMapCommand extends CommandBase {

    public LoreAbilityMapCommand(@NonNullDecl String name, @NonNullDecl String description, boolean requiresConfirmation) {
        super(name, description, requiresConfirmation);
        this.addAliases("loreabilitymap");
        this.addAliases("loreabilities");
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

        Map<String, String> colorMap = LoreGemRegistry.getSpiritColorMapSnapshot();
        if (colorMap.isEmpty()) {
            context.sendMessage(Message.raw("Lore spirit map is empty. (NPC spawn data not loaded yet.)"));
            return;
        }

        List<String> spirits = new ArrayList<>(colorMap.keySet());
        spirits.sort(String.CASE_INSENSITIVE_ORDER);

        List<String> lines = new ArrayList<>();
        lines.add("Lore spirit ability map (" + spirits.size() + " entries)");
        for (String spiritId : spirits) {
            LoreAbility ability = LoreAbilityRegistry.getAbility(spiritId);
            String detail = ability == null
                    ? "none"
                    : formatAbility(ability);
            lines.add(spiritId + " = " + detail);
        }

        Path out = Path.of("lore_spirit_ability_map.txt");
        try {
            Files.write(out, lines, StandardCharsets.UTF_8);
            context.sendMessage(Message.raw("Wrote lore spirit ability map to: " + out.toAbsolutePath()));
        } catch (Exception e) {
            context.sendMessage(Message.raw("Failed to write lore spirit ability map: " + e.getMessage()));
        }
    }

    private String formatAbility(LoreAbility ability) {
        if (ability == null) {
            return "none";
        }
        return ability.getTrigger() + " | "
                + ability.getEffectType() + " | "
                + formatPercent(ability.getProcChance()) + " | "
                + formatSeconds(ability.getCooldownMs()) + " | "
                + "base=" + formatValue(ability.getBaseValue()) + " | "
                + "perLvl=" + formatValue(ability.getPerLevel());
    }

    private String formatPercent(double chance) {
        double pct = chance * 100.0;
        return String.format(java.util.Locale.ROOT, "%.1f%%", pct);
    }

    private String formatSeconds(long millis) {
        double seconds = Math.max(0.0, millis / 1000.0);
        return String.format(java.util.Locale.ROOT, "%.2fs", seconds);
    }

    private String formatValue(double value) {
        double rounded = Math.round(value * 100.0) / 100.0;
        return String.format(java.util.Locale.ROOT, "%.2f", rounded);
    }
}
