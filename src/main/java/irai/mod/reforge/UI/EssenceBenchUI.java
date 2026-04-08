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

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;

import irai.mod.reforge.Common.UI.HyUIReflectionUtils;
import irai.mod.reforge.Common.UI.UIInventoryUtils;
import irai.mod.reforge.Common.UI.UIItemUtils;
import irai.mod.reforge.Common.UI.UITemplateUtils;
import irai.mod.reforge.Common.ResonantRecipeUtils;
import irai.mod.reforge.Config.SFXConfig;
import irai.mod.reforge.Interactions.ReforgeEquip;
import irai.mod.reforge.Socket.Essence;
import irai.mod.reforge.Socket.EssenceRegistry;
import irai.mod.reforge.Socket.ResonanceSystem;
import irai.mod.reforge.Socket.Socket;
import irai.mod.reforge.Socket.SocketData;
import irai.mod.reforge.Socket.SocketManager;
import irai.mod.reforge.Util.DynamicTooltipUtils;
import irai.mod.reforge.Util.LangLoader;

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
    private static final boolean DEBUG_ESSENCE_ICON = Boolean.parseBoolean(
            System.getProperty("socketreforge.debug.essenceicon", "false"));

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
    private static final String RESONANT_ESSENCE_ID = "Ingredient_Resonant_Essence";
    private static final String META_REFINEMENT_LEVEL = "SocketReforge.Refinement.Level";
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
        final String slotKey;
        final String statusText;
        final int progressValue;
        final boolean processing;

        SelectionState(String equipmentKey, String essenceKey, String supportKey, String slotKey,
                       String statusText, int progressValue, boolean processing) {
            this.equipmentKey = equipmentKey;
            this.essenceKey = essenceKey;
            this.supportKey = supportKey;
            this.slotKey = slotKey;
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

    private static final class AutoEssenceSelection {
        final Entry entry;
        final String notice;
        final String error;

        AutoEssenceSelection(Entry entry, String notice, String error) {
            this.entry = entry;
            this.notice = notice;
            this.error = error;
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
            player.sendMessage(Message.raw("<color=#FF5555>" + LangLoader.getUITranslation(player, "ui.essence_bench.hyui_missing")));
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
        collectFromContainer(player, player.getInventory().getHotbar(), ContainerKind.HOTBAR, equipments, essences, voidhearts);
        collectFromContainer(player, player.getInventory().getStorage(), ContainerKind.STORAGE, equipments, essences, voidhearts);
        return new Snapshot(equipments, essences, voidhearts);
    }

    private static void collectFromContainer(
            Player player,
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
            String name = UIItemUtils.displayNameOrItemId(stack, player);
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
            if (isVoidheartItem(itemId) || isHammerItem(itemId) || isCompletedRecipeSupport(stack)) {
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

    private static boolean isCompletedRecipeSupport(ItemStack stack) {
        if (!ResonantRecipeUtils.isResonantRecipeItem(stack)) {
            return false;
        }
        if (!ResonantRecipeUtils.isRecipeComplete(stack)) {
            return false;
        }
        ResonantRecipeUtils.UsageState usage = ResonantRecipeUtils.getUsageState(stack);
        return usage.hasRemaining();
    }

    private static boolean isRecipeSupport(Entry entry) {
        return entry != null && entry.item != null && ResonantRecipeUtils.isResonantRecipeItem(entry.item);
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

            String html = buildHtml(player, snapshot, state);
            Object pageBuilder = pageForPlayer.invoke(null, playerRef);
            pageBuilder = fromHtml.invoke(pageBuilder, html);

            final Player finalPlayer = player;
            final Snapshot finalSnapshot = snapshot;
            addListener.invoke(pageBuilder, "equipmentDropdown", valueChanged,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        String equipmentVal = extractEventValue(eventObj);
                        String essenceVal = getContextValue(ctxObj, "essenceDropdown", "#essenceDropdown.value");
                        String supportVal = getContextValue(ctxObj, "supportDropdown", "#supportDropdown.value");
                        String slotVal = getContextValue(ctxObj, "slotDropdown", "#slotDropdown.value");
                        pendingSelections.put(finalPlayer.getPlayerRef(),
                                new SelectionState(equipmentVal, essenceVal, supportVal, slotVal, null, 0, false));
                        finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));
                    });

            addListener.invoke(pageBuilder, "essenceDropdown", valueChanged,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        String equipmentVal = getContextValue(ctxObj, "equipmentDropdown", "#equipmentDropdown.value");
                        String essenceVal = extractEventValue(eventObj);
                        String supportVal = getContextValue(ctxObj, "supportDropdown", "#supportDropdown.value");
                        String slotVal = getContextValue(ctxObj, "slotDropdown", "#slotDropdown.value");
                        pendingSelections.put(finalPlayer.getPlayerRef(),
                                new SelectionState(equipmentVal, essenceVal, supportVal, slotVal, null, 0, false));
                        finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));
                    });

            addListener.invoke(pageBuilder, "supportDropdown", valueChanged,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        String equipmentVal = getContextValue(ctxObj, "equipmentDropdown", "#equipmentDropdown.value");
                        String essenceVal = getContextValue(ctxObj, "essenceDropdown", "#essenceDropdown.value");
                        String supportVal = extractEventValue(eventObj);
                        String slotVal = getContextValue(ctxObj, "slotDropdown", "#slotDropdown.value");
                        pendingSelections.put(finalPlayer.getPlayerRef(),
                                new SelectionState(equipmentVal, essenceVal, supportVal, slotVal, null, 0, false));
                        finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));
                    });

            addListener.invoke(pageBuilder, "slotDropdown", valueChanged,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        String equipmentVal = getContextValue(ctxObj, "equipmentDropdown", "#equipmentDropdown.value");
                        String essenceVal = getContextValue(ctxObj, "essenceDropdown", "#essenceDropdown.value");
                        String supportVal = getContextValue(ctxObj, "supportDropdown", "#supportDropdown.value");
                        String slotVal = extractEventValue(eventObj);
                        pendingSelections.put(finalPlayer.getPlayerRef(),
                                new SelectionState(equipmentVal, essenceVal, supportVal, slotVal, null, 0, false));
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
                        String slotVal = getContextValue(ctxObj, "slotDropdown", "#slotDropdown.value");
                        Entry equipment = resolveSelection(finalSnapshot.equipments, equipmentVal);
                        Entry essence = resolveSelection(finalSnapshot.essences, essenceVal);
                        Entry support = resolveSelection(finalSnapshot.voidhearts, supportVal);

                        processingPlayers.put(finalPlayer.getPlayerRef(), true);
                        pendingSelections.put(finalPlayer.getPlayerRef(),
                                new SelectionState(equipmentVal, essenceVal, supportVal, slotVal,
                                        LangLoader.getUITranslation(finalPlayer, "ui.essence_bench.status_processing"), 0, true));
                        sfxConfig.playReforgeStart(finalPlayer);
                        finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));

                        for (int elapsed = PROGRESS_TICK_MS; elapsed < PROCESS_DURATION_MS; elapsed += PROGRESS_TICK_MS) {
                            final int delay = elapsed;
                            final int timedProgress = Math.min(99, (int) Math.round(((delay + PROGRESS_TICK_MS) * 100.0) / PROCESS_DURATION_MS));
                            scheduler.schedule(() -> finalPlayer.getWorld().execute(() -> {
                                if (!Boolean.TRUE.equals(processingPlayers.get(finalPlayer.getPlayerRef()))) return;
                                pendingSelections.put(finalPlayer.getPlayerRef(),
                                        new SelectionState(equipmentVal, essenceVal, supportVal, slotVal,
                                                LangLoader.getUITranslation(finalPlayer, "ui.essence_bench.status_processing"),
                                                timedProgress, true));
                                openWithSync(finalPlayer);
                            }), delay, TimeUnit.MILLISECONDS);
                        }

                        scheduler.schedule(() -> finalPlayer.getWorld().execute(() -> {
                            try {
                                ProcessResult result = processSelection(finalPlayer, equipment, essence, support, slotVal);
                                pendingSelections.put(finalPlayer.getPlayerRef(),
                                        new SelectionState(equipmentVal, essenceVal, supportVal, slotVal, result.status, result.progress, false));
                            } finally {
                                processingPlayers.remove(finalPlayer.getPlayerRef());
                                openWithSync(finalPlayer);
                            }
                        }), PROCESS_DURATION_MS, TimeUnit.MILLISECONDS);
                    });

            addListener.invoke(pageBuilder, "extractButton", activating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        if (Boolean.TRUE.equals(processingPlayers.get(finalPlayer.getPlayerRef()))) {
                            return;
                        }
                        String equipmentVal = getContextValue(ctxObj, "equipmentDropdown", "#equipmentDropdown.value");
                        String essenceVal = getContextValue(ctxObj, "essenceDropdown", "#essenceDropdown.value");
                        String supportVal = getContextValue(ctxObj, "supportDropdown", "#supportDropdown.value");
                        String slotVal = getContextValue(ctxObj, "slotDropdown", "#slotDropdown.value");
                        Entry equipment = resolveSelection(finalSnapshot.equipments, equipmentVal);

                        processingPlayers.put(finalPlayer.getPlayerRef(), true);
                        pendingSelections.put(finalPlayer.getPlayerRef(),
                                new SelectionState(equipmentVal, essenceVal, supportVal, slotVal,
                                        LangLoader.getUITranslation(finalPlayer, "ui.essence_bench.status_extracting"), 0, true));
                        sfxConfig.playReforgeStart(finalPlayer);
                        finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));

                        for (int elapsed = PROGRESS_TICK_MS; elapsed < PROCESS_DURATION_MS; elapsed += PROGRESS_TICK_MS) {
                            final int delay = elapsed;
                            final int timedProgress = Math.min(99, (int) Math.round(((delay + PROGRESS_TICK_MS) * 100.0) / PROCESS_DURATION_MS));
                            scheduler.schedule(() -> finalPlayer.getWorld().execute(() -> {
                                if (!Boolean.TRUE.equals(processingPlayers.get(finalPlayer.getPlayerRef()))) return;
                                pendingSelections.put(finalPlayer.getPlayerRef(),
                                        new SelectionState(equipmentVal, essenceVal, supportVal, slotVal,
                                                LangLoader.getUITranslation(finalPlayer, "ui.essence_bench.status_extracting"),
                                                timedProgress, true));
                                openWithSync(finalPlayer);
                            }), delay, TimeUnit.MILLISECONDS);
                        }

                        scheduler.schedule(() -> finalPlayer.getWorld().execute(() -> {
                            try {
                                ProcessResult result = processExtraction(finalPlayer, equipment);
                                pendingSelections.put(finalPlayer.getPlayerRef(),
                                        new SelectionState(equipmentVal, essenceVal, supportVal, slotVal, result.status, result.progress, false));
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

    private static String buildHtml(Player player, Snapshot snapshot, SelectionState state) {
        String equipmentKey = state != null ? state.equipmentKey : null;
        String essenceKey = state != null ? state.essenceKey : null;
        String supportKey = state != null ? state.supportKey : null;
        String slotKey = state != null ? state.slotKey : null;
        boolean processing = state != null && state.processing;
        int progress = state != null ? Math.max(0, Math.min(100, state.progressValue)) : 0;
        String status = state != null && state.statusText != null
                ? state.statusText
                : LangLoader.getUITranslation(player, "ui.essence_bench.status_idle");
        if (!processing) {
            progress = 0;
        }

        Entry selectedEquipment = findByKey(snapshot.equipments, equipmentKey);
        Entry selectedSupport = resolveSelection(snapshot.voidhearts, supportKey);
        String idleText = LangLoader.getUITranslation(player, "ui.essence_bench.status_idle");
        if (!processing && (idleText.equals(status) || "Idle".equals(status)) && isFilled(selectedEquipment)) {
            status = LangLoader.getUITranslation(player, "ui.essence_bench.status_all_filled");
        }

        String html = loadTemplate();
        html = html.replace("{{equipmentOptions}}",
                buildOptions(snapshot.equipments,
                        LangLoader.getUITranslation(player, "ui.essence_bench.option_no_equipment"),
                        equipmentKey));
        html = html.replace("{{essenceOptions}}", buildEssenceOptions(player, snapshot.essences, essenceKey));
        html = html.replace("{{supportOptions}}", buildSupportOptions(player, snapshot.voidhearts, supportKey));
        html = html.replace("{{supportDurabilityText}}", escapeHtml(buildSupportDurabilityText(player, selectedSupport)));
        html = html.replace("{{supportRecipeText}}", escapeHtml(buildSupportRecipeText(player, selectedSupport)));
        html = html.replace("{{effectPreviewText}}", escapeHtml(buildEffectPreviewText(player, selectedEquipment, selectedSupport)));
        html = html.replace("{{slotOptions}}", buildSlotOptions(player, selectedEquipment, slotKey));
        html = html.replace("{{socketIcons}}", buildSocketIconsHtml(selectedEquipment));
        html = html.replace("{{socketSummary}}", escapeHtml(buildSocketSummary(player, selectedEquipment)));
        html = html.replace("{{metadataText}}", escapeHtml(buildMetadata(player, selectedEquipment)));
        html = html.replace("{{progressValue}}", String.valueOf(progress));
        html = html.replace("{{statusText}}", escapeHtml(status));
        html = html.replace("{{processDisabledAttr}}", shouldDisable(processing, selectedEquipment, essenceKey, selectedSupport, slotKey) ? "disabled=\"true\"" : "");
        html = html.replace("{{extractDisabledAttr}}", shouldDisableExtract(processing, selectedEquipment, player) ? "disabled=\"true\"" : "");
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

    private static String buildEssenceOptions(Player player, List<Entry> entries, String selectedKey) {
        StringBuilder sb = new StringBuilder();
        boolean hasSelection = selectedKey != null && !selectedKey.isEmpty();
        sb.append("<option value=\"\"").append(!hasSelection ? " selected=\"true\"" : "")
                .append(">").append(escapeHtml(LangLoader.getUITranslation(player, "ui.essence_bench.option_none"))).append("</option>");
        if (entries.isEmpty()) {
            sb.append("<option value=\"\" disabled=\"true\">")
                    .append(escapeHtml(LangLoader.getUITranslation(player, "ui.essence_bench.option_no_essence")))
                    .append("</option>");
            return sb.toString();
        }
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

    private static String buildSupportOptions(Player player, List<Entry> entries, String selectedKey) {
        StringBuilder sb = new StringBuilder();
        sb.append("<option value=\"\"").append(selectedKey == null || selectedKey.isEmpty() ? " selected=\"true\"" : "")
                .append(">").append(escapeHtml(LangLoader.getUITranslation(player, "ui.essence_bench.option_none"))).append("</option>");
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            String key = String.valueOf(i);
            sb.append("<option value=\"").append(key).append("\"");
            if (key.equals(selectedKey)) {
                sb.append(" selected=\"true\"");
            }
            String suffix = isHammerItem(entry.itemId)
                    ? " " + LangLoader.getUITranslation(player, "ui.essence_bench.support_suffix_clear")
                    : (isVoidheartItem(entry.itemId)
                    ? " " + LangLoader.getUITranslation(player, "ui.essence_bench.support_suffix_repair")
                    : "");
            if (suffix.isEmpty() && ResonantRecipeUtils.isResonantRecipeItem(entry.item)) {
                ResonantRecipeUtils.UsageState usage = ResonantRecipeUtils.getUsageState(entry.item);
                String usageLabel = ResonantRecipeUtils.formatUsages(usage);
                suffix = usageLabel.isEmpty()
                        ? " " + LangLoader.getUITranslation(player, "ui.essence_bench.support_suffix_recipe")
                        : " " + LangLoader.getUITranslation(player, "ui.essence_bench.support_suffix_recipe_usage", usageLabel);
            }
            sb.append(">").append(escapeHtml(entry.displayName)).append(suffix).append(" x").append(entry.quantity).append("</option>");
        }
        return sb.toString();
    }

    private static String buildSlotOptions(Player player, Entry equipment, String selectedKey) {
        StringBuilder sb = new StringBuilder();
        boolean hasSelection = selectedKey != null && !selectedKey.isEmpty();
        sb.append("<option value=\"\"").append(!hasSelection ? " selected=\"true\"" : "")
                .append(">")
                .append(escapeHtml(LangLoader.getUITranslation(player, "ui.essence_bench.option_auto")))
                .append("</option>");

        if (equipment == null || equipment.item == null || equipment.item.isEmpty()) {
            sb.append("<option value=\"\" disabled=\"true\">")
                    .append(escapeHtml(LangLoader.getUITranslation(player, "ui.essence_bench.option_no_equipment_selected")))
                    .append("</option>");
            return sb.toString();
        }
        SocketData sd = SocketManager.getSocketData(equipment.item);
        if (sd == null || sd.getSockets().isEmpty()) {
            sb.append("<option value=\"\" disabled=\"true\">")
                    .append(escapeHtml(LangLoader.getUITranslation(player, "ui.essence_bench.option_no_sockets")))
                    .append("</option>");
            return sb.toString();
        }

        for (Socket socket : sd.getSockets()) {
            if (socket == null) {
                continue;
            }
            String key = String.valueOf(socket.getSlotIndex());
            sb.append("<option value=\"").append(key).append("\"");
            if (key.equals(selectedKey)) {
                sb.append(" selected=\"true\"");
            }
            sb.append(">").append(escapeHtml(buildSlotLabel(player, socket))).append("</option>");
        }

        return sb.toString();
    }

    private static String buildSlotLabel(Player player, Socket socket) {
        if (socket == null) {
            return LangLoader.getUITranslation(player, "ui.essence_bench.slot_unknown");
        }
        int slotNumber = socket.getSlotIndex() + 1;
        if (socket.isBroken()) {
            return LangLoader.getUITranslation(player, "ui.essence_bench.slot_broken", slotNumber);
        }
        if (socket.isLocked()) {
            return LangLoader.getUITranslation(player, "ui.essence_bench.slot_locked", slotNumber);
        }
        if (socket.isEmpty()) {
            return LangLoader.getUITranslation(player, "ui.essence_bench.slot_empty", slotNumber);
        }
        String essenceLabel = resolveEssenceLabel(player, socket);
        return LangLoader.getUITranslation(player, "ui.essence_bench.slot_filled", slotNumber, essenceLabel);
    }

    private static String resolveEssenceLabel(Player player, Socket socket) {
        if (socket == null || socket.isEmpty()) {
            return LangLoader.getUITranslation(player, "ui.essence_bench.slot_empty_short");
        }
        String essenceId = socket.getEssenceId();
        Essence essence = essenceId == null ? null : EssenceRegistry.get().getById(essenceId);
        Essence.Type type = essence != null ? essence.getType() : null;
        String name = type != null
                ? formatEssenceToken(player, type)
                : LangLoader.getUITranslation(player, "ui.essence_bench.essence_generic");
        if (essenceId != null && SocketManager.isGreaterEssenceId(essenceId)) {
            return LangLoader.getUITranslation(player, "ui.essence_bench.essence_concentrated", name);
        }
        return name;
    }

    private static boolean shouldDisable(boolean processing, Entry equipment, String essenceKey, Entry selectedSupport, String slotKey) {
        if (processing) return true;
        if (equipment == null) return true;
        if (selectedSupport != null && isHammerItem(selectedSupport.itemId)) {
            return false;
        }
        if (essenceKey == null || essenceKey.isEmpty()) {
            return !canRepairWithoutEssence(equipment, selectedSupport)
                    && !canAutoSocketWithRecipe(equipment, selectedSupport)
                    && !canRepairSelectedSlot(equipment, selectedSupport, slotKey);
        }
        return isFilled(equipment);
    }

    private static boolean shouldDisableExtract(boolean processing, Entry equipment, Player player) {
        if (processing) return true;
        if (equipment == null) return true;
        ItemStack current = equipmentItem(equipment, player);
        if (current == null || current.isEmpty()) return true;
        return !SocketManager.hasResonance(current);
    }

    private static boolean canRepairSelectedSlot(Entry equipment, Entry support, String slotKey) {
        if (equipment == null || support == null) return false;
        if (!isVoidheartItem(support.itemId)) return false;
        SocketData sd = SocketManager.getSocketData(equipment.item);
        if (sd == null || sd.getSockets().isEmpty()) return false;
        int slotIndex = resolveSlotIndex(slotKey, sd);
        if (slotIndex < 0) return false;
        Socket target = findSocketByIndex(sd, slotIndex);
        return target != null && target.isBroken();
    }

    private static boolean canAutoSocketWithRecipe(Entry equipment, Entry support) {
        if (equipment == null || support == null) return false;
        if (!isRecipeSupport(support)) return false;
        String recipeName = ResonantRecipeUtils.getRecipeName(support.item);
        if (recipeName == null || recipeName.isBlank()) return false;
        Essence.Type[] pattern = ResonanceSystem.getPatternForRecipeName(recipeName);
        if (pattern == null || pattern.length == 0) return false;
        SocketData sd = SocketManager.getSocketData(equipment.item);
        if (sd == null) return false;
        for (int i = 0; i < Math.min(pattern.length, sd.getSockets().size()); i++) {
            Socket socket = sd.getSockets().get(i);
            if (socket == null) continue;
            if (socket.isBroken() || socket.isLocked() || !socket.isEmpty()) continue;
            return true;
        }
        return false;
    }

    private static boolean canRepairWithoutEssence(Entry equipment, Entry support) {
        if (equipment == null || support == null) return false;
        if (!isVoidheartItem(support.itemId)) return false;
        SocketData sd = SocketManager.getSocketData(equipment.item);
        if (sd == null || !sd.hasBrokenSocket()) return false;
        for (Socket socket : sd.getSockets()) {
            if (!socket.isBroken() && socket.isEmpty() && !socket.isLocked()) {
                return false;
            }
        }
        return true;
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
            // Use anchor sizing (HyUI-friendly) to keep wrapper visible.
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
        String lower = essenceId.toLowerCase(Locale.ROOT);
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
            String lower = essenceId.toLowerCase(Locale.ROOT);
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
        String base = "essence_" + type.name().toLowerCase(Locale.ROOT);
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
            if (DEBUG_ESSENCE_ICON) {
                System.out.println("[SocketReforge] Essence icon lookup: essenceId=" + essenceId + " -> itemId not resolved");
            }
            return null;
        }
        if (DEBUG_ESSENCE_ICON) {
            System.out.println("[SocketReforge] Essence icon lookup: essenceId=" + essenceId + " -> itemId=" + itemId);
        }
        try {
            Item item = Item.getAssetMap().getAssetMap().get(itemId);
            if (item == null || item == Item.UNKNOWN) {
                if (DEBUG_ESSENCE_ICON) {
                    System.out.println("[SocketReforge] Essence icon lookup: item not found for " + itemId);
                }
                return null;
            }
            String icon = item.getIcon();
            if (icon == null || icon.isBlank()) {
                if (DEBUG_ESSENCE_ICON) {
                    System.out.println("[SocketReforge] Essence icon lookup: no icon for " + itemId);
                }
                return null;
            }
            if (Item.UNKNOWN_TEXTURE.equals(icon)) {
                if (DEBUG_ESSENCE_ICON) {
                    System.out.println("[SocketReforge] Essence icon lookup: unknown texture for " + itemId);
                }
                return null;
            }
            String uiIcon = resolveUiIconPath(icon);
            if (uiIcon != null) {
                if (DEBUG_ESSENCE_ICON) {
                    System.out.println("[SocketReforge] Essence icon lookup: icon=" + icon + " -> ui=" + uiIcon);
                }
                return uiIcon;
            }
            if (DEBUG_ESSENCE_ICON) {
                System.out.println("[SocketReforge] Essence icon lookup: icon=" + icon + " for " + itemId);
            }
            return icon;
        } catch (Exception ignored) {
            if (DEBUG_ESSENCE_ICON) {
                System.out.println("[SocketReforge] Essence icon lookup: failed for " + itemId);
            }
            return null;
        }
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
        return "Icons/" + normalized;
    }

    private static boolean isFilled(Entry equipment) {
        if (equipment == null) return false;
        SocketData sd = SocketManager.getSocketData(equipment.item);
        return sd != null && !sd.hasEmptySocket();
    }

    private static int resolveSlotIndex(String slotKey, SocketData socketData) {
        if (slotKey == null || slotKey.isBlank() || socketData == null) {
            return -1;
        }
        try {
            int idx = Integer.parseInt(slotKey.trim());
            if (idx < 0 || idx >= socketData.getSockets().size()) {
                return -1;
            }
            return idx;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static Socket findSocketByIndex(SocketData socketData, int slotIndex) {
        if (socketData == null || slotIndex < 0) {
            return null;
        }
        for (Socket socket : socketData.getSockets()) {
            if (socket != null && socket.getSlotIndex() == slotIndex) {
                return socket;
            }
        }
        return null;
    }

    private static ProcessResult processSelection(Player player, Entry equipment, Entry essence, Entry support, String slotKey) {
        if (equipment == null) return new ProcessResult(t(player, "ui.essence_bench.error_pick_equipment"), 0);

        ItemStack item = equipmentItem(equipment, player);
        if (item == null || item.isEmpty()) {
            return new ProcessResult(t(player, "ui.essence_bench.error_equipment_missing"), 0);
        }
        boolean isWeapon = ReforgeEquip.isWeapon(item);
        boolean isArmor = ReforgeEquip.isArmor(item);
        if (!isWeapon && !isArmor) {
            return new ProcessResult(t(player, "ui.essence_bench.error_item_not_equipment"), 0);
        }

        SocketData socketData = SocketManager.getSocketData(item);
        if (socketData == null || socketData.getMaxSockets() == 0 || socketData.getSockets().isEmpty()) {
            return new ProcessResult(t(player, "ui.essence_bench.error_no_sockets"), 0);
        }
        int selectedSlot = resolveSlotIndex(slotKey, socketData);

        // Hammer support action: clear all socketed essences (broken sockets remain broken).
        if (support != null && isHammerItem(support.itemId)) {
            Socket target = selectedSlot >= 0 ? findSocketByIndex(socketData, selectedSlot) : null;
            if (selectedSlot >= 0 && target == null) {
                return new ProcessResult(t(player, "ui.essence_bench.error_slot_invalid"), 0);
            }
            int clearable = 0;
            if (target != null) {
                if (!target.isBroken() && !target.isEmpty()) {
                    clearable = 1;
                }
            } else {
                for (Socket socket : socketData.getSockets()) {
                    if (!socket.isBroken() && !socket.isEmpty()) {
                        clearable++;
                    }
                }
            }
            if (clearable <= 0) {
                sfxConfig.playNoChange(player);
                return new ProcessResult(t(player, "ui.essence_bench.clear_none"), 100);
            }

            boolean isThoriumHammer = isThoriumHammerItem(support.itemId);
            Map<String, Integer> refundCounts = new LinkedHashMap<>();
            if (isThoriumHammer) {
                if (target != null) {
                    if (!target.isBroken() && !target.isEmpty()) {
                        String essenceId = target.getEssenceId();
                        if (essenceId != null && !essenceId.isBlank()) {
                            refundCounts.merge(essenceId, 1, Integer::sum);
                        }
                    }
                } else {
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
            }

            double hammerWear;
            if (selectedSlot >= 0) {
                hammerWear = isThoriumHammer ? 0.02d : 0.03d;
            } else {
                hammerWear = isThoriumHammer ? 0.08d : 0.10d;
            }
            HammerUseResult hammerUse = applyHammerWear(player, support, hammerWear);
            if (!hammerUse.ok) {
                return new ProcessResult(t(player, "ui.essence_bench.error_hammer_changed"), 0);
            }

            double successChance = SocketManager.getConfig() != null
                    ? SocketManager.getConfig().getEssenceRemovalSuccessChance()
                    : 0.70;
            boolean success = Math.random() < successChance;

            boolean reducedMaxSockets = false;
            int removed = 0;
            if (success) {
                if (target != null) {
                    if (!target.isBroken() && !target.isEmpty()) {
                        target.setEssenceId(null);
                        removed = 1;
                    }
                } else {
                    for (Socket socket : socketData.getSockets()) {
                        if (!socket.isBroken() && !socket.isEmpty()) {
                            socket.setEssenceId(null);
                            removed++;
                        }
                    }
                }
            } else {
                if (target != null) {
                    if (!target.isBroken()) {
                        target.setBroken(true);
                        target.setEssenceId(null);
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
                String slotSuffix = "";
                if (target != null) {
                    String rawSuffix = t(player, "ui.essence_bench.clear_slot_suffix", selectedSlot + 1);
                    if (rawSuffix != null && !rawSuffix.isBlank()) {
                        slotSuffix = " " + rawSuffix;
                    }
                }
                String successStatus = hammerUse.consumed
                        ? t(player, "ui.essence_bench.clear_success_broke", removed, slotSuffix)
                        : t(player, "ui.essence_bench.clear_success", removed, slotSuffix);
                String refundSuffix = isThoriumHammer ? buildRefundSuffix(player, removed, refunded) : "";
                if (refundSuffix != null && !refundSuffix.isBlank()) {
                    successStatus += " " + refundSuffix;
                }
                if (reducedMaxSockets) {
                    successStatus += " " + t(player, "ui.essence_bench.clear_max_reduced", socketData.getMaxSockets());
                }
                return new ProcessResult(successStatus, 100);
            }

            sfxConfig.playShatter(player);
            String failureStatus = hammerUse.consumed
                    ? (target != null
                        ? t(player, "ui.essence_bench.clear_failed_selected_broke")
                        : t(player, "ui.essence_bench.clear_failed_random_broke"))
                    : (target != null
                        ? t(player, "ui.essence_bench.clear_failed_selected")
                        : t(player, "ui.essence_bench.clear_failed_random"));
            if (reducedMaxSockets) {
                failureStatus += " " + t(player, "ui.essence_bench.clear_max_reduced", socketData.getMaxSockets());
            }
            return new ProcessResult(failureStatus, 100);
        }

        if (support != null && isVoidheartItem(support.itemId) && selectedSlot >= 0) {
            Socket target = findSocketByIndex(socketData, selectedSlot);
            if (target == null) {
                return new ProcessResult(t(player, "ui.essence_bench.error_slot_invalid"), 0);
            }
            if (!target.isBroken()) {
                return new ProcessResult(t(player, "ui.essence_bench.error_slot_not_broken"), 100);
            }
            if (!consumeMaterial(player, support, 1)) {
                return new ProcessResult(t(player, "ui.essence_bench.error_voidheart_changed"), 0);
            }
            target.setBroken(false);
            target.setEssenceId(null);
            ItemStack repaired = SocketManager.withSocketData(item, socketData);
            writeStack(player, equipment, repaired);
            socketData.registerTooltips(repaired, repaired.getItemId(), isWeapon);
            DynamicTooltipUtils.refreshAllPlayers();
            sfxConfig.playSuccess(player);
            return new ProcessResult(t(player, "ui.essence_bench.status_socket_repaired_slot", selectedSlot + 1), 100);
        }

        boolean hasFillableSocket = false;
        for (Socket socket : socketData.getSockets()) {
            if (!socket.isBroken() && socket.isEmpty() && !socket.isLocked()) {
                hasFillableSocket = true;
                break;
            }
        }

        // Only force repair-first when no non-broken empty sockets are available.
        if (!hasFillableSocket && socketData.hasBrokenSocket()) {
            if (support == null || !isVoidheartItem(support.itemId)) {
                return new ProcessResult(t(player, "ui.essence_bench.error_broken_socket_select_voidheart"), 100);
            }
            if (!consumeMaterial(player, support, 1)) {
                return new ProcessResult(t(player, "ui.essence_bench.error_voidheart_changed"), 0);
            }
            if (selectedSlot >= 0) {
                Socket target = findSocketByIndex(socketData, selectedSlot);
                if (target == null) {
                    return new ProcessResult(t(player, "ui.essence_bench.error_slot_invalid"), 0);
                }
                if (!target.isBroken()) {
                    return new ProcessResult(t(player, "ui.essence_bench.error_slot_not_broken"), 100);
                }
                target.setBroken(false);
                target.setEssenceId(null);
            } else if (!socketData.repairBrokenSocket()) {
                return new ProcessResult(t(player, "ui.essence_bench.error_repair_failed"), 0);
            }

            ItemStack repaired = SocketManager.withSocketData(item, socketData);
            writeStack(player, equipment, repaired);
            socketData.registerTooltips(repaired, repaired.getItemId(), isWeapon);
            DynamicTooltipUtils.refreshAllPlayers();
            sfxConfig.playSuccess(player);
            if (selectedSlot >= 0) {
                return new ProcessResult(t(player, "ui.essence_bench.status_socket_repaired_slot", selectedSlot + 1), 100);
            }
            return new ProcessResult(t(player, "ui.essence_bench.status_socket_repaired"), 100);
        }

        Entry resolvedEssence = essence;
        String autoNotice = null;
        if (resolvedEssence == null && support != null && isRecipeSupport(support)) {
            AutoEssenceSelection auto = autoSelectEssenceForRecipe(player, equipment, support, socketData, selectedSlot);
            if (auto != null && auto.error != null) {
                return new ProcessResult(auto.error, 0);
            }
            if (auto != null && auto.entry != null) {
                resolvedEssence = auto.entry;
                autoNotice = auto.notice;
            }
        }
        if (resolvedEssence == null) {
            return new ProcessResult(t(player, "ui.essence_bench.error_pick_essence"), 0);
        }

        if (!hasFillableSocket) {
            return new ProcessResult(t(player, "ui.essence_bench.error_all_filled"), 100);
        }

        if (!consumeMaterial(player, resolvedEssence, 1)) {
            return new ProcessResult(t(player, "ui.essence_bench.error_essence_changed"), 0);
        }

        String essenceType = SocketManager.resolveEssenceTypeFromItemId(resolvedEssence.itemId);
        String essenceId = SocketManager.resolveEssenceIdFromItemId(resolvedEssence.itemId);
        if (essenceType == null || essenceId == null) {
            return new ProcessResult(t(player, "ui.essence_bench.error_invalid_essence"), 0);
        }
        if (!EssenceRegistry.get().exists(essenceId)) {
            return new ProcessResult(t(player, "ui.essence_bench.error_essence_not_found", essenceId), 0);
        }
        if (selectedSlot >= 0) {
            Socket target = findSocketByIndex(socketData, selectedSlot);
            if (target == null) {
                return new ProcessResult(t(player, "ui.essence_bench.error_slot_invalid"), 0);
            }
            if (target.isBroken()) {
                return new ProcessResult(t(player, "ui.essence_bench.error_slot_broken"), 100);
            }
            if (target.isLocked()) {
                return new ProcessResult(t(player, "ui.essence_bench.error_slot_locked"), 100);
            }
            if (!target.isEmpty()) {
                return new ProcessResult(t(player, "ui.essence_bench.error_slot_filled"), 100);
            }
            target.setEssenceId(essenceId);
        } else if (!SocketManager.socketEssence(socketData, essenceId)) {
            return new ProcessResult(t(player, "ui.essence_bench.error_socket_failed"), 0);
        }

        ResonanceSystem.ResonanceResult rawResonance = ResonanceSystem.evaluate(item, socketData);
        boolean rawActive = rawResonance != null && rawResonance.active();
        boolean consumedRecipe = false;
        String resonanceNotice = null;
        ItemStack baseItem = item;

        if (rawActive) {
            String resonanceName = rawResonance.name();
            boolean alreadyUnlocked = SocketManager.isResonanceUnlocked(item, resonanceName);
            if (alreadyUnlocked) {
                baseItem = SocketManager.withResonanceUnlock(item, resonanceName);
            } else if (support != null && isRecipeSupport(support)) {
                String recipeName = ResonantRecipeUtils.getRecipeName(support.item);
                boolean nameMatches = recipeName != null
                        && ResonantRecipeUtils.normalizeRecipeName(recipeName)
                        .equals(ResonantRecipeUtils.normalizeRecipeName(resonanceName));
                if (!nameMatches) {
                    resonanceNotice = t(player, "ui.essence_bench.error_recipe_mismatch_resonance");
                } else {
                    ResonantRecipeUtils.UsageState usage = ResonantRecipeUtils.getUsageState(support.item);
                    if (!usage.hasRemaining()) {
                        resonanceNotice = t(player, "ui.essence_bench.error_recipe_no_usages");
                    } else {
                        ItemStack updatedSupport = ResonantRecipeUtils.decrementUsage(support.item);
                        // Always write back; UI snapshot stacks may not reflect inventory mutations.
                        writeStack(player, support, updatedSupport);
                        consumedRecipe = true;
                        baseItem = SocketManager.withResonanceUnlock(item, resonanceName);
                    }
                }
            } else {
                resonanceNotice = t(player, "ui.essence_bench.error_resonance_locked");
            }
        }

        ItemStack updated = SocketManager.withSocketData(baseItem, socketData);
        writeStack(player, equipment, updated);
        socketData.registerTooltips(updated, updated.getItemId(), isWeapon);
        DynamicTooltipUtils.refreshAllPlayers();
        sfxConfig.playSuccess(player);
        String status = autoNotice != null
                ? autoNotice
                : (selectedSlot >= 0
                    ? t(player, "ui.essence_bench.status_essence_socketed_slot", selectedSlot + 1)
                    : t(player, "ui.essence_bench.status_essence_socketed"));
        if (rawActive) {
            if (SocketManager.hasResonance(updated)) {
                status = consumedRecipe
                        ? t(player, "ui.essence_bench.status_resonance_unlocked")
                        : t(player, "ui.essence_bench.status_resonance_active");
            } else if (resonanceNotice != null) {
                status = t(player, "ui.essence_bench.status_essence_socketed_with_notice", resonanceNotice);
            }
        }
        if (autoNotice != null && !status.startsWith("Auto-") && !status.startsWith("Auto ")) {
            status = autoNotice + " " + status;
        }
        return new ProcessResult(status, 100);
    }

    private static ProcessResult processExtraction(Player player, Entry equipment) {
        if (equipment == null) {
            return new ProcessResult(t(player, "ui.essence_bench.status_extract_no_equipment"), 0);
        }
        ItemStack current = equipmentItem(equipment, player);
        if (current == null || current.isEmpty()) {
            return new ProcessResult(t(player, "ui.essence_bench.status_extract_no_equipment"), 0);
        }
        if (!SocketManager.hasResonance(current)) {
            return new ProcessResult(t(player, "ui.essence_bench.status_extract_no_resonance"), 0);
        }

        int yield = calculateResonantEssenceYield(current);
        ItemStack output = new ItemStack(RESONANT_ESSENCE_ID, Math.max(1, yield));
        if (!canAddToInventory(player, output)) {
            return new ProcessResult(t(player, "ui.essence_bench.status_extract_no_space"), 0);
        }

        boolean removed = UIInventoryUtils.removeItem(player, equipment.kind == ContainerKind.HOTBAR, equipment.slot, 1);
        if (!removed) {
            return new ProcessResult(t(player, "ui.essence_bench.status_extract_failed"), 0);
        }
        if (!UIInventoryUtils.addItemToInventory(player, output)) {
            // Best effort restore to avoid losing the equipment.
            UIInventoryUtils.writeItem(player, equipment.kind == ContainerKind.HOTBAR, equipment.slot, current);
            return new ProcessResult(t(player, "ui.essence_bench.status_extract_failed"), 0);
        }

        sfxConfig.playSuccess(player);
        return new ProcessResult(t(player, "ui.essence_bench.status_extract_done", output.getQuantity()), 100);
    }

    private static boolean canAddToInventory(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return false;
        }
        ItemContainer hotbar = UIInventoryUtils.getContainer(player, true);
        if (hotbar != null && hotbar.canAddItemStack(stack)) {
            return true;
        }
        ItemContainer storage = UIInventoryUtils.getContainer(player, false);
        return storage != null && storage.canAddItemStack(stack);
    }

    private static int calculateResonantEssenceYield(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return 1;
        }
        int yield = 1;
        Integer refineLevel = item.getFromMetadataOrNull(META_REFINEMENT_LEVEL, Codec.INTEGER);
        if (refineLevel != null && refineLevel > 0) {
            yield += Math.max(0, refineLevel / 5);
        }
        if (SocketManager.isResonanceLegendary(item)) {
            yield += 1;
        }
        return Math.max(1, yield);
    }

    private static AutoEssenceSelection autoSelectEssenceForRecipe(Player player, Entry equipment, Entry support,
                                                                   SocketData socketData, int selectedSlot) {
        if (player == null || equipment == null || support == null || socketData == null) {
            return null;
        }
        if (!isRecipeSupport(support)) {
            return null;
        }
        String recipeName = ResonantRecipeUtils.getRecipeName(support.item);
        if (recipeName == null || recipeName.isBlank()) {
            return new AutoEssenceSelection(null, null, t(player, "ui.essence_bench.error_recipe_missing_name"));
        }
        Essence.Type[] pattern = ResonanceSystem.getPatternForRecipeName(recipeName);
        if (pattern == null || pattern.length == 0) {
            return new AutoEssenceSelection(null, null, t(player, "ui.essence_bench.error_recipe_pattern_missing"));
        }

        int limit = Math.min(pattern.length, socketData.getSockets().size());
        int targetIndex = -1;
        Essence.Type targetType = null;
        if (selectedSlot >= 0) {
            if (selectedSlot >= limit) {
                return new AutoEssenceSelection(null, null, t(player, "ui.essence_bench.error_slot_outside_pattern"));
            }
            Socket socket = findSocketByIndex(socketData, selectedSlot);
            if (socket == null) {
                return new AutoEssenceSelection(null, null, t(player, "ui.essence_bench.error_slot_invalid"));
            }
            if (socket.isBroken()) {
                return new AutoEssenceSelection(null, null, t(player, "ui.essence_bench.error_slot_broken"));
            }
            if (socket.isLocked()) {
                return new AutoEssenceSelection(null, null, t(player, "ui.essence_bench.error_slot_locked"));
            }
            if (!socket.isEmpty()) {
                return new AutoEssenceSelection(null, null, t(player, "ui.essence_bench.error_slot_filled"));
            }
            targetIndex = selectedSlot;
            targetType = pattern[selectedSlot];
        } else {
            for (int i = 0; i < limit; i++) {
                Socket socket = socketData.getSockets().get(i);
                if (socket == null) continue;
                if (socket.isBroken() || socket.isLocked() || !socket.isEmpty()) {
                    continue;
                }
                targetIndex = i;
                targetType = pattern[i];
                break;
            }
        }

        if (targetIndex < 0 || targetType == null) {
            return new AutoEssenceSelection(null, null, t(player, "ui.essence_bench.error_no_fillable_socket"));
        }

        Entry best = findBestEssenceEntry(player, targetType);
        if (best == null) {
            return new AutoEssenceSelection(null, null,
                    t(player, "ui.essence_bench.error_no_matching_essence", targetIndex + 1));
        }

        String notice = t(player, "ui.essence_bench.notice_auto_socketed", best.displayName, targetIndex + 1);
        return new AutoEssenceSelection(best, notice, null);
    }

    private static Entry findBestEssenceEntry(Player player, Essence.Type desiredType) {
        if (player == null || desiredType == null) {
            return null;
        }
        Entry best = null;
        best = pickBetterEssence(best, findBestEssenceInContainer(player, player.getInventory().getHotbar(), ContainerKind.HOTBAR, desiredType));
        best = pickBetterEssence(best, findBestEssenceInContainer(player, player.getInventory().getStorage(), ContainerKind.STORAGE, desiredType));
        return best;
    }

    private static Entry pickBetterEssence(Entry current, Entry candidate) {
        if (candidate == null) return current;
        if (current == null) return candidate;
        boolean currentGreater = SocketManager.isGreaterEssenceItemId(current.itemId);
        boolean candidateGreater = SocketManager.isGreaterEssenceItemId(candidate.itemId);
        if (candidateGreater && !currentGreater) {
            return candidate;
        }
        return current;
    }

    private static Entry findBestEssenceInContainer(Player player, ItemContainer container, ContainerKind kind, Essence.Type desiredType) {
        if (container == null || desiredType == null) {
            return null;
        }
        Entry best = null;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack == null || stack.isEmpty()) continue;
            String itemId = stack.getItemId();
            if (itemId == null || itemId.isBlank()) continue;
            String typeName = SocketManager.resolveEssenceTypeFromItemId(itemId);
            if (typeName == null) continue;
            Essence.Type type = toEssenceType(typeName);
            if (type != desiredType) continue;
            Entry entry = new Entry(kind, slot, stack, itemId, stack.getQuantity(), UIItemUtils.displayNameOrItemId(stack, player));
            best = pickBetterEssence(best, entry);
        }
        return best;
    }

    private static Essence.Type toEssenceType(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return null;
        }
        try {
            return Essence.Type.valueOf(typeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
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

    private static String buildRefundSuffix(Player player, int removed, int refunded) {
        if (removed <= 0) {
            return "";
        }
        if (refunded <= 0) {
            return LangLoader.getUITranslation(player, "ui.essence_bench.refund_failed");
        }
        if (refunded < removed) {
            return LangLoader.getUITranslation(player, "ui.essence_bench.refund_partial", refunded, removed);
        }
        return LangLoader.getUITranslation(player, "ui.essence_bench.refund_success", refunded);
    }

    private static HammerUseResult applyHammerWear(Player player, Entry hammerEntry, double durabilityFraction) {
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
                durabilityFraction,
                itemId -> isHammerItem(itemId));
        if (!wear.ok()) {
            return new HammerUseResult(false, false);
        }
        return new HammerUseResult(true, wear.consumed());
    }

    private static ItemContainer getContainer(Player player, ContainerKind kind) {
        return UIInventoryUtils.getContainer(player, kind == ContainerKind.HOTBAR);
    }

    private static String buildSocketSummary(Player player, Entry equipment) {
        if (equipment == null) {
            return LangLoader.getUITranslation(player, "ui.essence_bench.summary_no_equipment");
        }
        SocketData sd = SocketManager.getSocketData(equipment.item);
        if (sd == null) {
            return LangLoader.getUITranslation(player, "ui.essence_bench.summary_no_data");
        }
        int broken = 0;
        int filled = 0;
        for (irai.mod.reforge.Socket.Socket socket : sd.getSockets()) {
            if (socket.isBroken()) broken++;
            else if (!socket.isEmpty()) filled++;
        }
        int empty = Math.max(0, sd.getCurrentSocketCount() - filled - broken);
        return LangLoader.getUITranslation(player, "ui.essence_bench.summary_counts", filled, empty, broken);
    }

    private static String buildMetadata(Player player, Entry equipment) {
        if (equipment == null) {
            return LangLoader.getUITranslation(player, "ui.essence_bench.metadata_no_equipment");
        }
        StringBuilder sb = new StringBuilder();
        sb.append(LangLoader.getUITranslation(player, "ui.essence_bench.metadata_header_item")).append("\n");
        sb.append(LangLoader.getUITranslation(player, "ui.essence_bench.metadata_name", equipment.displayName)).append("\n");
        sb.append(LangLoader.getUITranslation(player, "ui.essence_bench.metadata_id", equipment.itemId)).append("\n");
        String location = equipment.kind == ContainerKind.HOTBAR
                ? LangLoader.getUITranslation(player, "ui.essence_bench.metadata_hotbar")
                : LangLoader.getUITranslation(player, "ui.essence_bench.metadata_storage");
        sb.append(LangLoader.getUITranslation(player, "ui.essence_bench.metadata_inventory", location, equipment.slot))
                .append("\n\n");
        SocketData sd = SocketManager.getSocketData(equipment.item);
        if (sd != null) {
            int broken = 0;
            int filled = 0;
            for (irai.mod.reforge.Socket.Socket socket : sd.getSockets()) {
                if (socket.isBroken()) broken++;
                else if (!socket.isEmpty()) filled++;
            }
            sb.append(LangLoader.getUITranslation(player, "ui.essence_bench.metadata_header_sockets")).append("\n");
            sb.append(LangLoader.getUITranslation(player, "ui.essence_bench.metadata_current_max",
                    sd.getCurrentSocketCount(), sd.getMaxSockets())).append("\n");
            sb.append(LangLoader.getUITranslation(player, "ui.essence_bench.metadata_filled_broken",
                    filled, broken)).append("\n\n");
        }
        sb.append(LangLoader.getUITranslation(player, "ui.essence_bench.metadata_header_metadata")).append("\n");
        if (equipment.item.getMetadata() != null && !equipment.item.getMetadata().isEmpty()) {
            int shown = 0;
            for (String key : equipment.item.getMetadata().keySet()) {
                if (shown >= 6) break;
                sb.append("- ").append(key).append("\n");
                shown++;
            }
        } else {
            sb.append(LangLoader.getUITranslation(player, "ui.essence_bench.metadata_none"));
        }
        return sb.toString();
    }

    private static String buildSupportDurabilityText(Player player, Entry support) {
        if (support == null || support.item == null || support.item.isEmpty()) {
            return LangLoader.getUITranslation(player, "ui.essence_bench.support_durability_none");
        }
        ItemStack item = support.item;
        if (ResonantRecipeUtils.isResonantRecipeItem(item)) {
            ResonantRecipeUtils.UsageState usage = ResonantRecipeUtils.getUsageState(item);
            String usageLabel = ResonantRecipeUtils.formatUsages(usage);
            return usageLabel.isEmpty()
                    ? LangLoader.getUITranslation(player, "ui.essence_bench.recipe_usages_none")
                    : LangLoader.getUITranslation(player, "ui.essence_bench.recipe_usages", usageLabel);
        }
        double max = item.getMaxDurability();
        double cur = item.getDurability();
        if (max <= 0.0d) {
            return LangLoader.getUITranslation(player, "ui.essence_bench.support_durability_na");
        }
        int maxInt = (int) Math.round(max);
        int curInt = (int) Math.round(cur);
        int percent = (int) Math.round((cur / max) * 100.0);
        percent = Math.max(0, Math.min(100, percent));
        return LangLoader.getUITranslation(player, "ui.essence_bench.support_durability_value",
                percent, curInt, maxInt);
    }

    private static String buildSupportRecipeText(Player player, Entry support) {
        if (support == null || support.item == null || support.item.isEmpty()) {
            return LangLoader.getUITranslation(player, "ui.essence_bench.recipe_none");
        }
        if (!ResonantRecipeUtils.isResonantRecipeItem(support.item)) {
            return LangLoader.getUITranslation(player, "ui.essence_bench.recipe_none");
        }
        String recipeName = ResonantRecipeUtils.getRecipeName(support.item);
        if (recipeName == null || recipeName.isBlank()) {
            return LangLoader.getUITranslation(player, "ui.essence_bench.recipe_unknown");
        }
        String localizedName = ResonanceSystem.getLocalizedName(recipeName, player);
        Essence.Type[] pattern = ResonanceSystem.getPatternForRecipeName(recipeName);
        String patternText = formatPattern(player, pattern);
        if (patternText.isBlank()) {
            return LangLoader.getUITranslation(player, "ui.essence_bench.recipe_label", localizedName);
        }
        return LangLoader.getUITranslation(player, "ui.essence_bench.recipe_with_pattern", localizedName, patternText);
    }

    private static String buildEffectPreviewText(Player player, Entry equipment, Entry support) {
        if (support == null || support.item == null || support.item.isEmpty()) {
            return LangLoader.getUITranslation(player, "ui.essence_bench.effect_preview_none");
        }
        if (!ResonantRecipeUtils.isResonantRecipeItem(support.item)) {
            return LangLoader.getUITranslation(player, "ui.essence_bench.effect_preview_none");
        }
        String recipeName = ResonantRecipeUtils.getRecipeName(support.item);
        if (recipeName == null || recipeName.isBlank()) {
            return LangLoader.getUITranslation(player, "ui.essence_bench.effect_preview_none");
        }
        ResonanceSystem.ResonanceResult result = ResonanceSystem.getResultForRecipeName(recipeName);
        if (result == null || !result.active()) {
            return LangLoader.getUITranslation(player, "ui.essence_bench.effect_preview_none");
        }
        boolean isWeapon = equipment != null && ReforgeEquip.isWeapon(equipment.item);
        boolean isArmor = equipment != null && ReforgeEquip.isArmor(equipment.item) && !isWeapon;
        boolean useWeaponContext = equipment == null || isWeapon || !isArmor;
        StringBuilder sb = new StringBuilder();
        String localizedName = ResonanceSystem.getLocalizedName(result.name(), player);
        sb.append(LangLoader.getUITranslation(player, "ui.essence_bench.effect_preview_header", localizedName));
        String flavor = ResonanceSystem.getLocalizedEffect(result.name(), result.effect(), player);
        if (flavor == null) {
            flavor = "";
        } else {
            flavor = flavor.trim();
        }
        if (!flavor.isBlank()) {
            sb.append("\n").append(flavor);
        }
        String details = ResonanceSystem.buildDetailedEffect(result, useWeaponContext, player);
        if (!details.isBlank()) {
            sb.append("\n").append(details);
        }
        return sb.toString();
    }

    private static String formatPattern(Player player, Essence.Type[] pattern) {
        if (pattern == null || pattern.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Essence.Type type : pattern) {
            sb.append('[').append(formatEssenceToken(player, type)).append(']');
        }
        return sb.toString();
    }

    private static String formatEssenceToken(Player player, Essence.Type type) {
        if (type == null) {
            return "x";
        }
        String key = switch (type) {
            case FIRE -> "essence.type.fire";
            case WATER -> "essence.type.water";
            case ICE -> "essence.type.ice";
            case LIGHTNING -> "essence.type.lightning";
            case LIFE -> "essence.type.life";
            case VOID -> "essence.type.void";
        };
        String lang = LangLoader.getPlayerLanguage(player);
        String translated = LangLoader.getTranslationForLanguage(key, lang);
        if (translated == null || translated.isBlank() || translated.equals(key)) {
            String raw = type.name().toLowerCase(Locale.ROOT);
            return raw.isEmpty() ? "x" : Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
        }
        return translated;
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

    private static String t(Player player, String key, Object... params) {
        return LangLoader.getUITranslation(player, key, params);
    }
}
