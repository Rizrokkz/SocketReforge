package irai.mod.reforge.UI;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import irai.mod.reforge.Common.UI.HyUIReflectionUtils;
import irai.mod.reforge.Common.UI.UIItemUtils;
import irai.mod.reforge.Common.UI.UITemplateUtils;
import irai.mod.reforge.Lore.LoreSocketData;
import irai.mod.reforge.Lore.LoreSocketManager;
import irai.mod.reforge.Lore.LoreAbility;
import irai.mod.reforge.Lore.LoreAbilityRegistry;
import irai.mod.reforge.Lore.LoreEffectType;
import irai.mod.reforge.Util.LangLoader;

/**
 * HyUI lore feed page opened through command.
 * Uses reflection so the plugin still runs when HyUI is not present.
 */
public final class LoreFeedBenchUI {
    private LoreFeedBenchUI() {}

    private static final int OMNISLASH_BASE_HITS = 2;
    private static final int OMNISLASH_MAX_HITS = 8;
    private static final int OCTASLASH_BASE_HITS = 2;
    private static final int OCTASLASH_MAX_HITS = 8;
    private static final int PUMMEL_BASE_HITS = 2;
    private static final int PUMMEL_MAX_HITS = 3;
    private static final int BLOOD_RUSH_BASE_HITS = 3;
    private static final int BLOOD_RUSH_MAX_HITS = 6;
    private static final int CHARGE_ATTACK_BASE_HITS = 1;
    private static final int CHARGE_ATTACK_MAX_HITS = 3;

    private static final String HYUI_PAGE_BUILDER = "au.ellie.hyui.builders.PageBuilder";
    private static final String HYUI_PLUGIN = "au.ellie.hyui.HyUIPlugin";
    private static final String HYUI_EVENT_BINDING = "com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType";
    private static final String UI_COMMAND_BUILDER = "com.hypixel.hytale.server.core.ui.builder.UICommandBuilder";
    private static final String TEMPLATE_PATH = "Common/UI/Custom/Pages/LoreFeedBench.html";

    private static boolean hyuiAvailable = false;
    private static final Map<PlayerRef, Object> openPages = new ConcurrentHashMap<>();
    private static final Map<PlayerRef, SelectionState> pendingSelections = new ConcurrentHashMap<>();

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

    private static final class FeedInfo {
        final String itemId;
        final String displayName;
        final int quantity;

        FeedInfo(String itemId, String displayName, int quantity) {
            this.itemId = itemId;
            this.displayName = displayName;
            this.quantity = quantity;
        }
    }

    private static final class Snapshot {
        final List<Entry> equipments;
        final List<FeedInfo> feedItems;

        Snapshot(List<Entry> equipments, List<FeedInfo> feedItems) {
            this.equipments = equipments;
            this.feedItems = feedItems;
        }
    }

    private static final class SelectionState {
        final String equipmentKey;
        final String slotKey;
        final String statusText;

        SelectionState(String equipmentKey, String slotKey, String statusText) {
            this.equipmentKey = equipmentKey;
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

    private static final class FeedInfoBuilder {
        String itemId;
        String displayName;
        int quantity;
    }

    public static void initialize() {
        hyuiAvailable = HyUIReflectionUtils.detectHyUi(HYUI_PAGE_BUILDER, HYUI_PLUGIN, "LoreFeedBenchUI");
    }

    public static boolean isAvailable() {
        return hyuiAvailable;
    }

    public static void open(Player player) {
        if (player == null) {
            return;
        }
        if (!hyuiAvailable) {
            player.sendMessage(Message.raw("<color=#FF5555>" + LangLoader.getUITranslation(player, "ui.lore_feed.hyui_missing")));
            return;
        }
        PlayerRef playerRef = player.getPlayerRef();
        closePageIfOpen(playerRef);
        pendingSelections.remove(playerRef);
        player.getWorld().execute(() -> openWithSync(player));
    }

    private static void openWithSync(Player player) {
        Snapshot snapshot = collectSnapshot(player);
        SelectionState selectionState = pendingSelections.remove(player.getPlayerRef());
        openPage(player, snapshot, selectionState);
    }

    private static Snapshot collectSnapshot(Player player) {
        List<Entry> equipments = new ArrayList<>();
        collectFromContainer(player, player.getInventory().getHotbar(), ContainerKind.HOTBAR, equipments);
        collectFromContainer(player, player.getInventory().getStorage(), ContainerKind.STORAGE, equipments);
        List<FeedInfo> feedItems = collectFeedInfo(player);
        return new Snapshot(equipments, feedItems);
    }

    private static void collectFromContainer(Player player,
                                             ItemContainer container,
                                             ContainerKind kind,
                                             List<Entry> equipments) {
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
        }
    }

