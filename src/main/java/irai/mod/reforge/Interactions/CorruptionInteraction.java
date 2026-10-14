package irai.mod.reforge.Interactions;

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
import irai.mod.reforge.Common.CorruptionUtils;
import irai.mod.reforge.Config.CorruptionConfig;
import irai.mod.reforge.Config.SFXConfig;
import irai.mod.reforge.UI.StatueConfirmationUI;
import irai.mod.reforge.UI.StatueConfirmationUI.ActionType;
import irai.mod.reforge.Common.UI.UIInventoryUtils;
import irai.mod.reforge.Util.DynamicTooltipUtils;
import irai.mod.reforge.Util.InventoryItemUtils;
import irai.mod.reforge.Util.LangLoader;

import java.util.concurrent.ThreadLocalRandom;

public class CorruptionInteraction extends SimpleInteraction {

    public static final BuilderCodec<CorruptionInteraction> CODEC =
            BuilderCodec.builder(CorruptionInteraction.class, CorruptionInteraction::new, SimpleInteraction.CODEC).build();

    private static final SFXConfig sfxConfig = new SFXConfig();
    private static final String VOIDHEART_ID = "Ingredient_Voidheart";
    private static final String GHASTLY_ESSENCE_ID = "Ingredient_Ghastly_Essence";
    private static final String CONCENTRATED_VOID_ESSENCE_ID = "Ingredient_Void_Essence_Concentrated";
    private static final String ABYSS_ESSENCE_ID = "Ingredient_Abyss_Essence";
    private static final int VOIDHEART_TRIBUTE_COST = 1;
    private static final int GHASTLY_TRIBUTE_COST = 1;
    private static final int CONCENTRATED_VOID_TRIBUTE_COST = 100;

    @SuppressWarnings("removal")
    @Override
    protected void tick0(boolean firstRun, float time, InteractionType type,
                         InteractionContext context, CooldownHandler cooldownHandler) {
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

        ItemStack heldItem = context.getHeldItem();
        if (heldItem == null || heldItem.isEmpty()) {
            player.getPlayerRef().sendMessage(Message.raw(LangLoader.getUITranslation(player, "corruption.hold_equipment", "Hold a weapon or armor to corrupt.")));
            return;
        }

        if (VOIDHEART_ID.equalsIgnoreCase(heldItem.getItemId())) {
            executeAbyssTribute(player, context.getHeldItemSlot());
            return;
        }

        CorruptionConfig config = CorruptionUtils.getConfig();
        if (!config.isEnabled()) {
            player.getPlayerRef().sendMessage(Message.raw(LangLoader.getUITranslation(player, "corruption.disabled", "Corruption is currently disabled.")));
            return;
        }

        if (!ReforgeEquip.isWeapon(heldItem) && !ReforgeEquip.isArmor(heldItem)) {
            player.getPlayerRef().sendMessage(Message.raw(LangLoader.getUITranslation(player, "corruption.only_weapons_armor", "Only weapons and armor can be corrupted.")));
            return;
        }

        if (!CorruptionUtils.canCorrupt(heldItem)) {
            player.getPlayerRef().sendMessage(Message.raw(LangLoader.getUITranslation(player, "corruption.max_reached", "Equipment has reached max corruptions.")));
            return;
        }

        int corruptionCount = CorruptionUtils.getCorruptionCount(heldItem);
        String requiredMaterial = CorruptionUtils.getRequiredMaterial(corruptionCount);

        if (!hasItem(player, requiredMaterial)) {
            player.getPlayerRef().sendMessage(Message.raw(LangLoader.getUITranslation(player, "corruption.need_material", "You need {0} to corrupt this equipment.", requiredMaterial)));
            return;
        }

        if (StatueConfirmationUI.isAvailable()) {
            StatueConfirmationUI.open(player, ActionType.CORRUPTION, heldItem, context.getHeldItemSlot());
        } else {
            executeCorruption(player, heldItem, context.getHeldItemSlot());
        }
    }

    public static void executeCorruption(Player player, ItemStack heldItem, short slot) {
        CorruptionConfig config = CorruptionUtils.getConfig();
        int corruptionCount = CorruptionUtils.getCorruptionCount(heldItem);
        String requiredMaterial = CorruptionUtils.getRequiredMaterial(corruptionCount);

        boolean itemLost = false;
        if (corruptionCount >= 1) {
            if (ThreadLocalRandom.current().nextDouble() < config.getTier2ItemLoss()) {
                itemLost = true;
            }
        }

        if (itemLost) {
            removeItem(player, requiredMaterial);
            ItemStack empty = heldItem.withQuantity(0);
            player.getInventory().getHotbar().setItemStackForSlot(slot, empty);
            sfxConfig.playSound(player, "SFX_Discovery_Z4_Short");
            player.getPlayerRef().sendMessage(Message.raw(LangLoader.getUITranslation(player, "corruption.item_lost", "The corruption consumed your equipment!")));
            return;
        }

        removeItem(player, requiredMaterial);
        ItemStack corrupted = CorruptionUtils.corruptItem(heldItem);
        player.getInventory().getHotbar().setItemStackForSlot(slot, corrupted);
        DynamicTooltipUtils.refreshPlayerTooltips(player.getPlayerRef());

        sfxConfig.playSound(player, "SFX_Discovery_Z4_Short");

        if (CorruptionUtils.isMaxCorrupted(corrupted)) {
            player.getPlayerRef().sendMessage(Message.raw(LangLoader.getUITranslation(player, "corruption.fully_corrupted", "Equipment fully corrupted! Cannot be refined, socketed, or ascended.")));
        } else {
            player.getPlayerRef().sendMessage(Message.raw(LangLoader.getUITranslation(player, "corruption.success", "Equipment corrupted! Cannot be refined or socketed.")));
        }
    }

