package irai.mod.reforge.Interactions;

import java.util.ArrayList;
import java.util.List;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Common.ResonantRecipeUtils;
import irai.mod.reforge.Util.DynamicTooltipUtils;

/**
 * Combines resonant recipe shards of the same name into a single merged recipe.
 */
@SuppressWarnings("removal")
public class ResonantRecipeCombineUse extends SimpleInteraction {

    public static final BuilderCodec<ResonantRecipeCombineUse> CODEC =
            BuilderCodec.builder(ResonantRecipeCombineUse.class, ResonantRecipeCombineUse::new, SimpleInteraction.CODEC).build();

    @Override
    protected void tick0(boolean firstRun, float time, @NonNullDecl InteractionType type,
                         @NonNullDecl InteractionContext context, @NonNullDecl CooldownHandler cooldownHandler) {
        super.tick0(firstRun, time, type, context, cooldownHandler);
        if (!firstRun || type != InteractionType.Use) {
            return;
        }

        Player player = getPlayerFromContext(context);
        if (player == null || player.getInventory() == null) {
            return;
        }

        ItemStack held = context.getHeldItem();
        if (!ResonantRecipeUtils.isResonantRecipeItem(held)) {
            return;
        }

        // Prefer HyUI page when available; keeps legacy chat flow as fallback.
        String recipeName = ResonantRecipeUtils.getRecipeName(held);
        String recipePattern = ResonantRecipeUtils.getRecipePattern(held);
        if ((recipeName == null || recipeName.isBlank())
                && (recipePattern == null || recipePattern.isBlank())) {
            player.sendMessage(Message.raw("This recipe shard is blank."));
            return;
        }
        if (irai.mod.reforge.UI.RecipeCombineUI.isAvailable()) {
            irai.mod.reforge.UI.RecipeCombineUI.open(player);
            return;
        }

        short heldSlot = context.getHeldItemSlot();
        combineRecipes(player, held, heldSlot);
    }

    private void combineRecipes(Player player, ItemStack held, short heldSlot) {
        String recipeName = ResonantRecipeUtils.getRecipeName(held);
        if (recipeName == null || recipeName.isBlank()) {
            player.sendMessage(Message.raw("This recipe shard is blank."));
            return;
        }

        ResonantRecipeUtils.PatternStats currentStats = ResonantRecipeUtils.getPatternStats(
                ResonantRecipeUtils.getRecipePattern(held));
        if (currentStats.isComplete()) {
            ItemStack ensured = ResonantRecipeUtils.ensureRecipeUsages(held);
            if (ensured != held) {
                ItemContainer hotbar = player.getInventory().getHotbar();
                if (hotbar != null) {
                    hotbar.setItemStackForSlot(heldSlot, ensured);
                }
                held = ensured;
            }
            String usages = ResonantRecipeUtils.getRecipeUsages(held);
            String usageText = usages != null && !usages.isBlank() ? " Usages: " + usages + "." : "";
            player.sendMessage(Message.raw("This recipe is already complete." + usageText));
            return;
        }

        String normalized = ResonantRecipeUtils.normalizeRecipeName(recipeName);
        List<RecipeSlot> matches = new ArrayList<>();

        ItemContainer hotbar = player.getInventory().getHotbar();
        ItemContainer storage = player.getInventory().getStorage();
        collectMatches(matches, hotbar, normalized);
        collectMatches(matches, storage, normalized);

        if (matches.size() <= 1) {
            player.sendMessage(Message.raw("No other \"" + recipeName + "\" recipe shards to combine."));
            return;
        }

        String basePattern = ResonantRecipeUtils.getRecipePattern(held);
        if (basePattern == null) {
            basePattern = "";
        }

        List<RecipeSlot> candidates = new ArrayList<>(matches.size());
        for (RecipeSlot slot : matches) {
            if (slot.container == hotbar && slot.slot == heldSlot) {
                continue;
            }
            candidates.add(slot);
        }

        List<RecipeSlot> selected = ResonantRecipeUtils.selectBestMergeCandidates(basePattern, candidates);
        if (selected.isEmpty()) {
            player.sendMessage(Message.raw("No other \"" + recipeName + "\" shards reveal new slots to combine."));
            return;
        }

        String mergedPattern = basePattern;
        for (RecipeSlot slot : selected) {
            mergedPattern = ResonantRecipeUtils.mergePatterns(mergedPattern, slot.pattern);
        }

        ResonantRecipeUtils.PatternStats beforeStats = ResonantRecipeUtils.getPatternStats(
                ResonantRecipeUtils.getRecipePattern(held));
        ResonantRecipeUtils.PatternStats afterStats = ResonantRecipeUtils.getPatternStats(mergedPattern);

        ItemStack updated = ResonantRecipeUtils.withRecipePattern(held, mergedPattern);
        updated = ResonantRecipeUtils.ensureRecipeUsages(updated);
        hotbar.setItemStackForSlot(heldSlot, updated);

        int removed = 0;
        for (RecipeSlot slot : selected) {
            slot.container.removeItemStackFromSlot(slot.slot, 1, false, false);
            removed++;
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
        int gained = Math.max(0, afterStats.revealedSlots() - beforeStats.revealedSlots());
        String gainLabel = gained > 0 ? " (+" + gained + " new)" : "";
        player.sendMessage(Message.raw("Combined " + removed + " shard(s). " + progress + gainLabel + "."));
    }

    private void collectMatches(List<RecipeSlot> matches, ItemContainer container, String normalizedRecipeName) {
        if (container == null) {
            return;
        }
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (!ResonantRecipeUtils.isResonantRecipeItem(stack)) {
                continue;
            }
            String name = ResonantRecipeUtils.getRecipeName(stack);
            if (name == null || name.isBlank()) {
                continue;
            }
            if (!ResonantRecipeUtils.normalizeRecipeName(name).equals(normalizedRecipeName)) {
                continue;
            }
            String pattern = ResonantRecipeUtils.getRecipePattern(stack);
            ResonantRecipeUtils.PatternStats stats = ResonantRecipeUtils.getPatternStats(pattern);
            if (stats.isComplete()) {
                continue;
            }
            matches.add(new RecipeSlot(container, slot, pattern == null ? "" : pattern));
        }
    }

    private Player getPlayerFromContext(InteractionContext context) {
        Ref<EntityStore> owningEntity = context.getOwningEntity();
        if (owningEntity == null) {
            return null;
        }
        Store<EntityStore> store = owningEntity.getStore();
        if (store == null) {
            return null;
        }
        return store.getComponent(owningEntity, Player.getComponentType());
    }

    private static final class RecipeSlot implements ResonantRecipeUtils.MergeCandidate {
        private final ItemContainer container;
        private final short slot;
        private final String pattern;
        private final boolean[] revealMask;

        private RecipeSlot(ItemContainer container, short slot, String pattern) {
            this.container = container;
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
}