    private static List<FeedInfo> collectFeedInfo(Player player) {
        String[] feedIds = LoreSocketManager.getConfig().getFeedItemIds();
        Map<String, FeedInfoBuilder> map = new LinkedHashMap<>();
        if (feedIds != null) {
            for (String feedId : feedIds) {
                if (feedId == null || feedId.isBlank()) {
                    continue;
                }
                String key = normalizeKey(feedId);
                FeedInfoBuilder builder = map.get(key);
                if (builder == null) {
                    builder = new FeedInfoBuilder();
                    builder.itemId = feedId.trim();
                    builder.displayName = feedId.trim();
                    builder.quantity = 0;
                    map.put(key, builder);
                }
            }
        }
        if (map.isEmpty() || player == null || player.getInventory() == null) {
            return new ArrayList<>();
        }
        collectFeedInfoFromContainer(player, player.getInventory().getHotbar(), map);
        collectFeedInfoFromContainer(player, player.getInventory().getStorage(), map);
        List<FeedInfo> feedItems = new ArrayList<>();
        for (FeedInfoBuilder builder : map.values()) {
            String display = builder.displayName == null || builder.displayName.isBlank()
                    ? builder.itemId
                    : builder.displayName;
            feedItems.add(new FeedInfo(builder.itemId, display, builder.quantity));
        }
        return feedItems;
    }

    private static void collectFeedInfoFromContainer(Player player,
                                                     ItemContainer container,
                                                     Map<String, FeedInfoBuilder> map) {
        if (container == null || map == null || map.isEmpty()) {
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
            String key = normalizeKey(itemId);
            FeedInfoBuilder builder = map.get(key);
            if (builder == null) {
                continue;
            }
            builder.quantity += Math.max(0, stack.getQuantity());
            if (builder.displayName == null || builder.displayName.equals(builder.itemId)) {
                builder.displayName = UIItemUtils.displayNameOrItemId(stack, player);
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
                        String slotVal = getContextValue(ctxObj, "slotDropdown", "#slotDropdown.value");
                        pendingSelections.put(finalPlayer.getPlayerRef(),
                                new SelectionState(equipmentVal, slotVal, null));
                        finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));
                    });

