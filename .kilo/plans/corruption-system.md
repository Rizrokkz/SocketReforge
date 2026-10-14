# Equipment Corruption System Implementation Plan

## Overview
Add a corruption system via `Furniture_Temple_Dark_Statue_Gaia` with two modes: Try and Ignore.

## Corruption Mechanics

### Two Modes:
1. **Try Mode** - For each stat on the item, 50% chance to apply an effect:
   - Stat Increase: +10% to +50%
   - Stat Decrease: -10% to -50%
   - Stat Invert: becomes negative
   - Stat Remove: removed entirely
   - Add New Stat: new stat added
   - Resonant Stat: random resonant stat added

2. **Ignore Mode** - No change (but still counts as corruption)

### Costs:
- 1st corruption: Void_Heart
- 2nd corruption: Abyss_Essence (50% item loss chance)

### Post-Corruption Restrictions:
- Cannot refine
- Cannot punch sockets (lore/essence)
- Cannot modify socketed essences
- Cannot remove lore gems/spirits (can still feed lore)

## Files to Create

### 1. CorruptionConfig.java
**Path:** `src/main/java/irai/mod/reforge/Config/CorruptionConfig.java`

```java
package irai.mod.reforge.Config;

import java.util.HashMap;
import java.util.Map;

import static com.hypixel.hytale.codec.Codec.BOOLEAN;
import static com.hypixel.hytale.codec.Codec.DOUBLE;
import static com.hypixel.hytale.codec.Codec.INTEGER;
import static com.hypixel.hytale.codec.Codec.STRING;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

@SuppressWarnings("removal")
public class CorruptionConfig implements ConfigDefaultInjector {
    private static final boolean DEFAULT_ENABLED = true;
    private static final int DEFAULT_MAX_CORRUPTIONS = 2;
    private static final String DEFAULT_TIER_1_COST = "Void_Heart";
    private static final String DEFAULT_TIER_2_COST = "Abyss_Essence";
    private static final double DEFAULT_TIER_2_ITEM_LOSS = 0.50;
    private static final double DEFAULT_STAT_INCREASE_CHANCE = 0.35;
    private static final double DEFAULT_STAT_DECREASE_CHANCE = 0.20;
    private static final double DEFAULT_STAT_INVERT_CHANCE = 0.10;
    private static final double DEFAULT_STAT_REMOVE_CHANCE = 0.10;
    private static final double DEFAULT_ADD_NEW_STAT_CHANCE = 0.10;
    private static final double DEFAULT_RESONANT_STAT_CHANCE = 0.15;
    private static final double DEFAULT_MIN_STAT_MODIFIER = 0.10;
    private static final double DEFAULT_MAX_STAT_MODIFIER = 0.50;

    public static final BuilderCodec<CorruptionConfig> CODEC = BuilderCodec.<CorruptionConfig>builder(CorruptionConfig.class, CorruptionConfig::new)
            .append(new KeyedCodec<>("ENABLED", BOOLEAN),
                    (config, value) -> config.enabled = value != null ? value : DEFAULT_ENABLED,
                    config -> config.enabled).add()
            .append(new KeyedCodec<>("MAX_CORRUPTIONS", INTEGER),
                    (config, value) -> config.maxCorruptions = value != null ? Math.max(1, Math.min(5, value)) : DEFAULT_MAX_CORRUPTIONS,
                    config -> config.maxCorruptions).add()
            .append(new KeyedCodec<>("TIER_1_COST", STRING),
                    (config, value) -> config.tier1Cost = value != null && !value.isBlank() ? value : DEFAULT_TIER_1_COST,
                    config -> config.tier1Cost).add()
            .append(new KeyedCodec<>("TIER_2_COST", STRING),
                    (config, value) -> config.tier2Cost = value != null && !value.isBlank() ? value : DEFAULT_TIER_2_COST,
                    config -> config.tier2Cost).add()
            .append(new KeyedCodec<>("TIER_2_ITEM_LOSS", DOUBLE),
                    (config, value) -> config.tier2ItemLoss = value != null ? Math.max(0.0, Math.min(1.0, value)) : DEFAULT_TIER_2_ITEM_LOSS,
                    config -> config.tier2ItemLoss).add()
            .append(new KeyedCodec<>("STAT_INCREASE_CHANCE", DOUBLE),
                    (config, value) -> config.statIncreaseChance = value != null ? Math.max(0.0, Math.min(1.0, value)) : DEFAULT_STAT_INCREASE_CHANCE,
                    config -> config.statIncreaseChance).add()
            .append(new KeyedCodec<>("STAT_DECREASE_CHANCE", DOUBLE),
                    (config, value) -> config.statDecreaseChance = value != null ? Math.max(0.0, Math.min(1.0, value)) : DEFAULT_STAT_DECREASE_CHANCE,
                    config -> config.statDecreaseChance).add()
            .append(new KeyedCodec<>("STAT_INVERT_CHANCE", DOUBLE),
                    (config, value) -> config.statInvertChance = value != null ? Math.max(0.0, Math.min(1.0, value)) : DEFAULT_STAT_INVERT_CHANCE,
                    config -> config.statInvertChance).add()
            .append(new KeyedCodec<>("STAT_REMOVE_CHANCE", DOUBLE),
                    (config, value) -> config.statRemoveChance = value != null ? Math.max(0.0, Math.min(1.0, value)) : DEFAULT_STAT_REMOVE_CHANCE,
                    config -> config.statRemoveChance).add()
            .append(new KeyedCodec<>("ADD_NEW_STAT_CHANCE", DOUBLE),
                    (config, value) -> config.addNewStatChance = value != null ? Math.max(0.0, Math.min(1.0, value)) : DEFAULT_ADD_NEW_STAT_CHANCE,
                    config -> config.addNewStatChance).add()
            .append(new KeyedCodec<>("RESONANT_STAT_CHANCE", DOUBLE),
                    (config, value) -> config.resonantStatChance = value != null ? Math.max(0.0, Math.min(1.0, value)) : DEFAULT_RESONANT_STAT_CHANCE,
                    config -> config.resonantStatChance).add()
            .append(new KeyedCodec<>("MIN_STAT_MODIFIER", DOUBLE),
                    (config, value) -> config.minStatModifier = value != null ? Math.max(0.0, Math.min(1.0, value)) : DEFAULT_MIN_STAT_MODIFIER,
                    config -> config.minStatModifier).add()
            .append(new KeyedCodec<>("MAX_STAT_MODIFIER", DOUBLE),
                    (config, value) -> config.maxStatModifier = value != null ? Math.max(0.0, Math.min(1.0, value)) : DEFAULT_MAX_STAT_MODIFIER,
                    config -> config.maxStatModifier).add()
            .build();

    private boolean enabled = DEFAULT_ENABLED;
    private int maxCorruptions = DEFAULT_MAX_CORRUPTIONS;
    private String tier1Cost = DEFAULT_TIER_1_COST;
    private String tier2Cost = DEFAULT_TIER_2_COST;
    private double tier2ItemLoss = DEFAULT_TIER_2_ITEM_LOSS;
    private double statIncreaseChance = DEFAULT_STAT_INCREASE_CHANCE;
    private double statDecreaseChance = DEFAULT_STAT_DECREASE_CHANCE;
    private double statInvertChance = DEFAULT_STAT_INVERT_CHANCE;
    private double statRemoveChance = DEFAULT_STAT_REMOVE_CHANCE;
    private double addNewStatChance = DEFAULT_ADD_NEW_STAT_CHANCE;
    private double resonantStatChance = DEFAULT_RESONANT_STAT_CHANCE;
    private double minStatModifier = DEFAULT_MIN_STAT_MODIFIER;
    private double maxStatModifier = DEFAULT_MAX_STAT_MODIFIER;

    // Getters and setters...
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getMaxCorruptions() { return maxCorruptions; }
    public void setMaxCorruptions(int maxCorruptions) { this.maxCorruptions = Math.max(1, Math.min(5, maxCorruptions)); }
    public String getTier1Cost() { return tier1Cost; }
    public void setTier1Cost(String tier1Cost) { this.tier1Cost = tier1Cost; }
    public String getTier2Cost() { return tier2Cost; }
    public void setTier2Cost(String tier2Cost) { this.tier2Cost = tier2Cost; }
    public double getTier2ItemLoss() { return tier2ItemLoss; }
    public void setTier2ItemLoss(double tier2ItemLoss) { this.tier2ItemLoss = Math.max(0.0, Math.min(1.0, tier2ItemLoss)); }
    public double getStatIncreaseChance() { return statIncreaseChance; }
    public void setStatIncreaseChance(double v) { this.statIncreaseChance = Math.max(0.0, Math.min(1.0, v)); }
    public double getStatDecreaseChance() { return statDecreaseChance; }
    public void setStatDecreaseChance(double v) { this.statDecreaseChance = Math.max(0.0, Math.min(1.0, v)); }
    public double getStatInvertChance() { return statInvertChance; }
    public void setStatInvertChance(double v) { this.statInvertChance = Math.max(0.0, Math.min(1.0, v)); }
    public double getStatRemoveChance() { return statRemoveChance; }
    public void setStatRemoveChance(double v) { this.statRemoveChance = Math.max(0.0, Math.min(1.0, v)); }
    public double getAddNewStatChance() { return addNewStatChance; }
    public void setAddNewStatChance(double v) { this.addNewStatChance = Math.max(0.0, Math.min(1.0, v)); }
    public double getResonantStatChance() { return resonantStatChance; }
    public void setResonantStatChance(double v) { this.resonantStatChance = Math.max(0.0, Math.min(1.0, v)); }
    public double getMinStatModifier() { return minStatModifier; }
    public void setMinStatModifier(double v) { this.minStatModifier = Math.max(0.0, Math.min(1.0, v)); }
    public double getMaxStatModifier() { return maxStatModifier; }
    public void setMaxStatModifier(double v) { this.maxStatModifier = Math.max(0.0, Math.min(1.0, v)); }

    @Override
    public boolean injectMissingDefaults() { return false; }
}
```

