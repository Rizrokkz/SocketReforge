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

import irai.mod.reforge.Config.RefinementConfig;
import irai.mod.reforge.Config.SFXConfig;
import irai.mod.reforge.Interactions.ReforgeEquip;
import irai.mod.reforge.Util.DynamicTooltipUtils;
import irai.mod.reforge.Util.NameResolver;

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
    private static final String HAMMER_ID = "Tool_Hammer_Iron";
    private static final int MATERIAL_COST = 3;
    private static final int MAX_UPGRADE_LEVEL = 3;
    private static final double HAMMER_BREAK_MULTIPLIER = 0.50d;
    private static final double HAMMER_DURABILITY_LOSS_FRACTION = 0.05d;

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
        try {
            Class.forName(HYUI_PAGE_BUILDER);
            Class.forName(HYUI_PLUGIN);
            hyuiAvailable = true;
            System.out.println("[SocketReforge] ReforgeBenchUI: HyUI loaded.");
        } catch (ClassNotFoundException e) {
            hyuiAvailable = false;
            System.out.println("[SocketReforge] ReforgeBenchUI: HyUI unavailable.");
        }
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
            player.sendMessage(Message.raw("<color=#FF5555>HyUI not installed - reforge UI disabled."));
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
        collectFromContainer(player.getInventory().getHotbar(), ContainerKind.HOTBAR, equipments, materials, supports);
        collectFromContainer(player.getInventory().getStorage(), ContainerKind.STORAGE, equipments, materials, supports);
        int total = 0;
        for (Entry e : materials) total += e.quantity;
        return new Snapshot(equipments, materials, supports, total);
    }

    private static void collectFromContainer(
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

            String name = NameResolver.getDisplayName(stack);
            if (name == null || name.isEmpty() || "Unknown Item".equals(name)) {
                name = itemId;
            }

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

            String html = buildHtml(snapshot, state);
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
                        pendingSelections.put(ref, new SelectionState(eqVal, matVal, supVal, "Refining...", 0, true));
                        sfxConfig.playReforgeStart(finalPlayer);
                        finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));

                        for (int elapsed = PROGRESS_TICK_MS; elapsed < PROCESS_DURATION_MS; elapsed += PROGRESS_TICK_MS) {
                            final int delay = elapsed;
                            final int timedProgress = Math.min(99, (int) Math.round(((delay + PROGRESS_TICK_MS) * 100.0) / PROCESS_DURATION_MS));
                            scheduler.schedule(() -> finalPlayer.getWorld().execute(() -> {
                                if (!Boolean.TRUE.equals(processingPlayers.get(ref))) return;
                                pendingSelections.put(ref, new SelectionState(eqVal, matVal, supVal, "Refining...", timedProgress, true));
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

    private static String buildHtml(Snapshot snapshot, SelectionState state) {
        String eqKey = state != null ? state.equipmentKey : null;
        String matKey = state != null ? state.materialKey : null;
        String supKey = state != null ? state.supportKey : null;
        String status = state != null && state.statusText != null ? state.statusText : "Idle";
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

        Preview preview = buildPreview(selectedEquipment, selectedSupport);
        if (!processing) {
            if (selectedEquipment == null) {
                status = "No reforgeable equipment found.";
            } else if (preview.currentLevel >= MAX_UPGRADE_LEVEL) {
                status = "Item already at max refine level.";
            } else if (snapshot.materialCount < MATERIAL_COST) {
                status = "Need " + MATERIAL_COST + " Refinement Globs.";
            } else if ("Idle".equals(status)) {
                status = "Ready to refine.";
            }
        }

        String html = loadTemplate();
        html = html.replace("{{equipmentOptions}}", buildOptions(snapshot.equipments, "No reforgeable equipment found", eqKey));
        html = html.replace("{{materialOptions}}", buildOptions(snapshot.materials, "No Refinement Glob found", matKey));
        html = html.replace("{{supportOptions}}", buildSupportOptions(snapshot.supports, supKey));
        html = html.replace("{{materialCountText}}", String.valueOf(snapshot.materialCount));
        html = html.replace("{{currentStatsText}}", escapeHtml(preview.currentStats));
        html = html.replace("{{expectedStatsText}}", escapeHtml(preview.expectedStats));
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
        html = html.replace("{{metadataCurrentStatText}}", escapeHtml(buildMetadataCurrentStat(selectedEquipment)));
        html = html.replace("{{metadataText}}", escapeHtml(buildMetadata(selectedEquipment)));
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

    private static String buildSupportOptions(List<Entry> entries, String selectedKey) {
        StringBuilder sb = new StringBuilder();
        sb.append("<option value=\"\"");
        if (selectedKey == null || selectedKey.isEmpty()) {
            sb.append(" selected=\"true\"");
        }
        sb.append(">None</option>");
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            String key = String.valueOf(i);
            sb.append("<option value=\"").append(key).append("\"");
            if (key.equals(selectedKey)) sb.append(" selected=\"true\"");
            sb.append(">").append(escapeHtml(entry.displayName)).append(" [Break Guard] x").append(entry.quantity).append("</option>");
        }
        return sb.toString();
    }

    private static Preview buildPreview(Entry equipment, Entry support) {
        if (equipment == null || equipment.item == null || equipment.item.isEmpty()) {
            return new Preview(
                    "No equipment selected.",
                    "Select an item to see expected refine outcomes.",
                    "Expected damage outcome will be shown here.",
                    "Expected damage outcome",
                    "will be shown here.",
                    "",
                    "-",
                    "-",
                    "-",
                    "-",
                    "-",
                    "Select equipment to preview chances.",
                    0);
        }

        ItemStack item = equipment.item;
        boolean isArmor = ReforgeEquip.isArmor(item) && !ReforgeEquip.isWeapon(item);
        boolean hammerSupport = support != null && isHammerItem(support.itemId);
        int level = Math.max(0, Math.min(MAX_UPGRADE_LEVEL, ReforgeEquip.getLevelFromItem(item)));
        String type = isArmor ? "Armor" : "Weapon";
        String refineName = isArmor ? ReforgeEquip.getArmorUpgradeName(level) : ReforgeEquip.getUpgradeName(level);
        double currentMult = statMultiplierForLevel(level, isArmor);
        String statLabel = isArmor ? "Defense" : "Damage";

        String currentStats = "Type: " + type + "\n"
                + "Refine Level: +" + level + (refineName == null || refineName.isBlank() ? "" : " (" + refineName + ")") + "\n"
                + "Current " + statLabel + ": x" + format3(currentMult) + " (" + formatPercent(currentMult - 1.0) + ")";

        if (level >= MAX_UPGRADE_LEVEL) {
            String expectedStats = "Break: 0%\nDegrade: 0%\nSame: 0%\nUpgrade: 0%\nJackpot: 0%\n(Max level reached)";
            String expectedDamage = "Expected after refine: max level";
            return new Preview(
                    currentStats,
                    expectedStats,
                    expectedDamage,
                    "Expected after refine:",
                    "max level",
                    "",
                    "0%",
                    "0%",
                    "0%",
                    "0%",
                    "0%",
                    hammerSupport
                            ? "Max level reached. Hammer equipped (durability -5% per attempt)."
                            : "Max level reached.",
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

        String expectedStats = "Break: " + formatPercent(breakChance) + "\n"
                + "Degrade: " + formatPercent(pDeg) + "\n"
                + "Same: " + formatPercent(pSame) + "\n"
                + "Upgrade: " + formatPercent(pUp) + "\n"
                + "Jackpot: " + formatPercent(pJack);

        String expectedDamage = "On Upgrade: x" + format3(mUp) + " | Jackpot: x" + format3(mJack) + "\n"
                + "Expected " + statLabel + " (incl. break risk): x" + format3(expectedWithBreak);

        return new Preview(
                currentStats,
                expectedStats,
                expectedDamage,
                "On Upgrade: x" + format3(mUp),
                "Jackpot: x" + format3(mJack),
                "Expected " + statLabel + ": x" + format3(expectedWithBreak),
                formatPercent(breakChance),
                formatPercent(pDeg),
                formatPercent(pSame),
                formatPercent(pUp),
                formatPercent(pJack),
                hammerSupport
                        ? "Hammer support active: break chance reduced; durability -5% on process."
                        : "",
                level);
    }

    private static ProcessResult processSelection(Player player, Entry equipment, Entry material, Entry support) {
        if (equipment == null) return new ProcessResult("Pick a valid equipment first.", 0);

        ItemStack current = readCurrentStack(player, equipment);
        if (current == null || current.isEmpty()) return new ProcessResult("Selected equipment changed; reselect.", 0);

        boolean isWeapon = ReforgeEquip.isWeapon(current);
        boolean isArmor = !isWeapon && ReforgeEquip.isArmor(current);
        if (!isWeapon && !isArmor) return new ProcessResult("Selected item cannot be refined.", 0);

        int level = ReforgeEquip.getLevelFromItem(current);
        if (level >= MAX_UPGRADE_LEVEL) return new ProcessResult("Item already at max refine level.", 100);

        if (countMaterialGlobally(player) < MATERIAL_COST) {
            return new ProcessResult("Not enough Refinement Globs (need " + MATERIAL_COST + ").", 0);
        }

        boolean hammerSupport = support != null && isHammerItem(support.itemId);
        HammerUseResult hammerUse = new HammerUseResult(true, false);
        if (hammerSupport) {
            hammerUse = applyHammerWear(player, support, HAMMER_DURABILITY_LOSS_FRACTION);
            if (!hammerUse.ok) {
                return new ProcessResult("Selected hammer stack changed; reselect and try again.", 0);
            }
        }

        if (!consumeMaterialGlobally(player, MATERIAL_COST)) {
            return new ProcessResult("Failed to consume Refinement Globs. Retry.", 0);
        }

        double breakChance = effectiveBreakChance(level, isArmor, hammerSupport);
        String hammerSuffix = "";
        if (hammerSupport) {
            hammerSuffix = hammerUse.consumed ? " Hammer broke." : " Hammer durability -5%.";
        }
        if (Math.random() < breakChance) {
            removeEquipment(player, equipment);
            sfxConfig.playShatter(player);
            return new ProcessResult("Item shattered during refinement." + hammerSuffix, 0);
        }

        ReforgeOutcome outcome = rollOutcome(level);
        int newLevel = clampLevel(level + outcome.levelChange);

        ItemStack updated = ReforgeEquip.withUpgradeLevel(current, newLevel);
        writeStack(player, equipment, updated);
        registerReforgeTooltip(updated, newLevel, isArmor);

        switch (outcome.type) {
            case DEGRADE -> {
                sfxConfig.playFail(player);
                return new ProcessResult("Refine failed: degraded +" + level + " -> +" + newLevel + hammerSuffix, 100);
            }
            case SAME -> {
                sfxConfig.playNoChange(player);
                return new ProcessResult("Refine failed: remained at +" + level + hammerSuffix, 100);
            }
            case UPGRADE -> {
                sfxConfig.playSuccess(player);
                return new ProcessResult("Refine success: +" + level + " -> +" + newLevel + hammerSuffix, 100);
            }
            case JACKPOT -> {
                sfxConfig.playJackpot(player);
                return new ProcessResult("JACKPOT: +" + level + " -> +" + newLevel + hammerSuffix, 100);
            }
            default -> {
                return new ProcessResult("Refine result unknown.", 0);
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

    private static double effectiveBreakChance(int currentLevel, boolean isArmor, boolean hammerSupport) {
        double base = breakChance(currentLevel, isArmor);
        if (!hammerSupport) return base;
        return Math.max(0.0, Math.min(1.0, base * HAMMER_BREAK_MULTIPLIER));
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
        if (itemId == null || itemId.isEmpty()) return false;
        if (HAMMER_ID.equalsIgnoreCase(itemId)) return true;
        String normalized = itemId.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return normalized.contains("toolhammeriron") || normalized.contains("hammeriron");
    }

    private static HammerUseResult applyHammerWear(Player player, Entry hammerEntry, double durabilityFraction) {
        if (player == null || hammerEntry == null || !isHammerItem(hammerEntry.itemId)) {
            return new HammerUseResult(false, false);
        }
        ItemContainer container = getContainer(player, hammerEntry.container);
        if (container == null) {
            return new HammerUseResult(false, false);
        }
        ItemStack hammer = container.getItemStack(hammerEntry.slot);
        if (hammer == null || hammer.isEmpty() || !isHammerItem(hammer.getItemId())) {
            return new HammerUseResult(false, false);
        }

        double max = hammer.getMaxDurability();
        double cur = hammer.getDurability();
        if (max <= 0) {
            container.removeItemStackFromSlot(hammerEntry.slot, 1, false, false);
            return new HammerUseResult(true, true);
        }

        double fraction = Math.max(0.0d, durabilityFraction);
        double loss = Math.max(1.0d, max * fraction);
        double next = Math.max(0.0d, cur - loss);
        if (next <= 0.0d) {
            container.removeItemStackFromSlot(hammerEntry.slot, 1, false, false);
            return new HammerUseResult(true, true);
        }

        container.setItemStackForSlot(hammerEntry.slot, hammer.withDurability(next));
        return new HammerUseResult(true, false);
    }

    private static ItemContainer getContainer(Player player, ContainerKind kind) {
        if (player == null || player.getInventory() == null) return null;
        return kind == ContainerKind.HOTBAR ? player.getInventory().getHotbar() : player.getInventory().getStorage();
    }

    private static ItemStack readCurrentStack(Player player, Entry entry) {
        ItemContainer container = getContainer(player, entry.container);
        if (container == null) return null;
        return container.getItemStack(entry.slot);
    }

    private static void writeStack(Player player, Entry entry, ItemStack stack) {
        ItemContainer container = getContainer(player, entry.container);
        if (container != null) container.setItemStackForSlot(entry.slot, stack);
    }

    private static void removeEquipment(Player player, Entry entry) {
        ItemContainer container = getContainer(player, entry.container);
        if (container == null) return;
        container.removeItemStackFromSlot(entry.slot, 1, false, false);
    }

    private static String buildMetadata(Entry equipment) {
        if (equipment == null || equipment.item == null || equipment.item.isEmpty()) {
            return "No equipment selected.";
        }
        ItemStack item = equipment.item;
        int level = ReforgeEquip.getLevelFromItem(item);

        StringBuilder sb = new StringBuilder();
        sb.append("Name : ").append(equipment.displayName).append("\n");
        sb.append("Refinement Level : +").append(level);
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

    private static String buildMetadataCurrentStat(Entry equipment) {
        if (equipment == null || equipment.item == null || equipment.item.isEmpty()) {
            return "Current Stat : -";
        }
        ItemStack item = equipment.item;
        boolean isArmor = ReforgeEquip.isArmor(item) && !ReforgeEquip.isWeapon(item);
        int level = ReforgeEquip.getLevelFromItem(item);
        double currentMult = statMultiplierForLevel(level, isArmor);
        String statLabel = isArmor ? "Current Defense" : "Current Damage";
        return statLabel + " : x" + format3(currentMult) + " (" + formatPercent(currentMult - 1.0) + ")";
    }

    private static String loadTemplate() {
        String fileSystemPath = "src/main/resources/" + TEMPLATE_PATH;
        try {
            Path path = Paths.get(fileSystemPath);
            if (Files.exists(path)) {
                return Files.readString(path, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            System.err.println("[SocketReforge] ReforgeBenchUI failed to read filesystem template: " + e.getMessage());
        }
        try (InputStream in = ReforgeBenchUI.class.getClassLoader().getResourceAsStream(TEMPLATE_PATH)) {
            if (in != null) {
                byte[] bytes = in.readAllBytes();
                return new String(bytes, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            System.err.println("[SocketReforge] ReforgeBenchUI failed to read classpath template: " + e.getMessage());
        }
        return "<div><p>Reforge Bench UI template missing.</p></div>";
    }

    private static Entry findByKey(List<Entry> entries, String key) {
        Entry exact = resolveSelection(entries, key);
        if (exact != null) return exact;
        if (entries.isEmpty()) return null;
        return entries.get(0);
    }

    private static Entry resolveSelection(List<Entry> entries, String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            int idx = Integer.parseInt(value.trim());
            if (idx < 0 || idx >= entries.size()) return null;
            return entries.get(idx);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String extractEventValue(Object eventObj) {
        if (eventObj == null) return null;
        try {
            Method getValue = eventObj.getClass().getMethod("getValue");
            Object value = getValue.invoke(eventObj);
            return value == null ? null : value.toString();
        } catch (Exception e) {
            return eventObj.toString();
        }
    }

    private static String getContextValue(Object ctxObj, String... keys) {
        if (ctxObj == null || keys == null) return null;
        for (String key : keys) {
            try {
                Method getValue = ctxObj.getClass().getMethod("getValue", String.class);
                Object optObj = getValue.invoke(ctxObj, key);
                if (!(optObj instanceof Optional<?> optional) || optional.isEmpty()) {
                    continue;
                }
                Object value = optional.get();
                if (value != null) return value.toString();
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static Object getStore(PlayerRef playerRef) throws Exception {
        Method getReference = playerRef.getClass().getMethod("getReference");
        Object ref = getReference.invoke(playerRef);
        Method getStore = ref.getClass().getMethod("getStore");
        return getStore.invoke(ref);
    }

    private static void closePageIfOpen(PlayerRef playerRef) {
        Object page = openPages.remove(playerRef);
        if (page == null) return;
        try {
            page.getClass().getMethod("close").invoke(page);
        } catch (Exception ignored) {
        }
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String format3(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String formatPercent(double fraction) {
        return String.format(Locale.ROOT, "%.1f%%", fraction * 100.0);
    }
}
