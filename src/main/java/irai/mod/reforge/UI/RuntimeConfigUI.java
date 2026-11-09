package irai.mod.reforge.UI;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.DynamicFloatingDamageFormatter.DamageNumberConfig;
import irai.mod.reforge.Common.LootInjectionUtils;
import irai.mod.reforge.Config.AscensionConfig;
import irai.mod.reforge.Config.CorruptionConfig;
import irai.mod.reforge.Config.CrossModConfig;
import irai.mod.reforge.Config.ElementalAffinityConfig;
import irai.mod.reforge.Config.LoreConfig;
import irai.mod.reforge.Config.LoreMappingConfig;
import irai.mod.reforge.Config.LootSocketRollConfig;
import irai.mod.reforge.Config.RefinementConfig;
import irai.mod.reforge.Config.RefinementConfig.MaterialTier;
import irai.mod.reforge.Config.SocketConfig;
import irai.mod.reforge.ReforgePlugin;
import irai.mod.reforge.Socket.ResonanceSystem;
import irai.mod.reforge.Util.LangLoader;

/**
 * OP-only live gameplay config editor backed by native Hytale UI.
 */
public final class RuntimeConfigUI {
    private RuntimeConfigUI() {}

    private static final String NATIVE_PAGE_UI = "Pages/RuntimeConfigEditor.ui";
    private static final String CATEGORY_CARD_UI = "Pages/RuntimeConfigCategoryCard.ui";
    private static final String GROUP_CARD_UI = "Pages/RuntimeConfigGroupCard.ui";
    private static final String NUMERIC_CARD_UI = "Pages/RuntimeConfigNumericCard.ui";
    private static final String TOGGLE_CARD_UI = "Pages/RuntimeConfigToggleCard.ui";
    private static final String TEXT_CARD_UI = "Pages/RuntimeConfigTextCard.ui";
    private static final String SELECT_CARD_UI = "Pages/RuntimeConfigSelectCard.ui";
    private static final String ARRAY_SECTION_UI = "Pages/RuntimeConfigArraySection.ui";
    private static final String ARRAY_ENTRY_UI = "Pages/RuntimeConfigArrayEntry.ui";
    private static final String STRUCTURED_ARRAY_HEADER_UI = "Pages/RuntimeConfigStructuredHeader.ui";
    private static final String STRUCTURED_ARRAY_ENTRY_UI = "Pages/RuntimeConfigStructuredEntry.ui";
    private static final String COLOR_SPIRIT_HEADER_UI = "Pages/RuntimeConfigColorSpiritHeader.ui";
    private static final String COLOR_SPIRIT_NPC_UI = "Pages/RuntimeConfigColorSpiritNpc.ui";
    private static final String ARRAY_RULE_ENTRY_UI = "Pages/RuntimeConfigRuleEntry.ui";
    private static final String ARRAY_LOOT_ENTRY_UI = "Pages/RuntimeConfigLootEntry.ui";
    private static final String ARRAY_SUPPORT_ENTRY_UI = "Pages/RuntimeConfigSupportEntry.ui";
    private static final String ARRAY_ABILITY_ENTRY_UI = "Pages/RuntimeConfigAbilityEntry.ui";
    private static final String WEIGHT_HEADER_UI = "Pages/RuntimeConfigWeightHeader.ui";
    private static final String WEIGHT_ROW_UI = "Pages/RuntimeConfigWeightRow.ui";
    private static final String NUMERIC_TABLE_HEADER_UI = "Pages/RuntimeConfigNumericTableHeader.ui";
    private static final String NUMERIC_TABLE_ROW_UI = "Pages/RuntimeConfigNumericTableRow.ui";
    private static final String EDITOR_SCHEMA_RESOURCE = "/Server/Config/RuntimeConfigEditor.json";
    private static final int MAX_STRUCTURED_ARRAY_FIELDS = 12;
    private static final String RESET_DEFAULTS_BUTTON = "resetDefaultsButton";
    private static final String REFINE_RECALCULATE_WEIGHTS_BUTTON = "recalculateWeightsButton";

    private static final String SOCKET_CONFIG_NAME = "SocketConfig";
    private static final String REFINEMENT_CONFIG_NAME = "RefinementConfig";
    private static final String CROSS_MOD_CONFIG_NAME = "CrossModConfig";
    private static final String LOOT_CONFIG_NAME = "LootSocketRollConfig";
    private static final String LORE_CONFIG_NAME = "LoreConfig";
    private static final String LORE_MAPPING_CONFIG_NAME = "LoreMappingConfig";
    private static final String DAMAGE_NUMBER_CONFIG_NAME = "DamageNumberConfig";
    private static final String ELEMENTAL_AFFINITY_CONFIG_NAME = "ElementalAffinityConfig";
    private static final String ASCENSION_CONFIG_NAME = "AscensionConfig";
    private static final String CORRUPTION_CONFIG_NAME = "CorruptionConfig";

    private static final String CATEGORY_SOCKET = "socket";
    private static final String CATEGORY_REFINEMENT = "refinement";
    private static final String CATEGORY_CROSS_MOD = "cross_mod";
    private static final String CATEGORY_LOOT = "loot";
    private static final String CATEGORY_LORE = "lore";
    private static final String CATEGORY_LORE_MAPPING = "lore_mapping";
    private static final String CATEGORY_DAMAGE_NUMBERS = "damage_numbers";
    private static final String CATEGORY_ELEMENTAL_AFFINITY = "elemental_affinity";
    private static final String CATEGORY_ASCENSION = "ascension";
    private static final String GROUP_LORE_MAPPING_GEMS = "lore_mapping_gems";
    private static final String GROUP_LORE_MAPPING_SPIRITS = "lore_mapping_spirits";
    private static final String GROUP_LORE_MAPPING_ABILITIES = "lore_mapping_abilities";
    private static final String GROUP_LORE_STATUS_RULES = "lore_status_rules";
    private static final String GROUP_LOOT_CHEST_INJECTIONS = "loot_chest_injections";
    private static final String GROUP_LOOT_NPC_INJECTIONS = "loot_npc_injections";
    private static final String GROUP_LOOT_NPC_AQUATIC_INJECTIONS = "loot_npc_aquatic_injections";
    private static final String GROUP_LOOT_NPC_FLYING_INJECTIONS = "loot_npc_flying_injections";
    private static final String GROUP_LOOT_NPC_VOID_INJECTIONS = "loot_npc_void_injections";
    private static final String GROUP_AFFINITY_ROLE_RULES = "affinity_role_rules";
    private static final String GROUP_AFFINITY_CUSTOM_RULES = "affinity_custom_role_rules";
    private static final String GROUP_AFFINITY_MULTIPLIERS = "affinity_multipliers";
    private static final String GROUP_AFFINITY_SHIELDS = "affinity_shields";
    private static final String GROUP_REFORGE_SUPPORT_MATERIALS = "reforge_support_materials";
    private static final String GROUP_RESONANCE_CLASS_MAPPINGS = "resonance_class_mappings";
    private static final String GROUP_RESONANCE_CLASS_HINTS = "resonance_class_hints";
    private static final String GROUP_CLOCKWORK_AMMO_HINTS = "clockwork_ammo_hints";
    private static final String[] AFFINITY_MODES = {"hint", "role"};
    private static final String[] AFFINITY_ELEMENTS = {"FIRE", "WATER", "ICE", "LIGHTNING", "LIFE", "VOID"};
    private static final String[] LORE_COLORS = {"black", "blue", "cyan", "green", "red", "white", "yellow"};
    private static final String[] LORE_TRIGGERS = {
            "ON_HIT", "ON_CRIT", "ON_KILL", "ON_DAMAGED", "ON_BLOCK", "ON_BLOCKED",
            "ON_NEAR_DEATH", "ON_FIRST_KILL", "ON_LORE_PROC", "ON_HEAL", "ON_POTION_USE",
            "ON_STATUS_APPLY", "ON_SPRINT", "ON_JUMP", "ON_SNEAK", "ON_SKILL_USE"
    };
    private static final String[] LORE_SIGNATURES = {
            "SIGNATURE_VORTEXSTRIKE", "SIGNATURE_GRAND_SLAM", "SIGNATURE_RAZORSTRIKE",
            "SIGNATURE_WHIRLWIND", "SIGNATURE_VOLLEY", "SIGNATURE_BIG_ARROW",
            "SIGNATURE_OMNISLASH", "SIGNATURE_PUMMEL",
            "SIGNATURE_BLOOD_RUSH", "SIGNATURE_CHARGE_ATTACK", "SIGNATURE_AREA_HEAL",
            "SIGNATURE_CAUSTIC_FINALE", "SIGNATURE_SHRAPNEL", "SIGNATURE_BURN_FINALE"
    };
    private static final String[] LORE_EFFECTS = {
            "DAMAGE_TARGET", "DAMAGE_ATTACKER", "HEAL_SELF", "HEAL_DEFENDER",
            "HEAL_SELF_OVER_TIME", "HEAL_AREA", "HEAL_AREA_OVER_TIME", "LIFESTEAL",
            "APPLY_BURN", "APPLY_FREEZE", "APPLY_SHOCK", "APPLY_BLEED", "APPLY_POISON",
            "APPLY_SLOW", "APPLY_WEAKNESS", "APPLY_BLIND", "APPLY_ROOT", "APPLY_STUN",
            "APPLY_FEAR", "APPLY_HASTE", "APPLY_INVISIBLE", "APPLY_SHIELD", "DOUBLE_CAST",
            "MULTI_HIT", "VORTEXSTRIKE", "CRIT_CHARGE", "BERSERK", "SUMMON_WOLF_PACK",
            "CHARGE_ATTACK", "OMNISLASH", "PUMMEL", "BLOOD_RUSH",
            "CAUSTIC_FINALE", "SHRAPNEL_FINALE", "BURN_FINALE", "DRAIN_LIFE"
    };
    private static final String[] REFORGE_SUPPORT_CONSUME_MODES = {"NEVER", "ON_USE", "ON_BLOCK"};
    private static final String[] DAMAGE_NUMBER_RENDER_MODES = {"COMBAT_TEXT", "HUD_POPUP", "PARTICLE_DIGITS", "OFF"};
    private static final String[] LORE_STATUS_KEYS = {
            "*", "bleed", "burn", "poison", "freeze", "shock", "slow", "weakness",
            "blind", "root", "stun", "fear", "drain"
    };
    private static final String[] LORE_STATUS_PATTERNS = {"LINEAR", "FIBONACCI"};

    private static final String DEFAULT_STATUS_KEY = "ui.runtime_config.status_default";
    private static final String DEFAULT_STATUS_FALLBACK =
            "Changes apply live and save immediately.\nUse Reload All From Disk to discard in-memory edits.";
    private static final Pattern NUMBER_PATTERN = Pattern.compile("[-+]?[0-9]*\\.?[0-9]+");

    private static final double[] DEFAULT_SOCKET_SUCCESS = {0.90, 0.75, 0.55, 0.35};
    private static final double[] DEFAULT_SOCKET_BREAK = {0.05, 0.10, 0.20, 0.35};
    private static final double[] DEFAULT_DAMAGE_MULTIPLIERS = {1.0, 1.10, 1.15, 1.25};
    private static final double[] DEFAULT_DEFENSE_MULTIPLIERS = {1.0, 1.08, 1.12, 1.20};
    private static final double[] DEFAULT_WEAPON_BREAK = {0.01, 0.05, 0.075};
    private static final double[] DEFAULT_ARMOR_BREAK = {0.01, 0.04, 0.065};
    private static final double[][] DEFAULT_WEIGHTS = {
            {0.00, 0.65, 0.34, 0.01},
            {0.35, 0.45, 0.19, 0.01},
            {0.60, 0.30, 0.095, 0.005}
    };

    private static ReforgePlugin plugin;

    private static final Map<PlayerRef, NativeRuntimeConfigPage> openPages = new ConcurrentHashMap<>();
    private static final Map<PlayerRef, ViewState> viewStates = new ConcurrentHashMap<>();
    private static final List<CategorySection> categories = new ArrayList<>();
    private static final List<ControlEntry> controls = new ArrayList<>();
    private static final Map<String, String> groupLayouts = new LinkedHashMap<>();
    private static final List<String> categoryOrder = new ArrayList<>();
    private static final Map<String, List<String>> groupOrder = new LinkedHashMap<>();

    private enum DisplayKind {
        INTEGER,
        PERCENT,
        MULTIPLIER,
        TOGGLE
    }

    private enum AbilityField {
        CHANCE,
        COOLDOWN,
        EFFECT,
        BASE,
        PER_LEVEL
    }

    private enum StatusRuleField {
        STATUS,
        STEP,
        PATTERN
    }

    private enum StatusResistanceField {
        NPC,
        STATUS,
        VALUE
    }

    private enum StatusCounterField {
        NPC,
        STATUS
    }

    @FunctionalInterface
    private interface ValueSupplier {
        double get();
    }

    @FunctionalInterface
    private interface AdjustHandler {
        void adjust(double delta);
    }

    @FunctionalInterface
    private interface ArraySupplier {
        double[] get();
    }

    private static final class ViewState {
        String activeCategoryId;
        String activeGroupId;
        String statusText;
        final Map<String, String> draftValues = new LinkedHashMap<>();
        final Map<String, List<String>> arrayDrafts = new LinkedHashMap<>();

        ViewState(String activeCategoryId, String activeGroupId, String statusText) {
            this.activeCategoryId = activeCategoryId;
            this.activeGroupId = activeGroupId;
            this.statusText = statusText;
        }
    }

    private interface ControlEntry {
        String id();
    }

    private static final class NumericControl implements ControlEntry {
        final String id;
        final String categoryId;
        final String configName;
        final String label;
        final String description;
        final DisplayKind displayKind;
        final double smallStep;
        final double largeStep;
        final ValueSupplier valueSupplier;
        final AdjustHandler adjustHandler;

        NumericControl(
                String id,
                String categoryId,
                String configName,
                String label,
                String description,
                DisplayKind displayKind,
                double smallStep,
                double largeStep,
                ValueSupplier valueSupplier,
                AdjustHandler adjustHandler) {
            this.id = id;
            this.categoryId = categoryId;
            this.configName = configName;
            this.label = label;
            this.description = description;
            this.displayKind = displayKind;
            this.smallStep = smallStep;
            this.largeStep = largeStep;
            this.valueSupplier = valueSupplier;
            this.adjustHandler = adjustHandler;
        }

        double currentValue() {
            return valueSupplier.get();
        }

        void adjust(double delta) {
            adjustHandler.adjust(delta);
        }

        String valueElementId() {
            return "value_" + id;
        }

        String inputElementId() {
            return "input_" + id;
        }

        String minusLargeButtonId() {
            return "btn_" + id + "_minusLarge";
        }

        String minusButtonId() {
            return "btn_" + id + "_minus";
        }

        String plusButtonId() {
            return "btn_" + id + "_plus";
        }

        String plusLargeButtonId() {
            return "btn_" + id + "_plusLarge";
        }

        String formatCurrentValue() {
            return RuntimeConfigUI.formatValue(displayKind, currentValue());
        }

        String formatInputValue() {
            return RuntimeConfigUI.formatInputValue(displayKind, currentValue());
        }

        String formatValue(double rawValue) {
            return RuntimeConfigUI.formatValue(displayKind, rawValue);
        }

        String formatSmallStepLabel(boolean positive) {
            return formatStepLabel(displayKind, smallStep, positive);
        }

        String formatLargeStepLabel(boolean positive) {
            return formatStepLabel(displayKind, largeStep, positive);
        }

        @Override
        public String id() {
            return id;
        }
    }

    @FunctionalInterface
    private interface TextSupplier {
        String get();
    }

    @FunctionalInterface
    private interface TextHandler {
        void set(String value);
    }

    private static final class TextControl implements ControlEntry {
        final String id;
        final String categoryId;
        final String configName;
        final String label;
        final String description;
        final TextSupplier valueSupplier;
        final TextHandler valueHandler;
        final boolean multiline;

        TextControl(
                String id,
                String categoryId,
                String configName,
                String label,
                String description,
                TextSupplier valueSupplier,
                TextHandler valueHandler) {
            this(id, categoryId, configName, label, description, valueSupplier, valueHandler, false);
        }

        TextControl(
                String id,
                String categoryId,
                String configName,
                String label,
                String description,
                TextSupplier valueSupplier,
                TextHandler valueHandler,
                boolean multiline) {
            this.id = id;
            this.categoryId = categoryId;
            this.configName = configName;
            this.label = label;
            this.description = description;
            this.valueSupplier = valueSupplier;
            this.valueHandler = valueHandler;
            this.multiline = multiline;
        }

        String currentValue() {
            String value = valueSupplier != null ? valueSupplier.get() : null;
            return value == null ? "" : value;
        }

        void applyValue(String value) {
            if (valueHandler != null) {
                valueHandler.set(value == null ? "" : value);
            }
        }

        String inputElementId() {
            return "input_" + id;
        }

        @Override
        public String id() {
            return id;
        }
    }

    private static final class SelectControl implements ControlEntry {
        final String id;
        final String categoryId;
        final String configName;
        final String label;
        final String description;
        final String[] options;
        final TextSupplier valueSupplier;
        final TextHandler valueHandler;

        SelectControl(
                String id,
                String categoryId,
                String configName,
                String label,
                String description,
                String[] options,
                TextSupplier valueSupplier,
                TextHandler valueHandler) {
            this.id = id;
            this.categoryId = categoryId;
            this.configName = configName;
            this.label = label;
            this.description = description;
            this.options = options == null ? new String[0] : options;
            this.valueSupplier = valueSupplier;
            this.valueHandler = valueHandler;
        }

        String currentValue() {
            String value = valueSupplier != null ? valueSupplier.get() : null;
            return value == null ? "" : value;
        }

        void applyValue(String value) {
            if (valueHandler != null) {
                valueHandler.set(value == null ? "" : value);
            }
        }

        String inputElementId() {
            return "input_" + id;
        }

        @Override
        public String id() {
            return id;
        }
    }

    private static final class ControlGroup {
        final String id;
        final String title;
        final String description;
        final List<ControlEntry> controls;

        ControlGroup(String id, String title, String description, List<ControlEntry> controls) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.controls = controls;
        }

