package irai.mod.reforge.UI;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
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
import irai.mod.reforge.Common.ResonantCompendiumUtils;
import irai.mod.reforge.Common.ResonantCompendiumUtils.CompendiumEntry;
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
    private static final boolean DEBUG = Boolean.parseBoolean(
            System.getProperty("socketreforge.debug.combine", "false"));

    private static boolean hyuiAvailable = false;

    private static final Map<PlayerRef, Object> openPages = new ConcurrentHashMap<>();
    private static final Map<PlayerRef, SelectionState> pendingSelections = new ConcurrentHashMap<>();
    private static final Map<PlayerRef, Boolean> processingPlayers = new ConcurrentHashMap<>();
    private static final Map<PlayerRef, CompendiumHandle> activeCompendiums = new ConcurrentHashMap<>();
    private static final Map<PlayerRef, Integer> sessionTokens = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final int PROCESS_DURATION_MS = 800;
    private static final int PROGRESS_TICK_MS = 50;
    private static final int PICKER_PAGE_SIZE = 8;

    private enum Source { HOTBAR, STORAGE, COMPENDIUM }
    private enum PickerMode { NONE, BASE, MERGE }

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
        final Source source;
        final short slot;
        final ItemStack item;
        final String itemId;
        final String compendiumKey;
        final int quantity;
        final String displayName;
        final String recipeName;
        final String pattern;
        final ResonantRecipeUtils.PatternStats stats;
        final String usages;

        Entry(Source source, short slot, ItemStack item, String itemId, String compendiumKey, int quantity,
              String displayName, String recipeName, String pattern,
              ResonantRecipeUtils.PatternStats stats, String usages) {
            this.source = source;
            this.slot = slot;
            this.item = item;
            this.itemId = itemId;
            this.compendiumKey = compendiumKey;
            this.quantity = quantity;
            this.displayName = displayName;
            this.recipeName = recipeName;
            this.pattern = pattern;
            this.stats = stats;
            this.usages = usages;
        }
    }

    private static final class Snapshot {
        final List<Entry> entries;

        Snapshot(List<Entry> entries) {
            this.entries = entries;
        }
    }

    private static final class ResolvedSelections {
        final Entry baseEntry;
        final String baseKey;
        final List<Entry> mergeCandidates;
        final Entry mergeEntry;
        final String mergeKey;

        ResolvedSelections(Entry baseEntry, String baseKey, List<Entry> mergeCandidates,
                           Entry mergeEntry, String mergeKey) {
            this.baseEntry = baseEntry;
            this.baseKey = baseKey;
            this.mergeCandidates = mergeCandidates;
            this.mergeEntry = mergeEntry;
            this.mergeKey = mergeKey;
        }
    }

    private static final class SelectionState {
        final String baseKey;
        final String mergeKey;
        final String statusText;
        final int progressValue;
        final boolean processing;
        final PickerMode picker;
        final int pickerPage;

        SelectionState(String baseKey, String mergeKey, String statusText, int progressValue, boolean processing,
                       PickerMode picker, int pickerPage) {
            this.baseKey = baseKey;
            this.mergeKey = mergeKey;
            this.statusText = statusText;
            this.progressValue = progressValue;
            this.processing = processing;
            this.picker = picker == null ? PickerMode.NONE : picker;
            this.pickerPage = Math.max(0, pickerPage);
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
        bumpSession(ref);
        closePageIfOpen(ref);
        pendingSelections.remove(ref);
        processingPlayers.remove(ref);
        activeCompendiums.remove(ref);
        player.getWorld().execute(() -> openWithSync(player));
    }

    public static void openFromCompendium(Player player, short compendiumSlot) {
        if (player == null) return;
        if (!hyuiAvailable) {
            player.sendMessage(Message.raw("<color=#FF5555>HyUI not installed - recipe combine UI disabled."));
            return;
        }
        PlayerRef ref = player.getPlayerRef();
        bumpSession(ref);
        closePageIfOpen(ref);
        pendingSelections.remove(ref);
        processingPlayers.remove(ref);
        activeCompendiums.put(ref, new CompendiumHandle(compendiumSlot));
        scheduler.schedule(() -> {
            if (player.getWorld() == null) {
                return;
            }
            player.getWorld().execute(() -> openWithSync(player));
        }, 50, TimeUnit.MILLISECONDS);
    }

    // ═══════════════════════════════════════════════════════════════
    // Internal flow
    // ═══════════════════════════════════════════════════════════════

    private static void openWithSync(Player player) {
        Snapshot snapshot = collectSnapshot(player);
        SelectionState state = pendingSelections.remove(player.getPlayerRef());
        if (DEBUG) {
            String baseKey = state != null ? state.baseKey : "";
            String mergeKey = state != null ? state.mergeKey : "";
            debug("openWithSync entries=" + snapshot.entries.size() + " baseKey=" + baseKey + " mergeKey=" + mergeKey);
        }
        openPage(player, snapshot, state);
    }

    private static Snapshot collectSnapshot(Player player) {
        List<Entry> entries = new ArrayList<>();
        collectInventoryShards(player.getInventory().getHotbar(), Source.HOTBAR, entries);
        collectInventoryShards(player.getInventory().getStorage(), Source.STORAGE, entries);

        CompendiumContext compendiumContext = resolveCompendium(player);
        if (compendiumContext != null) {
            collectCompendiumEntries(compendiumContext, entries);
        }

        sortEntries(entries);
        return new Snapshot(entries);
    }

    private static void collectInventoryShards(ItemContainer container, Source kind, List<Entry> shards) {
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

            shards.add(new Entry(kind, slot, stack, stack.getItemId(), null,
                    stack.getQuantity(), name, recipeName,
                    pattern == null ? "" : pattern, stats,
                    usages == null ? "" : usages));
        }
    }

    private static void collectCompendiumEntries(CompendiumContext context, List<Entry> entries) {
        if (context == null || context.compendium == null) {
            return;
        }
        Map<String, CompendiumEntry> data = ResonantCompendiumUtils.getCompendiumData(context.compendium);
        if (data.isEmpty()) {
            return;
        }
        for (Map.Entry<String, CompendiumEntry> entry : data.entrySet()) {
            String key = entry.getKey();
            CompendiumEntry value = entry.getValue();
            if (value == null) {
                continue;
            }
            String recipeName = value.name == null ? "" : value.name;
            String pattern = value.pattern == null ? "" : value.pattern;
            ResonantRecipeUtils.PatternStats stats = ResonantRecipeUtils.getPatternStats(pattern);
            String usages = value.usages == null ? "" : value.usages;
            int quantity = Math.max(1, value.quantity);
            entries.add(new Entry(Source.COMPENDIUM, (short) -1, null, null, key, quantity,
                    recipeName, recipeName, pattern, stats, usages));
        }
    }

    private static void sortEntries(List<Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        Comparator<Entry> comparator = Comparator
                .comparing((Entry e) -> ResonantRecipeUtils.normalizeRecipeName(e.recipeName))
                .thenComparingInt(e -> sourceOrder(e.source))
                .thenComparingInt(e -> e.source == Source.COMPENDIUM ? 0 : e.slot)
                .thenComparing(e -> e.compendiumKey == null ? "" : e.compendiumKey);
        entries.sort(comparator);
    }

    private static int sourceOrder(Source source) {
        if (source == Source.HOTBAR) return 0;
        if (source == Source.STORAGE) return 1;
        return 2;
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

            ResolvedSelections resolved = resolveSelections(snapshot, state);
            PickerMode picker = state != null ? state.picker : PickerMode.NONE;
            int pickerPage = state != null ? state.pickerPage : 0;
            int maxPickerPage = computePickerMaxPage(
                    picker == PickerMode.BASE ? snapshot.entries : resolved.mergeCandidates);
            int effectivePickerPage = Math.min(pickerPage, maxPickerPage);

            // Base recipe dropdown change
            addListener.invoke(pageBuilder, "baseRecipeDropdown", valueChanged,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        try {
                            String baseVal = extractEventValue(eventObj);
                            if (baseVal == null || baseVal.isBlank()) {
                                baseVal = getContextValue(ctxObj, "baseRecipeDropdown", "#baseRecipeDropdown.value");
                            }
                            if (DEBUG) {
                                debug("base change value=" + baseVal);
                            }
                            pendingSelections.put(finalPlayer.getPlayerRef(),
                                    new SelectionState(baseVal, null, null, 0, false, PickerMode.NONE, 0));
                            finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));
                        } catch (Exception e) {
                            System.err.println("[SocketReforge] RecipeCombineUI base change failed: " + e.getMessage());
                            e.printStackTrace();
                        }
                    });

            // Merge recipe dropdown change
            addListener.invoke(pageBuilder, "mergeRecipeDropdown", valueChanged,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        try {
                            String mergeVal = extractEventValue(eventObj);
                            if (mergeVal == null || mergeVal.isBlank()) {
                                mergeVal = getContextValue(ctxObj, "mergeRecipeDropdown", "#mergeRecipeDropdown.value");
                            }
                            String baseVal = getContextValue(ctxObj, "baseRecipeDropdown", "#baseRecipeDropdown.value");
                            if (DEBUG) {
                                debug("merge change value=" + mergeVal + " base=" + baseVal);
                            }
                            pendingSelections.put(finalPlayer.getPlayerRef(),
                                    new SelectionState(baseVal, mergeVal, null, 0, false, PickerMode.NONE, 0));
                            finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));
                        } catch (Exception e) {
                            System.err.println("[SocketReforge] RecipeCombineUI merge change failed: " + e.getMessage());
                            e.printStackTrace();
                        }
                    });

            addListener.invoke(pageBuilder, "openBasePicker", activating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        pendingSelections.put(finalPlayer.getPlayerRef(),
                                new SelectionState(resolved.baseKey, resolved.mergeKey, null, 0, false,
                                        PickerMode.BASE, 0));
                        finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));
                    });

            addListener.invoke(pageBuilder, "openMergePicker", activating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        pendingSelections.put(finalPlayer.getPlayerRef(),
                                new SelectionState(resolved.baseKey, resolved.mergeKey, null, 0, false,
                                        PickerMode.MERGE, 0));
                        finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));
                    });

            if (picker != PickerMode.NONE) {
                addListener.invoke(pageBuilder, "pickerCloseButton", activating,
                        (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                            pendingSelections.put(finalPlayer.getPlayerRef(),
                                    new SelectionState(resolved.baseKey, resolved.mergeKey, null, 0, false,
                                            PickerMode.NONE, 0));
                            finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));
                        });

                int maxPage = maxPickerPage;
                if (effectivePickerPage > 0) {
                    addListener.invoke(pageBuilder, "pickerPrevButton", activating,
                            (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                                pendingSelections.put(finalPlayer.getPlayerRef(),
                                        new SelectionState(resolved.baseKey, resolved.mergeKey, null, 0, false,
                                                picker, Math.max(0, effectivePickerPage - 1)));
                                finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));
                            });
                }
                if (effectivePickerPage < maxPage) {
                    addListener.invoke(pageBuilder, "pickerNextButton", activating,
                            (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                                pendingSelections.put(finalPlayer.getPlayerRef(),
                                        new SelectionState(resolved.baseKey, resolved.mergeKey, null, 0, false,
                                                picker, Math.min(maxPage, effectivePickerPage + 1)));
                                finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));
                            });
                }

                List<Integer> pickerIndices = computePickerIndices(
                        picker == PickerMode.BASE ? snapshot.entries : resolved.mergeCandidates,
                        effectivePickerPage);
                for (Integer index : pickerIndices) {
                    final String selectKey = String.valueOf(index);
                    addListener.invoke(pageBuilder, "pickerEntry_" + index, activating,
                            (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                                SelectionState next;
                                if (picker == PickerMode.BASE) {
                                    next = new SelectionState(selectKey, null, null, 0, false, PickerMode.NONE, 0);
                                } else {
                                    next = new SelectionState(resolved.baseKey, selectKey, null, 0, false, PickerMode.NONE, 0);
                                }
                                pendingSelections.put(finalPlayer.getPlayerRef(), next);
                                finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));
                            });
                }
            }

            // Combine button
            addListener.invoke(pageBuilder, "combineButton", activating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        PlayerRef ref = finalPlayer.getPlayerRef();
                        if (Boolean.TRUE.equals(processingPlayers.get(ref))) return;
                        int sessionToken = currentSession(ref);

                        String baseVal = resolved.baseKey;
                        String mergeVal = resolved.mergeKey;
                        Entry baseEntry = resolved.baseEntry;
                        List<Entry> mergeCandidates = resolved.mergeCandidates;
                        Entry mergeEntry = resolved.mergeEntry;

                        processingPlayers.put(ref, true);
                        pendingSelections.put(ref,
                                new SelectionState(baseVal, mergeVal, "Combining shards...", 0, true,
                                        PickerMode.NONE, 0));
                        finalPlayer.getWorld().execute(() -> openWithSync(finalPlayer));

                        for (int elapsed = PROGRESS_TICK_MS; elapsed < PROCESS_DURATION_MS; elapsed += PROGRESS_TICK_MS) {
                            final int delay = elapsed;
                            final int timedProgress = Math.min(99,
                                    (int) Math.round(((delay + PROGRESS_TICK_MS) * 100.0) / PROCESS_DURATION_MS));
                            scheduler.schedule(() -> finalPlayer.getWorld().execute(() -> {
                                if (!isSessionActive(ref, sessionToken)) return;
                                if (!Boolean.TRUE.equals(processingPlayers.get(ref))) return;
                                pendingSelections.put(ref,
                                        new SelectionState(baseVal, mergeVal, "Combining shards...", timedProgress, true,
                                                PickerMode.NONE, 0));
                                openWithSync(finalPlayer);
                            }), delay, TimeUnit.MILLISECONDS);
                        }

                        scheduler.schedule(() -> finalPlayer.getWorld().execute(() -> {
                            if (!isSessionActive(ref, sessionToken)) return;
                            try {
                                ProcessResult result = processCombine(finalPlayer, baseEntry, mergeEntry);
                                pendingSelections.put(ref,
                                        new SelectionState(baseVal, mergeVal, result.status, result.progress, false,
                                                PickerMode.NONE, 0));
                            } finally {
                                processingPlayers.remove(ref);
                                openWithSync(finalPlayer);
                            }
                        }), PROCESS_DURATION_MS, TimeUnit.MILLISECONDS);
                    });

            Method onDismiss = pageBuilderClass.getMethod("onDismiss", java.util.function.BiConsumer.class);
            pageBuilder = onDismiss.invoke(pageBuilder,
                    (java.util.function.BiConsumer<Object, Object>) (pageObj, dismissedByPlayer) -> {
                        if (Boolean.TRUE.equals(dismissedByPlayer)) {
                            cleanupPlayerState(finalPlayer);
                        }
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
        String baseKey = state != null ? state.baseKey : null;
        String mergeKey = state != null ? state.mergeKey : null;
        boolean processing = state != null && state.processing;
        int progress = state != null ? Math.max(0, Math.min(100, state.progressValue)) : 0;
        String status = state != null && state.statusText != null ? state.statusText : "Idle";
        PickerMode picker = state != null ? state.picker : PickerMode.NONE;
        int pickerPage = state != null ? state.pickerPage : 0;
        if (!processing) {
            progress = 0;
        }

        ResolvedSelections resolved = resolveSelections(snapshot, state);
        Entry selectedBase = resolved.baseEntry;
        Entry selectedMerge = resolved.mergeEntry;
        baseKey = resolved.baseKey;
        mergeKey = resolved.mergeKey;

        String mergePreviewText;
        String mergePreviewVisual = "";
        boolean canCombine = false;
        if (selectedBase == null) {
            mergePreviewText = "No recipe selected.";
            if (!processing) status = "No recipe shards found.";
        } else if (selectedBase.stats.isComplete()) {
            mergePreviewText = "Recipe is already complete.";
            if (!processing) status = "Recipe already complete. Usages: " + formatUsages(selectedBase);
        } else if (selectedMerge == null) {
            mergePreviewText = "Select a matching shard to merge.";
            if (!processing) status = resolved.mergeCandidates.isEmpty()
                    ? "No compatible shards to combine."
                    : "Select a shard to combine.";
            mergePreviewVisual = buildPatternPreviewStrip(selectedBase.pattern, 90, 68);
        } else {
            String mergedPattern = ResonantRecipeUtils.mergePatterns(selectedBase.pattern, selectedMerge.pattern);
            ResonantRecipeUtils.PatternStats beforeStats = selectedBase.stats;
            ResonantRecipeUtils.PatternStats afterStats = ResonantRecipeUtils.getPatternStats(mergedPattern);
            int gained = Math.max(0, afterStats.revealedSlots() - beforeStats.revealedSlots());
            mergePreviewVisual = buildPatternPreviewStrip(mergedPattern, 90, 68);

            if (gained <= 0) {
                mergePreviewText = "Selected shard adds no new slot data.";
                if (!processing) status = "Shard has no new data to contribute.";
            } else {
                canCombine = true;
                mergePreviewText = "Merging will reveal " + gained + " new slot(s).\n"
                        + "Result: " + afterStats.revealedSlots() + "/" + afterStats.totalSlots()
                        + " slots revealed"
                        + (afterStats.isComplete() ? " (complete!)" : "");
                if (!processing) status = "Ready to combine.";
            }
        }

        String basePickerSummary = buildPickerSummaryHtml(selectedBase, "No recipe shards found.");
        String mergePickerSummary;
        if (selectedBase == null) {
            mergePickerSummary = buildPickerSummaryHtml(null, "Select a base recipe first.");
        } else if (selectedMerge == null) {
            mergePickerSummary = buildPickerSummaryHtml(null,
                    resolved.mergeCandidates.isEmpty() ? "No matching shards." : "Select a shard to merge.");
        } else {
            mergePickerSummary = buildPickerSummaryHtml(selectedMerge, "Select a shard to merge.");
        }

        String pickerModal = buildPickerModalHtml(resolved, picker, pickerPage, snapshot.entries);

        String html = loadTemplate();
        html = html.replace("<p style=\"font-weight:bold;\">Matching Shards</p>", "");
        html = html.replace("id=\"shardListPanel\" style=\"", "id=\"shardListPanel\" style=\"display:none;");
        html = html.replace("{{shardListHtml}}", "");
        html = html.replace("{{baseRecipeOptions}}",
                buildRecipeOptions(snapshot.entries, baseKey, "No recipe shards found"));
        html = html.replace("{{mergeRecipeOptions}}",
                buildRecipeOptions(resolved.mergeCandidates, mergeKey, "No matching shards"));
        html = html.replace("{{basePickerSummary}}", basePickerSummary);
        html = html.replace("{{mergePickerSummary}}", mergePickerSummary);
        html = html.replace("{{recipeName}}", escapeHtml(selectedBase != null ? selectedBase.recipeName : "-"));
        html = html.replace("{{recipeType}}", escapeHtml(selectedBase != null ? resolveRecipeType(selectedBase) : "-"));
        html = html.replace("{{recipeProgress}}", escapeHtml(selectedBase != null
                ? selectedBase.stats.revealedSlots() + "/" + selectedBase.stats.totalSlots() + " slots revealed"
                        + (selectedBase.stats.isComplete() ? " (complete)" : "")
                : "-"));
        html = html.replace("{{recipeUsages}}", escapeHtml(selectedBase != null ? formatUsages(selectedBase) : "-"));
        html = html.replace("{{mergePreviewText}}", escapeHtml(mergePreviewText));
        html = html.replace("{{mergePreviewVisual}}", mergePreviewVisual);
        html = html.replace("{{progressValue}}", String.valueOf(progress));
        html = html.replace("{{statusText}}", escapeHtml(status));
        html = html.replace("{{combineDisabledAttr}}",
                shouldDisable(processing, canCombine) ? "disabled=\"true\"" : "");
        html = html.replace("{{pickerModal}}", pickerModal);
        return html;
    }

    private static ResolvedSelections resolveSelections(Snapshot snapshot, SelectionState state) {
        String baseKey = state != null ? state.baseKey : null;
        String mergeKey = state != null ? state.mergeKey : null;

        Entry selectedBase = resolveSelection(snapshot.entries, baseKey);
        if (selectedBase == null && !snapshot.entries.isEmpty()) {
            selectedBase = snapshot.entries.get(0);
            baseKey = "0";
            mergeKey = null;
        } else if (selectedBase != null) {
            baseKey = indexOfEntry(snapshot.entries, selectedBase);
        }

        List<Entry> mergeCandidates = buildMergeCandidates(snapshot.entries, selectedBase);
        Entry selectedMerge = resolveSelection(mergeCandidates, mergeKey);
        if (selectedMerge == null && !mergeCandidates.isEmpty()) {
            selectedMerge = mergeCandidates.get(0);
            mergeKey = "0";
        } else if (selectedMerge != null) {
            mergeKey = indexOfEntry(mergeCandidates, selectedMerge);
        }

        return new ResolvedSelections(selectedBase, baseKey, mergeCandidates, selectedMerge, mergeKey);
    }

    private static String buildPickerSummaryHtml(Entry entry, String emptyLabel) {
        if (entry == null) {
            String label = emptyLabel == null ? "Select a recipe" : emptyLabel;
            StringBuilder empty = new StringBuilder();
            empty.append("<div style=\"anchor-height:72; anchor-width:440; layout-mode:Top; padding:6; background-image:url(output_bg.png); background-size:100% 100%; background-repeat:no-repeat; background-position:center;\">");
            empty.append("<p style=\"font-size:11; color:#b0b0c2;\">").append(escapeHtml(label)).append("</p>");
            empty.append("</div>");
            return empty.toString();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"anchor-height:72; anchor-width:440; layout-mode:Top; padding:6; background-image:url(output_bg.png); background-size:100% 100%; background-repeat:no-repeat; background-position:center;\">");
        sb.append("<div style=\"layout-mode:Top; anchor-width:100%;\">");
        sb.append("<p style=\"font-weight:bold;\">").append(escapeHtml(entry.recipeName)).append("</p>");
        sb.append(buildPatternPreviewStrip(entry.pattern, 40, 40));
        sb.append("<p style=\"font-size:11; color:#b0b0c2;\">");
        sb.append(entry.stats.revealedSlots()).append("/").append(entry.stats.totalSlots())
                .append(" revealed");
        if (entry.quantity > 1) {
            sb.append(" | x").append(entry.quantity);
        }
        if (entry.source == Source.COMPENDIUM) {
            sb.append(" | Compendium");
        }
        sb.append("</p>");
        sb.append("</div>");
        
        sb.append("</div>");
        return sb.toString();
    }

    private static String buildPickerModalHtml(ResolvedSelections resolved, PickerMode picker,
                                               int pickerPage, List<Entry> baseEntries) {
        if (picker == null || picker == PickerMode.NONE) {
            return "";
        }
        List<Entry> entries = picker == PickerMode.BASE ? baseEntries : resolved.mergeCandidates;
        String selectedKey = picker == PickerMode.BASE ? resolved.baseKey : resolved.mergeKey;
        String title = picker == PickerMode.BASE ? "Select Base Recipe" : "Select Merge Shard";
        String emptyLabel = picker == PickerMode.BASE ? "No recipe shards found."
                : "No matching shards.";

        int maxPage = computePickerMaxPage(entries);
        int page = Math.max(0, Math.min(maxPage, pickerPage));
        String pageLabel = (maxPage <= 0)
                ? "Page 1/1"
                : "Page " + (page + 1) + "/" + (maxPage + 1);

        String prevDisabled = page <= 0 ? "disabled=\"true\"" : "";
        String nextDisabled = page >= maxPage ? "disabled=\"true\"" : "";

        String cards = buildPickerCardList(entries, selectedKey, emptyLabel, page);

        StringBuilder sb = new StringBuilder();
        sb.append("<div id=\"pickerOverlay\" style=\"anchor-full:200; layout-mode:Left; background-color:#0b0b1200;\">");
        //sb.append("<div style=\"flex-weight:1;\"></div>");
        sb.append("<div style=\"layout-mode:Left;\">");
        //sb.append("<div style=\"flex-weight:1;\"></div>");
        sb.append("<div style=\"anchor-width:350; anchor-height:700; layout-mode:Top; background-color:#1a1a2b; padding:10; border-radius:6;\">");
        sb.append("<div style=\"layout-mode:Left; spacing:10;\">");
        sb.append("<p style=\"font-weight:bold;\">").append(escapeHtml(title)).append("</p>");
        sb.append("<div style=\"flex-weight:1;\"></div>");
        sb.append("<button id=\"pickerCloseButton\" class=\"secondary-button\" style=\"anchor-width:120; anchor-height:32;\">Close</button>");
        sb.append("</div>");
        sb.append("<img src=\"divider.png\" style=\"anchor-width: 350; anchor-height: 3;\">");
        sb.append("<reorderable-list id=\"pickerList\" style=\"layout-mode:Top; spacing:6; anchor-width:350; anchor-height:600; background-color:#141426; padding:6; border-radius:4;\">");
        sb.append(cards);
        sb.append("</reorderable-list>");
        sb.append("<div style=\"layout-mode:Center; spacing:8; anchor-width:700;\">");
        sb.append("<button id=\"pickerPrevButton\" class=\"secondary-button\" style=\"anchor-width:120; anchor-height:32;\" ").append(prevDisabled).append(">Prev</button>");
        //sb.append("<div style=\"flex-weight:1;\"></div>");
        sb.append("<p style=\"font-size:11; color:#b0b0c2;\">").append(escapeHtml(pageLabel)).append("</p>");
        //sb.append("<div style=\"flex-weight:1;\"></div>");
        sb.append("<button id=\"pickerNextButton\" class=\"secondary-button\" style=\"anchor-width:120; anchor-height:32;\" ").append(nextDisabled).append(">Next</button>");
        sb.append("</div>");
        sb.append("</div>");
        //sb.append("<div style=\"flex-weight:1;\"></div>");
        sb.append("</div>");
        //sb.append("<div style=\"flex-weight:1;\"></div>");
        sb.append("</div>");
        return sb.toString();
    }

    private static String buildPickerCardList(List<Entry> entries, String selectedKey, String emptyLabel, int page) {
        if (entries == null || entries.isEmpty()) {
            String label = emptyLabel == null ? "No entries" : emptyLabel;
            return "<p style=\"font-size:11; color:#b0b0c2;\">" + escapeHtml(label) + "</p>";
        }
        int start = page * PICKER_PAGE_SIZE;
        int end = Math.min(entries.size(), start + PICKER_PAGE_SIZE);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            Entry entry = entries.get(i);
            String key = String.valueOf(i);
            boolean selected = key.equals(selectedKey) || (selectedKey == null && i == 0);
            String bg = selected ? "#343a5a00" : "#20203600";
            String border = selected ? "#7fa5ff" : "#2c2c4b";
            sb.append("<img src=\"divider.png\" style=\"anchor-width: 350; anchor-height: 3;\">");
            sb.append("<button id=\"pickerEntry_").append(i)
                    .append("\" class=\"custom-button\" style=\"anchor-width:350; anchor-height:70; text-align:left; padding:0; background-color:")
                    .append(bg).append("; border:1px solid ").append(border)
                    .append("; border-radius:1; layout-mode:Top;\">");
            sb.append("<div style=\"anchor-width:350; anchor-height:70; layout-mode:Top; padding:20; background-image:url(output_bg.png); background-size:100% 100%; background-repeat:no-repeat; background-position:center;\">");
            sb.append("<p style=\"font-weight:bold;\">").append(escapeHtml(entry.recipeName)).append("</p>");
            sb.append(buildPatternPreviewStrip(entry.pattern, 32, 32));
            sb.append("<p style=\"font-size:11; color:#b0b0c2;\">");
            sb.append(entry.stats.revealedSlots()).append("/").append(entry.stats.totalSlots())
                    .append(" revealed");
            if (entry.quantity > 1) {
                sb.append(" | x").append(entry.quantity);
            }
            if (entry.source == Source.COMPENDIUM) {
                sb.append(" | Compendium");
            }
            sb.append("</div>");
            sb.append("</button>");
        }
        return sb.toString();
    }

    private static int computePickerMaxPage(List<Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            return 0;
        }
        return Math.max(0, (entries.size() - 1) / PICKER_PAGE_SIZE);
    }

    private static List<Integer> computePickerIndices(List<Entry> entries, int page) {
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

    private static List<Entry> buildMergeCandidates(List<Entry> entries, Entry baseEntry) {
        if (entries == null || entries.isEmpty() || baseEntry == null) {
            return List.of();
        }
        if (baseEntry.stats.isComplete()) {
            return List.of();
        }
        String normalizedName = ResonantRecipeUtils.normalizeRecipeName(baseEntry.recipeName);
        boolean[] baseMask = ResonantRecipeUtils.revealMaskFromPattern(baseEntry.pattern);

        List<Entry> matches = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry == null) {
                continue;
            }
            if (isSameEntry(baseEntry, entry)) {
                continue;
            }
            if (entry.stats.isComplete()) {
                continue;
            }
            if (!ResonantRecipeUtils.normalizeRecipeName(entry.recipeName).equals(normalizedName)) {
                continue;
            }
            boolean[] shardMask = ResonantRecipeUtils.revealMaskFromPattern(entry.pattern);
            if (ResonantRecipeUtils.countNewReveals(baseMask, shardMask) <= 0) {
                continue;
            }
            matches.add(entry);
        }
        return matches;
    }

    private static boolean isSameEntry(Entry a, Entry b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.source != b.source) {
            return false;
        }
        if (a.source == Source.COMPENDIUM) {
            if (a.compendiumKey == null || b.compendiumKey == null) {
                return false;
            }
            return a.compendiumKey.equals(b.compendiumKey);
        }
        return a.slot == b.slot;
    }

    // ═══════════════════════════════════════════════════════════════
    // Processing
    // ═══════════════════════════════════════════════════════════════

    private static ProcessResult processCombine(Player player, Entry baseEntry, Entry mergeEntry) {
        if (baseEntry == null) {
            return new ProcessResult("No recipe selected.", 0);
        }
        if (mergeEntry == null) {
            return new ProcessResult("Select a shard to merge.", 0);
        }
        if (isSameEntry(baseEntry, mergeEntry)) {
            return new ProcessResult("Select two different shards.", 0);
        }

        String baseName = baseEntry.recipeName;
        String mergeName = mergeEntry.recipeName;
        if (!ResonantRecipeUtils.normalizeRecipeName(baseName)
                .equals(ResonantRecipeUtils.normalizeRecipeName(mergeName))) {
            return new ProcessResult("Selected shards do not match.", 0);
        }

        if (baseEntry.stats.isComplete()) {
            return new ProcessResult("Recipe is already complete. Usages: " + formatUsages(baseEntry), 100);
        }

        String mergedPattern = ResonantRecipeUtils.mergePatterns(baseEntry.pattern, mergeEntry.pattern);
        ResonantRecipeUtils.PatternStats beforeStats = baseEntry.stats;
        ResonantRecipeUtils.PatternStats afterStats = ResonantRecipeUtils.getPatternStats(mergedPattern);
        int gained = Math.max(0, afterStats.revealedSlots() - beforeStats.revealedSlots());
        if (gained <= 0) {
            return new ProcessResult("Selected shard adds no new slot data.", 0);
        }

        CompendiumContext compendiumContext = null;
        Map<String, CompendiumEntry> compendiumData = null;
        boolean needsCompendium = baseEntry.source == Source.COMPENDIUM || mergeEntry.source == Source.COMPENDIUM;
        if (needsCompendium) {
            compendiumContext = resolveCompendium(player);
            if (compendiumContext == null) {
                return new ProcessResult("Compendium moved. Reopen the UI.", 0);
            }
            compendiumData = ResonantCompendiumUtils.getCompendiumData(compendiumContext.compendium);
        }

        // Update base entry
        if (baseEntry.source == Source.COMPENDIUM) {
            CompendiumEntry baseComp = compendiumData.get(baseEntry.compendiumKey);
            if (baseComp == null) {
                return new ProcessResult("Base compendium entry missing. Reopen the UI.", 0);
            }
            String usages = baseComp.usages == null ? "" : baseComp.usages;
            String name = baseComp.name == null ? "" : baseComp.name;
            if (baseComp.quantity > 1) {
                baseComp.quantity -= 1;
                ResonantCompendiumUtils.addShardToCompendium(compendiumData, name, mergedPattern, usages, 1);
            } else {
                baseComp.pattern = mergedPattern;
            }
        } else {
            ItemContainer baseContainer = getContainerForSource(player, baseEntry.source);
            if (baseContainer == null) {
                return new ProcessResult("Inventory changed. Retry.", 0);
            }
            ItemStack baseStack = baseContainer.getItemStack(baseEntry.slot);
            if (!ResonantRecipeUtils.isResonantRecipeItem(baseStack)) {
                return new ProcessResult("Selected recipe changed. Retry.", 0);
            }
            String currentName = ResonantRecipeUtils.getRecipeName(baseStack);
            if (currentName == null || currentName.isBlank()
                    || !ResonantRecipeUtils.normalizeRecipeName(currentName)
                    .equals(ResonantRecipeUtils.normalizeRecipeName(baseName))) {
                return new ProcessResult("Selected recipe changed. Retry.", 0);
            }
            ItemStack updated = ResonantRecipeUtils.withRecipePattern(baseStack, mergedPattern);
            updated = ResonantRecipeUtils.ensureRecipeUsages(updated);
            baseContainer.setItemStackForSlot(baseEntry.slot, updated);
        }

        // Consume merge entry
        if (mergeEntry.source == Source.COMPENDIUM) {
            CompendiumEntry mergeComp = compendiumData.get(mergeEntry.compendiumKey);
            if (mergeComp == null) {
                return new ProcessResult("Merge shard missing. Reopen the UI.", 0);
            }
            if (mergeComp.quantity > 1) {
                mergeComp.quantity -= 1;
            } else {
                compendiumData.remove(mergeEntry.compendiumKey);
            }
        } else {
            ItemContainer mergeContainer = getContainerForSource(player, mergeEntry.source);
            if (mergeContainer == null) {
                return new ProcessResult("Inventory changed. Retry.", 0);
            }
            ItemStack mergeStack = mergeContainer.getItemStack(mergeEntry.slot);
            if (!ResonantRecipeUtils.isResonantRecipeItem(mergeStack)) {
                return new ProcessResult("Selected shard changed. Retry.", 0);
            }
            String currentName = ResonantRecipeUtils.getRecipeName(mergeStack);
            if (currentName == null || currentName.isBlank()
                    || !ResonantRecipeUtils.normalizeRecipeName(currentName)
                    .equals(ResonantRecipeUtils.normalizeRecipeName(mergeName))) {
                return new ProcessResult("Selected shard changed. Retry.", 0);
            }
            mergeContainer.removeItemStackFromSlot(mergeEntry.slot, 1, false, false);
        }

        if (compendiumData != null && compendiumContext != null) {
            ItemStack updatedCompendium = ResonantCompendiumUtils.saveCompendiumData(
                    compendiumContext.compendium, compendiumData);
            ItemContainer hotbar = player.getInventory().getHotbar();
            if (hotbar != null) {
                hotbar.setItemStackForSlot(compendiumContext.slot, updatedCompendium);
            }
        }

        if (DynamicTooltipUtils.isAvailable()) {
            DynamicTooltipUtils.refreshAllPlayers();
        }

        String progress = afterStats.totalSlots() > 0
                ? afterStats.revealedSlots() + "/" + afterStats.totalSlots() + " slots revealed"
                : "no pattern data";
        if (afterStats.isComplete()) {
            progress = progress + " (complete)";
        }
        return new ProcessResult("Combined 1 shard. " + progress + " (+" + gained + " new).", 100);
    }

    // ═══════════════════════════════════════════════════════════════
    // Selection helpers
    // ═══════════════════════════════════════════════════════════════

    // ═══════════════════════════════════════════════════════════════
    // HTML builders
    // ═══════════════════════════════════════════════════════════════

    private static String buildRecipeOptions(List<Entry> entries, String selectedKey, String emptyLabel) {
        if (entries.isEmpty()) {
            String label = emptyLabel == null ? "No recipe shards found" : emptyLabel;
            return "<option value=\"\" selected=\"true\">" + escapeHtml(label) + "</option>";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            String key = String.valueOf(i);
            sb.append("<option value=\"").append(key).append("\"");
            boolean selected = key.equals(selectedKey);
            if (!selected && selectedKey == null && i == 0) {
                selected = true;
            }
            if (selected) sb.append(" selected=\"true\"");
            sb.append(">");
            sb.append(escapeHtml(entry.recipeName));
            if (entry.quantity > 1) {
                sb.append(" x").append(entry.quantity);
            }
            if (entry.source == Source.COMPENDIUM) {
                sb.append(" (Compendium)");
            }
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
        List<String> tokens = parsePatternTokens(pattern);
        if (tokens.isEmpty()) {
            return "<div style=\"layout-mode: Left; spacing: 10;\"><p>No pattern data.</p></div>";
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

    private static String buildPatternPreviewStrip(String pattern, int cellSize, int iconSize) {
        List<String> tokens = parsePatternTokens(pattern);
        if (tokens.isEmpty()) {
            return "<p style=\"font-size:11;\">No pattern data.</p>";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"layout-mode: Center; spacing: 6;\">");
        for (String token : tokens) {
            String icon = resolveEssenceIcon(token);
            sb.append("<div style=\"anchor-width:").append(cellSize)
                    .append("; anchor-height:").append(cellSize)
                    .append("; background-color:#1a1a2b; layout-mode:Top; padding:4; border-radius:4;\">")
                    .append("<div style=\"flex-weight:1;\"></div>")
                    .append("<img src=\"").append(icon)
                    .append("\" width=\"").append(iconSize)
                    .append("\" height=\"").append(iconSize).append("\"/>")
                    .append("<div style=\"flex-weight:1;\"></div>")
                    .append("</div>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static String buildShardListHtml(List<Entry> shards) {
        if (shards.isEmpty()) {
            return "<p>No matching shards available.</p>";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"layout-mode:Top; spacing:8;\">");
        for (int i = 0; i < shards.size(); i++) {
            Entry shard = shards.get(i);
            sb.append("<div style=\"layout-mode:Top; background-color:#202036; padding:6; border-radius:4;\">");
            sb.append(buildPatternPreviewStrip(shard.pattern, 52, 36));
            sb.append("<p style=\"font-size:11;\">");
            sb.append("Shard ").append(i + 1);
            if (shard.quantity > 1) {
                sb.append(" x").append(shard.quantity);
            }
            sb.append(" | ").append(shard.stats.revealedSlots()).append("/")
                    .append(shard.stats.totalSlots()).append(" revealed");
            if (shard.source == Source.COMPENDIUM) {
                sb.append(" | Compendium");
            }
            sb.append("</p>");
            sb.append("</div>");
        }
        sb.append("</div>");
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
        if (entry == null) return "-";
        if (entry.item != null) {
            String type = ResonantRecipeUtils.getRecipeType(entry.item);
            if (type != null && !type.isBlank()) {
                return type;
            }
        }
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

    // ═══════════════════════════════════════════════════════════════
    // Shared helpers
    // ═══════════════════════════════════════════════════════════════

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

    private static ItemContainer getContainerForSource(Player player, Source source) {
        if (player == null || player.getInventory() == null || source == null) {
            return null;
        }
        if (source == Source.HOTBAR) {
            return player.getInventory().getHotbar();
        }
        if (source == Source.STORAGE) {
            return player.getInventory().getStorage();
        }
        return null;
    }

    private static Entry resolveSelection(List<Entry> entries, String value) {
        if (entries == null || entries.isEmpty() || value == null || value.isBlank()) {
            return null;
        }
        Entry byIndex = HyUIReflectionUtils.resolveIndexSelection(entries, value);
        if (byIndex != null) {
            return byIndex;
        }
        String base = trimOptionLabel(value);
        if (base.isBlank()) {
            return null;
        }
        String normalized = ResonantRecipeUtils.normalizeRecipeName(base);
        for (Entry entry : entries) {
            if (entry == null || entry.recipeName == null) {
                continue;
            }
            if (ResonantRecipeUtils.normalizeRecipeName(entry.recipeName).equals(normalized)) {
                return entry;
            }
        }
        return null;
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

    private static void debug(String message) {
        System.out.println("[SocketReforge] RecipeCombineUI: " + message);
    }

    private static String indexOfEntry(List<Entry> entries, Entry entry) {
        if (entries == null || entry == null) {
            return "";
        }
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i) == entry) {
                return String.valueOf(i);
            }
        }
        return "";
    }

    private static String trimOptionLabel(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        int cut = trimmed.length();
        int idx = trimmed.indexOf(" x");
        if (idx > 0) {
            cut = Math.min(cut, idx);
        }
        idx = trimmed.indexOf(" (");
        if (idx > 0) {
            cut = Math.min(cut, idx);
        }
        idx = trimmed.indexOf(" [");
        if (idx > 0) {
            cut = Math.min(cut, idx);
        }
        return trimmed.substring(0, cut).trim();
    }

    private static void cleanupPlayerState(Player player) {
        if (player == null) {
            return;
        }
        PlayerRef ref = player.getPlayerRef();
        bumpSession(ref);
        openPages.remove(ref);
        pendingSelections.remove(ref);
        processingPlayers.remove(ref);
        activeCompendiums.remove(ref);
    }

    private static int bumpSession(PlayerRef ref) {
        if (ref == null) {
            return 0;
        }
        return sessionTokens.merge(ref, 1, Integer::sum);
    }

    private static int currentSession(PlayerRef ref) {
        if (ref == null) {
            return 0;
        }
        return sessionTokens.getOrDefault(ref, 0);
    }

    private static boolean isSessionActive(PlayerRef ref, int token) {
        if (ref == null) {
            return false;
        }
        return sessionTokens.getOrDefault(ref, 0) == token;
    }
}