            addListener.invoke(pageBuilder, "slotDropdown", valueChanged,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        String equipmentVal = getContextValue(ctxObj, "equipmentDropdown", "#equipmentDropdown.value");
                        String slotVal = extractEventValue(eventObj);
                        pendingSelections.put(finalPlayer.getPlayerRef(),
                                new SelectionState(equipmentVal, slotVal, null));
                        finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));
                    });

            addListener.invoke(pageBuilder, "processButton", activating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        String equipmentVal = getContextValue(ctxObj, "equipmentDropdown", "#equipmentDropdown.value");
                        String slotVal = getContextValue(ctxObj, "slotDropdown", "#slotDropdown.value");
                        Entry equipment = resolveSelection(finalSnapshot.equipments, equipmentVal);

                        ProcessResult result = processSelection(finalPlayer, equipment, slotVal);
                        pendingSelections.put(finalPlayer.getPlayerRef(),
                                new SelectionState(equipmentVal, slotVal, result.status));
                        finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));
                    });

            pageBuilder = withLifetime.invoke(pageBuilder, CustomPageLifetime.CanDismiss);
            Object store = getStore(playerRef);
            Object page = openMethod.invoke(pageBuilder, store);
            openPages.put(playerRef, page);
        } catch (Exception e) {
            System.err.println("[SocketReforge] LoreFeedBenchUI open error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String buildHtml(Player player, Snapshot snapshot, SelectionState state) {
        String equipmentKey = state != null ? state.equipmentKey : null;
        String slotKey = state != null ? state.slotKey : null;
        String status = state != null && state.statusText != null
                ? state.statusText
                : LangLoader.getUITranslation(player, "ui.lore_feed.status_idle");

        Entry selectedEquipment = findByKey(snapshot.equipments, equipmentKey);

        String html = loadTemplate();
        html = html.replace("{{equipmentOptions}}",
                buildOptions(snapshot.equipments,
                        LangLoader.getUITranslation(player, "ui.lore_feed.option_no_equipment"),
                        equipmentKey));
        html = html.replace("{{slotOptions}}", buildSlotOptions(player, selectedEquipment, slotKey));
        html = html.replace("{{statusText}}", escapeHtml(status));
        html = html.replace("{{spiritPreview}}", escapeHtml(buildSpiritPreview(player, selectedEquipment, slotKey)));
        html = html.replace("{{progressBars}}", buildProgressBars(player, selectedEquipment));
        html = html.replace("{{processDisabledAttr}}", shouldDisable(selectedEquipment) ? "disabled=\"true\"" : "");
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

    private static String buildSlotOptions(Player player, Entry equipment, String selectedKey) {
        StringBuilder sb = new StringBuilder();
        sb.append("<option value=\"auto\"");
        if (selectedKey == null || selectedKey.isBlank() || "auto".equalsIgnoreCase(selectedKey)) {
            sb.append(" selected=\"true\"");
        }
        sb.append(">").append(escapeHtml(LangLoader.getUITranslation(player, "ui.lore_feed.option_auto"))).append("</option>");

        if (equipment == null) {
            sb.append("<option value=\"\" selected=\"true\">")
                    .append(escapeHtml(LangLoader.getUITranslation(player, "ui.lore_feed.option_no_equipment")))
                    .append("</option>");
            return sb.toString();
        }

        LoreSocketData data = LoreSocketManager.getLoreSocketData(equipment.item);
        if (data == null || data.getSocketCount() == 0) {
            sb.append("<option value=\"\" selected=\"true\">")
                    .append(escapeHtml(LangLoader.getUITranslation(player, "ui.lore_feed.option_no_sockets")))
                    .append("</option>");
            return sb.toString();
        }

        for (int i = 0; i < data.getSocketCount(); i++) {
            LoreSocketData.LoreSocket socket = data.getSocket(i);
            String key = String.valueOf(i);
            String label = buildSlotLabel(player, socket, i + 1);
            sb.append("<option value=\"").append(key).append("\"");
            if (key.equals(selectedKey)) {
                sb.append(" selected=\"true\"");
            }
            sb.append(">").append(escapeHtml(label)).append("</option>");
        }
        return sb.toString();
    }

    private static String buildSlotLabel(Player player, LoreSocketData.LoreSocket socket, int index) {
        if (socket == null) {
            return LangLoader.getUITranslation(player, "ui.lore_feed.slot_unavailable", index);
        }
        String baseKey;
        if (socket.isEmpty()) {
            baseKey = "ui.lore_feed.slot_empty";
        } else if (!socket.hasSpirit()) {
            baseKey = "ui.lore_feed.slot_no_spirit";
        } else if (LoreSocketManager.needsFeed(socket)) {
            baseKey = "ui.lore_feed.slot_ready";
        } else {
            baseKey = "ui.lore_feed.slot_not_ready";
        }
        String base = LangLoader.getUITranslation(player, baseKey, index);
        String color = socket.getColor();
        if (color == null || color.isBlank()) {
            color = LangLoader.getUITranslation(player, "ui.lore_feed.color_unknown");
        }
        if (socket.hasSpirit()) {
            String spiritName = localizeSpiritName(player, socket.getSpiritId());
            String level = LangLoader.getUITranslation(player, "ui.lore_feed.level_label", socket.getLevel());
            if (spiritName == null || spiritName.isBlank()) {
                return base + " " + level + " (" + color + ")";
            }
            return base + " " + spiritName + " " + level + " (" + color + ")";
        }
        return base + " (" + color + ")";
    }

    private static String buildMetadata(Player player, Entry equipment, String slotKey, List<FeedInfo> feedItems) {
        if (equipment == null) {
            return LangLoader.getUITranslation(player, "ui.lore_feed.metadata_no_equipment");
        }
        StringBuilder sb = new StringBuilder();
        sb.append(LangLoader.getUITranslation(player, "ui.lore_feed.metadata_header_item")).append("\n");
        sb.append(LangLoader.getUITranslation(player, "ui.lore_feed.metadata_name", equipment.displayName)).append("\n");
        sb.append(LangLoader.getUITranslation(player, "ui.lore_feed.metadata_id", equipment.itemId)).append("\n");
        String location = equipment.kind == ContainerKind.HOTBAR
                ? LangLoader.getUITranslation(player, "ui.lore_feed.metadata_hotbar")
                : LangLoader.getUITranslation(player, "ui.lore_feed.metadata_storage");
        sb.append(LangLoader.getUITranslation(player, "ui.lore_feed.metadata_inventory", location, equipment.slot))
                .append("\n");

        LoreSocketData data = LoreSocketManager.getLoreSocketData(equipment.item);
        if (data == null || data.getSocketCount() == 0) {
            sb.append(LangLoader.getUITranslation(player, "ui.lore_feed.metadata_no_sockets"));
            return sb.toString();
        }

        sb.append(LangLoader.getUITranslation(player, "ui.lore_feed.metadata_header_sockets")).append("\n");
        for (int i = 0; i < data.getSocketCount(); i++) {
            LoreSocketData.LoreSocket socket = data.getSocket(i);
            String state = socketStateLabel(player, socket);
            sb.append(LangLoader.getUITranslation(player, "ui.lore_feed.metadata_socket_line", i + 1, state))
                    .append("\n");
        }

        int slotIndex = resolveSlotIndexForDisplay(data, slotKey);
        if (slotIndex >= 0 && slotIndex < data.getSocketCount()) {
            LoreSocketData.LoreSocket socket = data.getSocket(slotIndex);
            sb.append("\n").append(LangLoader.getUITranslation(player, "ui.lore_feed.metadata_header_feed")).append("\n");
            if (socket != null) {
                String spirit = socket.getSpiritId();
                if (spirit == null || spirit.isBlank()) {
                    spirit = LangLoader.getUITranslation(player, "ui.lore_feed.value_none");
                } else {
                    String localized = localizeSpiritName(player, spirit);
                    if (localized != null && !localized.isBlank()) {
                        spirit = localized;
                    }
                }
                sb.append(LangLoader.getUITranslation(player, "ui.lore_feed.spirit_label", spirit)).append("\n");
                sb.append(LangLoader.getUITranslation(player, "ui.lore_feed.level_label", socket.getLevel())).append("\n");
                sb.append(LangLoader.getUITranslation(player, "ui.lore_feed.feed_tier", socket.getFeedTier())).append("\n");
                boolean needsFeed = LoreSocketManager.needsFeed(socket);
                String yes = LangLoader.getUITranslation(player, "ui.lore_feed.value_yes");
                String no = LangLoader.getUITranslation(player, "ui.lore_feed.value_no");
                sb.append(LangLoader.getUITranslation(player, "ui.lore_feed.feed_required", needsFeed ? yes : no)).append("\n");
                if (needsFeed) {
                    sb.append(LangLoader.getUITranslation(player, "ui.lore_feed.feed_cost", LoreSocketManager.getFeedCost(socket))).append("\n");
                }
                appendScalingStats(sb, player, socket);
            }
            sb.append(LangLoader.getUITranslation(player, "ui.lore_feed.feed_items", buildFeedSummary(player, feedItems)));
            String clearInfo = buildClearSummary(player);
            if (clearInfo != null && !clearInfo.isBlank()) {
                sb.append("\n").append(clearInfo);
            }
        }

        return sb.toString().trim();
    }

    private static String buildSpiritPreview(Player player, Entry equipment, String slotKey) {
        if (player == null) {
            return "";
        }
        if (equipment == null) {
            return LangLoader.getUITranslation(player, "ui.lore_feed.preview_no_equipment");
        }
        LoreSocketData data = LoreSocketManager.getLoreSocketData(equipment.item);
        if (data == null || data.getSocketCount() == 0) {
            return LangLoader.getUITranslation(player, "ui.lore_feed.preview_no_sockets");
        }
        int slotIndex = resolveSlotIndexForDisplay(data, slotKey);
        if (slotIndex < 0 || slotIndex >= data.getSocketCount()) {
            return LangLoader.getUITranslation(player, "ui.lore_feed.preview_no_spirit");
        }
        LoreSocketData.LoreSocket socket = data.getSocket(slotIndex);
        if (socket == null || !socket.hasSpirit()) {
            return LangLoader.getUITranslation(player, "ui.lore_feed.preview_no_spirit");
        }
        String spiritId = socket.getSpiritId();
        String spiritName = localizeSpiritName(player, spiritId);
        if (spiritName == null || spiritName.isBlank()) {
            spiritName = spiritId != null ? spiritId : LangLoader.getUITranslation(player, "ui.lore_feed.value_none");
        }
        LoreAbility ability = spiritId == null || spiritId.isBlank()
                ? null
                : LoreAbilityRegistry.getAbility(spiritId);
        int level = Math.max(1, socket.getLevel());
        int feedTier = Math.max(0, socket.getFeedTier());
        int nextFeedTier = feedTier + 1;

        StringBuilder sb = new StringBuilder();
        sb.append(LangLoader.getUITranslation(player, "ui.lore_feed.preview_spirit", spiritName)).append("\n");
        if (ability == null) {
            sb.append(LangLoader.getUITranslation(player, "ui.lore_feed.preview_ability",
                    LangLoader.getUITranslation(player, "ui.lore_feed.value_none")));
            return sb.toString().trim();
        }
        String langCode = LangLoader.getPlayerLanguage(player);
        String abilityName = ability.resolveAbilityName(langCode);
        if (abilityName == null || abilityName.isBlank()) {
            LoreEffectType effectType = ability.getEffectType();
            abilityName = effectType != null ? humanizeToken(effectType.name()) : "";
        }
        if (abilityName == null || abilityName.isBlank()) {
            abilityName = LangLoader.getUITranslation(player, "ui.lore_feed.value_none");
        }
        sb.append(LangLoader.getUITranslation(player, "ui.lore_feed.preview_ability", abilityName)).append("\n");
        String effectText = ability.describeEffectOnly(langCode, level, feedTier);
        if (effectText != null && !effectText.isBlank()) {
            sb.append(LangLoader.getUITranslation(player, "ui.lore_feed.preview_effect", effectText)).append("\n");
        }
        String triggerText = ability.describeTriggerText(langCode);
        if (triggerText != null && !triggerText.isBlank()) {
            sb.append(LangLoader.getUITranslation(player, "ui.lore_feed.preview_trigger", triggerText)).append("\n");
        }

        sb.append(LangLoader.getUITranslation(player, "ui.lore_feed.preview_scaling")).append("\n");

        double chanceCur = LoreAbility.scaleProcChance(ability.getProcChance(), feedTier) * 100.0d;
        double chanceNext = LoreAbility.scaleProcChance(ability.getProcChance(), nextFeedTier) * 100.0d;
        appendPreviewLine(sb, player, "ui.lore_feed.preview_chance",
                formatPercent(chanceCur) + "%", formatPercent(chanceNext) + "%");

        long cooldownCur = LoreAbility.scaleCooldownMs(ability.getCooldownMs(), feedTier);
        long cooldownNext = LoreAbility.scaleCooldownMs(ability.getCooldownMs(), nextFeedTier);
        appendPreviewLine(sb, player, "ui.lore_feed.preview_cooldown",
                formatSeconds(cooldownCur) + "s", formatSeconds(cooldownNext) + "s");

        LoreEffectType effectType = ability.getEffectType();
        if (effectType != null) {
            if (effectType == LoreEffectType.HEAL_AREA
                    || effectType == LoreEffectType.HEAL_AREA_OVER_TIME
                    || effectType == LoreEffectType.OMNISLASH) {
                double baseRadius = effectType == LoreEffectType.OMNISLASH
                        ? LoreAbility.BASE_OMNISLASH_RADIUS
                        : LoreAbility.BASE_HEAL_AREA_RADIUS;
                double radiusCur = LoreAbility.scaleRadius(baseRadius, feedTier);
                double radiusNext = LoreAbility.scaleRadius(baseRadius, nextFeedTier);
                appendPreviewLine(sb, player, "ui.lore_feed.preview_radius",
                        formatValue(radiusCur) + "m", formatValue(radiusNext) + "m");
            }

            if (effectType == LoreEffectType.HEAL_SELF_OVER_TIME
                    || effectType == LoreEffectType.HEAL_AREA_OVER_TIME) {
                long durationCur = LoreAbility.resolveHotDurationMs(feedTier);
                long durationNext = LoreAbility.resolveHotDurationMs(nextFeedTier);
                appendPreviewLine(sb, player, "ui.lore_feed.preview_duration",
                        formatSeconds(durationCur) + "s", formatSeconds(durationNext) + "s");
            }

            if (effectType == LoreEffectType.HEAL_AREA) {
                long durationCur = LoreAbility.resolveAreaHealDurationMs(feedTier);
                long durationNext = LoreAbility.resolveAreaHealDurationMs(nextFeedTier);
                appendPreviewLine(sb, player, "ui.lore_feed.preview_duration",
                        formatSeconds(durationCur) + "s", formatSeconds(durationNext) + "s");
            }

            if (effectType == LoreEffectType.APPLY_STUN) {
                double value = ability.getValueForLevel(level);
                long durationCur = LoreAbility.resolveStunFreezeDurationMs(value, feedTier);
                long durationNext = LoreAbility.resolveStunFreezeDurationMs(value, nextFeedTier);
                appendPreviewLine(sb, player, "ui.lore_feed.preview_duration",
                        formatSeconds(durationCur) + "s", formatSeconds(durationNext) + "s");
            }

            if (effectType == LoreEffectType.SUMMON_WOLF_PACK) {
                int capCur = Math.max(1, level + feedTier);
                int capNext = Math.max(1, level + nextFeedTier);
                appendPreviewLine(sb, player, "ui.lore_feed.preview_cap",
                        String.valueOf(capCur), String.valueOf(capNext));
            }

            if (effectType == LoreEffectType.DOUBLE_CAST) {
                double valueCur = LoreAbility.scaleEffectValue(ability.getValueForLevel(level), feedTier);
                double valueNext = LoreAbility.scaleEffectValue(ability.getValueForLevel(level), nextFeedTier);
                double pctCur = clampPercent(valueCur, 0.25d, 1.0d) * 100.0d;
                double pctNext = clampPercent(valueNext, 0.25d, 1.0d) * 100.0d;
                appendPreviewLine(sb, player, "ui.lore_feed.preview_double_cast",
                        formatPercent(pctCur) + "%", formatPercent(pctNext) + "%");
            }

            if (effectType == LoreEffectType.BERSERK) {
                long durationCur = LoreAbility.resolveBerserkDurationMs(feedTier);
                long durationNext = LoreAbility.resolveBerserkDurationMs(nextFeedTier);
                appendPreviewLine(sb, player, "ui.lore_feed.preview_berserk",
                        formatSeconds(durationCur) + "s", formatSeconds(durationNext) + "s");
            }

            int hitsCur = resolvePreviewHits(effectType, ability.getValueForLevel(level), feedTier);
            if (hitsCur > 0) {
                int hitsNext = resolvePreviewHits(effectType, ability.getValueForLevel(level), nextFeedTier);
                appendPreviewLine(sb, player, "ui.lore_feed.preview_hits",
                        String.valueOf(hitsCur), String.valueOf(hitsNext));
            }
        }

        return sb.toString().trim();
    }

    private static void appendPreviewLine(StringBuilder sb,
                                          Player player,
                                          String labelKey,
                                          String current,
                                          String next) {
        if (sb == null || player == null) {
            return;
        }
        String label = LangLoader.getUITranslation(player, labelKey);
        sb.append(label).append(":").append("\n");
        sb.append(LangLoader.getUITranslation(player, "ui.lore_feed.preview_current", current)).append("\n");
        sb.append(LangLoader.getUITranslation(player, "ui.lore_feed.preview_next", next)).append("\n");
    }

    private static int resolvePreviewHits(LoreEffectType effectType, double value, int feedTier) {
        if (effectType == null) {
            return 0;
        }
        int tier = Math.max(0, feedTier);
        return switch (effectType) {
            case OMNISLASH -> resolveFeedScaledHits(OMNISLASH_BASE_HITS, OMNISLASH_MAX_HITS, tier);
            case OCTASLASH -> resolveFeedScaledHits(OCTASLASH_BASE_HITS, OCTASLASH_MAX_HITS, tier);
            case PUMMEL -> resolveFeedScaledHits(PUMMEL_BASE_HITS, PUMMEL_MAX_HITS, tier);
            case BLOOD_RUSH -> resolveFeedScaledHits(BLOOD_RUSH_BASE_HITS, BLOOD_RUSH_MAX_HITS, tier);
            case CHARGE_ATTACK -> resolveFeedScaledHits(CHARGE_ATTACK_BASE_HITS, CHARGE_ATTACK_MAX_HITS, tier);
            case MULTI_HIT -> {
                int extraHits = Math.max(1, Math.min(3, calcExtraHits(value) + tier));
                yield Math.max(1, 1 + extraHits);
            }
            default -> 0;
        };
    }

    private static int resolveFeedScaledHits(int baseHits, int maxHits, int feedTier) {
        int base = Math.max(1, baseHits);
        int max = Math.max(base, maxHits);
        int hits = base + Math.max(0, feedTier);
        return Math.max(1, Math.min(max, hits));
    }

    private static int calcExtraHits(double value) {
        int hits = 1 + (int) Math.floor(Math.max(0.0d, value));
        return Math.max(1, Math.min(3, hits));
    }

    private static String socketStateLabel(Player player, LoreSocketData.LoreSocket socket) {
        if (socket == null) {
            return LangLoader.getUITranslation(player, "ui.lore_feed.slot_unavailable", 0);
        }
        String base;
        if (socket.isEmpty()) {
            base = LangLoader.getUITranslation(player, "ui.lore_feed.slot_empty", socket.getSlotIndex() + 1);
        } else if (!socket.hasSpirit()) {
            base = LangLoader.getUITranslation(player, "ui.lore_feed.slot_no_spirit", socket.getSlotIndex() + 1);
        } else if (LoreSocketManager.needsFeed(socket)) {
            base = LangLoader.getUITranslation(player, "ui.lore_feed.slot_ready", socket.getSlotIndex() + 1);
        } else {
            base = LangLoader.getUITranslation(player, "ui.lore_feed.slot_not_ready", socket.getSlotIndex() + 1);
        }
        String color = socket.getColor();
        if (color == null || color.isBlank()) {
            color = LangLoader.getUITranslation(player, "ui.lore_feed.color_unknown");
        }
        if (socket.hasSpirit()) {
            String spiritName = localizeSpiritName(player, socket.getSpiritId());
            String level = LangLoader.getUITranslation(player, "ui.lore_feed.level_label", socket.getLevel());
            if (spiritName == null || spiritName.isBlank()) {
                return base + " " + level + " (" + color + ")";
            }
            return base + " " + spiritName + " " + level + " (" + color + ")";
        }
        return base + " (" + color + ")";
    }

    private static String localizeSpiritName(Player player, String spiritId) {
        if (spiritId == null || spiritId.isBlank()) {
            return "";
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
        String lower = raw.trim().replace('_', ' ').toLowerCase(Locale.ROOT);
        String[] parts = lower.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
            if (i < parts.length - 1) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    private static String buildFeedSummary(Player player, List<FeedInfo> feedItems) {
        if (feedItems == null || feedItems.isEmpty()) {
            return LangLoader.getUITranslation(player, "ui.lore_feed.value_none");
        }
        List<String> parts = new ArrayList<>();
        for (FeedInfo info : feedItems) {
            String name = info.displayName == null || info.displayName.isBlank() ? info.itemId : info.displayName;
            String line = LangLoader.getUITranslation(player, "ui.lore_feed.feed_item_format", name, info.quantity);
            parts.add(line);
        }
        return String.join(", ", parts);
    }

    private static String buildClearSummary(Player player) {
        if (player == null) {
            return null;
        }
        String[] clearIds = LoreSocketManager.getConfig().getClearItemIds();
        if (clearIds == null || clearIds.length == 0) {
            return null;
        }
        String display = resolveItemDisplayName(player, clearIds);
        int count = countInventoryItems(player, clearIds);
        String label = LangLoader.getUITranslation(player, "ui.lore_feed.clear_item",
                display == null ? clearIds[0] : display,
                Math.max(0, count));
        String hint = LangLoader.getUITranslation(player, "ui.lore_feed.clear_hint",
                display == null ? clearIds[0] : display);
        return label + "\n" + hint;
    }

    private static int countInventoryItems(Player player, String[] itemIds) {
        if (player == null || player.getInventory() == null || itemIds == null || itemIds.length == 0) {
            return 0;
        }
        int total = 0;
        total += countInContainer(player.getInventory().getHotbar(), itemIds);
        total += countInContainer(player.getInventory().getStorage(), itemIds);
        return total;
    }

    private static int countInContainer(ItemContainer container, String[] itemIds) {
        if (container == null || itemIds == null || itemIds.length == 0) {
            return 0;
        }
        int total = 0;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack == null || stack.isEmpty() || stack.getItemId() == null) {
                continue;
            }
            String itemId = stack.getItemId().trim().toLowerCase(Locale.ROOT);
            for (String id : itemIds) {
                if (id == null || id.isBlank()) {
                    continue;
                }
                if (itemId.equals(id.trim().toLowerCase(Locale.ROOT))) {
                    total += Math.max(0, stack.getQuantity());
                    break;
                }
            }
        }
        return total;
    }

    private static String resolveItemDisplayName(Player player, String[] itemIds) {
        if (player == null || player.getInventory() == null || itemIds == null || itemIds.length == 0) {
            return null;
        }
        ItemContainer hotbar = player.getInventory().getHotbar();
        ItemContainer storage = player.getInventory().getStorage();
        String found = resolveItemDisplayNameFromContainer(player, hotbar, itemIds);
        if (found != null) {
            return found;
        }
        return resolveItemDisplayNameFromContainer(player, storage, itemIds);
    }

    private static String resolveItemDisplayNameFromContainer(Player player, ItemContainer container, String[] itemIds) {
        if (container == null || itemIds == null || itemIds.length == 0) {
            return null;
        }
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack == null || stack.isEmpty() || stack.getItemId() == null) {
                continue;
            }
            String itemId = stack.getItemId().trim().toLowerCase(Locale.ROOT);
            for (String id : itemIds) {
                if (id == null || id.isBlank()) {
                    continue;
                }
                if (itemId.equals(id.trim().toLowerCase(Locale.ROOT))) {
                    return UIItemUtils.displayNameOrItemId(stack, player);
                }
            }
        }
        return null;
    }

    private static String buildProgressBars(Player player, Entry equipment) {
        if (equipment == null) {
            return "<p>" + escapeHtml(LangLoader.getUITranslation(player, "ui.lore_feed.progress_no_equipment")) + "</p>";
        }
        LoreSocketData data = LoreSocketManager.getLoreSocketData(equipment.item);
        if (data == null || data.getSocketCount() == 0) {
            return "<p>" + escapeHtml(LangLoader.getUITranslation(player, "ui.lore_feed.progress_no_sockets")) + "</p>";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.getSocketCount(); i++) {
            LoreSocketData.LoreSocket socket = data.getSocket(i);
            String label = buildSlotLabel(player, socket, i + 1);
            int percent = 0;
            String xpText;
            if (socket == null || !socket.hasSpirit()) {
                xpText = LangLoader.getUITranslation(player, "ui.lore_feed.progress_no_spirit");
            } else {
                int xp = Math.max(0, socket.getXp());
                int xpNeeded = Math.max(1, LoreSocketManager.getXpRequiredForLevel(Math.max(1, socket.getLevel())));
                int clampedXp = Math.min(xp, xpNeeded);
                percent = (int) Math.min(100, Math.round((clampedXp * 100.0d) / xpNeeded));
                xpText = LangLoader.getUITranslation(player, "ui.lore_feed.progress_xp", xp, xpNeeded);
            }

            sb.append("<div style=\"layout-mode:Top; spacing:2;\">")
                    .append("<p>").append(escapeHtml(label)).append("</p>")
                    .append("<progress max=\"100\" value=\"").append(percent)
                    .append("\" data-hyui-bar-texture-path=\"boost_fill.png\" ")
                    .append("style=\"width:100%; anchor-height:14; background-image:url('boost_track.png'); ")
                    .append("background-size:100% 100%; background-repeat:no-repeat;\">")
                    .append("</progress>")
                    .append("<p style=\"font-size:10;\">").append(escapeHtml(xpText)).append("</p>")
                    .append("</div>");
        }
        return sb.toString();
    }

    private static void appendScalingStats(StringBuilder sb, Player player, LoreSocketData.LoreSocket socket) {
        if (sb == null || player == null || socket == null || !socket.hasSpirit()) {
            return;
        }
        String spiritId = socket.getSpiritId();
        LoreAbility ability = spiritId == null || spiritId.isBlank()
                ? null
                : LoreAbilityRegistry.getAbility(spiritId);
        if (ability == null) {
            return;
        }
        int level = Math.max(1, socket.getLevel());
        int feedTier = Math.max(0, socket.getFeedTier());
        double chance = LoreAbility.scaleProcChance(ability.getProcChance(), feedTier) * 100.0d;
        long cooldownMs = LoreAbility.scaleCooldownMs(ability.getCooldownMs(), feedTier);

        String header = LangLoader.getUITranslation(player, "ui.lore_feed.scaling_header");
        sb.append(header).append("\n");
        sb.append(LangLoader.getUITranslation(player, "ui.lore_feed.scaling_chance", formatPercent(chance))).append("\n");
        sb.append(LangLoader.getUITranslation(player, "ui.lore_feed.scaling_cooldown", formatSeconds(cooldownMs))).append("\n");

        LoreEffectType effectType = ability.getEffectType();
        if (effectType == null) {
            return;
        }

        if (effectType == LoreEffectType.HEAL_AREA || effectType == LoreEffectType.HEAL_AREA_OVER_TIME
                || effectType == LoreEffectType.OMNISLASH) {
            double radius = LoreAbility.scaleRadius(LoreAbility.BASE_HEAL_AREA_RADIUS, feedTier);
            if (effectType == LoreEffectType.OMNISLASH) {
                radius = LoreAbility.scaleRadius(LoreAbility.BASE_OMNISLASH_RADIUS, feedTier);
            }
            sb.append(LangLoader.getUITranslation(player, "ui.lore_feed.scaling_radius", formatValue(radius))).append("\n");
        }

        if (effectType == LoreEffectType.HEAL_SELF_OVER_TIME || effectType == LoreEffectType.HEAL_AREA_OVER_TIME) {
            long duration = LoreAbility.resolveHotDurationMs(feedTier);
            sb.append(LangLoader.getUITranslation(player, "ui.lore_feed.scaling_duration", formatSeconds(duration))).append("\n");
        }

        if (effectType == LoreEffectType.HEAL_AREA) {
            long duration = LoreAbility.resolveAreaHealDurationMs(feedTier);
            sb.append(LangLoader.getUITranslation(player, "ui.lore_feed.scaling_duration", formatSeconds(duration))).append("\n");
        }

        if (effectType == LoreEffectType.APPLY_STUN) {
            double value = ability.getValueForLevel(level);
            long duration = LoreAbility.resolveStunFreezeDurationMs(value, feedTier);
            sb.append(LangLoader.getUITranslation(player, "ui.lore_feed.scaling_duration", formatSeconds(duration))).append("\n");
        }

        if (effectType == LoreEffectType.SUMMON_WOLF_PACK) {
            int cap = Math.max(1, level + feedTier);
            sb.append(LangLoader.getUITranslation(player, "ui.lore_feed.scaling_cap", cap)).append("\n");
        }

        if (effectType == LoreEffectType.DOUBLE_CAST) {
            double value = LoreAbility.scaleEffectValue(ability.getValueForLevel(level), feedTier);
            double pct = clampPercent(value, 0.25d, 1.0d) * 100.0d;
            sb.append(LangLoader.getUITranslation(player, "ui.lore_feed.scaling_double_cast", formatPercent(pct))).append("\n");
        }

        if (effectType == LoreEffectType.BERSERK) {
            long duration = LoreAbility.resolveBerserkDurationMs(feedTier);
            sb.append(LangLoader.getUITranslation(player, "ui.lore_feed.scaling_berserk", formatSeconds(duration))).append("\n");
        }
    }

    private static String formatValue(double value) {
        double rounded = Math.round(value * 10.0d) / 10.0d;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.0001d) {
            return String.format(Locale.ROOT, "%.0f", rounded);
        }
        return String.format(Locale.ROOT, "%.1f", rounded);
    }

    private static String formatPercent(double percent) {
        double rounded = Math.round(percent * 10.0d) / 10.0d;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.0001d) {
            return String.format(Locale.ROOT, "%.0f", rounded);
        }
        return String.format(Locale.ROOT, "%.1f", rounded);
    }

    private static String formatSeconds(long millis) {
        double seconds = Math.max(0.0d, millis / 1000.0d);
        double rounded = Math.round(seconds * 10.0d) / 10.0d;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.0001d) {
            return String.format(Locale.ROOT, "%.0f", rounded);
        }
        return String.format(Locale.ROOT, "%.1f", rounded);
    }

    private static double clampPercent(double value, double min, double max) {
        double pct = value <= 1.0d ? value : value / 100.0d;
        if (pct < min) {
            return min;
        }
        if (pct > max) {
            return max;
        }
        return pct;
    }

    private static boolean shouldDisable(Entry equipment) {
        return equipment == null;
    }

    private static ProcessResult processSelection(Player player, Entry equipment, String slotKey) {
        if (player == null) {
            return new ProcessResult("Player not found.");
        }
        if (equipment == null || equipment.item == null || equipment.item.isEmpty()) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_feed.status_no_equipment"));
        }

        LoreSocketData data = LoreSocketManager.getLoreSocketData(equipment.item);
        if (data == null || data.getSocketCount() == 0) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_feed.status_no_socket"));
        }

        boolean wantsClear = LoreSocketManager.isHoldingClearItem(player);
        int slotIndex = resolveSlotIndexForProcess(data, slotKey, wantsClear);
        if (slotIndex < 0 || slotIndex >= data.getSocketCount()) {
            if (hasAnySpirit(data)) {
                return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_feed.status_not_ready"));
            }
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_feed.status_no_spirit"));
        }

        LoreSocketData.LoreSocket socket = data.getSocket(slotIndex);
        if (socket == null) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_feed.status_no_socket"));
        }
        if (!socket.hasSpirit()) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_feed.status_no_spirit"));
        }
        boolean cleared = false;
        if (wantsClear) {
            if (!LoreSocketManager.tryClearSpirit(player, data, slotIndex)) {
                return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_feed.status_no_clear_item"));
            }
            cleared = true;
        } else {
            if (!LoreSocketManager.needsFeed(socket)) {
                return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_feed.status_not_ready"));
            }
            String[] feedIds = LoreSocketManager.getConfig().getFeedItemIds();
            if (feedIds == null || feedIds.length == 0) {
                return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_feed.status_no_feed_items"));
            }

            if (!LoreSocketManager.tryFeed(player, data, slotIndex)) {
                return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_feed.status_no_essence"));
            }
        }

        ItemStack updated = LoreSocketManager.withLoreSocketData(equipment.item, data);
        ItemContainer equipmentContainer = getContainer(player, equipment.kind);
        if (equipmentContainer != null) {
            equipmentContainer.setItemStackForSlot(equipment.slot, updated);
        }

        if (cleared) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_feed.status_cleared"));
        }
        return new ProcessResult(LangLoader.getUITranslation(player, "ui.lore_feed.status_done"));
    }

    private static int resolveSlotIndexForProcess(LoreSocketData data, String slotKey, boolean preferAnySpirit) {
        if (data == null || data.getSocketCount() == 0) {
            return -1;
        }
        String key = slotKey == null ? "" : slotKey.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty() || "auto".equals(key)) {
            for (int i = 0; i < data.getSocketCount(); i++) {
                LoreSocketData.LoreSocket socket = data.getSocket(i);
                if (socket == null || !socket.hasSpirit()) {
                    continue;
                }
                if (preferAnySpirit || LoreSocketManager.needsFeed(socket)) {
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

    private static int resolveSlotIndexForDisplay(LoreSocketData data, String slotKey) {
        if (data == null || data.getSocketCount() == 0) {
            return -1;
        }
        String key = slotKey == null ? "" : slotKey.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty() || "auto".equals(key)) {
            for (int i = 0; i < data.getSocketCount(); i++) {
                LoreSocketData.LoreSocket socket = data.getSocket(i);
                if (socket != null && socket.hasSpirit()) {
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

    private static boolean hasAnySpirit(LoreSocketData data) {
        if (data == null) {
            return false;
        }
        for (int i = 0; i < data.getSocketCount(); i++) {
            LoreSocketData.LoreSocket socket = data.getSocket(i);
            if (socket != null && socket.hasSpirit()) {
                return true;
            }
        }
        return false;
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

    private static String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String loadTemplate() {
        return UITemplateUtils.loadTemplate(
                LoreFeedBenchUI.class,
                TEMPLATE_PATH,
                "<div style=\"padding:20px;\">Lore feed UI template missing.</div>",
                "LoreFeedBenchUI");
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
