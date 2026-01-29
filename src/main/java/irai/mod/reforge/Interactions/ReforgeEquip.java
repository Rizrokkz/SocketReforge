package irai.mod.reforge.Interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import irai.mod.reforge.Config.SFXConfig;
import irai.mod.reforge.UI.WeaponTooltip;
import irai.mod.reforge.Systems.WeaponUpgradeTracker;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.util.regex.Pattern;

/**
 * Flexible weapon reforging system with player-based tracking and custom tooltips.
 * Works with ANY weapon from ANY mod - no item ID conflicts!
 */
@SuppressWarnings("removal")
public class ReforgeEquip extends SimpleInteraction {

    public static final BuilderCodec<ReforgeEquip> CODEC =
            BuilderCodec.builder(ReforgeEquip.class, ReforgeEquip::new, SimpleInteraction.CODEC).build();

    private static final String MATERIAL_ID = "Ingredient_Bar_Iron";
    private static final int MATERIAL_COST = 1;
    private static final int MAX_UPGRADE_LEVEL = 3;
    private static final double BREAK_CHANCE_AT_MAX = 0.20;
    private static final Pattern WEAPON_PATTERN = Pattern.compile(".*[Ww]eapon.*");

    private static final double[][] REFORGE_WEIGHTS = {
            {0.00, 0.65, 0.34, 0.01},   // 0 → 1
            {0.35, 0.45, 0.19, 0.01},   // 1 → 2
            {0.60, 0.30, 0.095, 0.005}, // 2 → 3
    };

    private SFXConfig sfxConfig;

    public ReforgeEquip() {
        this.sfxConfig = new SFXConfig();
    }

    public void setSfxConfig(SFXConfig sfxConfig) {
        this.sfxConfig = sfxConfig;
    }

    @Override
    protected void tick0(boolean firstRun, float time, @NonNullDecl InteractionType type,
                         @NonNullDecl InteractionContext context, @NonNullDecl CooldownHandler cooldownHandler) {
        super.tick0(firstRun, time, type, context, cooldownHandler);
        if (!firstRun || type != InteractionType.Use) return;

        Player player = getPlayerFromContext(context);
        if (player == null) return;
        //if (!isValidReforgebench(context)) return;

        ItemStack heldItem = context.getHeldItem();
        if (heldItem == null) return;

        processReforge(player, heldItem, context);
    }

    private void processReforge(Player player, ItemStack heldItem, InteractionContext context) {
        if (!isWeapon(heldItem.getItemId())) {
            player.sendMessage(Message.raw("This item cannot be reforged"));
            return;
        }

        short slot = context.getHeldItemSlot();
        int currentLevel = WeaponUpgradeTracker.getUpgradeLevel(player, heldItem,slot);

        if (currentLevel >= MAX_UPGRADE_LEVEL) {
            player.sendMessage(Message.raw("Weapon is already at max level"));
            WeaponTooltip.showDetailedStats(player, heldItem, slot);
            return;
        }

        if (!hasEnoughMaterial(player, MATERIAL_ID, MATERIAL_COST)) {
            player.sendMessage(Message.raw("Not enough Iron Bars (need " + MATERIAL_COST + ")"));
            return;
        }

        sfxConfig.playReforgeStart(player);

        if (!consumeMaterial(player, MATERIAL_ID, MATERIAL_COST)) {
            player.sendMessage(Message.raw("Failed to consume materials"));
            return;
        }

        if (currentLevel == 2 && Math.random() < BREAK_CHANCE_AT_MAX) {
            player.getInventory().getHotbar().removeItemStackFromSlot(slot, 1, false, false);
            WeaponUpgradeTracker.removeWeapon(player, heldItem,slot);
            sfxConfig.playShatter(player);
            WeaponTooltip.showWeaponShatter(player);
            return;
        }

        ReforgeOutcome outcome = rollReforgeOutcome(currentLevel);
        int newLevel = Math.max(0, Math.min(currentLevel + outcome.levelChange, MAX_UPGRADE_LEVEL));
        WeaponUpgradeTracker.setUpgradeLevel(player, heldItem,newLevel);
        playOutcomeSound(player, outcome);
        showOutcomeFeedback(player, outcome, currentLevel, newLevel, heldItem, slot);
    }

