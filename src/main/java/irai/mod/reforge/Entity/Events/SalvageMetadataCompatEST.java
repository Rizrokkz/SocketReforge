package irai.mod.reforge.Entity.Events;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bson.BsonDocument;

import com.hypixel.hytale.builtin.crafting.CraftingPlugin;
import com.hypixel.hytale.builtin.crafting.component.BenchBlock;
import com.hypixel.hytale.builtin.crafting.component.CraftingManager;
import com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock;
import com.hypixel.hytale.builtin.crafting.window.BenchWindow;
import com.hypixel.hytale.builtin.crafting.window.ProcessingBenchWindow;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.Bench;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Common.ItemTypeUtils;

/**
 * Salvage bench compatibility:
 * Processing benches do strict metadata-aware stack matching.
 * First tries a runtime recipe clone bound to the current input metadata (no item mutation).
 * If that path is unavailable, falls back to temporary input metadata sanitize/restore.
 */
@SuppressWarnings("removal")
public class SalvageMetadataCompatEST extends EntityTickingSystem<EntityStore> {
    private static final String SALVAGE_BENCH_ID = "Salvagebench";
    private static final String SOCKET_REFORGE_META_PREFIX = "SocketReforge.";
    private static final String LEGACY_RESONANCE_QUALITY_KEY = "qualityIndex";
    private static final long SNAPSHOT_TTL_MS = 10L * 60L * 1000L;

    private static final boolean DEBUG_LOG = false;
    private static final Map<UUID, Map<Short, PendingSnapshot>> PENDING_ORIGINALS = new ConcurrentHashMap<>();

    private static Field benchField;
    private static Field benchBlockField;
    private static Field processingBenchField;
    private static Field processingRecipeField;
    private static Field processingRecipeIdField;
    private static Field craftingRecipeIdField;
    private static Field craftingRecipePrimaryOutputQuantityField;
    private static boolean loggedBenchReflectionError;
    private static boolean loggedBenchBlockReflectionError;
    private static boolean loggedProcessingBenchReflectionError;
    private static boolean loggedUpdateRecipeReflectionError;
    private static boolean loggedRecipeInjectionReflectionError;

    private static final class PendingSnapshot {
        private final ItemStack original;
        private final long createdAtMs;

        private PendingSnapshot(ItemStack original, long createdAtMs) {
            this.original = original;
            this.createdAtMs = createdAtMs;
        }
    }

    private static final class RecipeSelection {
        private final CraftingRecipe recipe;
        private final short[] assignedSlots;

        private RecipeSelection(CraftingRecipe recipe, short[] assignedSlots) {
            this.recipe = recipe;
            this.assignedSlots = assignedSlots;
        }
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public boolean isParallel(int from, int to) {
        return false;
    }

    @Override
    public void tick(float time,
                     int index,
                     ArchetypeChunk<EntityStore> chunk,
                     Store<EntityStore> store,
                     CommandBuffer<EntityStore> commandBuffer) {
        Player player = store.getComponent(chunk.getReferenceTo(index), Player.getComponentType());
        if (player == null || player.getWindowManager() == null) {
            return;
        }
        UUID playerId = player.getUuid();
        if (playerId == null) {
            return;
        }

        List<Window> windows = player.getWindowManager().getWindows();
        if (windows == null || windows.isEmpty()) {
            restorePendingOriginals(player, playerId);
            return;
        }

        boolean sawSalvageWindow = false;

        for (Window window : windows) {
            if (!(window instanceof ProcessingBenchWindow processingWindow)) {
                continue;
            }

            Bench bench = getBench(processingWindow);
            if (bench == null || bench.getId() == null || !SALVAGE_BENCH_ID.equalsIgnoreCase(bench.getId())) {
                continue;
            }

            sawSalvageWindow = true;
            BenchBlock benchBlock = getBenchBlock(processingWindow);
            ProcessingBenchBlock processingBench = getProcessingBench(processingWindow);
            if (processingBench != null) {
                ItemContainer inputContainer = processingBench.getInputContainer();
                if (inputContainer != null) {
                    if (tryInjectMetadataBoundRecipe(processingBench, bench, benchBlock, inputContainer)) {
                        restoreSnapshotsForRemovedInputSlots(player, playerId, inputContainer);
                        continue;
                    }
                    int changed = sanitizeContainer(inputContainer, playerId);
                    if (changed > 0) {
                        forceRecipeRefresh(processingBench, benchBlock);
                    }
                    restoreSnapshotsForRemovedInputSlots(player, playerId, inputContainer);
                } else {
                    // Fallback: sanitize the combined window container if direct input container is unavailable.
                    int changed = sanitizeContainer(processingWindow.getItemContainer(), playerId);
                    if (changed > 0) {
                        forceRecipeRefresh(processingBench, benchBlock);
                    }
                }
            } else {
                sanitizeContainer(processingWindow.getItemContainer(), playerId);
            }
        }

        if (!sawSalvageWindow) {
            restorePendingOriginals(player, playerId);
        }
    }

