package irai.mod.reforge.UI;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

import irai.mod.reforge.Common.ResonantRecipeUtils;
import irai.mod.reforge.Common.UI.HyUIReflectionUtils;
import irai.mod.reforge.Common.UI.UIItemUtils;
import irai.mod.reforge.Common.UI.UITemplateUtils;
import irai.mod.reforge.Socket.Essence;
import irai.mod.reforge.Socket.ResonanceSystem;
import irai.mod.reforge.Util.DynamicTooltipUtils;

/**
 * HyUI page for combining resonant recipe shards.
 * Uses reflection to keep HyUI optional at runtime.
 */
public final class RecipeCombineUI {
    private RecipeCombineUI() {}

    private static final String HYUI_PAGE_BUILDER  = "au.ellie.hyui.builders.PageBuilder";
    private static final String HYUI_PLUGIN        = "au.ellie.hyui.HyUIPlugin";
    private static final String HYUI_EVENT_BINDING = "com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType";
    private static final String TEMPLATE_PATH      = "Common/UI/Custom/Pages/RecipeCombineBench.html";

    private static boolean hyuiAvailable = false;

    private static final Map<PlayerRef, Object> openPages = new ConcurrentHashMap<>();
    private static final Map<PlayerRef, SelectionState> pendingSelections = new ConcurrentHashMap<>();
    private static final Map<PlayerRef, Boolean> processingPlayers = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final int PROCESS_DURATION_MS = 800;
    private static final int PROGRESS_TICK_MS = 50;

    private enum ContainerKind { HOTBAR, STORAGE }

    private static final class Entry {
        final ContainerKind container;
        final short slot;
        final ItemStack item;
        final String itemId;
        final int quantity;
        final String displayName;
        final String recipeName;
        final String pattern;
        final ResonantRecipeUtils.PatternStats stats;
        final String usages;

        Entry(ContainerKind container, short slot, ItemStack item, String itemId, int quantity,
              String displayName, String recipeName, String pattern,
              ResonantRecipeUtils.PatternStats stats, String usages) {
            this.container = container;
            this.slot = slot;
            this.item = item;
            this.itemId = itemId;
            this.quantity = quantity;
            this.displayName = displayName;
            this.recipeName = recipeName;
            this.pattern = pattern;
            this.stats = stats;
            this.usages = usages;
        }
    }

    private static final class Snapshot {
        final List<Entry> heldRecipes;
        final List<Entry> allShards;

        Snapshot(List<Entry> heldRecipes, List<Entry> allShards) {
            this.heldRecipes = heldRecipes;
            this.allShards = allShards;
        }
    }

    private static final class SelectionState {
        final String heldRecipeKey;
        final String statusText;
        final int progressValue;
        final boolean processing;

