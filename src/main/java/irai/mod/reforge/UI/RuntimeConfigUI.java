package irai.mod.reforge.UI;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import irai.mod.reforge.Common.UI.HyUIReflectionUtils;
import irai.mod.reforge.Common.UI.UITemplateUtils;
import irai.mod.reforge.Config.LootSocketRollConfig;
import irai.mod.reforge.Config.RefinementConfig;
import irai.mod.reforge.Config.SocketConfig;
import irai.mod.reforge.Config.WeatherEventConfig;
import irai.mod.reforge.ReforgePlugin;

/**
 * OP-only live gameplay config editor backed by HyUI.
 */
public final class RuntimeConfigUI {
    private RuntimeConfigUI() {}

    private static final String HYUI_PAGE_BUILDER = "au.ellie.hyui.builders.PageBuilder";
    private static final String HYUI_PLUGIN = "au.ellie.hyui.HyUIPlugin";
    private static final String HYUI_EVENT_BINDING = "com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType";
    private static final String TEMPLATE_PATH = "Common/UI/Custom/Pages/RuntimeConfigEditor.html";

    private static final String SOCKET_CONFIG_NAME = "SocketConfig";
    private static final String REFINEMENT_CONFIG_NAME = "RefinementConfig";
    private static final String LOOT_CONFIG_NAME = "LootSocketRollConfig";
    private static final String WEATHER_CONFIG_NAME = "WeatherEventConfig";

    private static final String CATEGORY_SOCKET = "socket";
    private static final String CATEGORY_REFINEMENT = "refinement";
    private static final String CATEGORY_LOOT = "loot";
    private static final String CATEGORY_WEATHER = "weather";

    private static final String DEFAULT_STATUS =
            "Changes apply live and save immediately.\nUse Reload All From Disk to discard in-memory edits.";
    private static final int CATEGORY_WIDTH = 848;
    private static final int GROUP_WIDTH = 828;
    private static final int GROUP_LIST_COLUMN_WIDTH = 270;
    private static final int CONTROL_PANEL_WIDTH = 550;
    private static final int CONTROL_COLUMN_WIDTH = 270;
    private static final int VALUE_WIDTH = 150;
    private static final int DIVIDER_WIDTH = 792;

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
    private static final List<NumericControl> controls = new ArrayList<>();

    private enum DisplayKind {
        INTEGER,
        PERCENT,
        MULTIPLIER
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

        ViewState(String activeCategoryId, String activeGroupId, String statusText) {
            this.activeCategoryId = activeCategoryId;
            this.activeGroupId = activeGroupId;
            this.statusText = statusText;
        }
    }

    private static final class NumericControl {
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

        String formatValue(double rawValue) {
            return RuntimeConfigUI.formatValue(displayKind, rawValue);
        }

        String formatSmallStepLabel(boolean positive) {
            return formatStepLabel(displayKind, smallStep, positive);
        }

        String formatLargeStepLabel(boolean positive) {
            return formatStepLabel(displayKind, largeStep, positive);
        }
    }

    private static final class ControlGroup {
        final String id;
        final String title;
        final String description;
        final List<NumericControl> controls;

        ControlGroup(String id, String title, String description, List<NumericControl> controls) {
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
        if (categories.isEmpty()) {
            registerControls();
        }
    }

    public static boolean isAvailable() {
        return hyuiAvailable && plugin != null;
    }

