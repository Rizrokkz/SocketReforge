package irai.mod.reforge.UI;

import java.util.List;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Socket.Essence;
import irai.mod.reforge.Socket.EssenceEffect;
import irai.mod.reforge.Socket.EssenceRegistry;
import irai.mod.reforge.Socket.Socket;
import irai.mod.reforge.Socket.SocketData;
import irai.mod.reforge.Socket.SocketManager;
import irai.mod.reforge.Socket.SocketManager.RemoveResult;

/**
 * Essence Socketing UI
 *
 * .ui file: resources/Common/UI/Custom/EssenceSocketUI.ui
 */
public class EssenceSocketUI extends InteractiveCustomUIPage<EssenceSocketUI.Data> {

    // ── Codec ─────────────────────────────────────────────────────────────────

    public static class Data {
        public String action = "";
        public String slot   = "";

        public static final BuilderCodec<Data> CODEC = BuilderCodec
            .builder(Data.class, Data::new)
            .append(new KeyedCodec<>("@Action", Codec.STRING),
                    (d, v) -> d.action = v, d -> d.action).add()
            .append(new KeyedCodec<>("@Slot",   Codec.STRING),
                    (d, v) -> d.slot   = v, d -> d.slot).add()
            .build();
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private ItemStack  equipmentItem = null;
    private SocketData socketData    = null;
    private ItemStack  essenceItem   = null;
    private String     essenceId     = null;
    private boolean    hasCatalyst   = false;

    // ── Constructor ───────────────────────────────────────────────────────────

    public EssenceSocketUI(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, Data.CODEC);
    }

    // ── build() ───────────────────────────────────────────────────────────────

    @Override
    public void build(
            @Nonnull Ref<EntityStore>   ref,
            @Nonnull UICommandBuilder   cmd,
            @Nonnull UIEventBuilder     evt,
            @Nonnull Store<EntityStore> store
    ) {
        cmd.append("EssenceSocketUI.ui");

        evt.addEventBinding(CustomUIEventBindingType.MouseButtonReleased, "#SocketEssenceButton",
                EventData.of("@Action", "socket"), true);
        evt.addEventBinding(CustomUIEventBindingType.MouseButtonReleased, "#RemoveEssenceButton",
                EventData.of("@Action", "remove"), true);
        evt.addEventBinding(CustomUIEventBindingType.MouseButtonReleased, "#EquipmentSlot",
                new EventData().append("@Action", "slot").append("@Slot", "equipment"), true);
        evt.addEventBinding(CustomUIEventBindingType.MouseButtonReleased, "#EssenceSlot",
                new EventData().append("@Action", "slot").append("@Slot", "essence"), true);
        evt.addEventBinding(CustomUIEventBindingType.MouseButtonReleased, "#CatalystSlot",
                new EventData().append("@Action", "slot").append("@Slot", "catalyst"), true);

        sendUpdate(cmd,false);
    }

