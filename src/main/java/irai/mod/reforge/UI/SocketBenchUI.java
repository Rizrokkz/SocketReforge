package irai.mod.reforge.UI;

import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.bson.BsonDocument;

import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import irai.mod.reforge.Common.UI.HyUIEditUtils;
import irai.mod.reforge.Common.UI.HyUIReflectionUtils;
import irai.mod.reforge.Common.UI.UIInventoryUtils;
import irai.mod.reforge.Common.UI.UIItemUtils;
import irai.mod.reforge.Common.UI.UISocketVisualUtils;
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
    private static final String HYUI_TEMPLATE_PROCESSOR = "au.ellie.hyui.html.TemplateProcessor";
    private static final String SOCKET_BENCH_TEMPLATE_PATH = "Common/UI/Custom/Pages/SocketBench.html";

    private static final String PUNCHER_ITEM_ID = "Socket_Puncher";
    private static final String NONE_SUPPORT_KEY = "__NONE_SUPPORT__";
    private static final String SUPPORT_KEY_DELIMITER = "|";

    private static boolean hyuiAvailable = false;
    private static final SFXConfig sfxConfig = new SFXConfig();

    private static final Map<PlayerRef, Object> openPages = new ConcurrentHashMap<>();
    private static final Map<PlayerRef, SelectionState> pendingSelections = new ConcurrentHashMap<>();
    private static final Map<PlayerRef, Boolean> processingPlayers = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService processScheduler = Executors.newSingleThreadScheduledExecutor();
    private static final int PROCESS_DURATION_MS = 1000;
    private static final int PROGRESS_TICK_MS = 200;
    private static final int ESSENCE_PREVIEW_SLOTS = 5;
    private static final int LORE_PREVIEW_SLOTS = 3;
    private static final String[][] SOCKET_PREVIEW_COLORS = {
            {"none", "#2b2b3a"},
            {"open", "#5A451E"},
            {"broken", "#8A2020"},
            {"fire", "#FFAA00"},
            {"ice", "#55FFFF"},
            {"life", "#55FF55"},
            {"lightning", "#FFFF55"},
            {"void", "#AA55FF"},
            {"water", "#5555FF"}
    };
    private static final String[][] LORE_PREVIEW_COLORS = {
            {"none", "#2b2b3a"},
            {"locked", "#3a3a3a"},
            {"red", "#FF5555"},
            {"blue", "#5599FF"},
            {"green", "#55FF77"},
            {"purple", "#AA55FF"},
            {"yellow", "#FFFF55"},
            {"orange", "#FFAA00"},
            {"black", "#555555"},
            {"white", "#FFFFFF"},
            {"cyan", "#55FFFF"}
    };

    private enum ContainerKind {
        HOTBAR,
        STORAGE
    }

    private enum ExpanderOutcome {
        NONE,
        GAINED,
        LOST,
        UNCHANGED;

        boolean changed() {
            return this == GAINED || this == LOST;
        }
    }

    private static final class SourceRef {
        final ContainerKind containerKind;
        final short slot;

        SourceRef(ContainerKind containerKind, short slot) {
            this.containerKind = containerKind;
            this.slot = slot;
        }
    }

    private static final class Entry {
        final ContainerKind containerKind;
        final short slot;
        final ItemStack item;
        final String itemId;
        final int quantity;
        final String displayName;
        final String selectionKey;
        final List<SourceRef> sources;

        Entry(ContainerKind containerKind, short slot, ItemStack item, String itemId, int quantity, String displayName) {
            this(containerKind, slot, item, itemId, quantity, displayName,
                    containerKind + ":" + slot + ":" + itemId,
                    List.of(new SourceRef(containerKind, slot)));
        }

        Entry(ContainerKind containerKind, short slot, ItemStack item, String itemId, int quantity, String displayName,
              String selectionKey, List<SourceRef> sources) {
            this.containerKind = containerKind;
            this.slot = slot;
            this.item = item;
            this.itemId = itemId;
            this.quantity = quantity;
            this.displayName = displayName;
            this.selectionKey = selectionKey;
            this.sources = sources == null ? List.of() : sources;
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
            player.getPlayerRef().sendMessage(Message.raw("<color=#FF5555>" + LangLoader.getUITranslation(player, "ui.socket_bench.hyui_missing")));
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
        List<Entry> rawPunchers = new ArrayList<>();
        List<Entry> rawSupports = new ArrayList<>();

        collectFromContainer(player, player.getInventory().getHotbar(), ContainerKind.HOTBAR, equipments, rawPunchers, rawSupports);
        collectFromContainer(player, player.getInventory().getStorage(), ContainerKind.STORAGE, equipments, rawPunchers, rawSupports);
        return new BenchSnapshot(
                equipments,
                mergeConsumableEntries(rawPunchers, "puncher"),
                mergeConsumableEntries(rawSupports, "support"));
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

    private static List<Entry> mergeConsumableEntries(List<Entry> entries, String keyPrefix) {
        LinkedHashMap<String, Entry> merged = new LinkedHashMap<>();
        if (entries == null) {
            return List.of();
        }
        for (Entry entry : entries) {
            if (entry == null || entry.itemId == null || entry.itemId.isBlank()) {
                continue;
            }
            String mergeKey = entry.itemId.toLowerCase();
            Entry existing = merged.get(mergeKey);
            if (existing == null) {
                merged.put(mergeKey, new Entry(
                        entry.containerKind,
                        entry.slot,
                        entry.item,
                        entry.itemId,
                        entry.quantity,
                        entry.displayName,
                        keyPrefix + ":" + mergeKey,
                        new ArrayList<>(entry.sources)));
                continue;
            }
            List<SourceRef> sources = new ArrayList<>(existing.sources);
            sources.addAll(entry.sources);
            merged.put(mergeKey, new Entry(
                    existing.containerKind,
                    existing.slot,
                    existing.item,
                    existing.itemId,
                    existing.quantity + entry.quantity,
                    existing.displayName,
                    existing.selectionKey,
                    sources));
        }
        return new ArrayList<>(merged.values());
    }

    private static void openPage(Player player, BenchSnapshot snapshot, SelectionState selectionState) {
        PlayerRef playerRef = player.getPlayerRef();

        try {
            Class<?> pageBuilderClass = Class.forName(HYUI_PAGE_BUILDER);
            Class<?> eventBindingClass = Class.forName(HYUI_EVENT_BINDING);
            Method pageForPlayer = pageBuilderClass.getMethod("pageForPlayer", PlayerRef.class);
            Method fromHtml = pageBuilderClass.getMethod("fromHtml", String.class);
            Method addListener = pageBuilderClass.getMethod(
                    "addEventListener",
                    String.class,
                    eventBindingClass,
                    java.util.function.BiConsumer.class);
            Method enablePersistentElementEdits = pageBuilderClass.getMethod("enablePersistentElementEdits", boolean.class);
            Method enableAsyncImageLoading = pageBuilderClass.getMethod("enableAsyncImageLoading", boolean.class);
            Method withLifetime = pageBuilderClass.getMethod("withLifetime", CustomPageLifetime.class);
            Method openMethod = pageBuilderClass.getMethod("open", Class.forName("com.hypixel.hytale.component.Store"));

            Object valueChanged = eventBindingClass.getField("ValueChanged").get(null);
            Object activating = eventBindingClass.getField("Activating").get(null);

            SelectionState effectiveState = normalizeSelectionState(snapshot, selectionState);
            Object pageBuilder = pageForPlayer.invoke(null, playerRef);
            pageBuilder = enablePersistentElementEdits.invoke(pageBuilder, true);
            pageBuilder = enableAsyncImageLoading.invoke(pageBuilder, true);
            String html = buildHtml(player, snapshot, effectiveState);
            pageBuilder = fromHtml.invoke(pageBuilder, html);

            final Player finalPlayer = player;
            final BenchSnapshot finalSnapshot = snapshot;

            registerEquipmentCardListeners(pageBuilder, addListener, activating, finalPlayer, finalSnapshot, effectiveState);
            registerMaterialCardListeners(pageBuilder, addListener, activating, finalPlayer, finalSnapshot, effectiveState);

            addListener.invoke(pageBuilder, "processButton", activating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        try {
                            if (Boolean.TRUE.equals(processingPlayers.get(finalPlayer.getPlayerRef()))) {
                                return;
                            }
                            SelectionState activeState = currentSelection(finalPlayer, finalSnapshot, effectiveState);
                            String equipmentVal = activeState != null ? activeState.equipmentKey : null;
                            String puncherVal = activeState != null ? activeState.puncherKey : null;
                            String supportVal = activeState != null ? activeState.supportKey : null;
                            Entry selectedEquipment = resolveSelection(finalSnapshot.equipments, equipmentVal);
                            Entry selectedPuncher = resolveSelection(finalSnapshot.punchers, puncherVal);
                            List<Entry> selectedSupports = resolveSupportSelections(finalSnapshot.supports, supportVal);
                            String selectedSupportKey = supportSelectionKey(supportVal, selectedSupports);
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
                            safeEditText(ctxObj, "statusLabel", LangLoader.getUITranslation(finalPlayer, "ui.socket_bench.status_processing"));
                            safeEditProgress(ctxObj, "socketProgress", 0);
                            safeEditDisabled(ctxObj, "processButton", true);
                            invokeUpdatePage(ctxObj, false);

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
                                    safeEditProgress(ctxObj, "socketProgress", timedProgress);
                                    safeEditText(ctxObj, "statusLabel", LangLoader.getUITranslation(finalPlayer, "ui.socket_bench.status_processing"));
                                }), delay, TimeUnit.MILLISECONDS);
                            }

                            processScheduler.schedule(() -> finalPlayer.getWorld().execute(() -> {
                                try {
                                    if (!Boolean.TRUE.equals(processingPlayers.get(finalPlayer.getPlayerRef()))) {
                                        return;
                                    }
                                    ProcessResult result = processSelection(finalPlayer, finalSnapshot, selectedEquipment, selectedPuncher, selectedSupports);

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
                                    BenchSnapshot refreshedSnapshot = collectSnapshot(finalPlayer);
                                    SelectionState completedState = normalizeSelectionState(refreshedSnapshot,
                                            pendingSelections.get(finalPlayer.getPlayerRef()));
                                    updateSelectionHighlights(ctxObj, finalSnapshot, activeState, completedState);
                                    updateCardData(ctxObj, finalPlayer, finalSnapshot);
                                    updateSelectionDetails(ctxObj, finalPlayer, refreshedSnapshot, completedState, true);
                                    safeEditProgress(ctxObj, "socketProgress", result.progressValue);
                                    safeEditText(ctxObj, "statusLabel", result.statusLine);
                                    safeEditDisabled(ctxObj, "processButton", processDisabled(finalPlayer, refreshedSnapshot, completedState));
                                    invokeUpdatePage(ctxObj, false);
                                    updateCardData(ctxObj, finalPlayer, finalSnapshot);
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

            Method onDismiss = pageBuilderClass.getMethod("onDismiss", java.util.function.BiConsumer.class);
            pageBuilder = onDismiss.invoke(pageBuilder,
                    (java.util.function.BiConsumer<Object, Object>) (pageObj, dismissedByPlayer) ->
                            cleanupDismissedPage(playerRef, pageObj));

            pageBuilder = withLifetime.invoke(pageBuilder, CustomPageLifetime.CanDismiss);
            Object store = getStore(playerRef);
            Object page = openMethod.invoke(pageBuilder, store);
            openPages.put(playerRef, page);
        } catch (Exception e) {
            System.err.println("[SocketReforge] SocketBenchUI open error: " + e.getMessage());
            e.printStackTrace();
        }
	    }

    private static void registerEquipmentCardListeners(
            Object pageBuilder,
            Method addListener,
            Object activating,
            Player player,
            BenchSnapshot snapshot,
            SelectionState state) throws Exception {
        if (snapshot == null || snapshot.equipments == null || snapshot.equipments.isEmpty()) {
            return;
        }
        for (int i = 0; i < snapshot.equipments.size(); i++) {
            Entry equipment = snapshot.equipments.get(i);
            if (equipment == null || equipment.item == null || equipment.item.isEmpty()) {
                continue;
            }
            final String equipmentKey = keyOf(equipment);
            addListener.invoke(pageBuilder, equipmentCardButtonId(i), activating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        try {
                            SelectionState current = currentSelection(player, snapshot, state);
                            applySelectionEdits(
                                    ctxObj,
                                    player,
                                    snapshot,
                                    current,
                                    new SelectionState(
                                            equipmentKey,
                                            current != null ? current.puncherKey : null,
                                            current != null ? current.supportKey : NONE_SUPPORT_KEY,
                                            null,
                                            0,
                                            false));
                        } catch (Exception e) {
                            System.err.println("[SocketReforge] SocketBenchUI equipment card click failed: " + e.getMessage());
                        }
                    });
        }
    }

    private static void registerMaterialCardListeners(
            Object pageBuilder,
            Method addListener,
            Object activating,
            Player player,
            BenchSnapshot snapshot,
            SelectionState state) throws Exception {
        if (snapshot != null && snapshot.punchers != null) {
            for (int i = 0; i < snapshot.punchers.size(); i++) {
                Entry puncher = snapshot.punchers.get(i);
                if (puncher == null || puncher.item == null || puncher.item.isEmpty()) {
                    continue;
                }
                final String puncherKey = keyOf(puncher);
                addListener.invoke(pageBuilder, puncherCardButtonId(i), activating,
                        (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                            SelectionState current = currentSelection(player, snapshot, state);
                            applySelectionEdits(ctxObj, player, snapshot, current,
                                    new SelectionState(
                                            current != null ? current.equipmentKey : null,
                                            puncherKey,
                                            current != null ? current.supportKey : NONE_SUPPORT_KEY,
                                            null,
                                            0,
                                            false));
                        });
            }
        }

        addListener.invoke(pageBuilder, supportNoneCardButtonId(), activating,
                (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                    SelectionState current = currentSelection(player, snapshot, state);
                    applySelectionEdits(ctxObj, player, snapshot, current,
                            new SelectionState(
                                    current != null ? current.equipmentKey : null,
                                    current != null ? current.puncherKey : null,
                                    NONE_SUPPORT_KEY,
                                    null,
                                    0,
                                    false));
                });

        if (snapshot != null && snapshot.supports != null) {
            for (int i = 0; i < snapshot.supports.size(); i++) {
                Entry support = snapshot.supports.get(i);
                if (support == null || support.item == null || support.item.isEmpty()) {
                    continue;
                }
                final String supportKey = keyOf(support);
                addListener.invoke(pageBuilder, supportCardButtonId(i), activating,
                        (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                            SelectionState current = currentSelection(player, snapshot, state);
                            String nextSupportKeys = toggleSupportKey(current != null ? current.supportKey : null, supportKey);
                            applySelectionEdits(ctxObj, player, snapshot, current,
                                    new SelectionState(
                                            current != null ? current.equipmentKey : null,
                                            current != null ? current.puncherKey : null,
                                            nextSupportKeys,
                                            null,
                                            0,
                                            false));
                        });
            }
        }
    }

    private static SelectionState normalizeSelectionState(BenchSnapshot snapshot, SelectionState state) {
        String equipmentKey = state != null ? state.equipmentKey : null;
        String puncherKey = state != null ? state.puncherKey : null;
        String supportKey = state != null ? state.supportKey : null;
        String statusText = state != null ? state.statusText : null;
        int progressValue = state != null ? state.progressValue : 0;
        boolean processing = state != null && state.processing;

        if ((puncherKey == null || findByKey(snapshot != null ? snapshot.punchers : null, puncherKey) == null)
                && snapshot != null && snapshot.punchers != null && !snapshot.punchers.isEmpty()) {
            puncherKey = keyOf(snapshot.punchers.get(0));
        }
        supportKey = normalizeSupportKeys(snapshot, supportKey);
        if (supportKey == null || supportKey.isBlank()) {
            supportKey = NONE_SUPPORT_KEY;
        }
        return new SelectionState(equipmentKey, puncherKey, supportKey, statusText, progressValue, processing);
    }

    private static SelectionState currentSelection(Player player, BenchSnapshot snapshot, SelectionState fallback) {
        SelectionState pending = pendingSelections.get(player.getPlayerRef());
        return normalizeSelectionState(snapshot, pending != null ? pending : fallback);
    }

    private static void applySelectionEdits(
            Object ctxObj,
            Player player,
            BenchSnapshot snapshot,
            SelectionState previousState,
            SelectionState requestedState) {
        SelectionState nextState = normalizeSelectionState(snapshot, requestedState);
        pendingSelections.put(player.getPlayerRef(), nextState);

        try {
            boolean equipmentChanged = !Objects.equals(
                    previousState != null ? previousState.equipmentKey : null,
                    nextState.equipmentKey);
            updateSelectionHighlights(ctxObj, snapshot, previousState, nextState);
            updateSelectionDetails(ctxObj, player, snapshot, nextState, equipmentChanged);
            invokeUpdatePage(ctxObj, false);
            refreshSelectedSocketPreviewFromInventory(ctxObj, player, snapshot, nextState);
        } catch (Exception e) {
            System.err.println("[SocketReforge] SocketBenchUI in-place selection update failed: " + e.getMessage());
        }
    }

    private static void refreshSelectedSocketPreviewFromInventory(
            Object ctxObj,
            Player player,
            BenchSnapshot snapshot,
            SelectionState state) {
        Entry selectedEquipment = findByKey(snapshot != null ? snapshot.equipments : null,
                state != null ? state.equipmentKey : null);
        if (selectedEquipment == null) {
            updateSocketPreviewVisuals(ctxObj, null);
            return;
        }
        ItemStack currentStack = readCurrentStack(player, selectedEquipment);
        Entry currentEquipment = currentStack == null || currentStack.isEmpty()
                ? selectedEquipment
                : new Entry(selectedEquipment.containerKind, selectedEquipment.slot, currentStack, currentStack.getItemId(),
                        currentStack.getQuantity(), selectedEquipment.displayName, selectedEquipment.selectionKey, selectedEquipment.sources);
        updateSocketPreviewVisuals(ctxObj, currentEquipment);
    }

    private static void updateSelectionHighlights(
            Object ctxObj,
            BenchSnapshot snapshot,
            SelectionState previousState,
            SelectionState nextState) {
        int nextEquipment = indexOfEntry(snapshot != null ? snapshot.equipments : null,
                nextState != null ? nextState.equipmentKey : null);
        refreshIndexedCardSelection(ctxObj,
                snapshot != null ? snapshot.equipments : null,
                nextEquipment,
                SocketBenchUI::equipmentCardSelectedLayerId,
                SocketBenchUI::equipmentCardMarkerId);

        int nextPuncher = indexOfEntry(snapshot != null ? snapshot.punchers : null,
                nextState != null ? nextState.puncherKey : null);
        refreshIndexedCardSelection(ctxObj,
                snapshot != null ? snapshot.punchers : null,
                nextPuncher,
                index -> materialCardSelectedLayerId(puncherCardButtonId(index)),
                index -> materialCardMarkerId(puncherCardButtonId(index)));

        updateSupportHighlight(ctxObj, snapshot, nextState);
    }

    private static void updateCardData(Object ctxObj, Player player, BenchSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        if (snapshot.equipments != null) {
            for (int i = 0; i < snapshot.equipments.size(); i++) {
                Entry entry = snapshot.equipments.get(i);
                ItemStack currentStack = readCurrentStack(player, entry);
                Entry currentEntry = currentStack == null
                        ? entry
                        : new Entry(entry.containerKind, entry.slot, currentStack, currentStack.getItemId(),
                                currentStack.getQuantity(), entry.displayName, entry.selectionKey, entry.sources);
                updateEquipmentCardSocketVisuals(ctxObj, currentEntry, i);
            }
        }
        if (snapshot.punchers != null) {
            for (int i = 0; i < snapshot.punchers.size(); i++) {
                Entry entry = snapshot.punchers.get(i);
                updateMaterialCardQuantity(ctxObj, puncherCardButtonId(i), currentQuantity(player, entry));
            }
        }
        if (snapshot.supports != null) {
            for (int i = 0; i < snapshot.supports.size(); i++) {
                Entry entry = snapshot.supports.get(i);
                updateMaterialCardQuantity(ctxObj, supportCardButtonId(i), currentQuantity(player, entry));
            }
        }
    }

    private static void updateMaterialCardQuantity(Object ctxObj, String buttonId, int quantity) {
        boolean visible = quantity > 0;
        safeEditVisible(ctxObj, buttonId, visible);
        if (!visible) {
            safeEditVisible(ctxObj, materialCardSelectedLayerId(buttonId), false);
            safeEditImage(ctxObj, selectedLayerImageId(materialCardSelectedLayerId(buttonId)), "");
            safeEditVisible(ctxObj, materialCardMarkerId(buttonId), false);
        }
        safeEditText(ctxObj, materialCardHeldLabelId(buttonId), "Held: " + Math.max(0, quantity));
    }

    private static void updateEquipmentCardSocketVisuals(Object ctxObj, Entry equipment, int equipmentIndex) {
        if (equipment == null || equipment.item == null || equipment.item.isEmpty()) {
            for (int i = 0; i < 5; i++) {
                safeEditVisible(ctxObj, equipmentCompactSocketWrapId(equipmentIndex, i), false);
            }
            return;
        }
        SocketData socketData = SocketManager.getSocketData(equipment.item);
        if (socketData == null) {
            socketData = SocketData.fromDefaults(ReforgeEquip.isWeapon(equipment.item) ? "weapon" : "armor");
        }
        int punchedSockets = Math.max(0, socketData.getCurrentSocketCount());
        List<Socket> sockets = socketData.getSockets();
        String brokenIconName = resolveBrokenSocketIconName();
        for (int i = 0; i < 5; i++) {
            boolean visible = i < punchedSockets;
            boolean punched = i < punchedSockets;
            Socket socket = i < sockets.size() ? sockets.get(i) : null;
            boolean broken = socket != null && socket.isBroken();
            boolean filled = socket != null && !socket.isBroken() && !socket.isEmpty();
            String backgroundIcon = resolveSocketBackgroundIcon(punched, broken, brokenIconName);
            String overlayIcon = punched && filled && !broken ? resolveEssenceIconName(socket) : null;
            safeEditVisible(ctxObj, equipmentCompactSocketWrapId(equipmentIndex, i), visible);
            safeEditStyle(ctxObj, equipmentCompactSocketWrapId(equipmentIndex, i),
                    compactSocketWrapStyle(socketPreviewAccent(socket, punched, broken, filled), visible));
            safeEditImage(ctxObj, equipmentCompactSocketBackgroundId(equipmentIndex, i), backgroundIcon);
            safeEditImage(ctxObj, equipmentCompactSocketOverlayId(equipmentIndex, i), overlayIcon != null ? overlayIcon : backgroundIcon);
            safeEditVisible(ctxObj, equipmentCompactSocketOverlayId(equipmentIndex, i), overlayIcon != null);
        }
        updateEquipmentCardLoreSocketVisuals(ctxObj, equipment, equipmentIndex);
    }

    private static void updateEquipmentCardLoreSocketVisuals(Object ctxObj, Entry equipment, int equipmentIndex) {
        LoreSocketData data = equipment == null || equipment.item == null || equipment.item.isEmpty()
                ? null
                : LoreSocketManager.getLoreSocketData(equipment.item);
        int count = data == null ? 0 : Math.max(0, data.getSocketCount());
        String baseIcon = UITemplateUtils.resolveCustomUiAsset("GemSlotEmpty.png", "GemSlotEmpty.png");
        for (int i = 0; i < 3; i++) {
            LoreSocketData.LoreSocket socket = i < count ? data.getSocket(i) : null;
            boolean visible = i < count;
            boolean filled = socket != null && !socket.isEmpty();
            String colorHex = resolveLoreColorHex(socket);
            String overlayIcon = filled ? resolveLoreGemOverlayIcon(socket) : null;
            safeEditVisible(ctxObj, equipmentCompactLoreSocketWrapId(equipmentIndex, i), visible);
            safeEditStyle(ctxObj, equipmentCompactLoreSocketWrapId(equipmentIndex, i),
                    compactLoreSocketWrapStyle(colorHex, visible));
            safeEditImage(ctxObj, equipmentCompactLoreSocketBackgroundId(equipmentIndex, i), baseIcon);
            safeEditImage(ctxObj, equipmentCompactLoreSocketOverlayId(equipmentIndex, i), overlayIcon != null ? overlayIcon : baseIcon);
            safeEditVisible(ctxObj, equipmentCompactLoreSocketOverlayId(equipmentIndex, i), overlayIcon != null);
        }
    }

    private static void updateSupportHighlight(
            Object ctxObj,
            BenchSnapshot snapshot,
            SelectionState nextState) {
        boolean nextNone = nextState == null
                || nextState.supportKey == null
                || NONE_SUPPORT_KEY.equals(nextState.supportKey);
        List<String> selectedSupportKeys = splitSupportKeys(nextState != null ? nextState.supportKey : null);
        safeEditVisible(ctxObj, supportNoneCardSelectedLayerId(), nextNone);
        safeEditImage(ctxObj, selectedLayerImageId(supportNoneCardSelectedLayerId()), nextNone ? "socket_panel_bg.png" : "");
        safeEditVisible(ctxObj, supportNoneCardMarkerId(), nextNone);
        refreshSupportCardSelection(ctxObj, snapshot != null ? snapshot.supports : null, selectedSupportKeys);
    }

    private static void refreshSupportCardSelection(Object ctxObj, List<Entry> entries, List<String> selectedKeys) {
        if (entries == null) {
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            boolean selected = containsSupportKey(selectedKeys, keyOf(entries.get(i)));
            String layerId = materialCardSelectedLayerId(supportCardButtonId(i));
            safeEditVisible(ctxObj, layerId, selected);
            safeEditImage(ctxObj, selectedLayerImageId(layerId), selected ? "socket_panel_bg.png" : "");
            safeEditVisible(ctxObj, materialCardMarkerId(supportCardButtonId(i)), selected);
        }
    }

    private static void refreshIndexedCardSelection(
            Object ctxObj,
            List<Entry> entries,
            int selectedIndex,
            java.util.function.IntFunction<String> idFactory,
            java.util.function.IntFunction<String> markerIdFactory) {
        if (entries == null) {
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            boolean selected = i == selectedIndex;
            String layerId = idFactory.apply(i);
            safeEditVisible(ctxObj, layerId, selected);
            safeEditImage(ctxObj, selectedLayerImageId(layerId), selected ? "socket_panel_bg.png" : "");
            safeEditVisible(ctxObj, markerIdFactory.apply(i), selected);
        }
    }

    private static void updateSelectionDetails(Object ctxObj, Player player, BenchSnapshot snapshot, SelectionState state, boolean refreshPreview) {
        Entry selectedEquipment = findByKey(snapshot != null ? snapshot.equipments : null,
                state != null ? state.equipmentKey : null);
        Entry selectedPuncher = findByKey(snapshot != null ? snapshot.punchers : null,
                state != null ? state.puncherKey : null);
        List<Entry> selectedSupports = findAllByKeys(snapshot != null ? snapshot.supports : null,
                state != null ? state.supportKey : null);
        boolean supportOverridesMax = supportOverridesMax(selectedSupports);
        String status = isSocketsMaxed(selectedEquipment) && !supportOverridesMax
                ? LangLoader.getUITranslation(player, "ui.socket_bench.status_max_sockets")
                : LangLoader.getUITranslation(player, "ui.socket_bench.status_idle");
        StatPreview stats = calculatePreview(snapshot,
                state != null ? state.equipmentKey : null,
                state != null ? state.supportKey : null);
        boolean disabled = selectedPuncher == null || (isSocketsMaxed(selectedEquipment) && !supportOverridesMax);

        safeEditText(ctxObj, "statusLabel", status);
        updateStatLabels(ctxObj, player, stats);
        safeEditProgress(ctxObj, "socketProgress", 0);
        safeEditDisabled(ctxObj, "processButton", disabled);
        if (refreshPreview) {
            updateSocketPreviewVisuals(ctxObj, selectedEquipment);
        }
    }

    private static String selectedEquipmentText(Player player, Entry entry) {
        return LangLoader.getUITranslation(player, "ui.socket_bench.equipment") + ": "
                + (entry == null ? LangLoader.getUITranslation(player, "ui.socket_bench.option_select_equipment") : entry.displayName);
    }

    private static String selectedPuncherText(Player player, Entry entry) {
        return LangLoader.getUITranslation(player, "ui.socket_bench.puncher") + ": "
                + (entry == null ? LangLoader.getUITranslation(player, "ui.socket_bench.option_no_puncher") : entry.displayName);
    }

    private static String selectedSupportText(Player player, Entry entry) {
        return LangLoader.getUITranslation(player, "ui.socket_bench.support") + ": "
                + (entry == null ? LangLoader.getUITranslation(player, "ui.socket_bench.option_none") : entry.displayName);
    }

    private static void updateStatLabels(Object ctxObj, Player player, StatPreview stats) {
        safeEditText(ctxObj, "successChanceLabel",
                LangLoader.getUITranslation(player, "ui.socket_bench.success_label") + ": " + stats.successText);
        safeEditText(ctxObj, "breakChanceLabel",
                LangLoader.getUITranslation(player, "ui.socket_bench.break_label") + ": " + stats.breakText);
        safeEditText(ctxObj, "socketCountLabel",
                LangLoader.getUITranslation(player, "ui.socket_bench.sockets_label") + ": " + stats.socketsText);
    }

    private static int indexOfEntry(List<Entry> entries, String key) {
        if (entries == null || key == null || key.isBlank()) {
            return -1;
        }
        for (int i = 0; i < entries.size(); i++) {
            if (key.equals(keyOf(entries.get(i)))) {
                return i;
            }
        }
        return -1;
    }

    private static void safeEditStyle(Object ctxObj, String id, String style) {
        HyUIEditUtils.editStyle(ctxObj, id, style);
    }

    private static void safeEditText(Object ctxObj, String id, String text) {
        HyUIEditUtils.editText(ctxObj, id, text);
    }

    private static void safeEditImage(Object ctxObj, String id, String image) {
        HyUIEditUtils.editImage(ctxObj, id, image);
    }

    private static void safeEditVisible(Object ctxObj, String id, boolean visible) {
        HyUIEditUtils.editVisible(ctxObj, id, visible);
    }

    private static void safeEditProgress(Object ctxObj, String id, int value) {
        HyUIEditUtils.editProgress(ctxObj, id, value);
    }

    private static void safeEditDisabled(Object ctxObj, String id, boolean disabled) {
        HyUIEditUtils.editDisabled(ctxObj, id, disabled);
    }

    private static void invokeUpdatePage(Object ctxObj, boolean rebuild) {
        HyUIEditUtils.updatePage(ctxObj, rebuild);
    }

    private static void updateSocketPreviewVisuals(Object ctxObj, Entry equipment) {
        updateEssenceSocketPreviewVisuals(ctxObj, equipment);
        updateLoreSocketPreviewVisuals(ctxObj, equipment);
    }

    private static void updateEssenceSocketPreviewVisuals(Object ctxObj, Entry equipment) {
        SocketData socketData = null;
        if (equipment != null && equipment.item != null && !equipment.item.isEmpty()) {
            socketData = SocketManager.getSocketData(equipment.item);
            if (socketData == null) {
                socketData = SocketData.fromDefaults(ReforgeEquip.isWeapon(equipment.item) ? "weapon" : "armor");
            }
        }

        int punchedSockets = socketData == null ? 0 : Math.max(0, socketData.getCurrentSocketCount());
        List<Socket> sockets = socketData == null ? List.of() : socketData.getSockets();
        String brokenIconName = resolveBrokenSocketIconName();

        for (int i = 0; i < ESSENCE_PREVIEW_SLOTS; i++) {
            boolean visible = i < punchedSockets;
            boolean isPunched = i < punchedSockets;
            Socket socket = i < sockets.size() ? sockets.get(i) : null;
            boolean isBroken = socket != null && socket.isBroken();
            boolean isFilled = socket != null && !socket.isBroken() && !socket.isEmpty();
            String backgroundIcon = resolveSocketBackgroundIcon(isPunched, isBroken, brokenIconName);
            String overlayIcon = isPunched && isFilled && !isBroken ? resolveEssenceIconName(socket) : null;
            String colorKey = socketPreviewColorKey(socket, isPunched, isBroken, isFilled);

            safeEditVisible(ctxObj, essencePreviewWrapId(i), visible);
            safeEditStyle(ctxObj, essencePreviewWrapId(i),
                    essencePreviewWrapStyle(socketPreviewAccent(socket, isPunched, isBroken, isFilled), visible));
            for (String[] color : SOCKET_PREVIEW_COLORS) {
                String key = color[0];
                safeEditVisible(ctxObj, essencePreviewColorLayerId(i, key), visible && key.equals(colorKey));
                safeEditImage(ctxObj, essencePreviewBackgroundId(i, key), backgroundIcon);
                safeEditImage(ctxObj, essencePreviewOverlayId(i, key), overlayIcon != null ? overlayIcon : backgroundIcon);
                safeEditVisible(ctxObj, essencePreviewOverlayId(i, key), overlayIcon != null);
            }
        }
    }

    private static void updateLoreSocketPreviewVisuals(Object ctxObj, Entry equipment) {
        LoreSocketData loreData = equipment == null || equipment.item == null || equipment.item.isEmpty()
                ? null
                : LoreSocketManager.getLoreSocketData(equipment.item);
        int count = loreData == null ? 0 : Math.max(0, loreData.getSocketCount());
        boolean hasLoreSockets = count > 0;
        safeEditVisible(ctxObj, "loreSocketSectionInner", hasLoreSockets);
        safeEditVisible(ctxObj, "loreSocketPreviewTitle", hasLoreSockets);
        safeEditVisible(ctxObj, "loreSocketIconsRow", hasLoreSockets);

        String baseIcon = UITemplateUtils.resolveCustomUiAsset("GemSlotEmpty.png", "GemSlotEmpty.png");
        for (int i = 0; i < LORE_PREVIEW_SLOTS; i++) {
            LoreSocketData.LoreSocket loreSocket = loreData != null && i < count ? loreData.getSocket(i) : null;
            boolean visible = i < count;
            boolean filled = loreSocket != null && !loreSocket.isEmpty();
            String overlayIcon = filled ? resolveLoreGemOverlayIcon(loreSocket) : baseIcon;
            String colorKey = lorePreviewColorKey(loreSocket);
            safeEditVisible(ctxObj, lorePreviewWrapId(i), visible);
            for (String[] color : LORE_PREVIEW_COLORS) {
                String key = color[0];
                safeEditVisible(ctxObj, lorePreviewColorLayerId(i, key), visible && key.equals(colorKey));
                safeEditImage(ctxObj, lorePreviewOverlayId(i, key), overlayIcon);
                safeEditVisible(ctxObj, lorePreviewOverlayId(i, key), filled);
            }
        }
    }

    private static String buildRuntimeTemplate(Player player) {
        String template = loadTemplate(SOCKET_BENCH_TEMPLATE_PATH);
        if (template == null || template.isBlank()) {
            return null;
        }
        return LangLoader.replaceUiTokens(player, template);
    }

    private static Object createTemplateProcessor(
            Class<?> templateProcessorClass,
            Player player,
            BenchSnapshot snapshot,
            SelectionState fallbackState) throws Exception {
        Object processor = templateProcessorClass.getConstructor().newInstance();
        Method setVariable = templateProcessorClass.getMethod("setVariable", String.class, Supplier.class);

        setTemplateVariable(setVariable, processor, "equipmentCards",
                () -> buildEquipmentCardsHtml(player, snapshot.equipments, selectedEquipmentKey(player, snapshot, fallbackState)));
        setTemplateVariable(setVariable, processor, "puncherCards",
                () -> buildPuncherCardsHtml(player, snapshot.punchers, selectedPuncherKey(player, snapshot, fallbackState)));
        setTemplateVariable(setVariable, processor, "supportCards",
                () -> buildSupportCardsHtml(player, snapshot.supports, selectedSupportKey(player, snapshot, fallbackState)));
        setTemplateVariable(setVariable, processor, "successText",
                () -> escapeHtml(statPreview(player, snapshot, fallbackState).successText));
        setTemplateVariable(setVariable, processor, "breakText",
                () -> escapeHtml(statPreview(player, snapshot, fallbackState).breakText));
        setTemplateVariable(setVariable, processor, "socketsText",
                () -> escapeHtml(statPreview(player, snapshot, fallbackState).socketsText));
        setTemplateVariable(setVariable, processor, "socketIcons",
                () -> buildSocketIconsHtml(player, selectedEquipment(player, snapshot, fallbackState)));
        setTemplateVariable(setVariable, processor, "loreSocketSection",
                () -> buildLoreSocketSection(player, selectedEquipment(player, snapshot, fallbackState)));
        setTemplateVariable(setVariable, processor, "progressValue",
                () -> String.valueOf(progressValue(player, snapshot, fallbackState)));
        setTemplateVariable(setVariable, processor, "statusText",
                () -> escapeHtml(statusText(player, snapshot, fallbackState)));
        setTemplateVariable(setVariable, processor, "processDisabledAttr",
                () -> processDisabledAttr(player, snapshot, fallbackState));
        setTemplateVariable(setVariable, processor, "metadataText",
                () -> escapeHtml(selectedSocketSummary(player, snapshot, fallbackState)));
        return processor;
    }

    private static void setTemplateVariable(Method setVariable, Object processor, String key, Supplier<?> supplier) throws Exception {
        setVariable.invoke(processor, key, supplier);
    }

    private static SelectionState templateState(Player player, BenchSnapshot snapshot, SelectionState fallbackState) {
        return currentSelection(player, snapshot, fallbackState);
    }

    private static String selectedEquipmentKey(Player player, BenchSnapshot snapshot, SelectionState fallbackState) {
        SelectionState state = templateState(player, snapshot, fallbackState);
        return state != null ? state.equipmentKey : null;
    }

    private static String selectedPuncherKey(Player player, BenchSnapshot snapshot, SelectionState fallbackState) {
        SelectionState state = templateState(player, snapshot, fallbackState);
        return state != null ? state.puncherKey : null;
    }

    private static String selectedSupportKey(Player player, BenchSnapshot snapshot, SelectionState fallbackState) {
        SelectionState state = templateState(player, snapshot, fallbackState);
        return state != null ? state.supportKey : null;
    }

    private static Entry selectedEquipment(Player player, BenchSnapshot snapshot, SelectionState fallbackState) {
        return findByKey(snapshot.equipments, selectedEquipmentKey(player, snapshot, fallbackState));
    }

    private static Entry selectedPuncher(Player player, BenchSnapshot snapshot, SelectionState fallbackState) {
        return findByKey(snapshot.punchers, selectedPuncherKey(player, snapshot, fallbackState));
    }

    private static Entry selectedSupport(Player player, BenchSnapshot snapshot, SelectionState fallbackState) {
        return findByKey(snapshot.supports, selectedSupportKey(player, snapshot, fallbackState));
    }

    private static List<Entry> selectedSupports(Player player, BenchSnapshot snapshot, SelectionState fallbackState) {
        return findAllByKeys(snapshot != null ? snapshot.supports : null,
                selectedSupportKey(player, snapshot, fallbackState));
    }

    private static String toggleSupportKey(String currentKeys, String supportKey) {
        if (supportKey == null || supportKey.isBlank()) {
            return NONE_SUPPORT_KEY;
        }
        List<String> keys = new ArrayList<>(splitSupportKeys(currentKeys));
        if (keys.removeIf(existing -> existing.equals(supportKey))) {
            return joinSupportKeys(keys);
        }
        keys.add(supportKey);
        return joinSupportKeys(keys);
    }

    private static String normalizeSupportKeys(BenchSnapshot snapshot, String supportKeys) {
        List<String> keys = splitSupportKeys(supportKeys);
        if (keys.isEmpty()) {
            return NONE_SUPPORT_KEY;
        }
        List<String> valid = new ArrayList<>();
        for (Entry support : snapshot != null ? snapshot.supports : List.<Entry>of()) {
            String key = keyOf(support);
            if (containsSupportKey(keys, key)) {
                valid.add(key);
            }
        }
        return joinSupportKeys(valid);
    }

    private static List<String> splitSupportKeys(String supportKeys) {
        if (supportKeys == null || supportKeys.isBlank() || NONE_SUPPORT_KEY.equals(supportKeys)) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        for (String key : supportKeys.split(java.util.regex.Pattern.quote(SUPPORT_KEY_DELIMITER))) {
            if (key != null && !key.isBlank() && !NONE_SUPPORT_KEY.equals(key) && !keys.contains(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    private static String joinSupportKeys(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return NONE_SUPPORT_KEY;
        }
        return String.join(SUPPORT_KEY_DELIMITER, keys);
    }

    private static boolean containsSupportKey(List<String> keys, String key) {
        return key != null && keys != null && keys.contains(key);
    }

    private static List<Entry> findAllByKeys(List<Entry> entries, String keysValue) {
        List<String> keys = splitSupportKeys(keysValue);
        if (entries == null || entries.isEmpty() || keys.isEmpty()) {
            return List.of();
        }
        List<Entry> matches = new ArrayList<>();
        for (Entry entry : entries) {
            if (containsSupportKey(keys, keyOf(entry))) {
                matches.add(entry);
            }
        }
        return matches;
    }

    private static boolean hasSupportMaterial(List<Entry> supports, SupportMaterial material) {
        if (supports == null || supports.isEmpty() || material == null) {
            return false;
        }
        for (Entry support : supports) {
            if (support != null && SocketManager.resolveSupportMaterial(support.itemId) == material) {
                return true;
            }
        }
        return false;
    }

    private static boolean supportOverridesMax(List<Entry> supports) {
        return hasSupportMaterial(supports, SupportMaterial.SOCKET_EXPANDER)
                || hasSupportMaterial(supports, SupportMaterial.SOCKET_DIFFUSER)
                || hasSupportMaterial(supports, SupportMaterial.GHASTLY_ESSENCE);
    }

    private static int progressValue(Player player, BenchSnapshot snapshot, SelectionState fallbackState) {
        SelectionState state = templateState(player, snapshot, fallbackState);
        int progressValue = state != null ? Math.max(0, Math.min(100, state.progressValue)) : 0;
        return state != null && state.processing ? progressValue : 0;
    }

    private static String statusText(Player player, BenchSnapshot snapshot, SelectionState fallbackState) {
        SelectionState state = templateState(player, snapshot, fallbackState);
        String statusText = state != null && state.statusText != null
                ? state.statusText
                : LangLoader.getUITranslation(player, "ui.socket_bench.status_idle");
        Entry equipment = selectedEquipment(player, snapshot, fallbackState);
        List<Entry> supports = selectedSupports(player, snapshot, fallbackState);
        boolean supportOverridesMax = supportOverridesMax(supports);
        String idleText = LangLoader.getUITranslation(player, "ui.socket_bench.status_idle");
        if ((idleText.equals(statusText) || "Idle".equals(statusText)) && isSocketsMaxed(equipment) && !supportOverridesMax) {
            return LangLoader.getUITranslation(player, "ui.socket_bench.status_max_sockets");
        }
        return statusText;
    }

    private static String processDisabledAttr(Player player, BenchSnapshot snapshot, SelectionState fallbackState) {
        return processDisabled(player, snapshot, fallbackState) ? "disabled data-hyui-disabled=\"true\"" : "";
    }

    private static boolean processDisabled(Player player, BenchSnapshot snapshot, SelectionState fallbackState) {
        SelectionState state = templateState(player, snapshot, fallbackState);
        Entry equipment = selectedEquipment(player, snapshot, fallbackState);
        Entry puncher = selectedPuncher(player, snapshot, fallbackState);
        List<Entry> supports = selectedSupports(player, snapshot, fallbackState);
        boolean supportOverridesMax = supportOverridesMax(supports);
        return (state != null && state.processing) || puncher == null || (isSocketsMaxed(equipment) && !supportOverridesMax);
    }

    private static String selectedSocketSummary(Player player, BenchSnapshot snapshot, SelectionState fallbackState) {
        Entry equipment = selectedEquipment(player, snapshot, fallbackState);
        return equipment == null
                ? LangLoader.getUITranslation(player, "ui.socket_bench.metadata_prompt")
                : buildSelectedSocketSummary(player, equipment);
    }

    private static StatPreview statPreview(Player player, BenchSnapshot snapshot, SelectionState fallbackState) {
        return calculatePreview(snapshot,
                selectedEquipmentKey(player, snapshot, fallbackState),
                selectedSupportKey(player, snapshot, fallbackState));
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
        String puncherCards = buildPuncherCardsHtml(player, snapshot.punchers, selectedPuncherKey);
        String supportCards = buildSupportCardsHtml(player, snapshot.supports, selectedSupportKey);

        Entry selectedEquipment = findByKey(snapshot.equipments, selectedEquipmentKey);
        Entry selectedPuncher = findByKey(snapshot.punchers, selectedPuncherKey);
        List<Entry> selectedSupports = findAllByKeys(snapshot.supports, selectedSupportKey);
        boolean supportOverridesMax = supportOverridesMax(selectedSupports);
        boolean socketsMaxed = isSocketsMaxed(selectedEquipment);
        String idleText = LangLoader.getUITranslation(player, "ui.socket_bench.status_idle");
        if (!isProcessing && (idleText.equals(statusText) || "Idle".equals(statusText)) && socketsMaxed && !supportOverridesMax) {
            statusText = LangLoader.getUITranslation(player, "ui.socket_bench.status_max_sockets");
        }
        String processDisabledAttr = (isProcessing || selectedPuncher == null || (socketsMaxed && !supportOverridesMax))
                ? "disabled data-hyui-disabled=\"true\""
                : "";
        String socketIcons = buildSocketIconsHtml(player, selectedEquipment);
        String loreSocketSection = buildLoreSocketSection(player, selectedEquipment);
        StatPreview defaultStats = calculatePreview(snapshot, selectedEquipmentKey, selectedSupportKey);

        String template = loadTemplate(SOCKET_BENCH_TEMPLATE_PATH);
	        if (template != null && !template.isBlank()) {
            String html = template
                    .replace("{{equipmentCards}}", buildEquipmentCardsHtml(player, snapshot.equipments, selectedEquipmentKey))
                    .replace("{{puncherCards}}", puncherCards)
                    .replace("{{supportCards}}", supportCards)
                    .replace("{{successText}}", escapeHtml(defaultStats.successText))
                    .replace("{{breakText}}", escapeHtml(defaultStats.breakText))
                    .replace("{{socketsText}}", escapeHtml(defaultStats.socketsText))
                    .replace("{{socketIcons}}", socketIcons)
                    .replace("{{loreSocketSection}}", loreSocketSection)
                    .replace("{{progressValue}}", String.valueOf(progressValue))
                    .replace("{{statusText}}", escapeHtml(statusText))
                    .replace("{{processDisabledAttr}}", processDisabledAttr)
                    .replace("{{metadataText}}", "");
            return LangLoader.replaceUiTokens(player, html);
        }

        return LangLoader.replaceUiTokens(player,
                "<div class=\"page-overlay\">"
                + "<div style=\"anchor-width: 1450; anchor-height: 760; layout-mode:left; spacing:24;\">"
                + "<div class=\"decorated-container\" data-hyui-title=\"Equipment\" style=\"anchor-width: 500; anchor-height: 760;\">"
                + "<div class=\"container-contents\" style=\"anchor-full: 14; layout-mode:top;\">"
                + "<h2 style=\"text-align:center;\">Equipment</h2>"
                + "<p style=\"text-align:center;\">Select equipment.</p>"
                + "<div style=\"anchor-width:460; anchor-height:640; layout-mode:topscrolling; padding:6; background-color:#101823;\">"
                + buildEquipmentCardsHtml(player, snapshot.equipments, selectedEquipmentKey)
                + "</div></div></div>"
                + "<div class=\"decorated-container\" data-hyui-title=\"Socket Punch Bench\" style=\"anchor-width: 920; anchor-height: 760;\">"
                + "<div class=\"container-contents\" style=\"anchor-full: 14; overflow-y:auto;\">"
                + "<h2 style=\"text-align:center;\">Socket Punch Bench</h2>"
                + "<p style=\"text-align:center;\">Select equipment and materials from inventory.</p>"
                + "<hr/>"
                + "<p><b>Equipment</b></p>"
                + "<p style=\"anchor-height:24; text-align:center; background-color:#1a1a2b; padding:4;\">"
                + escapeHtml(selectedEquipment != null ? selectedEquipment.displayName : LangLoader.getUITranslation(player, "ui.socket_bench.option_select_equipment"))
                + "</p>"
                + "<p><b>Main Material (Socket Puncher)</b></p>"
                + "<div style=\"anchor-width: 890; anchor-height: 150; layout-mode: topscrolling; background-color:#101823; padding:6;\">"
                + "<div style=\"anchor-width: 878; layout-mode:leftcenterwrap; spacing:8;\">"
                + puncherCards
                + "</div></div>"
                + "<p><b>Support Material (Optional)</b></p>"
                + "<div style=\"anchor-width: 890; anchor-height: 150; layout-mode: topscrolling; background-color:#101823; padding:6;\">"
                + "<div style=\"anchor-width: 878; layout-mode:leftcenterwrap; spacing:8;\">"
                + supportCards
                + "</div></div>"
                + "<hr/>"
                + "<p><b>Socket Preview</b></p>"
                + "<div id=\"socketIconsRow\">" + socketIcons + "</div>"
                + (loreSocketSection == null || loreSocketSection.isBlank() ? "" : loreSocketSection)
                + "<p><b>Progress</b></p>"
                + "<div style=\"anchor-width:890; layout-mode: center; spacing: 20;\">"
                + "<p id=\"successChanceLabel\" style=\"anchor-width:260; text-align:center;\">Success: " + defaultStats.successText + "</p>"
                + "<p id=\"breakChanceLabel\" style=\"anchor-width:260; text-align:center;\">Item Break: " + defaultStats.breakText + "</p>"
                + "<p id=\"socketCountLabel\" style=\"anchor-width:260; text-align:center;\">Sockets: " + defaultStats.socketsText + "</p>"
                + "</div>"
                + "<progress id=\"socketProgress\" max=\"100\" value=\"" + progressValue + "\" style=\"width:100%;\"></progress>"
                + "<p id=\"statusLabel\">" + escapeHtml(statusText) + "</p>"
                + "<hr/>"
                + "<p style=\"font-size:11;\">Failure consumes Socket Puncher. Break can damage socket state.</p>"
                + "<button id=\"processButton\" style=\"width:100%;height:40px;\"" + processDisabledAttr + ">Process Materials</button>"
                + "</div>"
                + "</div></div>");
    }

	    private static String loadTemplate(String path) {
	        if (path == null || path.isBlank()) {
	            return null;
	        }
	        return UITemplateUtils.loadTemplate(SocketBenchUI.class, path, null, "SocketBenchUI");
	    }

    private static String buildEquipmentCardsHtml(Player player, List<Entry> equipments, String selectedKey) {
        if (equipments == null || equipments.isEmpty()) {
            return "<p style=\"text-align:center;\">" + escapeHtml(LangLoader.getUITranslation(player, "ui.socket_bench.option_no_equipment")) + "</p>";
        }
        StringBuilder armor = new StringBuilder();
        StringBuilder weapons = new StringBuilder();
        for (int i = 0; i < equipments.size(); i++) {
            Entry entry = equipments.get(i);
            if (entry == null || entry.item == null || entry.item.isEmpty()) {
                continue;
            }
            StringBuilder target = ReforgeEquip.isArmor(entry.item) ? armor : weapons;
            appendEquipmentCardHtml(target, entry, i, keyOf(entry).equals(selectedKey));
        }

        StringBuilder sb = new StringBuilder();
        if (armor.length() > 0) {
            appendEquipmentSectionHeaderHtml(player, sb, "ui.socket_bench.tag_armor");
            sb.append(armor);
        }
        if (weapons.length() > 0) {
            appendEquipmentSectionHeaderHtml(player, sb, "ui.socket_bench.tag_weapon");
            sb.append(weapons);
        }
        if (sb.length() == 0) {
            return "<p style=\"text-align:center;\">" + escapeHtml(LangLoader.getUITranslation(player, "ui.socket_bench.option_no_equipment")) + "</p>";
        }
        return sb.toString();
    }

    private static void appendEquipmentSectionHeaderHtml(Player player, StringBuilder sb, String titleKey) {
        sb.append("<p style=\"anchor-height:20; font-weight:bold;\">")
                .append(escapeHtml(LangLoader.getUITranslation(player, titleKey)))
                .append("</p>")
                .append("<img src=\"divider.png\" style=\"anchor-width:430; anchor-height:3;\">");
    }

    private static void appendEquipmentCardHtml(StringBuilder sb, Entry equipment, int equipmentIndex, boolean selected) {
        String background = "#151526";
        sb.append("<button id=\"")
                .append(equipmentCardButtonId(equipmentIndex))
                .append("\" class=\"raw-button\" style=\"anchor-width:424; anchor-height:78; padding:0; border:0; background-color:#00000000;\">")
                .append("<div id=\"")
                .append(equipmentCardBgId(equipmentIndex))
                .append("\" style=\"")
                .append(equipmentCardBgStyle(background))
                .append("\">")
                .append("<div id=\"")
                .append(equipmentCardSelectedLayerId(equipmentIndex))
                .append("\" style=\"anchor-width:424; anchor-height:78; layout-mode:top; background-color:#2B3F6D; visibility:")
                .append(selected ? "shown" : "hidden")
                .append(";\"><img id=\"")
                .append(selectedLayerImageId(equipmentCardSelectedLayerId(equipmentIndex)))
                .append("\" src=\"")
                .append(selected ? "socket_panel_bg.png" : "")
                .append("\" width=\"424\" height=\"78\"/></div>")
                .append("<div style=\"anchor-width:424; anchor-height:78; layout-mode:left; padding:6;\">")
                .append("<div style=\"anchor-width:66; anchor-height:66; background-image:url('slot_bg.png'); background-size:100% 100%; background-repeat:no-repeat; layout-mode:top; padding:3;\">")
                .append("<div style=\"anchor-width:12;\"></div>")
                .append("<span class=\"item-icon\" data-hyui-item-id=\"")
                .append(escapeHtml(equipment.itemId))
                .append("\" style=\"anchor-width:60; anchor-height:60;\"></span>")
                .append("</div>")
                .append("<div style=\"anchor-width:14;\"></div>")
                .append("<div style=\"anchor-width:334; anchor-height:66; layout-mode:top; spacing:1;\">")
                .append("<div style=\"anchor-width:334; anchor-height:20; layout-mode:left;\">")
                .append("<p style=\"anchor-width:234; anchor-height:20; font-weight:bold; text-align:left; white-space:nowrap;\">")
                .append(escapeHtml(equipment.displayName))
                .append("</p>")
                .append("<p id=\"")
                .append(equipmentCardMarkerId(equipmentIndex))
                .append("\" style=\"anchor-width:90; anchor-height:20; text-align:right; font-size:11; color:#FFD24D; visibility:")
                .append(selected ? "shown" : "hidden")
                .append(";\">SELECTED</p>")
                .append("</div>")
                .append("<div style=\"anchor-width:334; anchor-height:36; layout-mode:left; spacing:0;\">");
        appendCompactSocketPreviewHtml(sb, equipment, equipmentIndex);
        if (hasLoreSockets(equipment)) {
            sb.append("<div style=\"anchor-width:16;\"></div>");
        }
        appendCompactLoreSocketPreviewHtml(sb, equipment, equipmentIndex);
        sb.append("</div></div></div></div></button><p style=\"anchor-height:4;\"></p>");
    }

    private static boolean hasLoreSockets(Entry equipment) {
        if (equipment == null || equipment.item == null || equipment.item.isEmpty()) {
            return false;
        }
        LoreSocketData data = LoreSocketManager.getLoreSocketData(equipment.item);
        return data != null && data.getSocketCount() > 0;
    }

    private static void appendCompactSocketPreviewHtml(StringBuilder sb, Entry equipment, int equipmentIndex) {
        SocketData socketData = SocketManager.getSocketData(equipment.item);
        if (socketData == null) {
            socketData = SocketData.fromDefaults(ReforgeEquip.isWeapon(equipment.item) ? "weapon" : "armor");
        }
        int punchedSockets = Math.max(0, socketData.getCurrentSocketCount());
        List<Socket> sockets = socketData.getSockets();
        String brokenIconName = resolveBrokenSocketIconName();
        for (int i = 0; i < 5; i++) {
            boolean punched = i < punchedSockets;
            Socket socket = i < sockets.size() ? sockets.get(i) : null;
            boolean broken = socket != null && socket.isBroken();
            boolean filled = socket != null && !socket.isBroken() && !socket.isEmpty();
            String backgroundIcon = resolveSocketBackgroundIcon(punched, broken, brokenIconName);
            String overlayIcon = punched && filled && !broken ? resolveEssenceIconName(socket) : null;
            String accent = socketPreviewAccent(socket, punched, broken, filled);
            boolean visible = i < punchedSockets;
            sb.append("<div id=\"")
                    .append(equipmentCompactSocketWrapId(equipmentIndex, i))
                    .append("\" style=\"")
                    .append(compactSocketWrapStyle(accent, visible))
                    .append("\">")
                    .append("<div style=\"anchor-width:32; anchor-height:32; layout-mode:full;\">")
                    .append("<img id=\"")
                    .append(equipmentCompactSocketBackgroundId(equipmentIndex, i))
                    .append("\" src=\"")
                    .append(backgroundIcon)
                    .append("\" width=\"32\" height=\"32\"/>")
                    .append("<div style=\"layout-mode:top;\"><div style=\"flex-weight:1;\"></div>")
                    .append("<div style=\"layout-mode:left;\"><div style=\"flex-weight:1;\"></div>")
                    .append("<img id=\"")
                    .append(equipmentCompactSocketOverlayId(equipmentIndex, i))
                    .append("\" src=\"")
                    .append(overlayIcon != null ? overlayIcon : backgroundIcon)
                    .append("\" width=\"22\" height=\"22\" style=\"visibility:")
                    .append(overlayIcon != null ? "shown" : "hidden")
                    .append(";\"/>")
                    .append("<div style=\"flex-weight:1;\"></div></div>")
                    .append("<div style=\"flex-weight:1;\"></div></div>")
                    .append("</div></div>");
        }
    }

    private static void appendCompactLoreSocketPreviewHtml(StringBuilder sb, Entry equipment, int equipmentIndex) {
        LoreSocketData data = LoreSocketManager.getLoreSocketData(equipment.item);
        int count = data == null ? 0 : Math.max(0, data.getSocketCount());
        String baseIcon = UITemplateUtils.resolveCustomUiAsset("GemSlotEmpty.png", "GemSlotEmpty.png");
        for (int i = 0; i < 3; i++) {
            LoreSocketData.LoreSocket socket = i < count ? data.getSocket(i) : null;
            boolean visible = i < count;
            boolean filled = socket != null && !socket.isEmpty();
            String colorHex = resolveLoreColorHex(socket);
            String overlayIcon = filled ? resolveLoreGemOverlayIcon(socket) : null;
            sb.append("<div id=\"")
                    .append(equipmentCompactLoreSocketWrapId(equipmentIndex, i))
                    .append("\" style=\"")
                    .append(compactLoreSocketWrapStyle(colorHex, visible))
                    .append("\">")
                    .append("<div style=\"anchor-width:32; anchor-height:32; layout-mode:full;\">")
                    .append("<img id=\"")
                    .append(equipmentCompactLoreSocketBackgroundId(equipmentIndex, i))
                    .append("\" src=\"")
                    .append(baseIcon)
                    .append("\" width=\"32\" height=\"32\"/>")
                    .append("<div style=\"layout-mode:top;\"><div style=\"flex-weight:1;\"></div>")
                    .append("<div style=\"layout-mode:left;\"><div style=\"flex-weight:1;\"></div>")
                    .append("<img id=\"")
                    .append(equipmentCompactLoreSocketOverlayId(equipmentIndex, i))
                    .append("\" src=\"")
                    .append(overlayIcon != null ? overlayIcon : baseIcon)
                    .append("\" width=\"28\" height=\"28\" style=\"visibility:")
                    .append(overlayIcon != null ? "shown" : "hidden")
                    .append(";\"/>")
                    .append("<div style=\"flex-weight:1;\"></div></div>")
                    .append("<div style=\"flex-weight:1;\"></div></div>")
                    .append("</div></div>");
        }
    }

    private static String buildPuncherCardsHtml(Player player, List<Entry> punchers, String selectedKey) {
        if (punchers == null || punchers.isEmpty()) {
            return "<p style=\"text-align:center; anchor-width:860;\">"
                    + escapeHtml(LangLoader.getUITranslation(player, "ui.socket_bench.option_no_puncher"))
                    + "</p>";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < punchers.size(); i++) {
            Entry entry = punchers.get(i);
            if (entry == null || entry.item == null || entry.item.isEmpty()) {
                continue;
            }
            appendMaterialCardHtml(sb, entry, puncherCardButtonId(i), keyOf(entry).equals(selectedKey), false);
        }
        return sb.toString();
    }

    private static String buildSupportCardsHtml(Player player, List<Entry> supports, String selectedKey) {
        StringBuilder sb = new StringBuilder();
        List<String> selectedKeys = splitSupportKeys(selectedKey);
        appendSupportNoneCardHtml(player, sb, selectedKeys.isEmpty());
        if (supports != null) {
            for (int i = 0; i < supports.size(); i++) {
                Entry entry = supports.get(i);
                if (entry == null || entry.item == null || entry.item.isEmpty()) {
                    continue;
                }
                appendMaterialCardHtml(sb, entry, supportCardButtonId(i), containsSupportKey(selectedKeys, keyOf(entry)), true);
            }
        }
        return sb.toString();
    }

    private static void appendSupportNoneCardHtml(Player player, StringBuilder sb, boolean selected) {
        String background = "#151526";
        String border = "#00000000";
        sb.append("<button id=\"")
                .append(supportNoneCardButtonId())
                .append("\" class=\"raw-button\" style=\"anchor-width:404; anchor-height:74; padding:0; border:0; background-color:#00000000;\">")
                .append("<div id=\"")
                .append(supportNoneCardBgId())
                .append("\" style=\"")
                .append(materialCardBgStyle(background, 74))
                .append("\">")
                .append("<div id=\"")
                .append(supportNoneCardSelectedLayerId())
                .append("\" style=\"anchor-width:404; anchor-height:74; layout-mode:top; background-color:#2B3F6D; visibility:")
                .append(selected ? "shown" : "hidden")
                .append(";\"><img id=\"")
                .append(selectedLayerImageId(supportNoneCardSelectedLayerId()))
                .append("\" src=\"")
                .append(selected ? "socket_panel_bg.png" : "")
                .append("\" width=\"404\" height=\"74\"/></div>")
                .append("<div style=\"anchor-width:404; anchor-height:74; layout-mode:left; padding:6;\">")
                .append("<div style=\"anchor-width:60; anchor-height:60; background-image:url('slot_bg.png'); background-size:100% 100%; background-repeat:no-repeat; padding:2; background-color:")
                .append(border)
                .append(";\"></div>")
                .append("<div style=\"anchor-width:10;\"></div>")
                .append("<div style=\"anchor-width:314; anchor-height:60; layout-mode:top; spacing:1;\">")
                .append("<div style=\"anchor-width:314; anchor-height:20; layout-mode:left;\">")
                .append("<p style=\"anchor-width:214; anchor-height:20; font-weight:bold; white-space:nowrap;\">")
                .append(escapeHtml(LangLoader.getUITranslation(player, "ui.socket_bench.option_none")))
                .append("</p>")
                .append("<p id=\"")
                .append(supportNoneCardMarkerId())
                .append("\" style=\"anchor-width:90; anchor-height:20; text-align:right; font-size:11; color:#FFD24D; visibility:")
                .append(selected ? "shown" : "hidden")
                .append(";\">SELECTED</p>")
                .append("</div>")
                .append("<p style=\"font-size:11; white-space:nowrap;\">No support material.</p>")
                .append("<p style=\"font-size:12; white-space:nowrap;\">Held: -</p>")
                .append("</div></div></div></button>");
    }

    private static void appendMaterialCardHtml(StringBuilder sb, Entry entry, String buttonId, boolean selected, boolean supportCard) {
        String background = "#151526";
        String border = "#00000000";
        String description = supportCard ? supportDescription(entry) : "Consumed on use.";
        sb.append("<button id=\"")
                .append(buttonId)
                .append("\" class=\"raw-button\" style=\"anchor-width:404; anchor-height:82; padding:0; border:0; background-color:#00000000;\">")
                .append("<div id=\"")
                .append(materialCardBgId(buttonId))
                .append("\" style=\"")
                .append(materialCardBgStyle(background, 82))
                .append("\">")
                .append("<div id=\"")
                .append(materialCardSelectedLayerId(buttonId))
                .append("\" style=\"anchor-width:404; anchor-height:82; layout-mode:top; background-color:#2B3F6D; visibility:")
                .append(selected ? "shown" : "hidden")
                .append(";\"><img id=\"")
                .append(selectedLayerImageId(materialCardSelectedLayerId(buttonId)))
                .append("\" src=\"")
                .append(selected ? "socket_panel_bg.png" : "")
                .append("\" width=\"404\" height=\"82\"/></div>")
                .append("<div style=\"anchor-width:404; anchor-height:82; layout-mode:left; padding:6;\">")
                .append("<div style=\"anchor-width:60; anchor-height:60; background-image:url('slot_bg.png'); background-size:100% 100%; background-repeat:no-repeat; padding:2; background-color:")
                .append(border)
                .append(";\">")
                .append("<span class=\"item-icon\" data-hyui-item-id=\"")
                .append(escapeHtml(entry.itemId))
                .append("\" style=\"anchor-width:56; anchor-height:56;\"></span>")
                .append("</div>")
                .append("<div style=\"anchor-width:10;\"></div>")
                .append("<div style=\"anchor-width:314; anchor-height:68; layout-mode:top; spacing:1;\">")
                .append("<div style=\"anchor-width:314; anchor-height:20; layout-mode:left;\">")
                .append("<p style=\"anchor-width:214; anchor-height:20; font-weight:bold; white-space:nowrap;\">")
                .append(escapeHtml(entry.displayName))
                .append("</p>")
                .append("<p id=\"")
                .append(materialCardMarkerId(buttonId))
                .append("\" style=\"anchor-width:90; anchor-height:20; text-align:right; font-size:11; color:#FFD24D; visibility:")
                .append(selected ? "shown" : "hidden")
                .append(";\">SELECTED</p>")
                .append("</div>")
                .append("<p style=\"font-size:11; white-space:wrap;\">")
                .append(escapeHtml(description))
                .append("</p>")
                .append("<p id=\"")
                .append(materialCardHeldLabelId(buttonId))
                .append("\" style=\"font-size:12; white-space:nowrap;\">Held: ")
                .append(entry.quantity)
                .append("</p>")
                .append("</div></div></div></button>");
    }

    private static String supportDescription(Entry entry) {
        if (entry == null || entry.itemId == null) {
            return "Support material.";
        }
        return switch (SocketManager.resolveSupportMaterial(entry.itemId)) {
            case SOCKET_STABILIZER -> "Halves break chance while punching.";
            case SOCKET_REINFORCER -> "Adds 20% success chance.";
            case SOCKET_GUARANTOR -> "Guarantees the first socket punch.";
            case SOCKET_EXPANDER -> "50% chance to gain 1 max socket, 50% chance to lose 1 max socket.";
            case SOCKET_DIFFUSER -> "Reduces max sockets by 1.";
            case GHASTLY_ESSENCE -> "Attempts to punch a lore socket instead.";
            default -> "Support material.";
        };
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
	        return entry.selectionKey;
	    }

    private static String equipmentCardButtonId(int equipmentIndex) {
        return "equipmentCard_" + equipmentIndex;
    }

    private static String puncherCardButtonId(int index) {
        return "puncherCard_" + index;
    }

    private static String supportCardButtonId(int index) {
        return "supportCard_" + index;
    }

    private static String supportNoneCardButtonId() {
        return "supportCard_none";
    }

    private static String equipmentCardBgId(int equipmentIndex) {
        return "equipmentCardBg_" + equipmentIndex;
    }

    private static String equipmentCardSelectedLayerId(int equipmentIndex) {
        return "equipmentCardSelected_" + equipmentIndex;
    }

    private static String materialCardBgId(String buttonId) {
        return buttonId + "_bg";
    }

    private static String materialCardSelectedLayerId(String buttonId) {
        return buttonId + "_selected";
    }

    private static String selectedLayerImageId(String layerId) {
        return layerId + "_image";
    }

    private static String materialCardHeldLabelId(String buttonId) {
        return buttonId + "_held";
    }

    private static String supportNoneCardBgId() {
        return supportNoneCardButtonId() + "_bg";
    }

    private static String supportNoneCardSelectedLayerId() {
        return supportNoneCardButtonId() + "_selected";
    }

    private static String equipmentCardMarkerId(int equipmentIndex) {
        return "equipmentCardMarker_" + equipmentIndex;
    }

    private static String equipmentCompactSocketWrapId(int equipmentIndex, int socketIndex) {
        return "equipmentCompactSocket_" + equipmentIndex + "_" + socketIndex;
    }

    private static String equipmentCompactSocketBackgroundId(int equipmentIndex, int socketIndex) {
        return "equipmentCompactSocketBg_" + equipmentIndex + "_" + socketIndex;
    }

    private static String equipmentCompactSocketOverlayId(int equipmentIndex, int socketIndex) {
        return "equipmentCompactSocketOverlay_" + equipmentIndex + "_" + socketIndex;
    }

    private static String equipmentCompactLoreSocketWrapId(int equipmentIndex, int socketIndex) {
        return "equipmentCompactLoreSocket_" + equipmentIndex + "_" + socketIndex;
    }

    private static String equipmentCompactLoreSocketBackgroundId(int equipmentIndex, int socketIndex) {
        return "equipmentCompactLoreSocketBg_" + equipmentIndex + "_" + socketIndex;
    }

    private static String equipmentCompactLoreSocketOverlayId(int equipmentIndex, int socketIndex) {
        return "equipmentCompactLoreSocketOverlay_" + equipmentIndex + "_" + socketIndex;
    }

    private static String materialCardMarkerId(String buttonId) {
        return buttonId + "_marker";
    }

    private static String supportNoneCardMarkerId() {
        return supportNoneCardButtonId() + "_marker";
    }

    private static String essencePreviewWrapId(int index) {
        return "socketPreviewWrap_" + index;
    }

    private static String essencePreviewTileId(int index) {
        return "socketPreviewTile_" + index;
    }

    private static String essencePreviewColorLayerId(int index, String colorKey) {
        return "socketPreviewColor_" + index + "_" + colorKey;
    }

    private static String essencePreviewBackgroundId(int index, String colorKey) {
        return "socketPreviewBackground_" + index + "_" + colorKey;
    }

    private static String essencePreviewOverlayId(int index, String colorKey) {
        return "socketPreviewOverlay_" + index + "_" + colorKey;
    }

    private static String lorePreviewWrapId(int index) {
        return "lorePreviewWrap_" + index;
    }

    private static String lorePreviewColorLayerId(int index, String colorKey) {
        return "lorePreviewColor_" + index + "_" + colorKey;
    }

    private static String lorePreviewOverlayId(int index, String colorKey) {
        return "lorePreviewOverlay_" + index + "_" + colorKey;
    }

    private static String equipmentCardBgStyle(String background) {
        return "anchor-width:424; anchor-height:78; layout-mode:full; background-color:" + background + ";";
    }

    private static String materialCardBgStyle(String background, int height) {
        return "anchor-width:404; anchor-height:" + height + "; layout-mode:full; background-color:" + background + ";";
    }

    private static String compactSocketWrapStyle(String color, boolean visible) {
        return "anchor-width:39; anchor-height:34; padding:1; padding-right:5; background-color:"
                + color + "; visibility:" + (visible ? "shown" : "hidden") + ";";
    }

    private static String compactLoreSocketWrapStyle(String color, boolean visible) {
        return "anchor-width:39; anchor-height:34; padding:1; padding-right:5; background-color:"
                + color + "; visibility:" + (visible ? "shown" : "hidden") + ";";
    }

    private static String essencePreviewWrapStyle(String color, boolean visible) {
        if (!visible) {
            return "anchor-width:86; anchor-height:86; layout-mode:Top; padding:2; visibility:hidden;";
        }
        return "anchor-width:86; anchor-height:86; layout-mode:Top; padding:2; visibility:shown;";
    }

    private static String essencePreviewColorLayerStyle(String color, boolean visible) {
        return "anchor-width:86; anchor-height:86; background-color:" + color
                + "; layout-mode:Top; padding:2; visibility:" + (visible ? "shown" : "hidden") + ";";
    }

    private static String essencePreviewTileStyle() {
        return "anchor-width:82; anchor-height:82; layout-mode:full;";
    }

    private static String lorePreviewWrapStyle(String color) {
        return "anchor-width:78; anchor-height:78; layout-mode:Top; padding:4; border-radius:6;";
    }

    private static String lorePreviewColorLayerStyle(String color, boolean visible) {
        return "anchor-width:78; anchor-height:78; background-color:" + color
                + "; layout-mode:Top; padding:4; border-radius:6; visibility:" + (visible ? "shown" : "hidden") + ";";
    }

    private static String buildSelectedSocketSummary(Player player, Entry entry) {
        if (entry == null || entry.item == null || entry.item.isEmpty()) {
            return LangLoader.getUITranslation(player, "ui.socket_bench.metadata_prompt");
        }
        StringBuilder sb = new StringBuilder();
        sb.append(entry.displayName).append("\n");

        SocketData socketData = SocketManager.getSocketData(entry.item);
        if (socketData == null) {
            socketData = SocketData.fromDefaults(ReforgeEquip.isWeapon(entry.item) ? "weapon" : "armor");
        }
        int filled = 0;
        int broken = 0;
        int empty = 0;
        List<Socket> sockets = socketData.getSockets();
        for (int i = 0; i < socketData.getCurrentSocketCount(); i++) {
            Socket socket = i < sockets.size() ? sockets.get(i) : null;
            if (socket == null || socket.isEmpty()) {
                empty++;
            } else if (socket.isBroken()) {
                broken++;
            } else {
                filled++;
            }
        }
        sb.append("Essence sockets: ")
                .append(socketData.getCurrentSocketCount())
                .append(" / ")
                .append(socketData.getMaxSockets())
                .append(" | Filled: ")
                .append(filled)
                .append(" | Empty: ")
                .append(empty)
                .append(" | Broken: ")
                .append(broken)
                .append("\n");
        for (int i = 0; i < socketData.getCurrentSocketCount(); i++) {
            Socket socket = i < sockets.size() ? sockets.get(i) : null;
            sb.append("Socket ").append(i + 1).append(": ");
            if (socket == null || socket.isEmpty()) {
                sb.append("Empty");
            } else if (socket.isBroken()) {
                sb.append("Broken");
            } else {
                sb.append(socket.getEssenceId() != null ? socket.getEssenceId() : "Filled");
            }
            sb.append("\n");
        }

        LoreSocketData loreData = LoreSocketManager.getLoreSocketData(entry.item);
        if (loreData != null && loreData.getSocketCount() > 0) {
            sb.append("Lore sockets: ").append(loreData.getSocketCount()).append("\n");
            for (int i = 0; i < loreData.getSocketCount(); i++) {
                LoreSocketData.LoreSocket socket = loreData.getSocket(i);
                sb.append("Lore ").append(i + 1).append(": ");
                if (socket == null) {
                    sb.append("Empty");
                } else if (socket.isLocked()) {
                    sb.append("Locked");
                } else if (socket.isEmpty()) {
                    sb.append("Empty ").append(socket.getColor() != null ? socket.getColor() : "");
                } else {
                    sb.append(socket.getGemItemId() != null ? socket.getGemItemId() : "Filled");
                }
                sb.append("\n");
            }
        }
        return sb.toString().trim();
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
        SocketData socketData = null;
        if (equipment != null && equipment.item != null && !equipment.item.isEmpty()) {
            socketData = SocketManager.getSocketData(equipment.item);
            if (socketData == null) {
                socketData = SocketData.fromDefaults(ReforgeEquip.isWeapon(equipment.item) ? "weapon" : "armor");
            }
        }

        int punchedSockets = socketData == null ? 0 : Math.max(0, socketData.getCurrentSocketCount());
        List<Socket> sockets = socketData == null ? List.of() : socketData.getSockets();
        String brokenIconName = resolveBrokenSocketIconName();

        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"layout-mode: Left; spacing: 14;\"><div style=\"flex-weight:1;\"></div>");
        for (int i = 0; i < ESSENCE_PREVIEW_SLOTS; i++) {
            boolean isPunched = i < punchedSockets;
            Socket socket = i < sockets.size() ? sockets.get(i) : null;
            boolean isBroken = socket != null && socket.isBroken();
            boolean isFilled = socket != null && !socket.isBroken() && !socket.isEmpty();

            String backgroundIcon = resolveSocketBackgroundIcon(isPunched, isBroken, brokenIconName);
            String overlayIcon = isPunched && isFilled && !isBroken ? resolveEssenceIconName(socket) : null;
            String colorKey = socketPreviewColorKey(socket, isPunched, isBroken, isFilled);
            boolean visible = i < punchedSockets;

            int previewSize = 82;
            sb.append("<div id=\"").append(essencePreviewWrapId(i)).append("\" style=\"")
                    .append(essencePreviewWrapStyle(socketPreviewAccent(socket, isPunched, isBroken, isFilled), visible))
                    .append("\">");
            for (String[] color : SOCKET_PREVIEW_COLORS) {
                String key = color[0];
                sb.append("<div id=\"").append(essencePreviewColorLayerId(i, key)).append("\" style=\"")
                        .append(essencePreviewColorLayerStyle(color[1], key.equals(colorKey)))
                        .append("\">")
                        .append("<div style=\"flex-weight:1;\"></div>")
                        .append("<div style=\"layout-mode:Left;\"><div style=\"flex-weight:1;\"></div>")
                        .append("<div id=\"").append(essencePreviewTileId(i)).append("_").append(key).append("\" style=\"")
                        .append(essencePreviewTileStyle()).append("\">")
                        .append("<img id=\"").append(essencePreviewBackgroundId(i, key)).append("\" src=\"")
                        .append(backgroundIcon)
                        .append("\" width=\"").append(previewSize).append("\" height=\"").append(previewSize)
                        .append("\"/>")
                        .append("<div style=\"layout-mode:top;\"><div style=\"flex-weight:1;\"></div>")
                        .append("<div style=\"layout-mode:left;\"><div style=\"flex-weight:1;\"></div>")
                        .append("<img id=\"").append(essencePreviewOverlayId(i, key)).append("\" src=\"")
                        .append(overlayIcon != null ? overlayIcon : backgroundIcon)
                        .append("\" width=\"54\" height=\"54\" style=\"visibility:")
                        .append(overlayIcon != null ? "shown" : "hidden")
                        .append(";\"/>")
                        .append("<div style=\"flex-weight:1;\"></div></div>")
                        .append("<div style=\"flex-weight:1;\"></div></div>")
                        .append("</div>")
                        .append("<div style=\"flex-weight:1;\"></div></div>")
                        .append("<div style=\"flex-weight:1;\"></div>")
                        .append("</div>");
            }
            sb.append("</div>");
        }
        sb.append("<div style=\"flex-weight:1;\"></div></div>");
        return sb.toString();
    }

    private static String buildLoreSocketIconsHtml(Player player, Entry equipment) {
        LoreSocketData data = equipment == null || equipment.item == null || equipment.item.isEmpty()
                ? null
                : LoreSocketManager.getLoreSocketData(equipment.item);
        int count = data == null ? 0 : Math.max(0, data.getSocketCount());

        String baseIcon = UITemplateUtils.resolveCustomUiAsset("GemSlotEmpty.png", "GemSlotEmpty.png");
        int tileSize = 70;
        int wrapSize = 78;
        int overlaySize = 44;

        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"layout-mode: Left; spacing: 10;\"><div style=\"flex-weight:1;\"></div>");
        for (int i = 0; i < LORE_PREVIEW_SLOTS; i++) {
            LoreSocketData.LoreSocket socket = i < count ? data.getSocket(i) : null;
            boolean visible = i < count;
            boolean filled = socket != null && !socket.isEmpty();
            String colorKey = lorePreviewColorKey(socket);
            String overlayIcon = filled ? resolveLoreGemOverlayIcon(socket) : null;

            String tileStyle = "anchor-width:" + tileSize + "; anchor-height:" + tileSize + ";"
                    + " background-image:url('" + baseIcon + "'); background-size:100% 100%;"
                    + " background-repeat:no-repeat; layout-mode:Top;";

            sb.append("<div id=\"").append(lorePreviewWrapId(i)).append("\" style=\"")
                    .append(lorePreviewWrapStyle(resolveLoreColorHex(socket)))
                    .append(" visibility:").append(visible ? "shown" : "hidden").append(";\">")
                    .append("");
            for (String[] color : LORE_PREVIEW_COLORS) {
                String key = color[0];
                sb.append("<div id=\"").append(lorePreviewColorLayerId(i, key)).append("\" style=\"")
                        .append(lorePreviewColorLayerStyle(color[1], key.equals(colorKey)))
                        .append("\">")
                        .append("<div style=\"flex-weight:1;\"></div>")
                        .append("<div style=\"layout-mode:Left;\"><div style=\"flex-weight:1;\"></div>")
                        .append("<div style=\"").append(tileStyle).append("\">")
                        .append("<div style=\"flex-weight:1;\"></div>")
                        .append("<div style=\"layout-mode:Left;\">")
                        .append("<div style=\"flex-weight:1;\"></div>")
                        .append("<img id=\"").append(lorePreviewOverlayId(i, key)).append("\" src=\"")
                        .append(overlayIcon != null ? overlayIcon : baseIcon)
                        .append("\" width=\"").append(overlaySize).append("\" height=\"").append(overlaySize)
                        .append("\" style=\"visibility:").append(overlayIcon != null ? "shown" : "hidden").append(";\"/>")
                        .append("<div style=\"flex-weight:1;\"></div>")
                        .append("</div>")
                        .append("<div style=\"flex-weight:1;\"></div>")
                        .append("</div>")
                        .append("<div style=\"flex-weight:1;\"></div></div>")
                        .append("<div style=\"flex-weight:1;\"></div>")
                        .append("</div>");
            }
            sb.append("</div>");
        }
        sb.append("<div style=\"flex-weight:1;\"></div></div>");
        return sb.toString();
    }

    private static String buildLoreSocketSection(Player player, Entry equipment) {
        String icons = buildLoreSocketIconsHtml(player, equipment);
        boolean visible = equipment != null && equipment.item != null && !equipment.item.isEmpty() && hasLoreSockets(equipment);
        return "<div id=\"loreSocketSectionInner\" style=\"anchor-width:890; layout-mode:top; spacing:18; padding-top:22; visibility:"
                + (visible ? "shown" : "hidden") + ";\">"
                + "<p id=\"loreSocketPreviewTitle\" style=\"visibility:" + (visible ? "shown" : "hidden") + ";\">"
                + escapeHtml(LangLoader.getUITranslation(player, "ui.socket_bench.lore_socket_preview")) + "</p>"
                + "<div id=\"loreSocketIconsRow\">" + icons + "</div>"
                + "</div>";
    }

    private static String resolveLoreColorHex(LoreSocketData.LoreSocket socket) {
        return UISocketVisualUtils.loreColorHex(socket);
    }

    private static String lorePreviewColorKey(LoreSocketData.LoreSocket socket) {
        return UISocketVisualUtils.loreColorKey(socket);
    }

    private static String resolveLoreGemOverlayIcon(LoreSocketData.LoreSocket socket) {
        return UISocketVisualUtils.loreGemOverlayIcon(socket);
    }

    private static String resolveFilledSocketIconName() {
        return UISocketVisualUtils.filledSocketIconName();
    }

    private static String resolveBrokenSocketIconName() {
        return UISocketVisualUtils.brokenSocketIconName();
    }

    private static String resolveSocketBackgroundIcon(boolean punched, boolean broken, String brokenIconName) {
        return UISocketVisualUtils.socketBackgroundIcon(punched, broken, brokenIconName);
    }

    private static String getSocketColorHex(Socket socket) {
        return UISocketVisualUtils.socketColorHex(socket);
    }

    private static String socketPreviewAccent(Socket socket, boolean punched, boolean broken, boolean filled) {
        return UISocketVisualUtils.socketPreviewAccent(socket, punched, broken, filled);
    }

    private static String socketPreviewColorKey(Socket socket, boolean punched, boolean broken, boolean filled) {
        return UISocketVisualUtils.socketPreviewColorKey(socket, punched, broken, filled);
    }

    private static String resolveEssenceIconName(Socket socket) {
        return UISocketVisualUtils.essenceIconName(socket);
    }

    private static ProcessResult processSelection(Player player, BenchSnapshot snapshot, Entry equipment, Entry puncher, List<Entry> supports) {

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

        List<Entry> activeSupports = supports == null ? List.of() : supports;
        boolean wantsGhastly = hasSupportMaterial(activeSupports, SupportMaterial.GHASTLY_ESSENCE);
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
            if (hasSupportMaterial(activeSupports, SupportMaterial.SOCKET_EXPANDER)) {
                previewMax = Math.max(1, maxSockets);
            } else if (hasSupportMaterial(activeSupports, SupportMaterial.SOCKET_DIFFUSER)) {
                previewMax = Math.max(1, maxSockets - 1);
                previewCurrent = Math.min(previewCurrent, previewMax);
            }
            if (!hasSupportMaterial(activeSupports, SupportMaterial.SOCKET_EXPANDER)
                    && previewCurrent >= previewMax
                    && previewMax == maxSockets) {
                return new ProcessResult(LangLoader.getUITranslation(player, "ui.socket_bench.error_max_sockets", maxSockets), 100);
            }
        }

        if (wantsGhastly) {
            if (!hasConsumableSupport(player, activeSupports, SupportMaterial.GHASTLY_ESSENCE)) {
                return new ProcessResult(LangLoader.getUITranslation(player, "ui.socket_bench.error_support_missing"), 0);
            }
        }

        if (!consumeMaterial(player, puncher, 1)) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.socket_bench.error_puncher_changed"), 0);
        }

        List<SupportMaterial> usedSupportMaterials = consumeSupports(player, activeSupports);
        if (wantsGhastly && !usedSupportMaterials.contains(SupportMaterial.GHASTLY_ESSENCE)) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.socket_bench.error_support_missing"), 0);
        }

        if (usedSupportMaterials.contains(SupportMaterial.GHASTLY_ESSENCE)) {
            PunchResult loreResult = SocketManager.rollPunchResult(loreCurrent, SupportMaterial.GHASTLY_ESSENCE);
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

        boolean supportAdjusted = false;
        ExpanderOutcome expanderOutcome = ExpanderOutcome.NONE;
        boolean expanderActive = usedSupportMaterials.contains(SupportMaterial.SOCKET_EXPANDER);
        if (expanderActive) {
            expanderOutcome = applySocketExpanderRisk(socketData, ReforgeEquip.isWeapon(freshEquipment));
            supportAdjusted = expanderOutcome.changed();
        }
        for (SupportMaterial supportMaterial : usedSupportMaterials) {
            if (supportMaterial == SupportMaterial.SOCKET_EXPANDER || supportMaterial == SupportMaterial.GHASTLY_ESSENCE || supportMaterial == SupportMaterial.NONE) {
                continue;
            }
            if (expanderActive && supportMaterial == SupportMaterial.SOCKET_DIFFUSER) {
                continue;
            }
            supportAdjusted = SocketManager.applySupportSocketLimit(socketData, supportMaterial, ReforgeEquip.isWeapon(freshEquipment)) || supportAdjusted;
        }

        currentSockets = socketData.getCurrentSocketCount();
        maxSockets = socketData.getMaxSockets();
        if (currentSockets >= maxSockets) {
            if (supportAdjusted) {
                writeUpdatedEquipment(player, equipment, freshEquipment, socketData);
                return new ProcessResult(withExpanderStatus(player,
                        LangLoader.getUITranslation(player, "ui.socket_bench.status_support_applied"),
                        expanderOutcome), 100);
            }
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.socket_bench.error_max_sockets", maxSockets), 100);
        }

        PunchResult result = rollPunchResult(socketData, usedSupportMaterials);

        switch (result) {
            case SUCCESS:
                boolean socketAdded = socketData.addSocket();
                if (!socketAdded) {
                    if (supportAdjusted) {
                        writeUpdatedEquipment(player, equipment, freshEquipment, socketData);
                        return new ProcessResult(withExpanderStatus(player,
                                LangLoader.getUITranslation(player, "ui.socket_bench.status_support_applied"),
                                expanderOutcome), 100);
                    }
                    return new ProcessResult(LangLoader.getUITranslation(player, "ui.socket_bench.error_max_sockets", socketData.getMaxSockets()), 100);
                }
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
                return new ProcessResult(withExpanderStatus(player,
                        LangLoader.getUITranslation(player, "ui.socket_bench.status_success"),
                        expanderOutcome), 100);
            case BREAK:
                socketData.breakSocket();
                if (!expanderActive && socketData.getMaxSockets() > 1) {
                    double maxReduceChance = SocketManager.getConfig() != null ? SocketManager.getConfig().getMaxReduceChance() : 0.25;
                    if (Math.random() < maxReduceChance) {
                        socketData.reduceMaxSockets();
                    }
                }
                writeUpdatedEquipment(player, equipment, freshEquipment, socketData);
                sfxConfig.playShatter(player);
                return new ProcessResult(withExpanderStatus(player,
                        LangLoader.getUITranslation(player, "ui.socket_bench.status_broke"),
                        expanderOutcome), 100);
            case FAIL:
            default:
                if (supportAdjusted) {
                    writeUpdatedEquipment(player, equipment, freshEquipment, socketData);
                }
                sfxConfig.playFail(player);
                return new ProcessResult(withExpanderStatus(player,
                        LangLoader.getUITranslation(player, "ui.socket_bench.status_failed"),
                        expanderOutcome), 100);
        }
    }

    private static void writeUpdatedEquipment(Player player, Entry equipment, ItemStack original, SocketData socketData) {
        ItemStack updated = SocketManager.withSocketData(original, socketData);
        String itemId = updated.getItemId();
        boolean isWeapon = ReforgeEquip.isWeapon(updated);
        socketData.registerTooltips(updated, itemId, isWeapon);
        UIInventoryUtils.writeItem(player, equipment.containerKind == ContainerKind.HOTBAR, equipment.slot, updated);
        DynamicTooltipUtils.refreshPlayerTooltips(player.getPlayerRef());
    }

    private static ExpanderOutcome applySocketExpanderRisk(SocketData socketData, boolean isWeapon) {
        if (socketData == null) {
            return ExpanderOutcome.UNCHANGED;
        }
        int currentMax = socketData.getMaxSockets();
        int baseMax = SocketManager.getConfig() != null
                ? (isWeapon ? SocketManager.getConfig().getMaxSocketsWeapon() : SocketManager.getConfig().getMaxSocketsArmor())
                : currentMax;
        int cap = Math.max(1, baseMax > 0 ? baseMax + 1 : currentMax + 1);
        boolean gain = Math.random() < 0.5d;
        if (gain) {
            int newMax = Math.min(currentMax + 1, cap);
            if (newMax > currentMax) {
                socketData.setMaxSockets(newMax);
                return ExpanderOutcome.GAINED;
            }
            return ExpanderOutcome.UNCHANGED;
        }
        if (currentMax > 1) {
            socketData.reduceMaxSockets();
            return ExpanderOutcome.LOST;
        }
        return ExpanderOutcome.UNCHANGED;
    }

    private static String withExpanderStatus(Player player, String baseStatus, ExpanderOutcome expanderOutcome) {
        if (expanderOutcome == null || expanderOutcome == ExpanderOutcome.NONE) {
            return baseStatus;
        }
        String detail = switch (expanderOutcome) {
            case GAINED -> LangLoader.getUITranslation(player, "ui.socket_bench.status_expander_gained");
            case LOST -> LangLoader.getUITranslation(player, "ui.socket_bench.status_expander_lost");
            case UNCHANGED -> LangLoader.getUITranslation(player, "ui.socket_bench.status_expander_unchanged");
            default -> "";
        };
        if (detail == null || detail.isBlank()) {
            return baseStatus;
        }
        return baseStatus + " " + detail;
    }

    private static ItemStack readCurrentStack(Player player, Entry entry) {
        if (entry == null) {
            return null;
        }
        for (SourceRef source : entry.sources) {
            ItemStack stack = UIInventoryUtils.readItem(player, source.containerKind == ContainerKind.HOTBAR, source.slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (entry.itemId != null && stack.getItemId() != null && stack.getItemId().equalsIgnoreCase(entry.itemId)) {
                return stack;
            }
        }
        return null;
    }

    private static int currentQuantity(Player player, Entry entry) {
        if (entry == null) {
            return 0;
        }
        int total = 0;
        for (SourceRef source : entry.sources) {
            ItemStack current = UIInventoryUtils.readItem(player, source.containerKind == ContainerKind.HOTBAR, source.slot);
            if (current == null || current.isEmpty() || current.getItemId() == null || !current.getItemId().equalsIgnoreCase(entry.itemId)) {
                continue;
            }
            total += Math.max(0, current.getQuantity());
        }
        return total;
    }

    private static boolean consumeMaterial(Player player, Entry entry, int amount) {
        if (entry == null || amount <= 0) {
            return false;
        }
        int remaining = amount;
        for (SourceRef source : entry.sources) {
            if (remaining <= 0) {
                break;
            }
            ItemStack current = UIInventoryUtils.readItem(player, source.containerKind == ContainerKind.HOTBAR, source.slot);
            if (current == null || current.isEmpty() || current.getItemId() == null || !current.getItemId().equalsIgnoreCase(entry.itemId)) {
                continue;
            }
            int available = current.getQuantity();
            if (available <= 0) {
                continue;
            }
            int consume = Math.min(available, remaining);
            boolean consumed = UIInventoryUtils.consumeItem(
                    player,
                    source.containerKind == ContainerKind.HOTBAR,
                    source.slot,
                    entry.itemId,
                    consume);
            if (!consumed) {
                continue;
            }
            remaining -= consume;
        }
        return remaining <= 0;
    }

    private static boolean hasConsumableSupport(Player player, List<Entry> supports, SupportMaterial material) {
        if (supports == null || supports.isEmpty() || material == null) {
            return false;
        }
        for (Entry support : supports) {
            if (support == null || SocketManager.resolveSupportMaterial(support.itemId) != material) {
                continue;
            }
            ItemStack currentSupport = readCurrentStack(player, support);
            if (currentSupport != null && !currentSupport.isEmpty()
                    && currentSupport.getItemId() != null
                    && currentSupport.getItemId().equalsIgnoreCase(support.itemId)
                    && currentSupport.getQuantity() >= 1) {
                return true;
            }
        }
        return false;
    }

    private static List<SupportMaterial> consumeSupports(Player player, List<Entry> supports) {
        if (supports == null || supports.isEmpty()) {
            return List.of();
        }
        List<SupportMaterial> consumed = new ArrayList<>();
        for (Entry support : supports) {
            if (support == null) {
                continue;
            }
            SupportMaterial material = SocketManager.resolveSupportMaterial(support.itemId);
            if (material == SupportMaterial.NONE) {
                continue;
            }
            if (consumeMaterial(player, support, 1)) {
                consumed.add(material);
            }
        }
        return consumed;
    }

    private static PunchResult rollPunchResult(SocketData socketData, List<SupportMaterial> supportMaterials) {
        if (socketData == null || !socketData.canAddSocket()) {
            return PunchResult.FAIL;
        }
        int current = Math.max(0, socketData.getCurrentSocketCount());
        double successChance = SocketManager.getConfig() != null ? SocketManager.getConfig().getSuccessChance(current) : 0.75;
        double breakChance = SocketManager.getConfig() != null ? SocketManager.getConfig().getBreakChance(current) : 0.10;
        if (supportMaterials != null) {
            if (supportMaterials.contains(SupportMaterial.SOCKET_STABILIZER)) {
                breakChance *= 0.50;
            }
            if (supportMaterials.contains(SupportMaterial.SOCKET_REINFORCER)) {
                successChance = Math.min(1.0, successChance + 0.20);
            }
            if (supportMaterials.contains(SupportMaterial.SOCKET_GUARANTOR) && current == 0) {
                successChance = 1.0;
            }
        }
        double roll = Math.random();
        if (roll < breakChance) {
            return PunchResult.BREAK;
        }
        if (roll < breakChance + successChance) {
            return PunchResult.SUCCESS;
        }
        return PunchResult.FAIL;
    }

    private static ItemContainer getContainer(Player player, ContainerKind kind) {
        return UIInventoryUtils.getContainer(player, kind == ContainerKind.HOTBAR);
    }

	    private static Entry resolveSelection(List<Entry> entries, String selectedValue) {
        Entry byKey = findByKey(entries, selectedValue);
        if (byKey != null) {
            return byKey;
        }
	        return HyUIReflectionUtils.resolveIndexSelection(entries, selectedValue);
	    }

    private static List<Entry> resolveSupportSelections(List<Entry> entries, String selectedValue) {
        if (selectedValue == null || selectedValue.isBlank() || NONE_SUPPORT_KEY.equals(selectedValue) || "-1".equals(selectedValue)) {
            return List.of();
        }
        List<Entry> byKeys = findAllByKeys(entries, selectedValue);
        if (!byKeys.isEmpty()) {
            return byKeys;
        }
        Entry byIndex = HyUIReflectionUtils.resolveIndexSelection(entries, selectedValue);
        return byIndex == null ? List.of() : List.of(byIndex);
    }

    private static String supportSelectionKey(String selectedValue, List<Entry> selectedSupports) {
        if ("-1".equals(selectedValue) || selectedSupports == null || selectedSupports.isEmpty()) {
            return NONE_SUPPORT_KEY;
        }
        List<String> keys = new ArrayList<>();
        for (Entry support : selectedSupports) {
            keys.add(keyOf(support));
        }
        return joinSupportKeys(keys);
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
        List<Entry> supports = resolveSupportSelections(snapshot.supports, supportValue);
        if (equipment == null) {
            return new StatPreview("0%", "0%", "0 / 0");
        }

        SocketData data = SocketManager.getSocketData(equipment.item);
        if (data == null) {
            data = SocketData.fromDefaults(ReforgeEquip.isWeapon(equipment.item) ? "weapon" : "armor");
        }

        if (hasSupportMaterial(supports, SupportMaterial.GHASTLY_ESSENCE)) {
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
        if (hasSupportMaterial(supports, SupportMaterial.SOCKET_EXPANDER)) {
            int cap = baseMax > 0 ? baseMax + 1 : max + 1;
            previewMax = max >= cap ? max : Math.min(max + 1, cap);
        } else if (hasSupportMaterial(supports, SupportMaterial.SOCKET_DIFFUSER)) {
            previewMax = Math.max(1, max - 1);
            previewCurrent = Math.min(previewCurrent, previewMax);
        }

        double success = SocketManager.getConfig() != null ? SocketManager.getConfig().getSuccessChance(previewCurrent) : 0.75;
        double breakChance = SocketManager.getConfig() != null ? SocketManager.getConfig().getBreakChance(previewCurrent) : 0.10;
        if (hasSupportMaterial(supports, SupportMaterial.SOCKET_STABILIZER)) {
            breakChance *= 0.50;
        }
        if (hasSupportMaterial(supports, SupportMaterial.SOCKET_REINFORCER)) {
            success = Math.min(1.0, success + 0.20);
        }
        if (hasSupportMaterial(supports, SupportMaterial.SOCKET_GUARANTOR) && previewCurrent == 0) {
            success = 1.0;
        }

        String successText = String.format(java.util.Locale.ROOT, "%.0f%%", success * 100.0);
        String breakText = String.format(java.util.Locale.ROOT, "%.0f%%", breakChance * 100.0);
        String socketsText = previewCurrent + " / " + previewMax;
        return new StatPreview(successText, breakText, socketsText);
    }

    private static Object getStore(PlayerRef playerRef) throws Exception {
        return HyUIReflectionUtils.getStore(playerRef);
    }

    public static void closeForDisconnect(PlayerRef playerRef) {
        if (playerRef == null) {
            return;
        }
        closePageIfOpen(playerRef);
        cleanupPlayerState(playerRef);
    }

    private static void cleanupDismissedPage(PlayerRef playerRef, Object pageObj) {
        if (playerRef == null) {
            return;
        }
        Object current = openPages.get(playerRef);
        if (current != pageObj) {
            return;
        }
        cleanupPlayerState(playerRef);
    }

    private static void cleanupPlayerState(PlayerRef playerRef) {
        openPages.remove(playerRef);
        pendingSelections.remove(playerRef);
        processingPlayers.remove(playerRef);
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
