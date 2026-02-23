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
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Config.SocketConfig;
import irai.mod.reforge.Socket.SocketData;
import irai.mod.reforge.Socket.SocketManager;
import irai.mod.reforge.Socket.SocketManager.PunchResult;
import irai.mod.reforge.Socket.SocketManager.SupportMaterial;

/**
 * Socket Punching UI
 *
 * .ui file: resources/Common/UI/Custom/SocketPunchUI.ui
 *
 * Usage from SocketPunchCommand:
 *   page = new SocketPunchUI(playerRef);
 *
 * Config from ReforgePlugin:
 *   SocketPunchUI.setConfig(socketCfg);
 */
public class SocketPunchUI extends InteractiveCustomUIPage<SocketPunchUI.Data> {

    // ── Static config ─────────────────────────────────────────────────────────

    private static SocketConfig config = new SocketConfig();

    public static void setConfig(SocketConfig cfg) {
        config = cfg;
        SocketManager.setConfig(cfg);
    }

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

    private ItemStack      equipmentItem = null;
    private SocketData     socketData    = null;
    private ItemStack      puncherItem   = null;
    private SupportMaterial support      = SupportMaterial.NONE;

    // Tracks filled socket count for gem display (derived from socketData, cached for speed)
    private int displayFilledSockets = 0;
    private int displayMaxSockets    = 4;

    // ── Constructor ───────────────────────────────────────────────────────────