    // ── handleDataEvent() ─────────────────────────────────────────────────────

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore>   ref,
            @Nonnull Store<EntityStore> store,
            Data data
    ) {
        super.handleDataEvent(ref, store, data);

        if (data == null || data.action == null || data.action.isEmpty()) {
            sendUpdate(); return;
        }

        switch (data.action) {
            case "socket" -> handleSocketEssence();
            case "remove" -> handleRemoveEssence();
            case "slot"   -> handleSlotClick(data.slot);
            default       -> sendUpdate();
        }
    }

    // ── Socket essence ────────────────────────────────────────────────────────

    private void handleSocketEssence() {
        UICommandBuilder cmd = new UICommandBuilder();

        if (equipmentItem == null || socketData == null) {
            setText(cmd, "#FeedbackText", "Place equipment first!");
            sendUpdate(cmd,false); return;
        }
        if (essenceId == null) {
            setText(cmd, "#FeedbackText", "Place an essence in the slot!");
            sendUpdate(cmd,false); return;
        }
        if (!socketData.hasEmptySocket()) {
            setText(cmd, "#FeedbackText", "No empty sockets! Punch more sockets first.");
            sendUpdate(cmd,false); return;
        }

        if (SocketManager.socketEssence(socketData, essenceId)) {
            essenceItem = null;
            essenceId   = null;
            setText(cmd, "#EssencePlaceholder", "Fire, Ice...");
            setText(cmd, "#EssenceItemName",    " ");
            setText(cmd, "#EssenceItemTier",    " ");
            setText(cmd, "#FeedbackText", "Essence socketed successfully!");
            clearEffectLines(cmd);
        } else {
            setText(cmd, "#FeedbackText", "No empty socket found.");
        }

        updateGems(cmd, socketData);
        sendUpdate(cmd,false);
    }

    // ── Remove essence ────────────────────────────────────────────────────────

    private void handleRemoveEssence() {
        UICommandBuilder cmd = new UICommandBuilder();

        if (equipmentItem == null || socketData == null) {
            setText(cmd, "#FeedbackText", "Place equipment first!");
            sendUpdate(cmd,false); return;
        }
        if (!hasCatalyst) {
            setText(cmd, "#FeedbackText", "Place a Removal Tool in the catalyst slot!");
            sendUpdate(cmd,false); return;
        }

        int targetSlot = -1;
        for (Socket socket : socketData.getSockets()) {
            if (!socket.isEmpty()) { targetSlot = socket.getSlotIndex(); break; }
        }

        if (targetSlot == -1) {
            setText(cmd, "#FeedbackText", "No filled sockets to remove!");
            sendUpdate(cmd,false); return;
        }

        RemoveResult result = SocketManager.removeEssence(socketData, targetSlot);
        hasCatalyst = false;
        setText(cmd, "#CatalystPlaceholder", "Removal Tool");
        setText(cmd, "#CatalystItemName",    " ");

        switch (result) {
            case SUCCESS              -> setText(cmd, "#FeedbackText", "Essence removed. The essence was destroyed.");
            case FAIL_TOOL_DESTROYED  -> setText(cmd, "#FeedbackText", "Removal failed. The tool was destroyed.");
        }

        updateGems(cmd, socketData);
        sendUpdate(cmd,false);
    }

    // ── Slot click ────────────────────────────────────────────────────────────

    private void handleSlotClick(String slotName) {
        if (slotName == null || slotName.isEmpty()) { sendUpdate(); return; }

        UICommandBuilder cmd = new UICommandBuilder();

        switch (slotName) {
            case "equipment" -> {
                if (equipmentItem == null) {
                    equipmentItem = ItemStack.EMPTY;
                    socketData    = SocketData.fromDefaults("weapon");
                    // Simulate one pre-filled socket for demo
                    socketData.addSocket();
                    socketData.socketEssence("Essence_Fire_1");
                    socketData.addSocket(); // one empty
                    setText(cmd, "#EquipmentPlaceholder",   " ");
                    setText(cmd, "#EquipmentItemName",      "Iron Sword");
                    setText(cmd, "#EquipmentSocketDisplay",
                        socketData.getCurrentSocketCount() + "/" + socketData.getMaxSockets() + " sockets");
                } else {
                    equipmentItem = null;
                    socketData    = null;
                    setText(cmd, "#EquipmentPlaceholder",   "Drop Equipment");
                    setText(cmd, "#EquipmentItemName",      " ");
                    setText(cmd, "#EquipmentSocketDisplay", " ");
                }
                updateGems(cmd, socketData);
            }
            case "essence" -> {
                if (essenceItem == null) {
                    essenceItem = ItemStack.EMPTY;
                    essenceId   = "Essence_Fire_3";
                    Essence e   = EssenceRegistry.get().getById(essenceId);
                    setText(cmd, "#EssencePlaceholder", " ");
                    setText(cmd, "#EssenceItemName",    e != null ? e.getDisplayName() : essenceId);
                    setText(cmd, "#EssenceItemTier",    e != null ? "Tier " + (e.getTier().ordinal() + 1) : " ");
                    updateEffectLines(cmd, e);
                } else {
                    essenceItem = null;
                    essenceId   = null;
                    setText(cmd, "#EssencePlaceholder", "Fire, Ice...");
                    setText(cmd, "#EssenceItemName",    " ");
                    setText(cmd, "#EssenceItemTier",    " ");
                    clearEffectLines(cmd);
                }
            }
            case "catalyst" -> {
                hasCatalyst = !hasCatalyst;
                if (hasCatalyst) {
                    setText(cmd, "#CatalystPlaceholder", " ");
                    setText(cmd, "#CatalystItemName",    "Removal Tool");
                } else {
                    setText(cmd, "#CatalystPlaceholder", "Removal Tool");
                    setText(cmd, "#CatalystItemName",    " ");
                }
            }
        }

        sendUpdate(cmd,false);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Targets Label.TextSpans — the correct property for cmd.set() on a Label. */
    private void setText(UICommandBuilder cmd, String elementId, String text) {
        cmd.set(elementId + ".TextSpans", Message.raw(text));
    }

    /**
     * Re-draws socket gem backgrounds using absolute texture paths in appendInline.
     * @variables from the parent .ui are NOT available inside appendInline markup.
     */
    private void updateGems(UICommandBuilder cmd, SocketData sd) {
        List<Socket> sockets = sd != null ? sd.getSockets() : List.of();
        int maxSockets       = sd != null ? sd.getMaxSockets() : 4;

        for (int i = 1; i <= 4; i++) {
            String texPath;
            if (i <= sockets.size()) {
                texPath = sockets.get(i - 1).isEmpty() ? "socket_empty.png" : "socket_filled.png";
            } else {
                texPath = "socket_empty.png";
            }
            cmd.appendInline(
                "#Socket" + i,
                "Group { Background: PatchStyle(TexturePath: \"" + texPath + "\"); "
                + "Anchor: (Width: 42, Height: 42); }"
            );
        }
    }

    private void updateEffectLines(UICommandBuilder cmd, Essence essence) {
        if (essence == null) { clearEffectLines(cmd); return; }
        List<EssenceEffect> effects = essence.getEffects();
        for (int i = 1; i <= 3; i++) {
            String line = i <= effects.size()
                ? "* " + effects.get(i - 1).getDisplayLine(essence.getDisplayName())
                : " ";
            setText(cmd, "#EffectLine" + i, line);
        }
    }

    private void clearEffectLines(UICommandBuilder cmd) {
        setText(cmd, "#EffectLine1", " ");
        setText(cmd, "#EffectLine2", " ");
        setText(cmd, "#EffectLine3", " ");
    }
}
