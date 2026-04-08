package irai.mod.reforge.UI;

import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import org.bson.BsonDocument;

import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import irai.mod.reforge.Common.UI.HyUIReflectionUtils;
import irai.mod.reforge.Common.UI.UIInventoryUtils;
import irai.mod.reforge.Common.UI.UIItemUtils;
import irai.mod.reforge.Common.UI.UITemplateUtils;
import irai.mod.reforge.Config.SFXConfig;
import irai.mod.reforge.Interactions.ReforgeEquip;
import irai.mod.reforge.Lore.LoreSocketData;
import irai.mod.reforge.Lore.LoreSocketManager;
import irai.mod.reforge.Socket.Essence;
import irai.mod.reforge.Socket.EssenceRegistry;
import irai.mod.reforge.Socket.Socket;
import irai.mod.reforge.Socket.SocketData;
import irai.mod.reforge.Socket.SocketManager;
import irai.mod.reforge.Socket.SocketManager.PunchResult;
import irai.mod.reforge.Socket.SocketManager.SupportMaterial;
import irai.mod.reforge.Util.DynamicTooltipUtils;
import irai.mod.reforge.Util.LangLoader;

/**
 * HyUI socket bench page opened through command.
 * Uses reflection so the plugin still runs when HyUI is not present.
 */
public class SocketBenchUI {

    private static final String HYUI_PAGE_BUILDER  = "au.ellie.hyui.builders.PageBuilder";
    private static final String HYUI_PLUGIN        = "au.ellie.hyui.HyUIPlugin";
    private static final String HYUI_EVENT_BINDING = "com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType";
    private static final String UI_COMMAND_BUILDER = "com.hypixel.hytale.server.core.ui.builder.UICommandBuilder";
    private static final String SOCKET_BENCH_TEMPLATE_PATH = "Common/UI/Custom/Pages/SocketBench.html";

    private static final String PUNCHER_ITEM_ID = "Socket_Puncher";
    private static final String NONE_SUPPORT_KEY = "__NONE_SUPPORT__";

    private static boolean hyuiAvailable = false;
    private static final SFXConfig sfxConfig = new SFXConfig();

    private static final Map<PlayerRef, Object> openPages = new ConcurrentHashMap<>();
    private static final Map<PlayerRef, SelectionState> pendingSelections = new ConcurrentHashMap<>();
    private static final Map<PlayerRef, Boolean> processingPlayers = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService processScheduler = Executors.newSingleThreadScheduledExecutor();
    private static final int PROCESS_DURATION_MS = 1000;
    private static final int PROGRESS_TICK_MS = 50;

    private enum ContainerKind {
        HOTBAR,
        STORAGE
    }

    private static final class Entry {
        final ContainerKind containerKind;
        final short slot;
        final ItemStack item;
        final String itemId;
        final int quantity;
        final String displayName;

        Entry(ContainerKind containerKind, short slot, ItemStack item, String itemId, int quantity, String displayName) {
            this.containerKind = containerKind;
            this.slot = slot;
            this.item = item;
            this.itemId = itemId;
            this.quantity = quantity;
            this.displayName = displayName;
        }
    }

    private static final class BenchSnapshot {
        final List<Entry> equipments;
        final List<Entry> punchers;
        final List<Entry> supports;

        BenchSnapshot(List<Entry> equipments, List<Entry> punchers, List<Entry> supports) {
            this.equipments = equipments;
            this.punchers = punchers;
            this.supports = supports;
        }
    }

    private static final class SelectionState {
        final String equipmentKey;
        final String puncherKey;
        final String supportKey;
        final String statusText;
        final int progressValue;
        final boolean processing;

        SelectionState(String equipmentKey, String puncherKey, String supportKey, String statusText, int progressValue, boolean processing) {
            this.equipmentKey = equipmentKey;
            this.puncherKey = puncherKey;
            this.supportKey = supportKey;
            this.statusText = statusText;
            this.progressValue = progressValue;
            this.processing = processing;
        }
    }

    public static void initialize() {
        hyuiAvailable = HyUIReflectionUtils.detectHyUi(HYUI_PAGE_BUILDER, HYUI_PLUGIN, "SocketBenchUI");
    }

    public static boolean isAvailable() {
        return hyuiAvailable;
    }

    public static void open(Player player) {
        if (player == null) {
            return;
        }
        if (!hyuiAvailable) {
            player.sendMessage(Message.raw("<color=#FF5555>" + LangLoader.getUITranslation(player, "ui.socket_bench.hyui_missing")));
            return;
        }

        PlayerRef playerRef = player.getPlayerRef();
        closePageIfOpen(playerRef);
        player.getWorld().execute(() -> openWithSync(player));
    }

    private static void openWithSync(Player player) {
        BenchSnapshot snapshot = collectSnapshot(player);
        SelectionState selectionState = pendingSelections.remove(player.getPlayerRef());
        openPage(player, snapshot, selectionState);
    }

    private static BenchSnapshot collectSnapshot(Player player) {
        List<Entry> equipments = new ArrayList<>();
        List<Entry> punchers = new ArrayList<>();
        List<Entry> supports = new ArrayList<>();

        collectFromContainer(player, player.getInventory().getHotbar(), ContainerKind.HOTBAR, equipments, punchers, supports);
        collectFromContainer(player, player.getInventory().getStorage(), ContainerKind.STORAGE, equipments, punchers, supports);
        return new BenchSnapshot(equipments, punchers, supports);
    }

