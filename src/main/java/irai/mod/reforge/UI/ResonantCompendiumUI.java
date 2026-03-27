package irai.mod.reforge.UI;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

import irai.mod.reforge.Common.ResonantCompendiumUtils;
import irai.mod.reforge.Common.ResonantCompendiumUtils.CompendiumEntry;
import irai.mod.reforge.Common.ResonantRecipeUtils;
import irai.mod.reforge.Common.UI.HyUIReflectionUtils;
import irai.mod.reforge.Common.UI.UIInventoryUtils;
import irai.mod.reforge.Common.UI.UITemplateUtils;
import irai.mod.reforge.Socket.ResonanceSystem;
import irai.mod.reforge.UI.RecipeCombineUI;
import irai.mod.reforge.Util.DynamicTooltipUtils;
import irai.mod.reforge.Util.NameResolver;
import irai.mod.reforge.Util.LangLoader;

/**
 * HyUI page for pulling recipes out of the Resonant Compendium.
 * Uses reflection so HyUI is optional at runtime.
 */
public final class ResonantCompendiumUI {
    private ResonantCompendiumUI() {}

    private static final String HYUI_PAGE_BUILDER = "au.ellie.hyui.builders.PageBuilder";
    private static final String HYUI_PLUGIN = "au.ellie.hyui.HyUIPlugin";
    private static final String HYUI_EVENT_BINDING = "com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType";
    private static final String TEMPLATE_PATH = "Common/UI/Custom/Pages/ResonantCompendium.html";

    private static boolean hyuiAvailable = false;

    private static final Map<PlayerRef, Object> openPages = new ConcurrentHashMap<>();
    private static final Map<PlayerRef, SelectionState> pendingSelections = new ConcurrentHashMap<>();
    private static final Map<PlayerRef, CompendiumHandle> activeCompendiums = new ConcurrentHashMap<>();
    private static final Map<PlayerRef, Short> pendingCombineSlots = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private static final class CompendiumHandle {
        final short slot;

        CompendiumHandle(short slot) {
            this.slot = slot;
        }
    }

    private static final class CompendiumContext {
        final short slot;
        final ItemStack compendium;

        CompendiumContext(short slot, ItemStack compendium) {
            this.slot = slot;
            this.compendium = compendium;
        }
    }

    private static final class Entry {
        final String key;
        final String name;
        final String pattern;
        final String usages;
        final ResonantRecipeUtils.PatternStats stats;
        final int quantity;

        Entry(String key, String name, String pattern, String usages, ResonantRecipeUtils.PatternStats stats, int quantity) {
            this.key = key;
            this.name = name;
            this.pattern = pattern;
            this.usages = usages;
            this.stats = stats;
            this.quantity = Math.max(1, quantity);
        }
    }

    private static final class Snapshot {
        final List<Entry> entries;
        final int completeCount;
        final int totalQuantity;

        Snapshot(List<Entry> entries, int completeCount, int totalQuantity) {
            this.entries = entries;
            this.completeCount = completeCount;
            this.totalQuantity = totalQuantity;
        }
    }

    private static final class SelectionState {
        final String selectedKey;
        final String statusText;

        SelectionState(String selectedKey, String statusText) {
            this.selectedKey = selectedKey;
            this.statusText = statusText;
        }
    }

    private static final class ProcessResult {
        final String status;

        ProcessResult(String status) {
            this.status = status;
        }
    }

    public static void initialize() {
        hyuiAvailable = HyUIReflectionUtils.detectHyUi(HYUI_PAGE_BUILDER, HYUI_PLUGIN, "ResonantCompendiumUI");
    }

    public static boolean isAvailable() {
        return hyuiAvailable;
    }

    public static void open(Player player, short heldSlot) {
        if (player == null) {
            return;
        }
        if (!hyuiAvailable) {
            player.sendMessage(Message.raw("<color=#FF5555>" + LangLoader.getUITranslation(player, "ui.compendium.hyui_missing")));
            return;
        }
        PlayerRef ref = player.getPlayerRef();
        activeCompendiums.put(ref, new CompendiumHandle(heldSlot));
        pendingCombineSlots.remove(ref);
        closePageIfOpen(ref);
        pendingSelections.remove(ref);
        player.getWorld().execute(() -> openWithSync(player));
    }

