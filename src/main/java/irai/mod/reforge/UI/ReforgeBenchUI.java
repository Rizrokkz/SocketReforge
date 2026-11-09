package irai.mod.reforge.UI;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Common.EquipmentDamageTooltipMath;
import irai.mod.reforge.Common.AscensionUtils;
import irai.mod.reforge.Common.ArmorAffinityResistanceUtils;
import irai.mod.reforge.Common.AttackSpeedMultiHitMath;
import irai.mod.reforge.Common.CorruptionUtils;
import irai.mod.reforge.Common.WeaponElementalDamageUtils;
import irai.mod.reforge.Common.UI.UIInventoryUtils;
import irai.mod.reforge.Common.UI.UIItemUtils;
import irai.mod.reforge.Common.SmithyLegacyUtils;
import irai.mod.reforge.Common.SmithyLegacyUtils.Bonuses;
import irai.mod.reforge.Config.RefinementConfig;
import irai.mod.reforge.Config.SFXConfig;
import irai.mod.reforge.Interactions.ReforgeEquip;
import irai.mod.reforge.Progression.CharacterPathService;
import irai.mod.reforge.Progression.CharacterPathService.Bonus;
import irai.mod.reforge.Socket.Essence;
import irai.mod.reforge.Socket.EssenceEffect;
import irai.mod.reforge.Socket.SocketData;
import irai.mod.reforge.Socket.SocketManager;
import irai.mod.reforge.Util.DynamicTooltipUtils;
import irai.mod.reforge.Util.LangLoader;

/** Native Hytale UI and refinement logic for the equipment refinement bench. */
public final class ReforgeBenchUI {
    private ReforgeBenchUI() {}

    private static final String NATIVE_PAGE_UI     = "Pages/RefinementBench.ui";
    private static final String EQUIPMENT_CARD_UI  = "Pages/RefinementEquipmentCard.ui";
    private static final String MATERIAL_CARD_UI   = "Pages/RefinementMaterialCard.ui";
    private static final String SUPPORT_CARD_UI    = "Pages/RefinementSupportCard.ui";
    private static final String SUPPORT_NONE_CARD_UI = "Pages/RefinementSupportNoneCard.ui";

