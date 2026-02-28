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
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
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
import irai.mod.reforge.Util.DynamicTooltipUtils;

/**
 * Socket Punching UI - Standard Hytale UI implementation.
 */
public class SocketPunchUI extends InteractiveCustomUIPage<SocketPunchUI.Data> {

    private static final String MAIN_MATERIAL_ID = "Socket_Puncher";
    private static final String SUPPORT_MATERIAL_ID = "Socket_Stabilizer";

    private static SocketConfig config = new SocketConfig();

    private ItemStack selectedEquipment = null;
    private int selectedEquipmentSlot = -1;
    private boolean useSupportMaterial = false;

    public static void setConfig(SocketConfig cfg) {
        config = cfg;
        SocketManager.setConfig(cfg);
    }

    public SocketPunchUI(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, Data.CODEC);
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

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt,
                      @Nonnull Store<EntityStore> store) {
        
        // Load the UI file
        cmd.append("SocketPunchUI.ui");

        // Set up event bindings for buttons
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#EquipmentSlot",
                new EventData().append("@Action", "slot").append("@Slot", "equipment"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#PuncherSlot",
                new EventData().append("@Action", "slot").append("@Slot", "puncher"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SupportSlot",
                new EventData().append("@Action", "slot").append("@Slot", "support"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#PunchSocketButton",
                EventData.of("@Action", "punch"), false);

        Player player = store.getComponent(ref, Player.getComponentType());
        refreshView(cmd, player);
        sendUpdate(cmd, false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull Data data) {
        super.handleDataEvent(ref, store, data);

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        UICommandBuilder cmd = new UICommandBuilder();

        if (data == null || data.action == null || data.action.isEmpty()) {
            refreshView(cmd, player);
            sendUpdate(cmd, false);
            return;
        }

        switch (data.action) {
            case "slot":
                handleSlotAction(ref, store, player, data.slot, cmd);
                break;
            case "punch":
                handlePunchAction(ref, store, player, cmd);
                break;
            default:
                refreshView(cmd, player);
                break;
        }

        sendUpdate(cmd, false);
    }

    private void handleSlotAction(Ref<EntityStore> ref, Store<EntityStore> store, 
                                  Player player, String slot, UICommandBuilder cmd) {
        switch (slot) {
            case "equipment":
                ItemStack heldItem = CommandUtils.getSelectedItem(player);
                if (heldItem != null && !heldItem.isEmpty()) {
                    selectedEquipment = heldItem;
                    selectedEquipmentSlot = player.getInventory().getActiveHotbarSlot();
                }
                break;
            case "support":
                useSupportMaterial = !useSupportMaterial;
                break;
        }
        refreshView(cmd, player);
    }

    private void handlePunchAction(Ref<EntityStore> ref, Store<EntityStore> store,
                                   Player player, UICommandBuilder cmd) {
        if (selectedEquipment == null) {
            cmd.set("#PunchSocketButton.TextSpans", Message.raw("Select Equipment First!"));
            refreshView(cmd, player);
            return;
        }

        SocketData socketData = SocketManager.getSocketData(selectedEquipment);
        if (socketData == null) {
            cmd.set("#PunchSocketButton.TextSpans", Message.raw("Cannot Add Sockets!"));
            refreshView(cmd, player);
            return;
        }

        if (!socketData.canAddSocket()) {
            cmd.set("#PunchSocketButton.TextSpans", Message.raw("Max Sockets Reached!"));
            refreshView(cmd, player);
            return;
        }

        SupportMaterial supportMaterial = useSupportMaterial ? SupportMaterial.SOCKET_STABILIZER : SupportMaterial.NONE;
        PunchResult result = SocketManager.punchSocket(socketData, supportMaterial);

        if (result == PunchResult.SUCCESS) {
            ItemStack updatedItem = SocketManager.withSocketData(selectedEquipment, socketData);
            player.getInventory().getHotbar().setItemStackForSlot((short) selectedEquipmentSlot, updatedItem);
            
            cmd.set("#PunchSocketButton.TextSpans", Message.raw("Success!"));
            
            if (!socketData.canAddSocket()) {
                selectedEquipment = null;
                selectedEquipmentSlot = -1;
            } else {
                selectedEquipment = updatedItem;
            }
        } else if (result == PunchResult.BREAK) {
            // Item broke during punch - reduce max sockets instead of destroying
            socketData.reduceMaxSockets();
            ItemStack brokenItem = SocketManager.withSocketData(selectedEquipment, socketData);
            player.getInventory().getHotbar().setItemStackForSlot((short) selectedEquipmentSlot, brokenItem);
            
            // Update tooltip
            String itemId = selectedEquipment.getItemId();
            socketData.registerTooltips(itemId);
            DynamicTooltipUtils.refreshAllPlayers();
            
            cmd.set("#PunchSocketButton.TextSpans", Message.raw("Item Damaged! Max Sockets: " + socketData.getMaxSockets()));
            selectedEquipment = brokenItem;
        } else {
            cmd.set("#PunchSocketButton.TextSpans", Message.raw("Failed - Try Again!"));
        }

        refreshView(cmd, player);
    }

    private void refreshView(UICommandBuilder cmd, Player player) {
        if (player == null) return;

        Inventory inventory = player.getInventory();
        boolean hasPuncher = hasItemInInventory(inventory, MAIN_MATERIAL_ID);
        boolean hasStabilizer = hasItemInInventory(inventory, SUPPORT_MATERIAL_ID);

        // Update equipment slot button text
        if (selectedEquipment != null && !selectedEquipment.isEmpty()) {
            SocketData socketData = SocketManager.getSocketData(selectedEquipment);
            int currentSockets = socketData != null ? socketData.getCurrentSocketCount() : 0;
            int maxSockets = socketData != null ? socketData.getMaxSockets() : getMaxSockets(selectedEquipment);
            
            String itemName = selectedEquipment.getItemId();
            cmd.set("#EquipmentSlot.TextSpans", Message.raw(itemName + " [" + currentSockets + "/" + maxSockets + "]"));
        } else {
            cmd.set("#EquipmentSlot.TextSpans", Message.raw("Equipment"));
        }

        // Update material slot button text
        cmd.set("#PuncherSlot.TextSpans", Message.raw(hasPuncher ? "Material ✓" : "Material"));
        
        // Update support slot button text
        if (useSupportMaterial && hasStabilizer) {
            cmd.set("#SupportSlot.TextSpans", Message.raw("Support (Active)"));
        } else {
            cmd.set("#SupportSlot.TextSpans", Message.raw("Support"));
        }
    }

    private boolean hasItemInInventory(Inventory inventory, String itemId) {
        try {
            for (short i = 0; i < 9; i++) {
                ItemStack item = inventory.getHotbar().getItemStack(i);
                if (item != null && !item.isEmpty() && itemId.equals(item.getItemId())) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return false;
    }

    private int getMaxSockets(ItemStack item) {
        if (item == null) return 4;
        boolean isWeapon = ReforgeEquip.isWeapon(item);
        return isWeapon ? config.getMaxSocketsWeapon() : config.getMaxSocketsArmor();
    }
}