    private static void openWithSync(Player player) {
        PlayerRef ref = player.getPlayerRef();
        CompendiumContext context = resolveCompendium(player);
        if (context == null) {
            closePageIfOpen(ref);
            player.sendMessage(Message.raw("<color=#FF5555>" + LangLoader.getUITranslation(player, "ui.compendium.error_hold")));
            return;
        }
        Snapshot snapshot = collectSnapshot(context.compendium);
        SelectionState state = pendingSelections.remove(ref);
        openPage(player, snapshot, state);
    }

    private static CompendiumContext resolveCompendium(Player player) {
        if (player == null || player.getInventory() == null) {
            return null;
        }
        PlayerRef ref = player.getPlayerRef();
        CompendiumHandle handle = activeCompendiums.get(ref);
        if (handle == null) {
            return null;
        }
        ItemContainer hotbar = player.getInventory().getHotbar();
        if (hotbar == null) {
            return null;
        }
        ItemStack compendium = hotbar.getItemStack(handle.slot);
        if (!ResonantCompendiumUtils.isCompendiumItem(compendium)) {
            return null;
        }
        return new CompendiumContext(handle.slot, compendium);
    }

    private static Snapshot collectSnapshot(ItemStack compendium) {
        Map<String, CompendiumEntry> data = ResonantCompendiumUtils.getCompendiumData(compendium);
        List<Entry> entries = new ArrayList<>();
        int complete = 0;
        int totalQuantity = 0;
        for (Map.Entry<String, CompendiumEntry> entry : data.entrySet()) {
            String dataKey = entry.getKey();
            CompendiumEntry value = entry.getValue();
            String name = value != null && value.name != null ? value.name : "";
            String pattern = value != null && value.pattern != null ? value.pattern : "";
            String usages = value != null && value.usages != null ? value.usages : "";
            int quantity = value != null ? value.quantity : 1;
            quantity = Math.max(1, quantity);
            ResonantRecipeUtils.PatternStats stats = ResonantRecipeUtils.getPatternStats(pattern);
            if (stats.isComplete()) {
                complete++;
            }
            String key = encodeKey(dataKey);
            entries.add(new Entry(key, name, pattern, usages, stats, quantity));
            totalQuantity += quantity;
        }
        entries.sort(Comparator.comparing(e -> e.name.toLowerCase(Locale.ROOT)));
        return new Snapshot(entries, complete, totalQuantity);
    }