        SelectionState(String heldRecipeKey, String statusText, int progressValue, boolean processing) {
            this.heldRecipeKey = heldRecipeKey;
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

    // ═══════════════════════════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════════════════════════

    public static void initialize() {
        hyuiAvailable = HyUIReflectionUtils.detectHyUi(HYUI_PAGE_BUILDER, HYUI_PLUGIN, "RecipeCombineUI");
    }

    public static boolean isAvailable() {
        return hyuiAvailable;
    }

    public static void open(Player player) {
        if (player == null) return;
        if (!hyuiAvailable) {
            player.sendMessage(Message.raw("<color=#FF5555>HyUI not installed - recipe combine UI disabled."));
            return;
        }
        PlayerRef ref = player.getPlayerRef();
        closePageIfOpen(ref);
        pendingSelections.remove(ref);
        processingPlayers.remove(ref);
        player.getWorld().execute(() -> openWithSync(player));
    }

    // ═══════════════════════════════════════════════════════════════
    // Internal flow
    // ═══════════════════════════════════════════════════════════════

    private static void openWithSync(Player player) {
        Snapshot snapshot = collectSnapshot(player);
        SelectionState state = pendingSelections.remove(player.getPlayerRef());
        openPage(player, snapshot, state);
    }

    private static Snapshot collectSnapshot(Player player) {
        List<Entry> allShards = new ArrayList<>();
        collectRecipeShards(player.getInventory().getHotbar(), ContainerKind.HOTBAR, allShards);
        collectRecipeShards(player.getInventory().getStorage(), ContainerKind.STORAGE, allShards);
        // All recipe shards can be selected as the "held" recipe
        return new Snapshot(allShards, allShards);
    }

    private static void collectRecipeShards(ItemContainer container, ContainerKind kind, List<Entry> shards) {
        if (container == null) return;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (!ResonantRecipeUtils.isResonantRecipeItem(stack)) continue;

            String recipeName = ResonantRecipeUtils.getRecipeName(stack);
            if (recipeName == null || recipeName.isBlank()) continue;

            String pattern = ResonantRecipeUtils.getRecipePattern(stack);
            ResonantRecipeUtils.PatternStats stats = ResonantRecipeUtils.getPatternStats(pattern);
            String usages = ResonantRecipeUtils.getRecipeUsages(stack);
            String name = UIItemUtils.displayNameOrItemId(stack);

            shards.add(new Entry(kind, slot, stack, stack.getItemId(),
                    stack.getQuantity(), name, recipeName,
                    pattern == null ? "" : pattern, stats,
                    usages == null ? "" : usages));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Page building
    // ═══════════════════════════════════════════════════════════════

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

            // Held recipe dropdown change
            addListener.invoke(pageBuilder, "heldRecipeDropdown", valueChanged,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        String recipeVal = extractEventValue(eventObj);
                        pendingSelections.put(finalPlayer.getPlayerRef(),
                                new SelectionState(recipeVal, null, 0, false));
                        finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));
                    });

