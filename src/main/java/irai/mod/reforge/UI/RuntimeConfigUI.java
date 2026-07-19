package irai.mod.reforge.UI;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import irai.mod.DynamicFloatingDamageFormatter.DamageNumberConfig;
import irai.mod.reforge.Common.UI.HyUIReflectionUtils;
import irai.mod.reforge.Common.UI.UITemplateUtils;
import irai.mod.reforge.Common.LootInjectionUtils;
import irai.mod.reforge.Config.CrossModConfig;
import irai.mod.reforge.Config.LoreConfig;
import irai.mod.reforge.Config.LoreMappingConfig;
import irai.mod.reforge.Config.LootSocketRollConfig;
import irai.mod.reforge.Config.RefinementConfig;
import irai.mod.reforge.Config.RefinementConfig.MaterialTier;
import irai.mod.reforge.Config.SocketConfig;
import irai.mod.reforge.ReforgePlugin;
import irai.mod.reforge.Util.LangLoader;

/**
 * OP-only live gameplay config editor backed by HyUI.
 */
public final class RuntimeConfigUI {
    private RuntimeConfigUI() {}

    private static final String HYUI_PAGE_BUILDER = "au.ellie.hyui.builders.PageBuilder";
    private static final String HYUI_PLUGIN = "au.ellie.hyui.HyUIPlugin";
    private static final String HYUI_EVENT_BINDING = "com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType";
    private static final String TEMPLATE_PATH = "Common/UI/Custom/Pages/RuntimeConfigEditor.html";
    private static final String RESET_DEFAULTS_BUTTON = "resetDefaultsButton";
    private static final String REFINE_RECALCULATE_WEIGHTS_BUTTON = "recalculateWeightsButton";

    private static final String SOCKET_CONFIG_NAME = "SocketConfig";
    private static final String REFINEMENT_CONFIG_NAME = "RefinementConfig";
    private static final String CROSS_MOD_CONFIG_NAME = "CrossModConfig";
    private static final String LOOT_CONFIG_NAME = "LootSocketRollConfig";
    private static final String LORE_CONFIG_NAME = "LoreConfig";
    private static final String LORE_MAPPING_CONFIG_NAME = "LoreMappingConfig";
    private static final String DAMAGE_NUMBER_CONFIG_NAME = "DamageNumberConfig";

    private static final String CATEGORY_SOCKET = "socket";
    private static final String CATEGORY_REFINEMENT = "refinement";
    private static final String CATEGORY_CROSS_MOD = "cross_mod";
    private static final String CATEGORY_LOOT = "loot";
    private static final String CATEGORY_LORE = "lore";
    private static final String CATEGORY_LORE_MAPPING = "lore_mapping";
    private static final String CATEGORY_DAMAGE_NUMBERS = "damage_numbers";
    private static final String GROUP_LORE_MAPPING_GEMS = "lore_mapping_gems";
    private static final String GROUP_LORE_MAPPING_SPIRITS = "lore_mapping_spirits";
    private static final String GROUP_LORE_MAPPING_ABILITIES = "lore_mapping_abilities";
    private static final String GROUP_LORE_STATUS_RULES = "lore_status_rules";
    private static final String GROUP_LOOT_CHEST_INJECTIONS = "loot_chest_injections";
    private static final String GROUP_LOOT_NPC_INJECTIONS = "loot_npc_injections";
    private static final String GROUP_LOOT_NPC_AQUATIC_INJECTIONS = "loot_npc_aquatic_injections";
    private static final String GROUP_LOOT_NPC_FLYING_INJECTIONS = "loot_npc_flying_injections";
    private static final String GROUP_LOOT_NPC_VOID_INJECTIONS = "loot_npc_void_injections";
    private static final String[] LORE_COLORS = {"black", "blue", "cyan", "green", "red", "white", "yellow"};
    private static final String[] LORE_TRIGGERS = {
            "ON_HIT", "ON_CRIT", "ON_KILL", "ON_DAMAGED", "ON_BLOCK", "ON_BLOCKED",
            "ON_NEAR_DEATH", "ON_FIRST_KILL", "ON_LORE_PROC", "ON_HEAL", "ON_POTION_USE",
            "ON_STATUS_APPLY", "ON_SPRINT", "ON_JUMP", "ON_SNEAK", "ON_SKILL_USE"
    };
    private static final String[] LORE_SIGNATURES = {
            "SIGNATURE_VORTEXSTRIKE", "SIGNATURE_GROUNDSLAM", "SIGNATURE_RAZORSTRIKE",
            "SIGNATURE_WHIRLWIND", "SIGNATURE_VOLLEY", "SIGNATURE_BIG_ARROW",
            "SIGNATURE_OMNISLASH", "SIGNATURE_OCTASLASH", "SIGNATURE_PUMMEL",
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
            "CHARGE_ATTACK", "OMNISLASH", "OCTASLASH", "PUMMEL", "BLOOD_RUSH",
            "CAUSTIC_FINALE", "SHRAPNEL_FINALE", "BURN_FINALE", "DRAIN_LIFE"
    };
    private static final String[] LORE_STATUS_KEYS = {
            "*", "bleed", "burn", "poison", "freeze", "shock", "slow", "weakness",
            "blind", "root", "stun", "fear", "drain"
    };
    private static final String[] LORE_STATUS_PATTERNS = {"LINEAR", "FIBONACCI"};

    private static final String DEFAULT_STATUS_KEY = "ui.runtime_config.status_default";
    private static final String DEFAULT_STATUS_FALLBACK =
            "Changes apply live and save immediately.\nUse Reload All From Disk to discard in-memory edits.";
    private static final String COMPACT_DROPDOWN_ATTRS = " class=\"default-style\"";
    private static final int ABILITY_MAPPING_BOTTOM_SPACER_CARDS = 5;
    private static final int CATEGORY_WIDTH = 848;
    private static final int GROUP_WIDTH = 828;
    private static final int GROUP_LIST_COLUMN_WIDTH = 270;
    private static final int CONTROL_PANEL_WIDTH = 550;
    private static final int GROUP_PANEL_HEIGHT = 420;
    private static final int CONTROL_COLUMN_WIDTH = 270;
    private static final int VALUE_WIDTH = 150;
    private static final int DIVIDER_WIDTH = 792;
    private static final int PICKER_PAGE_SIZE = 7;
    private static final int CONTROL_PAGE_SIZE = 5;
    private static final int SPIRIT_MAPPING_BOTTOM_SPACER_CARDS = 5;
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

    private static boolean hyuiAvailable = false;
    private static ReforgePlugin plugin;

    private static final Map<PlayerRef, Object> openPages = new ConcurrentHashMap<>();
    private static final Map<PlayerRef, ViewState> viewStates = new ConcurrentHashMap<>();
    private static final List<CategorySection> categories = new ArrayList<>();
    private static final List<ControlEntry> controls = new ArrayList<>();

    private enum DisplayKind {
        INTEGER,
        PERCENT,
        MULTIPLIER,
        TOGGLE
    }

    private enum PickerMode {
        NONE,
        CATEGORY,
        GROUP
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
        PickerMode picker;
        int pickerPage;
        int controlsPage;
        final Map<String, String> draftValues = new LinkedHashMap<>();

        ViewState(String activeCategoryId, String activeGroupId, String statusText, PickerMode picker, int pickerPage, int controlsPage) {
            this.activeCategoryId = activeCategoryId;
            this.activeGroupId = activeGroupId;
            this.statusText = statusText;
            this.picker = picker == null ? PickerMode.NONE : picker;
            this.pickerPage = Math.max(0, pickerPage);
            this.controlsPage = Math.max(0, controlsPage);
        }
    }

    private static final class PickerEntry {
        final String id;
        final String title;
        final String summary;

