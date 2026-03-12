package irai.mod.reforge.Interactions;

import java.util.Map;

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

import irai.mod.reforge.Common.ResonantCompendiumUtils;
import irai.mod.reforge.Common.ResonantCompendiumUtils.CompendiumEntry;
import irai.mod.reforge.Common.ResonantRecipeUtils;
import irai.mod.reforge.UI.ResonantCompendiumUI;
import irai.mod.reforge.Util.DynamicTooltipUtils;

public class ResonantCompendiumUse extends SimpleInteraction {

    public static final BuilderCodec<ResonantCompendiumUse> CODEC =
            BuilderCodec.builder(ResonantCompendiumUse.class, ResonantCompendiumUse::new, SimpleInteraction.CODEC).build();

    @SuppressWarnings("removal")
    @Override
    protected void tick0(boolean firstRun, float time, @NonNullDecl InteractionType type,
                         @NonNullDecl InteractionContext context, @NonNullDecl CooldownHandler cooldownHandler) {
        super.tick0(firstRun, time, type, context, cooldownHandler);
        if (!firstRun) {
            return;
        }

        Player player = getPlayerFromContext(context);
        if (player == null || player.getInventory() == null) {
            return;
        }

        ItemStack compendium = context.getHeldItem();
        if (!ResonantCompendiumUtils.isCompendiumItem(compendium)) {
            return;
        }

        if (type == InteractionType.Secondary) {
            if (ResonantCompendiumUI.isAvailable()) {
                ResonantCompendiumUI.open(player, context.getHeldItemSlot());
            } else {
                player.sendMessage(Message.raw("HyUI not installed - compendium UI disabled."));
            }
            return;
        }

        if (type != InteractionType.Use) {
            return;
        }

        Map<String, CompendiumEntry> data = ResonantCompendiumUtils.getCompendiumData(compendium);

        int absorbed = 0;

        absorbed += absorbFromContainer(player.getInventory().getHotbar(), data);
        absorbed += absorbFromContainer(player.getInventory().getStorage(), data);

        if (absorbed > 0) {
            ItemStack updated = ResonantCompendiumUtils.saveCompendiumData(compendium, data);

            String countSummary = getSummary(data);
            player.sendMessage(Message.raw(countSummary.replace("\n", " ")));

            ItemContainer hotbar = player.getInventory().getHotbar();
            if (hotbar != null) {
                hotbar.setItemStackForSlot(context.getHeldItemSlot(), updated);
            }
            
            player.sendMessage(Message.raw("Compendium absorbed " + absorbed + " shard" + (absorbed == 1 ? "" : "s") + "!"));
            if (DynamicTooltipUtils.isAvailable()) {
                DynamicTooltipUtils.refreshAllPlayers();
            }
        } else {
            int complete = 0;
            int incomplete = 0;
            for (CompendiumEntry entry : data.values()) {
                if (ResonantRecipeUtils.getPatternStats(entry.pattern).isComplete()) {
                    complete++;
                } else {
                    incomplete++;
                }
            }
            player.sendMessage(Message.raw("Compendium contents: " + complete + " complete, " + incomplete + " incomplete recipes."));
            if (data.isEmpty()) {
                player.sendMessage(Message.raw("The compendium is currently empty. Right-click to absorb recipe shards in your inventory."));
            }
        }
    }

    private int absorbFromContainer(ItemContainer container, Map<String, CompendiumEntry> data) {
        if (container == null) return 0;
        int absorbedCount = 0;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (!ResonantRecipeUtils.isResonantRecipeItem(stack)) continue;

            String recipeName = ResonantRecipeUtils.getRecipeName(stack);
            if (recipeName == null || recipeName.isBlank()) continue;

            String pattern = ResonantRecipeUtils.getRecipePattern(stack);
            String usages = ResonantRecipeUtils.getRecipeUsages(stack);

            int qty = Math.max(1, stack.getQuantity());
            ResonantCompendiumUtils.addShardToCompendium(data, recipeName, pattern, usages, qty);

            container.removeItemStackFromSlot(slot, qty, false, false);
            absorbedCount += qty;
        }
        return absorbedCount;
    }

    private String getSummary(Map<String, CompendiumEntry> data) {
        int complete = 0;
        int incomplete = 0;
        for (CompendiumEntry entry : data.values()) {
            if (ResonantRecipeUtils.getPatternStats(entry.pattern).isComplete()) {
                complete++;
            } else {
                incomplete++;
            }
        }
        return "Stores " + complete + " complete and " + incomplete + " incomplete recipes.\nRight-click to absorb shards.";
    }

    private Player getPlayerFromContext(InteractionContext context) {
        Ref<EntityStore> owningEntity = context.getOwningEntity();
        if (owningEntity == null) return null;
        Store<EntityStore> store = owningEntity.getStore();
        if (store == null) return null;
        return store.getComponent(owningEntity, Player.getComponentType());
    }
}
