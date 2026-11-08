package irai.mod.reforge.UI;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.PatchStyle;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Common.PlayerInventoryUtils;
import irai.mod.reforge.Common.CorruptionUtils;
import irai.mod.reforge.Common.UI.HyUIReflectionUtils;
import irai.mod.reforge.Common.UI.UIHtmlUtils;
import irai.mod.reforge.Common.UI.UIItemUtils;
import irai.mod.reforge.Common.UI.UISocketVisualUtils;
import irai.mod.reforge.Common.UI.UITemplateUtils;
import irai.mod.reforge.Lore.LoreAbility;
import irai.mod.reforge.Lore.LoreAbilityRegistry;
import irai.mod.reforge.Lore.LoreGemRegistry;
import irai.mod.reforge.Lore.LoreHeldItemUpdateManager;
import irai.mod.reforge.Lore.LoreSocketData;
import irai.mod.reforge.Lore.LoreSocketManager;
import irai.mod.reforge.Progression.CharacterPathService;
import irai.mod.reforge.Lore.LoreSoulstoneUtils;
import irai.mod.reforge.Socket.Socket;
import irai.mod.reforge.Socket.SocketData;
import irai.mod.reforge.Socket.SocketManager;
import irai.mod.reforge.Util.LangLoader;

/**
 * HyUI lore gem socketing page opened through command.
 * Uses reflection so the plugin still runs when HyUI is not present.
 */
public final class LoreSocketBenchUI {
    private LoreSocketBenchUI() {}

    private static final String HYUI_PAGE_BUILDER = "au.ellie.hyui.builders.PageBuilder";
    private static final String HYUI_PLUGIN = "au.ellie.hyui.HyUIPlugin";
    private static final String HYUI_EVENT_BINDING = "com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType";
    private static final String UI_COMMAND_BUILDER = "com.hypixel.hytale.server.core.ui.builder.UICommandBuilder";
    private static final String TEMPLATE_PATH = "Common/UI/Custom/Pages/LoreSocketBench.html";
    private static final String NATIVE_PAGE_UI = "Pages/LoreSocketBench.ui";
    private static final String EQUIPMENT_CARD_UI = "Pages/EssenceBenchEquipmentCard.ui";
    private static final String GEM_CARD_UI = "Pages/EssenceBenchEssenceCard.ui";
    private static final String SUPPORT_CARD_UI = "Pages/EssenceBenchSupportCard.ui";
    private static final String LORE_SOCKET_TILE_UI = "Pages/LoreSocketBenchTile.ui";
    private static final String LORE_PROGRESS_NODE_UI = "Pages/LoreSocketBenchProgressNode.ui";
    private static final int[] LORE_PROGRESS_NODE_FRAMES = {21, 20, 19, 18, 17};
    private static final int LORE_PROGRESS_TRACK_WIDTH = 728;
    private static final int LORE_PROGRESS_TRACK_LEFT = 28;
    private static final int LORE_PROGRESS_TRACK_RIGHT = 28;
    private static final int LORE_PROGRESS_CANVAS_WIDTH = LORE_PROGRESS_TRACK_WIDTH
            + LORE_PROGRESS_TRACK_LEFT + LORE_PROGRESS_TRACK_RIGHT;
    private static final String[] LORE_SOCKET_COLOR_KEYS = {
            "none", "locked", "red", "blue", "green", "purple", "yellow", "orange", "black", "white", "cyan"
    };
    private static final String SUPPORT_REROLL_ITEM_ID = "Ingredient_Resonant_Essence";
    private static final String SUPPORT_CLEAR_ITEM_ID = "Ingredient_Ghastly_Essence";
    private static final int EQUIPMENT_CARDS_PER_PAGE = 4;
    private static final int MATERIAL_CARDS_PER_PAGE = 5;

    private static boolean nativeUiAvailable = true;
    private static final Map<PlayerRef, Object> openPages = new ConcurrentHashMap<>();
    private static final Map<PlayerRef, SelectionState> pendingSelections = new ConcurrentHashMap<>();
    private static final Map<PlayerRef, Boolean> pendingNavToFeed = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private enum ContainerKind {
        HOTBAR,
        STORAGE
    }

    private enum MaterialKind {
        GEM,
        REROLL,
        FEED,
        CLEAR,
        UNKNOWN
    }

    private static final class Entry {
        final ContainerKind kind;
        final short slot;
        final ItemStack item;
        final String itemId;
        final int quantity;
        final String displayName;

        Entry(ContainerKind kind, short slot, ItemStack item, String itemId, int quantity, String displayName) {
            this.kind = kind;
            this.slot = slot;
            this.item = item;
            this.itemId = itemId;
            this.quantity = quantity;
            this.displayName = displayName;
        }
    }

    private static final class Snapshot {
        final List<Entry> equipments;
        final List<Entry> gems;
        final List<Entry> runes;
        final List<Entry> supports;

        Snapshot(List<Entry> equipments, List<Entry> gems, List<Entry> runes, List<Entry> supports) {
            this.equipments = equipments;
            this.gems = gems;
            this.runes = runes;
            this.supports = supports;
        }
    }

    private static final class SelectionState {
        final String equipmentKey;
        final String gemKey;
        final String supportKey;
        final String slotKey;
        final String statusText;
        final int equipmentPage;
        final int gemPage;
        final int supportPage;
        final String detailEquipmentKey;
        final String detailSlotKey;

        SelectionState(String equipmentKey, String gemKey, String supportKey, String slotKey, String statusText) {
            this(equipmentKey, gemKey, supportKey, slotKey, statusText, 0, 0, 0, null, null);
        }

        SelectionState(String equipmentKey,
                       String gemKey,
                       String supportKey,
                       String slotKey,
                       String statusText,
                       int equipmentPage,
                       int gemPage,
                       int supportPage,
                       String detailEquipmentKey,
                       String detailSlotKey) {
            this.equipmentKey = equipmentKey;
            this.gemKey = gemKey;
            this.supportKey = supportKey;
            this.slotKey = slotKey;
            this.statusText = statusText;
            this.equipmentPage = equipmentPage;
            this.gemPage = gemPage;
            this.supportPage = supportPage;
            this.detailEquipmentKey = detailEquipmentKey;
            this.detailSlotKey = detailSlotKey;
        }
    }

    private static final class ProcessResult {
        final String status;

        ProcessResult(String status) {
            this.status = status;
        }
    }

    public static void initialize() {
        nativeUiAvailable = true;
    }

    public static boolean isAvailable() {
        return nativeUiAvailable;
    }

    public static void open(Player player) {
        if (player == null) {
            return;
        }
        PlayerRef playerRef = player.getPlayerRef();
        LoreUiState.setActive(playerRef, LoreUiState.Page.SOCKET);
        pendingNavToFeed.remove(playerRef);
        closePageIfOpen(playerRef);
        pendingSelections.remove(playerRef);
        player.getWorld().execute(() -> openWithSync(player));
    }

    private static void openWithSync(Player player) {
        if (player == null) {
            return;
        }
        PlayerRef playerRef = player.getPlayerRef();
        if (!LoreUiState.isActive(playerRef, LoreUiState.Page.SOCKET)) {
            return;
        }
        Snapshot snapshot = collectSnapshot(player);
        SelectionState selectionState = pendingSelections.remove(playerRef);
        if (selectionState == null && !snapshot.equipments.isEmpty()) {
            selectionState = new SelectionState("0", null, null, "", null);
        }
        openPage(player, snapshot, selectionState);
    }

    private static Snapshot collectSnapshot(Player player) {
        List<Entry> equipments = new ArrayList<>();
        List<Entry> gems = new ArrayList<>();
        List<Entry> runes = new ArrayList<>();
        List<Entry> supports = new ArrayList<>();
        collectFromContainer(player, player.getInventory().getHotbar(), ContainerKind.HOTBAR, equipments, gems, runes, supports);
        collectFromContainer(player, player.getInventory().getStorage(), ContainerKind.STORAGE, equipments, gems, runes, supports);
        return new Snapshot(equipments, gems, runes, supports);
    }