    public SocketPunchUI(@Nonnull PlayerRef playerRef) {
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
        // WeaponStatsUI uses filename-only lookup for docs under Common/UI/Custom.
        cmd.append("SocketPunchUI.ui");

        // Use built-in button controls inside slot groups so activating bindings are valid.
        cmd.append("#PunchSocketButton", "Common/TextButton.ui");
        cmd.append("#EquipmentSlot", "Common/TextButton.ui");
        cmd.append("#PuncherSlot", "Common/TextButton.ui");
        cmd.append("#SupportSlot", "Common/TextButton.ui");

        cmd.set("#PunchSocketButton #Button.Text", "Punch");
        cmd.set("#EquipmentSlot #Button.Text", "Equip");
        cmd.set("#PuncherSlot #Button.Text", "Puncher");
        cmd.set("#SupportSlot #Button.Text", "Booster");

        // Bind events
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#PunchSocketButton #Button",
                EventData.of("@Action", "punch"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#EquipmentSlot #Button",
                new EventData().append("@Action", "slot").append("@Slot", "equipment"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#PuncherSlot #Button",
                new EventData().append("@Action", "slot").append("@Slot", "puncher"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SupportSlot #Button",
                new EventData().append("@Action", "slot").append("@Slot", "support"), false);

        // Set initial stat display
        setStats(cmd, 0, displayMaxSockets);
        // Initial socket gem state is already correct in .ui (2 filled, 2 empty default)
        // so no gem update needed on first build.

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
            case "punch" -> handlePunch();
            case "slot"  -> handleSlotClick(data.slot);
            default      -> sendUpdate();
        }
    }

    // ── Punch ─────────────────────────────────────────────────────────────────

    private void handlePunch() {
        UICommandBuilder cmd = new UICommandBuilder();

        if (equipmentItem == null) {
            setText(cmd, "#WarningText", "Place equipment in the slot first!");
            sendUpdate(cmd,false); return;
        }
        if (puncherItem == null) {
            setText(cmd, "#WarningText", "Place a Socket Puncher first!");
            sendUpdate(cmd,false); return;
        }
        if (socketData == null || !socketData.canAddSocket()) {
            setText(cmd, "#WarningText", "This item already has the maximum sockets.");
            sendUpdate(cmd,false); return;
        }

        PunchResult result = SocketManager.punchSocket(socketData, support);

        // Puncher always consumed
        puncherItem = null;
        setText(cmd, "#PuncherPlaceholder", "Socket Puncher");
        setText(cmd, "#PuncherItemName",    " ");

        // Support material consumed on use
        if (support != SupportMaterial.NONE) {
            support = SupportMaterial.NONE;
            setText(cmd, "#SupportPlaceholder", "Optional Booster");
            setText(cmd, "#SupportItemName",    " ");
        }

        switch (result) {
            case SUCCESS -> {
                displayFilledSockets = socketData.getCurrentSocketCount();
                setText(cmd, "#WarningText", "Socket successfully punched!");
                updateGems(cmd);
            }
            case FAIL -> setText(cmd, "#WarningText", "Punching failed. The Puncher was consumed.");
            case BREAK -> {
                equipmentItem        = null;
                socketData           = null;
                displayFilledSockets = 0;
                displayMaxSockets    = 4;
                setText(cmd, "#EquipmentPlaceholder",  "Drop Equipment");
                setText(cmd, "#EquipmentItemName",     " ");
                setText(cmd, "#EquipmentItemSockets",  " ");
                setText(cmd, "#WarningText", "The item was destroyed!");
                updateGems(cmd);
            }
        }

        setStats(cmd, socketData != null ? socketData.getCurrentSocketCount() : 0, displayMaxSockets);
        sendUpdate(cmd,false);
    }

    // ── Slot click ────────────────────────────────────────────────────────────

    private void handleSlotClick(String slotName) {
        if (slotName == null || slotName.isEmpty()) { sendUpdate(); return; }

        UICommandBuilder cmd = new UICommandBuilder();

        switch (slotName) {
            case "equipment" -> {
                if (equipmentItem == null) {
                    equipmentItem        = ItemStack.EMPTY;
                    socketData           = SocketData.fromDefaults("weapon");
                    displayFilledSockets = socketData.getCurrentSocketCount();
                    displayMaxSockets    = socketData.getMaxSockets();
                    setText(cmd, "#EquipmentPlaceholder",  " ");
                    setText(cmd, "#EquipmentItemName",     "Iron Sword");
                    setText(cmd, "#EquipmentItemSockets",
                        displayFilledSockets + "/" + displayMaxSockets + " sockets");
                } else {
                    equipmentItem        = null;
                    socketData           = null;
                    displayFilledSockets = 0;
                    displayMaxSockets    = 4;
                    setText(cmd, "#EquipmentPlaceholder",  "Drop Equipment");
                    setText(cmd, "#EquipmentItemName",     " ");
                    setText(cmd, "#EquipmentItemSockets",  " ");
                }
                updateGems(cmd);
            }
            case "puncher" -> {
                if (puncherItem == null) {
                    puncherItem = ItemStack.EMPTY;
                    setText(cmd, "#PuncherPlaceholder", " ");
                    setText(cmd, "#PuncherItemName",    "Socket Puncher");
                } else {
                    puncherItem = null;
                    setText(cmd, "#PuncherPlaceholder", "Socket Puncher");
                    setText(cmd, "#PuncherItemName",    " ");
                }
            }
            case "support" -> {
                if (support == SupportMaterial.NONE) {
                    support = SupportMaterial.SOCKET_REINFORCER;
                    setText(cmd, "#SupportPlaceholder", " ");
                    setText(cmd, "#SupportItemName",    "Socket Reinforcer");
                } else {
                    support = SupportMaterial.NONE;
                    setText(cmd, "#SupportPlaceholder", "Optional Booster");
                    setText(cmd, "#SupportItemName",    " ");
                }
            }
        }

        setStats(cmd, socketData != null ? socketData.getCurrentSocketCount() : 0, displayMaxSockets);
        sendUpdate(cmd,false);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * cmd.set() targets the TextSpans property on a Label.
     * The correct selector format is "#ElementId.TextSpans"
     */
    private void setText(UICommandBuilder cmd, String elementId, String text) {
        cmd.set(elementId + ".TextSpans", Message.raw(text));
    }

    private void setStats(UICommandBuilder cmd, int current, int max) {
        double successChance = config.getSuccessChance(current);
        double breakChance   = config.getBreakChance(current);

        switch (support) {
            case SOCKET_STABILIZER -> breakChance   *= 0.50;
            case SOCKET_REINFORCER -> successChance  = Math.min(1.0, successChance + 0.20);
            case SOCKET_GUARANTOR  -> { if (current == 0) successChance = 1.0; }
            default -> {}
        }

        setText(cmd, "#SuccessValue",     (int)(successChance * 100) + "%");
        setText(cmd, "#BreakValue",       (int)(breakChance   * 100) + "%");
        setText(cmd, "#SocketCountValue", current + " / " + max);
    }

    /**
     * Updates socket gem backgrounds by re-appending inline markup.
     * Uses absolute texture paths instead of @variables, because appendInline
     * does NOT have access to the @var declarations from the parent .ui file.
     */
    private void updateGems(UICommandBuilder cmd) {
        for (int i = 1; i <= 4; i++) {
            String texPath = i <= displayFilledSockets ? "socket_filled.png" : "socket_empty.png";
            cmd.appendInline(
                "#Socket" + i,
                "Group { Background: PatchStyle(TexturePath: \"" + texPath + "\"); "
                + "Anchor: (Width: 42, Height: 42); }"
            );
        }
    }
}