### 2. CorruptionUtils.java
**Path:** `src/main/java/irai/mod/reforge/Common/CorruptionUtils.java`

```java
package irai.mod.reforge.Common;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import irai.mod.reforge.Config.CorruptionConfig;
import irai.mod.reforge.Util.MetadataKeys;

import java.util.concurrent.ThreadLocalRandom;

public final class CorruptionUtils {

    private static volatile CorruptionConfig config = new CorruptionConfig();

    private CorruptionUtils() {}

    public static void setConfig(CorruptionConfig newConfig) {
        config = newConfig == null ? new CorruptionConfig() : newConfig;
    }

    public static CorruptionConfig getConfig() {
        return config;
    }

    public static boolean isCorrupted(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        Boolean corrupted = item.getFromMetadataOrNull(MetadataKeys.CORRUPTED, Codec.BOOLEAN);
        return corrupted != null && corrupted;
    }

    public static int getCorruptionCount(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return 0;
        }
        Integer count = item.getFromMetadataOrNull(MetadataKeys.CORRUPTION_COUNT, Codec.INTEGER);
        return count == null ? 0 : Math.max(0, count);
    }

    public static boolean canCorrupt(ItemStack item) {
        if (!config.isEnabled()) {
            return false;
        }
        if (item == null || item.isEmpty()) {
            return false;
        }
        int count = getCorruptionCount(item);
        return count < config.getMaxCorruptions();
    }

    public static ItemStack corruptItem(ItemStack item) {
        if (!canCorrupt(item)) {
            return item;
        }

        int corruptionCount = getCorruptionCount(item);
        int newCount = corruptionCount + 1;
        boolean isSecondCorruption = newCount >= 2;

        // 50% chance to ignore (no change)
        boolean ignore = ThreadLocalRandom.current().nextDouble() < 0.50;

        ItemStack result = item;
        if (!ignore) {
            // Try mode: apply random effects
            result = applyCorruptionEffects(result);
        }

        // Update corruption metadata
        result = result.withMetadata(MetadataKeys.CORRUPTION_COUNT, Codec.INTEGER, newCount);
        result = result.withMetadata(MetadataKeys.CORRUPTED, Codec.BOOLEAN, true);

        return result;
    }

    private static ItemStack applyCorruptionEffects(ItemStack item) {
        // Get stat modifier range
        double minMod = config.getMinStatModifier();
        double maxMod = config.getMaxStatModifier();
        double modifier = minMod + ThreadLocalRandom.current().nextDouble() * (maxMod - minMod);

        // Roll for outcome
        double roll = ThreadLocalRandom.current().nextDouble();
        double cumulative = 0.0;

        cumulative += config.getStatIncreaseChance();
        if (roll < cumulative) {
            // Stat Increase
            return applyStatIncrease(item, modifier);
        }

        cumulative += config.getStatDecreaseChance();
        if (roll < cumulative) {
            // Stat Decrease
            return applyStatDecrease(item, modifier);
        }

        cumulative += config.getStatInvertChance();
        if (roll < cumulative) {
            // Stat Invert
            return applyStatInvert(item);
        }

        cumulative += config.getStatRemoveChance();
        if (roll < cumulative) {
            // Stat Remove
            return applyStatRemove(item);
        }

        cumulative += config.getAddNewStatChance();
        if (roll < cumulative) {
            // Add New Stat
            return applyAddNewStat(item);
        }

        cumulative += config.getResonantStatChance();
        if (roll < cumulative) {
            // Resonant Stat
            return applyResonantStat(item);
        }

        // Fallback: no change
        return item;
    }

    private static ItemStack applyStatIncrease(ItemStack item, double modifier) {
        // Implementation depends on how stats are stored
        // For now, return item unchanged
        return item;
    }

    private static ItemStack applyStatDecrease(ItemStack item, double modifier) {
        return item;
    }

    private static ItemStack applyStatInvert(ItemStack item) {
        return item;
    }

    private static ItemStack applyStatRemove(ItemStack item) {
        return item;
    }

    private static ItemStack applyAddNewStat(ItemStack item) {
        return item;
    }

    private static ItemStack applyResonantStat(ItemStack item) {
        return item;
    }

    public static String getRequiredMaterial(int corruptionCount) {
        return corruptionCount == 0 ? config.getTier1Cost() : config.getTier2Cost();
    }
}
```

