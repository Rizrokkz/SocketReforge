package irai.mod.reforge.UI;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Commands.CommandUtils;
import irai.mod.reforge.Config.SocketConfig;
import irai.mod.reforge.Interactions.ReforgeEquip;
import irai.mod.reforge.Socket.SocketData;
import irai.mod.reforge.Socket.SocketManager;
import irai.mod.reforge.Socket.SocketManager.PunchResult;
import irai.mod.reforge.Socket.SocketManager.SupportMaterial;

/**
 * Socket Punching UI.
 * Uses held item as equipment and player inventory materials for puncher/support.
 */
public class SocketPunchUI extends InteractiveCustomUIPage<SocketPunchUI.Data> {

    private static final String MAIN_MATERIAL_ID = "Socket_Puncher";
    private static final String SUPPORT_MATERIAL_ID = "Socket_Stabilizer";

    private static SocketConfig config = new SocketConfig();

    private boolean useSupportMaterial = false;

    public static void setConfig(SocketConfig cfg) {
        config = cfg;
        SocketManager.setConfig(cfg);
    }

    public static class Data {
        public String action = "";
        public String slot = "";

        public static final BuilderCodec<Data> CODEC = BuilderCodec
                .builder(Data.class, Data::new)
                .append(new KeyedCodec<>("@Action", Codec.STRING),
                        (d, v) -> d.action = v, d -> d.action).add()
                .append(new KeyedCodec<>("@Slot", Codec.STRING),
                        (d, v) -> d.slot = v, d -> d.slot).add()
                .build();
    }

