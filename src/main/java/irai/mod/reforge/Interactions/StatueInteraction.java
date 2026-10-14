package irai.mod.reforge.Interactions;

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

import irai.mod.reforge.Common.AscensionUtils;
import irai.mod.reforge.Common.CorruptionUtils;
import irai.mod.reforge.Config.AscensionConfig;
import irai.mod.reforge.Config.SFXConfig;
import irai.mod.reforge.UI.StatueConfirmationUI;
import irai.mod.reforge.UI.StatueConfirmationUI.ActionType;
import irai.mod.reforge.Util.DynamicTooltipUtils;
import irai.mod.reforge.Util.LangLoader;

public class StatueInteraction extends SimpleInteraction {

    public static final BuilderCodec<StatueInteraction> CODEC =
            BuilderCodec.builder(StatueInteraction.class, StatueInteraction::new, SimpleInteraction.CODEC).build();

    private static final SFXConfig sfxConfig = new SFXConfig();

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

        if (type != InteractionType.Use) {
            return;
        }

        short selectedSlot = player.getInventory() != null ? player.getInventory().getActiveHotbarSlot() : -1;
        ItemStack heldItem = null;
        if (selectedSlot >= 0) {
            ItemContainer hotbar = player.getInventory().getHotbar();
            if (hotbar != null) {
                heldItem = hotbar.getItemStack(selectedSlot);
            }
        }

        if (heldItem == null || heldItem.isEmpty()) {
            player.getPlayerRef().sendMessage(Message.raw(LangLoader.getUITranslation(player, "ascension.hold_equipment", "Hold a maxed refinement weapon or armor to ascend.")));
            return;
        }

        AscensionConfig config = AscensionUtils.getConfig();
        if (!config.isEnabled()) {
            player.getPlayerRef().sendMessage(Message.raw(LangLoader.getUITranslation(player, "ascension.disabled", "Ascension is currently disabled.")));
            return;
        }

        if (!ReforgeEquip.isWeapon(heldItem) && !ReforgeEquip.isArmor(heldItem)) {
            player.getPlayerRef().sendMessage(Message.raw(LangLoader.getUITranslation(player, "ascension.only_weapons_armor", "Only weapons and armor can be ascended.")));
            return;
        }

        if (!AscensionUtils.isMaxRefinement(heldItem)) {
            int currentLevel = ReforgeEquip.getLevelFromItem(heldItem);
            int maxLevel = ReforgeEquip.getMaxUpgradeLevel();
            player.getPlayerRef().sendMessage(Message.raw(LangLoader.getUITranslation(player, "ascension.max_refinement_required", "Equipment must be at max refinement level ({0}) to ascend. Current: {1}", maxLevel, currentLevel)));
            return;
        }

        if (CorruptionUtils.isCorrupted(heldItem)) {
            player.getPlayerRef().sendMessage(Message.raw(LangLoader.getUITranslation(player, "ascension.corrupted_blocked", "Corrupted equipment cannot be ascended.")));
            return;
        }

        int currentTier = AscensionUtils.getItemAscensionTier(heldItem);
        int maxTier = config.getMaxTier();
        if (currentTier >= maxTier) {
            player.getPlayerRef().sendMessage(Message.raw(LangLoader.getUITranslation(player, "ascension.max_tier_reached", "Equipment is already at max ascension tier ({0}).", AscensionUtils.getTierLabel(maxTier))));
            return;
        }

        int nextTier = AscensionUtils.getNextTier(heldItem);
        String requiredMaterial = AscensionUtils.getRequiredMaterial(heldItem);
        if (requiredMaterial == null || requiredMaterial.isBlank()) {
            player.getPlayerRef().sendMessage(Message.raw(LangLoader.getUITranslation(player, "ascension.no_material_configured", "No material configured for tier {0}.", AscensionUtils.getTierLabel(nextTier))));
            return;
        }

        if (!hasItem(player, requiredMaterial)) {
            player.getPlayerRef().sendMessage(Message.raw(LangLoader.getUITranslation(player, "ascension.need_material", "You need {0} to ascend to {1}.", requiredMaterial, AscensionUtils.getTierLabel(nextTier))));
            return;
        }

        if (StatueConfirmationUI.isAvailable()) {
            StatueConfirmationUI.open(player, ActionType.ASCENSION, heldItem, selectedSlot);
        } else {
            executeAscension(player, heldItem, selectedSlot);
        }
    }

    public static void executeAscension(Player player, ItemStack heldItem, short selectedSlot) {
        AscensionConfig config = AscensionUtils.getConfig();
        String requiredMaterial = AscensionUtils.getRequiredMaterial(heldItem);
        int nextTier = AscensionUtils.getNextTier(heldItem);

        removeItem(player, requiredMaterial);
        ItemStack ascended = AscensionUtils.ascendItem(heldItem);

        ItemContainer hotbar = player.getInventory().getHotbar();
        hotbar.setItemStackForSlot(selectedSlot, ascended);

        DynamicTooltipUtils.refreshPlayerTooltips(player.getPlayerRef());

        double ascensionMultiplier = AscensionUtils.getAscensionBaseMultiplier(ascended);
        int bonusPercent = (int) ((ascensionMultiplier - 1.0) * 100);

        sfxConfig.playSound(player, "SFX_Deployable_Totem_Heal_Effect_Local");
        sfxConfig.playSound(player, "SFX_Divine_Respawn");
        sfxConfig.playSound(player, "SFX_Sleep_Success");

        player.getPlayerRef().sendMessage(Message.raw(LangLoader.getUITranslation(player, "ascension.success", "Equipment ascended to {0}! Refinement reset with +{1}% base bonus.", AscensionUtils.getTierLabel(nextTier), bonusPercent)));
    }

    private static boolean hasItem(Player player, String itemId) {
        if (player == null || player.getInventory() == null || itemId == null || itemId.isBlank()) {
            return false;
        }
        ItemContainer hotbar = player.getInventory().getHotbar();
        ItemContainer storage = player.getInventory().getStorage();
        return containerHasItem(hotbar, itemId) || containerHasItem(storage, itemId);
    }

    private static boolean containerHasItem(ItemContainer container, String itemId) {
        if (container == null) {
            return false;
        }
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack != null && !stack.isEmpty() && itemId.equalsIgnoreCase(stack.getItemId())) {
                return true;
            }
        }
        return false;
    }

    private static void removeItem(Player player, String itemId) {
        if (player == null || player.getInventory() == null || itemId == null || itemId.isBlank()) {
            return;
        }
        ItemContainer hotbar = player.getInventory().getHotbar();
        ItemContainer storage = player.getInventory().getStorage();
        if (removeFromContainer(hotbar, itemId)) {
            return;
        }
        removeFromContainer(storage, itemId);
    }

    private static boolean removeFromContainer(ItemContainer container, String itemId) {
        if (container == null) {
            return false;
        }
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack != null && !stack.isEmpty() && itemId.equalsIgnoreCase(stack.getItemId())) {
                int qty = stack.getQuantity();
                if (qty <= 1) {
                    container.removeItemStackFromSlot(slot, 1, false, false);
                } else {
                    container.setItemStackForSlot(slot, stack.withQuantity(qty - 1));
                }
                return true;
            }
        }
        return false;
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
}
