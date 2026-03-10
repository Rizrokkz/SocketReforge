package irai.mod.reforge.UI;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Map;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;

import irai.mod.reforge.Common.UI.HyUIReflectionUtils;
import irai.mod.reforge.Common.UI.UIInventoryUtils;
import irai.mod.reforge.Common.UI.UIItemUtils;
import irai.mod.reforge.Common.UI.UITemplateUtils;
import irai.mod.reforge.Config.SFXConfig;
import irai.mod.reforge.Interactions.ReforgeEquip;
import irai.mod.reforge.Socket.Essence;
import irai.mod.reforge.Socket.EssenceRegistry;
import irai.mod.reforge.Socket.Socket;
import irai.mod.reforge.Socket.SocketData;
import irai.mod.reforge.Socket.SocketManager;
import irai.mod.reforge.Util.DynamicTooltipUtils;

/**
 * HyUI essence socketing page, with reflection-based loading so plugin still works
 * when HyUI is not installed.
 */
public final class EssenceBenchUI {
    private EssenceBenchUI() {}

    private static final String HYUI_PAGE_BUILDER  = "au.ellie.hyui.builders.PageBuilder";
    private static final String HYUI_PLUGIN        = "au.ellie.hyui.HyUIPlugin";
    private static final String HYUI_EVENT_BINDING = "com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType";
    private static final String UI_COMMAND_BUILDER = "com.hypixel.hytale.server.core.ui.builder.UICommandBuilder";
    private static final String TEMPLATE_PATH = "Common/UI/Custom/Pages/EssenceBench.html";

    private static final String[] ESSENCE_ITEM_IDS = {
            "Ingredient_Fire_Essence",
            "Ingredient_Ice_Essence",
            "Ingredient_Life_Essence",
            "Ingredient_Lightning_Essence",
            "Ingredient_Void_Essence",
            "Ingredient_Water_Essence",
            "Ingredient_Fire_Essence_Concentrated",
            "Ingredient_Ice_Essence_Concentrated",
            "Ingredient_Life_Essence_Concentrated",
            "Ingredient_Lightning_Essence_Concentrated",
            "Ingredient_Void_Essence_Concentrated",
            "Ingredient_Water_Essence_Concentrated"
    };
    private static final String VOIDHEART_ID = "Ingredient_Voidheart";
    private static final String HAMMER_ID = "Tool_Hammer_Iron";
    private static final String HAMMER_THORIUM_ID = "Tool_Hammer_Thorium";
    private static final double THORIUM_MAX_SOCKET_REDUCE_CHANCE = 0.02d;

    private static final SFXConfig sfxConfig = new SFXConfig();
    private static boolean hyuiAvailable = false;

    private static final Map<PlayerRef, Object> openPages = new ConcurrentHashMap<>();
    private static final Map<PlayerRef, SelectionState> pendingSelections = new ConcurrentHashMap<>();
    private static final Map<PlayerRef, Boolean> processingPlayers = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final int PROCESS_DURATION_MS = 1000;
    private static final int PROGRESS_TICK_MS = 50;

    private enum ContainerKind {
        HOTBAR,
        STORAGE
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
        final List<Entry> essences;
        final List<Entry> voidhearts;

        Snapshot(List<Entry> equipments, List<Entry> essences, List<Entry> voidhearts) {
            this.equipments = equipments;
            this.essences = essences;
            this.voidhearts = voidhearts;
        }
    }

    private static final class SelectionState {
        final String equipmentKey;
        final String essenceKey;
        final String supportKey;
        final String statusText;
        final int progressValue;
        final boolean processing;

        SelectionState(String equipmentKey, String essenceKey, String supportKey, String statusText, int progressValue, boolean processing) {
            this.equipmentKey = equipmentKey;
            this.essenceKey = essenceKey;
            this.supportKey = supportKey;
            this.statusText = statusText;
            this.progressValue = progressValue;
            this.processing = processing;
        }
    }

    private static final class ProcessResult {
        final String status;
        final int progress;

        ProcessResult(String status, int progress) {
            this.status = status;
            this.progress = progress;
        }
    }

    private static final class HammerUseResult {
        final boolean ok;
        final boolean consumed;

        HammerUseResult(boolean ok, boolean consumed) {
            this.ok = ok;
            this.consumed = consumed;
        }
    }

