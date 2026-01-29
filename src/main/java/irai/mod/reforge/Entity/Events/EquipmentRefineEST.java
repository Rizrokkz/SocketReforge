package irai.mod.reforge.Entity.Events;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import irai.mod.reforge.Systems.WeaponUpgradeTracker;

import java.util.regex.Pattern;

@SuppressWarnings("removal")
public class EquipmentRefineEST extends DamageEventSystem {

    // Weapon ID pattern - matches items with "Weapon" keyword
    private static final Pattern WEAPON_PATTERN = Pattern.compile(".*[Ww]eapon.*");

    // Damage multipliers per upgrade level (0..3)
    private static final double[] DAMAGE_MULTIPLIERS = {
            1.0,   // base (no +)
            1.10,  // +1 (10% increase)
            1.15,  // +2 (15% increase)
            1.25   // +3 (25% increase)
    };

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

        // Check if it's a weapon using our pattern
        if (!isWeapon(weapon)) {
            return;
        }

        // ═══════════════════════════════════════════════════════════════════════
        // KEY CHANGE: Get upgrade level from WeaponUpgradeTracker
        // ═══════════════════════════════════════════════════════════════════════
        short slot = attacker.getInventory().getActiveHotbarSlot();
        int upgradeLevel = WeaponUpgradeTracker.getUpgradeLevel(attacker, weapon, slot);

        System.out.println("═══════════════════════════════════════════════");
        System.out.println("[EquipmentRefineEST] Player: " + attacker.getPlayerRef().getUsername());
        System.out.println("[EquipmentRefineEST] Weapon: " + getItemId(weapon));
        System.out.println("[EquipmentRefineEST] Upgrade Level: " + upgradeLevel);
        System.out.println("[EquipmentRefineEST] Max Level: " + (DAMAGE_MULTIPLIERS.length - 1));

        // Clamp the level to valid range
        int clampedLevel = Math.max(0, Math.min(upgradeLevel, DAMAGE_MULTIPLIERS.length - 1));
        double multiplier = DAMAGE_MULTIPLIERS[clampedLevel];

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
     * Checks if an item is a weapon.
     */
    private boolean isWeapon(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }

        String itemId = getItemId(item);
        if (itemId == null) {
            return false;
        }

        return WEAPON_PATTERN.matcher(itemId).matches();
    }

    /**
     * Safely gets item ID from an ItemStack.
     */
    private String getItemId(ItemStack item) {
        if (item == null) return null;

        try {
            // Try different methods to get item ID
            return item.getItemId();
        } catch (Exception e1) {
            try {
                if (item != null) {
                    return item.getItemId();
                }
            } catch (Exception e2) {
                try {
                    if (item.getItem() != null) {
                        return item.getItem().getId();
                    }
                } catch (Exception e3) {
                    try {
                        return item.getItemId();
                    } catch (Exception e4) {
                        return null;
                    }
                }
            }
        }
        return null;
    }
}