    private void showOutcomeFeedback(Player player, ReforgeOutcome outcome, int oldLevel, int newLevel, ItemStack weapon, short slot) {
        switch (outcome.type) {
            case DEGRADE -> WeaponTooltip.showUpgradeFailure(player, oldLevel, newLevel);
            case SAME -> {
                player.sendMessage(Message.raw("--------------------"));
                player.sendMessage(Message.raw("   Refine Failed    "));
                player.sendMessage(Message.raw("--------------------"));
                WeaponTooltip.showCompactTooltip(player, weapon, slot);
            }
            case UPGRADE -> {
                WeaponTooltip.showUpgradeSuccess(player, oldLevel, newLevel);
                WeaponTooltip.showDetailedStats(player, weapon, slot);
            }
            case JACKPOT -> {
                player.sendMessage(Message.raw("**** JACKPOT! ****"));
                WeaponTooltip.showDetailedStats(player, weapon, slot);
            }
        }
    }

    private void playOutcomeSound(Player player, ReforgeOutcome outcome) {
        switch (outcome.type) {
            case DEGRADE -> sfxConfig.playFail(player);
            case SAME -> sfxConfig.playNoChange(player);
            case UPGRADE -> sfxConfig.playSuccess(player);
            case JACKPOT -> sfxConfig.playJackpot(player);
        }
    }

    private boolean isWeapon(String itemId) {
        return itemId != null && WEAPON_PATTERN.matcher(itemId).matches();
    }

    private boolean isValidReforgebench(InteractionContext context) {
        if (sfxConfig == null || sfxConfig.getBenches() == null) return false;
        BlockPosition target = context.getTargetBlock();
        if (target == null) return false;
        Player player = getPlayerFromContext(context);
        if (player == null) return false;
        World world = player.getWorld();
        if (world == null) return false;
        Ref<ChunkStore> chunk = BlockModule.getBlockEntity(world, target.x, target.y, target.z);
        if (chunk == null) return false;
        BlockState state = BlockState.getBlockState(chunk, chunk.getStore());
        String blockId = state.getBlockType().getId();
        for (String bench : sfxConfig.getBenches()) {
            if (blockId.equals(bench)) return true;
        }
        return false;
    }

    private ReforgeOutcome rollReforgeOutcome(int currentLevel) {
        double[] weights = REFORGE_WEIGHTS[Math.min(currentLevel, REFORGE_WEIGHTS.length - 1)];
        double random = Math.random(), cumulative = 0.0;
        cumulative += weights[0];
        if (random < cumulative) return new ReforgeOutcome(-1, OutcomeType.DEGRADE);
        cumulative += weights[1];
        if (random < cumulative) return new ReforgeOutcome(0, OutcomeType.SAME);
        cumulative += weights[2];
        if (random < cumulative) return new ReforgeOutcome(1, OutcomeType.UPGRADE);
        return new ReforgeOutcome(2, OutcomeType.JACKPOT);
    }

    private Player getPlayerFromContext(InteractionContext context) {
        Ref<EntityStore> owningEntity = context.getOwningEntity();
        if (owningEntity == null) return null;
        Store<EntityStore> store = owningEntity.getStore();
        if (store == null) return null;
        return store.getComponent(owningEntity, Player.getComponentType());
    }

    private boolean hasEnoughMaterial(Player player, String itemId, int requiredAmount) {
        int totalFound = countItemInContainer(player.getInventory().getStorage(), itemId) +
                countItemInContainer(player.getInventory().getHotbar(), itemId);
        return totalFound >= requiredAmount;
    }

    private int countItemInContainer(ItemContainer container, String itemId) {
        int count = 0;
        for (short i = 0; i < container.getCapacity(); i++) {
            ItemStack stack = container.getItemStack(i);
            if (stack != null && stack.getItemId().equalsIgnoreCase(itemId)) {
                count += stack.getQuantity();
            }
        }
        return count;
    }

    private boolean consumeMaterial(Player player, String itemId, int amount) {
        return consumeFromContainer(player.getInventory().getStorage(), itemId, amount) ||
                consumeFromContainer(player.getInventory().getHotbar(), itemId, amount);
    }

    private boolean consumeFromContainer(ItemContainer container, String itemId, int amount) {
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack != null && stack.getItemId().equalsIgnoreCase(itemId) && stack.getQuantity() >= amount) {
                container.removeItemStackFromSlot(slot, amount, false, false);
                return true;
            }
        }
        return false;
    }

    private static class ReforgeOutcome {
        final int levelChange;
        final OutcomeType type;
        ReforgeOutcome(int levelChange, OutcomeType type) {
            this.levelChange = levelChange;
            this.type = type;
        }
    }

    private enum OutcomeType {
        DEGRADE, SAME, UPGRADE, JACKPOT
    }
}