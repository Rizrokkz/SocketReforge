package irai.mod.reforge.Lore;

import java.util.Locale;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

/** Migrates legacy spirit sockets to the infused soulstone item IDs used by the current lore system. */
public final class LoreSoulstoneMigration {

    private LoreSoulstoneMigration() {}

    /** Converts every legacy lore socket in a player's inventory, including equipped items. */
    public static int migratePlayerInventory(Player player) {
        if (player == null || player.getInventory() == null) {
            return 0;
        }
        Inventory inventory = player.getInventory();
        ItemContainer[] containers;
        try {
            containers = new ItemContainer[] {
                    inventory.getHotbar(),
                    inventory.getStorage(),
                    inventory.getBackpack(),
                    inventory.getArmor(),
                    inventory.getUtility(),
                    inventory.getTools()
            };
        } catch (Exception ignored) {
            return 0;
        }

        int converted = 0;
        for (ItemContainer container : containers) {
            converted += migrateContainer(container);
        }
        return converted;
    }

    /** Converts legacy lore sockets in every slot of a container. */
    public static int migrateContainer(ItemContainer container) {
        if (container == null) {
            return 0;
        }
        int converted = 0;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            if (migrateSlot(container, slot)) {
                converted++;
            }
        }
        return converted;
    }

    /** Converts a legacy lore socket in one slot and writes the updated stack in place. */
    public static boolean migrateSlot(ItemContainer container, int slot) {
        if (container == null || slot < 0 || slot >= container.getCapacity()) {
            return false;
        }
        try {
            ItemStack current = container.getItemStack((short) slot);
            ItemStack migrated = migrateItem(current);
            if (migrated == current || migrated == null || migrated.isEmpty()) {
                return false;
            }
            container.setItemStackForSlot((short) slot, migrated, false);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Returns the original stack unless at least one legacy spirit socket was converted. */
    public static ItemStack migrateItem(ItemStack item) {
        if (item == null || item.isEmpty() || !LoreSocketManager.isEquipment(item)) {
            return item;
        }
        LoreSocketData data = LoreSocketManager.getLoreSocketData(item);
        if (data == null) {
            return item;
        }

        boolean changed = false;
        for (int i = 0; i < data.getSocketCount(); i++) {
            LoreSocketData.LoreSocket socket = data.getSocket(i);
            if (socket == null || !socket.hasSpirit()
                    || LoreSoulstoneUtils.isInfusedSoulstoneId(socket.getGemItemId())) {
                continue;
            }

            ItemStack infusedSoulstone = LoreSoulstoneUtils.createInfusedSoulstone(socket.getSpiritId());
            if (infusedSoulstone == null || infusedSoulstone.isEmpty()) {
                continue;
            }
            String color = LoreGemRegistry.resolveSpiritColor(socket.getSpiritId());
            if (color == null || color.isBlank()) {
                continue;
            }

            socket.setGemItemId(infusedSoulstone.getItemId());
            socket.setColor(color.trim().toLowerCase(Locale.ROOT));
            socket.setLevel(Math.max(1, socket.getLevel()));
            socket.setLocked(true);
            changed = true;
        }

        return changed ? LoreSocketManager.withLoreSocketData(item, data) : item;
    }

    /** Checks that a changed container is one of this player's inventory sections. */
    public static boolean isPlayerInventoryContainer(Player player, ItemContainer candidate) {
        if (player == null || player.getInventory() == null || candidate == null) {
            return false;
        }
        Inventory inventory = player.getInventory();
        try {
            return candidate == inventory.getHotbar()
                    || candidate == inventory.getStorage()
                    || candidate == inventory.getBackpack()
                    || candidate == inventory.getArmor()
                    || candidate == inventory.getUtility()
                    || candidate == inventory.getTools();
        } catch (Exception ignored) {
            return false;
        }
    }
}
