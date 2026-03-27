package irai.mod.reforge.UI;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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

import irai.mod.reforge.Common.EquipmentDamageTooltipMath;
import irai.mod.reforge.Common.UI.HyUIReflectionUtils;
import irai.mod.reforge.Common.UI.UIInventoryUtils;
import irai.mod.reforge.Common.UI.UIItemUtils;
import irai.mod.reforge.Common.UI.UITemplateUtils;
import irai.mod.reforge.Config.RefinementConfig;
import irai.mod.reforge.Config.SFXConfig;
import irai.mod.reforge.Interactions.ReforgeEquip;
import irai.mod.reforge.Socket.SocketData;
import irai.mod.reforge.Socket.SocketManager;
import irai.mod.reforge.Util.DynamicTooltipUtils;
import irai.mod.reforge.Util.LangLoader;

/**
 * HyUI Reforge bench page.
 * Uses reflection to keep HyUI optional at runtime.
 */
public final class ReforgeBenchUI {
    private ReforgeBenchUI() {}

    private static final String HYUI_PAGE_BUILDER  = "au.ellie.hyui.builders.PageBuilder";
    private static final String HYUI_PLUGIN        = "au.ellie.hyui.HyUIPlugin";
    private static final String HYUI_EVENT_BINDING = "com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType";
    private static final String TEMPLATE_PATH      = "Common/UI/Custom/Pages/ReforgeBench.html";

    private static final String MATERIAL_ID = "Refinement_Glob";
    private static final String HAMMER_IRON_ID = "Tool_Hammer_Iron";
    private static final String HAMMER_THORIUM_ID = "Tool_Hammer_Thorium";
    private static final int MATERIAL_COST = 3;
    private static final int MAX_UPGRADE_LEVEL = 3;
    private static final double HAMMER_IRON_BREAK_MULTIPLIER = 0.50d;
    private static final double HAMMER_THORIUM_BREAK_MULTIPLIER = 0.30d;
    private static final double HAMMER_IRON_DURABILITY_LOSS_FRACTION = 0.05d;
    private static final double HAMMER_THORIUM_DURABILITY_LOSS_FRACTION = 0.15d;

    private static final double[] DEFAULT_BREAK_CHANCES = {0.010, 0.050, 0.075};
    private static final double[][] DEFAULT_WEIGHTS = {
            {0.00, 0.65, 0.34, 0.01},
            {0.35, 0.45, 0.19, 0.01},
            {0.60, 0.30, 0.095, 0.005},
    };

    private static final SFXConfig sfxConfig = new SFXConfig();
    private static RefinementConfig refinementConfig;
    private static boolean hyuiAvailable = false;

    private static final Map<PlayerRef, Object> openPages = new ConcurrentHashMap<>();
    private static final Map<PlayerRef, SelectionState> pendingSelections = new ConcurrentHashMap<>();
    private static final Map<PlayerRef, Boolean> processingPlayers = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final int PROCESS_DURATION_MS = 1000;
    private static final int PROGRESS_TICK_MS = 50;

    private enum ContainerKind { HOTBAR, STORAGE }
    private enum OutcomeType { DEGRADE, SAME, UPGRADE, JACKPOT }
    private enum HammerSupportType {
        IRON("ui.reforge.hammer_iron", HAMMER_IRON_BREAK_MULTIPLIER, HAMMER_IRON_DURABILITY_LOSS_FRACTION),
        THORIUM("ui.reforge.hammer_thorium", HAMMER_THORIUM_BREAK_MULTIPLIER, HAMMER_THORIUM_DURABILITY_LOSS_FRACTION);

        final String labelKey;
        final double breakMultiplier;
        final double durabilityLossFraction;

        HammerSupportType(String labelKey, double breakMultiplier, double durabilityLossFraction) {
            this.labelKey = labelKey;
            this.breakMultiplier = breakMultiplier;
            this.durabilityLossFraction = durabilityLossFraction;
        }
    }

    private static final class Entry {
        final ContainerKind container;
        final short slot;
        final ItemStack item;
        final String itemId;
        final int quantity;
        final String displayName;

        Entry(ContainerKind container, short slot, ItemStack item, String itemId, int quantity, String displayName) {
            this.container = container;
            this.slot = slot;
            this.item = item;
            this.itemId = itemId;
            this.quantity = quantity;
            this.displayName = displayName;
        }
    }

    private static final class Snapshot {
        final List<Entry> equipments;
        final List<Entry> materials;
        final List<Entry> supports;
        final int materialCount;

        Snapshot(List<Entry> equipments, List<Entry> materials, List<Entry> supports, int materialCount) {
            this.equipments = equipments;
            this.materials = materials;
            this.supports = supports;
            this.materialCount = materialCount;
        }
    }

    private static final class SelectionState {
        final String equipmentKey;
        final String materialKey;
        final String supportKey;
        final String statusText;
        final int progressValue;
        final boolean processing;

