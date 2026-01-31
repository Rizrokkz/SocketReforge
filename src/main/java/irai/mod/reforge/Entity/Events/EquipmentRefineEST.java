package irai.mod.reforge.Entity.Events;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Config.RefinementConfig;
import irai.mod.reforge.Interactions.ReforgeEquip;


@SuppressWarnings("removal")
public class EquipmentRefineEST extends DamageEventSystem {

    // Refinement config - will be injected from plugin
    private RefinementConfig refinementConfig;

    // Default damage multipliers per upgrade level (0..3) - used if config not loaded
    private static final double[] DEFAULT_DAMAGE_MULTIPLIERS = {
            1.0,   // base (no +)
            1.10,  // +1 (10% increase)
            1.15,  // +2 (15% increase)
            1.25   // +3 (25% increase)
    };

    /**
     * Sets the refinement config for this system.
     * @param config The refinement configuration to use
     */
    public void setRefinementConfig(RefinementConfig config) {
        this.refinementConfig = config;
    }

    /**
     * Gets the damage multiplier for a given level.
     * Uses config if available, otherwise uses defaults.
     */
    private double getDamageMultiplier(int level) {
        if (refinementConfig != null) {
            return refinementConfig.getDamageMultiplier(level);
        }
        // Fallback to defaults
        if (level < 0 || level >= DEFAULT_DAMAGE_MULTIPLIERS.length) {
            return 1.0;
        }
        return DEFAULT_DAMAGE_MULTIPLIERS[level];
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
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
                       Damage damage) {

        // Get target (entity receiving damage)
        Ref<EntityStore> targetRef = chunk.getReferenceTo(index);

        // Get damage source
        Damage.Source source = damage.getSource();
        if (!(source instanceof Damage.EntitySource entitySource)) {
            return;
        }

        Ref<EntityStore> attackerRef = entitySource.getRef();

        // Check if attacker is a player
        Player attacker = store.getComponent(attackerRef, Player.getComponentType());
        if (attacker == null) {
            return;
        }

        // Find weapon in attacker's hotbar
        ItemStack weapon = findWeaponInHotbar(attacker);
        if (weapon == null) {
            return;
        }

        // Check if it's a weapon using proper category check
        if (!isWeapon(weapon)) {
            return;
        }

        // ═══════════════════════════════════════════════════════════════════════
        // Get upgrade level from weapon ID suffix (e.g., Weapon_Axe_Cobalt1 = +1)
        // ═══════════════════════════════════════════════════════════════════════
        String weaponId = getItemId(weapon);
        int upgradeLevel = ReforgeEquip.getLevelFromWeaponId(weaponId);

        System.out.println("═══════════════════════════════════════════════");
        System.out.println("[EquipmentRefineEST] Player: " + attacker.getPlayerRef().getUsername());
        System.out.println("[EquipmentRefineEST] Weapon: " + getItemId(weapon));
        System.out.println("[EquipmentRefineEST] Upgrade Level: " + upgradeLevel);
        System.out.println("[EquipmentRefineEST] Max Level: 3");

        // Clamp the level to valid range
        int clampedLevel = Math.max(0, Math.min(upgradeLevel, 3));
        double multiplier = getDamageMultiplier(clampedLevel);

        System.out.println("[EquipmentRefineEST] Clamped Level: " + clampedLevel);
        System.out.println("[EquipmentRefineEST] Multiplier: " + multiplier);

        // Store original damage
        float originalDamage = damage.getAmount();
        float newDamage = (float) (originalDamage * multiplier);

        System.out.println("[EquipmentRefineEST] Original Damage: " + originalDamage);
        System.out.println("[EquipmentRefineEST] New Damage: " + newDamage);
        System.out.println("═══════════════════════════════════════════════");

        // Apply multiplier
        damage.setAmount(newDamage);
    }

    /**
     * Looks in the player's hotbar for the first item that is a weapon.
     */
    private ItemStack findWeaponInHotbar(Player player) {
        try {
            ItemContainer hotbar = player.getInventory().getHotbar();

            // Try to get currently selected slot first
            short selectedSlot = player.getInventory().getActiveHotbarSlot();
            ItemStack selectedItem = hotbar.getItemStack(selectedSlot);

            if (selectedItem != null && !selectedItem.isEmpty() && isWeapon(selectedItem)) {
                return selectedItem;
            }

            // Fallback: search entire hotbar
            for (short slot = 0; slot < hotbar.getCapacity(); slot++) {
                ItemStack stack = hotbar.getItemStack(slot);
                if (stack != null && !stack.isEmpty() && isWeapon(stack)) {
                    return stack;
                }
            }

        } catch (Exception e) {
            System.err.println("[EquipmentRefineEST] Error finding weapon: " + e.getMessage());
        }

        return null;
    }

    /**
     * Checks if an item is a weapon using proper category checks.
     * Uses ReforgeEquip.isWeapon(ItemStack) for accurate detection.
     */
    private boolean isWeapon(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }

        // Use the proper category check from ReforgeEquip
        return ReforgeEquip.isWeapon(item);
    }

    /**
     * Safely gets item ID from an ItemStack.
     */
    private String getItemId(ItemStack item) {
        if (item == null) return null;

        try {
            return item.getItemId();
        } catch (Exception e1) {
            try {
                if (item.getItem() != null) {
                    return item.getItem().getId();
                }
            } catch (Exception e2) {
                // Fallback: return null
            }
        }
        return null;
    }
}
