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
import irai.mod.reforge.Common.UI.UIItemUtils;
import irai.mod.reforge.Common.UI.UITemplateUtils;
import irai.mod.reforge.Lore.LoreGemRegistry;
import irai.mod.reforge.Lore.LoreSocketData;
import irai.mod.reforge.Lore.LoreSocketManager;
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

    private static boolean hyuiAvailable = false;
    private static final Map<PlayerRef, Object> openPages = new ConcurrentHashMap<>();
    private static final Map<PlayerRef, SelectionState> pendingSelections = new ConcurrentHashMap<>();
    private static final Map<PlayerRef, Boolean> pendingNavToFeed = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

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

        SelectionState(String equipmentKey, String gemKey, String supportKey, String slotKey, String statusText) {
            this.equipmentKey = equipmentKey;
            this.gemKey = gemKey;
            this.supportKey = supportKey;
            this.slotKey = slotKey;
            this.statusText = statusText;
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
            player.sendMessage(Message.raw("<color=#FF5555>" + LangLoader.getUITranslation(player, "ui.lore_socket.hyui_missing")));
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
            if (itemId.equalsIgnoreCase(SUPPORT_REROLL_ITEM_ID)) {
                String name = UIItemUtils.displayNameOrItemId(stack, player);
                supports.add(new Entry(kind, slot, stack, itemId, stack.getQuantity(), name));
            }
        }
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

            Object valueChanged = eventBindingClass.getField("ValueChanged").get(null);
            Object activating = eventBindingClass.getField("Activating").get(null);

            String html = buildHtml(player, snapshot, selectionState);
            Object pageBuilder = pageForPlayer.invoke(null, playerRef);
            pageBuilder = fromHtml.invoke(pageBuilder, html);

            final Player finalPlayer = player;
            final Snapshot finalSnapshot = snapshot;

            addListener.invoke(pageBuilder, "equipmentDropdown", valueChanged,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        String equipmentVal = extractEventValue(eventObj);
                        String gemVal = getContextValue(ctxObj, "gemDropdown", "#gemDropdown.value");
                        String supportVal = getContextValue(ctxObj, "supportDropdown", "#supportDropdown.value");
                        String slotVal = getContextValue(ctxObj, "slotDropdown", "#slotDropdown.value");
                        pendingSelections.put(finalPlayer.getPlayerRef(),
                                new SelectionState(equipmentVal, gemVal, supportVal, slotVal, null));
                        finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));
                    });

            addListener.invoke(pageBuilder, "gemDropdown", valueChanged,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        String equipmentVal = getContextValue(ctxObj, "equipmentDropdown", "#equipmentDropdown.value");
                        String gemVal = extractEventValue(eventObj);
                        String supportVal = getContextValue(ctxObj, "supportDropdown", "#supportDropdown.value");
                        String slotVal = getContextValue(ctxObj, "slotDropdown", "#slotDropdown.value");
                        pendingSelections.put(finalPlayer.getPlayerRef(),
                                new SelectionState(equipmentVal, gemVal, supportVal, slotVal, null));
                        finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));
                    });

            addListener.invoke(pageBuilder, "supportDropdown", valueChanged,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        String equipmentVal = getContextValue(ctxObj, "equipmentDropdown", "#equipmentDropdown.value");
                        String gemVal = getContextValue(ctxObj, "gemDropdown", "#gemDropdown.value");
                        String supportVal = extractEventValue(eventObj);
                        String slotVal = getContextValue(ctxObj, "slotDropdown", "#slotDropdown.value");
                        pendingSelections.put(finalPlayer.getPlayerRef(),
                                new SelectionState(equipmentVal, gemVal, supportVal, slotVal, null));
                        finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));
                    });

            addListener.invoke(pageBuilder, "slotDropdown", valueChanged,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        String equipmentVal = getContextValue(ctxObj, "equipmentDropdown", "#equipmentDropdown.value");
                        String gemVal = getContextValue(ctxObj, "gemDropdown", "#gemDropdown.value");
                        String supportVal = getContextValue(ctxObj, "supportDropdown", "#supportDropdown.value");
                        String slotVal = extractEventValue(eventObj);
                        pendingSelections.put(finalPlayer.getPlayerRef(),
                                new SelectionState(equipmentVal, gemVal, supportVal, slotVal, null));
                        finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));
                    });

            addListener.invoke(pageBuilder, "processButton", activating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        String equipmentVal = getContextValue(ctxObj, "equipmentDropdown", "#equipmentDropdown.value");
                        String gemVal = getContextValue(ctxObj, "gemDropdown", "#gemDropdown.value");
                        String supportVal = getContextValue(ctxObj, "supportDropdown", "#supportDropdown.value");
                        String slotVal = getContextValue(ctxObj, "slotDropdown", "#slotDropdown.value");
                        Entry equipment = resolveSelection(finalSnapshot.equipments, equipmentVal);
                        Entry gem = resolveSelection(finalSnapshot.gems, gemVal);
                        Entry support = resolveSelection(finalSnapshot.supports, supportVal);

                        ProcessResult result = processSelection(finalPlayer, equipment, gem, support, slotVal);
                        pendingSelections.put(finalPlayer.getPlayerRef(),
                                new SelectionState(equipmentVal, gemVal, supportVal, slotVal, result.status));
                        finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));
                    });

            addListener.invoke(pageBuilder, "navFeedButton", activating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        PlayerRef ref = finalPlayer.getPlayerRef();
                        LoreUiState.setActive(ref, LoreUiState.Page.FEED);
                        pendingNavToFeed.put(ref, Boolean.TRUE);
                        closePageIfOpen(ref);
                        // Fallback: if onDismiss doesn't fire, open after a short delay.
                        scheduler.schedule(() -> {
                            Boolean pending = pendingNavToFeed.remove(ref);
                            if (pending == null || !pending) {
                                return;
                            }
                            finalPlayer.getWorld().execute(() -> LoreFeedBenchUI.open(finalPlayer));
                        }, 100, TimeUnit.MILLISECONDS);
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
                        Boolean pending = pendingNavToFeed.remove(ref);
                        if (pending != null && pending) {
                            LoreFeedBenchUI.open(finalPlayer);
                        }
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
        String gemColor = selectedGem != null ? LoreGemRegistry.resolveColor(selectedGem.itemId) : null;

        String html = loadTemplate();
        html = html.replace("{{equipmentOptions}}",
                buildOptions(snapshot.equipments,
                        LangLoader.getUITranslation(player, "ui.lore_socket.option_no_equipment"),
                        equipmentKey));
        html = html.replace("{{gemOptions}}", buildGemOptions(player, snapshot.gems, gemKey));
        html = html.replace("{{supportOptions}}", buildSupportOptions(player, snapshot.supports, supportKey));
        html = html.replace("{{slotOptions}}", buildSlotOptions(player, selectedEquipment, slotKey, gemColor));
        html = html.replace("{{socketIcons}}", buildSocketPreview(player, selectedEquipment));
        html = html.replace("{{statusText}}", escapeHtml(status));
        html = html.replace("{{metadataText}}", escapeHtml(buildMetadata(player, selectedEquipment)));
        html = html.replace("{{processDisabledAttr}}", shouldDisable(selectedEquipment, selectedGem, selectedSupport) ? "disabled=\"true\"" : "");
        return LangLoader.replaceUiTokens(player, html);
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

    private static String buildSocketPreview(Player player, Entry equipment) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"layout-mode:Left; spacing:12;\"><div style=\"flex-weight:1;\"></div>");
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
        int tileSize = 86;
        int wrapSize = 94;
        int overlaySize = 56;

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
        String lower = trimmed.toLowerCase(Locale.ROOT);
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
        String lower = itemId.toLowerCase(Locale.ROOT);
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
        String lower = color.toLowerCase(Locale.ROOT);
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

    private static boolean shouldDisable(Entry equipment, Entry gem, Entry support) {
        if (equipment == null) {
            return true;
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
            if (!support.itemId.equalsIgnoreCase(SUPPORT_REROLL_ITEM_ID)) {
                return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_socket.status_invalid_support"));
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