    private static Bench getBench(ProcessingBenchWindow window) {
        if (window == null) {
            return null;
        }
        try {
            if (benchField == null) {
                benchField = BenchWindow.class.getDeclaredField("bench");
                benchField.setAccessible(true);
            }
            return (Bench) benchField.get(window);
        } catch (Throwable t) {
            if (!loggedBenchReflectionError) {
                loggedBenchReflectionError = true;
                log("Failed to access BenchWindow.bench via reflection: " + t.getMessage());
            }
            return null;
        }
    }

    private static BenchBlock getBenchBlock(ProcessingBenchWindow window) {
        if (window == null) {
            return null;
        }
        try {
            if (benchBlockField == null) {
                benchBlockField = BenchWindow.class.getDeclaredField("benchBlock");
                benchBlockField.setAccessible(true);
            }
            Object value = benchBlockField.get(window);
            if (value instanceof BenchBlock benchBlock) {
                return benchBlock;
            }
            return null;
        } catch (Throwable t) {
            if (!loggedBenchBlockReflectionError) {
                loggedBenchBlockReflectionError = true;
                log("Failed to access BenchWindow.benchBlock via reflection: " + t.getMessage());
            }
            return null;
        }
    }

    private static ProcessingBenchBlock getProcessingBench(ProcessingBenchWindow window) {
        if (window == null) {
            return null;
        }
        try {
            if (processingBenchField == null) {
                processingBenchField = ProcessingBenchWindow.class.getDeclaredField("processingBenchState");
                processingBenchField.setAccessible(true);
            }
            Object state = processingBenchField.get(window);
            if (state instanceof ProcessingBenchBlock processingBench) {
                return processingBench;
            }
            return null;
        } catch (Throwable t) {
            if (!loggedProcessingBenchReflectionError) {
                loggedProcessingBenchReflectionError = true;
                log("Failed to access ProcessingBenchWindow.processingBenchState via reflection: " + t.getMessage());
            }
            return null;
        }
    }

    private static boolean tryInjectMetadataBoundRecipe(ProcessingBenchBlock processingBench,
                                                        Bench bench,
                                                        BenchBlock benchBlock,
                                                        ItemContainer inputContainer) {
        if (processingBench == null || bench == null || inputContainer == null) {
            return false;
        }
        if (!containsMetadataSensitiveInput(inputContainer)) {
            return false;
        }
        RecipeSelection selection = selectRecipeIgnoringMetadata(bench, benchBlock, inputContainer);
        if (selection == null) {
            return false;
        }
        try {
            CraftingRecipe clone = cloneRecipeWithBoundMetadata(selection, inputContainer);
            setProcessingRecipe(processingBench, clone);
            return true;
        } catch (Throwable t) {
            if (!loggedRecipeInjectionReflectionError) {
                loggedRecipeInjectionReflectionError = true;
                log("Failed to inject metadata-bound salvage recipe via reflection: " + t.getMessage());
            }
            return false;
        }
    }

    private static boolean containsMetadataSensitiveInput(ItemContainer inputContainer) {
        if (inputContainer == null) {
            return false;
        }
        for (short slot = 0; slot < inputContainer.getCapacity(); slot++) {
            ItemStack stack = inputContainer.getItemStack(slot);
            if (shouldSanitizeForSalvage(stack)) {
                return true;
            }
        }
        return false;
    }