    public static void initialize() {
        hyuiAvailable = HyUIReflectionUtils.detectHyUi(HYUI_PAGE_BUILDER, HYUI_PLUGIN, "EssenceBenchUI");
    }

    public static boolean isAvailable() {
        return hyuiAvailable;
    }

    public static void open(Player player) {
        if (player == null) return;
        if (!hyuiAvailable) {
            player.sendMessage(Message.raw("<color=#FF5555>HyUI not installed - essence bench UI disabled."));
            return;
        }
        PlayerRef ref = player.getPlayerRef();
        closePageIfOpen(ref);
        // Fresh open should not keep last temporary UI selection state.
        pendingSelections.remove(ref);
        processingPlayers.remove(ref);
        player.getWorld().execute(() -> openWithSync(player));
    }

    private static void openWithSync(Player player) {
        Snapshot snapshot = collectSnapshot(player);
        SelectionState state = pendingSelections.remove(player.getPlayerRef());
        openPage(player, snapshot, state);
    }

    private static Snapshot collectSnapshot(Player player) {
        List<Entry> equipments = new ArrayList<>();
        List<Entry> essences = new ArrayList<>();
        List<Entry> voidhearts = new ArrayList<>();
        collectFromContainer(player.getInventory().getHotbar(), ContainerKind.HOTBAR, equipments, essences, voidhearts);
        collectFromContainer(player.getInventory().getStorage(), ContainerKind.STORAGE, equipments, essences, voidhearts);
        return new Snapshot(equipments, essences, voidhearts);
    }

    private static void collectFromContainer(
            ItemContainer container,
            ContainerKind kind,
            List<Entry> equipments,
            List<Entry> essences,
            List<Entry> voidhearts) {
        if (container == null) return;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack == null || stack.isEmpty()) continue;
            String itemId = stack.getItemId();
            if (itemId == null || itemId.isEmpty()) continue;
            String name = UIItemUtils.displayNameOrItemId(stack);
            Entry entry = new Entry(kind, slot, stack, itemId, stack.getQuantity(), name);