    private static void openPage(Player player, Snapshot snapshot, SelectionState state) {
        PlayerRef playerRef = player.getPlayerRef();
        try {
            Class<?> pageBuilderClass = Class.forName(HYUI_PAGE_BUILDER);
            Class<?> eventBindingClass = Class.forName(HYUI_EVENT_BINDING);

            Method pageForPlayer = pageBuilderClass.getMethod("pageForPlayer", PlayerRef.class);
            Method fromHtml = pageBuilderClass.getMethod("fromHtml", String.class);
            Method addListener = pageBuilderClass.getMethod("addEventListener", String.class,
                    eventBindingClass, java.util.function.BiConsumer.class);
            Method withLifetime = pageBuilderClass.getMethod("withLifetime", CustomPageLifetime.class);
            Method openMethod = pageBuilderClass.getMethod("open",
                    Class.forName("com.hypixel.hytale.component.Store"));

            Object valueChanged = eventBindingClass.getField("ValueChanged").get(null);
            Object activating = eventBindingClass.getField("Activating").get(null);
            String html = buildHtml(player, snapshot, state);
            Object pageBuilder = pageForPlayer.invoke(null, playerRef);
            pageBuilder = fromHtml.invoke(pageBuilder, html);

            final Player finalPlayer = player;
            final Snapshot finalSnapshot = snapshot;

            addListener.invoke(pageBuilder, "recipeDropdown", valueChanged,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        String selected = extractEventValue(eventObj);
                        pendingSelections.put(finalPlayer.getPlayerRef(), new SelectionState(selected, null));
                        finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));
                    });

            addListener.invoke(pageBuilder, "extractButton", activating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        String selection = getContextValue(ctxObj, "recipeDropdown", "#recipeDropdown.value");
                        ProcessResult result = processExtract(finalPlayer, finalSnapshot, selection);
                        pendingSelections.put(finalPlayer.getPlayerRef(),
                                new SelectionState(selection, result.status));
                        finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));
                    });

            addListener.invoke(pageBuilder, "openCombineButton", activating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        CompendiumContext context = resolveCompendium(finalPlayer);
                        if (context == null) {
                            finalPlayer.sendMessage(Message.raw("<color=#FF5555>" + LangLoader.getUITranslation(finalPlayer, "ui.compendium.error_hold")));
                            return;
                        }
                        if (RecipeCombineUI.isAvailable()) {
                            // Close compendium UI before opening combine UI to avoid HyUI focus conflicts.
                            PlayerRef ref = finalPlayer.getPlayerRef();
                            pendingCombineSlots.put(ref, context.slot);
                            closePageIfOpen(ref);
                            // Fallback: if onDismiss isn't triggered, open after a short delay.
                            scheduler.schedule(() -> {
                                Short slot = pendingCombineSlots.remove(ref);
                                if (slot == null || finalPlayer.getWorld() == null || !RecipeCombineUI.isAvailable()) {
                                    return;
                                }
                                finalPlayer.getWorld().execute(() -> {
                                    stopCompendiumAnimation(finalPlayer);
                                    RecipeCombineUI.openFromCompendium(finalPlayer, slot);
                                });
                            }, 100, TimeUnit.MILLISECONDS);
                        } else {
                            finalPlayer.sendMessage(Message.raw("<color=#FF5555>" + LangLoader.getUITranslation(finalPlayer, "ui.recipe_combine.hyui_missing")));
                        }
                    });

            Method onDismiss = pageBuilderClass.getMethod("onDismiss", java.util.function.BiConsumer.class);
            pageBuilder = onDismiss.invoke(pageBuilder,
                    (java.util.function.BiConsumer<Object, Object>) (pageObj, dismissedByPlayer) -> {
                        PlayerRef ref = finalPlayer.getPlayerRef();
                        Object current = openPages.get(ref);
                        if (current != pageObj) {
                            return;
                        }
                        openPages.remove(ref);
                        Short slot = pendingCombineSlots.remove(ref);
                        if (slot != null && RecipeCombineUI.isAvailable()) {
                            stopCompendiumAnimation(finalPlayer);
                            RecipeCombineUI.openFromCompendium(finalPlayer, slot);
                            return;
                        }
                        if (dismissedByPlayer instanceof Boolean dismissed && dismissed) {
                            stopCompendiumAnimation(finalPlayer);
                        }
                    });

            pageBuilder = withLifetime.invoke(pageBuilder, CustomPageLifetime.CanDismiss);
            Object store = getStore(playerRef);
            Object page = openMethod.invoke(pageBuilder, store);
            openPages.put(playerRef, page);
        } catch (Exception e) {
            System.err.println("[SocketReforge] ResonantCompendiumUI open error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String buildHtml(Player player, Snapshot snapshot, SelectionState state) {
        String selectedKey = state != null ? state.selectedKey : null;
        String status = state != null && state.statusText != null
                ? state.statusText
                : LangLoader.getUITranslation(player, "ui.compendium.status_select_recipe");

        Entry selected = resolveSelection(snapshot.entries, selectedKey);
        if (selected == null && !snapshot.entries.isEmpty()) {
            selected = snapshot.entries.get(0);
            selectedKey = selected.key;
        }

        if (snapshot.entries.isEmpty()) {
            status = LangLoader.getUITranslation(player, "ui.compendium.status_empty");
        }

        String recipeOptions = buildRecipeOptions(player, snapshot.entries, selectedKey);
        String summaryText = buildSummaryText(player, snapshot);

        String recipeName = selected != null ? localizeRecipeName(player, selected.name) : "-";
        String recipeType = selected != null ? resolveRecipeType(player, selected) : "-";
        String recipeProgress = "-";
        if (selected != null) {
            String suffix = "";
            if (selected.stats.isComplete()) {
                String completeSuffix = LangLoader.getUITranslation(player, "ui.compendium.recipe_complete_suffix");
                if (completeSuffix != null && !completeSuffix.isBlank()) {
                    suffix = " " + completeSuffix;
                }
            }
            recipeProgress = LangLoader.getUITranslation(player, "ui.compendium.recipe_progress",
                    selected.stats.revealedSlots(), selected.stats.totalSlots(), suffix);
        }
        String recipeQuantity = selected != null ? String.valueOf(selected.quantity) : "-";
        String recipeUsages = selected != null ? formatUsages(player, selected) : "-";
        String patternPreview = buildPatternPreviewHtml(player, selected);

        boolean canExtract = selected != null;
        String extractDisabledAttr = canExtract ? "" : "disabled=\"true\"";

        String html = loadTemplate();
        html = html.replace("{{recipeOptions}}", recipeOptions);
        html = html.replace("{{summaryText}}", escapeHtml(summaryText));
        html = html.replace("{{recipeName}}", escapeHtml(recipeName));
        html = html.replace("{{recipeType}}", escapeHtml(recipeType));
        html = html.replace("{{recipeProgress}}", escapeHtml(recipeProgress));
        html = html.replace("{{recipeQuantity}}", escapeHtml(recipeQuantity));
        html = html.replace("{{recipeUsages}}", escapeHtml(recipeUsages));
        html = html.replace("{{patternPreview}}", patternPreview);
        html = html.replace("{{statusText}}", escapeHtml(status));
        html = html.replace("{{extractDisabledAttr}}", extractDisabledAttr);
        return LangLoader.replaceUiTokens(player, html);
    }

    private static ProcessResult processExtract(Player player, Snapshot snapshot, String selectionKey) {
        if (selectionKey == null || selectionKey.isBlank()) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.compendium.error_pick_recipe"));
        }
        CompendiumContext context = resolveCompendium(player);
        if (context == null) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.compendium.error_compendium_moved"));
        }
        Map<String, CompendiumEntry> data = ResonantCompendiumUtils.getCompendiumData(context.compendium);
        String dataKey = decodeKey(selectionKey);
        if (dataKey == null || dataKey.isBlank()) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.compendium.error_recipe_missing"));
        }
        CompendiumEntry entry = data.get(dataKey);
        if (entry == null) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.compendium.error_recipe_missing"));
        }

        String pattern = entry != null && entry.pattern != null ? entry.pattern : "";
        ResonantRecipeUtils.PatternStats stats = ResonantRecipeUtils.getPatternStats(pattern);
        boolean complete = stats.isComplete();

        int quantity = Math.max(1, entry.quantity);
        ItemStack recipe = buildRecipeItem(player, entry.name, entry, complete);
        if (!UIInventoryUtils.addItemToInventory(player, recipe)) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.compendium.error_inventory_full"));
        }

        int remaining = Math.max(0, quantity - 1);
        if (remaining <= 0) {
            data.remove(dataKey);
        } else {
            entry.quantity = remaining;
        }

        ItemStack updated = ResonantCompendiumUtils.saveCompendiumData(context.compendium, data);
        ItemContainer hotbar = player.getInventory().getHotbar();
        if (hotbar != null) {
            hotbar.setItemStackForSlot(context.slot, updated);
        }
        if (DynamicTooltipUtils.isAvailable()) {
            DynamicTooltipUtils.refreshAllPlayers();
        }
        if (remaining > 0) {
            return new ProcessResult(LangLoader.getUITranslation(player, "ui.compendium.status_extracted_remaining", remaining));
        }
        return new ProcessResult(LangLoader.getUITranslation(player, "ui.compendium.status_extracted"));
    }

    private static ItemStack buildRecipeItem(Player player, String recipeName, CompendiumEntry entry, boolean complete) {
        String safeName = recipeName == null ? "" : recipeName.trim();
        String displayName = localizeRecipeName(player, safeName);
        String pattern = entry != null && entry.pattern != null ? entry.pattern : "";
        String usages = entry != null && entry.usages != null ? entry.usages : "";
        ItemStack recipe = new ItemStack(ResonantRecipeUtils.RECIPE_ITEM_ID, 1)
                .withMetadata(NameResolver.KEY_DISPLAY_NAME, Codec.STRING,
                        LangLoader.getUITranslation(player, "ui.compendium.recipe_display_name", displayName))
                .withMetadata(ResonantRecipeUtils.META_RECIPE_NAME, Codec.STRING, safeName)
                .withMetadata(ResonantRecipeUtils.META_RECIPE_PATTERN, Codec.STRING, pattern);
        String type = resolveRecipeTypeFromSystem(safeName);
        if (type != null && !type.isBlank()) {
            recipe = recipe.withMetadata(ResonantRecipeUtils.META_RECIPE_TYPE, Codec.STRING, type);
        }
        if (usages != null && !usages.isBlank()) {
            recipe = recipe.withMetadata(ResonantRecipeUtils.META_RECIPE_USAGES, Codec.STRING, usages);
        }
        return complete ? ResonantRecipeUtils.ensureRecipeUsages(recipe) : recipe;
    }

    private static String resolveRecipeType(Player player, Entry entry) {
        if (entry == null) {
            return LangLoader.getUITranslation(player, "ui.compendium.type_unknown");
        }
        String type = resolveRecipeTypeFromSystem(entry.name);
        if (type == null || type.isBlank()) {
            return LangLoader.getUITranslation(player, "ui.compendium.type_unknown");
        }
        return ResonanceSystem.localizeAppliesTo(type, player);
    }

    private static String resolveRecipeTypeFromSystem(String recipeName) {
        if (recipeName == null || recipeName.isBlank()) {
            return "";
        }
        String normalized = ResonantRecipeUtils.normalizeRecipeName(recipeName);
        for (ResonanceSystem.RecipeDisplay display : ResonanceSystem.getSeededRecipeDisplays()) {
            if (display == null || display.name() == null) {
                continue;
            }
            if (ResonantRecipeUtils.normalizeRecipeName(display.name()).equals(normalized)) {
                return display.appliesTo();
            }
        }
        return "";
    }

    private static String formatUsages(Player player, Entry entry) {
        if (entry == null) {
            return LangLoader.getUITranslation(player, "ui.compendium.usages_none");
        }
        if (!entry.stats.isComplete()) {
            return LangLoader.getUITranslation(player, "ui.compendium.usages_incomplete");
        }
        String usages = entry.usages;
        if (usages == null || usages.isBlank()) {
            return ResonantRecipeUtils.DEFAULT_RECIPE_USAGES;
        }
        return usages;
    }

    private static String buildSummaryText(Player player, Snapshot snapshot) {
        int total = snapshot.entries.size();
        int complete = snapshot.completeCount;
        int incomplete = Math.max(0, total - complete);
        int totalQuantity = snapshot.totalQuantity;
        return LangLoader.getUITranslation(player, "ui.compendium.summary",
                total, complete, incomplete, totalQuantity);
    }

    private static String localizeRecipeName(Player player, String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return rawName == null ? "" : rawName;
        }
        return ResonanceSystem.getLocalizedName(rawName, player);
    }

    private static String buildRecipeOptions(Player player, List<Entry> entries, String selectedKey) {
        if (entries.isEmpty()) {
            return "<option value=\"\" selected=\"true\">"
                    + escapeHtml(LangLoader.getUITranslation(player, "ui.compendium.empty_no_recipes"))
                    + "</option>";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            String key = entry.key;
            boolean selected = key != null && key.equals(selectedKey);
            if (selectedKey == null && i == 0) {
                selected = true;
            }
            sb.append("<option value=\"").append(escapeHtml(key)).append("\"");
            if (selected) sb.append(" selected=\"true\"");
            sb.append(">");
            sb.append(escapeHtml(localizeRecipeName(player, entry.name)));
            if (entry.quantity > 1) {
                sb.append(" x").append(entry.quantity);
            }
            sb.append(" [").append(entry.stats.revealedSlots()).append("/")
                    .append(entry.stats.totalSlots()).append("]");
            if (entry.stats.isComplete()) {
                sb.append(" ").append(LangLoader.getUITranslation(player, "ui.compendium.recipe_complete_suffix"));
            }
            sb.append("</option>");
        }
        return sb.toString();
    }

    private static Entry resolveSelection(List<Entry> entries, String key) {
        if (entries == null || entries.isEmpty() || key == null || key.isBlank()) {
            return null;
        }
        String normalized = key.trim();
        for (Entry entry : entries) {
            if (entry == null || entry.key == null) {
                continue;
            }
            if (entry.key.equals(normalized)) {
                return entry;
            }
        }
        return null;
    }

    private static String buildPatternPreviewHtml(Player player, Entry entry) {
        if (entry == null || entry.pattern == null || entry.pattern.isBlank()) {
            StringBuilder empty = new StringBuilder();
            empty.append("<div style=\"layout-mode: Left; spacing: 10;\">");
            empty.append("<div style=\"flex-weight:1;\"></div>");
            empty.append("<p>").append(escapeHtml(LangLoader.getUITranslation(player, "ui.compendium.no_pattern"))).append("</p>");
            empty.append("<div style=\"flex-weight:1;\"></div>");
            empty.append("</div>");
            return empty.toString();
        }

        List<String> tokens = parsePatternTokens(entry.pattern);
        if (tokens.isEmpty()) {
            return "<div style=\"layout-mode: Left; spacing: 10;\"><p>"
                    + escapeHtml(LangLoader.getUITranslation(player, "ui.compendium.no_pattern"))
                    + "</p></div>";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"layout-mode: Left; spacing: 8;\"><div style=\"flex-weight:1;\"></div>");
        for (String token : tokens) {
            String icon = resolveEssenceIcon(token);
            sb.append("<div style=\"anchor-width:80; anchor-height:80; background-color:#1a1a2b; layout-mode:Top; padding:6; border-radius:6;\">")
                    .append("<div style=\"flex-weight:1;\"></div>")
                    .append("<img src=\"").append(icon).append("\" width=\"64\" height=\"64\"/>")
                    .append("<div style=\"flex-weight:1;\"></div>")
                    .append("</div>");
        }
        sb.append("<div style=\"flex-weight:1;\"></div></div>");
        return sb.toString();
    }

    private static List<String> parsePatternTokens(String pattern) {
        List<String> tokens = new ArrayList<>();
        if (pattern == null || pattern.isBlank()) {
            return tokens;
        }

        int idx = 0;
        while (idx < pattern.length()) {
            int start = pattern.indexOf('[', idx);
            if (start < 0) break;
            int end = pattern.indexOf(']', start + 1);
            if (end < 0) break;
            String token = pattern.substring(start + 1, end).trim();
            tokens.add(token.isEmpty() ? "x" : token);
            idx = end + 1;
        }

        if (!tokens.isEmpty()) {
            return tokens;
        }

        String[] parts = pattern.split(",");
        for (String part : parts) {
            String token = part == null ? "" : part.trim();
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static String resolveEssenceIcon(String token) {
        if (token == null || token.isBlank()) {
            return "slot_bg.png";
        }
        String lower = token.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("fire")) return "Icons/ItemsGenerated/Ingredient_Fire_Essence.png";
        if (lower.contains("ice")) return "Icons/ItemsGenerated/Ingredient_Ice_Essence.png";
        if (lower.contains("life")) return "Icons/ItemsGenerated/Ingredient_Life_Essence.png";
        if (lower.contains("lightning")) return "Icons/ItemsGenerated/Ingredient_Lightning_Essence.png";
        if (lower.contains("void")) return "Icons/ItemsGenerated/Ingredient_Void_Essence.png";
        if (lower.contains("water")) return "Icons/ItemsGenerated/Ingredient_Water_Essence.png";
        return "slot_bg.png";
    }

    private static String loadTemplate() {
        return UITemplateUtils.loadTemplate(
                ResonantCompendiumUI.class,
                TEMPLATE_PATH,
                "<div><p>Compendium UI template missing.</p></div>",
                "ResonantCompendiumUI");
    }

    private static String extractEventValue(Object eventObj) {
        return HyUIReflectionUtils.extractEventValue(eventObj);
    }

    private static String getContextValue(Object ctxObj, String... keys) {
        return HyUIReflectionUtils.getContextValue(ctxObj, keys);
    }

    private static Object getStore(PlayerRef ref) throws Exception {
        return HyUIReflectionUtils.getStore(ref);
    }

    private static void closePageIfOpen(PlayerRef ref) {
        HyUIReflectionUtils.closePageIfOpen(openPages, ref);
    }

    private static void stopCompendiumAnimation(Player player) {
        if (player == null || player.getInventory() == null) {
            return;
        }
        PlayerRef ref = player.getPlayerRef();
        CompendiumHandle handle = activeCompendiums.remove(ref);
        if (handle == null) {
            return;
        }
        ItemContainer hotbar = player.getInventory().getHotbar();
        if (hotbar == null) {
            return;
        }
        ItemStack stack = hotbar.getItemStack(handle.slot);
        if (stack == null || stack.isEmpty()) {
            return;
        }
        // Re-set the stack to nudge the client to refresh the held-item animation state.
        hotbar.setItemStackForSlot(handle.slot, stack);
    }

    private static String escapeHtml(String text) {
        return UITemplateUtils.escapeHtml(text);
    }

    private static String encodeKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeKey(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(encoded);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