    private static RecipeSelection selectRecipeIgnoringMetadata(Bench bench, BenchBlock benchBlock, ItemContainer inputContainer) {
        if (bench == null || inputContainer == null) {
            return null;
        }
        List<CraftingRecipe> recipes = CraftingPlugin.getBenchRecipes(bench.getType(), bench.getId());
        if (recipes == null || recipes.isEmpty()) {
            return null;
        }

        RecipeSelection bestSelection = null;
        int bestInputCount = -1;
        int tierLevel = benchBlock != null ? benchBlock.getTierLevel() : 0;

        for (CraftingRecipe recipe : recipes) {
            if (recipe == null || recipe.isRestrictedByBenchTierLevel(bench.getId(), tierLevel)) {
                continue;
            }
            MaterialQuantity[] inputs = recipe.getInput();
            if (inputs == null || inputs.length == 0) {
                continue;
            }
            short[] assignedSlots = assignSlotsIgnoringMetadata(inputs, inputContainer);
            if (assignedSlots == null) {
                continue;
            }
            if (inputs.length > bestInputCount) {
                bestInputCount = inputs.length;
                bestSelection = new RecipeSelection(recipe, assignedSlots);
            }
        }

        return bestSelection;
    }

    private static short[] assignSlotsIgnoringMetadata(MaterialQuantity[] inputs, ItemContainer inputContainer) {
        if (inputs == null || inputContainer == null) {
            return null;
        }
        int capacity = inputContainer.getCapacity();
        boolean[] used = new boolean[Math.max(capacity, 0)];
        short[] assigned = new short[inputs.length];

        for (int materialIndex = 0; materialIndex < inputs.length; materialIndex++) {
            MaterialQuantity material = inputs[materialIndex];
            if (material == null) {
                return null;
            }
            int requiredQty = material.getQuantity();
            if (requiredQty <= 0) {
                requiredQty = 1;
            }

            short foundSlot = -1;
            for (short slot = 0; slot < capacity; slot++) {
                if (used[slot]) {
                    continue;
                }
                ItemStack stack = inputContainer.getItemStack(slot);
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                if (stack.getQuantity() < requiredQty) {
                    continue;
                }
                if (!CraftingManager.matches(material, stack)) {
                    continue;
                }
                foundSlot = slot;
                break;
            }
            if (foundSlot < 0) {
                return null;
            }
            used[foundSlot] = true;
            assigned[materialIndex] = foundSlot;
        }

        return assigned;
    }

    private static CraftingRecipe cloneRecipeWithBoundMetadata(RecipeSelection selection, ItemContainer inputContainer) throws Exception {
        CraftingRecipe recipe = selection.recipe;
        MaterialQuantity[] originalInputs = recipe.getInput();
        MaterialQuantity[] adjustedInputs = new MaterialQuantity[originalInputs.length];

        for (int i = 0; i < originalInputs.length; i++) {
            MaterialQuantity original = originalInputs[i];
            if (original == null) {
                adjustedInputs[i] = null;
                continue;
            }
            // Tag-only materials do not expose their tag publicly; keep original reference.
            if (original.getItemId() == null && original.getResourceTypeId() == null) {
                adjustedInputs[i] = original;
                continue;
            }

            BsonDocument metadata = original.getMetadata();
            short assignedSlot = (selection.assignedSlots != null && i < selection.assignedSlots.length)
                    ? selection.assignedSlots[i]
                    : -1;
            if (assignedSlot >= 0) {
                ItemStack sourceStack = inputContainer.getItemStack(assignedSlot);
                if (sourceStack != null && !sourceStack.isEmpty()) {
                    metadata = sourceStack.getMetadata();
                }
            }

            adjustedInputs[i] = new MaterialQuantity(
                    original.getItemId(),
                    original.getResourceTypeId(),
                    null,
                    original.getQuantity(),
                    metadata
            );
        }

        CraftingRecipe clone = new CraftingRecipe(
                adjustedInputs,
                recipe.getPrimaryOutput(),
                recipe.getOutputs(),
                getRecipePrimaryOutputQuantity(recipe),
                recipe.getBenchRequirement(),
                recipe.getTimeSeconds(),
                recipe.isKnowledgeRequired(),
                recipe.getRequiredMemoriesLevel()
        );
        setCraftingRecipeId(clone, recipe.getId());
        return clone;
    }

