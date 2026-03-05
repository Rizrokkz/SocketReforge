package irai.mod.reforge.UI;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import org.bson.BsonDocument;

import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import irai.mod.reforge.Config.SFXConfig;
import irai.mod.reforge.Interactions.ReforgeEquip;
import irai.mod.reforge.Socket.Socket;
import irai.mod.reforge.Socket.SocketData;
import irai.mod.reforge.Socket.SocketManager;
import irai.mod.reforge.Socket.SocketManager.PunchResult;
import irai.mod.reforge.Socket.SocketManager.SupportMaterial;
import irai.mod.reforge.Util.DynamicTooltipUtils;
import irai.mod.reforge.Util.NameResolver;

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
    private static final String SUPPORT_ITEM_ID = "Socket_Stabilizer";

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
        try {
            Class.forName(HYUI_PAGE_BUILDER);
            Class.forName(HYUI_PLUGIN);
            hyuiAvailable = true;
            System.out.println("[SocketReforge] SocketBenchUI: HyUI loaded.");
        } catch (ClassNotFoundException e) {
            hyuiAvailable = false;
            System.out.println("[SocketReforge] SocketBenchUI: HyUI unavailable.");
        }
    }

    public static boolean isAvailable() {
        return hyuiAvailable;
    }

    public static void open(Player player) {
        if (player == null) {
            return;
        }
        if (!hyuiAvailable) {
            player.sendMessage(Message.raw("<color=#FF5555>HyUI not installed - socket bench UI disabled."));
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

        collectFromContainer(player.getInventory().getHotbar(), ContainerKind.HOTBAR, equipments, punchers, supports);
        collectFromContainer(player.getInventory().getStorage(), ContainerKind.STORAGE, equipments, punchers, supports);
        return new BenchSnapshot(equipments, punchers, supports);
    }

    private static void collectFromContainer(
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

            String name = NameResolver.getDisplayName(stack);
            if (name == null || name.isEmpty() || "Unknown Item".equals(name)) {
                name = itemId;
            }

            Entry entry = new Entry(kind, slot, stack, itemId, stack.getQuantity(), name);

            if (ReforgeEquip.isWeapon(stack) || ReforgeEquip.isArmor(stack)) {
                // Only scanning hotbar/storage so equipped armor is naturally excluded.
                equipments.add(entry);
            }

            if (PUNCHER_ITEM_ID.equalsIgnoreCase(itemId)) {
                punchers.add(entry);
            }
            if (SUPPORT_ITEM_ID.equalsIgnoreCase(itemId)) {
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

            String html = buildHtml(snapshot, selectionState);
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
                                            keyOf(selectedSupport),
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
                                            keyOf(selectedSupport),
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
                            processingPlayers.put(finalPlayer.getPlayerRef(), true);
                            pendingSelections.put(
                                    finalPlayer.getPlayerRef(),
                                    new SelectionState(
                                            keyOf(selectedEquipment),
                                            keyOf(selectedPuncher),
                                            keyOf(selectedSupport),
                                            "Processing...",
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
                                                    keyOf(selectedSupport),
                                                    "Processing...",
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
                                                    keyOf(selectedSupport),
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

    private static String buildHtml(BenchSnapshot snapshot, SelectionState selectionState) {
        String selectedEquipmentKey = selectionState != null ? selectionState.equipmentKey : null;
        String selectedPuncherKey = selectionState != null ? selectionState.puncherKey : null;
        String selectedSupportKey = selectionState != null ? selectionState.supportKey : null;
        String statusText = selectionState != null && selectionState.statusText != null ? selectionState.statusText : "Idle";
        int progressValue = selectionState != null ? Math.max(0, Math.min(100, selectionState.progressValue)) : 0;
        boolean isProcessing = selectionState != null && selectionState.processing;
        if (!isProcessing) {
            progressValue = 0;
        }
        String equipmentOptions = buildEquipmentOptions(snapshot.equipments, selectedEquipmentKey);
        String puncherOptions = buildMaterialOptions(snapshot.punchers, "No Socket Puncher found", selectedPuncherKey);
        String supportOptions = buildSupportOptions(snapshot.supports, selectedSupportKey);

        Entry selectedEquipment = findByKey(snapshot.equipments, selectedEquipmentKey);
        Entry selectedSupport = findByKey(snapshot.supports, selectedSupportKey);
        boolean socketsMaxed = isSocketsMaxed(selectedEquipment);
        if (!isProcessing && "Idle".equals(statusText) && socketsMaxed) {
            statusText = "Max sockets reached.";
        }
        String processDisabledAttr = (isProcessing || socketsMaxed)
                ? "disabled data-hyui-disabled=\"true\""
                : "";
        String defaultMetadata = selectedEquipment == null
                ? "Select an equipment from the dropdown to view metadata."
                : buildMetadataText(selectedEquipment.item, selectedEquipment);
        String socketIcons = buildSocketIconsHtml(selectedEquipment);

        String selectedEquipmentValue = selectedEquipment == null ? "-1" : String.valueOf(snapshot.equipments.indexOf(selectedEquipment));
        String selectedSupportValue = selectedSupport == null ? "-1" : String.valueOf(snapshot.supports.indexOf(selectedSupport));
        StatPreview defaultStats = calculatePreview(snapshot, selectedEquipmentValue, selectedSupportValue);

        String template = loadTemplate(SOCKET_BENCH_TEMPLATE_PATH);
        if (template != null && !template.isBlank()) {
            System.out.println("[SocketReforge] SocketBenchUI using external HTML: " + SOCKET_BENCH_TEMPLATE_PATH);
            return template
                    .replace("{{equipmentOptions}}", equipmentOptions)
                    .replace("{{puncherOptions}}", puncherOptions)
                    .replace("{{supportOptions}}", supportOptions)
                    .replace("{{successText}}", escapeHtml(defaultStats.successText))
                    .replace("{{breakText}}", escapeHtml(defaultStats.breakText))
                    .replace("{{socketsText}}", escapeHtml(defaultStats.socketsText))
                    .replace("{{socketIcons}}", socketIcons)
                    .replace("{{progressValue}}", String.valueOf(progressValue))
                    .replace("{{statusText}}", escapeHtml(statusText))
                    .replace("{{processDisabledAttr}}", processDisabledAttr)
                    .replace("{{metadataText}}", escapeHtml(defaultMetadata));
        }

        System.out.println("[SocketReforge] SocketBenchUI using inline fallback HTML.");
        return "<div class=\"page-overlay\">"
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
                + "</div>";
    }

    private static String loadTemplate(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        // Dev-first: read directly from workspace resources so HTML edits apply immediately.
        try {
            Path fsPath = Paths.get("src", "main", "resources").resolve(path.replace("/", java.io.File.separator));
            if (Files.exists(fsPath) && Files.isRegularFile(fsPath)) {
                String html = Files.readString(fsPath, StandardCharsets.UTF_8);
                System.out.println("[SocketReforge] SocketBenchUI template loaded from filesystem: " + fsPath.toAbsolutePath());
                return html;
            }
            System.out.println("[SocketReforge] SocketBenchUI filesystem template not found: " + fsPath.toAbsolutePath());
        } catch (Exception e) {
            System.err.println("[SocketReforge] SocketBenchUI filesystem template load failed: " + e.getMessage());
        }

        try (InputStream in = SocketBenchUI.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                System.err.println("[SocketReforge] SocketBenchUI template missing on classpath: " + path);
                return null;
            }
            System.out.println("[SocketReforge] SocketBenchUI template found on classpath: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[SocketReforge] SocketBenchUI template load failed: " + e.getMessage());
            return null;
        }
    }

    private static String buildEquipmentOptions(List<Entry> equipments, String selectedKey) {
        if (equipments.isEmpty()) {
            return "<option value=\"-1\" selected>No valid equipment found</option>";
        }
        StringBuilder sb = new StringBuilder();
        boolean hasSelected = selectedKey != null && findByKey(equipments, selectedKey) != null;
        sb.append("<option value=\"-1\"").append(hasSelected ? "" : " selected").append(">Select equipment...</option>");
        for (int i = 0; i < equipments.size(); i++) {
            Entry e = equipments.get(i);
            String tag = ReforgeEquip.isWeapon(e.item) ? "Weapon" : (ReforgeEquip.isArmor(e.item) ? "Armor" : "Item");
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

    private static String buildMaterialOptions(List<Entry> entries, String emptyText, String selectedKey) {
        if (entries.isEmpty()) {
            return "<option value=\"-1\">" + escapeHtml(emptyText) + "</option>";
        }
        StringBuilder sb = new StringBuilder();
        boolean hasSelected = selectedKey != null && findByKey(entries, selectedKey) != null;
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            String label = e.itemId + " x" + e.quantity + " (" + locationText(e) + " slot " + e.slot + ")";
            boolean isSelected = hasSelected ? selectedKey.equals(keyOf(e)) : i == 0;
            sb.append("<option value=\"").append(i).append("\"")
                    .append(isSelected ? " selected" : "")
                    .append(">")
                    .append(escapeHtml(label))
                    .append("</option>");
        }
        return sb.toString();
    }

    private static String buildSupportOptions(List<Entry> supports, String selectedKey) {
        StringBuilder sb = new StringBuilder();
        boolean hasSelected = selectedKey != null && findByKey(supports, selectedKey) != null;
        boolean selectNone = supports.isEmpty() || (!hasSelected && selectedKey != null && selectedKey.equals("NONE"));
        sb.append("<option value=\"-1\"").append(selectNone ? " selected" : "").append(">None</option>");
        for (int i = 0; i < supports.size(); i++) {
            Entry e = supports.get(i);
            String label = e.itemId + " x" + e.quantity + " (" + locationText(e) + " slot " + e.slot + ")";
            boolean isSelected = hasSelected ? selectedKey.equals(keyOf(e)) : (supports.size() > 0 && i == 0);
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

    private static String buildSocketIconsHtml(Entry equipment) {
        if (equipment == null || equipment.item == null || equipment.item.isEmpty()) {
            return "<div style=\"layout-mode: Left; spacing: 20;\"><div style=\"flex-weight:1;\"></div><p style=\"color:#AAAAAA;\">Select equipment</p><div style=\"flex-weight:1;\"></div></div>";
        }

        SocketData socketData = SocketManager.getSocketData(equipment.item);
        if (socketData == null) {
            socketData = SocketData.fromDefaults(ReforgeEquip.isWeapon(equipment.item) ? "weapon" : "armor");
        }

        int maxSockets = Math.max(0, socketData.getMaxSockets());
        int punchedSockets = socketData.getCurrentSocketCount();
        List<Socket> sockets = socketData.getSockets();
        String filledIconName = resolveFilledSocketIconName();
        String brokenIconName = resolveBrokenSocketIconName();

        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"layout-mode: Left; spacing: 14;\"><div style=\"flex-weight:1;\"></div>");
        for (int i = 0; i < maxSockets; i++) {
            boolean isPunched = i < punchedSockets;
            Socket socket = i < sockets.size() ? sockets.get(i) : null;
            boolean isBroken = socket != null && socket.isBroken();
            boolean isFilled = socket != null && !socket.isBroken() && !socket.isEmpty();

            String tileStyle = isBroken
                    ? "padding:10; background-color:#b22222;"
                    : "padding:10; background-color:#ffffff;";

            // Filled sockets use filled icon.
            // Broken sockets use broken icon.
            // Empty sockets use empty icon.
            String icon = isBroken
                    ? brokenIconName
                    : (!isPunched ? "slot_bg.png" : (isFilled ? filledIconName : "socket_empty.png"));

            sb.append("<div style=\"").append(tileStyle).append("\">")
                    .append("<img src=\"").append(icon).append("\" width=\"90\" height=\"90\"/>")
                    .append("</div>");
        }
        sb.append("<div style=\"flex-weight:1;\"></div></div>");
        return sb.toString();
    }

    private static String resolveFilledSocketIconName() {
        // Prefer the user-requested file name first.
        String preferred = "socket_filled.png";
        String fallback = "socket_filled.png";

        try {
            Path preferredFs = Paths.get("src", "main", "resources", "Common", "UI", "Custom", preferred);
            if (Files.exists(preferredFs) && Files.isRegularFile(preferredFs)) {
                return preferred;
            }
        } catch (Exception ignored) {
        }
        try (InputStream in = SocketBenchUI.class.getClassLoader().getResourceAsStream("Common/UI/Custom/" + preferred)) {
            if (in != null) {
                return preferred;
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private static String resolveBrokenSocketIconName() {
        String[] candidates = {"socket_broken.png", "socket_Broken.png"};
        for (String candidate : candidates) {
            try {
                Path fs = Paths.get("src", "main", "resources", "Common", "UI", "Custom", candidate);
                if (Files.exists(fs) && Files.isRegularFile(fs)) {
                    return candidate;
                }
            } catch (Exception ignored) {
            }
            try (InputStream in = SocketBenchUI.class.getClassLoader().getResourceAsStream("Common/UI/Custom/" + candidate)) {
                if (in != null) {
                    return candidate;
                }
            } catch (Exception ignored) {
            }
        }
        return "socket_empty.png";
    }

    private static void updateMetadataAndProgress(
            Object cmd,
            Class<?> uiCommandClass,
            BenchSnapshot snapshot,
            String selectedValue,
            String supportValue,
            String statusLine) {
        Entry selected = resolveSelection(snapshot.equipments, selectedValue);
        String metadataText;
        int progressValue;
        StatPreview stats;
        if (selected == null) {
            metadataText = "No equipment selected.";
            progressValue = 0;
            stats = new StatPreview("0%", "0%", "0 / 0");
        } else {
            metadataText = buildMetadataText(selected.item, selected);
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
            return new ProcessResult("Pick a valid equipment first.", 0);
        }
        if (puncher == null) {
            return new ProcessResult("No valid Socket Puncher selected.", 0);
        }

        ItemStack freshEquipment = readCurrentStack(player, equipment);
        if (freshEquipment == null || freshEquipment.isEmpty()) {
            return new ProcessResult("Selected equipment is no longer available.", 0);
        }
        if (!ReforgeEquip.isWeapon(freshEquipment) && !ReforgeEquip.isArmor(freshEquipment)) {
            return new ProcessResult("Selected item is not valid equipment.", 0);
        }

        SocketData socketData = SocketManager.getSocketData(freshEquipment);
        if (socketData == null) {
            socketData = SocketData.fromDefaults(ReforgeEquip.isWeapon(freshEquipment) ? "weapon" : "armor");
        }

        int currentSockets = socketData.getCurrentSocketCount();
        int maxSockets = socketData.getMaxSockets();
        if (currentSockets >= maxSockets) {
            return new ProcessResult("Item already has max sockets (" + maxSockets + ").", 100);
        }

        if (!consumeMaterial(player, puncher, 1)) {
            return new ProcessResult("Socket Puncher stack changed; reselect and try again.", 0);
        }

        boolean usedSupport = false;
        if (support != null) {
            usedSupport = consumeMaterial(player, support, 1);
        }

        SupportMaterial supportMaterial = usedSupport ? SupportMaterial.SOCKET_STABILIZER : SupportMaterial.NONE;
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
                return new ProcessResult("Success: socket punched.", 100);
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
                return new ProcessResult("Result: socket broke.", 100);
            case FAIL:
            default:
                sfxConfig.playFail(player);
                return new ProcessResult("Process failed. No socket added.", 100);
        }
    }

    private static void writeUpdatedEquipment(Player player, Entry equipment, ItemStack original, SocketData socketData) {
        ItemStack updated = SocketManager.withSocketData(original, socketData);
        ItemContainer container = getContainer(player, equipment.containerKind);
        if (container != null) {
            container.setItemStackForSlot(equipment.slot, updated);
        }

        String itemId = updated.getItemId();
        boolean isWeapon = ReforgeEquip.isWeapon(updated);
        socketData.registerTooltips(updated, itemId, isWeapon);
        DynamicTooltipUtils.refreshAllPlayers();
    }

    private static ItemStack readCurrentStack(Player player, Entry entry) {
        ItemContainer container = getContainer(player, entry.containerKind);
        if (container == null) {
            return null;
        }
        return container.getItemStack(entry.slot);
    }

    private static boolean consumeMaterial(Player player, Entry entry, int amount) {
        if (entry == null || amount <= 0) {
            return false;
        }
        ItemContainer container = getContainer(player, entry.containerKind);
        if (container == null) {
            return false;
        }
        ItemStack stack = container.getItemStack(entry.slot);
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (!entry.itemId.equalsIgnoreCase(stack.getItemId())) {
            return false;
        }
        if (stack.getQuantity() < amount) {
            return false;
        }
        container.removeItemStackFromSlot(entry.slot, amount, false, false);
        return true;
    }

    private static ItemContainer getContainer(Player player, ContainerKind kind) {
        if (player == null || player.getInventory() == null) {
            return null;
        }
        return kind == ContainerKind.HOTBAR ? player.getInventory().getHotbar() : player.getInventory().getStorage();
    }

    private static Entry resolveSelection(List<Entry> entries, String selectedValue) {
        if (selectedValue == null) {
            return null;
        }
        try {
            int idx = Integer.parseInt(selectedValue.trim());
            if (idx < 0 || idx >= entries.size()) {
                return null;
            }
            return entries.get(idx);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String buildMetadataText(ItemStack item, Entry entry) {
        if (item == null || item.isEmpty()) {
            return "No item selected.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[Item]\n");
        sb.append("Name: ").append(entry.displayName).append("\n");
        sb.append("Id: ").append(item.getItemId()).append("\n");
        sb.append("Type: ").append(ReforgeEquip.isWeapon(item) ? "Weapon" : (ReforgeEquip.isArmor(item) ? "Armor" : "Unknown")).append("\n");
        sb.append("Inventory: ").append(locationText(entry)).append(" slot ").append(entry.slot).append("\n\n");

        SocketData socketData = SocketManager.getSocketData(item);
        if (socketData != null) {
            int broken = 0;
            int filled = 0;
            for (Socket socket : socketData.getSockets()) {
                if (socket.isBroken()) broken++;
                else if (!socket.isEmpty()) filled++;
            }
            sb.append("[Sockets]\n");
            sb.append("Current/Max: ")
                    .append(socketData.getCurrentSocketCount())
                    .append("/")
                    .append(socketData.getMaxSockets())
                    .append("\nFilled: ")
                    .append(filled)
                    .append("   Broken: ")
                    .append(broken)
                    .append("\n\n");
        } else {
            sb.append("[Sockets]\nN/A\n\n");
        }

        BsonDocument metadata = item.getMetadata();
        if (metadata != null && !metadata.isEmpty()) {
            sb.append("[Metadata]\n");
            sb.append("Keys: ").append(metadata.keySet().size()).append("\n");
            int shown = 0;
            for (String key : metadata.keySet()) {
                if (shown >= 6) break;
                sb.append("- ").append(key).append("\n");
                shown++;
            }
        } else {
            sb.append("[Metadata]\nnone");
        }
        return sb.toString();
    }

    private static String locationText(Entry entry) {
        return entry.containerKind == ContainerKind.HOTBAR ? "Hotbar" : "Storage";
    }

    private static String extractEventValue(Object eventObj) {
        if (eventObj == null) {
            return null;
        }
        try {
            Method getValue = eventObj.getClass().getMethod("getValue");
            Object value = getValue.invoke(eventObj);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            return eventObj.toString();
        }
    }

    private static String getContextValue(Object ctxObj, String... keys) {
        if (ctxObj == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            try {
                Method getValue = ctxObj.getClass().getMethod("getValue", String.class);
                Object optObj = getValue.invoke(ctxObj, key);
                if (!(optObj instanceof Optional<?> optional)) {
                    continue;
                }
                if (optional.isEmpty()) {
                    continue;
                }
                Object value = optional.get();
                if (value != null) {
                    return value.toString();
                }
            } catch (Exception ignored) {
            }
        }
        return null;
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

        int current = data.getCurrentSocketCount();
        int max = data.getMaxSockets();
        double success = SocketManager.getConfig() != null ? SocketManager.getConfig().getSuccessChance(current) : 0.75;
        double breakChance = SocketManager.getConfig() != null ? SocketManager.getConfig().getBreakChance(current) : 0.10;
        if (support != null) {
            success = Math.min(1.0, success + 0.15);
            breakChance = Math.max(0.0, breakChance - 0.05);
        }

        String successText = String.format(java.util.Locale.ROOT, "%.0f%%", success * 100.0);
        String breakText = String.format(java.util.Locale.ROOT, "%.0f%%", breakChance * 100.0);
        String socketsText = current + " / " + max;
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
        Method getReference = playerRef.getClass().getMethod("getReference");
        Object ref = getReference.invoke(playerRef);
        Method getStore = ref.getClass().getMethod("getStore");
        return getStore.invoke(ref);
    }

    private static void closePageIfOpen(PlayerRef playerRef) {
        Object page = openPages.remove(playerRef);
        if (page == null) {
            return;
        }
        try {
            page.getClass().getMethod("close").invoke(page);
        } catch (Exception ignored) {
        }
    }

    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
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
