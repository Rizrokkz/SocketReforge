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

import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import irai.mod.reforge.Common.UI.HyUIReflectionUtils;
import irai.mod.reforge.Common.UI.UIHtmlUtils;
import irai.mod.reforge.Common.UI.UIItemUtils;
import irai.mod.reforge.Common.UI.UISocketVisualUtils;
import irai.mod.reforge.Common.UI.UITemplateUtils;
import irai.mod.reforge.Lore.LoreAbility;
import irai.mod.reforge.Lore.LoreAbilityRegistry;
import irai.mod.reforge.Lore.LoreGemRegistry;
import irai.mod.reforge.Lore.LoreSocketData;
import irai.mod.reforge.Lore.LoreSocketManager;
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
    private static final String SUPPORT_REROLL_ITEM_ID = "Ingredient_Resonant_Essence";
    private static final String SUPPORT_CLEAR_ITEM_ID = "Ingredient_Ghastly_Essence";
    private static final int EQUIPMENT_CARDS_PER_PAGE = 4;
    private static final int MATERIAL_CARDS_PER_PAGE = 5;

    private static boolean hyuiAvailable = false;
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
        final List<Entry> supports;

        Snapshot(List<Entry> equipments, List<Entry> gems, List<Entry> supports) {
            this.equipments = equipments;
            this.gems = gems;
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
        hyuiAvailable = HyUIReflectionUtils.detectHyUi(HYUI_PAGE_BUILDER, HYUI_PLUGIN, "LoreSocketBenchUI");
    }

    public static boolean isAvailable() {
        return hyuiAvailable;
    }

    public static void open(Player player) {
        if (player == null) {
            return;
        }
        if (!hyuiAvailable) {
            player.getPlayerRef().sendMessage(Message.raw("<color=#FF5555>" + LangLoader.getUITranslation(player, "ui.lore_socket.hyui_missing")));
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
        List<Entry> supports = new ArrayList<>();
        collectFromContainer(player, player.getInventory().getHotbar(), ContainerKind.HOTBAR, equipments, gems, supports);
        collectFromContainer(player, player.getInventory().getStorage(), ContainerKind.STORAGE, equipments, gems, supports);
        return new Snapshot(equipments, gems, supports);
    }

    private static void collectFromContainer(Player player,
                                             ItemContainer container,
                                             ContainerKind kind,
                                             List<Entry> equipments,
                                             List<Entry> gems,
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
        if (itemId.equalsIgnoreCase(SUPPORT_REROLL_ITEM_ID)) {
            return true;
        }
        return matchesAnyId(itemId, LoreSocketManager.getConfig().getFeedItemIds())
                || matchesAnyId(itemId, LoreSocketManager.getConfig().getClearItemIds());
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
            Class<?> pageBuilderClass = Class.forName(HYUI_PAGE_BUILDER);
            Class<?> eventBindingClass = Class.forName(HYUI_EVENT_BINDING);

            Method pageForPlayer = pageBuilderClass.getMethod("pageForPlayer", PlayerRef.class);
            Method fromHtml = pageBuilderClass.getMethod("fromHtml", String.class);
            Method addListener = pageBuilderClass.getMethod("addEventListener", String.class, eventBindingClass, java.util.function.BiConsumer.class);
            Method withLifetime = pageBuilderClass.getMethod("withLifetime", CustomPageLifetime.class);
            Method openMethod = pageBuilderClass.getMethod("open", Class.forName("com.hypixel.hytale.component.Store"));

            Object activating = eventBindingClass.getField("Activating").get(null);

            String html = buildHtml(player, snapshot, selectionState);
            Object pageBuilder = pageForPlayer.invoke(null, playerRef);
            pageBuilder = fromHtml.invoke(pageBuilder, html);

            final Player finalPlayer = player;
            final Snapshot finalSnapshot = snapshot;

            registerEquipmentCardListeners(pageBuilder, addListener, activating, finalPlayer, finalSnapshot, selectionState);
            registerMaterialCardListeners(pageBuilder, addListener, activating, finalPlayer, finalSnapshot, selectionState);
            registerSocketPreviewListeners(pageBuilder, addListener, activating, finalPlayer, finalSnapshot, selectionState);

            addListener.invoke(pageBuilder, "processButton", activating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        String equipmentVal = selectionState != null ? selectionState.equipmentKey : null;
                        String gemVal = selectionState != null ? selectionState.gemKey : null;
                        String supportVal = selectionState != null ? selectionState.supportKey : null;
                        String slotVal = selectionState != null ? selectionState.slotKey : null;
                        Entry equipment = resolveSelection(finalSnapshot.equipments, equipmentVal);
                        Entry gem = resolveSelection(finalSnapshot.gems, gemVal);
                        Entry support = resolveSelection(finalSnapshot.supports, supportVal);

                        ProcessResult result = processSelection(finalPlayer, equipment, gem, support, slotVal);
                        pendingSelections.put(finalPlayer.getPlayerRef(),
                                stateWith(selectionState, equipmentVal, gemVal, supportVal, slotVal,
                                        result.status, null, null));
                        finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));
                    });

            Method onDismiss = pageBuilderClass.getMethod("onDismiss", java.util.function.BiConsumer.class);
            pageBuilder = onDismiss.invoke(pageBuilder,
                    (java.util.function.BiConsumer<Object, Object>) (pageObj, dismissedByPlayer) -> {
                        PlayerRef ref = finalPlayer.getPlayerRef();
                        Object current = openPages.get(ref);
                        if (current != pageObj) {
                            return;
                        }
                        openPages.remove(ref);
                        pendingNavToFeed.remove(ref);
                    });

            pageBuilder = withLifetime.invoke(pageBuilder, CustomPageLifetime.CanDismiss);
            Object store = getStore(playerRef);
            Object page = openMethod.invoke(pageBuilder, store);
            openPages.put(playerRef, page);
        } catch (Exception e) {
            System.err.println("[SocketReforge] LoreSocketBenchUI open error: " + e.getMessage());
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
        Entry selectedGem = findByKey(snapshot.gems, gemKey);
        Entry selectedSupport = findByKey(snapshot.supports, supportKey);

        String html = loadTemplate();
        html = html.replace("{{equipmentCards}}", buildEquipmentCardsHtml(player, snapshot, state));
        html = html.replace("{{materialsCards}}", buildMaterialsCardsHtml(player, snapshot, state));
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
        String icon = socket != null && !socket.isEmpty() ? resolveLoreGemOverlayIcon(socket) : null;
        sb.append("<div style=\"anchor-width:28; anchor-height:28; padding:2; background-color:").append(color).append(";\">")
                .append("<div style=\"anchor-width:24; anchor-height:24; background-image:url('GemSlotEmpty.png'); background-size:100% 100%;\">");
        if (icon != null) {
            sb.append("<img src=\"").append(icon).append("\" width=\"22\" height=\"22\"/>");
        }
        sb.append("</div></div>");
    }

    private static String buildEquipmentSocketSummary(Player player, LoreSocketData data) {
        if (data == null || data.getSocketCount() == 0) {
            return LangLoader.getUITranslation(player, "ui.lore_socket.metadata_no_sockets");
        }
        int filled = 0;
        int spirits = 0;
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
            if (socket.hasSpirit() && LoreSocketManager.needsFeed(socket)) {
                feedReady++;
            }
        }
        return LangLoader.getUITranslation(player, "ui.lore_socket.card_summary",
                data.getSocketCount(), filled, spirits, feedReady);
    }

    private static String buildMaterialsCardsHtml(Player player, Snapshot snapshot, SelectionState state) {
        StringBuilder sb = new StringBuilder();
        appendMaterialSectionHtml(player, sb, "ui.lore_socket.materials_gems", snapshot != null ? snapshot.gems : List.of(),
                state != null ? state.gemKey : null, state != null ? state.gemPage : 0, true);
        appendMaterialSectionHtml(player, sb, "ui.lore_socket.materials_supports", snapshot != null ? snapshot.supports : List.of(),
                state != null ? state.supportKey : null, state != null ? state.supportPage : 0, false);
        return sb.toString();
    }

    private static void appendMaterialSectionHtml(Player player,
                                                  StringBuilder sb,
                                                  String titleKey,
                                                  List<Entry> entries,
                                                  String selectedKey,
                                                  int page,
                                                  boolean gemSection) {
        sb.append("<div style=\"anchor-width:330; layout-mode:top; spacing:4;\">")
                .append("<p style=\"anchor-height:18; text-align:left; font-weight:bold;\">")
                .append(escapeHtml(LangLoader.getUITranslation(player, titleKey))).append("</p>")
                .append("<img src=\"divider.png\" style=\"anchor-width: 320; anchor-height: 3;\">");
        int totalCards = gemSection ? (entries == null ? 0 : entries.size()) : (entries == null ? 0 : entries.size()) + 1;
        if (gemSection && totalCards == 0) {
            String emptyKey = gemSection ? "ui.lore_socket.option_no_gem" : "ui.lore_socket.option_no_support";
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

        LoreSocketData data = LoreSocketManager.getLoreSocketData(equipment.item);
        if (data == null || data.getSocketCount() == 0) {
            sb.append("<option value=\"\" selected=\"true\">")
                    .append(escapeHtml(LangLoader.getUITranslation(player, "ui.lore_socket.option_no_sockets")))
                    .append("</option>");
            return sb.toString();
        }

        for (int i = 0; i < data.getSocketCount(); i++) {
            LoreSocketData.LoreSocket socket = data.getSocket(i);
            String key = String.valueOf(i);
            String label = buildSlotLabel(player, socket, i + 1, gemColor);
            sb.append("<option value=\"").append(key).append("\"");
            if (key.equals(selectedKey)) {
                sb.append(" selected=\"true\"");
            }
            sb.append(">").append(escapeHtml(label)).append("</option>");
        }
        return sb.toString();
    }

    private static String buildSlotLabel(Player player, LoreSocketData.LoreSocket socket, int index, String gemColor) {
        if (socket == null) {
            return LangLoader.getUITranslation(player, "ui.lore_socket.slot_unavailable", index);
        }
        String color = socket.getColor();
        String colorLabel = color == null || color.isBlank()
                ? LangLoader.getUITranslation(player, "ui.lore_socket.color_unknown")
                : color;
        String labelKey;
        if (socket.isLocked()) {
            labelKey = "ui.lore_socket.slot_locked";
        } else if (socket.isEmpty()) {
            labelKey = "ui.lore_socket.slot_empty";
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
        LoreSocketData data = LoreSocketManager.getLoreSocketData(equipment.item);
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
            boolean filled = socket != null && !socket.isEmpty();
            String colorHex = resolveLoreColorHex(socket);
            String overlayIcon = filled ? resolveLoreGemOverlayIcon(socket) : null;
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
                    .append("<div style=\"flex-weight:1;\"></div>")
                    .append(overlayIcon != null
                            ? "<img src=\"" + overlayIcon + "\" width=\"" + overlaySize + "\" height=\"" + overlaySize + "\"/>"
                            : "")
                    .append("<div style=\"flex-weight:1;\"></div>")
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
        return buildSlotLabel(player, socket, socket.getSlotIndex() + 1, null);
    }

    private static String buildSelectedSlotSummaryHtml(Player player, Entry equipment, String slotKey) {
        LoreSocketData.LoreSocket socket = resolveSelectedSocket(equipment, slotKey);
        if (socket == null) {
            return detailText(LangLoader.getUITranslation(player, "ui.lore_socket.detail_click_slot"));
        }
        return buildSlotDetailHtml(player, socket);
    }

    private static String buildSlotDetailHtml(Player player, LoreSocketData.LoreSocket socket) {
        if (socket == null) {
            return detailText(LangLoader.getUITranslation(player, "ui.lore_socket.status_no_socket"));
        }
        String color = socket.getColor() == null || socket.getColor().isBlank()
                ? LangLoader.getUITranslation(player, "ui.lore_socket.color_unknown")
                : socket.getColor();
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"anchor-width:860; layout-mode:top; spacing:5;\">")
                .append(detailSection(LangLoader.getUITranslation(player, "ui.lore_socket.detail_socket_section")))
                .append(detailRow(LangLoader.getUITranslation(player, "ui.lore_socket.detail_slot_label"),
                        String.valueOf(socket.getSlotIndex() + 1)))
                .append(detailRow(LangLoader.getUITranslation(player, "ui.lore_socket.detail_color_label"), color));

        if (socket.hasSpirit()) {
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
            }
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
            String color = LoreGemRegistry.resolveColor(entry.itemId);
            return color == null || color.isBlank()
                    ? LangLoader.getUITranslation(player, "ui.lore_socket.material_gem_desc")
                    : LangLoader.getUITranslation(player, "ui.lore_socket.material_gem_color_desc", color);
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
            return LangLoader.getUITranslation(player, "ui.lore_socket.process_button");
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
        if (matchesAnyId(entry.itemId, LoreSocketManager.getConfig().getFeedItemIds())) {
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
                                stateWith(state, equipmentKey, state != null ? state.gemKey : null,
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
        List<Entry> gems = snapshot != null ? snapshot.gems : List.of();
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
        String color = socket.getColor() == null || socket.getColor().isBlank()
                ? LangLoader.getUITranslation(player, "ui.lore_socket.color_unknown")
                : socket.getColor();
        lines.add(LangLoader.getUITranslation(player, "ui.lore_socket.detail_slot", socket.getSlotIndex() + 1));
        lines.add(LangLoader.getUITranslation(player, "ui.lore_socket.detail_color", color));
        if (socket.hasSpirit()) {
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
            String state = socketStateLabel(player, socket);
            sb.append(LangLoader.getUITranslation(player, "ui.lore_socket.metadata_socket_line", i + 1, state))
                    .append("\n");
        }
        return sb.toString().trim();
    }

    private static String socketStateLabel(Player player, LoreSocketData.LoreSocket socket) {
        if (socket == null) {
            return LangLoader.getUITranslation(player, "ui.lore_socket.socket_empty");
        }
        String base;
        if (socket.isLocked()) {
            base = LangLoader.getUITranslation(player, "ui.lore_socket.socket_locked");
        } else if (socket.isEmpty()) {
            base = LangLoader.getUITranslation(player, "ui.lore_socket.socket_empty");
        } else {
            base = LangLoader.getUITranslation(player, "ui.lore_socket.socket_filled");
        }
        String color = socket.getColor();
        if (color == null || color.isBlank()) {
            color = LangLoader.getUITranslation(player, "ui.lore_socket.color_unknown");
        }
        return base + " (" + color + ")";
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
        LoreSocketData data = LoreSocketManager.getLoreSocketData(equipment.item);
        if (data == null || data.getSocketCount() == 0) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.option_no_sockets"));
        }

        if (support != null && support.item != null && !support.item.isEmpty()) {
            MaterialKind kind = materialKind(support);
            if (kind == MaterialKind.FEED) {
                return processFeed(player, equipment, slotKey);
            }
            if (kind == MaterialKind.CLEAR) {
                return processClear(player, equipment, slotKey);
            }
            if (kind != MaterialKind.REROLL) {
                return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_invalid_support"));
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

            ItemStack updated = LoreSocketManager.withLoreSocketData(equipment.item, data);
            ItemContainer equipmentContainer = getContainer(player, equipment.kind);
            if (equipmentContainer != null) {
                equipmentContainer.setItemStackForSlot(equipment.slot, updated);
            }
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_reroll_done"));
        }

        if (gem == null || gem.item == null || gem.item.isEmpty()) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_no_gem"));
        }
        if (!LoreSocketManager.isLoreGem(gem.item)) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_invalid_gem"));
        }

        String gemColor = LoreGemRegistry.resolveColor(gem.itemId);
        if (gemColor == null || gemColor.isBlank()) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_missing_color"));
        }

        int slotIndex = resolveSlotIndex(data, slotKey, gemColor);
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
        String socketColor = socket.getColor();
        if (socketColor != null && !socketColor.isBlank() && !socketColor.equalsIgnoreCase(gemColor)) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_color_mismatch"));
        }

        socket.setGemItemId(gem.itemId);
        if (socketColor == null || socketColor.isBlank()) {
            socket.setColor(gemColor.toLowerCase(Locale.ROOT));
        }

        ItemStack updated = LoreSocketManager.withLoreSocketData(equipment.item, data);
        ItemContainer equipmentContainer = getContainer(player, equipment.kind);
        if (equipmentContainer != null) {
            equipmentContainer.setItemStackForSlot(equipment.slot, updated);
        }

        ItemContainer gemContainer = getContainer(player, gem.kind);
        if (gemContainer != null) {
            gemContainer.removeItemStackFromSlot(gem.slot, 1, false, false);
        }

        return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_done"));
    }

    private static ProcessResult processFeed(Player player, Entry equipment, String slotKey) {
        if (player == null) {
            return new ProcessResult("Player not found.");
        }
        if (equipment == null || equipment.item == null || equipment.item.isEmpty()) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_feed.status_no_equipment"));
        }
        LoreSocketData data = LoreSocketManager.getLoreSocketData(equipment.item);
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
        return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_feed.status_done"));
    }

    private static ProcessResult processClear(Player player, Entry equipment, String slotKey) {
        if (player == null) {
            return new ProcessResult("Player not found.");
        }
        if (equipment == null || equipment.item == null || equipment.item.isEmpty()) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_feed.status_no_equipment"));
        }
        LoreSocketData data = LoreSocketManager.getLoreSocketData(equipment.item);
        int slotIndex = resolveSlotIndex(data, slotKey, null);
        if (slotIndex < 0 || data == null || slotIndex >= data.getSocketCount()) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_feed.status_no_socket"));
        }
        LoreSocketData.LoreSocket socket = data.getSocket(slotIndex);
        if (socket == null || socket.isEmpty()) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_feed.status_no_socket"));
        }
        if (!LoreSocketManager.tryClearSpirit(player, data, slotIndex, true)) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_feed.status_no_clear_item"));
        }
        saveEquipment(player, equipment, data);
        return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_feed.status_cleared"));
    }

    private static void saveEquipment(Player player, Entry equipment, LoreSocketData data) {
        ItemStack updated = LoreSocketManager.withLoreSocketData(equipment.item, data);
        ItemContainer equipmentContainer = getContainer(player, equipment.kind);
        if (equipmentContainer != null) {
            equipmentContainer.setItemStackForSlot(equipment.slot, updated);
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
}