    public static void open(Player player) {
        if (player == null) {
            return;
        }
        if (!isAvailable()) {
            player.sendMessage(Message.raw("<color=#FF5555>HyUI not installed - runtime config UI disabled."));
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

            String html = loadTemplate();
            html = html.replace("{{statusText}}", escapeHtml(state.statusText));
            html = html.replace("{{categoriesHtml}}", buildCategoriesHtml(state));

            Object pageBuilder = pageForPlayer.invoke(null, playerRef);
            pageBuilder = fromHtml.invoke(pageBuilder, html);

            final Player finalPlayer = player;
            final ViewState finalState = state;
            final CategorySection activeCategory = resolveActiveCategory(finalState);
            final ControlGroup activeGroup = resolveActiveGroup(activeCategory, finalState);

            addListener.invoke(pageBuilder, "reloadAllButton", activating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> handleReload(finalPlayer, finalState));

            for (CategorySection category : categories) {
                addListener.invoke(pageBuilder, category.toggleButtonId, activating,
                        (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                            selectCategory(finalState, category.id);
                            requestReopen(finalPlayer, finalState);
                        });
            }

            if (activeCategory != null) {
                for (ControlGroup group : activeCategory.groups) {
                    addListener.invoke(pageBuilder, group.buttonId(activeCategory.id), activating,
                            (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                                finalState.activeGroupId = group.id;
                                requestReopen(finalPlayer, finalState);
                            });
                }
            }

            for (NumericControl control : visibleControls(activeCategory, activeGroup)) {
                registerAdjustmentListener(pageBuilder, addListener, activating, control, -control.largeStep, finalPlayer, finalState);
                registerAdjustmentListener(pageBuilder, addListener, activating, control, -control.smallStep, finalPlayer, finalState);
                registerAdjustmentListener(pageBuilder, addListener, activating, control, control.smallStep, finalPlayer, finalState);
                registerAdjustmentListener(pageBuilder, addListener, activating, control, control.largeStep, finalPlayer, finalState);
            }

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

    private static void handleReload(Player player, ViewState state) {
        try {
            plugin.getConfigService().reloadAll();
            state.statusText = "Reloaded all live gameplay configs from disk.";
        } catch (Exception e) {
            state.statusText = "Reload failed: " + sanitizeError(e);
        }
        requestReopen(player, state);
    }

    private static void handleAdjustment(Player player, ViewState state, NumericControl control, double delta) {
        double before = control.currentValue();
        try {
            control.adjust(delta);
            plugin.getConfigService().saveAndApply(control.configName);
            double after = control.currentValue();
            if (roughlyEqual(before, after)) {
                state.statusText = control.label + " unchanged (" + control.formatValue(after) + ").";
            } else {
                state.statusText = control.label + ": " + control.formatValue(before) + " -> " + control.formatValue(after) + ".";
            }
        } catch (Exception e) {
            state.statusText = "Update failed for " + control.label + ": " + sanitizeError(e);
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

    private static String buildCategoriesHtml(ViewState state) {
        StringBuilder sb = new StringBuilder();
        CategorySection activeCategory = resolveActiveCategory(state);
        sb.append("<div style=\"layout-mode:Top; spacing:10;\">");
        sb.append("<div style=\"anchor-width:")
                .append(CATEGORY_WIDTH)
                .append("; layout-mode:Left; spacing:8; background-color:#0f1520; padding:10; border-radius:6;\">");
        for (CategorySection category : categories) {
            sb.append(buildNavigationButton(
                    category.toggleButtonId,
                    category.title,
                    category.id.equals(state.activeCategoryId),
                    188));
        }
        sb.append("</div>");
        if (activeCategory != null) {
            sb.append(buildCategoryHtml(activeCategory, state));
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static String buildCategoryHtml(CategorySection category, ViewState state) {
        ControlGroup activeGroup = resolveActiveGroup(category, state);
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"anchor-width:")
                .append(CATEGORY_WIDTH)
                .append("; layout-mode:Top; spacing:8; background-color:#0f1520; padding:10; border-radius:6;\">");
        sb.append("<div style=\"layout-mode:Top; spacing:6;\">");
        sb.append("<p style=\"font-size:12; color:#F1E2A4;\"><b>")
                .append(escapeHtml(category.title))
                .append("</b></p>");
        sb.append("<p style=\"color:#B8B8C8;\">")
                .append(escapeHtml(category.summary))
                .append("</p>");
        sb.append("</div>");

        sb.append("<div style=\"anchor-width:")
                .append(GROUP_WIDTH)
                .append("; layout-mode:Top; spacing:6; background-color:#121b29; padding:8; border-radius:4;\">");
        sb.append("<div style=\"layout-mode:Left; spacing:8;\">");
        sb.append("<div style=\"anchor-width:")
                .append(GROUP_LIST_COLUMN_WIDTH)
                .append("; layout-mode:Top; spacing:6; background-color:#0f1520; padding:8; border-radius:4;\">");
        for (ControlGroup group : category.groups) {
            sb.append(buildNavigationButton(
                    group.buttonId(category.id),
                    group.title,
                    activeGroup != null && group.id.equals(activeGroup.id),
                    GROUP_LIST_COLUMN_WIDTH - 16));
        }
        sb.append("</div>");

        sb.append("<div style=\"anchor-width:")
                .append(CONTROL_PANEL_WIDTH)
                .append("; layout-mode:Top; spacing:6; background-color:#0f1520; padding:8; border-radius:4;\">");
        if (activeGroup != null) {
            sb.append(buildGroupHtml(activeGroup));
        }
        sb.append("</div>");
        sb.append("</div>");

        if (category.noteText != null && !category.noteText.isBlank()) {
            sb.append("<div style=\"layout-mode:Top; padding:8; background-color:#191919; border-radius:4;\">")
                    .append("<p style=\"color:#C9B26D;\">")
                    .append(escapeHtml(category.noteText))
                    .append("</p></div>");
        }

        sb.append("</div>");
        return sb.toString();
    }

    private static String buildNavigationButton(String id, String label, boolean active, int width) {
        String text = active ? "[@] "+label  : label;
        String wrapBg = active ? "#3448ab" : "#1b2332";
        String wrapBorder = active ? "#5d70ce" : "#2A3448";
        return "<div style=\"anchor-width:" + width + "; layout-mode:Top; padding:1; background-color:" + wrapBg
                + "; border-radius:2; border-color:" + wrapBorder + "; border-size:1;\">"
                + "<button id=\"" + id + "\" style=\"width:" + (width - 4) + ";height:30;\">"
                + escapeHtml(text)
                + "</button></div>";
    }

    private static String buildGroupHtml(ControlGroup group) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"anchor-width:")
                .append(CONTROL_PANEL_WIDTH - 16)
                .append("; layout-mode:Top; spacing:6; padding:8; background-color:#121b29; border-radius:4;\">");
        sb.append("<p style=\"font-size:11; color:#D8C27A;\"><b>")
                .append(escapeHtml(group.title))
                .append("</b></p>");
        if (group.description != null && !group.description.isBlank()) {
            sb.append("<p style=\"font-size:10; color:#9EA8B5;\">")
                    .append(escapeHtml(group.description))
                    .append("</p>");
        }
        int singleColumnWidth = CONTROL_PANEL_WIDTH - 32;
        for (NumericControl control : group.controls) {
            sb.append(buildControlCell(control, singleColumnWidth));
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static String buildControlCell(NumericControl control, int width) {
        int dividerWidth = Math.max(60, width - 20);
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"anchor-width:")
                .append(width)
                .append("; layout-mode:Top; spacing:6; padding:8; background-color:#0d131d; border-radius:4;\">");
        sb.append("<div style=\"layout-mode:Top; spacing:2;\">");
        sb.append("<p><b>").append(escapeHtml(control.label)).append("</b></p>");
        if (control.description != null && !control.description.isBlank()) {
            sb.append("<p style=\"font-size:10; color:#9EA8B5;\">")
                    .append(escapeHtml(control.description))
                    .append("</p>");
        }
        sb.append("</div>");
        sb.append("<div style=\"layout-mode:Left; spacing:8;\">");
        sb.append(buildButton(control.minusLargeButtonId(), control.formatLargeStepLabel(false)));
        sb.append(buildButton(control.minusButtonId(), control.formatSmallStepLabel(false)));
        sb.append(buildButton(control.plusButtonId(), control.formatSmallStepLabel(true)));
        sb.append(buildButton(control.plusLargeButtonId(), control.formatLargeStepLabel(true)));
        sb.append("<p id=\"")
                .append(control.valueElementId())
                .append("\" style=\"width:")
                .append(VALUE_WIDTH)
                .append("; text-align:right; background-color:#1b2332; padding:8; border-radius:4;\">")
                .append(escapeHtml(control.formatCurrentValue()))
                .append("</p>");
        sb.append("</div>");
        sb.append("<img src=\"divider.png\" style=\"anchor-width: ")
                .append(Math.min(DIVIDER_WIDTH, dividerWidth))
                .append("; anchor-height: 2;\">");
        sb.append("</div>");
        return sb.toString();
    }

    private static String buildButton(String id, String label) {
        return "<button id=\"" + id + "\" style=\"width:58;height:30;\">" + escapeHtml(label) + "</button>";
    }

    private static ViewState createDefaultViewState(PlayerRef playerRef) {
        CategorySection defaultCategory = categories.isEmpty() ? null : categories.get(0);
        return new ViewState(
                defaultCategory == null ? CATEGORY_SOCKET : defaultCategory.id,
                firstGroupId(defaultCategory),
                DEFAULT_STATUS);
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

    private static List<NumericControl> visibleControls(CategorySection category, ControlGroup group) {
        if (category == null || group == null) {
            return List.of();
        }
        return group.controls;
    }

    private static void registerControls() {
        addCategory(buildSocketCategory());
        addCategory(buildRefinementCategory());
        addCategory(buildLootCategory());
        addCategory(buildWeatherCategory());
    }

    private static void addCategory(CategorySection category) {
        categories.add(category);
        for (ControlGroup group : category.groups) {
            controls.addAll(group.controls);
        }
    }

    private static CategorySection buildSocketCategory() {
        List<ControlGroup> groups = new ArrayList<>();

        List<NumericControl> limits = new ArrayList<>();
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

        List<NumericControl> punchSuccess = new ArrayList<>();
        punchSuccess.add(chanceArrayControl("socket_success_0", CATEGORY_SOCKET, SOCKET_CONFIG_NAME, "Punch 1st socket success", "Base success chance when item has 0 sockets.", RuntimeConfigUI::ensureSocketSuccessArray, 0));
        punchSuccess.add(chanceArrayControl("socket_success_1", CATEGORY_SOCKET, SOCKET_CONFIG_NAME, "Punch 2nd socket success", "Base success chance when item has 1 socket.", RuntimeConfigUI::ensureSocketSuccessArray, 1));
        punchSuccess.add(chanceArrayControl("socket_success_2", CATEGORY_SOCKET, SOCKET_CONFIG_NAME, "Punch 3rd socket success", "Base success chance when item has 2 sockets.", RuntimeConfigUI::ensureSocketSuccessArray, 2));
        punchSuccess.add(chanceArrayControl("socket_success_3", CATEGORY_SOCKET, SOCKET_CONFIG_NAME, "Punch 4th socket success", "Base success chance when item has 3 sockets.", RuntimeConfigUI::ensureSocketSuccessArray, 3));
        groups.add(new ControlGroup("punch_success", "Punch Success", "Per-attempt success values by current socket count.", punchSuccess));

        List<NumericControl> punchBreak = new ArrayList<>();
        punchBreak.add(chanceArrayControl("socket_break_0", CATEGORY_SOCKET, SOCKET_CONFIG_NAME, "Punch 1st socket break", "Break risk when item has 0 sockets.", RuntimeConfigUI::ensureSocketBreakArray, 0));
        punchBreak.add(chanceArrayControl("socket_break_1", CATEGORY_SOCKET, SOCKET_CONFIG_NAME, "Punch 2nd socket break", "Break risk when item has 1 socket.", RuntimeConfigUI::ensureSocketBreakArray, 1));
        punchBreak.add(chanceArrayControl("socket_break_2", CATEGORY_SOCKET, SOCKET_CONFIG_NAME, "Punch 3rd socket break", "Break risk when item has 2 sockets.", RuntimeConfigUI::ensureSocketBreakArray, 2));
        punchBreak.add(chanceArrayControl("socket_break_3", CATEGORY_SOCKET, SOCKET_CONFIG_NAME, "Punch 4th socket break", "Break risk when item has 3 sockets.", RuntimeConfigUI::ensureSocketBreakArray, 3));
        groups.add(new ControlGroup("punch_break", "Punch Break", "Per-attempt break risks by current socket count.", punchBreak));

        List<NumericControl> specialRules = new ArrayList<>();
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

        List<NumericControl> weaponMultipliers = new ArrayList<>();
        weaponMultipliers.add(multiplierArrayControl("refine_damage_0", CATEGORY_REFINEMENT, "Weapon +0 multiplier", "Damage multiplier at refine level 0.", RuntimeConfigUI::ensureDamageMultipliers, REFINEMENT_CONFIG_NAME, 0));
        weaponMultipliers.add(multiplierArrayControl("refine_damage_1", CATEGORY_REFINEMENT, "Weapon +1 multiplier", "Damage multiplier at refine level 1.", RuntimeConfigUI::ensureDamageMultipliers, REFINEMENT_CONFIG_NAME, 1));
        weaponMultipliers.add(multiplierArrayControl("refine_damage_2", CATEGORY_REFINEMENT, "Weapon +2 multiplier", "Damage multiplier at refine level 2.", RuntimeConfigUI::ensureDamageMultipliers, REFINEMENT_CONFIG_NAME, 2));
        weaponMultipliers.add(multiplierArrayControl("refine_damage_3", CATEGORY_REFINEMENT, "Weapon +3 multiplier", "Damage multiplier at refine level 3.", RuntimeConfigUI::ensureDamageMultipliers, REFINEMENT_CONFIG_NAME, 3));
        groups.add(new ControlGroup("weapon_multipliers", "Weapon Multipliers", "Damage scaling by refinement tier.", weaponMultipliers));

        List<NumericControl> armorMultipliers = new ArrayList<>();
        armorMultipliers.add(multiplierArrayControl("refine_defense_0", CATEGORY_REFINEMENT, "Armor +0 multiplier", "Defense multiplier at refine level 0.", RuntimeConfigUI::ensureDefenseMultipliers, REFINEMENT_CONFIG_NAME, 0));
        armorMultipliers.add(multiplierArrayControl("refine_defense_1", CATEGORY_REFINEMENT, "Armor +1 multiplier", "Defense multiplier at refine level 1.", RuntimeConfigUI::ensureDefenseMultipliers, REFINEMENT_CONFIG_NAME, 1));
        armorMultipliers.add(multiplierArrayControl("refine_defense_2", CATEGORY_REFINEMENT, "Armor +2 multiplier", "Defense multiplier at refine level 2.", RuntimeConfigUI::ensureDefenseMultipliers, REFINEMENT_CONFIG_NAME, 2));
        armorMultipliers.add(multiplierArrayControl("refine_defense_3", CATEGORY_REFINEMENT, "Armor +3 multiplier", "Defense multiplier at refine level 3.", RuntimeConfigUI::ensureDefenseMultipliers, REFINEMENT_CONFIG_NAME, 3));
        groups.add(new ControlGroup("armor_multipliers", "Armor Multipliers", "Defense scaling by refinement tier.", armorMultipliers));

        List<NumericControl> weaponBreak = new ArrayList<>();
        weaponBreak.add(chanceArrayControl("refine_break_weapon_0", CATEGORY_REFINEMENT, REFINEMENT_CONFIG_NAME, "Weapon 0->1 break", "Break chance while attempting +0 to +1.", RuntimeConfigUI::ensureWeaponBreakArray, 0));
        weaponBreak.add(chanceArrayControl("refine_break_weapon_1", CATEGORY_REFINEMENT, REFINEMENT_CONFIG_NAME, "Weapon 1->2 break", "Break chance while attempting +1 to +2.", RuntimeConfigUI::ensureWeaponBreakArray, 1));
        weaponBreak.add(chanceArrayControl("refine_break_weapon_2", CATEGORY_REFINEMENT, REFINEMENT_CONFIG_NAME, "Weapon 2->3 break", "Break chance while attempting +2 to +3.", RuntimeConfigUI::ensureWeaponBreakArray, 2));
        groups.add(new ControlGroup("weapon_break", "Weapon Break", "Break risks for weapon refinement transitions.", weaponBreak));

        List<NumericControl> armorBreak = new ArrayList<>();
        armorBreak.add(chanceArrayControl("refine_break_armor_0", CATEGORY_REFINEMENT, REFINEMENT_CONFIG_NAME, "Armor 0->1 break", "Break chance while attempting +0 to +1.", RuntimeConfigUI::ensureArmorBreakArray, 0));
        armorBreak.add(chanceArrayControl("refine_break_armor_1", CATEGORY_REFINEMENT, REFINEMENT_CONFIG_NAME, "Armor 1->2 break", "Break chance while attempting +1 to +2.", RuntimeConfigUI::ensureArmorBreakArray, 1));
        armorBreak.add(chanceArrayControl("refine_break_armor_2", CATEGORY_REFINEMENT, REFINEMENT_CONFIG_NAME, "Armor 2->3 break", "Break chance while attempting +2 to +3.", RuntimeConfigUI::ensureArmorBreakArray, 2));
        groups.add(new ControlGroup("armor_break", "Armor Break", "Break risks for armor refinement transitions.", armorBreak));

        groups.add(new ControlGroup("weights_0_1", "Outcome Weights 0->1", "Adjust one value and the other outcomes auto-normalize to stay at 100%.", buildWeightControls("refine_weight_0_", 0, "0->1")));
        groups.add(new ControlGroup("weights_1_2", "Outcome Weights 1->2", "Adjust one value and the other outcomes auto-normalize to stay at 100%.", buildWeightControls("refine_weight_1_", 1, "1->2")));
        groups.add(new ControlGroup("weights_2_3", "Outcome Weights 2->3", "Adjust one value and the other outcomes auto-normalize to stay at 100%.", buildWeightControls("refine_weight_2_", 2, "2->3")));

        return new CategorySection(
                CATEGORY_REFINEMENT,
                "toggleRefinementCategory",
                "Refinement",
                "Weapon and armor scaling, break risks, and reforge outcome distributions.",
                groups,
                null);
    }

    private static CategorySection buildLootCategory() {
        List<ControlGroup> groups = new ArrayList<>();

        List<NumericControl> chestRolls = new ArrayList<>();
        chestRolls.add(chanceControl("loot_chest_three", CATEGORY_LOOT, LOOT_CONFIG_NAME, "Chest 3-socket chance", "Chance for rolled chest loot to land at 3 sockets.", () -> lootConfig().getChestThreeSocketChance(), delta -> lootConfig().setChestThreeSocketChance(clampChance(lootConfig().getChestThreeSocketChance() + delta))));
        chestRolls.add(chanceControl("loot_chest_four", CATEGORY_LOOT, LOOT_CONFIG_NAME, "Chest 4-socket chance", "Chance for rolled chest loot to land at 4 sockets.", () -> lootConfig().getChestFourSocketChance(), delta -> lootConfig().setChestFourSocketChance(clampChance(lootConfig().getChestFourSocketChance() + delta))));
        chestRolls.add(chanceControl("loot_chest_five", CATEGORY_LOOT, LOOT_CONFIG_NAME, "Chest 5-socket chance", "Chance for rolled chest loot to land at 5 sockets.", () -> lootConfig().getChestFiveSocketChance(), delta -> lootConfig().setChestFiveSocketChance(clampChance(lootConfig().getChestFiveSocketChance() + delta))));
        chestRolls.add(chanceControl("loot_chest_three_to_four", CATEGORY_LOOT, LOOT_CONFIG_NAME, "Chest 3->4 conversion", "Upgrade chance when chest loot first rolls 3 sockets.", () -> lootConfig().getChestThreeToFourChance(), delta -> lootConfig().setChestThreeToFourChance(clampChance(lootConfig().getChestThreeToFourChance() + delta))));
        chestRolls.add(chanceControl("loot_chest_resonance", CATEGORY_LOOT, LOOT_CONFIG_NAME, "Chest resonance chance", "Chance for chest loot to roll a fully resonant item.", () -> lootConfig().getChestResonanceChance(), delta -> lootConfig().setChestResonanceChance(clampChance(lootConfig().getChestResonanceChance() + delta))));
        groups.add(new ControlGroup("chest_loot", "Chest Loot", "Socket roll tuning for treasure chests.", chestRolls));

        List<NumericControl> dropRolls = new ArrayList<>();
        dropRolls.add(chanceControl("loot_drop_three", CATEGORY_LOOT, LOOT_CONFIG_NAME, "Drop 3-socket chance", "Chance for NPC drops to land at 3 sockets.", () -> lootConfig().getDropThreeSocketChance(), delta -> lootConfig().setDropThreeSocketChance(clampChance(lootConfig().getDropThreeSocketChance() + delta))));
        dropRolls.add(chanceControl("loot_drop_four", CATEGORY_LOOT, LOOT_CONFIG_NAME, "Drop 4-socket chance", "Chance for NPC drops to land at 4 sockets.", () -> lootConfig().getDropFourSocketChance(), delta -> lootConfig().setDropFourSocketChance(clampChance(lootConfig().getDropFourSocketChance() + delta))));
        dropRolls.add(chanceControl("loot_drop_five", CATEGORY_LOOT, LOOT_CONFIG_NAME, "Drop 5-socket chance", "Chance for NPC drops to land at 5 sockets.", () -> lootConfig().getDropFiveSocketChance(), delta -> lootConfig().setDropFiveSocketChance(clampChance(lootConfig().getDropFiveSocketChance() + delta))));
        dropRolls.add(chanceControl("loot_drop_three_to_four", CATEGORY_LOOT, LOOT_CONFIG_NAME, "Drop 3->4 conversion", "Upgrade chance when drop loot first rolls 3 sockets.", () -> lootConfig().getDropThreeToFourChance(), delta -> lootConfig().setDropThreeToFourChance(clampChance(lootConfig().getDropThreeToFourChance() + delta))));
        dropRolls.add(chanceControl("loot_drop_resonance", CATEGORY_LOOT, LOOT_CONFIG_NAME, "Drop resonance chance", "Chance for NPC drops to roll a fully resonant item.", () -> lootConfig().getDropResonanceChance(), delta -> lootConfig().setDropResonanceChance(clampChance(lootConfig().getDropResonanceChance() + delta))));
        groups.add(new ControlGroup("npc_drops", "NPC Drops", "Socket roll tuning for NPC and world drops.", dropRolls));

        List<NumericControl> brokenRange = new ArrayList<>();
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

    private static CategorySection buildWeatherCategory() {
        List<ControlGroup> groups = new ArrayList<>();

        List<NumericControl> timing = new ArrayList<>();
        timing.add(intControlStep(
                "weather_min_interval",
                CATEGORY_WEATHER,
                WEATHER_CONFIG_NAME,
                "Min spawn interval (s)",
                "Minimum seconds between weather spawns per player.",
                1,
                5,
                () -> weatherConfig().getMinSpawnIntervalSeconds(),
                delta -> {
                    WeatherEventConfig cfg = weatherConfig();
                    double value = clamp(cfg.getMinSpawnIntervalSeconds() + delta, 0.1, cfg.getMaxSpawnIntervalSeconds());
                    cfg.setMinSpawnIntervalSeconds(value);
                }));
        timing.add(intControlStep(
                "weather_max_interval",
                CATEGORY_WEATHER,
                WEATHER_CONFIG_NAME,
                "Max spawn interval (s)",
                "Maximum seconds between weather spawns per player.",
                1,
                5,
                () -> weatherConfig().getMaxSpawnIntervalSeconds(),
                delta -> {
                    WeatherEventConfig cfg = weatherConfig();
                    double value = Math.max(cfg.getMinSpawnIntervalSeconds(), cfg.getMaxSpawnIntervalSeconds() + delta);
                    cfg.setMaxSpawnIntervalSeconds(value);
                }));
        timing.add(intControlStep(
                "weather_despawn_delay",
                CATEGORY_WEATHER,
                WEATHER_CONFIG_NAME,
                "Despawn delay (s)",
                "Seconds after rain ends before spirits are removed.",
                1,
                5,
                () -> weatherConfig().getDespawnAfterRainEndSeconds(),
                delta -> {
                    WeatherEventConfig cfg = weatherConfig();
                    double value = clamp(cfg.getDespawnAfterRainEndSeconds() + delta, 0.0, 3600.0);
                    cfg.setDespawnAfterRainEndSeconds(value);
                }));
        groups.add(new ControlGroup("weather_timing", "Timing", "Interval and cleanup timing for weather spawns.", timing));

        List<NumericControl> counts = new ArrayList<>();
        counts.add(intControlStep(
                "weather_max_spirits",
                CATEGORY_WEATHER,
                WEATHER_CONFIG_NAME,
                "Max spirits per player",
                "Hard cap for spirits spawned per player.",
                1,
                5,
                () -> weatherConfig().getMaxSpiritsPerPlayer(),
                delta -> {
                    WeatherEventConfig cfg = weatherConfig();
                    int value = clampInt(cfg.getMaxSpiritsPerPlayer() + (int) Math.round(delta), 1, 100);
                    cfg.setMaxSpiritsPerPlayer(value);
                }));
        counts.add(intControlStep(
                "weather_min_spawns",
                CATEGORY_WEATHER,
                WEATHER_CONFIG_NAME,
                "Min spawns per interval",
                "Minimum number of spirits per spawn interval.",
                1,
                2,
                () -> weatherConfig().getMinSpawnsPerInterval(),
                delta -> {
                    WeatherEventConfig cfg = weatherConfig();
                    int value = clampInt(cfg.getMinSpawnsPerInterval() + (int) Math.round(delta), 1, cfg.getMaxSpawnsPerInterval());
                    cfg.setMinSpawnsPerInterval(value);
                }));
        counts.add(intControlStep(
                "weather_max_spawns",
                CATEGORY_WEATHER,
                WEATHER_CONFIG_NAME,
                "Max spawns per interval",
                "Maximum number of spirits per spawn interval.",
                1,
                2,
                () -> weatherConfig().getMaxSpawnsPerInterval(),
                delta -> {
                    WeatherEventConfig cfg = weatherConfig();
                    int value = Math.max(cfg.getMinSpawnsPerInterval(), cfg.getMaxSpawnsPerInterval() + (int) Math.round(delta));
                    cfg.setMaxSpawnsPerInterval(value);
                }));
        groups.add(new ControlGroup("weather_counts", "Spawn Counts", "Per-player caps and interval spawn counts.", counts));

        List<NumericControl> distance = new ArrayList<>();
        distance.add(intControlStep(
                "weather_min_distance",
                CATEGORY_WEATHER,
                WEATHER_CONFIG_NAME,
                "Min spawn distance",
                "Closest distance from the player.",
                1,
                5,
                () -> weatherConfig().getMinSpawnDistance(),
                delta -> {
                    WeatherEventConfig cfg = weatherConfig();
                    double value = clamp(cfg.getMinSpawnDistance() + delta, 0.0, cfg.getMaxSpawnDistance());
                    cfg.setMinSpawnDistance(value);
                }));
        distance.add(intControlStep(
                "weather_max_distance",
                CATEGORY_WEATHER,
                WEATHER_CONFIG_NAME,
                "Max spawn distance",
                "Farthest distance from the player.",
                1,
                5,
                () -> weatherConfig().getMaxSpawnDistance(),
                delta -> {
                    WeatherEventConfig cfg = weatherConfig();
                    double value = Math.max(cfg.getMinSpawnDistance(), cfg.getMaxSpawnDistance() + delta);
                    cfg.setMaxSpawnDistance(value);
                }));
        groups.add(new ControlGroup("weather_distance", "Spawn Distance", "Spawn radius around the player.", distance));

        return new CategorySection(
                CATEGORY_WEATHER,
                "toggleWeatherCategory",
                "Weather Spawns",
                "Configure Spirit_Thunder weather event spawns.",
                groups,
                "Note: SPIRIT_ROLE and RAIN_KEYWORDS must be edited in WeatherEventConfig.json.");
    }

    private static List<NumericControl> buildWeightControls(String idPrefix, int transition, String labelPrefix) {
        List<NumericControl> weightControls = new ArrayList<>();
        weightControls.add(weightControl(idPrefix + "degrade", transition, 0, labelPrefix + " degrade", "Chance to drop one level."));
        weightControls.add(weightControl(idPrefix + "same", transition, 1, labelPrefix + " same", "Chance to stay at the same level."));
        weightControls.add(weightControl(idPrefix + "upgrade", transition, 2, labelPrefix + " upgrade", "Chance to gain one level."));
        weightControls.add(weightControl(idPrefix + "jackpot", transition, 3, labelPrefix + " jackpot", "Chance to gain two levels."));
        return weightControls;
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
                () -> ensureWeightArray(transition)[index],
                delta -> adjustWeight(transition, index, delta));
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

    private static WeatherEventConfig weatherConfig() {
        return plugin.getWeatherEventRuntimeConfig();
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
        double[] normalized = ensureArray(current, 4, DEFAULT_DAMAGE_MULTIPLIERS);
        if (normalized != current) {
            cfg.setDamageMultipliers(normalized);
        }
        return normalized;
    }

    private static double[] ensureDefenseMultipliers() {
        RefinementConfig cfg = refinementConfig();
        double[] current = cfg.getDefenseMultipliers();
        double[] normalized = ensureArray(current, 4, DEFAULT_DEFENSE_MULTIPLIERS);
        if (normalized != current) {
            cfg.setDefenseMultipliers(normalized);
        }
        return normalized;
    }

    private static double[] ensureWeaponBreakArray() {
        RefinementConfig cfg = refinementConfig();
        double[] current = cfg.getBreakChances();
        double[] normalized = ensureArray(current, 3, DEFAULT_WEAPON_BREAK);
        if (normalized != current) {
            cfg.setBreakChances(normalized);
        }
        return normalized;
    }

    private static double[] ensureArmorBreakArray() {
        RefinementConfig cfg = refinementConfig();
        double[] current = cfg.getArmorBreakChances();
        double[] normalized = ensureArray(current, 3, DEFAULT_ARMOR_BREAK);
        if (normalized != current) {
            cfg.setArmorBreakChances(normalized);
        }
        return normalized;
    }

    private static double[] ensureWeightArray(int transition) {
        RefinementConfig cfg = refinementConfig();
        double[] current = transition == 0 ? cfg.getWeights0to1() : (transition == 1 ? cfg.getWeights1to2() : cfg.getWeights2to3());
        double[] normalized = ensureArray(current, 4, DEFAULT_WEIGHTS[transition]);
        if (normalized != current) {
            setWeightArray(cfg, transition, normalized);
        }
        return normalized;
    }

    private static void adjustWeight(int transition, int index, double delta) {
        double[] weights = ensureWeightArray(transition);
        double target = clamp(weights[index] + delta, 0.0, 1.0);
        if (weights.length <= 1) {
            weights[0] = 1.0;
            return;
        }

        double remainingTarget = Math.max(0.0, 1.0 - target);
        double remainingCurrent = 0.0;
        for (int i = 0; i < weights.length; i++) {
            if (i != index) {
                remainingCurrent += Math.max(0.0, weights[i]);
            }
        }

        weights[index] = target;
        if (remainingCurrent <= 0.0000001) {
            double even = remainingTarget / (weights.length - 1);
            for (int i = 0; i < weights.length; i++) {
                if (i != index) {
                    weights[i] = even;
                }
            }
            return;
        }

        double scale = remainingTarget / remainingCurrent;
        for (int i = 0; i < weights.length; i++) {
            if (i != index) {
                weights[i] = clamp(Math.max(0.0, weights[i]) * scale, 0.0, 1.0);
            }
        }
    }

    private static void setWeightArray(RefinementConfig cfg, int transition, double[] values) {
        if (transition == 0) {
            cfg.setWeights0to1(values);
        } else if (transition == 1) {
            cfg.setWeights1to2(values);
        } else {
            cfg.setWeights2to3(values);
        }
    }

    private static double[] ensureArray(double[] source, int length, double[] defaults) {
        if (source != null && source.length >= length) {
            return source;
        }
        double[] normalized = new double[length];
        for (int i = 0; i < length; i++) {
            normalized[i] = defaults != null && i < defaults.length ? defaults[i] : 0.0;
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
        double bonusPercent = (rawValue - 1.0) * 100.0;
        return "x" + String.format(Locale.ROOT, "%.3f", rawValue)
                + " (" + String.format(Locale.ROOT, "%+.1f%%", bonusPercent) + ")";
    }

    private static String formatStepLabel(DisplayKind kind, double rawStep, boolean positive) {
        String prefix = positive ? "+" : "-";
        if (kind == DisplayKind.INTEGER) {
            return prefix + (int) Math.round(rawStep);
        }
        double percentStep = rawStep * 100.0;
        if (roughlyEqual(percentStep, Math.rint(percentStep))) {
            return prefix + (int) Math.round(percentStep) + "%";
        }
        return prefix + String.format(Locale.ROOT, "%.1f%%", percentStep);
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
}
