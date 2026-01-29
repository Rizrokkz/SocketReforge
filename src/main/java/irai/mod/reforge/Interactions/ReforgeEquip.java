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
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles weapon reforging interactions at a Reforgebench.
 * Features: Dynamic upgrades, degradation chance, breaking chance, and configurable sound effects.
 */
@SuppressWarnings("removal")
public class ReforgeEquip extends SimpleInteraction {

    public static final BuilderCodec<ReforgeEquip> CODEC =
            BuilderCodec.builder(ReforgeEquip.class, ReforgeEquip::new, SimpleInteraction.CODEC).build();

    // ══════════════════════════════════════════════════════════════════════════════
    // Configuration Constants
    // ══════════════════════════════════════════════════════════════════════════════

    private static final String MATERIAL_ID = "Ingredient_Bar_Iron";
    private static final int MATERIAL_COST = 1;
    private static final int MAX_UPGRADE_LEVEL = 3;
    private static final double BREAK_CHANCE_AT_MAX = 0.20;

    // Weapon patterns
    private static final Pattern WEAPON_PATTERN = Pattern.compile("^(.*)Weapon(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern UPGRADE_LEVEL_PATTERN = Pattern.compile("^(.+?)(\\d+)$");

    // Damage multipliers
    private static final double[] DAMAGE_MULTIPLIERS = {
            1.0,   // Base
            1.10,  // +1 (10%)
            1.15,  // +2 (15%)
            1.25   // +3 (25%)
    };

    // [degrade, same, +1, +2]
    private static final double[][] REFORGE_WEIGHTS = {
            {0.00, 0.65, 0.34, 0.01},   // 0 → 1
            {0.35, 0.45, 0.19, 0.01},   // 1 → 2
            {0.60, 0.30, 0.095, 0.005}, // 2 → 3
    };

    // SFX Config instance
    private SFXConfig sfxConfig;

    // ══════════════════════════════════════════════════════════════════════════════
    // Constructor
    // ══════════════════════════════════════════════════════════════════════════════

    public ReforgeEquip() {
        // Initialize with default SFX config
        this.sfxConfig = new SFXConfig();
    }

    /**
     * Sets the SFX configuration (called by plugin during setup).
     */
    public void setSfxConfig(SFXConfig sfxConfig) {
        this.sfxConfig = sfxConfig;
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Main Interaction Logic
    // ══════════════════════════════════════════════════════════════════════════════

    @Override
    protected void tick0(boolean firstRun,
                         float time,
                         @NonNullDecl InteractionType type,
                         @NonNullDecl InteractionContext context,
                         @NonNullDecl CooldownHandler cooldownHandler) {
        super.tick0(firstRun, time, type, context, cooldownHandler);

        if (!firstRun || type != InteractionType.Use) {
            return;
        }

        Player player = getPlayerFromContext(context);
        if (player == null) {
            return;
        }

        // Validate reforgebench block
        //if (!isValidReforgebench(context)) {
        //    return;
        //}

        ItemStack heldItem = context.getHeldItem();
        if (heldItem == null) {
            return;
        }

        processReforge(player, heldItem, context);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Core Reforge Logic
    // ══════════════════════════════════════════════════════════════════════════════

    private void processReforge(Player player, ItemStack heldItem, InteractionContext context) {
        String heldItemId = heldItem.getItemId();

        if (!isWeapon(heldItemId)) {
            player.sendMessage(Message.raw("This item cannot be reforged (not a weapon)"));
            return;
        }

        WeaponInfo weaponInfo = parseWeaponInfo(heldItemId);

        if (weaponInfo.currentLevel >= MAX_UPGRADE_LEVEL) {
            player.sendMessage(Message.raw("Weapon is already at maximum upgrade level (+3)"));
            return;
        }

        if (!hasEnoughMaterial(player, MATERIAL_ID, MATERIAL_COST)) {
            player.sendMessage(Message.raw("Not enough Iron Bars (need " + MATERIAL_COST + ")"));
            return;
        }

        // Play reforge start sound
        sfxConfig.playReforgeStart(player);

        // Consume materials
        if (!consumeMaterial(player, MATERIAL_ID, MATERIAL_COST)) {
            player.sendMessage(Message.raw("Failed to consume materials"));
            return;
        }

        // Check for weapon breaking at +2→+3
        if (weaponInfo.currentLevel == 2) {
            if (Math.random() < BREAK_CHANCE_AT_MAX) {
                // WEAPON BREAKS!
                short slot = context.getHeldItemSlot();
                player.getInventory().getHotbar().removeItemStackFromSlot(slot, 1, false, false);

                // Play dramatic breaking sound
                sfxConfig.playShatter(player);

                player.sendMessage(Message.raw("XXX---WEAPON SHATTERED!---XXX"));
                player.sendMessage(Message.raw("The weapon couldn't handle the power and broke into pieces..."));
                player.sendMessage(Message.raw(MATERIAL_COST + " Iron Bars were consumed"));
                return;
            }
        }

        // Roll for outcome
        ReforgeOutcome outcome = rollReforgeOutcome(weaponInfo.currentLevel);
        int newLevel = Math.max(0, Math.min(weaponInfo.currentLevel + outcome.levelChange, MAX_UPGRADE_LEVEL));

        // Create new item
        String newItemId = generateUpgradedItemId(weaponInfo.baseName, newLevel);
        ItemStack reforgedItem = createReforgedItem(newItemId, heldItem, newLevel);

        // Replace item
        replaceHeldItem(player, context, reforgedItem);

        // Play outcome sound and send message
        playOutcomeSound(player, outcome);
        sendOutcomeMessage(player, outcome, weaponInfo.currentLevel, newLevel);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Sound System (delegates to SFXConfig)
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Plays the appropriate sound for each reforge outcome.
     */
    private void playOutcomeSound(Player player, ReforgeOutcome outcome) {
        switch (outcome.type) {
            case DEGRADE:
                sfxConfig.playFail(player);
                break;
            case SAME:
                sfxConfig.playNoChange(player);
                break;
            case UPGRADE:
                sfxConfig.playSuccess(player);
                break;
            case JACKPOT:
                sfxConfig.playJackpot(player);
                break;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Validation Helpers
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Validates that the target block is a valid reforge bench.
     */
    private boolean isValidReforgebench(InteractionContext context) {
        if (sfxConfig == null || sfxConfig.getBenches() == null) {
            return false;
        }

        BlockPosition target = context.getTargetBlock();
        if (target == null) {
            return false;
        }

        Player player = getPlayerFromContext(context);
        if (player == null) {
            return false;
        }

        World world = player.getWorld();
        if (world == null) {
            return false;
        }

        Ref<ChunkStore> chunk = BlockModule.getBlockEntity(world, target.x, target.y, target.z);
        if (chunk == null) {
            return false;
        }

        BlockState state = BlockState.getBlockState(chunk, chunk.getStore());
        String blockId = state.getBlockType().getId();

        // Check if block ID matches any configured bench
        for (String bench : sfxConfig.getBenches()) {
            if (blockId.equals(bench)) {
                return true;
            }
        }

        return false;
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Reforge Outcome System
    // ══════════════════════════════════════════════════════════════════════════════

    private ReforgeOutcome rollReforgeOutcome(int currentLevel) {
        double[] weights = REFORGE_WEIGHTS[Math.min(currentLevel, REFORGE_WEIGHTS.length - 1)];
        double random = Math.random();
        double cumulative = 0.0;

        cumulative += weights[0];
        if (random < cumulative) return new ReforgeOutcome(-1, OutcomeType.DEGRADE);

        cumulative += weights[1];
        if (random < cumulative) return new ReforgeOutcome(0, OutcomeType.SAME);

        cumulative += weights[2];
        if (random < cumulative) return new ReforgeOutcome(1, OutcomeType.UPGRADE);

        return new ReforgeOutcome(2, OutcomeType.JACKPOT);
    }

    private void sendOutcomeMessage(Player player, ReforgeOutcome outcome, int oldLevel, int newLevel) {
        switch (outcome.type) {
            case DEGRADE:
                player.sendMessage(Message.raw("Reforge Failed! The weapon degraded during the refining process..."));
                player.sendMessage(Message.raw("Level: +" + oldLevel + " ->+ " + newLevel));
                player.sendMessage(Message.raw(MATERIAL_COST + " Iron Bars were consumed"));
                break;
            case SAME:
                player.sendMessage(Message.raw("Refine Failed!." + MATERIAL_COST + " Iron Bars were consumed"));
                break;
            case UPGRADE:
                player.sendMessage(Message.raw("Refine Success!"+"Level: +" + oldLevel + " ->+ " + newLevel));
                player.sendMessage(Message.raw(MATERIAL_COST + " Iron Bars were consumed"));
                break;
            case JACKPOT:
                player.sendMessage(Message.raw("//// JACKPOT! ////" + "Level: +" + oldLevel + " ->+ " + newLevel + " (+2!)"));
                player.sendMessage(Message.raw("**The weapon surged with incredible power!**"));
                break;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ══════════════════════════════════════════════════════════════════════════════

    private boolean isWeapon(String itemId) {
        return WEAPON_PATTERN.matcher(itemId).matches();
    }

    private WeaponInfo parseWeaponInfo(String itemId) {
        Matcher levelMatcher = UPGRADE_LEVEL_PATTERN.matcher(itemId);
        if (levelMatcher.matches()) {
            String baseName = levelMatcher.group(1);
            int currentLevel = Integer.parseInt(levelMatcher.group(2));
            return new WeaponInfo(baseName, currentLevel);
        }
        return new WeaponInfo(itemId, 0);
    }

    private String generateUpgradedItemId(String baseName, int level) {
        return level == 0 ? baseName : baseName + level;
    }

    private ItemStack createReforgedItem(String newItemId, ItemStack originalItem, int upgradeLevel) {
        return new ItemStack(newItemId, 1, originalItem.getDurability(), originalItem.getMaxDurability(), null);
    }

    private void replaceHeldItem(Player player, InteractionContext context, ItemStack newItem) {
        short slot = context.getHeldItemSlot();
        var hotbar = player.getInventory().getHotbar();
        hotbar.removeItemStackFromSlot(slot, 1, false, false);
        hotbar.addItemStackToSlot(slot, newItem);
    }

    private Player getPlayerFromContext(InteractionContext context) {
        Ref<EntityStore> owningEntity = context.getOwningEntity();
        if (owningEntity == null) return null;
        Store<EntityStore> store = owningEntity.getStore();
        if (store == null) return null;
        return store.getComponent(owningEntity, Player.getComponentType());
    }

    private boolean hasEnoughMaterial(Player player, String itemId, int requiredAmount) {
        int totalFound = 0;
        totalFound += countItemInContainer(player.getInventory().getStorage(), itemId);
        totalFound += countItemInContainer(player.getInventory().getHotbar(), itemId);
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
        if (consumeFromContainer(player.getInventory().getStorage(), itemId, amount)) return true;
        return consumeFromContainer(player.getInventory().getHotbar(), itemId, amount);
    }

    private boolean consumeFromContainer(ItemContainer container, String itemId, int amount) {
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack == null) continue;
            if (stack.getItemId().equalsIgnoreCase(itemId) && stack.getQuantity() >= amount) {
                container.removeItemStackFromSlot(slot, amount, false, false);
                return true;
            }
        }
        return false;
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Inner Classes
    // ══════════════════════════════════════════════════════════════════════════════

    private static class WeaponInfo {
        final String baseName;
        final int currentLevel;
        WeaponInfo(String baseName, int currentLevel) {
            this.baseName = baseName;
            this.currentLevel = currentLevel;
        }
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