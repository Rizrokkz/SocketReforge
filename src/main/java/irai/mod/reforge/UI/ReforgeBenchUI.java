package irai.mod.reforge.UI;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import irai.mod.reforge.Common.EquipmentDamageTooltipMath;
import irai.mod.reforge.Common.UI.HyUIEditUtils;
import irai.mod.reforge.Common.UI.HyUIReflectionUtils;
import irai.mod.reforge.Common.UI.UIInventoryUtils;
import irai.mod.reforge.Common.UI.UIItemUtils;
import irai.mod.reforge.Common.UI.UITemplateUtils;
import irai.mod.reforge.Config.RefinementConfig;
import irai.mod.reforge.Config.SFXConfig;
import irai.mod.reforge.Interactions.ReforgeEquip;
import irai.mod.reforge.Socket.EssenceEffect;
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

    private static final String DEFAULT_MATERIAL_ID = "Refinement_Glob";
    private static final String ANTI_DEGRADATION_ID = "Anti_Degradation";
    private static final String SUPPORT_KEY_SEPARATOR = ";";
    private static final double ANTI_DEGRADATION_BLOCK_CHANCE = 0.80d;
    private static final String HAMMER_IRON_ID = "Tool_Hammer_Iron";
    private static final String HAMMER_THORIUM_ID = "Tool_Hammer_Thorium";
    private static final int DEFAULT_MATERIAL_COST = 3;
    private static final int DEFAULT_MAX_UPGRADE_LEVEL = 3;
    private static final double HAMMER_IRON_BREAK_MULTIPLIER = 0.50d;
    private static final double HAMMER_THORIUM_BREAK_MULTIPLIER = 0.30d;
    private static final double HAMMER_IRON_DURABILITY_LOSS_FRACTION = 0.05d;
    private static final double HAMMER_THORIUM_DURABILITY_LOSS_FRACTION = 0.15d;
    private static final double DEGRADE_B_SHARE_OF_DEGRADE = 0.20d;
    private static final double JACKPOT_B_SHARE_OF_JACKPOT = 0.20d;

    private static final double[] DEFAULT_BREAK_CHANCES = {0.010, 0.050, 0.075};
    private static final double[][] DEFAULT_WEIGHTS = {
            {0.00, 0.65, 0.34, 0.01},
            {0.35, 0.45, 0.19, 0.01},
            {0.60, 0.30, 0.095, 0.005},
    };

    private static final SFXConfig sfxConfig = new SFXConfig();
    private static RefinementConfig refinementConfig;
    private static volatile List<String> refinementMaterialIds = List.of(DEFAULT_MATERIAL_ID);
    private static boolean hyuiAvailable = false;

    private static final Map<PlayerRef, Object> openPages = new ConcurrentHashMap<>();
    private static final Map<PlayerRef, SelectionState> pendingSelections = new ConcurrentHashMap<>();
    private static final Map<PlayerRef, Boolean> processingPlayers = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final int PROCESS_DURATION_MS = 1000;
    private static final int PROGRESS_TICK_MS = 50;
    private static final int MAX_RENDERED_DAMAGE_ROWS = 24;

    private enum ContainerKind { HOTBAR, STORAGE }
    private enum OutcomeType { DEGRADE, DEGRADE_B, SAME, UPGRADE, JACKPOT, JACKPOT_B }
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

    private static final class MaterialRequirement {
        final String itemId;
        final int cost;
        final String displayName;

        MaterialRequirement(String itemId, int cost, String displayName) {
            this.itemId = itemId;
            this.cost = cost;
            this.displayName = displayName;
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

    private static final class OutcomeWeights {
        final double degrade;
        final double degradeB;
        final double same;
        final double upgrade;
        final double jackpot;
        final double jackpotB;
        final double total;

        OutcomeWeights(double degrade, double degradeB, double same, double upgrade, double jackpot, double jackpotB) {
            this.degrade = degrade;
            this.degradeB = degradeB;
            this.same = same;
            this.upgrade = upgrade;
            this.jackpot = jackpot;
            this.jackpotB = jackpotB;
            this.total = degrade + degradeB + same + upgrade + jackpot + jackpotB;
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
        if (config != null) {
            List<String> ids = config.getMaterialItemIds();
            if (ids != null && !ids.isEmpty()) {
                refinementMaterialIds = ids;
            } else {
                refinementMaterialIds = List.of(DEFAULT_MATERIAL_ID);
            }
        } else {
            refinementMaterialIds = List.of(DEFAULT_MATERIAL_ID);
        }
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
            player.getPlayerRef().sendMessage(Message.raw("<color=#FF5555>HyUI not installed - " + LangLoader.getUITranslation(player, "ui.reforge.title") + " disabled."));
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
            if (isRefinementMaterial(itemId)) {
                materials.add(entry);
            }
            if (isReforgeSupportItem(itemId)) {
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

            Object activating = eventBindingClass.getField("Activating").get(null);

            String html = buildHtml(player, snapshot, state);
            Object pageBuilder = pageForPlayer.invoke(null, playerRef);
            pageBuilder = fromHtml.invoke(pageBuilder, html);

            final Player finalPlayer = player;
            final Snapshot finalSnapshot = snapshot;

            for (int i = 0; i < finalSnapshot.equipments.size(); i++) {
                final int index = i;
                addListener.invoke(pageBuilder, equipmentCardButtonId(index), activating,
                        (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                            SelectionState current = pendingSelections.get(finalPlayer.getPlayerRef());
                            pendingSelections.put(finalPlayer.getPlayerRef(), new SelectionState(
                                    String.valueOf(index),
                                    current != null ? current.materialKey : null,
                                    current != null ? current.supportKey : "",
                                    null,
                                    0,
                                    false));
                            finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));
                        });
            }

            List<RefinementConfig.MaterialTier> materialTiers = materialTierCards();
            for (int i = 0; i < materialTiers.size(); i++) {
                final String materialId = materialTiers.get(i).itemId;
                addListener.invoke(pageBuilder, materialCardButtonId(i), activating,
                        (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                            SelectionState current = pendingSelections.get(finalPlayer.getPlayerRef());
                            pendingSelections.put(finalPlayer.getPlayerRef(), new SelectionState(
                                    current != null ? current.equipmentKey : null,
                                    materialId,
                                    current != null ? current.supportKey : "",
                                    null,
                                    0,
                                    false));
                            finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));
                        });
            }

            addListener.invoke(pageBuilder, supportNoneButtonId(), activating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        SelectionState current = pendingSelections.get(finalPlayer.getPlayerRef());
                        pendingSelections.put(finalPlayer.getPlayerRef(), new SelectionState(
                                current != null ? current.equipmentKey : null,
                                current != null ? current.materialKey : null,
                                "",
                                null,
                                0,
                                false));
                        finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));
                    });

            List<Entry> supportCards = combinedSupports(finalSnapshot.supports);
            for (int i = 0; i < supportCards.size(); i++) {
                final String supportId = supportCards.get(i).itemId;
                addListener.invoke(pageBuilder, supportCardButtonId(i), activating,
                        (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                            SelectionState current = pendingSelections.get(finalPlayer.getPlayerRef());
                            pendingSelections.put(finalPlayer.getPlayerRef(), new SelectionState(
                                    current != null ? current.equipmentKey : null,
                                    current != null ? current.materialKey : null,
                                    toggleSupportKey(current != null ? current.supportKey : "", supportId),
                                    null,
                                    0,
                                    false));
                            finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));
                        });
            }

            addListener.invoke(pageBuilder, "processButton", activating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        PlayerRef ref = finalPlayer.getPlayerRef();
                        if (Boolean.TRUE.equals(processingPlayers.get(ref))) return;

                        SelectionState current = pendingSelections.get(ref);
                        String eqVal = current != null ? current.equipmentKey : null;
                        String matVal = current != null ? current.materialKey : null;
                        String supVal = current != null ? current.supportKey : "";
                        Entry equipment = resolveSelection(finalSnapshot.equipments, eqVal);
                        Entry material = resolveMaterialKey(finalSnapshot.materials, matVal);
                        List<Entry> supports = resolveSupportKeys(finalSnapshot.supports, supVal);

                        processingPlayers.put(ref, true);
                        pendingSelections.put(ref, new SelectionState(eqVal, matVal, supVal,
                                LangLoader.getUITranslation(finalPlayer, "ui.reforge.status_processing"), 0, true));
                        sfxConfig.playReforgeStart(finalPlayer);
                        HyUIEditUtils.editText(ctxObj, "statusLabel", LangLoader.getUITranslation(finalPlayer, "ui.reforge.status_processing"));
                        HyUIEditUtils.editProgress(ctxObj, "refineProgress", 0);
                        HyUIEditUtils.editDisabled(ctxObj, "processButton", true);

                        for (int elapsed = PROGRESS_TICK_MS; elapsed < PROCESS_DURATION_MS; elapsed += PROGRESS_TICK_MS) {
                            final int delay = elapsed;
                            final int timedProgress = Math.min(99, (int) Math.round(((delay + PROGRESS_TICK_MS) * 100.0) / PROCESS_DURATION_MS));
                            scheduler.schedule(() -> finalPlayer.getWorld().execute(() -> {
                                if (!Boolean.TRUE.equals(processingPlayers.get(ref))) return;
                                pendingSelections.put(ref, new SelectionState(eqVal, matVal, supVal,
                                        LangLoader.getUITranslation(finalPlayer, "ui.reforge.status_processing"),
                                        timedProgress, true));
                                HyUIEditUtils.editProgress(ctxObj, "refineProgress", timedProgress);
                            }), delay, TimeUnit.MILLISECONDS);
                        }

                        scheduler.schedule(() -> finalPlayer.getWorld().execute(() -> {
                            try {
                                ProcessResult result = processSelection(finalPlayer, equipment, material, supports);
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

        Entry selectedEquipment = resolveSelection(snapshot.equipments, eqKey);
        Entry selectedMaterial = resolveMaterialKey(snapshot.materials, matKey);
        List<Entry> selectedSupports = resolveSupportKeys(snapshot.supports, supKey);

        MaterialRequirement requiredMaterial = getMaterialRequirement(player, selectedEquipment);
        if (selectedMaterial == null && requiredMaterial != null) {
            selectedMaterial = findFirstByItemId(snapshot.materials, requiredMaterial.itemId);
            matKey = requiredMaterial.itemId;
        }
        MaterialRequirement materialRequirement = getMaterialRequirement(player, selectedEquipment, selectedMaterial);
        int selectedMaterialCount = materialRequirement != null
                ? countMaterials(snapshot.materials, materialRequirement.itemId)
                : 0;
        Entry materialEntry = materialRequirement != null
                ? findFirstByItemId(snapshot.materials, materialRequirement.itemId)
                : null;
        String materialName = materialRequirement != null
                ? resolveMaterialName(player, materialRequirement.itemId, materialEntry)
                : LangLoader.getUITranslation(player, "ui.reforge.material");
        int materialCost = materialRequirement != null ? materialRequirement.cost : DEFAULT_MATERIAL_COST;

        Preview preview = buildPreview(player, selectedEquipment, selectedMaterial, selectedSupports);
        if (!processing) {
            if (selectedEquipment == null) {
                status = LangLoader.getUITranslation(player, "ui.reforge.status_no_equipment");
            } else if (preview.currentLevel >= getMaxUpgradeLevel()) {
                status = LangLoader.getUITranslation(player, "ui.reforge.status_max_level");
            } else if (selectedMaterialCount < materialCost) {
                status = LangLoader.getUITranslation(player, "ui.reforge.status_need_material", materialCost, materialName);
            } else if (LangLoader.getUITranslation(player, "ui.reforge.status_idle").equals(status)) {
                status = LangLoader.getUITranslation(player, "ui.reforge.status_ready");
            }
        }

        String html = loadTemplate();
        
        // UI translations
        html = html.replace("{{title}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.title")));
        html = html.replace("{{equipmentPanelTitle}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.equipment")));
        html = html.replace("{{equipmentPanelHint}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.equipment")));
        html = html.replace("{{supportPanelTitle}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.support")));
        html = html.replace("{{chancePanelTitle}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.expected_outcome")));
        html = html.replace("{{equipmentLabel}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.equipment")));
        html = html.replace("{{materialLabel}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.material")));
        html = html.replace("{{supportLabel}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.support")));
        html = html.replace("{{metadataLabel}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.metadata_name")));
        html = html.replace("{{currentDamageLabel}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.current_damage")));
        html = html.replace("{{metadataNameLabel}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.metadata_name")));
        html = html.replace("{{metadataLevelLabel}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.metadata_level")));
        html = html.replace("{{metadataCurrentStatLabel}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.metadata_current_stat")));
        html = html.replace("{{metadataBaseStatLabel}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.metadata_base_stat")));
        html = html.replace("{{expectedOutcomeLabel}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.expected_outcome")));
        html = html.replace("{{expectedDamageLabel}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.expected_damage")));
        html = html.replace("{{refineProgressLabel}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.refine_progress")));
        html = html.replace("{{consumesMaterialText}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.consumes_material", materialCost, materialName)));
        html = html.replace("{{ironHammerInfoText}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.iron_hammer_info")));
        html = html.replace("{{thoriumHammerInfoText}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.thorium_hammer_info")));
        html = html.replace("{{refineButtonText}}", escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.button_refine")));
        
        // Dynamic content
        html = html.replace("{{equipmentCards}}", buildEquipmentCards(player, snapshot.equipments, eqKey));
        html = html.replace("{{materialCards}}", buildMaterialCards(player, snapshot.materials, matKey));
        html = html.replace("{{supportCards}}", buildSupportCards(player, snapshot.supports, supKey));
        html = html.replace("{{selectedEquipmentIcon}}", buildSelectedEquipmentIcon(selectedEquipment));
        html = html.replace("{{supportDurabilityText}}", escapeHtml(buildSupportDurabilityText(player, selectedHammerSupport(selectedSupports))));
        html = html.replace("{{materialCountText}}", LangLoader.getUITranslation(player, "ui.reforge.material_count", materialName, selectedMaterialCount));
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
        html = html.replace("{{currentDamageRows}}", buildCurrentDamageRowsHtml(player, selectedEquipment));
        html = html.replace("{{expectedDamageRows}}", buildExpectedDamageRowsHtml(player, selectedEquipment));
        html = html.replace("{{metadataNameText}}", escapeHtml(buildMetadataName(selectedEquipment)));
        html = html.replace("{{metadataLevelText}}", escapeHtml(buildMetadataLevel(selectedEquipment)));
        html = html.replace("{{metadataCurrentStatText}}", escapeHtml(buildMetadataCurrentStat(player, selectedEquipment)));
        html = html.replace("{{metadataBaseStatText}}", escapeHtml(buildMetadataBaseStat(player, selectedEquipment)));
        html = html.replace("{{metadataText}}", escapeHtml(buildMetadata(player, selectedEquipment)));
        html = html.replace("{{progressValue}}", String.valueOf(progress));
        html = html.replace("{{statusText}}", escapeHtml(status));
        html = html.replace("{{processDisabledAttr}}", shouldDisable(processing, selectedEquipment, selectedMaterialCount, materialCost) ? "disabled=\"true\"" : "");
        pendingSelections.put(player.getPlayerRef(), new SelectionState(eqKey, matKey, supKey == null ? "" : supKey, status, progress, processing));
        return html;
    }

    private static boolean shouldDisable(boolean processing, Entry selectedEquipment, int materialCount, int materialCost) {
        if (processing) return true;
        if (selectedEquipment == null) return true;
        int level = ReforgeEquip.getLevelFromItem(selectedEquipment.item);
        if (level >= getMaxUpgradeLevel()) return true;
        return materialCount < materialCost;
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

    private static String buildEquipmentCards(Player player, List<Entry> entries, String selectedKey) {
        if (entries == null || entries.isEmpty()) {
            return "<p style=\"text-align:center;\">" + escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.no_equipment")) + "</p>";
        }
        StringBuilder armor = new StringBuilder();
        StringBuilder weapons = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry == null || entry.item == null || entry.item.isEmpty()) continue;
            String html = buildEquipmentCard(entry, i, String.valueOf(i).equals(selectedKey));
            if (ReforgeEquip.isArmor(entry.item) && !ReforgeEquip.isWeapon(entry.item)) {
                armor.append(html);
            } else {
                weapons.append(html);
            }
        }
        StringBuilder sb = new StringBuilder();
        if (armor.length() > 0) {
            sb.append("<p style=\"font-weight:bold;\">Armor</p>").append(armor);
        }
        if (weapons.length() > 0) {
            sb.append("<p style=\"font-weight:bold;\">Weapon</p>").append(weapons);
        }
        return sb.toString();
    }

    private static String buildEquipmentCard(Entry entry, int index, boolean selected) {
        int level = ReforgeEquip.getLevelFromItem(entry.item);
        String tierColor = tierColorForLevel(level);
        return "<button id=\"" + equipmentCardButtonId(index) + "\" class=\"raw-button\" style=\"anchor-width:360; anchor-height:82; padding:0; border:0; background-color:#00000000;\">"
                + "<div style=\"anchor-width:360; anchor-height:82; layout-mode:full; background-color:" + (selected ? "#2B3F6D" : "#151526") + ";\">"
                + "<div style=\"anchor-width:344; anchor-height:66; layout-mode:left; padding:8;\">"
                + "<div style=\"anchor-width:66; anchor-height:66; padding:3; background-color:" + tierColor + ";\">"
                + "<div style=\"anchor-width:60; anchor-height:60; background-image:url('slot_bg.png'); background-size:100% 100%; background-repeat:no-repeat; padding:3;\">"
                + "<span class=\"item-icon\" data-hyui-item-id=\"" + escapeHtml(entry.itemId) + "\" style=\"anchor-width:54; anchor-height:54;\"></span>"
                + "</div>"
                + "</div>"
                + "<div style=\"anchor-width:14;\"></div>"
                + "<div style=\"anchor-width:256; anchor-height:60; layout-mode:top; spacing:3;\">"
                + "<div style=\"anchor-width:256; layout-mode:left;\">"
                + "<p style=\"anchor-width:184; font-weight:bold; white-space:nowrap;\">" + escapeHtml(entry.displayName) + "</p>"
                + "<p style=\"anchor-width:62; text-align:right; font-size:11; color:#FFD24D; visibility:" + (selected ? "shown" : "hidden") + ";\">SELECTED</p>"
                + "</div>"
                + "<p style=\"font-size:12;\">+" + level + " | " + tierLabelForLevel(level) + "</p>"
                + "</div></div></div></button>";
    }

    private static String buildMaterialCards(Player player, List<Entry> entries, String selectedMaterialId) {
        StringBuilder sb = new StringBuilder();
        List<RefinementConfig.MaterialTier> tiers = materialTierCards();
        for (int i = 0; i < tiers.size(); i++) {
            RefinementConfig.MaterialTier tier = tiers.get(i);
            String itemId = tier.itemId == null || tier.itemId.isBlank() ? DEFAULT_MATERIAL_ID : tier.itemId;
            int count = countMaterials(entries, itemId);
            boolean selected = itemId.equalsIgnoreCase(selectedMaterialId);
            String name = resolveMaterialName(player, itemId, findFirstByItemId(entries, itemId));
            sb.append("<button id=\"").append(materialCardButtonId(i)).append("\" class=\"raw-button\" style=\"anchor-width:92; anchor-height:92; padding:0; border:0; background-color:#00000000;\">")
                    .append("<div style=\"anchor-width:92; anchor-height:92; padding:4; background-color:")
                    .append(selected ? "#FFD24D" : tierColorForIndex(i))
                    .append(";\">")
                    .append("<div style=\"anchor-width:84; anchor-height:84; layout-mode:full; background-color:#151526; padding:4;\">")
                    .append("<span class=\"item-icon\" data-hyui-item-id=\"").append(escapeHtml(itemId)).append("\" style=\"anchor-width:58; anchor-height:58;\"></span>")
                    .append("<div style=\"layout-mode:top;\"><div style=\"flex-weight:1;\"></div><div style=\"layout-mode:left;\"><div style=\"flex-weight:1;\"></div>")
                    .append("<p style=\"anchor-width:34; anchor-height:18; text-align:center; background-color:#000000AA; font-size:12;\">x").append(count).append("</p>")
                    .append("</div></div></div></div></button>");
        }
        return sb.toString();
    }

    private static String buildSupportCards(Player player, List<Entry> entries, String selectedSupportId) {
        StringBuilder sb = new StringBuilder();
        Set<String> selectedSupportIds = supportSelectionSet(selectedSupportId);
        boolean noneSelected = selectedSupportIds.isEmpty();
        sb.append("<button id=\"").append(supportNoneButtonId()).append("\" class=\"raw-button\" style=\"anchor-width:360; anchor-height:86; padding:0; border:0; background-color:#00000000;\">")
                .append("<div style=\"anchor-width:344; anchor-height:70; layout-mode:left; background-color:")
                .append(noneSelected ? "#2B3F6D" : "#151526")
                .append(";\">")
                .append("<div style=\"anchor-width:10;\"></div>")
                .append("<div style=\"anchor-width:56; anchor-height:56; background-image:url('slot_bg.png'); background-size:100% 100%;\"></div>")
                .append("<div style=\"anchor-width:16;\"></div><div style=\"anchor-width:250; layout-mode:top;\">")
                .append("<p style=\"font-weight:bold;\">").append(escapeHtml(LangLoader.getUITranslation(player, "ui.reforge.support_none"))).append("</p>")
                .append("<p style=\"font-size:11;\">No support material.</p>")
                .append("</div></div></button>");

        List<Entry> supports = combinedSupports(entries);
        for (int i = 0; i < supports.size(); i++) {
            Entry entry = supports.get(i);
            boolean selected = selectedSupportIds.contains(normalizeSupportId(entry.itemId));
            sb.append("<button id=\"").append(supportCardButtonId(i)).append("\" class=\"raw-button\" style=\"anchor-width:360; anchor-height:98; padding:0; border:0; background-color:#00000000;\">")
                    .append("<div style=\"anchor-width:344; anchor-height:82; layout-mode:left; background-color:")
                    .append(selected ? "#2B3F6D" : "#151526")
                    .append(";\">")
                    .append("<div style=\"anchor-width:10;\"></div>")
                    .append("<div style=\"anchor-width:60; anchor-height:60; padding:2; background-color:#5A451E;\">")
                    .append("<span class=\"item-icon\" data-hyui-item-id=\"").append(escapeHtml(entry.itemId)).append("\" style=\"anchor-width:56; anchor-height:56;\"></span>")
                    .append("</div><div style=\"anchor-width:16;\"></div><div style=\"anchor-width:246; layout-mode:top; spacing:2;\">")
                    .append("<p style=\"font-weight:bold; white-space:nowrap;\">").append(escapeHtml(entry.displayName)).append("</p>")
                    .append("<p style=\"font-size:11;\">").append(escapeHtml(supportDescription(player, entry))).append("</p>")
                    .append("<p style=\"font-size:12;\">Held: ").append(entry.quantity).append("</p>")
                    .append("</div></div></button>");
        }
        return sb.toString();
    }

    private static String buildSelectedEquipmentIcon(Entry selectedEquipment) {
        if (selectedEquipment == null || selectedEquipment.item == null || selectedEquipment.item.isEmpty()) {
            return "<div style=\"anchor-width:86; anchor-height:86; padding:3; background-color:#2b2b3a;\">"
                    + "<div style=\"anchor-width:80; anchor-height:80; background-image:url('slot_bg.png'); background-size:100% 100%;\"></div>"
                    + "</div>";
        }
        int level = ReforgeEquip.getLevelFromItem(selectedEquipment.item);
        return "<div style=\"anchor-width:92; anchor-height:92; padding:4; background-color:" + tierColorForLevel(level) + ";\">"
                + "<div style=\"anchor-width:84; anchor-height:84; background-color:#151526; padding:2;\">"
                + "<span class=\"item-icon\" data-hyui-item-id=\"" + escapeHtml(selectedEquipment.itemId) + "\" style=\"anchor-width:80; anchor-height:80;\"></span>"
                + "</div></div>";
    }

    private static List<RefinementConfig.MaterialTier> materialTierCards() {
        List<RefinementConfig.MaterialTier> tiers = refinementConfig != null ? refinementConfig.getMaterialTiers() : List.of();
        if (tiers == null || tiers.isEmpty()) {
            return List.of(
                    new RefinementConfig.MaterialTier(0, 0, "Refinement_Glob", DEFAULT_MATERIAL_COST),
                    new RefinementConfig.MaterialTier(1, 1, "Refinement_Glob_Plus", DEFAULT_MATERIAL_COST),
                    new RefinementConfig.MaterialTier(2, getMaxUpgradeLevel(), "Resonant_Glob", DEFAULT_MATERIAL_COST));
        }
        List<RefinementConfig.MaterialTier> result = new ArrayList<>();
        for (RefinementConfig.MaterialTier tier : tiers) {
            if (tier != null && tier.itemId != null && !tier.itemId.isBlank()) {
                result.add(tier);
            }
            if (result.size() >= 3) break;
        }
        String[] fallbackIds = {"Refinement_Glob", "Refinement_Glob_Plus", "Resonant_Glob"};
        for (int i = result.size(); i < 3; i++) {
            result.add(new RefinementConfig.MaterialTier(i, i, fallbackIds[i], DEFAULT_MATERIAL_COST));
        }
        return result;
    }

    private static List<Entry> combinedSupports(List<Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        Map<String, Entry> combined = new LinkedHashMap<>();
        for (Entry entry : entries) {
            if (entry == null || entry.itemId == null || entry.itemId.isBlank()) continue;
            Entry existing = combined.get(entry.itemId.toLowerCase(Locale.ROOT));
            if (existing == null) {
                combined.put(entry.itemId.toLowerCase(Locale.ROOT), entry);
            } else {
                combined.put(entry.itemId.toLowerCase(Locale.ROOT),
                        new Entry(existing.container, existing.slot, existing.item, existing.itemId,
                                existing.quantity + entry.quantity, existing.displayName));
            }
        }
        return new ArrayList<>(combined.values());
    }

    private static Entry resolveMaterialKey(List<Entry> entries, String key) {
        Entry indexed = resolveSelection(entries, key);
        if (indexed != null) return indexed;
        return findFirstByItemId(entries, key);
    }

    private static Entry resolveSupportKey(List<Entry> entries, String key) {
        if (key == null || key.isBlank()) return null;
        Entry indexed = resolveSelection(entries, key);
        if (indexed != null) return indexed;
        return findFirstByItemId(entries, key);
    }

    private static List<Entry> resolveSupportKeys(List<Entry> entries, String key) {
        Set<String> selectedIds = supportSelectionSet(key);
        if (selectedIds.isEmpty()) {
            return List.of();
        }
        List<Entry> resolved = new ArrayList<>();
        for (String selectedId : selectedIds) {
            Entry support = resolveSupportKey(entries, selectedId);
            if (support != null) {
                resolved.add(support);
            }
        }
        return resolved;
    }

    private static String toggleSupportKey(String currentKey, String itemId) {
        String normalized = normalizeSupportId(itemId);
        if (normalized.isBlank()) {
            return normalizeSupportKey(currentKey);
        }
        Set<String> selected = supportSelectionSet(currentKey);
        if (selected.contains(normalized)) {
            selected.remove(normalized);
        } else {
            selected.add(normalized);
        }
        return String.join(SUPPORT_KEY_SEPARATOR, selected);
    }

    private static Set<String> supportSelectionSet(String key) {
        Set<String> selected = new LinkedHashSet<>();
        if (key == null || key.isBlank()) {
            return selected;
        }
        for (String part : key.split(SUPPORT_KEY_SEPARATOR)) {
            String normalized = normalizeSupportId(part);
            if (!normalized.isBlank()) {
                selected.add(normalized);
            }
        }
        return selected;
    }

    private static String normalizeSupportKey(String key) {
        return String.join(SUPPORT_KEY_SEPARATOR, supportSelectionSet(key));
    }

    private static String normalizeSupportId(String itemId) {
        return itemId == null ? "" : itemId.trim().toLowerCase(Locale.ROOT);
    }

    private static String equipmentCardButtonId(int index) {
        return "reforgeEquipmentCard_" + index;
    }

    private static String materialCardButtonId(int index) {
        return "reforgeMaterialCard_" + index;
    }

    private static String supportCardButtonId(int index) {
        return "reforgeSupportCard_" + index;
    }

    private static String supportNoneButtonId() {
        return "reforgeSupportNoneCard";
    }

    private static String tierLabelForLevel(int level) {
        return "T" + Math.max(1, Math.min(3, tierIndexForLevel(level) + 1));
    }

    private static int tierIndexForLevel(int level) {
        if (refinementConfig != null) {
            int index = refinementConfig.getMaterialTierIndexForLevel(level);
            if (index >= 0) return Math.min(2, index);
        }
        int max = Math.max(1, getMaxUpgradeLevel());
        if (level <= max / 3) return 0;
        if (level <= (max * 2) / 3) return 1;
        return 2;
    }

    private static String tierColorForLevel(int level) {
        return tierColorForIndex(tierIndexForLevel(level));
    }

    private static String tierColorForIndex(int index) {
        return switch (Math.max(0, Math.min(2, index))) {
            case 0 -> "#5A451E";
            case 1 -> "#55FFFF";
            default -> "#AA55FF";
        };
    }

    private static String supportDescription(Player player, Entry support) {
        HammerSupportType type = getHammerSupportType(support);
        if (isAntiDegradationItem(support)) {
            return LangLoader.getUITranslation(player, "ui.reforge.anti_degradation_info");
        }
        if (type == HammerSupportType.THORIUM) {
            return LangLoader.getUITranslation(player, "ui.reforge.thorium_hammer_info");
        }
        if (type == HammerSupportType.IRON) {
            return LangLoader.getUITranslation(player, "ui.reforge.iron_hammer_info");
        }
        return LangLoader.getUITranslation(player, "ui.reforge.support_guard");
    }

    private static Preview buildPreview(Player player, Entry equipment, Entry material, List<Entry> supports) {
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
        Entry hammerEntry = selectedHammerSupport(supports);
        HammerSupportType hammerSupport = getHammerSupportType(hammerEntry);
        boolean antiDegradationSupport = hasAntiDegradationSupport(supports);
        int maxLevel = getMaxUpgradeLevel();
        int level = Math.max(0, Math.min(maxLevel, ReforgeEquip.getLevelFromItem(item)));
        MaterialRequirement requirement = getMaterialRequirement(player, equipment, material);
        String materialId = requirement != null ? requirement.itemId : DEFAULT_MATERIAL_ID;
        String typeLabel = isArmor ? t(player, "ui.reforge.type_armor") : t(player, "ui.reforge.type_weapon");
        String refineName = isArmor ? ReforgeEquip.getArmorUpgradeName(level) : ReforgeEquip.getUpgradeName(level);
        double softcoreMultiplier = ReforgeEquip.getSoftcoreStatMultiplier(item);
        double currentMult = statMultiplierForLevel(level, isArmor) * softcoreMultiplier;
        String statLabel = isArmor ? t(player, "ui.reforge.stat_defense") : t(player, "ui.reforge.stat_damage");
        String refineSuffix = refineName == null || refineName.isBlank() ? "" : " (" + refineName + ")";

        String currentStats = t(player, "ui.reforge.preview_type", typeLabel) + "\n"
                + t(player, "ui.reforge.preview_refine_level", level, refineSuffix) + "\n"
                + t(player, "ui.reforge.preview_current_stat", statLabel, format3(currentMult),
                    formatPercent(currentMult - 1.0));

        if (level >= maxLevel) {
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

        double breakChance = effectiveBreakChance(level, isArmor, materialId, hammerSupport);
        double softcoreBreakProtectionChance = ReforgeEquip.getSoftcoreBreakProtectionChance(materialId);
        OutcomeWeights w = outcomeWeights(level, materialId);
        double survive = Math.max(0.0, 1.0 - breakChance);

        int degBLevel = clampLevel(level - 2);
        int degLevel = clampLevel(level - 1);
        int sameLevel = clampLevel(level);
        int upLevel = clampLevel(level + 1);
        int jackLevel = clampLevel(level + 2);
        int jackBLevel = clampLevel(level + 3);
        double pSoftBreak = breakChance * softcoreBreakProtectionChance;

        double mDegB = statMultiplierForLevel(degBLevel, isArmor) * softcoreMultiplier;
        double mDeg = statMultiplierForLevel(degLevel, isArmor) * softcoreMultiplier;
        double mSame = statMultiplierForLevel(sameLevel, isArmor) * softcoreMultiplier;
        double mUp = statMultiplierForLevel(upLevel, isArmor) * softcoreMultiplier;
        double mJack = statMultiplierForLevel(jackLevel, isArmor) * softcoreMultiplier;
        double mJackB = statMultiplierForLevel(jackBLevel, isArmor) * softcoreMultiplier;
        double mSoftBreak = 0.0;
        if (pSoftBreak > 0.0) {
            ItemStack softenedPreview = ReforgeEquip.previewSoftcoreBreakPenalty(item);
            mSoftBreak = ReforgeEquip.getEffectiveRefinementMultiplier(softenedPreview, isArmor);
        }

        double pDeg = survive * w.degrade;
        double pDegB = survive * w.degradeB;
        double pSame = survive * w.same;
        double pUp = survive * w.upgrade;
        double pJack = survive * w.jackpot;
        double pJackB = survive * w.jackpotB;
        if (antiDegradationSupport) {
            double blockedDegrade = (pDeg + pDegB) * ANTI_DEGRADATION_BLOCK_CHANCE;
            pSame += blockedDegrade;
            pDeg *= 1.0 - ANTI_DEGRADATION_BLOCK_CHANCE;
            pDegB *= 1.0 - ANTI_DEGRADATION_BLOCK_CHANCE;
        }
        double expectedWithBreak = (pSoftBreak * mSoftBreak)
                + (pDegB * mDegB)
                + (pDeg * mDeg)
                + (pSame * mSame)
                + (pUp * mUp)
                + (pJack * mJack)
                + (pJackB * mJackB);

        String expectedStats = t(player, "ui.reforge.chance_break") + ": " + formatPercent(breakChance) + "\n"
                + t(player, "ui.reforge.chance_degrade") + ": " + formatPercent(pDeg + pDegB) + "\n"
                + t(player, "ui.reforge.chance_same") + ": " + formatPercent(pSame) + "\n"
                + t(player, "ui.reforge.chance_upgrade") + ": " + formatPercent(pUp) + "\n"
                + t(player, "ui.reforge.chance_jackpot") + ": " + formatPercent(pJack + pJackB);

        String expectedDamage = t(player, "ui.reforge.preview_upgrade_jackpot", format3(mUp), format3(Math.max(mJack, mJackB))) + "\n"
                + t(player, "ui.reforge.preview_expected_stat_with_break", statLabel, format3(expectedWithBreak));

        return new Preview(
                currentStats,
                expectedStats,
                expectedDamage,
                t(player, "ui.reforge.preview_upgrade_line", format3(mUp)),
                t(player, "ui.reforge.preview_jackpot_line", format3(Math.max(mJack, mJackB))),
                t(player, "ui.reforge.preview_expected_stat_line", statLabel, format3(expectedWithBreak)),
                formatPercent(breakChance),
                formatPercent(pDeg + pDegB),
                formatPercent(pSame),
                formatPercent(pUp),
                formatPercent(pJack + pJackB),
                supportPreviewNote(player, hammerSupport, antiDegradationSupport),
                level);
    }

    private static ProcessResult processSelection(Player player, Entry equipment, Entry material, List<Entry> supports) {
        if (equipment == null) return new ProcessResult(t(player, "ui.reforge.error_pick_equipment"), 0);

        ItemStack current = readCurrentStack(player, equipment);
        if (current == null || current.isEmpty()) return new ProcessResult(t(player, "ui.reforge.error_equipment_changed"), 0);

        boolean isWeapon = ReforgeEquip.isWeapon(current);
        boolean isArmor = !isWeapon && ReforgeEquip.isArmor(current);
        if (!isWeapon && !isArmor) return new ProcessResult(t(player, "ui.reforge.error_item_not_refinable"), 0);

        int level = ReforgeEquip.getLevelFromItem(current);
        if (level >= getMaxUpgradeLevel()) return new ProcessResult(t(player, "ui.reforge.error_max_level"), 100);

        MaterialRequirement requirement = getMaterialRequirement(player, equipment, material);
        String materialId = requirement != null ? requirement.itemId : DEFAULT_MATERIAL_ID;
        String materialName = requirement != null ? requirement.displayName : LangLoader.getUITranslation(player, "ui.reforge.material");
        int materialCost = requirement != null ? requirement.cost : DEFAULT_MATERIAL_COST;

        if (countMaterialGlobally(player, materialId) < materialCost) {
            return new ProcessResult(t(player, "ui.reforge.error_need_material", materialCost, materialName), 0);
        }

        Entry hammerEntry = selectedHammerSupport(supports);
        HammerSupportType hammerSupport = getHammerSupportType(hammerEntry);
        boolean antiDegradationSupport = hasAntiDegradationSupport(supports);
        HammerUseResult hammerUse = new HammerUseResult(true, false);
        if (hammerSupport != null) {
            hammerUse = applyHammerWear(player, hammerEntry, hammerSupport.durabilityLossFraction);
            if (!hammerUse.ok) {
                return new ProcessResult(t(player, "ui.reforge.error_hammer_changed"), 0);
            }
        }

        if (!consumeMaterialGlobally(player, materialId, materialCost)) {
            return new ProcessResult(t(player, "ui.reforge.error_material_consume", materialName), 0);
        }

        double breakChance = effectiveBreakChance(level, isArmor, materialId, hammerSupport);
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
            if (ReforgeEquip.shouldUseSoftcoreBreakProtection(materialId)) {
                ItemStack softened = ReforgeEquip.applySoftcoreBreakPenalty(current);
                writeStack(player, equipment, softened);
                DynamicTooltipUtils.refreshPlayerTooltips(player.getPlayerRef());
                sfxConfig.playFail(player);
                String statLabel = isArmor ? t(player, "ui.reforge.stat_defense") : t(player, "ui.reforge.stat_damage");
                return new ProcessResult(
                        t(player,
                                "ui.reforge.result_softcore_break",
                                statLabel,
                                formatPercent(1.0 - ReforgeEquip.getSoftcoreStatMultiplier(softened)),
                                ReforgeEquip.getSoftcoreBreakCount(softened),
                                hammerSuffix),
                        100);
            }
            removeEquipment(player, equipment);
            sfxConfig.playShatter(player);
            return new ProcessResult(t(player, "ui.reforge.result_shattered", hammerSuffix), 0);
        }

        ReforgeOutcome outcome = rollOutcome(level, materialId);
        String antiDegradationSuffix = "";
        if (antiDegradationSupport
                && (outcome.type == OutcomeType.DEGRADE || outcome.type == OutcomeType.DEGRADE_B)
                && Math.random() < ANTI_DEGRADATION_BLOCK_CHANCE) {
            if (!consumeMaterialGlobally(player, ANTI_DEGRADATION_ID, 1)) {
                return new ProcessResult(t(player, "ui.reforge.error_material_consume",
                        antiDegradationName(player)), 0);
            }
            outcome = new ReforgeOutcome(0, OutcomeType.SAME);
            antiDegradationSuffix = " " + t(player, "ui.reforge.anti_degradation_blocked");
        }
        int newLevel = clampLevel(level + outcome.levelChange);

        ItemStack updated = ReforgeEquip.withUpgradeLevel(current, newLevel);
        writeStack(player, equipment, updated);
        registerReforgeTooltip(player, updated, newLevel, isArmor);

        switch (outcome.type) {
            case DEGRADE, DEGRADE_B -> {
                sfxConfig.playFail(player);
                return new ProcessResult(t(player, "ui.reforge.result_degraded", level, newLevel, hammerSuffix), 100);
            }
            case SAME -> {
                sfxConfig.playNoChange(player);
                return new ProcessResult(t(player, "ui.reforge.result_same", level, hammerSuffix + antiDegradationSuffix), 100);
            }
            case UPGRADE -> {
                sfxConfig.playSuccess(player);
                return new ProcessResult(t(player, "ui.reforge.result_success", level, newLevel, hammerSuffix), 100);
            }
            case JACKPOT -> {
                sfxConfig.playJackpot(player);
                return new ProcessResult(t(player, "ui.reforge.result_jackpot", level, newLevel, hammerSuffix), 100);
            }
            case JACKPOT_B -> {
                sfxConfig.playJackpot(player);
                return new ProcessResult(t(player, "ui.reforge.result_jackpot", level, newLevel, hammerSuffix), 100);
            }
            default -> {
                return new ProcessResult(t(player, "ui.reforge.result_unknown"), 0);
            }
        }
    }

    private static void registerReforgeTooltip(Player player, ItemStack item, int level, boolean isArmor) {
        if (item == null || item.isEmpty() || level <= 0) {
            DynamicTooltipUtils.refreshPlayerTooltips(player.getPlayerRef());
            return;
        }
        String itemId = item.getItemId();
        String upgradeName = isArmor ? ReforgeEquip.getArmorUpgradeName(level) : ReforgeEquip.getUpgradeName(level);
        double multiplier = statMultiplierForLevel(level, isArmor);
        int percentBonus = (int) Math.round((multiplier - 1.0) * 100.0);
        DynamicTooltipUtils.addReforgeTooltip(itemId, upgradeName, level, percentBonus, isArmor);
        DynamicTooltipUtils.refreshPlayerTooltips(player.getPlayerRef());
    }

    private static ReforgeOutcome rollOutcome(int level, String materialId) {
        OutcomeWeights w = outcomeWeights(level, materialId);
        if (w.total <= 0.0) return new ReforgeOutcome(0, OutcomeType.SAME);

        double random = Math.random() * w.total;
        if (random < w.degrade) return new ReforgeOutcome(-1, OutcomeType.DEGRADE);
        random -= w.degrade;
        if (random < w.degradeB) return new ReforgeOutcome(-2, OutcomeType.DEGRADE_B);
        random -= w.degradeB;
        if (random < w.same) return new ReforgeOutcome(0, OutcomeType.SAME);
        random -= w.same;
        if (random < w.upgrade) return new ReforgeOutcome(1, OutcomeType.UPGRADE);
        random -= w.upgrade;
        if (random < w.jackpot) return new ReforgeOutcome(2, OutcomeType.JACKPOT);
        return new ReforgeOutcome(3, OutcomeType.JACKPOT_B);
    }

    private static OutcomeWeights outcomeWeights(int level, String materialId) {
        double[] w = weights(level, materialId);
        double degradeTotal = weightAt(w, 0);
        double same = weightAt(w, 1);
        double upgrade = weightAt(w, 2);
        double jackpotTotal = weightAt(w, 3);

        double degradeB = level >= 2 ? degradeTotal * DEGRADE_B_SHARE_OF_DEGRADE : 0.0;
        double degrade = degradeTotal - degradeB;
        double jackpotB = level + 3 <= getMaxUpgradeLevel() ? jackpotTotal * JACKPOT_B_SHARE_OF_JACKPOT : 0.0;
        double jackpot = jackpotTotal - jackpotB;
        return normalizeOutcomeWeights(new OutcomeWeights(degrade, degradeB, same, upgrade, jackpot, jackpotB));
    }

    private static OutcomeWeights normalizeOutcomeWeights(OutcomeWeights weights) {
        if (weights.total <= 0.0) return weights;
        return new OutcomeWeights(
                weights.degrade / weights.total,
                weights.degradeB / weights.total,
                weights.same / weights.total,
                weights.upgrade / weights.total,
                weights.jackpot / weights.total,
                weights.jackpotB / weights.total);
    }

    private static double weightAt(double[] weights, int index) {
        if (weights == null || index < 0 || index >= weights.length) return 0.0;
        return Math.max(0.0, weights[index]);
    }

    private static double[] weights(int currentLevel, String materialId) {
        double[] fromCfg = refinementConfig != null ? refinementConfig.getAdjustedReforgeWeights(currentLevel, materialId) : null;
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

    private static double effectiveBreakChance(int currentLevel, boolean isArmor, String materialId, HammerSupportType hammerSupport) {
        double base = refinementConfig != null
                ? refinementConfig.getAdjustedBreakChance(currentLevel, isArmor, materialId)
                : breakChance(currentLevel, isArmor);
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
        return Math.max(0, Math.min(getMaxUpgradeLevel(), level));
    }

    private static int getMaxUpgradeLevel() {
        return refinementConfig != null ? refinementConfig.getMaxLevel() : DEFAULT_MAX_UPGRADE_LEVEL;
    }

    private static boolean isRefinementMaterial(String itemId) {
        if (itemId == null || itemId.isBlank()) return false;
        for (String id : refinementMaterialIds) {
            if (id != null && id.equalsIgnoreCase(itemId)) return true;
        }
        return false;
    }

    private static MaterialRequirement getMaterialRequirement(Player player, Entry equipment) {
        return getMaterialRequirement(player, equipment, null);
    }

    private static MaterialRequirement getMaterialRequirement(Player player, Entry equipment, Entry selectedMaterial) {
        if (equipment == null || equipment.item == null || equipment.item.isEmpty()) {
            return null;
        }
        int level = ReforgeEquip.getLevelFromItem(equipment.item);
        RefinementConfig.MaterialTier tier = null;
        if (refinementConfig != null && selectedMaterial != null && isRefinementMaterial(selectedMaterial.itemId)) {
            tier = refinementConfig.getMaterialTierForItem(selectedMaterial.itemId);
        }
        if (tier == null) {
            tier = refinementConfig != null
                    ? refinementConfig.getMaterialTierForLevel(level)
                    : null;
        }
        String itemId = tier != null && tier.itemId != null && !tier.itemId.isBlank()
                ? tier.itemId
                : DEFAULT_MATERIAL_ID;
        int cost = tier != null ? tier.cost : DEFAULT_MATERIAL_COST;
        String name = resolveMaterialName(player, itemId, null);
        return new MaterialRequirement(itemId, cost, name);
    }

    private static int countMaterials(List<Entry> entries, String itemId) {
        if (entries == null || entries.isEmpty() || itemId == null) return 0;
        int total = 0;
        for (Entry entry : entries) {
            if (entry == null || entry.itemId == null) continue;
            if (entry.itemId.equalsIgnoreCase(itemId)) {
                total += entry.quantity;
            }
        }
        return total;
    }

    private static Entry findFirstByItemId(List<Entry> entries, String itemId) {
        if (entries == null || entries.isEmpty() || itemId == null) return null;
        for (Entry entry : entries) {
            if (entry != null && entry.itemId != null && entry.itemId.equalsIgnoreCase(itemId)) {
                return entry;
            }
        }
        return null;
    }

    private static int findMaterialIndex(List<Entry> entries, String itemId) {
        if (entries == null || entries.isEmpty() || itemId == null) return -1;
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry != null && entry.itemId != null && entry.itemId.equalsIgnoreCase(itemId)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean materialMatches(Entry entry, String itemId) {
        if (entry == null || entry.itemId == null || itemId == null) return false;
        return entry.itemId.equalsIgnoreCase(itemId);
    }

    private static String resolveMaterialName(Player player, String itemId, Entry entry) {
        if (entry != null && entry.displayName != null && !entry.displayName.isBlank()) {
            return entry.displayName;
        }
        if (itemId == null || itemId.isBlank()) {
            return LangLoader.getUITranslation(player, "ui.reforge.material");
        }
        try {
            ItemStack stack = new ItemStack(itemId, 1);
            return UIItemUtils.displayNameOrItemId(stack, player);
        } catch (Exception ignored) {
            return itemId;
        }
    }

    private static int countMaterialGlobally(Player player, String itemId) {
        return countInContainer(player.getInventory().getStorage(), itemId)
                + countInContainer(player.getInventory().getHotbar(), itemId);
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

    private static boolean consumeMaterialGlobally(Player player, String itemId, int amount) {
        int remaining = amount;
        remaining = consumeFromContainer(player.getInventory().getStorage(), itemId, remaining);
        if (remaining > 0) remaining = consumeFromContainer(player.getInventory().getHotbar(), itemId, remaining);
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

    private static boolean isReforgeSupportItem(String itemId) {
        return isHammerItem(itemId) || isAntiDegradationItem(itemId);
    }

    private static boolean isHammerItem(String itemId) {
        return isIronHammerItem(itemId) || isThoriumHammerItem(itemId);
    }

    private static boolean isAntiDegradationItem(Entry support) {
        return support != null && isAntiDegradationItem(support.itemId);
    }

    private static boolean isAntiDegradationItem(String itemId) {
        return itemId != null && ANTI_DEGRADATION_ID.equalsIgnoreCase(itemId);
    }

    private static boolean hasAntiDegradationSupport(List<Entry> supports) {
        if (supports == null || supports.isEmpty()) {
            return false;
        }
        for (Entry support : supports) {
            if (isAntiDegradationItem(support)) {
                return true;
            }
        }
        return false;
    }

    private static Entry selectedHammerSupport(List<Entry> supports) {
        if (supports == null || supports.isEmpty()) {
            return null;
        }
        Entry iron = null;
        for (Entry support : supports) {
            if (support == null) continue;
            if (isThoriumHammerItem(support.itemId)) {
                return support;
            }
            if (iron == null && isIronHammerItem(support.itemId)) {
                iron = support;
            }
        }
        return iron;
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
        if (ReforgeEquip.hasSoftcorePenalty(item)) {
            sb.append("\n").append(t(player,
                    "ui.reforge.metadata_softcore_value",
                    format3(ReforgeEquip.getSoftcoreStatMultiplier(item)),
                    formatPercent(1.0 - ReforgeEquip.getSoftcoreStatMultiplier(item)),
                    ReforgeEquip.getSoftcoreBreakCount(item)));
        }
        return sb.toString();
    }

    private static String buildMetadataName(Entry equipment) {
        if (equipment == null || equipment.item == null || equipment.item.isEmpty()) {
            return "-";
        }
        return equipment.displayName;
    }

    private static String buildCurrentDamageRowsHtml(Player player, Entry equipment) {
        if (equipment == null || equipment.item == null || equipment.item.isEmpty()) {
            return statRow(t(player, "ui.reforge.no_equipment"), t(player, "ui.reforge.preview_select_item"));
        }
        ItemStack item = equipment.item;
        int level = ReforgeEquip.getLevelFromItem(item);
        StringBuilder sb = new StringBuilder();
        sb.append(statRow(t(player, "ui.reforge.equipment"), equipment.displayName + " +" + level, "#F8D36A", "#FFFFFF"));

        if (ReforgeEquip.isArmor(item) && !ReforgeEquip.isWeapon(item)) {
            ArmorProfile armor = armorProfile(item, level);
            sb.append(statRow(t(player, "ui.reforge.armor_defense_multiplier"), "x" + format3(armor.multiplier), "#83D8FF", "#83D8FF"));
            sb.append(statRow(t(player, "ui.reforge.stat_defense"), formatDamageValue(armor.current), "#77DD77", "#77DD77"));
            sb.append(statRow(t(player, "ui.reforge.base_defense"), formatDamageValue(armor.base), "#D8DEE9", "#FFFFFF"));
            sb.append(statRow(t(player, "ui.reforge.essence_resonance_defense"), bonusLine(0.0, armor.defensePercent), "#77DD77", "#77DD77"));
            appendArmorBonusRows(player, sb, armor);
            return sb.toString();
        }

        List<EquipmentDamageTooltipMath.DamageBreakdown> breakdowns =
                EquipmentDamageTooltipMath.getDamageBreakdownFromInteractionVars(item.getItemId());
        WeaponDamageProfile averageProfile = weaponProfile(item, level, 0.0);
        sb.append(statRow(t(player, "ui.reforge.damage_multiplier"), "x" + format3(averageProfile.multiplier), "#83D8FF", "#83D8FF"));
        sb.append(statRow(t(player, "ui.reforge.essence_resonance_damage"), bonusLine(averageProfile.damageFlat, averageProfile.damagePercent), "#77DD77", "#77DD77"));
        sb.append(statRow(t(player, "ui.reforge.crit"), formatPercent(averageProfile.critChance / 100.0)
                + " / +" + formatPercent(averageProfile.critDamage / 100.0), "#FF8888", "#FFAAAA"));
        if (breakdowns.isEmpty()) {
            sb.append(statRow(t(player, "ui.reforge.normal_total"), formatDamageValue(averageProfile.normal), "#F8D36A", "#F8D36A"));
            sb.append(statRow(t(player, "ui.reforge.crit_total"), formatDamageValue(averageProfile.crit), "#FF8888", "#FFAAAA"));
            sb.append(statRow(t(player, "ui.reforge.expected_average"), formatDamageValue(averageProfile.expected), "#F8D36A", "#F8D36A"));
            return sb.toString();
        }

        int rendered = 0;
        for (EquipmentDamageTooltipMath.DamageBreakdown breakdown : breakdowns) {
            if (rendered >= MAX_RENDERED_DAMAGE_ROWS) {
                sb.append(statRow("...", t(player, "ui.reforge.more_rows", breakdowns.size() - rendered)));
                break;
            }
            WeaponDamageProfile profile = weaponProfile(item, level, breakdown.getBaseValue());
            sb.append(statRow(breakdown.getLabel(), formatDamageValue(profile.normal), "#D8DEE9", "#FFFFFF"));
            rendered++;
        }
        sb.append(statRow(t(player, "ui.reforge.normal_total"), formatDamageValue(averageProfile.normal), "#F8D36A", "#F8D36A"));
        sb.append(statRow(t(player, "ui.reforge.crit_total"), formatDamageValue(averageProfile.crit), "#FF8888", "#FFAAAA"));
        sb.append(statRow(t(player, "ui.reforge.expected_average"), formatDamageValue(averageProfile.expected), "#F8D36A", "#F8D36A"));
        return sb.toString();
    }

    private static String buildExpectedDamageRowsHtml(Player player, Entry equipment) {
        if (equipment == null || equipment.item == null || equipment.item.isEmpty()) {
            return statRow(t(player, "ui.reforge.expected_outcome"), t(player, "ui.reforge.preview_select_equipment_note"));
        }
        ItemStack item = equipment.item;
        int level = ReforgeEquip.getLevelFromItem(item);
        int nextLevel = Math.min(getMaxUpgradeLevel(), level + 1);
        if (level >= getMaxUpgradeLevel()) {
            return statRow(t(player, "ui.reforge.expected_outcome"), t(player, "ui.reforge.preview_max_level_short"));
        }

        StringBuilder sb = new StringBuilder();
        sb.append(statRow(t(player, "ui.reforge.preview_refine_level", level, ""), "+" + level + " → +" + nextLevel, "#F8D36A", "#FFFFFF"));

        if (ReforgeEquip.isArmor(item) && !ReforgeEquip.isWeapon(item)) {
            ArmorProfile nextArmor = armorProfile(item, nextLevel);
            sb.append(statRow(t(player, "ui.reforge.armor_defense_multiplier"), "x" + format3(nextArmor.multiplier), "#83D8FF", "#83D8FF"));
            sb.append(statRow(t(player, "ui.reforge.stat_defense"), formatDamageValue(nextArmor.current), "#77DD77", "#77DD77"));
            sb.append(statRow(t(player, "ui.reforge.base_defense"), formatDamageValue(nextArmor.base), "#D8DEE9", "#FFFFFF"));
            sb.append(statRow(t(player, "ui.reforge.essence_resonance_defense"), bonusLine(0.0, nextArmor.defensePercent), "#77DD77", "#77DD77"));
            appendArmorBonusRows(player, sb, nextArmor);
            sb.append(statRow(t(player, "ui.reforge.preview_upgrade_line", format3(statMultiplierForLevel(nextLevel, true))),
                    "+" + formatPercent(statMultiplierForLevel(nextLevel, true) - statMultiplierForLevel(level, true)), "#83D8FF", "#83D8FF"));
            return sb.toString();
        }

        List<EquipmentDamageTooltipMath.DamageBreakdown> breakdowns =
                EquipmentDamageTooltipMath.getDamageBreakdownFromInteractionVars(item.getItemId());
        WeaponDamageProfile currentAverage = weaponProfile(item, level, 0.0);
        WeaponDamageProfile nextAverage = weaponProfile(item, nextLevel, 0.0);
        sb.append(statRow(t(player, "ui.reforge.damage_multiplier"), "x" + format3(nextAverage.multiplier), "#83D8FF", "#83D8FF"));
        sb.append(statRow(t(player, "ui.reforge.essence_resonance_damage"), bonusLine(currentAverage.damageFlat, currentAverage.damagePercent), "#77DD77", "#77DD77"));
        sb.append(statRow(t(player, "ui.reforge.crit"), formatPercent(currentAverage.critChance / 100.0)
                + " / +" + formatPercent(currentAverage.critDamage / 100.0), "#FF8888", "#FFAAAA"));
        if (breakdowns.isEmpty()) {
            sb.append(statRow(t(player, "ui.reforge.normal_total"), formatDamageValue(nextAverage.normal), "#F8D36A", "#F8D36A"));
            sb.append(statRow(t(player, "ui.reforge.crit_total"), formatDamageValue(nextAverage.crit), "#FF8888", "#FFAAAA"));
            sb.append(statRow(t(player, "ui.reforge.expected_average"), formatDamageValue(nextAverage.expected), "#F8D36A", "#F8D36A"));
            return sb.toString();
        }

        int rendered = 0;
        for (EquipmentDamageTooltipMath.DamageBreakdown breakdown : breakdowns) {
            if (rendered >= MAX_RENDERED_DAMAGE_ROWS) {
                sb.append(statRow("...", t(player, "ui.reforge.more_rows", breakdowns.size() - rendered)));
                break;
            }
            WeaponDamageProfile next = weaponProfile(item, nextLevel, breakdown.getBaseValue());
            sb.append(statRow(breakdown.getLabel(), formatDamageValue(next.normal), "#D8DEE9", "#FFFFFF"));
            rendered++;
        }
        sb.append(statRow(t(player, "ui.reforge.normal_total"), formatDamageValue(nextAverage.normal), "#F8D36A", "#F8D36A"));
        sb.append(statRow(t(player, "ui.reforge.crit_total"), formatDamageValue(nextAverage.crit), "#FF8888", "#FFAAAA"));
        sb.append(statRow(t(player, "ui.reforge.expected_average"), formatDamageValue(nextAverage.expected), "#F8D36A", "#F8D36A"));
        return sb.toString();
    }

    private static String statRow(String label, String value) {
        return statRow(label, value, "#D8DEE9", "#FFFFFF");
    }

    private static String statRow(String label, String value, String labelColor, String valueColor) {
        return "<div style=\"anchor-width:338; layout-mode:left; spacing:8;\">"
                + "<p style=\"anchor-width:126; font-size:14; color:" + escapeHtml(labelColor) + "; white-space:nowrap;\">"
                + escapeHtml(label)
                + "</p><p style=\"anchor-width:204; font-size:14; color:" + escapeHtml(valueColor) + "; white-space:nowrap;\">"
                + escapeHtml(value)
                + "</p></div>";
    }

    private static String beforeAfter(double before, double after) {
        return formatDamageValue(before) + " → " + formatDamageValue(after);
    }

    private static EquipmentDamageTooltipMath.StatSummary weaponSummary(ItemStack item, int level, double baseOverride) {
        SocketData socketData = SocketManager.getSocketData(item);
        double partsMultiplier = partsDamageMultiplier(item);
        double softcoreMultiplier = ReforgeEquip.getSoftcoreStatMultiplier(item);
        if (baseOverride > 0.0) {
            return new EquipmentDamageTooltipMath.StatSummary(
                    baseOverride,
                    EquipmentDamageTooltipMath.computeBuffedWeaponDamage(
                            item.getItemId(),
                            baseOverride,
                            level,
                            socketData,
                            partsMultiplier,
                            softcoreMultiplier));
        }
        return EquipmentDamageTooltipMath.computeWeaponDamageSummary(
                item.getItemId(),
                level,
                socketData,
                partsMultiplier,
                softcoreMultiplier);
    }

    private static double weaponDamage(ItemStack item, double baseDamage, int level) {
        return weaponSummary(item, level, baseDamage).getBuffedValue();
    }

    private static WeaponDamageProfile weaponProfile(ItemStack item, int level, double baseOverride) {
        SocketData socketData = SocketManager.getSocketData(item);
        double base = baseOverride > 0.0
                ? baseOverride
                : EquipmentDamageTooltipMath.getAverageBaseDamageFromInteractionVars(item.getItemId());
        double partsMultiplier = partsDamageMultiplier(item);
        double softcoreMultiplier = ReforgeEquip.getSoftcoreStatMultiplier(item);
        double refineMultiplier = statMultiplierForLevel(level, false);
        double[] damageBonus = weaponStatBonus(item, socketData, EssenceEffect.StatType.DAMAGE);
        double[] attackSpeedBonus = weaponStatBonus(item, socketData, EssenceEffect.StatType.ATTACK_SPEED);
        double[] critChanceBonus = weaponStatBonus(item, socketData, EssenceEffect.StatType.CRIT_CHANCE);
        double[] critDamageBonus = weaponStatBonus(item, socketData, EssenceEffect.StatType.CRIT_DAMAGE);

        double damageFlat = damageBonus[0];
        double damagePercent = damageBonus[1];
        double attackSpeedPercent = attackSpeedBonus[1];
        double critChance = Math.max(0.0, Math.min(100.0, critChanceBonus[1]));
        double critDamage = Math.max(0.0, Math.min(200.0, critDamageBonus[1]));
        double multiplier = refineMultiplier
                * softcoreMultiplier
                * (1.0 + (damagePercent / 100.0))
                * partsMultiplier
                * (1.0 + (attackSpeedPercent / 100.0));
        double normal = Math.max(0.0, (base * multiplier) + damageFlat);
        double crit = normal * (1.0 + (critDamage / 100.0));
        double expected = normal * (1.0 + ((critChance / 100.0) * (critDamage / 100.0)));
        return new WeaponDamageProfile(base, damageFlat, damagePercent, critChance, critDamage, multiplier, normal, crit, expected);
    }

    private static double[] weaponStatBonus(ItemStack item, SocketData socketData, EssenceEffect.StatType stat) {
        double[] stored = normalizeBonus(SocketManager.getStoredStatBonus(item, stat));
        if (hasBonus(stored) || socketData == null) {
            return stored;
        }
        return normalizeBonus(SocketManager.calculateTieredBonus(socketData, stat, true));
    }

    private static double[] normalizeBonus(double[] bonus) {
        if (bonus == null || bonus.length < 2) {
            return new double[] {0.0, 0.0};
        }
        return new double[] {bonus[0], bonus[1]};
    }

    private static boolean hasBonus(double[] bonus) {
        return bonus != null
                && bonus.length >= 2
                && (Math.abs(bonus[0]) > 0.0001 || Math.abs(bonus[1]) > 0.0001);
    }

    private static String bonusLine(double flat, double percent) {
        boolean hasFlat = Math.abs(flat) > 0.0001;
        boolean hasPercent = Math.abs(percent) > 0.0001;
        if (!hasFlat && !hasPercent) {
            return "+0";
        }
        if (hasFlat && hasPercent) {
            return signedNumber(flat) + " / " + signedPercent(percent);
        }
        if (hasFlat) {
            return signedNumber(flat);
        }
        return signedPercent(percent);
    }

    private static String signedNumber(double value) {
        return (value >= 0.0 ? "+" : "") + formatDamageValue(value);
    }

    private static String signedPercent(double percent) {
        return (percent >= 0.0 ? "+" : "") + formatPercent(percent / 100.0);
    }

    private static void appendArmorBonusRows(Player player, StringBuilder sb, ArmorProfile armor) {
        appendBonusRow(player, sb, "ui.reforge.health_bonus", armor.healthFlat, armor.healthPercent, "#77DD77");
        appendBonusRow(player, sb, "ui.reforge.regen_bonus", armor.regenFlat, armor.regenPercent, "#77DD77");
        appendBonusRow(player, sb, "ui.reforge.fire_defense_bonus", armor.fireDefenseFlat, armor.fireDefensePercent, "#FFAA55");
        appendBonusRow(player, sb, "ui.reforge.evasion_bonus", armor.evasionFlat, armor.evasionPercent, "#83D8FF");
        appendBonusRow(player, sb, "ui.reforge.slow_bonus", armor.slowFlat, armor.slowPercent, "#AAAAFF");
    }

    private static void appendBonusRow(Player player,
                                       StringBuilder sb,
                                       String labelKey,
                                       double flat,
                                       double percent,
                                       String color) {
        if (Math.abs(flat) <= 0.0001 && Math.abs(percent) <= 0.0001) {
            return;
        }
        sb.append(statRow(t(player, labelKey), bonusLine(flat, percent), color, color));
    }

    private static ArmorProfile armorProfile(ItemStack item, int level) {
        SocketData socketData = SocketManager.getSocketData(item);
        double softcoreMultiplier = ReforgeEquip.getSoftcoreStatMultiplier(item);
        EquipmentDamageTooltipMath.StatSummary summary =
                EquipmentDamageTooltipMath.computeArmorDefenseSummary(item.getItemId(), level, socketData, softcoreMultiplier);
        double multiplier = statMultiplierForLevel(level, true) * softcoreMultiplier;
        double[] defense = SocketManager.getStoredStatBonus(item, EssenceEffect.StatType.DEFENSE);
        double[] health = SocketManager.getStoredStatBonus(item, EssenceEffect.StatType.HEALTH);
        double[] regen = SocketManager.getStoredStatBonus(item, EssenceEffect.StatType.REGENERATION);
        double[] fireDefense = SocketManager.getStoredStatBonus(item, EssenceEffect.StatType.FIRE_DEFENSE);
        double[] evasion = SocketManager.getStoredStatBonus(item, EssenceEffect.StatType.EVASION);
        double[] slow = SocketManager.getStoredStatBonus(item, EssenceEffect.StatType.MOVEMENT_SPEED);
        return new ArmorProfile(
                summary.getBaseValue(),
                summary.getBuffedValue(),
                multiplier,
                defense[0],
                defense[1],
                health[0],
                health[1],
                regen[0],
                regen[1],
                fireDefense[0],
                fireDefense[1],
                evasion[0],
                evasion[1],
                slow[0],
                slow[1]);
    }

    private static double partsDamageMultiplier(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return 1.0;
        }
        Double value = item.getFromMetadataOrNull("SocketReforge.Parts.DamageMultiplier", Codec.DOUBLE);
        if (value == null || !Double.isFinite(value)) {
            return 1.0;
        }
        return Math.max(0.5, Math.min(2.0, value));
    }

    private record ArmorProfile(double base,
                                double current,
                                double multiplier,
                                double defenseFlat,
                                double defensePercent,
                                double healthFlat,
                                double healthPercent,
                                double regenFlat,
                                double regenPercent,
                                double fireDefenseFlat,
                                double fireDefensePercent,
                                double evasionFlat,
                                double evasionPercent,
                                double slowFlat,
                                double slowPercent) {}
    private record WeaponDamageProfile(double base,
                                       double damageFlat,
                                       double damagePercent,
                                       double critChance,
                                       double critDamage,
                                       double multiplier,
                                       double normal,
                                       double crit,
                                       double expected) {}

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
        double currentMult = ReforgeEquip.getEffectiveRefinementMultiplier(item, isArmor);
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
        double softcoreMultiplier = ReforgeEquip.getSoftcoreStatMultiplier(item);
        if (isArmor) {
            EquipmentDamageTooltipMath.StatSummary summary =
                    EquipmentDamageTooltipMath.computeArmorDefenseSummary(itemId, level, socketData, softcoreMultiplier);
            if (summary.getBaseValue() <= 0.0) {
                return t(player, "ui.reforge.metadata_base_defense_na");
            }
            return t(player, "ui.reforge.metadata_base_defense_value",
                    formatDamageValue(summary.getBaseValue()),
                    formatDamageValue(summary.getBuffedValue()));
        }
        EquipmentDamageTooltipMath.StatSummary summary =
                EquipmentDamageTooltipMath.computeWeaponDamageSummary(itemId, level, socketData, 1.0, softcoreMultiplier);
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

    private static String supportPreviewNote(Player player, HammerSupportType hammerSupport, boolean antiDegradationSupport) {
        StringBuilder sb = new StringBuilder();
        if (hammerSupport != null) {
            sb.append(t(player, "ui.reforge.preview_hammer_active", hammerLabel(player, hammerSupport),
                    durabilityPercent(hammerSupport)));
        }
        if (antiDegradationSupport) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(t(player, "ui.reforge.preview_anti_degradation_active"));
        }
        return sb.toString();
    }

    private static String antiDegradationName(Player player) {
        return LangLoader.resolveTranslation("items." + ANTI_DEGRADATION_ID + ".name", LangLoader.getPlayerLanguage(player));
    }

    private static String hammerLabel(Player player, HammerSupportType hammerSupport) {
        if (hammerSupport == null) {
            return "";
        }
        return t(player, hammerSupport.labelKey);
    }
}