        SelectionState(String equipmentKey, String materialKey, String supportKey, String statusText, int progressValue, boolean processing) {
            this.equipmentKey = equipmentKey;
            this.materialKey = materialKey;
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

    private static final class ReforgeOutcome {
        final int levelChange;
        final OutcomeType type;

        ReforgeOutcome(int levelChange, OutcomeType type) {
            this.levelChange = levelChange;
            this.type = type;
        }
    }

    private static final class Preview {
        final String currentStats;
        final String expectedStats;
        final String expectedDamage;
        final String expectedDamageLine1;
        final String expectedDamageLine2;
        final String expectedDamageLine3;
        final String chanceBreak;
        final String chanceDegrade;
        final String chanceSame;
        final String chanceUpgrade;
        final String chanceJackpot;
        final String chanceNote;
        final int currentLevel;

        Preview(
                String currentStats,
                String expectedStats,
                String expectedDamage,
                String expectedDamageLine1,
                String expectedDamageLine2,
                String expectedDamageLine3,
                String chanceBreak,
                String chanceDegrade,
                String chanceSame,
                String chanceUpgrade,
                String chanceJackpot,
                String chanceNote,
                int currentLevel) {
            this.currentStats = currentStats;
            this.expectedStats = expectedStats;
            this.expectedDamage = expectedDamage;
            this.expectedDamageLine1 = expectedDamageLine1;
            this.expectedDamageLine2 = expectedDamageLine2;
            this.expectedDamageLine3 = expectedDamageLine3;
            this.chanceBreak = chanceBreak;
            this.chanceDegrade = chanceDegrade;
            this.chanceSame = chanceSame;
            this.chanceUpgrade = chanceUpgrade;
            this.chanceJackpot = chanceJackpot;
            this.chanceNote = chanceNote;
            this.currentLevel = currentLevel;
        }
    }

    public static void initialize() {
        hyuiAvailable = HyUIReflectionUtils.detectHyUi(HYUI_PAGE_BUILDER, HYUI_PLUGIN, "ReforgeBenchUI");
    }

    public static boolean isAvailable() {
        return hyuiAvailable;
    }

    public static void setRefinementConfig(RefinementConfig config) {
        refinementConfig = config;
    }

    public static void setSfxConfig(SFXConfig config) {
        if (config == null) return;
        sfxConfig.setSFX_START(config.getSFX_START());
        sfxConfig.setSFX_SUCCESS(config.getSFX_SUCCESS());
        sfxConfig.setSFX_JACKPOT(config.getSFX_JACKPOT());
        sfxConfig.setSFX_FAIL(config.getSFX_FAIL());
        sfxConfig.setSFX_NO_CHANGE(config.getSFX_NO_CHANGE());
        sfxConfig.setSFX_SHATTER(config.getSFX_SHATTER());
        sfxConfig.setBenches(config.getBenches());
    }

    public static void open(Player player) {
        if (player == null) return;
        if (!hyuiAvailable) {
            player.sendMessage(Message.raw("<color=#FF5555>HyUI not installed - " + LangLoader.getUITranslation(player, "ui.reforge.title") + " disabled."));
            return;
        }
        PlayerRef ref = player.getPlayerRef();
        closePageIfOpen(ref);
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
        List<Entry> materials = new ArrayList<>();
        List<Entry> supports = new ArrayList<>();
        collectFromContainer(player, player.getInventory().getHotbar(), ContainerKind.HOTBAR, equipments, materials, supports);
        collectFromContainer(player, player.getInventory().getStorage(), ContainerKind.STORAGE, equipments, materials, supports);
        int total = 0;
        for (Entry e : materials) total += e.quantity;
        return new Snapshot(equipments, materials, supports, total);
    }

    private static void collectFromContainer(
            Player player,
            ItemContainer container,
            ContainerKind kind,
            List<Entry> equipments,
            List<Entry> materials,
            List<Entry> supports) {
        if (container == null) return;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack == null || stack.isEmpty()) continue;
            String itemId = stack.getItemId();
            if (itemId == null || itemId.isEmpty()) continue;

            String name = UIItemUtils.displayNameOrItemId(stack, player);

            Entry entry = new Entry(kind, slot, stack, itemId, stack.getQuantity(), name);
            if (ReforgeEquip.isWeapon(stack) || ReforgeEquip.isArmor(stack)) {
                equipments.add(entry);
            }
            if (MATERIAL_ID.equalsIgnoreCase(itemId)) {
                materials.add(entry);
            }
            if (isHammerItem(itemId)) {
                supports.add(entry);
            }
        }
    }