            if (ReforgeEquip.isWeapon(stack) || ReforgeEquip.isArmor(stack)) {
                SocketData sd = SocketManager.getSocketData(stack);
                if (sd != null && sd.getMaxSockets() > 0) {
                    equipments.add(entry);
                }
            }
            if (isEssenceItem(itemId)) {
                essences.add(entry);
            }
            if (isVoidheartItem(itemId) || isHammerItem(itemId)) {
                voidhearts.add(entry);
            }
        }
    }

    private static boolean isEssenceItem(String itemId) {
        for (String id : ESSENCE_ITEM_IDS) {
            if (id.equalsIgnoreCase(itemId)) return true;
        }
        return false;
    }

    private static boolean isVoidheartItem(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return false;
        }
        if (VOIDHEART_ID.equalsIgnoreCase(itemId)) {
            return true;
        }
        if (itemId.toLowerCase(Locale.ROOT).contains("voidheart")) {
            return true;
        }
        String normalized = UIItemUtils.normalizeItemId(itemId);
        return normalized.contains("voidheart") || normalized.endsWith("ingredientvoidheart");
    }

    private static boolean isHammerItem(String itemId) {
        return isIronHammerItem(itemId) || isThoriumHammerItem(itemId);
    }

    private static boolean isIronHammerItem(String itemId) {
        return UIItemUtils.isIronHammerItem(itemId, HAMMER_ID);
    }

    private static boolean isThoriumHammerItem(String itemId) {
        return itemId != null && HAMMER_THORIUM_ID.equalsIgnoreCase(itemId);
    }

    private static void openPage(Player player, Snapshot snapshot, SelectionState state) {
        PlayerRef playerRef = player.getPlayerRef();
        try {
            Class<?> pageBuilderClass = Class.forName(HYUI_PAGE_BUILDER);
            Class<?> eventBindingClass = Class.forName(HYUI_EVENT_BINDING);
            Class<?> uiCommandClass = Class.forName(UI_COMMAND_BUILDER);

            Method pageForPlayer = pageBuilderClass.getMethod("pageForPlayer", PlayerRef.class);
            Method fromHtml = pageBuilderClass.getMethod("fromHtml", String.class);
            Method addListener = pageBuilderClass.getMethod("addEventListener", String.class, eventBindingClass, java.util.function.BiConsumer.class);
            Method withLifetime = pageBuilderClass.getMethod("withLifetime", CustomPageLifetime.class);
            Method openMethod = pageBuilderClass.getMethod("open", Class.forName("com.hypixel.hytale.component.Store"));

            Object valueChanged = eventBindingClass.getField("ValueChanged").get(null);
            Object activating = eventBindingClass.getField("Activating").get(null);

            String html = buildHtml(snapshot, state);
            Object pageBuilder = pageForPlayer.invoke(null, playerRef);
            pageBuilder = fromHtml.invoke(pageBuilder, html);

            final Player finalPlayer = player;
            final Snapshot finalSnapshot = snapshot;
            addListener.invoke(pageBuilder, "equipmentDropdown", valueChanged,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        String equipmentVal = extractEventValue(eventObj);
                        String essenceVal = getContextValue(ctxObj, "essenceDropdown", "#essenceDropdown.value");
                        String supportVal = getContextValue(ctxObj, "supportDropdown", "#supportDropdown.value");
                        pendingSelections.put(finalPlayer.getPlayerRef(),
                                new SelectionState(equipmentVal, essenceVal, supportVal, null, 0, false));
                        finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));
                    });

            addListener.invoke(pageBuilder, "essenceDropdown", valueChanged,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        String equipmentVal = getContextValue(ctxObj, "equipmentDropdown", "#equipmentDropdown.value");
                        String essenceVal = extractEventValue(eventObj);
                        String supportVal = getContextValue(ctxObj, "supportDropdown", "#supportDropdown.value");
                        pendingSelections.put(finalPlayer.getPlayerRef(),
                                new SelectionState(equipmentVal, essenceVal, supportVal, null, 0, false));
                        finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));
                    });

            addListener.invoke(pageBuilder, "supportDropdown", valueChanged,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        String equipmentVal = getContextValue(ctxObj, "equipmentDropdown", "#equipmentDropdown.value");
                        String essenceVal = getContextValue(ctxObj, "essenceDropdown", "#essenceDropdown.value");
                        String supportVal = extractEventValue(eventObj);
                        pendingSelections.put(finalPlayer.getPlayerRef(),
                                new SelectionState(equipmentVal, essenceVal, supportVal, null, 0, false));
                        finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));
                    });

            addListener.invoke(pageBuilder, "processButton", activating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        if (Boolean.TRUE.equals(processingPlayers.get(finalPlayer.getPlayerRef()))) {
                            return;
                        }
                        String equipmentVal = getContextValue(ctxObj, "equipmentDropdown", "#equipmentDropdown.value");
                        String essenceVal = getContextValue(ctxObj, "essenceDropdown", "#essenceDropdown.value");
                        String supportVal = getContextValue(ctxObj, "supportDropdown", "#supportDropdown.value");
                        Entry equipment = resolveSelection(finalSnapshot.equipments, equipmentVal);
                        Entry essence = resolveSelection(finalSnapshot.essences, essenceVal);
                        Entry support = resolveSelection(finalSnapshot.voidhearts, supportVal);

                        processingPlayers.put(finalPlayer.getPlayerRef(), true);
                        pendingSelections.put(finalPlayer.getPlayerRef(),
                                new SelectionState(equipmentVal, essenceVal, supportVal, "Processing...", 0, true));
                        sfxConfig.playReforgeStart(finalPlayer);
                        finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));

                        for (int elapsed = PROGRESS_TICK_MS; elapsed < PROCESS_DURATION_MS; elapsed += PROGRESS_TICK_MS) {
                            final int delay = elapsed;
                            final int timedProgress = Math.min(99, (int) Math.round(((delay + PROGRESS_TICK_MS) * 100.0) / PROCESS_DURATION_MS));
                            scheduler.schedule(() -> finalPlayer.getWorld().execute(() -> {
                                if (!Boolean.TRUE.equals(processingPlayers.get(finalPlayer.getPlayerRef()))) return;
                                pendingSelections.put(finalPlayer.getPlayerRef(),
                                        new SelectionState(equipmentVal, essenceVal, supportVal, "Processing...", timedProgress, true));
                                openWithSync(finalPlayer);
                            }), delay, TimeUnit.MILLISECONDS);
                        }

                        scheduler.schedule(() -> finalPlayer.getWorld().execute(() -> {
                            try {
                                ProcessResult result = processSelection(finalPlayer, equipment, essence, support);
                                pendingSelections.put(finalPlayer.getPlayerRef(),
                                        new SelectionState(equipmentVal, essenceVal, supportVal, result.status, result.progress, false));
                            } finally {
                                processingPlayers.remove(finalPlayer.getPlayerRef());
                                openWithSync(finalPlayer);
                            }
                        }), PROCESS_DURATION_MS, TimeUnit.MILLISECONDS);
                    });

            pageBuilder = withLifetime.invoke(pageBuilder, CustomPageLifetime.CanDismiss);
            Object store = getStore(playerRef);
            Object page = openMethod.invoke(pageBuilder, store);
            openPages.put(playerRef, page);
        } catch (Exception e) {
            System.err.println("[SocketReforge] EssenceBenchUI open error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String buildHtml(Snapshot snapshot, SelectionState state) {
        String equipmentKey = state != null ? state.equipmentKey : null;
        String essenceKey = state != null ? state.essenceKey : null;
        String supportKey = state != null ? state.supportKey : null;
        boolean processing = state != null && state.processing;
        int progress = state != null ? Math.max(0, Math.min(100, state.progressValue)) : 0;
        String status = state != null && state.statusText != null ? state.statusText : "Idle";
        if (!processing) {
            progress = 0;
        }

        Entry selectedEquipment = findByKey(snapshot.equipments, equipmentKey);
        Entry selectedSupport = resolveSelection(snapshot.voidhearts, supportKey);
        if (!processing && "Idle".equals(status) && isFilled(selectedEquipment)) {
            status = "All sockets are filled.";
        }

        String html = loadTemplate();
        html = html.replace("{{equipmentOptions}}", buildOptions(snapshot.equipments, "No socketed equipment found", equipmentKey));
        html = html.replace("{{essenceOptions}}", buildOptions(snapshot.essences, "No essence found", essenceKey));
        html = html.replace("{{supportOptions}}", buildSupportOptions(snapshot.voidhearts, supportKey));
        html = html.replace("{{supportDurabilityText}}", escapeHtml(buildSupportDurabilityText(selectedSupport)));
        html = html.replace("{{socketIcons}}", buildSocketIconsHtml(selectedEquipment));
        html = html.replace("{{socketSummary}}", escapeHtml(buildSocketSummary(selectedEquipment)));
        html = html.replace("{{metadataText}}", escapeHtml(buildMetadata(selectedEquipment)));
        html = html.replace("{{progressValue}}", String.valueOf(progress));
        html = html.replace("{{statusText}}", escapeHtml(status));
        html = html.replace("{{processDisabledAttr}}", shouldDisable(processing, selectedEquipment, essenceKey, selectedSupport) ? "disabled=\"true\"" : "");
        return html;
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

    private static String buildSupportOptions(List<Entry> entries, String selectedKey) {
        StringBuilder sb = new StringBuilder();
        sb.append("<option value=\"\"").append(selectedKey == null || selectedKey.isEmpty() ? " selected=\"true\"" : "")
                .append(">None</option>");
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            String key = String.valueOf(i);
            sb.append("<option value=\"").append(key).append("\"");
            if (key.equals(selectedKey)) {
                sb.append(" selected=\"true\"");
            }
            String suffix = isHammerItem(entry.itemId)
                    ? " [Clear Essences]"
                    : (isVoidheartItem(entry.itemId) ? " [Repair Broken]" : "");
            sb.append(">").append(escapeHtml(entry.displayName)).append(suffix).append(" x").append(entry.quantity).append("</option>");
        }
        return sb.toString();
    }

    private static boolean shouldDisable(boolean processing, Entry equipment, String essenceKey, Entry selectedSupport) {
        if (processing) return true;
        if (equipment == null) return true;
        if (selectedSupport != null && isHammerItem(selectedSupport.itemId)) {
            return false;
        }
        if (essenceKey == null || essenceKey.isEmpty()) return true;
        return isFilled(equipment);
    }

    private static String buildSocketIconsHtml(Entry equipment) {
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
        String filledIconName = resolveFilledSocketIconName();
        String brokenIconName = resolveBrokenSocketIconName();

        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"layout-mode: Left; spacing: 14;\"><div style=\"flex-weight:1;\"></div>");
        for (int i = 0; i < maxSockets; i++) {
            boolean isPunched = i < punchedSockets;
            Socket socket = i < sockets.size() ? sockets.get(i) : null;
            boolean isBroken = socket != null && socket.isBroken();
            boolean isFilled = socket != null && !socket.isBroken() && !socket.isEmpty();
            String essenceColor = getSocketColorHex(socket);

            String tileStyle = isBroken
                    ? "anchor-width:95; anchor-height:95; background-color:#b22222; layout-mode:Top;"
                    : "anchor-width:95; anchor-height:95; background-color:#00000000; layout-mode:Top;";
            // Use anchor sizing (HyUI-friendly) to keep wrapper visible.
            String wrapStyle = "anchor-width:95; anchor-height:95; background-color:" + essenceColor
                    + "; layout-mode:Top;";
            String icon = isBroken
                    ? brokenIconName
                    : (!isPunched ? "slot_bg.png" : (isFilled ? filledIconName : "socket_empty.png"));

            sb.append("<div style=\"").append(wrapStyle).append("\">")
                    .append("<div style=\"flex-weight:1;\"></div>")
                    .append("<div style=\"layout-mode:Left;\"><div style=\"flex-weight:1;\"></div>")
                    .append("<div style=\"").append(tileStyle).append("\">")
                    .append("<div style=\"flex-weight:1;\"></div>")
                    .append("<div style=\"layout-mode:Left;\">")
                    .append("<div style=\"flex-weight:1;\"></div>")
                    .append("<img src=\"").append(icon).append("\" width=\"90\" height=\"90\"/>")
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
        String lower = essenceId.toLowerCase(Locale.ROOT);
        if (lower.contains("fire")) return "#FFAA00";
        if (lower.contains("ice")) return "#55FFFF";
        if (lower.contains("life")) return "#55FF55";
        if (lower.contains("lightning")) return "#FFFF55";
        if (lower.contains("void")) return "#AA55FF";
        if (lower.contains("water")) return "#5555FF";
        return "#FFFFFF";
    }

    private static String resolveFilledSocketIconName() {
        return UITemplateUtils.resolveCustomUiAsset("socket_empty.png", "socket_fille.png", "socket_filled.png");
    }

    private static String resolveBrokenSocketIconName() {
        return UITemplateUtils.resolveCustomUiAsset("socket_empty.png", "socket_broken.png", "socket_Broken.png");
    }

    private static boolean isFilled(Entry equipment) {
        if (equipment == null) return false;
        SocketData sd = SocketManager.getSocketData(equipment.item);
        return sd != null && !sd.hasEmptySocket();
    }

    private static ProcessResult processSelection(Player player, Entry equipment, Entry essence, Entry support) {
        if (equipment == null) return new ProcessResult("Pick a valid equipment first.", 0);

        ItemStack item = equipmentItem(equipment, player);
        if (item == null || item.isEmpty()) return new ProcessResult("Selected equipment is no longer available.", 0);
        boolean isWeapon = ReforgeEquip.isWeapon(item);
        boolean isArmor = ReforgeEquip.isArmor(item);
        if (!isWeapon && !isArmor) return new ProcessResult("Selected item is not valid equipment.", 0);

        SocketData socketData = SocketManager.getSocketData(item);
        if (socketData == null || socketData.getMaxSockets() == 0 || socketData.getSockets().isEmpty()) {
            return new ProcessResult("No sockets found. Punch sockets first.", 0);
        }

        // Hammer support action: clear all socketed essences (broken sockets remain broken).
        if (support != null && isHammerItem(support.itemId)) {
            int clearable = 0;
            for (Socket socket : socketData.getSockets()) {
                if (!socket.isBroken() && !socket.isEmpty()) {
                    clearable++;
                }
            }
            if (clearable <= 0) {
                sfxConfig.playNoChange(player);
                return new ProcessResult("No socketed essences to clear.", 100);
            }

            boolean isThoriumHammer = isThoriumHammerItem(support.itemId);
            Map<String, Integer> refundCounts = new LinkedHashMap<>();
            if (isThoriumHammer) {
                for (Socket socket : socketData.getSockets()) {
                    if (socket.isBroken() || socket.isEmpty()) {
                        continue;
                    }
                    String essenceId = socket.getEssenceId();
                    if (essenceId == null || essenceId.isBlank()) {
                        continue;
                    }
                    refundCounts.merge(essenceId, 1, Integer::sum);
                }
            }

            HammerUseResult hammerUse = applyHammerWear(player, support);
            if (!hammerUse.ok) {
                return new ProcessResult("Selected hammer stack changed; reselect and try again.", 0);
            }

            double successChance = SocketManager.getConfig() != null
                    ? SocketManager.getConfig().getEssenceRemovalSuccessChance()
                    : 0.70;
            boolean success = Math.random() < successChance;

            boolean reducedMaxSockets = false;
            int removed = 0;
            if (success) {
                for (Socket socket : socketData.getSockets()) {
                    if (!socket.isBroken() && !socket.isEmpty()) {
                        socket.setEssenceId(null);
                        removed++;
                    }
                }
            } else {
                List<Socket> breakable = new ArrayList<>();
                for (Socket socket : socketData.getSockets()) {
                    if (!socket.isBroken()) {
                        breakable.add(socket);
                    }
                }
                if (!breakable.isEmpty()) {
                    int idx = (int) (Math.random() * breakable.size());
                    Socket broken = breakable.get(idx);
                    broken.setBroken(true);
                    broken.setEssenceId(null);
                }
            }
            if (isThoriumHammer && socketData.getMaxSockets() > 1
                    && Math.random() < THORIUM_MAX_SOCKET_REDUCE_CHANCE) {
                reducedMaxSockets = socketData.reduceMaxSockets();
            }

            ItemStack cleared = SocketManager.withSocketData(item, socketData);
            writeStack(player, equipment, cleared);
            socketData.registerTooltips(cleared, cleared.getItemId(), isWeapon);
            DynamicTooltipUtils.refreshAllPlayers();
            if (success) {
                int refunded = isThoriumHammer ? refundEssences(player, refundCounts) : 0;
                sfxConfig.playSuccess(player);
                String successStatus = hammerUse.consumed
                        ? "Clear succeeded: removed " + removed + ". Hammer broke."
                        : "Clear succeeded: removed " + removed + " socketed essence(s).";
                successStatus += (isThoriumHammer ? buildRefundSuffix(removed, refunded) : "");
                if (reducedMaxSockets) {
                    successStatus += " Max sockets reduced to " + socketData.getMaxSockets() + ".";
                }
                return new ProcessResult(successStatus, 100);
            }

            sfxConfig.playShatter(player);
            String failureStatus = hammerUse.consumed
                    ? "Clear failed: a random socket broke. Hammer broke."
                    : "Clear failed: a random socket broke.";
            if (reducedMaxSockets) {
                failureStatus += " Max sockets reduced to " + socketData.getMaxSockets() + ".";
            }
            return new ProcessResult(failureStatus, 100);
        }

        if (essence == null) return new ProcessResult("Pick an essence first.", 0);

        boolean hasFillableSocket = false;
        for (Socket socket : socketData.getSockets()) {
            if (!socket.isBroken() && socket.isEmpty() && !socket.isLocked()) {
                hasFillableSocket = true;
                break;
            }
        }

        // Only force repair-first when no non-broken empty sockets are available.
        if (!hasFillableSocket && socketData.hasBrokenSocket()) {
            if (support == null) {
                return new ProcessResult("Broken socket detected. Select Voidheart to repair first.", 100);
            }
            if (!consumeMaterial(player, support, 1)) {
                return new ProcessResult("Voidheart stack changed; reselect and try again.", 0);
            }
            if (!socketData.repairBrokenSocket()) {
                return new ProcessResult("Repair failed. Try again.", 0);
            }

            ItemStack repaired = SocketManager.withSocketData(item, socketData);
            writeStack(player, equipment, repaired);
            socketData.registerTooltips(repaired, repaired.getItemId(), isWeapon);
            DynamicTooltipUtils.refreshAllPlayers();
            sfxConfig.playSuccess(player);
            return new ProcessResult("Socket repaired. Process again to socket essence.", 100);
        }

        if (!hasFillableSocket) {
            return new ProcessResult("All sockets are filled.", 100);
        }

        if (!consumeMaterial(player, essence, 1)) {
            return new ProcessResult("Selected essence stack changed; reselect and try again.", 0);
        }

        String essenceType = SocketManager.resolveEssenceTypeFromItemId(essence.itemId);
        String essenceId = SocketManager.resolveEssenceIdFromItemId(essence.itemId);
        if (essenceType == null || essenceId == null) {
            return new ProcessResult("Invalid essence selected.", 0);
        }
        if (!EssenceRegistry.get().exists(essenceId)) {
            return new ProcessResult("Essence not found: " + essenceId, 0);
        }
        if (!SocketManager.socketEssence(socketData, essenceId)) {
            return new ProcessResult("Could not socket essence. Try again.", 0);
        }

        ItemStack updated = SocketManager.withSocketData(item, socketData);
        writeStack(player, equipment, updated);
        socketData.registerTooltips(updated, updated.getItemId(), isWeapon);
        DynamicTooltipUtils.refreshAllPlayers();
        sfxConfig.playSuccess(player);
        return new ProcessResult("Essence socketed successfully.", 100);
    }

    private static ItemStack equipmentItem(Entry equipment, Player player) {
        if (equipment == null) return null;
        return UIInventoryUtils.readItem(player, equipment.kind == ContainerKind.HOTBAR, equipment.slot);
    }

    private static void writeStack(Player player, Entry entry, ItemStack stack) {
        UIInventoryUtils.writeItem(player, entry.kind == ContainerKind.HOTBAR, entry.slot, stack);
    }

    private static boolean consumeMaterial(Player player, Entry entry, int amount) {
        if (entry == null || amount <= 0) return false;
        return UIInventoryUtils.consumeItem(player, entry.kind == ContainerKind.HOTBAR, entry.slot, entry.itemId, amount);
    }

    private static int refundEssences(Player player, Map<String, Integer> refundCounts) {
        if (player == null || refundCounts == null || refundCounts.isEmpty()) {
            return 0;
        }
        int refunded = 0;
        for (Map.Entry<String, Integer> entry : refundCounts.entrySet()) {
            String itemId = resolveEssenceItemId(entry.getKey());
            Integer qty = entry.getValue();
            if (itemId == null || itemId.isBlank() || qty == null || qty <= 0) {
                continue;
            }
            ItemStack stack = new ItemStack(itemId, qty);
            if (UIInventoryUtils.addItemToInventory(player, stack)) {
                refunded += qty;
            }
        }
        return refunded;
    }

    private static String resolveEssenceItemId(String essenceId) {
        if (essenceId == null || essenceId.isBlank()) {
            return null;
        }
        String lower = essenceId.toLowerCase(Locale.ROOT);
        if (lower.contains("ingredient_") && lower.contains("essence")) {
            return essenceId;
        }
        String cleaned = essenceId;
        if (lower.startsWith("essence_")) {
            cleaned = essenceId.substring("Essence_".length());
        }
        boolean isGreater = lower.endsWith("_concentrated");
        if (isGreater && cleaned.length() >= "_Concentrated".length()) {
            cleaned = cleaned.substring(0, cleaned.length() - "_Concentrated".length());
        }
        if (cleaned.isBlank()) {
            return null;
        }
        return "Ingredient_" + cleaned + "_Essence" + (isGreater ? "_Concentrated" : "");
    }

    private static String buildRefundSuffix(int removed, int refunded) {
        if (removed <= 0) {
            return "";
        }
        if (refunded <= 0) {
            return " Inventory full: refund failed.";
        }
        if (refunded < removed) {
            return " Refunded " + refunded + " of " + removed + " (inventory full).";
        }
        return " Refunded " + refunded + " essence(s).";
    }

    private static HammerUseResult applyHammerWear(Player player, Entry hammerEntry) {
        if (player == null || hammerEntry == null || !isHammerItem(hammerEntry.itemId)) {
            return new HammerUseResult(false, false);
        }
        ItemContainer container = UIInventoryUtils.getContainer(player, hammerEntry.kind == ContainerKind.HOTBAR);
        if (container == null) {
            return new HammerUseResult(false, false);
        }
        UIItemUtils.HammerWearResult wear = UIItemUtils.applyHammerWear(
                container,
                hammerEntry.slot,
                0.10d,
                itemId -> isHammerItem(itemId));
        if (!wear.ok()) {
            return new HammerUseResult(false, false);
        }
        return new HammerUseResult(true, wear.consumed());
    }

    private static ItemContainer getContainer(Player player, ContainerKind kind) {
        return UIInventoryUtils.getContainer(player, kind == ContainerKind.HOTBAR);
    }

    private static String buildSocketSummary(Entry equipment) {
        if (equipment == null) return "No equipment selected.";
        SocketData sd = SocketManager.getSocketData(equipment.item);
        if (sd == null) {
            return "No socket data.";
        }
        int broken = 0;
        int filled = 0;
        for (irai.mod.reforge.Socket.Socket socket : sd.getSockets()) {
            if (socket.isBroken()) broken++;
            else if (!socket.isEmpty()) filled++;
        }
        int empty = Math.max(0, sd.getCurrentSocketCount() - filled - broken);
        return "Filled " + filled + " | Empty " + empty + " | Broken " + broken;
    }

    private static String buildMetadata(Entry equipment) {
        if (equipment == null) return "No equipment selected.";
        StringBuilder sb = new StringBuilder();
        sb.append("[Item]\n");
        sb.append("Name: ").append(equipment.displayName).append("\n");
        sb.append("Id: ").append(equipment.itemId).append("\n");
        sb.append("Inventory: ").append(equipment.kind == ContainerKind.HOTBAR ? "Hotbar" : "Storage")
                .append(" slot ").append(equipment.slot).append("\n\n");
        SocketData sd = SocketManager.getSocketData(equipment.item);
        if (sd != null) {
            int broken = 0;
            int filled = 0;
            for (irai.mod.reforge.Socket.Socket socket : sd.getSockets()) {
                if (socket.isBroken()) broken++;
                else if (!socket.isEmpty()) filled++;
            }
            sb.append("[Sockets]\n");
            sb.append("Current/Max: ").append(sd.getCurrentSocketCount()).append("/").append(sd.getMaxSockets()).append("\n");
            sb.append("Filled: ").append(filled).append("  Broken: ").append(broken).append("\n\n");
        }
        sb.append("[Metadata]\n");
        if (equipment.item.getMetadata() != null && !equipment.item.getMetadata().isEmpty()) {
            int shown = 0;
            for (String key : equipment.item.getMetadata().keySet()) {
                if (shown >= 6) break;
                sb.append("- ").append(key).append("\n");
                shown++;
            }
        } else {
            sb.append("none");
        }
        return sb.toString();
    }

    private static String buildSupportDurabilityText(Entry support) {
        if (support == null || support.item == null || support.item.isEmpty()) {
            return "Support durability: -";
        }
        ItemStack item = support.item;
        double max = item.getMaxDurability();
        double cur = item.getDurability();
        if (max <= 0.0d) {
            return "Support durability: N/A";
        }
        int maxInt = (int) Math.round(max);
        int curInt = (int) Math.round(cur);
        int percent = (int) Math.round((cur / max) * 100.0);
        percent = Math.max(0, Math.min(100, percent));
        return "Support durability: " + percent + "% (" + curInt + "/" + maxInt + ")";
    }

    private static String loadTemplate() {
        return UITemplateUtils.loadTemplate(
                EssenceBenchUI.class,
                TEMPLATE_PATH,
                "<div><p>Essence Bench UI template missing.</p></div>",
                "EssenceBenchUI");
    }

    private static Entry findByKey(List<Entry> entries, String key) {
        return resolveSelection(entries, key);
    }

    private static Entry resolveSelection(List<Entry> entries, String value) {
        return HyUIReflectionUtils.resolveIndexSelection(entries, value);
    }

    private static String getEssenceTypeFromItem(String itemId) {
        if (itemId == null) return null;
        return SocketManager.resolveEssenceTypeFromItemId(itemId);
    }

    private static String capitalize(String text) {
        if (text == null || text.isEmpty()) return text;
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1).toLowerCase(Locale.ROOT);
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

    private static String escapeHtml(String text) {
        return UITemplateUtils.escapeHtml(text);
    }
}