    private static void collectFromContainer(Player player,
                                             ItemContainer container,
                                             ContainerKind kind,
                                             List<Entry> equipments,
                                             List<Entry> gems,
                                             List<Entry> runes,
                                             List<Entry> supports) {
        if (container == null) {
            return;
        }
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            String itemId = stack.getItemId();
            if (itemId == null || itemId.isBlank()) {
                continue;
            }
            if (LoreSocketManager.isEquipment(stack)) {
                LoreSocketData data = LoreSocketManager.getLoreSocketData(stack);
                if (data != null && data.getSocketCount() > 0) {
                    ItemStack effective = stack;
                    if (LoreSocketManager.syncSocketColors(stack, data)) {
                        effective = LoreSocketManager.withLoreSocketData(stack, data);
                        container.setItemStackForSlot(slot, effective);
                    }
                    String name = UIItemUtils.displayNameOrItemId(effective, player);
                    equipments.add(new Entry(kind, slot, effective, itemId, effective.getQuantity(), name));
                }
            }
            if (LoreSocketManager.isLoreGem(stack)) {
                String name = UIItemUtils.displayNameOrItemId(stack, player);
                gems.add(new Entry(kind, slot, stack, itemId, stack.getQuantity(), name));
            }
            if (LoreSocketManager.isAbilityRunestone(stack) || LoreSocketManager.isModifierRunestone(stack)) {
                String name = UIItemUtils.displayNameOrItemId(stack, player);
                runes.add(new Entry(kind, slot, stack, itemId, stack.getQuantity(), name));
            }
            if (isLoreSupportItem(itemId)) {
                String name = UIItemUtils.displayNameOrItemId(stack, player);
                supports.add(new Entry(kind, slot, stack, itemId, stack.getQuantity(), name));
            }
        }
    }

    private static boolean isLoreSupportItem(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        if (itemId.equalsIgnoreCase(SUPPORT_REROLL_ITEM_ID) || isRuneFeedItem(itemId)) {
            return true;
        }
        return matchesAnyId(itemId, LoreSocketManager.getConfig().getFeedItemIds())
                || matchesAnyId(itemId, LoreSocketManager.getConfig().getClearItemIds());
    }

    private static boolean isRuneFeedItem(String itemId) {
        return itemId != null && (itemId.equalsIgnoreCase(LoreSocketManager.RUNE_DAMAGE_FEED_ITEM_ID)
                || itemId.equalsIgnoreCase(LoreSocketManager.RUNE_MANA_FEED_ITEM_ID));
    }

    private static boolean matchesAnyId(String itemId, String[] ids) {
        if (itemId == null || itemId.isBlank() || ids == null) {
            return false;
        }
        for (String id : ids) {
            if (id != null && itemId.equalsIgnoreCase(id.trim())) {
                return true;
            }
        }
        return false;
    }

    private static void openPage(Player player, Snapshot snapshot, SelectionState selectionState) {
        PlayerRef playerRef = player.getPlayerRef();
        try {
            Ref<EntityStore> entityRef = playerRef.getReference();
            Store<EntityStore> store = entityRef == null ? null : entityRef.getStore();
            if (entityRef == null || store == null) return;
            NativeLoreSocketBenchPage page = new NativeLoreSocketBenchPage(playerRef, snapshot, selectionState);
            player.getPageManager().openCustomPage(entityRef, store, page);
            openPages.put(playerRef, page);
        } catch (Exception e) {
            System.err.println("[SocketReforge] Native LoreSocketBenchUI open error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String buildHtml(Player player, Snapshot snapshot, SelectionState state) {
        String equipmentKey = state != null ? state.equipmentKey : null;
        String gemKey = state != null ? state.gemKey : null;
        String supportKey = state != null ? state.supportKey : null;
        String slotKey = state != null ? state.slotKey : null;
        String status = state != null && state.statusText != null
                ? state.statusText
                : LangLoader.getUITranslation(player, "ui.lore_socket.status_idle");

        Entry selectedEquipment = findByKey(snapshot.equipments, equipmentKey);
        Entry selectedGem = findByKey(materialEntries(snapshot, selectedEquipment), gemKey);
        Entry selectedSupport = findByKey(snapshot.supports, supportKey);

        String html = loadTemplate();
        html = html.replace("{{equipmentCards}}", buildEquipmentCardsHtml(player, snapshot, state));
        html = html.replace("{{materialsCards}}", buildMaterialsCardsHtml(player, snapshot, state, selectedEquipment));
        html = html.replace("{{selectedEquipmentLabel}}", escapeHtml(selectedEquipment != null
                ? selectedEquipment.displayName
                : LangLoader.getUITranslation(player, "ui.lore_socket.metadata_no_equipment")));
        html = html.replace("{{selectedGemLabel}}", escapeHtml(selectedGem != null
                ? selectedGem.displayName
                : LangLoader.getUITranslation(player, "ui.lore_socket.option_none")));
        html = html.replace("{{selectedSupportLabel}}", escapeHtml(selectedSupport != null
                ? selectedSupport.displayName
                : LangLoader.getUITranslation(player, "ui.lore_socket.option_none")));
        html = html.replace("{{slotLabel}}", escapeHtml(buildSelectedSlotLabel(player, selectedEquipment, slotKey)));
        html = html.replace("{{socketIcons}}", buildSocketPreview(player, selectedEquipment, slotKey));
        html = html.replace("{{slotDetails}}", buildSelectedSlotSummaryHtml(player, selectedEquipment, slotKey));
        html = html.replace("{{statusText}}", escapeHtml(status));
        html = html.replace("{{metadataText}}", escapeHtml(buildMetadata(player, selectedEquipment)));
        html = html.replace("{{processDisabledAttr}}", shouldDisable(selectedEquipment, selectedGem, selectedSupport, slotKey) ? "disabled=\"true\"" : "");
        html = html.replace("{{processButtonLabel}}", escapeHtml(resolveProcessButtonLabel(player, selectedEquipment, selectedGem, selectedSupport, slotKey)));
        html = html.replace("{{detailOverlay}}", "");
        return LangLoader.replaceUiTokens(player, html);
    }

    private static SelectionState stateWith(SelectionState state,
                                            String equipmentKey,
                                            String gemKey,
                                            String supportKey,
                                            String slotKey,
                                            String statusText,
                                            String detailEquipmentKey,
                                            String detailSlotKey) {
        return new SelectionState(
                equipmentKey,
                gemKey,
                supportKey,
                slotKey,
                statusText,
                state != null ? state.equipmentPage : 0,
                state != null ? state.gemPage : 0,
                state != null ? state.supportPage : 0,
                detailEquipmentKey,
                detailSlotKey);
    }

    private static SelectionState stateWithPages(SelectionState state, int equipmentPage, int gemPage, int supportPage) {
        return new SelectionState(
                state != null ? state.equipmentKey : null,
                state != null ? state.gemKey : null,
                state != null ? state.supportKey : null,
                state != null ? state.slotKey : null,
                state != null ? state.statusText : null,
                equipmentPage,
                gemPage,
                supportPage,
                state != null ? state.detailEquipmentKey : null,
                state != null ? state.detailSlotKey : null);
    }

    private static String buildEquipmentCardsHtml(Player player, Snapshot snapshot, SelectionState state) {
        List<Entry> entries = snapshot != null ? snapshot.equipments : List.of();
        StringBuilder sb = new StringBuilder();
        if (entries.isEmpty()) {
            return "<p style=\"font-size:12; color:#b0b0c2;\">" + escapeHtml(LangLoader.getUITranslation(player, "ui.lore_socket.option_no_equipment")) + "</p>";
        }
        int pageCount = cardPageCount(entries.size(), EQUIPMENT_CARDS_PER_PAGE);
        int page = clampPage(state != null ? state.equipmentPage : 0, pageCount);
        sb.append(buildPagerHtml(player, "equipmentCardsPrev", "equipmentCardsNext", page, pageCount, 420));
        int start = page * EQUIPMENT_CARDS_PER_PAGE;
        int end = Math.min(entries.size(), start + EQUIPMENT_CARDS_PER_PAGE);
        for (int i = start; i < end; i++) {
            appendEquipmentCardHtml(player, sb, entries.get(i), i, String.valueOf(i).equals(state != null ? state.equipmentKey : null));
        }
        return sb.toString();
    }

    private static void appendEquipmentCardHtml(Player player, StringBuilder sb, Entry entry, int index, boolean selected) {
        if (entry == null) {
            return;
        }
        LoreSocketData loreData = LoreSocketManager.getLoreSocketData(entry.item);
        SocketData socketData = SocketManager.getSocketData(entry.item);
        String background = selected ? "#253456" : "#151526";
        sb.append("<button id=\"").append(equipmentCardButtonId(index)).append("\" class=\"raw-button\" style=\"anchor-width:420; anchor-height:88; layout-mode:top; padding:0; border:0; background-color:#00000000;\">")
                .append("<div style=\"anchor-width:420; anchor-height:84; layout-mode:left; spacing:0; padding:6; background-color:").append(background).append(";\">")
                .append("<div style=\"anchor-width:66; anchor-height:66; background-image:url('slot_bg.png'); background-size:100% 100%; padding:3;\">")
                .append("<span class=\"item-icon\" data-hyui-item-id=\"").append(escapeHtml(entry.itemId)).append("\" style=\"anchor-width:60; anchor-height:60;\"></span>")
                .append("</div>")
                .append("<div style=\"anchor-width:12;\"></div>")
                .append("<div style=\"anchor-width:330; anchor-height:70; layout-mode:top; spacing:3;\">")
                .append("<p style=\"anchor-height:20; font-weight:bold; text-align:left; white-space:nowrap;\">")
                .append(escapeHtml(entry.displayName))
                .append("</p>")
                .append("<div style=\"anchor-width:330; anchor-height:34; layout-mode:left; spacing:0;\">")
                .append("<div style=\"anchor-width:150; anchor-height:32; layout-mode:leftcenterwrap; spacing:5;\">");
        appendSmallEssenceSocketsHtml(sb, socketData);
        sb.append("</div>")
                .append("<div style=\"anchor-width:18;\"></div>")
                .append("<div style=\"anchor-width:150; anchor-height:32; layout-mode:leftcenterwrap; spacing:5;\">");
        if (loreData != null) {
            for (int i = 0; i < loreData.getSocketCount(); i++) {
                appendSmallLoreSocketHtml(sb, loreData.getSocket(i));
            }
        }
        sb.append("</div></div></div></div></button><p style=\"anchor-height:3;\"></p>");
    }

    private static void appendSmallEssenceSocketsHtml(StringBuilder sb, SocketData data) {
        if (data == null || data.getSockets() == null || data.getSockets().isEmpty()) {
            for (int i = 0; i < 4; i++) {
                sb.append("<div style=\"anchor-width:28; anchor-height:28; padding:2; background-color:#00000000;\">")
                        .append("<img src=\"slot_bg.png\" width=\"24\" height=\"24\"/>")
                        .append("</div>");
            }
            return;
        }
        for (Socket socket : data.getSockets()) {
            appendSmallEssenceSocketHtml(sb, socket);
        }
    }

    private static void appendSmallEssenceSocketHtml(StringBuilder sb, Socket socket) {
        boolean broken = socket != null && socket.isBroken();
        boolean filled = socket != null && !socket.isEmpty() && !broken;
        String baseIcon = broken ? resolveSmallBrokenSocketIconName() : (filled ? resolveSmallEmptySocketIconName() : resolveSmallEmptySocketIconName());
        String overlayIcon = filled ? resolveSmallEssenceIconName(socket) : null;
        String border = broken ? "#8A2020" : (filled ? getSmallEssenceColorHex(socket) : "#362f1e");
        sb.append("<div style=\"anchor-width:28; anchor-height:28; padding:2; background-color:").append(border).append(";\">")
                .append("<div style=\"anchor-width:24; anchor-height:24; layout-mode:center; background-image:url('")
                .append(baseIcon)
                .append("'); background-size:100% 100%;\">");
        if (overlayIcon != null) {
            sb.append("<img src=\"").append(overlayIcon).append("\" width=\"22\" height=\"22\"/>");
        }
        sb.append("</div></div>");
    }

    private static String resolveSmallEmptySocketIconName() {
        return UITemplateUtils.resolveCustomUiAsset("socket_empty.png", "socket_Empty.png");
    }

    private static String resolveSmallFilledSocketBaseIconName() {
        return UISocketVisualUtils.filledSocketIconName();
    }

    private static String resolveSmallBrokenSocketIconName() {
        return UISocketVisualUtils.brokenSocketIconName();
    }

    private static String getSmallEssenceColorHex(Socket socket) {
        if (socket == null || socket.isEmpty()) {
            return "#362f1e";
        }
        return UISocketVisualUtils.socketColorHex(socket);
    }

    private static String resolveSmallEssenceIconName(Socket socket) {
        return UISocketVisualUtils.essenceIconName(socket);
    }

    private static void appendSmallLoreSocketHtml(StringBuilder sb, LoreSocketData.LoreSocket socket) {
        String color = resolveLoreColorHex(socket);
        sb.append("<div style=\"anchor-width:28; anchor-height:28; padding:2; background-color:").append(color).append(";\">")
                .append("<div style=\"anchor-width:24; anchor-height:24; background-image:url('GemSlotEmpty.png'); background-size:100% 100%;\">");
        appendLoreSocketOverlayHtml(sb, socket, 22);
        sb.append("</div></div>");
    }

    private static void appendLoreSocketOverlayHtml(StringBuilder sb, LoreSocketData.LoreSocket socket, int size) {
        if (socket == null || socket.isEmpty()) {
            return;
        }
        if (socket.hasRune()) {
            sb.append("<span class=\"item-icon\" data-hyui-item-id=\"")
                    .append(escapeHtml(socket.getRuneItemId()))
                    .append("\" style=\"anchor-width:").append(size)
                    .append("; anchor-height:").append(size).append(";\"></span>");
            return;
        }
        String icon = resolveLoreGemOverlayIcon(socket);
        if (icon != null) {
            sb.append("<img src=\"").append(icon).append("\" width=\"").append(size)
                    .append("\" height=\"").append(size).append("\"/>");
        }
    }

    private static String buildEquipmentSocketSummary(Player player, LoreSocketData data, boolean runeDelta) {
        if (data == null || data.getSocketCount() == 0) {
            return LangLoader.getUITranslation(player, "ui.lore_socket.metadata_no_sockets");
        }
        int filled = 0;
        int spirits = 0;
        int runes = 0;
        int feedReady = 0;
        for (int i = 0; i < data.getSocketCount(); i++) {
            LoreSocketData.LoreSocket socket = data.getSocket(i);
            if (socket == null) {
                continue;
            }
            if (!socket.isEmpty()) {
                filled++;
            }
            if (socket.hasSpirit()) {
                spirits++;
            }
            if (socket.hasRune()) {
                runes++;
            }
            if (socket.hasSpirit() && LoreSocketManager.needsFeed(socket)) {
                feedReady++;
            } else if (LoreSocketManager.needsRuneFeed(socket)) {
                feedReady++;
            }
        }
        return runeDelta
                ? LangLoader.getUITranslation(player, "ui.lore_socket.card_summary_runes",
                        data.getSocketCount(), filled, runes, feedReady)
                : LangLoader.getUITranslation(player, "ui.lore_socket.card_summary",
                        data.getSocketCount(), filled, spirits, feedReady);
    }

    private static String buildMaterialsCardsHtml(Player player, Snapshot snapshot, SelectionState state, Entry selectedEquipment) {
        StringBuilder sb = new StringBuilder();
        boolean runeDelta = isRuneDeltaEquipment(selectedEquipment);
        appendMaterialSectionHtml(player, sb,
                runeDelta ? "ui.lore_socket.materials_runes" : "ui.lore_socket.materials_gems",
                materialEntries(snapshot, selectedEquipment),
                state != null ? state.gemKey : null, state != null ? state.gemPage : 0, true,
                runeDelta ? "ui.lore_socket.option_no_rune" : "ui.lore_socket.option_no_gem");
        appendMaterialSectionHtml(player, sb, "ui.lore_socket.materials_supports", snapshot != null ? snapshot.supports : List.of(),
                state != null ? state.supportKey : null, state != null ? state.supportPage : 0, false,
                "ui.lore_socket.option_no_support");
        return sb.toString();
    }

    private static boolean isRuneDeltaEquipment(Entry equipment) {
        return equipment != null
                && equipment.item != null
                && LoreSocketManager.isRuneDeltaActive(equipment.item);
    }

    private static List<Entry> materialEntries(Snapshot snapshot, Entry equipment) {
        if (snapshot == null) {
            return List.of();
        }
        return isRuneDeltaEquipment(equipment) ? snapshot.runes : snapshot.gems;
    }

    private static void appendMaterialSectionHtml(Player player,
                                                  StringBuilder sb,
                                                  String titleKey,
                                                  List<Entry> entries,
                                                  String selectedKey,
                                                  int page,
                                                  boolean gemSection,
                                                  String emptyKey) {
        sb.append("<div style=\"anchor-width:330; layout-mode:top; spacing:4;\">")
                .append("<p style=\"anchor-height:18; text-align:left; font-weight:bold;\">")
                .append(escapeHtml(LangLoader.getUITranslation(player, titleKey))).append("</p>")
                .append("<img src=\"divider.png\" style=\"anchor-width: 320; anchor-height: 3;\">");
        int totalCards = gemSection ? (entries == null ? 0 : entries.size()) : (entries == null ? 0 : entries.size()) + 1;
        if (gemSection && totalCards == 0) {
            sb.append("<p style=\"font-size:11; color:#b0b0c2; text-align:left;\">")
                    .append(escapeHtml(LangLoader.getUITranslation(player, emptyKey))).append("</p>");
        } else {
            int pageCount = cardPageCount(totalCards, MATERIAL_CARDS_PER_PAGE);
            int currentPage = clampPage(page, pageCount);
            sb.append(buildPagerHtml(player, gemSection ? "gemCardsPrev" : "supportCardsPrev",
                    gemSection ? "gemCardsNext" : "supportCardsNext", currentPage, pageCount, 320));
            int start = currentPage * MATERIAL_CARDS_PER_PAGE;
            int end = Math.min(totalCards, start + MATERIAL_CARDS_PER_PAGE);
            for (int visibleIndex = start; visibleIndex < end; visibleIndex++) {
                if (!gemSection && visibleIndex == 0) {
                    appendSupportNoneCardHtml(player, sb, selectedKey == null || selectedKey.isBlank());
                    continue;
                }
                int entryIndex = gemSection ? visibleIndex : visibleIndex - 1;
                appendMaterialCardHtml(player, sb, entries.get(entryIndex), entryIndex,
                        String.valueOf(entryIndex).equals(selectedKey), gemSection);
            }
        }
        sb.append("</div>");
    }

    private static void appendSupportNoneCardHtml(Player player, StringBuilder sb, boolean selected) {
        String background = selected ? "#253456" : "#151526";
        String border = selected ? "#FFD24D" : "#00000000";
        sb.append("<button id=\"").append(supportNoneCardButtonId()).append("\" class=\"raw-button\" style=\"anchor-width:320; anchor-height:58; layout-mode:top; padding:0; border:0; background-color:#00000000;\">")
                .append("<div style=\"anchor-width:320; anchor-height:58; layout-mode:left; spacing:8; padding:5; background-color:").append(background).append(";\">")
                .append("<div style=\"anchor-width:48; anchor-height:48; padding:2; background-color:").append(border).append(";\">")
                .append("<img src=\"slot_bg.png\" width=\"44\" height=\"44\"/>")
                .append("</div>")
                .append("<div style=\"anchor-width:250; anchor-height:48; layout-mode:top;\">")
                .append("<p style=\"anchor-height:22; font-weight:bold; text-align:left;\">")
                .append(escapeHtml(LangLoader.getUITranslation(player, "ui.lore_socket.option_none")))
                .append("</p>")
                .append("<p style=\"anchor-height:18; font-size:10; color:#b0b0c2; text-align:left;\">")
                .append(escapeHtml(LangLoader.getUITranslation(player, "ui.lore_socket.support")))
                .append("</p>")
                .append("</div></div></button><p style=\"anchor-height:3;\"></p>");
    }

    private static void appendMaterialCardHtml(Player player, StringBuilder sb, Entry entry, int index, boolean selected, boolean gemSection) {
        if (entry == null) {
            return;
        }
        String buttonId = materialCardButtonId(gemSection, index);
        String background = selected ? "#253456" : "#151526";
        String border = selected ? "#FFD24D" : "#00000000";
        sb.append("<button id=\"").append(buttonId).append("\" class=\"raw-button\" style=\"anchor-width:320; anchor-height:58; layout-mode:top; padding:0; border:0; background-color:#00000000;\">")
                .append("<div style=\"anchor-width:320; anchor-height:58; layout-mode:left; spacing:8; padding:5; background-color:").append(background).append(";\">")
                .append("<div style=\"anchor-width:48; anchor-height:48; padding:2; background-color:").append(border).append(";\">")
                .append("<span class=\"item-icon\" data-hyui-item-id=\"").append(escapeHtml(entry.itemId)).append("\" style=\"anchor-width:44; anchor-height:44;\"></span>")
                .append("</div>")
                .append("<div style=\"anchor-width:250; anchor-height:48; layout-mode:top;\">")
                .append("<p style=\"anchor-height:18; font-weight:bold; text-align:left; white-space:nowrap;\">").append(escapeHtml(entry.displayName)).append("</p>")
                .append("<p style=\"anchor-height:14; font-size:10; color:#b0b0c2; text-align:left; white-space:nowrap;\">")
                .append(escapeHtml(buildMaterialDescription(player, entry, gemSection))).append("</p>")
                .append("<p style=\"anchor-height:14; font-size:10; color:#d6d6e8; text-align:left;\">x").append(entry.quantity).append("</p>")
                .append("</div></div></button><p style=\"anchor-height:3;\"></p>");
    }

    private static String buildOptions(List<Entry> entries, String emptyLabel, String selectedKey) {
        if (entries.isEmpty()) {
            return "<option value=\"\" selected=\"true\">" + escapeHtml(emptyLabel) + "</option>";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            String key = String.valueOf(i);
            sb.append("<option value=\"").append(key).append("\"");
            if (key.equals(selectedKey)) {
                sb.append(" selected=\"true\"");
            }
            sb.append(">").append(escapeHtml(entry.displayName)).append(" x").append(entry.quantity).append("</option>");
        }
        return sb.toString();
    }

    private static String buildGemOptions(Player player, List<Entry> entries, String selectedKey) {
        StringBuilder sb = new StringBuilder();
        boolean hasSelection = selectedKey != null && !selectedKey.isEmpty();
        sb.append("<option value=\"\"").append(!hasSelection ? " selected=\"true\"" : "")
                .append(">").append(escapeHtml(LangLoader.getUITranslation(player, "ui.lore_socket.option_none"))).append("</option>");

        if (entries.isEmpty()) {
            sb.append("<option value=\"\"").append(!hasSelection ? " selected=\"true\"" : "")
                    .append(">").append(escapeHtml(LangLoader.getUITranslation(player, "ui.lore_socket.option_no_gem"))).append("</option>");
            return sb.toString();
        }

        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            String key = String.valueOf(i);
            String color = LoreGemRegistry.resolveColor(entry.itemId);
            String label = entry.displayName;
            if (color != null && !color.isBlank()) {
                label = LangLoader.getUITranslation(player, "ui.lore_socket.gem_color_label", entry.displayName, color);
            }
            sb.append("<option value=\"").append(key).append("\"");
            if (key.equals(selectedKey)) {
                sb.append(" selected=\"true\"");
            }
            sb.append(">").append(escapeHtml(label)).append(" x").append(entry.quantity).append("</option>");
        }
        return sb.toString();
    }

    private static String buildSupportOptions(Player player, List<Entry> entries, String selectedKey) {
        StringBuilder sb = new StringBuilder();
        boolean hasSelection = selectedKey != null && !selectedKey.isEmpty();
        sb.append("<option value=\"\"").append(!hasSelection ? " selected=\"true\"" : "")
                .append(">").append(escapeHtml(LangLoader.getUITranslation(player, "ui.lore_socket.option_none"))).append("</option>");

        if (entries.isEmpty()) {
            sb.append("<option value=\"\"").append(!hasSelection ? " selected=\"true\"" : "")
                    .append(">").append(escapeHtml(LangLoader.getUITranslation(player, "ui.lore_socket.option_no_support"))).append("</option>");
            return sb.toString();
        }

        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            String key = String.valueOf(i);
            String label = entry.displayName + " x" + entry.quantity;
            sb.append("<option value=\"").append(key).append("\"");
            if (key.equals(selectedKey)) {
                sb.append(" selected=\"true\"");
            }
            sb.append(">").append(escapeHtml(label)).append("</option>");
        }
        return sb.toString();
    }

    private static String buildSlotOptions(Player player, Entry equipment, String selectedKey, String gemColor) {
        StringBuilder sb = new StringBuilder();
        sb.append("<option value=\"auto\"");
        if (selectedKey == null || selectedKey.isBlank() || "auto".equalsIgnoreCase(selectedKey)) {
            sb.append(" selected=\"true\"");
        }
        sb.append(">").append(escapeHtml(LangLoader.getUITranslation(player, "ui.lore_socket.option_auto"))).append("</option>");

        if (equipment == null) {
            sb.append("<option value=\"\" selected=\"true\">")
                    .append(escapeHtml(LangLoader.getUITranslation(player, "ui.lore_socket.option_no_equipment")))
                    .append("</option>");
            return sb.toString();
        }

        ItemStack equipmentItem = getEffectiveEquipmentItem(player, equipment);
        LoreSocketData data = LoreSocketManager.getLoreSocketData(equipmentItem);
        if (data == null || data.getSocketCount() == 0) {
            sb.append("<option value=\"\" selected=\"true\">")
                    .append(escapeHtml(LangLoader.getUITranslation(player, "ui.lore_socket.option_no_sockets")))
                    .append("</option>");
            return sb.toString();
        }

        for (int i = 0; i < data.getSocketCount(); i++) {
            LoreSocketData.LoreSocket socket = data.getSocket(i);
            String key = String.valueOf(i);
            String label = buildSlotLabel(player, socket, i + 1, gemColor, isRuneDeltaEquipment(equipment));
            sb.append("<option value=\"").append(key).append("\"");
            if (key.equals(selectedKey)) {
                sb.append(" selected=\"true\"");
            }
            sb.append(">").append(escapeHtml(label)).append("</option>");
        }
        return sb.toString();
    }

    private static String buildSlotLabel(Player player,
                                         LoreSocketData.LoreSocket socket,
                                         int index,
                                         String gemColor,
                                         boolean runeDelta) {
        if (socket == null) {
            return LangLoader.getUITranslation(player, "ui.lore_socket.slot_unavailable", index);
        }
        String color = socket.getColor();
        String colorLabel = displaySocketColor(player, socket, runeDelta);
        String labelKey;
        if (socket.isLocked()) {
            labelKey = "ui.lore_socket.slot_locked";
        } else if (socket.isEmpty()) {
            labelKey = runeDelta ? "ui.lore_socket.slot_empty_rune" : "ui.lore_socket.slot_empty";
        } else {
            labelKey = "ui.lore_socket.slot_filled";
        }
        String base = LangLoader.getUITranslation(player, labelKey, index);
        if (gemColor != null && !gemColor.isBlank() && color != null && !color.isBlank()
                && !gemColor.equalsIgnoreCase(color)) {
            base += " - " + LangLoader.getUITranslation(player, "ui.lore_socket.color_mismatch");
        }
        return base + " (" + colorLabel + ")";
    }

    private static String buildSocketPreview(Player player, Entry equipment, String selectedSlotKey) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"layout-mode:left; spacing:14;\"><div style=\"flex-weight:1;\"></div>");
        if (equipment == null) {
            sb.append("<p>").append(escapeHtml(LangLoader.getUITranslation(player, "ui.lore_socket.option_no_equipment")))
                    .append("</p>");
            sb.append("<div style=\"flex-weight:1;\"></div></div>");
            return sb.toString();
        }
        ItemStack equipmentItem = getEffectiveEquipmentItem(player, equipment);
        LoreSocketData data = LoreSocketManager.getLoreSocketData(equipmentItem);
        if (data == null || data.getSocketCount() == 0) {
            sb.append("<p>").append(escapeHtml(LangLoader.getUITranslation(player, "ui.lore_socket.option_no_sockets")))
                    .append("</p>");
            sb.append("<div style=\"flex-weight:1;\"></div></div>");
            return sb.toString();
        }

        String baseIcon = UITemplateUtils.resolveCustomUiAsset("GemSlotEmpty.png", "GemSlotEmpty.png");
        int tileSize = 82;
        int wrapSize = 104;
        int overlaySize = 54;

        for (int i = 0; i < data.getSocketCount(); i++) {
            LoreSocketData.LoreSocket socket = data.getSocket(i);
            String colorHex = resolveLoreColorHex(socket);
            boolean selected = String.valueOf(i).equals(selectedSlotKey);
            boolean feedReady = socket != null && socket.hasSpirit() && LoreSocketManager.needsFeed(socket);
            String border = selected ? "#FFD24D" : colorHex;
            int xpPercent = resolveXpPercent(socket);

            String wrapStyle = "anchor-width:" + wrapSize + "; anchor-height:" + (feedReady ? 142 : 124) + ";"
                    + " layout-mode:top; padding:4;";
            String tileStyle = "anchor-width:" + tileSize + "; anchor-height:" + tileSize + ";"
                    + " background-image:url('" + baseIcon + "'); background-size:100% 100%;"
                    + " background-repeat:no-repeat; layout-mode:Top;";

            sb.append("<div style=\"").append(wrapStyle).append("\">");
            if (socket != null && socket.hasSpirit()) {
                sb.append("<div style=\"anchor-width:96; anchor-height:8; background-color:#0b0705; border-color:#5b3a0c;\">")
                        .append("<div style=\"anchor-width:").append(Math.max(2, xpPercent)).append("; anchor-height:8; background-color:#d39a28;\"></div>")
                        .append("</div>");
            } else {
                sb.append("<p style=\"anchor-height:8;\"></p>");
            }
            sb.append("<button id=\"").append(loreSlotButtonId(i)).append("\" class=\"raw-button\" style=\"anchor-width:96; anchor-height:92; layout-mode:top; padding:0; border:0; background-color:#00000000;\">")
                    .append("<div style=\"anchor-width:92; anchor-height:92; background-color:").append(border).append("; layout-mode:top; padding:5;\">")
                    .append("<div style=\"").append(tileStyle).append("\">")
                    .append("<div style=\"flex-weight:1;\"></div>")
                    .append("<div style=\"layout-mode:Left;\">")
                    .append("<div style=\"flex-weight:1;\"></div>");
            appendLoreSocketOverlayHtml(sb, socket, overlaySize);
            sb.append("<div style=\"flex-weight:1;\"></div>")
                    .append("</div>")
                    .append("<div style=\"flex-weight:1;\"></div>")
                    .append("</div>")
                    .append("</div>")
                    .append("</button>");
            if (feedReady) {
                sb.append("<button id=\"").append(feedButtonId(i)).append("\" class=\"small-secondary-button\" style=\"anchor-width:96; anchor-height:24;\">")
                        .append(escapeHtml(LangLoader.getUITranslation(player, "ui.lore_socket.feed_button")))
                        .append("</button>");
            }
            sb.append("</div>");
        }
        sb.append("<div style=\"flex-weight:1;\"></div></div>");
        return sb.toString();
    }

    private static String buildSelectedSlotLabel(Player player, Entry equipment, String slotKey) {
        LoreSocketData.LoreSocket socket = resolveSelectedSocket(equipment, slotKey);
        if (socket == null) {
            return LangLoader.getUITranslation(player, "ui.lore_socket.slot_none_selected");
        }
        return buildSlotLabel(player, socket, socket.getSlotIndex() + 1, null,
                isRuneDeltaEquipment(equipment));
    }

    private static String buildSelectedSlotSummaryHtml(Player player, Entry equipment, String slotKey) {
        LoreSocketData.LoreSocket socket = resolveSelectedSocket(equipment, slotKey);
        if (socket == null) {
            return detailText(LangLoader.getUITranslation(player, "ui.lore_socket.detail_click_slot"));
        }
        return buildSlotDetailHtml(player, socket, isRuneDeltaEquipment(equipment));
    }

    private static String buildSlotDetailHtml(Player player, LoreSocketData.LoreSocket socket, boolean runeDelta) {
        if (socket == null) {
            return detailText(LangLoader.getUITranslation(player, "ui.lore_socket.status_no_socket"));
        }
        String color = displaySocketColor(player, socket, runeDelta);
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"anchor-width:860; layout-mode:top; spacing:5;\">")
                .append(detailSection(LangLoader.getUITranslation(player, "ui.lore_socket.detail_socket_section")))
                .append(detailRow(LangLoader.getUITranslation(player, "ui.lore_socket.detail_slot_label"),
                        String.valueOf(socket.getSlotIndex() + 1)))
                .append(detailRow(LangLoader.getUITranslation(player, "ui.lore_socket.detail_color_label"), color));

        if (socket.hasRune()) {
            sb.append(detailSection(LangLoader.getUITranslation(player, "ui.lore_socket.materials_runes")))
                    .append(detailText(formatRunestoneNameWithLevel(player, socket)));
            if (socket.getRuneDamageFeeds() > 0) {
                sb.append(detailText(LangLoader.getUITranslation(player, "ui.lore_socket.rune_damage_feed_bonus",
                        LoreSocketManager.getRuneDamageBonusPercent(socket.getRuneDamageFeeds()))));
            }
            if (socket.getRuneManaFeeds() > 0) {
                sb.append(detailText(LangLoader.getUITranslation(player, "ui.lore_socket.rune_mana_feed_bonus",
                        LoreSocketManager.getRuneManaCostReductionPercent(socket.getRuneManaFeeds()))));
            }
        } else if (socket.hasSpirit()) {
            String spiritName = localizeSpiritName(player, socket.getSpiritId());
            int needed = Math.max(1, LoreSocketManager.getXpRequiredForLevel(Math.max(1, socket.getLevel())));
            sb.append(detailSection(LangLoader.getUITranslation(player, "ui.lore_socket.detail_spirit_section")))
                    .append(detailRow(LangLoader.getUITranslation(player, "ui.lore_socket.detail_spirit_label"), spiritName))
                    .append(detailRow(LangLoader.getUITranslation(player, "ui.lore_socket.detail_level_label"),
                            LangLoader.getUITranslation(player, "ui.lore_feed.level_label", Math.max(1, socket.getLevel()))
                                    + " | " + LangLoader.getUITranslation(player, "ui.lore_feed.feed_tier", Math.max(0, socket.getFeedTier()))))
                    .append(detailRow(LangLoader.getUITranslation(player, "ui.lore_socket.detail_xp_label"),
                            LangLoader.getUITranslation(player, "ui.lore_feed.progress_xp", Math.max(0, socket.getXp()), needed)))
                    .append(detailRow(LangLoader.getUITranslation(player, "ui.lore_socket.detail_feed_label"),
                            LoreSocketManager.needsFeed(socket)
                                    ? LangLoader.getUITranslation(player, "ui.lore_feed.value_yes")
                                    : LangLoader.getUITranslation(player, "ui.lore_feed.value_no")));

            LoreAbility ability = LoreAbilityRegistry.getAbility(socket.getSpiritId());
            if (ability != null) {
                String langCode = resolveLangCode(player);
                sb.append(detailSection(LangLoader.getUITranslation(player, "ui.lore_socket.detail_ability_section")))
                        .append(detailRow(LangLoader.getUITranslation(player, "ui.lore_socket.detail_ability_label"),
                                ability.resolveAbilityName(langCode)));
                String effect = ability.describeEffectOnly(langCode, Math.max(1, socket.getLevel()), Math.max(0, socket.getFeedTier()));
                if (effect != null && !effect.isBlank()) {
                    sb.append(detailRow(LangLoader.getUITranslation(player, "ui.lore_socket.detail_effect_label"), effect));
                }
                if (LoreSocketManager.isOmnislashSpirit(socket.getSpiritId())) {
                    String runeItemId = LoreSocketManager.resolveOmnislashRuneItemId(
                            socket.getLevel(), socket.getFeedTier());
                    String runeName = LangLoader.getUITranslation(player, "irai.items." + runeItemId + ".name");
                    sb.append(detailRow(LangLoader.getUITranslation(player, "ui.lore_socket.omnislash_rune_stage"),
                            runeName));
                }
                if (LoreSocketManager.isWolfPackSpirit(socket.getSpiritId())) {
                    String runeItemId = LoreSocketManager.resolveWolfPackRuneItemId(socket.getFeedTier());
                    String runeName = LangLoader.getUITranslation(player, "irai.items." + runeItemId + ".name");
                    sb.append(detailRow(LangLoader.getUITranslation(player, "ui.lore_socket.wolfpack_rune_stage"),
                            runeName));
                }
                if (LoreSoulstoneUtils.isInfusedSoulstoneId(socket.getGemItemId())) {
                    sb.append(detailText(LangLoader.getUITranslation(
                            player, "ui.lore_socket.infused_soulstone_hit_roll")));
                }
            }
        } else if (runeDelta) {
            sb.append(detailSection(LangLoader.getUITranslation(player, "ui.lore_socket.materials_runes")))
                    .append(detailText(LangLoader.getUITranslation(player, "ui.lore_socket.detail_empty_rune")))
                    .append(detailText(LangLoader.getUITranslation(player, "ui.lore_socket.detail_eligible_runes")));
        } else {
            sb.append(detailSection(LangLoader.getUITranslation(player, "ui.lore_socket.detail_empty_section")))
                    .append(detailText(LangLoader.getUITranslation(player, "ui.lore_socket.detail_empty")))
                    .append(detailSection(LangLoader.getUITranslation(player, "ui.lore_socket.detail_eligible")));
            List<String> names = buildCompatibleSpiritNames(player, color);
            sb.append(buildEligibleSpiritGrid(player, names));
        }

        sb.append("</div>");
        return sb.toString();
    }

    private static String detailSection(String title) {
        return "<p style=\"anchor-height:20; font-weight:bold; color:#FFD24D; text-align:left;\">" + escapeHtml(title) + "</p>";
    }

    private static String detailRow(String label, String value) {
        return "<div style=\"anchor-width:850; layout-mode:left; spacing:8;\">"
                + "<p style=\"anchor-width:150; font-size:13; color:#b0b0c2; text-align:left; white-space:wrap;\">"
                + escapeHtml(label) + "</p>"
                + "<p style=\"anchor-width:680; font-size:13; text-align:left; white-space:wrap; word-break:break-word;\">"
                + escapeHtml(value) + "</p>"
                + "</div>";
    }

    private static String detailText(String text) {
        return "<p style=\"anchor-width:840; font-size:13; text-align:left; white-space:wrap; word-break:break-word;\">"
                + escapeHtml(text) + "</p>";
    }

    private static String buildEligibleSpiritGrid(Player player, List<String> names) {
        if (names == null || names.isEmpty()) {
            return detailText(LangLoader.getUITranslation(player, "ui.lore_feed.value_none"));
        }
        StringBuilder sb = new StringBuilder("<div style=\"anchor-width:850; layout-mode:top; spacing:3;\">");
        for (int i = 0; i < names.size(); i += 3) {
            sb.append("<div style=\"anchor-width:850; layout-mode:left; spacing:8;\">");
            for (int col = 0; col < 3; col++) {
                int index = i + col;
                String name = index < names.size() ? names.get(index) : "";
                sb.append("<p style=\"anchor-width:270; font-size:12; text-align:left; white-space:wrap; word-break:break-word;\">")
                        .append(escapeHtml(name))
                        .append("</p>");
            }
            sb.append("</div>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static LoreSocketData.LoreSocket resolveSelectedSocket(Entry equipment, String slotKey) {
        if (equipment == null || equipment.item == null || slotKey == null || slotKey.isBlank()) {
            return null;
        }
        LoreSocketData data = LoreSocketManager.getLoreSocketData(equipment.item);
        int slotIndex = resolveSlotIndex(data, slotKey, null);
        return data != null && slotIndex >= 0 && slotIndex < data.getSocketCount() ? data.getSocket(slotIndex) : null;
    }

    private static int resolveXpPercent(LoreSocketData.LoreSocket socket) {
        if (socket == null || !socket.hasSpirit()) {
            return 0;
        }
        int needed = Math.max(1, LoreSocketManager.getXpRequiredForLevel(Math.max(1, socket.getLevel())));
        int xp = Math.max(0, Math.min(needed, socket.getXp()));
        return Math.max(2, Math.min(96, (int) Math.round((xp * 96.0d) / needed)));
    }

    private static String buildMaterialDescription(Player player, Entry entry, boolean gemSection) {
        if (entry == null) {
            return "";
        }
        if (gemSection) {
            if (LoreSocketManager.isModifierRunestone(entry.item)) {
                return LangLoader.getUITranslation(player, "ui.lore_socket.material_modifier_rune_desc");
            }
            if (LoreSocketManager.isAbilityRunestone(entry.item)) {
                return LangLoader.getUITranslation(player, "ui.lore_socket.material_rune_desc");
            }
            String color = LoreGemRegistry.resolveColor(entry.itemId);
            return color == null || color.isBlank()
                    ? LangLoader.getUITranslation(player, "ui.lore_socket.material_gem_desc")
                    : LangLoader.getUITranslation(player, "ui.lore_socket.material_gem_color_desc", color);
        }
        if (entry.itemId != null && entry.itemId.equalsIgnoreCase(LoreSocketManager.RUNE_DAMAGE_FEED_ITEM_ID)) {
            return LangLoader.getUITranslation(player, "ui.lore_socket.material_rune_damage_feed_desc");
        }
        if (entry.itemId != null && entry.itemId.equalsIgnoreCase(LoreSocketManager.RUNE_MANA_FEED_ITEM_ID)) {
            return LangLoader.getUITranslation(player, "ui.lore_socket.material_rune_mana_feed_desc");
        }
        MaterialKind kind = materialKind(entry);
        return switch (kind) {
            case FEED -> LangLoader.getUITranslation(player, "ui.lore_socket.material_feed_desc");
            case CLEAR -> LangLoader.getUITranslation(player, "ui.lore_socket.material_clear_desc");
            case REROLL -> LangLoader.getUITranslation(player, "ui.lore_socket.material_reroll_desc");
            default -> LangLoader.getUITranslation(player, "ui.lore_socket.material_support_desc");
        };
    }

    private static String resolveProcessButtonLabel(Player player, Entry equipment, Entry gem, Entry support, String slotKey) {
        if (support != null) {
            if (isRuneDeltaEquipment(equipment) && support.itemId != null
                    && support.itemId.equalsIgnoreCase(LoreSocketManager.RUNE_DAMAGE_FEED_ITEM_ID)) {
                return LangLoader.getUITranslation(player, "ui.lore_socket.process_rune_damage_feed_button");
            }
            if (isRuneDeltaEquipment(equipment) && support.itemId != null
                    && support.itemId.equalsIgnoreCase(LoreSocketManager.RUNE_MANA_FEED_ITEM_ID)) {
                return LangLoader.getUITranslation(player, "ui.lore_socket.process_rune_mana_feed_button");
            }
            if (materialKind(support) == MaterialKind.CLEAR) {
                LoreSocketData.LoreSocket socket = resolveSelectedSocket(equipment, slotKey);
                if (isRuneDeltaEquipment(equipment) && socket != null && socket.hasRune()
                        && LoreSocketManager.isAbilityRunestoneId(socket.getRuneItemId())) {
                    return LangLoader.getUITranslation(player,
                            "ui.lore_socket.process_rune_delta_extract_button");
                }
                if (socket != null && LoreSocketManager.isOctaslashSpirit(socket.getSpiritId())) {
                    String actionKey = LoreSocketManager.isMaxLevelOctaslashLore(socket)
                            ? "ui.lore_socket.process_octaslash_extract_button"
                            : "ui.lore_socket.process_octaslash_max_required_button";
                    return LangLoader.getUITranslation(player, actionKey);
                }
                if (socket != null && LoreSocketManager.isOmnislashSpirit(socket.getSpiritId())) {
                    String actionKey = LoreSocketManager.isMaxLevelOmnislashLore(socket)
                            ? "ui.lore_socket.process_omnislash_extract_button"
                            : "ui.lore_socket.process_omnislash_max_required_button";
                    return LangLoader.getUITranslation(player, actionKey);
                }
                if (socket != null && LoreSocketManager.isBloodRushSpirit(socket.getSpiritId())) {
                    String actionKey = LoreSocketManager.isMaxLevelBloodRushLore(socket)
                            ? "ui.lore_socket.process_bloodrush_extract_button"
                            : "ui.lore_socket.process_bloodrush_max_required_button";
                    return LangLoader.getUITranslation(player, actionKey);
                }
                if (socket != null && LoreSocketManager.isPummelSpirit(socket.getSpiritId())) {
                    String actionKey = LoreSocketManager.isMaxLevelPummelLore(socket)
                            ? "ui.lore_socket.process_pummel_extract_button"
                            : "ui.lore_socket.process_pummel_max_required_button";
                    return LangLoader.getUITranslation(player, actionKey);
                }
                if (socket != null && LoreSocketManager.isWolfPackSpirit(socket.getSpiritId())) {
                    String actionKey = LoreSocketManager.isMaxLevelWolfPackLore(socket)
                            ? "ui.lore_socket.process_wolfpack_extract_button"
                            : "ui.lore_socket.process_wolfpack_max_required_button";
                    if (socket.getLevel() >= LoreSocketManager.getConfig().getMaxLevel()
                            && socket.getFeedTier() < 1) {
                        actionKey = "ui.lore_socket.process_wolfpack_feed_required_button";
                    }
                    return LangLoader.getUITranslation(player, actionKey);
                }
                if (socket != null && LoreSocketManager.isCoreAbilityRunestoneSpirit(socket.getSpiritId())) {
                    String actionKey = LoreSocketManager.isExtractableCoreAbilityLore(socket)
                            ? "ui.lore_socket.process_core_rune_extract_button"
                            : "ui.lore_socket.process_core_rune_feed_required_button";
                    return LangLoader.getUITranslation(player, actionKey);
                }
                if (socket != null && LoreSocketManager.isModifierRunestoneSpirit(socket.getSpiritId())) {
                    String actionKey = LoreSocketManager.isMaxLevelModifierLore(socket)
                            ? "ui.lore_socket.process_modifier_rune_extract_button"
                            : "ui.lore_socket.process_modifier_rune_max_required_button";
                    return LangLoader.getUITranslation(player, actionKey);
                }
            }
            return switch (materialKind(support)) {
                case FEED -> LangLoader.getUITranslation(player, "ui.lore_socket.process_feed_button");
                case CLEAR -> LangLoader.getUITranslation(player, "ui.lore_socket.process_clear_button");
                case REROLL -> shouldResonantFeed(equipment, slotKey)
                        ? LangLoader.getUITranslation(player, "ui.lore_socket.process_feed_button")
                        : LangLoader.getUITranslation(player, "ui.lore_socket.process_reroll_button");
                default -> LangLoader.getUITranslation(player, "ui.lore_socket.process_support_button");
            };
        }
        if (gem != null) {
            return isRuneDeltaEquipment(equipment)
                    ? LangLoader.getUITranslation(player, "ui.lore_socket.process_rune_button")
                    : LangLoader.getUITranslation(player, "ui.lore_socket.process_button");
        }
        return LangLoader.getUITranslation(player, "ui.lore_socket.process_select_button");
    }

    private static boolean shouldResonantFeed(Entry equipment, String slotKey) {
        LoreSocketData.LoreSocket socket = resolveSelectedSocket(equipment, slotKey);
        return socket != null && !socket.isEmpty() && socket.hasSpirit();
    }

    private static MaterialKind materialKind(Entry entry) {
        if (entry == null || entry.itemId == null) {
            return MaterialKind.UNKNOWN;
        }
        if (entry.itemId.equalsIgnoreCase(SUPPORT_REROLL_ITEM_ID)) {
            return MaterialKind.REROLL;
        }
        if (entry.itemId.equalsIgnoreCase(SUPPORT_CLEAR_ITEM_ID)
                || matchesAnyId(entry.itemId, LoreSocketManager.getConfig().getClearItemIds())) {
            return MaterialKind.CLEAR;
        }
        if (isRuneFeedItem(entry.itemId)
                || matchesAnyId(entry.itemId, LoreSocketManager.getConfig().getFeedItemIds())) {
            return MaterialKind.FEED;
        }
        return MaterialKind.UNKNOWN;
    }

    private static String buildLocationText(Player player, Entry entry) {
        if (entry == null) {
            return "";
        }
        String location = entry.kind == ContainerKind.HOTBAR
                ? LangLoader.getUITranslation(player, "ui.lore_socket.metadata_hotbar")
                : LangLoader.getUITranslation(player, "ui.lore_socket.metadata_storage");
        return LangLoader.getUITranslation(player, "ui.lore_socket.metadata_inventory", location, entry.slot);
    }

    private static String buildPagerHtml(Player player, String prevId, String nextId, int page, int pageCount, int width) {
        if (pageCount <= 1) {
            return "";
        }
        return UIHtmlUtils.pager(
                prevId,
                LangLoader.getUITranslation(player, "ui.lore_socket.pager_prev"),
                LangLoader.getUITranslation(player, "ui.lore_socket.pager_page", page + 1, pageCount),
                nextId,
                LangLoader.getUITranslation(player, "ui.lore_socket.pager_next"),
                width,
                30,
                92,
                24,
                true);
    }

    private static int cardPageCount(int size, int perPage) {
        return Math.max(1, (int) Math.ceil(Math.max(0, size) / (double) Math.max(1, perPage)));
    }

    private static int clampPage(int page, int pageCount) {
        return Math.max(0, Math.min(Math.max(1, pageCount) - 1, page));
    }

    private static String equipmentCardButtonId(int index) {
        return "loreEquipmentCard" + index;
    }

    private static String materialCardButtonId(boolean gem, int index) {
        return (gem ? "loreGemCard" : "loreSupportCard") + index;
    }

    private static String supportNoneCardButtonId() {
        return "loreSupportNoneCard";
    }

    private static String loreSlotButtonId(int index) {
        return "loreSlotButton" + index;
    }

    private static String feedButtonId(int index) {
        return "loreFeedButton" + index;
    }

    private static void registerEquipmentCardListeners(Object pageBuilder,
                                                       Method addListener,
                                                       Object activating,
                                                       Player player,
                                                       Snapshot snapshot,
                                                       SelectionState state) throws Exception {
        List<Entry> entries = snapshot != null ? snapshot.equipments : List.of();
        int pageCount = cardPageCount(entries.size(), EQUIPMENT_CARDS_PER_PAGE);
        int page = clampPage(state != null ? state.equipmentPage : 0, pageCount);
        registerPager(pageBuilder, addListener, activating, player, state, "equipmentCardsPrev", "equipmentCardsNext", page, pageCount, 0);
        int start = page * EQUIPMENT_CARDS_PER_PAGE;
        int end = Math.min(entries.size(), start + EQUIPMENT_CARDS_PER_PAGE);
        for (int i = start; i < end; i++) {
            final String equipmentKey = String.valueOf(i);
            addListener.invoke(pageBuilder, equipmentCardButtonId(i), activating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        pendingSelections.put(player.getPlayerRef(),
                                stateWith(state, equipmentKey, null,
                                        state != null ? state.supportKey : null, "", null, null, null));
                        player.getWorld().execute(() -> openWithSync(player));
                    });
        }
    }

    private static void registerMaterialCardListeners(Object pageBuilder,
                                                      Method addListener,
                                                      Object activating,
                                                      Player player,
                                                      Snapshot snapshot,
                                                      SelectionState state) throws Exception {
        Entry equipment = snapshot != null && state != null
                ? resolveSelection(snapshot.equipments, state.equipmentKey)
                : null;
        List<Entry> gems = materialEntries(snapshot, equipment);
        List<Entry> supports = snapshot != null ? snapshot.supports : List.of();
        int gemPageCount = cardPageCount(gems.size(), MATERIAL_CARDS_PER_PAGE);
        int supportPageCount = cardPageCount(supports.size() + 1, MATERIAL_CARDS_PER_PAGE);
        int gemPage = clampPage(state != null ? state.gemPage : 0, gemPageCount);
        int supportPage = clampPage(state != null ? state.supportPage : 0, supportPageCount);
        registerPager(pageBuilder, addListener, activating, player, state, "gemCardsPrev", "gemCardsNext", gemPage, gemPageCount, 1);
        registerPager(pageBuilder, addListener, activating, player, state, "supportCardsPrev", "supportCardsNext", supportPage, supportPageCount, 2);
        registerVisibleMaterialCards(pageBuilder, addListener, activating, player, state, gems, gemPage, true);
        registerVisibleMaterialCards(pageBuilder, addListener, activating, player, state, supports, supportPage, false);
    }

    private static void registerVisibleMaterialCards(Object pageBuilder,
                                                     Method addListener,
                                                     Object activating,
                                                     Player player,
                                                     SelectionState state,
                                                     List<Entry> entries,
                                                     int page,
                                                     boolean gemSection) throws Exception {
        if (gemSection && (entries == null || entries.isEmpty())) {
            return;
        }
        int totalCards = gemSection ? entries.size() : (entries != null ? entries.size() : 0) + 1;
        int start = clampPage(page, cardPageCount(totalCards, MATERIAL_CARDS_PER_PAGE)) * MATERIAL_CARDS_PER_PAGE;
        int end = Math.min(totalCards, start + MATERIAL_CARDS_PER_PAGE);
        for (int visibleIndex = start; visibleIndex < end; visibleIndex++) {
            final boolean noneSupport = !gemSection && visibleIndex == 0;
            final int entryIndex = gemSection ? visibleIndex : visibleIndex - 1;
            final String key = noneSupport ? "" : String.valueOf(entryIndex);
            addListener.invoke(pageBuilder, noneSupport ? supportNoneCardButtonId() : materialCardButtonId(gemSection, entryIndex), activating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        String gemKey = gemSection ? key : null;
                        String supportKey = gemSection ? null : key;
                        pendingSelections.put(player.getPlayerRef(),
                                stateWith(state, state != null ? state.equipmentKey : null, gemKey, supportKey,
                                        state != null ? state.slotKey : null, null, null, null));
                        player.getWorld().execute(() -> openWithSync(player));
                    });
        }
    }

    private static void registerSocketPreviewListeners(Object pageBuilder,
                                                       Method addListener,
                                                       Object activating,
                                                       Player player,
                                                       Snapshot snapshot,
                                                       SelectionState state) throws Exception {
        if (snapshot == null || state == null) {
            return;
        }
        Entry equipment = resolveSelection(snapshot.equipments, state.equipmentKey);
        LoreSocketData data = equipment != null ? LoreSocketManager.getLoreSocketData(equipment.item) : null;
        if (data == null || data.getSocketCount() == 0) {
            return;
        }
        for (int i = 0; i < data.getSocketCount(); i++) {
            final String slotKey = String.valueOf(i);
            addListener.invoke(pageBuilder, loreSlotButtonId(i), activating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        pendingSelections.put(player.getPlayerRef(),
                                stateWith(state, state.equipmentKey, state.gemKey, state.supportKey, slotKey,
                                        null, null, null));
                        player.getWorld().execute(() -> openWithSync(player));
                    });
            LoreSocketData.LoreSocket socket = data.getSocket(i);
            if (socket != null && socket.hasSpirit() && LoreSocketManager.needsFeed(socket)) {
                addListener.invoke(pageBuilder, feedButtonId(i), activating,
                        (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                            ProcessResult result = processFeed(player, equipment, slotKey);
                            pendingSelections.put(player.getPlayerRef(),
                                    stateWith(state, state.equipmentKey, state.gemKey, state.supportKey, slotKey,
                                            result.status, state.detailEquipmentKey, state.detailSlotKey));
                            player.getWorld().execute(() -> openWithSync(player));
                        });
            }
        }
    }

    private static void registerPager(Object pageBuilder,
                                      Method addListener,
                                      Object activating,
                                      Player player,
                                      SelectionState state,
                                      String prevId,
                                      String nextId,
                                      int page,
                                      int pageCount,
                                      int pageKind) throws Exception {
        if (pageCount <= 1) {
            return;
        }
        addListener.invoke(pageBuilder, prevId, activating,
                (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                    if (page <= 0) {
                        return;
                    }
                    pendingSelections.put(player.getPlayerRef(), pageState(state, pageKind, page - 1));
                    player.getWorld().execute(() -> openWithSync(player));
                });
        addListener.invoke(pageBuilder, nextId, activating,
                (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                    if (page >= pageCount - 1) {
                        return;
                    }
                    pendingSelections.put(player.getPlayerRef(), pageState(state, pageKind, page + 1));
                    player.getWorld().execute(() -> openWithSync(player));
                });
    }

    private static SelectionState pageState(SelectionState state, int pageKind, int page) {
        int equipmentPage = state != null ? state.equipmentPage : 0;
        int gemPage = state != null ? state.gemPage : 0;
        int supportPage = state != null ? state.supportPage : 0;
        if (pageKind == 0) {
            equipmentPage = page;
        } else if (pageKind == 1) {
            gemPage = page;
        } else {
            supportPage = page;
        }
        return stateWithPages(state, equipmentPage, gemPage, supportPage);
    }

    private static String buildDetailOverlayHtml(Player player, Snapshot snapshot, SelectionState state) {
        if (snapshot == null || state == null || state.detailEquipmentKey == null || state.detailSlotKey == null) {
            return "";
        }
        Entry equipment = resolveSelection(snapshot.equipments, state.detailEquipmentKey);
        LoreSocketData.LoreSocket socket = resolveSelectedSocket(equipment, state.detailSlotKey);
        if (equipment == null || socket == null) {
            return "";
        }
        List<String> lines = buildSlotDetailLines(player, equipment, socket);
        int lineCount = Math.max(4, lines.size());
        int height = Math.min(520, 110 + (lineCount * 24));
        StringBuilder sb = new StringBuilder();
        sb.append("<div id=\"loreDetailOverlay\" style=\"anchor-full:200; layout-mode:left; background-color:#0b0b1200;\">")
                .append("<div style=\"anchor-width:430; anchor-height:").append(height)
                .append("; layout-mode:top; background-color:#151526; padding:10;\">")
                .append("<div style=\"anchor-width:410; anchor-height:34; layout-mode:left;\">")
                .append("<p style=\"anchor-width:280; font-weight:bold; color:#FFD24D; text-align:left;\">")
                .append(escapeHtml(LangLoader.getUITranslation(player, "ui.lore_socket.detail_title"))).append("</p>")
                .append("<button id=\"loreDetailOverlayClose\" class=\"small-secondary-button\" style=\"anchor-width:110; anchor-height:28;\">")
                .append(escapeHtml(LangLoader.getUITranslation(player, "ui.lore_socket.detail_close"))).append("</button>")
                .append("</div>")
                .append("<p style=\"anchor-height:22; font-weight:bold; text-align:left;\">")
                .append(escapeHtml(equipment.displayName)).append("</p>")
                .append("<img src=\"divider.png\" style=\"anchor-width: 400; anchor-height: 3;\">")
                .append("<div style=\"anchor-width:400; layout-mode:top; spacing:4;\">");
        for (String line : lines) {
            sb.append("<p style=\"anchor-height:20; font-size:14; text-align:left; white-space:nowrap;\">")
                    .append(escapeHtml(line)).append("</p>");
        }
        sb.append("</div></div></div>");
        return sb.toString();
    }

    private static List<String> buildSlotDetailLines(Player player, Entry equipment, LoreSocketData.LoreSocket socket) {
        List<String> lines = new ArrayList<>();
        if (socket == null) {
            lines.add(LangLoader.getUITranslation(player, "ui.lore_socket.status_no_socket"));
            return lines;
        }
        boolean runeDelta = isRuneDeltaEquipment(equipment);
        String color = displaySocketColor(player, socket, runeDelta);
        lines.add(LangLoader.getUITranslation(player, "ui.lore_socket.detail_slot", socket.getSlotIndex() + 1));
        lines.add(LangLoader.getUITranslation(player, "ui.lore_socket.detail_color", color));
        if (socket.hasRune()) {
            lines.add(LangLoader.getUITranslation(player, "ui.lore_socket.materials_runes")
                    + ": " + formatRunestoneNameWithLevel(player, socket));
            if (socket.getRuneDamageFeeds() > 0) {
                lines.add(LangLoader.getUITranslation(player, "ui.lore_socket.rune_damage_feed_bonus",
                        LoreSocketManager.getRuneDamageBonusPercent(socket.getRuneDamageFeeds())));
            }
            if (socket.getRuneManaFeeds() > 0) {
                lines.add(LangLoader.getUITranslation(player, "ui.lore_socket.rune_mana_feed_bonus",
                        LoreSocketManager.getRuneManaCostReductionPercent(socket.getRuneManaFeeds())));
            }
            if (!LoreSocketManager.needsRuneFeed(socket)) {
                lines.add(LangLoader.getUITranslation(player, "ui.lore_socket.rune_feed_maxed"));
            }
        } else if (socket.hasSpirit()) {
            String spiritName = localizeSpiritName(player, socket.getSpiritId());
            lines.add(LangLoader.getUITranslation(player, "ui.lore_feed.spirit_label", spiritName));
            lines.add(LangLoader.getUITranslation(player, "ui.lore_feed.level_label", Math.max(1, socket.getLevel()))
                    + " | " + LangLoader.getUITranslation(player, "ui.lore_feed.feed_tier", Math.max(0, socket.getFeedTier())));
            int needed = Math.max(1, LoreSocketManager.getXpRequiredForLevel(Math.max(1, socket.getLevel())));
            lines.add(LangLoader.getUITranslation(player, "ui.lore_feed.progress_xp", Math.max(0, socket.getXp()), needed));
            lines.add(LangLoader.getUITranslation(player, "ui.lore_feed.feed_required",
                    LoreSocketManager.needsFeed(socket)
                            ? LangLoader.getUITranslation(player, "ui.lore_feed.value_yes")
                            : LangLoader.getUITranslation(player, "ui.lore_feed.value_no")));
            LoreAbility ability = LoreAbilityRegistry.getAbility(socket.getSpiritId());
            if (ability != null) {
                String langCode = resolveLangCode(player);
                lines.add(LangLoader.getUITranslation(player, "ui.lore_feed.preview_ability", ability.resolveAbilityName(langCode)));
                String effect = ability.describeEffectOnly(langCode, Math.max(1, socket.getLevel()), Math.max(0, socket.getFeedTier()));
                if (effect != null && !effect.isBlank()) {
                    lines.add(LangLoader.getUITranslation(player, "ui.lore_feed.preview_effect", effect));
                }
            }
        } else if (runeDelta) {
            lines.add(LangLoader.getUITranslation(player, "ui.lore_socket.detail_empty_rune"));
            lines.add(LangLoader.getUITranslation(player, "ui.lore_socket.detail_eligible_runes"));
        } else {
            lines.add(LangLoader.getUITranslation(player, "ui.lore_socket.detail_empty"));
            lines.add(LangLoader.getUITranslation(player, "ui.lore_socket.detail_eligible"));
            List<String> names = buildCompatibleSpiritNames(player, color);
            if (names.isEmpty()) {
                lines.add(LangLoader.getUITranslation(player, "ui.lore_feed.value_none"));
            } else {
                lines.addAll(names);
            }
        }
        return lines;
    }

    private static List<String> buildCompatibleSpiritNames(Player player, String color) {
        List<String> names = new ArrayList<>();
        List<String> spirits = LoreGemRegistry.getAllowedSpirits(color);
        if (spirits == null) {
            return names;
        }
        for (String spirit : spirits) {
            if (spirit == null || spirit.isBlank()) {
                continue;
            }
            names.add(localizeSpiritName(player, spirit));
        }
        return names;
    }

    private static String resolveRunestoneName(Player player, LoreSocketData.LoreSocket socket) {
        return UIItemUtils.displayNameOrItemId(new ItemStack(socket.getRuneItemId(), 1), player);
    }

    private static String formatRunestoneNameWithLevel(Player player, LoreSocketData.LoreSocket socket) {
        return LangLoader.getUITranslation(player, "ui.lore_socket.rune_level",
                resolveRunestoneName(player, socket), LoreSocketManager.getRuneUpgradeLevel(socket));
    }

    private static String localizeSpiritName(Player player, String spiritId) {
        if (spiritId == null || spiritId.isBlank()) {
            return LangLoader.getUITranslation(player, "ui.lore_feed.value_none");
        }
        String trimmed = spiritId.trim();
        String[] keys = {
                "npcRoles." + trimmed + ".name",
                "npc." + trimmed + ".name",
                "spirit." + trimmed + ".name",
                trimmed
        };
        for (String key : keys) {
            String translated = LangLoader.getUITranslation(player, key);
            if (translated != null && !translated.isBlank() && !translated.equals(key)) {
                return translated;
            }
        }
        return humanizeToken(trimmed);
    }

    private static String humanizeToken(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        String normalized = token.replace('-', '_').replace('.', '_');
        String[] parts = normalized.split("_+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) {
                sb.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return sb.isEmpty() ? token : sb.toString();
    }

    private static String resolveLangCode(Player player) {
        String code = LangLoader.getPlayerLanguage(player);
        return code == null || code.isBlank() ? "en-US" : code;
    }

    private static String resolveLoreColorHex(LoreSocketData.LoreSocket socket) {
        return UISocketVisualUtils.loreColorHex(socket);
    }

    private static String resolveLoreGemOverlayIcon(LoreSocketData.LoreSocket socket) {
        return UISocketVisualUtils.loreGemOverlayIcon(socket);
    }

    private static String buildMetadata(Player player, Entry equipment) {
        if (equipment == null) {
            return LangLoader.getUITranslation(player, "ui.lore_socket.metadata_no_equipment");
        }
        StringBuilder sb = new StringBuilder();
        sb.append(LangLoader.getUITranslation(player, "ui.lore_socket.metadata_header_item")).append("\n");
        sb.append(LangLoader.getUITranslation(player, "ui.lore_socket.metadata_name", equipment.displayName)).append("\n");
        sb.append(LangLoader.getUITranslation(player, "ui.lore_socket.metadata_id", equipment.itemId)).append("\n");
        String location = equipment.kind == ContainerKind.HOTBAR
                ? LangLoader.getUITranslation(player, "ui.lore_socket.metadata_hotbar")
                : LangLoader.getUITranslation(player, "ui.lore_socket.metadata_storage");
        sb.append(LangLoader.getUITranslation(player, "ui.lore_socket.metadata_inventory", location, equipment.slot))
                .append("\n");

        LoreSocketData data = LoreSocketManager.getLoreSocketData(equipment.item);
        if (data == null || data.getSocketCount() == 0) {
            sb.append(LangLoader.getUITranslation(player, "ui.lore_socket.metadata_no_sockets"));
            return sb.toString();
        }
        sb.append(LangLoader.getUITranslation(player, "ui.lore_socket.metadata_header_sockets")).append("\n");
        for (int i = 0; i < data.getSocketCount(); i++) {
            LoreSocketData.LoreSocket socket = data.getSocket(i);
            String state = socketStateLabel(player, socket, isRuneDeltaEquipment(equipment));
            sb.append(LangLoader.getUITranslation(player, "ui.lore_socket.metadata_socket_line", i + 1, state))
                    .append("\n");
        }
        return sb.toString().trim();
    }

    private static String socketStateLabel(Player player, LoreSocketData.LoreSocket socket, boolean runeDelta) {
        if (socket == null) {
            return LangLoader.getUITranslation(player, "ui.lore_socket.socket_empty");
        }
        String base;
        if (socket.isLocked()) {
            base = LangLoader.getUITranslation(player, "ui.lore_socket.socket_locked");
        } else if (socket.isEmpty()) {
            base = LangLoader.getUITranslation(player,
                    runeDelta ? "ui.lore_socket.slot_empty_rune_state" : "ui.lore_socket.socket_empty");
        } else {
            base = LangLoader.getUITranslation(player, "ui.lore_socket.socket_filled");
        }
        String color = displaySocketColor(player, socket, runeDelta);
        return base + " (" + color + ")";
    }

    private static String displaySocketColor(Player player, LoreSocketData.LoreSocket socket, boolean runeDelta) {
        if (socket != null && socket.getColor() != null && !socket.getColor().isBlank()) {
            return socket.getColor();
        }
        return runeDelta || (socket != null && socket.hasRune())
                ? LangLoader.getUITranslation(player, "ui.lore_socket.colorless")
                : LangLoader.getUITranslation(player, "ui.lore_socket.color_unknown");
    }

    private static boolean shouldDisable(Entry equipment, Entry gem, Entry support, String slotKey) {
        if (equipment == null) {
            return true;
        }
        if (support != null) {
            MaterialKind kind = materialKind(support);
            if (kind == MaterialKind.FEED || kind == MaterialKind.CLEAR) {
                return slotKey == null || slotKey.isBlank();
            }
            return false;
        }
        return gem == null && support == null;
    }

    private static ProcessResult processSelection(Player player, Entry equipment, Entry gem, Entry support, String slotKey) {
        if (player == null) {
            return new ProcessResult("Player not found.");
        }
        if (equipment == null || equipment.item == null || equipment.item.isEmpty()) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_no_equipment"));
        }

        if (CorruptionUtils.isCorrupted(equipment.item)) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.error_corrupted"));
        }

        LoreSocketData data = LoreSocketManager.getLoreSocketData(equipment.item);
        if (data == null || data.getSocketCount() == 0) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.option_no_sockets"));
        }
        boolean runeDelta = isRuneDeltaEquipment(equipment);

        if (support != null && support.item != null && !support.item.isEmpty()) {
            MaterialKind kind = materialKind(support);
            if (kind == MaterialKind.FEED) {
                if (runeDelta && isRuneFeedItem(support.itemId)) {
                    return processRuneFeed(player, equipment, slotKey, support.itemId);
                }
                return processFeed(player, equipment, slotKey);
            }
            if (kind == MaterialKind.CLEAR) {
                return processClear(player, equipment, support.itemId, slotKey);
            }
            if (kind != MaterialKind.REROLL) {
                return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_invalid_support"));
            }
            if (runeDelta) {
                return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_rune_delta_support"));
            }

            LoreSocketData.LoreSocket selectedSocket = resolveSelectedSocket(equipment, slotKey);
            if (selectedSocket != null && !selectedSocket.isEmpty()) {
                if (selectedSocket.hasSpirit()) {
                    return processFeed(player, equipment, slotKey);
                }
                return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_slot_full"));
            }

            boolean auto = slotKey == null || slotKey.isBlank() || "auto".equalsIgnoreCase(slotKey);
            int required = auto ? countRerollTargets(data) : 1;
            if (required <= 0) {
                return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_no_socket"));
            }

            if (!hasEnoughItem(player, SUPPORT_REROLL_ITEM_ID, required)) {
                return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_no_support"));
            }

            if (!consumeFromInventory(player, SUPPORT_REROLL_ITEM_ID, required)) {
                return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_no_support"));
            }

            Random rng = ThreadLocalRandom.current();
            if (auto) {
                for (int i = 0; i < data.getSocketCount(); i++) {
                    LoreSocketData.LoreSocket socket = data.getSocket(i);
                    if (socket == null || socket.isLocked() || !socket.isEmpty()) {
                        continue;
                    }
                    rerollSocketColor(socket, rng);
                }
            } else {
                int slotIndex = resolveSlotIndex(data, slotKey, null);
                if (slotIndex < 0 || slotIndex >= data.getSocketCount()) {
                    return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_no_socket"));
                }
                LoreSocketData.LoreSocket socket = data.getSocket(slotIndex);
                if (socket == null) {
                    return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_no_socket"));
                }
                if (socket.isLocked()) {
                    return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_slot_locked"));
                }
                if (!socket.isEmpty()) {
                    return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_slot_full"));
                }
                rerollSocketColor(socket, rng);
            }

            saveEquipment(player, equipment, data);
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_reroll_done"));
        }

        if (gem == null || gem.item == null || gem.item.isEmpty()) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_no_gem"));
        }
        if (runeDelta) {
            boolean modifierRunestone = LoreSocketManager.isModifierRunestone(gem.item);
            if (!LoreSocketManager.isAbilityRunestone(gem.item) && !modifierRunestone) {
                return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_invalid_rune"));
            }
            int slotIndex = resolveSlotIndex(data, slotKey, null);
            if (slotIndex < 0 || slotIndex >= data.getSocketCount()) {
                return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_no_socket"));
            }
            LoreSocketData.LoreSocket socket = data.getSocket(slotIndex);
            if (socket == null) {
                return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_no_socket"));
            }
            if (socket.isLocked()) {
                return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_slot_locked"));
            }
            if (!socket.isEmpty()) {
                return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_slot_full"));
            }
            socket.setRuneItemId(gem.itemId);
            socket.setColor("");
            socket.setRuneDamageFeeds(0);
            socket.setRuneManaFeeds(0);
            if (modifierRunestone) {
                socket.setLevel(LoreSocketManager.getModifierRunestoneLevel(gem.item));
                socket.setFeedTier(LoreSocketManager.getModifierRunestoneFeedTier(gem.item));
            } else {
                boolean hasLoreProgression = LoreSocketManager.hasCoreAbilityRunestoneProgression(gem.item);
                socket.setLevel(hasLoreProgression
                        ? LoreSocketManager.getCoreAbilityRunestoneLevel(gem.item) : 0);
                socket.setXp(0);
                socket.setXpBonusRemainder(0);
                socket.setFeedTier(hasLoreProgression
                        ? LoreSocketManager.getCoreAbilityRunestoneFeedTier(gem.item) : 0);
                socket.setRuneDamageFeeds(LoreSocketManager.getRunestoneDamageFeeds(gem.item));
                socket.setRuneManaFeeds(LoreSocketManager.getRunestoneManaFeeds(gem.item));
            }
            saveEquipment(player, equipment, data);

            ItemContainer runeContainer = getContainer(player, gem.kind);
            if (runeContainer != null) {
                runeContainer.removeItemStackFromSlot(gem.slot, 1, false, false);
            }
            CharacterPathService.awardBenchXp(player, 10, "rune socketing");
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_rune_done"));
        }
        if (!LoreSocketManager.isLoreGem(gem.item)) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_invalid_gem"));
        }

        String gemColor = LoreGemRegistry.resolveColor(gem.itemId);
        if (gemColor == null || gemColor.isBlank()) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_missing_color"));
        }

        String infusedSpiritId = LoreSoulstoneUtils.getSpiritId(gem.item);
        boolean infusedSoulstone = infusedSpiritId != null;
        if (LoreSoulstoneUtils.isInfusedSoulstone(gem.item) && !infusedSoulstone) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_invalid_soulstone"));
        }
        int slotIndex = infusedSoulstone
                ? resolveInfusedSoulstoneSlotIndex(data, slotKey, gemColor)
                : resolveSlotIndex(data, slotKey, gemColor);
        if (slotIndex < 0 || slotIndex >= data.getSocketCount()) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_no_socket"));
        }

        LoreSocketData.LoreSocket socket = data.getSocket(slotIndex);
        if (socket == null) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_no_socket"));
        }
        if (socket.isLocked()) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_slot_locked"));
        }
        String socketColor = socket.getColor();
        String existingGemColor = LoreGemRegistry.resolveColor(socket.getGemItemId());
        if ((socketColor == null || socketColor.isBlank()) && existingGemColor != null) {
            socketColor = existingGemColor;
        }
        if (socketColor != null && !socketColor.isBlank() && !socketColor.equalsIgnoreCase(gemColor)) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_color_mismatch"));
        }

        if (infusedSoulstone) {
            if (socket.hasSpirit() || socket.hasRune()) {
                return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_slot_full"));
            }
            socket.setGemItemId(gem.itemId);
            socket.setSpiritId(infusedSpiritId);
            socket.setEffectOverride("");
            socket.setColor(gemColor.toLowerCase(Locale.ROOT));
            socket.setLevel(1);
            socket.setXp(0);
            socket.setXpBonusRemainder(0);
            socket.setFeedTier(0);
            socket.setRuneDamageFeeds(0);
            socket.setRuneManaFeeds(0);
            socket.setLocked(true);

            saveEquipment(player, equipment, data);
            ItemContainer stoneContainer = getContainer(player, gem.kind);
            if (stoneContainer != null) {
                stoneContainer.removeItemStackFromSlot(gem.slot, 1, false, false);
            }
            CharacterPathService.awardBenchXp(player, 10, "soulstone socketing");
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_soulstone_done"));
        }

        if (!socket.isEmpty()) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_slot_full"));
        }

        socket.setGemItemId(gem.itemId);
        socket.setRuneDamageFeeds(0);
        socket.setRuneManaFeeds(0);
        if (socketColor == null || socketColor.isBlank()) {
            socket.setColor(gemColor.toLowerCase(Locale.ROOT));
        }

        saveEquipment(player, equipment, data);

        ItemContainer gemContainer = getContainer(player, gem.kind);
        if (gemContainer != null) {
            gemContainer.removeItemStackFromSlot(gem.slot, 1, false, false);
        }
        CharacterPathService.awardBenchXp(player, 8, "lore socketing");

        return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_done"));
    }

    private static ProcessResult processFeed(Player player, Entry equipment, String slotKey) {
        if (player == null) {
            return new ProcessResult("Player not found.");
        }
        if (equipment == null || equipment.item == null || equipment.item.isEmpty()) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_feed.status_no_equipment"));
        }
        ItemStack equipmentItem = getEffectiveEquipmentItem(player, equipment);
        LoreSocketData data = LoreSocketManager.getLoreSocketData(equipmentItem);
        int slotIndex = resolveSlotIndex(data, slotKey, null);
        if (slotIndex < 0 || data == null || slotIndex >= data.getSocketCount()) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_feed.status_no_socket"));
        }
        LoreSocketData.LoreSocket socket = data.getSocket(slotIndex);
        if (socket == null || !socket.hasSpirit()) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_feed.status_no_spirit"));
        }
        if (!LoreSocketManager.needsFeed(socket)) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_feed.status_not_ready"));
        }
        if (!LoreSocketManager.tryFeed(player, data, slotIndex)) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_feed.status_no_essence"));
        }
        saveEquipment(player, equipment, data);
        CharacterPathService.awardBenchXp(player, 10, "lore feeding");
        return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_feed.status_done"));
    }

    private static ProcessResult processRuneFeed(Player player, Entry equipment, String slotKey, String feedItemId) {
        if (player == null) {
            return new ProcessResult("Player not found.");
        }
        if (equipment == null || equipment.item == null || equipment.item.isEmpty()) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_feed.status_no_equipment"));
        }
        ItemStack equipmentItem = getEffectiveEquipmentItem(player, equipment);
        if (!LoreSocketManager.isRuneDeltaActive(equipmentItem)) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_rune_feed_requires_delta"));
        }
        LoreSocketData data = LoreSocketManager.getLoreSocketData(equipmentItem);
        int slotIndex = resolveRuneFeedSlotIndex(data, slotKey);
        if (slotIndex < 0 || data == null || slotIndex >= data.getSocketCount()) {
            if (data != null && (slotKey == null || slotKey.isBlank() || "auto".equalsIgnoreCase(slotKey))) {
                for (int i = 0; i < data.getSocketCount(); i++) {
                    LoreSocketData.LoreSocket candidate = data.getSocket(i);
                    if (candidate != null && candidate.hasRune()) {
                        return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_rune_feed_maxed"));
                    }
                }
            }
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_feed.status_no_socket"));
        }
        LoreSocketData.LoreSocket socket = data.getSocket(slotIndex);
        if (socket == null || !socket.hasRune()) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_rune_feed_no_rune"));
        }
        if (!LoreSocketManager.needsRuneFeed(socket)) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_rune_feed_maxed"));
        }
        if (!LoreSocketManager.tryFeedRune(player, socket, feedItemId)) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_rune_feed_no_material"));
        }
        saveEquipment(player, equipment, data);
        CharacterPathService.awardBenchXp(player, 10, "rune feeding");
        return new ProcessResult(formatRuneFeedStatus(player, socket));
    }

    private static String formatRuneFeedStatus(Player player, LoreSocketData.LoreSocket socket) {
        int level = LoreSocketManager.getRuneUpgradeLevel(socket);
        int damageFeeds = socket == null ? 0 : socket.getRuneDamageFeeds();
        int manaFeeds = socket == null ? 0 : socket.getRuneManaFeeds();
        if (damageFeeds > 0 && manaFeeds > 0) {
            return LangLoader.getUITranslation(player, "ui.lore_socket.status_rune_feed_done_both",
                    level,
                    LoreSocketManager.getRuneDamageBonusPercent(damageFeeds),
                    LoreSocketManager.getRuneManaCostReductionPercent(manaFeeds));
        }
        if (manaFeeds > 0) {
            return LangLoader.getUITranslation(player, "ui.lore_socket.status_rune_feed_done_mana",
                    level, LoreSocketManager.getRuneManaCostReductionPercent(manaFeeds));
        }
        return LangLoader.getUITranslation(player, "ui.lore_socket.status_rune_feed_done_damage",
                level, LoreSocketManager.getRuneDamageBonusPercent(damageFeeds));
    }

    private static int resolveRuneFeedSlotIndex(LoreSocketData data, String slotKey) {
        if (data == null || data.getSocketCount() == 0) {
            return -1;
        }
        String key = slotKey == null ? "" : slotKey.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty() || "auto".equals(key)) {
            for (int i = 0; i < data.getSocketCount(); i++) {
                if (LoreSocketManager.needsRuneFeed(data.getSocket(i))) {
                    return i;
                }
            }
            return -1;
        }
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static ProcessResult processClear(Player player, Entry equipment, String supportItemId, String slotKey) {
        if (player == null) {
            return new ProcessResult("Player not found.");
        }
        if (equipment == null || equipment.item == null || equipment.item.isEmpty()) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_feed.status_no_equipment"));
        }
        ItemStack equipmentItem = getEffectiveEquipmentItem(player, equipment);
        LoreSocketData data = LoreSocketManager.getLoreSocketData(equipmentItem);
        int slotIndex = resolveSlotIndex(data, slotKey, null);
        if (slotIndex < 0 || data == null || slotIndex >= data.getSocketCount()) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_feed.status_no_socket"));
        }
        LoreSocketData.LoreSocket socket = data.getSocket(slotIndex);
        if (socket == null || socket.isEmpty()) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_feed.status_no_socket"));
        }

        if (LoreSocketManager.isRuneDeltaActive(equipmentItem) && socket.hasRune()
                && LoreSocketManager.isAbilityRunestoneId(socket.getRuneItemId())) {
            if (supportItemId == null
                    || !supportItemId.equalsIgnoreCase(LoreSocketManager.GHASTLY_ESSENCE_ITEM_ID)) {
                return new ProcessResult(LangLoader.getUITranslation(player,
                        "ui.lore_feed.status_rune_delta_extract_requires_ghastly"));
            }
            if (!hasEnoughItem(player, LoreSocketManager.GHASTLY_ESSENCE_ITEM_ID, 1)) {
                return new ProcessResult(LangLoader.getUITranslation(player,
                        "ui.lore_feed.status_no_clear_item"));
            }
            if (!LoreSocketManager.tryExtractRuneDeltaAbilityRunestone(player, data, slotIndex)) {
                return new ProcessResult(LangLoader.getUITranslation(player,
                        "ui.lore_feed.status_rune_delta_extract_no_space"));
            }
            saveEquipment(player, equipment, data);
            return new ProcessResult(LangLoader.getUITranslation(player,
                    "ui.lore_feed.status_rune_delta_extracted"));
        }

        if (LoreSocketManager.isOctaslashSpirit(socket.getSpiritId())) {
            if (!LoreSocketManager.isMaxLevelOctaslashLore(socket)) {
                return new ProcessResult(LangLoader.getUITranslation(player,
                        "ui.lore_feed.status_octaslash_max_required"));
            }
            if (supportItemId == null || !supportItemId.equalsIgnoreCase(LoreSocketManager.GHASTLY_ESSENCE_ITEM_ID)) {
                return new ProcessResult(LangLoader.getUITranslation(player,
                        "ui.lore_feed.status_octaslash_requires_ghastly"));
            }
            if (!hasEnoughItem(player, LoreSocketManager.GHASTLY_ESSENCE_ITEM_ID, 1)) {
                return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_feed.status_no_clear_item"));
            }
            if (!LoreSocketManager.tryExtractOctaslashRune(player, data, slotIndex)) {
                return new ProcessResult(LangLoader.getUITranslation(player,
                        "ui.lore_feed.status_octaslash_no_space"));
            }
            saveEquipment(player, equipment, data);
            return new ProcessResult(LangLoader.getUITranslation(player,
                    "ui.lore_feed.status_octaslash_extracted"));
        }

        if (LoreSocketManager.isOmnislashSpirit(socket.getSpiritId())) {
            if (!LoreSocketManager.isMaxLevelOmnislashLore(socket)) {
                return new ProcessResult(LangLoader.getUITranslation(player,
                        "ui.lore_feed.status_omnislash_max_required"));
            }
            if (supportItemId == null || !supportItemId.equalsIgnoreCase(LoreSocketManager.GHASTLY_ESSENCE_ITEM_ID)) {
                return new ProcessResult(LangLoader.getUITranslation(player,
                        "ui.lore_feed.status_omnislash_requires_ghastly"));
            }
            if (!hasEnoughItem(player, LoreSocketManager.GHASTLY_ESSENCE_ITEM_ID, 1)) {
                return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_feed.status_no_clear_item"));
            }
            if (!LoreSocketManager.tryExtractOmnislashRune(player, data, slotIndex)) {
                return new ProcessResult(LangLoader.getUITranslation(player,
                        "ui.lore_feed.status_omnislash_no_space"));
            }
            saveEquipment(player, equipment, data);
            return new ProcessResult(LangLoader.getUITranslation(player,
                    "ui.lore_feed.status_omnislash_extracted"));
        }

        if (LoreSocketManager.isBloodRushSpirit(socket.getSpiritId())) {
            if (!LoreSocketManager.isMaxLevelBloodRushLore(socket)) {
                return new ProcessResult(LangLoader.getUITranslation(player,
                        "ui.lore_feed.status_bloodrush_max_required"));
            }
            if (supportItemId == null || !supportItemId.equalsIgnoreCase(LoreSocketManager.GHASTLY_ESSENCE_ITEM_ID)) {
                return new ProcessResult(LangLoader.getUITranslation(player,
                        "ui.lore_feed.status_bloodrush_requires_ghastly"));
            }
            if (!hasEnoughItem(player, LoreSocketManager.GHASTLY_ESSENCE_ITEM_ID, 1)) {
                return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_feed.status_no_clear_item"));
            }
            if (!LoreSocketManager.tryExtractBloodRushRune(player, data, slotIndex)) {
                return new ProcessResult(LangLoader.getUITranslation(player,
                        "ui.lore_feed.status_bloodrush_no_space"));
            }
            saveEquipment(player, equipment, data);
            return new ProcessResult(LangLoader.getUITranslation(player,
                    "ui.lore_feed.status_bloodrush_extracted"));
        }

        if (LoreSocketManager.isPummelSpirit(socket.getSpiritId())) {
            if (!LoreSocketManager.isMaxLevelPummelLore(socket)) {
                return new ProcessResult(LangLoader.getUITranslation(player,
                        "ui.lore_feed.status_pummel_max_required"));
            }
            if (supportItemId == null || !supportItemId.equalsIgnoreCase(LoreSocketManager.GHASTLY_ESSENCE_ITEM_ID)) {
                return new ProcessResult(LangLoader.getUITranslation(player,
                        "ui.lore_feed.status_pummel_requires_ghastly"));
            }
            if (!hasEnoughItem(player, LoreSocketManager.GHASTLY_ESSENCE_ITEM_ID, 1)) {
                return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_feed.status_no_clear_item"));
            }
            if (!LoreSocketManager.tryExtractPummelRune(player, data, slotIndex)) {
                return new ProcessResult(LangLoader.getUITranslation(player,
                        "ui.lore_feed.status_pummel_no_space"));
            }
            saveEquipment(player, equipment, data);
            return new ProcessResult(LangLoader.getUITranslation(player,
                    "ui.lore_feed.status_pummel_extracted"));
        }

        if (LoreSocketManager.isWolfPackSpirit(socket.getSpiritId())) {
            if (!LoreSocketManager.isMaxLevelWolfPackLore(socket)) {
                String statusKey = socket.getLevel() >= LoreSocketManager.getConfig().getMaxLevel()
                        && socket.getFeedTier() < 1
                        ? "ui.lore_feed.status_wolfpack_feed_required"
                        : "ui.lore_feed.status_wolfpack_max_required";
                return new ProcessResult(LangLoader.getUITranslation(player,
                        statusKey));
            }
            if (supportItemId == null || !supportItemId.equalsIgnoreCase(LoreSocketManager.GHASTLY_ESSENCE_ITEM_ID)) {
                return new ProcessResult(LangLoader.getUITranslation(player,
                        "ui.lore_feed.status_wolfpack_requires_ghastly"));
            }
            if (!hasEnoughItem(player, LoreSocketManager.GHASTLY_ESSENCE_ITEM_ID, 1)) {
                return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_feed.status_no_clear_item"));
            }
            if (!LoreSocketManager.tryExtractWolfPackRune(player, data, slotIndex)) {
                return new ProcessResult(LangLoader.getUITranslation(player,
                        "ui.lore_feed.status_wolfpack_no_space"));
            }
            saveEquipment(player, equipment, data);
            return new ProcessResult(LangLoader.getUITranslation(player,
                    "ui.lore_feed.status_wolfpack_extracted"));
        }

        if (LoreSocketManager.isCoreAbilityRunestoneSpirit(socket.getSpiritId())) {
            if (!LoreSocketManager.isExtractableCoreAbilityLore(socket)) {
                return new ProcessResult(LangLoader.getUITranslation(player,
                        "ui.lore_feed.status_core_rune_feed_required"));
            }
            if (supportItemId == null || !supportItemId.equalsIgnoreCase(LoreSocketManager.GHASTLY_ESSENCE_ITEM_ID)) {
                return new ProcessResult(LangLoader.getUITranslation(player,
                        "ui.lore_feed.status_core_rune_requires_ghastly"));
            }
            if (!hasEnoughItem(player, LoreSocketManager.GHASTLY_ESSENCE_ITEM_ID, 1)) {
                return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_feed.status_no_clear_item"));
            }
            if (!LoreSocketManager.tryExtractCoreAbilityRunestone(player, data, slotIndex)) {
                return new ProcessResult(LangLoader.getUITranslation(player,
                        "ui.lore_feed.status_core_rune_no_space"));
            }
            saveEquipment(player, equipment, data);
            return new ProcessResult(LangLoader.getUITranslation(player,
                    "ui.lore_feed.status_core_rune_extracted"));
        }

        if (LoreSocketManager.isModifierRunestoneSpirit(socket.getSpiritId())) {
            if (!LoreSocketManager.isMaxLevelModifierLore(socket)) {
                return new ProcessResult(LangLoader.getUITranslation(player,
                        "ui.lore_feed.status_modifier_rune_max_required"));
            }
            if (supportItemId == null || !supportItemId.equalsIgnoreCase(LoreSocketManager.GHASTLY_ESSENCE_ITEM_ID)) {
                return new ProcessResult(LangLoader.getUITranslation(player,
                        "ui.lore_feed.status_modifier_rune_requires_ghastly"));
            }
            if (!hasEnoughItem(player, LoreSocketManager.GHASTLY_ESSENCE_ITEM_ID, 1)) {
                return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_feed.status_no_clear_item"));
            }
            if (!LoreSocketManager.tryExtractModifierRunestone(player, data, slotIndex)) {
                return new ProcessResult(LangLoader.getUITranslation(player,
                        "ui.lore_feed.status_modifier_rune_no_space"));
            }
            saveEquipment(player, equipment, data);
            return new ProcessResult(LangLoader.getUITranslation(player,
                    "ui.lore_feed.status_modifier_rune_extracted"));
        }

        if (!LoreSocketManager.tryClearSpirit(player, data, slotIndex, true)) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_feed.status_no_clear_item"));
        }
        saveEquipment(player, equipment, data);
        return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_feed.status_cleared"));
    }

    private static void saveEquipment(Player player, Entry equipment, LoreSocketData data) {
        ItemStack baseItem = getEffectiveEquipmentItem(player, equipment);
        if (baseItem == null || baseItem.isEmpty()) {
            baseItem = equipment.item;
        }
        ItemStack updated = LoreSocketManager.withLoreSocketData(baseItem, data);
        ItemContainer equipmentContainer = getContainer(player, equipment.kind);
        if (equipmentContainer != null) {
            equipmentContainer.setItemStackForSlot(equipment.slot, updated);
            player.markNeedsSave();
            LoreHeldItemUpdateManager.clearPending(player, sectionIdFor(equipment.kind), equipment.slot, updated);
        }
    }

    private static int resolveSlotIndex(LoreSocketData data, String slotKey, String gemColor) {
        if (data == null || data.getSocketCount() == 0) {
            return -1;
        }
        String key = slotKey == null ? "" : slotKey.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty() || "auto".equals(key)) {
            for (int i = 0; i < data.getSocketCount(); i++) {
                LoreSocketData.LoreSocket socket = data.getSocket(i);
                if (socket == null || socket.isLocked() || !socket.isEmpty()) {
                    continue;
                }
                String color = socket.getColor();
                if (color != null && !color.isBlank() && gemColor != null && !gemColor.equalsIgnoreCase(color)) {
                    continue;
                }
                return i;
            }
            return -1;
        }
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static int resolveInfusedSoulstoneSlotIndex(LoreSocketData data, String slotKey, String stoneColor) {
        if (data == null || data.getSocketCount() == 0) {
            return -1;
        }
        String key = slotKey == null ? "" : slotKey.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty() || "auto".equals(key)) {
            for (int i = 0; i < data.getSocketCount(); i++) {
                LoreSocketData.LoreSocket socket = data.getSocket(i);
                if (isCompatibleEmptySoulstoneSocket(socket, stoneColor)) {
                    return i;
                }
            }
            return -1;
        }
        try {
            int index = Integer.parseInt(key);
            if (index < 0 || index >= data.getSocketCount()) {
                return -1;
            }
            return isCompatibleEmptySoulstoneSocket(data.getSocket(index), stoneColor) ? index : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static boolean isCompatibleEmptySoulstoneSocket(LoreSocketData.LoreSocket socket, String stoneColor) {
        if (socket == null || socket.isLocked() || socket.hasSpirit() || socket.hasRune()) {
            return false;
        }
        String color = socket.getColor();
        if (color == null || color.isBlank()) {
            color = LoreGemRegistry.resolveColor(socket.getGemItemId());
        }
        return color == null || color.isBlank() || color.equalsIgnoreCase(stoneColor);
    }

    private static boolean rerollSocketColor(LoreSocketData.LoreSocket socket, Random rng) {
        if (socket == null) {
            return false;
        }
        Random random = rng == null ? ThreadLocalRandom.current() : rng;
        String previous = socket.getColor();
        String next = LoreGemRegistry.pickRandomColor(random);
        if (next == null || next.isBlank()) {
            return false;
        }
        if (previous != null && !previous.isBlank() && previous.equalsIgnoreCase(next)) {
            String second = LoreGemRegistry.pickRandomColor(random);
            if (second != null && !second.isBlank()) {
                next = second;
            }
        }
        socket.setColor(next.toLowerCase(Locale.ROOT));
        return previous == null || !previous.equalsIgnoreCase(next);
    }

    private static int countRerollTargets(LoreSocketData data) {
        if (data == null || data.getSocketCount() == 0) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < data.getSocketCount(); i++) {
            LoreSocketData.LoreSocket socket = data.getSocket(i);
            if (socket == null) {
                continue;
            }
            if (socket.isLocked() || !socket.isEmpty()) {
                continue;
            }
            count++;
        }
        return count;
    }

    private static boolean hasEnoughItem(Player player, String itemId, int required) {
        if (player == null || itemId == null || itemId.isBlank() || required <= 0) {
            return false;
        }
        int total = countItem(player.getInventory().getHotbar(), itemId)
                + countItem(player.getInventory().getStorage(), itemId);
        return total >= required;
    }

    private static int countItem(ItemContainer container, String itemId) {
        if (container == null || itemId == null || itemId.isBlank()) {
            return 0;
        }
        int count = 0;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack == null || stack.isEmpty() || stack.getItemId() == null) {
                continue;
            }
            if (stack.getItemId().equalsIgnoreCase(itemId)) {
                count += stack.getQuantity();
            }
        }
        return count;
    }

    private static boolean consumeFromInventory(Player player, String itemId, int amount) {
        if (player == null || itemId == null || itemId.isBlank() || amount <= 0) {
            return false;
        }
        int remaining = amount;
        remaining = consumeFromContainer(player.getInventory().getHotbar(), itemId, remaining);
        if (remaining <= 0) {
            return true;
        }
        remaining = consumeFromContainer(player.getInventory().getStorage(), itemId, remaining);
        return remaining <= 0;
    }

    private static int consumeFromContainer(ItemContainer container, String itemId, int remaining) {
        if (container == null || itemId == null || itemId.isBlank() || remaining <= 0) {
            return remaining;
        }
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            if (remaining <= 0) {
                break;
            }
            ItemStack stack = container.getItemStack(slot);
            if (stack == null || stack.isEmpty() || stack.getItemId() == null) {
                continue;
            }
            if (!stack.getItemId().equalsIgnoreCase(itemId)) {
                continue;
            }
            int quantity = stack.getQuantity();
            if (quantity <= 0) {
                continue;
            }
            int take = Math.min(quantity, remaining);
            container.removeItemStackFromSlot(slot, take, false, false);
            remaining -= take;
        }
        return remaining;
    }

    private static Entry resolveSelection(List<Entry> entries, String key) {
        return HyUIReflectionUtils.resolveIndexSelection(entries, key);
    }

    private static Entry findByKey(List<Entry> entries, String key) {
        return HyUIReflectionUtils.resolveIndexSelection(entries, key);
    }

    private static ItemStack getEffectiveEquipmentItem(Player player, Entry equipment) {
        if (player == null || equipment == null) {
            return equipment == null ? null : equipment.item;
        }
        ItemContainer container = getContainer(player, equipment.kind);
        ItemStack item = equipment.item;
        if (container != null && equipment.slot >= 0 && equipment.slot < container.getCapacity()) {
            try {
                ItemStack current = container.getItemStack(equipment.slot);
                if (current != null && !current.isEmpty()) {
                    item = current;
                }
            } catch (Throwable ignored) {
            }
        }
        if (equipment.kind == ContainerKind.HOTBAR
                && player.getInventory() != null
                && PlayerInventoryUtils.getSelectedHotbarSlot(player) == equipment.slot) {
            PlayerInventoryUtils.HeldItemContext ctx = new PlayerInventoryUtils.HeldItemContext(
                    PlayerInventoryUtils.HOTBAR_SECTION_ID,
                    equipment.slot,
                    container,
                    item);
            ItemStack resolved = LoreHeldItemUpdateManager.resolveHeldItem(player, ctx);
            if (resolved != null && !resolved.isEmpty()) {
                return resolved;
            }
        }
        return item;
    }

    private static int sectionIdFor(ContainerKind kind) {
        return kind == ContainerKind.HOTBAR
                ? PlayerInventoryUtils.HOTBAR_SECTION_ID
                : 0;
    }

    private static ItemContainer getContainer(Player player, ContainerKind kind) {
        if (player == null || player.getInventory() == null) {
            return null;
        }
        return kind == ContainerKind.HOTBAR ? player.getInventory().getHotbar() : player.getInventory().getStorage();
    }

    private static String loadTemplate() {
        return UITemplateUtils.loadTemplate(
                LoreSocketBenchUI.class,
                TEMPLATE_PATH,
                "<div style=\"padding:20px;\">Lore socket UI template missing.</div>",
                "LoreSocketBenchUI");
    }

    private static String escapeHtml(String text) {
        return UITemplateUtils.escapeHtml(text);
    }

    private static String extractEventValue(Object eventObj) {
        return HyUIReflectionUtils.extractEventValue(eventObj);
    }

    private static String getContextValue(Object ctxObj, String... keys) {
        return HyUIReflectionUtils.getContextValue(ctxObj, keys);
    }

    private static Object getStore(PlayerRef playerRef) throws Exception {
        return HyUIReflectionUtils.getStore(playerRef);
    }

    private static void closePageIfOpen(PlayerRef playerRef) {
        HyUIReflectionUtils.closePageIfOpen(openPages, playerRef);
    }

    private static PatchStyle textureBackground(String texturePath) {
        return new PatchStyle(Value.of(texturePath), Value.of(0));
    }

    private static String child(String parent, int index) {
        return parent + "[" + index + "]";
    }

    /** Native Hytale page for lore gem and runestone socketing. */
    private static final class NativeLoreSocketBenchPage extends InteractiveCustomUIPage<NativeLoreSocketBenchPage.PageEvent> {
        private static final class DetailRow {
            final String label;
            final String value;

            DetailRow(String label, String value) {
                this.label = label == null ? "" : label;
                this.value = value == null ? "" : value;
            }
        }

        private static final class ProgressMilestone {
            final int level;
            final int feedTier;
            final boolean feedGate;
            final boolean maxLevel;

            ProgressMilestone(int level, int feedTier, boolean feedGate, boolean maxLevel) {
                this.level = level;
                this.feedTier = feedTier;
                this.feedGate = feedGate;
                this.maxLevel = maxLevel;
            }
        }

        private final PlayerRef owner;
        private Snapshot snapshot;
        private SelectionState state;
        private volatile boolean dismissed;

        NativeLoreSocketBenchPage(PlayerRef owner, Snapshot snapshot, SelectionState state) {
            super(owner, CustomPageLifetime.CanDismiss, PageEvent.CODEC);
            this.owner = owner;
            this.snapshot = snapshot;
            this.state = state == null
                    ? new SelectionState(snapshot == null || snapshot.equipments.isEmpty() ? null : "0",
                            null, null, null, null)
                    : state;
        }

        @Override
        public void build(Ref<EntityStore> playerRef, UICommandBuilder commands,
                          UIEventBuilder events, Store<EntityStore> store) {
            Player player = store.getComponent(playerRef, Player.getComponentType());
            if (player == null) return;
            commands.append(NATIVE_PAGE_UI);
            populate(player, commands, events, false);
        }

        @Override
        public void handleDataEvent(Ref<EntityStore> playerRef, Store<EntityStore> store, PageEvent event) {
            super.handleDataEvent(playerRef, store, event);
            if (dismissed || event == null || event.action == null) return;
            Player player = store.getComponent(playerRef, Player.getComponentType());
            if (player == null) return;
            if (state == null) state = new SelectionState(null, null, null, null, null);

            switch (event.action) {
                case "progress_node_hover" -> {
                    showProgressNodeTooltip(player, event.target);
                    return;
                }
                case "progress_node_exit" -> {
                    UICommandBuilder commands = new UICommandBuilder();
                    commands.set("#LoreProgressHover.Visible", false);
                    sendUpdate(commands, false);
                    return;
                }
                case "select_equipment" -> state = stateWith(state, event.target, null, state.supportKey,
                        null, null, null, null);
                case "select_slot" -> state = stateWith(state, state.equipmentKey, state.gemKey,
                        state.supportKey, event.target, null, null, null);
                case "select_equipment_slot" -> selectEquipmentSlot(event.target);
                case "select_gem" -> state = stateWith(state, state.equipmentKey, event.target,
                        null, state.slotKey, null, null, null);
                case "select_support" -> state = stateWith(state, state.equipmentKey, null,
                        event.target == null || event.target.isBlank() ? null : event.target,
                        state.slotKey, null, null, null);
                case "feed_slot" -> {
                    feedSlot(player, event.target);
                    return;
                }
                case "process" -> {
                    process(player);
                    return;
                }
                default -> { return; }
            }
            refresh(player);
        }

        @Override
        public void onDismiss(Ref<EntityStore> playerRef, Store<EntityStore> store) {
            dismissed = true;
            pendingSelections.remove(owner);
            pendingNavToFeed.remove(owner);
            openPages.remove(owner, this);
            super.onDismiss(playerRef, store);
        }

        private void selectEquipmentSlot(String target) {
            String[] parts = target == null ? null : target.split(":", 2);
            if (parts == null || parts.length != 2) return;
            Entry equipment = resolveSelection(snapshot.equipments, parts[0]);
            if (equipment == null) return;
            state = stateWith(state, parts[0], null, state.supportKey, parts[1], null, null, null);
        }

        private void process(Player player) {
            snapshot = collectSnapshot(player);
            Entry equipment = resolveSelection(snapshot.equipments, state.equipmentKey);
            Entry gem = resolveSelection(materialEntries(snapshot, equipment), state.gemKey);
            Entry support = resolveSelection(snapshot.supports, state.supportKey);
            if (shouldDisable(equipment, gem, support, state.slotKey)) {
                refresh(player);
                return;
            }
            ProcessResult result = processSelection(player, equipment, gem, support, state.slotKey);
            state = stateWith(state, state.equipmentKey, null, null, state.slotKey,
                    result.status, null, null);
            refresh(player);
        }

        private void feedSlot(Player player, String target) {
            Entry equipment = resolveSelection(snapshot.equipments, state.equipmentKey);
            int slot = parseIndex(target, -1);
            if (equipment == null || slot < 0) return;
            String slotKey = String.valueOf(slot);
            ProcessResult result = processFeed(player, equipment, slotKey);
            state = stateWith(state, state.equipmentKey, state.gemKey, state.supportKey,
                    slotKey, result.status, null, null);
            refresh(player);
        }

        private void refresh(Player player) {
            if (dismissed) return;
            snapshot = collectSnapshot(player);
            if (state == null) state = new SelectionState(null, null, null, null, null);
            pendingSelections.put(owner, state);
            UICommandBuilder commands = new UICommandBuilder();
            UIEventBuilder events = new UIEventBuilder();
            populate(player, commands, events, true);
            sendUpdate(commands, events, false);
        }

        private void populate(Player player, UICommandBuilder commands,
                              UIEventBuilder events, boolean replaceContents) {
            if (snapshot == null) snapshot = collectSnapshot(player);
            if (state == null) state = new SelectionState(null, null, null, null, null);
            Entry equipment = resolveSelection(snapshot.equipments, state.equipmentKey);
            List<Entry> materials = materialEntries(snapshot, equipment);
            Entry gem = resolveSelection(materials, state.gemKey);
            Entry support = resolveSelection(snapshot.supports, state.supportKey);
            LoreSocketData loreData = equipment == null ? null
                    : LoreSocketManager.getLoreSocketData(getEffectiveEquipmentItem(player, equipment));
            boolean hasLoreSockets = loreData != null && loreData.getSocketCount() > 0;
            LoreSocketData.LoreSocket selectedSocket = selectedSocket(loreData, state.slotKey);
            boolean canProcess = !shouldDisable(equipment, gem, support, state.slotKey);
            boolean runeDelta = isRuneDeltaEquipment(equipment);

            commands.set("#LoreBenchTitle.Text", LangLoader.getUITranslation(player, "ui.lore_socket.title"));
            commands.set("#LoreBenchSubtitle.Text", LangLoader.getUITranslation(player, "ui.lore_socket.subtitle"));
            commands.set("#EquipmentTitle.Text", LangLoader.getUITranslation(player, "ui.lore_socket.equipment_list_title"));
            commands.set("#EquipmentHint.Text", LangLoader.getUITranslation(player, "ui.lore_socket.equipment_list_hint"));
            commands.set("#MaterialsTitle.Text", LangLoader.getUITranslation(player, "ui.lore_socket.materials_list_title"));
            commands.set("#MaterialsHint.Text", LangLoader.getUITranslation(player, "ui.lore_socket.materials_list_hint"));
            commands.set("#LoreGemTitle.Text", LangLoader.getUITranslation(player,
                    runeDelta ? "ui.lore_socket.materials_runes" : "ui.lore_socket.materials_gems"));
            commands.set("#SupportMaterialsTitle.Text", LangLoader.getUITranslation(player, "ui.lore_socket.materials_supports"));
            commands.set("#SupportFooter.Text", LangLoader.getUITranslation(player, "ui.lore_socket.materials_list_hint"));
            commands.set("#LoreSocketPreviewTitle.Text", LangLoader.getUITranslation(player, "ui.lore_socket.sockets"));
            commands.set("#LoreProgressTitle.Text", LangLoader.getUITranslation(player, "ui.lore_socket.progress_title"));
            commands.set("#LoreStatusTitle.Text", LangLoader.getUITranslation(player, "ui.lore_socket.status"));

            commands.set("#EquipmentEmpty.Text", LangLoader.getUITranslation(player, "ui.lore_socket.option_no_equipment"));
            commands.set("#EquipmentEmpty.Visible", snapshot.equipments.isEmpty());
            commands.set("#SelectedEquipmentName.Text", equipment == null
                    ? LangLoader.getUITranslation(player, "ui.lore_socket.option_no_equipment") : equipment.displayName);
            commands.set("#SelectedEquipmentLocation.Text", buildLocationText(player, equipment));
            commands.set("#SelectedEquipmentIcon.ItemId", equipment == null ? "" : equipment.itemId);
            commands.set("#SelectedEquipmentIcon.Visible", equipment != null);
            commands.set("#LoreSocketEmpty.Text", LangLoader.getUITranslation(player, "ui.lore_socket.option_no_sockets"));
            commands.set("#LoreSocketEmpty.Visible", !hasLoreSockets);
            commands.set("#LoreSocketPreviewSummary.Text", hasLoreSockets
                    ? buildSocketSummary(loreData) : "");
            commands.set("#LoreSelectedSlotLabel.Text", buildSelectedSlotLabel(player, equipment, state.slotKey));
            commands.set("#LoreStatus.Text", state.statusText == null
                    ? LangLoader.getUITranslation(player, "ui.lore_socket.status_idle") : state.statusText);
            commands.set("#LoreProcessButton.Text", resolveProcessButtonLabel(player, equipment, gem, support, state.slotKey));
            commands.set("#LoreProcessButton.Visible", canProcess);
            commands.set("#LoreUnavailable.Visible", !canProcess);
            commands.set("#LoreUnavailable.Text", equipment == null
                    ? LangLoader.getUITranslation(player, "ui.lore_socket.status_no_equipment")
                    : gem == null && support == null
                            ? LangLoader.getUITranslation(player, runeDelta
                                    ? "ui.lore_socket.option_no_rune" : "ui.lore_socket.option_no_gem")
                            : LangLoader.getUITranslation(player, "ui.lore_socket.slot_none_selected"));
            commands.set("#LoreGemEmpty.Text", LangLoader.getUITranslation(player,
                    runeDelta ? "ui.lore_socket.option_no_rune" : "ui.lore_socket.option_no_gem"));
            commands.set("#LoreGemEmpty.Visible", materials.isEmpty());

            events.addEventBinding(CustomUIEventBindingType.Activating, "#LoreProcessButton",
                    EventData.of("Action", "process"));
            if (replaceContents) {
                commands.clear("#EquipmentList");
                commands.clear("#LoreSocketTiles");
                commands.clear("#LoreGemList");
                commands.clear("#SupportList");
                commands.clear("#LoreProgressionNodes");
            }
            appendEquipmentCards(commands, events, player, equipment);
            appendLoreSocketTiles(commands, events, player, equipment, loreData);
            appendLoreProgression(commands, events, player, selectedSocket, runeDelta);
            appendGemCards(commands, events, materials, gem);
            appendSupportCards(commands, events, player, support);
        }

        private void appendEquipmentCards(UICommandBuilder commands, UIEventBuilder events,
                                          Player player, Entry selected) {
            for (int i = 0; i < snapshot.equipments.size(); i++) {
                Entry entry = snapshot.equipments.get(i);
                if (entry == null || entry.item == null || entry.item.isEmpty()) continue;
                commands.append("#EquipmentList", EQUIPMENT_CARD_UI);
                String base = child("#EquipmentList", i);
                commands.set(base + " #EquipmentIcon.ItemId", entry.itemId);
                commands.set(base + " #EquipmentName.Text", entry.displayName);
                commands.set(base + " #EquipmentSocketsCaption.Text",
                        LangLoader.getUITranslation(player, "tooltip.sockets_label"));
                commands.set(base + " #EquipmentResonantEssence.Visible", false);
                commands.set(base + " #EquipmentSelected.Visible", selected == entry);
                events.addEventBinding(CustomUIEventBindingType.Activating, base + " #EquipmentCardHit",
                        EventData.of("Action", "select_equipment").append("Target", String.valueOf(i)));

                SocketData socketData = SocketManager.getSocketData(entry.item);
                List<Socket> sockets = socketData == null ? List.of() : socketData.getSockets();
                int punched = socketData == null ? 0 : socketData.getCurrentSocketCount();
                for (int slot = 0; slot < 5; slot++) {
                    String slotBase = base + " #EquipmentSocketSlot" + slot;
                    boolean visible = slot < sockets.size();
                    commands.set(slotBase + ".Visible", visible);
                    if (!visible) continue;
                    Socket socket = sockets.get(slot);
                    commands.setObject(base + " #EquipmentSocketVisual" + slot + ".Background",
                            textureBackground(UISocketVisualUtils.socketBackgroundIcon(slot < punched,
                                    socket != null && socket.isBroken(), UISocketVisualUtils.brokenSocketIconName())));
                    String essenceIcon = socket == null || socket.isEmpty() || socket.isBroken()
                            ? null : UISocketVisualUtils.essenceItemId(socket.getEssenceId());
                    commands.set(base + " #EquipmentSocketEssence" + slot + ".ItemId",
                            essenceIcon == null ? "" : essenceIcon);
                    commands.set(base + " #EquipmentSocketEssence" + slot + ".Visible", essenceIcon != null);
                    events.addEventBinding(CustomUIEventBindingType.Activating,
                            base + " #EquipmentSocketHit" + slot,
                            EventData.of("Action", "select_equipment").append("Target", String.valueOf(i)));
                }

                LoreSocketData cardLoreData = LoreSocketManager.getLoreSocketData(entry.item);
                List<LoreSocketData.LoreSocket> loreSockets = cardLoreData == null
                        ? List.of() : cardLoreData.getSockets();
                for (int slot = 0; slot < 3; slot++) {
                    String slotBase = base + " #EquipmentLoreSlot" + slot;
                    boolean visible = slot < loreSockets.size();
                    commands.set(slotBase + ".Visible", visible);
                    if (!visible) continue;
                    LoreSocketData.LoreSocket socket = loreSockets.get(slot);
                    commands.setObject(base + " #EquipmentLoreVisual" + slot + ".Background",
                            textureBackground("GemSlotEmpty.png"));
                    String colorKey = UISocketVisualUtils.loreColorKey(socket);
                    for (String color : LORE_SOCKET_COLOR_KEYS) {
                        String colorName = Character.toUpperCase(color.charAt(0)) + color.substring(1);
                        commands.set(base + " #EquipmentLoreColor" + colorName + slot + ".Visible",
                                color.equals(colorKey));
                    }
                    String icon = UISocketVisualUtils.loreSocketItemId(socket);
                    commands.set(base + " #EquipmentLoreIcon" + slot + ".ItemId", icon == null ? "" : icon);
                    commands.set(base + " #EquipmentLoreIcon" + slot + ".Visible", icon != null);
                    events.addEventBinding(CustomUIEventBindingType.Activating,
                            base + " #EquipmentLoreHit" + slot,
                            EventData.of("Action", "select_equipment_slot")
                                    .append("Target", i + ":" + slot));
                }
            }
            commands.set("#EquipmentList.ContentHeight", snapshot.equipments.size() * 72 + 12);
        }

        private void appendLoreSocketTiles(UICommandBuilder commands, UIEventBuilder events,
                                           Player player, Entry equipment, LoreSocketData data) {
            List<LoreSocketData.LoreSocket> sockets = data == null ? List.of() : data.getSockets();
            String selectedSlot = state.slotKey;
            for (int i = 0; i < sockets.size(); i++) {
                LoreSocketData.LoreSocket socket = sockets.get(i);
                commands.append("#LoreSocketTiles", LORE_SOCKET_TILE_UI);
                String base = child("#LoreSocketTiles", i);
                String icon = UISocketVisualUtils.loreSocketItemId(socket);
                commands.set(base + " #LoreSocketIcon.ItemId", icon == null ? "" : icon);
                commands.set(base + " #LoreSocketIcon.Visible", icon != null);
                String colorKey = UISocketVisualUtils.loreColorKey(socket);
                for (String color : LORE_SOCKET_COLOR_KEYS) {
                    String colorName = Character.toUpperCase(color.charAt(0)) + color.substring(1);
                    commands.set(base + " #LoreSocketColor" + colorName + ".Visible",
                            color.equals(colorKey));
                }
                boolean selected = String.valueOf(i).equals(selectedSlot);
                for (String color : LORE_SOCKET_COLOR_KEYS) {
                    String colorName = Character.toUpperCase(color.charAt(0)) + color.substring(1);
                    commands.set(base + " #LoreSocketSelectionColor" + colorName + ".Visible",
                            selected && color.equals(colorKey));
                }
                boolean feedReady = socket != null && socket.hasSpirit() && LoreSocketManager.needsFeed(socket);
                commands.set(base + " #LoreSocketFeed.Visible", feedReady);
                commands.set(base + " #LoreSocketFeed.Text",
                        LangLoader.getUITranslation(player, "ui.lore_socket.feed_button"));
                commands.set(base + " #LoreSocketHit.TooltipText",
                        buildNativeSlotTooltip(player, socket, isRuneDeltaEquipment(equipment)));
                events.addEventBinding(CustomUIEventBindingType.Activating, base + " #LoreSocketHit",
                        EventData.of("Action", "select_slot").append("Target", String.valueOf(i)));
                if (feedReady) {
                    events.addEventBinding(CustomUIEventBindingType.Activating, base + " #LoreSocketFeed",
                            EventData.of("Action", "feed_slot").append("Target", String.valueOf(i)));
                }
            }
            commands.set("#LoreSocketTiles.ContentWidth", sockets.size() * 108);
        }

        private static LoreSocketData.LoreSocket selectedSocket(LoreSocketData data, String slotKey) {
            int index = parseIndex(slotKey, -1);
            return data != null && index >= 0 && index < data.getSocketCount() ? data.getSocket(index) : null;
        }

        private static String buildNativeSlotTooltip(Player player, LoreSocketData.LoreSocket socket,
                                                     boolean runeDelta) {
            List<DetailRow> rows = buildNativeSlotDetailRows(player, socket, runeDelta);
            StringBuilder tooltip = new StringBuilder();
            for (DetailRow row : rows) {
                if (tooltip.length() > 0) tooltip.append('\n');
                if (!row.label.isBlank()) tooltip.append(row.label).append(": ");
                tooltip.append(row.value);
            }
            return tooltip.toString();
        }

        private void appendLoreProgression(UICommandBuilder commands, UIEventBuilder events, Player player,
                                           LoreSocketData.LoreSocket socket, boolean runeDelta) {
            commands.set("#LoreProgressHover.Visible", false);
            String emptyText;
            if (socket == null) {
                emptyText = LangLoader.getUITranslation(player, "ui.lore_socket.progress_empty_select");
            } else if (runeDelta || !socket.hasSpirit()) {
                emptyText = LangLoader.getUITranslation(player, "ui.lore_socket.progress_empty_no_spirit");
            } else {
                emptyText = "";
            }
            boolean showProgress = socket != null && socket.hasSpirit() && !runeDelta;
            commands.set("#LoreProgressEmpty.Text", emptyText);
            commands.set("#LoreProgressEmpty.Visible", !showProgress);
            commands.set("#LoreProgressSummary.Text", showProgress
                    ? buildLoreProgressSummary(player, socket) : "");
            if (!showProgress) {
                commands.set("#LoreProgressTrackFill.Visible", false);
                commands.setObject("#LoreProgressTrackFill.Anchor", loreProgressTrackAnchor(0));
                return;
            }

            List<ProgressMilestone> milestones = buildProgressMilestones();
            int maxLevel = Math.max(1, LoreSocketManager.getConfig().getMaxLevel());
            int progressWidth = (int) Math.round(LORE_PROGRESS_TRACK_WIDTH
                    * loreProgressTrackFraction(socket, milestones, maxLevel));
            commands.setObject("#LoreProgressTrackFill.Anchor", loreProgressTrackAnchor(progressWidth));
            commands.set("#LoreProgressTrackFill.Visible", progressWidth > 0);
            for (int i = 0; i < milestones.size(); i++) {
                ProgressMilestone milestone = milestones.get(i);
                commands.append("#LoreProgressionNodes", LORE_PROGRESS_NODE_UI);
                String base = child("#LoreProgressionNodes", i);
                commands.set(base + " #LoreProgressNodeLabel.Text",
                        LangLoader.getUITranslation(player, "ui.lore_feed.level_label", milestone.level));
                String rewardItemId = progressionRuneItemId(socket, milestone);
                commands.set(base + " #LoreProgressNodeFrame.Visible", rewardItemId != null);
                if (rewardItemId != null) {
                    commands.setObject(base + " #LoreProgressNodeFrame.Background",
                            textureBackground("SkillTreeIcons/SkillTreeNode_"
                                    + LORE_PROGRESS_NODE_FRAMES[i % LORE_PROGRESS_NODE_FRAMES.length] + ".png"));
                }
                commands.set(base + " #LoreProgressRuneIcon.ItemId", rewardItemId == null ? "" : rewardItemId);
                commands.set(base + " #LoreProgressRuneIcon.Visible", rewardItemId != null);
                commands.set(base + " #LoreProgressNodeCurrent.Visible",
                        rewardItemId != null && isCurrentProgressMilestone(socket, milestone, maxLevel));
                String inspectRuneItemId = progressionRuneTooltipItemId(socket, milestone, rewardItemId);
                commands.set(base + " #LoreProgressNodeHit.TooltipText",
                        buildProgressMilestoneTooltip(player, socket, milestone, inspectRuneItemId, maxLevel));
                events.addEventBinding(CustomUIEventBindingType.MouseEntered,
                        base + " #LoreProgressNodeHit",
                        EventData.of("Action", "progress_node_hover").append("Target", String.valueOf(i)), false);
                events.addEventBinding(CustomUIEventBindingType.MouseExited,
                        base + " #LoreProgressNodeHit",
                        EventData.of("Action", "progress_node_exit"), false);
            }
        }

        private void showProgressNodeTooltip(Player player, String target) {
            int index = parseIndex(target, -1);
            List<ProgressMilestone> milestones = buildProgressMilestones();
            if (index < 0 || index >= milestones.size()) return;
            Entry equipment = resolveSelection(snapshot.equipments, state.equipmentKey);
            ItemStack item = equipment == null ? null : getEffectiveEquipmentItem(player, equipment);
            LoreSocketData data = item == null ? null : LoreSocketManager.getLoreSocketData(item);
            LoreSocketData.LoreSocket socket = selectedSocket(data, state.slotKey);
            if (socket == null || !socket.hasSpirit()) return;

            ProgressMilestone milestone = milestones.get(index);
            String visibleRuneItemId = progressionRuneItemId(socket, milestone);
            String inspectRuneItemId = progressionRuneTooltipItemId(socket, milestone, visibleRuneItemId);
            int maxLevel = Math.max(1, LoreSocketManager.getConfig().getMaxLevel());
            UICommandBuilder commands = new UICommandBuilder();
            commands.set("#LoreProgressHoverText.Text",
                    buildProgressMilestoneTooltip(player, socket, milestone, inspectRuneItemId, maxLevel));
            commands.set("#LoreProgressHover.Visible", true);
            sendUpdate(commands, false);
        }

        private static List<ProgressMilestone> buildProgressMilestones() {
            int maxLevel = Math.max(1, LoreSocketManager.getConfig().getMaxLevel());
            int interval = Math.max(1, LoreSocketManager.getConfig().getFeedInterval());
            List<ProgressMilestone> milestones = new ArrayList<>();
            milestones.add(new ProgressMilestone(1, 0, false, maxLevel == 1));
            for (int tier = 1; tier <= LoreSocketManager.getMaxRuneFeedThresholds(); tier++) {
                int level = tier * interval;
                if (level >= maxLevel) break;
                milestones.add(new ProgressMilestone(level, tier, true, false));
            }
            if (maxLevel > 1) {
                if (milestones.get(milestones.size() - 1).level == maxLevel) {
                    ProgressMilestone last = milestones.remove(milestones.size() - 1);
                    milestones.add(new ProgressMilestone(maxLevel, last.feedTier, last.feedGate, true));
                } else {
                    milestones.add(new ProgressMilestone(maxLevel,
                            LoreSocketManager.getMaxRuneFeedThresholds(), false, true));
                }
            }
            return milestones;
        }

        private static String buildLoreProgressSummary(Player player, LoreSocketData.LoreSocket socket) {
            int level = Math.max(1, socket.getLevel());
            int maxLevel = Math.max(1, LoreSocketManager.getConfig().getMaxLevel());
            int interval = Math.max(1, LoreSocketManager.getConfig().getFeedInterval());
            if (level >= maxLevel) {
                return LangLoader.getUITranslation(player, "ui.lore_socket.progress_summary_max", level);
            }
            if (LoreSocketManager.needsFeed(socket)) {
                return LangLoader.getUITranslation(player, "ui.lore_socket.progress_summary_gate", level)
                        + " | " + feedCostSummary(player, socket);
            }

            int nextGate = interval * (Math.max(0, socket.getFeedTier()) + 1);
            if (nextGate >= maxLevel) {
                long missing = xpMissingToLevel(socket, maxLevel);
                return LangLoader.getUITranslation(player, "ui.lore_socket.progress_summary_max_xp",
                        level, Math.max(0, socket.getXp()), LoreSocketManager.getXpRequiredForLevel(level), missing, maxLevel);
            }
            long missing = xpMissingToLevel(socket, nextGate);
            return LangLoader.getUITranslation(player, "ui.lore_socket.progress_summary_gate_xp",
                    level, Math.max(0, socket.getXp()), LoreSocketManager.getXpRequiredForLevel(level), missing, nextGate)
                    + " | " + feedCostSummary(player, socket);
        }

        private static String feedCostSummary(Player player, LoreSocketData.LoreSocket socket) {
            return LangLoader.getUITranslation(player, "ui.lore_socket.progress_summary_feed_cost",
                    LoreSocketManager.getFeedCost(socket));
        }

        private static long xpMissingToLevel(LoreSocketData.LoreSocket socket, int targetLevel) {
            int currentLevel = Math.max(1, socket.getLevel());
            if (targetLevel <= currentLevel) return 0L;
            long needed = -Math.max(0, socket.getXp());
            for (int level = currentLevel; level < targetLevel; level++) {
                needed += Math.max(1, LoreSocketManager.getXpRequiredForLevel(level));
            }
            return Math.max(0L, needed);
        }

        private static double loreProgressTrackFraction(LoreSocketData.LoreSocket socket,
                                                        List<ProgressMilestone> milestones,
                                                        int maxLevel) {
            if (socket == null || milestones == null || milestones.isEmpty()) return 0.0d;
            int lastMilestoneIndex = milestones.size() - 1;
            if (milestones.size() == 1) return progressTrackMilestoneFraction(0, 1);

            int level = Math.max(1, Math.min(maxLevel, socket.getLevel()));
            if (level >= maxLevel) {
                return progressTrackMilestoneFraction(lastMilestoneIndex, milestones.size());
            }
            double progressLevel = level;
            if (!LoreSocketManager.needsFeed(socket)) {
                int xpRequired = Math.max(1, LoreSocketManager.getXpRequiredForLevel(level));
                double levelProgress = Math.max(0.0d, Math.min(1.0d,
                        socket.getXp() / (double) xpRequired));
                progressLevel += levelProgress;
            }

            for (int i = 0; i < milestones.size() - 1; i++) {
                int startLevel = milestones.get(i).level;
                int endLevel = milestones.get(i + 1).level;
                if (progressLevel > endLevel) continue;
                double span = Math.max(1, endLevel - startLevel);
                double segmentProgress = Math.max(0.0d, Math.min(1.0d,
                        (progressLevel - startLevel) / span));
                double startFraction = progressTrackMilestoneFraction(i, milestones.size());
                double endFraction = progressTrackMilestoneFraction(i + 1, milestones.size());
                return Math.max(0.0d, Math.min(1.0d,
                        startFraction + (endFraction - startFraction) * segmentProgress));
            }
            return progressTrackMilestoneFraction(lastMilestoneIndex, milestones.size());
        }

        private static double progressTrackMilestoneFraction(int index, int milestoneCount) {
            double nodeCenter = LORE_PROGRESS_CANVAS_WIDTH
                    * ((index + 0.5d) / Math.max(1, milestoneCount));
            return Math.max(0.0d, Math.min(1.0d,
                    (nodeCenter - LORE_PROGRESS_TRACK_LEFT) / LORE_PROGRESS_TRACK_WIDTH));
        }

        private static Anchor loreProgressTrackAnchor(int width) {
            Anchor anchor = new Anchor();
            anchor.setLeft(Value.of(LORE_PROGRESS_TRACK_LEFT));
            anchor.setTop(Value.of(188));
            anchor.setWidth(Value.of(Math.max(0, Math.min(LORE_PROGRESS_TRACK_WIDTH, width))));
            anchor.setHeight(Value.of(3));
            return anchor;
        }

        private static boolean isCurrentProgressMilestone(LoreSocketData.LoreSocket socket,
                                                          ProgressMilestone milestone, int maxLevel) {
            int level = Math.max(1, socket.getLevel());
            if (level >= maxLevel) return milestone.maxLevel;
            if (milestone.level == 1) return false;
            if (milestone.feedGate) return milestone.feedTier == Math.max(0, socket.getFeedTier()) + 1;
            return milestone.maxLevel && milestone.feedTier <= socket.getFeedTier();
        }

        private static String buildProgressMilestoneTooltip(Player player, LoreSocketData.LoreSocket socket,
                                                           ProgressMilestone milestone, String rewardItemId,
                                                           int maxLevel) {
            StringBuilder tooltip = new StringBuilder();
            if (milestone.feedGate) {
                int completedTier = Math.max(0, socket.getFeedTier());
                if (completedTier >= milestone.feedTier) {
                    tooltip.append(LangLoader.getUITranslation(player,
                            "ui.lore_socket.progress_node_gate_passed", milestone.level));
                } else if (Math.max(1, socket.getLevel()) >= milestone.level) {
                    tooltip.append(LangLoader.getUITranslation(player,
                            "ui.lore_socket.progress_node_gate_ready", milestone.level));
                } else {
                    tooltip.append(LangLoader.getUITranslation(player,
                            "ui.lore_socket.progress_node_xp", xpMissingToLevel(socket, milestone.level), milestone.level));
                }
            } else if (milestone.maxLevel) {
                if (Math.max(1, socket.getLevel()) >= maxLevel) {
                    tooltip.append(LangLoader.getUITranslation(player, "ui.lore_socket.progress_node_max_reached"));
                } else {
                    tooltip.append(LangLoader.getUITranslation(player,
                            "ui.lore_socket.progress_node_max_xp", xpMissingToLevel(socket, maxLevel), maxLevel));
                }
            } else if (milestone.level == 1) {
                int interval = Math.max(1, LoreSocketManager.getConfig().getFeedInterval());
                int nextGate = Math.min(maxLevel, interval * (Math.max(0, socket.getFeedTier()) + 1));
                if (LoreSocketManager.needsFeed(socket)) {
                    tooltip.append(LangLoader.getUITranslation(player,
                            "ui.lore_socket.progress_node_gate_ready", Math.max(1, socket.getLevel())));
                } else {
                    tooltip.append(LangLoader.getUITranslation(player,
                            nextGate >= maxLevel ? "ui.lore_socket.progress_node_max_xp"
                                    : "ui.lore_socket.progress_node_xp",
                            xpMissingToLevel(socket, nextGate), nextGate));
                }
            } else if (Math.max(1, socket.getLevel()) >= milestone.level) {
                tooltip.append(LangLoader.getUITranslation(player,
                        "ui.lore_socket.progress_node_level_reached", milestone.level));
            } else {
                int nextGate = Math.min(maxLevel,
                        Math.max(1, LoreSocketManager.getConfig().getFeedInterval())
                                * (Math.max(0, socket.getFeedTier()) + 1));
                tooltip.append(LangLoader.getUITranslation(player,
                        "ui.lore_socket.progress_node_xp", xpMissingToLevel(socket, nextGate), nextGate));
            }
            if (rewardItemId != null) {
                String rewardLabelKey = isFeedGateRuneStage(socket, milestone)
                        ? "ui.lore_socket.progress_node_rune_stage"
                        : "ui.lore_socket.progress_node_reward";
                tooltip.append('\n').append(LangLoader.getUITranslation(player,
                        rewardLabelKey,
                        UIItemUtils.displayNameOrItemId(new ItemStack(rewardItemId, 1), player)));
                String descriptionKey = "irai.items." + rewardItemId + ".description";
                String description = LangLoader.getUITranslation(player, descriptionKey);
                if (description != null && !description.isBlank() && !description.equals(descriptionKey)) {
                    tooltip.append('\n').append(description);
                }
            }
            return tooltip.toString();
        }

        private static boolean isFeedGateRuneStage(LoreSocketData.LoreSocket socket,
                                                   ProgressMilestone milestone) {
            return socket != null && milestone.feedGate && !milestone.maxLevel
                    && (LoreSocketManager.isWolfPackSpirit(socket.getSpiritId())
                    || LoreSocketManager.isOmnislashSpirit(socket.getSpiritId())
                    || LoreSocketManager.isCoreAbilityRunestoneSpirit(socket.getSpiritId()));
        }

        private static String progressionRuneItemId(LoreSocketData.LoreSocket socket,
                                                    ProgressMilestone milestone) {
            String spiritId = socket.getSpiritId();
            if (milestone.feedGate && milestone.feedTier >= 1
                    && LoreSocketManager.isCoreAbilityRunestoneSpirit(spiritId)) {
                String runeItemId = LoreSocketManager.resolveCoreAbilityRunestoneItemId(
                        spiritId, milestone.feedTier);
                String previousRuneItemId = milestone.feedTier <= 1 ? null
                        : LoreSocketManager.resolveCoreAbilityRunestoneItemId(spiritId, milestone.feedTier - 1);
                return runeItemId != null && (milestone.feedTier == 1 || !runeItemId.equals(previousRuneItemId))
                        ? runeItemId : null;
            }
            if (milestone.feedGate && milestone.feedTier >= 1
                    && LoreSocketManager.isWolfPackSpirit(spiritId)) {
                String runeItemId = LoreSocketManager.resolveWolfPackRuneItemId(milestone.feedTier);
                String previousRuneItemId = milestone.feedTier <= 1 ? null
                        : LoreSocketManager.resolveWolfPackRuneItemId(milestone.feedTier - 1);
                return !runeItemId.equals(previousRuneItemId) ? runeItemId : null;
            }
            if (milestone.feedGate && milestone.feedTier >= 1
                    && LoreSocketManager.isOmnislashSpirit(spiritId)) {
                String runeItemId = LoreSocketManager.resolveOmnislashRuneItemId(
                        milestone.level, milestone.feedTier);
                String previousRuneItemId = milestone.feedTier <= 1 ? null
                        : LoreSocketManager.resolveOmnislashRuneItemId(
                                milestone.level, milestone.feedTier - 1);
                return !runeItemId.equals(previousRuneItemId) ? runeItemId : null;
            }
            if (!milestone.maxLevel) return null;
            int feedTier = Math.max(0, milestone.feedTier);
            if (LoreSocketManager.isModifierRunestoneSpirit(spiritId)) {
                return LoreSocketManager.resolveModifierRunestoneItemId(spiritId);
            }
            if (LoreSocketManager.isWolfPackSpirit(spiritId) && feedTier >= 1) {
                return LoreSocketManager.resolveWolfPackRuneItemId(feedTier);
            }
            if (LoreSocketManager.isOmnislashSpirit(spiritId)) return LoreSocketManager.OMNISLASH_RUNE_12_ITEM_ID;
            if (LoreSocketManager.isOctaslashSpirit(spiritId)) return LoreSocketManager.OCTASLASH_RUNE_ITEM_ID;
            if (LoreSocketManager.isBloodRushSpirit(spiritId)) return LoreSocketManager.BLOOD_RUSH_RUNE_ITEM_ID;
            if (LoreSocketManager.isPummelSpirit(spiritId)) return LoreSocketManager.PUMMEL_RUNE_MAX_TIER_ITEM_ID;
            return null;
        }

        private static String progressionRuneTooltipItemId(LoreSocketData.LoreSocket socket,
                                                            ProgressMilestone milestone,
                                                            String visibleRuneItemId) {
            if (socket == null || milestone == null) return visibleRuneItemId;
            if (milestone.feedGate && milestone.feedTier >= 1) {
                String spiritId = socket.getSpiritId();
                if (LoreSocketManager.isCoreAbilityRunestoneSpirit(spiritId)) {
                    return LoreSocketManager.resolveCoreAbilityRunestoneItemId(spiritId, milestone.feedTier);
                }
                if (LoreSocketManager.isWolfPackSpirit(spiritId)) {
                    return LoreSocketManager.resolveWolfPackRuneItemId(milestone.feedTier);
                }
                if (LoreSocketManager.isOmnislashSpirit(spiritId)) {
                    return LoreSocketManager.resolveOmnislashRuneItemId(milestone.level, milestone.feedTier);
                }
            }
            return visibleRuneItemId;
        }

        private static List<DetailRow> buildNativeSlotDetailRows(Player player,
                                                                 LoreSocketData.LoreSocket socket,
                                                                 boolean runeDelta) {
            List<DetailRow> rows = new ArrayList<>();
            if (socket == null) {
                rows.add(new DetailRow("", LangLoader.getUITranslation(player, "ui.lore_socket.detail_click_slot")));
                return rows;
            }

            rows.add(new DetailRow(LangLoader.getUITranslation(player, "ui.lore_socket.detail_slot_label"),
                    String.valueOf(socket.getSlotIndex() + 1)));
            rows.add(new DetailRow(LangLoader.getUITranslation(player, "ui.lore_socket.detail_color_label"),
                    displaySocketColor(player, socket, runeDelta)));

            if (socket.hasRune()) {
                rows.add(new DetailRow(LangLoader.getUITranslation(player, "ui.lore_socket.materials_runes"),
                        formatRunestoneNameWithLevel(player, socket)));
                if (socket.getRuneDamageFeeds() > 0) {
                    rows.add(new DetailRow(LangLoader.getUITranslation(player,
                            "ui.lore_socket.detail_rune_damage_label"),
                            "+" + LoreSocketManager.getRuneDamageBonusPercent(socket.getRuneDamageFeeds()) + "%"));
                }
                if (socket.getRuneManaFeeds() > 0) {
                    rows.add(new DetailRow(LangLoader.getUITranslation(player,
                            "ui.lore_socket.detail_rune_mana_label"),
                            "-" + LoreSocketManager.getRuneManaCostReductionPercent(socket.getRuneManaFeeds()) + "%"));
                }
                return rows;
            }

            if (!socket.hasSpirit()) {
                if (runeDelta) {
                    rows.add(new DetailRow(LangLoader.getUITranslation(player, "ui.lore_socket.materials_runes"),
                            LangLoader.getUITranslation(player, "ui.lore_socket.detail_empty_rune")));
                    rows.add(new DetailRow("", LangLoader.getUITranslation(player,
                            "ui.lore_socket.detail_eligible_runes")));
                } else {
                    rows.add(new DetailRow(LangLoader.getUITranslation(player, "ui.lore_socket.detail_empty_section"),
                            LangLoader.getUITranslation(player, "ui.lore_socket.detail_empty")));
                    List<String> names = buildCompatibleSpiritNames(player,
                            displaySocketColor(player, socket, false));
                    rows.add(new DetailRow(LangLoader.getUITranslation(player, "ui.lore_socket.detail_eligible"),
                            names.isEmpty() ? LangLoader.getUITranslation(player, "ui.lore_feed.value_none")
                                    : String.join(", ", names)));
                }
                return rows;
            }

            rows.add(new DetailRow(LangLoader.getUITranslation(player, "ui.lore_socket.detail_spirit_label"),
                    localizeSpiritName(player, socket.getSpiritId())));
            int level = Math.max(1, socket.getLevel());
            int needed = Math.max(1, LoreSocketManager.getXpRequiredForLevel(level));
            rows.add(new DetailRow(LangLoader.getUITranslation(player, "ui.lore_socket.detail_level_label"),
                    LangLoader.getUITranslation(player, "ui.lore_feed.level_label", level)
                            + " | " + LangLoader.getUITranslation(player, "ui.lore_feed.feed_tier",
                                    Math.max(0, socket.getFeedTier()))));
            rows.add(new DetailRow(LangLoader.getUITranslation(player, "ui.lore_socket.detail_xp_label"),
                    LangLoader.getUITranslation(player, "ui.lore_feed.progress_xp",
                            Math.max(0, socket.getXp()), needed)));
            rows.add(new DetailRow(LangLoader.getUITranslation(player, "ui.lore_socket.detail_feed_label"),
                    LoreSocketManager.needsFeed(socket)
                            ? LangLoader.getUITranslation(player, "ui.lore_feed.value_yes")
                            : LangLoader.getUITranslation(player, "ui.lore_feed.value_no")));

            LoreAbility ability = LoreAbilityRegistry.getAbility(socket.getSpiritId());
            if (ability != null) {
                String langCode = resolveLangCode(player);
                rows.add(new DetailRow(LangLoader.getUITranslation(player, "ui.lore_socket.detail_ability_label"),
                        ability.resolveAbilityName(langCode)));
                String effect = ability.describeEffectOnly(langCode, level, Math.max(0, socket.getFeedTier()));
                if (effect != null && !effect.isBlank()) {
                    rows.add(new DetailRow(LangLoader.getUITranslation(player, "ui.lore_socket.detail_effect_label"), effect));
                }
                if (LoreSocketManager.isOmnislashSpirit(socket.getSpiritId())) {
                    String runeItemId = LoreSocketManager.resolveOmnislashRuneItemId(level, socket.getFeedTier());
                    rows.add(new DetailRow(LangLoader.getUITranslation(player, "ui.lore_socket.omnislash_rune_stage"),
                            LangLoader.getUITranslation(player, "irai.items." + runeItemId + ".name")));
                }
                if (LoreSocketManager.isWolfPackSpirit(socket.getSpiritId())) {
                    String runeItemId = LoreSocketManager.resolveWolfPackRuneItemId(socket.getFeedTier());
                    rows.add(new DetailRow(LangLoader.getUITranslation(player, "ui.lore_socket.wolfpack_rune_stage"),
                            LangLoader.getUITranslation(player, "irai.items." + runeItemId + ".name")));
                }
                if (LoreSoulstoneUtils.isInfusedSoulstoneId(socket.getGemItemId())) {
                    rows.add(new DetailRow(LangLoader.getUITranslation(player, "ui.lore_socket.detail_note_label"),
                            LangLoader.getUITranslation(player, "ui.lore_socket.infused_soulstone_hit_roll")));
                }
            }
            return rows;
        }

        private void appendGemCards(UICommandBuilder commands, UIEventBuilder events,
                                    List<Entry> materials, Entry selected) {
            for (int i = 0; i < materials.size(); i++) {
                Entry entry = materials.get(i);
                if (entry == null || entry.item == null || entry.item.isEmpty()) continue;
                commands.append("#LoreGemList", GEM_CARD_UI);
                String base = child("#LoreGemList", i);
                commands.set(base + " #EssenceIcon.ItemId", entry.itemId);
                commands.set(base + " #EssenceName.Text", entry.displayName);
                commands.set(base + " #EssenceQuantity.Text", "x" + entry.quantity);
                commands.set(base + " #EssenceSelected.Visible", selected == entry);
                events.addEventBinding(CustomUIEventBindingType.Activating, base + " #EssenceCardHit",
                        EventData.of("Action", "select_gem").append("Target", String.valueOf(i)));
            }
            commands.set("#LoreGemList.ContentHeight", 10 + materials.size() * 58);
        }

        private void appendSupportCards(UICommandBuilder commands, UIEventBuilder events,
                                        Player player, Entry selected) {
            commands.append("#SupportList", SUPPORT_CARD_UI);
            commands.set("#SupportList[0] #SupportIcon.Visible", false);
            commands.set("#SupportList[0] #SupportName.Text",
                    LangLoader.getUITranslation(player, "ui.lore_socket.option_none"));
            commands.set("#SupportList[0] #SupportQuantity.Text", "");
            commands.set("#SupportList[0] #SupportCardHit.TooltipText", "");
            commands.set("#SupportList[0] #SupportSelected.Visible", selected == null);
            events.addEventBinding(CustomUIEventBindingType.Activating, "#SupportList[0] #SupportCardHit",
                    EventData.of("Action", "select_support").append("Target", ""));
            for (int i = 0; i < snapshot.supports.size(); i++) {
                Entry entry = snapshot.supports.get(i);
                if (entry == null || entry.item == null || entry.item.isEmpty()) continue;
                commands.append("#SupportList", SUPPORT_CARD_UI);
                String base = child("#SupportList", i + 1);
                commands.set(base + " #SupportIcon.ItemId", entry.itemId);
                commands.set(base + " #SupportName.Text", entry.displayName);
                commands.set(base + " #SupportQuantity.Text", "Held: " + entry.quantity);
                commands.set(base + " #SupportCardHit.TooltipText", buildMaterialDescription(player, entry, false));
                commands.set(base + " #SupportSelected.Visible", selected == entry);
                events.addEventBinding(CustomUIEventBindingType.Activating, base + " #SupportCardHit",
                        EventData.of("Action", "select_support").append("Target", String.valueOf(i)));
            }
            commands.set("#SupportList.ContentHeight", 10 + (snapshot.supports.size() + 1) * 58);
        }

        private static String buildSocketSummary(LoreSocketData data) {
            if (data == null) return "";
            int filled = 0;
            for (int i = 0; i < data.getSocketCount(); i++) {
                LoreSocketData.LoreSocket socket = data.getSocket(i);
                if (socket != null && !socket.isEmpty()) filled++;
            }
            return filled + " / " + data.getSocketCount() + " filled";
        }

        private static int parseIndex(String value, int fallback) {
            try { return Integer.parseInt(value); } catch (Exception ignored) { return fallback; }
        }

        public static final class PageEvent {
            public static final BuilderCodec<PageEvent> CODEC = BuilderCodec.<PageEvent>builder(
                            PageEvent.class, PageEvent::new)
                    .append(new KeyedCodec<>("Action", Codec.STRING),
                            (event, value) -> event.action = value, event -> event.action).add()
                    .append(new KeyedCodec<>("Target", Codec.STRING),
                            (event, value) -> event.target = value, event -> event.target).add()
                    .build();
            public String action;
            public String target;
            public PageEvent() { }
        }
    }
}
