package irai.mod.reforge.UI;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Set;

import org.bson.BsonDocument;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.PatchStyle;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Common.UI.UIInventoryUtils;
import irai.mod.reforge.Common.UI.UIItemUtils;
import irai.mod.reforge.Common.UI.UISocketVisualUtils;
import irai.mod.reforge.Common.CorruptionUtils;
import irai.mod.reforge.Config.SFXConfig;
import irai.mod.reforge.Interactions.ReforgeEquip;
import irai.mod.reforge.Progression.CharacterPathService;
import irai.mod.reforge.Progression.CharacterPathService.Bonus;
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
 * Native Hytale socket punching bench page and processing logic.
 */
public class SocketBenchUI {

    private static final String PUNCHER_ITEM_ID = "Socket_Puncher";
    private static final String NONE_SUPPORT_KEY = "__NONE_SUPPORT__";
    private static final String SUPPORT_KEY_DELIMITER = "|";
    private static final String NATIVE_PAGE_UI = "Pages/SocketPunchBench.ui";
    private static final String EQUIPMENT_CARD_UI = "Pages/SocketBenchEquipmentCard.ui";
    private static final String PUNCHER_CARD_UI = "Pages/SocketBenchPuncherCard.ui";
    private static final String SUPPORT_CARD_UI = "Pages/SocketBenchSupportCard.ui";
    private static final String SOCKET_TILE_UI = "Pages/SocketBenchSocketTile.ui";
    private static final String LORE_TILE_UI = "Pages/SocketBenchLoreTile.ui";

    private static final SFXConfig sfxConfig = new SFXConfig();

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