        private PickerEntry(String id, String title, String summary) {
            this.id = id == null ? "" : id;
            this.title = title == null ? "" : title;
            this.summary = summary == null ? "" : summary;
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

    public static void initialize(ReforgePlugin pluginInstance) {
        plugin = pluginInstance;
        hyuiAvailable = HyUIReflectionUtils.detectHyUi(HYUI_PAGE_BUILDER, HYUI_PLUGIN, "RuntimeConfigUI");
        registerControls();
    }

    public static boolean isAvailable() {
        return hyuiAvailable && plugin != null;
    }

    public static void open(Player player) {
        if (player == null) {
            return;
        }
        if (!isAvailable()) {
            player.getPlayerRef().sendMessage(Message.raw("<color=#FF5555>" + t(player, "ui.runtime_config.hyui_missing")));
            return;
        }
        PlayerRef playerRef = player.getPlayerRef();
        viewStates.computeIfAbsent(playerRef, RuntimeConfigUI::createDefaultViewState);
        closePageIfOpen(playerRef);
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
        openPage(player, state);
    }

    private static void openPage(Player player, ViewState state) {
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
            Method onDismiss = pageBuilderClass.getMethod("onDismiss", java.util.function.BiConsumer.class);
            Method withLifetime = pageBuilderClass.getMethod("withLifetime", CustomPageLifetime.class);
            Method openMethod = pageBuilderClass.getMethod("open", Class.forName("com.hypixel.hytale.component.Store"));

            Object activating = eventBindingClass.getField("Activating").get(null);
            Object valueChanged = eventBindingClass.getField("ValueChanged").get(null);
            Object validating = eventBindingClass.getField("Validating").get(null);

            String html = loadTemplate();
            CategorySection activeCategory = resolveActiveCategory(state);
            ControlGroup activeGroup = resolveActiveGroup(activeCategory, state);

            html = html.replace("{{statusText}}", escapeHtml(state.statusText));
            html = html.replace("{{activeCategoryId}}", activeCategory == null ? "" : escapeHtml(activeCategory.id));
            html = html.replace("{{categoryTabsHtml}}", buildCategoryTabsHtml(player, state));
            html = html.replace("{{groupNavHtml}}", buildGroupNavHtml(player, activeCategory, activeGroup));
            html = html.replace("{{controlsHtml}}", buildControlsHtml(player, activeCategory, activeGroup, state));
            html = LangLoader.replaceUiTokens(player, html);

            Object pageBuilder = pageForPlayer.invoke(null, playerRef);
            pageBuilder = fromHtml.invoke(pageBuilder, html);

            final Player finalPlayer = player;
            final ViewState finalState = state;

            addListener.invoke(pageBuilder, "reloadAllButton", activating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleReload(finalPlayer, finalState));
            addListener.invoke(pageBuilder, RESET_DEFAULTS_BUTTON, activating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleResetDefaults(finalPlayer, finalState));
            addListener.invoke(pageBuilder, "applyChangesButton", activating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) ->
                            handleApplyVisibleInputs(finalPlayer, finalState, activeCategory, activeGroup, ctxObj));
            if (activeGroup != null && "refine_limits".equals(activeGroup.id)) {
                addListener.invoke(pageBuilder, REFINE_RECALCULATE_WEIGHTS_BUTTON, activating,
                        (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) ->
                                handleRecalculateRefinementWeights(finalPlayer, finalState, ctxObj));
            }

            for (CategorySection category : categories) {
                addListener.invoke(pageBuilder, category.toggleButtonId, activating,
                        (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                            selectCategory(finalState, category.id);
                            finalState.picker = PickerMode.NONE;
                            finalState.pickerPage = 0;
                            finalState.controlsPage = 0;
                            requestReopen(finalPlayer, finalState);
                        });
            }

            if (activeCategory != null) {
                for (ControlGroup group : activeCategory.groups) {
                    addListener.invoke(pageBuilder, group.buttonId(activeCategory.id), activating,
                        (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                            finalState.activeGroupId = group.id;
                            finalState.picker = PickerMode.NONE;
                            finalState.pickerPage = 0;
                            finalState.controlsPage = 0;
                            requestReopen(finalPlayer, finalState);
                        });
                }
            }

            for (ControlEntry entry : visibleControls(activeCategory, activeGroup, finalState)) {
                if (entry instanceof NumericControl control) {
                    if (control.displayKind == DisplayKind.TOGGLE) {
                        registerToggleListener(pageBuilder, addListener, activating, control, finalPlayer, finalState);
                    } else {
                        registerValueListener(pageBuilder, addListener, valueChanged, control, finalPlayer, finalState);
                        registerValueListener(pageBuilder, addListener, validating, control, finalPlayer, finalState);
                    }
                } else if (entry instanceof TextControl control) {
                    registerTextListener(pageBuilder, addListener, valueChanged, control, finalPlayer, finalState);
                }
            }
            registerLoreMappingListeners(pageBuilder, addListener, activating, valueChanged, validating, activeGroup, finalPlayer, finalState);
            registerLootInjectionListeners(pageBuilder, addListener, activating, valueChanged, validating, activeGroup, finalPlayer, finalState);

            pageBuilder = onDismiss.invoke(pageBuilder,
                    (java.util.function.BiConsumer<Object, Object>) (pageObj, dismissedByServer) ->
                            openPages.remove(playerRef, pageObj));
            pageBuilder = withLifetime.invoke(pageBuilder, CustomPageLifetime.CanDismiss);
            Object page = openMethod.invoke(pageBuilder, getStore(playerRef));
            openPages.put(playerRef, page);
        } catch (Exception e) {
            System.err.println("[SocketReforge] RuntimeConfigUI open error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void registerAdjustmentListener(
            Object pageBuilder,
            Method addListener,
            Object activating,
            NumericControl control,
            double delta,
            Player player,
            ViewState state) throws Exception {
        String buttonId = buttonIdForDelta(control, delta);
        addListener.invoke(pageBuilder, buttonId, activating,
                (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleAdjustment(player, state, control, delta));
    }

    private static void registerValueListener(
            Object pageBuilder,
            Method addListener,
            Object validating,
            NumericControl control,
            Player player,
            ViewState state) throws Exception {
        String inputId = control.inputElementId();
        addListener.invoke(pageBuilder, inputId, validating,
                (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleValueChange(player, state, control, eventObj, ctxObj));
    }

    private static void registerToggleListener(
            Object pageBuilder,
            Method addListener,
            Object activating,
            NumericControl control,
            Player player,
            ViewState state) throws Exception {
        String buttonId = control.plusButtonId();
        addListener.invoke(pageBuilder, buttonId, activating,
                (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleToggle(player, state, control));
    }

    private static void registerTextListener(
            Object pageBuilder,
            Method addListener,
            Object validating,
            TextControl control,
            Player player,
            ViewState state) throws Exception {
        String inputId = control.inputElementId();
        addListener.invoke(pageBuilder, inputId, validating,
                (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleTextChange(player, state, control, eventObj, ctxObj));
    }

    private static void registerDraftValueListener(
            Object pageBuilder,
            Method addListener,
            Object valueChanged,
            String elementId,
            ViewState state) throws Exception {
        addListener.invoke(pageBuilder, elementId, valueChanged,
                (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) ->
                        handleDraftValueChange(state, elementId, eventObj, ctxObj));
    }

    private static void registerLoreMappingListeners(
            Object pageBuilder,
            Method addListener,
            Object activating,
            Object valueChanged,
            Object validating,
            ControlGroup activeGroup,
            Player player,
            ViewState state) throws Exception {
        if (activeGroup == null) {
            return;
        }
        if (GROUP_LORE_MAPPING_GEMS.equals(activeGroup.id)) {
            String[] entries = loreMappingConfig().getGemColorEntries();
            for (String color : LORE_COLORS) {
                addListener.invoke(pageBuilder, coreColorToggleButtonId(color), activating,
                        (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleToggleCoreColor(player, state, color));
            }
            for (int i = 0; i < entries.length; i++) {
                int index = i;
                registerDraftValueListener(pageBuilder, addListener, valueChanged, gemMappingTokenInputId(index), state);
                addListener.invoke(pageBuilder, gemMappingTokenInputId(index), validating,
                        (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleGemMappingTokenChange(player, state, index, eventObj, ctxObj));
            }
            return;
        }
        if (GROUP_LORE_MAPPING_SPIRITS.equals(activeGroup.id)) {
            for (String color : LORE_COLORS) {
                List<String> spirits = spiritsForColor(color);
                addListener.invoke(pageBuilder, spiritMappingAddButtonId(color), activating,
                        (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleAddSpiritMapping(player, state, color));
                for (int i = 0; i < spirits.size(); i++) {
                    int index = i;
                    addListener.invoke(pageBuilder, spiritMappingSelectId(color, index), valueChanged,
                            (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleSpiritMappingChange(player, state, color, index, eventObj, ctxObj));
                    addListener.invoke(pageBuilder, spiritMappingDeleteButtonId(color, index), activating,
                            (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleDeleteSpiritMapping(player, state, color, index));
                }
            }
            return;
        }
        if (GROUP_LORE_MAPPING_ABILITIES.equals(activeGroup.id)) {
            String[] entries = loreMappingConfig().getAbilityEntries();
            addListener.invoke(pageBuilder, abilityMappingAddButtonId(), activating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleAddAbilityMapping(player, state));
            for (int i = 0; i < entries.length; i++) {
                int index = i;
                AbilityEntry ability = parseAbilityEntry(entries[i]);
                addListener.invoke(pageBuilder, abilitySpiritSelectId(index), valueChanged,
                        (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleAbilitySpiritChange(player, state, index, eventObj, ctxObj));
                addListener.invoke(pageBuilder, abilityTriggerSelectId(index), valueChanged,
                        (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleAbilityTriggerChange(player, state, index, eventObj, ctxObj));
                addListener.invoke(pageBuilder, abilityDeleteButtonId(index), activating,
                        (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleDeleteAbilityMapping(player, state, index));
                if (!ability.signature()) {
                    registerDraftValueListener(pageBuilder, addListener, valueChanged, abilityChanceInputId(index), state);
                    addListener.invoke(pageBuilder, abilityChanceInputId(index), validating,
                            (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleAbilityFieldChange(player, state, index, AbilityField.CHANCE, eventObj, ctxObj));
                    registerDraftValueListener(pageBuilder, addListener, valueChanged, abilityCooldownInputId(index), state);
                    addListener.invoke(pageBuilder, abilityCooldownInputId(index), validating,
                            (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleAbilityFieldChange(player, state, index, AbilityField.COOLDOWN, eventObj, ctxObj));
                    addListener.invoke(pageBuilder, abilityEffectSelectId(index), valueChanged,
                            (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleAbilityFieldChange(player, state, index, AbilityField.EFFECT, eventObj, ctxObj));
                    registerDraftValueListener(pageBuilder, addListener, valueChanged, abilityBaseInputId(index), state);
                    addListener.invoke(pageBuilder, abilityBaseInputId(index), validating,
                            (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleAbilityFieldChange(player, state, index, AbilityField.BASE, eventObj, ctxObj));
                    registerDraftValueListener(pageBuilder, addListener, valueChanged, abilityPerLevelInputId(index), state);
                    addListener.invoke(pageBuilder, abilityPerLevelInputId(index), validating,
                            (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleAbilityFieldChange(player, state, index, AbilityField.PER_LEVEL, eventObj, ctxObj));
                }
            }
            return;
        }
        if (GROUP_LORE_STATUS_RULES.equals(activeGroup.id)) {
            List<StatusRuleEntry> reapplyRules = parseStatusRuleEntries(loreConfig().getStatusBossReapplyRules());
            addListener.invoke(pageBuilder, statusRuleAddButtonId(), activating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleAddStatusRule(player, state));
            for (int i = 0; i < reapplyRules.size(); i++) {
                int index = i;
                addListener.invoke(pageBuilder, statusRuleStatusSelectId(index), valueChanged,
                        (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleStatusRuleChange(player, state, index, StatusRuleField.STATUS, eventObj, ctxObj));
                registerDraftValueListener(pageBuilder, addListener, valueChanged, statusRuleStepInputId(index), state);
                addListener.invoke(pageBuilder, statusRuleStepInputId(index), validating,
                        (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleStatusRuleChange(player, state, index, StatusRuleField.STEP, eventObj, ctxObj));
                addListener.invoke(pageBuilder, statusRulePatternSelectId(index), valueChanged,
                        (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleStatusRuleChange(player, state, index, StatusRuleField.PATTERN, eventObj, ctxObj));
                addListener.invoke(pageBuilder, statusRuleDeleteButtonId(index), activating,
                        (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleDeleteStatusRule(player, state, index));
            }

            List<StatusResistanceEntry> resistances = parseStatusResistanceEntries(loreConfig().getNpcStatusResistanceEntries());
            addListener.invoke(pageBuilder, statusResistanceAddButtonId(), activating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleAddStatusResistance(player, state));
            for (int i = 0; i < resistances.size(); i++) {
                int index = i;
                registerDraftValueListener(pageBuilder, addListener, valueChanged, statusResistanceNpcInputId(index), state);
                addListener.invoke(pageBuilder, statusResistanceNpcInputId(index), validating,
                        (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleStatusResistanceChange(player, state, index, StatusResistanceField.NPC, eventObj, ctxObj));
                addListener.invoke(pageBuilder, statusResistanceStatusSelectId(index), valueChanged,
                        (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleStatusResistanceChange(player, state, index, StatusResistanceField.STATUS, eventObj, ctxObj));
                registerDraftValueListener(pageBuilder, addListener, valueChanged, statusResistanceValueInputId(index), state);
                addListener.invoke(pageBuilder, statusResistanceValueInputId(index), validating,
                        (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleStatusResistanceChange(player, state, index, StatusResistanceField.VALUE, eventObj, ctxObj));
                addListener.invoke(pageBuilder, statusResistanceDeleteButtonId(index), activating,
                        (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleDeleteStatusResistance(player, state, index));
            }

            List<StatusCounterEntry> counters = parseStatusCounterEntries(loreConfig().getStatusBossCounterNpcIds());
            addListener.invoke(pageBuilder, statusCounterAddButtonId(), activating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleAddStatusCounter(player, state));
            for (int i = 0; i < counters.size(); i++) {
                int index = i;
                registerDraftValueListener(pageBuilder, addListener, valueChanged, statusCounterNpcInputId(index), state);
                addListener.invoke(pageBuilder, statusCounterNpcInputId(index), validating,
                        (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleStatusCounterChange(player, state, index, StatusCounterField.NPC, eventObj, ctxObj));
                addListener.invoke(pageBuilder, statusCounterStatusSelectId(index), valueChanged,
                        (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleStatusCounterChange(player, state, index, StatusCounterField.STATUS, eventObj, ctxObj));
                addListener.invoke(pageBuilder, statusCounterDeleteButtonId(index), activating,
                        (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleDeleteStatusCounter(player, state, index));
            }

            List<String> bleedCounters = mutableList(loreConfig().getBleedBossCounterNpcIds());
            addListener.invoke(pageBuilder, bleedCounterAddButtonId(), activating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleAddBleedCounter(player, state));
            for (int i = 0; i < bleedCounters.size(); i++) {
                int index = i;
                registerDraftValueListener(pageBuilder, addListener, valueChanged, bleedCounterNpcInputId(index), state);
                addListener.invoke(pageBuilder, bleedCounterNpcInputId(index), validating,
                        (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleBleedCounterChange(player, state, index, eventObj, ctxObj));
                addListener.invoke(pageBuilder, bleedCounterDeleteButtonId(index), activating,
                        (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleDeleteBleedCounter(player, state, index));
            }
        }
    }

    private static void registerLootInjectionListeners(
            Object pageBuilder,
            Method addListener,
            Object activating,
            Object valueChanged,
            Object validating,
            ControlGroup activeGroup,
            Player player,
            ViewState state) throws Exception {
        if (activeGroup == null || !isLootInjectionGroup(activeGroup.id)) {
            return;
        }
        String[] entries = lootInjectionEntries(activeGroup.id);
        addListener.invoke(pageBuilder, lootInjectionAddButtonId(activeGroup.id), activating,
                (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleAddLootInjectionRule(player, state, activeGroup.id));
        for (int i = 0; i < entries.length; i++) {
            int index = i;
            registerDraftValueListener(pageBuilder, addListener, valueChanged, lootInjectionItemInputId(activeGroup.id, index), state);
            addListener.invoke(pageBuilder, lootInjectionItemInputId(activeGroup.id, index), validating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleLootInjectionRuleChange(player, state, activeGroup.id, index, ctxObj));
            if (isNpcLootInjectionGroup(activeGroup.id)) {
                registerDraftValueListener(pageBuilder, addListener, valueChanged, lootInjectionTargetInputId(activeGroup.id, index), state);
                addListener.invoke(pageBuilder, lootInjectionTargetInputId(activeGroup.id, index), validating,
                        (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleLootInjectionRuleChange(player, state, activeGroup.id, index, ctxObj));
            }
            registerDraftValueListener(pageBuilder, addListener, valueChanged, lootInjectionChanceInputId(activeGroup.id, index), state);
            addListener.invoke(pageBuilder, lootInjectionChanceInputId(activeGroup.id, index), validating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleLootInjectionRuleChange(player, state, activeGroup.id, index, ctxObj));
            registerDraftValueListener(pageBuilder, addListener, valueChanged, lootInjectionMinInputId(activeGroup.id, index), state);
            addListener.invoke(pageBuilder, lootInjectionMinInputId(activeGroup.id, index), validating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleLootInjectionRuleChange(player, state, activeGroup.id, index, ctxObj));
            registerDraftValueListener(pageBuilder, addListener, valueChanged, lootInjectionMaxInputId(activeGroup.id, index), state);
            addListener.invoke(pageBuilder, lootInjectionMaxInputId(activeGroup.id, index), validating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleLootInjectionRuleChange(player, state, activeGroup.id, index, ctxObj));
            addListener.invoke(pageBuilder, lootInjectionDeleteButtonId(activeGroup.id, index), activating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleDeleteLootInjectionRule(player, state, activeGroup.id, index));
        }
    }

    private static void handleReload(Player player, ViewState state) {
        try {
            plugin.getConfigService().reloadAll();
            state.draftValues.clear();
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
        }
    }

    private static void handleAdjustment(Player player, ViewState state, NumericControl control, double delta) {
        double before = control.currentValue();
        try {
            control.adjust(delta);
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

    private static void handleValueChange(Player player, ViewState state, NumericControl control, Object eventObj, Object ctxObj) {
        String rawValue = HyUIReflectionUtils.extractEventValue(eventObj);
        if (rawValue == null || rawValue.isBlank()) {
            rawValue = HyUIReflectionUtils.getContextValue(ctxObj, control.inputElementId(), "#" + control.inputElementId() + ".value");
        }
        if (rawValue != null) {
            state.draftValues.put(control.inputElementId(), rawValue);
        }
    }

    private static void handleTextChange(Player player, ViewState state, TextControl control, Object eventObj, Object ctxObj) {
        String rawValue = HyUIReflectionUtils.extractEventValue(eventObj);
        if (rawValue == null) {
            rawValue = HyUIReflectionUtils.getContextValue(ctxObj, control.inputElementId(), "#" + control.inputElementId() + ".value");
        }
        if (rawValue == null) {
            rawValue = "";
        }
        state.draftValues.put(control.inputElementId(), rawValue);
    }

    private static void handleDraftValueChange(ViewState state, String elementId, Object eventObj, Object ctxObj) {
        if (state == null || elementId == null || elementId.isBlank()) {
            return;
        }
        String rawValue = HyUIReflectionUtils.extractEventValue(eventObj);
        if (rawValue == null) {
            rawValue = HyUIReflectionUtils.getContextValue(ctxObj, elementId, "#" + elementId + ".value");
        }
        state.draftValues.put(elementId, rawValue == null ? "" : rawValue);
    }

    private static void handleApplyVisibleInputs(
            Player player,
            ViewState state,
            CategorySection activeCategory,
            ControlGroup activeGroup,
            Object ctxObj) {
        List<String> configsToSave = new ArrayList<>();
        int changed = 0;
        boolean refinementMaxChanged = false;
        try {
            for (ControlEntry entry : visibleControls(activeCategory, activeGroup, state)) {
                if (entry instanceof NumericControl control) {
                    if (control.displayKind == DisplayKind.TOGGLE) {
                        continue;
                    }
                    if (refinementMaxChanged && isWeightControl(control)) {
                        state.draftValues.remove(control.inputElementId());
                        continue;
                    }
                    String rawValue = draftOrContextValue(state, ctxObj, control.inputElementId(), control.formatInputValue());
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
                    String rawValue = draftOrContextValue(state, ctxObj, control.inputElementId(), control.currentValue());
                    String before = control.currentValue();
                    control.applyValue(rawValue == null ? "" : rawValue);
                    if (!before.equals(control.currentValue())) {
                        changed++;
                        addUniqueConfig(configsToSave, control.configName);
                    }
                    state.draftValues.remove(control.inputElementId());
                }
            }
            changed += applyVisibleMappingInputs(state, activeGroup, ctxObj, configsToSave);
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

    private static void handleRecalculateRefinementWeights(Player player, ViewState state, Object ctxObj) {
        String maxLevelInputId = "input_refine_max_level";
        String rawValue = draftOrContextValue(state, ctxObj, maxLevelInputId, String.valueOf(refinementConfig().getMaxLevel()));
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

    private static void handleAddLootInjectionRule(Player player, ViewState state, String groupId) {
        List<String> entries = mutableList(lootInjectionEntries(groupId));
        entries.add(LootInjectionUtils.formatEntry("Item_ID", 0.01d, 1, 1, isNpcLootInjectionGroup(groupId) ? "NPC_ID" : ""));
        setLootInjectionEntries(groupId, entries.toArray(String[]::new));
        plugin.getConfigService().saveAndApply(LOOT_CONFIG_NAME);
        state.statusText = tOrFallback(player, "ui.runtime_config.mapping_added", "Mapping added.");
        requestReopen(player, state);
    }

    private static void handleDeleteLootInjectionRule(Player player, ViewState state, String groupId, int index) {
        List<String> entries = mutableList(lootInjectionEntries(groupId));
        if (index >= 0 && index < entries.size()) {
            entries.remove(index);
            setLootInjectionEntries(groupId, entries.toArray(String[]::new));
            plugin.getConfigService().saveAndApply(LOOT_CONFIG_NAME);
            state.statusText = tOrFallback(player, "ui.runtime_config.mapping_deleted", "Mapping deleted.");
        }
        requestReopen(player, state);
    }

    private static void handleLootInjectionRuleChange(Player player, ViewState state, String groupId, int index, Object ctxObj) {
        List<String> entries = mutableList(lootInjectionEntries(groupId));
        if (index < 0 || index >= entries.size()) {
            requestReopen(player, state);
            return;
        }
        LootRuleEntry entry = parseLootRuleEntry(entries.get(index));
        String itemId = draftOrContextValue(state, ctxObj, lootInjectionItemInputId(groupId, index), entry.itemId()).trim();
        String targetId = isNpcLootInjectionGroup(groupId)
                ? draftOrContextValue(state, ctxObj, lootInjectionTargetInputId(groupId, index), entry.targetId()).trim()
                : "";
        String chance = normalizeDecimalText(draftOrContextValue(state, ctxObj, lootInjectionChanceInputId(groupId, index), entry.chance()), entry.chance(), true);
        int minQty = Math.max(0, parseIntSafe(normalizeIntegerText(draftOrContextValue(state, ctxObj, lootInjectionMinInputId(groupId, index), entry.min()), entry.min()), 1));
        int maxQty = Math.max(minQty, parseIntSafe(normalizeIntegerText(draftOrContextValue(state, ctxObj, lootInjectionMaxInputId(groupId, index), entry.max()), entry.max()), minQty));
        if (!itemId.isBlank()) {
            entries.set(index, LootInjectionUtils.formatEntry(itemId, parseDoubleSafe(chance, 0.0d), minQty, maxQty, targetId));
            setLootInjectionEntries(groupId, entries.toArray(String[]::new));
            plugin.getConfigService().saveAndApply(LOOT_CONFIG_NAME);
            state.statusText = tOrFallback(player, "ui.runtime_config.mapping_updated", "Mapping updated.");
        }
        requestReopen(player, state);
    }

    private static int applyVisibleMappingInputs(ViewState state, ControlGroup activeGroup, Object ctxObj, List<String> configsToSave) {
        if (activeGroup == null) {
            return 0;
        }
        if (GROUP_LORE_MAPPING_GEMS.equals(activeGroup.id)) {
            return applyGemMappingInputs(state, ctxObj, configsToSave);
        }
        if (GROUP_LORE_MAPPING_SPIRITS.equals(activeGroup.id)) {
            return applySpiritMappingInputs(state, ctxObj, configsToSave);
        }
        if (GROUP_LORE_MAPPING_ABILITIES.equals(activeGroup.id)) {
            return applyAbilityMappingInputs(state, ctxObj, configsToSave);
        }
        if (GROUP_LORE_STATUS_RULES.equals(activeGroup.id)) {
            return applyLoreStatusInputs(state, ctxObj, configsToSave);
        }
        if (isLootInjectionGroup(activeGroup.id)) {
            return applyLootInjectionInputs(state, activeGroup.id, ctxObj, configsToSave);
        }
        return 0;
    }

    private static int applyLootInjectionInputs(ViewState state, String groupId, Object ctxObj, List<String> configsToSave) {
        String[] before = lootInjectionEntries(groupId);
        List<String> after = new ArrayList<>();
        for (int i = 0; i < before.length; i++) {
            LootRuleEntry entry = parseLootRuleEntry(before[i]);
            String itemId = draftOrContextValue(state, ctxObj, lootInjectionItemInputId(groupId, i), entry.itemId()).trim();
            String targetId = isNpcLootInjectionGroup(groupId)
                    ? draftOrContextValue(state, ctxObj, lootInjectionTargetInputId(groupId, i), entry.targetId()).trim()
                    : "";
            String chance = normalizeDecimalText(draftOrContextValue(state, ctxObj, lootInjectionChanceInputId(groupId, i), entry.chance()), entry.chance(), true);
            String min = normalizeIntegerText(draftOrContextValue(state, ctxObj, lootInjectionMinInputId(groupId, i), entry.min()), entry.min());
            String max = normalizeIntegerText(draftOrContextValue(state, ctxObj, lootInjectionMaxInputId(groupId, i), entry.max()), entry.max());
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

    private static int applySpiritMappingInputs(ViewState state, Object ctxObj, List<String> configsToSave) {
        String[] before = loreMappingConfig().getColorSpiritEntries();
        List<String> entries = mutableList(before);
        for (String color : LORE_COLORS) {
            List<String> spirits = spiritsForColor(color);
            List<String> updated = new ArrayList<>();
            for (int i = 0; i < spirits.size(); i++) {
                String elementId = spiritMappingSelectId(color, i);
                String spirit = draftOrContextValue(state, ctxObj, elementId, spirits.get(i)).trim();
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

    private static int applyGemMappingInputs(ViewState state, Object ctxObj, List<String> configsToSave) {
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
                String token = draftOrContextValue(state, ctxObj, elementId, entry.key()).trim();
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

    private static int applyAbilityMappingInputs(ViewState state, Object ctxObj, List<String> configsToSave) {
        List<String> before = mutableList(loreMappingConfig().getAbilityEntries());
        List<String> after = new ArrayList<>();
        for (int i = 0; i < before.size(); i++) {
            AbilityEntry ability = parseAbilityEntry(before.get(i));
            String spirit = draftOrContextValue(state, ctxObj, abilitySpiritSelectId(i), ability.spirit()).trim();
            String trigger = normalizeAbilityToken(draftOrContextValue(state, ctxObj, abilityTriggerSelectId(i), ability.trigger()), ability.trigger());
            if (spirit.isBlank()) {
                continue;
            }
            AbilityEntry updated;
            if (isSignatureAbility(trigger)) {
                updated = ability.withSpirit(spirit).asSignature(trigger);
            } else {
                updated = ability.withSpirit(spirit).asNormal(trigger)
                        .withChance(normalizeDecimalText(draftOrContextValue(state, ctxObj, abilityChanceInputId(i), ability.chance()), ability.chance(), false))
                        .withCooldown(normalizeIntegerText(draftOrContextValue(state, ctxObj, abilityCooldownInputId(i), ability.cooldown()), ability.cooldown()))
                        .withEffect(normalizeAbilityToken(draftOrContextValue(state, ctxObj, abilityEffectSelectId(i), ability.effect()), ability.effect()))
                        .withBase(normalizeDecimalText(draftOrContextValue(state, ctxObj, abilityBaseInputId(i), ability.base()), ability.base(), false))
                        .withPerLevel(normalizeDecimalText(draftOrContextValue(state, ctxObj, abilityPerLevelInputId(i), ability.perLevel()), ability.perLevel(), false));
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

    private static int applyLoreStatusInputs(ViewState state, Object ctxObj, List<String> configsToSave) {
        int changed = 0;
        List<StatusRuleEntry> rulesBefore = parseStatusRuleEntries(loreConfig().getStatusBossReapplyRules());
        List<StatusRuleEntry> rulesAfter = new ArrayList<>();
        for (int i = 0; i < rulesBefore.size(); i++) {
            StatusRuleEntry entry = rulesBefore.get(i);
            rulesAfter.add(new StatusRuleEntry(
                    normalizeStatusKeyForUi(draftOrContextValue(state, ctxObj, statusRuleStatusSelectId(i), entry.status())),
                    normalizeIntegerText(draftOrContextValue(state, ctxObj, statusRuleStepInputId(i), entry.step()), entry.step()),
                    normalizeStatusPattern(draftOrContextValue(state, ctxObj, statusRulePatternSelectId(i), entry.pattern()))));
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
                    normalizeNpcIdForUi(draftOrContextValue(state, ctxObj, statusResistanceNpcInputId(i), entry.npcId())),
                    normalizeStatusKeyForUi(draftOrContextValue(state, ctxObj, statusResistanceStatusSelectId(i), entry.status())),
                    normalizeDecimalText(draftOrContextValue(state, ctxObj, statusResistanceValueInputId(i), entry.value()), entry.value(), true)));
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
                    normalizeNpcIdForUi(draftOrContextValue(state, ctxObj, statusCounterNpcInputId(i), entry.npcId())),
                    normalizeStatusKeyForUi(draftOrContextValue(state, ctxObj, statusCounterStatusSelectId(i), entry.status()))));
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
            String npcId = normalizeNpcIdForUi(draftOrContextValue(state, ctxObj, bleedCounterNpcInputId(i), bleedBefore.get(i)));
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

    private static void handleGemMappingTokenChange(Player player, ViewState state, int index, Object eventObj, Object ctxObj) {
        String rawValue = HyUIReflectionUtils.extractEventValue(eventObj);
        if (rawValue == null) {
            rawValue = HyUIReflectionUtils.getContextValue(ctxObj, gemMappingTokenInputId(index), "#" + gemMappingTokenInputId(index) + ".value");
        }
        String token = rawValue == null ? "" : rawValue.trim();
        List<String> entries = mutableList(loreMappingConfig().getGemColorEntries());
        if (index >= 0 && index < entries.size() && !token.isBlank()) {
            KeyValue entry = parseKeyValue(entries.get(index));
            entries.set(index, token + "=" + normalizeLoreColor(entry.value()));
            saveGemColorEntries(entries);
            state.statusText = tOrFallback(player, "ui.runtime_config.mapping_updated", "Mapping updated.");
        }
        requestReopen(player, state);
    }

    private static void handleToggleCoreColor(Player player, ViewState state, String color) {
        String normalizedColor = normalizeLoreColor(color);
        List<String> colors = mutableList(loreMappingConfig().getCoreColorEntries());
        boolean removed = colors.removeIf(entry -> normalizedColor.equals(normalizeLoreColor(entry)));
        if (!removed) {
            colors.add(normalizedColor);
        }
        loreMappingConfig().setCoreColorEntries(colors.toArray(String[]::new));
        plugin.getConfigService().saveAndApply(LORE_MAPPING_CONFIG_NAME);
        state.statusText = tOrFallback(player, "ui.runtime_config.mapping_updated", "Mapping updated.");
        requestReopen(player, state);
    }

    private static void handleAddSpiritMapping(Player player, ViewState state, String color) {
        String nextSpirit = firstAvailableSpirit(color);
        if (nextSpirit == null || nextSpirit.isBlank()) {
            state.statusText = tOrFallback(player, "ui.runtime_config.mapping_no_spirits", "No spirit entries available.");
            requestReopen(player, state);
            return;
        }
        List<String> spirits = spiritsForColor(color);
        spirits.add(nextSpirit);
        setSpiritsForColor(color, spirits);
        state.statusText = tOrFallback(player, "ui.runtime_config.mapping_added", "Mapping added.");
        requestReopen(player, state);
    }

    private static void handleDeleteSpiritMapping(Player player, ViewState state, String color, int index) {
        List<String> spirits = spiritsForColor(color);
        if (index >= 0 && index < spirits.size()) {
            spirits.remove(index);
            setSpiritsForColor(color, spirits);
            state.statusText = tOrFallback(player, "ui.runtime_config.mapping_deleted", "Mapping deleted.");
        }
        requestReopen(player, state);
    }

    private static void handleSpiritMappingChange(Player player, ViewState state, String color, int index, Object eventObj, Object ctxObj) {
        String rawValue = HyUIReflectionUtils.extractEventValue(eventObj);
        if (rawValue == null) {
            rawValue = HyUIReflectionUtils.getContextValue(ctxObj, spiritMappingSelectId(color, index), "#" + spiritMappingSelectId(color, index) + ".value");
        }
        if (rawValue != null) {
            state.draftValues.put(spiritMappingSelectId(color, index), rawValue);
        }
    }

    private static void handleAddAbilityMapping(Player player, ViewState state) {
        List<String> entries = mutableList(loreMappingConfig().getAbilityEntries());
        String spirit = firstAvailableAbilitySpirit(entries);
        entries.add(spirit + "=ON_HIT,0.10,2000,DAMAGE_TARGET,2.0,0.10");
        saveAbilityEntries(entries);
        state.statusText = tOrFallback(player, "ui.runtime_config.mapping_added", "Mapping added.");
        requestReopen(player, state);
    }

    private static void handleDeleteAbilityMapping(Player player, ViewState state, int index) {
        List<String> entries = mutableList(loreMappingConfig().getAbilityEntries());
        if (index >= 0 && index < entries.size()) {
            entries.remove(index);
            saveAbilityEntries(entries);
            state.statusText = tOrFallback(player, "ui.runtime_config.mapping_deleted", "Mapping deleted.");
        }
        requestReopen(player, state);
    }

    private static void handleAbilitySpiritChange(Player player, ViewState state, int index, Object eventObj, Object ctxObj) {
        String spirit = extractMappingEventValue(eventObj, ctxObj, abilitySpiritSelectId(index));
        List<String> entries = mutableList(loreMappingConfig().getAbilityEntries());
        if (index >= 0 && index < entries.size() && spirit != null && !spirit.isBlank()) {
            AbilityEntry ability = parseAbilityEntry(entries.get(index));
            entries.set(index, formatAbilityEntry(ability.withSpirit(spirit.trim())));
            saveAbilityEntries(entries);
            state.statusText = tOrFallback(player, "ui.runtime_config.mapping_updated", "Mapping updated.");
        }
        requestReopen(player, state);
    }

    private static void handleAbilityTriggerChange(Player player, ViewState state, int index, Object eventObj, Object ctxObj) {
        String trigger = normalizeAbilityToken(extractMappingEventValue(eventObj, ctxObj, abilityTriggerSelectId(index)), "ON_HIT");
        List<String> entries = mutableList(loreMappingConfig().getAbilityEntries());
        if (index >= 0 && index < entries.size()) {
            AbilityEntry ability = parseAbilityEntry(entries.get(index));
            if (isSignatureAbility(trigger)) {
                entries.set(index, formatAbilityEntry(ability.asSignature(trigger)));
            } else {
                entries.set(index, formatAbilityEntry(ability.asNormal(trigger)));
            }
            saveAbilityEntries(entries);
            state.statusText = tOrFallback(player, "ui.runtime_config.mapping_updated", "Mapping updated.");
        }
        requestReopen(player, state);
    }

    private static void handleAbilityFieldChange(Player player, ViewState state, int index, AbilityField field, Object eventObj, Object ctxObj) {
        String elementId = switch (field) {
            case CHANCE -> abilityChanceInputId(index);
            case COOLDOWN -> abilityCooldownInputId(index);
            case EFFECT -> abilityEffectSelectId(index);
            case BASE -> abilityBaseInputId(index);
            case PER_LEVEL -> abilityPerLevelInputId(index);
        };
        String value = extractMappingEventValue(eventObj, ctxObj, elementId);
        List<String> entries = mutableList(loreMappingConfig().getAbilityEntries());
        if (index >= 0 && index < entries.size()) {
            AbilityEntry ability = parseAbilityEntry(entries.get(index));
            AbilityEntry updated = switch (field) {
                case CHANCE -> ability.withChance(normalizeDecimalText(value, ability.chance(), false));
                case COOLDOWN -> ability.withCooldown(normalizeIntegerText(value, ability.cooldown()));
                case EFFECT -> ability.withEffect(normalizeAbilityToken(value, ability.effect()));
                case BASE -> ability.withBase(normalizeDecimalText(value, ability.base(), false));
                case PER_LEVEL -> ability.withPerLevel(normalizeDecimalText(value, ability.perLevel(), false));
            };
            entries.set(index, formatAbilityEntry(updated));
            saveAbilityEntries(entries);
            state.statusText = tOrFallback(player, "ui.runtime_config.mapping_updated", "Mapping updated.");
        }
        requestReopen(player, state);
    }

    private static void handleAddStatusRule(Player player, ViewState state) {
        List<StatusRuleEntry> entries = parseStatusRuleEntries(loreConfig().getStatusBossReapplyRules());
        entries.add(new StatusRuleEntry("bleed", "5", "FIBONACCI"));
        saveStatusRuleEntries(entries);
        state.statusText = tOrFallback(player, "ui.runtime_config.mapping_added", "Mapping added.");
        requestReopen(player, state);
    }

    private static void handleDeleteStatusRule(Player player, ViewState state, int index) {
        List<StatusRuleEntry> entries = parseStatusRuleEntries(loreConfig().getStatusBossReapplyRules());
        if (index >= 0 && index < entries.size()) {
            entries.remove(index);
            saveStatusRuleEntries(entries);
            state.statusText = tOrFallback(player, "ui.runtime_config.mapping_deleted", "Mapping deleted.");
        }
        requestReopen(player, state);
    }

    private static void handleStatusRuleChange(Player player, ViewState state, int index, StatusRuleField field, Object eventObj, Object ctxObj) {
        String elementId = switch (field) {
            case STATUS -> statusRuleStatusSelectId(index);
            case STEP -> statusRuleStepInputId(index);
            case PATTERN -> statusRulePatternSelectId(index);
        };
        String value = extractMappingEventValue(eventObj, ctxObj, elementId);
        List<StatusRuleEntry> entries = parseStatusRuleEntries(loreConfig().getStatusBossReapplyRules());
        if (index >= 0 && index < entries.size()) {
            StatusRuleEntry entry = entries.get(index);
            entries.set(index, switch (field) {
                case STATUS -> new StatusRuleEntry(normalizeStatusKeyForUi(value), entry.step(), entry.pattern());
                case STEP -> new StatusRuleEntry(entry.status(), normalizeIntegerText(value, entry.step()), entry.pattern());
                case PATTERN -> new StatusRuleEntry(entry.status(), entry.step(), normalizeStatusPattern(value));
            });
            saveStatusRuleEntries(entries);
            state.statusText = tOrFallback(player, "ui.runtime_config.mapping_updated", "Mapping updated.");
        }
        requestReopen(player, state);
    }

    private static void handleAddStatusResistance(Player player, ViewState state) {
        List<StatusResistanceEntry> entries = parseStatusResistanceEntries(loreConfig().getNpcStatusResistanceEntries());
        entries.add(new StatusResistanceEntry("NPC_ID", "bleed", "1.0"));
        saveStatusResistanceEntries(entries);
        state.statusText = tOrFallback(player, "ui.runtime_config.mapping_added", "Mapping added.");
        requestReopen(player, state);
    }

    private static void handleDeleteStatusResistance(Player player, ViewState state, int index) {
        List<StatusResistanceEntry> entries = parseStatusResistanceEntries(loreConfig().getNpcStatusResistanceEntries());
        if (index >= 0 && index < entries.size()) {
            entries.remove(index);
            saveStatusResistanceEntries(entries);
            state.statusText = tOrFallback(player, "ui.runtime_config.mapping_deleted", "Mapping deleted.");
        }
        requestReopen(player, state);
    }

    private static void handleStatusResistanceChange(Player player, ViewState state, int index, StatusResistanceField field, Object eventObj, Object ctxObj) {
        String elementId = switch (field) {
            case NPC -> statusResistanceNpcInputId(index);
            case STATUS -> statusResistanceStatusSelectId(index);
            case VALUE -> statusResistanceValueInputId(index);
        };
        String value = extractMappingEventValue(eventObj, ctxObj, elementId);
        List<StatusResistanceEntry> entries = parseStatusResistanceEntries(loreConfig().getNpcStatusResistanceEntries());
        if (index >= 0 && index < entries.size()) {
            StatusResistanceEntry entry = entries.get(index);
            entries.set(index, switch (field) {
                case NPC -> new StatusResistanceEntry(normalizeNpcIdForUi(value), entry.status(), entry.value());
                case STATUS -> new StatusResistanceEntry(entry.npcId(), normalizeStatusKeyForUi(value), entry.value());
                case VALUE -> new StatusResistanceEntry(entry.npcId(), entry.status(), normalizeDecimalText(value, entry.value(), true));
            });
            saveStatusResistanceEntries(entries);
            state.statusText = tOrFallback(player, "ui.runtime_config.mapping_updated", "Mapping updated.");
        }
        requestReopen(player, state);
    }

    private static void handleAddStatusCounter(Player player, ViewState state) {
        List<StatusCounterEntry> entries = parseStatusCounterEntries(loreConfig().getStatusBossCounterNpcIds());
        entries.add(new StatusCounterEntry("NPC_ID", "bleed"));
        saveStatusCounterEntries(entries);
        state.statusText = tOrFallback(player, "ui.runtime_config.mapping_added", "Mapping added.");
        requestReopen(player, state);
    }

    private static void handleDeleteStatusCounter(Player player, ViewState state, int index) {
        List<StatusCounterEntry> entries = parseStatusCounterEntries(loreConfig().getStatusBossCounterNpcIds());
        if (index >= 0 && index < entries.size()) {
            entries.remove(index);
            saveStatusCounterEntries(entries);
            state.statusText = tOrFallback(player, "ui.runtime_config.mapping_deleted", "Mapping deleted.");
        }
        requestReopen(player, state);
    }

    private static void handleStatusCounterChange(Player player, ViewState state, int index, StatusCounterField field, Object eventObj, Object ctxObj) {
        String elementId = field == StatusCounterField.NPC
                ? statusCounterNpcInputId(index)
                : statusCounterStatusSelectId(index);
        String value = extractMappingEventValue(eventObj, ctxObj, elementId);
        List<StatusCounterEntry> entries = parseStatusCounterEntries(loreConfig().getStatusBossCounterNpcIds());
        if (index >= 0 && index < entries.size()) {
            StatusCounterEntry entry = entries.get(index);
            entries.set(index, field == StatusCounterField.NPC
                    ? new StatusCounterEntry(normalizeNpcIdForUi(value), entry.status())
                    : new StatusCounterEntry(entry.npcId(), normalizeStatusKeyForUi(value)));
            saveStatusCounterEntries(entries);
            state.statusText = tOrFallback(player, "ui.runtime_config.mapping_updated", "Mapping updated.");
        }
        requestReopen(player, state);
    }

    private static void handleAddBleedCounter(Player player, ViewState state) {
        List<String> entries = mutableList(loreConfig().getBleedBossCounterNpcIds());
        entries.add("NPC_ID");
        saveBleedCounterEntries(entries);
        state.statusText = tOrFallback(player, "ui.runtime_config.mapping_added", "Mapping added.");
        requestReopen(player, state);
    }

    private static void handleDeleteBleedCounter(Player player, ViewState state, int index) {
        List<String> entries = mutableList(loreConfig().getBleedBossCounterNpcIds());
        if (index >= 0 && index < entries.size()) {
            entries.remove(index);
            saveBleedCounterEntries(entries);
            state.statusText = tOrFallback(player, "ui.runtime_config.mapping_deleted", "Mapping deleted.");
        }
        requestReopen(player, state);
    }

    private static void handleBleedCounterChange(Player player, ViewState state, int index, Object eventObj, Object ctxObj) {
        String npcId = extractMappingEventValue(eventObj, ctxObj, bleedCounterNpcInputId(index));
        List<String> entries = mutableList(loreConfig().getBleedBossCounterNpcIds());
        if (index >= 0 && index < entries.size()) {
            entries.set(index, normalizeNpcIdForUi(npcId));
            saveBleedCounterEntries(entries);
            state.statusText = tOrFallback(player, "ui.runtime_config.mapping_updated", "Mapping updated.");
        }
        requestReopen(player, state);
    }

    private static void requestReopen(Player player, ViewState state) {
        if (player == null) {
            return;
        }
        PlayerRef playerRef = player.getPlayerRef();
        viewStates.put(playerRef, state);
        player.getWorld().execute(() -> openWithSync(player));
    }

    private static String buildCategoriesHtml(Player player, ViewState state) {
        StringBuilder sb = new StringBuilder();
        CategorySection activeCategory = resolveActiveCategory(state);
        sb.append("<div style=\"layout-mode:Top; spacing:10;\">");
        sb.append("<div style=\"anchor-width:")
                .append(CATEGORY_WIDTH)
                .append("; anchor-height:52; layout-mode:Left; spacing:8; background-color:#0f1520; padding:10; border-radius:6; overflow-x:auto; overflow-y:hidden;\">");
        for (CategorySection category : categories) {
            sb.append(buildNavigationButton(
                    category.toggleButtonId,
                    localizedCategoryTitle(player, category),
                    category.id.equals(state.activeCategoryId),
                    188));
        }
        sb.append("</div>");
        if (activeCategory != null) {
            sb.append(buildCategoryHtml(player, activeCategory, state));
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static String buildCategoryTabsHtml(Player player, ViewState state) {
        StringBuilder sb = new StringBuilder();
        String activeId = state == null ? "" : state.activeCategoryId;
        for (CategorySection category : categories) {
            sb.append(buildNavigationButton(
                    category.toggleButtonId,
                    localizedCategoryTitle(player, category),
                    category.id.equals(activeId),
                    180));
        }
        return sb.toString();
    }

    private static String buildGroupNavHtml(Player player, CategorySection category, ControlGroup activeGroup) {
        if (category == null || category.groups.isEmpty()) {
            return "<p style=\"font-size:11; color:#b0b0c2;\">" + escapeHtml(t(player, "ui.runtime_config.empty_sections")) + "</p>";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"layout-mode: top; spacing: 6;\">");
        sb.append("<p style=\"font-size:11; color:#D8C27A; font-weight:bold;\">")
                .append(escapeHtml(localizedCategoryTitle(player, category)))
                .append("</p>");
        String categorySummary = localizedCategorySummary(player, category);
        if (!categorySummary.isBlank()) {
            sb.append("<p style=\"font-size:10; color:#9EA8B5;\">")
                    .append(escapeHtml(categorySummary))
                    .append("</p>");
        }
        for (ControlGroup group : category.groups) {
            sb.append(buildNavigationButton(
                    group.buttonId(category.id),
                    localizedGroupTitle(player, category, group),
                    activeGroup != null && group.id.equals(activeGroup.id),
                    260));
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static String buildCategorySummaryHtml(CategorySection category) {
        if (category == null) {
            return buildPickerSummaryCard("No categories available.", "");
        }
        return buildPickerSummaryCard(category.title, category.summary);
    }

    private static String buildGroupSummaryHtml(CategorySection category, ControlGroup group) {
        if (category == null) {
            return buildPickerSummaryCard("Select a category first.", "");
        }
        if (group == null) {
            return buildPickerSummaryCard("No sections available.", "");
        }
        return buildPickerSummaryCard(group.title, group.description);
    }

    private static String buildPickerSummaryCard(String title, String summary) {
        String safeTitle = title == null ? "" : title;
        String safeSummary = summary == null ? "" : summary;
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"anchor-height:72; anchor-width:440; layout-mode:Top; padding:6; background-image:url(output_bg.png); background-size:100% 100%; background-repeat:no-repeat; background-position:center;\">");
        sb.append("<p style=\"font-weight:bold;\">").append(escapeHtml(safeTitle)).append("</p>");
        if (!safeSummary.isBlank()) {
            sb.append("<p style=\"font-size:11; color:#b0b0c2;\">").append(escapeHtml(safeSummary)).append("</p>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static String buildControlsHtml(Player player, CategorySection category, ControlGroup group, ViewState state) {
        if (category == null) {
            return "<p>" + escapeHtml(t(player, "ui.runtime_config.empty_categories")) + "</p>";
        }
        if (group == null) {
            return "<p>" + escapeHtml(t(player, "ui.runtime_config.empty_sections")) + "</p>";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"layout-mode: top; spacing: 4; padding-bottom: 8;\">");
        sb.append("<p style=\"font-size:12; color:#D8C27A; font-weight:bold;\">")
                .append(escapeHtml(localizedGroupTitle(player, category, group)))
                .append("</p>");
        String groupDescription = localizedGroupDescription(player, category, group);
        if (!groupDescription.isBlank()) {
            sb.append("<p style=\"font-size:10; color:#9EA8B5;\">")
                    .append(escapeHtml(groupDescription))
                    .append("</p>");
        }
        sb.append("</div>");
        sb.append(buildControlsListHtml(player, group, state));
        String noteText = localizedCategoryNote(player, category);
        if (!noteText.isBlank()) {
            sb.append("<div style=\"layout-mode:Top; padding:8; background-color:#191919; border-radius:4;\">")
                    .append("<p style=\"color:#C9B26D;\">")
                    .append(escapeHtml(noteText))
                    .append("</p></div>");
        }
        return sb.toString();
    }

    private static String buildControlsListHtml(Player player, ControlGroup group, ViewState state) {
        if (group == null) {
            return "<p>" + escapeHtml(t(player, "ui.runtime_config.empty_controls")) + "</p>";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div id=\"controlsList\" style=\"layout-mode: top; spacing: 6; anchor-width: 1100; background-color: #121B29; padding: 10; overflow-x:auto; overflow-y:visible;\">");
        if (GROUP_LORE_MAPPING_GEMS.equals(group.id)) {
            sb.append(buildGemMappingCardsHtml(player));
        } else if (GROUP_LORE_MAPPING_SPIRITS.equals(group.id)) {
            sb.append(buildSpiritMappingCardsHtml(player));
        } else if (GROUP_LORE_MAPPING_ABILITIES.equals(group.id)) {
            sb.append(buildAbilityMappingCardsHtml(player));
        } else if (GROUP_LORE_STATUS_RULES.equals(group.id)) {
            sb.append(buildLoreStatusCardsHtml(player));
        } else if (isLootInjectionGroup(group.id)) {
            sb.append(buildLootInjectionCardsHtml(player, group.id));
        } else if ("refine_limits".equals(group.id)) {
            sb.append(buildRefineLimitsWithWeightCardsHtml(player, group, state));
        } else if (group.controls == null || group.controls.isEmpty()) {
            sb.append("<p style=\"font-size:11; color:#b0b0c2;\">").append(escapeHtml(t(player, "ui.runtime_config.empty_controls_section"))).append("</p>");
        } else {
            int singleColumnWidth = 1100;
            for (ControlEntry entry : group.controls) {
                if (entry instanceof NumericControl control) {
                    sb.append(buildControlCell(player, control, singleColumnWidth, state));
                } else if (entry instanceof TextControl control) {
                    sb.append(buildTextControlCell(player, control, singleColumnWidth, state));
                }
            }
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static String buildGemMappingCardsHtml(Player player) {
        StringBuilder sb = new StringBuilder();
        String[] entries = loreMappingConfig().getGemColorEntries();
        for (String color : LORE_COLORS) {
            sb.append("<div style=\"layout-mode:Top; spacing:4; anchor-width:730; padding:6; background-color:#0d131d; border-radius:4;\">");
            sb.append("<div style=\"layout-mode:Left; spacing:6; anchor-width:710; anchor-height:28; padding-left:6;\">");
            sb.append("<p style=\"font-size:12; color:")
                    .append(loreColorHex(color))
                    .append("; font-weight:bold; anchor-width:120;\">")
                    .append(escapeHtml(localizedLoreColor(player, color)))
                    .append("</p>");
            String coreKey = isCoreLoreColor(color) ? "ui.runtime_config.mapping_core_on" : "ui.runtime_config.mapping_core_off";
            String coreFallback = isCoreLoreColor(color) ? "Core: On" : "Core: Off";
            sb.append("<button id=\"").append(coreColorToggleButtonId(color))
                    .append("\" class=\"secondary-button default-style\" style=\"anchor-width:110; anchor-height:24;\">")
                    .append(escapeHtml(tOrFallback(player, coreKey, coreFallback)))
                    .append("</button>");
            sb.append("</div>");

            boolean any = false;
            for (int i = 0; i < entries.length; i++) {
                KeyValue entry = parseKeyValue(entries[i]);
                if (!color.equals(normalizeLoreColor(entry.value()))) {
                    continue;
                }
                any = true;
                sb.append("<div style=\"layout-mode:Left; spacing:6; anchor-width:710; anchor-height:38; padding:5; background-color:#172033; border-radius:3;\">");
                sb.append("<input type=\"text\" id=\"").append(gemMappingTokenInputId(i))
                        .append("\" style=\"anchor-width:640; anchor-height:26; background-color:#1b2332; padding:5;\" value=\"")
                        .append(escapeHtml(entry.key()))
                        .append("\">");
                sb.append("</div>");
            }
            if (!any) {
                sb.append("<p style=\"font-size:10; color:#78849A;\">")
                        .append(escapeHtml(tOrFallback(player, "ui.runtime_config.mapping_empty_color", "No mappings for this color.")))
                        .append("</p>");
            }
            sb.append("</div>");
        }
        return sb.toString();
    }

    private static String buildSpiritMappingCardsHtml(Player player) {
        StringBuilder sb = new StringBuilder();
        for (String color : LORE_COLORS) {
            List<String> spirits = spiritsForColor(color);
            sb.append("<div style=\"layout-mode:Top; spacing:4; anchor-width:730; padding:6; background-color:#0d131d; border-radius:4;\">");
            sb.append("<div style=\"layout-mode:Left; spacing:6; anchor-width:710; anchor-height:28; padding-left:6;\">");
            sb.append("<p style=\"font-size:12; color:")
                    .append(loreColorHex(color))
                    .append("; font-weight:bold; anchor-width:120;\">")
                    .append(escapeHtml(localizedLoreColor(player, color)))
                    .append("</p>");
            sb.append("<button id=\"").append(spiritMappingAddButtonId(color))
                    .append("\" class=\"secondary-button default-style\" style=\"anchor-width:34; anchor-height:24;\">")
                    .append("+")
                    .append("</button>");
            sb.append("</div>");

            if (spirits.isEmpty()) {
                sb.append("<p style=\"font-size:10; color:#78849A;\">")
                        .append(escapeHtml(tOrFallback(player, "ui.runtime_config.mapping_all_spirits", "No spirit cards yet. Empty means all spirits are allowed.")))
                        .append("</p>");
            }
            for (int i = 0; i < spirits.size(); i++) {
                String selected = spirits.get(i);
                sb.append("<div style=\"layout-mode:Left; spacing:6; anchor-width:710; anchor-height:38; padding:5; background-color:#172033; border-radius:3;\">");
                sb.append("<input type=\"text\" id=\"").append(spiritMappingSelectId(color, i))
                        .append("\" style=\"anchor-width:390; anchor-height:26; background-color:#1b2332; padding:5;\" value=\"")
                        .append(escapeHtml(selected))
                        .append("\">");
                sb.append("<button id=\"").append(spiritMappingDeleteButtonId(color, i))
                        .append("\" class=\"secondary-button default-style\" style=\"anchor-width:34; anchor-height:26;\">")
                        .append("x")
                        .append("</button>");
                sb.append("</div>");
            }
            sb.append("</div>");
        }
        appendBlankSpiritMappingSpacerCards(sb);
        return sb.toString();
    }

    private static String buildLootInjectionCardsHtml(Player player, String groupId) {
        StringBuilder sb = new StringBuilder();
        String[] entries = lootInjectionEntries(groupId);
        boolean npcGroup = isNpcLootInjectionGroup(groupId);
        sb.append("<div style=\"layout-mode:Top; spacing:6; anchor-width:1060; padding:6; background-color:#0d131d; border-radius:4;\">");
        sb.append("<div style=\"layout-mode:Left; spacing:8; anchor-width:1040; anchor-height:30; padding-left:6;\">");
        sb.append("<p style=\"font-size:12; color:#F1E2A4; font-weight:bold; anchor-width:260;\">")
                .append(escapeHtml(lootInjectionGroupTitle(player, groupId)))
                .append("</p>");
        sb.append("<button id=\"").append(lootInjectionAddButtonId(groupId))
                .append("\" class=\"secondary-button default-style\" style=\"anchor-width:34; anchor-height:24;\">+</button>");
        sb.append("</div>");
        sb.append("<div style=\"layout-mode:Left; spacing:6; anchor-width:1040; anchor-height:20; padding-left:8;\">")
                .append("<p style=\"font-size:10; color:#9EA8B5; anchor-width:")
                .append(npcGroup ? 330 : 460)
                .append(";\">Item ID</p>");
        if (npcGroup) {
            sb.append("<p style=\"font-size:10; color:#9EA8B5; anchor-width:190;\">NPC / Role ID</p>");
        }
        sb.append("<p style=\"font-size:10; color:#9EA8B5; anchor-width:110;\">Chance</p>");
        sb.append("<p style=\"font-size:10; color:#9EA8B5; anchor-width:70;\">Min</p>");
        sb.append("<p style=\"font-size:10; color:#9EA8B5; anchor-width:70;\">Max</p>");
        sb.append("</div>");
        if (entries.length == 0) {
            sb.append("<p style=\"font-size:10; color:#78849A;\">")
                    .append(escapeHtml(tOrFallback(player, "ui.runtime_config.mapping_empty_loot_rules", "No injected loot rules.")))
                    .append("</p>");
        }
        for (int i = 0; i < entries.length; i++) {
            LootRuleEntry entry = parseLootRuleEntry(entries[i]);
            sb.append("<div style=\"layout-mode:Left; spacing:6; anchor-width:1040; anchor-height:42; padding:7; background-color:#172033; border-radius:3;\">");
            sb.append("<input type=\"text\" id=\"").append(lootInjectionItemInputId(groupId, i))
                    .append("\" style=\"anchor-width:").append(npcGroup ? 330 : 460).append("; anchor-height:26; background-color:#1b2332; padding:5;\" value=\"")
                    .append(escapeHtml(entry.itemId())).append("\">");
            if (npcGroup) {
                sb.append("<input type=\"text\" id=\"").append(lootInjectionTargetInputId(groupId, i))
                        .append("\" style=\"anchor-width:190; anchor-height:26; background-color:#1b2332; padding:5;\" value=\"")
                        .append(escapeHtml(entry.targetId())).append("\">");
            }
            sb.append("<input type=\"text\" id=\"").append(lootInjectionChanceInputId(groupId, i))
                    .append("\" style=\"anchor-width:110; anchor-height:26; background-color:#1b2332; padding:5;\" value=\"")
                    .append(escapeHtml(entry.chance())).append("\">");
            sb.append("<input type=\"text\" id=\"").append(lootInjectionMinInputId(groupId, i))
                    .append("\" style=\"anchor-width:70; anchor-height:26; background-color:#1b2332; padding:5;\" value=\"")
                    .append(escapeHtml(entry.min())).append("\">");
            sb.append("<input type=\"text\" id=\"").append(lootInjectionMaxInputId(groupId, i))
                    .append("\" style=\"anchor-width:70; anchor-height:26; background-color:#1b2332; padding:5;\" value=\"")
                    .append(escapeHtml(entry.max())).append("\">");
            sb.append("<button id=\"").append(lootInjectionDeleteButtonId(groupId, i))
                    .append("\" class=\"secondary-button default-style\" style=\"anchor-width:34; anchor-height:26;\">x</button>");
            sb.append("</div>");
        }
        for (int i = 0; i < 4; i++) {
            sb.append("<div style=\"anchor-width:1040; anchor-height:38; padding:5; background-color:#101827; border-radius:3;\"></div>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static void appendBlankSpiritMappingSpacerCards(StringBuilder sb) {
        for (int i = 0; i < SPIRIT_MAPPING_BOTTOM_SPACER_CARDS; i++) {
            sb.append("<div style=\"anchor-width:730; anchor-height:38; padding:5; background-color:#101827; border-radius:3;\"></div>");
        }
    }

    private static String buildAbilityMappingCardsHtml(Player player) {
        StringBuilder sb = new StringBuilder();
        String[] entries = loreMappingConfig().getAbilityEntries();
        List<String> allSpirits = allSpiritIds();
        sb.append("<div style=\"layout-mode:Top; spacing:6; anchor-width:1060; padding:6; background-color:#0d131d; border-radius:4;\">");
        sb.append("<div style=\"layout-mode:Left; spacing:8; anchor-width:1040; anchor-height:28; padding-left:6;\">");
        sb.append("<p style=\"font-size:12; color:#F1E2A4; font-weight:bold; anchor-width:190;\">")
                .append(escapeHtml(tOrFallback(player, "ui.runtime_config.mapping_ability_title", "Spirit Abilities")))
                .append("</p>");
        sb.append("<button id=\"").append(abilityMappingAddButtonId())
                .append("\" class=\"secondary-button default-style\" style=\"anchor-width:34; anchor-height:24;\">+</button>");
        sb.append("</div>");

        if (entries == null || entries.length == 0) {
            sb.append("<p style=\"font-size:10; color:#78849A;\">")
                    .append(escapeHtml(tOrFallback(player, "ui.runtime_config.mapping_empty_abilities", "No spirit ability cards yet.")))
                    .append("</p>");
        } else {
            for (int i = 0; i < entries.length; i++) {
                AbilityEntry ability = parseAbilityEntry(entries[i]);
                sb.append("<div style=\"layout-mode:Left; spacing:6; anchor-width:1040; anchor-height:42; padding:7; background-color:#172033; border-radius:3;\">");
                sb.append("<select id=\"").append(abilitySpiritSelectId(i))
                        .append("\"").append(COMPACT_DROPDOWN_ATTRS)
                        .append(" style=\"anchor-width:220; anchor-height:26; background-color:#1b2332;\">")
                        .append(buildSpiritOptions(allSpirits, ability.spirit()))
                        .append("</select>");
                sb.append("<select id=\"").append(abilityTriggerSelectId(i))
                        .append("\"").append(COMPACT_DROPDOWN_ATTRS)
                        .append(" style=\"anchor-width:250; anchor-height:26; background-color:#1b2332;\">")
                        .append(buildAbilityTriggerOptions(ability.trigger()))
                        .append("</select>");
                if (ability.signature()) {
                    sb.append("<p style=\"font-size:10; color:#B8B8C8; anchor-width:430;\">")
                            .append(escapeHtml(tOrFallback(player, "ui.runtime_config.mapping_signature_preset", "Signature preset uses built-in trigger, chance, cooldown, and effect values.")))
                            .append("</p>");
                } else {
                    sb.append("<input type=\"text\" id=\"").append(abilityChanceInputId(i))
                            .append("\" style=\"anchor-width:64; anchor-height:26; background-color:#1b2332; padding:5;\" value=\"")
                            .append(escapeHtml(ability.chance())).append("\">");
                    sb.append("<input type=\"text\" id=\"").append(abilityCooldownInputId(i))
                            .append("\" style=\"anchor-width:82; anchor-height:26; background-color:#1b2332; padding:5;\" value=\"")
                            .append(escapeHtml(ability.cooldown())).append("\">");
                    sb.append("<select id=\"").append(abilityEffectSelectId(i))
                            .append("\"").append(COMPACT_DROPDOWN_ATTRS)
                            .append(" style=\"anchor-width:220; anchor-height:26; background-color:#1b2332;\">")
                            .append(buildOptions(LORE_EFFECTS, ability.effect()))
                            .append("</select>");
                    sb.append("<input type=\"text\" id=\"").append(abilityBaseInputId(i))
                            .append("\" style=\"anchor-width:58; anchor-height:26; background-color:#1b2332; padding:5;\" value=\"")
                            .append(escapeHtml(ability.base())).append("\">");
                    sb.append("<input type=\"text\" id=\"").append(abilityPerLevelInputId(i))
                            .append("\" style=\"anchor-width:58; anchor-height:26; background-color:#1b2332; padding:5;\" value=\"")
                            .append(escapeHtml(ability.perLevel())).append("\">");
                }
                sb.append("<button id=\"").append(abilityDeleteButtonId(i))
                        .append("\" class=\"secondary-button default-style\" style=\"anchor-width:34; anchor-height:26;\">x</button>");
                sb.append("</div>");
            }
        }
        appendBlankAbilitySpacerCards(sb);
        sb.append("</div>");
        return sb.toString();
    }

    private static void appendBlankAbilitySpacerCards(StringBuilder sb) {
        for (int i = 0; i < ABILITY_MAPPING_BOTTOM_SPACER_CARDS; i++) {
            sb.append("<div style=\"anchor-width:1040; anchor-height:42; padding:7; background-color:#101827; border-radius:3;\"></div>");
        }
    }

    private static String buildLoreStatusCardsHtml(Player player) {
        StringBuilder sb = new StringBuilder();
        sb.append(buildStatusRuleCardsHtml(player));
        sb.append(buildStatusResistanceCardsHtml(player));
        sb.append(buildStatusCounterCardsHtml(player));
        sb.append(buildBleedCounterCardsHtml(player));
        return sb.toString();
    }

    private static String buildStatusRuleCardsHtml(Player player) {
        List<StatusRuleEntry> entries = parseStatusRuleEntries(loreConfig().getStatusBossReapplyRules());
        StringBuilder sb = new StringBuilder();
        sb.append(buildMappingSectionHeader(player, "ui.runtime_config.mapping_status_reapply_title", "Boss Reapply Rules", statusRuleAddButtonId()));
        if (entries.isEmpty()) {
            sb.append(buildEmptyMappingText(player, "ui.runtime_config.mapping_empty_status_rules", "No reapply rules yet."));
        }
        for (int i = 0; i < entries.size(); i++) {
            StatusRuleEntry entry = entries.get(i);
            sb.append("<div style=\"layout-mode:Left; spacing:6; anchor-width:710; anchor-height:38; padding:5; background-color:#172033; border-radius:3;\">");
            sb.append("<select id=\"").append(statusRuleStatusSelectId(i))
                    .append("\"").append(COMPACT_DROPDOWN_ATTRS)
                    .append(" style=\"anchor-width:130; anchor-height:26; background-color:#1b2332;\">")
                    .append(buildOptions(LORE_STATUS_KEYS, entry.status())).append("</select>");
            sb.append("<input type=\"text\" id=\"").append(statusRuleStepInputId(i))
                    .append("\" style=\"anchor-width:80; anchor-height:26; background-color:#1b2332; padding:5;\" value=\"")
                    .append(escapeHtml(entry.step())).append("\">");
            sb.append("<select id=\"").append(statusRulePatternSelectId(i))
                    .append("\"").append(COMPACT_DROPDOWN_ATTRS)
                    .append(" style=\"anchor-width:130; anchor-height:26; background-color:#1b2332;\">")
                    .append(buildOptions(LORE_STATUS_PATTERNS, entry.pattern())).append("</select>");
            sb.append("<button id=\"").append(statusRuleDeleteButtonId(i))
                    .append("\" class=\"secondary-button default-style\" style=\"anchor-width:34; anchor-height:26;\">x</button>");
            sb.append("</div>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static String buildStatusResistanceCardsHtml(Player player) {
        List<StatusResistanceEntry> entries = parseStatusResistanceEntries(loreConfig().getNpcStatusResistanceEntries());
        StringBuilder sb = new StringBuilder();
        sb.append(buildMappingSectionHeader(player, "ui.runtime_config.mapping_status_resistance_title", "NPC Status Resistances", statusResistanceAddButtonId()));
        if (entries.isEmpty()) {
            sb.append(buildEmptyMappingText(player, "ui.runtime_config.mapping_empty_status_resistances", "No NPC resistance cards yet."));
        }
        for (int i = 0; i < entries.size(); i++) {
            StatusResistanceEntry entry = entries.get(i);
            sb.append("<div style=\"layout-mode:Left; spacing:6; anchor-width:710; anchor-height:38; padding:5; background-color:#172033; border-radius:3;\">");
            sb.append("<input type=\"text\" id=\"").append(statusResistanceNpcInputId(i))
                    .append("\" style=\"anchor-width:250; anchor-height:26; background-color:#1b2332; padding:5;\" value=\"")
                    .append(escapeHtml(entry.npcId())).append("\">");
            sb.append("<select id=\"").append(statusResistanceStatusSelectId(i))
                    .append("\"").append(COMPACT_DROPDOWN_ATTRS)
                    .append(" style=\"anchor-width:120; anchor-height:26; background-color:#1b2332;\">")
                    .append(buildOptions(LORE_STATUS_KEYS, entry.status())).append("</select>");
            sb.append("<input type=\"text\" id=\"").append(statusResistanceValueInputId(i))
                    .append("\" style=\"anchor-width:70; anchor-height:26; background-color:#1b2332; padding:5;\" value=\"")
                    .append(escapeHtml(entry.value())).append("\">");
            sb.append("<button id=\"").append(statusResistanceDeleteButtonId(i))
                    .append("\" class=\"secondary-button default-style\" style=\"anchor-width:34; anchor-height:26;\">x</button>");
            sb.append("</div>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static String buildStatusCounterCardsHtml(Player player) {
        List<StatusCounterEntry> entries = parseStatusCounterEntries(loreConfig().getStatusBossCounterNpcIds());
        StringBuilder sb = new StringBuilder();
        sb.append(buildMappingSectionHeader(player, "ui.runtime_config.mapping_status_counter_title", "Status Counter NPCs", statusCounterAddButtonId()));
        if (entries.isEmpty()) {
            sb.append(buildEmptyMappingText(player, "ui.runtime_config.mapping_empty_status_counters", "No status counter NPC cards yet."));
        }
        for (int i = 0; i < entries.size(); i++) {
            StatusCounterEntry entry = entries.get(i);
            sb.append("<div style=\"layout-mode:Left; spacing:6; anchor-width:710; anchor-height:38; padding:5; background-color:#172033; border-radius:3;\">");
            sb.append("<input type=\"text\" id=\"").append(statusCounterNpcInputId(i))
                    .append("\" style=\"anchor-width:260; anchor-height:26; background-color:#1b2332; padding:5;\" value=\"")
                    .append(escapeHtml(entry.npcId())).append("\">");
            sb.append("<select id=\"").append(statusCounterStatusSelectId(i))
                    .append("\"").append(COMPACT_DROPDOWN_ATTRS)
                    .append(" style=\"anchor-width:130; anchor-height:26; background-color:#1b2332;\">")
                    .append(buildOptions(LORE_STATUS_KEYS, entry.status())).append("</select>");
            sb.append("<button id=\"").append(statusCounterDeleteButtonId(i))
                    .append("\" class=\"secondary-button default-style\" style=\"anchor-width:34; anchor-height:26;\">x</button>");
            sb.append("</div>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static String buildBleedCounterCardsHtml(Player player) {
        List<String> entries = mutableList(loreConfig().getBleedBossCounterNpcIds());
        StringBuilder sb = new StringBuilder();
        sb.append(buildMappingSectionHeader(player, "ui.runtime_config.mapping_bleed_counter_title", "Legacy Bleed Counter NPCs", bleedCounterAddButtonId()));
        if (entries.isEmpty()) {
            sb.append(buildEmptyMappingText(player, "ui.runtime_config.mapping_empty_bleed_counters", "No legacy bleed counter NPC cards yet."));
        }
        for (int i = 0; i < entries.size(); i++) {
            sb.append("<div style=\"layout-mode:Left; spacing:6; anchor-width:710; anchor-height:38; padding:5; background-color:#172033; border-radius:3;\">");
            sb.append("<input type=\"text\" id=\"").append(bleedCounterNpcInputId(i))
                    .append("\" style=\"anchor-width:330; anchor-height:26; background-color:#1b2332; padding:5;\" value=\"")
                    .append(escapeHtml(entries.get(i))).append("\">");
            sb.append("<button id=\"").append(bleedCounterDeleteButtonId(i))
                    .append("\" class=\"secondary-button default-style\" style=\"anchor-width:34; anchor-height:26;\">x</button>");
            sb.append("</div>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static String buildMappingSectionHeader(Player player, String titleKey, String fallbackTitle, String addButtonId) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"layout-mode:Top; spacing:4; anchor-width:730; padding:6; background-color:#0d131d; border-radius:4;\">");
        sb.append("<div style=\"layout-mode:Left; spacing:6; anchor-width:710; anchor-height:28; padding-left:6;\">");
        sb.append("<p style=\"font-size:12; color:#F1E2A4; font-weight:bold; anchor-width:190;\">")
                .append(escapeHtml(tOrFallback(player, titleKey, fallbackTitle)))
                .append("</p>");
        sb.append("<button id=\"").append(addButtonId)
                .append("\" class=\"secondary-button default-style\" style=\"anchor-width:34; anchor-height:24;\">+</button>");
        sb.append("</div>");
        return sb.toString();
    }

    private static String buildEmptyMappingText(Player player, String key, String fallback) {
        return "<p style=\"font-size:10; color:#78849A;\">" + escapeHtml(tOrFallback(player, key, fallback)) + "</p>";
    }

    private static int computeControlMaxPage(List<ControlEntry> controls) {
        if (controls == null || controls.isEmpty()) {
            return 0;
        }
        return Math.max(0, (controls.size() - 1) / CONTROL_PAGE_SIZE);
    }

    private static List<PickerEntry> buildPickerEntries(PickerMode mode, CategorySection activeCategory) {
        List<PickerEntry> entries = new ArrayList<>();
        if (mode == PickerMode.CATEGORY) {
            for (CategorySection category : categories) {
                entries.add(new PickerEntry(category.id, category.title, category.summary));
            }
        } else if (mode == PickerMode.GROUP && activeCategory != null) {
            for (ControlGroup group : activeCategory.groups) {
                entries.add(new PickerEntry(group.id, group.title, group.description));
            }
        }
        return entries;
    }

    private static String buildPickerModalHtml(Player player, ViewState state, CategorySection activeCategory, ControlGroup activeGroup) {
        PickerMode picker = state != null ? state.picker : PickerMode.NONE;
        if (picker == null || picker == PickerMode.NONE) {
            return "";
        }
        List<PickerEntry> entries = buildPickerEntries(picker, activeCategory);
        String selectedId = picker == PickerMode.CATEGORY
                ? (activeCategory != null ? activeCategory.id : null)
                : (activeGroup != null ? activeGroup.id : null);
        String title = picker == PickerMode.CATEGORY
                ? t(player, "ui.runtime_config.picker_title_category")
                : t(player, "ui.runtime_config.picker_title_section");
        String emptyLabel = picker == PickerMode.CATEGORY
                ? t(player, "ui.runtime_config.picker_empty_categories")
                : t(player, "ui.runtime_config.picker_empty_sections");

        int maxPage = computePickerMaxPage(entries);
        int page = Math.max(0, Math.min(maxPage, state.pickerPage));
        String pageLabel = t(player, "ui.runtime_config.page_label", page + 1, Math.max(1, maxPage + 1));

        String prevDisabled = page <= 0 ? "disabled=\"true\"" : "";
        String nextDisabled = page >= maxPage ? "disabled=\"true\"" : "";
        String cards = buildPickerCardList(entries, selectedId, emptyLabel, page);

        StringBuilder sb = new StringBuilder();
        sb.append("<div id=\"pickerOverlay\" style=\"anchor-full:200; layout-mode:Left; background-color:#0b0b1200;\">");
        sb.append("<div style=\"layout-mode:Left;\">");
        sb.append("<div style=\"anchor-width:350; anchor-height:700; layout-mode:Top; background-color:#1a1a2b; padding:10; border-radius:6;\">");
        sb.append("<div style=\"layout-mode:Left; spacing:10;\">");
        sb.append("<p style=\"font-weight:bold;\">").append(escapeHtml(title)).append("</p>");
        sb.append("<div style=\"flex-weight:1;\"></div>");
        sb.append("<button id=\"pickerCloseButton\" class=\"secondary-button\" style=\"anchor-width:120; anchor-height:32;\">")
                .append(escapeHtml(t(player, "ui.runtime_config.button_close")))
                .append("</button>");
        sb.append("</div>");
        sb.append("<img src=\"divider.png\" style=\"anchor-width: 350; anchor-height: 3;\">");
        sb.append("<reorderable-list id=\"pickerList\" style=\"layout-mode:Top; spacing:6; anchor-width:350; anchor-height:600; background-color:#141426; padding:6; border-radius:4;\">");
        sb.append(cards);
        sb.append("</reorderable-list>");
        sb.append("<div style=\"layout-mode:Center; spacing:8; anchor-width:700;\">");
        sb.append("<button id=\"pickerPrevButton\" class=\"secondary-button\" style=\"anchor-width:120; anchor-height:32;\" ")
                .append(prevDisabled).append(">")
                .append(escapeHtml(t(player, "ui.runtime_config.button_prev")))
                .append("</button>");
        sb.append("<p style=\"font-size:11; color:#b0b0c2;\">").append(escapeHtml(pageLabel)).append("</p>");
        sb.append("<button id=\"pickerNextButton\" class=\"secondary-button\" style=\"anchor-width:120; anchor-height:32;\" ")
                .append(nextDisabled).append(">")
                .append(escapeHtml(t(player, "ui.runtime_config.button_next")))
                .append("</button>");
        sb.append("</div>");
        sb.append("</div>");
        sb.append("</div>");
        sb.append("</div>");
        return sb.toString();
    }

    private static String buildPickerCardList(List<PickerEntry> entries, String selectedId, String emptyLabel, int page) {
        if (entries == null || entries.isEmpty()) {
            String label = emptyLabel == null ? "No entries" : emptyLabel;
            return "<p style=\"font-size:11; color:#b0b0c2;\">" + escapeHtml(label) + "</p>";
        }
        int start = page * PICKER_PAGE_SIZE;
        int end = Math.min(entries.size(), start + PICKER_PAGE_SIZE);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            PickerEntry entry = entries.get(i);
            boolean selected = entry.id.equals(selectedId) || (selectedId == null && i == 0);
            String bg = selected ? "#343a5a" : "#202036";
            String border = selected ? "#7fa5ff" : "#2c2c4b";
            sb.append("<button id=\"pickerEntry_").append(i)
                    .append("\" class=\"custom-button\" style=\"anchor-width:330; anchor-height:64; text-align:left; padding:6; background-color:")
                    .append(bg).append("; border:1px solid ").append(border)
                    .append("; border-radius:2; layout-mode:Top;\">");
            sb.append("<p style=\"font-weight:bold;\">").append(escapeHtml(entry.title)).append("</p>");
            if (entry.summary != null && !entry.summary.isBlank()) {
                sb.append("<p style=\"font-size:11; color:#b0b0c2;\">")
                        .append(escapeHtml(entry.summary))
                        .append("</p>");
            }
            sb.append("</button>");
        }
        return sb.toString();
    }

    private static int computePickerMaxPage(List<PickerEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return 0;
        }
        return Math.max(0, (entries.size() - 1) / PICKER_PAGE_SIZE);
    }

    private static List<Integer> computePickerIndices(List<PickerEntry> entries, int page) {
        List<Integer> indices = new ArrayList<>();
        if (entries == null || entries.isEmpty()) {
            return indices;
        }
        int start = page * PICKER_PAGE_SIZE;
        int end = Math.min(entries.size(), start + PICKER_PAGE_SIZE);
        for (int i = start; i < end; i++) {
            indices.add(i);
        }
        return indices;
    }

    private static String buildCategoryHtml(Player player, CategorySection category, ViewState state) {
        ControlGroup activeGroup = resolveActiveGroup(category, state);
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"anchor-width:")
                .append(CATEGORY_WIDTH)
                .append("; layout-mode:Top; spacing:8; background-color:#0f1520; padding:10; border-radius:6;\">");
        sb.append("<div style=\"layout-mode:Top; spacing:6;\">");
        sb.append("<p style=\"font-size:12; color:#F1E2A4;\"><b>")
                .append(escapeHtml(localizedCategoryTitle(player, category)))
                .append("</b></p>");
        sb.append("<p style=\"color:#B8B8C8;\">")
                .append(escapeHtml(localizedCategorySummary(player, category)))
                .append("</p>");
        sb.append("</div>");

        sb.append("<div style=\"anchor-width:")
                .append(GROUP_WIDTH)
                .append("; layout-mode:Top; spacing:6; background-color:#121b29; padding:8; border-radius:4;\">");
        sb.append("<div style=\"layout-mode:Left; spacing:8; anchor-height:")
                .append(GROUP_PANEL_HEIGHT)
                .append(";\">");
        sb.append("<div style=\"anchor-width:")
                .append(GROUP_LIST_COLUMN_WIDTH)
                .append("; anchor-height:")
                .append(GROUP_PANEL_HEIGHT)
                .append("; layout-mode:Top; spacing:6; background-color:#0f1520; padding:8; border-radius:4; overflow-y:auto;\">");
        for (ControlGroup group : category.groups) {
            sb.append(buildNavigationButton(
                    group.buttonId(category.id),
                    localizedGroupTitle(player, category, group),
                    activeGroup != null && group.id.equals(activeGroup.id),
                    GROUP_LIST_COLUMN_WIDTH - 16));
        }
        sb.append("</div>");

        sb.append("<div style=\"anchor-width:")
                .append(CONTROL_PANEL_WIDTH)
                .append("; anchor-height:")
                .append(GROUP_PANEL_HEIGHT)
                .append("; layout-mode:Top; spacing:6; background-color:#0f1520; padding:8; border-radius:4; overflow-y:auto;\">");
        if (activeGroup != null) {
            sb.append(buildGroupHtml(player, category, activeGroup, null));
        }
        sb.append("</div>");
        sb.append("</div>");

        String noteText = localizedCategoryNote(player, category);
        if (!noteText.isBlank()) {
            sb.append("<div style=\"layout-mode:Top; padding:8; background-color:#191919; border-radius:4;\">")
                    .append("<p style=\"color:#C9B26D;\">")
                    .append(escapeHtml(noteText))
                    .append("</p></div>");
        }

        sb.append("</div>");
        return sb.toString();
    }

    private static String buildNavigationButton(String id, String label, boolean active, int width) {
        String text = active ? "[@] "+label  : label;
        String wrapBg = active ? "#3448ab" : "#1b2332";
        String wrapBorder = active ? "#5d70ce" : "#2A3448";
        return "<div style=\"anchor-width:" + width + "; anchor-height:36; layout-mode:Top; padding:1; background-color:" + wrapBg
                + "; border-radius:2; border-color:" + wrapBorder + "; border-size:1;\">"
                + "<button id=\"" + id + "\" class=\"secondary-button default-style\" style=\"anchor-width:" + (width - 4) + "; anchor-height:30;\">"
                + escapeHtml(text)
                + "</button></div>";
    }

    private static String buildGroupHtml(Player player, CategorySection category, ControlGroup group, ViewState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"anchor-width:")
                .append(CONTROL_PANEL_WIDTH - 16)
                .append("; layout-mode:Top; spacing:6; padding:8; background-color:#121b29; border-radius:4;\">");
        sb.append("<p style=\"font-size:11; color:#D8C27A;\"><b>")
                .append(escapeHtml(localizedGroupTitle(player, category, group)))
                .append("</b></p>");
        String groupDescription = localizedGroupDescription(player, category, group);
        if (!groupDescription.isBlank()) {
            sb.append("<p style=\"font-size:10; color:#9EA8B5;\">")
                    .append(escapeHtml(groupDescription))
                    .append("</p>");
        }
        int singleColumnWidth = CONTROL_PANEL_WIDTH - 32;
        for (ControlEntry entry : group.controls) {
            if (entry instanceof NumericControl control) {
                sb.append(buildControlCell(player, control, singleColumnWidth, state));
            } else if (entry instanceof TextControl control) {
                sb.append(buildTextControlCell(player, control, singleColumnWidth, state));
            }
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static String buildControlCell(Player player, NumericControl control, int width, ViewState state) {
        int dividerWidth = Math.max(60, width - 20);
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"anchor-width:")
                .append(width)
                .append("; layout-mode:Top; spacing:6; padding:8; background-color:#0d131d; border-radius:4;\">");
        sb.append("<div style=\"layout-mode:Top; spacing:2;\">");
        sb.append("<p><b>").append(escapeHtml(localizedControlLabel(player, control))).append("</b></p>");
        String controlDescription = localizedControlDescription(player, control);
        if (!controlDescription.isBlank()) {
            sb.append("<p style=\"font-size:10; color:#9EA8B5;\">")
                    .append(escapeHtml(controlDescription))
                    .append("</p>");
        }
        sb.append("</div>");
        sb.append("<div style=\"layout-mode:Left; spacing:8;\">");
        if (control.displayKind == DisplayKind.TOGGLE) {
            boolean enabled = control.currentValue() > 0.0;
            String toggleBg = enabled ? "#284D37" : "#4A2630";
            sb.append("<button id=\"")
                    .append(control.plusButtonId())
                    .append("\" class=\"secondary-button default-style\" style=\"anchor-width:")
                    .append(VALUE_WIDTH)
                    .append("; anchor-height:34; background-color:")
                    .append(toggleBg)
                    .append(";\">")
                    .append(escapeHtml(control.formatCurrentValue()))
                    .append("</button>");
        } else {
            String inputStep = inputStep(control);
            int inputDecimals = inputDecimals(control);
            String inputMin = inputMin(control);
            String inputMax = inputMax(control);
            boolean useNumberInput = control.displayKind == DisplayKind.INTEGER;
            sb.append("<input type=\"")
                    .append(useNumberInput ? "number" : "text")
                    .append("\" id=\"")
                    .append(control.inputElementId())
                    .append("\" style=\"anchor-width:")
                    .append(VALUE_WIDTH)
                    .append("; anchor-height:30; background-color:#1b2332; padding:6; border-radius:4;\" value=\"")
                    .append(escapeHtml(draftValue(state, control.inputElementId(), control.formatInputValue())))
                    .append("\"");
            if (!useNumberInput) {
                sb.append(" inputmode=\"decimal\"");
            }
            if (useNumberInput) {
                sb.append(" step=\"").append(inputStep).append("\"");
            }
            sb.append(" data-hyui-max-decimal-places=\"").append(inputDecimals).append("\"");
            if (inputMin != null) {
                if (useNumberInput) {
                    sb.append(" min=\"").append(inputMin).append("\"");
                }
                sb.append(" data-hyui-min=\"").append(inputMin).append("\"");
            }
            if (inputMax != null) {
                if (useNumberInput) {
                    sb.append(" max=\"").append(inputMax).append("\"");
                }
                sb.append(" data-hyui-max=\"").append(inputMax).append("\"");
            }
            sb.append(">");
            if (isRefineMaxLevelControl(control)) {
                sb.append("<button id=\"")
                        .append(REFINE_RECALCULATE_WEIGHTS_BUTTON)
                        .append("\" class=\"secondary-button default-style\" style=\"anchor-width:170; anchor-height:30;\">")
                        .append(escapeHtml(tOrFallback(player, "ui.runtime_config.button_recalculate_weights", "Recalc Weights")))
                        .append("</button>");
            }
        }
        sb.append("</div>");
        sb.append("<img src=\"divider.png\" style=\"anchor-width: ")
                .append(Math.min(DIVIDER_WIDTH, dividerWidth))
                .append("; anchor-height: 2;\">");
        sb.append("</div>");
        return sb.toString();
    }

    private static String buildTextControlCell(Player player, TextControl control, int width, ViewState state) {
        int dividerWidth = Math.max(60, width - 20);
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"anchor-width:")
                .append(width)
                .append("; layout-mode:Top; spacing:6; padding:8; background-color:#0d131d; border-radius:4;\">");
        sb.append("<div style=\"layout-mode:Top; spacing:2;\">");
        sb.append("<p><b>").append(escapeHtml(localizedControlLabel(player, control))).append("</b></p>");
        String controlDescription = localizedControlDescription(player, control);
        if (!controlDescription.isBlank()) {
            sb.append("<p style=\"font-size:10; color:#9EA8B5;\">")
                    .append(escapeHtml(controlDescription))
                    .append("</p>");
        }
        sb.append("</div>");
        sb.append("<div style=\"layout-mode:Left; spacing:8;\">");
        if (control.multiline) {
            sb.append("<textarea id=\"")
                    .append(control.inputElementId())
                    .append("\" rows=\"6\" style=\"anchor-width:")
                    .append(Math.max(VALUE_WIDTH, width - 24))
                    .append("; anchor-height:120; text-align:left; background-color:#1b2332; padding:6; border-radius:4; white-space:wrap;\" value=\"")
                    .append(escapeHtml(draftValue(state, control.inputElementId(), control.currentValue())).replace("\n", "&#10;"))
                    .append("\"></textarea>");
        } else {
            sb.append("<input type=\"text\" id=\"")
                    .append(control.inputElementId())
                    .append("\" style=\"anchor-width:")
                    .append(VALUE_WIDTH)
                    .append("; anchor-height:30; text-align:left; background-color:#1b2332; padding:6; border-radius:4;\" value=\"")
                    .append(escapeHtml(draftValue(state, control.inputElementId(), control.currentValue())))
                    .append("\">");
        }
        sb.append("</div>");
        sb.append("<img src=\"divider.png\" style=\"anchor-width: ")
                .append(Math.min(DIVIDER_WIDTH, dividerWidth))
                .append("; anchor-height: 2;\">");
        sb.append("</div>");
        return sb.toString();
    }

    private static String buildRefineLimitsWithWeightCardsHtml(Player player, ControlGroup group, ViewState state) {
        StringBuilder sb = new StringBuilder();
        int width = 1100;
        for (ControlEntry entry : group.controls) {
            if (entry instanceof NumericControl control && !isWeightControl(control)) {
                sb.append(buildControlCell(player, control, width, state));
            } else if (entry instanceof TextControl control) {
                sb.append(buildTextControlCell(player, control, width, state));
            }
        }

        sb.append("<div style=\"layout-mode:Top; spacing:8; anchor-width:1100; padding:8; background-color:#0d131d; border-radius:4;\">");
        sb.append("<p style=\"font-size:12; color:#F1E2A4; font-weight:bold;\">")
                .append(escapeHtml(tOrFallback(player, "ui.runtime_config.refine_outcome_weights_title", "Outcome Weights")))
                .append("</p>");
        sb.append("<p style=\"font-size:10; color:#9EA8B5;\">")
                .append(escapeHtml(tOrFallback(player, "ui.runtime_config.refine_outcome_weights_description", "Each transition is a compact card. Editing one outcome auto-normalizes the rest back to 100%.")))
                .append("</p>");
        int maxLevel = Math.max(1, refinementConfig().getMaxLevel());
        for (int level = 0; level < maxLevel; level++) {
            sb.append(buildOutcomeWeightCard(player, level, state));
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static String buildOutcomeWeightCard(Player player, int level, ViewState state) {
        List<ControlEntry> controls = buildWeightControls(level);
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"layout-mode:Top; spacing:6; anchor-width:1060; padding:8; background-color:#172033; border-radius:3;\">");
        sb.append("<p style=\"font-size:11; color:#D8C27A; font-weight:bold;\">")
                .append("L").append(level).append(" -> L").append(level + 1)
                .append("</p>");
        sb.append("<div style=\"layout-mode:Left; spacing:8; anchor-width:1040; anchor-height:62;\">");
        for (ControlEntry entry : controls) {
            if (entry instanceof NumericControl control) {
                sb.append(buildOutcomeWeightMiniCard(player, control, state));
            }
        }
        sb.append("</div>");
        sb.append("</div>");
        return sb.toString();
    }

    private static String buildOutcomeWeightMiniCard(Player player, NumericControl control, ViewState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"layout-mode:Top; spacing:3; anchor-width:245; anchor-height:58; padding:5; background-color:#0f1520; border-radius:3;\">");
        sb.append("<p style=\"font-size:10; color:#D8DEE9; font-weight:bold;\">")
                .append(escapeHtml(localizedWeightOutcomeLabel(player, control)))
                .append("</p>");
        sb.append("<input type=\"text\" id=\"")
                .append(control.inputElementId())
                .append("\" inputmode=\"decimal\" data-hyui-max-decimal-places=\"2\" data-hyui-min=\"0\" data-hyui-max=\"100\" style=\"anchor-width:92; anchor-height:24; background-color:#1b2332; padding:5; border-radius:4;\" value=\"")
                .append(escapeHtml(draftValue(state, control.inputElementId(), control.formatInputValue())))
                .append("\">");
        sb.append("</div>");
        return sb.toString();
    }

    private static boolean isWeightControl(NumericControl control) {
        return control != null && control.id != null && control.id.startsWith("refine_weight_");
    }

    private static boolean isRefineMaxLevelControl(NumericControl control) {
        return control != null && "refine_max_level".equals(control.id);
    }

    private static String localizedWeightOutcomeLabel(Player player, NumericControl control) {
        String id = control == null ? "" : control.id;
        String fallback = control == null ? "" : control.label;
        if (id.endsWith("_degrade")) {
            return tOrFallback(player, "ui.runtime_config.refine_outcome_degrade", "Degrade");
        }
        if (id.endsWith("_same")) {
            return tOrFallback(player, "ui.runtime_config.refine_outcome_same", "Same");
        }
        if (id.endsWith("_upgrade")) {
            return tOrFallback(player, "ui.runtime_config.refine_outcome_upgrade", "Upgrade");
        }
        if (id.endsWith("_jackpot")) {
            return tOrFallback(player, "ui.runtime_config.refine_outcome_jackpot", "Jackpot");
        }
        return fallback;
    }

    private static String buildButton(String id, String label) {
        return "<button id=\"" + id + "\" class=\"secondary-button default-style\" style=\"anchor-width:58; anchor-height:30;\">" + escapeHtml(label) + "</button>";
    }

    private static ViewState createDefaultViewState(PlayerRef playerRef) {
        CategorySection defaultCategory = categories.isEmpty() ? null : categories.get(0);
        String statusText = tOrFallback(playerRef, DEFAULT_STATUS_KEY, DEFAULT_STATUS_FALLBACK);
        return new ViewState(
                defaultCategory == null ? CATEGORY_SOCKET : defaultCategory.id,
                firstGroupId(defaultCategory),
                statusText,
                PickerMode.NONE,
                0,
                0);
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
        addCategory(buildDamageNumberCategory());
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
                    "Break chance while attempting +" + level + " to +" + (level + 1) + ".",
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
                    "Break chance while attempting +" + level + " to +" + (level + 1) + ".",
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
        groups.add(new ControlGroup(GROUP_LOOT_CHEST_INJECTIONS, "Injected Chest Loot", "Extra item rules injected into generated chest loot. Format per row: item, chance, min, max.", List.of()));

        List<ControlEntry> dropRolls = new ArrayList<>();
        dropRolls.add(chanceControl("loot_drop_three", CATEGORY_LOOT, LOOT_CONFIG_NAME, "Drop 3-socket chance", "Chance for NPC drops to land at 3 sockets.", () -> lootConfig().getDropThreeSocketChance(), delta -> lootConfig().setDropThreeSocketChance(clampChance(lootConfig().getDropThreeSocketChance() + delta))));
        dropRolls.add(chanceControl("loot_drop_four", CATEGORY_LOOT, LOOT_CONFIG_NAME, "Drop 4-socket chance", "Chance for NPC drops to land at 4 sockets.", () -> lootConfig().getDropFourSocketChance(), delta -> lootConfig().setDropFourSocketChance(clampChance(lootConfig().getDropFourSocketChance() + delta))));
        dropRolls.add(chanceControl("loot_drop_five", CATEGORY_LOOT, LOOT_CONFIG_NAME, "Drop 5-socket chance", "Chance for NPC drops to land at 5 sockets.", () -> lootConfig().getDropFiveSocketChance(), delta -> lootConfig().setDropFiveSocketChance(clampChance(lootConfig().getDropFiveSocketChance() + delta))));
        dropRolls.add(chanceControl("loot_drop_three_to_four", CATEGORY_LOOT, LOOT_CONFIG_NAME, "Drop 3->4 conversion", "Upgrade chance when drop loot first rolls 3 sockets.", () -> lootConfig().getDropThreeToFourChance(), delta -> lootConfig().setDropThreeToFourChance(clampChance(lootConfig().getDropThreeToFourChance() + delta))));
        dropRolls.add(chanceControl("loot_drop_resonance", CATEGORY_LOOT, LOOT_CONFIG_NAME, "Drop resonance chance", "Chance for NPC drops to roll a fully resonant item.", () -> lootConfig().getDropResonanceChance(), delta -> lootConfig().setDropResonanceChance(clampChance(lootConfig().getDropResonanceChance() + delta))));
        dropRolls.add(chanceControl("loot_drop_socketed_essence", CATEGORY_LOOT, LOOT_CONFIG_NAME, "Drop socketed essence chance", "Chance for NPC equipment to spawn with filled essences.", () -> lootConfig().getDropSocketedEssenceChance(), delta -> lootConfig().setDropSocketedEssenceChance(clampChance(lootConfig().getDropSocketedEssenceChance() + delta))));
        groups.add(new ControlGroup("npc_drops", "NPC Drops", "Socket roll tuning for NPC and world drops.", dropRolls));
        groups.add(new ControlGroup(GROUP_LOOT_NPC_INJECTIONS, "Injected NPC Drops", "Extra item rules injected into every NPC drop list.", List.of()));
        groups.add(new ControlGroup(GROUP_LOOT_NPC_AQUATIC_INJECTIONS, "Aquatic NPC Drops", "Extra item rules injected when the NPC role breathes in water.", List.of()));
        groups.add(new ControlGroup(GROUP_LOOT_NPC_FLYING_INJECTIONS, "Flying NPC Drops", "Extra item rules injected when the NPC role matches flying role hints.", List.of()));
        groups.add(new ControlGroup(GROUP_LOOT_NPC_VOID_INJECTIONS, "Void NPC Drops", "Extra item rules injected when the NPC role matches void spawn hints.", List.of()));

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
                "Enabled: SocketReforge combat text. Disabled: built-in combat text.",
                () -> damageNumberConfig().isUseCustomCombatText(),
                value -> damageNumberConfig().setUseCustomCombatText(value)));
        groups.add(new ControlGroup("damage_numbers_mode", "Combat Text Mode", "Toggle custom vs built-in combat text rendering.", modeControls));

        return new CategorySection(
                CATEGORY_DAMAGE_NUMBERS,
                "toggleDamageNumbersCategory",
                "Damage Numbers",
                "Combat text system toggles for SocketReforge damage numbers.",
                groups,
                null);
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
        leveling.add(intControl("lore_max_level", CATEGORY_LORE, LORE_CONFIG_NAME, "Lore max level", "Maximum lore level before absorption.", () -> loreConfig().getMaxLevel(), delta -> loreConfig().setMaxLevel(clampInt(loreConfig().getMaxLevel() + (int) Math.round(delta), 1, 200))));
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
        bleed.add(chanceControl("lore_bleed_max_hp_pct", CATEGORY_LORE, LORE_CONFIG_NAME, "Bleed max HP per tick", "Percent of max HP dealt by bleed each tick.", () -> loreConfig().getBleedMaxHpPctPerTick(), delta -> loreConfig().setBleedMaxHpPctPerTick(clampChance(loreConfig().getBleedMaxHpPctPerTick() + delta))));
        bleed.add(multiplierControl("lore_bleed_ramp", CATEGORY_LORE, LORE_CONFIG_NAME, "Bleed ramp per tick", "Additional bleed scaling each tick.", () -> loreConfig().getBleedRampPerTick(), delta -> loreConfig().setBleedRampPerTick(clamp(loreConfig().getBleedRampPerTick() + delta, 0.0, 10.0))));
        bleed.add(chanceControl("lore_bleed_weapon_base_cap", CATEGORY_LORE, LORE_CONFIG_NAME, "Bleed weapon base cap pct", "Optional cap based on weapon base damage. Zero disables this cap.", () -> loreConfig().getBleedWeaponBaseCapPct(), delta -> loreConfig().setBleedWeaponBaseCapPct(clampChance(loreConfig().getBleedWeaponBaseCapPct() + delta))));
        bleed.add(chanceControl("lore_bleed_total_current_hp", CATEGORY_LORE, LORE_CONFIG_NAME, "Bleed total current HP pct", "Total current HP pressure applied by bleed.", () -> loreConfig().getBleedTotalCurrentHpPct(), delta -> loreConfig().setBleedTotalCurrentHpPct(clampChance(loreConfig().getBleedTotalCurrentHpPct() + delta))));
        bleed.add(multiplierControl("lore_bleed_weapon_ref_base", CATEGORY_LORE, LORE_CONFIG_NAME, "Bleed weapon reference base", "Reference weapon base used for bleed scaling.", () -> loreConfig().getBleedWeaponReferenceBase(), delta -> loreConfig().setBleedWeaponReferenceBase(clamp(loreConfig().getBleedWeaponReferenceBase() + (delta * 100.0), 1.0, 10000.0))));
        bleed.add(multiplierControl("lore_bleed_weapon_scale_min", CATEGORY_LORE, LORE_CONFIG_NAME, "Bleed weapon scale min", "Minimum weapon scaling multiplier for bleed.", () -> loreConfig().getBleedWeaponScaleMin(), delta -> loreConfig().setBleedWeaponScaleMin(clamp(loreConfig().getBleedWeaponScaleMin() + delta, 0.0, loreConfig().getBleedWeaponScaleMax()))));
        bleed.add(multiplierControl("lore_bleed_weapon_scale_max", CATEGORY_LORE, LORE_CONFIG_NAME, "Bleed weapon scale max", "Maximum weapon scaling multiplier for bleed.", () -> loreConfig().getBleedWeaponScaleMax(), delta -> loreConfig().setBleedWeaponScaleMax(clamp(loreConfig().getBleedWeaponScaleMax() + delta, loreConfig().getBleedWeaponScaleMin(), 20.0))));
        groups.add(new ControlGroup("lore_bleed", "Lore Bleed Scaling", "Damage and boss-scaling knobs for bleed-style lore effects.", bleed));

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

    private static String loadTemplate() {
        return UITemplateUtils.loadTemplate(
                RuntimeConfigUI.class,
                TEMPLATE_PATH,
                "<div><p>Runtime config template missing.</p></div>",
                "RuntimeConfigUI");
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
        String[] lines = normalized.split("\n");
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

    private static String buildAbilityTriggerOptions(String selectedTrigger) {
        String selected = normalizeAbilityToken(selectedTrigger, "ON_HIT");
        StringBuilder sb = new StringBuilder();
        for (String trigger : LORE_TRIGGERS) {
            appendOption(sb, trigger, selected);
        }
        for (String signature : LORE_SIGNATURES) {
            appendOption(sb, signature, selected);
        }
        return sb.toString();
    }

    private static String buildOptions(String[] values, String selectedValue) {
        String selected = selectedValue == null ? "" : selectedValue.trim();
        StringBuilder sb = new StringBuilder();
        if (values != null) {
            for (String value : values) {
                appendOption(sb, value, selected);
            }
        }
        return sb.toString();
    }

    private static void appendOption(StringBuilder sb, String value, String selectedValue) {
        if (value == null || value.isBlank()) {
            return;
        }
        String trimmed = value.trim();
        sb.append("<option value=\"").append(escapeHtml(trimmed)).append("\"");
        if (trimmed.equalsIgnoreCase(selectedValue == null ? "" : selectedValue.trim())) {
            sb.append(" selected=\"true\"");
        }
        sb.append(">").append(escapeHtml(trimmed)).append("</option>");
    }

    private static String buildSpiritOptions(List<String> allSpirits, String selectedSpirit) {
        String selected = selectedSpirit == null ? "" : selectedSpirit.trim();
        StringBuilder sb = new StringBuilder();
        if (!selected.isBlank() && (allSpirits == null || !allSpirits.contains(selected))) {
            appendOption(sb, selected, selected);
        }
        for (String spirit : allSpirits) {
            sb.append("<option value=\"").append(escapeHtml(spirit)).append("\"");
            if (spirit.equals(selected)) {
                sb.append(" selected=\"true\"");
            }
            sb.append(">").append(escapeHtml(spirit)).append("</option>");
        }
        return sb.toString();
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

    private static String extractMappingEventValue(Object eventObj, Object ctxObj, String elementId) {
        String rawValue = HyUIReflectionUtils.extractEventValue(eventObj);
        if (rawValue == null) {
            rawValue = HyUIReflectionUtils.getContextValue(ctxObj, elementId, "#" + elementId + ".value");
        }
        return rawValue == null ? "" : rawValue.trim();
    }

    private static String currentContextValue(Object ctxObj, String elementId, String fallback) {
        String value = HyUIReflectionUtils.getContextValue(ctxObj, elementId, "#" + elementId + ".value");
        if (value == null) {
            return fallback == null ? "" : fallback;
        }
        return value;
    }

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
        String chance = params.length > 0 ? normalizeDecimalText(params[0], "0.0", true) : "0.0";
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

    private static String draftOrContextValue(ViewState state, Object ctxObj, String elementId, String fallback) {
        if (state != null && elementId != null && state.draftValues.containsKey(elementId)) {
            return state.draftValues.get(elementId);
        }
        return currentContextValue(ctxObj, elementId, fallback);
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
    private record LootRuleEntry(String itemId, String chance, String min, String max, String targetId) {}

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

    private static Object getStore(PlayerRef playerRef) throws Exception {
        return HyUIReflectionUtils.getStore(playerRef);
    }

    private static void closePageIfOpen(PlayerRef playerRef) {
        HyUIReflectionUtils.closePageIfOpen(openPages, playerRef);
    }

    private static String escapeHtml(String text) {
        return UITemplateUtils.escapeHtml(text);
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