    public SocketPunchUI(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, Data.CODEC);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt,
                      @Nonnull Store<EntityStore> store) {
        cmd.append("SocketPunchUI.ui");

        // Use default Hytale button UI for interaction surfaces.
        cmd.append("#PunchSocketButton", "Common/TextButton.ui");
        cmd.append("#EquipmentSlot", "Common/TextButton.ui");
        cmd.append("#PuncherSlot", "Common/TextButton.ui");
        cmd.append("#SupportSlot", "Common/TextButton.ui");

        cmd.set("#PunchSocketButton #Button.Text", "Punch Socket");
        cmd.set("#EquipmentSlot #Button.Text", "Refresh Item");
        cmd.set("#PuncherSlot #Button.Text", "Materials");
        cmd.set("#SupportSlot #Button.Text", "Toggle Support");

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#PunchSocketButton #Button",
                EventData.of("@Action", "punch"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#EquipmentSlot #Button",
                new EventData().append("@Action", "slot").append("@Slot", "equipment"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#PuncherSlot #Button",
                new EventData().append("@Action", "slot").append("@Slot", "puncher"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SupportSlot #Button",
                new EventData().append("@Action", "slot").append("@Slot", "support"), false);

        Player player = store.getComponent(ref, Player.getComponentType());
        refreshView(cmd, player, "Place an item in your hand and press Punch Socket.");
        sendUpdate(cmd, false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                Data data) {
        super.handleDataEvent(ref, store, data);

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            sendUpdate();
            return;
        }

        if (data == null || data.action == null || data.action.isEmpty()) {
            UICommandBuilder cmd = new UICommandBuilder();
            refreshView(cmd, player, null);
            sendUpdate(cmd, false);
            return;
        }

        if ("punch".equals(data.action)) {
            handlePunch(player);
            return;
        }

        if ("slot".equals(data.action)) {
            handleSlotClick(player, data.slot);
            return;
        }
        UICommandBuilder cmd = new UICommandBuilder();
        refreshView(cmd, player, null);
        sendUpdate(cmd, false);
    }

    private void handlePunch(Player player) {
        UICommandBuilder cmd = new UICommandBuilder();
        ItemStack heldItem = getHeldItem(player);

        if (heldItem == null || heldItem.isEmpty()) {
            refreshView(cmd, player, "Hold an equipment item first.");
            sendUpdate(cmd, false);
            return;
        }

        boolean isWeapon = ReforgeEquip.isWeapon(heldItem);
        boolean isArmor = !isWeapon && ReforgeEquip.isArmor(heldItem);
        if (!isWeapon && !isArmor) {
            refreshView(cmd, player, "Only weapons and armor can receive sockets.");
            sendUpdate(cmd, false);
            return;
        }

        SocketData socketData = SocketManager.getSocketData(heldItem);
        if (socketData == null) {
            socketData = SocketData.fromDefaults(isWeapon ? "weapon" : "armor");
        }

        if (!socketData.canAddSocket()) {
            refreshView(cmd, player, "This item already has the maximum sockets.");
            sendUpdate(cmd, false);
            return;
        }

        if (!hasEnoughMaterial(player, MAIN_MATERIAL_ID, 1)) {
            refreshView(cmd, player, "Missing Socket Puncher.");
            sendUpdate(cmd, false);
            return;
        }

        if (useSupportMaterial && !hasEnoughMaterial(player, SUPPORT_MATERIAL_ID, 1)) {
            useSupportMaterial = false;
            refreshView(cmd, player, "Support toggled off: no Socket Stabilizer found.");
            sendUpdate(cmd, false);
            return;
        }

        if (!consumeMaterial(player, MAIN_MATERIAL_ID, 1)) {
            refreshView(cmd, player, "Could not consume Socket Puncher.");
            sendUpdate(cmd, false);
            return;
        }

        SupportMaterial support = SupportMaterial.NONE;
        if (useSupportMaterial) {
            if (!consumeMaterial(player, SUPPORT_MATERIAL_ID, 1)) {
                refreshView(cmd, player, "Could not consume Socket Stabilizer.");
                sendUpdate(cmd, false);
                return;
            }
            support = SupportMaterial.SOCKET_STABILIZER;
        }

        PunchResult result = SocketManager.punchSocket(socketData, support);
        short heldSlot = player.getInventory().getActiveHotbarSlot();

        switch (result) {
            case SUCCESS -> {
                ItemStack updated = SocketManager.withSocketData(heldItem, socketData);
                player.getInventory().getHotbar().setItemStackForSlot(heldSlot, updated);
                refreshView(cmd, player, "Socket punched successfully.");
            }
            case FAIL -> refreshView(cmd, player, "Punch failed. Materials consumed.");
            case BREAK -> {
                player.getInventory().getHotbar().removeItemStackFromSlot(heldSlot, 1, false, false);
                refreshView(cmd, player, "Item shattered during socket punch.");
            }
        }

        sendUpdate(cmd, false);
    }

    private void handleSlotClick(Player player, String slotName) {
        UICommandBuilder cmd = new UICommandBuilder();

        if ("support".equals(slotName)) {
            if (!useSupportMaterial && !hasEnoughMaterial(player, SUPPORT_MATERIAL_ID, 1)) {
                refreshView(cmd, player, "No Socket Stabilizer in inventory.");
                sendUpdate(cmd, false);
                return;
            }
            useSupportMaterial = !useSupportMaterial;
            refreshView(cmd, player, useSupportMaterial
                    ? "Support enabled (Socket Stabilizer will be consumed)."
                    : "Support disabled.");
            sendUpdate(cmd, false);
            return;
        }

        // Equipment/puncher clicks simply refresh current live inventory state.
        refreshView(cmd, player, null);
        sendUpdate(cmd, false);
    }

    private void refreshView(UICommandBuilder cmd, Player player, String warningText) {
        ItemStack heldItem = getHeldItem(player);
        SocketData socketData = null;
        boolean validEquipment = false;

        if (heldItem != null && !heldItem.isEmpty()) {
            boolean isWeapon = ReforgeEquip.isWeapon(heldItem);
            boolean isArmor = !isWeapon && ReforgeEquip.isArmor(heldItem);
            validEquipment = isWeapon || isArmor;
            if (validEquipment) {
                socketData = SocketManager.getSocketData(heldItem);
                if (socketData == null) {
                    socketData = SocketData.fromDefaults(isWeapon ? "weapon" : "armor");
                }
            }
        }

        if (validEquipment && heldItem != null && !heldItem.isEmpty()) {
            String itemName = CommandUtils.getItemDisplayName(heldItem);
            if (itemName == null || itemName.isBlank()) {
                itemName = heldItem.getItemId();
            }
            setText(cmd, "#EquipmentPlaceholder", " ");
            setText(cmd, "#EquipmentItemName", itemName);
            setText(cmd, "#EquipmentItemSockets",
                    socketData.getCurrentSocketCount() + "/" + socketData.getMaxSockets() + " sockets");
        } else {
            setText(cmd, "#EquipmentPlaceholder", "Hold weapon/armor");
            setText(cmd, "#EquipmentItemName", " ");
            setText(cmd, "#EquipmentItemSockets", " ");
        }

        int puncherCount = countMaterial(player, MAIN_MATERIAL_ID);
        if (puncherCount > 0) {
            setText(cmd, "#PuncherPlaceholder", " ");
            setText(cmd, "#PuncherItemName", "Socket Puncher x" + puncherCount);
        } else {
            setText(cmd, "#PuncherPlaceholder", "Socket Puncher");
            setText(cmd, "#PuncherItemName", " ");
        }

        int supportCount = countMaterial(player, SUPPORT_MATERIAL_ID);
        if (useSupportMaterial) {
            setText(cmd, "#SupportPlaceholder", " ");
            setText(cmd, "#SupportItemName", "Stabilizer ON (x" + supportCount + ")");
        } else if (supportCount > 0) {
            setText(cmd, "#SupportPlaceholder", "Optional Booster");
            setText(cmd, "#SupportItemName", "Socket Stabilizer x" + supportCount);
        } else {
            setText(cmd, "#SupportPlaceholder", "Optional Booster");
            setText(cmd, "#SupportItemName", " ");
        }

        int currentSockets = socketData != null ? socketData.getCurrentSocketCount() : 0;
        int maxSockets = socketData != null ? socketData.getMaxSockets() : 4;
        setStats(cmd, currentSockets, maxSockets, useSupportMaterial);
        updateGems(cmd, currentSockets);

        setText(cmd, "#WarningText", warningText != null ? warningText : " ");
    }

    private void setText(UICommandBuilder cmd, String elementId, String text) {
        cmd.set(elementId + ".TextSpans", Message.raw(text));
    }

    private void setStats(UICommandBuilder cmd, int current, int max, boolean withSupport) {
        double successChance = config.getSuccessChance(current);
        double breakChance = config.getBreakChance(current);

        if (withSupport) {
            breakChance *= 0.50;
        }

        setText(cmd, "#SuccessValue", (int) (successChance * 100) + "%");
        setText(cmd, "#BreakValue", (int) (breakChance * 100) + "%");
        setText(cmd, "#SocketCountValue", current + " / " + max);
    }

    private void updateGems(UICommandBuilder cmd, int filledSockets) {
        for (int i = 1; i <= 4; i++) {
            String texPath = i <= filledSockets ? "socket_filled.png" : "socket_empty.png";
            cmd.appendInline(
                    "#Socket" + i,
                    "Group { Background: PatchStyle(TexturePath: \"" + texPath + "\"); "
                            + "Anchor: (Width: 42, Height: 42); }"
            );
        }
    }

    private ItemStack getHeldItem(Player player) {
        if (player == null || player.getInventory() == null || player.getInventory().getHotbar() == null) {
            return null;
        }
        short slot = player.getInventory().getActiveHotbarSlot();
        return player.getInventory().getHotbar().getItemStack(slot);
    }

    private int countMaterial(Player player, String itemId) {
        return countItemInContainer(player.getInventory().getStorage(), itemId)
                + countItemInContainer(player.getInventory().getHotbar(), itemId);
    }

    private boolean hasEnoughMaterial(Player player, String itemId, int requiredAmount) {
        return countMaterial(player, itemId) >= requiredAmount;
    }

    private boolean consumeMaterial(Player player, String itemId, int amount) {
        if (consumeFromContainer(player.getInventory().getStorage(), itemId, amount)) {
            return true;
        }
        return consumeFromContainer(player.getInventory().getHotbar(), itemId, amount);
    }

    private int countItemInContainer(ItemContainer container, String itemId) {
        int count = 0;
        for (short i = 0; i < container.getCapacity(); i++) {
            ItemStack stack = container.getItemStack(i);
            if (stack != null && stack.getItemId().equalsIgnoreCase(itemId)) {
                count += stack.getQuantity();
            }
        }
        return count;
    }

    private boolean consumeFromContainer(ItemContainer container, String itemId, int amount) {
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack != null && stack.getItemId().equalsIgnoreCase(itemId) && stack.getQuantity() >= amount) {
                container.removeItemStackFromSlot(slot, amount, false, false);
                return true;
            }
        }
        return false;
    }
}