    public static void open(Player player) {
        if (player == null) {
            return;
        }
        PlayerRef playerRef = player.getPlayerRef();
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
            Ref<EntityStore> entityRef = playerRef.getReference();
            Store<EntityStore> store = entityRef == null ? null : entityRef.getStore();
            if (entityRef == null || !entityRef.isValid() || store == null) return;
            NativeSocketBenchPage page = new NativeSocketBenchPage(playerRef, snapshot, selectionState);
            player.getPageManager().openCustomPage(entityRef, store, page);
        } catch (Exception e) {
            System.err.println("[SocketReforge] Native socket bench open error: " + e.getMessage());
            e.printStackTrace();
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





    /** Reopen the active bench using the latest inventory and pending selection state. */










































































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







	    private static String keyOf(Entry entry) {
	        if (entry == null) return null;
	        return entry.selectionKey;
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











    private static String lorePreviewColorKey(LoreSocketData.LoreSocket socket) {
        return UISocketVisualUtils.loreColorKey(socket);
    }





    private static String resolveBrokenSocketIconName() {
        return UISocketVisualUtils.brokenSocketIconName();
    }

    private static String resolveSocketBackgroundIcon(boolean punched, boolean broken, String brokenIconName) {
        return UISocketVisualUtils.socketBackgroundIcon(punched, broken, brokenIconName);
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

        if (CorruptionUtils.isCorrupted(freshEquipment)) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.socket_bench.error_corrupted"), 0);
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
            PunchResult loreResult = SocketManager.rollPunchResult(loreCurrent, SupportMaterial.GHASTLY_ESSENCE,
                    CharacterPathService.bonus(player, Bonus.SOCKET_CHANCE));
            switch (loreResult) {
                case SUCCESS -> {
                    ItemStack loreUpdated = LoreSocketManager.punchRandomLoreSocket(freshEquipment, null);
                    if (loreUpdated == null || loreUpdated == freshEquipment) {
                        CharacterPathService.awardBenchXp(player, 15, "socketing", true);
                        return new ProcessResult(LangLoader.getUITranslation(player, "ui.socket_bench.error_lore_max", loreMax), 100);
                    }
                    writeUpdatedEquipment(player, equipment, loreUpdated, socketData);
                    sfxConfig.playSuccess(player);
                    CharacterPathService.awardBenchXp(player, 15, "socketing");
                    return new ProcessResult(LangLoader.getUITranslation(player, "ui.socket_bench.status_lore_success"), 100);
                }
                case BREAK -> {
                    sfxConfig.playShatter(player);
                    CharacterPathService.awardBenchXp(player, 15, "socketing", true);
                    return new ProcessResult(LangLoader.getUITranslation(player, "ui.socket_bench.status_broke"), 100);
                }
                case FAIL -> {
                    sfxConfig.playFail(player);
                    CharacterPathService.awardBenchXp(player, 15, "socketing", true);
                    return new ProcessResult(LangLoader.getUITranslation(player, "ui.socket_bench.status_failed"), 100);
                }
                default -> {
                    sfxConfig.playFail(player);
                    CharacterPathService.awardBenchXp(player, 15, "socketing", true);
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
                CharacterPathService.awardBenchXp(player, 15, "socketing");
                return new ProcessResult(withExpanderStatus(player,
                        LangLoader.getUITranslation(player, "ui.socket_bench.status_support_applied"),
                        expanderOutcome), 100);
            }
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.socket_bench.error_max_sockets", maxSockets), 100);
        }

        PunchResult result = rollPunchResult(player, socketData, usedSupportMaterials);

        switch (result) {
            case SUCCESS:
                boolean socketAdded = socketData.addSocket();
                if (!socketAdded) {
                    if (supportAdjusted) {
                        writeUpdatedEquipment(player, equipment, freshEquipment, socketData);
                        CharacterPathService.awardBenchXp(player, 15, "socketing");
                        return new ProcessResult(withExpanderStatus(player,
                                LangLoader.getUITranslation(player, "ui.socket_bench.status_support_applied"),
                                expanderOutcome), 100);
                    }
                    CharacterPathService.awardBenchXp(player, 15, "socketing", true);
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
                CharacterPathService.awardBenchXp(player, 15, "socketing");
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
                CharacterPathService.awardBenchXp(player, 15, "socketing", true);
                return new ProcessResult(withExpanderStatus(player,
                        LangLoader.getUITranslation(player, "ui.socket_bench.status_broke"),
                        expanderOutcome), 100);
            case FAIL:
            default:
                if (supportAdjusted) {
                    writeUpdatedEquipment(player, equipment, freshEquipment, socketData);
                }
                sfxConfig.playFail(player);
                CharacterPathService.awardBenchXp(player, 15, "socketing", true);
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

    private static PunchResult rollPunchResult(Player player, SocketData socketData, List<SupportMaterial> supportMaterials) {
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
        successChance = Math.min(1.0d, successChance
                + CharacterPathService.bonus(player, Bonus.SOCKET_CHANCE));
        double roll = Math.random();
        if (roll < breakChance) {
            return PunchResult.BREAK;
        }
        if (roll < breakChance + successChance) {
            return PunchResult.SUCCESS;
        }
        return PunchResult.FAIL;
    }



	    private static Entry resolveSelection(List<Entry> entries, String selectedValue) {
        Entry byKey = findByKey(entries, selectedValue);
        if (byKey != null) {
            return byKey;
        }
	        return UIInventoryUtils.resolveIndex(entries, selectedValue);
	    }

    private static List<Entry> resolveSupportSelections(List<Entry> entries, String selectedValue) {
        if (selectedValue == null || selectedValue.isBlank() || NONE_SUPPORT_KEY.equals(selectedValue) || "-1".equals(selectedValue)) {
            return List.of();
        }
        List<Entry> byKeys = findAllByKeys(entries, selectedValue);
        if (!byKeys.isEmpty()) {
            return byKeys;
        }
        Entry byIndex = UIInventoryUtils.resolveIndex(entries, selectedValue);
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









    private static StatPreview calculatePreview(Player player, BenchSnapshot snapshot, String equipmentValue, String supportValue) {
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
            double breakChance = SocketManager.getConfig() != null ? SocketManager.getConfig().getBreakChance(loreCurrent) : 0.10;
            double success = SocketManager.getConfig() != null ? SocketManager.getConfig().getSuccessChance(loreCurrent) : 0.75;
            success = Math.min(1.0d, success
                    + CharacterPathService.bonus(player, Bonus.SOCKET_CHANCE));
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
        success = Math.min(1.0d, success
                + CharacterPathService.bonus(player, Bonus.SOCKET_CHANCE));

        String successText = String.format(java.util.Locale.ROOT, "%.0f%%", success * 100.0);
        String breakText = String.format(java.util.Locale.ROOT, "%.0f%%", breakChance * 100.0);
        String socketsText = previewCurrent + " / " + previewMax;
        return new StatPreview(successText, breakText, socketsText);
    }

    private static PatchStyle textureBackground(String texturePath) {
        return new PatchStyle(Value.of(texturePath), Value.of(0));
    }

    public static void closeForDisconnect(PlayerRef playerRef) {
        if (playerRef == null) {
            return;
        }
        cleanupPlayerState(playerRef);
    }



    private static void cleanupPlayerState(PlayerRef playerRef) {
        pendingSelections.remove(playerRef);
        processingPlayers.remove(playerRef);
    }



    /** Native Custom UI page for socket punching. */
    private static final class NativeSocketBenchPage extends InteractiveCustomUIPage<NativeSocketBenchPage.PageEvent> {
        private final PlayerRef owner;
        private BenchSnapshot snapshot;
        private SelectionState state;
        private volatile boolean dismissed;

        NativeSocketBenchPage(PlayerRef owner, BenchSnapshot snapshot, SelectionState state) {
            super(owner, CustomPageLifetime.CanDismiss, PageEvent.CODEC);
            this.owner = owner;
            this.snapshot = snapshot;
            this.state = normalizeSelectionState(snapshot, state);
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
            if (Boolean.TRUE.equals(processingPlayers.get(owner)) && !"process".equals(event.action)) return;
            if (state == null) state = normalizeSelectionState(snapshot, null);
            switch (event.action) {
                case "select_equipment" -> state = new SelectionState(event.target, state.puncherKey,
                        state.supportKey, null, 0, false);
                case "select_puncher" -> state = new SelectionState(state.equipmentKey, event.target,
                        state.supportKey, null, 0, false);
                case "select_support" -> state = new SelectionState(state.equipmentKey, state.puncherKey,
                        toggleSupportKey(state.supportKey, event.target), null, 0, false);
                case "clear_supports" -> state = new SelectionState(state.equipmentKey, state.puncherKey,
                        NONE_SUPPORT_KEY, null, 0, false);
                case "process" -> {
                    startProcess(player);
                    return;
                }
                default -> { return; }
            }
            refresh(player);
        }

        @Override
        public void onDismiss(Ref<EntityStore> playerRef, Store<EntityStore> store) {
            dismissed = true;
            processingPlayers.remove(owner);
            pendingSelections.remove(owner);
            super.onDismiss(playerRef, store);
        }

        private void refresh(Player player) {
            if (dismissed) return;
            snapshot = collectSnapshot(player);
            state = normalizeSelectionState(snapshot, state);
            pendingSelections.put(owner, state);
            UICommandBuilder commands = new UICommandBuilder();
            UIEventBuilder events = new UIEventBuilder();
            populate(player, commands, events, true);
            sendUpdate(commands, events, false);
        }

        private void populate(Player player, UICommandBuilder commands,
                              UIEventBuilder events, boolean replaceContents) {
            if (snapshot == null) snapshot = collectSnapshot(player);
            state = normalizeSelectionState(snapshot, state);
            Entry equipment = findByKey(snapshot.equipments, state.equipmentKey);
            Entry puncher = findByKey(snapshot.punchers, state.puncherKey);
            List<Entry> supports = findAllByKeys(snapshot.supports, state.supportKey);
            StatPreview preview = calculatePreview(player, snapshot, state.equipmentKey, state.supportKey);
            boolean maxed = isSocketsMaxed(equipment) && !supportOverridesMax(supports);
            boolean processing = state.processing;
            String status = state.statusText == null
                    ? LangLoader.getUITranslation(player, maxed
                            ? "ui.socket_bench.status_max_sockets" : "ui.socket_bench.status_idle")
                    : state.statusText;
            if (!processing && state.statusText == null && equipment == null) {
                status = LangLoader.getUITranslation(player, "ui.socket_bench.prompt_select_equipment");
            }
            int progress = processing ? Math.max(0, Math.min(100, state.progressValue)) : 0;
            state = new SelectionState(state.equipmentKey, state.puncherKey, state.supportKey,
                    status, progress, processing);
            pendingSelections.put(owner, state);

            commands.set("#SocketBenchTitle.Text", LangLoader.getUITranslation(player, "ui.socket_bench.tool_title"));
            commands.set("#SocketBenchSubtitle.Text", LangLoader.getUITranslation(player, "ui.socket_bench.subtitle"));
            commands.set("#EquipmentPanelTitle.Text", LangLoader.getUITranslation(player, "ui.socket_bench.equipment"));
            commands.set("#EquipmentHint.Text", LangLoader.getUITranslation(player, "ui.socket_bench.option_select_equipment"));
            commands.set("#SelectedEquipmentName.Text", equipment == null
                    ? LangLoader.getUITranslation(player, "ui.socket_bench.option_select_equipment")
                    : equipment.displayName);
            commands.set("#SelectedEquipmentLevel.Text", equipment == null ? "-"
                    : (ReforgeEquip.isWeapon(equipment.item) ? LangLoader.getUITranslation(player, "ui.socket_bench.type_weapon")
                    : LangLoader.getUITranslation(player, "ui.socket_bench.type_armor"))
                    + "  |  " + LangLoader.getUITranslation(player, "ui.socket_bench.sockets_label") + " "
                    + preview.socketsText);
            commands.set("#SelectedEquipmentIcon.ItemId", equipment == null ? "" : equipment.itemId);
            commands.set("#SelectedEquipmentIcon.Visible", equipment != null);
            commands.set("#PuncherTitle.Text", LangLoader.getUITranslation(player, "ui.socket_bench.puncher"));
            commands.set("#SupportPanelTitle.Text", LangLoader.getUITranslation(player, "ui.socket_bench.support"));
            commands.set("#SupportHint.Text", LangLoader.getUITranslation(player, "ui.socket_bench.support_hint"));
            commands.set("#SupportFooter.Text", LangLoader.getUITranslation(player, "ui.socket_bench.support_click_hint"));
            commands.set("#SocketPreviewTitle.Text", LangLoader.getUITranslation(player, "ui.socket_bench.socket_preview"));
            commands.set("#LorePreviewTitle.Text", LangLoader.getUITranslation(player, "ui.socket_bench.lore_socket_preview"));
            commands.set("#SocketSuccessLabel.Text", LangLoader.getUITranslation(player, "ui.socket_bench.success_label"));
            commands.set("#SocketBreakLabel.Text", LangLoader.getUITranslation(player, "ui.socket_bench.break_label"));
            commands.set("#SocketCountLabel.Text", LangLoader.getUITranslation(player, "ui.socket_bench.sockets_label"));
            commands.set("#SocketSuccessValue.Text", preview.successText);
            commands.set("#SocketBreakValue.Text", preview.breakText);
            commands.set("#SocketCountValue.Text", preview.socketsText);
            commands.set("#SocketProgressTitle.Text", LangLoader.getUITranslation(player, "ui.socket_bench.progress"));
            commands.set("#SocketStatus.Text", status);
            commands.set("#SocketBusy.Text", LangLoader.getUITranslation(player, "ui.socket_bench.status_processing"));
            commands.set("#SocketProgress.Value", progress / 100.0f);
            commands.set("#SocketProgressPercent.Text", progress + "%");
            commands.set("#SocketFailureNote.Text", LangLoader.getUITranslation(player, "ui.socket_bench.failure_note"));
            commands.set("#SocketProcessButton.Text", LangLoader.getUITranslation(player, "ui.socket_bench.process_button"));

            boolean canProcess = !processing && equipment != null && puncher != null && !maxed;
            commands.set("#SocketProcessButton.Visible", canProcess);
            commands.set("#SocketUnavailable.Visible", !canProcess && !processing);
            commands.set("#SocketBusy.Visible", processing);
            commands.set("#SocketUnavailable.Text", equipment == null
                    ? LangLoader.getUITranslation(player, "ui.socket_bench.prompt_select_equipment")
                    : puncher == null ? LangLoader.getUITranslation(player, "ui.socket_bench.error_no_puncher")
                    : LangLoader.getUITranslation(player, "ui.socket_bench.status_max_sockets"));
            events.addEventBinding(CustomUIEventBindingType.Activating, "#SocketProcessButton",
                    EventData.of("Action", "process"));

            if (replaceContents) {
                commands.clear("#EquipmentList");
                commands.clear("#PuncherList");
                commands.clear("#SupportList");
                commands.clear("#SocketPreviewTiles");
                commands.clear("#LorePreviewTiles");
            }
            appendEquipmentCards(commands, events, player, equipment);
            appendPuncherCards(commands, events, player, puncher);
            appendSupportCards(commands, events, player, supports);
            appendSocketTiles(commands, equipment);
            appendLoreTiles(commands, equipment);
        }

        private void appendEquipmentCards(UICommandBuilder commands, UIEventBuilder events,
                                          Player player, Entry selected) {
            if (snapshot.equipments.isEmpty()) {
                commands.set("#EquipmentEmpty.Text", LangLoader.getUITranslation(player, "ui.socket_bench.option_no_equipment"));
                commands.set("#EquipmentEmpty.Visible", true);
                return;
            }
            commands.set("#EquipmentEmpty.Visible", false);
            for (int i = 0; i < snapshot.equipments.size(); i++) {
                Entry entry = snapshot.equipments.get(i);
                if (entry == null || entry.item == null || entry.item.isEmpty()) continue;
                commands.append("#EquipmentList", EQUIPMENT_CARD_UI);
                String base = child("#EquipmentList", i);
                int level = ReforgeEquip.getLevelFromItem(entry.item);
                commands.set(base + " #EquipmentIcon.ItemId", entry.itemId);
                commands.set(base + " #EquipmentName.Text", entry.displayName);
                commands.set(base + " #EquipmentTier.Text", (ReforgeEquip.isWeapon(entry.item)
                        ? LangLoader.getUITranslation(player, "ui.socket_bench.type_weapon")
                        : LangLoader.getUITranslation(player, "ui.socket_bench.type_armor")) + "  |  +" + level);
                commands.set(base + " #EquipmentSelected.Visible", selected != null
                        && keyOf(selected).equals(keyOf(entry)));
                events.addEventBinding(CustomUIEventBindingType.Activating, base + " #EquipmentCardHit",
                        EventData.of("Action", "select_equipment").append("Target", keyOf(entry)));
            }
            commands.set("#EquipmentList.ContentHeight", Math.max(700, snapshot.equipments.size() * 82));
        }

        private void appendPuncherCards(UICommandBuilder commands, UIEventBuilder events,
                                        Player player, Entry selected) {
            if (snapshot.punchers.isEmpty()) {
                commands.set("#PuncherEmpty.Text", LangLoader.getUITranslation(player, "ui.socket_bench.option_no_puncher"));
                commands.set("#PuncherEmpty.Visible", true);
            } else {
                commands.set("#PuncherEmpty.Visible", false);
            }
            for (int i = 0; i < snapshot.punchers.size(); i++) {
                Entry entry = snapshot.punchers.get(i);
                if (entry == null || entry.item == null || entry.item.isEmpty()) continue;
                commands.append("#PuncherList", PUNCHER_CARD_UI);
                String base = child("#PuncherList", i);
                commands.set(base + " #PuncherIcon.ItemId", entry.itemId);
                commands.set(base + " #PuncherName.Text", entry.displayName);
                commands.set(base + " #PuncherQuantity.Text", "x" + entry.quantity);
                commands.set(base + " #PuncherSelected.Visible", selected != null
                        && keyOf(selected).equals(keyOf(entry)));
                events.addEventBinding(CustomUIEventBindingType.Activating, base + " #PuncherCardHit",
                        EventData.of("Action", "select_puncher").append("Target", keyOf(entry)));
            }
            commands.set("#PuncherList.ContentWidth", Math.max(190, snapshot.punchers.size() * 196));
        }

        private void appendSupportCards(UICommandBuilder commands, UIEventBuilder events,
                                        Player player, List<Entry> selected) {
            Set<String> selectedKeys = new java.util.HashSet<>();
            for (Entry entry : selected) selectedKeys.add(keyOf(entry));
            commands.append("#SupportList", "Pages/SocketBenchSupportNoneCard.ui");
            String noneCard = child("#SupportList", 0);
            commands.set(noneCard + " #SupportNoneSelected.Visible", selected.isEmpty());
            commands.set(noneCard + " #SupportNoneText.Text", LangLoader.getUITranslation(player, "ui.socket_bench.option_none"));
            events.addEventBinding(CustomUIEventBindingType.Activating, noneCard + " #SupportNoneHit",
                    EventData.of("Action", "clear_supports"));
            int height = 68;
            for (int i = 0; i < snapshot.supports.size(); i++) {
                Entry entry = snapshot.supports.get(i);
                if (entry == null || entry.item == null || entry.item.isEmpty()) continue;
                commands.append("#SupportList", SUPPORT_CARD_UI);
                String base = child("#SupportList", i + 1);
                commands.set(base + " #SupportIcon.ItemId", entry.itemId);
                commands.set(base + " #SupportName.Text", entry.displayName);
                commands.set(base + " #SupportDescription.Text", supportDescription(entry));
                commands.set(base + " #SupportQuantity.Text", "Held: " + entry.quantity);
                commands.set(base + " #SupportSelected.Visible", selectedKeys.contains(keyOf(entry)));
                events.addEventBinding(CustomUIEventBindingType.Activating, base + " #SupportCardHit",
                        EventData.of("Action", "select_support").append("Target", keyOf(entry)));
                height += 98;
            }
            commands.set("#SupportList.ContentHeight", Math.max(620, height));
        }

        private void appendSocketTiles(UICommandBuilder commands, Entry equipment) {
            SocketData data = equipment == null ? null : SocketManager.getSocketData(equipment.item);
            if (equipment != null && data == null) {
                data = SocketData.fromDefaults(ReforgeEquip.isWeapon(equipment.item) ? "weapon" : "armor");
            }
            List<Socket> sockets = data == null ? List.of() : data.getSockets();
            int punched = data == null ? 0 : data.getCurrentSocketCount();
            String brokenIcon = resolveBrokenSocketIconName();
            for (int i = 0; i < ESSENCE_PREVIEW_SLOTS; i++) {
                Socket socket = i < sockets.size() ? sockets.get(i) : null;
                boolean broken = socket != null && socket.isBroken();
                boolean filled = socket != null && !socket.isBroken() && !socket.isEmpty();
                boolean punchedSlot = i < punched;
                String background = resolveSocketBackgroundIcon(punchedSlot, broken, brokenIcon);
                commands.append("#SocketPreviewTiles", SOCKET_TILE_UI);
                String base = child("#SocketPreviewTiles", i);
                // UICommandBuilder texture paths resolve from Common/UI/Custom, not from Pages/.
                commands.setObject(base + " #SocketVisual.Background", textureBackground(background));
                String essenceId = filled ? UISocketVisualUtils.essenceItemId(socket.getEssenceId()) : null;
                commands.set(base + " #SocketEssence.ItemId", essenceId == null ? "" : essenceId);
                commands.set(base + " #SocketEssence.Visible", essenceId != null);
            }
            commands.set("#SocketPreviewTiles.ContentWidth", ESSENCE_PREVIEW_SLOTS * 80);
        }

        private void appendLoreTiles(UICommandBuilder commands, Entry equipment) {
            LoreSocketData data = equipment == null ? null : LoreSocketManager.getLoreSocketData(equipment.item);
            List<LoreSocketData.LoreSocket> sockets = data == null ? List.of() : data.getSockets();
            for (int i = 0; i < LORE_PREVIEW_SLOTS; i++) {
                LoreSocketData.LoreSocket socket = i < sockets.size() ? sockets.get(i) : null;
                commands.append("#LorePreviewTiles", LORE_TILE_UI);
                String base = child("#LorePreviewTiles", i);
                    String itemId = UISocketVisualUtils.loreSocketItemId(socket);
                commands.set(base + " #LoreSocketIcon.ItemId", itemId == null ? "" : itemId);
                commands.set(base + " #LoreSocketIcon.Visible", itemId != null);
                String colorKey = lorePreviewColorKey(socket);
                for (String[] color : LORE_PREVIEW_COLORS) {
                    String colorName = Character.toUpperCase(color[0].charAt(0)) + color[0].substring(1);
                    commands.set(base + " #LoreSocketColor" + colorName + ".Visible",
                            color[0].equals(colorKey));
                }
            }
            commands.set("#LorePreviewTiles.ContentWidth", LORE_PREVIEW_SLOTS * 80);
        }

        private void startProcess(Player player) {
            if (dismissed || Boolean.TRUE.equals(processingPlayers.get(owner))) return;
            snapshot = collectSnapshot(player);
            state = normalizeSelectionState(snapshot, state);
            Entry equipment = findByKey(snapshot.equipments, state.equipmentKey);
            Entry puncher = findByKey(snapshot.punchers, state.puncherKey);
            List<Entry> supports = findAllByKeys(snapshot.supports, state.supportKey);
            if (equipment == null || puncher == null
                    || (isSocketsMaxed(equipment) && !supportOverridesMax(supports))) {
                refresh(player);
                return;
            }
            processingPlayers.put(owner, true);
            state = new SelectionState(keyOf(equipment), keyOf(puncher),
                    supportSelectionKey(state.supportKey, supports),
                    LangLoader.getUITranslation(player, "ui.socket_bench.status_processing"), 0, true);
            sfxConfig.playReforgeStart(player);
            refresh(player);

            for (int elapsed = PROGRESS_TICK_MS; elapsed < PROCESS_DURATION_MS; elapsed += PROGRESS_TICK_MS) {
                final int delay = elapsed;
                final int progress = Math.min(99,
                        (int) Math.round(((delay + PROGRESS_TICK_MS) * 100.0) / PROCESS_DURATION_MS));
                processScheduler.schedule(() -> player.getWorld().execute(() -> updateProgress(player, progress)),
                        delay, TimeUnit.MILLISECONDS);
            }
            processScheduler.schedule(() -> player.getWorld().execute(() -> {
                try {
                    if (dismissed || !Boolean.TRUE.equals(processingPlayers.get(owner))) return;
                    ProcessResult result = processSelection(player, snapshot, equipment, puncher, supports);
                    state = new SelectionState(keyOf(equipment), keyOf(puncher), state.supportKey,
                            result.statusLine, result.progressValue, false);
                } catch (Exception e) {
                    System.err.println("[SocketReforge] Native socket bench process failed: " + e.getMessage());
                    e.printStackTrace();
                    state = new SelectionState(state.equipmentKey, state.puncherKey, state.supportKey,
                            LangLoader.getUITranslation(player, "ui.socket_bench.status_failed"), 0, false);
                } finally {
                    processingPlayers.remove(owner);
                    if (!dismissed) refresh(player);
                }
            }), PROCESS_DURATION_MS, TimeUnit.MILLISECONDS);
        }

        private void updateProgress(Player player, int progress) {
            if (dismissed || !Boolean.TRUE.equals(processingPlayers.get(owner))) return;
            state = new SelectionState(state.equipmentKey, state.puncherKey, state.supportKey,
                    LangLoader.getUITranslation(player, "ui.socket_bench.status_processing"), progress, true);
            pendingSelections.put(owner, state);
            UICommandBuilder commands = new UICommandBuilder();
            commands.set("#SocketProgress.Value", progress / 100.0f);
            commands.set("#SocketProgressPercent.Text", progress + "%");
            sendUpdate(commands, false);
        }

        private static String child(String parent, int index) {
            return parent + "[" + index + "]";
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
