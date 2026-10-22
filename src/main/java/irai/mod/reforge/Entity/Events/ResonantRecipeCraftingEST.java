package irai.mod.reforge.Entity.Events;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.CraftRecipeEvent;
import com.hypixel.hytale.server.core.inventory.InventoryUtils;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSettings;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Common.ResonantRecipeUtils;
import irai.mod.reforge.Socket.Essence;
import irai.mod.reforge.Socket.ResonanceSystem;
import irai.mod.reforge.Util.DynamicTooltipUtils;
import irai.mod.reforge.Util.LangLoader;
import irai.mod.reforge.Util.NameResolver;

/** Replaces Goblin Lab dummy recipe output before the crafting manager gives it to the player. */
public final class ResonantRecipeCraftingEST extends EntityEventSystem<EntityStore, CraftRecipeEvent.Post> {
    private static final String DUMMY_RECIPE_ITEM_ID = "Dummy_Resonant_Recipe";
    private static final String BENCH_ID = "GoblinLab_Workbench";
    private static final String BENCH_CATEGORY = "GoblinLab_Fragments";
    private static final int COMPLETE_RECIPE_CHANCE_PERCENT = 25;

    public ResonantRecipeCraftingEST() {
        super(CraftRecipeEvent.Post.class);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(int index,
                       ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store,
                       CommandBuffer<EntityStore> commandBuffer,
                       CraftRecipeEvent.Post event) {
        if (event == null || isCancelled(event) || !isTargetRecipe(event.getCraftedRecipe())) {
            return;
        }

        Ref<EntityStore> playerRef = chunk.getReferenceTo(index);
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null || player.getInventory() == null) {
            return;
        }

        MaterialQuantity primaryOutput = event.getCraftedRecipe().getPrimaryOutput();
        int outputCount = Math.max(1, primaryOutput.getQuantity()) * Math.max(1, event.getQuantity());
        List<ItemStack> rolledRecipes = new ArrayList<>(outputCount);
        for (int i = 0; i < outputCount; i++) {
            ItemStack rolledRecipe = rollRecipeItem();
            if (rolledRecipe == null || rolledRecipe.isEmpty()) {
                return;
            }
            rolledRecipes.add(rolledRecipe);
        }

        PlayerSettings settings = store.getComponent(playerRef, PlayerSettings.getComponentType());
        if (settings == null) {
            settings = PlayerSettings.defaults();
        }
        ItemContainer outputContainer = InventoryUtils.getContainerForItemPickup(
                playerRef, rolledRecipes.get(0).getItem(), settings, store);
        if (outputContainer == null || !outputContainer.canAddItemStacks(rolledRecipes)) {
            // Leave the vanilla dummy output intact if the rolled results cannot all fit.
            return;
        }
        var addTransaction = outputContainer.addItemStacks(rolledRecipes);
        if (!addTransaction.succeeded()) {
            return;
        }

        String locale = LangLoader.getPlayerLanguage(player);
        for (ItemStackTransaction itemTransaction : addTransaction.getList()) {
            if (itemTransaction == null) {
                continue;
            }
            for (ItemStackSlotTransaction slotTransaction : itemTransaction.getSlotTransactions()) {
                if (slotTransaction != null && slotTransaction.succeeded()) {
                    DynamicTooltipUtils.refreshContainerSlot(
                            outputContainer, slotTransaction.getSlot(), locale);
                }
            }
        }

        // CraftRecipeEvent.Post is dispatched before giveOutput; cancellation suppresses the
        // dummy stack, so the rolled items are the only output from this craft.
        event.setCancelled(true);
    }

    private static boolean isCancelled(CraftRecipeEvent.Post event) {
        return event.isCancelled();
    }

    private static boolean isTargetRecipe(CraftingRecipe recipe) {
        if (recipe == null) {
            return false;
        }
        MaterialQuantity primaryOutput = recipe.getPrimaryOutput();
        if (primaryOutput == null || !DUMMY_RECIPE_ITEM_ID.equalsIgnoreCase(primaryOutput.getItemId())) {
            return false;
        }

        BenchRequirement[] requirements = recipe.getBenchRequirement();
        if (requirements == null) {
            return false;
        }
        for (BenchRequirement requirement : requirements) {
            if (requirement == null || !BENCH_ID.equalsIgnoreCase(requirement.id)
                    || requirement.categories == null) {
                continue;
            }
            for (String category : requirement.categories) {
                if (BENCH_CATEGORY.equalsIgnoreCase(category)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static ItemStack rollRecipeItem() {
        ResonanceSystem.ResonanceRecipe recipe = ResonanceSystem.rollRandomResonanceRecipe(
                ThreadLocalRandom.current());
        if (recipe == null || recipe.name() == null || recipe.name().isBlank()) {
            return null;
        }

        Essence.Type[] pattern = recipe.pattern();
        if (pattern == null || pattern.length == 0) {
            return null;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        boolean complete = pattern.length <= 1 || random.nextInt(100) < COMPLETE_RECIPE_CHANCE_PERCENT;
        boolean[] revealMask = complete ? null : buildPartialRevealMask(pattern.length, random);

        String displayName = recipe.name().trim() + " Recipe";
        String appliesTo = recipe.appliesTo() == null ? "" : recipe.appliesTo();
        ItemStack stack = new ItemStack(ResonantRecipeUtils.RECIPE_ITEM_ID, 1)
                .withMetadata(NameResolver.KEY_DISPLAY_NAME, Codec.STRING, displayName)
                .withMetadata(ResonantRecipeUtils.META_RECIPE_NAME, Codec.STRING, recipe.name().trim())
                .withMetadata(ResonantRecipeUtils.META_RECIPE_PATTERN, Codec.STRING,
                        ResonantRecipeUtils.buildPatternFromEssence(pattern, revealMask))
                .withMetadata(ResonantRecipeUtils.META_RECIPE_TYPE, Codec.STRING, appliesTo);
        if (complete) {
            stack = stack.withMetadata(ResonantRecipeUtils.META_RECIPE_USAGES, Codec.STRING,
                    ResonantRecipeUtils.DEFAULT_RECIPE_USAGES);
        }
        return stack;
    }

    private static boolean[] buildPartialRevealMask(int length, ThreadLocalRandom random) {
        if (length <= 1) {
            return new boolean[] {true};
        }

        int maxReveal = Math.min(2, length - 1);
        int revealCount = 1 + random.nextInt(maxReveal);
        boolean[] revealMask = new boolean[length];
        int revealed = 0;
        while (revealed < revealCount) {
            int slot = random.nextInt(length);
            if (!revealMask[slot]) {
                revealMask[slot] = true;
                revealed++;
            }
        }
        return revealMask;
    }
}