        String buttonId(String categoryId) {
            return "group_" + categoryId + "_" + id;
        }
    }

    private static final class CategorySection {
        final String id;
        final String toggleButtonId;
        final String title;
        final String summary;
        final List<ControlGroup> groups;
        final String noteText;

        CategorySection(
                String id,
                String toggleButtonId,
                String title,
                String summary,
                List<ControlGroup> groups,
                String noteText) {
            this.id = id;
            this.toggleButtonId = toggleButtonId;
            this.title = title;
            this.summary = summary;
            this.groups = groups;
            this.noteText = noteText;
        }
    }

    private record ArraySection(String id, String title, String configName) {}

    public static void initialize(ReforgePlugin pluginInstance) {
        plugin = pluginInstance;
        registerControls();
        loadGroupLayouts();
        sortRegistryFromSchema();
    }

    private static void loadGroupLayouts() {
        groupLayouts.clear();
        categoryOrder.clear();
        groupOrder.clear();
        try (var input = RuntimeConfigUI.class.getResourceAsStream(EDITOR_SCHEMA_RESOURCE)) {
            if (input == null) return;
            JsonObject root = JsonParser.parseReader(new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var categoryArray = root.getAsJsonArray("categories");
            if (categoryArray == null) return;
            categoryArray.forEach(categoryElement -> {
                if (!categoryElement.isJsonObject()) return;
                JsonObject category = categoryElement.getAsJsonObject();
                String categoryId = category.has("id") ? category.get("id").getAsString() : "";
                if (!categoryId.isBlank()) categoryOrder.add(categoryId);
                var groups = category.getAsJsonArray("groups");
                if (groups == null) return;
                List<String> orderedGroups = new ArrayList<>();
                groups.forEach(groupElement -> {
                    if (!groupElement.isJsonObject()) return;
                    JsonObject group = groupElement.getAsJsonObject();
                    if (group.has("id")) orderedGroups.add(group.get("id").getAsString());
                    if (group.has("id") && group.has("layout")) {
                        groupLayouts.put(group.get("id").getAsString(), group.get("layout").getAsString());
                    }
                });
                if (!categoryId.isBlank()) groupOrder.put(categoryId, orderedGroups);
            });
        } catch (Exception e) {
            System.err.println("[SocketReforge] Could not read runtime config editor schema: " + e.getMessage());
        }
    }

    private static String groupLayout(String groupId) {
        return groupLayouts.getOrDefault(groupId, "setting_cards");
    }

    private static void sortRegistryFromSchema() {
        categories.sort((left, right) -> compareSchemaOrder(categoryOrder, left.id, right.id));
        for (CategorySection category : categories) {
            List<String> order = groupOrder.get(category.id);
            if (order != null) category.groups.sort((left, right) -> compareSchemaOrder(order, left.id, right.id));
        }
    }

    private static int compareSchemaOrder(List<String> order, String left, String right) {
        int leftIndex = order == null ? -1 : order.indexOf(left);
        int rightIndex = order == null ? -1 : order.indexOf(right);
        if (leftIndex < 0) leftIndex = Integer.MAX_VALUE;
        if (rightIndex < 0) rightIndex = Integer.MAX_VALUE;
        return Integer.compare(leftIndex, rightIndex);
    }

    private static String layoutLabel(String layout) {
        if (layout == null || layout.isBlank()) return "";
        String[] parts = layout.replace('_', ' ').split("\\s+");
        StringBuilder label = new StringBuilder("LAYOUT  ");
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (label.length() > 8) label.append(' ');
            label.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
        }
        return label.toString();
    }

    private static String arrayEntryUi(String layout) {
        return switch (layout == null ? "" : layout) {
            case "rule_cards" -> ARRAY_RULE_ENTRY_UI;
            case "loot_rule_cards" -> ARRAY_LOOT_ENTRY_UI;
            case "support_rule_cards" -> ARRAY_SUPPORT_ENTRY_UI;
            case "ability_cards" -> ARRAY_ABILITY_ENTRY_UI;
            default -> ARRAY_ENTRY_UI;
        };
    }

    private static String arrayEntryKind(String layout) {
        return switch (layout == null ? "" : layout) {
            case "rule_cards" -> "ELEMENTAL RULE";
            case "loot_rule_cards" -> "LOOT RULE";
            case "support_rule_cards" -> "SUPPORT MATERIAL";
            case "ability_cards" -> "LORE ABILITY";
            default -> "MAPPING";
        };
    }

    private static NumericControl findNumericControl(String id) {
        if (id == null) return null;
        for (ControlEntry entry : controls) {
            if (entry instanceof NumericControl control
                    && (control.id.equals(id) || control.inputElementId().equals(id))) return control;
        }
        return null;
    }

    private static int appendRefinementWeightMatrix(UICommandBuilder commands, UIEventBuilder events,
                                                     ControlGroup group, ViewState state, int cardIndex) {
        Map<Integer, Map<String, NumericControl>> levels = new java.util.TreeMap<>();
        for (ControlEntry entry : group.controls) {
            if (!(entry instanceof NumericControl control) || !isWeightControl(control)) continue;
            String[] parts = control.id.split("_");
            if (parts.length < 4) continue;
            int level = parseIntSafe(parts[2], -1);
            String outcome = parts[3];
            if (level >= 0) levels.computeIfAbsent(level, ignored -> new LinkedHashMap<>()).put(outcome, control);
        }
        if (levels.isEmpty()) return cardIndex;
        commands.append("#RuntimeConfigCards", WEIGHT_HEADER_UI);
        cardIndex++;
        for (Map.Entry<Integer, Map<String, NumericControl>> level : levels.entrySet()) {
            commands.append("#RuntimeConfigCards", WEIGHT_ROW_UI);
            String base = "#RuntimeConfigCards[" + cardIndex++ + "]";
            commands.set(base + " #WeightLevel.Text", "Level " + level.getKey());
            bindWeightField(commands, events, state, base, "degrade", "#WeightDegrade", level.getValue().get("degrade"));
            bindWeightField(commands, events, state, base, "same", "#WeightSame", level.getValue().get("same"));
            bindWeightField(commands, events, state, base, "upgrade", "#WeightUpgrade", level.getValue().get("upgrade"));
            bindWeightField(commands, events, state, base, "jackpot", "#WeightJackpot", level.getValue().get("jackpot"));
        }
        return cardIndex;
    }

    private static int appendNumericControlTable(UICommandBuilder commands, UIEventBuilder events,
                                                  Player player, ControlGroup group, ViewState state,
                                                  int cardIndex) {
        List<NumericControl> numericControls = group.controls.stream()
                .filter(NumericControl.class::isInstance)
                .map(NumericControl.class::cast)
                .filter(control -> control.displayKind != DisplayKind.TOGGLE)
                .toList();
        if (numericControls.isEmpty()) return cardIndex;

        commands.append("#RuntimeConfigCards", NUMERIC_TABLE_HEADER_UI);
        cardIndex++;
        for (NumericControl control : numericControls) {
            commands.append("#RuntimeConfigCards", NUMERIC_TABLE_ROW_UI);
            String base = "#RuntimeConfigCards[" + cardIndex++ + "]";
            String selector = base + " #TableValue";
            commands.set(base + " #TableSetting.Text", localizedControlLabel(player, control));
            commands.set(base + " #TableDescription.Text", localizedControlDescription(player, control));
            commands.set(base + " #TableUnit.Text", control.displayKind == DisplayKind.PERCENT
                    ? "%" : control.displayKind == DisplayKind.MULTIPLIER ? "×" : "");
            String raw = draftValue(state, control.inputElementId(), control.formatInputValue());
            double fallback = control.displayKind == DisplayKind.PERCENT
                    ? control.currentValue() * 100.0 : control.currentValue();
            commands.set(selector + ".Value", parseDoubleSafe(raw, fallback));
            events.addEventBinding(CustomUIEventBindingType.FocusLost, selector,
                    EventData.of("Action", "number").append("Target", control.inputElementId())
                            .append("@Number", selector + ".Value"), false);
        }
        return cardIndex;
    }

    private static void bindWeightField(UICommandBuilder commands, UIEventBuilder events, ViewState state,
                                        String base, String outcome, String suffix, NumericControl control) {
        if (control == null) return;
        String selector = base + " " + suffix;
        String input = draftValue(state, control.inputElementId(), control.formatInputValue());
        commands.set(selector + ".Value", parseDoubleSafe(input, control.currentValue() * 100.0));
        events.addEventBinding(CustomUIEventBindingType.FocusLost, selector,
                EventData.of("Action", "number").append("Target", control.inputElementId())
                        .append("@Number", selector + ".Value"), false);
    }

    public static boolean isAvailable() {
        return plugin != null;
    }

    public static void open(Player player) {
        if (player == null) {
            return;
        }
        if (!isAvailable()) {
            player.getPlayerRef().sendMessage(Message.raw("<color=#FF5555>Runtime config editor is unavailable."));
            return;
        }
        PlayerRef playerRef = player.getPlayerRef();
        viewStates.computeIfAbsent(playerRef, RuntimeConfigUI::createDefaultViewState);
        player.getWorld().execute(() -> openWithSync(player));
    }

    private static void openWithSync(Player player) {
        if (player == null) {
            return;
        }
        PlayerRef playerRef = player.getPlayerRef();
        ViewState state = viewStates.computeIfAbsent(playerRef, RuntimeConfigUI::createDefaultViewState);
        if (CATEGORY_LORE_MAPPING.equals(state.activeCategoryId)) {
            state.activeCategoryId = CATEGORY_LORE;
        }
        registerControls();
        sortRegistryFromSchema();
        openPage(player, state);
    }

    private static void openPage(Player player, ViewState state) {
        PlayerRef playerRef = player.getPlayerRef();
        Ref<EntityStore> entityRef = playerRef.getReference();
        Store<EntityStore> store = entityRef == null ? null : entityRef.getStore();
        if (entityRef == null || store == null) return;
        NativeRuntimeConfigPage page = new NativeRuntimeConfigPage(playerRef, state);
        openPages.put(playerRef, page);
        player.getPageManager().openCustomPage(entityRef, store, page);
    }

    private static final class NativeRuntimeConfigPage extends InteractiveCustomUIPage<NativeRuntimeConfigPage.PageEvent> {
        private final PlayerRef owner;
        private final ViewState state;
        private boolean dismissed;

        private NativeRuntimeConfigPage(PlayerRef owner, ViewState state) {
            super(owner, CustomPageLifetime.CanDismiss, PageEvent.CODEC);
            this.owner = owner;
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
                case "category" -> {
                    selectCategory(state, event.target);
                    refresh(player);
                }
                case "group" -> {
                    state.activeGroupId = event.target;
                    refresh(player);
                }
                case "number" -> {
                    if (event.numberValue != null && event.target != null)
                        state.draftValues.put(event.target, Double.toString(event.numberValue));
                }
                case "text" -> {
                    if (event.target != null) state.draftValues.put(event.target, event.textValue == null ? "" : event.textValue);
                }
                case "toggle" -> {
                    NumericControl control = findNumericControl(event.target);
                    if (control != null) handleToggle(player, state, control);
                }
                case "array_value" -> setArrayDraft(state, event.target, event.textValue);
                case "array_structured_value" -> setStructuredArrayDraft(state, event.target, event.textValue);
                case "color_spirit_value" -> setColorSpiritDraft(state, event.target, event.textValue, false);
                case "color_spirit_npc_value" -> setColorSpiritDraft(state, event.target, event.textValue, true);
                case "color_spirit_add_npc" -> {
                    addColorSpiritNpcDraft(state, event.target);
                    refresh(player);
                }
                case "color_spirit_remove_npc" -> {
                    removeColorSpiritNpcDraft(state, event.target);
                    refresh(player);
                }
                case "array_add" -> {
                    mutateArray(state, event.target, -1, null);
                    refresh(player);
                }
                case "array_remove" -> {
                    int separator = event.target == null ? -1 : event.target.lastIndexOf(':');
                    if (separator > 0) mutateArray(state, event.target.substring(0, separator), parseIntSafe(event.target.substring(separator + 1), -1), null);
                    refresh(player);
                }
                case "apply" -> {
                    CategorySection category = resolveActiveCategory(state);
                    ControlGroup group = resolveActiveGroup(category, state);
                    handleApplyVisibleInputs(player, state, group);
                }
                case "reload" -> handleReload(player, state);
                case "reset" -> handleResetDefaults(player, state);
                case "recalculate_weights" -> handleRecalculateRefinementWeights(player, state);
                default -> { }
            }
        }

        @Override
        public void onDismiss(Ref<EntityStore> playerRef, Store<EntityStore> store) {
            dismissed = true;
            openPages.remove(owner, this);
            super.onDismiss(playerRef, store);
        }

        private void refresh(Player player) {
            if (dismissed) return;
            UICommandBuilder commands = new UICommandBuilder();
            UIEventBuilder events = new UIEventBuilder();
            populate(player, commands, events, true);
            sendUpdate(commands, events, false);
        }

        private void populate(Player player, UICommandBuilder commands, UIEventBuilder events, boolean replace) {
            registerControls();
            sortRegistryFromSchema();
            CategorySection category = resolveActiveCategory(state);
            ControlGroup group = resolveActiveGroup(category, state);
            if (replace) {
                commands.clear("#RuntimeConfigCategories");
                commands.clear("#ConfigGroupCards");
                commands.clear("#RuntimeConfigCards");
            }
            commands.set("#ConfigTitle.Text", tOrFallback(player, "ui.runtime_config.title", "Runtime Configuration"));
            commands.set("#ConfigStatus.Text", state.statusText == null ? "" : state.statusText);
            commands.set("#ConfigCategorySummary.Text", category == null ? "" : localizedCategorySummary(player, category));
            commands.set("#ConfigGroupTitle.Text", group == null ? "" : localizedGroupTitle(player, category, group));
            commands.set("#ConfigGroupDescription.Text", group == null ? "" : localizedGroupDescription(player, category, group));
            commands.set("#ConfigGroupLayout.Text", group == null ? "" : layoutLabel(groupLayout(group.id)));
            events.addEventBinding(CustomUIEventBindingType.Activating, "#ConfigReload", EventData.of("Action", "reload"));
            events.addEventBinding(CustomUIEventBindingType.Activating, "#ConfigReset", EventData.of("Action", "reset"));
            events.addEventBinding(CustomUIEventBindingType.Activating, "#ConfigApply", EventData.of("Action", "apply"));

            for (int i = 0; i < categories.size(); i++) {
                CategorySection entry = categories.get(i);
                commands.append("#RuntimeConfigCategories", CATEGORY_CARD_UI);
                String selector = "#RuntimeConfigCategories[" + i + "] #CategoryButton";
                commands.set(selector + ".Text", localizedCategoryTitle(player, entry));
                if (entry.id.equals(category == null ? "" : category.id))
                    commands.set(selector + ".Text", "• " + localizedCategoryTitle(player, entry));
                events.addEventBinding(CustomUIEventBindingType.Activating, selector,
                        EventData.of("Action", "category").append("Target", entry.id));
            }

            List<ControlGroup> visibleGroups = category == null ? List.of() : category.groups;
            for (int i = 0; i < visibleGroups.size(); i++) {
                ControlGroup entry = visibleGroups.get(i);
                commands.append("#ConfigGroupCards", GROUP_CARD_UI);
                String selector = "#ConfigGroupCards[" + i + "] #GroupButton";
                commands.set(selector + ".Text", localizedGroupTitle(player, category, entry));
                if (entry.id.equals(group == null ? "" : group.id))
                    commands.set(selector + ".Text", "• " + localizedGroupTitle(player, category, entry));
                events.addEventBinding(CustomUIEventBindingType.Activating, selector,
                        EventData.of("Action", "group").append("Target", entry.id));
            }
            if (category == null || group == null) return;

            int cardIndex = 0;
            String activeLayout = groupLayout(group.id);
            for (ControlEntry entry : group.controls) {
                if ("probability_matrix".equals(activeLayout)
                        && entry instanceof NumericControl numeric && isWeightControl(numeric)) continue;
                if ("numeric_table".equals(activeLayout)
                        && entry instanceof NumericControl numeric && numeric.displayKind != DisplayKind.TOGGLE) continue;
                if (entry instanceof NumericControl numeric && numeric.displayKind == DisplayKind.TOGGLE) {
                    commands.append("#RuntimeConfigCards", TOGGLE_CARD_UI);
                    String base = "#RuntimeConfigCards[" + cardIndex++ + "]";
                    commands.set(base + " #ToggleTitle.Text", localizedControlLabel(player, numeric));
                    commands.set(base + " #ToggleDescription.Text", localizedControlDescription(player, numeric));
                    commands.set(base + " #ToggleValue.Text", numeric.currentValue() > 0 ? "ENABLED" : "DISABLED");
                    events.addEventBinding(CustomUIEventBindingType.Activating, base + " #ToggleValue",
                            EventData.of("Action", "toggle").append("Target", numeric.id));
                } else if (entry instanceof NumericControl numeric) {
                    commands.append("#RuntimeConfigCards", NUMERIC_CARD_UI);
                    String base = "#RuntimeConfigCards[" + cardIndex++ + "]";
                    String input = base + " #NumericValue";
                    commands.set(base + " #NumericTitle.Text", localizedControlLabel(player, numeric));
                    commands.set(base + " #NumericDescription.Text", localizedControlDescription(player, numeric));
                    commands.set(base + " #NumericUnit.Text", numeric.displayKind == DisplayKind.PERCENT ? "%" : numeric.displayKind == DisplayKind.MULTIPLIER ? "×" : "");
                    String raw = draftValue(state, numeric.inputElementId(), numeric.formatInputValue());
                    commands.set(input + ".Value", parseDoubleSafe(raw, numeric.currentValue()));
                    EventData data = EventData.of("Action", "number").append("Target", numeric.inputElementId())
                            .append("@Number", input + ".Value");
                    events.addEventBinding(CustomUIEventBindingType.FocusLost, input, data, false);
                    if (isRefineMaxLevelControl(numeric)) {
                        String recalculate = base + " #RecalculateWeights";
                        commands.set(recalculate + ".Visible", true);
                        events.addEventBinding(CustomUIEventBindingType.Activating, recalculate,
                                EventData.of("Action", "recalculate_weights"));
                    }
                } else if (entry instanceof TextControl text) {
                    commands.append("#RuntimeConfigCards", TEXT_CARD_UI);
                    String base = "#RuntimeConfigCards[" + cardIndex++ + "]";
                    String input = base + " #TextValue";
                    commands.set(base + " #TextTitle.Text", localizedControlLabel(player, text));
                    commands.set(base + " #TextDescription.Text", localizedControlDescription(player, text));
                    String textValue = text.currentValue();
                    if (text.multiline) textValue = String.join(", ", splitListValue(textValue));
                    commands.set(input + ".Value", draftValue(state, text.inputElementId(), textValue));
                    events.addEventBinding(CustomUIEventBindingType.ValueChanged, input,
                            EventData.of("Action", "text").append("Target", text.inputElementId()).append("@Text", input + ".Value"), false);
                } else if (entry instanceof SelectControl select) {
                    commands.append("#RuntimeConfigCards", SELECT_CARD_UI);
                    String base = "#RuntimeConfigCards[" + cardIndex++ + "]";
                    String input = base + " #SelectValue";
                    commands.set(base + " #SelectTitle.Text", localizedControlLabel(player, select));
                    commands.set(base + " #SelectDescription.Text", localizedControlDescription(player, select));
                    List<DropdownEntryInfo> options = new ArrayList<>(select.options.length);
                    for (String option : select.options) options.add(new DropdownEntryInfo(LocalizableString.fromString(option), option));
                    commands.set(input + ".Entries", options);
                    String selected = draftValue(state, select.inputElementId(), select.currentValue());
                    commands.set(input + ".Value", selected);
                    events.addEventBinding(CustomUIEventBindingType.ValueChanged, input,
                            EventData.of("Action", "text").append("Target", select.inputElementId()).append("@Text", input + ".Value"), false);
                }
            }

            if ("probability_matrix".equals(activeLayout)) {
                cardIndex = appendRefinementWeightMatrix(commands, events, group, state, cardIndex);
            } else if ("numeric_table".equals(activeLayout)) {
                cardIndex = appendNumericControlTable(commands, events, player, group, state, cardIndex);
            }

            List<ArraySection> sections = arraySections(group.id);
            for (ArraySection section : sections) {
                List<String> values = state.arrayDrafts.computeIfAbsent(section.id(), ignored -> mutableList(readArray(section.id())));
                commands.append("#RuntimeConfigCards", ARRAY_SECTION_UI);
                String sectionBase = "#RuntimeConfigCards[" + cardIndex++ + "]";
                commands.set(sectionBase + " #ArraySectionTitle.Text", section.title());
                events.addEventBinding(CustomUIEventBindingType.Activating, sectionBase + " #ArrayAdd",
                        EventData.of("Action", "array_add").append("Target", section.id()));
                if ("lore_mapping.color_spirits".equals(section.id())) {
                    cardIndex = appendColorSpiritMappings(commands, events, values, cardIndex);
                    continue;
                }
                List<String> columnLabels = structuredArrayLabels(section.id(), values);
                if (columnLabels != null) {
                    commands.append("#RuntimeConfigCards", STRUCTURED_ARRAY_HEADER_UI);
                    String headerBase = "#RuntimeConfigCards[" + cardIndex++ + "]";
                    for (int field = 0; field < MAX_STRUCTURED_ARRAY_FIELDS; field++) {
                        boolean visible = field < columnLabels.size();
                        commands.set(headerBase + " #StructuredHeaderField" + field + ".Visible", visible);
                        if (visible) {
                            commands.set(headerBase + " #StructuredHeaderField" + field + " #StructuredHeaderLabel" + field + ".Text",
                                    columnLabels.get(field));
                        }
                    }
                }
                for (int itemIndex = 0; itemIndex < values.size(); itemIndex++) {
                    String target = section.id() + ":" + itemIndex;
                    if (columnLabels != null) {
                        commands.append("#RuntimeConfigCards", STRUCTURED_ARRAY_ENTRY_UI);
                        String itemBase = "#RuntimeConfigCards[" + cardIndex++ + "]";
                        List<String> fields = structuredArrayFields(section.id(), values.get(itemIndex), columnLabels.size());
                        for (int field = 0; field < MAX_STRUCTURED_ARRAY_FIELDS; field++) {
                            boolean visible = field < columnLabels.size();
                            commands.set(itemBase + " #StructuredField" + field + ".Visible", visible);
                            if (!visible) continue;
                            String fieldTarget = section.id() + ":" + itemIndex + ":" + field + ":" + columnLabels.size();
                            if (field == 0 && isAffinityStructuredModeSection(section.id())) {
                                String textInput = itemBase + " #StructuredField0 #StructuredField0Value";
                                String modeInput = itemBase + " #StructuredField0 #StructuredField0Mode";
                                commands.set(textInput + ".Visible", false);
                                commands.set(modeInput + ".Visible", true);
                                commands.set(modeInput + ".Value", fields.get(field));
                                events.addEventBinding(CustomUIEventBindingType.ValueChanged, modeInput,
                                        EventData.of("Action", "array_structured_value").append("Target", fieldTarget)
                                                .append("@Text", modeInput + ".Value"), false);
                            } else {
                                String input = itemBase + " #StructuredField" + field + " #StructuredField" + field + "Value";
                                commands.set(input + ".Value", fields.get(field));
                                if (field == 0) {
                                    commands.set(itemBase + " #StructuredField0 #StructuredField0Mode.Visible", false);
                                }
                                events.addEventBinding(CustomUIEventBindingType.ValueChanged, input,
                                        EventData.of("Action", "array_structured_value").append("Target", fieldTarget)
                                                .append("@Text", input + ".Value"), false);
                            }
                        }
                        events.addEventBinding(CustomUIEventBindingType.Activating, itemBase + " #StructuredRemove",
                                EventData.of("Action", "array_remove").append("Target", target));
                    } else {
                        String entryLayout = groupLayout(group.id);
                        commands.append("#RuntimeConfigCards", arrayEntryUi(entryLayout));
                        String itemBase = "#RuntimeConfigCards[" + cardIndex++ + "]";
                        String valueSelector = itemBase + " #ArrayValue";
                        commands.set(itemBase + " #ArrayKind.Text", arrayEntryKind(entryLayout));
                        String value = draftValue(state, arrayDraftKey(section.id(), itemIndex), values.get(itemIndex));
                        commands.set(valueSelector + ".Value", value);
                        events.addEventBinding(CustomUIEventBindingType.ValueChanged, valueSelector,
                                EventData.of("Action", "array_value").append("Target", target).append("@Text", valueSelector + ".Value"), false);
                        events.addEventBinding(CustomUIEventBindingType.Activating, itemBase + " #ArrayRemove",
                                EventData.of("Action", "array_remove").append("Target", target));
                    }
                }
            }
        }

        public static final class PageEvent {
            public static final BuilderCodec<PageEvent> CODEC = BuilderCodec.<PageEvent>builder(PageEvent.class, PageEvent::new)
                    .append(new KeyedCodec<>("Action", Codec.STRING), (event, value) -> event.action = value, event -> event.action).add()
                    .append(new KeyedCodec<>("Target", Codec.STRING), (event, value) -> event.target = value, event -> event.target).add()
                    .append(new KeyedCodec<>("@Text", Codec.STRING), (event, value) -> event.textValue = value, event -> event.textValue).add()
                    .append(new KeyedCodec<>("@Number", Codec.DOUBLE), (event, value) -> event.numberValue = value, event -> event.numberValue).add()
                    .build();
            public String action;
            public String target;
            public String textValue;
            public Double numberValue;
            public PageEvent() { }
        }
    }

    private static void handleReload(Player player, ViewState state) {
        try {
            plugin.getConfigService().reloadAll();
            state.draftValues.clear();
            state.arrayDrafts.clear();
            state.statusText = t(player, "ui.runtime_config.status_reload_success");
        } catch (Exception e) {
            state.statusText = t(player, "ui.runtime_config.status_reload_failed", sanitizeError(e));
        }
        requestReopen(player, state);
    }

    private static void handleResetDefaults(Player player, ViewState state) {
        CategorySection activeCategory = resolveActiveCategory(state);
        if (activeCategory == null) {
            state.statusText = t(player, "ui.runtime_config.status_reset_no_category");
            requestReopen(player, state);
            return;
        }
        try {
            resetCategoryToDefaults(activeCategory.id);
            state.draftValues.clear();
            state.arrayDrafts.clear();
            state.statusText = t(player, "ui.runtime_config.status_reset_success", localizedCategoryTitle(player, activeCategory));
        } catch (Exception e) {
            state.statusText = t(player, "ui.runtime_config.status_reset_failed", sanitizeError(e));
        }
        requestReopen(player, state);
    }

    private static void resetCategoryToDefaults(String categoryId) {
        if (CATEGORY_SOCKET.equals(categoryId)) {
            socketConfig().resetToDefaults();
            plugin.getConfigService().saveAndApply(SOCKET_CONFIG_NAME);
            return;
        }
        if (CATEGORY_REFINEMENT.equals(categoryId)) {
            refinementConfig().resetToDefaults();
            plugin.getConfigService().saveAndApply(REFINEMENT_CONFIG_NAME);
            return;
        }
        if (CATEGORY_CROSS_MOD.equals(categoryId)) {
            crossModConfig().resetToDefaults();
            plugin.getConfigService().saveAndApply(CROSS_MOD_CONFIG_NAME);
            return;
        }
        if (CATEGORY_LOOT.equals(categoryId)) {
            lootConfig().resetToDefaults();
            plugin.getConfigService().saveAndApply(LOOT_CONFIG_NAME);
            return;
        }
        if (CATEGORY_LORE.equals(categoryId)) {
            loreConfig().resetToDefaults();
            plugin.getConfigService().saveAndApply(LORE_CONFIG_NAME);
            loreMappingConfig().resetToDefaults();
            plugin.getConfigService().saveAndApply(LORE_MAPPING_CONFIG_NAME);
            return;
        }
        if (CATEGORY_LORE_MAPPING.equals(categoryId)) {
            loreMappingConfig().resetToDefaults();
            plugin.getConfigService().saveAndApply(LORE_MAPPING_CONFIG_NAME);
            return;
        }
        if (CATEGORY_DAMAGE_NUMBERS.equals(categoryId)) {
            damageNumberConfig().resetToDefaults();
            plugin.getConfigService().saveAndApply(DAMAGE_NUMBER_CONFIG_NAME);
            return;
        }
        if (CATEGORY_ELEMENTAL_AFFINITY.equals(categoryId)) {
            elementalAffinityConfig().resetToDefaults();
            plugin.getConfigService().saveAndApply(ELEMENTAL_AFFINITY_CONFIG_NAME);
        }
        if (CATEGORY_ASCENSION.equals(categoryId)) {
            ascensionConfig().resetToDefaults();
            corruptionConfig().resetToDefaults();
            plugin.getConfigService().saveAndApply(ASCENSION_CONFIG_NAME);
            plugin.getConfigService().saveAndApply(CORRUPTION_CONFIG_NAME);
        }
    }


    private static void handleToggle(Player player, ViewState state, NumericControl control) {
        double before = control.currentValue();
        try {
            control.adjust(before > 0.0 ? -control.smallStep : control.smallStep);
            plugin.getConfigService().saveAndApply(control.configName);
            double after = control.currentValue();
            if (roughlyEqual(before, after)) {
                state.statusText = t(player, "ui.runtime_config.status_control_unchanged", localizedControlLabel(player, control), control.formatValue(after));
            } else {
                state.statusText = t(player, "ui.runtime_config.status_control_changed", localizedControlLabel(player, control), control.formatValue(before), control.formatValue(after));
            }
        } catch (Exception e) {
            state.statusText = t(player, "ui.runtime_config.status_control_failed", localizedControlLabel(player, control), sanitizeError(e));
        }
        requestReopen(player, state);
    }

    private static void handleApplyVisibleInputs(
            Player player,
            ViewState state,
            ControlGroup activeGroup) {
        List<String> configsToSave = new ArrayList<>();
        int changed = 0;
        boolean refinementMaxChanged = false;
        try {
            List<ControlEntry> activeEntries = activeGroup == null ? List.of() : activeGroup.controls;
            for (ControlEntry entry : activeEntries) {
                if (entry instanceof NumericControl control) {
                    if (control.displayKind == DisplayKind.TOGGLE) {
                        continue;
                    }
                    if (refinementMaxChanged && isWeightControl(control)) {
                        state.draftValues.remove(control.inputElementId());
                        continue;
                    }
                    String rawValue = draftValue(state, control.inputElementId(), control.formatInputValue());
                    Double parsed = parseInputValue(control.displayKind, rawValue);
                    if (parsed == null) {
                        continue;
                    }
                    double before = control.currentValue();
                    control.adjust(computeDelta(control.displayKind, before, parsed));
                    if (!roughlyEqual(before, control.currentValue())) {
                        changed++;
                        addUniqueConfig(configsToSave, control.configName);
                        if (isRefineMaxLevelControl(control)) {
                            refinementMaxChanged = true;
                            removeDraftValuesStartingWith(state, "input_refine_weight_");
                        }
                    }
                    state.draftValues.remove(control.inputElementId());
                } else if (entry instanceof TextControl control) {
                    String rawValue = draftValue(state, control.inputElementId(), control.currentValue());
                    String before = control.currentValue();
                    control.applyValue(rawValue == null ? "" : rawValue);
                    if (!before.equals(control.currentValue())) {
                        changed++;
                        addUniqueConfig(configsToSave, control.configName);
                    }
                    state.draftValues.remove(control.inputElementId());
                } else if (entry instanceof SelectControl control) {
                    String rawValue = draftValue(state, control.inputElementId(), control.currentValue());
                    String before = control.currentValue();
                    control.applyValue(rawValue == null ? "" : rawValue);
                    if (!before.equalsIgnoreCase(control.currentValue())) {
                        changed++;
                        addUniqueConfig(configsToSave, control.configName);
                    }
                    state.draftValues.remove(control.inputElementId());
                }
            }
            if (activeGroup == null || arraySections(activeGroup.id).isEmpty()) {
                changed += applyVisibleMappingInputs(state, activeGroup, configsToSave);
            }
            changed += applyNativeArrayInputs(state, activeGroup, configsToSave);
            saveConfigs(configsToSave);
            state.statusText = changed <= 0
                    ? tOrFallback(player, "ui.runtime_config.status_apply_no_changes", "No changes to apply.")
                    : tOrFallback(player, "ui.runtime_config.status_apply_success", "Applied " + changed + " change(s).", changed);
        } catch (Exception e) {
            String error = sanitizeError(e);
            state.statusText = tOrFallback(player, "ui.runtime_config.status_apply_failed", "Apply failed: " + error, error);
        }
        requestReopen(player, state);
    }

    private static void handleRecalculateRefinementWeights(Player player, ViewState state) {
        String maxLevelInputId = "input_refine_max_level";
        String rawValue = draftValue(state, maxLevelInputId, String.valueOf(refinementConfig().getMaxLevel()));
        Double parsed = parseInputValue(DisplayKind.INTEGER, rawValue);
        if (parsed == null) {
            state.statusText = t(player, "ui.runtime_config.status_control_invalid",
                    tOrFallback(player, "ui.runtime_config.control.refinement.refine_max_level.label", "Max refine level"),
                    rawValue == null ? "" : rawValue.trim());
            requestReopen(player, state);
            return;
        }
        int nextMax = clampInt((int) Math.round(parsed), 1, 100);
        try {
            RefinementConfig cfg = refinementConfig();
            cfg.setMaxLevel(nextMax);
            cfg.resetMaterialTiersToDefault();
            cfg.applyDefaultWeights();
            plugin.getConfigService().saveAndApply(REFINEMENT_CONFIG_NAME);
            state.draftValues.remove(maxLevelInputId);
            removeDraftValuesStartingWith(state, "input_refine_weight_");
            state.statusText = tOrFallback(player,
                    "ui.runtime_config.status_recalculate_weights_success",
                    "Recalculated outcome weights for max refine level " + nextMax + ".",
                    nextMax);
        } catch (Exception e) {
            state.statusText = tOrFallback(player,
                    "ui.runtime_config.status_recalculate_weights_failed",
                    "Recalculate weights failed: " + sanitizeError(e),
                    sanitizeError(e));
        }
        requestReopen(player, state);
    }

    private static int applyVisibleMappingInputs(ViewState state, ControlGroup activeGroup, List<String> configsToSave) {
        if (activeGroup == null) {
            return 0;
        }
        if (GROUP_LORE_MAPPING_GEMS.equals(activeGroup.id)) {
            return applyGemMappingInputs(state, configsToSave);
        }
        if (GROUP_LORE_MAPPING_SPIRITS.equals(activeGroup.id)) {
            return applySpiritMappingInputs(state, configsToSave);
        }
        if (GROUP_LORE_MAPPING_ABILITIES.equals(activeGroup.id)) {
            return applyAbilityMappingInputs(state, configsToSave);
        }
        if (GROUP_LORE_STATUS_RULES.equals(activeGroup.id)) {
            return applyLoreStatusInputs(state, configsToSave);
        }
        if (isLootInjectionGroup(activeGroup.id)) {
            return applyLootInjectionInputs(state, activeGroup.id, configsToSave);
        }
        if (isAffinityMappingGroup(activeGroup.id)) {
            return applyAffinityMappingInputs(state, activeGroup.id, configsToSave);
        }
        if (GROUP_REFORGE_SUPPORT_MATERIALS.equals(activeGroup.id)) {
            return applyReforgeSupportMaterialInputs(state, configsToSave);
        }
        if (GROUP_RESONANCE_CLASS_MAPPINGS.equals(activeGroup.id)) {
            return applyResonanceMappingInputs(state, configsToSave);
        }
        if (GROUP_RESONANCE_CLASS_HINTS.equals(activeGroup.id)) {
            return applyResonanceHintInputs(state, configsToSave);
        }
        if (GROUP_CLOCKWORK_AMMO_HINTS.equals(activeGroup.id)) {
            return applyClockworkAmmoHintInputs(state, configsToSave);
        }
        return 0;
    }

    private static int applyResonanceMappingInputs(ViewState state, List<String> configsToSave) {
        String[] before = socketConfig().getResonanceClassMappings();
        String[] display = resonanceMappingDisplayEntries();
        List<String> after = new ArrayList<>();
        for (int i = 0; i < display.length; i++) {
            ResonanceMappingEntry entry = parseResonanceMappingEntry(display[i]);
            String name = draftValue(state, resonanceMappingNameInputId(i), entry.name()).trim();
            String classes = normalizeResonanceClassText(
                    draftValue(state, resonanceMappingClassesInputId(i), entry.classes()));
            if (!name.isBlank() && !classes.isBlank()) {
                after.add(formatResonanceMappingEntry(new ResonanceMappingEntry(name, classes)));
            }
            state.draftValues.remove(resonanceMappingNameInputId(i));
            state.draftValues.remove(resonanceMappingClassesInputId(i));
        }
        if (java.util.Arrays.asList(before).equals(after)) {
            return 0;
        }
        socketConfig().setResonanceClassMappings(after.toArray(String[]::new));
        addUniqueConfig(configsToSave, SOCKET_CONFIG_NAME);
        return 1;
    }

    private static int applyResonanceHintInputs(ViewState state, List<String> configsToSave) {
        String[] before = socketConfig().getResonanceWeaponClassHints();
        List<String> after = new ArrayList<>();
        for (int i = 0; i < before.length; i++) {
            ResonanceHintEntry entry = parseResonanceHintEntry(before[i]);
            String clazz = normalizeResonanceHintClassText(
                    draftValue(state, resonanceHintClassInputId(i), entry.weaponClass()));
            String tokens = normalizeHintTokenText(
                    draftValue(state, resonanceHintTokensInputId(i), entry.hints()));
            if (!clazz.isBlank() && !tokens.isBlank()) {
                after.add(clazz + "=" + tokens);
            }
            state.draftValues.remove(resonanceHintClassInputId(i));
            state.draftValues.remove(resonanceHintTokensInputId(i));
        }
        if (java.util.Arrays.asList(before).equals(after)) {
            return 0;
        }
        socketConfig().setResonanceWeaponClassHints(after.toArray(String[]::new));
        addUniqueConfig(configsToSave, SOCKET_CONFIG_NAME);
        return 1;
    }

    private static int applyClockworkAmmoHintInputs(ViewState state, List<String> configsToSave) {
        String[] before = socketConfig().getClockworkAmmoItemHints();
        List<String> after = new ArrayList<>();
        for (int i = 0; i < before.length; i++) {
            String token = normalizeSingleHintToken(
                    draftValue(state, clockworkAmmoHintInputId(i), before[i]));
            if (!token.isBlank() && !after.contains(token)) {
                after.add(token);
            }
            state.draftValues.remove(clockworkAmmoHintInputId(i));
        }
        if (java.util.Arrays.asList(before).equals(after)) {
            return 0;
        }
        socketConfig().setClockworkAmmoItemHints(after.toArray(String[]::new));
        addUniqueConfig(configsToSave, SOCKET_CONFIG_NAME);
        return 1;
    }

    private static int applyAffinityMappingInputs(ViewState state, String groupId, List<String> configsToSave) {
        if (isAffinityRuleGroup(groupId)) {
            String[] before = affinityRuleEntries(groupId);
            List<String> after = new ArrayList<>();
            for (int i = 0; i < before.length; i++) {
                AffinityRuleEntry entry = parseAffinityRuleEntry(before[i]);
                String mode = normalizeAffinityMode(draftValue(state, affinityRuleModeSelectId(groupId, i), entry.mode()));
                String target = draftValue(state, affinityRuleTargetInputId(groupId, i), entry.target()).trim();
                String element = normalizeAffinityElement(draftValue(state, affinityRuleElementSelectId(groupId, i), entry.element()));
                if (!target.isBlank()) {
                    after.add(formatAffinityRuleEntry(new AffinityRuleEntry(mode, target, element)));
                }
                clearAffinityRuleDrafts(state, groupId, i);
            }
            if (java.util.Arrays.asList(before).equals(after)) {
                return 0;
            }
            setAffinityRuleEntries(groupId, after.toArray(String[]::new));
            addUniqueConfig(configsToSave, ELEMENTAL_AFFINITY_CONFIG_NAME);
            return 1;
        }

        if (GROUP_AFFINITY_SHIELDS.equals(groupId)) {
            return applyAffinityShieldInputs(state, configsToSave);
        }

        String[] before = affinityMultiplierEntries(groupId);
        List<String> after = new ArrayList<>();
        for (int i = 0; i < before.length; i++) {
            AffinityMultiplierEntry entry = parseAffinityMultiplierEntry(before[i]);
            String mode = normalizeAffinityMode(draftValue(state, affinityMultiplierModeSelectId(groupId, i), entry.mode()));
            String target = draftValue(state, affinityMultiplierTargetInputId(groupId, i), entry.target()).trim();
            Map<String, String> multipliers = new LinkedHashMap<>();
            for (String element : AFFINITY_ELEMENTS) {
                String rawValue = draftValue(state, affinityMultiplierElementInputId(groupId, i, element), entry.multipliers().getOrDefault(element, "")).trim();
                if (!rawValue.isBlank()) {
                    multipliers.put(element, normalizeAffinityValueText(groupId, rawValue));
                }
            }
            if (!target.isBlank() && !multipliers.isEmpty()) {
                after.add(formatAffinityMultiplierEntry(groupId, new AffinityMultiplierEntry(mode, target, multipliers)));
            }
            clearAffinityMultiplierDrafts(state, groupId, i);
        }
        if (java.util.Arrays.asList(before).equals(after)) {
            return 0;
        }
        setAffinityMultiplierEntries(groupId, after.toArray(String[]::new));
        addUniqueConfig(configsToSave, ELEMENTAL_AFFINITY_CONFIG_NAME);
        return 1;
    }

    private static int applyAffinityShieldInputs(ViewState state, List<String> configsToSave) {
        String[] before = elementalAffinityConfig().getElementShields();
        List<String> after = new ArrayList<>();
        for (int i = 0; i < before.length; i++) {
            AffinityShieldEntry entry = parseAffinityShieldEntry(before[i]);
            String mode = normalizeAffinityMode(draftValue(state, affinityShieldModeSelectId(i), entry.mode()));
            String target = draftValue(state, affinityShieldTargetInputId(i), entry.target()).trim();
            String shield = normalizeAffinityShieldText(
                    draftValue(state, affinityShieldValueInputId(i), entry.shield()),
                    entry.shield());
            String delay = normalizeAffinityShieldNumberText(
                    draftValue(state, affinityShieldDelayInputId(i), entry.delay()),
                    entry.delay());
            String rate = normalizeAffinityShieldNumberText(
                    draftValue(state, affinityShieldRateInputId(i), entry.rate()),
                    entry.rate());
            String duration = normalizeAffinityShieldNumberText(
                    draftValue(state, affinityShieldDurationInputId(i), entry.duration()),
                    entry.duration());
            if (!target.isBlank()) {
                after.add(formatAffinityShieldEntry(new AffinityShieldEntry(mode, target, shield, delay, rate, duration)));
            }
            clearAffinityShieldDrafts(state, i);
        }
        if (java.util.Arrays.asList(before).equals(after)) {
            return 0;
        }
        elementalAffinityConfig().setElementShields(after.toArray(String[]::new));
        addUniqueConfig(configsToSave, ELEMENTAL_AFFINITY_CONFIG_NAME);
        return 1;
    }

    private static int applyLootInjectionInputs(ViewState state, String groupId, List<String> configsToSave) {
        String[] before = lootInjectionEntries(groupId);
        List<String> after = new ArrayList<>();
        for (int i = 0; i < before.length; i++) {
            LootRuleEntry entry = parseLootRuleEntry(before[i]);
            String itemId = draftValue(state, lootInjectionItemInputId(groupId, i), entry.itemId()).trim();
            String targetId = isNpcLootInjectionGroup(groupId)
                    ? draftValue(state, lootInjectionTargetInputId(groupId, i), entry.targetId()).trim()
                    : "";
            String chance = normalizeLootChanceText(draftValue(state, lootInjectionChanceInputId(groupId, i), entry.chance()), entry.chance());
            String min = normalizeIntegerText(draftValue(state, lootInjectionMinInputId(groupId, i), entry.min()), entry.min());
            String max = normalizeIntegerText(draftValue(state, lootInjectionMaxInputId(groupId, i), entry.max()), entry.max());
            if (!itemId.isBlank()) {
                int minQty = Math.max(0, parseIntSafe(min, 1));
                int maxQty = Math.max(minQty, parseIntSafe(max, minQty));
                after.add(LootInjectionUtils.formatEntry(itemId, parseDoubleSafe(chance, 0.0d), minQty, maxQty, targetId));
            }
            if (state != null) {
                state.draftValues.remove(lootInjectionItemInputId(groupId, i));
                state.draftValues.remove(lootInjectionTargetInputId(groupId, i));
                state.draftValues.remove(lootInjectionChanceInputId(groupId, i));
                state.draftValues.remove(lootInjectionMinInputId(groupId, i));
                state.draftValues.remove(lootInjectionMaxInputId(groupId, i));
            }
        }
        if (java.util.Arrays.asList(before).equals(after)) {
            return 0;
        }
        setLootInjectionEntries(groupId, after.toArray(String[]::new));
        addUniqueConfig(configsToSave, LOOT_CONFIG_NAME);
        return 1;
    }

    private static int applyReforgeSupportMaterialInputs(ViewState state, List<String> configsToSave) {
        String[] before = refinementConfig().getSupportMaterialEntries();
        List<String> after = new ArrayList<>();
        for (int i = 0; i < before.length; i++) {
            ReforgeSupportRuleEntry entry = readReforgeSupportEntryFromInputs(state, i, parseReforgeSupportEntry(before[i]));
            if (!entry.itemId().isBlank()) {
                after.add(formatReforgeSupportEntry(entry));
            }
            clearReforgeSupportDrafts(state, i);
        }
        if (java.util.Arrays.asList(before).equals(after)) {
            return 0;
        }
        refinementConfig().setSupportMaterialEntries(after.toArray(String[]::new));
        addUniqueConfig(configsToSave, REFINEMENT_CONFIG_NAME);
        return 1;
    }

    private static int applySpiritMappingInputs(ViewState state, List<String> configsToSave) {
        String[] before = loreMappingConfig().getColorSpiritEntries();
        List<String> entries = mutableList(before);
        for (String color : LORE_COLORS) {
            List<String> spirits = spiritsForColor(color);
            List<String> updated = new ArrayList<>();
            for (int i = 0; i < spirits.size(); i++) {
                String elementId = spiritMappingSelectId(color, i);
                String spirit = draftValue(state, elementId, spirits.get(i)).trim();
                if (!spirit.isBlank() && !updated.contains(spirit)) {
                    updated.add(spirit);
                }
                if (state != null) {
                    state.draftValues.remove(elementId);
                }
            }
            putSpiritsForColor(entries, color, updated);
        }
        loreMappingConfig().setColorSpiritEntries(entries.toArray(String[]::new));
        if (!java.util.Arrays.equals(before, loreMappingConfig().getColorSpiritEntries())) {
            addUniqueConfig(configsToSave, LORE_MAPPING_CONFIG_NAME);
            return 1;
        }
        return 0;
    }

    private static int applyGemMappingInputs(ViewState state, List<String> configsToSave) {
        List<String> before = mutableList(loreMappingConfig().getGemColorEntries());
        List<String> after = new ArrayList<>();
        for (String color : LORE_COLORS) {
            for (int i = 0; i < before.size(); i++) {
                KeyValue entry = parseKeyValue(before.get(i));
                String normalizedColor = normalizeLoreColor(entry.value());
                if (!color.equals(normalizedColor)) {
                    continue;
                }
                String elementId = gemMappingTokenInputId(i);
                String token = draftValue(state, elementId, entry.key()).trim();
                if (!token.isBlank()) {
                    after.add(token + "=" + color);
                }
                if (state != null) {
                    state.draftValues.remove(elementId);
                }
            }
        }
        if (before.equals(after)) {
            return 0;
        }
        loreMappingConfig().setGemColorEntries(after.toArray(String[]::new));
        addUniqueConfig(configsToSave, LORE_MAPPING_CONFIG_NAME);
        return 1;
    }

    private static int applyAbilityMappingInputs(ViewState state, List<String> configsToSave) {
        List<String> before = mutableList(loreMappingConfig().getAbilityEntries());
        List<String> after = new ArrayList<>();
        for (int i = 0; i < before.size(); i++) {
            AbilityEntry ability = parseAbilityEntry(before.get(i));
            String spirit = draftValue(state, abilitySpiritSelectId(i), ability.spirit()).trim();
            String trigger = normalizeAbilityToken(draftValue(state, abilityTriggerSelectId(i), ability.trigger()), ability.trigger());
            if (spirit.isBlank()) {
                continue;
            }
            AbilityEntry updated;
            if (isSignatureAbility(trigger)) {
                updated = ability.withSpirit(spirit).asSignature(trigger);
            } else {
                updated = ability.withSpirit(spirit).asNormal(trigger)
                        .withChance(normalizeDecimalText(draftValue(state, abilityChanceInputId(i), ability.chance()), ability.chance(), false))
                        .withCooldown(normalizeIntegerText(draftValue(state, abilityCooldownInputId(i), ability.cooldown()), ability.cooldown()))
                        .withEffect(normalizeAbilityToken(draftValue(state, abilityEffectSelectId(i), ability.effect()), ability.effect()))
                        .withBase(normalizeDecimalText(draftValue(state, abilityBaseInputId(i), ability.base()), ability.base(), false))
                        .withPerLevel(normalizeDecimalText(draftValue(state, abilityPerLevelInputId(i), ability.perLevel()), ability.perLevel(), false));
            }
            after.add(formatAbilityEntry(updated));
            if (state != null) {
                state.draftValues.remove(abilitySpiritSelectId(i));
                state.draftValues.remove(abilityTriggerSelectId(i));
                state.draftValues.remove(abilityChanceInputId(i));
                state.draftValues.remove(abilityCooldownInputId(i));
                state.draftValues.remove(abilityEffectSelectId(i));
                state.draftValues.remove(abilityBaseInputId(i));
                state.draftValues.remove(abilityPerLevelInputId(i));
            }
        }
        if (before.equals(after)) {
            return 0;
        }
        loreMappingConfig().setAbilityEntries(after.toArray(String[]::new));
        addUniqueConfig(configsToSave, LORE_MAPPING_CONFIG_NAME);
        return 1;
    }

    private static int applyLoreStatusInputs(ViewState state, List<String> configsToSave) {
        int changed = 0;
        List<StatusRuleEntry> rulesBefore = parseStatusRuleEntries(loreConfig().getStatusBossReapplyRules());
        List<StatusRuleEntry> rulesAfter = new ArrayList<>();
        for (int i = 0; i < rulesBefore.size(); i++) {
            StatusRuleEntry entry = rulesBefore.get(i);
            rulesAfter.add(new StatusRuleEntry(
                    normalizeStatusKeyForUi(draftValue(state, statusRuleStatusSelectId(i), entry.status())),
                    normalizeIntegerText(draftValue(state, statusRuleStepInputId(i), entry.step()), entry.step()),
                    normalizeStatusPattern(draftValue(state, statusRulePatternSelectId(i), entry.pattern()))));
            if (state != null) {
                state.draftValues.remove(statusRuleStatusSelectId(i));
                state.draftValues.remove(statusRuleStepInputId(i));
                state.draftValues.remove(statusRulePatternSelectId(i));
            }
        }
        if (!rulesBefore.equals(rulesAfter)) {
            loreConfig().setStatusBossReapplyRules(formatStatusRuleEntries(rulesAfter));
            addUniqueConfig(configsToSave, LORE_CONFIG_NAME);
            changed++;
        }

        List<StatusResistanceEntry> resistBefore = parseStatusResistanceEntries(loreConfig().getNpcStatusResistanceEntries());
        List<StatusResistanceEntry> resistAfter = new ArrayList<>();
        for (int i = 0; i < resistBefore.size(); i++) {
            StatusResistanceEntry entry = resistBefore.get(i);
            resistAfter.add(new StatusResistanceEntry(
                    normalizeNpcIdForUi(draftValue(state, statusResistanceNpcInputId(i), entry.npcId())),
                    normalizeStatusKeyForUi(draftValue(state, statusResistanceStatusSelectId(i), entry.status())),
                    normalizeDecimalText(draftValue(state, statusResistanceValueInputId(i), entry.value()), entry.value(), true)));
            if (state != null) {
                state.draftValues.remove(statusResistanceNpcInputId(i));
                state.draftValues.remove(statusResistanceStatusSelectId(i));
                state.draftValues.remove(statusResistanceValueInputId(i));
            }
        }
        if (!resistBefore.equals(resistAfter)) {
            loreConfig().setNpcStatusResistanceEntries(formatStatusResistanceEntries(resistAfter));
            addUniqueConfig(configsToSave, LORE_CONFIG_NAME);
            changed++;
        }

        List<StatusCounterEntry> countersBefore = parseStatusCounterEntries(loreConfig().getStatusBossCounterNpcIds());
        List<StatusCounterEntry> countersAfter = new ArrayList<>();
        for (int i = 0; i < countersBefore.size(); i++) {
            StatusCounterEntry entry = countersBefore.get(i);
            countersAfter.add(new StatusCounterEntry(
                    normalizeNpcIdForUi(draftValue(state, statusCounterNpcInputId(i), entry.npcId())),
                    normalizeStatusKeyForUi(draftValue(state, statusCounterStatusSelectId(i), entry.status()))));
            if (state != null) {
                state.draftValues.remove(statusCounterNpcInputId(i));
                state.draftValues.remove(statusCounterStatusSelectId(i));
            }
        }
        if (!countersBefore.equals(countersAfter)) {
            loreConfig().setStatusBossCounterNpcIds(formatStatusCounterEntries(countersAfter));
            addUniqueConfig(configsToSave, LORE_CONFIG_NAME);
            changed++;
        }

        List<String> bleedBefore = mutableList(loreConfig().getBleedBossCounterNpcIds());
        List<String> bleedAfter = new ArrayList<>();
        for (int i = 0; i < bleedBefore.size(); i++) {
            String npcId = normalizeNpcIdForUi(draftValue(state, bleedCounterNpcInputId(i), bleedBefore.get(i)));
            if (!npcId.isBlank()) {
                bleedAfter.add(npcId);
            }
            if (state != null) {
                state.draftValues.remove(bleedCounterNpcInputId(i));
            }
        }
        if (!bleedBefore.equals(bleedAfter)) {
            loreConfig().setBleedBossCounterNpcIds(bleedAfter.toArray(String[]::new));
            addUniqueConfig(configsToSave, LORE_CONFIG_NAME);
            changed++;
        }
        return changed;
    }

    private static void requestReopen(Player player, ViewState state) {
        if (player == null) {
            return;
        }
        PlayerRef playerRef = player.getPlayerRef();
        viewStates.put(playerRef, state);
        player.getWorld().execute(() -> {
            NativeRuntimeConfigPage nativePage = openPages.get(playerRef);
            if (nativePage != null) {
                nativePage.refresh(player);
            } else {
                openWithSync(player);
            }
        });
    }

    private static boolean isWeightControl(NumericControl control) {
        return control != null && control.id != null && control.id.startsWith("refine_weight_");
    }

    private static boolean isRefineMaxLevelControl(NumericControl control) {
        return control != null && "refine_max_level".equals(control.id);
    }

    private static ViewState createDefaultViewState(PlayerRef playerRef) {
        CategorySection defaultCategory = categories.isEmpty() ? null : categories.get(0);
        String statusText = tOrFallback(playerRef, DEFAULT_STATUS_KEY, DEFAULT_STATUS_FALLBACK);
        return new ViewState(
                defaultCategory == null ? CATEGORY_SOCKET : defaultCategory.id,
                firstGroupId(defaultCategory),
                statusText);
    }

    private static CategorySection resolveActiveCategory(ViewState state) {
        if (state != null) {
            for (CategorySection category : categories) {
                if (category.id.equals(state.activeCategoryId)) {
                    return category;
                }
            }
        }
        return categories.isEmpty() ? null : categories.get(0);
    }

    private static ControlGroup resolveActiveGroup(CategorySection category, ViewState state) {
        if (category == null || category.groups.isEmpty()) {
            return null;
        }
        if (state != null && state.activeGroupId != null) {
            for (ControlGroup group : category.groups) {
                if (group.id.equals(state.activeGroupId)) {
                    return group;
                }
            }
        }
        return category.groups.get(0);
    }

    private static void selectCategory(ViewState state, String categoryId) {
        if (state == null) {
            return;
        }
        state.activeCategoryId = categoryId;
        state.activeGroupId = firstGroupId(findCategory(categoryId));
    }

    private static CategorySection findCategory(String categoryId) {
        for (CategorySection category : categories) {
            if (category.id.equals(categoryId)) {
                return category;
            }
        }
        return null;
    }

    private static String firstGroupId(CategorySection category) {
        if (category == null || category.groups.isEmpty()) {
            return null;
        }
        return category.groups.get(0).id;
    }

    private static List<ControlEntry> visibleControls(CategorySection category, ControlGroup group, ViewState state) {
        if (category == null || group == null) {
            return List.of();
        }
        return group.controls;
    }

    private static void registerControls() {
        categories.clear();
        controls.clear();
        addCategory(buildSocketCategory());
        addCategory(buildRefinementCategory());
        addCategory(buildCrossModCategory());
        addCategory(buildLootCategory());
        addCategory(buildLoreCategory());
        addCategory(buildElementalAffinityCategory());
        addCategory(buildDamageNumberCategory());
        addCategory(buildEndgameCategory());
    }

    private static void addCategory(CategorySection category) {
        categories.add(category);
        for (ControlGroup group : category.groups) {
            controls.addAll(group.controls);
        }
    }

    private static CategorySection buildSocketCategory() {
        List<ControlGroup> groups = new ArrayList<>();

        List<ControlEntry> limits = new ArrayList<>();
        limits.add(intControl(
                "socket_weapon_max",
                CATEGORY_SOCKET,
                SOCKET_CONFIG_NAME,
                "Weapon max sockets",
                "Upper limit for weapon socket count.",
                () -> socketConfig().getMaxSocketsWeapon(),
                delta -> socketConfig().setMaxSocketsWeapon(clampInt(socketConfig().getMaxSocketsWeapon() + (int) Math.round(delta), 1, 8))));
        limits.add(intControl(
                "socket_armor_max",
                CATEGORY_SOCKET,
                SOCKET_CONFIG_NAME,
                "Armor max sockets",
                "Upper limit for armor socket count.",
                () -> socketConfig().getMaxSocketsArmor(),
                delta -> socketConfig().setMaxSocketsArmor(clampInt(socketConfig().getMaxSocketsArmor() + (int) Math.round(delta), 1, 8))));
        groups.add(new ControlGroup("limits", "Limits", "Hard caps used by runtime socket logic.", limits));

        List<ControlEntry> punchSuccess = new ArrayList<>();
        punchSuccess.add(chanceArrayControl("socket_success_0", CATEGORY_SOCKET, SOCKET_CONFIG_NAME, "Punch 1st socket success", "Base success chance when item has 0 sockets.", RuntimeConfigUI::ensureSocketSuccessArray, 0));
        punchSuccess.add(chanceArrayControl("socket_success_1", CATEGORY_SOCKET, SOCKET_CONFIG_NAME, "Punch 2nd socket success", "Base success chance when item has 1 socket.", RuntimeConfigUI::ensureSocketSuccessArray, 1));
        punchSuccess.add(chanceArrayControl("socket_success_2", CATEGORY_SOCKET, SOCKET_CONFIG_NAME, "Punch 3rd socket success", "Base success chance when item has 2 sockets.", RuntimeConfigUI::ensureSocketSuccessArray, 2));
        punchSuccess.add(chanceArrayControl("socket_success_3", CATEGORY_SOCKET, SOCKET_CONFIG_NAME, "Punch 4th socket success", "Base success chance when item has 3 sockets.", RuntimeConfigUI::ensureSocketSuccessArray, 3));
        groups.add(new ControlGroup("punch_success", "Punch Success", "Per-attempt success values by current socket count.", punchSuccess));

        List<ControlEntry> punchBreak = new ArrayList<>();
        punchBreak.add(chanceArrayControl("socket_break_0", CATEGORY_SOCKET, SOCKET_CONFIG_NAME, "Punch 1st socket break", "Break risk when item has 0 sockets.", RuntimeConfigUI::ensureSocketBreakArray, 0));
        punchBreak.add(chanceArrayControl("socket_break_1", CATEGORY_SOCKET, SOCKET_CONFIG_NAME, "Punch 2nd socket break", "Break risk when item has 1 socket.", RuntimeConfigUI::ensureSocketBreakArray, 1));
        punchBreak.add(chanceArrayControl("socket_break_2", CATEGORY_SOCKET, SOCKET_CONFIG_NAME, "Punch 3rd socket break", "Break risk when item has 2 sockets.", RuntimeConfigUI::ensureSocketBreakArray, 2));
        punchBreak.add(chanceArrayControl("socket_break_3", CATEGORY_SOCKET, SOCKET_CONFIG_NAME, "Punch 4th socket break", "Break risk when item has 3 sockets.", RuntimeConfigUI::ensureSocketBreakArray, 3));
        groups.add(new ControlGroup("punch_break", "Punch Break", "Per-attempt break risks by current socket count.", punchBreak));

        List<ControlEntry> specialRules = new ArrayList<>();
        specialRules.add(chanceControl(
                "socket_remove_success",
                CATEGORY_SOCKET,
                SOCKET_CONFIG_NAME,
                "Essence clear success",
                "Used by the hammer-based essence clearing flow.",
                () -> socketConfig().getEssenceRemovalSuccessChance(),
                delta -> socketConfig().setEssenceRemovalSuccessChance(clampChance(socketConfig().getEssenceRemovalSuccessChance() + delta))));
        specialRules.add(chanceControl(
                "socket_bonus_chance",
                CATEGORY_SOCKET,
                SOCKET_CONFIG_NAME,
                "Bonus 5th socket chance",
                "Extra chance to expand 4-socket items to 5 on success.",
                () -> socketConfig().getBonusSocketChance(),
                delta -> socketConfig().setBonusSocketChance(clampChance(socketConfig().getBonusSocketChance() + delta))));
        specialRules.add(chanceControl(
                "socket_reduce_max",
                CATEGORY_SOCKET,
                SOCKET_CONFIG_NAME,
                "Break reduces max sockets",
                "Chance that a break event also lowers max sockets.",
                () -> socketConfig().getMaxReduceChance(),
                delta -> socketConfig().setMaxReduceChance(clampChance(socketConfig().getMaxReduceChance() + delta))));
        groups.add(new ControlGroup("special_rules", "Special Rules", "Additional runtime modifiers used by socket and essence systems.", specialRules));
        groups.add(new ControlGroup(
                GROUP_RESONANCE_CLASS_MAPPINGS,
                "Resonance Class Mapping",
                "Override which weapon classes or armor can activate each resonance.",
                List.of()));
        groups.add(new ControlGroup(
                GROUP_RESONANCE_CLASS_HINTS,
                "Resonance Class Hints",
                "Map custom weapon families such as guns, glaives, and knuckles by item-id hint.",
                List.of()));
        groups.add(new ControlGroup(
                GROUP_CLOCKWORK_AMMO_HINTS,
                "Clockwork Ammo Hints",
                "Map custom ammo item-id hints for Clockwork Loader refunds and ammo socket exclusions.",
                List.of()));

        return new CategorySection(
                CATEGORY_SOCKET,
                "toggleSocketCategory",
                "Socket Punching",
                "Caps, punch odds, removal tuning, and extra socket rules.",
                groups,
                "Note: ESSENCE_REMOVAL_DESTROY exists in the JSON but current runtime logic does not read it.");
    }

    private static CategorySection buildRefinementCategory() {
        List<ControlGroup> groups = new ArrayList<>();
        RefinementConfig cfg = refinementConfig();
        int maxLevel = Math.max(1, cfg.getMaxLevel());

        List<ControlEntry> limits = new ArrayList<>();
        limits.add(intControlStep(
                "refine_max_level",
                CATEGORY_REFINEMENT,
                REFINEMENT_CONFIG_NAME,
                "Max refine level",
                "Upper refinement cap (affects array sizes + UI controls).",
                1,
                5,
                () -> refinementConfig().getMaxLevel(),
                delta -> {
                    int next = clampInt(refinementConfig().getMaxLevel() + (int) Math.round(delta), 1, 100);
                    refinementConfig().setMaxLevel(next);
                    refinementConfig().resetMaterialTiersToDefault();
                    refinementConfig().applyDefaultMultipliersAndWeights();
                }));
        for (int level = 0; level < maxLevel; level++) {
            limits.addAll(buildWeightControls(level));
        }
        groups.add(new ControlGroup("refine_limits", "Refine Limits", "Global refinement caps and tiers.", limits));

        List<ControlEntry> nameFormat = new ArrayList<>();
        nameFormat.add(toggleControl(
                "refine_use_prefix",
                CATEGORY_REFINEMENT,
                REFINEMENT_CONFIG_NAME,
                "Use prefix for refine level",
                "Enabled: level tag appears before item name. Disabled: level tag appears after item name.",
                () -> refinementConfig().isRefinementLevelUsePrefix(),
                value -> {
                    refinementConfig().setRefinementLevelUsePrefix(value);
                    refinementConfig().applyDefaultRefinementLevelLabels();
                }));
        nameFormat.add(textControl(
                "refine_level_prefix",
                CATEGORY_REFINEMENT,
                REFINEMENT_CONFIG_NAME,
                "Refine level prefix",
                "Fallback text inserted before the level number (e.g., \" +\", \" [R\").",
                () -> refinementConfig().getRefinementLevelPrefix(),
                value -> refinementConfig().setRefinementLevelPrefix(value)));
        nameFormat.add(textControl(
                "refine_level_suffix",
                CATEGORY_REFINEMENT,
                REFINEMENT_CONFIG_NAME,
                "Refine level suffix",
                "Fallback text inserted after the level number (e.g., \"]\").",
                () -> refinementConfig().getRefinementLevelSuffix(),
                value -> refinementConfig().setRefinementLevelSuffix(value)));
        groups.add(new ControlGroup("refine_name_format", "Refine Name Format", "Customize how refine levels are displayed in item names.", nameFormat));
        List<ControlEntry> weaponLabelControls = buildRefinementLabelControls(false);
        if (!weaponLabelControls.isEmpty()) {
            groups.add(new ControlGroup("refine_level_labels_weapon", "Weapon Level Labels", "Set per-level weapon labels. Leave empty to fall back to prefix/suffix + number.", weaponLabelControls));
        }
        List<ControlEntry> armorLabelControls = buildRefinementLabelControls(true);
        if (!armorLabelControls.isEmpty()) {
            groups.add(new ControlGroup("refine_level_labels_armor", "Armor Level Labels", "Set per-level armor labels. Leave empty to fall back to prefix/suffix + number.", armorLabelControls));
        }
        List<ControlEntry> tierControls = buildMaterialTierControls();
        if (!tierControls.isEmpty()) {
            groups.add(new ControlGroup("refine_material_tiers", "Refine Material Tiers", "Adjust level thresholds and material costs per tier.", tierControls));
        }

        groups.add(new ControlGroup(
                GROUP_REFORGE_SUPPORT_MATERIALS,
                "Reforge Support Materials",
                "Table format: Item ID, break multiplier, durability loss, anti-degrade chance, consume mode, and description.",
                List.of()));

        List<ControlEntry> weaponMultipliers = new ArrayList<>();
        for (int level = 0; level <= maxLevel; level++) {
            weaponMultipliers.add(multiplierArrayControl(
                    "refine_damage_" + level,
                    CATEGORY_REFINEMENT,
                    "Weapon +" + level + " multiplier",
                    "Damage multiplier at refine level " + level + ".",
                    RuntimeConfigUI::ensureDamageMultipliers,
                    REFINEMENT_CONFIG_NAME,
                    level));
        }
        groups.add(new ControlGroup("weapon_multipliers", "Weapon Multipliers", "Damage scaling by refinement tier.", weaponMultipliers));

        List<ControlEntry> armorMultipliers = new ArrayList<>();
        for (int level = 0; level <= maxLevel; level++) {
            armorMultipliers.add(multiplierArrayControl(
                    "refine_defense_" + level,
                    CATEGORY_REFINEMENT,
                    "Armor +" + level + " multiplier",
                    "Defense multiplier at refine level " + level + ".",
                    RuntimeConfigUI::ensureDefenseMultipliers,
                    REFINEMENT_CONFIG_NAME,
                    level));
        }
        groups.add(new ControlGroup("armor_multipliers", "Armor Multipliers", "Defense scaling by refinement tier.", armorMultipliers));

        List<ControlEntry> weaponBreak = new ArrayList<>();
        for (int level = 0; level < maxLevel; level++) {
            weaponBreak.add(chanceArrayControl(
                    "refine_break_weapon_" + level,
                    CATEGORY_REFINEMENT,
                    REFINEMENT_CONFIG_NAME,
                    "Weapon " + level + "->" + (level + 1) + " break",
                    "Equipment break chance while attempting +" + level + " to +" + (level + 1) + ". UI input is percent, so 0.1 means 0.1%. JSON stores fractions, so 0.001 means 0.1%.",
                    RuntimeConfigUI::ensureWeaponBreakArray,
                    level));
        }
        groups.add(new ControlGroup("weapon_break", "Weapon Break", "Break risks for weapon refinement transitions.", weaponBreak));

        List<ControlEntry> armorBreak = new ArrayList<>();
        for (int level = 0; level < maxLevel; level++) {
            armorBreak.add(chanceArrayControl(
                    "refine_break_armor_" + level,
                    CATEGORY_REFINEMENT,
                    REFINEMENT_CONFIG_NAME,
                    "Armor " + level + "->" + (level + 1) + " break",
                    "Equipment break chance while attempting +" + level + " to +" + (level + 1) + ". UI input is percent, so 0.1 means 0.1%. JSON stores fractions, so 0.001 means 0.1%.",
                    RuntimeConfigUI::ensureArmorBreakArray,
                    level));
        }
        groups.add(new ControlGroup("armor_break", "Armor Break", "Break risks for armor refinement transitions.", armorBreak));

        List<ControlEntry> softcoreControls = new ArrayList<>();
        softcoreControls.add(toggleControl(
                "refine_softcore_enabled",
                CATEGORY_REFINEMENT,
                REFINEMENT_CONFIG_NAME,
                "Softcore break protection",
                "When enabled, all refinement breaks preserve the item and apply permanent core stat loss instead of shattering it. Turning this on disables mixed mode.",
                () -> refinementConfig().isSoftcoreBreakEnabled(),
                value -> refinementConfig().setSoftcoreBreakEnabled(value)));
        softcoreControls.add(toggleControl(
                "refine_mixed_break_mode",
                CATEGORY_REFINEMENT,
                REFINEMENT_CONFIG_NAME,
                "Mixed break mode",
                "Non-resonant refinement materials always use softcore break protection. Resonant Glob break rolls use a separate softcore-save chance, with the remaining chance becoming a true shatter. Turning this on disables full softcore mode.",
                () -> refinementConfig().isMixedBreakModeEnabled(),
                value -> refinementConfig().setMixedBreakModeEnabled(value)));
        softcoreControls.add(chanceControl(
                "refine_softcore_loss_per_break",
                CATEGORY_REFINEMENT,
                REFINEMENT_CONFIG_NAME,
                "Softcore max loss per break",
                "Each saved break rolls a permanent core damage/defense loss between 1% and this value.",
                () -> refinementConfig().getSoftcoreStatLossPerBreak(),
                delta -> refinementConfig().setSoftcoreStatLossPerBreak(
                        clampChance(refinementConfig().getSoftcoreStatLossPerBreak() + delta))));
        softcoreControls.add(chanceControl(
                "refine_softcore_min_multiplier",
                CATEGORY_REFINEMENT,
                REFINEMENT_CONFIG_NAME,
                "Softcore minimum stat floor",
                "Lowest remaining core damage/defense multiplier softcore protection can reduce an item to.",
                () -> refinementConfig().getSoftcoreMinStatMultiplier(),
                delta -> refinementConfig().setSoftcoreMinStatMultiplier(
                        clampChance(refinementConfig().getSoftcoreMinStatMultiplier() + delta))));
        softcoreControls.add(chanceControl(
                "refine_mixed_resonant_softcore_chance",
                CATEGORY_REFINEMENT,
                REFINEMENT_CONFIG_NAME,
                "Mixed resonant softcore chance",
                "When mixed mode is enabled, this is the chance that a Resonant Glob break is converted into softcore stat wear instead of a true shatter.",
                () -> refinementConfig().getMixedResonantSoftcoreChance(),
                delta -> refinementConfig().setMixedResonantSoftcoreChance(
                        clampChance(refinementConfig().getMixedResonantSoftcoreChance() + delta))));
        groups.add(new ControlGroup("softcore_breaks", "Break Protection Modes", "Choose between full softcore protection or mixed protection. If both are off, classic shatter behavior is used.", softcoreControls));

        return new CategorySection(
                CATEGORY_REFINEMENT,
                "toggleRefinementCategory",
                "Refinement",
                "Weapon and armor scaling, break risks, and reforge outcome distributions.",
                groups,
                null);
    }

    private static CategorySection buildCrossModCategory() {
        List<ControlGroup> groups = new ArrayList<>();

        List<ControlEntry> loot = new ArrayList<>();
        loot.add(toggleControl(
                "crossmod_loot4everyone_chest_compat",
                CATEGORY_CROSS_MOD,
                CROSS_MOD_CONFIG_NAME,
                "Loot4Everyone chest compat",
                "Enables per-player chest detection and roll tracking for Loot4Everyone template chests.",
                () -> crossModConfig().isLoot4EveryoneChestCompatEnabled(),
                value -> crossModConfig().setLoot4EveryoneChestCompatEnabled(value)));
        groups.add(new ControlGroup("crossmod_loot", "Loot", "Loot and container compatibility hooks for supported mods.", loot));

        return new CategorySection(
                CATEGORY_CROSS_MOD,
                "toggleCrossModCategory",
                "Cross-Mod",
                "Optional integration switches for other mods without mixing them into core refinement or loot tuning.",
                groups,
                null);
    }

    private static CategorySection buildLootCategory() {
        List<ControlGroup> groups = new ArrayList<>();

        List<ControlEntry> chestRolls = new ArrayList<>();
        chestRolls.add(chanceControl("loot_chest_three", CATEGORY_LOOT, LOOT_CONFIG_NAME, "Chest 3-socket chance", "Chance for rolled chest loot to land at 3 sockets.", () -> lootConfig().getChestThreeSocketChance(), delta -> lootConfig().setChestThreeSocketChance(clampChance(lootConfig().getChestThreeSocketChance() + delta))));
        chestRolls.add(chanceControl("loot_chest_four", CATEGORY_LOOT, LOOT_CONFIG_NAME, "Chest 4-socket chance", "Chance for rolled chest loot to land at 4 sockets.", () -> lootConfig().getChestFourSocketChance(), delta -> lootConfig().setChestFourSocketChance(clampChance(lootConfig().getChestFourSocketChance() + delta))));
        chestRolls.add(chanceControl("loot_chest_five", CATEGORY_LOOT, LOOT_CONFIG_NAME, "Chest 5-socket chance", "Chance for rolled chest loot to land at 5 sockets.", () -> lootConfig().getChestFiveSocketChance(), delta -> lootConfig().setChestFiveSocketChance(clampChance(lootConfig().getChestFiveSocketChance() + delta))));
        chestRolls.add(chanceControl("loot_chest_three_to_four", CATEGORY_LOOT, LOOT_CONFIG_NAME, "Chest 3->4 conversion", "Upgrade chance when chest loot first rolls 3 sockets.", () -> lootConfig().getChestThreeToFourChance(), delta -> lootConfig().setChestThreeToFourChance(clampChance(lootConfig().getChestThreeToFourChance() + delta))));
        chestRolls.add(chanceControl("loot_chest_resonance", CATEGORY_LOOT, LOOT_CONFIG_NAME, "Chest resonance chance", "Chance for chest loot to roll a fully resonant item.", () -> lootConfig().getChestResonanceChance(), delta -> lootConfig().setChestResonanceChance(clampChance(lootConfig().getChestResonanceChance() + delta))));
        chestRolls.add(chanceControl("loot_chest_socketed_essence", CATEGORY_LOOT, LOOT_CONFIG_NAME, "Chest socketed essence chance", "Chance for chest equipment to spawn with filled essences.", () -> lootConfig().getChestSocketedEssenceChance(), delta -> lootConfig().setChestSocketedEssenceChance(clampChance(lootConfig().getChestSocketedEssenceChance() + delta))));
        groups.add(new ControlGroup("chest_loot", "Chest Loot", "Socket roll tuning for treasure chests.", chestRolls));
        groups.add(new ControlGroup(GROUP_LOOT_CHEST_INJECTIONS, "Injected Chest Loot", "Extra item rules injected into generated chest loot. Chance is a fraction: 0.0001 = 0.01% = ~1 in 10,000 rolls.", List.of()));

        List<ControlEntry> dropRolls = new ArrayList<>();
        dropRolls.add(chanceControl("loot_drop_three", CATEGORY_LOOT, LOOT_CONFIG_NAME, "Drop 3-socket chance", "Chance for NPC drops to land at 3 sockets.", () -> lootConfig().getDropThreeSocketChance(), delta -> lootConfig().setDropThreeSocketChance(clampChance(lootConfig().getDropThreeSocketChance() + delta))));
        dropRolls.add(chanceControl("loot_drop_four", CATEGORY_LOOT, LOOT_CONFIG_NAME, "Drop 4-socket chance", "Chance for NPC drops to land at 4 sockets.", () -> lootConfig().getDropFourSocketChance(), delta -> lootConfig().setDropFourSocketChance(clampChance(lootConfig().getDropFourSocketChance() + delta))));
        dropRolls.add(chanceControl("loot_drop_five", CATEGORY_LOOT, LOOT_CONFIG_NAME, "Drop 5-socket chance", "Chance for NPC drops to land at 5 sockets.", () -> lootConfig().getDropFiveSocketChance(), delta -> lootConfig().setDropFiveSocketChance(clampChance(lootConfig().getDropFiveSocketChance() + delta))));
        dropRolls.add(chanceControl("loot_drop_three_to_four", CATEGORY_LOOT, LOOT_CONFIG_NAME, "Drop 3->4 conversion", "Upgrade chance when drop loot first rolls 3 sockets.", () -> lootConfig().getDropThreeToFourChance(), delta -> lootConfig().setDropThreeToFourChance(clampChance(lootConfig().getDropThreeToFourChance() + delta))));
        dropRolls.add(chanceControl("loot_drop_resonance", CATEGORY_LOOT, LOOT_CONFIG_NAME, "Drop resonance chance", "Chance for NPC drops to roll a fully resonant item.", () -> lootConfig().getDropResonanceChance(), delta -> lootConfig().setDropResonanceChance(clampChance(lootConfig().getDropResonanceChance() + delta))));
        dropRolls.add(chanceControl("loot_drop_socketed_essence", CATEGORY_LOOT, LOOT_CONFIG_NAME, "Drop socketed essence chance", "Chance for NPC equipment to spawn with filled essences.", () -> lootConfig().getDropSocketedEssenceChance(), delta -> lootConfig().setDropSocketedEssenceChance(clampChance(lootConfig().getDropSocketedEssenceChance() + delta))));
        groups.add(new ControlGroup("npc_drops", "NPC Drops", "Socket roll tuning for NPC and world drops.", dropRolls));
        groups.add(new ControlGroup(GROUP_LOOT_NPC_INJECTIONS, "Injected NPC Drops", "Extra item rules injected into every NPC drop list. Chance is a fraction: 0.0001 = 0.01% = ~1 in 10,000 rolls.", List.of()));
        groups.add(new ControlGroup(GROUP_LOOT_NPC_AQUATIC_INJECTIONS, "Aquatic NPC Drops", "Extra item rules injected when the NPC role breathes in water. Chance is a fraction: 0.0001 = 0.01%.", List.of()));
        groups.add(new ControlGroup(GROUP_LOOT_NPC_FLYING_INJECTIONS, "Flying NPC Drops", "Extra item rules injected when the NPC role matches flying role hints. Chance is a fraction: 0.0001 = 0.01%.", List.of()));
        groups.add(new ControlGroup(GROUP_LOOT_NPC_VOID_INJECTIONS, "Void NPC Drops", "Extra item rules injected when the NPC role matches void spawn hints. Chance is a fraction: 0.0001 = 0.01%.", List.of()));

        List<ControlEntry> essenceFill = new ArrayList<>();
        essenceFill.add(chanceControl("loot_greater_essence_chance", CATEGORY_LOOT, LOOT_CONFIG_NAME, "Greater essence chance", "Chance each filled socket uses a concentrated essence.", () -> lootConfig().getGreaterEssenceChance(), delta -> lootConfig().setGreaterEssenceChance(clampChance(lootConfig().getGreaterEssenceChance() + delta))));
        groups.add(new ControlGroup("essence_fill", "Essence Fill", "Controls for pre-filled socketed essences.", essenceFill));

        List<ControlEntry> brokenRange = new ArrayList<>();
        brokenRange.add(intControl("loot_min_broken", CATEGORY_LOOT, LOOT_CONFIG_NAME, "Min broken sockets", "Lower clamp after a loot roll resolves.", () -> lootConfig().getMinBrokenSockets(), delta -> lootConfig().setMinBrokenSockets(Math.min(clampInt(lootConfig().getMinBrokenSockets() + (int) Math.round(delta), 1, 8), lootConfig().getMaxBrokenSockets()))));
        brokenRange.add(intControl("loot_max_broken", CATEGORY_LOOT, LOOT_CONFIG_NAME, "Max broken sockets", "Upper clamp after a loot roll resolves.", () -> lootConfig().getMaxBrokenSockets(), delta -> lootConfig().setMaxBrokenSockets(Math.max(clampInt(lootConfig().getMaxBrokenSockets() + (int) Math.round(delta), 1, 8), lootConfig().getMinBrokenSockets()))));
        groups.add(new ControlGroup("broken_socket_range", "Broken Socket Range", "Final min/max clamp used after chest and drop rolls.", brokenRange));

        return new CategorySection(
                CATEGORY_LOOT,
                "toggleLootCategory",
                "Loot Rolls",
                "Treasure chest and NPC socket roll tuning with min/max clamps.",
                groups,
                null);
    }

    private static CategorySection buildDamageNumberCategory() {
        List<ControlGroup> groups = new ArrayList<>();

        List<ControlEntry> modeControls = new ArrayList<>();
        modeControls.add(toggleControl(
                "damage_numbers_custom",
                CATEGORY_DAMAGE_NUMBERS,
                DAMAGE_NUMBER_CONFIG_NAME,
                "Use custom damage numbers",
                "Legacy setting. Socket Reforge custom damage rendering is disabled; native Hytale combat text is forced on.",
                () -> damageNumberConfig().isUseCustomCombatText(),
                value -> damageNumberConfig().setUseCustomCombatText(value)));
        modeControls.add(selectControl(
                "damage_numbers_render_mode",
                CATEGORY_DAMAGE_NUMBERS,
                DAMAGE_NUMBER_CONFIG_NAME,
                "Render mode",
                "Legacy custom-renderer setting; native Hytale combat text is used for damage events.",
                DAMAGE_NUMBER_RENDER_MODES,
                () -> normalizeDamageNumberRenderMode(damageNumberConfig().getDamageNumberRenderMode()),
                value -> damageNumberConfig().setDamageNumberRenderMode(normalizeDamageNumberRenderMode(value))));
        groups.add(new ControlGroup("damage_numbers_mode", "Native Combat Text", "Damage events use native Hytale combat text. The legacy Socket Reforge renderer is forced off.", modeControls));

        return new CategorySection(
                CATEGORY_DAMAGE_NUMBERS,
                "toggleDamageNumbersCategory",
                "Damage Numbers",
                "Combat text system toggles for SocketReforge damage numbers.",
                groups,
                null);
    }

    private static CategorySection buildElementalAffinityCategory() {
        List<ControlGroup> groups = new ArrayList<>();

        List<ControlEntry> core = new ArrayList<>();
        core.add(toggleControl(
                "affinity_enabled",
                CATEGORY_ELEMENTAL_AFFINITY,
                ELEMENTAL_AFFINITY_CONFIG_NAME,
                "Enable elemental affinities",
                "Applies weapon affinity damage modifiers against NPC elemental profiles.",
                () -> elementalAffinityConfig().isEnabled(),
                value -> elementalAffinityConfig().setEnabled(value)));
        core.add(multiplierControl(
                "affinity_weakness_multiplier",
                CATEGORY_ELEMENTAL_AFFINITY,
                ELEMENTAL_AFFINITY_CONFIG_NAME,
                "Default weakness multiplier",
                "Fallback multiplier when an attacking element counters the target affinity.",
                () -> elementalAffinityConfig().getWeaknessMultiplier(),
                delta -> elementalAffinityConfig().setWeaknessMultiplier(
                        clamp(elementalAffinityConfig().getWeaknessMultiplier() + delta, 0.10, 10.0))));
        core.add(multiplierControl(
                "affinity_resistance_multiplier",
                CATEGORY_ELEMENTAL_AFFINITY,
                ELEMENTAL_AFFINITY_CONFIG_NAME,
                "Same-element resistance multiplier",
                "Fallback multiplier when attack and target affinities match. 0.10-0.20 heavily resists; 1.0 is neutral; above 1.0 is weakness.",
                () -> elementalAffinityConfig().getResistanceMultiplier(),
                delta -> elementalAffinityConfig().setResistanceMultiplier(
                        clamp(elementalAffinityConfig().getResistanceMultiplier() + delta, 0.10, 10.0))));
        core.add(multiplierControl(
                "affinity_shielded_hp_damage_multiplier",
                CATEGORY_ELEMENTAL_AFFINITY,
                ELEMENTAL_AFFINITY_CONFIG_NAME,
                "Shielded HP damage multiplier",
                "HP damage multiplier while an enemy elemental shield is active. 0.35 means HP takes 35% damage after shield absorption.",
                () -> elementalAffinityConfig().getShieldedHpDamageMultiplier(),
                delta -> elementalAffinityConfig().setShieldedHpDamageMultiplier(
                        clamp(elementalAffinityConfig().getShieldedHpDamageMultiplier() + delta, 0.0, 1.0))));
        core.add(multiplierControl(
                "affinity_non_elemental_shield_damage_multiplier",
                CATEGORY_ELEMENTAL_AFFINITY,
                ELEMENTAL_AFFINITY_CONFIG_NAME,
                "Non-elemental shield damage",
                "Shield damage multiplier for hits without elemental damage. 0.10 means plain hits chip shields for 10% damage.",
                () -> elementalAffinityConfig().getNonElementalShieldDamageMultiplier(),
                delta -> elementalAffinityConfig().setNonElementalShieldDamageMultiplier(
                        clamp(elementalAffinityConfig().getNonElementalShieldDamageMultiplier() + delta, 0.0, 1.0))));
        core.add(multiplierControl(
                "affinity_shield_recharge_delay_seconds",
                CATEGORY_ELEMENTAL_AFFINITY,
                ELEMENTAL_AFFINITY_CONFIG_NAME,
                "Shield recharge delay",
                "Seconds without damage after shield depletion before lazy recharge can begin.",
                () -> elementalAffinityConfig().getShieldRechargeDelaySeconds(),
                delta -> elementalAffinityConfig().setShieldRechargeDelaySeconds(
                        clamp(elementalAffinityConfig().getShieldRechargeDelaySeconds() + delta, 0.0, 3600.0))));
        core.add(multiplierControl(
                "affinity_shield_recharge_rate_per_second",
                CATEGORY_ELEMENTAL_AFFINITY,
                ELEMENTAL_AFFINITY_CONFIG_NAME,
                "Shield recharge rate",
                "Fraction of max shield restored per second after delay. 0.20 means 20% max shield per second.",
                () -> elementalAffinityConfig().getShieldRechargeRatePerSecond(),
                delta -> elementalAffinityConfig().setShieldRechargeRatePerSecond(
                        clamp(elementalAffinityConfig().getShieldRechargeRatePerSecond() + delta, 0.0, 1000.0))));
        core.add(multiplierControl(
                "affinity_shield_recharge_duration_seconds",
                CATEGORY_ELEMENTAL_AFFINITY,
                ELEMENTAL_AFFINITY_CONFIG_NAME,
                "Shield recharge duration",
                "Maximum seconds the depleted shield can recharge during one idle window.",
                () -> elementalAffinityConfig().getShieldRechargeDurationSeconds(),
                delta -> elementalAffinityConfig().setShieldRechargeDurationSeconds(
                        clamp(elementalAffinityConfig().getShieldRechargeDurationSeconds() + delta, 0.0, 3600.0))));
        groups.add(new ControlGroup("affinity_core", "Core Rules", "Global switches and fallback elemental effectiveness values.", core));

        groups.add(new ControlGroup(GROUP_AFFINITY_ROLE_RULES, "Default Affinity Rules", "Rules used after custom overrides to resolve an NPC's base affinity.", List.of()));
        groups.add(new ControlGroup(GROUP_AFFINITY_CUSTOM_RULES, "Custom Affinity Overrides", "Server-added NPC rules that win over default rules.", List.of()));
        groups.add(new ControlGroup(GROUP_AFFINITY_MULTIPLIERS, "Per-Target Effectiveness", "Optional damage overrides by target hint or exact NPC role.", List.of()));
        groups.add(new ControlGroup(GROUP_AFFINITY_SHIELDS, "Elemental Shields", "Optional per-target shield pools that absorb elemental damage before HP.", List.of()));

        return new CategorySection(
                CATEGORY_ELEMENTAL_AFFINITY,
                "toggleElementalAffinityCategory",
                "Elemental Affinity",
                "NPC affinity matching and per-element damage modifiers.",
                groups,
                "Valid elements: FIRE, WATER, ICE, LIGHTNING, LIFE, VOID. Custom rules are checked before defaults.");
    }

    private static CategorySection buildEndgameCategory() {
        List<ControlGroup> groups = new ArrayList<>();

        List<ControlEntry> ascensionCore = new ArrayList<>();
        ascensionCore.add(toggleControl(
                "ascension_enabled",
                CATEGORY_ASCENSION,
                ASCENSION_CONFIG_NAME,
                "Enable ascension",
                "Allow players to ascend maxed refinement equipment at the statue.",
                () -> ascensionConfig().isEnabled(),
                value -> ascensionConfig().setEnabled(value)));
        ascensionCore.add(intControl(
                "ascension_max_tier",
                CATEGORY_ASCENSION,
                ASCENSION_CONFIG_NAME,
                "Max ascension tier",
                "Maximum ascension tier (1-10).",
                () -> ascensionConfig().getMaxTier(),
                delta -> ascensionConfig().setMaxTier(clampInt(ascensionConfig().getMaxTier() + (int) Math.round(delta), 1, 10))));

        int maxTier = ascensionConfig().getMaxTier();
        for (int tier = 1; tier <= maxTier; tier++) {
            int currentTier = tier;
            ascensionCore.add(multiplierControl(
                    "ascension_carry_over_" + currentTier,
                    CATEGORY_ASCENSION,
                    ASCENSION_CONFIG_NAME,
                    "Tier " + currentTier + " carry over %",
                    "Fraction of refinement bonus carried to base on T" + currentTier + " ascension.",
                    () -> ascensionConfig().getTierCarryOver(currentTier),
                    delta -> ascensionConfig().setTierCarryOver(currentTier,
                            clamp(ascensionConfig().getTierCarryOver(currentTier) + delta, 0.0, 1.0))));
        }
        groups.add(new ControlGroup("ascension_core", "Ascension Settings", "Ascension enable/disable and per-tier carry over.", ascensionCore));

        List<ControlEntry> ascensionCosts = new ArrayList<>();
        for (int tier = 1; tier <= maxTier; tier++) {
            int currentTier = tier;
            ascensionCosts.add(textControl(
                    "ascension_cost_" + currentTier,
                    CATEGORY_ASCENSION,
                    ASCENSION_CONFIG_NAME,
                    "Tier " + currentTier + " cost",
                    "Item required to ascend to tier " + currentTier + ".",
                    () -> ascensionConfig().getTierCost(currentTier),
                    itemId -> ascensionConfig().setTierCost(currentTier, itemId)));
        }
        groups.add(new ControlGroup("ascension_costs", "Ascension Tier Costs", "Items consumed when ascending to each tier.", ascensionCosts));

        List<ControlEntry> corruptionCore = new ArrayList<>();
        corruptionCore.add(toggleControl(
                "corruption_enabled",
                CATEGORY_ASCENSION,
                CORRUPTION_CONFIG_NAME,
                "Enable corruption",
                "Allow players to corrupt equipment at the dark statue.",
                () -> corruptionConfig().isEnabled(),
                value -> corruptionConfig().setEnabled(value)));
        corruptionCore.add(intControl(
                "corruption_max",
                CATEGORY_ASCENSION,
                CORRUPTION_CONFIG_NAME,
                "Max corruptions",
                "Maximum number of corruptions per item (1-5).",
                () -> (int) corruptionConfig().getMaxCorruptions(),
                delta -> corruptionConfig().setMaxCorruptions(clampInt((int) corruptionConfig().getMaxCorruptions() + (int) Math.round(delta), 1, 5))));
        corruptionCore.add(chanceControl(
                "corruption_tier2_item_loss",
                CATEGORY_ASCENSION,
                CORRUPTION_CONFIG_NAME,
                "Tier 2 item loss chance",
                "Chance to lose the item on second corruption.",
                () -> corruptionConfig().getTier2ItemLoss(),
                delta -> corruptionConfig().setTier2ItemLoss(clamp(corruptionConfig().getTier2ItemLoss() + delta, 0.0, 1.0))));
        groups.add(new ControlGroup("corruption_core", "Corruption Settings", "Corruption enable/disable and core parameters.", corruptionCore));

        List<ControlEntry> corruptionCosts = new ArrayList<>();
        corruptionCosts.add(textControl(
                "corruption_tier1_cost",
                CATEGORY_ASCENSION,
                CORRUPTION_CONFIG_NAME,
                "Tier 1 cost",
                "Item required for first corruption.",
                () -> corruptionConfig().getTier1Cost(),
                itemId -> corruptionConfig().setTier1Cost(itemId)));
        corruptionCosts.add(textControl(
                "corruption_tier2_cost",
                CATEGORY_ASCENSION,
                CORRUPTION_CONFIG_NAME,
                "Tier 2 cost",
                "Item required for second corruption.",
                () -> corruptionConfig().getTier2Cost(),
                itemId -> corruptionConfig().setTier2Cost(itemId)));
        groups.add(new ControlGroup("corruption_costs", "Corruption Costs", "Items consumed when corrupting equipment.", corruptionCosts));

        List<ControlEntry> corruptionChances = new ArrayList<>();
        corruptionChances.add(chanceControl(
                "corruption_stat_increase",
                CATEGORY_ASCENSION,
                CORRUPTION_CONFIG_NAME,
                "Stat increase chance",
                "Chance for a stat to be increased.",
                () -> corruptionConfig().getStatIncreaseChance(),
                delta -> corruptionConfig().setStatIncreaseChance(clamp(corruptionConfig().getStatIncreaseChance() + delta, 0.0, 1.0))));
        corruptionChances.add(chanceControl(
                "corruption_stat_decrease",
                CATEGORY_ASCENSION,
                CORRUPTION_CONFIG_NAME,
                "Stat decrease chance",
                "Chance for a stat to be decreased.",
                () -> corruptionConfig().getStatDecreaseChance(),
                delta -> corruptionConfig().setStatDecreaseChance(clamp(corruptionConfig().getStatDecreaseChance() + delta, 0.0, 1.0))));
        corruptionChances.add(chanceControl(
                "corruption_stat_invert",
                CATEGORY_ASCENSION,
                CORRUPTION_CONFIG_NAME,
                "Stat invert chance",
                "Chance for a stat to be inverted.",
                () -> corruptionConfig().getStatInvertChance(),
                delta -> corruptionConfig().setStatInvertChance(clamp(corruptionConfig().getStatInvertChance() + delta, 0.0, 1.0))));
        corruptionChances.add(chanceControl(
                "corruption_stat_remove",
                CATEGORY_ASCENSION,
                CORRUPTION_CONFIG_NAME,
                "Stat remove chance",
                "Chance for a stat to be removed.",
                () -> corruptionConfig().getStatRemoveChance(),
                delta -> corruptionConfig().setStatRemoveChance(clamp(corruptionConfig().getStatRemoveChance() + delta, 0.0, 1.0))));
        corruptionChances.add(chanceControl(
                "corruption_add_new_stat",
                CATEGORY_ASCENSION,
                CORRUPTION_CONFIG_NAME,
                "Add new stat chance",
                "Chance to add a new stat.",
                () -> corruptionConfig().getAddNewStatChance(),
                delta -> corruptionConfig().setAddNewStatChance(clamp(corruptionConfig().getAddNewStatChance() + delta, 0.0, 1.0))));
        corruptionChances.add(chanceControl(
                "corruption_resonant_stat",
                CATEGORY_ASCENSION,
                CORRUPTION_CONFIG_NAME,
                "Resonant stat chance",
                "Chance to add a resonant stat.",
                () -> corruptionConfig().getResonantStatChance(),
                delta -> corruptionConfig().setResonantStatChance(clamp(corruptionConfig().getResonantStatChance() + delta, 0.0, 1.0))));
        groups.add(new ControlGroup("corruption_chances", "Corruption Stat Chances", "Probabilities for each corruption outcome.", corruptionChances));

        List<ControlEntry> corruptionModifiers = new ArrayList<>();
        corruptionModifiers.add(multiplierControl(
                "corruption_min_modifier",
                CATEGORY_ASCENSION,
                CORRUPTION_CONFIG_NAME,
                "Min stat modifier",
                "Minimum stat change magnitude.",
                () -> corruptionConfig().getMinStatModifier(),
                delta -> corruptionConfig().setMinStatModifier(clamp(corruptionConfig().getMinStatModifier() + delta, 0.0, 1.0))));
        corruptionModifiers.add(multiplierControl(
                "corruption_max_modifier",
                CATEGORY_ASCENSION,
                CORRUPTION_CONFIG_NAME,
                "Max stat modifier",
                "Maximum stat change magnitude.",
                () -> corruptionConfig().getMaxStatModifier(),
                delta -> corruptionConfig().setMaxStatModifier(clamp(corruptionConfig().getMaxStatModifier() + delta, 0.0, 1.0))));
        groups.add(new ControlGroup("corruption_modifiers", "Corruption Modifiers", "Stat change magnitude range.", corruptionModifiers));

        return new CategorySection(
                CATEGORY_ASCENSION,
                "toggleEndgameCategory",
                "Endgame System",
                "Ascension and Corruption systems for maxed refinement equipment.",
                groups,
                "Tier configurations update dynamically based on Max Ascension Tier. Corruption allows stat modification at a cost.");
    }

    private static CategorySection buildLoreCategory() {
        List<ControlGroup> groups = new ArrayList<>();

        List<ControlEntry> socketRolls = new ArrayList<>();
        socketRolls.add(chanceControl("lore_chest_socket_chance", CATEGORY_LORE, LORE_CONFIG_NAME, "Lore socket chest chance", "Chance for chest loot to roll lore sockets.", () -> loreConfig().getChestLoreSocketChance(), delta -> loreConfig().setChestLoreSocketChance(clampChance(loreConfig().getChestLoreSocketChance() + delta))));
        socketRolls.add(chanceControl("lore_drop_socket_chance", CATEGORY_LORE, LORE_CONFIG_NAME, "Lore socket drop chance", "Chance for NPC drops to roll lore sockets.", () -> loreConfig().getDropLoreSocketChance(), delta -> loreConfig().setDropLoreSocketChance(clampChance(loreConfig().getDropLoreSocketChance() + delta))));
        socketRolls.add(intControl("lore_socket_min", CATEGORY_LORE, LORE_CONFIG_NAME, "Lore socket min", "Minimum lore sockets on a roll.", () -> loreConfig().getMinLoreSockets(), delta -> {
            int next = clampInt(loreConfig().getMinLoreSockets() + (int) Math.round(delta), 0, 3);
            loreConfig().setMinLoreSockets(next);
            if (loreConfig().getMaxLoreSockets() < next) {
                loreConfig().setMaxLoreSockets(next);
            }
        }));
        socketRolls.add(intControl("lore_socket_max", CATEGORY_LORE, LORE_CONFIG_NAME, "Lore socket max", "Maximum lore sockets on a roll.", () -> loreConfig().getMaxLoreSockets(), delta -> {
            int next = clampInt(loreConfig().getMaxLoreSockets() + (int) Math.round(delta), 0, 3);
            loreConfig().setMaxLoreSockets(Math.max(next, loreConfig().getMinLoreSockets()));
        }));
        groups.add(new ControlGroup("lore_socket_rolls", "Lore Socket Rolls", "Controls how lore sockets appear on generated equipment.", socketRolls));

        List<ControlEntry> leveling = new ArrayList<>();
        leveling.add(intControl("lore_max_level", CATEGORY_LORE, LORE_CONFIG_NAME, "Lore max level", "Maximum level a socketed lore can reach.", () -> loreConfig().getMaxLevel(), delta -> loreConfig().setMaxLevel(clampInt(loreConfig().getMaxLevel() + (int) Math.round(delta), 1, 200))));
        leveling.add(intControl("lore_xp_per_proc", CATEGORY_LORE, LORE_CONFIG_NAME, "Lore XP per proc", "XP gained per lore proc trigger.", () -> loreConfig().getXpPerProc(), delta -> loreConfig().setXpPerProc(clampInt(loreConfig().getXpPerProc() + (int) Math.round(delta), 1, 50))));
        leveling.add(intControl("lore_base_xp_per_level", CATEGORY_LORE, LORE_CONFIG_NAME, "Lore base XP per level", "Base XP required per level.", () -> loreConfig().getBaseXpPerLevel(), delta -> loreConfig().setBaseXpPerLevel(clampInt(loreConfig().getBaseXpPerLevel() + (int) Math.round(delta), 1, 500))));
        leveling.add(multiplierControl("lore_xp_growth", CATEGORY_LORE, LORE_CONFIG_NAME, "Lore XP growth", "Additional XP growth per level.", () -> loreConfig().getXpGrowthPerLevel(), delta -> loreConfig().setXpGrowthPerLevel(clamp(loreConfig().getXpGrowthPerLevel() + delta, 0.0, 10.0))));
        groups.add(new ControlGroup("lore_leveling", "Lore Leveling", "XP and level progression for spirit-filled lore gems.", leveling));

        List<ControlEntry> feeding = new ArrayList<>();
        feeding.add(intControl("lore_feed_interval", CATEGORY_LORE, LORE_CONFIG_NAME, "Lore feed interval", "Levels per feed gate.", () -> loreConfig().getFeedInterval(), delta -> loreConfig().setFeedInterval(clampInt(loreConfig().getFeedInterval() + (int) Math.round(delta), 1, 50))));
        feeding.add(intControl("lore_feed_base", CATEGORY_LORE, LORE_CONFIG_NAME, "Lore feed base cost", "Base Resonant Essence cost per feed.", () -> loreConfig().getFeedBase(), delta -> loreConfig().setFeedBase(clampInt(loreConfig().getFeedBase() + (int) Math.round(delta), 0, 100))));
        feeding.add(intControl("lore_feed_multiplier", CATEGORY_LORE, LORE_CONFIG_NAME, "Lore feed multiplier", "Cost multiplier each feed tier.", () -> loreConfig().getFeedMultiplier(), delta -> loreConfig().setFeedMultiplier(clampInt(loreConfig().getFeedMultiplier() + (int) Math.round(delta), 1, 10))));
        feeding.add(textListControl("lore_feed_item_ids", CATEGORY_LORE, LORE_CONFIG_NAME, "Lore feed item IDs", "One item ID per line. These items can feed spirits.", () -> loreConfig().getFeedItemIds(), value -> loreConfig().setFeedItemIds(splitListValue(value))));
        feeding.add(textListControl("lore_clear_item_ids", CATEGORY_LORE, LORE_CONFIG_NAME, "Lore clear item IDs", "One item ID per line. These items clear socketed lore gems or spirits.", () -> loreConfig().getClearItemIds(), value -> loreConfig().setClearItemIds(splitListValue(value))));
        groups.add(new ControlGroup("lore_feeding", "Lore Feeding and Clearing", "Feed cadence, feed cost, and material item ID lists.", feeding));

        List<ControlEntry> bleed = new ArrayList<>();
        bleed.add(multiplierControl("lore_bleed_ramp", CATEGORY_LORE, LORE_CONFIG_NAME, "Bleed ramp per tick", "Distributes the bleed's own configured total damage budget across later ticks. It does not scale from weapon damage.", () -> loreConfig().getBleedRampPerTick(), delta -> loreConfig().setBleedRampPerTick(clamp(loreConfig().getBleedRampPerTick() + delta, 0.0, 10.0))));
        groups.add(new ControlGroup("lore_bleed", "Lore Bleed Scaling", "Bleed uses its own configured damage, then applies native physical resistance and ailment resistance.", bleed));

        groups.add(new ControlGroup(GROUP_LORE_STATUS_RULES, "Lore Status Rules", "Resistance, counter, and reapply rules for lore statuses.", List.of()));
        groups.addAll(buildLoreMappingGroups());

        return new CategorySection(
                CATEGORY_LORE,
                "toggleLoreCategory",
                "Lore Config",
                "Lore socket roll rates, leveling, feeding, clearing, and status-effect balancing.",
                groups,
                null);
    }

    private static List<ControlGroup> buildLoreMappingGroups() {
        List<ControlGroup> groups = new ArrayList<>();

        groups.add(new ControlGroup("lore_mapping_gems", "Gem Color Mapping", "Maps gem item tokens to lore socket colors and marks core colors.", List.of()));
        groups.add(new ControlGroup("lore_mapping_spirits", "Gem-to-Spirit Compatibility", "Controls which spirits can roll for each lore gem color.", List.of()));

        groups.add(new ControlGroup(GROUP_LORE_MAPPING_ABILITIES, "Spirit Ability Mapping", "Maps spirits to triggers, proc chances, cooldowns, and lore effects.", List.of()));

        return groups;
    }

    private static List<ControlEntry> buildWeightControls(int level) {
        String labelPrefix = "L" + level;
        String idPrefix = "refine_weight_" + level + "_";
        List<ControlEntry> weightControls = new ArrayList<>();
        weightControls.add(weightControl(idPrefix + "degrade", level, 0, labelPrefix + " degrade", "Chance to drop one level."));
        weightControls.add(weightControl(idPrefix + "same", level, 1, labelPrefix + " same", "Chance to stay at the same level."));
        weightControls.add(weightControl(idPrefix + "upgrade", level, 2, labelPrefix + " upgrade", "Chance to gain one level."));
        weightControls.add(weightControl(idPrefix + "jackpot", level, 3, labelPrefix + " jackpot", "Chance to gain two levels."));
        return weightControls;
    }

    private static List<ControlEntry> buildMaterialTierControls() {
        List<ControlEntry> controls = new ArrayList<>();
        List<MaterialTier> tiers = getMaterialTierSnapshot();
        if (tiers.isEmpty()) {
            return controls;
        }
        int tierCount = tiers.size();
        for (int i = 0; i < tierCount; i++) {
            MaterialTier tier = tiers.get(i);
            String tierLabel = "Tier " + (i + 1) + " (" + tier.itemId + ")";
            if (i < tierCount - 1) {
                int index = i;
                controls.add(intControlStep(
                        "refine_tier_max_" + index,
                        CATEGORY_REFINEMENT,
                        REFINEMENT_CONFIG_NAME,
                        tierLabel + " max level",
                        "Upper level bound for " + tierLabel + ".",
                        1,
                        5,
                        () -> getTierMaxValue(index),
                        delta -> setTierMaxValue(index, getTierMaxValue(index) + (int) Math.round(delta))));
            }
            int costIndex = i;
            controls.add(intControlStep(
                    "refine_tier_cost_" + costIndex,
                    CATEGORY_REFINEMENT,
                    REFINEMENT_CONFIG_NAME,
                    tierLabel + " cost",
                    "Material count consumed per refine attempt.",
                    1,
                    5,
                    () -> getTierCostValue(costIndex),
                    delta -> setTierCostValue(costIndex, getTierCostValue(costIndex) + (int) Math.round(delta))));
        }
        return controls;
    }

    private static List<ControlEntry> buildRefinementLabelControls(boolean isArmor) {
        RefinementConfig cfg = refinementConfig();
        int maxLevel = Math.max(1, cfg.getMaxLevel());
        List<ControlEntry> controls = new ArrayList<>();
        boolean usePrefix = cfg.isRefinementLevelUsePrefix();
        String idPrefix = isArmor ? "refine_label_armor_" : "refine_label_weapon_";
        for (int level = 1; level <= maxLevel; level++) {
            int index = level;
            controls.add(textControl(
                    idPrefix + level,
                    CATEGORY_REFINEMENT,
                    REFINEMENT_CONFIG_NAME,
                    "Level " + level + " label",
                    usePrefix
                            ? "Prefix text for level " + level + " (e.g., \"Sharp \")."
                            : "Suffix text for level " + level + " (e.g., \" +" + level + "\").",
                    () -> getRefinementLabelValue(index, isArmor),
                    value -> refinementConfig().setRefinementLevelLabel(index, value, isArmor)));
        }
        return controls;
    }

    private static String getRefinementLabelValue(int level, boolean isArmor) {
        String[] labels = isArmor
                ? refinementConfig().getRefinementLevelLabelsArmor()
                : refinementConfig().getRefinementLevelLabels();
        if (labels == null || level < 0 || level >= labels.length) {
            return "";
        }
        String value = labels[level];
        return value == null ? "" : value;
    }

    private static NumericControl intControl(String id, String categoryId, String configName, String label, String description, ValueSupplier supplier, AdjustHandler adjuster) {
        return new NumericControl(id, categoryId, configName, label, description, DisplayKind.INTEGER, 1, 2, supplier, adjuster);
    }

    private static NumericControl intControlStep(String id,
                                                 String categoryId,
                                                 String configName,
                                                 String label,
                                                 String description,
                                                 double smallStep,
                                                 double largeStep,
                                                 ValueSupplier supplier,
                                                 AdjustHandler adjuster) {
        return new NumericControl(id, categoryId, configName, label, description, DisplayKind.INTEGER, smallStep, largeStep, supplier, adjuster);
    }

    private static NumericControl chanceControl(String id, String categoryId, String configName, String label, String description, ValueSupplier supplier, AdjustHandler adjuster) {
        return new NumericControl(id, categoryId, configName, label, description, DisplayKind.PERCENT, 0.01, 0.05, supplier, adjuster);
    }

    private static NumericControl toggleControl(String id, String categoryId, String configName, String label, String description, java.util.function.BooleanSupplier supplier, java.util.function.Consumer<Boolean> setter) {
        return new NumericControl(
                id,
                categoryId,
                configName,
                label,
                description,
                DisplayKind.TOGGLE,
                1,
                1,
                () -> supplier.getAsBoolean() ? 1.0 : 0.0,
                delta -> setter.accept(delta > 0));
    }

    private static TextControl textControl(String id,
                                           String categoryId,
                                           String configName,
                                           String label,
                                           String description,
                                           TextSupplier supplier,
                                           TextHandler handler) {
        return new TextControl(id, categoryId, configName, label, description, supplier, handler);
    }

    private static TextControl textListControl(String id,
                                               String categoryId,
                                               String configName,
                                               String label,
                                               String description,
                                               java.util.function.Supplier<String[]> supplier,
                                               TextHandler handler) {
        return new TextControl(
                id,
                categoryId,
                configName,
                label,
                description,
                () -> joinListValue(supplier == null ? null : supplier.get()),
                handler,
                true);
    }

    private static SelectControl selectControl(String id,
                                               String categoryId,
                                               String configName,
                                               String label,
                                               String description,
                                               String[] options,
                                               TextSupplier supplier,
                                               TextHandler handler) {
        return new SelectControl(id, categoryId, configName, label, description, options, supplier, handler);
    }

    private static String normalizeDamageNumberRenderMode(String value) {
        if (value == null || value.isBlank()) {
            return "COMBAT_TEXT";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "HUD", "HUD_POPUP", "POPUP_HUD", "SCREEN_HUD" -> "HUD_POPUP";
            case "PARTICLE", "PARTICLES", "PARTICLE_DIGITS", "DIGIT_PARTICLES" -> "PARTICLE_DIGITS";
            case "OFF", "NONE", "DISABLED" -> "OFF";
            default -> "COMBAT_TEXT";
        };
    }

    private static NumericControl chanceArrayControl(String id, String categoryId, String configName, String label, String description, ArraySupplier arraySupplier, int index) {
        return new NumericControl(
                id,
                categoryId,
                configName,
                label,
                description,
                DisplayKind.PERCENT,
                0.01,
                0.05,
                () -> arraySupplier.get()[index],
                delta -> arraySupplier.get()[index] = clampChance(arraySupplier.get()[index] + delta));
    }

    private static NumericControl multiplierArrayControl(String id, String categoryId, String label, String description, ArraySupplier arraySupplier, String configName, int index) {
        return new NumericControl(
                id,
                categoryId,
                configName,
                label,
                description,
                DisplayKind.MULTIPLIER,
                0.01,
                0.05,
                () -> arraySupplier.get()[index],
                delta -> arraySupplier.get()[index] = clamp(arraySupplier.get()[index] + delta, 0.10, 5.0));
    }

    private static NumericControl weightControl(String id, int transition, int index, String label, String description) {
        return new NumericControl(
                id,
                CATEGORY_REFINEMENT,
                REFINEMENT_CONFIG_NAME,
                label,
                description,
                DisplayKind.PERCENT,
                0.005,
                0.025,
                () -> getWeightValue(transition, index),
                delta -> adjustWeightByLevel(transition, index, delta));
    }

    private static SocketConfig socketConfig() {
        return plugin.getSocketRuntimeConfig();
    }

    private static RefinementConfig refinementConfig() {
        return plugin.getRefinementRuntimeConfig();
    }

    private static LootSocketRollConfig lootConfig() {
        return plugin.getLootSocketRollRuntimeConfig();
    }

    private static CrossModConfig crossModConfig() {
        return plugin.getCrossModRuntimeConfig();
    }

    private static LoreConfig loreConfig() {
        return plugin.getLoreRuntimeConfig();
    }

    private static LoreMappingConfig loreMappingConfig() {
        return plugin.getLoreMappingRuntimeConfig();
    }

    private static DamageNumberConfig damageNumberConfig() {
        return plugin.getDamageNumberRuntimeConfig();
    }

    private static ElementalAffinityConfig elementalAffinityConfig() {
        return plugin.getElementalAffinityRuntimeConfig();
    }

    private static AscensionConfig ascensionConfig() {
        return plugin.getAscensionRuntimeConfig();
    }

    private static CorruptionConfig corruptionConfig() {
        return plugin.getCorruptionRuntimeConfig();
    }

    private static List<MaterialTier> getMaterialTierSnapshot() {
        List<MaterialTier> tiers = new ArrayList<>(refinementConfig().getMaterialTiers());
        tiers.sort(java.util.Comparator.comparingInt(t -> t.minLevel));
        return tiers;
    }

    private static int getTierMaxValue(int index) {
        List<MaterialTier> tiers = getMaterialTierSnapshot();
        if (tiers.isEmpty() || index < 0 || index >= tiers.size()) {
            return refinementConfig().getMaxLevel();
        }
        if (index == tiers.size() - 1) {
            return refinementConfig().getMaxLevel();
        }
        return tiers.get(index).maxLevel;
    }

    private static int getTierCostValue(int index) {
        List<MaterialTier> tiers = getMaterialTierSnapshot();
        if (tiers.isEmpty() || index < 0 || index >= tiers.size()) {
            return 1;
        }
        return tiers.get(index).cost;
    }

    private static void setTierMaxValue(int index, int newMax) {
        List<MaterialTier> tiers = getMaterialTierSnapshot();
        if (tiers.isEmpty() || index < 0 || index >= tiers.size() - 1) {
            return;
        }
        int maxLevel = refinementConfig().getMaxLevel();
        int tierCount = tiers.size();

        int[] maxes = new int[tierCount];
        for (int i = 0; i < tierCount; i++) {
            maxes[i] = i == tierCount - 1 ? maxLevel : tiers.get(i).maxLevel;
        }

        int minBound = index == 0 ? 0 : maxes[index - 1] + 1;
        int maxBound = Math.min(maxLevel - (tierCount - index - 2), maxes[index + 1] - 1);
        if (maxBound < minBound) {
            maxBound = minBound;
        }
        maxes[index] = clampInt(newMax, minBound, maxBound);

        for (int i = index + 1; i < tierCount - 1; i++) {
            if (maxes[i] <= maxes[i - 1]) {
                maxes[i] = maxes[i - 1] + 1;
            }
        }
        maxes[tierCount - 1] = maxLevel;

        applyMaterialTierEntries(tiers, maxes);
    }

    private static void setTierCostValue(int index, int newCost) {
        List<MaterialTier> tiers = getMaterialTierSnapshot();
        if (tiers.isEmpty() || index < 0 || index >= tiers.size()) {
            return;
        }
        int tierCount = tiers.size();
        int maxLevel = refinementConfig().getMaxLevel();

        int[] maxes = new int[tierCount];
        for (int i = 0; i < tierCount; i++) {
            maxes[i] = i == tierCount - 1 ? maxLevel : tiers.get(i).maxLevel;
        }

        List<MaterialTier> updated = new ArrayList<>();
        for (int i = 0; i < tierCount; i++) {
            MaterialTier tier = tiers.get(i);
            int cost = i == index ? Math.max(1, newCost) : tier.cost;
            updated.add(new MaterialTier(tier.minLevel, tier.maxLevel, tier.itemId, cost));
        }
        applyMaterialTierEntries(updated, maxes);
    }

    private static void applyMaterialTierEntries(List<MaterialTier> tiers, int[] maxes) {
        if (tiers == null || tiers.isEmpty()) {
            return;
        }
        int tierCount = tiers.size();
        int maxLevel = refinementConfig().getMaxLevel();
        String[] entries = new String[tierCount];
        int min = 0;
        for (int i = 0; i < tierCount; i++) {
            MaterialTier tier = tiers.get(i);
            int max = i == tierCount - 1 ? maxLevel : Math.max(min, maxes[i]);
            entries[i] = min + "-" + max + "=" + tier.itemId + ":" + Math.max(1, tier.cost);
            min = max + 1;
        }
        refinementConfig().setMaterialTierEntries(entries);
    }

    private static NumericControl multiplierControl(String id,
                                                    String categoryId,
                                                    String configName,
                                                    String label,
                                                    String description,
                                                    ValueSupplier supplier,
                                                    AdjustHandler adjuster) {
        return new NumericControl(
                id,
                categoryId,
                configName,
                label,
                description,
                DisplayKind.MULTIPLIER,
                0.05,
                0.25,
                supplier,
                adjuster);
    }

    private static double[] ensureSocketSuccessArray() {
        SocketConfig cfg = socketConfig();
        double[] current = cfg.getPunchSuccessChances();
        double[] normalized = ensureArray(current, 4, DEFAULT_SOCKET_SUCCESS);
        if (normalized != current) {
            cfg.setPunchSuccessChances(normalized);
        }
        return normalized;
    }

    private static double[] ensureSocketBreakArray() {
        SocketConfig cfg = socketConfig();
        double[] current = cfg.getPunchBreakChances();
        double[] normalized = ensureArray(current, 4, DEFAULT_SOCKET_BREAK);
        if (normalized != current) {
            cfg.setPunchBreakChances(normalized);
        }
        return normalized;
    }

    private static double[] ensureDamageMultipliers() {
        RefinementConfig cfg = refinementConfig();
        double[] current = cfg.getDamageMultipliers();
        int required = Math.max(1, cfg.getMaxLevel()) + 1;
        double[] normalized = ensureArray(current, required, DEFAULT_DAMAGE_MULTIPLIERS);
        if (normalized != current) {
            cfg.setDamageMultipliers(normalized);
        }
        return normalized;
    }

    private static double[] ensureDefenseMultipliers() {
        RefinementConfig cfg = refinementConfig();
        double[] current = cfg.getDefenseMultipliers();
        int required = Math.max(1, cfg.getMaxLevel()) + 1;
        double[] normalized = ensureArray(current, required, DEFAULT_DEFENSE_MULTIPLIERS);
        if (normalized != current) {
            cfg.setDefenseMultipliers(normalized);
        }
        return normalized;
    }

    private static double[] ensureWeaponBreakArray() {
        RefinementConfig cfg = refinementConfig();
        double[] current = cfg.getBreakChances();
        int required = Math.max(1, cfg.getMaxLevel());
        double[] normalized = ensureArray(current, required, DEFAULT_WEAPON_BREAK);
        if (normalized != current) {
            cfg.setBreakChances(normalized);
        }
        return normalized;
    }

    private static double[] ensureArmorBreakArray() {
        RefinementConfig cfg = refinementConfig();
        double[] current = cfg.getArmorBreakChances();
        int required = Math.max(1, cfg.getMaxLevel());
        double[] normalized = ensureArray(current, required, DEFAULT_ARMOR_BREAK);
        if (normalized != current) {
            cfg.setArmorBreakChances(normalized);
        }
        return normalized;
    }

    private static double getWeightValue(int level, int index) {
        double[] weights = ensureWeightsByLevelArray();
        int base = level * 4;
        int offset = base + index;
        if (offset < 0 || offset >= weights.length) {
            return 0.0;
        }
        return weights[offset];
    }

    private static double[] ensureWeightsByLevelArray() {
        RefinementConfig cfg = refinementConfig();
        int maxLevel = Math.max(1, cfg.getMaxLevel());
        int required = maxLevel * 4;
        double[] current = cfg.getWeightsByLevel();
        if (current != null && current.length >= required) {
            enforceLastLevelJackpotZero(current);
            return current;
        }
        double[] normalized = new double[required];
        for (int level = 0; level < maxLevel; level++) {
            double[] weights = cfg.getReforgeWeights(level);
            if (weights == null || weights.length < 4) {
                weights = DEFAULT_WEIGHTS[Math.min(2, level)];
            }
            int base = level * 4;
            for (int i = 0; i < 4; i++) {
                normalized[base + i] = weights[i];
            }
        }
        enforceLastLevelJackpotZero(normalized);
        cfg.setWeightsByLevel(normalized);
        return normalized;
    }

    private static double[] ensureWeightArray(int transition) {
        double[] weights = ensureWeightsByLevelArray();
        int base = transition * 4;
        if (base + 3 >= weights.length) {
            return DEFAULT_WEIGHTS[Math.min(2, transition)];
        }
        double[] out = new double[4];
        System.arraycopy(weights, base, out, 0, 4);
        return out;
    }

    private static void adjustWeightByLevel(int level, int index, double delta) {
        RefinementConfig cfg = refinementConfig();
        double[] weights = ensureWeightsByLevelArray();
        int base = level * 4;
        if (base + 3 >= weights.length) {
            return;
        }
        double target = clamp(weights[base + index] + delta, 0.0, 1.0);
        double remainingTarget = Math.max(0.0, 1.0 - target);
        double remainingCurrent = 0.0;
        for (int i = 0; i < 4; i++) {
            if (i != index) {
                remainingCurrent += Math.max(0.0, weights[base + i]);
            }
        }

        weights[base + index] = target;
        if (remainingCurrent <= 0.0000001) {
            double even = remainingTarget / 3.0;
            for (int i = 0; i < 4; i++) {
                if (i != index) {
                    weights[base + i] = even;
                }
            }
        } else {
            double scale = remainingTarget / remainingCurrent;
            for (int i = 0; i < 4; i++) {
                if (i != index) {
                    weights[base + i] = clamp(Math.max(0.0, weights[base + i]) * scale, 0.0, 1.0);
                }
            }
        }

        if (level == 0) {
            cfg.setWeights0to1(extractWeightsSlice(weights, base));
        } else if (level == 1) {
            cfg.setWeights1to2(extractWeightsSlice(weights, base));
        } else if (level == 2) {
            cfg.setWeights2to3(extractWeightsSlice(weights, base));
        }
        enforceLastLevelJackpotZero(weights);
    }

    private static void enforceLastLevelJackpotZero(double[] weights) {
        if (weights == null || weights.length < 4) {
            return;
        }
        int maxLevel = Math.max(1, refinementConfig().getMaxLevel());
        int lastLevel = Math.max(0, maxLevel - 1);
        int base = lastLevel * 4;
        if (base + 3 >= weights.length) {
            return;
        }
        double degrade = Math.max(0.0, weights[base]);
        double same = Math.max(0.0, weights[base + 1]);
        double upgrade = Math.max(0.0, weights[base + 2]);
        double sum = degrade + same + upgrade;
        if (sum <= 0.0) {
            degrade = 0.0;
            same = 1.0;
            upgrade = 0.0;
        } else {
            double scale = 1.0 / sum;
            degrade *= scale;
            same *= scale;
            upgrade *= scale;
        }
        weights[base] = degrade;
        weights[base + 1] = same;
        weights[base + 2] = upgrade;
        weights[base + 3] = 0.0;

        RefinementConfig cfg = refinementConfig();
        if (lastLevel == 0) {
            cfg.setWeights0to1(extractWeightsSlice(weights, base));
        } else if (lastLevel == 1) {
            cfg.setWeights1to2(extractWeightsSlice(weights, base));
        } else if (lastLevel == 2) {
            cfg.setWeights2to3(extractWeightsSlice(weights, base));
        }
    }

    private static double[] extractWeightsSlice(double[] weights, int base) {
        double[] out = new double[4];
        if (weights != null && base >= 0 && base + 3 < weights.length) {
            System.arraycopy(weights, base, out, 0, 4);
        }
        return out;
    }

    private static double[] ensureArray(double[] source, int length, double[] defaults) {
        if (source != null && source.length >= length) {
            return source;
        }
        double[] normalized = new double[length];
        double fallback = 0.0;
        if (defaults != null && defaults.length > 0) {
            fallback = defaults[defaults.length - 1];
        }
        for (int i = 0; i < length; i++) {
            normalized[i] = defaults != null && i < defaults.length ? defaults[i] : fallback;
        }
        if (source != null) {
            for (int i = 0; i < Math.min(source.length, length); i++) {
                normalized[i] = source[i];
            }
        }
        return normalized;
    }

    private static String buttonIdForDelta(NumericControl control, double delta) {
        if (control.displayKind == DisplayKind.TOGGLE) {
            if (roughlyEqual(delta, -control.smallStep)) {
                return control.minusButtonId();
            }
            return control.plusButtonId();
        }
        if (roughlyEqual(delta, -control.largeStep)) {
            return control.minusLargeButtonId();
        }
        if (roughlyEqual(delta, -control.smallStep)) {
            return control.minusButtonId();
        }
        if (roughlyEqual(delta, control.smallStep)) {
            return control.plusButtonId();
        }
        return control.plusLargeButtonId();
    }

    private static String formatValue(DisplayKind kind, double rawValue) {
        if (kind == DisplayKind.INTEGER) {
            return String.valueOf((int) Math.round(rawValue));
        }
        if (kind == DisplayKind.PERCENT) {
            return String.format(Locale.ROOT, "%.1f%%", rawValue * 100.0);
        }
        if (kind == DisplayKind.TOGGLE) {
            return rawValue >= 0.5 ? "Enabled" : "Disabled";
        }
        double bonusPercent = (rawValue - 1.0) * 100.0;
        return "x" + String.format(Locale.ROOT, "%.3f", rawValue)
                + " (" + String.format(Locale.ROOT, "%+.1f%%", bonusPercent) + ")";
    }

    private static String formatInputValue(DisplayKind kind, double rawValue) {
        if (kind == DisplayKind.INTEGER) {
            return String.valueOf((int) Math.round(rawValue));
        }
        if (kind == DisplayKind.PERCENT) {
            return String.format(Locale.ROOT, "%.2f", rawValue * 100.0);
        }
        if (kind == DisplayKind.TOGGLE) {
            return rawValue >= 0.5 ? "Enabled" : "Disabled";
        }
        return String.format(Locale.ROOT, "%.3f", rawValue);
    }

    private static String formatStepLabel(DisplayKind kind, double rawStep, boolean positive) {
        String prefix = positive ? "+" : "-";
        if (kind == DisplayKind.INTEGER) {
            return prefix + (int) Math.round(rawStep);
        }
        if (kind == DisplayKind.TOGGLE) {
            return positive ? "Enable" : "Disable";
        }
        double percentStep = rawStep * 100.0;
        if (roughlyEqual(percentStep, Math.rint(percentStep))) {
            return prefix + (int) Math.round(percentStep) + "%";
        }
        return prefix + String.format(Locale.ROOT, "%.1f%%", percentStep);
    }

    private static String inputStep(NumericControl control) {
        if (control.displayKind == DisplayKind.INTEGER) {
            return "1";
        }
        if (control.displayKind == DisplayKind.PERCENT) {
            return "0.01";
        }
        if (control.displayKind == DisplayKind.MULTIPLIER) {
            return "0.001";
        }
        return "1";
    }

    private static int inputDecimals(NumericControl control) {
        if (control.displayKind == DisplayKind.INTEGER) {
            return 0;
        }
        if (control.displayKind == DisplayKind.PERCENT) {
            return 2;
        }
        if (control.displayKind == DisplayKind.MULTIPLIER) {
            return 3;
        }
        return 0;
    }

    private static String inputMin(NumericControl control) {
        if (control.displayKind == DisplayKind.TOGGLE) {
            return null;
        }
        if (control.displayKind == DisplayKind.PERCENT) {
            return "0";
        }
        if (control.displayKind == DisplayKind.MULTIPLIER) {
            return "0";
        }
        if (control.displayKind == DisplayKind.INTEGER) {
            return "0";
        }
        return null;
    }

    private static String inputMax(NumericControl control) {
        if (control.displayKind == DisplayKind.PERCENT) {
            return "100";
        }
        return null;
    }

    private static String joinListValue(String[] values) {
        if (values == null || values.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(value.trim());
        }
        return sb.toString();
    }

    private static String[] splitListValue(String value) {
        if (value == null || value.isBlank()) {
            return new String[0];
        }
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.replace(',', '\n').split("\n");
        List<String> entries = new ArrayList<>();
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                entries.add(trimmed);
            }
        }
        return entries.toArray(String[]::new);
    }

    private static List<String> mutableList(String[] values) {
        List<String> list = new ArrayList<>();
        if (values == null) {
            return list;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                list.add(value.trim());
            }
        }
        return list;
    }

    private static List<ArraySection> arraySections(String groupId) {
        if (groupId == null) return List.of();
        if (GROUP_RESONANCE_CLASS_MAPPINGS.equals(groupId))
            return List.of(new ArraySection("socket.resonance_mappings", "Resonance class mappings", SOCKET_CONFIG_NAME));
        if (GROUP_RESONANCE_CLASS_HINTS.equals(groupId))
            return List.of(new ArraySection("socket.resonance_class_hints", "Weapon class hints", SOCKET_CONFIG_NAME));
        if (GROUP_CLOCKWORK_AMMO_HINTS.equals(groupId))
            return List.of(new ArraySection("socket.clockwork_ammo_hints", "Ammo item hints", SOCKET_CONFIG_NAME));
        if (GROUP_REFORGE_SUPPORT_MATERIALS.equals(groupId))
            return List.of(new ArraySection("refinement.support_materials", "Support material rules", REFINEMENT_CONFIG_NAME));
        if (GROUP_LORE_MAPPING_GEMS.equals(groupId))
            return List.of(
                    new ArraySection("lore_mapping.gem_colors", "Gem color mappings", LORE_MAPPING_CONFIG_NAME),
                    new ArraySection("lore_mapping.core_colors", "Core lore colors", LORE_MAPPING_CONFIG_NAME));
        if (GROUP_LORE_MAPPING_SPIRITS.equals(groupId))
            return List.of(new ArraySection("lore_mapping.color_spirits", "Color to spirit mappings", LORE_MAPPING_CONFIG_NAME));
        if (GROUP_LORE_MAPPING_ABILITIES.equals(groupId))
            return List.of(new ArraySection("lore_mapping.abilities", "Spirit ability mappings", LORE_MAPPING_CONFIG_NAME));
        if (GROUP_LORE_STATUS_RULES.equals(groupId))
            return List.of(
                    new ArraySection("lore.status_reapply", "Boss status reapply rules", LORE_CONFIG_NAME),
                    new ArraySection("lore.status_resistance", "NPC status resistance", LORE_CONFIG_NAME),
                    new ArraySection("lore.status_counters", "Boss status counters", LORE_CONFIG_NAME),
                    new ArraySection("lore.bleed_counters", "Bleed counter NPCs", LORE_CONFIG_NAME));
        if (isLootInjectionGroup(groupId))
            return List.of(new ArraySection("loot." + groupId, localizedArrayTitle(groupId), LOOT_CONFIG_NAME));
        if (isAffinityRuleGroup(groupId))
            return List.of(new ArraySection("affinity.rules." + groupId, localizedArrayTitle(groupId), ELEMENTAL_AFFINITY_CONFIG_NAME));
        if (GROUP_AFFINITY_MULTIPLIERS.equals(groupId) || GROUP_AFFINITY_SHIELDS.equals(groupId))
            return List.of(new ArraySection("affinity.values." + groupId, localizedArrayTitle(groupId), ELEMENTAL_AFFINITY_CONFIG_NAME));
        return List.of();
    }

    private static String localizedArrayTitle(String groupId) {
        for (CategorySection category : categories) {
            for (ControlGroup group : category.groups) {
                if (group.id.equals(groupId)) return group.title;
            }
        }
        return "Entries";
    }

    private static String[] readArray(String sectionId) {
        if (sectionId == null) return new String[0];
        return switch (sectionId) {
            case "socket.resonance_mappings" -> resonanceMappingDisplayEntries();
            case "socket.resonance_class_hints" -> socketConfig().getResonanceWeaponClassHints();
            case "socket.clockwork_ammo_hints" -> socketConfig().getClockworkAmmoItemHints();
            case "refinement.support_materials" -> refinementConfig().getSupportMaterialEntries();
            case "lore_mapping.gem_colors" -> loreMappingConfig().getGemColorEntries();
            case "lore_mapping.core_colors" -> loreMappingConfig().getCoreColorEntries();
            case "lore_mapping.color_spirits" -> loreMappingConfig().getColorSpiritEntries();
            case "lore_mapping.abilities" -> loreMappingConfig().getAbilityEntries();
            case "lore.status_reapply" -> loreConfig().getStatusBossReapplyRules();
            case "lore.status_resistance" -> loreConfig().getNpcStatusResistanceEntries();
            case "lore.status_counters" -> loreConfig().getStatusBossCounterNpcIds();
            case "lore.bleed_counters" -> loreConfig().getBleedBossCounterNpcIds();
            default -> readParameterizedArray(sectionId);
        };
    }

    private static String[] readParameterizedArray(String sectionId) {
        if (sectionId.startsWith("loot.")) return lootInjectionEntries(sectionId.substring("loot.".length()));
        if (sectionId.startsWith("affinity.rules.")) return affinityRuleEntries(sectionId.substring("affinity.rules.".length()));
        if (sectionId.startsWith("affinity.values.")) return affinityMultiplierEntries(sectionId.substring("affinity.values.".length()));
        return new String[0];
    }

    private static void writeArray(String sectionId, String[] values) {
        String[] safe = values == null ? new String[0] : values;
        switch (sectionId) {
            case "socket.resonance_mappings" -> socketConfig().setResonanceClassMappings(safe);
            case "socket.resonance_class_hints" -> socketConfig().setResonanceWeaponClassHints(safe);
            case "socket.clockwork_ammo_hints" -> socketConfig().setClockworkAmmoItemHints(safe);
            case "refinement.support_materials" -> refinementConfig().setSupportMaterialEntries(safe);
            case "lore_mapping.gem_colors" -> loreMappingConfig().setGemColorEntries(safe);
            case "lore_mapping.core_colors" -> loreMappingConfig().setCoreColorEntries(safe);
            case "lore_mapping.color_spirits" -> loreMappingConfig().setColorSpiritEntries(safe);
            case "lore_mapping.abilities" -> loreMappingConfig().setAbilityEntries(safe);
            case "lore.status_reapply" -> loreConfig().setStatusBossReapplyRules(safe);
            case "lore.status_resistance" -> loreConfig().setNpcStatusResistanceEntries(safe);
            case "lore.status_counters" -> loreConfig().setStatusBossCounterNpcIds(safe);
            case "lore.bleed_counters" -> loreConfig().setBleedBossCounterNpcIds(safe);
            default -> writeParameterizedArray(sectionId, safe);
        }
    }

    private static void writeParameterizedArray(String sectionId, String[] values) {
        if (sectionId.startsWith("loot.")) setLootInjectionEntries(sectionId.substring("loot.".length()), values);
        else if (sectionId.startsWith("affinity.rules.")) setAffinityRuleEntries(sectionId.substring("affinity.rules.".length()), values);
        else if (sectionId.startsWith("affinity.values.")) setAffinityMultiplierEntries(sectionId.substring("affinity.values.".length()), values);
    }

    private static String arrayDraftKey(String sectionId, int index) {
        return "array_" + sectionId.replace('.', '_') + "_" + index;
    }

    /** Returns table columns for arrays whose entries contain more than one setting. */
    private static List<String> structuredArrayLabels(String sectionId, List<String> entries) {
        if (sectionId == null) return null;
        return switch (sectionId) {
            case "socket.resonance_mappings" -> List.of("Equipment name", "Resonance classes");
            case "socket.resonance_class_hints" -> List.of("Weapon class", "Item hints");
            case "socket.clockwork_ammo_hints" -> List.of("Ammo item hint");
            case "refinement.support_materials" -> List.of("Item ID", "Break multiplier", "Durability loss",
                    "Anti-degrade chance", "Consume mode", "Upgrade guarantee", "Jackpot guarantee",
                    "Upgrade weight", "Jackpot weight", "Description");
            case "lore_mapping.gem_colors", "lore_mapping.core_colors" -> List.of("Mapping key", "Color");
            case "lore_mapping.color_spirits" -> List.of("Lore color", "Spirit IDs");
            case "lore_mapping.abilities" -> List.of("Spirit", "Trigger", "Chance", "Cooldown (ms)",
                    "Effect", "Base amount", "Per-level amount");
            case "lore.status_reapply" -> List.of("Status", "Reapply step", "Pattern");
            case "lore.status_resistance" -> List.of("NPC ID", "Status 1", "Value 1", "Status 2", "Value 2",
                    "Status 3", "Value 3", "Status 4", "Value 4", "Status 5", "Value 5", "More pairs (| separated)");
            case "lore.status_counters" -> List.of("NPC ID", "Status 1", "Status 2", "Status 3", "Status 4",
                    "Status 5", "Status 6", "Status 7", "Status 8", "Status 9", "Status 10", "More statuses (| separated)");
            case "lore.bleed_counters" -> List.of("NPC ID");
            case "affinity.values." + GROUP_AFFINITY_MULTIPLIERS -> List.of("Mode", "Target", "Fire", "Water", "Ice",
                    "Lightning", "Life", "Void");
            case "affinity.values." + GROUP_AFFINITY_SHIELDS -> List.of("Mode", "Target", "Shield", "Delay", "Rate", "Duration");
            default -> {
                if (sectionId.startsWith("loot.")) {
                    yield isNpcLootInjectionGroup(sectionId.substring("loot.".length()))
                            ? List.of("Item ID", "Chance", "Min", "Max", "NPC target ID")
                            : List.of("Item ID", "Chance", "Min", "Max");
                }
                if (sectionId.startsWith("affinity.rules.")) yield List.of("Mode", "Target", "Element");
                yield null;
            }
        };
    }

    private static boolean isAffinityStructuredModeSection(String sectionId) {
        return sectionId != null && (sectionId.startsWith("affinity.rules.")
                || sectionId.equals("affinity.values." + GROUP_AFFINITY_MULTIPLIERS)
                || sectionId.equals("affinity.values." + GROUP_AFFINITY_SHIELDS));
    }

    private static int appendColorSpiritMappings(
            UICommandBuilder commands,
            UIEventBuilder events,
            List<String> entries,
            int cardIndex) {
        for (int rowIndex = 0; rowIndex < entries.size(); rowIndex++) {
            ColorSpiritMapping mapping = parseColorSpiritMapping(entries.get(rowIndex));
            String rowTarget = "lore_mapping.color_spirits:" + rowIndex;
            commands.append("#RuntimeConfigCards", COLOR_SPIRIT_HEADER_UI);
            String headerBase = "#RuntimeConfigCards[" + cardIndex++ + "]";
            String colorInput = headerBase + " #ColorSpiritName";
            commands.set(colorInput + ".Value", mapping.color());
            events.addEventBinding(CustomUIEventBindingType.ValueChanged, colorInput,
                    EventData.of("Action", "color_spirit_value").append("Target", rowTarget)
                            .append("@Text", colorInput + ".Value"), false);
            events.addEventBinding(CustomUIEventBindingType.Activating, headerBase + " #ColorSpiritAddNpc",
                    EventData.of("Action", "color_spirit_add_npc").append("Target", rowTarget));
            events.addEventBinding(CustomUIEventBindingType.Activating, headerBase + " #ColorSpiritRemove",
                    EventData.of("Action", "array_remove").append("Target", rowTarget));

            for (int npcIndex = 0; npcIndex < mapping.npcIds().size(); npcIndex++) {
                commands.append("#RuntimeConfigCards", COLOR_SPIRIT_NPC_UI);
                String npcBase = "#RuntimeConfigCards[" + cardIndex++ + "]";
                String npcTarget = rowTarget + ":" + npcIndex;
                String npcInput = npcBase + " #ColorSpiritNpcId";
                commands.set(npcInput + ".Value", mapping.npcIds().get(npcIndex));
                events.addEventBinding(CustomUIEventBindingType.ValueChanged, npcInput,
                        EventData.of("Action", "color_spirit_npc_value").append("Target", npcTarget)
                                .append("@Text", npcInput + ".Value"), false);
                events.addEventBinding(CustomUIEventBindingType.Activating, npcBase + " #ColorSpiritNpcRemove",
                        EventData.of("Action", "color_spirit_remove_npc").append("Target", npcTarget));
            }
        }
        return cardIndex;
    }

    private static ColorSpiritMapping parseColorSpiritMapping(String raw) {
        if (raw == null) return new ColorSpiritMapping("", new ArrayList<>());
        String[] keyValue = raw.split("=", 2);
        String color = keyValue[0].trim();
        List<String> npcIds = new ArrayList<>();
        if (keyValue.length > 1 && !keyValue[1].isEmpty()) {
            for (String npcId : keyValue[1].split(",", -1)) npcIds.add(npcId.trim());
        }
        return new ColorSpiritMapping(color, npcIds);
    }

    private static String formatColorSpiritMapping(ColorSpiritMapping mapping) {
        return mapping.color().trim() + "=" + String.join(",", mapping.npcIds());
    }

    private static void setColorSpiritDraft(ViewState state, String target, String value, boolean npcField) {
        if (state == null || target == null) return;
        int npcSeparator = target.lastIndexOf(':');
        int rowSeparator = npcField && npcSeparator > 0 ? target.lastIndexOf(':', npcSeparator - 1) : npcSeparator;
        if (rowSeparator <= 0) return;
        String sectionId = target.substring(0, rowSeparator);
        int rowIndex = parseIntSafe(target.substring(rowSeparator + 1, npcField ? npcSeparator : target.length()), -1);
        int npcIndex = npcField ? parseIntSafe(target.substring(npcSeparator + 1), -1) : -1;
        List<String> entries = state.arrayDrafts.computeIfAbsent(sectionId, ignored -> mutableList(readArray(sectionId)));
        if (rowIndex < 0 || rowIndex >= entries.size()) return;
        ColorSpiritMapping mapping = parseColorSpiritMapping(entries.get(rowIndex));
        if (npcField) {
            if (npcIndex < 0 || npcIndex >= mapping.npcIds().size()) return;
            mapping.npcIds().set(npcIndex, value == null ? "" : value.trim());
        } else {
            mapping = new ColorSpiritMapping(value == null ? "" : value.trim(), mapping.npcIds());
        }
        entries.set(rowIndex, formatColorSpiritMapping(mapping));
    }

    private static void addColorSpiritNpcDraft(ViewState state, String target) {
        if (state == null || target == null) return;
        int separator = target.lastIndexOf(':');
        if (separator <= 0) return;
        String sectionId = target.substring(0, separator);
        int rowIndex = parseIntSafe(target.substring(separator + 1), -1);
        List<String> entries = state.arrayDrafts.computeIfAbsent(sectionId, ignored -> mutableList(readArray(sectionId)));
        if (rowIndex < 0 || rowIndex >= entries.size()) return;
        ColorSpiritMapping mapping = parseColorSpiritMapping(entries.get(rowIndex));
        mapping.npcIds().add("NPC_ID");
        entries.set(rowIndex, formatColorSpiritMapping(mapping));
    }

    private static void removeColorSpiritNpcDraft(ViewState state, String target) {
        if (state == null || target == null) return;
        int npcSeparator = target.lastIndexOf(':');
        int rowSeparator = npcSeparator <= 0 ? -1 : target.lastIndexOf(':', npcSeparator - 1);
        if (rowSeparator <= 0) return;
        String sectionId = target.substring(0, rowSeparator);
        int rowIndex = parseIntSafe(target.substring(rowSeparator + 1, npcSeparator), -1);
        int npcIndex = parseIntSafe(target.substring(npcSeparator + 1), -1);
        List<String> entries = state.arrayDrafts.computeIfAbsent(sectionId, ignored -> mutableList(readArray(sectionId)));
        if (rowIndex < 0 || rowIndex >= entries.size()) return;
        ColorSpiritMapping mapping = parseColorSpiritMapping(entries.get(rowIndex));
        if (npcIndex < 0 || npcIndex >= mapping.npcIds().size()) return;
        mapping.npcIds().remove(npcIndex);
        entries.set(rowIndex, formatColorSpiritMapping(mapping));
    }

    private static List<String> structuredArrayFields(String sectionId, String raw, int fieldCount) {
        List<String> fields = new ArrayList<>(Collections.nCopies(fieldCount, ""));
        if (sectionId == null || fieldCount == 0) return fields;
        String value = raw == null ? "" : raw.trim();
        switch (sectionId) {
            case "socket.resonance_mappings" -> {
                ResonanceMappingEntry entry = parseResonanceMappingEntry(value);
                putFields(fields, entry.name(), entry.classes());
            }
            case "socket.resonance_class_hints" -> {
                ResonanceHintEntry entry = parseResonanceHintEntry(value);
                putFields(fields, entry.weaponClass(), entry.hints());
            }
            case "socket.clockwork_ammo_hints", "lore.bleed_counters" -> putFields(fields, value);
            case "refinement.support_materials" -> {
                ReforgeSupportRuleEntry entry = parseReforgeSupportEntry(value);
                putFields(fields, entry.itemId(), entry.breakMultiplier(), entry.durabilityLoss(), entry.antiDegradeChance(),
                        entry.consumeMode(), entry.upgradeGuarantee(), entry.jackpotGuarantee(), entry.upgradeWeight(),
                        entry.jackpotWeight(), entry.description());
            }
            case "lore_mapping.gem_colors", "lore_mapping.core_colors", "lore_mapping.color_spirits" -> {
                KeyValue entry = parseKeyValue(value);
                putFields(fields, entry.key(), entry.value());
            }
            case "lore_mapping.abilities" -> {
                AbilityEntry entry = parseAbilityEntry(value);
                putFields(fields, entry.spirit(), entry.trigger(), entry.chance(), entry.cooldown(), entry.effect(),
                        entry.base(), entry.perLevel());
            }
            case "lore.status_reapply" -> {
                String[] parts = value.split(",", 2);
                KeyValue entry = parseKeyValue(parts[0]);
                putFields(fields, entry.key(), entry.value(), parts.length > 1 ? parts[1].trim() : "LINEAR");
            }
            case "lore.status_resistance" -> {
                String[] parts = value.split("\\|", -1);
                if (parts.length > 0) fields.set(0, parts[0].trim());
                int pairCount = Math.min(5, Math.max(0, parts.length - 1));
                for (int i = 0; i < pairCount; i++) {
                    KeyValue pair = parseKeyValue(parts[i + 1]);
                    fields.set(1 + i * 2, pair.key());
                    fields.set(2 + i * 2, pair.value());
                }
                if (parts.length > 6) fields.set(11, String.join("|", Arrays.copyOfRange(parts, 6, parts.length)));
            }
            case "lore.status_counters" -> {
                String[] parts = value.split("\\|", -1);
                if (parts.length > 0) fields.set(0, parts[0].trim());
                if (parts.length == 1 && fieldCount > 1) fields.set(1, "*");
                int statusCount = Math.min(10, Math.max(0, parts.length - 1));
                for (int i = 0; i < statusCount; i++) fields.set(i + 1, parts[i + 1].trim());
                if (parts.length > 11) fields.set(11, String.join("|", Arrays.copyOfRange(parts, 11, parts.length)));
            }
            default -> {
                if (sectionId.startsWith("loot.")) {
                    LootRuleEntry entry = parseLootRuleEntry(value);
                    putFields(fields, entry.itemId(), entry.chance(), entry.min(), entry.max());
                    if (fieldCount > 4) fields.set(4, entry.targetId());
                } else if (sectionId.startsWith("affinity.rules.")) {
                    AffinityRuleEntry entry = parseAffinityRuleEntry(value);
                    putFields(fields, entry.mode(), entry.target(), entry.element());
                } else if (sectionId.equals("affinity.values." + GROUP_AFFINITY_MULTIPLIERS)) {
                    AffinityMultiplierEntry entry = parseAffinityMultiplierEntry(value);
                    putFields(fields, entry.mode(), entry.target());
                    for (int i = 0; i < AFFINITY_ELEMENTS.length && i + 2 < fieldCount; i++) {
                        fields.set(i + 2, entry.multipliers().getOrDefault(AFFINITY_ELEMENTS[i], ""));
                    }
                } else if (sectionId.equals("affinity.values." + GROUP_AFFINITY_SHIELDS)) {
                    AffinityShieldEntry entry = parseAffinityShieldEntry(value);
                    putFields(fields, entry.mode(), entry.target(), entry.shield(), entry.delay(), entry.rate(), entry.duration());
                }
            }
        }
        return fields;
    }

    private static void putFields(List<String> target, String... values) {
        for (int i = 0; i < Math.min(target.size(), values.length); i++) {
            target.set(i, values[i] == null ? "" : values[i]);
        }
    }

    private static String formatStructuredArrayFields(String sectionId, List<String> fields) {
        if (sectionId == null || fields == null) return "";
        switch (sectionId) {
            case "socket.resonance_mappings", "lore_mapping.gem_colors", "lore_mapping.core_colors",
                    "lore_mapping.color_spirits" -> { return field(fields, 0) + "=" + field(fields, 1); }
            case "socket.resonance_class_hints" -> { return field(fields, 0) + "=" + field(fields, 1); }
            case "socket.clockwork_ammo_hints", "lore.bleed_counters" -> { return field(fields, 0); }
            case "refinement.support_materials" -> {
                return field(fields, 0) + "=" + String.join(",", fields.subList(1, Math.min(10, fields.size())));
            }
            case "lore_mapping.abilities" -> {
                String prefix = field(fields, 0) + "=" + field(fields, 1);
                if (isSignatureAbility(field(fields, 1))) return prefix;
                return prefix + "," + String.join(",", fields.subList(2, Math.min(7, fields.size())));
            }
            case "lore.status_reapply" -> { return field(fields, 0) + "=" + field(fields, 1) + ", " + field(fields, 2); }
            case "lore.status_resistance" -> {
                List<String> parts = new ArrayList<>();
                parts.add(field(fields, 0));
                for (int i = 1; i <= 9; i += 2) {
                    if (!field(fields, i).isBlank() || !field(fields, i + 1).isBlank()) {
                        parts.add(field(fields, i) + "=" + field(fields, i + 1));
                    }
                }
                appendPipeParts(parts, field(fields, 11));
                return String.join("|", parts);
            }
            case "lore.status_counters" -> {
                List<String> parts = new ArrayList<>();
                parts.add(field(fields, 0));
                for (int i = 1; i <= 10; i++) if (!field(fields, i).isBlank()) parts.add(field(fields, i));
                appendPipeParts(parts, field(fields, 11));
                return String.join("|", parts);
            }
            default -> {
                if (sectionId.startsWith("loot.")) {
                    String formatted = field(fields, 0) + "=" + field(fields, 1) + "," + field(fields, 2) + "," + field(fields, 3);
                    return field(fields, 4).isBlank() ? formatted : formatted + "," + field(fields, 4);
                }
                if (sectionId.startsWith("affinity.rules.")) {
                    return field(fields, 0) + ":" + field(fields, 1) + "=" + field(fields, 2);
                }
                if (sectionId.equals("affinity.values." + GROUP_AFFINITY_MULTIPLIERS)) {
                    List<String> values = new ArrayList<>();
                    for (int i = 0; i < AFFINITY_ELEMENTS.length && i + 2 < fields.size(); i++) {
                        if (!field(fields, i + 2).isBlank()) values.add(AFFINITY_ELEMENTS[i] + ":" + field(fields, i + 2));
                    }
                    return field(fields, 0) + ":" + field(fields, 1) + "=" + String.join(",", values);
                }
                if (sectionId.equals("affinity.values." + GROUP_AFFINITY_SHIELDS)) {
                    return field(fields, 0) + ":" + field(fields, 1) + "=" + field(fields, 2) + ","
                            + field(fields, 3) + "," + field(fields, 4) + "," + field(fields, 5);
                }
            }
        }
        return String.join(",", fields);
    }

    private static String field(List<String> fields, int index) {
        return fields != null && index >= 0 && index < fields.size() && fields.get(index) != null ? fields.get(index).trim() : "";
    }

    private static void appendPipeParts(List<String> parts, String raw) {
        if (raw == null || raw.isBlank()) return;
        for (String part : raw.split("\\|")) if (!part.isBlank()) parts.add(part.trim());
    }

    private static void setStructuredArrayDraft(ViewState state, String target, String value) {
        if (state == null || target == null) return;
        int fieldCountSeparator = target.lastIndexOf(':');
        int fieldSeparator = fieldCountSeparator <= 0 ? -1 : target.lastIndexOf(':', fieldCountSeparator - 1);
        int rowSeparator = fieldSeparator <= 0 ? -1 : target.lastIndexOf(':', fieldSeparator - 1);
        if (rowSeparator <= 0) return;
        String sectionId = target.substring(0, rowSeparator);
        int row = parseIntSafe(target.substring(rowSeparator + 1, fieldSeparator), -1);
        int fieldIndex = parseIntSafe(target.substring(fieldSeparator + 1, fieldCountSeparator), -1);
        int fieldCount = parseIntSafe(target.substring(fieldCountSeparator + 1), -1);
        if (row < 0 || fieldIndex < 0 || fieldCount <= fieldIndex || fieldCount > MAX_STRUCTURED_ARRAY_FIELDS) return;
        List<String> entries = state.arrayDrafts.computeIfAbsent(sectionId, ignored -> mutableList(readArray(sectionId)));
        if (row >= entries.size()) return;
        List<String> fields = structuredArrayFields(sectionId, entries.get(row), fieldCount);
        fields.set(fieldIndex, value == null ? "" : value);
        entries.set(row, formatStructuredArrayFields(sectionId, fields));
    }

    private static void setArrayDraft(ViewState state, String target, String value) {
        int separator = target == null ? -1 : target.lastIndexOf(':');
        if (separator <= 0) return;
        String sectionId = target.substring(0, separator);
        int index = parseIntSafe(target.substring(separator + 1), -1);
        List<String> entries = state.arrayDrafts.computeIfAbsent(sectionId, ignored -> mutableList(readArray(sectionId)));
        if (index >= 0 && index < entries.size()) {
            entries.set(index, value == null ? "" : value);
        }
    }

    private static void mutateArray(ViewState state, String sectionId, int index, String value) {
        if (sectionId == null) return;
        List<String> entries = state.arrayDrafts.computeIfAbsent(sectionId, ignored -> mutableList(readArray(sectionId)));
        if (index < 0) entries.add(value == null ? "" : value);
        else if (index < entries.size()) entries.remove(index);
    }

    private static int applyNativeArrayInputs(ViewState state, ControlGroup group, List<String> configsToSave) {
        if (state == null || group == null) return 0;
        int changed = 0;
        for (ArraySection section : arraySections(group.id)) {
            List<String> draft = state.arrayDrafts.get(section.id());
            if (draft == null) continue;
            List<String> cleaned = new ArrayList<>();
            for (String value : draft) if (value != null && !value.isBlank()) cleaned.add(value.trim());
            String[] before = readArray(section.id());
            String[] after = cleaned.toArray(String[]::new);
            if (!Arrays.equals(before, after)) {
                writeArray(section.id(), after);
                addUniqueConfig(configsToSave, section.configName());
                changed++;
            }
        }
        return changed;
    }

    private static void saveGemColorEntries(List<String> entries) {
        loreMappingConfig().setGemColorEntries(entries.toArray(String[]::new));
        plugin.getConfigService().saveAndApply(LORE_MAPPING_CONFIG_NAME);
    }

    private static void saveColorSpiritEntries(List<String> entries) {
        loreMappingConfig().setColorSpiritEntries(entries.toArray(String[]::new));
        plugin.getConfigService().saveAndApply(LORE_MAPPING_CONFIG_NAME);
    }

    private static void saveAbilityEntries(List<String> entries) {
        loreMappingConfig().setAbilityEntries(entries.toArray(String[]::new));
        plugin.getConfigService().saveAndApply(LORE_MAPPING_CONFIG_NAME);
    }

    private static void saveStatusRuleEntries(List<StatusRuleEntry> entries) {
        loreConfig().setStatusBossReapplyRules(formatStatusRuleEntries(entries));
        plugin.getConfigService().saveAndApply(LORE_CONFIG_NAME);
    }

    private static void saveStatusResistanceEntries(List<StatusResistanceEntry> entries) {
        loreConfig().setNpcStatusResistanceEntries(formatStatusResistanceEntries(entries));
        plugin.getConfigService().saveAndApply(LORE_CONFIG_NAME);
    }

    private static void saveStatusCounterEntries(List<StatusCounterEntry> entries) {
        loreConfig().setStatusBossCounterNpcIds(formatStatusCounterEntries(entries));
        plugin.getConfigService().saveAndApply(LORE_CONFIG_NAME);
    }

    private static void saveBleedCounterEntries(List<String> entries) {
        loreConfig().setBleedBossCounterNpcIds(entries.toArray(String[]::new));
        plugin.getConfigService().saveAndApply(LORE_CONFIG_NAME);
    }

    private static String[] formatStatusRuleEntries(List<StatusRuleEntry> entries) {
        List<String> formatted = new ArrayList<>();
        for (StatusRuleEntry entry : entries) {
            if (entry != null && !entry.status().isBlank()) {
                formatted.add(entry.status() + "=" + entry.step() + ", " + entry.pattern());
            }
        }
        return formatted.toArray(String[]::new);
    }

    private static String[] formatStatusResistanceEntries(List<StatusResistanceEntry> entries) {
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (StatusResistanceEntry entry : entries) {
            if (entry == null || entry.npcId().isBlank() || entry.status().isBlank()) {
                continue;
            }
            grouped.computeIfAbsent(entry.npcId(), ignored -> new ArrayList<>())
                    .add(entry.status() + "=" + entry.value());
        }
        List<String> formatted = new ArrayList<>();
        for (Map.Entry<String, List<String>> group : grouped.entrySet()) {
            formatted.add(group.getKey() + "|" + String.join("|", group.getValue()));
        }
        return formatted.toArray(String[]::new);
    }

    private static String[] formatStatusCounterEntries(List<StatusCounterEntry> entries) {
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (StatusCounterEntry entry : entries) {
            if (entry == null || entry.npcId().isBlank() || entry.status().isBlank()) {
                continue;
            }
            grouped.computeIfAbsent(entry.npcId(), ignored -> new ArrayList<>()).add(entry.status());
        }
        List<String> formatted = new ArrayList<>();
        for (Map.Entry<String, List<String>> group : grouped.entrySet()) {
            formatted.add(group.getKey() + "|" + String.join("|", group.getValue()));
        }
        return formatted.toArray(String[]::new);
    }

    private static KeyValue parseKeyValue(String entry) {
        if (entry == null) {
            return new KeyValue("", "red");
        }
        int equals = entry.indexOf('=');
        if (equals < 0) {
            return new KeyValue(entry.trim(), "red");
        }
        String key = entry.substring(0, equals).trim();
        String value = entry.substring(equals + 1).trim();
        return new KeyValue(key, value);
    }

    private static AbilityEntry parseAbilityEntry(String entry) {
        KeyValue parsed = parseKeyValue(entry);
        String spirit = parsed.key().isBlank() ? "New_Spirit" : parsed.key();
        String[] parts = parsed.value().split(",");
        if (parts.length == 1 && isSignatureAbility(parts[0])) {
            return new AbilityEntry(spirit, normalizeAbilityToken(parts[0], "SIGNATURE_RAZORSTRIKE"),
                    "0.10", "2000", "DAMAGE_TARGET", "2.0", "0.10", true);
        }
        String trigger = parts.length > 0 ? normalizeAbilityToken(parts[0], "ON_HIT") : "ON_HIT";
        String chance = parts.length > 1 ? normalizeDecimalText(parts[1], "0.10", false) : "0.10";
        String cooldown = parts.length > 2 ? normalizeIntegerText(parts[2], "2000") : "2000";
        String effect = parts.length > 3 ? normalizeAbilityToken(parts[3], "DAMAGE_TARGET") : "DAMAGE_TARGET";
        String base = parts.length > 4 ? normalizeDecimalText(parts[4], "2.0", false) : "2.0";
        String perLevel = parts.length > 5 ? normalizeDecimalText(parts[5], "0.10", false) : "0.10";
        return new AbilityEntry(spirit, trigger, chance, cooldown, effect, base, perLevel, false);
    }

    private static String formatAbilityEntry(AbilityEntry ability) {
        if (ability == null) {
            return "New_Spirit=ON_HIT,0.10,2000,DAMAGE_TARGET,2.0,0.10";
        }
        if (ability.signature()) {
            return ability.spirit() + "=" + ability.trigger();
        }
        return ability.spirit() + "=" + ability.trigger() + "," + ability.chance() + "," + ability.cooldown()
                + "," + ability.effect() + "," + ability.base() + "," + ability.perLevel();
    }

    private static List<StatusRuleEntry> parseStatusRuleEntries(String[] rawEntries) {
        List<StatusRuleEntry> entries = new ArrayList<>();
        for (String raw : mutableList(rawEntries)) {
            String[] parts = raw.split(",", 2);
            KeyValue parsed = parseKeyValue(parts[0]);
            String status = normalizeStatusKeyForUi(parsed.key());
            String step = normalizeIntegerText(parsed.value(), "5");
            String pattern = parts.length > 1 ? normalizeStatusPattern(parts[1]) : "LINEAR";
            entries.add(new StatusRuleEntry(status, step, pattern));
        }
        return entries;
    }

    private static List<StatusResistanceEntry> parseStatusResistanceEntries(String[] rawEntries) {
        List<StatusResistanceEntry> entries = new ArrayList<>();
        for (String raw : mutableList(rawEntries)) {
            String[] parts = raw.split("\\|");
            if (parts.length == 0) {
                continue;
            }
            String npcId = normalizeNpcIdForUi(parts[0]);
            for (int i = 1; i < parts.length; i++) {
                KeyValue parsed = parseKeyValue(parts[i]);
                entries.add(new StatusResistanceEntry(
                        npcId,
                        normalizeStatusKeyForUi(parsed.key()),
                        normalizeDecimalText(parsed.value(), "1.0", true)));
            }
        }
        return entries;
    }

    private static List<StatusCounterEntry> parseStatusCounterEntries(String[] rawEntries) {
        List<StatusCounterEntry> entries = new ArrayList<>();
        for (String raw : mutableList(rawEntries)) {
            String[] parts = raw.split("\\|");
            if (parts.length == 0) {
                continue;
            }
            String npcId = normalizeNpcIdForUi(parts[0]);
            if (parts.length == 1) {
                entries.add(new StatusCounterEntry(npcId, "*"));
                continue;
            }
            for (int i = 1; i < parts.length; i++) {
                entries.add(new StatusCounterEntry(npcId, normalizeStatusKeyForUi(parts[i])));
            }
        }
        return entries;
    }

    private static String normalizeLoreColor(String color) {
        String normalized = color == null ? "" : color.trim().toLowerCase(Locale.ROOT);
        for (String known : LORE_COLORS) {
            if (known.equals(normalized)) {
                return known;
            }
        }
        return "red";
    }

    private static List<String> allSpiritIds() {
        List<String> spirits = new ArrayList<>();
        String[] entries = loreMappingConfig().getAbilityEntries();
        if (entries != null) {
            for (String entry : entries) {
                KeyValue parsed = parseKeyValue(entry);
                if (!parsed.key().isBlank() && !spirits.contains(parsed.key())) {
                    spirits.add(parsed.key());
                }
            }
        }
        String[] colorEntries = loreMappingConfig().getColorSpiritEntries();
        if (colorEntries != null) {
            for (String entry : colorEntries) {
                KeyValue parsed = parseKeyValue(entry);
                for (String spirit : parsed.value().split(",")) {
                    String trimmed = spirit.trim();
                    if (!trimmed.isBlank() && !spirits.contains(trimmed)) {
                        spirits.add(trimmed);
                    }
                }
            }
        }
        spirits.sort(String.CASE_INSENSITIVE_ORDER);
        return spirits;
    }

    private static List<String> spiritsForColor(String color) {
        String normalizedColor = normalizeLoreColor(color);
        String[] entries = loreMappingConfig().getColorSpiritEntries();
        if (entries == null) {
            return new ArrayList<>();
        }
        for (String entry : entries) {
            KeyValue parsed = parseKeyValue(entry);
            if (normalizedColor.equals(normalizeLoreColor(parsed.key()))) {
                List<String> spirits = new ArrayList<>();
                for (String spirit : parsed.value().split(",")) {
                    String trimmed = spirit.trim();
                    if (!trimmed.isBlank()) {
                        spirits.add(trimmed);
                    }
                }
                return spirits;
            }
        }
        return new ArrayList<>();
    }

    private static void setSpiritsForColor(String color, List<String> spirits) {
        List<String> entries = mutableList(loreMappingConfig().getColorSpiritEntries());
        putSpiritsForColor(entries, color, spirits);
        saveColorSpiritEntries(entries);
    }

    private static void putSpiritsForColor(List<String> entries, String color, List<String> spirits) {
        if (entries == null) {
            return;
        }
        String normalizedColor = normalizeLoreColor(color);
        String joined = joinCommaList(spirits);
        boolean updated = false;
        for (int i = 0; i < entries.size(); i++) {
            KeyValue parsed = parseKeyValue(entries.get(i));
            if (normalizedColor.equals(normalizeLoreColor(parsed.key()))) {
                if (joined.isBlank()) {
                    entries.remove(i);
                } else {
                    entries.set(i, normalizedColor + "=" + joined);
                }
                updated = true;
                break;
            }
        }
        if (!updated && !joined.isBlank()) {
            entries.add(normalizedColor + "=" + joined);
        }
    }

    private static String joinCommaList(List<String> values) {
        StringBuilder sb = new StringBuilder();
        if (values != null) {
            for (String value : values) {
                if (value == null || value.isBlank()) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(value.trim());
            }
        }
        return sb.toString();
    }

    private static String firstAvailableSpirit(String color) {
        List<String> existing = spiritsForColor(color);
        for (String spirit : allSpiritIds()) {
            if (!existing.contains(spirit)) {
                return spirit;
            }
        }
        int suffix = 1;
        String candidate = "NPC_ID";
        while (existing.contains(candidate)) {
            suffix++;
            candidate = "NPC_ID_" + suffix;
        }
        return candidate;
    }

    private static String localizedLoreColor(Player player, String color) {
        String normalized = normalizeLoreColor(color);
        String fallback = normalized.substring(0, 1).toUpperCase(Locale.ROOT) + normalized.substring(1);
        return tOrFallback(player, "ui.runtime_config.lore_color." + normalized, fallback);
    }

    private static boolean isCoreLoreColor(String color) {
        String normalizedColor = normalizeLoreColor(color);
        String[] colors = loreMappingConfig().getCoreColorEntries();
        if (colors == null) {
            return false;
        }
        for (String entry : colors) {
            if (normalizedColor.equals(normalizeLoreColor(entry))) {
                return true;
            }
        }
        return false;
    }

    private static String loreColorHex(String color) {
        return switch (normalizeLoreColor(color)) {
            case "black" -> "#9B5CFF";
            case "blue" -> "#66A8FF";
            case "cyan" -> "#54F0FF";
            case "green" -> "#73E86A";
            case "white" -> "#FFFFFF";
            case "yellow" -> "#FFE45C";
            default -> "#FF6A5C";
        };
    }

    private static String normalizeAbilityToken(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean isSignatureAbility(String raw) {
        String normalized = normalizeAbilityToken(raw, "");
        for (String signature : LORE_SIGNATURES) {
            if (signature.equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeStatusKeyForUi(String raw) {
        if (raw == null || raw.isBlank()) {
            return "bleed";
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "all", "any" -> "*";
            case "apply_bleed" -> "bleed";
            case "apply_burn", "fire" -> "burn";
            case "apply_poison", "toxic" -> "poison";
            case "frozen", "apply_freeze" -> "freeze";
            case "apply_shock" -> "shock";
            case "slowness", "apply_slow" -> "slow";
            case "weak", "apply_weakness" -> "weakness";
            case "blindness", "apply_blind" -> "blind";
            case "apply_root" -> "root";
            case "apply_stun" -> "stun";
            case "apply_fear" -> "fear";
            case "drain_life", "life_drain", "lifedrain" -> "drain";
            default -> containsOption(LORE_STATUS_KEYS, normalized) ? normalized : "bleed";
        };
    }

    private static String normalizeStatusPattern(String raw) {
        String normalized = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        return "FIBONACCI".equals(normalized) ? "FIBONACCI" : "LINEAR";
    }

    private static String normalizeNpcIdForUi(String raw) {
        String value = raw == null ? "" : raw.trim();
        return value.isBlank() ? "NPC_ID" : value;
    }

    private static String normalizeIntegerText(String raw, String fallback) {
        Double parsed = extractFirstNumber(raw);
        if (parsed == null) {
            return fallback;
        }
        return String.valueOf(Math.max(0, (int) Math.round(parsed)));
    }

    private static String normalizeDecimalText(String raw, String fallback, boolean chanceLike) {
        Double parsed = extractFirstNumber(raw);
        if (parsed == null) {
            return fallback;
        }
        double value = parsed;
        if (chanceLike && value > 1.0d) {
            value = value / 100.0d;
        }
        if (chanceLike) {
            value = clampChance(value);
        }
        return String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", ".0");
    }

    private static String normalizeLootChanceText(String raw, String fallback) {
        Double parsed = extractFirstNumber(raw);
        if (parsed == null) {
            return fallback;
        }
        double value = parsed;
        if (value > 1.0d) {
            value = value / 100.0d;
        }
        value = clampChance(value);
        return formatPreciseChance(value);
    }

    private static String formatPreciseChance(double value) {
        return String.format(Locale.ROOT, "%.8f", value)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", ".0");
    }

    private static String normalizeAffinityMultiplierText(String raw, String fallback) {
        Double parsed = extractFirstNumber(raw);
        if (parsed == null) {
            return fallback;
        }
        double value = clamp(parsed, 0.0d, 10.0d);
        return String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", ".0");
    }

    private static String normalizeAffinityShieldText(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
            String inner = trimmed.substring(1, trimmed.length() - 1);
            String[] parts = inner.split(",");
            if (parts.length == 2) {
                Double min = extractFirstNumber(parts[0]);
                Double max = extractFirstNumber(parts[1]);
                if (min != null && max != null) {
                    double lo = Math.min(min, max);
                    double hi = Math.max(min, max);
                    lo = Math.max(0.0d, Math.min(1_000_000.0d, lo));
                    hi = Math.max(0.0d, Math.min(1_000_000.0d, hi));
                    return "(" + formatShieldNumber(lo) + "," + formatShieldNumber(hi) + ")";
                }
            }
            return fallback;
        }
        Double parsed = extractFirstNumber(trimmed);
        if (parsed == null) {
            return fallback;
        }
        double value = clamp(parsed, 0.0d, 1_000_000.0d);
        return formatShieldNumber(value);
    }

    private static String formatShieldNumber(double value) {
        return String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", ".0");
    }

    private static String normalizeAffinityShieldNumberText(String raw, String fallback) {
        Double parsed = extractFirstNumber(raw);
        if (parsed == null) {
            return fallback;
        }
        double value = Math.max(0.0d, parsed);
        return String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", ".0");
    }

    private static String normalizeAffinityValueText(String groupId, String raw) {
        return GROUP_AFFINITY_SHIELDS.equals(groupId)
                ? normalizeAffinityShieldText(raw, "0.0")
                : normalizeAffinityMultiplierText(raw, "1.0");
    }

    private static String normalizeBoundedDecimalText(String raw, String fallback, double min, double max) {
        Double parsed = extractFirstNumber(raw);
        if (parsed == null) {
            return fallback;
        }
        double value = clamp(parsed, min, max);
        return String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", ".0");
    }

    private static String normalizeReforgeSupportMode(String raw) {
        String normalized = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "ON_USE", "USE", "ALWAYS" -> "ON_USE";
            case "ON_BLOCK", "BLOCK", "ANTI_DEGRADE" -> "ON_BLOCK";
            default -> "NEVER";
        };
    }

    private static ReforgeSupportRuleEntry parseReforgeSupportEntry(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ReforgeSupportRuleEntry("", "1.0", "0.0", "0.0", "NEVER", "0.0", "0.0", "1.0", "1.0", "");
        }
        String[] split = raw.trim().split("=", 2);
        String itemId = split[0].trim();
        String[] params = split.length > 1 ? split[1].split(",", 9) : new String[0];
        String breakMultiplier = params.length > 0 ? normalizeBoundedDecimalText(params[0], "1.0", 0.0d, 10.0d) : "1.0";
        String durabilityLoss = params.length > 1 ? normalizeBoundedDecimalText(params[1], "0.0", 0.0d, 1.0d) : "0.0";
        String antiDegradeChance = params.length > 2 ? normalizeBoundedDecimalText(params[2], "0.0", 0.0d, 1.0d) : "0.0";
        String consumeMode = params.length > 3 ? normalizeReforgeSupportMode(params[3]) : "NEVER";
        boolean oldDescriptionFormat = params.length > 4 && extractFirstNumber(params[4]) == null;
        String upgradeGuarantee = !oldDescriptionFormat && params.length > 4 ? normalizeBoundedDecimalText(params[4], "0.0", 0.0d, 1.0d) : "0.0";
        String jackpotGuarantee = !oldDescriptionFormat && params.length > 5 ? normalizeBoundedDecimalText(params[5], "0.0", 0.0d, 1.0d) : "0.0";
        String upgradeWeight = !oldDescriptionFormat && params.length > 6 ? normalizeBoundedDecimalText(params[6], "1.0", 0.0d, 10.0d) : "1.0";
        String jackpotWeight = !oldDescriptionFormat && params.length > 7 ? normalizeBoundedDecimalText(params[7], "1.0", 0.0d, 10.0d) : "1.0";
        String description = oldDescriptionFormat ? params[4].trim() : params.length > 8 ? params[8].trim() : "";
        return new ReforgeSupportRuleEntry(itemId, breakMultiplier, durabilityLoss, antiDegradeChance, consumeMode,
                upgradeGuarantee, jackpotGuarantee, upgradeWeight, jackpotWeight, description);
    }

    private static ReforgeSupportRuleEntry readReforgeSupportEntryFromInputs(
            ViewState state,
            int index,
            ReforgeSupportRuleEntry fallback) {
        String itemId = draftValue(state, reforgeSupportItemInputId(index), fallback.itemId()).trim();
        String breakMultiplier = normalizeBoundedDecimalText(
                draftValue(state, reforgeSupportBreakInputId(index), fallback.breakMultiplier()),
                fallback.breakMultiplier(),
                0.0d,
                10.0d);
        String durabilityLoss = normalizeBoundedDecimalText(
                draftValue(state, reforgeSupportDurabilityInputId(index), fallback.durabilityLoss()),
                fallback.durabilityLoss(),
                0.0d,
                1.0d);
        String antiDegradeChance = normalizeBoundedDecimalText(
                draftValue(state, reforgeSupportAntiInputId(index), fallback.antiDegradeChance()),
                fallback.antiDegradeChance(),
                0.0d,
                1.0d);
        String consumeMode = normalizeReforgeSupportMode(draftValue(state, reforgeSupportConsumeInputId(index), fallback.consumeMode()));
        String upgradeGuarantee = normalizeBoundedDecimalText(
                draftValue(state, reforgeSupportUpgradeGuaranteeInputId(index), fallback.upgradeGuarantee()),
                fallback.upgradeGuarantee(),
                0.0d,
                1.0d);
        String jackpotGuarantee = normalizeBoundedDecimalText(
                draftValue(state, reforgeSupportJackpotGuaranteeInputId(index), fallback.jackpotGuarantee()),
                fallback.jackpotGuarantee(),
                0.0d,
                1.0d);
        String upgradeWeight = normalizeBoundedDecimalText(
                draftValue(state, reforgeSupportUpgradeWeightInputId(index), fallback.upgradeWeight()),
                fallback.upgradeWeight(),
                0.0d,
                10.0d);
        String jackpotWeight = normalizeBoundedDecimalText(
                draftValue(state, reforgeSupportJackpotWeightInputId(index), fallback.jackpotWeight()),
                fallback.jackpotWeight(),
                0.0d,
                10.0d);
        String description = draftValue(state, reforgeSupportDescriptionInputId(index), fallback.description())
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim();
        return new ReforgeSupportRuleEntry(itemId, breakMultiplier, durabilityLoss, antiDegradeChance, consumeMode,
                upgradeGuarantee, jackpotGuarantee, upgradeWeight, jackpotWeight, description);
    }

    private static String formatReforgeSupportEntry(ReforgeSupportRuleEntry entry) {
        return (entry.itemId() == null ? "" : entry.itemId().trim())
                + "="
                + normalizeBoundedDecimalText(entry.breakMultiplier(), "1.0", 0.0d, 10.0d)
                + ","
                + normalizeBoundedDecimalText(entry.durabilityLoss(), "0.0", 0.0d, 1.0d)
                + ","
                + normalizeBoundedDecimalText(entry.antiDegradeChance(), "0.0", 0.0d, 1.0d)
                + ","
                + normalizeReforgeSupportMode(entry.consumeMode())
                + ","
                + normalizeBoundedDecimalText(entry.upgradeGuarantee(), "0.0", 0.0d, 1.0d)
                + ","
                + normalizeBoundedDecimalText(entry.jackpotGuarantee(), "0.0", 0.0d, 1.0d)
                + ","
                + normalizeBoundedDecimalText(entry.upgradeWeight(), "1.0", 0.0d, 10.0d)
                + ","
                + normalizeBoundedDecimalText(entry.jackpotWeight(), "1.0", 0.0d, 10.0d)
                + ","
                + (entry.description() == null ? "" : entry.description().replace('\n', ' ').replace('\r', ' ').trim());
    }

    private static void clearReforgeSupportDrafts(ViewState state, int index) {
        if (state == null) {
            return;
        }
        state.draftValues.remove(reforgeSupportItemInputId(index));
        state.draftValues.remove(reforgeSupportBreakInputId(index));
        state.draftValues.remove(reforgeSupportDurabilityInputId(index));
        state.draftValues.remove(reforgeSupportAntiInputId(index));
        state.draftValues.remove(reforgeSupportConsumeInputId(index));
        state.draftValues.remove(reforgeSupportUpgradeGuaranteeInputId(index));
        state.draftValues.remove(reforgeSupportJackpotGuaranteeInputId(index));
        state.draftValues.remove(reforgeSupportUpgradeWeightInputId(index));
        state.draftValues.remove(reforgeSupportJackpotWeightInputId(index));
        state.draftValues.remove(reforgeSupportDescriptionInputId(index));
    }

    private static boolean containsOption(String[] values, String selected) {
        if (values == null || selected == null) {
            return false;
        }
        for (String value : values) {
            if (selected.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAffinityMappingGroup(String groupId) {
        return isAffinityRuleGroup(groupId)
                || GROUP_AFFINITY_MULTIPLIERS.equals(groupId)
                || GROUP_AFFINITY_SHIELDS.equals(groupId);
    }

    private static boolean isAffinityRuleGroup(String groupId) {
        return GROUP_AFFINITY_ROLE_RULES.equals(groupId) || isCustomAffinityRuleGroup(groupId);
    }

    private static boolean isCustomAffinityRuleGroup(String groupId) {
        return GROUP_AFFINITY_CUSTOM_RULES.equals(groupId);
    }

    private static String[] affinityRuleEntries(String groupId) {
        return isCustomAffinityRuleGroup(groupId)
                ? elementalAffinityConfig().getCustomRoleAffinities()
                : elementalAffinityConfig().getRoleAffinities();
    }

    private static void setAffinityRuleEntries(String groupId, String[] entries) {
        if (isCustomAffinityRuleGroup(groupId)) {
            elementalAffinityConfig().setCustomRoleAffinities(entries);
        } else {
            elementalAffinityConfig().setRoleAffinities(entries);
        }
    }

    private static String[] affinityMultiplierEntries(String groupId) {
        return GROUP_AFFINITY_SHIELDS.equals(groupId)
                ? elementalAffinityConfig().getElementShields()
                : elementalAffinityConfig().getElementMultipliers();
    }

    private static void setAffinityMultiplierEntries(String groupId, String[] entries) {
        if (GROUP_AFFINITY_SHIELDS.equals(groupId)) {
            elementalAffinityConfig().setElementShields(entries);
        } else {
            elementalAffinityConfig().setElementMultipliers(entries);
        }
    }

    private static AffinityRuleEntry parseAffinityRuleEntry(String raw) {
        if (raw == null || raw.isBlank()) {
            return new AffinityRuleEntry("hint", "", AFFINITY_ELEMENTS[0]);
        }
        String[] split = raw.trim().split("=", 2);
        String key = split[0].trim();
        String element = split.length > 1 ? normalizeAffinityElement(split[1]) : AFFINITY_ELEMENTS[0];
        String mode = "hint";
        if (key.regionMatches(true, 0, "role:", 0, 5)) {
            mode = "role";
            key = key.substring(5).trim();
        } else if (key.regionMatches(true, 0, "id:", 0, 3)) {
            mode = "role";
            key = key.substring(3).trim();
        } else if (key.regionMatches(true, 0, "hint:", 0, 5)) {
            key = key.substring(5).trim();
        }
        return new AffinityRuleEntry(mode, key, element);
    }

    private static String formatAffinityRuleEntry(AffinityRuleEntry entry) {
        String mode = normalizeAffinityMode(entry.mode());
        String target = entry.target() == null ? "" : entry.target().trim();
        String element = normalizeAffinityElement(entry.element());
        return mode + ":" + target + "=" + element;
    }

    private static AffinityShieldEntry parseAffinityShieldEntry(String raw) {
        if (raw == null || raw.isBlank()) {
            return new AffinityShieldEntry("hint", "", "0.0", "8.0", "0.2", "5.0");
        }
        String[] split = raw.trim().split("=", 2);
        String key = split[0].trim();
        String valuesPart = split.length > 1 ? split[1] : "";
        String shield;
        String delay = "8.0";
        String rate = "0.2";
        String duration = "5.0";
        if (valuesPart.startsWith("(")) {
            int closeParen = valuesPart.indexOf(')');
            if (closeParen > 0) {
                shield = valuesPart.substring(0, closeParen + 1);
                String remaining = valuesPart.substring(closeParen + 1);
                if (remaining.startsWith(",")) {
                    String[] rest = remaining.substring(1).split(",");
                    delay = normalizeAffinityShieldNumberText(rest.length > 0 ? rest[0] : "", "8.0");
                    rate = normalizeAffinityShieldNumberText(rest.length > 1 ? rest[1] : "", "0.2");
                    duration = normalizeAffinityShieldNumberText(rest.length > 2 ? rest[2] : "", "5.0");
                }
            } else {
                shield = normalizeAffinityShieldText(valuesPart, "0.0");
            }
        } else {
            String[] values = valuesPart.split(",");
            shield = normalizeAffinityShieldText(values.length > 0 ? values[0] : "", "0.0");
            delay = normalizeAffinityShieldNumberText(values.length > 1 ? values[1] : "", "8.0");
            rate = normalizeAffinityShieldNumberText(values.length > 2 ? values[2] : "", "0.2");
            duration = normalizeAffinityShieldNumberText(values.length > 3 ? values[3] : "", "5.0");
        }
        shield = normalizeAffinityShieldText(shield, "0.0");
        String mode = "hint";
        if (key.regionMatches(true, 0, "role:", 0, 5)) {
            mode = "role";
            key = key.substring(5).trim();
        } else if (key.regionMatches(true, 0, "id:", 0, 3)) {
            mode = "role";
            key = key.substring(3).trim();
        } else if (key.regionMatches(true, 0, "hint:", 0, 5)) {
            key = key.substring(5).trim();
        }
        return new AffinityShieldEntry(mode, key, shield, delay, rate, duration);
    }

    private static String formatAffinityShieldEntry(AffinityShieldEntry entry) {
        return normalizeAffinityMode(entry.mode())
                + ":"
                + (entry.target() == null ? "" : entry.target().trim())
                + "="
                + normalizeAffinityShieldText(entry.shield(), "0.0")
                + ","
                + normalizeAffinityShieldNumberText(entry.delay(), "8.0")
                + ","
                + normalizeAffinityShieldNumberText(entry.rate(), "0.2")
                + ","
                + normalizeAffinityShieldNumberText(entry.duration(), "5.0");
    }

    private static AffinityMultiplierEntry parseAffinityMultiplierEntry(String raw) {
        AffinityRuleEntry key = parseAffinityRuleEntry(raw);
        Map<String, String> multipliers = new LinkedHashMap<>();
        if (raw != null && raw.contains("=")) {
            String values = raw.substring(raw.indexOf('=') + 1);
            for (String part : values.split(",")) {
                String[] pair = part.split(":", 2);
                if (pair.length != 2) {
                    continue;
                }
                String element = normalizeAffinityElement(pair[0]);
                multipliers.put(element, pair[1].trim());
            }
        }
        return new AffinityMultiplierEntry(key.mode(), key.target(), multipliers);
    }

    private static String formatAffinityMultiplierEntry(AffinityMultiplierEntry entry) {
        StringBuilder sb = new StringBuilder();
        sb.append(normalizeAffinityMode(entry.mode()))
                .append(":")
                .append(entry.target() == null ? "" : entry.target().trim())
                .append("=");
        boolean first = true;
        for (String element : AFFINITY_ELEMENTS) {
            String value = entry.multipliers().get(element);
            if (value == null || value.isBlank()) {
                continue;
            }
            if (!first) {
                sb.append(",");
            }
            sb.append(element).append(":").append(normalizeAffinityMultiplierText(value, "1.0"));
            first = false;
        }
        return sb.toString();
    }

    private static String formatAffinityMultiplierEntry(String groupId, AffinityMultiplierEntry entry) {
        StringBuilder sb = new StringBuilder();
        sb.append(normalizeAffinityMode(entry.mode()))
                .append(":")
                .append(entry.target() == null ? "" : entry.target().trim())
                .append("=");
        boolean first = true;
        for (String element : AFFINITY_ELEMENTS) {
            String value = entry.multipliers().get(element);
            if (value == null || value.isBlank()) {
                continue;
            }
            if (!first) {
                sb.append(",");
            }
            sb.append(element).append(":").append(normalizeAffinityValueText(groupId, value));
            first = false;
        }
        return sb.toString();
    }

    private static String[] resonanceMappingDisplayEntries() {
        String[] configured = socketConfig().getResonanceClassMappings();
        Map<String, String> merged = new LinkedHashMap<>();
        for (String entry : ResonanceSystem.getDefaultResonanceClassMappings()) {
            ResonanceMappingEntry parsed = parseResonanceMappingEntry(entry);
            if (!parsed.name().isBlank()) {
                merged.put(resonanceMappingKey(parsed.name()), formatResonanceMappingEntry(parsed));
            }
        }
        for (String entry : configured) {
            ResonanceMappingEntry parsed = parseResonanceMappingEntry(entry);
            if (!parsed.name().isBlank()) {
                merged.put(resonanceMappingKey(parsed.name()), formatResonanceMappingEntry(parsed));
            }
        }
        return merged.values().toArray(String[]::new);
    }

    private static String resonanceMappingKey(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private static ResonanceMappingEntry parseResonanceMappingEntry(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ResonanceMappingEntry("", "WEAPON");
        }
        String[] split = raw.split("=", 2);
        String name = split[0].trim();
        String classes = split.length > 1 ? normalizeResonanceClassText(split[1]) : "WEAPON";
        return new ResonanceMappingEntry(name, classes);
    }

    private static String formatResonanceMappingEntry(ResonanceMappingEntry entry) {
        return (entry.name() == null ? "" : entry.name().trim())
                + "="
                + normalizeResonanceClassText(entry.classes());
    }

    private static String normalizeResonanceClassText(String value) {
        if (value == null || value.isBlank()) {
            return "WEAPON";
        }
        List<String> tokens = new ArrayList<>();
        for (String part : value.split(",")) {
            String token = part == null ? "" : part.trim().toUpperCase(Locale.ROOT)
                    .replace('-', '_')
                    .replace(' ', '_');
            if (token.isBlank()) {
                continue;
            }
            if ("ANY".equals(token) || "ANY_WEAPON".equals(token) || "GENERIC".equals(token)) {
                token = "WEAPON";
            }
            if ("WEAPON".equals(token) || "ARMOR".equals(token)
                    || "SWORD".equals(token) || "AXE".equals(token)
                    || "MACE".equals(token) || "DAGGER".equals(token)
                    || "BOW".equals(token) || "CROSSBOW".equals(token)
                    || "STAFF".equals(token) || "GUN".equals(token)
                    || "GLAIVE".equals(token) || "KNUCKLE".equals(token)) {
                if (!tokens.contains(token)) {
                    tokens.add(token);
                }
            }
        }
        if (tokens.contains("ARMOR")) {
            return "ARMOR";
        }
        if (tokens.contains("WEAPON")) {
            return "WEAPON";
        }
        return tokens.isEmpty() ? "WEAPON" : String.join(",", tokens);
    }

    private static ResonanceHintEntry parseResonanceHintEntry(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ResonanceHintEntry("GUN", "");
        }
        String[] split = raw.split("=", 2);
        String clazz = normalizeResonanceHintClassText(split[0]);
        String hints = split.length > 1 ? normalizeHintTokenText(split[1]) : "";
        return new ResonanceHintEntry(clazz, hints);
    }

    private static String normalizeHintTokenText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        List<String> tokens = new ArrayList<>();
        for (String part : value.split(",")) {
            String token = part == null ? "" : part.trim().toLowerCase(Locale.ROOT);
            if (!token.isBlank() && !tokens.contains(token)) {
                tokens.add(token);
            }
        }
        return String.join(",", tokens);
    }

    private static String normalizeSingleHintToken(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeResonanceHintClassText(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        if ("SWORD".equals(normalized) || "AXE".equals(normalized)
                || "MACE".equals(normalized) || "DAGGER".equals(normalized)
                || "BOW".equals(normalized) || "CROSSBOW".equals(normalized)
                || "STAFF".equals(normalized) || "GUN".equals(normalized)
                || "GLAIVE".equals(normalized) || "KNUCKLE".equals(normalized)) {
            return normalized;
        }
        return "GUN";
    }

    private static String normalizeAffinityMode(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return "role".equals(normalized) || "id".equals(normalized) ? "role" : "hint";
    }

    private static String normalizeAffinityElement(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return containsOption(AFFINITY_ELEMENTS, normalized) ? normalized : AFFINITY_ELEMENTS[0];
    }

    private static void clearAffinityRuleDrafts(ViewState state, String groupId, int index) {
        if (state == null) {
            return;
        }
        state.draftValues.remove(affinityRuleModeSelectId(groupId, index));
        state.draftValues.remove(affinityRuleTargetInputId(groupId, index));
        state.draftValues.remove(affinityRuleElementSelectId(groupId, index));
    }

    private static void clearAffinityMultiplierDrafts(ViewState state, String groupId, int index) {
        if (state == null) {
            return;
        }
        state.draftValues.remove(affinityMultiplierModeSelectId(groupId, index));
        state.draftValues.remove(affinityMultiplierTargetInputId(groupId, index));
        for (String element : AFFINITY_ELEMENTS) {
            state.draftValues.remove(affinityMultiplierElementInputId(groupId, index, element));
        }
    }

    private static void clearAffinityShieldDrafts(ViewState state, int index) {
        if (state == null) {
            return;
        }
        state.draftValues.remove(affinityShieldModeSelectId(index));
        state.draftValues.remove(affinityShieldTargetInputId(index));
        state.draftValues.remove(affinityShieldValueInputId(index));
        state.draftValues.remove(affinityShieldDelayInputId(index));
        state.draftValues.remove(affinityShieldRateInputId(index));
        state.draftValues.remove(affinityShieldDurationInputId(index));
    }

    private static String affinityRuleAddButtonId(String groupId) { return "affinityRuleAdd_" + groupId; }
    private static String affinityRuleModeSelectId(String groupId, int index) { return "affinityRuleMode_" + groupId + "_" + index; }
    private static String affinityRuleTargetInputId(String groupId, int index) { return "affinityRuleTarget_" + groupId + "_" + index; }
    private static String affinityRuleElementSelectId(String groupId, int index) { return "affinityRuleElement_" + groupId + "_" + index; }
    private static String affinityRuleDeleteButtonId(String groupId, int index) { return "affinityRuleDelete_" + groupId + "_" + index; }
    private static String affinityMultiplierAddButtonId(String groupId) { return "affinityMultiplierAdd_" + groupId; }
    private static String affinityMultiplierModeSelectId(String groupId, int index) { return "affinityMultiplierMode_" + groupId + "_" + index; }
    private static String affinityMultiplierTargetInputId(String groupId, int index) { return "affinityMultiplierTarget_" + groupId + "_" + index; }
    private static String affinityMultiplierElementInputId(String groupId, int index, String element) { return "affinityMultiplier_" + groupId + "_" + element + "_" + index; }
    private static String affinityMultiplierDeleteButtonId(String groupId, int index) { return "affinityMultiplierDelete_" + groupId + "_" + index; }
    private static String affinityShieldAddButtonId() { return "affinityShieldAdd"; }
    private static String affinityShieldModeSelectId(int index) { return "affinityShieldMode_" + index; }
    private static String affinityShieldTargetInputId(int index) { return "affinityShieldTarget_" + index; }
    private static String affinityShieldValueInputId(int index) { return "affinityShieldValue_" + index; }
    private static String affinityShieldDelayInputId(int index) { return "affinityShieldDelay_" + index; }
    private static String affinityShieldRateInputId(int index) { return "affinityShieldRate_" + index; }
    private static String affinityShieldDurationInputId(int index) { return "affinityShieldDuration_" + index; }
    private static String affinityShieldDeleteButtonId(int index) { return "affinityShieldDelete_" + index; }

    private static boolean isLootInjectionGroup(String groupId) {
        return GROUP_LOOT_CHEST_INJECTIONS.equals(groupId)
                || GROUP_LOOT_NPC_INJECTIONS.equals(groupId)
                || GROUP_LOOT_NPC_AQUATIC_INJECTIONS.equals(groupId)
                || GROUP_LOOT_NPC_FLYING_INJECTIONS.equals(groupId)
                || GROUP_LOOT_NPC_VOID_INJECTIONS.equals(groupId);
    }

    private static boolean isNpcLootInjectionGroup(String groupId) {
        return GROUP_LOOT_NPC_INJECTIONS.equals(groupId)
                || GROUP_LOOT_NPC_AQUATIC_INJECTIONS.equals(groupId)
                || GROUP_LOOT_NPC_FLYING_INJECTIONS.equals(groupId)
                || GROUP_LOOT_NPC_VOID_INJECTIONS.equals(groupId);
    }

    private static String[] lootInjectionEntries(String groupId) {
        LootSocketRollConfig cfg = lootConfig();
        if (GROUP_LOOT_CHEST_INJECTIONS.equals(groupId)) {
            return cfg.getChestInjectedLootRules();
        }
        if (GROUP_LOOT_NPC_INJECTIONS.equals(groupId)) {
            return cfg.getNpcInjectedDropRules();
        }
        if (GROUP_LOOT_NPC_AQUATIC_INJECTIONS.equals(groupId)) {
            return cfg.getNpcAquaticInjectedDropRules();
        }
        if (GROUP_LOOT_NPC_FLYING_INJECTIONS.equals(groupId)) {
            return cfg.getNpcFlyingInjectedDropRules();
        }
        if (GROUP_LOOT_NPC_VOID_INJECTIONS.equals(groupId)) {
            return cfg.getNpcVoidInjectedDropRules();
        }
        return new String[0];
    }

    private static void setLootInjectionEntries(String groupId, String[] entries) {
        LootSocketRollConfig cfg = lootConfig();
        if (GROUP_LOOT_CHEST_INJECTIONS.equals(groupId)) {
            cfg.setChestInjectedLootRules(entries);
        } else if (GROUP_LOOT_NPC_INJECTIONS.equals(groupId)) {
            cfg.setNpcInjectedDropRules(entries);
        } else if (GROUP_LOOT_NPC_AQUATIC_INJECTIONS.equals(groupId)) {
            cfg.setNpcAquaticInjectedDropRules(entries);
        } else if (GROUP_LOOT_NPC_FLYING_INJECTIONS.equals(groupId)) {
            cfg.setNpcFlyingInjectedDropRules(entries);
        } else if (GROUP_LOOT_NPC_VOID_INJECTIONS.equals(groupId)) {
            cfg.setNpcVoidInjectedDropRules(entries);
        }
    }

    private static LootRuleEntry parseLootRuleEntry(String raw) {
        if (raw == null || raw.isBlank()) {
            return new LootRuleEntry("", "0.0", "1", "1", "");
        }
        String[] split = raw.trim().split("=", 2);
        String itemId = split[0].trim();
        String[] params = split.length > 1 ? split[1].split(",") : new String[0];
        String chance = params.length > 0 ? normalizeLootChanceText(params[0], "0.0") : "0.0";
        String min = params.length > 1 ? normalizeIntegerText(params[1], "1") : "1";
        String max = params.length > 2 ? normalizeIntegerText(params[2], min) : min;
        String targetId = params.length > 3 ? params[3].trim() : "";
        int minQty = Math.max(0, parseIntSafe(min, 1));
        int maxQty = Math.max(minQty, parseIntSafe(max, minQty));
        return new LootRuleEntry(itemId, chance, String.valueOf(minQty), String.valueOf(maxQty), targetId);
    }

    private static String lootInjectionGroupTitle(Player player, String groupId) {
        if (GROUP_LOOT_CHEST_INJECTIONS.equals(groupId)) {
            return tOrFallback(player, "ui.runtime_config.group.loot.loot_chest_injections.title", "Injected Chest Loot");
        }
        if (GROUP_LOOT_NPC_INJECTIONS.equals(groupId)) {
            return tOrFallback(player, "ui.runtime_config.group.loot.loot_npc_injections.title", "Injected NPC Drops");
        }
        if (GROUP_LOOT_NPC_AQUATIC_INJECTIONS.equals(groupId)) {
            return tOrFallback(player, "ui.runtime_config.group.loot.loot_npc_aquatic_injections.title", "Aquatic NPC Drops");
        }
        if (GROUP_LOOT_NPC_FLYING_INJECTIONS.equals(groupId)) {
            return tOrFallback(player, "ui.runtime_config.group.loot.loot_npc_flying_injections.title", "Flying NPC Drops");
        }
        if (GROUP_LOOT_NPC_VOID_INJECTIONS.equals(groupId)) {
            return tOrFallback(player, "ui.runtime_config.group.loot.loot_npc_void_injections.title", "Void NPC Drops");
        }
        return "Injected Loot";
    }

    private static int parseIntSafe(String raw, int fallback) {
        Double parsed = extractFirstNumber(raw);
        return parsed == null ? fallback : (int) Math.round(parsed);
    }

    private static double parseDoubleSafe(String raw, double fallback) {
        Double parsed = extractFirstNumber(raw);
        return parsed == null ? fallback : parsed;
    }

    private static String draftValue(ViewState state, String elementId, String fallback) {
        if (state != null && elementId != null && state.draftValues.containsKey(elementId)) {
            return state.draftValues.get(elementId);
        }
        return fallback == null ? "" : fallback;
    }

    private static void removeDraftValuesStartingWith(ViewState state, String prefix) {
        if (state == null || prefix == null || prefix.isBlank()) {
            return;
        }
        state.draftValues.keySet().removeIf(key -> key != null && key.startsWith(prefix));
    }

    private static void addUniqueConfig(List<String> configs, String configName) {
        if (configs == null || configName == null || configName.isBlank() || configs.contains(configName)) {
            return;
        }
        configs.add(configName);
    }

    private static void saveConfigs(List<String> configs) {
        if (configs == null) {
            return;
        }
        for (String configName : configs) {
            plugin.getConfigService().saveAndApply(configName);
        }
    }

    private static String firstAvailableAbilitySpirit(List<String> abilityEntries) {
        List<String> used = new ArrayList<>();
        for (String entry : abilityEntries) {
            KeyValue parsed = parseKeyValue(entry);
            if (!parsed.key().isBlank()) {
                used.add(parsed.key());
            }
        }
        for (String spirit : allSpiritIds()) {
            if (!used.contains(spirit)) {
                return spirit;
            }
        }
        return "New_Spirit";
    }

    private static String spiritMappingAddButtonId(String color) { return "loreSpiritAdd_" + normalizeLoreColor(color); }
    private static String gemMappingTokenInputId(int index) { return "loreGemToken_" + index; }
    private static String coreColorToggleButtonId(String color) { return "loreCoreToggle_" + normalizeLoreColor(color); }
    private static String spiritMappingSelectId(String color, int index) { return "loreSpirit_" + normalizeLoreColor(color) + "_" + index; }
    private static String spiritMappingDeleteButtonId(String color, int index) { return "loreSpiritDelete_" + normalizeLoreColor(color) + "_" + index; }
    private static String abilityMappingAddButtonId() { return "loreAbilityAdd"; }
    private static String abilitySpiritSelectId(int index) { return "loreAbilitySpirit_" + index; }
    private static String abilityTriggerSelectId(int index) { return "loreAbilityTrigger_" + index; }
    private static String abilityChanceInputId(int index) { return "loreAbilityChance_" + index; }
    private static String abilityCooldownInputId(int index) { return "loreAbilityCooldown_" + index; }
    private static String abilityEffectSelectId(int index) { return "loreAbilityEffect_" + index; }
    private static String abilityBaseInputId(int index) { return "loreAbilityBase_" + index; }
    private static String abilityPerLevelInputId(int index) { return "loreAbilityPerLevel_" + index; }
    private static String abilityDeleteButtonId(int index) { return "loreAbilityDelete_" + index; }
    private static String statusRuleAddButtonId() { return "loreStatusRuleAdd"; }
    private static String statusRuleStatusSelectId(int index) { return "loreStatusRuleStatus_" + index; }
    private static String statusRuleStepInputId(int index) { return "loreStatusRuleStep_" + index; }
    private static String statusRulePatternSelectId(int index) { return "loreStatusRulePattern_" + index; }
    private static String statusRuleDeleteButtonId(int index) { return "loreStatusRuleDelete_" + index; }
    private static String statusResistanceAddButtonId() { return "loreStatusResistanceAdd"; }
    private static String statusResistanceNpcInputId(int index) { return "loreStatusResistanceNpc_" + index; }
    private static String statusResistanceStatusSelectId(int index) { return "loreStatusResistanceStatus_" + index; }
    private static String statusResistanceValueInputId(int index) { return "loreStatusResistanceValue_" + index; }
    private static String statusResistanceDeleteButtonId(int index) { return "loreStatusResistanceDelete_" + index; }
    private static String statusCounterAddButtonId() { return "loreStatusCounterAdd"; }
    private static String statusCounterNpcInputId(int index) { return "loreStatusCounterNpc_" + index; }
    private static String statusCounterStatusSelectId(int index) { return "loreStatusCounterStatus_" + index; }
    private static String statusCounterDeleteButtonId(int index) { return "loreStatusCounterDelete_" + index; }
    private static String bleedCounterAddButtonId() { return "loreBleedCounterAdd"; }
    private static String bleedCounterNpcInputId(int index) { return "loreBleedCounterNpc_" + index; }
    private static String bleedCounterDeleteButtonId(int index) { return "loreBleedCounterDelete_" + index; }
    private static String lootInjectionAddButtonId(String groupId) { return "lootInjectAdd_" + groupId; }
    private static String lootInjectionItemInputId(String groupId, int index) { return "lootInjectItem_" + groupId + "_" + index; }
    private static String lootInjectionTargetInputId(String groupId, int index) { return "lootInjectTarget_" + groupId + "_" + index; }
    private static String lootInjectionChanceInputId(String groupId, int index) { return "lootInjectChance_" + groupId + "_" + index; }
    private static String lootInjectionMinInputId(String groupId, int index) { return "lootInjectMin_" + groupId + "_" + index; }
    private static String lootInjectionMaxInputId(String groupId, int index) { return "lootInjectMax_" + groupId + "_" + index; }
    private static String lootInjectionDeleteButtonId(String groupId, int index) { return "lootInjectDelete_" + groupId + "_" + index; }
    private static String reforgeSupportAddButtonId() { return "reforgeSupportAdd"; }
    private static String reforgeSupportItemInputId(int index) { return "reforgeSupportItem_" + index; }
    private static String reforgeSupportBreakInputId(int index) { return "reforgeSupportBreak_" + index; }
    private static String reforgeSupportDurabilityInputId(int index) { return "reforgeSupportDurability_" + index; }
    private static String reforgeSupportAntiInputId(int index) { return "reforgeSupportAnti_" + index; }
    private static String reforgeSupportConsumeInputId(int index) { return "reforgeSupportConsume_" + index; }
    private static String reforgeSupportUpgradeGuaranteeInputId(int index) { return "reforgeSupportUpgradeGuarantee_" + index; }
    private static String reforgeSupportJackpotGuaranteeInputId(int index) { return "reforgeSupportJackpotGuarantee_" + index; }
    private static String reforgeSupportUpgradeWeightInputId(int index) { return "reforgeSupportUpgradeWeight_" + index; }
    private static String reforgeSupportJackpotWeightInputId(int index) { return "reforgeSupportJackpotWeight_" + index; }
    private static String reforgeSupportDescriptionInputId(int index) { return "reforgeSupportDescription_" + index; }
    private static String reforgeSupportDeleteButtonId(int index) { return "reforgeSupportDelete_" + index; }
    private static String resonanceMappingAddButtonId() { return "resonanceMappingAdd"; }
    private static String resonanceMappingNameInputId(int index) { return "resonanceMappingName_" + index; }
    private static String resonanceMappingClassesInputId(int index) { return "resonanceMappingClasses_" + index; }
    private static String resonanceMappingDeleteButtonId(int index) { return "resonanceMappingDelete_" + index; }
    private static String resonanceHintAddButtonId() { return "resonanceHintAdd"; }
    private static String resonanceHintClassInputId(int index) { return "resonanceHintClass_" + index; }
    private static String resonanceHintTokensInputId(int index) { return "resonanceHintTokens_" + index; }
    private static String resonanceHintDeleteButtonId(int index) { return "resonanceHintDelete_" + index; }
    private static String clockworkAmmoHintAddButtonId() { return "clockworkAmmoHintAdd"; }
    private static String clockworkAmmoHintInputId(int index) { return "clockworkAmmoHint_" + index; }
    private static String clockworkAmmoHintDeleteButtonId(int index) { return "clockworkAmmoHintDelete_" + index; }

    private record KeyValue(String key, String value) {}
    private record AbilityEntry(
            String spirit,
            String trigger,
            String chance,
            String cooldown,
            String effect,
            String base,
            String perLevel,
            boolean signature) {
        AbilityEntry withSpirit(String value) {
            return new AbilityEntry(value, trigger, chance, cooldown, effect, base, perLevel, signature);
        }

        AbilityEntry asSignature(String value) {
            return new AbilityEntry(spirit, value, chance, cooldown, effect, base, perLevel, true);
        }

        AbilityEntry asNormal(String value) {
            return new AbilityEntry(spirit, value, chance, cooldown, effect, base, perLevel, false);
        }

        AbilityEntry withChance(String value) {
            return new AbilityEntry(spirit, trigger, value, cooldown, effect, base, perLevel, false);
        }

        AbilityEntry withCooldown(String value) {
            return new AbilityEntry(spirit, trigger, chance, value, effect, base, perLevel, false);
        }

        AbilityEntry withEffect(String value) {
            return new AbilityEntry(spirit, trigger, chance, cooldown, value, base, perLevel, false);
        }

        AbilityEntry withBase(String value) {
            return new AbilityEntry(spirit, trigger, chance, cooldown, effect, value, perLevel, false);
        }

        AbilityEntry withPerLevel(String value) {
            return new AbilityEntry(spirit, trigger, chance, cooldown, effect, base, value, false);
        }
    }
    private record StatusRuleEntry(String status, String step, String pattern) {}
    private record StatusResistanceEntry(String npcId, String status, String value) {}
    private record StatusCounterEntry(String npcId, String status) {}
    private record ColorSpiritMapping(String color, List<String> npcIds) {}
    private record LootRuleEntry(String itemId, String chance, String min, String max, String targetId) {}
    private record ReforgeSupportRuleEntry(
            String itemId,
            String breakMultiplier,
            String durabilityLoss,
            String antiDegradeChance,
            String consumeMode,
            String upgradeGuarantee,
            String jackpotGuarantee,
            String upgradeWeight,
            String jackpotWeight,
            String description) {}
    private record AffinityRuleEntry(String mode, String target, String element) {}
    private record AffinityMultiplierEntry(String mode, String target, Map<String, String> multipliers) {}
    private record AffinityShieldEntry(String mode, String target, String shield, String delay, String rate, String duration) {}
    private record ResonanceMappingEntry(String name, String classes) {}
    private record ResonanceHintEntry(String weaponClass, String hints) {}

    private static double computeDelta(DisplayKind kind, double current, double target) {
        if (kind == DisplayKind.TOGGLE) {
            return target >= 0.5 ? 1.0 : -1.0;
        }
        return target - current;
    }

    private static double clampChance(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Double parseInputValue(DisplayKind kind, String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (kind == DisplayKind.TOGGLE) {
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if ("1".equals(lower) || "true".equals(lower) || "enabled".equals(lower) || "on".equals(lower)) {
                return 1.0;
            }
            if ("0".equals(lower) || "false".equals(lower) || "disabled".equals(lower) || "off".equals(lower)) {
                return 0.0;
            }
            Double numeric = extractFirstNumber(trimmed);
            if (numeric == null) {
                return null;
            }
            return numeric >= 0.5 ? 1.0 : 0.0;
        }

        Double numeric = extractFirstNumber(trimmed);
        if (numeric == null) {
            return null;
        }
        boolean hasPercent = trimmed.contains("%");
        if (kind == DisplayKind.INTEGER) {
            return (double) Math.round(numeric);
        }
        if (kind == DisplayKind.PERCENT) {
            // Percent inputs are displayed as 0-100 in the UI, so always treat input as percent.
            return numeric / 100.0;
        }
        if (kind == DisplayKind.MULTIPLIER) {
            if (hasPercent) {
                return 1.0 + (numeric / 100.0);
            }
            return numeric;
        }
        return numeric;
    }

    private static Double extractFirstNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Matcher matcher = NUMBER_PATTERN.matcher(raw.replace(',', '.'));
        if (!matcher.find()) {
            return null;
        }
        try {
            return Double.parseDouble(matcher.group());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean roughlyEqual(double left, double right) {
        return Math.abs(left - right) < 0.0000001;
    }

    private static String sanitizeError(Exception e) {
        Throwable current = e;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }


    private static String t(Object player, String key, Object... params) {
        return LangLoader.getUITranslation(player, key, params);
    }

    private static String localizedCategoryTitle(Object player, CategorySection category) {
        if (category == null) {
            return "";
        }
        return tOrFallback(player, "ui.runtime_config.category." + category.id + ".title", category.title);
    }

    private static String localizedCategorySummary(Object player, CategorySection category) {
        if (category == null) {
            return "";
        }
        return tOrFallback(player, "ui.runtime_config.category." + category.id + ".summary", safeText(category.summary));
    }

    private static String localizedCategoryNote(Object player, CategorySection category) {
        if (category == null) {
            return "";
        }
        return tOrFallback(player, "ui.runtime_config.category." + category.id + ".note", safeText(category.noteText));
    }

    private static String localizedGroupTitle(Object player, CategorySection category, ControlGroup group) {
        if (category == null || group == null) {
            return "";
        }
        String key = "ui.runtime_config.group." + category.id + "." + group.id + ".title";
        String value = LangLoader.getUITranslation(player, key);
        if (value != null && !value.equals(key)) {
            return value;
        }
        if (CATEGORY_LORE.equals(category.id) && group.id.startsWith("lore_mapping_")) {
            String mappingKey = "ui.runtime_config.group." + CATEGORY_LORE_MAPPING + "." + group.id + ".title";
            value = LangLoader.getUITranslation(player, mappingKey);
            if (value != null && !value.equals(mappingKey)) {
                return value;
            }
        }
        return group.title;
    }

    private static String localizedGroupDescription(Object player, CategorySection category, ControlGroup group) {
        if (category == null || group == null) {
            return "";
        }
        String fallback = safeText(group.description);
        String key = "ui.runtime_config.group." + category.id + "." + group.id + ".description";
        String value = LangLoader.getUITranslation(player, key);
        if (value != null && !value.equals(key)) {
            return value;
        }
        if (CATEGORY_LORE.equals(category.id) && group.id.startsWith("lore_mapping_")) {
            String mappingKey = "ui.runtime_config.group." + CATEGORY_LORE_MAPPING + "." + group.id + ".description";
            value = LangLoader.getUITranslation(player, mappingKey);
            if (value != null && !value.equals(mappingKey)) {
                return value;
            }
        }
        return fallback;
    }

    private static String localizedControlLabel(Object player, NumericControl control) {
        if (control == null) {
            return "";
        }
        return tOrFallback(player, "ui.runtime_config.control." + control.id + ".label", control.label);
    }

    private static String localizedControlDescription(Object player, NumericControl control) {
        if (control == null) {
            return "";
        }
        return tOrFallback(player, "ui.runtime_config.control." + control.id + ".description", safeText(control.description));
    }

    private static String localizedControlLabel(Object player, TextControl control) {
        if (control == null) {
            return "";
        }
        return tOrFallback(player, "ui.runtime_config.control." + control.id + ".label", control.label);
    }

    private static String localizedControlDescription(Object player, TextControl control) {
        if (control == null) {
            return "";
        }
        return tOrFallback(player, "ui.runtime_config.control." + control.id + ".description", safeText(control.description));
    }

    private static String localizedControlLabel(Object player, SelectControl control) {
        if (control == null) {
            return "";
        }
        return tOrFallback(player, "ui.runtime_config.control." + control.id + ".label", control.label);
    }

    private static String localizedControlDescription(Object player, SelectControl control) {
        if (control == null) {
            return "";
        }
        return tOrFallback(player, "ui.runtime_config.control." + control.id + ".description", safeText(control.description));
    }

    private static String safeText(String text) {
        return text == null ? "" : text;
    }

    private static String tOrFallback(Object player, String key, String fallback, Object... params) {
        String value = LangLoader.getUITranslation(player, key, params);
        if (value == null || value.equals(key)) {
            return fallback;
        }
        return value;
    }
}