    private static void executeAbyssTribute(Player player, short heldSlot) {
        if (player == null || player.getInventory() == null) {
            return;
        }

        ItemContainer hotbar = player.getInventory().getHotbar();
        ItemStack heldStack = hotbar == null ? null : hotbar.getItemStack(heldSlot);
        if (heldStack == null || heldStack.isEmpty() || !VOIDHEART_ID.equalsIgnoreCase(heldStack.getItemId())) {
            player.getPlayerRef().sendMessage(Message.raw(tr(player, "corruption.abyss_tribute_hold_voidheart", "Hold a Voidheart before the statue to offer tribute.")));
            return;
        }

        int ghastlyCount = countItem(player, GHASTLY_ESSENCE_ID);
        int concentratedVoidCount = countItem(player, CONCENTRATED_VOID_ESSENCE_ID);
        if (ghastlyCount < GHASTLY_TRIBUTE_COST || concentratedVoidCount < CONCENTRATED_VOID_TRIBUTE_COST) {
            String title = tr(player, "corruption.abyss_tribute_missing_title", "Tribute Incomplete");
            String message = tr(
                    player,
                    "corruption.abyss_tribute_missing",
                    "Gaia rejects the offering. Tribute requires {0}x Ghastly Essence and {1}x Greater Void Essence.",
                    String.valueOf(GHASTLY_TRIBUTE_COST),
                    String.valueOf(CONCENTRATED_VOID_TRIBUTE_COST));
            String button = tr(player, "statue.ok", "OK");
            StatueConfirmationUI.openNotice(player, title, message, button);
            return;
        }

        boolean replaceHeldWithOutput = heldStack.getQuantity() <= VOIDHEART_TRIBUTE_COST;
        ItemStack abyssEssence = new ItemStack(ABYSS_ESSENCE_ID, 1);
        if (!replaceHeldWithOutput && !UIInventoryUtils.addItemToInventory(player, abyssEssence)) {
            player.getPlayerRef().sendMessage(Message.raw(tr(player, "corruption.abyss_tribute_no_space", "You need inventory space to receive Abyss Essence.")));
            return;
        }

        InventoryItemUtils.consumeItem(player, GHASTLY_ESSENCE_ID, GHASTLY_TRIBUTE_COST);
        InventoryItemUtils.consumeItem(player, CONCENTRATED_VOID_ESSENCE_ID, CONCENTRATED_VOID_TRIBUTE_COST);
        if (replaceHeldWithOutput) {
            hotbar.setItemStackForSlot(heldSlot, abyssEssence);
        } else {
            consumeHeldVoidheart(hotbar, heldSlot, heldStack);
        }
        sfxConfig.playSound(player, "SFX_Discovery_Z4_Short");
        player.getPlayerRef().sendMessage(Message.raw(tr(player, "corruption.abyss_tribute_success", "Gaia accepts your tribute. Abyss Essence received.")));
    }

    private static void consumeHeldVoidheart(ItemContainer hotbar, short heldSlot, ItemStack heldStack) {
        if (hotbar == null || heldStack == null || heldStack.isEmpty()) {
            return;
        }
        int remaining = heldStack.getQuantity() - VOIDHEART_TRIBUTE_COST;
        if (remaining <= 0) {
            hotbar.setItemStackForSlot(heldSlot, null);
        } else {
            hotbar.setItemStackForSlot(heldSlot, heldStack.withQuantity(remaining));
        }
    }

    private static String tr(Player player, String key, String fallback, Object... params) {
        String template = LangLoader.getUITranslation(player, key);
        if (template == null || template.equals(key)) {
            template = fallback;
        }
        for (int i = 0; i < params.length; i++) {
            template = template.replace("{" + i + "}", String.valueOf(params[i]));
        }
        return template;
    }

    private static int countItem(Player player, String itemId) {
        if (player == null || player.getInventory() == null || itemId == null || itemId.isBlank()) {
            return 0;
        }
        return countInContainer(player.getInventory().getHotbar(), itemId)
                + countInContainer(player.getInventory().getStorage(), itemId);
    }

    private static int countInContainer(ItemContainer container, String itemId) {
        if (container == null) {
            return 0;
        }
        int total = 0;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack != null && !stack.isEmpty() && itemId.equalsIgnoreCase(stack.getItemId())) {
                total += stack.getQuantity();
            }
        }
        return total;
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
