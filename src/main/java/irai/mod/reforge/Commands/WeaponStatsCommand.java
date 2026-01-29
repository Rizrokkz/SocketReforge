package irai.mod.reforge.Commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import irai.mod.reforge.UI.WeaponTooltip;
import irai.mod.reforge.Systems.WeaponUpgradeTracker;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.jline.console.impl.Builtins;

import java.util.regex.Pattern;

/**
 * Command to check weapon upgrade stats.
 * Usage: /weaponstats
 */
public class WeaponStatsCommand extends CommandBase {

    private static final Pattern WEAPON_PATTERN = Pattern.compile(".*[Ww]eapon.*");

    public WeaponStatsCommand(@NonNullDecl String name, @NonNullDecl String description, boolean requiresConfirmation) {
        super(name, description, requiresConfirmation);
    }

    @Override
    public void executeSync(CommandContext context) {
        // Get the player executing the command
        CommandSender sender = context.sender();
        Player player = (Player) sender;
        if (player == null) {
            context.sendMessage(Message.raw("This command can only be used by players!"));
            return;
        }

        // Get the held item
        ItemStack heldItem = getHeldItem(player);
        if (heldItem == null) {
            player.sendMessage(Message.raw("You must be holding an item!"));
            return;
        }

        // Check if it's a weapon
        if (!isWeapon(heldItem.getItemId())) {
            player.sendMessage(Message.raw("You must be holding a weapon!"));
            return;
        }

        // Get the slot
        short slot = 0;
        try {
            slot = player.getInventory().getActiveHotbarSlot();
        } catch (Exception e) {
            // Can't get slot, use 0
        }

        // Show detailed stats
        WeaponTooltip.showDetailedStats(player, heldItem, slot);
    }

    @Override
    public String getName() {
        return "weaponstats";
    }

    @Override
    public String getDescription() {
        return "Shows detailed stats for the weapon you're holding";
    }

//    @Override
//    public String getUsage() {
//        return "/weaponstats";
//    }

    /**
     * Gets the item the player is currently holding.
     */
    private ItemStack getHeldItem(Player player) {
        try {
            short selectedSlot = player.getInventory().getActiveHotbarSlot();
            return player.getInventory().getHotbar().getItemStack(selectedSlot);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Checks if an item is a weapon.
     */
    private boolean isWeapon(String itemId) {
        return itemId != null && WEAPON_PATTERN.matcher(itemId).matches();
    }
}