    private static final String DEFAULT_MATERIAL_ID = "Refinement_Glob";
    private static final String ANTI_DEGRADATION_ID = "Anti_Degradation";
    private static final String SUPPORT_KEY_SEPARATOR = ";";
    private static final double ANTI_DEGRADATION_BLOCK_CHANCE = 0.80d;
    private static final String HAMMER_IRON_ID = "Tool_Hammer_Iron";
    private static final String HAMMER_THORIUM_ID = "Tool_Hammer_Thorium";
    private static final String HAMMER_IRAINIUM_ID = "Tool_Hammer_Irainium";
    private static final int DEFAULT_MATERIAL_COST = 3;
    private static final int DEFAULT_MAX_UPGRADE_LEVEL = 3;
    private static final double HAMMER_IRON_BREAK_MULTIPLIER = 0.50d;
    private static final double HAMMER_THORIUM_BREAK_MULTIPLIER = 0.30d;
    private static final double HAMMER_IRAINIUM_BREAK_MULTIPLIER = 0.05d;
    private static final double HAMMER_IRON_DURABILITY_LOSS_FRACTION = 0.05d;
    private static final double HAMMER_THORIUM_DURABILITY_LOSS_FRACTION = 0.15d;
    private static final double HAMMER_IRAINIUM_DURABILITY_LOSS_FRACTION = 0.50d;
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
        THORIUM("ui.reforge.hammer_thorium", HAMMER_THORIUM_BREAK_MULTIPLIER, HAMMER_THORIUM_DURABILITY_LOSS_FRACTION),
        IRAINIUM("ui.reforge.hammer_irainium", HAMMER_IRAINIUM_BREAK_MULTIPLIER, HAMMER_IRAINIUM_DURABILITY_LOSS_FRACTION);

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
        PlayerRef ref = player.getPlayerRef();
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
            Ref<EntityStore> entityRef = playerRef.getReference();
            Store<EntityStore> store = entityRef == null ? null : entityRef.getStore();
            if (entityRef == null || !entityRef.isValid() || store == null) return;
            ReforgeNativePage page = new ReforgeNativePage(playerRef, snapshot, state);
            player.getPageManager().openCustomPage(entityRef, store, page);
        } catch (Exception e) {
            System.err.println("[SocketReforge] Native refinement bench open error: " + e.getMessage());
            e.printStackTrace();
        }
    }



    private static boolean shouldDisable(boolean processing, Entry selectedEquipment, int materialCount, int materialCost) {
        if (processing) return true;
        if (selectedEquipment == null) return true;
        int level = ReforgeEquip.getLevelFromItem(selectedEquipment.item);
        if (level >= getMaxUpgradeLevel()) return true;
        return materialCount < materialCost;
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

    private static List<Entry> liveAvailableSupports(Player player, List<Entry> supports) {
        if (player == null || supports == null || supports.isEmpty()) {
            return List.of();
        }
        List<Entry> available = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Entry support : supports) {
            if (support == null || support.itemId == null || support.itemId.isBlank()) {
                continue;
            }
            String normalized = normalizeSupportId(support.itemId);
            if (!seen.add(normalized)) {
                continue;
            }
            if (countMaterialGlobally(player, support.itemId) > 0) {
                available.add(support);
            }
        }
        return available;
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

    private static String sanitizeSupportKey(List<Entry> entries, String key) {
        Set<String> selected = supportSelectionSet(key);
        if (selected.isEmpty()) {
            return "";
        }
        Set<String> available = new LinkedHashSet<>();
        for (Entry entry : combinedSupports(entries)) {
            if (entry != null && entry.quantity > 0) {
                available.add(normalizeSupportId(entry.itemId));
            }
        }
        selected.retainAll(available);
        return String.join(SUPPORT_KEY_SEPARATOR, selected);
    }

    private static String normalizeSupportId(String itemId) {
        return itemId == null ? "" : itemId.trim().toLowerCase(Locale.ROOT);
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





    private static String supportDescription(Player player, Entry support) {
        RefinementConfig.SupportMaterial supportMaterial = supportMaterial(support);
        if (supportMaterial != null && !supportMaterial.description.isBlank()) {
            return supportMaterial.description;
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
        Entry breakSupportEntry = selectedBreakSupport(supports);
        RefinementConfig.SupportMaterial breakSupport = supportMaterial(breakSupportEntry);
        Entry breakBlockSupportEntry = selectedBreakBlockSupport(supports);
        RefinementConfig.SupportMaterial breakBlockSupport = supportMaterial(breakBlockSupportEntry);
        Entry antiDegradationEntry = selectedAntiDegradationSupport(supports);
        RefinementConfig.SupportMaterial antiDegradationSupport = supportMaterial(antiDegradationEntry);
        double antiDegradationChance = antiDegradationSupport == null ? 0.0d : antiDegradationSupport.antiDegradeChance;
        SupportOutcomeEffects supportEffects = supportOutcomeEffects(supports, player);
        Bonuses smithyBonuses = SmithyLegacyUtils.equippedBonuses(player);
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
            String chanceNote = breakSupport != null
                    ? t(player, "ui.reforge.preview_max_level_hammer", breakSupportEntry.displayName,
                        durabilityPercent(breakSupport))
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

        double rawBreakChance = effectiveBreakChance(level, isArmor, materialId, breakSupport, smithyBonuses)
                * (1.0d - supportEffects.breakProtectionFraction);
        double blockedBreakChance = breakBlockSupport == null
                ? 0.0d
                : rawBreakChance * (1.0d - breakBlockSupport.breakMultiplier);
        double breakChance = Math.max(0.0d, rawBreakChance - blockedBreakChance);
        double softcoreBreakProtectionChance = ReforgeEquip.getSoftcoreBreakProtectionChance(materialId);
        OutcomeWeights w = outcomeWeights(level, materialId, smithyBonuses, supportEffects);
        double survive = Math.max(0.0, 1.0 - rawBreakChance);
        double forcedJackpotChance = canJackpot(level) ? supportEffects.jackpotGuaranteeChance : 0.0d;
        double forcedUpgradeChance = supportEffects.upgradeGuaranteeChance;
        double rollPool = survive * (1.0d - forcedJackpotChance) * (1.0d - forcedUpgradeChance);

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

        double pDeg = rollPool * w.degrade;
        double pDegB = rollPool * w.degradeB;
        double pSame = rollPool * w.same + blockedBreakChance;
        double pUp = rollPool * w.upgrade + survive * (1.0d - forcedJackpotChance) * forcedUpgradeChance;
        double pJack = rollPool * w.jackpot + survive * forcedJackpotChance;
        double pJackB = rollPool * w.jackpotB;
        if (antiDegradationChance > 0.0d) {
            double blockedDegrade = (pDeg + pDegB) * antiDegradationChance;
            pSame += blockedDegrade;
            pDeg *= 1.0 - antiDegradationChance;
            pDegB *= 1.0 - antiDegradationChance;
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
                supportPreviewNote(player, breakSupportEntry, breakSupport, breakBlockSupportEntry, breakBlockSupport,
                        antiDegradationEntry, antiDegradationSupport, supportEffects, smithyBonuses),
                level);
    }

    private static ProcessResult processSelection(Player player, Entry equipment, Entry material, List<Entry> supports) {
        if (equipment == null) return new ProcessResult(t(player, "ui.reforge.error_pick_equipment"), 0);

        ItemStack current = readCurrentStack(player, equipment);
        if (current == null || current.isEmpty()) return new ProcessResult(t(player, "ui.reforge.error_equipment_changed"), 0);

        boolean isWeapon = ReforgeEquip.isWeapon(current);
        boolean isArmor = !isWeapon && ReforgeEquip.isArmor(current);
        if (!isWeapon && !isArmor) return new ProcessResult(t(player, "ui.reforge.error_item_not_refinable"), 0);

        if (CorruptionUtils.isCorrupted(current)) return new ProcessResult(t(player, "ui.reforge.error_corrupted"), 0);

        int level = ReforgeEquip.getLevelFromItem(current);
        if (level >= getMaxUpgradeLevel()) return new ProcessResult(t(player, "ui.reforge.error_max_level"), 100);

        MaterialRequirement requirement = getMaterialRequirement(player, equipment, material);
        String materialId = requirement != null ? requirement.itemId : DEFAULT_MATERIAL_ID;
        String materialName = requirement != null ? requirement.displayName : LangLoader.getUITranslation(player, "ui.reforge.material");
        int materialCost = requirement != null ? requirement.cost : DEFAULT_MATERIAL_COST;

        if (countMaterialGlobally(player, materialId) < materialCost) {
            return new ProcessResult(t(player, "ui.reforge.error_need_material", materialCost, materialName), 0);
        }

        supports = liveAvailableSupports(player, supports);
        Entry breakSupportEntry = selectedBreakSupport(supports);
        RefinementConfig.SupportMaterial breakSupport = supportMaterial(breakSupportEntry);
        Entry breakBlockSupportEntry = selectedBreakBlockSupport(supports);
        RefinementConfig.SupportMaterial breakBlockSupport = supportMaterial(breakBlockSupportEntry);
        Entry antiDegradationEntry = selectedAntiDegradationSupport(supports);
        RefinementConfig.SupportMaterial antiDegradationSupport = supportMaterial(antiDegradationEntry);
        double antiDegradationChance = antiDegradationSupport == null ? 0.0d : antiDegradationSupport.antiDegradeChance;
        SupportOutcomeEffects supportEffects = supportOutcomeEffects(supports, player);
        Bonuses smithyBonuses = SmithyLegacyUtils.equippedBonuses(player);
        HammerUseResult hammerUse = new HammerUseResult(true, false);
        if (breakSupport != null && breakSupport.usesDurability()) {
            hammerUse = applySupportWear(player, breakSupportEntry, breakSupport.durabilityLossFraction);
            if (!hammerUse.ok) {
                return new ProcessResult(t(player, "ui.reforge.error_hammer_changed"), 0);
            }
        }

        if (!consumeMaterialGlobally(player, materialId, materialCost)) {
            return new ProcessResult(t(player, "ui.reforge.error_material_consume", materialName), 0);
        }

        ProcessResult consumeOnUseResult = consumeOnUseSupports(player, supports);
        if (consumeOnUseResult != null) {
            return consumeOnUseResult;
        }
        CharacterPathService.awardBenchXp(player, 20, "refining");

        double breakChance = effectiveBreakChance(level, isArmor, materialId, breakSupport, smithyBonuses)
                * (1.0d - supportEffects.breakProtectionFraction);
        String breakChanceSuffix = " " + t(player, "ui.reforge.break_roll_info", formatPercent(breakChance));
        String hammerSuffix = "";
        if (breakSupport != null) {
            String hammerLabel = breakSupportEntry.displayName;
            String rawSuffix = hammerUse.consumed
                    ? t(player, "ui.reforge.hammer_broke", hammerLabel)
                    : breakSupport.usesDurability()
                    ? t(player, "ui.reforge.hammer_durability_loss", hammerLabel, durabilityPercent(breakSupport))
                    : "";
            if (rawSuffix != null && !rawSuffix.isBlank()) {
                hammerSuffix = " " + rawSuffix;
            }
        }
        String smithySuffix = smithyBonuses != null
                ? " " + t(player, "ui.reforge.smithy_chest_roll_info",
                        SmithyLegacyUtils.localizedName(smithyBonuses.legacy(), LangLoader.getPlayerLanguage(player)))
                : "";
        String resultSuffix = hammerSuffix + smithySuffix + breakChanceSuffix;
        if (Math.random() < breakChance) {
            if (breakBlockSupport != null && Math.random() >= breakBlockSupport.breakMultiplier) {
                ProcessResult blockConsumeResult = consumeBlockedSupport(player, breakBlockSupportEntry, breakBlockSupport);
                if (blockConsumeResult != null) {
                    return blockConsumeResult;
                }
                sfxConfig.playNoChange(player);
                return new ProcessResult(t(player, "ui.reforge.result_same", level,
                        hammerSuffix + " " + breakBlockSupportEntry.displayName + ": "
                                + t(player, "ui.reforge.anti_degradation_blocked")
                                + smithySuffix + breakChanceSuffix), 100);
            }
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
                                resultSuffix),
                        100);
            }
            removeEquipment(player, equipment);
            sfxConfig.playShatter(player);
            return new ProcessResult(t(player, "ui.reforge.result_shattered", resultSuffix), 0);
        }

        ReforgeOutcome outcome = rollOutcome(level, materialId, smithyBonuses, supportEffects);
        String antiDegradationSuffix = "";
        if (antiDegradationChance > 0.0d
                && (outcome.type == OutcomeType.DEGRADE || outcome.type == OutcomeType.DEGRADE_B)
                && Math.random() < antiDegradationChance) {
            if (antiDegradationSupport.consumeMode == RefinementConfig.SupportConsumeMode.ON_BLOCK) {
                ProcessResult blockConsumeResult = consumeBlockedSupport(player, antiDegradationEntry, antiDegradationSupport);
                if (blockConsumeResult != null) {
                    return blockConsumeResult;
                }
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
                return new ProcessResult(t(player, "ui.reforge.result_degraded", level, newLevel, resultSuffix), 100);
            }
            case SAME -> {
                sfxConfig.playNoChange(player);
                return new ProcessResult(t(player, "ui.reforge.result_same", level,
                        hammerSuffix + antiDegradationSuffix + smithySuffix + breakChanceSuffix), 100);
            }
            case UPGRADE -> {
                sfxConfig.playSuccess(player);
                return new ProcessResult(t(player, "ui.reforge.result_success", level, newLevel, resultSuffix), 100);
            }
            case JACKPOT -> {
                sfxConfig.playJackpot(player);
                return new ProcessResult(t(player, "ui.reforge.result_jackpot", level, newLevel, resultSuffix), 100);
            }
            case JACKPOT_B -> {
                sfxConfig.playJackpot(player);
                return new ProcessResult(t(player, "ui.reforge.result_jackpot", level, newLevel, resultSuffix), 100);
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
        return rollOutcome(level, materialId, null);
    }

    private static ReforgeOutcome rollOutcome(int level, String materialId, Bonuses smithyBonuses) {
        return rollOutcome(level, materialId, smithyBonuses, SupportOutcomeEffects.NONE);
    }

    private static ReforgeOutcome rollOutcome(int level, String materialId, Bonuses smithyBonuses, SupportOutcomeEffects supportEffects) {
        SupportOutcomeEffects effects = supportEffects == null ? SupportOutcomeEffects.NONE : supportEffects;
        if (canJackpot(level) && Math.random() < effects.jackpotGuaranteeChance) {
            boolean jackpotB = level + 3 <= getMaxUpgradeLevel();
            return new ReforgeOutcome(jackpotB ? 3 : 2, jackpotB ? OutcomeType.JACKPOT_B : OutcomeType.JACKPOT);
        }
        if (Math.random() < effects.upgradeGuaranteeChance) {
            return new ReforgeOutcome(1, OutcomeType.UPGRADE);
        }
        OutcomeWeights w = outcomeWeights(level, materialId, smithyBonuses, effects);
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
        return outcomeWeights(level, materialId, null);
    }

    private static OutcomeWeights outcomeWeights(int level, String materialId, Bonuses smithyBonuses) {
        return outcomeWeights(level, materialId, smithyBonuses, SupportOutcomeEffects.NONE);
    }

    private static OutcomeWeights outcomeWeights(int level, String materialId, Bonuses smithyBonuses, SupportOutcomeEffects supportEffects) {
        double[] w = weights(level, materialId);
        w = SmithyLegacyUtils.applyToOutcomeWeights(w, smithyBonuses);
        SupportOutcomeEffects effects = supportEffects == null ? SupportOutcomeEffects.NONE : supportEffects;
        double degradeTotal = weightAt(w, 0) * effects.degradeWeightMultiplier;
        double same = weightAt(w, 1) * effects.sameWeightMultiplier;
        double upgrade = weightAt(w, 2) * effects.upgradeWeightMultiplier;
        double jackpotTotal = weightAt(w, 3) * effects.jackpotWeightMultiplier;

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

    /** Native Hytale UI page for equipment refinement. */
    private static final class ReforgeNativePage extends InteractiveCustomUIPage<ReforgeNativePage.PageEvent> {
        private final PlayerRef owner;
        private Snapshot snapshot;
        private SelectionState state;
        private volatile boolean dismissed;

        ReforgeNativePage(PlayerRef owner, Snapshot snapshot, SelectionState state) {
            super(owner, CustomPageLifetime.CanDismiss, PageEvent.CODEC);
            this.owner = owner;
            this.snapshot = snapshot;
            this.state = state;
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
            switch (event.action) {
                case "select_equipment" -> {
                    state = new SelectionState(event.target, state == null ? null : state.materialKey,
                            state == null ? "" : state.supportKey, null, 0, false);
                    refresh(player);
                }
                case "select_material" -> {
                    state = new SelectionState(state == null ? null : state.equipmentKey, event.target,
                            state == null ? "" : state.supportKey, null, 0, false);
                    refresh(player);
                }
                case "select_support" -> {
                    String selected = state == null ? "" : state.supportKey;
                    state = new SelectionState(state == null ? null : state.equipmentKey,
                            state == null ? null : state.materialKey,
                            toggleSupportKey(selected, event.target), null, 0, false);
                    refresh(player);
                }
                case "clear_supports" -> {
                    state = new SelectionState(state == null ? null : state.equipmentKey,
                            state == null ? null : state.materialKey, "", null, 0, false);
                    refresh(player);
                }
                case "refine" -> startRefinement(player);
                default -> { }
            }
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
            UICommandBuilder commands = new UICommandBuilder();
            UIEventBuilder events = new UIEventBuilder();
            populate(player, commands, events, true);
            sendUpdate(commands, events, false);
        }

        private void populate(Player player, UICommandBuilder commands,
                              UIEventBuilder events, boolean replaceContents) {
            if (snapshot == null) snapshot = collectSnapshot(player);
            if (state == null) state = new SelectionState(null, null, "", null, 0, false);
            String equipmentKey = state.equipmentKey;
            String materialKey = state.materialKey;
            String supportKey = sanitizeSupportKey(snapshot.supports, state.supportKey);
            boolean processing = state.processing;
            int progress = processing ? Math.max(0, Math.min(100, state.progressValue)) : 0;

            Entry selectedEquipment = resolveSelection(snapshot.equipments, equipmentKey);
            Entry selectedMaterial = resolveMaterialKey(snapshot.materials, materialKey);
            List<Entry> selectedSupports = resolveSupportKeys(snapshot.supports, supportKey);
            MaterialRequirement requiredMaterial = getMaterialRequirement(player, selectedEquipment);
            if (selectedMaterial == null && requiredMaterial != null) {
                selectedMaterial = findFirstByItemId(snapshot.materials, requiredMaterial.itemId);
                materialKey = requiredMaterial.itemId;
            }
            MaterialRequirement materialRequirement = getMaterialRequirement(player, selectedEquipment, selectedMaterial);
            int materialCost = materialRequirement == null ? DEFAULT_MATERIAL_COST : materialRequirement.cost;
            String materialName = materialRequirement == null
                    ? t(player, "ui.reforge.material")
                    : resolveMaterialName(player, materialRequirement.itemId,
                            findFirstByItemId(snapshot.materials, materialRequirement.itemId));
            int materialCount = materialRequirement == null ? 0
                    : countMaterials(snapshot.materials, materialRequirement.itemId);

            Preview preview = buildPreview(player, selectedEquipment, selectedMaterial, selectedSupports);
            String status = state.statusText == null ? t(player, "ui.reforge.status_idle") : state.statusText;
            if (!processing) {
                if (selectedEquipment == null) {
                    status = t(player, "ui.reforge.status_no_equipment");
                } else if (preview.currentLevel >= getMaxUpgradeLevel()) {
                    status = t(player, "ui.reforge.status_max_level");
                } else if (materialCount < materialCost) {
                    status = t(player, "ui.reforge.status_need_material", materialCost, materialName);
                } else if (t(player, "ui.reforge.status_idle").equals(status)) {
                    status = t(player, "ui.reforge.status_ready");
                }
            }
            state = new SelectionState(equipmentKey, materialKey, supportKey, status, progress, processing);
            pendingSelections.put(owner, state);

            commands.set("#BenchTitle.Text", t(player, "ui.reforge.title"));
            commands.set("#EquipmentPanelTitle.Text", t(player, "ui.reforge.equipment"));
            commands.set("#EquipmentHint.Text", t(player, "ui.reforge.equipment"));
            commands.set("#CurrentDamageTitle.Text", t(player, "ui.reforge.current_damage"));
            commands.set("#ExpectedDamageTitle.Text", t(player, "ui.reforge.expected_damage"));
            commands.set("#RefinementPanelTitle.Text", t(player, "ui.reforge.material"));
            commands.set("#ChancePanelTitle.Text", t(player, "ui.reforge.expected_outcome"));
            commands.set("#ChanceBreakLabel.Text", t(player, "ui.reforge.chance_break"));
            commands.set("#ChanceDegradeLabel.Text", t(player, "ui.reforge.chance_degrade"));
            commands.set("#ChanceSameLabel.Text", t(player, "ui.reforge.chance_same"));
            commands.set("#ChanceUpgradeLabel.Text", t(player, "ui.reforge.chance_upgrade"));
            commands.set("#ChanceJackpotLabel.Text", t(player, "ui.reforge.chance_jackpot"));
            commands.set("#ChanceBreakValue.Text", preview.chanceBreak);
            commands.set("#ChanceDegradeValue.Text", preview.chanceDegrade);
            commands.set("#ChanceSameValue.Text", preview.chanceSame);
            commands.set("#ChanceUpgradeValue.Text", preview.chanceUpgrade);
            commands.set("#ChanceJackpotValue.Text", preview.chanceJackpot);
            commands.set("#ChanceNote.Text", preview.chanceNote);
            commands.set("#SelectedEquipmentIcon.ItemId",
                    selectedEquipment == null ? "" : selectedEquipment.itemId);
            commands.set("#SelectedEquipmentIcon.Visible", selectedEquipment != null);
            commands.set("#SelectedEquipmentName.Text", buildMetadataName(selectedEquipment));
            commands.set("#SelectedEquipmentLevel.Text", buildMetadataLevel(selectedEquipment));
            commands.set("#RefineProgressTitle.Text", t(player, "ui.reforge.refine_progress"));
            commands.set("#RefineStatus.Text", status);
            commands.set("#RefineMaterialCost.Text",
                    t(player, "ui.reforge.consumes_material", materialCost, materialName));
            commands.set("#RefineButton.Text", t(player, "ui.reforge.button_refine"));
            commands.set("#RefineProgress.Value", progress / 100.0f);
            commands.set("#RefinePercent.Text", progress + "%");

            boolean canProcess = !shouldDisable(processing, selectedEquipment, materialCount, materialCost);
            commands.set("#RefineButton.Visible", canProcess);
            commands.set("#RefineUnavailable.Visible", !canProcess && !processing);
            commands.set("#RefineBusy.Visible", processing);
            events.addEventBinding(CustomUIEventBindingType.Activating, "#RefineButton",
                    EventData.of("Action", "refine"));
            if (replaceContents) {
                commands.clear("#EquipmentList");
                commands.clear("#MaterialCardRow");
                commands.clear("#SupportList");
                commands.clear("#CurrentDamageRows");
                commands.clear("#ExpectedDamageRows");
            }
            appendEquipmentCards(commands, events, player, equipmentKey);
            appendMaterialCards(commands, events, player, materialKey);
            appendSupportCards(commands, events, player, supportKey);
            appendStatRows(commands, "#CurrentDamageRows", buildDamageRows(player, selectedEquipment, false));
            appendStatRows(commands, "#ExpectedDamageRows", buildDamageRows(player, selectedEquipment, true));
        }

        private void appendEquipmentCards(UICommandBuilder commands, UIEventBuilder events,
                                          Player player, String selectedKey) {
            if (snapshot.equipments.isEmpty()) {
                commands.set("#EquipmentEmpty.Text", t(player, "ui.reforge.no_equipment"));
                commands.set("#EquipmentEmpty.Visible", true);
            } else {
                commands.set("#EquipmentEmpty.Visible", false);
            }
            int height = 0;
            for (int i = 0; i < snapshot.equipments.size(); i++) {
                Entry entry = snapshot.equipments.get(i);
                if (entry == null || entry.item == null || entry.item.isEmpty()) continue;
                commands.append("#EquipmentList", EQUIPMENT_CARD_UI);
                String base = child("#EquipmentList", i);
                boolean selected = String.valueOf(i).equals(selectedKey);
                int level = ReforgeEquip.getLevelFromItem(entry.item);
                commands.set(base + " #EquipmentIcon.ItemId", entry.itemId);
                commands.set(base + " #EquipmentName.Text", entry.displayName);
                commands.set(base + " #EquipmentTier.Text", (ReforgeEquip.isArmor(entry.item)
                        && !ReforgeEquip.isWeapon(entry.item) ? "Armor" : "Weapon")
                        + "  |  +" + level + "  |  " + tierLabelForLevel(level));
                commands.set(base + " #EquipmentSelected.Visible", selected);
                events.addEventBinding(CustomUIEventBindingType.Activating, base + " #EquipmentCardHit",
                        EventData.of("Action", "select_equipment").append("Target", String.valueOf(i)));
                height += 82;
            }
            commands.set("#EquipmentList.ContentHeight", Math.max(720, height));
        }

        private void appendMaterialCards(UICommandBuilder commands, UIEventBuilder events,
                                         Player player, String selectedMaterialId) {
            List<RefinementConfig.MaterialTier> tiers = materialTierCards();
            for (int i = 0; i < tiers.size(); i++) {
                RefinementConfig.MaterialTier tier = tiers.get(i);
                String itemId = tier.itemId == null || tier.itemId.isBlank()
                        ? DEFAULT_MATERIAL_ID : tier.itemId;
                Entry owned = findFirstByItemId(snapshot.materials, itemId);
                int count = countMaterials(snapshot.materials, itemId);
                commands.append("#MaterialCardRow", MATERIAL_CARD_UI);
                String base = child("#MaterialCardRow", i);
                boolean selected = itemId.equalsIgnoreCase(selectedMaterialId == null ? "" : selectedMaterialId);
                commands.set(base + " #MaterialIcon.ItemId", itemId);
                commands.set(base + " #MaterialName.Text", resolveMaterialName(player, itemId, owned));
                commands.set(base + " #MaterialCount.Text", "x" + count);
                commands.set(base + " #MaterialSelected.Visible", selected);
                events.addEventBinding(CustomUIEventBindingType.Activating, base + " #MaterialCardHit",
                        EventData.of("Action", "select_material").append("Target", itemId));
            }
            commands.set("#MaterialCardRow.ContentWidth", tiers.size() * 104);
        }

        private void appendSupportCards(UICommandBuilder commands, UIEventBuilder events,
                                        Player player, String selectedSupportKey) {
            Set<String> selectedIds = supportSelectionSet(selectedSupportKey);
            commands.append("#SupportList", SUPPORT_NONE_CARD_UI);
            String noneCard = child("#SupportList", 0);
            commands.set(noneCard + " #SupportNoneSelected.Visible", selectedIds.isEmpty());
            commands.set(noneCard + " #SupportNoneText.Text", t(player, "ui.reforge.support_none"));
            events.addEventBinding(CustomUIEventBindingType.Activating, noneCard + " #SupportNoneHit",
                    EventData.of("Action", "clear_supports"));
            List<Entry> supports = combinedSupports(snapshot.supports);
            int height = 64;
            for (int i = 0; i < supports.size(); i++) {
                Entry entry = supports.get(i);
                if (entry == null || entry.item == null || entry.item.isEmpty()) continue;
                commands.append("#SupportList", SUPPORT_CARD_UI);
                String base = child("#SupportList", i + 1);
                boolean selected = selectedIds.contains(normalizeSupportId(entry.itemId));
                commands.set(base + " #SupportIcon.ItemId", entry.itemId);
                commands.set(base + " #SupportName.Text", entry.displayName);
                commands.set(base + " #SupportDescription.Text", supportDescription(player, entry));
                commands.set(base + " #SupportQuantity.Text", "Held: " + entry.quantity);
                commands.set(base + " #SupportSelected.Visible", selected);
                events.addEventBinding(CustomUIEventBindingType.Activating, base + " #SupportCardHit",
                        EventData.of("Action", "select_support").append("Target", entry.itemId));
                height += 98;
            }
            commands.set("#SupportList.ContentHeight", Math.max(560, height));
        }

        private void appendStatRows(UICommandBuilder commands, String parent, String content) {
            String[] rows = content == null ? new String[0] : content.split("\\n");
            int count = 0;
            for (String row : rows) {
                if (row == null || row.isBlank()) continue;
                String[] parts = row.split("\\t", 2);
                commands.append(parent, "Pages/RefinementStatRow.ui");
                String base = child(parent, count++);
                commands.set(base + " #StatLabel.Text", parts.length > 0 ? parts[0] : "");
                commands.set(base + " #StatValue.Text", parts.length > 1 ? parts[1] : "");
            }
            commands.set(parent + ".ContentHeight", count * 26);
        }

        private void startRefinement(Player player) {
            if (dismissed || Boolean.TRUE.equals(processingPlayers.get(owner))) return;
            snapshot = collectSnapshot(player);
            String equipmentKey = state == null ? null : state.equipmentKey;
            String materialKey = state == null ? null : state.materialKey;
            String supportKey = sanitizeSupportKey(snapshot.supports, state == null ? "" : state.supportKey);
            Entry equipment = resolveSelection(snapshot.equipments, equipmentKey);
            Entry material = resolveMaterialKey(snapshot.materials, materialKey);
            List<Entry> supports = resolveSupportKeys(snapshot.supports, supportKey);
            int materialCount = material == null ? 0 : countMaterials(snapshot.materials, material.itemId);
            MaterialRequirement requirement = getMaterialRequirement(player, equipment, material);
            int cost = requirement == null ? DEFAULT_MATERIAL_COST : requirement.cost;
            if (shouldDisable(false, equipment, materialCount, cost)) {
                refresh(player);
                return;
            }

            processingPlayers.put(owner, true);
            state = new SelectionState(equipmentKey, materialKey, supportKey,
                    t(player, "ui.reforge.status_processing"), 0, true);
            sfxConfig.playReforgeStart(player);
            refresh(player);
            for (int elapsed = PROGRESS_TICK_MS; elapsed < PROCESS_DURATION_MS; elapsed += PROGRESS_TICK_MS) {
                final int delay = elapsed;
                final int timedProgress = Math.min(99,
                        (int) Math.round(((delay + PROGRESS_TICK_MS) * 100.0) / PROCESS_DURATION_MS));
                scheduler.schedule(() -> player.getWorld().execute(() -> updateProgress(player, timedProgress)),
                        delay, TimeUnit.MILLISECONDS);
            }
            scheduler.schedule(() -> player.getWorld().execute(() -> {
                try {
                    if (dismissed || !Boolean.TRUE.equals(processingPlayers.get(owner))) return;
                    ProcessResult result = processSelection(player, equipment, material, supports);
                    state = new SelectionState(equipmentKey, materialKey, supportKey,
                            result.status, result.progress, false);
                } finally {
                    processingPlayers.remove(owner);
                    if (!dismissed) refresh(player);
                }
            }), PROCESS_DURATION_MS, TimeUnit.MILLISECONDS);
        }

        private void updateProgress(Player player, int progress) {
            if (dismissed || !Boolean.TRUE.equals(processingPlayers.get(owner))) return;
            state = new SelectionState(state.equipmentKey, state.materialKey, state.supportKey,
                    t(player, "ui.reforge.status_processing"), progress, true);
            pendingSelections.put(owner, state);
            UICommandBuilder commands = new UICommandBuilder();
            commands.set("#RefineProgress.Value", progress / 100.0f);
            commands.set("#RefinePercent.Text", progress + "%");
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

    private static final class SupportOutcomeEffects {
        static final SupportOutcomeEffects NONE = new SupportOutcomeEffects(
                0.0d, 0.0d, 0.0d, 1.0d, 1.0d, 1.0d, 1.0d);

        final double upgradeGuaranteeChance;
        final double jackpotGuaranteeChance;
        final double breakProtectionFraction;
        final double degradeWeightMultiplier;
        final double sameWeightMultiplier;
        final double upgradeWeightMultiplier;
        final double jackpotWeightMultiplier;

        SupportOutcomeEffects(double upgradeGuaranteeChance, double jackpotGuaranteeChance,
                              double breakProtectionFraction, double degradeWeightMultiplier,
                              double sameWeightMultiplier, double upgradeWeightMultiplier,
                              double jackpotWeightMultiplier) {
            this.upgradeGuaranteeChance = Math.max(0.0d, Math.min(1.0d, upgradeGuaranteeChance));
            this.jackpotGuaranteeChance = Math.max(0.0d, Math.min(1.0d, jackpotGuaranteeChance));
            this.breakProtectionFraction = Math.max(0.0d, Math.min(0.95d, breakProtectionFraction));
            this.degradeWeightMultiplier = Math.max(0.0d, Math.min(10.0d, degradeWeightMultiplier));
            this.sameWeightMultiplier = Math.max(0.0d, Math.min(10.0d, sameWeightMultiplier));
            this.upgradeWeightMultiplier = Math.max(0.0d, Math.min(10.0d, upgradeWeightMultiplier));
            this.jackpotWeightMultiplier = Math.max(0.0d, Math.min(10.0d, jackpotWeightMultiplier));
        }

        boolean hasAny() {
            return upgradeGuaranteeChance > 0.0d
                    || jackpotGuaranteeChance > 0.0d
                    || breakProtectionFraction > 0.0d
                    || Math.abs(degradeWeightMultiplier - 1.0d) > 0.0001d
                    || Math.abs(sameWeightMultiplier - 1.0d) > 0.0001d
                    || Math.abs(upgradeWeightMultiplier - 1.0d) > 0.0001d
                    || Math.abs(jackpotWeightMultiplier - 1.0d) > 0.0001d;
        }
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

    private static double effectiveBreakChance(int currentLevel, boolean isArmor, String materialId,
                                               RefinementConfig.SupportMaterial breakSupport, Bonuses smithyBonuses) {
        double base = refinementConfig != null
                ? refinementConfig.getAdjustedBreakChance(currentLevel, isArmor, materialId)
                : breakChance(currentLevel, isArmor);
        double multiplier = breakSupport == null ? 1.0d : breakSupport.breakMultiplier;
        if (smithyBonuses != null) {
            multiplier *= smithyBonuses.breakMultiplier();
        }
        return Math.max(0.0, Math.min(1.0, base * multiplier));
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

    private static boolean canJackpot(int level) {
        return level + 2 <= getMaxUpgradeLevel();
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
        return supportMaterial(itemId) != null;
    }



    private static RefinementConfig.SupportMaterial supportMaterial(Entry support) {
        return support == null ? null : supportMaterial(support.itemId);
    }

    private static RefinementConfig.SupportMaterial supportMaterial(String itemId) {
        return refinementConfig == null ? null : refinementConfig.getSupportMaterial(itemId);
    }

    private static Entry selectedBreakSupport(List<Entry> supports) {
        if (supports == null || supports.isEmpty()) {
            return null;
        }
        Entry selected = null;
        double bestMultiplier = Double.POSITIVE_INFINITY;
        for (Entry support : supports) {
            RefinementConfig.SupportMaterial material = supportMaterial(support);
            if (material == null
                    || material.consumeMode == RefinementConfig.SupportConsumeMode.ON_BLOCK
                    || !material.reducesBreakChance()) {
                continue;
            }
            if (material.breakMultiplier < bestMultiplier) {
                selected = support;
                bestMultiplier = material.breakMultiplier;
            }
        }
        return selected;
    }

    private static Entry selectedBreakBlockSupport(List<Entry> supports) {
        if (supports == null || supports.isEmpty()) {
            return null;
        }
        Entry selected = null;
        double bestMultiplier = Double.POSITIVE_INFINITY;
        for (Entry support : supports) {
            RefinementConfig.SupportMaterial material = supportMaterial(support);
            if (material == null
                    || material.consumeMode != RefinementConfig.SupportConsumeMode.ON_BLOCK
                    || !material.reducesBreakChance()) {
                continue;
            }
            if (material.breakMultiplier < bestMultiplier) {
                selected = support;
                bestMultiplier = material.breakMultiplier;
            }
        }
        return selected;
    }

    private static Entry selectedAntiDegradationSupport(List<Entry> supports) {
        if (supports == null || supports.isEmpty()) {
            return null;
        }
        Entry selected = null;
        double bestChance = 0.0d;
        for (Entry support : supports) {
            RefinementConfig.SupportMaterial material = supportMaterial(support);
            if (material == null || !material.blocksDegradation()) {
                continue;
            }
            if (material.antiDegradeChance > bestChance) {
                selected = support;
                bestChance = material.antiDegradeChance;
            }
        }
        return selected;
    }

    private static SupportOutcomeEffects supportOutcomeEffects(List<Entry> supports, Player player) {
        double upgradeGuaranteeMiss = 1.0d;
        double jackpotGuaranteeMiss = 1.0d;
        double upgradeWeightMultiplier = 1.0d;
        double jackpotWeightMultiplier = 1.0d;
        boolean any = false;
        Set<String> appliedIds = new LinkedHashSet<>();
        for (Entry support : supports == null ? List.<Entry>of() : supports) {
            RefinementConfig.SupportMaterial material = supportMaterial(support);
            if (material == null
                    || material.consumeMode == RefinementConfig.SupportConsumeMode.ON_BLOCK
                    || !material.affectsOutcomeRoll()) {
                continue;
            }
            String normalized = normalizeSupportId(material.itemId);
            if (!appliedIds.add(normalized)) {
                continue;
            }
            any = true;
            upgradeGuaranteeMiss *= 1.0d - material.upgradeGuaranteeChance;
            jackpotGuaranteeMiss *= 1.0d - material.jackpotGuaranteeChance;
            upgradeWeightMultiplier *= material.upgradeWeightMultiplier;
            jackpotWeightMultiplier *= material.jackpotWeightMultiplier;
        }
        double pathUpgradeGuarantee = CharacterPathService.bonus(player, Bonus.REFINING_CHANCE);
        if (pathUpgradeGuarantee > 0.0d) {
            upgradeGuaranteeMiss *= 1.0d - Math.min(0.25d, pathUpgradeGuarantee);
            any = true;
        }

        // Tree bonuses alter the same outcome weights used by Smithy chest
        // legacies. Positive protection lowers the bad-outcome weights, while
        // chance bonuses raise the selected safe/success outcome's weight.
        double breakProtection = Math.min(0.75d, Math.max(0.0d,
                CharacterPathService.bonus(player, Bonus.REFINING_BREAK_PROTECTION)));
        double degradeProtection = Math.min(0.75d, Math.max(0.0d,
                CharacterPathService.bonus(player, Bonus.REFINING_DEGRADE_PROTECTION)));
        double sameChance = Math.min(9.0d, Math.max(0.0d,
                CharacterPathService.bonus(player, Bonus.REFINING_SAME_CHANCE)));
        double upgradeChance = Math.min(9.0d, Math.max(0.0d,
                CharacterPathService.bonus(player, Bonus.REFINING_UPGRADE_CHANCE)));
        double jackpotChance = Math.min(9.0d, Math.max(0.0d,
                CharacterPathService.bonus(player, Bonus.REFINING_JACKPOT_CHANCE)));
        any |= breakProtection > 0.0d || degradeProtection > 0.0d
                || sameChance > 0.0d || upgradeChance > 0.0d || jackpotChance > 0.0d;
        if (!any) {
            return SupportOutcomeEffects.NONE;
        }
        return new SupportOutcomeEffects(
                1.0d - upgradeGuaranteeMiss,
                1.0d - jackpotGuaranteeMiss,
                breakProtection,
                1.0d - degradeProtection,
                1.0d + sameChance,
                upgradeWeightMultiplier * (1.0d + upgradeChance),
                jackpotWeightMultiplier * (1.0d + jackpotChance));
    }

    private static int durabilityPercent(RefinementConfig.SupportMaterial support) {
        if (support == null) return 0;
        return (int) Math.round(support.durabilityLossFraction * 100.0);
    }

    private static HammerUseResult applySupportWear(Player player, Entry supportEntry, double durabilityFraction) {
        if (player == null || supportEntry == null || supportMaterial(supportEntry) == null) {
            return new HammerUseResult(false, false);
        }
        ItemContainer container = UIInventoryUtils.getContainer(player, supportEntry.container == ContainerKind.HOTBAR);
        if (container == null) {
            return new HammerUseResult(false, false);
        }
        UIItemUtils.HammerWearResult wear = UIItemUtils.applyHammerWear(
                container,
                supportEntry.slot,
                durabilityFraction,
                itemId -> supportMaterial(itemId) != null);
        if (!wear.ok()) {
            return new HammerUseResult(false, false);
        }
        return new HammerUseResult(true, wear.consumed());
    }

    private static ProcessResult consumeOnUseSupports(Player player, List<Entry> supports) {
        if (supports == null || supports.isEmpty()) {
            return null;
        }
        Set<String> consumedIds = new LinkedHashSet<>();
        for (Entry support : supports) {
            RefinementConfig.SupportMaterial material = supportMaterial(support);
            if (material == null || material.consumeMode != RefinementConfig.SupportConsumeMode.ON_USE) {
                continue;
            }
            String normalized = normalizeSupportId(material.itemId);
            if (!consumedIds.add(normalized)) {
                continue;
            }
            if (!consumeMaterialGlobally(player, material.itemId, 1)) {
                return new ProcessResult(t(player, "ui.reforge.error_material_consume", support.displayName), 0);
            }
        }
        return null;
    }

    private static ProcessResult consumeBlockedSupport(Player player, Entry supportEntry,
                                                       RefinementConfig.SupportMaterial material) {
        if (supportEntry == null || material == null) {
            return null;
        }
        if (material.usesDurability()) {
            HammerUseResult wear = applySupportWear(player, supportEntry, material.durabilityLossFraction);
            if (!wear.ok) {
                return new ProcessResult(t(player, "ui.reforge.error_hammer_changed"), 0);
            }
            return null;
        }
        if (material.consumeMode == RefinementConfig.SupportConsumeMode.ON_BLOCK
                && !consumeMaterialGlobally(player, material.itemId, 1)) {
            return new ProcessResult(t(player, "ui.reforge.error_material_consume", supportEntry.displayName), 0);
        }
        return null;
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



    private static String buildMetadataName(Entry equipment) {
        if (equipment == null || equipment.item == null || equipment.item.isEmpty()) {
            return "-";
        }
        return equipment.displayName;
    }

    private static String buildCurrentDamageRows(Player player, Entry equipment) {
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
            appendArmorAffinityRows(player, sb, item);
            return sb.toString();
        }

        List<EquipmentDamageTooltipMath.DamageBreakdown> breakdowns =
                EquipmentDamageTooltipMath.getDamageBreakdownFromInteractionVars(item.getItemId());
        WeaponDamageProfile averageProfile = weaponProfile(player, item, level, 0.0);
        sb.append(statRow(t(player, "ui.reforge.damage_multiplier"), "x" + format3(averageProfile.multiplier), "#83D8FF", "#83D8FF"));
        sb.append(statRow(t(player, "ui.reforge.essence_resonance_damage"), bonusLine(averageProfile.damageFlat, averageProfile.damagePercent), "#77DD77", "#77DD77"));
        appendWeaponElementalDamageRows(player, sb, item, averageProfile.normal);
        sb.append(statRow(t(player, "ui.reforge.crit"), formatPercent(averageProfile.critChance / 100.0)
                + " / +" + formatPercent(averageProfile.critDamage / 100.0), "#FF8888", "#FFAAAA"));
        sb.append(statRow("Multi-hit Chance", formatPercent(averageProfile.multiHitChancePercent / 100.0), "#F8D36A", "#F8D36A"));
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
            WeaponDamageProfile profile = weaponProfile(player, item, level, breakdown.getBaseValue());
            sb.append(statRow(breakdown.getLabel(), formatDamageValue(profile.normal), "#D8DEE9", "#FFFFFF"));
            rendered++;
        }
        sb.append(statRow(t(player, "ui.reforge.normal_total"), formatDamageValue(averageProfile.normal), "#F8D36A", "#F8D36A"));
        sb.append(statRow(t(player, "ui.reforge.crit_total"), formatDamageValue(averageProfile.crit), "#FF8888", "#FFAAAA"));
        sb.append(statRow(t(player, "ui.reforge.expected_average"), formatDamageValue(averageProfile.expected), "#F8D36A", "#F8D36A"));
        return sb.toString();
    }

    private static String buildExpectedDamageRows(Player player, Entry equipment) {
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
            appendArmorAffinityRows(player, sb, item);
            sb.append(statRow(t(player, "ui.reforge.preview_upgrade_line", format3(statMultiplierForLevel(nextLevel, true))),
                    "+" + formatPercent(statMultiplierForLevel(nextLevel, true) - statMultiplierForLevel(level, true)), "#83D8FF", "#83D8FF"));
            return sb.toString();
        }

        List<EquipmentDamageTooltipMath.DamageBreakdown> breakdowns =
                EquipmentDamageTooltipMath.getDamageBreakdownFromInteractionVars(item.getItemId());
        WeaponDamageProfile currentAverage = weaponProfile(player, item, level, 0.0);
        WeaponDamageProfile nextAverage = weaponProfile(player, item, nextLevel, 0.0);
        sb.append(statRow(t(player, "ui.reforge.damage_multiplier"), "x" + format3(nextAverage.multiplier), "#83D8FF", "#83D8FF"));
        sb.append(statRow(t(player, "ui.reforge.essence_resonance_damage"), bonusLine(currentAverage.damageFlat, currentAverage.damagePercent), "#77DD77", "#77DD77"));
        appendWeaponElementalDamageRows(player, sb, item, nextAverage.normal);
        sb.append(statRow(t(player, "ui.reforge.crit"), formatPercent(currentAverage.critChance / 100.0)
                + " / +" + formatPercent(currentAverage.critDamage / 100.0), "#FF8888", "#FFAAAA"));
        sb.append(statRow("Multi-hit Chance", formatPercent(currentAverage.multiHitChancePercent / 100.0), "#F8D36A", "#F8D36A"));
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
            WeaponDamageProfile next = weaponProfile(player, item, nextLevel, breakdown.getBaseValue());
            sb.append(statRow(breakdown.getLabel(), formatDamageValue(next.normal), "#D8DEE9", "#FFFFFF"));
            rendered++;
        }
        sb.append(statRow(t(player, "ui.reforge.normal_total"), formatDamageValue(nextAverage.normal), "#F8D36A", "#F8D36A"));
        sb.append(statRow(t(player, "ui.reforge.crit_total"), formatDamageValue(nextAverage.crit), "#FF8888", "#FFAAAA"));
        sb.append(statRow(t(player, "ui.reforge.expected_average"), formatDamageValue(nextAverage.expected), "#F8D36A", "#F8D36A"));
        return sb.toString();
    }

    private static String statRow(String label, String value) {
        return (label == null ? "" : label) + "\t" + (value == null ? "" : value) + "\n";
    }

    private static String statRow(String label, String value, String ignoredLabelColor, String ignoredValueColor) {
        // The native RefinementStatRow.ui document owns colors and layout.
        return statRow(label, value);
    }

    private static String buildDamageRows(Player player, Entry equipment, boolean expected) {
        return expected ? buildExpectedDamageRows(player, equipment)
                : buildCurrentDamageRows(player, equipment);
    }







    private static WeaponDamageProfile weaponProfile(Player player, ItemStack item, int level, double baseOverride) {
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
        double totalAttackSpeedPercent = attackSpeedBonus[1]
                + CharacterPathService.bonusForWeapon(player, item.getItemId(), Bonus.ATTACK_SPEED_PERCENT) * 100.0d;
        double multiHitChancePercent = AttackSpeedMultiHitMath.multiHitChancePercent(totalAttackSpeedPercent);
        double critChance = Math.max(0.0, Math.min(100.0, critChanceBonus[1]));
        double critDamage = Math.max(0.0, Math.min(200.0, critDamageBonus[1]));
        double multiplier = refineMultiplier
                * softcoreMultiplier
                * (1.0 + (damagePercent / 100.0))
                * partsMultiplier;
        double normal = Math.max(0.0, (base * multiplier) + damageFlat);
        double crit = normal * (1.0 + (critDamage / 100.0));
        double expected = normal * (1.0 + ((critChance / 100.0) * (critDamage / 100.0)))
                * (1.0 + ((multiHitChancePercent / 100.0)
                * AttackSpeedMultiHitMath.EXTRA_HIT_DAMAGE_FRACTION));
        return new WeaponDamageProfile(base, damageFlat, damagePercent, critChance, critDamage,
                multiHitChancePercent, multiplier, normal, crit, expected);
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
        appendBonusRow(player, sb, "ui.reforge.evasion_bonus", armor.evasionFlat, armor.evasionPercent, "#83D8FF");
        appendBonusRow(player, sb, "ui.reforge.block_chance_bonus", armor.blockChanceFlat, armor.blockChancePercent, "#FFD166");
        appendBonusRow(player, sb, "ui.reforge.slow_bonus", armor.slowFlat, armor.slowPercent, "#AAAAFF");
    }

    private static void appendArmorAffinityRows(Player player, StringBuilder sb, ItemStack armor) {
        Map<Essence.Type, Double> resistances = ArmorAffinityResistanceUtils.calculateResistancePercentByIncomingType(armor);
        if (!ArmorAffinityResistanceUtils.hasAnyResistance(resistances)) {
            return;
        }
        for (Essence.Type type : Essence.Type.values()) {
            double percent = resistances.getOrDefault(type, 0.0d);
            if (percent <= 0.0001d) {
                continue;
            }
            sb.append(statRow(
                    t(player, "ui.reforge.affinity_resistance_element", localizeEssenceType(player, type)),
                    formatPercent(percent / 100.0d),
                    "#B388FF",
                    "#D8B4FE"));
        }
    }

    private static void appendWeaponElementalDamageRows(Player player, StringBuilder sb, ItemStack weapon, double damageOutput) {
        List<WeaponElementalDamageUtils.ElementDamage> elementDamages =
                WeaponElementalDamageUtils.calculateElementDamage(weapon, damageOutput);
        for (WeaponElementalDamageUtils.ElementDamage elementDamage : elementDamages) {
            sb.append(statRow(
                    t(player, "ui.reforge.elemental_damage_element", localizeEssenceType(player, elementDamage.type())),
                    formatDamageValue(elementDamage.damage()) + " (" + formatPercent(elementDamage.rate()) + ")",
                    essenceColor(elementDamage.type()),
                    "#D8B4FE"));
        }
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
        double[] evasion = SocketManager.getStoredStatBonus(item, EssenceEffect.StatType.EVASION);
        double[] blockChance = SocketManager.getStoredStatBonus(item, EssenceEffect.StatType.BLOCK_CHANCE);
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
                evasion[0],
                evasion[1],
                blockChance[0],
                blockChance[1],
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
                                double evasionFlat,
                                double evasionPercent,
                                double blockChanceFlat,
                                double blockChancePercent,
                                double slowFlat,
                                double slowPercent) {}
    private record WeaponDamageProfile(double base,
                                       double damageFlat,
                                       double damagePercent,
                                       double critChance,
                                       double critDamage,
                                       double multiHitChancePercent,
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









    private static Entry resolveSelection(List<Entry> entries, String value) {
        return UIInventoryUtils.resolveIndex(entries, value);
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

    private static String localizeEssenceType(Player player, Essence.Type type) {
        if (type == null) {
            return t(player, "tooltip.essence.unknown");
        }
        String key = switch (type) {
            case FIRE -> "essence.type.fire";
            case WATER -> "essence.type.water";
            case ICE -> "essence.type.ice";
            case LIGHTNING -> "essence.type.lightning";
            case LIFE -> "essence.type.life";
            case VOID -> "essence.type.void";
        };
        String translated = LangLoader.getUITranslation(player, key);
        if (translated == null || translated.isBlank() || translated.equals(key)) {
            String raw = type.name().toLowerCase(Locale.ROOT);
            return raw.isEmpty() ? type.name() : Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
        }
        return translated;
    }

    private static String essenceColor(Essence.Type type) {
        if (type == null) {
            return "#D8DEE9";
        }
        return switch (type) {
            case FIRE -> "#FFAA55";
            case WATER -> "#5599FF";
            case ICE -> "#55FFFF";
            case LIGHTNING -> "#FFFF55";
            case LIFE -> "#55FF55";
            case VOID -> "#B388FF";
        };
    }

    private static String t(Player player, String key, Object... params) {
        return LangLoader.getUITranslation(player, key, params);
    }

    private static String supportPreviewNote(Player player,
                                             Entry breakSupportEntry,
                                             RefinementConfig.SupportMaterial breakSupport,
                                             Entry breakBlockSupportEntry,
                                             RefinementConfig.SupportMaterial breakBlockSupport,
                                             Entry antiDegradationEntry,
                                             RefinementConfig.SupportMaterial antiDegradationSupport,
                                             SupportOutcomeEffects supportEffects,
                                             Bonuses smithyBonuses) {
        StringBuilder sb = new StringBuilder();
        if (breakSupport != null && breakSupportEntry != null) {
            sb.append(t(player, "ui.reforge.preview_hammer_active", breakSupportEntry.displayName,
                    durabilityPercent(breakSupport)));
        }
        if (breakBlockSupport != null && breakBlockSupportEntry != null) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(breakBlockSupportEntry.displayName)
                    .append(": blocks break only after break rolls (")
                    .append(formatPercent(1.0d - breakBlockSupport.breakMultiplier))
                    .append(").");
        }
        if (antiDegradationSupport != null && antiDegradationEntry != null) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(antiDegradationEntry.displayName)
                    .append(": ")
                    .append(formatPercent(antiDegradationSupport.antiDegradeChance))
                    .append(" ")
                    .append(t(player, "ui.reforge.anti_degradation_blocked"));
        }
        SupportOutcomeEffects effects = supportEffects == null ? SupportOutcomeEffects.NONE : supportEffects;
        if (effects.hasAny()) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append("Refinement modifiers:");
            if (effects.upgradeGuaranteeChance > 0.0d) {
                sb.append(" Upgrade guaranteed ").append(formatPercent(effects.upgradeGuaranteeChance)).append(".");
            }
            if (effects.jackpotGuaranteeChance > 0.0d) {
                sb.append(" Jackpot guaranteed ").append(formatPercent(effects.jackpotGuaranteeChance)).append(".");
            }
            if (effects.breakProtectionFraction > 0.0d) {
                sb.append(" Break chance reduced ").append(formatPercent(effects.breakProtectionFraction)).append(".");
            }
            if (Math.abs(effects.degradeWeightMultiplier - 1.0d) > 0.0001d) {
                sb.append(" Degrade weight x").append(format3(effects.degradeWeightMultiplier)).append(".");
            }
            if (Math.abs(effects.sameWeightMultiplier - 1.0d) > 0.0001d) {
                sb.append(" Same weight x").append(format3(effects.sameWeightMultiplier)).append(".");
            }
            if (Math.abs(effects.upgradeWeightMultiplier - 1.0d) > 0.0001d) {
                sb.append(" Upgrade weight x").append(format3(effects.upgradeWeightMultiplier)).append(".");
            }
            if (Math.abs(effects.jackpotWeightMultiplier - 1.0d) > 0.0001d) {
                sb.append(" Jackpot weight x").append(format3(effects.jackpotWeightMultiplier)).append(".");
            }
        }
        if (smithyBonuses != null) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(t(player, "ui.reforge.preview_smithy_chest_active",
                    SmithyLegacyUtils.localizedName(smithyBonuses.legacy(), LangLoader.getPlayerLanguage(player)),
                    SmithyLegacyUtils.compactBonusText(smithyBonuses)));
        }
        return sb.toString();
    }


}