### 3. CorruptionInteraction.java
**Path:** `src/main/java/irai/mod/reforge/Interactions/CorruptionInteraction.java`

```java
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

import java.util.concurrent.ThreadLocalRandom;

public class CorruptionInteraction extends SimpleInteraction {

    public static final BuilderCodec<CorruptionInteraction> CODEC =
            BuilderCodec.builder(CorruptionInteraction.class, CorruptionInteraction::new, SimpleInteraction.CODEC).build();

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
            player.getPlayerRef().sendMessage(Message.raw("Hold a weapon or armor to corrupt."));
            return;
        }

        CorruptionConfig config = CorruptionUtils.getConfig();
        if (!config.isEnabled()) {
            player.getPlayerRef().sendMessage(Message.raw("Corruption is currently disabled."));
            return;
        }

        if (!ReforgeEquip.isWeapon(heldItem) && !ReforgeEquip.isArmor(heldItem)) {
            player.getPlayerRef().sendMessage(Message.raw("Only weapons and armor can be corrupted."));
            return;
        }

        if (!CorruptionUtils.canCorrupt(heldItem)) {
            player.getPlayerRef().sendMessage(Message.raw("Equipment has reached max corruptions."));
            return;
        }

        int corruptionCount = CorruptionUtils.getCorruptionCount(heldItem);
        String requiredMaterial = CorruptionUtils.getRequiredMaterial(corruptionCount);

        if (!hasItem(player, requiredMaterial)) {
            player.getPlayerRef().sendMessage(Message.raw("You need " + requiredMaterial + " to corrupt this equipment."));
            return;
        }

        // Check for item loss on 2nd corruption
        boolean itemLost = false;
        if (corruptionCount >= 1) {
            if (ThreadLocalRandom.current().nextDouble() < config.getTier2ItemLoss()) {
                itemLost = true;
            }
        }

        if (itemLost) {
            // Remove item
            removeItem(player, requiredMaterial);
            ItemStack empty = heldItem.withQuantity(0);
            player.getInventory().getHotbar().setItemStackForSlot(context.getHeldItemSlot(), empty);
            player.getPlayerRef().sendMessage(Message.raw("The corruption consumed your equipment!"));
            return;
        }

        // Apply corruption
        removeItem(player, requiredMaterial);
        ItemStack corrupted = CorruptionUtils.corruptItem(heldItem);
        player.getInventory().getHotbar().setItemStackForSlot(context.getHeldItemSlot(), corrupted);
        player.getPlayerRef().sendMessage(Message.raw("Equipment corrupted! Cannot be refined or socketed anymore."));
    }

    private boolean hasItem(Player player, String itemId) {
        if (player == null || player.getInventory() == null || itemId == null || itemId.isBlank()) {
            return false;
        }
        ItemContainer hotbar = player.getInventory().getHotbar();
        ItemContainer storage = player.getInventory().getStorage();
        return containerHasItem(hotbar, itemId) || containerHasItem(storage, itemId);
    }

    private boolean containerHasItem(ItemContainer container, String itemId) {
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

    private void removeItem(Player player, String itemId) {
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

    private boolean removeFromContainer(ItemContainer container, String itemId) {
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
```

