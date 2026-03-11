package irai.mod.reforge.UI;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
import irai.mod.reforge.Util.DynamicTooltipUtils;
import irai.mod.reforge.Util.NameResolver;

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

        Entry(String key, String name, String pattern, String usages, ResonantRecipeUtils.PatternStats stats) {
            this.key = key;
            this.name = name;
            this.pattern = pattern;
            this.usages = usages;
            this.stats = stats;
        }
    }

    private static final class Snapshot {
        final List<Entry> entries;
        final int completeCount;

        Snapshot(List<Entry> entries, int completeCount) {
            this.entries = entries;
            this.completeCount = completeCount;
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
            player.sendMessage(Message.raw("<color=#FF5555>HyUI not installed - compendium UI disabled."));
            return;
        }
        PlayerRef ref = player.getPlayerRef();
        activeCompendiums.put(ref, new CompendiumHandle(heldSlot));
        closePageIfOpen(ref);
        pendingSelections.remove(ref);
        player.getWorld().execute(() -> openWithSync(player));
    }

    private static void openWithSync(Player player) {
        PlayerRef ref = player.getPlayerRef();
        CompendiumContext context = resolveCompendium(player);
        if (context == null) {
            closePageIfOpen(ref);
            player.sendMessage(Message.raw("<color=#FF5555>Hold the Resonant Compendium and reopen the UI."));
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
        for (Map.Entry<String, CompendiumEntry> entry : data.entrySet()) {
            String name = entry.getKey();
            CompendiumEntry value = entry.getValue();
            String pattern = value != null && value.pattern != null ? value.pattern : "";
            String usages = value != null && value.usages != null ? value.usages : "";
            ResonantRecipeUtils.PatternStats stats = ResonantRecipeUtils.getPatternStats(pattern);
            if (stats.isComplete()) {
                complete++;
            }
            String key = ResonantRecipeUtils.normalizeRecipeName(name);
            entries.add(new Entry(key, name, pattern, usages, stats));
        }
        entries.sort(Comparator.comparing(e -> e.name.toLowerCase(Locale.ROOT)));
        return new Snapshot(entries, complete);
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

            String html = buildHtml(snapshot, state);
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

            pageBuilder = withLifetime.invoke(pageBuilder, CustomPageLifetime.CanDismiss);
            Object store = getStore(playerRef);
            Object page = openMethod.invoke(pageBuilder, store);
            openPages.put(playerRef, page);
        } catch (Exception e) {
            System.err.println("[SocketReforge] ResonantCompendiumUI open error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String buildHtml(Snapshot snapshot, SelectionState state) {
        String selectedKey = state != null ? state.selectedKey : null;
        String status = state != null && state.statusText != null ? state.statusText : "Select a recipe to extract.";

        Entry selected = resolveSelection(snapshot.entries, selectedKey);
        if (selected == null && !snapshot.entries.isEmpty()) {
            selected = snapshot.entries.get(0);
            selectedKey = selected.key;
        }

        if (snapshot.entries.isEmpty()) {
            status = "Compendium is empty.";
        }

        String recipeOptions = buildRecipeOptions(snapshot.entries, selectedKey);
        String summaryText = buildSummaryText(snapshot);

        String recipeName = selected != null ? selected.name : "-";
        String recipeType = selected != null ? resolveRecipeType(selected) : "-";
        String recipeProgress = selected != null
                ? selected.stats.revealedSlots() + "/" + selected.stats.totalSlots() + " slots revealed"
                + (selected.stats.isComplete() ? " (complete)" : "")
                : "-";
        String recipeUsages = selected != null ? formatUsages(selected) : "-";
        String patternPreview = buildPatternPreviewHtml(selected);

        boolean canExtract = selected != null && selected.stats.isComplete();
        String extractDisabledAttr = canExtract ? "" : "disabled=\"true\"";

        String html = loadTemplate();
        html = html.replace("{{recipeOptions}}", recipeOptions);
        html = html.replace("{{summaryText}}", escapeHtml(summaryText));
        html = html.replace("{{recipeName}}", escapeHtml(recipeName));
        html = html.replace("{{recipeType}}", escapeHtml(recipeType));
        html = html.replace("{{recipeProgress}}", escapeHtml(recipeProgress));
        html = html.replace("{{recipeUsages}}", escapeHtml(recipeUsages));
        html = html.replace("{{patternPreview}}", patternPreview);
        html = html.replace("{{statusText}}", escapeHtml(status));
        html = html.replace("{{extractDisabledAttr}}", extractDisabledAttr);
        return html;
    }

    private static ProcessResult processExtract(Player player, Snapshot snapshot, String selectionKey) {
        if (selectionKey == null || selectionKey.isBlank()) {
            return new ProcessResult("Pick a recipe first.");
        }
        CompendiumContext context = resolveCompendium(player);
        if (context == null) {
            return new ProcessResult("Compendium moved. Reopen the UI.");
        }
        Map<String, CompendiumEntry> data = ResonantCompendiumUtils.getCompendiumData(context.compendium);
        Map.Entry<String, CompendiumEntry> target = findEntry(data, selectionKey);
        if (target == null) {
            return new ProcessResult("Recipe not found. Reopen the UI.");
        }

        CompendiumEntry entry = target.getValue();
        String pattern = entry != null && entry.pattern != null ? entry.pattern : "";
        ResonantRecipeUtils.PatternStats stats = ResonantRecipeUtils.getPatternStats(pattern);
        if (!stats.isComplete()) {
            return new ProcessResult("Recipe incomplete. Absorb more shards.");
        }

        ItemStack recipe = buildRecipeItem(target.getKey(), entry);
        if (!UIInventoryUtils.addItemToInventory(player, recipe)) {
            return new ProcessResult("Inventory full. Make space first.");
        }

        data.remove(target.getKey());
        ItemStack updated = ResonantCompendiumUtils.saveCompendiumData(context.compendium, data);
        ItemContainer hotbar = player.getInventory().getHotbar();
        if (hotbar != null) {
            hotbar.setItemStackForSlot(context.slot, updated);
        }
        if (DynamicTooltipUtils.isAvailable()) {
            DynamicTooltipUtils.refreshAllPlayers();
        }
        return new ProcessResult("Extracted \"" + target.getKey() + "\" recipe.");
    }

    private static ItemStack buildRecipeItem(String recipeName, CompendiumEntry entry) {
        String safeName = recipeName == null ? "" : recipeName.trim();
        String pattern = entry != null && entry.pattern != null ? entry.pattern : "";
        String usages = entry != null && entry.usages != null ? entry.usages : "";
        ItemStack recipe = new ItemStack(ResonantRecipeUtils.RECIPE_ITEM_ID, 1)
                .withMetadata(NameResolver.KEY_DISPLAY_NAME, Codec.STRING, safeName + " Recipe")
                .withMetadata(ResonantRecipeUtils.META_RECIPE_NAME, Codec.STRING, safeName)
                .withMetadata(ResonantRecipeUtils.META_RECIPE_PATTERN, Codec.STRING, pattern);
        String type = resolveRecipeTypeFromSystem(safeName);
        if (type != null && !type.isBlank()) {
            recipe = recipe.withMetadata(ResonantRecipeUtils.META_RECIPE_TYPE, Codec.STRING, type);
        }
        if (usages != null && !usages.isBlank()) {
            recipe = recipe.withMetadata(ResonantRecipeUtils.META_RECIPE_USAGES, Codec.STRING, usages);
        }
        return ResonantRecipeUtils.ensureRecipeUsages(recipe);
    }

    private static Map.Entry<String, CompendiumEntry> findEntry(Map<String, CompendiumEntry> data, String key) {
        if (data == null || key == null) {
            return null;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, CompendiumEntry> entry : data.entrySet()) {
            String name = entry.getKey();
            if (name == null) {
                continue;
            }
            if (ResonantRecipeUtils.normalizeRecipeName(name).equals(normalized)) {
                return entry;
            }
        }
        return null;
    }

    private static String resolveRecipeType(Entry entry) {
        if (entry == null) {
            return "Unknown";
        }
        String type = resolveRecipeTypeFromSystem(entry.name);
        return type == null || type.isBlank() ? "Unknown" : type;
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

    private static String formatUsages(Entry entry) {
        if (entry == null) {
            return "-";
        }
        if (!entry.stats.isComplete()) {
            return "Complete recipe to unlock";
        }
        String usages = entry.usages;
        if (usages == null || usages.isBlank()) {
            return ResonantRecipeUtils.DEFAULT_RECIPE_USAGES;
        }
        return usages;
    }

    private static String buildSummaryText(Snapshot snapshot) {
        int total = snapshot.entries.size();
        int complete = snapshot.completeCount;
        int incomplete = Math.max(0, total - complete);
        return "Stored: " + total + " total, " + complete + " complete, " + incomplete + " incomplete.";
    }

    private static String buildRecipeOptions(List<Entry> entries, String selectedKey) {
        if (entries.isEmpty()) {
            return "<option value=\"\" selected=\"true\">No stored recipes</option>";
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
            sb.append(escapeHtml(entry.name));
            sb.append(" [").append(entry.stats.revealedSlots()).append("/")
                    .append(entry.stats.totalSlots()).append("]");
            if (entry.stats.isComplete()) sb.append(" (complete)");
            sb.append("</option>");
        }
        return sb.toString();
    }

    private static Entry resolveSelection(List<Entry> entries, String key) {
        if (entries == null || entries.isEmpty() || key == null || key.isBlank()) {
            return null;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
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

    private static String buildPatternPreviewHtml(Entry entry) {
        if (entry == null || entry.pattern == null || entry.pattern.isBlank()) {
            StringBuilder empty = new StringBuilder();
            empty.append("<div style=\"layout-mode: Left; spacing: 10;\">");
            empty.append("<div style=\"flex-weight:1;\"></div>");
            empty.append("<p>No pattern data.</p>");
            empty.append("<div style=\"flex-weight:1;\"></div>");
            empty.append("</div>");
            return empty.toString();
        }

        List<String> tokens = parsePatternTokens(entry.pattern);
        if (tokens.isEmpty()) {
            return "<div style=\"layout-mode: Left; spacing: 10;\"><p>No pattern data.</p></div>";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"layout-mode: Left; spacing: 8;\"><div style=\"flex-weight:1;\"></div>");
        for (String token : tokens) {
            boolean unknown = isUnknownToken(token);
            String color = unknown ? "#555555" : getEssenceColorHex(token);
            String label = unknown ? "?" : capitalize(token);
            sb.append("<div style=\"anchor-width:80; anchor-height:80; background-color:")
                    .append(color)
                    .append("; layout-mode:Top; padding:4; border-radius:6;\">")
                    .append("<div style=\"flex-weight:1;\"></div>")
                    .append("<p style=\"text-align:center; font-size:12;\">")
                    .append(escapeHtml(label))
                    .append("</p>")
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

    private static boolean isUnknownToken(String token) {
        if (token == null) return true;
        String trimmed = token.trim();
        return trimmed.isEmpty() || "x".equalsIgnoreCase(trimmed);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        String lower = s.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String getEssenceColorHex(String token) {
        if (token == null) return "#555555";
        String lower = token.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("fire")) return "#FFAA00";
        if (lower.contains("ice")) return "#55FFFF";
        if (lower.contains("life")) return "#55FF55";
        if (lower.contains("lightning")) return "#FFFF55";
        if (lower.contains("void")) return "#AA55FF";
        if (lower.contains("water")) return "#5555FF";
        return "#888888";
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

    private static String escapeHtml(String text) {
        return UITemplateUtils.escapeHtml(text);
    }
}