            // Combine button
            addListener.invoke(pageBuilder, "combineButton", activating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        PlayerRef ref = finalPlayer.getPlayerRef();
                        if (Boolean.TRUE.equals(processingPlayers.get(ref))) return;

                        String recipeVal = getContextValue(ctxObj, "heldRecipeDropdown",
                                "#heldRecipeDropdown.value");
                        Entry heldEntry = resolveSelection(finalSnapshot.heldRecipes, recipeVal);

                        processingPlayers.put(ref, true);
                        pendingSelections.put(ref,
                                new SelectionState(recipeVal, "Combining shards...", 0, true));
                        finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));

                        for (int elapsed = PROGRESS_TICK_MS; elapsed < PROCESS_DURATION_MS; elapsed += PROGRESS_TICK_MS) {
                            final int delay = elapsed;
                            final int timedProgress = Math.min(99,
                                    (int) Math.round(((delay + PROGRESS_TICK_MS) * 100.0) / PROCESS_DURATION_MS));
                            scheduler.schedule(() -> finalPlayer.getWorld().execute(() -> {
                                if (!Boolean.TRUE.equals(processingPlayers.get(ref))) return;
                                pendingSelections.put(ref,
                                        new SelectionState(recipeVal, "Combining shards...", timedProgress, true));
                                openWithSync(finalPlayer);
                            }), delay, TimeUnit.MILLISECONDS);
                        }

                        scheduler.schedule(() -> finalPlayer.getWorld().execute(() -> {
                            try {
                                ProcessResult result = processCombine(finalPlayer, heldEntry);
                                pendingSelections.put(ref,
                                        new SelectionState(recipeVal, result.status, result.progress, false));
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
            System.err.println("[SocketReforge] RecipeCombineUI open error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String buildHtml(Snapshot snapshot, SelectionState state) {
        String heldKey = state != null ? state.heldRecipeKey : null;
        boolean processing = state != null && state.processing;
        int progress = state != null ? Math.max(0, Math.min(100, state.progressValue)) : 0;
        String status = state != null && state.statusText != null ? state.statusText : "Idle";
        if (!processing) {
            progress = 0;
        }

        Entry selectedHeld = resolveSelection(snapshot.heldRecipes, heldKey);
        if (selectedHeld == null && !snapshot.heldRecipes.isEmpty()) {
            selectedHeld = snapshot.heldRecipes.get(0);
            heldKey = "0";
        }

        // Build matching shards list (same normalized name, excluding the held entry,
        // and only shards that can reveal at least one new slot)
        String normalizedName = selectedHeld != null
                ? ResonantRecipeUtils.normalizeRecipeName(selectedHeld.recipeName)
                : "";
        boolean[] heldMask = selectedHeld != null
                ? ResonantRecipeUtils.revealMaskFromPattern(selectedHeld.pattern)
                : new boolean[0];
        List<Entry> matchingShards = new ArrayList<>();
        if (selectedHeld != null) {
            for (Entry e : snapshot.allShards) {
                if (e == selectedHeld) continue;
                if (e.stats.isComplete()) continue;
                if (!ResonantRecipeUtils.normalizeRecipeName(e.recipeName).equals(normalizedName)) continue;
                boolean[] shardMask = ResonantRecipeUtils.revealMaskFromPattern(e.pattern);
                if (ResonantRecipeUtils.countNewReveals(heldMask, shardMask) <= 0) continue;
                matchingShards.add(e);
            }
        }

        // Build merge preview
        String mergePreviewText;
        boolean canCombine = false;
        if (selectedHeld == null) {
            mergePreviewText = "No recipe selected.";
            if (!processing) status = "No recipe shards found.";
        } else if (selectedHeld.stats.isComplete()) {
            mergePreviewText = "Recipe is already complete.";
            if (!processing) status = "Recipe already complete. Usages: " + formatUsages(selectedHeld);
        } else if (matchingShards.isEmpty()) {
            mergePreviewText = "No compatible shards with new slot data found.";
            if (!processing) status = "No compatible shards to combine.";
        } else {
            // Simulate merge
            String mergedPattern = selectedHeld.pattern;
            int consumeCount = 0;
            for (Entry shard : matchingShards) {
                String trial = ResonantRecipeUtils.mergePatterns(mergedPattern, shard.pattern);
                ResonantRecipeUtils.PatternStats trialStats = ResonantRecipeUtils.getPatternStats(trial);
                if (trialStats.revealedSlots() > ResonantRecipeUtils.getPatternStats(mergedPattern).revealedSlots()) {
                    mergedPattern = trial;
                    consumeCount++;
                }
                if (trialStats.isComplete()) break;
            }
            ResonantRecipeUtils.PatternStats beforeStats = selectedHeld.stats;
            ResonantRecipeUtils.PatternStats afterStats = ResonantRecipeUtils.getPatternStats(mergedPattern);
            int gained = Math.max(0, afterStats.revealedSlots() - beforeStats.revealedSlots());

            if (gained <= 0) {
                mergePreviewText = "No new slots can be revealed from available shards.";
                if (!processing) status = "Shards have no new data to contribute.";
            } else {
                canCombine = true;
                mergePreviewText = "Combining " + consumeCount + " shard(s) will reveal "
                        + gained + " new slot(s).\n"
                        + "Result: " + afterStats.revealedSlots() + "/" + afterStats.totalSlots()
                        + " slots revealed"
                        + (afterStats.isComplete() ? " (complete!)" : "") + "\n"
                        + "Merged pattern: " + formatPatternText(mergedPattern);
                if (!processing) status = "Ready to combine.";
            }
        }

        String html = loadTemplate();
        html = html.replace("{{heldRecipeOptions}}", buildRecipeOptions(snapshot.heldRecipes, heldKey));
        html = html.replace("{{recipeName}}", escapeHtml(selectedHeld != null ? selectedHeld.recipeName : "-"));
        html = html.replace("{{recipeType}}", escapeHtml(selectedHeld != null ? resolveRecipeType(selectedHeld) : "-"));
        html = html.replace("{{recipeProgress}}", escapeHtml(selectedHeld != null
                ? selectedHeld.stats.revealedSlots() + "/" + selectedHeld.stats.totalSlots() + " slots revealed"
                        + (selectedHeld.stats.isComplete() ? " (complete)" : "")
                : "-"));
        html = html.replace("{{recipeUsages}}", escapeHtml(selectedHeld != null ? formatUsages(selectedHeld) : "-"));
        html = html.replace("{{patternPreview}}", buildPatternPreviewHtml(selectedHeld));
        html = html.replace("{{shardListText}}", escapeHtml(buildShardListText(matchingShards)));
        html = html.replace("{{mergePreviewText}}", escapeHtml(mergePreviewText));
        html = html.replace("{{progressValue}}", String.valueOf(progress));
        html = html.replace("{{statusText}}", escapeHtml(status));
        html = html.replace("{{combineDisabledAttr}}",
                shouldDisable(processing, canCombine) ? "disabled=\"true\"" : "");
        return html;
    }

    // ═══════════════════════════════════════════════════════════════
    // Processing
    // ═══════════════════════════════════════════════════════════════

    private static ProcessResult processCombine(Player player, Entry heldEntry) {
        if (heldEntry == null) {
            return new ProcessResult("No recipe selected.", 0);
        }

        // Re-read current state of the held item
        ItemContainer container = heldEntry.container == ContainerKind.HOTBAR
                ? player.getInventory().getHotbar()
                : player.getInventory().getStorage();
        if (container == null) {
            return new ProcessResult("Inventory changed. Retry.", 0);
        }
        ItemStack current = container.getItemStack(heldEntry.slot);
        if (!ResonantRecipeUtils.isResonantRecipeItem(current)) {
            return new ProcessResult("Selected recipe changed. Retry.", 0);
        }

        String recipeName = ResonantRecipeUtils.getRecipeName(current);
        if (recipeName == null || recipeName.isBlank()) {
            return new ProcessResult("Recipe is missing resonance data.", 0);
        }

        ResonantRecipeUtils.PatternStats currentStats = ResonantRecipeUtils.getPatternStats(
                ResonantRecipeUtils.getRecipePattern(current));
        if (currentStats.isComplete()) {
            ItemStack ensured = ResonantRecipeUtils.ensureRecipeUsages(current);
            if (ensured != current) {
                container.setItemStackForSlot(heldEntry.slot, ensured);
            }
            String usages = ResonantRecipeUtils.getRecipeUsages(ensured);
            String usageText = usages != null && !usages.isBlank() ? " Usages: " + usages + "." : "";
            return new ProcessResult("Recipe is already complete." + usageText, 100);
        }

        String normalized = ResonantRecipeUtils.normalizeRecipeName(recipeName);

        // Collect matching shards from both containers
        List<ShardSlot> matches = new ArrayList<>();
        collectShardSlots(matches, player.getInventory().getHotbar(), ContainerKind.HOTBAR, normalized);
        collectShardSlots(matches, player.getInventory().getStorage(), ContainerKind.STORAGE, normalized);

        // Remove the held entry from candidates
        List<ShardSlot> candidates = new ArrayList<>();
        for (ShardSlot s : matches) {
            if (s.kind == heldEntry.container && s.slot == heldEntry.slot) continue;
            candidates.add(s);
        }

        if (candidates.isEmpty()) {
            return new ProcessResult("No other \"" + recipeName + "\" shards to combine.", 0);
        }

        // Select best merge candidates (greedy, same algorithm as ResonantRecipeCombineUse)
        String basePattern = ResonantRecipeUtils.getRecipePattern(current);
        if (basePattern == null) basePattern = "";
        List<ShardSlot> selected = ResonantRecipeUtils.selectBestMergeCandidates(basePattern, candidates);
        if (selected.isEmpty()) {
            return new ProcessResult("No shards reveal new slots to combine.", 0);
        }

        // Merge
        String mergedPattern = basePattern;
        for (ShardSlot s : selected) {
            mergedPattern = ResonantRecipeUtils.mergePatterns(mergedPattern, s.pattern);
        }

        ResonantRecipeUtils.PatternStats beforeStats = ResonantRecipeUtils.getPatternStats(basePattern);
        ResonantRecipeUtils.PatternStats afterStats = ResonantRecipeUtils.getPatternStats(mergedPattern);

        // Update the held item
        ItemStack updated = ResonantRecipeUtils.withRecipePattern(current, mergedPattern);
        updated = ResonantRecipeUtils.ensureRecipeUsages(updated);
        container.setItemStackForSlot(heldEntry.slot, updated);

        // Remove consumed shards
        int removed = 0;
        for (ShardSlot s : selected) {
            ItemContainer shardContainer = s.kind == ContainerKind.HOTBAR
                    ? player.getInventory().getHotbar()
                    : player.getInventory().getStorage();
            if (shardContainer != null) {
                shardContainer.removeItemStackFromSlot(s.slot, 1, false, false);
                removed++;
            }
        }

        DynamicTooltipUtils.refreshAllPlayers();

        int gained = Math.max(0, afterStats.revealedSlots() - beforeStats.revealedSlots());
        String gainLabel = gained > 0 ? " (+" + gained + " new)" : "";
        String progress = afterStats.totalSlots() > 0
                ? afterStats.revealedSlots() + "/" + afterStats.totalSlots() + " slots revealed"
                : "no pattern data";
        if (afterStats.isComplete()) {
            progress = progress + " (complete)";
        }
        return new ProcessResult("Combined " + removed + " shard(s). " + progress + gainLabel + ".", 100);
    }

    // ═══════════════════════════════════════════════════════════════
    // Shard merge selection (mirrors ResonantRecipeCombineUse logic)
    // ═══════════════════════════════════════════════════════════════

    private static final class ShardSlot implements ResonantRecipeUtils.MergeCandidate {
        final ContainerKind kind;
        final short slot;
        final String pattern;
        final boolean[] revealMask;

        ShardSlot(ContainerKind kind, short slot, String pattern) {
            this.kind = kind;
            this.slot = slot;
            this.pattern = pattern == null ? "" : pattern;
            this.revealMask = ResonantRecipeUtils.revealMaskFromPattern(this.pattern);
        }

        @Override
        public String getPattern() {
            return pattern;
        }

        @Override
        public boolean[] getRevealMask() {
            return revealMask;
        }
    }

    private static void collectShardSlots(List<ShardSlot> matches, ItemContainer container,
                                           ContainerKind kind, String normalizedRecipeName) {
        if (container == null) return;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (!ResonantRecipeUtils.isResonantRecipeItem(stack)) continue;
            String name = ResonantRecipeUtils.getRecipeName(stack);
            if (name == null || name.isBlank()) continue;
            if (!ResonantRecipeUtils.normalizeRecipeName(name).equals(normalizedRecipeName)) continue;
            String pattern = ResonantRecipeUtils.getRecipePattern(stack);
            ResonantRecipeUtils.PatternStats stats = ResonantRecipeUtils.getPatternStats(pattern);
            if (stats.isComplete()) continue;
            matches.add(new ShardSlot(kind, slot, pattern == null ? "" : pattern));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // HTML builders
    // ═══════════════════════════════════════════════════════════════

    private static String buildRecipeOptions(List<Entry> entries, String selectedKey) {
        if (entries.isEmpty()) {
            return "<option value=\"\" selected=\"true\">No recipe shards found</option>";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            String key = String.valueOf(i);
            sb.append("<option value=\"").append(key).append("\"");
            if (key.equals(selectedKey)) sb.append(" selected=\"true\"");
            sb.append(">");
            sb.append(escapeHtml(entry.recipeName));
            sb.append(" [").append(entry.stats.revealedSlots()).append("/")
                    .append(entry.stats.totalSlots()).append("]");
            if (entry.stats.isComplete()) sb.append(" ✓");
            sb.append("</option>");
        }
        return sb.toString();
    }

    private static String buildPatternPreviewHtml(Entry held) {
        if (held == null || held.pattern.isBlank()) {
            StringBuilder empty = new StringBuilder();
            empty.append("<div style=\"layout-mode: Left; spacing: 10;\">");
            empty.append("<div style=\"flex-weight:1;\"></div>");
            empty.append("<p>No pattern data.</p>");
            empty.append("<div style=\"flex-weight:1;\"></div>");
            empty.append("</div>");
            return empty.toString();
        }

        String pattern = held.pattern;
        // Parse pattern tokens like [Fire][x][Ice]
        List<String> tokens = parsePatternTokens(pattern);
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

    private static String buildShardListText(List<Entry> shards) {
        if (shards.isEmpty()) {
            return "No matching shards in inventory.";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < shards.size(); i++) {
            Entry shard = shards.get(i);
            sb.append("Shard ").append(i + 1).append(": ");
            sb.append(formatPatternText(shard.pattern));
            sb.append(" (").append(shard.stats.revealedSlots()).append("/")
                    .append(shard.stats.totalSlots()).append(" revealed)");
            if (i < shards.size() - 1) sb.append("\n");
        }
        return sb.toString();
    }

    private static String formatPatternText(String pattern) {
        if (pattern == null || pattern.isBlank()) return "[ ]";
        List<String> tokens = parsePatternTokens(pattern);
        if (tokens.isEmpty()) return "[ ]";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            sb.append("[");
            sb.append(isUnknownToken(token) ? "?" : capitalize(token));
            sb.append("]");
            if (i < tokens.size() - 1) sb.append(" ");
        }
        return sb.toString();
    }

    private static String formatUsages(Entry entry) {
        if (entry == null) return "-";
        if (!entry.stats.isComplete()) return "Complete recipe to unlock";
        String usages = entry.usages;
        if (usages == null || usages.isBlank()) return ResonantRecipeUtils.DEFAULT_RECIPE_USAGES;
        return usages;
    }

    private static String resolveRecipeType(Entry entry) {
        if (entry == null || entry.item == null) return "-";
        String type = ResonantRecipeUtils.getRecipeType(entry.item);
        if (type != null && !type.isBlank()) return type;
        // Try to infer from resonance system
        Essence.Type[] essencePattern = ResonanceSystem.getPatternForRecipeName(entry.recipeName);
        if (essencePattern != null && essencePattern.length > 0) {
            return "Resonance (" + essencePattern.length + " sockets)";
        }
        return "Unknown";
    }

    private static boolean shouldDisable(boolean processing, boolean canCombine) {
        if (processing) return true;
        return !canCombine;
    }

    // ═══════════════════════════════════════════════════════════════
    // Pattern parsing helpers
    // ═══════════════════════════════════════════════════════════════

    private static List<String> parsePatternTokens(String pattern) {
        List<String> tokens = new ArrayList<>();
        if (pattern == null || pattern.isBlank()) return tokens;

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

        if (!tokens.isEmpty()) return tokens;

        // Fallback: split on commas for legacy format
        String[] parts = pattern.split(",");
        for (String part : parts) {
            String token = part == null ? "" : part.trim();
            if (!token.isEmpty()) tokens.add(token);
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

    // ═══════════════════════════════════════════════════════════════
    // Shared helpers
    // ═══════════════════════════════════════════════════════════════

    private static Entry resolveSelection(List<Entry> entries, String value) {
        return HyUIReflectionUtils.resolveIndexSelection(entries, value);
    }

    private static String loadTemplate() {
        return UITemplateUtils.loadTemplate(
                RecipeCombineUI.class,
                TEMPLATE_PATH,
                "<div><p>Recipe combine UI template missing.</p></div>",
                "RecipeCombineUI");
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
