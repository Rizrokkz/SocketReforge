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
import com.hypixel.hytale.server.core.permissions.HytalePermissions;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import irai.mod.reforge.Progression.CharacterPathService;

/** OP-only testing controls for character path progression. */
public final class CharacterPathAdminCommand extends CommandBase {
    private static final long MAX_XP_GRANT = 1_000_000_000L;

    private final RequiredArg<String> actionArg;
    private final OptionalArg<String> valueOrTargetArg;
    private final OptionalArg<String> targetArg;

    public CharacterPathAdminCommand(@NonNullDecl String name, @NonNullDecl String description,
                                     boolean requiresConfirmation) {
        super(name, description, requiresConfirmation);
        addAliases("pathadmin", "skilltreeadmin");
        requirePermission(HytalePermissions.fromCommand("op.add"));
        actionArg = withRequiredArg("action", "xp|reset|respec", ArgTypes.STRING);
        valueOrTargetArg = withOptionalArg("amount-or-player", "XP amount, or player name for reset/respec", ArgTypes.STRING);
        targetArg = withOptionalArg("player", "online player name (defaults to you)", ArgTypes.STRING);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        Player operator = CommandUtils.getPlayer(context, true);
        if (operator == null) return;
        if (!CommandUtils.isOperator(operator)) {
            context.sendMessage(Message.raw("OP only command."));
            return;
        }

        String action = actionArg.get(context);
        String valueOrTarget = valueOrTargetArg.provided(context) ? valueOrTargetArg.get(context) : null;
        String explicitTarget = targetArg.provided(context) ? targetArg.get(context) : null;
        String normalized = action == null ? "" : action.toLowerCase(Locale.ROOT);

        if (normalized.equals("xp") || normalized.equals("givexp")) {
            grantXp(context, operator, valueOrTarget, explicitTarget);
            return;
        }
        if (normalized.equals("reset")) {
            String targetName = explicitTarget == null ? valueOrTarget : explicitTarget;
            Player target = resolveTarget(context, operator, targetName);
            if (target == null) return;
            if (!CharacterPathService.resetProgress(target)) {
                context.sendMessage(Message.raw("Could not reset that player's character path."));
                return;
            }
            sendToTarget(target, operator, "Character path XP and allocations were reset by an operator.");
            context.sendMessage(Message.raw("Reset character path progress for " + username(target) + "."));
            return;
        }
        if (normalized.equals("respec")) {
            String targetName = explicitTarget == null ? valueOrTarget : explicitTarget;
            Player target = resolveTarget(context, operator, targetName);
            if (target == null) return;
            int refunded = CharacterPathService.respec(target);
            sendToTarget(target, operator, "An operator refunded " + refunded
                    + " skill point" + (refunded == 1 ? "" : "s") + "; your earned XP was kept.");
            context.sendMessage(Message.raw("Refunded " + refunded + " allocated point"
                    + (refunded == 1 ? "" : "s") + " for " + username(target) + "."));
            return;
        }
        sendUsage(context);
    }

    private void grantXp(CommandContext context, Player operator, String amountText, String targetName) {
        if (amountText == null || amountText.isBlank()) {
            sendUsage(context);
            return;
        }
        long amount;
        try { amount = Long.parseLong(amountText); }
        catch (NumberFormatException ignored) {
            context.sendMessage(Message.raw("XP must be a whole number between 1 and " + MAX_XP_GRANT + "."));
            return;
        }
        if (amount < 1 || amount > MAX_XP_GRANT) {
            context.sendMessage(Message.raw("XP must be between 1 and " + MAX_XP_GRANT + "."));
            return;
        }
        Player target = resolveTarget(context, operator, targetName);
        if (target == null) return;
        long total = CharacterPathService.grantXp(target, amount);
        if (total < 0) {
            context.sendMessage(Message.raw("Could not grant character XP."));
            return;
        }
        sendToTarget(target, operator, "An operator granted " + amount + " character XP. Total: " + total + ".");
        context.sendMessage(Message.raw("Granted " + amount + " character XP to " + username(target)
                + " (total " + total + ")."));
    }

    private Player resolveTarget(CommandContext context, Player operator, String targetName) {
        if (targetName == null || targetName.isBlank()
                || username(operator).equalsIgnoreCase(targetName.trim())) return operator;
        Universe universe = Universe.get();
        if (universe != null) {
            for (PlayerRef ref : universe.getPlayers()) {
                if (ref == null || !ref.isValid() || ref.getUsername() == null
                        || !ref.getUsername().equalsIgnoreCase(targetName.trim())) continue;
                Player target = ref.getComponent(Player.getComponentType());
                if (target != null) return target;
            }
        }
        context.sendMessage(Message.raw("Player '" + targetName + "' is not online."));
        return null;
    }

    private static void sendToTarget(Player target, Player operator, String message) {
        if (target == null || target.getPlayerRef() == null) return;
        if (operator == null || operator.getPlayerRef() == null
                || !target.getPlayerRef().getUuid().equals(operator.getPlayerRef().getUuid())) {
            target.getPlayerRef().sendMessage(Message.raw(message));
        }
    }

    private static String username(Player player) {
        try {
            String username = player == null || player.getPlayerRef() == null
                    ? null : player.getPlayerRef().getUsername();
            return username == null || username.isBlank() ? "player" : username;
        } catch (Throwable ignored) { return "player"; }
    }

    private static void sendUsage(CommandContext context) {
        context.sendMessage(Message.raw("Usage: /characterpathadmin xp <amount> [player]"));
        context.sendMessage(Message.raw("Usage: /characterpathadmin reset [player] (clears XP and allocations)"));
        context.sendMessage(Message.raw("Usage: /characterpathadmin respec [player] (keeps XP)"));
    }
}
