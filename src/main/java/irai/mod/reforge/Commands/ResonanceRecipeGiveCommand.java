package irai.mod.reforge.Commands;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nonnull;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;

import irai.mod.reforge.Common.ResonantRecipeUtils;
import irai.mod.reforge.Common.UI.UIInventoryUtils;
import irai.mod.reforge.Socket.Essence;
import irai.mod.reforge.Socket.ResonanceSystem;
import irai.mod.reforge.Util.NameResolver;

/**
 * OP-only command to grant random resonant recipe shards.
 *
 * Usage:
 * /resonancerecipe <rarity> [count] [mode]
 * rarity: 1=3 sockets, 2=4 sockets, 3=5 sockets
 * mode: random|partial|complete (defaults to random)
 */
@SuppressWarnings("removal")
public class ResonanceRecipeGiveCommand extends CommandBase {
    private static final int MAX_COUNT = 64;

    private final RequiredArg<Integer> rarityArg;
    private final OptionalArg<Integer> countArg;
    private final OptionalArg<String> modeArg;

    public ResonanceRecipeGiveCommand(@NonNullDecl String name, @NonNullDecl String description, boolean requiresConfirmation) {
        super(name, description, requiresConfirmation);
        this.addAliases("giveresonancerecipe");
        this.addAliases("resonantrecipe");
        this.requirePermission(HytalePermissions.fromCommand("op.add"));

        this.rarityArg = this.withRequiredArg("rarity", "1=3 sockets, 2=4 sockets, 3=5 sockets", ArgTypes.INTEGER);
        this.countArg = this.withOptionalArg("count", "1-64", ArgTypes.INTEGER);
        this.modeArg = this.withOptionalArg("mode", "random|partial|complete", ArgTypes.STRING);
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

        Integer rarity = rarityArg.get(context);
        int sockets = socketsFromRarity(rarity);
        if (sockets <= 0) {
            sendUsage(context);
            return;
        }

        Integer requestedCount = countArg.provided(context) ? countArg.get(context) : 1;
        int count = Math.max(1, Math.min(MAX_COUNT, requestedCount == null ? 1 : requestedCount));
        if (requestedCount != null && requestedCount != count) {
            context.sendMessage(Message.raw("Count clamped to " + count + "."));
        }

        Mode mode = parseMode(modeArg.provided(context) ? modeArg.get(context) : null);
        if (mode == null) {
            sendUsage(context);
            return;
        }

        Random rng = ThreadLocalRandom.current();
        int added = 0;
        int partial = 0;
        int complete = 0;

        for (int i = 0; i < count; i++) {
            ResonanceSystem.ResonanceRecipe recipe = ResonanceSystem.rollRandomResonanceRecipeBySockets(sockets, rng);
            if (recipe == null || recipe.name() == null || recipe.name().isBlank()) {
                context.sendMessage(Message.raw("No resonance recipes found for " + sockets + " sockets."));
                break;
            }

            boolean makeComplete = mode == Mode.RANDOM ? rng.nextBoolean() : mode == Mode.COMPLETE;
            ItemStack stack = buildRecipeItem(recipe, makeComplete, rng);
            if (stack == null || stack.isEmpty()) {
                context.sendMessage(Message.raw("Failed to create a recipe item."));
                break;
            }

            if (!UIInventoryUtils.addItemToInventory(player, stack)) {
                context.sendMessage(Message.raw("Inventory full. Added " + added + "/" + count + " recipes."));
                break;
            }

            added++;
            if (makeComplete) {
                complete++;
            } else {
                partial++;
            }
        }

        if (added > 0) {
            String modeLabel = mode == Mode.RANDOM ? "random" : mode.name().toLowerCase(java.util.Locale.ROOT);
            context.sendMessage(Message.raw("Gave " + added + " Resonant Recipe(s) (" + sockets
                    + "-socket, " + modeLabel + "). Complete: " + complete + ", Partial: " + partial + "."));
        }
    }

    private static int socketsFromRarity(Integer rarity) {
        if (rarity == null) {
            return 0;
        }
        return switch (rarity) {
            case 1 -> 3;
            case 2 -> 4;
            case 3 -> 5;
            default -> 0;
        };
    }

    private static Mode parseMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return Mode.RANDOM;
        }
        String normalized = raw.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "random", "rand", "r" -> Mode.RANDOM;
            case "partial", "p", "incomplete" -> Mode.PARTIAL;
            case "complete", "c", "full" -> Mode.COMPLETE;
            default -> null;
        };
    }

    private static ItemStack buildRecipeItem(ResonanceSystem.ResonanceRecipe recipe, boolean complete, Random rng) {
        if (recipe == null || recipe.name() == null || recipe.name().isBlank()) {
            return null;
        }
        Essence.Type[] pattern = recipe.pattern();
        if (pattern == null) {
            pattern = new Essence.Type[0];
        }

        String patternText;
        if (complete) {
            patternText = ResonantRecipeUtils.buildPatternFromEssence(pattern, null);
        } else {
            boolean[] revealMask = buildPartialRevealMask(pattern.length, rng);
            patternText = ResonantRecipeUtils.buildPatternFromEssence(pattern, revealMask);
        }

        String displayName = recipe.name().trim() + " Recipe";
        String appliesTo = recipe.appliesTo() == null ? "" : recipe.appliesTo();

        ItemStack stack = new ItemStack(ResonantRecipeUtils.RECIPE_ITEM_ID, 1)
                .withMetadata(NameResolver.KEY_DISPLAY_NAME, Codec.STRING, displayName)
                .withMetadata(ResonantRecipeUtils.META_RECIPE_NAME, Codec.STRING, recipe.name().trim())
                .withMetadata(ResonantRecipeUtils.META_RECIPE_PATTERN, Codec.STRING, patternText)
                .withMetadata(ResonantRecipeUtils.META_RECIPE_TYPE, Codec.STRING, appliesTo);

        if (complete) {
            stack = stack.withMetadata(ResonantRecipeUtils.META_RECIPE_USAGES, Codec.STRING,
                    ResonantRecipeUtils.DEFAULT_RECIPE_USAGES);
        }
        return stack;
    }

    private static boolean[] buildPartialRevealMask(int length, Random rng) {
        if (length <= 0) {
            return new boolean[0];
        }
        if (length == 1) {
            return new boolean[] {true};
        }

        Random random = rng != null ? rng : ThreadLocalRandom.current();
        int maxReveal = Math.min(3, length - 1);
        int revealCount = 1;
        if (maxReveal > 1) {
            revealCount = 1 + random.nextInt(maxReveal);
        }

        boolean[] reveal = new boolean[length];
        int picked = 0;
        while (picked < revealCount) {
            int idx = random.nextInt(length);
            if (!reveal[idx]) {
                reveal[idx] = true;
                picked++;
            }
        }
        return reveal;
    }

    private static void sendUsage(CommandContext context) {
        context.sendMessage(Message.raw("Usage: /resonancerecipe <rarity> [count] [mode]"));
        context.sendMessage(Message.raw("rarity: 1=3 sockets, 2=4 sockets, 3=5 sockets"));
        context.sendMessage(Message.raw("mode: random|partial|complete (default random)"));
    }

    private enum Mode {
        RANDOM,
        PARTIAL,
        COMPLETE
    }
}