    private static void setProcessingRecipe(ProcessingBenchBlock state, CraftingRecipe recipe) throws Exception {
        if (processingRecipeField == null) {
            processingRecipeField = ProcessingBenchBlock.class.getDeclaredField("recipe");
            processingRecipeField.setAccessible(true);
        }
        if (processingRecipeIdField == null) {
            processingRecipeIdField = ProcessingBenchBlock.class.getDeclaredField("recipeId");
            processingRecipeIdField.setAccessible(true);
        }
        processingRecipeField.set(state, recipe);
        processingRecipeIdField.set(state, recipe != null ? recipe.getId() : null);
    }

    private static int getRecipePrimaryOutputQuantity(CraftingRecipe recipe) throws Exception {
        if (craftingRecipePrimaryOutputQuantityField == null) {
            craftingRecipePrimaryOutputQuantityField = CraftingRecipe.class.getDeclaredField("primaryOutputQuantity");
            craftingRecipePrimaryOutputQuantityField.setAccessible(true);
        }
        return craftingRecipePrimaryOutputQuantityField.getInt(recipe);
    }

    private static void setCraftingRecipeId(CraftingRecipe recipe, String id) throws Exception {
        if (craftingRecipeIdField == null) {
            craftingRecipeIdField = CraftingRecipe.class.getDeclaredField("id");
            craftingRecipeIdField.setAccessible(true);
        }
        craftingRecipeIdField.set(recipe, id);
    }

    private static int sanitizeContainer(ItemContainer container, UUID playerId) {
        if (container == null) {
            return 0;
        }

        int sanitizedCount = 0;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack == null || stack.isEmpty() || !shouldSanitizeForSalvage(stack)) {
                continue;
            }