### 4. Interaction JSON Files
**Path:** `src/main/resources/Server/Item/Interactions/CorruptionInteraction.json`
```json
{ "Type": "CorruptionInteraction" }
```

**Path:** `src/main/resources/Server/Item/RootInteractions/CorruptionInteraction.json`
```json
{ "Interactions": ["CorruptionInteraction"], "RequireNewClick": true }
```

### 5. Update Dark Statue JSON
**Path:** `src/main/resources/Server/Item/Items/Furniture/Temple_Dark/Unique/Furniture_Temple_Dark_Statue_Gaia.json`

Change line 54:
```json
"Use": "CorruptionInteraction"
```

### 6. Register in ReforgePlugin.java
```java
this.getCodecRegistry(Interaction.CODEC).register("CorruptionInteraction", CorruptionInteraction.class, CorruptionInteraction.CODEC);
```

### 7. Config Service Registration
```java
this.corruptionConfig = this.withConfig("CorruptionConfig", CorruptionConfig.CODEC);
this.configService.register("CorruptionConfig", this.corruptionConfig, CorruptionUtils::setConfig);
```

### 8. Add to RuntimeConfigUI
Add corruption configuration category with:
- Enable/disable toggle
- Max corruptions setting
- Cost materials
- Item loss chance
- Outcome chances
- Stat modifier ranges

## Implementation Notes

The stat modification methods in CorruptionUtils are placeholders. The actual implementation depends on how stats are stored in the item metadata. The system is designed to be extensible - the stat modification logic can be added once the stat storage format is determined.

## Validation Plan

1. Test corruption on weapon - verify random outcomes work
2. Test corruption on armor - verify random outcomes work
3. Test 2nd corruption item loss - verify 50% chance works
4. Test refinement lock - verify corrupted items cannot be refined
5. Test socketing lock - verify corrupted items cannot have sockets modified
6. Test config changes - verify RuntimeConfig updates work