    private static void openPage(Player player, Snapshot snapshot, SelectionState state) {
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

            String html = buildHtml(player, snapshot, state);
            Object pageBuilder = pageForPlayer.invoke(null, playerRef);
            pageBuilder = fromHtml.invoke(pageBuilder, html);

            final Player finalPlayer = player;
            final Snapshot finalSnapshot = snapshot;

            addListener.invoke(pageBuilder, "equipmentDropdown", valueChanged,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        String eq = extractEventValue(eventObj);
                        String mat = getContextValue(ctxObj, "materialDropdown", "#materialDropdown.value");
                        String sup = getContextValue(ctxObj, "supportDropdown", "#supportDropdown.value");
                        pendingSelections.put(finalPlayer.getPlayerRef(), new SelectionState(eq, mat, sup, null, 0, false));
                        finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));
                    });

            addListener.invoke(pageBuilder, "materialDropdown", valueChanged,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        String eq = getContextValue(ctxObj, "equipmentDropdown", "#equipmentDropdown.value");
                        String mat = extractEventValue(eventObj);
                        String sup = getContextValue(ctxObj, "supportDropdown", "#supportDropdown.value");
                        pendingSelections.put(finalPlayer.getPlayerRef(), new SelectionState(eq, mat, sup, null, 0, false));
                        finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));
                    });

            addListener.invoke(pageBuilder, "supportDropdown", valueChanged,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        String eq = getContextValue(ctxObj, "equipmentDropdown", "#equipmentDropdown.value");
                        String mat = getContextValue(ctxObj, "materialDropdown", "#materialDropdown.value");
                        String sup = extractEventValue(eventObj);
                        pendingSelections.put(finalPlayer.getPlayerRef(), new SelectionState(eq, mat, sup, null, 0, false));
                        finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));
                    });

            addListener.invoke(pageBuilder, "processButton", activating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        PlayerRef ref = finalPlayer.getPlayerRef();
                        if (Boolean.TRUE.equals(processingPlayers.get(ref))) return;

                        String eqVal = getContextValue(ctxObj, "equipmentDropdown", "#equipmentDropdown.value");
                        String matVal = getContextValue(ctxObj, "materialDropdown", "#materialDropdown.value");
                        String supVal = getContextValue(ctxObj, "supportDropdown", "#supportDropdown.value");
                        Entry equipment = resolveSelection(finalSnapshot.equipments, eqVal);
                        Entry material = resolveSelection(finalSnapshot.materials, matVal);
                        Entry support = resolveSelection(finalSnapshot.supports, supVal);

                        processingPlayers.put(ref, true);
                        pendingSelections.put(ref, new SelectionState(eqVal, matVal, supVal,
                                LangLoader.getUITranslation(finalPlayer, "ui.reforge.status_processing"), 0, true));
                        sfxConfig.playReforgeStart(finalPlayer);
                        finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));

                        for (int elapsed = PROGRESS_TICK_MS; elapsed < PROCESS_DURATION_MS; elapsed += PROGRESS_TICK_MS) {
                            final int delay = elapsed;
                            final int timedProgress = Math.min(99, (int) Math.round(((delay + PROGRESS_TICK_MS) * 100.0) / PROCESS_DURATION_MS));
                            scheduler.schedule(() -> finalPlayer.getWorld().execute(() -> {
                                if (!Boolean.TRUE.equals(processingPlayers.get(ref))) return;
                                pendingSelections.put(ref, new SelectionState(eqVal, matVal, supVal,
                                        LangLoader.getUITranslation(finalPlayer, "ui.reforge.status_processing"),
                                        timedProgress, true));
                                openWithSync(finalPlayer);
                            }), delay, TimeUnit.MILLISECONDS);
                        }

                        scheduler.schedule(() -> finalPlayer.getWorld().execute(() -> {
                            try {
                                ProcessResult result = processSelection(finalPlayer, equipment, material, support);
                                pendingSelections.put(ref, new SelectionState(eqVal, matVal, supVal, result.status, result.progress, false));
                            } finally {
                                processingPlayers.remove(ref);
                                openWithSync(finalPlayer);
                            }
                        }), PROCESS_DURATION_MS, TimeUnit.MILLISECONDS);
                    });

            pageBuilder = withLifetime.invoke(pageBuilder, CustomPageLifetime.CanDismiss);
            Object store = getStore(playerRef);
            Object page = openMethod.invoke(pageBuilder, store);
            openPages.put(playerRef, page);
        } catch (Exception e) {
            System.err.println("[SocketReforge] ReforgeBenchUI open error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String buildHtml(Player player, Snapshot snapshot, SelectionState state) {
        String eqKey = state != null ? state.equipmentKey : null;
        String matKey = state != null ? state.materialKey : null;
        String supKey = state != null ? state.supportKey : null;
        String status = state != null && state.statusText != null ? state.statusText : LangLoader.getUITranslation(player, "ui.reforge.status_idle");
        int progress = state != null ? Math.max(0, Math.min(100, state.progressValue)) : 0;
        boolean processing = state != null && state.processing;
        if (!processing) {
            progress = 0;
        }

        Entry selectedEquipment = findByKey(snapshot.equipments, eqKey);
        Entry selectedMaterial = findByKey(snapshot.materials, matKey);
        Entry selectedSupport = resolveSelection(snapshot.supports, supKey);

        if (selectedEquipment == null && !snapshot.equipments.isEmpty()) {
            selectedEquipment = snapshot.equipments.get(0);
            eqKey = "0";
        }
        if (selectedMaterial == null && !snapshot.materials.isEmpty()) {
            selectedMaterial = snapshot.materials.get(0);
            matKey = "0";
        }

        Preview preview = buildPreview(player, selectedEquipment, selectedSupport);
        if (!processing) {
            if (selectedEquipment == null) {
                status = LangLoader.getUITranslation(player, "ui.reforge.status_no_equipment");
            } else if (preview.currentLevel >= MAX_UPGRADE_LEVEL) {
                status = LangLoader.getUITranslation(player, "ui.reforge.status_max_level");
            } else if (snapshot.materialCount < MATERIAL_COST) {
                status = LangLoader.getUITranslation(player, "ui.reforge.status_need_material", MATERIAL_COST);
            } else if (LangLoader.getUITranslation(player, "ui.reforge.status_idle").equals(status)) {
                status = LangLoader.getUITranslation(player, "ui.reforge.status_ready");
            }
        }

        String html = loadTemplate();
        
        // UI translations
        html = html.replace("{{title}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.title")));
        html = html.replace("{{equipmentLabel}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.equipment")));
        html = html.replace("{{materialLabel}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.material")));
        html = html.replace("{{supportLabel}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.support")));
        html = html.replace("{{metadataLabel}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.metadata_name")));
        html = html.replace("{{metadataNameLabel}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.metadata_name")));
        html = html.replace("{{metadataLevelLabel}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.metadata_level")));
        html = html.replace("{{metadataCurrentStatLabel}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.metadata_current_stat")));
        html = html.replace("{{metadataBaseStatLabel}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.metadata_base_stat")));
        html = html.replace("{{expectedOutcomeLabel}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.expected_outcome")));
        html = html.replace("{{expectedDamageLabel}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.expected_damage")));
        html = html.replace("{{refineProgressLabel}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.refine_progress")));
        html = html.replace("{{consumesMaterialText}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.consumes_material", MATERIAL_COST)));
        html = html.replace("{{ironHammerInfoText}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.iron_hammer_info")));
        html = html.replace("{{thoriumHammerInfoText}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.thorium_hammer_info")));
        html = html.replace("{{refineButtonText}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.button_refine")));
        
        // Dynamic content
        html = html.replace("{{equipmentOptions}}", buildOptions(snapshot.equipments, LangLoader.getUITranslation(player, "ui.reforge.no_equipment"), eqKey));
        html = html.replace("{{materialOptions}}", buildOptions(snapshot.materials, LangLoader.getUITranslation(player, "ui.reforge.no_material"), matKey));
        html = html.replace("{{supportOptions}}", buildSupportOptions(player, snapshot.supports, supKey));
        html = html.replace("{{supportDurabilityText}}", escapeHtml(buildSupportDurabilityText(player, selectedSupport)));
        html = html.replace("{{materialCountText}}", LangLoader.getUITranslation(player, "ui.reforge.material_count", snapshot.materialCount));
        html = html.replace("{{currentStatsText}}", escapeHtml(preview.currentStats));
        html = html.replace("{{expectedStatsText}}", escapeHtml(preview.expectedStats));
        html = html.replace("{{chanceBreakLabel}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.chance_break")));
        html = html.replace("{{chanceDegradeLabel}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.chance_degrade")));
        html = html.replace("{{chanceSameLabel}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.chance_same")));
        html = html.replace("{{chanceUpgradeLabel}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.chance_upgrade")));
        html = html.replace("{{chanceJackpotLabel}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.chance_jackpot")));
        html = html.replace("{{chanceBreakText}}", escapeHtml(preview.chanceBreak));
        html = html.replace("{{chanceDegradeText}}", escapeHtml(preview.chanceDegrade));
        html = html.replace("{{chanceSameText}}", escapeHtml(preview.chanceSame));
        html = html.replace("{{chanceUpgradeText}}", escapeHtml(preview.chanceUpgrade));
        html = html.replace("{{chanceJackpotText}}", escapeHtml(preview.chanceJackpot));
        html = html.replace("{{chanceNoteText}}", escapeHtml(preview.chanceNote));
        html = html.replace("{{expectedDamageText}}", escapeHtml(preview.expectedDamage));
        html = html.replace("{{expectedDamageLine1Text}}", escapeHtml(preview.expectedDamageLine1));
        html = html.replace("{{expectedDamageLine2Text}}", escapeHtml(preview.expectedDamageLine2));
        html = html.replace("{{expectedDamageLine3Text}}", escapeHtml(preview.expectedDamageLine3));
        html = html.replace("{{metadataNameText}}", escapeHtml(buildMetadataName(selectedEquipment)));
        html = html.replace("{{metadataLevelText}}", escapeHtml(buildMetadataLevel(selectedEquipment)));
        html = html.replace("{{metadataCurrentStatText}}", escapeHtml(buildMetadataCurrentStat(player, selectedEquipment)));
        html = html.replace("{{metadataBaseStatText}}", escapeHtml(buildMetadataBaseStat(player, selectedEquipment)));
        html = html.replace("{{metadataText}}", escapeHtml(buildMetadata(player, selectedEquipment)));
        html = html.replace("{{progressValue}}", String.valueOf(progress));
        html = html.replace("{{statusText}}", escapeHtml(status));
        html = html.replace("{{processDisabledAttr}}", shouldDisable(processing, selectedEquipment, snapshot.materialCount) ? "disabled=\"true\"" : "");
        return html;
    }

    private static boolean shouldDisable(boolean processing, Entry selectedEquipment, int materialCount) {
        if (processing) return true;
        if (selectedEquipment == null) return true;
        int level = ReforgeEquip.getLevelFromItem(selectedEquipment.item);
        if (level >= MAX_UPGRADE_LEVEL) return true;
        return materialCount < MATERIAL_COST;
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
            if (key.equals(selectedKey)) sb.append(" selected=\"true\"");
            sb.append(">").append(escapeHtml(entry.displayName)).append(" x").append(entry.quantity).append("</option>");
        }
        return sb.toString();
    }

    private static String buildSupportOptions(Player player, List<Entry> entries, String selectedKey) {
        StringBuilder sb = new StringBuilder();
        sb.append("<option value=\"\"");
        if (selectedKey == null || selectedKey.isEmpty()) {
            sb.append(" selected=\"true\"");
        }
        sb.append(">").append(escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.support_none"))).append("</option>");
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            String key = String.valueOf(i);
            sb.append("<option value=\"").append(key).append("\"");
            if (key.equals(selectedKey)) sb.append(" selected=\"true\"");
            sb.append(">").append(escapeHtml(entry.displayName)).append(" ").append(escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.support_guard"))).append(" x").append(entry.quantity).append("</option>");
        }
        return sb.toString();
    }

    private static Preview buildPreview(Player player, Entry equipment, Entry support) {
        if (equipment == null || equipment.item == null || equipment.item.isEmpty()) {
            return new Preview(
                    t(player, "ui.reforge.preview_no_equipment"),
                    t(player, "ui.reforge.preview_select_item"),
                    t(player, "ui.reforge.preview_expected_damage_placeholder"),
                    t(player, "ui.reforge.preview_expected_damage_line1"),
                    t(player, "ui.reforge.preview_expected_damage_line2"),
                    "",
                    "-",
                    "-",
                    "-",
                    "-",
                    "-",
                    t(player, "ui.reforge.preview_select_equipment_note"),
                    0);
        }

        ItemStack item = equipment.item;
        boolean isArmor = ReforgeEquip.isArmor(item) && !ReforgeEquip.isWeapon(item);
        HammerSupportType hammerSupport = getHammerSupportType(support);
        int level = Math.max(0, Math.min(MAX_UPGRADE_LEVEL, ReforgeEquip.getLevelFromItem(item)));
        String typeLabel = isArmor ? t(player, "ui.reforge.type_armor") : t(player, "ui.reforge.type_weapon");
        String refineName = isArmor ? ReforgeEquip.getArmorUpgradeName(level) : ReforgeEquip.getUpgradeName(level);
        double currentMult = statMultiplierForLevel(level, isArmor);
        String statLabel = isArmor ? t(player, "ui.reforge.stat_defense") : t(player, "ui.reforge.stat_damage");
        String refineSuffix = refineName == null || refineName.isBlank() ? "" : " (" + refineName + ")";

        String currentStats = t(player, "ui.reforge.preview_type", typeLabel) + "\n"
                + t(player, "ui.reforge.preview_refine_level", level, refineSuffix) + "\n"
                + t(player, "ui.reforge.preview_current_stat", statLabel, format3(currentMult),
                    formatPercent(currentMult - 1.0));

        if (level >= MAX_UPGRADE_LEVEL) {
            String expectedStats = t(player, "ui.reforge.chance_break") + ": 0%\n"
                    + t(player, "ui.reforge.chance_degrade") + ": 0%\n"
                    + t(player, "ui.reforge.chance_same") + ": 0%\n"
                    + t(player, "ui.reforge.chance_upgrade") + ": 0%\n"
                    + t(player, "ui.reforge.chance_jackpot") + ": 0%\n"
                    + t(player, "ui.reforge.preview_max_level_note");
            String expectedDamage = t(player, "ui.reforge.preview_expected_max_level");
            String chanceNote = hammerSupport != null
                    ? t(player, "ui.reforge.preview_max_level_hammer", hammerLabel(player, hammerSupport),
                        durabilityPercent(hammerSupport))
                    : t(player, "ui.reforge.preview_max_level");
            return new Preview(
                    currentStats,
                    expectedStats,
                    expectedDamage,
                    t(player, "ui.reforge.preview_expected_after_refine"),
                    t(player, "ui.reforge.preview_max_level_short"),
                    "",
                    "0%",
                    "0%",
                    "0%",
                    "0%",
                    "0%",
                    chanceNote,
                    level);
        }

        double breakChance = effectiveBreakChance(level, isArmor, hammerSupport);
        double[] w = weights(level);
        double survive = Math.max(0.0, 1.0 - breakChance);

        int degLevel = clampLevel(level - 1);
        int sameLevel = clampLevel(level);
        int upLevel = clampLevel(level + 1);
        int jackLevel = clampLevel(level + 2);

        double mDeg = statMultiplierForLevel(degLevel, isArmor);
        double mSame = statMultiplierForLevel(sameLevel, isArmor);
        double mUp = statMultiplierForLevel(upLevel, isArmor);
        double mJack = statMultiplierForLevel(jackLevel, isArmor);

        double pDeg = survive * w[0];
        double pSame = survive * w[1];
        double pUp = survive * w[2];
        double pJack = survive * w[3];
        double expectedWithBreak = (pDeg * mDeg) + (pSame * mSame) + (pUp * mUp) + (pJack * mJack);

        String expectedStats = t(player, "ui.reforge.chance_break") + ": " + formatPercent(breakChance) + "\n"
                + t(player, "ui.reforge.chance_degrade") + ": " + formatPercent(pDeg) + "\n"
                + t(player, "ui.reforge.chance_same") + ": " + formatPercent(pSame) + "\n"
                + t(player, "ui.reforge.chance_upgrade") + ": " + formatPercent(pUp) + "\n"
                + t(player, "ui.reforge.chance_jackpot") + ": " + formatPercent(pJack);

        String expectedDamage = t(player, "ui.reforge.preview_upgrade_jackpot", format3(mUp), format3(mJack)) + "\n"
                + t(player, "ui.reforge.preview_expected_stat_with_break", statLabel, format3(expectedWithBreak));

        return new Preview(
                currentStats,
                expectedStats,
                expectedDamage,
                t(player, "ui.reforge.preview_upgrade_line", format3(mUp)),
                t(player, "ui.reforge.preview_jackpot_line", format3(mJack)),
                t(player, "ui.reforge.preview_expected_stat_line", statLabel, format3(expectedWithBreak)),
                formatPercent(breakChance),
                formatPercent(pDeg),
                formatPercent(pSame),
                formatPercent(pUp),
                formatPercent(pJack),
                hammerSupport != null
                        ? t(player, "ui.reforge.preview_hammer_active", hammerLabel(player, hammerSupport),
                            durabilityPercent(hammerSupport))
                        : "",
                level);
    }

    private static ProcessResult processSelection(Player player, Entry equipment, Entry material, Entry support) {
        if (equipment == null) return new ProcessResult(t(player, "ui.reforge.error_pick_equipment"), 0);

        ItemStack current = readCurrentStack(player, equipment);
        if (current == null || current.isEmpty()) return new ProcessResult(t(player, "ui.reforge.error_equipment_changed"), 0);

        boolean isWeapon = ReforgeEquip.isWeapon(current);
        boolean isArmor = !isWeapon && ReforgeEquip.isArmor(current);
        if (!isWeapon && !isArmor) return new ProcessResult(t(player, "ui.reforge.error_item_not_refinable"), 0);

        int level = ReforgeEquip.getLevelFromItem(current);
        if (level >= MAX_UPGRADE_LEVEL) return new ProcessResult(t(player, "ui.reforge.error_max_level"), 100);

        if (countMaterialGlobally(player) < MATERIAL_COST) {
            return new ProcessResult(t(player, "ui.reforge.error_need_material", MATERIAL_COST), 0);
        }

        HammerSupportType hammerSupport = getHammerSupportType(support);
        HammerUseResult hammerUse = new HammerUseResult(true, false);
        if (hammerSupport != null) {
            hammerUse = applyHammerWear(player, support, hammerSupport.durabilityLossFraction);
            if (!hammerUse.ok) {
                return new ProcessResult(t(player, "ui.reforge.error_hammer_changed"), 0);
            }
        }

        if (!consumeMaterialGlobally(player, MATERIAL_COST)) {
            return new ProcessResult(t(player, "ui.reforge.error_material_consume"), 0);
        }

        double breakChance = effectiveBreakChance(level, isArmor, hammerSupport);
        String hammerSuffix = "";
        if (hammerSupport != null) {
            String hammerLabel = hammerLabel(player, hammerSupport);
            String rawSuffix = hammerUse.consumed
                    ? t(player, "ui.reforge.hammer_broke", hammerLabel)
                    : t(player, "ui.reforge.hammer_durability_loss", hammerLabel, durabilityPercent(hammerSupport));
            if (rawSuffix != null && !rawSuffix.isBlank()) {
                hammerSuffix = " " + rawSuffix;
            }
        }
        if (Math.random() < breakChance) {
            removeEquipment(player, equipment);
            sfxConfig.playShatter(player);
            return new ProcessResult(t(player, "ui.reforge.result_shattered", hammerSuffix), 0);
        }

        ReforgeOutcome outcome = rollOutcome(level);
        int newLevel = clampLevel(level + outcome.levelChange);

        ItemStack updated = ReforgeEquip.withUpgradeLevel(current, newLevel);
        writeStack(player, equipment, updated);
        registerReforgeTooltip(updated, newLevel, isArmor);

        switch (outcome.type) {
            case DEGRADE -> {
                sfxConfig.playFail(player);
                return new ProcessResult(t(player, "ui.reforge.result_degraded", level, newLevel, hammerSuffix), 100);
            }
            case SAME -> {
                sfxConfig.playNoChange(player);
                return new ProcessResult(t(player, "ui.reforge.result_same", level, hammerSuffix), 100);
            }
            case UPGRADE -> {
                sfxConfig.playSuccess(player);
                return new ProcessResult(t(player, "ui.reforge.result_success", level, newLevel, hammerSuffix), 100);
            }
            case JACKPOT -> {
                sfxConfig.playJackpot(player);
                return new ProcessResult(t(player, "ui.reforge.result_jackpot", level, newLevel, hammerSuffix), 100);
            }
            default -> {
                return new ProcessResult(t(player, "ui.reforge.result_unknown"), 0);
            }
        }
    }

    private static void registerReforgeTooltip(ItemStack item, int level, boolean isArmor) {
        if (item == null || item.isEmpty() || level <= 0) {
            DynamicTooltipUtils.refreshAllPlayers();
            return;
        }
        String itemId = item.getItemId();
        String upgradeName = isArmor ? ReforgeEquip.getArmorUpgradeName(level) : ReforgeEquip.getUpgradeName(level);
        double multiplier = statMultiplierForLevel(level, isArmor);
        int percentBonus = (int) Math.round((multiplier - 1.0) * 100.0);
        DynamicTooltipUtils.addReforgeTooltip(itemId, upgradeName, level, percentBonus, isArmor);
        DynamicTooltipUtils.refreshAllPlayers();
    }

    private static ReforgeOutcome rollOutcome(int level) {
        double[] w = weights(level);
        double random = Math.random();
        double cumulative = 0.0;
        cumulative += w[0];
        if (random < cumulative) return new ReforgeOutcome(-1, OutcomeType.DEGRADE);
        cumulative += w[1];
        if (random < cumulative) return new ReforgeOutcome(0, OutcomeType.SAME);
        cumulative += w[2];
        if (random < cumulative) return new ReforgeOutcome(1, OutcomeType.UPGRADE);
        return new ReforgeOutcome(2, OutcomeType.JACKPOT);
    }

    private static double[] weights(int currentLevel) {
        double[] fromCfg = refinementConfig != null ? refinementConfig.getReforgeWeights(currentLevel) : null;
        if (fromCfg != null && fromCfg.length >= 4) {
            return fromCfg;
        }
        return DEFAULT_WEIGHTS[Math.max(0, Math.min(currentLevel, DEFAULT_WEIGHTS.length - 1))];
    }

    private static double breakChance(int currentLevel, boolean isArmor) {
        if (refinementConfig != null) {
            return isArmor
                    ? refinementConfig.getArmorBreakChance(currentLevel)
                    : refinementConfig.getBreakChance(currentLevel);
        }
        int idx = Math.max(0, Math.min(currentLevel, DEFAULT_BREAK_CHANCES.length - 1));
        return DEFAULT_BREAK_CHANCES[idx];
    }

    private static double effectiveBreakChance(int currentLevel, boolean isArmor, HammerSupportType hammerSupport) {
        double base = breakChance(currentLevel, isArmor);
        if (hammerSupport == null) return base;
        return Math.max(0.0, Math.min(1.0, base * hammerSupport.breakMultiplier));
    }

    private static double statMultiplierForLevel(int level, boolean isArmor) {
        int safeLevel = clampLevel(level);
        if (refinementConfig != null) {
            return isArmor
                    ? refinementConfig.getDefenseMultiplier(safeLevel)
                    : refinementConfig.getDamageMultiplier(safeLevel);
        }
        return isArmor
                ? ReforgeEquip.getDefenseMultiplier(safeLevel)
                : ReforgeEquip.getDamageMultiplier(safeLevel);
    }

    private static int clampLevel(int level) {
        return Math.max(0, Math.min(MAX_UPGRADE_LEVEL, level));
    }

    private static int countMaterialGlobally(Player player) {
        return countInContainer(player.getInventory().getStorage(), MATERIAL_ID)
                + countInContainer(player.getInventory().getHotbar(), MATERIAL_ID);
    }

    private static int countInContainer(ItemContainer container, String itemId) {
        if (container == null) return 0;
        int count = 0;
        for (short i = 0; i < container.getCapacity(); i++) {
            ItemStack stack = container.getItemStack(i);
            if (stack == null || stack.isEmpty()) continue;
            if (itemId.equalsIgnoreCase(stack.getItemId())) count += stack.getQuantity();
        }
        return count;
    }

    private static boolean consumeMaterialGlobally(Player player, int amount) {
        int remaining = amount;
        remaining = consumeFromContainer(player.getInventory().getStorage(), MATERIAL_ID, remaining);
        if (remaining > 0) remaining = consumeFromContainer(player.getInventory().getHotbar(), MATERIAL_ID, remaining);
        return remaining <= 0;
    }

    private static int consumeFromContainer(ItemContainer container, String itemId, int remaining) {
        if (container == null || remaining <= 0) return remaining;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            if (remaining <= 0) break;
            ItemStack stack = container.getItemStack(slot);
            if (stack == null || stack.isEmpty()) continue;
            if (!itemId.equalsIgnoreCase(stack.getItemId())) continue;
            int take = Math.min(remaining, stack.getQuantity());
            container.removeItemStackFromSlot(slot, take, false, false);
            remaining -= take;
        }
        return remaining;
    }

    private static boolean isHammerItem(String itemId) {
        return isIronHammerItem(itemId) || isThoriumHammerItem(itemId);
    }

    private static boolean isIronHammerItem(String itemId) {
        return UIItemUtils.isIronHammerItem(itemId, HAMMER_IRON_ID);
    }

    private static boolean isThoriumHammerItem(String itemId) {
        return itemId != null && HAMMER_THORIUM_ID.equalsIgnoreCase(itemId);
    }

    private static HammerSupportType getHammerSupportType(Entry support) {
        if (support == null) return null;
        if (isThoriumHammerItem(support.itemId)) return HammerSupportType.THORIUM;
        if (isIronHammerItem(support.itemId)) return HammerSupportType.IRON;
        return null;
    }

    private static int durabilityPercent(HammerSupportType hammerSupport) {
        if (hammerSupport == null) return 0;
        return (int) Math.round(hammerSupport.durabilityLossFraction * 100.0);
    }

    private static HammerUseResult applyHammerWear(Player player, Entry hammerEntry, double durabilityFraction) {
        if (player == null || hammerEntry == null || !isHammerItem(hammerEntry.itemId)) {
            return new HammerUseResult(false, false);
        }
        ItemContainer container = UIInventoryUtils.getContainer(player, hammerEntry.container == ContainerKind.HOTBAR);
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

    private static ItemStack readCurrentStack(Player player, Entry entry) {
        return UIInventoryUtils.readItem(player, entry.container == ContainerKind.HOTBAR, entry.slot);
    }

    private static void writeStack(Player player, Entry entry, ItemStack stack) {
        UIInventoryUtils.writeItem(player, entry.container == ContainerKind.HOTBAR, entry.slot, stack);
    }

    private static void removeEquipment(Player player, Entry entry) {
        UIInventoryUtils.removeItem(player, entry.container == ContainerKind.HOTBAR, entry.slot, 1);
    }

    private static String buildMetadata(Player player, Entry equipment) {
        if (equipment == null || equipment.item == null || equipment.item.isEmpty()) {
            return t(player, "ui.reforge.metadata_no_equipment");
        }
        ItemStack item = equipment.item;
        int level = ReforgeEquip.getLevelFromItem(item);

        StringBuilder sb = new StringBuilder();
        sb.append(t(player, "ui.reforge.metadata_line_name", equipment.displayName)).append("\n");
        sb.append(t(player, "ui.reforge.metadata_line_level", level));
        return sb.toString();
    }

    private static String buildMetadataName(Entry equipment) {
        if (equipment == null || equipment.item == null || equipment.item.isEmpty()) {
            return "-";
        }
        return equipment.displayName;
    }

    private static String buildMetadataLevel(Entry equipment) {
        if (equipment == null || equipment.item == null || equipment.item.isEmpty()) {
            return "-";
        }
        return "+" + ReforgeEquip.getLevelFromItem(equipment.item);
    }

    private static String buildSupportDurabilityText(Player player, Entry support) {
        if (support == null || support.item == null || support.item.isEmpty()) {
            return t(player, "ui.reforge.support_durability_none");
        }
        ItemStack item = support.item;
        double max = item.getMaxDurability();
        double cur = item.getDurability();
        if (max <= 0.0d) {
            return t(player, "ui.reforge.support_durability_na");
        }
        int maxInt = (int) Math.round(max);
        int curInt = (int) Math.round(cur);
        int percent = (int) Math.round((cur / max) * 100.0);
        percent = Math.max(0, Math.min(100, percent));
        return t(player, "ui.reforge.support_durability_value", percent, curInt, maxInt);
    }

    private static String buildMetadataCurrentStat(Player player, Entry equipment) {
        if (equipment == null || equipment.item == null || equipment.item.isEmpty()) {
            return t(player, "ui.reforge.metadata_current_stat_none");
        }
        ItemStack item = equipment.item;
        boolean isArmor = ReforgeEquip.isArmor(item) && !ReforgeEquip.isWeapon(item);
        int level = ReforgeEquip.getLevelFromItem(item);
        double currentMult = statMultiplierForLevel(level, isArmor);
        String statLabel = isArmor ? t(player, "ui.reforge.stat_defense") : t(player, "ui.reforge.stat_damage");
        return t(player, "ui.reforge.metadata_current_stat_value", statLabel, format3(currentMult), formatPercent(currentMult - 1.0));
    }

    private static String buildMetadataBaseStat(Player player, Entry equipment) {
        if (equipment == null || equipment.item == null || equipment.item.isEmpty()) {
            return t(player, "ui.reforge.metadata_base_stat_none");
        }
        ItemStack item = equipment.item;
        String itemId = item.getItemId();
        if (itemId == null || itemId.isBlank()) {
            return t(player, "ui.reforge.metadata_base_stat_none");
        }
        boolean isArmor = ReforgeEquip.isArmor(item) && !ReforgeEquip.isWeapon(item);
        int level = ReforgeEquip.getLevelFromItem(item);
        SocketData socketData = SocketManager.getSocketData(item);
        if (isArmor) {
            EquipmentDamageTooltipMath.StatSummary summary =
                    EquipmentDamageTooltipMath.computeArmorDefenseSummary(itemId, level, socketData);
            if (summary.getBaseValue() <= 0.0) {
                return t(player, "ui.reforge.metadata_base_defense_na");
            }
            return t(player, "ui.reforge.metadata_base_defense_value",
                    formatDamageValue(summary.getBaseValue()),
                    formatDamageValue(summary.getBuffedValue()));
        }
        EquipmentDamageTooltipMath.StatSummary summary =
                EquipmentDamageTooltipMath.computeWeaponDamageSummary(itemId, level, socketData, 1.0);
        if (summary.getBaseValue() <= 0.0) {
            return t(player, "ui.reforge.metadata_base_damage_na");
        }
        return t(player, "ui.reforge.metadata_base_damage_value",
                formatDamageValue(summary.getBaseValue()),
                formatDamageValue(summary.getBuffedValue()));
    }

    private static String loadTemplate() {
        return UITemplateUtils.loadTemplate(
                ReforgeBenchUI.class,
                TEMPLATE_PATH,
                "<div><p>Reforge Bench UI template missing.</p></div>",
                "ReforgeBenchUI");
    }

    private static Entry findByKey(List<Entry> entries, String key) {
        Entry exact = resolveSelection(entries, key);
        if (exact != null) return exact;
        if (entries.isEmpty()) return null;
        return entries.get(0);
    }

    private static Entry resolveSelection(List<Entry> entries, String value) {
        return HyUIReflectionUtils.resolveIndexSelection(entries, value);
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

    private static String format3(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String formatPercent(double fraction) {
        return String.format(Locale.ROOT, "%.1f%%", fraction * 100.0);
    }

    private static String formatDamageValue(double value) {
        double safe = Math.max(0.0, value);
        long rounded = Math.round(safe);
        if (Math.abs(safe - rounded) < 0.05) {
            return Long.toString(rounded);
        }
        return String.format(Locale.ROOT, "%.1f", safe);
    }

    private static String t(Player player, String key, Object... params) {
        return LangLoader.getUITranslation(player, key, params);
    }

    private static String hammerLabel(Player player, HammerSupportType hammerSupport) {
        if (hammerSupport == null) {
            return "";
        }
        return t(player, hammerSupport.labelKey);
    }
}