            rememberOriginal(playerId, slot, stack);
            ItemStack sanitized = stripSocketReforgeMetadata(stack);
            if (!sanitized.equals(stack)) {
                container.setItemStackForSlot(slot, sanitized);
                sanitizedCount++;
            }
        }
        return sanitizedCount;
    }

    private static boolean shouldSanitizeForSalvage(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        BsonDocument metadata = stack.getMetadata();
        if (metadata == null || metadata.isEmpty()) {
            return false;
        }
        if (hasSocketReforgeMetadata(stack)) {
            return true;
        }
        return ItemTypeUtils.isEquipment(stack);
    }

    private static boolean hasSocketReforgeMetadata(ItemStack stack) {
        BsonDocument metadata = stack.getMetadata();
        if (metadata == null || metadata.isEmpty()) {
            return false;
        }

        if (metadata.containsKey(LEGACY_RESONANCE_QUALITY_KEY)) {
            return true;
        }

        for (String key : metadata.keySet()) {
            if (key != null && key.startsWith(SOCKET_REFORGE_META_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack stripSocketReforgeMetadata(ItemStack stack) {
        BsonDocument metadata = stack.getMetadata();
        if (metadata == null || metadata.isEmpty()) {
            return stack;
        }

        // For salvage inputs we only need itemId + quantity matching.
        // Clearing metadata entirely avoids any strict removeMaterials metadata mismatch.
        return stack.withMetadata((BsonDocument) null);
    }

    private static void rememberOriginal(UUID playerId, short slot, ItemStack original) {
        if (playerId == null || original == null) {
            return;
        }
        PENDING_ORIGINALS
                .computeIfAbsent(playerId, ignored -> new HashMap<>())
                .putIfAbsent(slot, new PendingSnapshot(original, System.currentTimeMillis()));
    }

    private static void restorePendingOriginals(Player player, UUID playerId) {
        if (player == null || playerId == null || player.getInventory() == null) {
            return;
        }

        Map<Short, PendingSnapshot> pending = PENDING_ORIGINALS.get(playerId);
        if (pending == null || pending.isEmpty()) {
            return;
        }

        ItemContainer inventory = player.getInventory().getCombinedHotbarFirst();
        if (inventory == null) {
            return;
        }

        int restored = 0;
        List<Short> done = new ArrayList<>();
        for (Map.Entry<Short, PendingSnapshot> entry : pending.entrySet()) {
            PendingSnapshot snapshot = entry.getValue();
            if (snapshot == null || snapshot.original == null) {
                done.add(entry.getKey());
                continue;
            }
            short slot = findRestorableSlot(inventory, snapshot.original);
            if (slot >= 0) {
                inventory.setItemStackForSlot(slot, snapshot.original);
                restored++;
                done.add(entry.getKey());
                continue;
            }
            if (isExpired(snapshot)) {
                done.add(entry.getKey());
            }
        }
        for (Short slot : done) {
            pending.remove(slot);
        }

        if (DEBUG_LOG && restored > 0) {
            log("player=" + playerId + " restoredOriginalStacks=" + restored);
        }
        if (pending.isEmpty()) {
            PENDING_ORIGINALS.remove(playerId);
        }
    }

    private static void restoreSnapshotsForRemovedInputSlots(Player player, UUID playerId, ItemContainer inputContainer) {
        if (player == null || playerId == null || inputContainer == null || player.getInventory() == null) {
            return;
        }
        Map<Short, PendingSnapshot> pending = PENDING_ORIGINALS.get(playerId);
        if (pending == null || pending.isEmpty()) {
            return;
        }

        ItemContainer inventory = player.getInventory().getCombinedHotbarFirst();
        if (inventory == null) {
            return;
        }

        List<Short> done = new ArrayList<>();
        for (Map.Entry<Short, PendingSnapshot> entry : pending.entrySet()) {
            short slot = entry.getKey();
            PendingSnapshot snapshot = entry.getValue();
            if (snapshot == null || snapshot.original == null) {
                done.add(slot);
                continue;
            }
            ItemStack original = snapshot.original;
            ItemStack current = slot < inputContainer.getCapacity() ? inputContainer.getItemStack(slot) : null;

            if (current != null && !current.isEmpty()
                    && String.valueOf(current.getItemId()).equals(String.valueOf(original.getItemId()))) {
                continue;
            }

            short restoreSlot = findRestorableSlot(inventory, original);
            if (restoreSlot >= 0) {
                inventory.setItemStackForSlot(restoreSlot, original);
                if (DEBUG_LOG) {
                    log("player=" + playerId + " restoredOriginalStack slot=" + restoreSlot + " item=" + original.getItemId());
                }
                done.add(slot);
                continue;
            }
            if (isExpired(snapshot)) {
                done.add(slot);
            }
        }

        for (Short slot : done) {
            pending.remove(slot);
        }
        if (pending.isEmpty()) {
            PENDING_ORIGINALS.remove(playerId);
        }
    }

    private static boolean isExpired(PendingSnapshot snapshot) {
        return snapshot != null && (System.currentTimeMillis() - snapshot.createdAtMs) > SNAPSHOT_TTL_MS;
    }

    private static short findRestorableSlot(ItemContainer inventory, ItemStack original) {
        for (short slot = 0; slot < inventory.getCapacity(); slot++) {
            ItemStack candidate = inventory.getItemStack(slot);
            if (candidate == null || candidate.isEmpty()) {
                continue;
            }
            if (!sameRestorableIdentity(candidate, original)) {
                continue;
            }
            return slot;
        }
        return -1;
    }

    private static boolean sameRestorableIdentity(ItemStack candidate, ItemStack original) {
        if (candidate == null || original == null) {
            return false;
        }
        if (!String.valueOf(candidate.getItemId()).equals(String.valueOf(original.getItemId()))) {
            return false;
        }
        if (candidate.getQuantity() != original.getQuantity()) {
            return false;
        }
        BsonDocument metadata = candidate.getMetadata();
        return metadata == null || metadata.isEmpty();
    }

    private static void forceRecipeRefresh(ProcessingBenchBlock state, BenchBlock benchBlock) {
        if (state == null) {
            return;
        }
        try {
            state.clearCurrentRecipe();
            if (benchBlock != null) {
                state.checkForRecipeUpdate(benchBlock);
            }
        } catch (Throwable t) {
            if (!loggedUpdateRecipeReflectionError) {
                loggedUpdateRecipeReflectionError = true;
                log("Failed to refresh processing bench recipe: " + t.getMessage());
            }
        }
    }

    private static void log(String message) {
        System.out.println("[SocketReforge][SALVAGE_COMPAT] " + message);
    }
}
