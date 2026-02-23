package irai.mod.reforge.Commands;

import javax.annotation.Nonnull;

import com.google.gson.JsonObject;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import irai.mod.reforge.Interactions.ReforgeEquip;

/**
 * Utility class for common command patterns.

 * Provides static methods for player validation, item retrieval, and weapon checking.
 */
public class CommandUtils {

    private static final String WEAPON_ID_PATTERN = "Weapon";

    /**
     * Gets the player from the command context.
     * @param context The command context
     * @return The player, or null if not a player
     */
    public static Player getPlayer(@Nonnull CommandContext context) {
        CommandSender sender = context.sender();
        if (sender instanceof Player) {
            return (Player) sender;
        }
        return null;
    }

    /**
     * Gets the player from the command context and sends error message if not a player.
     * @param context The command context
     * @param sendError Whether to send an error message if not a player
     * @return The player, or null if validation fails
     */
    public static Player getPlayer(@Nonnull CommandContext context, boolean sendError) {
        Player player = getPlayer(context);
        if (player == null && sendError) {
            context.sendMessage(Message.raw("§cThis command can only be run by a player."));
        }
        return player;
    }

    /**
     * Gets the selected item from the player's hotbar.
     * @param player The player
     * @return The selected ItemStack, or null if unable to get
     */
    public static ItemStack getSelectedItem(@Nonnull Player player) {
        try {
            short selectedSlot = player.getInventory().getActiveHotbarSlot();
            return player.getInventory().getHotbar().getItemStack(selectedSlot);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Gets the selected item and sends error message if unable.
     * @param context The command context
     * @param player The player
     * @param sendError Whether to send an error message
     * @return The selected ItemStack, or null if validation fails
     */
    public static ItemStack getSelectedItem(@Nonnull CommandContext context, @Nonnull Player player, boolean sendError) {
        ItemStack itemStack = getSelectedItem(player);
        if (itemStack == null && sendError) {
            context.sendMessage(Message.raw("§cUnable to get selected slot."));
        }
        return itemStack;
    }

    /**
     * Validates that the item is not null and has a valid item.
     * @param itemStack The item stack to validate
     * @param sendError Whether to send an error message
     * @return true if valid, false otherwise
     */
    public static boolean validateItem(ItemStack itemStack, @Nonnull CommandContext context, boolean sendError) {
        if (itemStack == null || itemStack.getItem() == null) {
            if (sendError) {
                context.sendMessage(Message.raw("§cNo item in the selected hotbar slot."));
            }
            return false;
        }
        return true;
    }

    /**
     * Checks if an item is a weapon based on its item ID.
     * @param itemStack The item stack to check
     * @return true if it's a weapon, false otherwise
     */
    public static boolean isWeapon(ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }
        String itemId = itemStack.getItemId();
        return itemId != null && itemId.contains(WEAPON_ID_PATTERN);
    }

    /**
     * Validates that the item is a weapon.
     * @param itemStack The item stack to validate
     * @param sendError Whether to send an error message
     * @return true if valid weapon, false otherwise
     */
    public static boolean validateWeapon(ItemStack itemStack, @Nonnull CommandContext context, boolean sendError) {
        if (!isWeapon(itemStack)) {
            if (sendError) {
                context.sendMessage(Message.raw("§cThe selected item is not a weapon."));
            }
            return false;
        }
        return true;
    }

    /**
     * Gets the selected hotbar slot number.
     * @param player The player
     * @return The slot number, or -1 if unable to get
     */
    public static short getSelectedSlot(Player player) {
        try {
            return player.getInventory().getActiveHotbarSlot();
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Extracts weapon data as JSON from an ItemStack.
     * @param itemStack The ItemStack to extract data from
     * @param player The player context
     * @return JsonObject containing weapon data, or null if extraction fails
     */
    public static JsonObject extractWeaponDataAsJson(ItemStack itemStack, Player player) {
        if (itemStack == null) {
            return null;
        }

        try {
            JsonObject weaponData = new JsonObject();

            // Extract basic item properties
            String itemId = itemStack.getItemId();
            weaponData.addProperty("itemId", itemId);
            weaponData.addProperty("quantity", itemStack.getQuantity());
            weaponData.addProperty("durability", itemStack.getDurability());
            weaponData.addProperty("maxDurability", itemStack.getMaxDurability());

            // Get display name from item translation properties
            String displayName = getItemDisplayName(itemStack);
            if (displayName != null) {
                weaponData.addProperty("displayName", displayName);
            }

            // Get quality index (refinement level) from metadata (legacy ID suffix fallback)
            int qualityIndex = ReforgeEquip.getLevelFromItem(itemStack);
            weaponData.addProperty("qualityIndex", qualityIndex);


            // Get max stack size
            int maxStack = getItemMaxStack(itemStack);
            weaponData.addProperty("maxStack", maxStack);

            // Add player context
            if (player != null) {
                weaponData.addProperty("playerId", player.getPlayerRef().getUuid().toString());
            }

            return weaponData;

        } catch (Exception e) {
            System.err.println("[CommandUtils] Failed to extract weapon data: " + e.getMessage());
            return null;
        }
    }

    /**
     * Gets the display name of an item.
     */
    public static String getItemDisplayName(ItemStack itemStack) {
        if (itemStack == null || itemStack.getItem() == null) {
            return null;
        }
        try {
            return itemStack.getItem().getTranslationProperties().getName();
        } catch (Exception e) {
            // Fallback to item ID
            return itemStack.getItemId();
        }
    }

    /**
     * Gets the quality index of an item.
     */
    public static int getItemQualityIndex(ItemStack itemStack) {
        if (itemStack == null || itemStack.getItem() == null) {
            return 0;
        }
        try {
            java.lang.reflect.Field qualityField = itemStack.getItem().getClass().getDeclaredField("qualityIndex");
            if (qualityField != null) {
                qualityField.setAccessible(true);
                return (Integer) qualityField.get(itemStack.getItem());
            }
        } catch (Exception e) {
            // Default quality
        }
        return 0;
    }

    /**
     * Gets the max stack size of an item.
     */
    public static int getItemMaxStack(ItemStack itemStack) {
        if (itemStack == null || itemStack.getItem() == null) {
            return 1;
        }
        try {
            return itemStack.getItem().getMaxStack();
        } catch (Exception e) {
            // Default max stack
            return 1;
        }
    }
}