    private static void collectFromContainer(
            Player player,
            ItemContainer container,
            ContainerKind kind,
            List<Entry> equipments,
            List<Entry> punchers,
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
            if (itemId == null || itemId.isEmpty()) {
                continue;
            }

            String name = UIItemUtils.displayNameOrItemId(stack, player);

            Entry entry = new Entry(kind, slot, stack, itemId, stack.getQuantity(), name);

            if (ReforgeEquip.isWeapon(stack) || ReforgeEquip.isArmor(stack)) {
                // Only scanning hotbar/storage so equipped armor is naturally excluded.
                equipments.add(entry);
            }

            if (PUNCHER_ITEM_ID.equalsIgnoreCase(itemId)) {
                punchers.add(entry);
            }
            if (SocketManager.isSupportMaterial(itemId)) {
                supports.add(entry);
            }
        }
    }

    private static void openPage(Player player, BenchSnapshot snapshot, SelectionState selectionState) {
        PlayerRef playerRef = player.getPlayerRef();

        try {
            Class<?> pageBuilderClass = Class.forName(HYUI_PAGE_BUILDER);
            Class<?> eventBindingClass = Class.forName(HYUI_EVENT_BINDING);
            Class<?> uiCommandClass = Class.forName(UI_COMMAND_BUILDER);

            Method pageForPlayer = pageBuilderClass.getMethod("pageForPlayer", PlayerRef.class);
            Method fromHtml = pageBuilderClass.getMethod("fromHtml", String.class);
            Method addListener = pageBuilderClass.getMethod(
                    "addEventListener",
                    String.class,
                    eventBindingClass,
                    java.util.function.BiConsumer.class);
            Method withLifetime = pageBuilderClass.getMethod("withLifetime", CustomPageLifetime.class);
            Method openMethod = pageBuilderClass.getMethod("open", Class.forName("com.hypixel.hytale.component.Store"));

            Object valueChanged = eventBindingClass.getField("ValueChanged").get(null);
            Object activating = eventBindingClass.getField("Activating").get(null);

            String html = buildHtml(player, snapshot, selectionState);
            Object pageBuilder = pageForPlayer.invoke(null, playerRef);
            pageBuilder = fromHtml.invoke(pageBuilder, html);

            final Player finalPlayer = player;
            final BenchSnapshot finalSnapshot = snapshot;
            final Class<?> finalUiCommandClass = uiCommandClass;

            addListener.invoke(pageBuilder, "equipmentDropdown", valueChanged,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        try {
                            String selected = extractEventValue(eventObj);
                            String supportSelected = getContextValue(ctxObj, "supportDropdown", "#supportDropdown.value");
                            String puncherSelected = getContextValue(ctxObj, "puncherDropdown", "#puncherDropdown.value");
                            Entry selectedEquipment = resolveSelection(finalSnapshot.equipments, selected);
                            Entry selectedPuncher = resolveSelection(finalSnapshot.punchers, puncherSelected);
                            Entry selectedSupport = resolveSelection(finalSnapshot.supports, supportSelected);
                            pendingSelections.put(
                                    finalPlayer.getPlayerRef(),
                                    new SelectionState(
                                            keyOf(selectedEquipment),
                                            keyOf(selectedPuncher),
                                            supportSelectionKey(supportSelected, selectedSupport),
                                            null,
                                            0,
                                            false));
                            finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));
                        } catch (Exception e) {
                            System.err.println("[SocketReforge] SocketBenchUI equipment change failed: " + e.getMessage());
                        }
                    });

            addListener.invoke(pageBuilder, "supportDropdown", valueChanged,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        try {
                            String supportSelected = extractEventValue(eventObj);
                            String equipmentSelected = getContextValue(ctxObj, "equipmentDropdown", "#equipmentDropdown.value");
                            String puncherSelected = getContextValue(ctxObj, "puncherDropdown", "#puncherDropdown.value");
                            Entry selectedEquipment = resolveSelection(finalSnapshot.equipments, equipmentSelected);
                            Entry selectedPuncher = resolveSelection(finalSnapshot.punchers, puncherSelected);
                            Entry selectedSupport = resolveSelection(finalSnapshot.supports, supportSelected);
                            pendingSelections.put(
                                    finalPlayer.getPlayerRef(),
                                    new SelectionState(
                                            keyOf(selectedEquipment),
                                            keyOf(selectedPuncher),
                                            supportSelectionKey(supportSelected, selectedSupport),
                                            null,
                                            0,
                                            false));
                            finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));
                        } catch (Exception e) {
                            System.err.println("[SocketReforge] SocketBenchUI support change failed: " + e.getMessage());
                        }
                    });

            addListener.invoke(pageBuilder, "processButton", activating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        try {
                            if (Boolean.TRUE.equals(processingPlayers.get(finalPlayer.getPlayerRef()))) {
                                return;
                            }
                            String equipmentVal = getContextValue(ctxObj, "equipmentDropdown", "#equipmentDropdown.value");
                            String puncherVal = getContextValue(ctxObj, "puncherDropdown", "#puncherDropdown.value");
                            String supportVal = getContextValue(ctxObj, "supportDropdown", "#supportDropdown.value");
                            Entry selectedEquipment = resolveSelection(finalSnapshot.equipments, equipmentVal);
                            Entry selectedPuncher = resolveSelection(finalSnapshot.punchers, puncherVal);
                            Entry selectedSupport = resolveSelection(finalSnapshot.supports, supportVal);
                            String selectedSupportKey = supportSelectionKey(supportVal, selectedSupport);
                            processingPlayers.put(finalPlayer.getPlayerRef(), true);
                            pendingSelections.put(
                                    finalPlayer.getPlayerRef(),
                                    new SelectionState(
                                            keyOf(selectedEquipment),
                                            keyOf(selectedPuncher),
                                            selectedSupportKey,
                                            LangLoader.getUITranslation(finalPlayer, "ui.socket_bench.status_processing"),
                                            0,
                                            true));
                            sfxConfig.playReforgeStart(finalPlayer);
                            finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));

                            for (int elapsed = PROGRESS_TICK_MS; elapsed < PROCESS_DURATION_MS; elapsed += PROGRESS_TICK_MS) {
                                final int delay = elapsed;
                                final int timedProgress = Math.min(99, (int) Math.round(((delay + PROGRESS_TICK_MS) * 100.0) / PROCESS_DURATION_MS));
                                processScheduler.schedule(() -> finalPlayer.getWorld().execute(() -> {
                                    if (!Boolean.TRUE.equals(processingPlayers.get(finalPlayer.getPlayerRef()))) return;
                                    pendingSelections.put(
                                            finalPlayer.getPlayerRef(),
                                            new SelectionState(
                                                    keyOf(selectedEquipment),
                                                    keyOf(selectedPuncher),
                                                    selectedSupportKey,
                                                    LangLoader.getUITranslation(finalPlayer, "ui.socket_bench.status_processing"),
                                                    timedProgress,
                                                    true));
                                    openWithSync(finalPlayer);
                                }), delay, TimeUnit.MILLISECONDS);
                            }

                            processScheduler.schedule(() -> finalPlayer.getWorld().execute(() -> {
                                try {
                                    ProcessResult result = processSelection(finalPlayer, finalSnapshot, selectedEquipment, selectedPuncher, selectedSupport);

                                    pendingSelections.put(
                                            finalPlayer.getPlayerRef(),
                                            new SelectionState(
                                                    keyOf(selectedEquipment),
                                                    keyOf(selectedPuncher),
                                                    selectedSupportKey,
                                                    result.statusLine,
                                                    result.progressValue,
                                                    false));
                                    processingPlayers.remove(finalPlayer.getPlayerRef());
                                    openWithSync(finalPlayer);
                                } catch (Exception ex) {
                                    processingPlayers.remove(finalPlayer.getPlayerRef());
                                    System.err.println("[SocketReforge] SocketBenchUI delayed process failed: " + ex.getMessage());
                                    ex.printStackTrace();
                                }
                            }), PROCESS_DURATION_MS, TimeUnit.MILLISECONDS);
                        } catch (Exception e) {
                            System.err.println("[SocketReforge] SocketBenchUI process failed: " + e.getMessage());
                            e.printStackTrace();
                        }
                    });

            pageBuilder = withLifetime.invoke(pageBuilder, CustomPageLifetime.CanDismiss);
            Object store = getStore(playerRef);
            Object page = openMethod.invoke(pageBuilder, store);
            openPages.put(playerRef, page);
        } catch (Exception e) {
            System.err.println("[SocketReforge] SocketBenchUI open error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String buildHtml(Player player, BenchSnapshot snapshot, SelectionState selectionState) {
        String selectedEquipmentKey = selectionState != null ? selectionState.equipmentKey : null;
        String selectedPuncherKey = selectionState != null ? selectionState.puncherKey : null;
        String selectedSupportKey = selectionState != null ? selectionState.supportKey : null;
        String statusText = selectionState != null && selectionState.statusText != null
                ? selectionState.statusText
                : LangLoader.getUITranslation(player, "ui.socket_bench.status_idle");
        int progressValue = selectionState != null ? Math.max(0, Math.min(100, selectionState.progressValue)) : 0;
        boolean isProcessing = selectionState != null && selectionState.processing;
        if (!isProcessing) {
            progressValue = 0;
        }
        String equipmentOptions = buildEquipmentOptions(player, snapshot.equipments, selectedEquipmentKey);
        String puncherOptions = buildMaterialOptions(player, snapshot.punchers,
                LangLoader.getUITranslation(player, "ui.socket_bench.option_no_puncher"),
                selectedPuncherKey);
        String supportOptions = buildSupportOptions(player, snapshot.supports, selectedSupportKey);

        Entry selectedEquipment = findByKey(snapshot.equipments, selectedEquipmentKey);
        Entry selectedSupport = findByKey(snapshot.supports, selectedSupportKey);
        SupportMaterial selectedSupportMaterial = selectedSupport == null
                ? SupportMaterial.NONE
                : SocketManager.resolveSupportMaterial(selectedSupport.itemId);
        boolean supportOverridesMax = selectedSupportMaterial == SupportMaterial.SOCKET_EXPANDER
                || selectedSupportMaterial == SupportMaterial.SOCKET_DIFFUSER
                || selectedSupportMaterial == SupportMaterial.GHASTLY_ESSENCE;
        boolean socketsMaxed = isSocketsMaxed(selectedEquipment);
        String idleText = LangLoader.getUITranslation(player, "ui.socket_bench.status_idle");
        if (!isProcessing && (idleText.equals(statusText) || "Idle".equals(statusText)) && socketsMaxed && !supportOverridesMax) {
            statusText = LangLoader.getUITranslation(player, "ui.socket_bench.status_max_sockets");
        }
        String processDisabledAttr = (isProcessing || (socketsMaxed && !supportOverridesMax))
                ? "disabled data-hyui-disabled=\"true\""
                : "";
        String defaultMetadata = selectedEquipment == null
                ? LangLoader.getUITranslation(player, "ui.socket_bench.metadata_prompt")
                : buildMetadataText(player, selectedEquipment.item, selectedEquipment);
        String socketIcons = buildSocketIconsHtml(player, selectedEquipment);
        String loreSocketSection = buildLoreSocketSection(player, selectedEquipment);

        String selectedEquipmentValue = selectedEquipment == null ? "-1" : String.valueOf(snapshot.equipments.indexOf(selectedEquipment));
        String selectedSupportValue = selectedSupport == null ? "-1" : String.valueOf(snapshot.supports.indexOf(selectedSupport));
        StatPreview defaultStats = calculatePreview(snapshot, selectedEquipmentValue, selectedSupportValue);

        String template = loadTemplate(SOCKET_BENCH_TEMPLATE_PATH);
        if (template != null && !template.isBlank()) {
            String html = template
                    .replace("{{equipmentOptions}}", equipmentOptions)
                    .replace("{{puncherOptions}}", puncherOptions)
                    .replace("{{supportOptions}}", supportOptions)
                    .replace("{{successText}}", escapeHtml(defaultStats.successText))
                    .replace("{{breakText}}", escapeHtml(defaultStats.breakText))
                    .replace("{{socketsText}}", escapeHtml(defaultStats.socketsText))
                    .replace("{{socketIcons}}", socketIcons)
                    .replace("{{loreSocketSection}}", loreSocketSection)
                    .replace("{{progressValue}}", String.valueOf(progressValue))
                    .replace("{{statusText}}", escapeHtml(statusText))
                    .replace("{{processDisabledAttr}}", processDisabledAttr)
                    .replace("{{metadataText}}", escapeHtml(defaultMetadata));
            return LangLoader.replaceUiTokens(player, html);
        }

        return LangLoader.replaceUiTokens(player,
                "<div class=\"page-overlay\">"
                + "<div class=\"decorated-container\" data-hyui-title=\"Socket Punch Bench\" style=\"anchor-width: 920; anchor-height: 760;\">"
                + "<div class=\"container-contents\" style=\"anchor-full: 14; overflow-y:auto;\">"
                + "<h2 style=\"text-align:center;\">Socket Punch Bench</h2>"
                + "<p style=\"text-align:center;\">Select equipment and materials from inventory.</p>"
                + "<hr/>"
                + "<p><b>Equipment</b></p>"
                + "<select id=\"equipmentDropdown\" data-hyui-showlabel=\"true\">" + equipmentOptions + "</select>"
                + "<p><b>Main Material (Socket Puncher)</b></p>"
                + "<select id=\"puncherDropdown\" data-hyui-showlabel=\"true\">" + puncherOptions + "</select>"
                + "<p><b>Support Material (Optional)</b></p>"
                + "<select id=\"supportDropdown\" data-hyui-showlabel=\"true\">" + supportOptions + "</select>"
                + "<hr/>"
                + "<p><b>Socket Preview</b></p>"
                + "<div id=\"socketIconsRow\">" + socketIcons + "</div>"
                + (loreSocketSection == null || loreSocketSection.isBlank() ? "" : loreSocketSection)
                + "<div style=\"layout-mode: Left; spacing: 12;\">"
                + "<p style=\"flex-weight:1;\">Success: <span id=\"successChanceLabel\">" + defaultStats.successText + "</span></p>"
                + "<p style=\"flex-weight:1;\">Item Break: <span id=\"breakChanceLabel\">" + defaultStats.breakText + "</span></p>"
                + "<p style=\"flex-weight:1;\">Sockets: <span id=\"socketCountLabel\">" + defaultStats.socketsText + "</span></p>"
                + "</div>"
                + "<p><b>Progress</b></p>"
                + "<progress id=\"socketProgress\" max=\"100\" value=\"" + progressValue + "\" style=\"width:100%;\"></progress>"
                + "<p id=\"statusLabel\">" + escapeHtml(statusText) + "</p>"
                + "<hr/>"
                + "<p><b>Selected Item Metadata</b></p>"
                + "<p id=\"metadataLabel\" style=\"white-space:pre-line;max-height:130px;overflow-y:auto;word-break:break-word;\">"
                + escapeHtml(defaultMetadata)
                + "</p>"
                + "<p style=\"font-size:11;\">Failure consumes Socket Puncher. Break can damage socket state.</p>"
                + "<button id=\"processButton\" style=\"width:100%;height:40px;\"" + processDisabledAttr + ">Process Materials</button>"
                + "</div>"
                + "</div>");
    }

    private static String loadTemplate(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        return UITemplateUtils.loadTemplate(SocketBenchUI.class, path, null, "SocketBenchUI");
    }

    private static String buildEquipmentOptions(Player player, List<Entry> equipments, String selectedKey) {
        if (equipments.isEmpty()) {
            return "<option value=\"-1\" selected>"
                    + escapeHtml(LangLoader.getUITranslation(player, "ui.socket_bench.option_no_equipment"))
                    + "</option>";
        }
        StringBuilder sb = new StringBuilder();
        boolean hasSelected = selectedKey != null && findByKey(equipments, selectedKey) != null;
        sb.append("<option value=\"-1\"").append(hasSelected ? "" : " selected").append(">")
                .append(escapeHtml(LangLoader.getUITranslation(player, "ui.socket_bench.option_select_equipment")))
                .append("</option>");
        for (int i = 0; i < equipments.size(); i++) {
            Entry e = equipments.get(i);
            String tag = ReforgeEquip.isWeapon(e.item)
                    ? LangLoader.getUITranslation(player, "ui.socket_bench.tag_weapon")
                    : (ReforgeEquip.isArmor(e.item)
                        ? LangLoader.getUITranslation(player, "ui.socket_bench.tag_armor")
                        : LangLoader.getUITranslation(player, "ui.socket_bench.tag_item"));
            String label = e.displayName + " [" + tag + "] x" + e.quantity;
            boolean isSelected = selectedKey != null && selectedKey.equals(keyOf(e));
            sb.append("<option value=\"").append(i).append("\"")
                    .append(isSelected ? " selected" : "")
                    .append(">")
                    .append(escapeHtml(label))
                    .append("</option>");
        }
        return sb.toString();
    }

    private static String buildMaterialOptions(Player player, List<Entry> entries, String emptyText, String selectedKey) {
        if (entries.isEmpty()) {
            return "<option value=\"-1\">" + escapeHtml(emptyText) + "</option>";
        }
        StringBuilder sb = new StringBuilder();
        boolean hasSelected = selectedKey != null && findByKey(entries, selectedKey) != null;
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            String label = LangLoader.getUITranslation(player, "ui.socket_bench.option_material_label",
                    e.itemId, e.quantity, locationText(player, e), e.slot);
            boolean isSelected = hasSelected ? selectedKey.equals(keyOf(e)) : i == 0;
            sb.append("<option value=\"").append(i).append("\"")
                    .append(isSelected ? " selected" : "")
                    .append(">")
                    .append(escapeHtml(label))
                    .append("</option>");
        }
        return sb.toString();
    }

    private static String buildSupportOptions(Player player, List<Entry> supports, String selectedKey) {
        StringBuilder sb = new StringBuilder();
        boolean noneSelected = selectedKey == null || NONE_SUPPORT_KEY.equals(selectedKey);
        boolean hasSelected = !noneSelected && selectedKey != null && findByKey(supports, selectedKey) != null;
        boolean selectNone = supports.isEmpty() || noneSelected;
        sb.append("<option value=\"-1\"").append(selectNone ? " selected" : "").append(">")
                .append(escapeHtml(LangLoader.getUITranslation(player, "ui.socket_bench.option_none")))
                .append("</option>");
        for (int i = 0; i < supports.size(); i++) {
            Entry e = supports.get(i);
            String label = LangLoader.getUITranslation(player, "ui.socket_bench.option_material_label",
                    e.itemId, e.quantity, locationText(player, e), e.slot);
            boolean isSelected = hasSelected && selectedKey.equals(keyOf(e));
            sb.append("<option value=\"").append(i).append("\"")
                    .append(isSelected ? " selected" : "")
                    .append(">")
                    .append(escapeHtml(label))
                    .append("</option>");
        }
        return sb.toString();
    }

    private static String keyOf(Entry entry) {
        if (entry == null) return null;
        return entry.containerKind + ":" + entry.slot + ":" + entry.itemId;
    }

    private static Entry findByKey(List<Entry> entries, String key) {
        if (entries == null || entries.isEmpty() || key == null || key.isBlank()) {
            return null;
        }
        for (Entry entry : entries) {
            if (key.equals(keyOf(entry))) {
                return entry;
            }
        }
        return null;
    }

    private static boolean isSocketsMaxed(Entry equipment) {
        if (equipment == null || equipment.item == null || equipment.item.isEmpty()) {
            return false;
        }
        SocketData socketData = SocketManager.getSocketData(equipment.item);
        if (socketData == null) {
            socketData = SocketData.fromDefaults(ReforgeEquip.isWeapon(equipment.item) ? "weapon" : "armor");
        }
        int max = socketData.getMaxSockets();
        return max > 0 && socketData.getCurrentSocketCount() >= max;
    }

    private static String buildSocketIconsHtml(Player player, Entry equipment) {
        if (equipment == null || equipment.item == null || equipment.item.isEmpty()) {
            StringBuilder empty = new StringBuilder();
            empty.append("<div style=\"layout-mode: Left; spacing: 14;\"><div style=\"flex-weight:1;\"></div>");
            for (int i = 0; i < 4; i++) {
                empty.append("<div style=\"anchor-width:110; anchor-height:110; background-color:#00000000; layout-mode:Top;\">")
                        .append("<div style=\"flex-weight:1;\"></div>")
                        .append("<div style=\"layout-mode:Left;\"><div style=\"flex-weight:1;\"></div>")
                        .append("<img src=\"slot_bg.png\" width=\"90\" height=\"90\"/>")
                        .append("<div style=\"flex-weight:1;\"></div></div>")
                        .append("<div style=\"flex-weight:1;\"></div>")
                        .append("</div>");
            }
            empty.append("<div style=\"flex-weight:1;\"></div></div>");
            return empty.toString();
        }

        SocketData socketData = SocketManager.getSocketData(equipment.item);
        if (socketData == null) {
            socketData = SocketData.fromDefaults(ReforgeEquip.isWeapon(equipment.item) ? "weapon" : "armor");
        }

        int maxSockets = Math.max(0, socketData.getMaxSockets());
        int punchedSockets = socketData.getCurrentSocketCount();
        List<Socket> sockets = socketData.getSockets();
        String brokenIconName = resolveBrokenSocketIconName();

        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"layout-mode: Left; spacing: 14;\"><div style=\"flex-weight:1;\"></div>");
        for (int i = 0; i < maxSockets; i++) {
            boolean isPunched = i < punchedSockets;
            Socket socket = i < sockets.size() ? sockets.get(i) : null;
            boolean isBroken = socket != null && socket.isBroken();
            boolean isFilled = socket != null && !socket.isBroken() && !socket.isEmpty();

            String backgroundIcon = isBroken
                    ? brokenIconName
                    : (!isPunched ? "slot_bg.png" : "socket_empty.png");
            String overlayIcon = (isPunched && isFilled && !isBroken) ? resolveEssenceIconName(socket) : null;

            String tileStyle = "anchor-width:91; anchor-height:91;"
                    + " background-image:url('" + backgroundIcon + "'); background-size:100% 100%;"
                    + " background-repeat:no-repeat; layout-mode:Top;";
            String wrapStyle = "anchor-width:95; anchor-height:95; background-color:" + getSocketColorHex(socket)
                    + "; layout-mode:Top; padding:2;";

            int overlaySize = 85;
            sb.append("<div style=\"").append(wrapStyle).append("\">")
                    .append("<div style=\"flex-weight:1;\"></div>")
                    .append("<div style=\"layout-mode:Left;\"><div style=\"flex-weight:1;\"></div>")
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
                    .append("<div style=\"flex-weight:1;\"></div></div>")
                    .append("<div style=\"flex-weight:1;\"></div>")
                    .append("</div>");
        }
        sb.append("<div style=\"flex-weight:1;\"></div></div>");
        return sb.toString();
    }

    private static String buildLoreSocketIconsHtml(Player player, Entry equipment) {
        if (equipment == null || equipment.item == null || equipment.item.isEmpty()) {
            return "";
        }

        LoreSocketData data = LoreSocketManager.getLoreSocketData(equipment.item);
        if (data == null || data.getSocketCount() == 0) {
            return "";
        }

        String baseIcon = UITemplateUtils.resolveCustomUiAsset("GemSlotEmpty.png", "GemSlotEmpty.png");
        int tileSize = 70;
        int wrapSize = 78;
        int overlaySize = 44;

        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"layout-mode: Left; spacing: 10;\"><div style=\"flex-weight:1;\"></div>");
        for (int i = 0; i < data.getSocketCount(); i++) {
            LoreSocketData.LoreSocket socket = data.getSocket(i);
            boolean filled = socket != null && !socket.isEmpty();
            String colorHex = resolveLoreColorHex(socket);
            String overlayIcon = filled ? resolveLoreGemOverlayIcon(socket) : null;

            String wrapStyle = "anchor-width:" + wrapSize + "; anchor-height:" + wrapSize + ";"
                    + " background-color:" + colorHex + "; layout-mode:Top; padding:4; border-radius:6;";
            String tileStyle = "anchor-width:" + tileSize + "; anchor-height:" + tileSize + ";"
                    + " background-image:url('" + baseIcon + "'); background-size:100% 100%;"
                    + " background-repeat:no-repeat; layout-mode:Top;";

            sb.append("<div style=\"").append(wrapStyle).append("\">")
                    .append("<div style=\"flex-weight:1;\"></div>")
                    .append("<div style=\"layout-mode:Left;\"><div style=\"flex-weight:1;\"></div>")
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
                    .append("<div style=\"flex-weight:1;\"></div></div>")
                    .append("<div style=\"flex-weight:1;\"></div>")
                    .append("</div>");
        }
        sb.append("<div style=\"flex-weight:1;\"></div></div>");
        return sb.toString();
    }

    private static String buildLoreSocketSection(Player player, Entry equipment) {
        String icons = buildLoreSocketIconsHtml(player, equipment);
        if (icons == null || icons.isBlank()) {
            return "";
        }
        return "<p style=\"margin-top:8;\">" + escapeHtml(LangLoader.getUITranslation(player, "ui.socket_bench.lore_socket_preview")) + "</p>"
                + "<div id=\"loreSocketIconsRow\">" + icons + "</div>";
    }

    private static String resolveLoreColorHex(LoreSocketData.LoreSocket socket) {
        if (socket == null) {
            return "#2b2b3a";
        }
        if (socket.isLocked()) {
            return "#3a3a3a";
        }
        String color = socket.getColor();
        if (color == null || color.isBlank()) {
            return "#2b2b3a";
        }
        String trimmed = color.trim();
        if (trimmed.startsWith("#")) {
            return trimmed;
        }
        String lower = trimmed.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("red") || lower.contains("ruby")) return "#FF5555";
        if (lower.contains("blue") || lower.contains("sapphire")) return "#5599FF";
        if (lower.contains("green") || lower.contains("emerald")) return "#55FF77";
        if (lower.contains("purple") || lower.contains("amethyst")) return "#AA55FF";
        if (lower.contains("yellow") || lower.contains("topaz")) return "#FFFF55";
        if (lower.contains("orange")) return "#FFAA00";
        if (lower.contains("black") || lower.contains("onyx")) return "#555555";
        if (lower.contains("white") || lower.contains("diamond")) return "#FFFFFF";
        if (lower.contains("cyan") || lower.contains("opal")) return "#55FFFF";
        return "#2b2b3a";
    }

    private static String resolveLoreGemOverlayIcon(LoreSocketData.LoreSocket socket) {
        if (socket == null || socket.isEmpty()) {
            return null;
        }
        String gemItemId = socket.getGemItemId();
        String byItem = resolveGemIconByItemId(gemItemId);
        if (byItem != null) {
            return byItem;
        }
        String color = socket.getColor();
        String byColor = resolveGemIconByColor(color);
        if (byColor != null) {
            return byColor;
        }
        return UITemplateUtils.resolveCustomUiAsset(
                "Icons/ItemsGenerated/Plant_Fruit_Spiral_Tree.png",
                "Icons/ItemsGenerated/Plant_Fruit_Spiral_Tree.png");
    }

    private static String resolveGemIconByItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        String lower = itemId.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("ruby")) return resolveGemIconByColor("red");
        if (lower.contains("sapphire")) return resolveGemIconByColor("blue");
        if (lower.contains("emerald")) return resolveGemIconByColor("green");
        if (lower.contains("diamond")) return resolveGemIconByColor("white");
        if (lower.contains("topaz")) return resolveGemIconByColor("yellow");
        if (lower.contains("voidstone") || lower.contains("onyx")) return resolveGemIconByColor("black");
        if (lower.contains("zephyr") || lower.contains("opal")) return resolveGemIconByColor("cyan");
        return null;
    }

    private static String resolveGemIconByColor(String color) {
        if (color == null || color.isBlank()) {
            return null;
        }
        String lower = color.toLowerCase(java.util.Locale.ROOT);
        String icon;
        if (lower.contains("red") || lower.contains("ruby")) {
            icon = "Icons/ItemsGenerated/Rock_Gem_Ruby.png";
        } else if (lower.contains("blue") || lower.contains("sapphire")) {
            icon = "Icons/ItemsGenerated/Rock_Gem_Sapphire.png";
        } else if (lower.contains("green") || lower.contains("emerald")) {
            icon = "Icons/ItemsGenerated/Rock_Gem_Emerald.png";
        } else if (lower.contains("white") || lower.contains("diamond")) {
            icon = "Icons/ItemsGenerated/Rock_Gem_Diamond.png";
        } else if (lower.contains("yellow") || lower.contains("topaz")) {
            icon = "Icons/ItemsGenerated/Rock_Gem_Topaz.png";
        } else if (lower.contains("black") || lower.contains("voidstone") || lower.contains("onyx")) {
            icon = "Icons/ItemsGenerated/Rock_Gem_Voidstone.png";
        } else if (lower.contains("cyan") || lower.contains("opal") || lower.contains("zephyr")) {
            icon = "Icons/ItemsGenerated/Rock_Gem_Zephyr.png";
        } else {
            return null;
        }
        return UITemplateUtils.resolveCustomUiAsset(
                "Icons/ItemsGenerated/Plant_Fruit_Spiral_Tree.png",
                icon);
    }

    private static String resolveFilledSocketIconName() {
        return UITemplateUtils.resolveCustomUiAsset("socket_empty.png", "socket_fille.png", "socket_filled.png");
    }

    private static String resolveBrokenSocketIconName() {
        return UITemplateUtils.resolveCustomUiAsset("socket_empty.png", "socket_broken.png", "socket_Broken.png");
    }

    private static String getSocketColorHex(Socket socket) {
        if (socket == null || socket.isEmpty()) {
            return "#FFFFFF";
        }
        if (socket.isBroken()) {
            return "#B22222";
        }
        String essenceId = socket.getEssenceId();
        if (essenceId == null) {
            return "#FFFFFF";
        }
        try {
            Essence essence = EssenceRegistry.get().getById(essenceId);
            if (essence != null) {
                return switch (essence.getType()) {
                    case FIRE -> "#FFAA00";
                    case ICE -> "#55FFFF";
                    case LIFE -> "#55FF55";
                    case LIGHTNING -> "#FFFF55";
                    case VOID -> "#AA55FF";
                    case WATER -> "#5555FF";
                };
            }
        } catch (Exception ignored) {
        }
        String lower = essenceId.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("fire")) return "#FFAA00";
        if (lower.contains("ice")) return "#55FFFF";
        if (lower.contains("life")) return "#55FF55";
        if (lower.contains("lightning")) return "#FFFF55";
        if (lower.contains("void")) return "#AA55FF";
        if (lower.contains("water")) return "#5555FF";
        return "#FFFFFF";
    }

    private static String resolveEssenceIconName(Socket socket) {
        if (socket == null || socket.isEmpty()) {
            return resolveFilledSocketIconName();
        }
        String essenceId = socket.getEssenceId();
        String itemIcon = resolveIconFromEssenceId(essenceId);
        if (itemIcon != null && !itemIcon.isBlank()) {
            return itemIcon;
        }
        Essence.Type type = null;
        try {
            Essence essence = essenceId == null ? null : EssenceRegistry.get().getById(essenceId);
            if (essence != null) {
                type = essence.getType();
            }
        } catch (Exception ignored) {
        }
        if (type == null && essenceId != null) {
            String lower = essenceId.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("fire")) type = Essence.Type.FIRE;
            else if (lower.contains("ice")) type = Essence.Type.ICE;
            else if (lower.contains("life")) type = Essence.Type.LIFE;
            else if (lower.contains("lightning")) type = Essence.Type.LIGHTNING;
            else if (lower.contains("void")) type = Essence.Type.VOID;
            else if (lower.contains("water")) type = Essence.Type.WATER;
        }
        if (type == null) {
            return resolveFilledSocketIconName();
        }
        String base = "essence_" + type.name().toLowerCase(java.util.Locale.ROOT);
        boolean greater = essenceId != null && SocketManager.isGreaterEssenceId(essenceId);
        if (greater) {
            return UITemplateUtils.resolveCustomUiAsset(
                    resolveFilledSocketIconName(),
                    base + "_concentrated.png",
                    base + "_greater.png",
                    base + "_concentrated_icon.png",
                    base + "_greater_icon.png",
                    base + ".png");
        }
        return UITemplateUtils.resolveCustomUiAsset(
                resolveFilledSocketIconName(),
                base + ".png",
                base + "_icon.png");
    }

    private static String resolveIconFromEssenceId(String essenceId) {
        if (essenceId == null || essenceId.isBlank()) {
            return null;
        }
        String itemId = resolveEssenceItemId(essenceId);
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        try {
            Item item = Item.getAssetMap().getAssetMap().get(itemId);
            if (item == null || item == Item.UNKNOWN) {
                return null;
            }
            String icon = item.getIcon();
            if (icon == null || icon.isBlank()) {
                return null;
            }
            if (Item.UNKNOWN_TEXTURE.equals(icon)) {
                return null;
            }
            String uiIcon = resolveUiIconPath(icon);
            if (uiIcon != null) {
                return uiIcon;
            }
            return icon;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String resolveEssenceItemId(String essenceId) {
        if (essenceId == null || essenceId.isBlank()) {
            return null;
        }
        String cleaned = essenceId.trim();
        if (cleaned.startsWith("Essence_")) {
            cleaned = cleaned.substring("Essence_".length());
        }
        boolean greater = SocketManager.isGreaterEssenceId(essenceId);
        if (greater && cleaned.endsWith("_Concentrated")) {
            cleaned = cleaned.substring(0, cleaned.length() - "_Concentrated".length());
        }
        return "Ingredient_" + cleaned + "_Essence" + (greater ? "_Concentrated" : "");
    }

    private static String resolveUiIconPath(String iconPath) {
        if (iconPath == null || iconPath.isBlank()) {
            return null;
        }
        String normalized = iconPath.replace('\\', '/');
        if (normalized.startsWith("Common/Icons/")) {
            return normalized.substring("Common/".length());
        }
        if (normalized.startsWith("Icons/")) {
            return normalized;
        }
        return null;
    }

    private static void updateMetadataAndProgress(
            Object cmd,
            Class<?> uiCommandClass,
            Player player,
            BenchSnapshot snapshot,
            String selectedValue,
            String supportValue,
            String statusLine) {
        Entry selected = resolveSelection(snapshot.equipments, selectedValue);
        String metadataText;
        int progressValue;
        StatPreview stats;
        if (selected == null) {
            metadataText = LangLoader.getUITranslation(player, "ui.socket_bench.error_no_equipment");
            progressValue = 0;
            stats = new StatPreview("0%", "0%", "0 / 0");
        } else {
            metadataText = buildMetadataText(player, selected.item, selected);
            SocketData socketData = SocketManager.getSocketData(selected.item);
            int maxSockets = socketData != null ? Math.max(1, socketData.getMaxSockets()) : 1;
            int currentSockets = socketData != null ? socketData.getCurrentSocketCount() : 0;
            progressValue = Math.min(100, (int) Math.round((currentSockets * 100.0) / maxSockets));
            stats = calculatePreview(snapshot, selectedValue, supportValue);
        }

        safeSetText(uiCommandClass, cmd, "#metadataLabel", metadataText);
        safeSetInt(uiCommandClass, cmd, "#socketProgress.value", progressValue);
        safeSetText(uiCommandClass, cmd, "#statusLabel", statusLine);
        safeSetText(uiCommandClass, cmd, "#successChanceLabel", stats.successText);
        safeSetText(uiCommandClass, cmd, "#breakChanceLabel", stats.breakText);
        safeSetText(uiCommandClass, cmd, "#socketCountLabel", stats.socketsText);
    }

    private static ProcessResult processSelection(Player player, BenchSnapshot snapshot, Entry equipment, Entry puncher, Entry support) {

        if (equipment == null) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.socket_bench.error_pick_equipment"), 0);
        }
        if (puncher == null) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.socket_bench.error_no_puncher"), 0);
        }

        ItemStack freshEquipment = readCurrentStack(player, equipment);
        if (freshEquipment == null || freshEquipment.isEmpty()) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.socket_bench.error_equipment_missing"), 0);
        }
        if (!ReforgeEquip.isWeapon(freshEquipment) && !ReforgeEquip.isArmor(freshEquipment)) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.socket_bench.error_item_not_equipment"), 0);
        }

        SocketData socketData = SocketManager.getSocketData(freshEquipment);
        if (socketData == null) {
            socketData = SocketData.fromDefaults(ReforgeEquip.isWeapon(freshEquipment) ? "weapon" : "armor");
        }

        SupportMaterial selectedSupport = support == null ? SupportMaterial.NONE : SocketManager.resolveSupportMaterial(support.itemId);
        boolean wantsGhastly = selectedSupport == SupportMaterial.GHASTLY_ESSENCE;
        int loreCurrent = 0;
        int loreMax = 0;
        if (wantsGhastly) {
            if (!LoreSocketManager.isEquipment(freshEquipment)) {
                return new ProcessResult(LangLoader.getUITranslation(player, "ui.socket_bench.error_lore_unsupported"), 0);
            }
            LoreSocketData loreData = LoreSocketManager.getLoreSocketData(freshEquipment);
            if (loreData != null) {
                loreCurrent = Math.max(loreData.getSocketCount(), loreData.getMaxSockets());
            }
            var loreConfig = LoreSocketManager.getConfig();
            loreMax = loreConfig != null ? Math.max(0, loreConfig.getMaxLoreSockets()) : 0;
            if (loreMax <= 0 || loreCurrent >= loreMax) {
                return new ProcessResult(LangLoader.getUITranslation(player, "ui.socket_bench.error_lore_max", loreMax), 100);
            }
        }

        int currentSockets = socketData.getCurrentSocketCount();
        int maxSockets = socketData.getMaxSockets();
        if (!wantsGhastly) {
            int previewMax = maxSockets;
            int previewCurrent = currentSockets;
            int baseMax = SocketManager.getConfig() != null
                    ? (ReforgeEquip.isWeapon(freshEquipment) ? SocketManager.getConfig().getMaxSocketsWeapon() : SocketManager.getConfig().getMaxSocketsArmor())
                    : maxSockets;
            if (selectedSupport == SupportMaterial.SOCKET_EXPANDER) {
                int cap = baseMax > 0 ? baseMax + 1 : maxSockets + 1;
                previewMax = maxSockets >= cap ? maxSockets : Math.min(maxSockets + 1, cap);
            } else if (selectedSupport == SupportMaterial.SOCKET_DIFFUSER) {
                previewMax = Math.max(1, maxSockets - 1);
                previewCurrent = Math.min(previewCurrent, previewMax);
            }
            if (previewCurrent >= previewMax && previewMax == maxSockets) {
                return new ProcessResult(LangLoader.getUITranslation(player, "ui.socket_bench.error_max_sockets", maxSockets), 100);
            }
        }

        if (wantsGhastly) {
            if (support == null) {
                return new ProcessResult(LangLoader.getUITranslation(player, "ui.socket_bench.error_support_missing"), 0);
            }
            ItemStack currentSupport = readCurrentStack(player, support);
            if (currentSupport == null || currentSupport.isEmpty()
                    || currentSupport.getItemId() == null
                    || !currentSupport.getItemId().equalsIgnoreCase(support.itemId)
                    || currentSupport.getQuantity() < 1) {
                return new ProcessResult(LangLoader.getUITranslation(player, "ui.socket_bench.error_support_missing"), 0);
            }
        }

        if (!consumeMaterial(player, puncher, 1)) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.socket_bench.error_puncher_changed"), 0);
        }

        boolean usedSupport = false;
        if (support != null) {
            usedSupport = consumeMaterial(player, support, 1);
        }

        SupportMaterial supportMaterial = usedSupport ? SocketManager.resolveSupportMaterial(support.itemId) : SupportMaterial.NONE;
        if (wantsGhastly && supportMaterial != SupportMaterial.GHASTLY_ESSENCE) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.socket_bench.error_support_missing"), 0);
        }

        if (supportMaterial == SupportMaterial.GHASTLY_ESSENCE) {
            PunchResult loreResult = SocketManager.rollPunchResult(loreCurrent, supportMaterial);
            switch (loreResult) {
                case SUCCESS -> {
                    ItemStack loreUpdated = LoreSocketManager.punchRandomLoreSocket(freshEquipment, null);
                    if (loreUpdated == null || loreUpdated == freshEquipment) {
                        return new ProcessResult(LangLoader.getUITranslation(player, "ui.socket_bench.error_lore_max", loreMax), 100);
                    }
                    writeUpdatedEquipment(player, equipment, loreUpdated, socketData);
                    sfxConfig.playSuccess(player);
                    return new ProcessResult(LangLoader.getUITranslation(player, "ui.socket_bench.status_lore_success"), 100);
                }
                case BREAK -> {
                    sfxConfig.playShatter(player);
                    return new ProcessResult(LangLoader.getUITranslation(player, "ui.socket_bench.status_broke"), 100);
                }
                case FAIL -> {
                    sfxConfig.playFail(player);
                    return new ProcessResult(LangLoader.getUITranslation(player, "ui.socket_bench.status_failed"), 100);
                }
                default -> {
                    sfxConfig.playFail(player);
                    return new ProcessResult(LangLoader.getUITranslation(player, "ui.socket_bench.status_failed"), 100);
                }
            }
        }

        boolean supportAdjusted = SocketManager.applySupportSocketLimit(socketData, supportMaterial, ReforgeEquip.isWeapon(freshEquipment));

        currentSockets = socketData.getCurrentSocketCount();
        maxSockets = socketData.getMaxSockets();
        if (currentSockets >= maxSockets) {
            if (supportAdjusted) {
                writeUpdatedEquipment(player, equipment, freshEquipment, socketData);
                return new ProcessResult(LangLoader.getUITranslation(player, "ui.socket_bench.status_support_applied"), 100);
            }
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.socket_bench.error_max_sockets", maxSockets), 100);
        }

        PunchResult result = SocketManager.punchSocket(socketData, supportMaterial);

        switch (result) {
            case SUCCESS:
                if (currentSockets == 3 && maxSockets == 4) {
                    double bonusChance = SocketManager.getConfig() != null ? SocketManager.getConfig().getBonusSocketChance() : 0.01;
                    if (Math.random() < bonusChance && socketData.getMaxSockets() < 5) {
                        socketData.setMaxSockets(5);
                        if (socketData.canAddSocket()) {
                            socketData.addSocket();
                        }
                    }
                }
                writeUpdatedEquipment(player, equipment, freshEquipment, socketData);
                sfxConfig.playSuccess(player);
                return new ProcessResult(LangLoader.getUITranslation(player, "ui.socket_bench.status_success"), 100);
            case BREAK:
                socketData.breakSocket();
                if (socketData.getMaxSockets() > 1) {
                    double maxReduceChance = SocketManager.getConfig() != null ? SocketManager.getConfig().getMaxReduceChance() : 0.25;
                    if (Math.random() < maxReduceChance) {
                        socketData.reduceMaxSockets();
                    }
                }
                writeUpdatedEquipment(player, equipment, freshEquipment, socketData);
                sfxConfig.playShatter(player);
                return new ProcessResult(LangLoader.getUITranslation(player, "ui.socket_bench.status_broke"), 100);
            case FAIL:
            default:
                if (supportAdjusted) {
                    writeUpdatedEquipment(player, equipment, freshEquipment, socketData);
                }
                sfxConfig.playFail(player);
                return new ProcessResult(LangLoader.getUITranslation(player, "ui.socket_bench.status_failed"), 100);
        }
    }

    private static void writeUpdatedEquipment(Player player, Entry equipment, ItemStack original, SocketData socketData) {
        ItemStack updated = SocketManager.withSocketData(original, socketData);
        UIInventoryUtils.writeItem(player, equipment.containerKind == ContainerKind.HOTBAR, equipment.slot, updated);

        String itemId = updated.getItemId();
        boolean isWeapon = ReforgeEquip.isWeapon(updated);
        socketData.registerTooltips(updated, itemId, isWeapon);
        DynamicTooltipUtils.refreshAllPlayers();
    }

    private static ItemStack readCurrentStack(Player player, Entry entry) {
        ItemStack stack = UIInventoryUtils.readItem(player, entry.containerKind == ContainerKind.HOTBAR, entry.slot);
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return stack;
    }

    private static boolean consumeMaterial(Player player, Entry entry, int amount) {
        if (entry == null || amount <= 0) {
            return false;
        }
        return UIInventoryUtils.consumeItem(
                player,
                entry.containerKind == ContainerKind.HOTBAR,
                entry.slot,
                entry.itemId,
                amount);
    }

    private static ItemContainer getContainer(Player player, ContainerKind kind) {
        return UIInventoryUtils.getContainer(player, kind == ContainerKind.HOTBAR);
    }

    private static Entry resolveSelection(List<Entry> entries, String selectedValue) {
        return HyUIReflectionUtils.resolveIndexSelection(entries, selectedValue);
    }

    private static String supportSelectionKey(String selectedValue, Entry selectedSupport) {
        if ("-1".equals(selectedValue)) {
            return NONE_SUPPORT_KEY;
        }
        return keyOf(selectedSupport);
    }

    private static String buildMetadataText(Player player, ItemStack item, Entry entry) {
        if (item == null || item.isEmpty()) {
            return LangLoader.getUITranslation(player, "ui.socket_bench.metadata_no_item");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(LangLoader.getUITranslation(player, "ui.socket_bench.metadata_header_item")).append("\n");
        sb.append(LangLoader.getUITranslation(player, "ui.socket_bench.metadata_name", entry.displayName)).append("\n");
        sb.append(LangLoader.getUITranslation(player, "ui.socket_bench.metadata_id", item.getItemId())).append("\n");
        String typeLabel = ReforgeEquip.isWeapon(item)
                ? LangLoader.getUITranslation(player, "ui.socket_bench.type_weapon")
                : (ReforgeEquip.isArmor(item)
                    ? LangLoader.getUITranslation(player, "ui.socket_bench.type_armor")
                    : LangLoader.getUITranslation(player, "ui.socket_bench.type_unknown"));
        sb.append(LangLoader.getUITranslation(player, "ui.socket_bench.metadata_type", typeLabel)).append("\n");
        sb.append(LangLoader.getUITranslation(player, "ui.socket_bench.metadata_inventory",
                locationText(player, entry), entry.slot)).append("\n\n");

        SocketData socketData = SocketManager.getSocketData(item);
        if (socketData != null) {
            int broken = 0;
            int filled = 0;
            for (Socket socket : socketData.getSockets()) {
                if (socket.isBroken()) broken++;
                else if (!socket.isEmpty()) filled++;
            }
            sb.append(LangLoader.getUITranslation(player, "ui.socket_bench.metadata_header_sockets")).append("\n");
            sb.append(LangLoader.getUITranslation(player, "ui.socket_bench.metadata_current_max",
                    socketData.getCurrentSocketCount(), socketData.getMaxSockets())).append("\n");
            sb.append(LangLoader.getUITranslation(player, "ui.socket_bench.metadata_filled_broken",
                    filled, broken)).append("\n\n");
        } else {
            sb.append(LangLoader.getUITranslation(player, "ui.socket_bench.metadata_header_sockets")).append("\n");
            sb.append(LangLoader.getUITranslation(player, "ui.socket_bench.metadata_na")).append("\n\n");
        }

        BsonDocument metadata = item.getMetadata();
        if (metadata != null && !metadata.isEmpty()) {
            sb.append(LangLoader.getUITranslation(player, "ui.socket_bench.metadata_header_metadata")).append("\n");
            sb.append(LangLoader.getUITranslation(player, "ui.socket_bench.metadata_keys", metadata.keySet().size())).append("\n");
            int shown = 0;
            for (String key : metadata.keySet()) {
                if (shown >= 6) break;
                sb.append("- ").append(key).append("\n");
                shown++;
            }
        } else {
            sb.append(LangLoader.getUITranslation(player, "ui.socket_bench.metadata_header_metadata")).append("\n");
            sb.append(LangLoader.getUITranslation(player, "ui.socket_bench.metadata_none"));
        }
        return sb.toString();
    }

    private static String locationText(Player player, Entry entry) {
        return entry.containerKind == ContainerKind.HOTBAR
                ? LangLoader.getUITranslation(player, "ui.socket_bench.location_hotbar")
                : LangLoader.getUITranslation(player, "ui.socket_bench.location_storage");
    }

    private static String extractEventValue(Object eventObj) {
        return HyUIReflectionUtils.extractEventValue(eventObj);
    }

    private static String getContextValue(Object ctxObj, String... keys) {
        return HyUIReflectionUtils.getContextValue(ctxObj, keys);
    }

    private static StatPreview calculatePreview(BenchSnapshot snapshot, String equipmentValue, String supportValue) {
        Entry equipment = resolveSelection(snapshot.equipments, equipmentValue);
        Entry support = resolveSelection(snapshot.supports, supportValue);
        if (equipment == null) {
            return new StatPreview("0%", "0%", "0 / 0");
        }

        SocketData data = SocketManager.getSocketData(equipment.item);
        if (data == null) {
            data = SocketData.fromDefaults(ReforgeEquip.isWeapon(equipment.item) ? "weapon" : "armor");
        }

        SupportMaterial supportMaterial = support == null ? SupportMaterial.NONE : SocketManager.resolveSupportMaterial(support.itemId);
        if (supportMaterial == SupportMaterial.GHASTLY_ESSENCE) {
            LoreSocketData loreData = LoreSocketManager.getLoreSocketData(equipment.item);
            int loreCurrent = 0;
            if (loreData != null) {
                loreCurrent = Math.max(loreData.getSocketCount(), loreData.getMaxSockets());
            }
            var loreConfig = LoreSocketManager.getConfig();
            int loreMax = loreConfig != null ? Math.max(0, loreConfig.getMaxLoreSockets()) : 0;
            double success = SocketManager.getConfig() != null ? SocketManager.getConfig().getSuccessChance(loreCurrent) : 0.75;
            double breakChance = SocketManager.getConfig() != null ? SocketManager.getConfig().getBreakChance(loreCurrent) : 0.10;
            String successText = String.format(java.util.Locale.ROOT, "%.0f%%", success * 100.0);
            String breakText = String.format(java.util.Locale.ROOT, "%.0f%%", breakChance * 100.0);
            String socketsText = loreCurrent + " / " + Math.max(0, loreMax);
            return new StatPreview(successText, breakText, socketsText);
        }
        int current = data.getCurrentSocketCount();
        int max = data.getMaxSockets();
        int previewMax = max;
        int previewCurrent = current;
        int baseMax = SocketManager.getConfig() != null
                ? (ReforgeEquip.isWeapon(equipment.item) ? SocketManager.getConfig().getMaxSocketsWeapon() : SocketManager.getConfig().getMaxSocketsArmor())
                : max;
        if (supportMaterial == SupportMaterial.SOCKET_EXPANDER) {
            int cap = baseMax > 0 ? baseMax + 1 : max + 1;
            previewMax = max >= cap ? max : Math.min(max + 1, cap);
        } else if (supportMaterial == SupportMaterial.SOCKET_DIFFUSER) {
            previewMax = Math.max(1, max - 1);
            previewCurrent = Math.min(previewCurrent, previewMax);
        }

        double success = SocketManager.getConfig() != null ? SocketManager.getConfig().getSuccessChance(previewCurrent) : 0.75;
        double breakChance = SocketManager.getConfig() != null ? SocketManager.getConfig().getBreakChance(previewCurrent) : 0.10;
        switch (supportMaterial) {
            case SOCKET_STABILIZER -> breakChance *= 0.50;
            case SOCKET_REINFORCER -> success = Math.min(1.0, success + 0.20);
            case SOCKET_GUARANTOR -> {
                if (previewCurrent == 0) {
                    success = 1.0;
                }
            }
            default -> {
                // NONE / SOCKET_EXPANDER / SOCKET_DIFFUSER: no success modifier
            }
        }

        String successText = String.format(java.util.Locale.ROOT, "%.0f%%", success * 100.0);
        String breakText = String.format(java.util.Locale.ROOT, "%.0f%%", breakChance * 100.0);
        String socketsText = previewCurrent + " / " + previewMax;
        return new StatPreview(successText, breakText, socketsText);
    }

    private static void setMessage(Class<?> uiCommandClass, Object commandBuilder, String selector, String text) throws Exception {
        Method setMessage = uiCommandClass.getMethod("set", String.class, Message.class);
        setMessage.invoke(commandBuilder, selector, Message.raw(text == null ? "" : text));
    }

    private static void setObject(Class<?> uiCommandClass, Object commandBuilder, String selector, Object value) throws Exception {
        Method setObject = uiCommandClass.getMethod("setObject", String.class, Object.class);
        setObject.invoke(commandBuilder, selector, value);
    }

    private static void setInt(Class<?> uiCommandClass, Object commandBuilder, String selector, int value) throws Exception {
        Method setInt = uiCommandClass.getMethod("set", String.class, int.class);
        setInt.invoke(commandBuilder, selector, value);
    }

    private static void safeSetObject(Class<?> uiCommandClass, Object commandBuilder, String selector, Object value) {
        try {
            setObject(uiCommandClass, commandBuilder, selector, value);
        } catch (Exception ignored) {
        }
    }

    private static void safeSetInt(Class<?> uiCommandClass, Object commandBuilder, String selector, int value) {
        try {
            setInt(uiCommandClass, commandBuilder, selector, value);
            return;
        } catch (Exception ignored) {
        }
        try {
            Method setFloat = uiCommandClass.getMethod("set", String.class, float.class);
            setFloat.invoke(commandBuilder, selector, (float) value);
            return;
        } catch (Exception ignored) {
        }
        try {
            Method setDouble = uiCommandClass.getMethod("set", String.class, double.class);
            setDouble.invoke(commandBuilder, selector, (double) value);
            return;
        } catch (Exception ignored) {
        }
        safeSetObject(uiCommandClass, commandBuilder, selector, String.valueOf(value));
    }

    private static void safeSetText(Class<?> uiCommandClass, Object commandBuilder, String selector, String text) {
        String safe = text == null ? "" : text;
        try {
            setMessage(uiCommandClass, commandBuilder, selector, safe);
            return;
        } catch (Exception ignored) {
        }

        // Fallback path for element implementations that expect TextSpans instead of direct Message.
        try {
            Class<?> chatMessageClass = Class.forName("com.hypixel.hytale.chat.Message");
            Method raw = chatMessageClass.getMethod("raw", String.class);
            Object chatMessage = raw.invoke(null, safe);
            setObject(uiCommandClass, commandBuilder, selector + ".TextSpans", chatMessage);
            return;
        } catch (Exception ignored) {
        }

        try {
            setObject(uiCommandClass, commandBuilder, selector, safe);
        } catch (Exception ignored) {
        }
    }

    private static void sendUpdate(Object ctxObj, Object cmdBuilder) {
        if (ctxObj == null) {
            return;
        }

        try {
            for (Method method : ctxObj.getClass().getMethods()) {
                if (method.getName().equals("sendUpdate") && method.getParameterCount() == 1) {
                    method.invoke(ctxObj, cmdBuilder);
                    return;
                }
            }
        } catch (Exception ignored) {
        }

        try {
            Method updatePage = ctxObj.getClass().getMethod("updatePage", boolean.class);
            // In HyUI 0.9, forcing true can rebuild the whole page and reset dropdowns.
            // false acknowledges the event without full refresh.
            updatePage.invoke(ctxObj, false);
        } catch (Exception e) {
            System.err.println("[SocketReforge] SocketBenchUI: unable to update page: " + e.getMessage());
        }
    }

    private static Object getStore(PlayerRef playerRef) throws Exception {
        return HyUIReflectionUtils.getStore(playerRef);
    }

    private static void closePageIfOpen(PlayerRef playerRef) {
        HyUIReflectionUtils.closePageIfOpen(openPages, playerRef);
    }

    private static String escapeHtml(String text) {
        return UITemplateUtils.escapeHtml(text);
    }

    private static final class ProcessResult {
        final String statusLine;
        final int progressValue;

        ProcessResult(String statusLine, int progressValue) {
            this.statusLine = statusLine;
            this.progressValue = progressValue;
        }
    }

    private static final class StatPreview {
        final String successText;
        final String breakText;
        final String socketsText;

        StatPreview(String successText, String breakText, String socketsText) {
            this.successText = successText;
            this.breakText = breakText;
            this.socketsText = socketsText;
        }
    }
}
