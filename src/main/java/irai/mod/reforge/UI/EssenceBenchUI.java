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
import irai.mod.reforge.Common.UI.HyUIEditUtils;
import irai.mod.reforge.Common.UI.UIHtmlUtils;
import irai.mod.reforge.Common.UI.UIInventoryUtils;
import irai.mod.reforge.Common.UI.UIItemUtils;
import irai.mod.reforge.Common.UI.UISocketVisualUtils;
import irai.mod.reforge.Common.UI.UITemplateUtils;
import irai.mod.reforge.Common.ResonantRecipeUtils;
import irai.mod.reforge.Config.SFXConfig;
import irai.mod.reforge.Interactions.ReforgeEquip;
import irai.mod.reforge.Lore.LoreGemRegistry;
import irai.mod.reforge.Lore.LoreSocketData;
import irai.mod.reforge.Lore.LoreSocketManager;
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
    private static final String LAYOUT_TEMPLATE_PATH = "Common/UI/Custom/Pages/EssenceBenchLayout.html";
    private static final String TEMPLATE_PATH = "Common/UI/Custom/Pages/EssenceBench.html";
    private static final String EQUIPMENT_TEMPLATE_PATH = "Common/UI/Custom/Pages/EssenceBenchEquipment.html";
    private static final String EQUIPMENT_SECTION_TEMPLATE_PATH = "Common/UI/Custom/Pages/EssenceBenchEquipmentSection.html";
    private static final String EQUIPMENT_CARD_TEMPLATE_PATH = "Common/UI/Custom/Pages/EssenceBenchEquipmentCard.html";
    private static final String MATERIALS_TEMPLATE_PATH = "Common/UI/Custom/Pages/EssenceBenchMaterials.html";
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
    private static final int PROGRESS_TICK_MS = 250;
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

    private enum ContainerKind {
        HOTBAR,
        STORAGE,
        ARMOR
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
        final List<Entry> armorSlots;
        final List<Entry> essences;
        final List<Entry> voidhearts;

        Snapshot(List<Entry> equipments, List<Entry> armorSlots, List<Entry> essences, List<Entry> voidhearts) {
            this.equipments = equipments;
            this.armorSlots = armorSlots;
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
        final int armorCardPage;
        final int weaponCardPage;
        final int essenceCardPage;
        final int supportCardPage;
        final String loreEquipmentKey;
        final String loreSocketKey;
        final boolean extractPrompt;

        SelectionState(String equipmentKey, String essenceKey, String supportKey, String slotKey,
                       String statusText, int progressValue, boolean processing) {
            this(equipmentKey, essenceKey, supportKey, slotKey, statusText, progressValue, processing, 0, 0, 0, 0);
        }

        SelectionState(String equipmentKey, String essenceKey, String supportKey, String slotKey,
                       String statusText, int progressValue, boolean processing, int armorCardPage, int weaponCardPage) {
            this(equipmentKey, essenceKey, supportKey, slotKey, statusText, progressValue, processing,
                    armorCardPage, weaponCardPage, 0, 0);
        }

        SelectionState(String equipmentKey, String essenceKey, String supportKey, String slotKey,
                       String statusText, int progressValue, boolean processing,
                       int armorCardPage, int weaponCardPage, int essenceCardPage, int supportCardPage) {
            this(equipmentKey, essenceKey, supportKey, slotKey, statusText, progressValue, processing,
                    armorCardPage, weaponCardPage, essenceCardPage, supportCardPage, null, null);
        }

        SelectionState(String equipmentKey, String essenceKey, String supportKey, String slotKey,
                       String statusText, int progressValue, boolean processing,
                       int armorCardPage, int weaponCardPage, int essenceCardPage, int supportCardPage,
                       String loreEquipmentKey, String loreSocketKey) {
            this(equipmentKey, essenceKey, supportKey, slotKey, statusText, progressValue, processing,
                    armorCardPage, weaponCardPage, essenceCardPage, supportCardPage, loreEquipmentKey, loreSocketKey, false);
        }

        SelectionState(String equipmentKey, String essenceKey, String supportKey, String slotKey,
                       String statusText, int progressValue, boolean processing,
                       int armorCardPage, int weaponCardPage, int essenceCardPage, int supportCardPage,
                       String loreEquipmentKey, String loreSocketKey, boolean extractPrompt) {
            this.equipmentKey = equipmentKey;
            this.essenceKey = essenceKey;
            this.supportKey = supportKey;
            this.slotKey = slotKey;
            this.statusText = statusText;
            this.progressValue = progressValue;
            this.processing = processing;
            this.armorCardPage = Math.max(0, armorCardPage);
            this.weaponCardPage = Math.max(0, weaponCardPage);
            this.essenceCardPage = Math.max(0, essenceCardPage);
            this.supportCardPage = Math.max(0, supportCardPage);
            this.loreEquipmentKey = loreEquipmentKey;
            this.loreSocketKey = loreSocketKey;
            this.extractPrompt = extractPrompt;
        }
    }

    private static final class EquipmentCardModel {
        final Entry entry;
        final int equipmentIndex;
        final int placeholderSlotNumber;
        final String sectionKey;

        EquipmentCardModel(Entry entry, int equipmentIndex, int placeholderSlotNumber, String sectionKey) {
            this.entry = entry;
            this.equipmentIndex = equipmentIndex;
            this.placeholderSlotNumber = placeholderSlotNumber;
            this.sectionKey = sectionKey;
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
            player.getPlayerRef().sendMessage(Message.raw("<color=#FF5555>" + LangLoader.getUITranslation(player, "ui.essence_bench.hyui_missing")));
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
        List<Entry> armorSlots = collectEquippedArmorSlots(player, equipments);
        List<Entry> essences = new ArrayList<>();
        List<Entry> voidhearts = new ArrayList<>();
        collectFromContainer(player, player.getInventory().getHotbar(), ContainerKind.HOTBAR, equipments, essences, voidhearts);
        collectFromContainer(player, player.getInventory().getStorage(), ContainerKind.STORAGE, equipments, essences, voidhearts);
        return new Snapshot(equipments, armorSlots, essences, voidhearts);
    }

    private static List<Entry> collectEquippedArmorSlots(Player player, List<Entry> equipments) {
        List<Entry> armorSlots = new ArrayList<>();
        if (player == null || player.getInventory() == null) {
            return armorSlots;
        }
        try {
            ItemContainer armorContainer = player.getInventory().getArmor();
            if (armorContainer == null) {
                return armorSlots;
            }
            for (short slot = 0; slot < armorContainer.getCapacity(); slot++) {
                ItemStack stack = armorContainer.getItemStack(slot);
                Entry entry = null;
                if (stack != null && !stack.isEmpty()) {
                    String itemId = stack.getItemId();
                    if (itemId != null && !itemId.isEmpty() && ReforgeEquip.isArmor(stack)) {
                        ItemStack effective = syncLoreSocketsIfNeeded(armorContainer, slot, stack);
                        entry = new Entry(ContainerKind.ARMOR, slot, effective, itemId, effective.getQuantity(),
                                UIItemUtils.displayNameOrItemId(effective, player));
                        equipments.add(entry);
                    }
                }
                armorSlots.add(entry);
            }
        } catch (Exception ignored) {
        }
        return armorSlots;
    }

    private static ItemStack syncLoreSocketsIfNeeded(ItemContainer container, short slot, ItemStack stack) {
        if (container == null || stack == null || stack.isEmpty()) {
            return stack;
        }
        LoreSocketData loreData = LoreSocketManager.getLoreSocketData(stack);
        if (loreData == null || loreData.getSocketCount() <= 0) {
            return stack;
        }
        if (!LoreSocketManager.syncSocketColors(stack, loreData)) {
            return stack;
        }
        ItemStack effective = LoreSocketManager.withLoreSocketData(stack, loreData);
        container.setItemStackForSlot(slot, effective);
        return effective;
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
            boolean equipmentItem = ReforgeEquip.isWeapon(stack) || ReforgeEquip.isArmor(stack);
            ItemStack effective = equipmentItem ? syncLoreSocketsIfNeeded(container, slot, stack) : stack;
            String itemId = effective.getItemId();
            if (itemId == null || itemId.isEmpty()) continue;
            String name = UIItemUtils.displayNameOrItemId(effective, player);
            Entry entry = new Entry(kind, slot, effective, itemId, effective.getQuantity(), name);

            if (equipmentItem) {
                SocketData sd = SocketManager.getSocketData(effective);
                LoreSocketData loreData = LoreSocketManager.getLoreSocketData(effective);
                if ((sd != null && sd.getMaxSockets() > 0)
                        || (loreData != null && loreData.getSocketCount() > 0)) {
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
            Method pageForPlayer = pageBuilderClass.getMethod("pageForPlayer", PlayerRef.class);
            Method fromHtml = pageBuilderClass.getMethod("fromHtml", String.class);
            Method addListener = pageBuilderClass.getMethod("addEventListener", String.class, eventBindingClass, java.util.function.BiConsumer.class);
            Method enablePersistentElementEdits = pageBuilderClass.getMethod("enablePersistentElementEdits", boolean.class);
            Method enableAsyncImageLoading = pageBuilderClass.getMethod("enableAsyncImageLoading", boolean.class);
            Method withLifetime = pageBuilderClass.getMethod("withLifetime", CustomPageLifetime.class);
            Method openMethod = pageBuilderClass.getMethod("open", Class.forName("com.hypixel.hytale.component.Store"));

	            Object activating = eventBindingClass.getField("Activating").get(null);

            String html = buildPageHtml(player, snapshot, state);
            Object pageBuilder = pageForPlayer.invoke(null, playerRef);
            pageBuilder = enablePersistentElementEdits.invoke(pageBuilder, true);
            pageBuilder = enableAsyncImageLoading.invoke(pageBuilder, true);
            pageBuilder = fromHtml.invoke(pageBuilder, html);

	            final Player finalPlayer = player;
	            final Snapshot finalSnapshot = snapshot;
	            final SelectionState finalState = state;
			            registerEquipmentSocketCardListeners(pageBuilder, addListener, activating, finalPlayer, finalSnapshot, state);
		            registerMaterialCardListeners(pageBuilder, addListener, activating, finalPlayer, finalSnapshot, state);
		            registerSocketPreviewListeners(pageBuilder, addListener, activating, finalPlayer, finalSnapshot, state);
		            registerLoreOverlayCloseListener(pageBuilder, addListener, activating, finalPlayer, state);
            registerExtractPromptListeners(pageBuilder, addListener, activating, finalPlayer, finalSnapshot, state);

	            addListener.invoke(pageBuilder, "processButton", activating,
	                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        if (Boolean.TRUE.equals(processingPlayers.get(finalPlayer.getPlayerRef()))) {
                            return;
                        }
                        SelectionState clickState = currentSelectionState(finalPlayer, finalState);
		                        String equipmentVal = clickState != null ? clickState.equipmentKey : null;
		                        String essenceVal = clickState != null ? clickState.essenceKey : null;
		                        String supportVal = clickState != null ? clickState.supportKey : null;
		                        String slotVal = clickState != null ? clickState.slotKey : null;
                        Entry equipment = resolveSelection(finalSnapshot.equipments, equipmentVal);
                        Entry essence = resolveSelection(finalSnapshot.essences, essenceVal);
                        Entry support = resolveSelection(finalSnapshot.voidhearts, supportVal);

                        final String processingText = LangLoader.getUITranslation(finalPlayer, "ui.essence_bench.status_processing");
	                        processingPlayers.put(finalPlayer.getPlayerRef(), true);
	                        pendingSelections.put(finalPlayer.getPlayerRef(),
	                                selectionWithPages(clickState, equipmentVal, essenceVal, supportVal, slotVal,
	                                        processingText,
	                                        0, true));
                        sfxConfig.playReforgeStart(finalPlayer);
                        HyUIEditUtils.editText(ctxObj, "statusLabel", processingText);
                        HyUIEditUtils.editProgress(ctxObj, "socketProgress", 0);
                        HyUIEditUtils.editDisabled(ctxObj, "processButton", true);
                        HyUIEditUtils.editDisabled(ctxObj, "extractButton", true);
                        HyUIEditUtils.updatePage(ctxObj, false);

                        for (int elapsed = PROGRESS_TICK_MS; elapsed < PROCESS_DURATION_MS; elapsed += PROGRESS_TICK_MS) {
                            final int delay = elapsed;
                            final int timedProgress = Math.min(99, (int) Math.round(((delay + PROGRESS_TICK_MS) * 100.0) / PROCESS_DURATION_MS));
                            scheduler.schedule(() -> finalPlayer.getWorld().execute(() -> {
	                                if (!Boolean.TRUE.equals(processingPlayers.get(finalPlayer.getPlayerRef()))) return;
	                                pendingSelections.put(finalPlayer.getPlayerRef(),
	                                        selectionWithPages(clickState, equipmentVal, essenceVal, supportVal, slotVal,
	                                                processingText,
	                                                timedProgress, true));
                                HyUIEditUtils.editProgress(ctxObj, "socketProgress", timedProgress);
                                HyUIEditUtils.editText(ctxObj, "statusLabel", processingText);
                            }), delay, TimeUnit.MILLISECONDS);
                        }

                        scheduler.schedule(() -> finalPlayer.getWorld().execute(() -> {
                            try {
	                                ProcessResult result = processSelection(finalPlayer, equipment, essence, support, slotVal);
	                                pendingSelections.put(finalPlayer.getPlayerRef(),
	                                        selectionWithPages(clickState, equipmentVal, essenceVal, supportVal, slotVal,
	                                                result.status, result.progress, false));
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
                        SelectionState clickState = currentSelectionState(finalPlayer, finalState);
                        Entry equipment = resolveSelection(finalSnapshot.equipments,
                                clickState != null ? clickState.equipmentKey : null);
                        if (shouldDisableExtract(clickState != null && clickState.processing, equipment, finalPlayer)) {
                            return;
                        }
                        pendingSelections.put(finalPlayer.getPlayerRef(), selectionWithExtractPrompt(clickState, true));
                        finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));
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

	    private static void registerEquipmentSocketCardListeners(
		            Object pageBuilder,
		            Method addListener,
	            Object activating,
	            Player player,
            Snapshot snapshot,
            SelectionState state) throws Exception {
        if (snapshot == null || snapshot.equipments == null || snapshot.equipments.isEmpty()) {
            return;
        }
        for (int equipmentIndex = 0; equipmentIndex < snapshot.equipments.size(); equipmentIndex++) {
            Entry equipment = snapshot.equipments.get(equipmentIndex);
            if (equipment == null || equipment.item == null || equipment.item.isEmpty()) {
                continue;
            }
	            final String equipmentKey = String.valueOf(equipmentIndex);
	            addListener.invoke(pageBuilder, equipmentCardButtonId(equipmentIndex), activating,
	                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                            SelectionState current = currentSelectionState(player, state);
	                        String essenceVal = current != null ? current.essenceKey : null;
	                        String supportVal = current != null ? current.supportKey : null;
                        pendingSelections.put(player.getPlayerRef(),
                                new SelectionState(equipmentKey, essenceVal, supportVal, "", null, 0, false,
	                                        current != null ? current.armorCardPage : 0,
	                                        current != null ? current.weaponCardPage : 0,
	                                        current != null ? current.essenceCardPage : 0,
	                                        current != null ? current.supportCardPage : 0));
                            player.getWorld().execute(() -> openWithSync(player));
	                    });

            LoreSocketData loreData = LoreSocketManager.getLoreSocketData(equipment.item);
            if (loreData != null && loreData.getSocketCount() > 0) {
                for (int loreIndex = 0; loreIndex < Math.min(3, loreData.getSocketCount()); loreIndex++) {
	                    final String loreSocketKey = String.valueOf(loreIndex);
	                    addListener.invoke(pageBuilder, loreSocketButtonId(equipmentIndex, loreIndex), activating,
	                            (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                                    SelectionState current = currentSelectionState(player, state);
		                                String essenceVal = current != null ? current.essenceKey : null;
		                                String supportVal = current != null ? current.supportKey : null;
	                                String slotVal = current != null ? current.slotKey : null;
	                                boolean sameOverlay = current != null
	                                        && equipmentKey.equals(current.loreEquipmentKey)
	                                        && loreSocketKey.equals(current.loreSocketKey);
	                                pendingSelections.put(player.getPlayerRef(),
	                                        new SelectionState(equipmentKey, essenceVal, supportVal, slotVal, null, 0, false,
	                                                current != null ? current.armorCardPage : 0,
	                                                current != null ? current.weaponCardPage : 0,
	                                                current != null ? current.essenceCardPage : 0,
	                                                current != null ? current.supportCardPage : 0,
	                                                sameOverlay ? null : equipmentKey,
	                                                sameOverlay ? null : loreSocketKey));
	                                    player.getWorld().execute(() -> openWithSync(player));
	                            });
                }
            }

            SocketData socketData = SocketManager.getSocketData(equipment.item);
            if (socketData == null || socketData.getSockets().isEmpty()) {
                continue;
            }
            List<Socket> sockets = socketData.getSockets();
            int renderedSocketButtons = renderedPunchedSocketCount(socketData);
            for (int socketIndex = 0; socketIndex < renderedSocketButtons; socketIndex++) {
                Socket socket = sockets.get(socketIndex);
                if (socket == null) {
                    continue;
                }
	                final String slotKey = String.valueOf(socket.getSlotIndex());
	                addListener.invoke(pageBuilder, equipmentSocketButtonId(equipmentIndex, socket.getSlotIndex()), activating,
	                        (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                                SelectionState current = currentSelectionState(player, state);
		                            String essenceVal = current != null ? current.essenceKey : null;
		                            String supportVal = current != null ? current.supportKey : null;
	                            pendingSelections.put(player.getPlayerRef(),
	                                    new SelectionState(equipmentKey, essenceVal, supportVal, slotKey, null, 0, false,
	                                            current != null ? current.armorCardPage : 0,
	                                            current != null ? current.weaponCardPage : 0,
	                                            current != null ? current.essenceCardPage : 0,
	                                            current != null ? current.supportCardPage : 0));
	                                applySelectionEdits(ctxObj, player, snapshot,
	                                        new SelectionState(equipmentKey, essenceVal, supportVal, slotKey, null, 0, false,
	                                                current != null ? current.armorCardPage : 0,
	                                                current != null ? current.weaponCardPage : 0,
	                                                current != null ? current.essenceCardPage : 0,
	                                                current != null ? current.supportCardPage : 0));
	                        });
	        }

		    }
	    }

    private static SelectionState currentSelectionState(Player player, SelectionState fallback) {
        SelectionState pending = player != null ? pendingSelections.get(player.getPlayerRef()) : null;
        return pending != null ? pending : fallback;
    }

    private static void applySelectionEdits(Object ctxObj, Player player, Snapshot snapshot, SelectionState nextState) {
        if (player == null) {
            return;
        }
        pendingSelections.put(player.getPlayerRef(), nextState);
        try {
            updateSelectionHighlights(ctxObj, snapshot, nextState);
            updateSelectionDetails(ctxObj, player, snapshot, nextState);
            HyUIEditUtils.updatePage(ctxObj, false);
        } catch (Exception e) {
            System.err.println("[SocketReforge] EssenceBenchUI in-place update failed: " + e.getMessage());
            player.getWorld().execute(() -> openWithSync(player));
        }
    }

    private static void updateSelectionHighlights(Object ctxObj, Snapshot snapshot, SelectionState state) {
        String equipmentKey = state != null ? state.equipmentKey : null;
        List<Entry> equipments = snapshot != null ? snapshot.equipments : List.of();
        for (int i = 0; i < equipments.size(); i++) {
            boolean selected = String.valueOf(i).equals(equipmentKey);
            String layerId = equipmentCardSelectedLayerId(i);
            HyUIEditUtils.editVisible(ctxObj, layerId, selected);
            HyUIEditUtils.editImage(ctxObj, selectedLayerImageId(layerId), selected ? "socket_panel_bg.png" : "");
            HyUIEditUtils.editVisible(ctxObj, equipmentCardMarkerId(i), selected);
        }

        String essenceKey = state != null ? state.essenceKey : null;
        List<Entry> essences = snapshot != null ? snapshot.essences : List.of();
        for (int i = 0; i < essences.size(); i++) {
            String buttonId = materialCardButtonId(true, i);
            boolean selected = String.valueOf(i).equals(essenceKey);
            String layerId = materialCardSelectedLayerId(buttonId);
            HyUIEditUtils.editVisible(ctxObj, layerId, selected);
            HyUIEditUtils.editImage(ctxObj, selectedLayerImageId(layerId), selected ? "socket_panel_bg.png" : "");
        }

        String supportKey = state != null ? state.supportKey : null;
        boolean noneSelected = supportKey == null || supportKey.isBlank();
        String noneLayerId = materialCardSelectedLayerId(supportNoneCardButtonId());
        HyUIEditUtils.editVisible(ctxObj, noneLayerId, noneSelected);
        HyUIEditUtils.editImage(ctxObj, selectedLayerImageId(noneLayerId), noneSelected ? "socket_panel_bg.png" : "");
        List<Entry> supports = snapshot != null ? snapshot.voidhearts : List.of();
        for (int i = 0; i < supports.size(); i++) {
            String buttonId = materialCardButtonId(false, i);
            boolean selected = String.valueOf(i).equals(supportKey);
            String layerId = materialCardSelectedLayerId(buttonId);
            HyUIEditUtils.editVisible(ctxObj, layerId, selected);
            HyUIEditUtils.editImage(ctxObj, selectedLayerImageId(layerId), selected ? "socket_panel_bg.png" : "");
        }
    }

    private static void updateSelectionDetails(Object ctxObj, Player player, Snapshot snapshot, SelectionState state) {
        Entry selectedEquipment = resolveSelection(snapshot != null ? snapshot.equipments : List.of(), state != null ? state.equipmentKey : null);
        Entry selectedEssence = resolveSelection(snapshot != null ? snapshot.essences : List.of(), state != null ? state.essenceKey : null);
        Entry selectedSupport = resolveSelection(snapshot != null ? snapshot.voidhearts : List.of(), state != null ? state.supportKey : null);
        String slotKey = state != null ? state.slotKey : null;

        HyUIEditUtils.editText(ctxObj, "selectedEquipmentText", selectedEquipment != null
                ? selectedEquipment.displayName
                : LangLoader.getUITranslation(player, "ui.essence_bench.option_no_equipment"));
        HyUIEditUtils.editText(ctxObj, "selectedEssenceText", selectedEssence != null
                ? selectedEssence.displayName
                : LangLoader.getUITranslation(player, "ui.essence_bench.option_no_essence"));
        HyUIEditUtils.editText(ctxObj, "selectedSupportText", selectedSupport != null
                ? selectedSupport.displayName
                : LangLoader.getUITranslation(player, "ui.essence_bench.option_no_support"));
        HyUIEditUtils.editText(ctxObj, "supportDurabilityText", buildSupportDurabilityText(player, selectedSupport));
        HyUIEditUtils.editText(ctxObj, "supportRecipeText", buildSupportRecipeText(player, selectedSupport));
        HyUIEditUtils.editText(ctxObj, "effectPreviewText", buildEffectPreviewText(player, selectedEquipment, selectedSupport));
        HyUIEditUtils.editText(ctxObj, "socketSummaryLabel", buildSocketSummary(player, selectedEquipment));
        HyUIEditUtils.editText(ctxObj, "selectedSocketDetails", buildSelectedSocketDetails(player, selectedEquipment, slotKey));
        HyUIEditUtils.editText(ctxObj, "statusLabel", selectedEquipment != null && isFilled(selectedEquipment)
                ? LangLoader.getUITranslation(player, "ui.essence_bench.status_all_filled")
                : LangLoader.getUITranslation(player, "ui.essence_bench.status_idle"));
        HyUIEditUtils.editProgress(ctxObj, "socketProgress", 0);
        HyUIEditUtils.editDisabled(ctxObj, "processButton",
                shouldDisable(false, selectedEquipment, state != null ? state.essenceKey : null, selectedSupport, slotKey));
        boolean showExtract = hasExtractableResonance(selectedEquipment, player);
        HyUIEditUtils.editVisible(ctxObj, "extractButton", showExtract);
        HyUIEditUtils.editDisabled(ctxObj, "extractButton", !showExtract);
        updateSocketPreviewVisuals(ctxObj, selectedEquipment, slotKey);
    }

	    private static void registerLoreOverlayCloseListener(
            Object pageBuilder,
            Method addListener,
            Object activating,
            Player player,
            SelectionState state) throws Exception {
        if (state == null || state.loreEquipmentKey == null || state.loreSocketKey == null) {
            return;
        }
	        addListener.invoke(pageBuilder, "loreOverlayClose", activating,
	                (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
	                    pendingSelections.put(player.getPlayerRef(),
	                            new SelectionState(state.equipmentKey, state.essenceKey, state.supportKey, state.slotKey, null, 0, false,
	                                    state.armorCardPage, state.weaponCardPage,
	                                    state.essenceCardPage, state.supportCardPage));
	                    player.getWorld().execute(() -> openWithSync(player));
	                });
    }

	    private static void registerSocketPreviewListeners(
	            Object pageBuilder,
	            Method addListener,
	            Object activating,
	            Player player,
	            Snapshot snapshot,
	            SelectionState state) throws Exception {
		        if (snapshot == null || snapshot.equipments == null || snapshot.equipments.isEmpty()) {
		            return;
		        }
		        for (int socketIndex = 0; socketIndex < 5; socketIndex++) {
                    final int previewIndex = socketIndex;
			            final String slotKey = String.valueOf(previewIndex);
			            addListener.invoke(pageBuilder, socketPreviewButtonId(previewIndex), activating,
			                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
	                                SelectionState current = currentSelectionState(player, state);
                                if (current == null || current.equipmentKey == null || current.equipmentKey.isBlank()) {
                                    return;
                                }
                                Entry equipment = resolveSelection(snapshot.equipments, current.equipmentKey);
                                SocketData socketData = equipment != null ? SocketManager.getSocketData(equipment.item) : null;
                                if (socketData == null || previewIndex >= renderedPunchedSocketCount(socketData)) {
                                    return;
                                }
				                        pendingSelections.put(player.getPlayerRef(),
				                                new SelectionState(current.equipmentKey, current.essenceKey, current.supportKey, slotKey, null, 0, false,
				                                        current.armorCardPage, current.weaponCardPage,
			                                        current.essenceCardPage, current.supportCardPage));
			                        applySelectionEdits(ctxObj, player, snapshot,
		                                new SelectionState(current.equipmentKey, current.essenceKey, current.supportKey, slotKey, null, 0, false,
		                                        current.armorCardPage, current.weaponCardPage,
		                                        current.essenceCardPage, current.supportCardPage));
		                    });
	        }
	    }

    private static void registerMaterialCardListeners(
            Object pageBuilder,
            Method addListener,
            Object activating,
            Player player,
            Snapshot snapshot,
            SelectionState state) throws Exception {
        registerVisibleMaterialCardListeners(pageBuilder, addListener, activating, player, snapshot, state,
                snapshot != null ? snapshot.essences : List.of(), true, 0);
        registerVisibleMaterialCardListeners(pageBuilder, addListener, activating, player, snapshot, state,
                snapshot != null ? snapshot.voidhearts : List.of(), false, 0);
    }

    private static void registerVisibleMaterialCardListeners(
            Object pageBuilder,
            Method addListener,
            Object activating,
            Player player,
            Snapshot snapshot,
            SelectionState state,
            List<Entry> entries,
            boolean essence,
            int page) throws Exception {
        if (essence && (entries == null || entries.isEmpty())) {
            return;
        }
        int totalCards = essence ? entries.size() : (entries != null ? entries.size() : 0) + 1;
        for (int visibleIndex = 0; visibleIndex < totalCards; visibleIndex++) {
            final boolean noneSupport = !essence && visibleIndex == 0;
            final int entryIndex = essence ? visibleIndex : visibleIndex - 1;
            final String materialKey = noneSupport ? "" : String.valueOf(entryIndex);
            addListener.invoke(pageBuilder, noneSupport ? supportNoneCardButtonId() : materialCardButtonId(essence, entryIndex), activating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                            SelectionState current = currentSelectionState(player, state);
		                        String equipmentVal = current != null ? current.equipmentKey : null;
		                        String essenceVal = essence ? materialKey : (current != null ? current.essenceKey : null);
		                        String supportVal = essence ? (current != null ? current.supportKey : null) : materialKey;
	                        String slotVal = current != null ? current.slotKey : null;
	                        pendingSelections.put(player.getPlayerRef(),
	                                selectionWithPages(current, equipmentVal, essenceVal, supportVal, slotVal, null, 0, false));
	                            applySelectionEdits(ctxObj, player, snapshot,
	                                    selectionWithPages(current, equipmentVal, essenceVal, supportVal, slotVal, null, 0, false));
			                    });
	        }
	    }

    private static void registerExtractPromptListeners(
            Object pageBuilder,
            Method addListener,
            Object activating,
            Player player,
            Snapshot snapshot,
            SelectionState state) throws Exception {
        if (state == null || !state.extractPrompt) {
            return;
        }
        addListener.invoke(pageBuilder, "extractCancelButton", activating,
                (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                    pendingSelections.put(player.getPlayerRef(), selectionWithExtractPrompt(state, false));
                    player.getWorld().execute(() -> openWithSync(player));
                });
        addListener.invoke(pageBuilder, "extractConfirmButton", activating,
                (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                    if (Boolean.TRUE.equals(processingPlayers.get(player.getPlayerRef()))) {
                        return;
                    }
                    Entry equipment = resolveSelection(snapshot != null ? snapshot.equipments : List.of(), state.equipmentKey);
                    if (shouldDisableExtract(state.processing, equipment, player)) {
                        pendingSelections.put(player.getPlayerRef(), selectionWithExtractPrompt(state, false));
                        player.getWorld().execute(() -> openWithSync(player));
                        return;
                    }
                    startExtractionProcess(player, equipment, selectionWithExtractPrompt(state, false), ctxObj);
                });
    }

    private static void startExtractionProcess(Player player, Entry equipment, SelectionState state, Object ctxObj) {
        String equipmentVal = state != null ? state.equipmentKey : null;
        String essenceVal = state != null ? state.essenceKey : null;
        String supportVal = state != null ? state.supportKey : null;
        String slotVal = state != null ? state.slotKey : null;

        final String extractingText = LangLoader.getUITranslation(player, "ui.essence_bench.status_extracting");
        processingPlayers.put(player.getPlayerRef(), true);
        pendingSelections.put(player.getPlayerRef(),
                selectionWithPages(state, equipmentVal, essenceVal, supportVal, slotVal,
                        extractingText,
                        0, true));
        sfxConfig.playReforgeStart(player);
        HyUIEditUtils.editVisible(ctxObj, "extractOverlay", false);
        HyUIEditUtils.editText(ctxObj, "statusLabel", extractingText);
        HyUIEditUtils.editProgress(ctxObj, "socketProgress", 0);
        HyUIEditUtils.editDisabled(ctxObj, "processButton", true);
        HyUIEditUtils.editDisabled(ctxObj, "extractButton", true);
        HyUIEditUtils.updatePage(ctxObj, false);

        for (int elapsed = PROGRESS_TICK_MS; elapsed < PROCESS_DURATION_MS; elapsed += PROGRESS_TICK_MS) {
            final int delay = elapsed;
            final int timedProgress = Math.min(99, (int) Math.round(((delay + PROGRESS_TICK_MS) * 100.0) / PROCESS_DURATION_MS));
            scheduler.schedule(() -> player.getWorld().execute(() -> {
                if (!Boolean.TRUE.equals(processingPlayers.get(player.getPlayerRef()))) return;
                pendingSelections.put(player.getPlayerRef(),
                        selectionWithPages(state, equipmentVal, essenceVal, supportVal, slotVal,
                                extractingText,
                                timedProgress, true));
                HyUIEditUtils.editProgress(ctxObj, "socketProgress", timedProgress);
                HyUIEditUtils.editText(ctxObj, "statusLabel", extractingText);
            }), delay, TimeUnit.MILLISECONDS);
        }

        scheduler.schedule(() -> player.getWorld().execute(() -> {
            try {
                ProcessResult result = processExtraction(player, equipment);
                pendingSelections.put(player.getPlayerRef(),
                        selectionWithPages(state, equipmentVal, essenceVal, supportVal, slotVal,
                                result.status, result.progress, false));
            } finally {
                processingPlayers.remove(player.getPlayerRef());
                openWithSync(player);
            }
        }), PROCESS_DURATION_MS, TimeUnit.MILLISECONDS);
    }

	    private static SelectionState selectionWithPages(
	            SelectionState fallback,
	            String equipmentKey,
	            String essenceKey,
	            String supportKey,
	            String slotKey,
	            String statusText,
	            int progressValue,
	            boolean processing) {
	        return new SelectionState(equipmentKey, essenceKey, supportKey, slotKey, statusText, progressValue,
	                processing,
	                fallback != null ? fallback.armorCardPage : 0,
	                fallback != null ? fallback.weaponCardPage : 0,
	                fallback != null ? fallback.essenceCardPage : 0,
	                fallback != null ? fallback.supportCardPage : 0);
	    }

    private static SelectionState selectionWithExtractPrompt(SelectionState fallback, boolean showPrompt) {
        return new SelectionState(
                fallback != null ? fallback.equipmentKey : null,
                fallback != null ? fallback.essenceKey : null,
                fallback != null ? fallback.supportKey : null,
                fallback != null ? fallback.slotKey : null,
                fallback != null ? fallback.statusText : null,
                fallback != null ? fallback.progressValue : 0,
                fallback != null && fallback.processing,
                fallback != null ? fallback.armorCardPage : 0,
                fallback != null ? fallback.weaponCardPage : 0,
                fallback != null ? fallback.essenceCardPage : 0,
                fallback != null ? fallback.supportCardPage : 0,
                fallback != null ? fallback.loreEquipmentKey : null,
                fallback != null ? fallback.loreSocketKey : null,
                showPrompt);
    }

    private static int renderedPunchedSocketCount(SocketData socketData) {
        if (socketData == null || socketData.getSockets() == null) {
            return 0;
        }
        return Math.min(Math.max(0, socketData.getCurrentSocketCount()), socketData.getSockets().size());
    }

	    private static String buildPageHtml(Player player, Snapshot snapshot, SelectionState state) {
	        String html = loadLayoutTemplate();
	        html = html.replace("{{equipmentPanel}}", buildEquipmentHtml(player, snapshot, state));
		html = html.replace("{{benchPanel}}", buildHtml(player, snapshot, state));
		html = html.replace("{{materialsPanel}}", buildMaterialsHtml(player, snapshot, state));
		html = html.replace("{{loreOverlayPanel}}", buildLoreOverlayHtml(player, snapshot, state));
        html = html.replace("{{extractOverlayPanel}}", buildExtractOverlayHtml(player, snapshot, state));
		return LangLoader.replaceUiTokens(player, html);
	    }

    private static String buildLoreOverlayHtml(Player player, Snapshot snapshot, SelectionState state) {
        if (snapshot == null || state == null || state.loreEquipmentKey == null || state.loreSocketKey == null) {
            return "";
        }
        Entry equipment = findByKey(snapshot.equipments, state.loreEquipmentKey);
        if (equipment == null || equipment.item == null || equipment.item.isEmpty()) {
            return "";
        }
        LoreSocketData data = LoreSocketManager.getLoreSocketData(equipment.item);
        if (data == null) {
            return "";
        }
        int socketIndex;
        try {
            socketIndex = Integer.parseInt(state.loreSocketKey);
        } catch (NumberFormatException ignored) {
            return "";
        }
        LoreSocketData.LoreSocket socket = data.getSocket(socketIndex);
        if (socket == null) {
            return "";
        }
        return buildLoreOverlayPanelHtml(player, equipment, socket);
    }

    private static String buildLoreOverlayPanelHtml(Player player, Entry equipment, LoreSocketData.LoreSocket socket) {
        String title = LangLoader.getUITranslation(player, "ui.essence_bench.lore_overlay_title");
        List<String> detailLines = buildLoreOverlayDetailLines(player, socket);
        String detailsHtml = buildLoreOverlayDetailsHtml(detailLines);
        int detailHeight = Math.max(130, Math.min(330, detailLines.size() * 22 + 14));
        int panelHeight = detailHeight + 88;
        return "<div id=\"loreOverlay\" style=\"anchor-full:200; layout-mode:Left; background-color:#0b0b1200;\">"
                + "<div style=\"layout-mode:Left;\">"
                + "<div style=\"anchor-width:430; anchor-height:" + panelHeight + "; layout-mode:Top; background-color:#1a1a2b; padding:10; border-radius:6;\">"
                + "<div style=\"layout-mode:Left; spacing:10; anchor-width:330;\">"
                + "<p style=\"anchor-width:250; anchor-height:24; font-weight:bold; color:#FFE28A;\">"
                + escapeHtml(title)
                + "</p>"
                + "<div style=\"flex-weight:1;\"></div>"
                + "<button id=\"loreOverlayClose\" class=\"secondary-button\" style=\"anchor-width:90; anchor-height:28;\">"
                + escapeHtml(LangLoader.getUITranslation(player, "ui.essence_bench.lore_overlay_close"))
                + "</button>"
                + "</div>"
                + "<img src=\"divider.png\" style=\"anchor-width:430; anchor-height:3;\">"
                + "<p style=\"anchor-width:410; anchor-height:24; color:#FFFFFF; font-weight:bold; font-size:14;\">"
                + escapeHtml(equipment.displayName)
                + "</p>"
                + "<div style=\"anchor-width:410; anchor-height:" + detailHeight + "; layout-mode:Top; spacing:4; background-color:#141426; padding:7; border-radius:4;\">"
                + detailsHtml
                + "</div>"
                + "</div>"
                + "</div>"
                + "</div>";
    }

    private static List<String> buildLoreOverlayDetailLines(Player player, LoreSocketData.LoreSocket socket) {
        List<String> lines = new ArrayList<>();
        if (socket == null) {
            lines.add(LangLoader.getUITranslation(player, "ui.essence_bench.lore_tooltip_unknown"));
            return lines;
        }
        if (socket.isLocked()) {
            lines.add(LangLoader.getUITranslation(player, "ui.essence_bench.lore_tooltip_locked"));
        }
        String color = normalizeLoreSocketColor(socket);
        String colorLabel = color == null
                ? LangLoader.getUITranslation(player, "ui.essence_bench.lore_tooltip_color_unknown")
                : humanizeToken(color);
        if (socket.hasSpirit()) {
            lines.add(LangLoader.getUITranslation(player, "ui.essence_bench.lore_tooltip_spirit",
                    localizeSpiritName(player, socket.getSpiritId())));
            lines.add(LangLoader.getUITranslation(player, "ui.essence_bench.lore_tooltip_level",
                    Math.max(1, socket.getLevel())));
            int xpNeeded = Math.max(1, LoreSocketManager.getXpRequiredForLevel(Math.max(1, socket.getLevel())));
            lines.add(LangLoader.getUITranslation(player, "ui.essence_bench.lore_tooltip_xp",
                    Math.max(0, socket.getXp()), xpNeeded));
            if (LoreSocketManager.needsFeed(socket)) {
                lines.add(LangLoader.getUITranslation(player, "ui.essence_bench.lore_tooltip_feed_ready"));
                lines.add(LangLoader.getUITranslation(player, "ui.essence_bench.lore_tooltip_feed_cost",
                        LoreSocketManager.getFeedCost(socket)));
            } else {
                lines.add(LangLoader.getUITranslation(player, "ui.essence_bench.lore_tooltip_feed_not_ready"));
            }
        } else if (socket.isEmpty()) {
            lines.add(LangLoader.getUITranslation(player, "ui.essence_bench.lore_tooltip_empty"));
        } else {
            lines.add(LangLoader.getUITranslation(player, "ui.essence_bench.lore_tooltip_no_spirit"));
        }
        lines.add(LangLoader.getUITranslation(player, "ui.essence_bench.lore_tooltip_color", colorLabel));
        lines.addAll(buildCompatibleSpiritNameLines(player, color));
        return lines;
    }

    private static String buildLoreOverlayDetailsHtml(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(loreOverlayLine(line));
        }
        return sb.toString();
    }

    private static String loreOverlayLine(String text) {
        return "<p style=\"anchor-width:396; anchor-height:20; color:#DDE6F3; font-size:13;\">"
                + escapeHtml(text)
                + "</p>";
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
        Entry selectedEssence = resolveSelection(snapshot.essences, essenceKey);
	        Entry selectedSupport = resolveSelection(snapshot.voidhearts, supportKey);
        String idleText = LangLoader.getUITranslation(player, "ui.essence_bench.status_idle");
        if (!processing && (idleText.equals(status) || "Idle".equals(status)) && isFilled(selectedEquipment)) {
            status = LangLoader.getUITranslation(player, "ui.essence_bench.status_all_filled");
        }

	        boolean processDisabled = shouldDisable(processing, selectedEquipment, essenceKey, selectedSupport, slotKey);
        boolean showExtract = hasExtractableResonance(selectedEquipment, player);
	        boolean extractDisabled = processing || !showExtract;

	        String html = loadTemplate();
        html = html.replace("{{selectedEquipmentText}}", escapeHtml(selectedEquipment != null
                ? selectedEquipment.displayName
                : LangLoader.getUITranslation(player, "ui.essence_bench.option_no_equipment")));
        html = html.replace("{{selectedEssenceText}}", escapeHtml(selectedEssence != null
                ? selectedEssence.displayName
                : LangLoader.getUITranslation(player, "ui.essence_bench.option_no_essence")));
        html = html.replace("{{selectedSupportText}}", escapeHtml(selectedSupport != null
                ? selectedSupport.displayName
                : LangLoader.getUITranslation(player, "ui.essence_bench.option_no_support")));
	        html = html.replace("{{supportDurabilityText}}", escapeHtml(buildSupportDurabilityText(player, selectedSupport)));
	        html = html.replace("{{supportRecipeText}}", escapeHtml(buildSupportRecipeText(player, selectedSupport)));
	        html = html.replace("{{effectPreviewText}}", escapeHtml(buildEffectPreviewText(player, selectedEquipment, selectedSupport)));
		        html = html.replace("{{socketIcons}}", buildSocketIconsHtml(selectedEquipment, slotKey));
	        html = html.replace("{{socketSummary}}", escapeHtml(buildSocketSummary(player, selectedEquipment)));
	        html = html.replace("{{selectedSocketDetails}}", escapeHtml(buildSelectedSocketDetails(player, selectedEquipment, slotKey)));
	        html = html.replace("{{socketProgressBar}}", buildSocketProgressBarHtml(progress));
	        html = html.replace("{{statusText}}", escapeHtml(status));
	        html = html.replace("{{processButton}}", buildActionButtonsHtml(player, processDisabled, extractDisabled, showExtract));
        html = html.replace("{{extractSection}}", "");
	        return LangLoader.replaceUiTokens(player, html);
	    }

    private static String buildEquipmentHtml(Player player, Snapshot snapshot, SelectionState state) {
	        String equipmentKey = state != null ? state.equipmentKey : null;
	        String slotKey = state != null ? state.slotKey : null;
	        String html = loadEquipmentTemplate();
	        html = html.replace("{{equipmentSocketCards}}",
	                buildEquipmentSocketCardsHtml(player, snapshot, equipmentKey, slotKey, 0, 0));
	        return LangLoader.replaceUiTokens(player, html);
		    }

    private static String buildExtractSectionHtml(Player player, boolean disabled) {
        if (disabled) {
            return "";
        }
        return "<img src=\"divider.png\" style=\"anchor-width: 920; anchor-height: 3;\">"
                + "<p>" + escapeHtml(LangLoader.getUITranslation(player, "ui.essence_bench.extract_title")) + "</p>"
                + "<p style=\"font-size:11; text-align:center;\">"
                + escapeHtml(LangLoader.getUITranslation(player, "ui.essence_bench.extract_hint"))
                + "</p>"
                + buildBenchButtonHtml(
                "extractButton",
                LangLoader.getUITranslation(player, "ui.essence_bench.extract_button"),
                false);
    }

    private static String buildActionButtonsHtml(Player player,
                                                 boolean processDisabled,
                                                 boolean extractDisabled,
                                                 boolean showExtract) {
        return "<div id=\"benchActionRow\" style=\"anchor-width:900; anchor-height:44; layout-mode:left; spacing:10;\">"
                + "<div style=\"flex-weight:1;\"></div>"
                + buildActionButtonHtml(
                "extractButton",
                LangLoader.getUITranslation(player, "ui.essence_bench.extract_button"),
                extractDisabled,
                showExtract)
                + buildActionButtonHtml(
                "processButton",
                LangLoader.getUITranslation(player, "ui.essence_bench.process_button"),
                processDisabled,
                true)
                + "</div>";
    }

    private static String buildActionButtonHtml(String id, String label, boolean disabled, boolean visible) {
        return "<button id=\"" + escapeHtml(id)
                + "\" style=\"anchor-width:220; anchor-height:40; visibility:" + (visible ? "shown" : "hidden") + ";\""
                + (disabled ? " disabled=\"true\"" : "")
                + ">" + escapeHtml(label) + "</button>";
    }

    private static String buildExtractOverlayHtml(Player player, Snapshot snapshot, SelectionState state) {
        if (state == null || !state.extractPrompt) {
            return "";
        }
        Entry equipment = findByKey(snapshot != null ? snapshot.equipments : List.of(), state.equipmentKey);
        String equipmentName = equipment != null && equipment.displayName != null
                ? equipment.displayName
                : LangLoader.getUITranslation(player, "ui.essence_bench.option_no_equipment_selected");
        return "<div id=\"extractOverlay\" style=\"anchor-width:1840; anchor-height:900; layout-mode:center; background-color:#000000AA;\">"
                + "<div class=\"decorated-container\" data-hyui-title=\""
                + escapeHtml(LangLoader.getUITranslation(player, "ui.essence_bench.extract_confirm_title"))
                + "\" style=\"anchor-width:520; anchor-height:250;\">"
                + "<div class=\"container-contents\" style=\"anchor-full:14; layout-mode:top; spacing:8;\">"
                + "<p style=\"font-weight:bold; text-align:center;\">"
                + escapeHtml(LangLoader.getUITranslation(player, "ui.essence_bench.extract_confirm_equipment", equipmentName))
                + "</p>"
                + "<p style=\"white-space:wrap; text-align:center;\">"
                + escapeHtml(LangLoader.getUITranslation(player, "ui.essence_bench.extract_confirm_warning"))
                + "</p>"
                + "<div style=\"flex-weight:1;\"></div>"
                + "<div style=\"layout-mode:left; spacing:14;\">"
                + "<div style=\"flex-weight:1;\"></div>"
                + buildSmallBenchButtonHtml("extractCancelButton",
                LangLoader.getUITranslation(player, "ui.essence_bench.extract_cancel_button"), false)
                + buildSmallBenchButtonHtml("extractConfirmButton",
                LangLoader.getUITranslation(player, "ui.essence_bench.extract_confirm_button"), false)
                + "<div style=\"flex-weight:1;\"></div>"
                + "</div></div></div></div>";
    }

	    private static String buildMaterialsHtml(Player player, Snapshot snapshot, SelectionState state) {
	        String essenceKey = state != null ? state.essenceKey : null;
	        String supportKey = state != null ? state.supportKey : null;
	        String html = loadMaterialsTemplate();
	        html = html.replace("{{materialsCards}}", buildMaterialsCardsHtml(player, snapshot, essenceKey, supportKey,
	                0, 0));
	        return LangLoader.replaceUiTokens(player, html);
	    }

	    private static String buildMaterialsCardsHtml(Player player, Snapshot snapshot, String essenceKey, String supportKey,
	                                                  int essencePage, int supportPage) {
	        StringBuilder sb = new StringBuilder();
	        appendMaterialSectionHtml(player, sb,
	                "ui.essence_bench.materials_essences",
	                "ui.essence_bench.materials_empty_essences",
	                snapshot != null ? snapshot.essences : List.of(),
	                essenceKey,
	                essencePage,
	                true);
	        appendMaterialSectionHtml(player, sb,
	                "ui.essence_bench.materials_supports",
	                "ui.essence_bench.materials_empty_supports",
	                snapshot != null ? snapshot.voidhearts : List.of(),
	                supportKey,
	                supportPage,
	                false);
	        return sb.toString();
	    }

	    private static void appendMaterialSectionHtml(
	            Player player,
	            StringBuilder sb,
	            String titleKey,
	            String emptyKey,
	            List<Entry> entries,
	            String selectedKey,
	            int page,
	            boolean essence) {
        sb.append("<div style=\"anchor-width:286; layout-mode:top; spacing:4;\">")
	                .append("<p style=\"anchor-height:18; text-align:left; font-weight:bold;\">")
	                .append(escapeHtml(LangLoader.getUITranslation(player, titleKey)))
	                .append("</p>")
                .append("<img src=\"divider.png\" style=\"anchor-width: 286; anchor-height: 3;\">");
	        if (essence && (entries == null || entries.isEmpty())) {
	            sb.append("<p style=\"font-size:11; color:#b0b0c2; text-align:left;\">")
	                    .append(escapeHtml(LangLoader.getUITranslation(player, emptyKey)))
	                    .append("</p>");
	        } else {
	            int totalCards = essence ? entries.size() : (entries != null ? entries.size() : 0) + 1;
	            for (int visibleIndex = 0; visibleIndex < totalCards; visibleIndex++) {
	                if (!essence && visibleIndex == 0) {
	                    appendSupportNoneCardHtml(player, sb, selectedKey == null || selectedKey.isBlank());
	                    continue;
	                }
	                int entryIndex = essence ? visibleIndex : visibleIndex - 1;
	                appendMaterialCardHtml(player, sb, entries.get(entryIndex), String.valueOf(entryIndex).equals(selectedKey),
	                        materialCardButtonId(essence, entryIndex));
	            }
	        }
	        sb.append("</div>");
	    }

	    private static void appendSupportNoneCardHtml(Player player, StringBuilder sb, boolean selected) {
	        String border = selected ? "#FFD24D" : "#00000000";
        String layerId = materialCardSelectedLayerId(supportNoneCardButtonId());
	        sb.append("<button id=\"")
	                .append(supportNoneCardButtonId())
                .append("\" class=\"raw-button\" style=\"anchor-width:280; anchor-height:58; layout-mode:top; padding:0; border:0; background-color:#00000000;\">")
	                .append("<div id=\"")
	                .append(supportNoneCardRootId())
	                .append("\" style=\"")
	                .append(materialCardRootStyle("#151526"))
	                .append("\">")
                .append("<div id=\"")
                .append(layerId)
                .append("\" style=\"anchor-width:280; anchor-height:58; layout-mode:top; background-color:#2B3F6D; visibility:")
                .append(selected ? "shown" : "hidden")
                .append(";\"><img id=\"")
                .append(selectedLayerImageId(layerId))
                .append("\" src=\"")
                .append(selected ? "socket_panel_bg.png" : "")
                .append("\" width=\"280\" height=\"58\"/></div>")
                .append("<div style=\"anchor-width:280; anchor-height:58; layout-mode:left; spacing:8; padding:5;\">")
	                .append("<div style=\"anchor-width:48; anchor-height:48; padding:2; background-color:")
	                .append(border)
	                .append("; layout-mode:top;\">")
	                .append("<img src=\"slot_bg.png\" width=\"44\" height=\"44\"/>")
	                .append("</div>")
                .append("<div style=\"anchor-width:210; anchor-height:48; layout-mode:top;\">")
	                .append("<p style=\"anchor-height:22; font-weight:bold; text-align:left;\">")
	                .append(escapeHtml(LangLoader.getUITranslation(player, "ui.essence_bench.option_none")))
	                .append("</p>")
	                .append("<p style=\"anchor-height:18; font-size:10; color:#b0b0c2; text-align:left;\">")
	                .append(escapeHtml(LangLoader.getUITranslation(player, "ui.essence_bench.support_material")))
	                .append("</p>")
	                .append("</div>")
                .append("</div>")
	                .append("</div>")
	                .append("</button>")
	                .append("<p style=\"anchor-height:3;\"></p>");
	    }

	    private static void appendMaterialCardHtml(Player player, StringBuilder sb, Entry entry, boolean selected, String buttonId) {
	        if (entry == null || entry.item == null || entry.item.isEmpty()) {
	            return;
	        }
	        String border = selected ? "#FFD24D" : "#00000000";
	        String description = buildMaterialDescription(player, entry);
        String layerId = materialCardSelectedLayerId(buttonId);
	        sb.append("<button id=\"")
	                .append(escapeHtml(buttonId))
                .append("\" class=\"raw-button\" style=\"anchor-width:280; anchor-height:58; layout-mode:top; padding:0; border:0; background-color:#00000000;\">")
	                .append("<div id=\"")
	                .append(materialCardRootId(buttonId))
	                .append("\" style=\"")
	                .append(materialCardRootStyle("#151526"))
	                .append("\">")
                .append("<div id=\"")
                .append(layerId)
                .append("\" style=\"anchor-width:280; anchor-height:58; layout-mode:top; background-color:#2B3F6D; visibility:")
                .append(selected ? "shown" : "hidden")
                .append(";\"><img id=\"")
                .append(selectedLayerImageId(layerId))
                .append("\" src=\"")
                .append(selected ? "socket_panel_bg.png" : "")
                .append("\" width=\"280\" height=\"58\"/></div>")
                .append("<div style=\"anchor-width:280; anchor-height:58; layout-mode:left; spacing:8; padding:5;\">")
	                .append("<div style=\"anchor-width:48; anchor-height:48; padding:2; background-color:")
	                .append(border)
	                .append("; layout-mode:top;\">")
	                .append("<span class=\"item-icon\" data-hyui-item-id=\"")
	                .append(escapeHtml(entry.itemId))
	                .append("\" style=\"anchor-width:44; anchor-height:44;\"></span>")
	                .append("</div>")
                .append("<div style=\"anchor-width:210; anchor-height:48; layout-mode:top;\">")
	                .append("<p style=\"anchor-height:18; font-weight:bold; text-align:left; white-space:nowrap;\">")
	                .append(escapeHtml(entry.displayName))
	                .append("</p>")
	                .append("<p style=\"anchor-height:14; font-size:10; color:#b0b0c2; text-align:left; white-space:nowrap;\">")
	                .append(escapeHtml(description))
	                .append("</p>")
	                .append("<p style=\"anchor-height:14; font-size:10; color:#d6d6e8; text-align:left;\">x")
	                .append(entry.quantity)
	                .append("</p>")
	                .append("</div>")
                .append("</div>")
	                .append("</div>")
	                .append("</button>")
	                .append("<p style=\"anchor-height:3;\"></p>");
	    }

	    private static String buildMaterialDescription(Player player, Entry entry) {
	        if (entry == null || entry.itemId == null || entry.itemId.isBlank()) {
	            return LangLoader.getUITranslation(player, "ui.essence_bench.material_desc_unknown");
	        }
	        String itemId = entry.itemId;
	        if (isEssenceItem(itemId)) {
	            String type = SocketManager.resolveEssenceTypeFromItemId(itemId);
	            String label = type == null || type.isBlank()
	                    ? LangLoader.getUITranslation(player, "ui.essence_bench.material_desc_essence_generic")
	                    : formatEssenceTypeName(player, type);
	            return LangLoader.getUITranslation(player, "ui.essence_bench.material_desc_essence", label);
	        }
	        if (isVoidheartItem(itemId)) {
	            return LangLoader.getUITranslation(player, "ui.essence_bench.material_desc_voidheart");
	        }
	        if (isHammerItem(itemId)) {
	            return isThoriumHammerItem(itemId)
	                    ? LangLoader.getUITranslation(player, "ui.essence_bench.material_desc_thorium_hammer")
	                    : LangLoader.getUITranslation(player, "ui.essence_bench.material_desc_hammer");
	        }
	        if (entry.item != null && ResonantRecipeUtils.isResonantRecipeItem(entry.item)) {
	            return LangLoader.getUITranslation(player, "ui.essence_bench.material_desc_recipe");
	        }
	        return LangLoader.getUITranslation(player, "ui.essence_bench.material_desc_support");
	    }

	    private static String formatEssenceTypeName(Player player, String type) {
	        if (type == null || type.isBlank()) {
	            return LangLoader.getUITranslation(player, "ui.essence_bench.material_desc_essence_generic");
	        }
	        String lower = type.trim().toLowerCase(Locale.ROOT);
	        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	    }

	    private static String buildSocketProgressBarHtml(int progress) {
	        return UIHtmlUtils.progressBar("socketProgress", progress, 900, 18, "boost_fill.png", "boost_track.png");
	    }

	    private static String buildBenchButtonHtml(String id, String label, boolean disabled) {
	        return UIHtmlUtils.button(id, label, 900, 40, disabled);
	    }

    private static String buildSmallBenchButtonHtml(String id, String label, boolean disabled) {
        return UIHtmlUtils.button(id, label, 190, 36, disabled);
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

    private static String buildEquipmentSocketCardsHtml(
            Player player,
            Snapshot snapshot,
            String selectedEquipmentKey,
            String selectedSlotKey,
            int armorCardPage,
            int weaponCardPage) {
        List<EquipmentCardModel> armorCards = buildArmorCardModels(snapshot);
        List<EquipmentCardModel> weaponCards = buildWeaponCardModels(snapshot);
        if (armorCards.isEmpty() && weaponCards.isEmpty()) {
            return "<p style=\"text-align:center; color:#b0b0c2;\">"
                    + escapeHtml(LangLoader.getUITranslation(player, "ui.essence_bench.equipment_list_none"))
                    + "</p>";
        }

        StringBuilder sb = new StringBuilder();
        appendEquipmentCardSectionHtml(player, sb, armorCards, selectedEquipmentKey, selectedSlotKey,
                armorCardPage, "ui.essence_bench.equipment_list_armor", "armorCardsPrev", "armorCardsNext");
        appendEquipmentCardSectionHtml(player, sb, weaponCards, selectedEquipmentKey, selectedSlotKey,
                weaponCardPage, "ui.essence_bench.equipment_list_weapons", "weaponCardsPrev", "weaponCardsNext");
        return sb.toString();
    }

    private static void appendEquipmentCardSectionHtml(
            Player player,
            StringBuilder sb,
            List<EquipmentCardModel> cards,
            String selectedEquipmentKey,
            String selectedSlotKey,
            int cardPage,
            String titleKey,
            String prevId,
            String nextId) {
	        if (cards == null || cards.isEmpty()) {
	            return;
	        }

	        StringBuilder cardsHtml = new StringBuilder();
	        for (int i = 0; i < cards.size(); i++) {
	            EquipmentCardModel card = cards.get(i);
	            appendEquipmentSocketCardHtml(player, cardsHtml, card.entry, card.equipmentIndex, selectedEquipmentKey,
	                    selectedSlotKey, card.placeholderSlotNumber);
	        }
	        sb.append(renderTemplate(loadEquipmentSectionTemplate(), Map.of(
	                "sectionTitle", escapeHtml(LangLoader.getUITranslation(player, titleKey)),
	                "pager", "",
	                "cards", cardsHtml.toString()
	        )));
	    }

    private static List<EquipmentCardModel> buildArmorCardModels(Snapshot snapshot) {
        List<EquipmentCardModel> cards = new ArrayList<>();
        List<Entry> equipments = snapshot != null ? snapshot.equipments : List.of();
        List<Entry> armorSlots = snapshot != null ? snapshot.armorSlots : List.of();
        int slotsToRender = Math.max(4, armorSlots != null ? armorSlots.size() : 0);
        for (int slot = 0; slot < slotsToRender; slot++) {
            Entry entry = armorSlots != null && slot < armorSlots.size() ? armorSlots.get(slot) : null;
            int equipmentIndex = entry != null ? equipments.indexOf(entry) : -1;
            cards.add(new EquipmentCardModel(entry, equipmentIndex, slot + 1, "ui.essence_bench.equipment_list_armor"));
        }
        for (int i = 0; i < equipments.size(); i++) {
            Entry entry = equipments.get(i);
            if (entry == null || entry.item == null || entry.item.isEmpty()) {
                continue;
            }
            if (entry.kind == ContainerKind.ARMOR || !ReforgeEquip.isArmor(entry.item) || ReforgeEquip.isWeapon(entry.item)) {
                continue;
            }
            cards.add(new EquipmentCardModel(entry, i, -1, "ui.essence_bench.equipment_list_armor"));
        }
        return cards;
    }

    private static List<EquipmentCardModel> buildWeaponCardModels(Snapshot snapshot) {
        List<EquipmentCardModel> cards = new ArrayList<>();
        List<Entry> equipments = snapshot != null ? snapshot.equipments : List.of();
        for (int i = 0; i < equipments.size(); i++) {
            Entry entry = equipments.get(i);
            if (entry == null || entry.item == null || entry.item.isEmpty()) {
                continue;
            }
            if (!ReforgeEquip.isWeapon(entry.item)) {
                continue;
            }
            cards.add(new EquipmentCardModel(entry, i, -1, "ui.essence_bench.equipment_list_weapons"));
        }
        return cards;
    }

    private static void appendEquipmentSectionHeaderHtml(Player player, StringBuilder sb, String titleKey) {
        sb.append("<div style=\"anchor-width:410; anchor-height:26; layout-mode:top;\">")
                .append("<p style=\"anchor-height:18; text-align:left; font-weight:bold;\">")
                .append(escapeHtml(LangLoader.getUITranslation(player, titleKey)))
                .append("</p>")
                .append("<img src=\"divider.png\" style=\"anchor-width: 410; anchor-height: 3;\">")
                .append("</div>");
    }

    private static void appendEquipmentSocketCardHtml(
            Player player,
            StringBuilder sb,
            Entry equipment,
            int equipmentIndex,
            String selectedEquipmentKey,
            String selectedSlotKey,
            int placeholderSlotNumber) {
        boolean hasEquipment = equipment != null && equipment.item != null && !equipment.item.isEmpty();
	        String equipmentKey = equipmentIndex >= 0 ? String.valueOf(equipmentIndex) : "";
	        boolean selectedEquipment = !equipmentKey.isEmpty() && equipmentKey.equals(selectedEquipmentKey);
	        String displayName = hasEquipment
	                ? equipment.displayName
	                : LangLoader.getUITranslation(player, "ui.essence_bench.equipment_card_empty_slot",
	                Math.max(1, placeholderSlotNumber));
	        boolean renderLoreSockets = hasEquipment && getPunchedLoreSocketCount(equipment.item) > 0;
        boolean renderResonanceIcon = hasEquipment && SocketManager.hasResonance(equipment.item);

	        StringBuilder essenceSockets = new StringBuilder();
	        if (hasEquipment) {
	            appendCompactSocketButtons(player, essenceSockets, equipment, equipmentIndex, selectedEquipment, selectedSlotKey);
	        }

	        String loreSocketsBlock = "";
	        if (renderLoreSockets) {
	            StringBuilder loreSockets = new StringBuilder();
	            appendCompactLoreSocketCells(player, loreSockets, equipment, equipmentIndex);
            loreSocketsBlock = "<div style=\"anchor-width:16;\"></div>" + loreSockets;
        }
        String essenceSocketsBlock = essenceSockets.toString();
        String selectedLayerId = equipmentCardSelectedLayerId(equipmentIndex);
        sb.append(renderTemplate(loadEquipmentCardTemplate(), Map.of(
                "cardId", equipmentCardRootId(equipmentIndex),
                "selectedLayerId", selectedLayerId,
                "selectedLayerImageId", selectedLayerImageId(selectedLayerId),
                "selectedLayerImage", selectedEquipment ? "socket_panel_bg.png" : "",
                "selectedVisibility", selectedEquipment ? "shown" : "hidden",
                "selectedMarkerId", equipmentCardMarkerId(equipmentIndex),
                "displayName", escapeHtml(displayName),
                "iconCell", buildEquipmentIconCell(equipment, equipmentIndex, renderResonanceIcon),
                "essenceSocketsBlock", essenceSocketsBlock,
                "loreSocketsBlock", loreSocketsBlock
        )));
    }

    private static String buildResonanceBadgeHtml() {
        String icon = UITemplateUtils.resolveCustomUiAsset(
                "Ingredient_Resonant_Essence.png",
                "Icons/ItemsGenerated/Ingredient_Resonant_Essence.png");
        return "<div style=\"layout-mode:top;\"><div style=\"flex-weight:1;\"></div>"
                + "<div style=\"layout-mode:left;\"><div style=\"flex-weight:1;\"></div>"
                + "<img src=\"" + icon + "\" width=\"28\" height=\"28\"/>"
                + "</div></div>";
    }

    private static void appendEquipmentIconCell(StringBuilder sb, Entry equipment, int equipmentIndex, boolean renderResonanceIcon) {
        boolean clickable = equipment != null && equipmentIndex >= 0;
        if (clickable) {
            sb.append("<button id=\"")
                    .append(equipmentCardButtonId(equipmentIndex))
                    .append("\" class=\"raw-button\" style=\"anchor-width:66; anchor-height:66; padding:0; background-color:#00000000; border:0; layout-mode:top;\">");
        } else {
            sb.append("<div style=\"anchor-width:66; anchor-height:66; padding:0; background-color:#00000000; border:0; layout-mode:top;\">");
        }
        sb.append("<div style=\"anchor-width:66; anchor-height:66; background-image:url('slot_bg.png'); background-size:100% 100%; background-repeat:no-repeat; layout-mode:full; padding:3;\">");
        if (equipment != null && equipment.itemId != null && !equipment.itemId.isBlank()) {
            sb.append("<span class=\"item-icon\" data-hyui-item-id=\"")
                    .append(escapeHtml(equipment.itemId))
                    .append("\" style=\"anchor-width:60; anchor-height:60;\"></span>");
        }
        if (renderResonanceIcon) {
            sb.append(buildResonanceBadgeHtml());
        }
        sb.append("</div>")
                .append(clickable ? "</button>" : "</div>");
    }

    private static String buildEquipmentIconCell(Entry equipment, int equipmentIndex, boolean renderResonanceIcon) {
        StringBuilder sb = new StringBuilder();
        appendEquipmentIconCell(sb, equipment, equipmentIndex, renderResonanceIcon);
        return sb.toString();
    }

    private static void appendEquipmentIconCell(StringBuilder sb, Entry equipment, int equipmentIndex) {
        appendEquipmentIconCell(sb, equipment, equipmentIndex, false);
    }

    private static String buildEquipmentIconCell(Entry equipment, int equipmentIndex) {
        StringBuilder sb = new StringBuilder();
        appendEquipmentIconCell(sb, equipment, equipmentIndex);
        return sb.toString();
    }

    private static void appendCompactSocketButtons(
            Player player,
            StringBuilder sb,
            Entry equipment,
            int equipmentIndex,
            boolean selectedEquipment,
            String selectedSlotKey) {
        SocketData socketData = SocketManager.getSocketData(equipment.item);
        if (socketData == null) {
            socketData = SocketData.fromDefaults(ReforgeEquip.isWeapon(equipment.item) ? "weapon" : "armor");
        }
        List<Socket> sockets = socketData.getSockets();
        int renderedSockets = Math.min(5, renderedPunchedSocketCount(socketData));
        String brokenIconName = resolveBrokenSocketIconName();
        for (int i = 0; i < renderedSockets; i++) {
            Socket socket = i < sockets.size() ? sockets.get(i) : null;
            int slotIndex = socket != null ? socket.getSlotIndex() : i;
            boolean selectedSocket = selectedEquipment && String.valueOf(slotIndex).equals(selectedSlotKey);
            String buttonId = socket != null
                    ? equipmentSocketButtonId(equipmentIndex, slotIndex)
                    : null;
            appendCompactSocketCell(sb, socket, true, selectedSocket, buttonId, brokenIconName);
        }
    }

    private static void appendCompactSocketCell(
            StringBuilder sb,
            Socket socket,
            boolean isPunched,
            boolean selected,
            String buttonId,
            String brokenIconName) {
        boolean isBroken = socket != null && socket.isBroken();
        boolean isFilled = socket != null && !socket.isBroken() && !socket.isEmpty();
        String backgroundIcon = UISocketVisualUtils.socketBackgroundIcon(isPunched, isBroken, brokenIconName);
        String overlayIcon = (isPunched && isFilled && !isBroken) ? resolveEssenceIconName(socket) : null;
        String accent = selected ? "#00000000" : UISocketVisualUtils.socketPreviewAccent(socket, isPunched, isBroken, isFilled);
        String cellStyle = "anchor-width:34; anchor-height:34; padding:1"
                + "; background-color:" + accent
                + (selected ? "; background-image:url('socket_panel_bg.png'); background-size:100% 100%; background-repeat:no-repeat" : "")
                + "; border:0; layout-mode:top;";

        if (buttonId != null) {
            sb.append("<button id=\"")
                    .append(buttonId)
                    .append("\" class=\"raw-button\" style=\"")
                    .append(cellStyle)
                    .append("\">");
        } else {
            sb.append("<div style=\"").append(cellStyle).append("\">");
        }
        sb.append("<div style=\"anchor-width:32; anchor-height:32; layout-mode:full;\">")
                .append("<img src=\"")
                .append(backgroundIcon)
                .append("\" width=\"32\" height=\"32\"/>")
                .append("<div style=\"layout-mode:top;\"><div style=\"flex-weight:1;\"></div>")
                .append("<div style=\"layout-mode:left;\"><div style=\"flex-weight:1;\"></div>");
        if (overlayIcon != null) {
            sb.append("<img src=\"").append(overlayIcon).append("\" width=\"22\" height=\"22\"/>");
        }
        sb.append("<div style=\"flex-weight:1;\"></div></div>")
                .append("<div style=\"flex-weight:1;\"></div></div>")
                .append("</div>");
        sb.append(buttonId != null ? "</button>" : "</div>");
    }

    private static int getPunchedLoreSocketCount(ItemStack item) {
        LoreSocketData data = LoreSocketManager.getLoreSocketData(item);
        return data == null ? 0 : Math.max(0, data.getSocketCount());
    }

    private static void appendCompactLoreSocketCells(Player player, StringBuilder sb, Entry equipment, int equipmentIndex) {
        LoreSocketData data = equipment != null ? LoreSocketManager.getLoreSocketData(equipment.item) : null;
        if (data == null || data.getSocketCount() <= 0) {
            return;
        }
        String baseIcon = UITemplateUtils.resolveCustomUiAsset("GemSlotEmpty.png", "GemSlotEmpty.png");
        for (int i = 0; i < Math.min(3, data.getSocketCount()); i++) {
            LoreSocketData.LoreSocket socket = data.getSocket(i);
            appendCompactLoreSocketCell(player, sb, equipment, socket, baseIcon, loreSocketButtonId(equipmentIndex, i));
        }
	    }

    private static void appendCompactLoreSocketCell(
            Player player,
            StringBuilder sb,
            Entry equipment,
            LoreSocketData.LoreSocket socket,
            String baseIcon,
            String buttonId) {
        boolean filled = socket != null && !socket.isEmpty();
        String accent = socket == null ? "#00000000" : resolveLoreColorHex(socket);
        String overlayIcon = filled ? resolveLoreGemOverlayIcon(socket) : null;
        sb.append("<button id=\"")
                .append(escapeHtml(buttonId))
                .append("\" class=\"raw-button\" style=\"anchor-width:34; anchor-height:34; padding:1; background-color:")
                .append(accent)
                .append("; border:0; layout-mode:Top;\">")
                .append("<div style=\"anchor-width:32; anchor-height:32; layout-mode:full;\">")
                .append("<img src=\"")
                .append(baseIcon)
                .append("\" width=\"32\" height=\"32\"/>")
                .append("<div style=\"layout-mode:top;\"><div style=\"flex-weight:1;\"></div>")
                .append("<div style=\"layout-mode:left;\"><div style=\"flex-weight:1;\"></div>");
        if (overlayIcon != null) {
            sb.append("<img src=\"").append(overlayIcon).append("\" width=\"28\" height=\"28\"/>");
        }
        sb.append("<div style=\"flex-weight:1;\"></div></div>")
                .append("<div style=\"flex-weight:1;\"></div></div>")
                .append("</div></button>");
    }

    private static String buildLoreSocketTooltip(Player player, LoreSocketData.LoreSocket socket) {
        if (socket == null) {
            return LangLoader.getUITranslation(player, "ui.essence_bench.lore_tooltip_unknown");
        }

        List<String> lines = new ArrayList<>();
        if (socket.isLocked()) {
            lines.add(LangLoader.getUITranslation(player, "ui.essence_bench.lore_tooltip_locked"));
        }

        String color = normalizeLoreSocketColor(socket);
        String colorLabel = color == null
                ? LangLoader.getUITranslation(player, "ui.essence_bench.lore_tooltip_color_unknown")
                : humanizeToken(color);

        if (socket.hasSpirit()) {
            lines.add(LangLoader.getUITranslation(player, "ui.essence_bench.lore_tooltip_spirit",
                    localizeSpiritName(player, socket.getSpiritId())));
            lines.add(LangLoader.getUITranslation(player, "ui.essence_bench.lore_tooltip_level",
                    Math.max(1, socket.getLevel())));
            int xpNeeded = Math.max(1, LoreSocketManager.getXpRequiredForLevel(Math.max(1, socket.getLevel())));
            lines.add(LangLoader.getUITranslation(player, "ui.essence_bench.lore_tooltip_xp",
                    Math.max(0, socket.getXp()), xpNeeded));
            if (LoreSocketManager.needsFeed(socket)) {
                lines.add(LangLoader.getUITranslation(player, "ui.essence_bench.lore_tooltip_feed_ready"));
                lines.add(LangLoader.getUITranslation(player, "ui.essence_bench.lore_tooltip_feed_cost",
                        LoreSocketManager.getFeedCost(socket)));
            } else {
                lines.add(LangLoader.getUITranslation(player, "ui.essence_bench.lore_tooltip_feed_not_ready"));
            }
        } else if (socket.isEmpty()) {
            lines.add(LangLoader.getUITranslation(player, "ui.essence_bench.lore_tooltip_empty"));
        } else {
            lines.add(LangLoader.getUITranslation(player, "ui.essence_bench.lore_tooltip_no_spirit"));
        }

        lines.add(LangLoader.getUITranslation(player, "ui.essence_bench.lore_tooltip_color", colorLabel));
        String compatible = buildCompatibleSpiritSummary(player, color);
        if (compatible != null && !compatible.isBlank()) {
            lines.add(LangLoader.getUITranslation(player, "ui.essence_bench.lore_tooltip_compatible", compatible));
        }
        return String.join("\n", lines);
    }

    private static String normalizeLoreSocketColor(LoreSocketData.LoreSocket socket) {
        if (socket == null) {
            return null;
        }
        String color = socket.getColor();
        if (color == null || color.isBlank()) {
            color = LoreGemRegistry.resolveColor(socket.getGemItemId());
        }
        if ((color == null || color.isBlank()) && socket.hasSpirit()) {
            color = LoreGemRegistry.resolveSpiritColor(socket.getSpiritId());
        }
        return color == null || color.isBlank() ? null : color.trim().toLowerCase(Locale.ROOT);
    }

    private static String buildCompatibleSpiritSummary(Player player, String color) {
        if (color == null || color.isBlank()) {
            return LangLoader.getUITranslation(player, "ui.essence_bench.lore_tooltip_compatible_unknown");
        }
        List<String> spirits = LoreGemRegistry.getAllowedSpirits(color);
        if (spirits == null || spirits.isEmpty()) {
            return LangLoader.getUITranslation(player, "ui.essence_bench.lore_tooltip_compatible_color_only", humanizeToken(color));
        }
        return LangLoader.getUITranslation(player, "ui.essence_bench.lore_tooltip_compatible_count",
                humanizeToken(color), spirits.size());
    }

    private static String buildCompatibleSpiritNameSummary(Player player, String color) {
        if (color == null || color.isBlank()) {
            return LangLoader.getUITranslation(player, "ui.essence_bench.lore_tooltip_compatible_unknown");
        }
        List<String> spirits = LoreGemRegistry.getAllowedSpirits(color);
        if (spirits == null || spirits.isEmpty()) {
            return LangLoader.getUITranslation(player, "ui.essence_bench.lore_tooltip_compatible_color_only", humanizeToken(color));
        }
        List<String> names = new ArrayList<>();
        for (int i = 0; i < Math.min(6, spirits.size()); i++) {
            String name = localizeSpiritName(player, spirits.get(i));
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }
        if (names.isEmpty()) {
            return LangLoader.getUITranslation(player, "ui.essence_bench.lore_tooltip_compatible_color_only", humanizeToken(color));
        }
        String summary = String.join(", ", names);
        int remaining = spirits.size() - names.size();
        if (remaining > 0) {
            summary += LangLoader.getUITranslation(player, "ui.essence_bench.lore_tooltip_compatible_more", remaining);
        }
        return summary;
    }

    private static List<String> buildCompatibleSpiritNameLines(Player player, String color) {
        List<String> lines = new ArrayList<>();
        if (color == null || color.isBlank()) {
            lines.add(LangLoader.getUITranslation(player, "ui.essence_bench.lore_tooltip_compatible",
                    LangLoader.getUITranslation(player, "ui.essence_bench.lore_tooltip_compatible_unknown")));
            return lines;
        }
        List<String> spirits = LoreGemRegistry.getAllowedSpirits(color);
        if (spirits == null || spirits.isEmpty()) {
            lines.add(LangLoader.getUITranslation(player, "ui.essence_bench.lore_tooltip_compatible",
                    LangLoader.getUITranslation(player, "ui.essence_bench.lore_tooltip_compatible_color_only", humanizeToken(color))));
            return lines;
        }
        lines.add(LangLoader.getUITranslation(player, "ui.essence_bench.lore_overlay_compatible_header"));
        int limit = Math.min(10, spirits.size());
        for (int i = 0; i < limit; i++) {
            String name = localizeSpiritName(player, spirits.get(i));
            if (name != null && !name.isBlank()) {
                lines.add("- " + name);
            }
        }
        int remaining = spirits.size() - limit;
        if (remaining > 0) {
            lines.add(LangLoader.getUITranslation(player, "ui.essence_bench.lore_tooltip_compatible_more", remaining));
        }
        return lines;
    }

    private static String localizeSpiritName(Player player, String spiritId) {
        if (spiritId == null || spiritId.isBlank()) {
            return LangLoader.getUITranslation(player, "ui.essence_bench.metadata_none");
        }
        String trimmed = spiritId.trim();
        String langCode = LangLoader.getPlayerLanguage(player);
        String[] keys = {
                "npcRoles." + trimmed + ".name",
                "npc." + trimmed + ".name",
                "spirit." + trimmed + ".name",
                trimmed
        };
        for (String key : keys) {
            String translated = LangLoader.getTranslationForLanguage(key, langCode);
            if (translated != null && !translated.isBlank() && !translated.equals(key)) {
                return translated;
            }
        }
        String raw = trimmed.contains(".") ? trimmed.substring(trimmed.lastIndexOf('.') + 1) : trimmed;
        return humanizeToken(raw);
    }

    private static String humanizeToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String lower = raw.trim().replace('_', ' ').replace('-', ' ').toLowerCase(Locale.ROOT);
        String[] parts = lower.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }

    private static String resolveLoreColorHex(LoreSocketData.LoreSocket socket) {
        return socket == null ? "#00000000" : UISocketVisualUtils.loreColorHex(socket);
    }

    private static String resolveLoreGemOverlayIcon(LoreSocketData.LoreSocket socket) {
        return UISocketVisualUtils.loreGemOverlayIcon(socket);
    }

    private static String resolveLoreGemIconByItemId(String itemId) {
        return UISocketVisualUtils.loreGemIconByItemId(itemId);
    }

    private static String resolveLoreGemIconByColor(String color) {
        return UISocketVisualUtils.loreGemIconByColor(color);
    }

    private static String buildSocketCardSummary(Player player, Entry equipment) {
        SocketData socketData = SocketManager.getSocketData(equipment.item);
        if (socketData == null) {
            return LangLoader.getUITranslation(player, "ui.essence_bench.summary_no_data");
        }
        return LangLoader.getUITranslation(player, "ui.essence_bench.equipment_card_socket_counts",
                socketData.getCurrentSocketCount(), socketData.getMaxSockets())
                + " | "
                + buildSocketSummary(player, equipment);
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
        return processing || !hasExtractableResonance(equipment, player);
    }

    private static boolean hasExtractableResonance(Entry equipment, Player player) {
        if (equipment == null) return false;
        ItemStack current = equipmentItem(equipment, player);
        if (current == null || current.isEmpty()) return false;
        return SocketManager.hasResonance(current);
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

	    private static String buildSocketIconsHtml(Entry equipment, String selectedSlotKey) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"layout-mode:left; spacing:0;\"><div style=\"flex-weight:1;\"></div>");
        SocketData socketData = equipment != null && equipment.item != null && !equipment.item.isEmpty()
                ? SocketManager.getSocketData(equipment.item)
                : null;
        if (socketData == null && equipment != null && equipment.item != null && !equipment.item.isEmpty()) {
            socketData = SocketData.fromDefaults(ReforgeEquip.isWeapon(equipment.item) ? "weapon" : "armor");
        }
        for (int i = 0; i < 5; i++) {
            Socket socket = socketData != null && i < socketData.getSockets().size() ? socketData.getSockets().get(i) : null;
            boolean visible = socketData == null ? i < 4 : i < renderedPunchedSocketCount(socketData);
            appendSocketPreviewSlotHtml(sb, i, socket, String.valueOf(i).equals(selectedSlotKey), visible);
        }
        sb.append("<div style=\"flex-weight:1;\"></div></div>");
        return sb.toString();
    }

    private static void appendSocketPreviewSlotHtml(StringBuilder sb, int slotIndex, Socket socket, boolean selected, boolean visible) {
        boolean isBroken = socket != null && socket.isBroken();
        boolean isFilled = socket != null && !socket.isBroken() && !socket.isEmpty();
        String backgroundIcon = isBroken ? resolveBrokenSocketIconName() : (visible ? "socket_empty.png" : "slot_bg.png");
        String overlayIcon = (visible && isFilled && !isBroken) ? resolveEssenceIconName(socket) : null;
        String colorKey = socketPreviewColorKey(socket, visible, isBroken, isFilled);

        sb.append("<button id=\"")
                .append(socketPreviewButtonId(slotIndex))
                .append("\" class=\"raw-button\" style=\"")
                .append(socketPreviewButtonStyle(visible))
                .append("\">")
                .append("<div style=\"flex-weight:1;\"></div>")
                .append("<div style=\"layout-mode:left;\"><div style=\"flex-weight:1;\"></div>")
                .append("<div id=\"").append(socketPreviewWrapId(slotIndex)).append("\" style=\"")
                .append(socketPreviewWrapStyle(selected, visible))
                .append("\">")
                .append("<div id=\"").append(socketPreviewSelectedLayerId(slotIndex))
                .append("\" style=\"anchor-width:111; anchor-height:111; layout-mode:top; background-color:#FFD24D; visibility:")
                .append(selected && visible ? "shown" : "hidden")
                .append(";\"></div>");
        for (String[] color : SOCKET_PREVIEW_COLORS) {
            String key = color[0];
            sb.append("<div id=\"").append(socketPreviewColorLayerId(slotIndex, key)).append("\" style=\"")
                    .append(socketPreviewColorLayerStyle(color[1], visible && key.equals(colorKey)))
                    .append("\">")
                    .append("<div style=\"flex-weight:1;\"></div>")
                    .append("<div style=\"layout-mode:left;\"><div style=\"flex-weight:1;\"></div>")
                    .append("<div style=\"anchor-width:96; anchor-height:96; layout-mode:full;\">")
                    .append("<img id=\"").append(socketPreviewBackgroundId(slotIndex, key)).append("\" src=\"")
                    .append(backgroundIcon)
                    .append("\" width=\"96\" height=\"96\"/>")
                    .append("<div style=\"layout-mode:top;\"><div style=\"flex-weight:1;\"></div>")
                    .append("<div style=\"layout-mode:left;\"><div style=\"flex-weight:1;\"></div>")
                    .append("<img id=\"").append(socketPreviewOverlayId(slotIndex, key)).append("\" src=\"")
                    .append(overlayIcon != null ? overlayIcon : backgroundIcon)
                    .append("\" width=\"83\" height=\"83\" style=\"visibility:")
                    .append(overlayIcon != null ? "shown" : "hidden")
                    .append(";\"/>")
                    .append("<div style=\"flex-weight:1;\"></div></div>")
                    .append("<div style=\"flex-weight:1;\"></div></div>")
                    .append("</div>")
                    .append("<div style=\"flex-weight:1;\"></div></div>")
                    .append("<div style=\"flex-weight:1;\"></div>")
                    .append("</div>");
        }
        sb.append("</div>")
                .append("<div style=\"flex-weight:1;\"></div></div>")
                .append("<div style=\"flex-weight:1;\"></div>")
                .append("</button>");
    }

    private static String socketPreviewWrapStyle(boolean selected, boolean visible) {
        return "anchor-width:111; anchor-height:111; layout-mode:full; padding:4"
                + "; visibility:" + (visible ? "shown" : "hidden") + ";";
    }

    private static String socketPreviewButtonStyle(boolean visible) {
        return "anchor-width:" + (visible ? 133 : 0)
                + "; anchor-height:119; background-color:#00000000; border:0; layout-mode:top; padding:0"
                + "; visibility:" + (visible ? "shown" : "hidden") + ";";
    }

    private static String socketPreviewColorLayerStyle(String color, boolean visible) {
        return "anchor-width:103; anchor-height:103; background-color:" + color
                + "; layout-mode:top; padding:4; visibility:" + (visible ? "shown" : "hidden") + ";";
    }

    private static String socketEssenceAccentColor(Socket socket, boolean visible, boolean broken, boolean filled) {
        if (broken) {
            return "#8A2020";
        }
        if (filled) {
            String color = socketEssenceColorFromText(resolveEssenceIconName(socket));
            if (color != null) {
                return color;
            }
            color = socketEssenceColorFromText(socket != null ? socket.getEssenceId() : null);
            if (color != null) {
                return color;
            }
            return getSocketColorHex(socket);
        }
        return visible ? "#5A451E" : "#2b2b3a";
    }

    private static String socketEssenceColorFromText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("fire")) return "#FFAA00";
        if (lower.contains("ice")) return "#55FFFF";
        if (lower.contains("life")) return "#55FF55";
        if (lower.contains("lightning")) return "#FFFF55";
        if (lower.contains("void")) return "#AA55FF";
        if (lower.contains("water")) return "#5555FF";
        return null;
    }

    private static String socketEssenceColorKeyFromText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("fire")) return "fire";
        if (lower.contains("ice")) return "ice";
        if (lower.contains("life")) return "life";
        if (lower.contains("lightning")) return "lightning";
        if (lower.contains("void")) return "void";
        if (lower.contains("water")) return "water";
        return null;
    }

    private static String socketPreviewColorKey(Socket socket, boolean punched, boolean broken, boolean filled) {
        String key = UISocketVisualUtils.socketPreviewColorKey(socket, punched, broken, filled);
        if (!filled || socket == null) {
            return key;
        }
        String fromIcon = socketEssenceColorKeyFromText(resolveEssenceIconName(socket));
        if (fromIcon != null) {
            return fromIcon;
        }
        String fromId = socketEssenceColorKeyFromText(socket.getEssenceId());
        return fromId != null ? fromId : key;
    }

    private static void updateSocketPreviewVisuals(Object ctxObj, Entry equipment, String selectedSlotKey) {
        SocketData socketData = equipment != null && equipment.item != null && !equipment.item.isEmpty()
                ? SocketManager.getSocketData(equipment.item)
                : null;
        if (socketData == null && equipment != null && equipment.item != null && !equipment.item.isEmpty()) {
            socketData = SocketData.fromDefaults(ReforgeEquip.isWeapon(equipment.item) ? "weapon" : "armor");
        }
        int renderedSockets = socketData == null ? 4 : renderedPunchedSocketCount(socketData);
        for (int i = 0; i < 5; i++) {
            Socket socket = socketData != null && i < socketData.getSockets().size() ? socketData.getSockets().get(i) : null;
            boolean visible = i < renderedSockets;
            boolean selected = String.valueOf(i).equals(selectedSlotKey);
            boolean isBroken = socket != null && socket.isBroken();
            boolean isFilled = socket != null && !socket.isBroken() && !socket.isEmpty();
            String backgroundIcon = isBroken ? resolveBrokenSocketIconName() : (visible ? "socket_empty.png" : "slot_bg.png");
            String overlayIcon = (visible && isFilled && !isBroken) ? resolveEssenceIconName(socket) : null;
            String colorKey = socketPreviewColorKey(socket, visible, isBroken, isFilled);
            HyUIEditUtils.editVisible(ctxObj, socketPreviewButtonId(i), visible);
            HyUIEditUtils.editVisible(ctxObj, socketPreviewWrapId(i), visible);
            HyUIEditUtils.editStyle(ctxObj, socketPreviewWrapId(i), socketPreviewWrapStyle(selected, visible));
            HyUIEditUtils.editVisible(ctxObj, socketPreviewSelectedLayerId(i), visible && selected);
            for (String[] color : SOCKET_PREVIEW_COLORS) {
                String key = color[0];
                boolean layerVisible = visible && key.equals(colorKey);
                HyUIEditUtils.editVisible(ctxObj, socketPreviewColorLayerId(i, key), layerVisible);
                HyUIEditUtils.editImage(ctxObj, socketPreviewBackgroundId(i, key), backgroundIcon);
                HyUIEditUtils.editImage(ctxObj, socketPreviewOverlayId(i, key), overlayIcon != null ? overlayIcon : backgroundIcon);
                HyUIEditUtils.editVisible(ctxObj, socketPreviewOverlayId(i, key), layerVisible && overlayIcon != null);
            }
        }
    }

    private static String resolveFilledSocketIconName() {
        return UISocketVisualUtils.filledSocketIconName();
    }

    private static String resolveBrokenSocketIconName() {
        return UISocketVisualUtils.brokenSocketIconName();
    }

    private static String getSocketColorHex(Socket socket) {
        return UISocketVisualUtils.socketColorHex(socket);
    }

    private static String resolveEssenceIconName(Socket socket) {
        return UISocketVisualUtils.essenceIconName(socket);
    }

    private static String resolveIconFromEssenceId(String essenceId) {
        return UISocketVisualUtils.iconFromEssenceId(essenceId);
    }

    private static String resolveIconFromItemStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        String icon = resolveIconFromItem(stack.getItem());
        if (icon != null && !icon.isBlank()) {
            return icon;
        }
        return resolveIconFromItemId(stack.getItemId());
    }

    private static String resolveIconFromItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        try {
            Item item = Item.getAssetMap().getAssetMap().get(itemId);
            String icon = resolveIconFromItem(item);
            if (icon != null && !icon.isBlank()) {
                return icon;
            }
        } catch (Exception ignored) {
        }
        return "Icons/ItemsGenerated/" + itemId.trim() + ".png";
    }

    private static String resolveIconFromItem(Item item) {
        if (item == null || item == Item.UNKNOWN) {
            return null;
        }
        String icon = item.getIcon();
        if (icon == null || icon.isBlank() || Item.UNKNOWN_TEXTURE.equals(icon)) {
            return null;
        }
        String uiIcon = resolveUiIconPath(icon);
        return uiIcon != null ? uiIcon : icon;
    }

    private static String resolveUiIconPath(String iconPath) {
        return UISocketVisualUtils.uiIconPath(iconPath);
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
            DynamicTooltipUtils.refreshPlayerTooltips(player.getPlayerRef());
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
            DynamicTooltipUtils.refreshPlayerTooltips(player.getPlayerRef());
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
            DynamicTooltipUtils.refreshPlayerTooltips(player.getPlayerRef());
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
                    } else if (!consumeRecipeSupportUsage(player, support)) {
                        resonanceNotice = t(player, "ui.essence_bench.error_recipe_no_usages");
                    } else {
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
        DynamicTooltipUtils.refreshPlayerTooltips(player.getPlayerRef());
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

        boolean removed = removeEquipmentStack(player, equipment);
        if (!removed) {
            return new ProcessResult(t(player, "ui.essence_bench.status_extract_failed"), 0);
        }
        if (!UIInventoryUtils.addItemToInventory(player, output)) {
            // Best effort restore to avoid losing the equipment.
            writeStack(player, equipment, current);
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
        if (equipment.kind == ContainerKind.ARMOR) {
            ItemContainer container = getContainer(player, ContainerKind.ARMOR);
            return container == null ? null : container.getItemStack(equipment.slot);
        }
        return UIInventoryUtils.readItem(player, equipment.kind == ContainerKind.HOTBAR, equipment.slot);
    }

    private static void writeStack(Player player, Entry entry, ItemStack stack) {
        if (entry == null || stack == null) {
            return;
        }
        if (entry.kind == ContainerKind.ARMOR) {
            ItemContainer container = getContainer(player, ContainerKind.ARMOR);
            if (container != null) {
                container.setItemStackForSlot(entry.slot, stack);
            }
            return;
        }
        UIInventoryUtils.writeItem(player, entry.kind == ContainerKind.HOTBAR, entry.slot, stack);
    }

    private static boolean removeEquipmentStack(Player player, Entry equipment) {
        if (player == null || equipment == null) {
            return false;
        }
        if (equipment.kind == ContainerKind.ARMOR) {
            ItemContainer container = getContainer(player, ContainerKind.ARMOR);
            if (container == null) {
                return false;
            }
            container.removeItemStackFromSlot(equipment.slot, 1, false, false);
            return true;
        }
        return UIInventoryUtils.removeItem(player, equipment.kind == ContainerKind.HOTBAR, equipment.slot, 1);
    }

    private static boolean consumeMaterial(Player player, Entry entry, int amount) {
        if (entry == null || amount <= 0) return false;
        return UIInventoryUtils.consumeItem(player, entry.kind == ContainerKind.HOTBAR, entry.slot, entry.itemId, amount);
    }

    private static boolean consumeRecipeSupportUsage(Player player, Entry support) {
        if (player == null || support == null || !isRecipeSupport(support)) {
            return false;
        }
        ItemStack current = UIInventoryUtils.readItem(player, support.kind == ContainerKind.HOTBAR, support.slot);
        if (!ResonantRecipeUtils.isResonantRecipeItem(current)) {
            return false;
        }
        String expectedName = ResonantRecipeUtils.normalizeRecipeName(ResonantRecipeUtils.getRecipeName(support.item));
        String currentName = ResonantRecipeUtils.normalizeRecipeName(ResonantRecipeUtils.getRecipeName(current));
        if (!expectedName.equals(currentName)) {
            return false;
        }
        ResonantRecipeUtils.UsageState usage = ResonantRecipeUtils.getUsageState(current);
        if (!usage.hasRemaining()) {
            return false;
        }
        if (usage.remaining() <= 1) {
            return UIInventoryUtils.removeItem(player, support.kind == ContainerKind.HOTBAR, support.slot, 1);
        }
        ItemStack updatedSupport = ResonantRecipeUtils.decrementUsage(current);
        writeStack(player, support, DynamicTooltipUtils.applyNativeTooltip(updatedSupport, LangLoader.getPlayerLanguage(player)));
        return true;
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
        return UISocketVisualUtils.essenceItemId(essenceId);
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
        if (player == null || player.getInventory() == null || kind == null) {
            return null;
        }
        if (kind == ContainerKind.ARMOR) {
            return player.getInventory().getArmor();
        }
        return UIInventoryUtils.getContainer(player, kind == ContainerKind.HOTBAR);
    }

    private static String equipmentLocationLabel(Player player, ContainerKind kind) {
        if (kind == ContainerKind.ARMOR) {
            return LangLoader.getUITranslation(player, "ui.essence_bench.metadata_armor");
        }
        if (kind == ContainerKind.HOTBAR) {
            return LangLoader.getUITranslation(player, "ui.essence_bench.metadata_hotbar");
        }
        return LangLoader.getUITranslation(player, "ui.essence_bench.metadata_storage");
    }

    private static String buildSelectedSocketDetails(Player player, Entry equipment, String slotKey) {
        if (equipment == null) {
            return LangLoader.getUITranslation(player, "ui.essence_bench.selected_socket_none");
        }
        SocketData socketData = SocketManager.getSocketData(equipment.item);
        if (socketData == null || socketData.getSockets().isEmpty()) {
            return LangLoader.getUITranslation(player, "ui.essence_bench.summary_no_data");
        }
        int slotIndex = resolveSlotIndex(slotKey, socketData);
        if (slotIndex < 0) {
            return LangLoader.getUITranslation(player, "ui.essence_bench.selected_socket_none");
        }
        Socket socket = findSocketByIndex(socketData, slotIndex);
        if (socket == null) {
            return LangLoader.getUITranslation(player, "ui.essence_bench.error_slot_invalid");
        }

        return LangLoader.getUITranslation(player, "ui.essence_bench.selected_socket_slot", buildSlotLabel(player, socket));
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
        String location = equipmentLocationLabel(player, equipment.kind);
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

	    private static String loadLayoutTemplate() {
	        return UITemplateUtils.loadTemplate(
	                EssenceBenchUI.class,
	                LAYOUT_TEMPLATE_PATH,
	                "<div class=\"page-overlay\">{{equipmentPanel}}{{benchPanel}}</div>",
	                "EssenceBenchLayoutUI");
	    }

	    private static String loadEquipmentTemplate() {
	        return UITemplateUtils.loadTemplate(
	                EssenceBenchUI.class,
	                EQUIPMENT_TEMPLATE_PATH,
	                "<div><p>Essence Bench equipment UI template missing.</p></div>",
	                "EssenceBenchEquipmentUI");
	    }

	    private static String loadEquipmentSectionTemplate() {
	        return UITemplateUtils.loadTemplate(
	                EssenceBenchUI.class,
	                EQUIPMENT_SECTION_TEMPLATE_PATH,
	                "<div style=\"anchor-width:450; layout-mode:top; spacing:4;\"><p>{{sectionTitle}}</p>{{cards}}</div>",
	                "EssenceBenchEquipmentSectionUI");
	    }

	    private static String loadEquipmentCardTemplate() {
	        return UITemplateUtils.loadTemplate(
	                EssenceBenchUI.class,
	                EQUIPMENT_CARD_TEMPLATE_PATH,
	                "<div><p>{{displayName}}</p>{{iconCell}}<p>{{locationText}}</p><p>{{summaryText}}</p>{{essenceSocketsBlock}}{{loreSocketsBlock}}</div>",
	                "EssenceBenchEquipmentCardUI");
	    }

	    private static String loadMaterialsTemplate() {
	        return UITemplateUtils.loadTemplate(
	                EssenceBenchUI.class,
	                MATERIALS_TEMPLATE_PATH,
	                "<div><p>{{ui.essence_bench.materials_list_title}}</p>{{materialsCards}}</div>",
	                "EssenceBenchMaterialsUI");
	    }

	    private static String renderTemplate(String template, Map<String, String> values) {
	        String rendered = template == null ? "" : template;
	        if (values == null || values.isEmpty()) {
	            return rendered;
	        }
	        for (Map.Entry<String, String> entry : values.entrySet()) {
	            rendered = rendered.replace("{{" + entry.getKey() + "}}",
	                    entry.getValue() != null ? entry.getValue() : "");
	        }
	        return rendered;
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

    private static String selectionContextValue(Object ctxObj, String elementId, String selector, String fallback) {
        String value = getContextValue(ctxObj, elementId, selector);
        return value != null ? value : fallback;
    }

	    private static String equipmentCardButtonId(int equipmentIndex) {
	        return "equipmentCard_" + equipmentIndex;
	    }

    private static String equipmentCardRootId(int equipmentIndex) {
        return "equipmentCardRoot_" + equipmentIndex;
    }

    private static String equipmentCardSelectedLayerId(int equipmentIndex) {
        return "equipmentCardSelected_" + equipmentIndex;
    }

    private static String equipmentCardMarkerId(int equipmentIndex) {
        return "equipmentCardMarker_" + equipmentIndex;
    }

    private static String selectedLayerImageId(String layerId) {
        return layerId + "_image";
    }

    private static String equipmentCardRootStyle(String background) {
        return "anchor-width:424; anchor-height:78; layout-mode:left; padding:6; background-color:" + background + ";";
    }

	    private static String materialCardButtonId(boolean essence, int index) {
	        return (essence ? "essenceCard_" : "supportCard_") + index;
	    }

    private static String materialCardRootId(String buttonId) {
        return buttonId + "_root";
    }

    private static String materialCardSelectedLayerId(String buttonId) {
        return buttonId + "_selected";
    }

    private static String materialCardRootStyle(String background) {
        return "anchor-width:280; anchor-height:58; layout-mode:full; background-color:" + background + ";";
    }

	    private static String supportNoneCardButtonId() {
	        return "supportCard_none";
	    }

    private static String supportNoneCardRootId() {
        return materialCardRootId(supportNoneCardButtonId());
    }

	    private static String equipmentSocketButtonId(int equipmentIndex, int slotIndex) {
	        return "equipmentSocket_" + equipmentIndex + "_" + slotIndex;
	    }

    private static String loreSocketButtonId(int equipmentIndex, int loreIndex) {
        return "loreSocket_" + equipmentIndex + "_" + loreIndex;
    }

	    private static String socketPreviewButtonId(int slotIndex) {
	        return "socketPreview_" + slotIndex;
	    }

    private static String socketPreviewWrapId(int slotIndex) {
        return "socketPreviewWrap_" + slotIndex;
    }

    private static String socketPreviewSelectedLayerId(int slotIndex) {
        return "socketPreviewSelected_" + slotIndex;
    }

    private static String socketPreviewColorLayerId(int slotIndex, String colorKey) {
        return "socketPreviewColor_" + slotIndex + "_" + colorKey;
    }

    private static String socketPreviewBackgroundId(int slotIndex, String colorKey) {
        return "socketPreviewBg_" + slotIndex + "_" + colorKey;
    }

    private static String socketPreviewOverlayId(int slotIndex, String colorKey) {
        return "socketPreviewOverlay_" + slotIndex + "_" + colorKey;
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

