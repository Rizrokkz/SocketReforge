package irai.mod.reforge.Entity.Events;

import java.util.ArrayList;
import java.util.List;

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

    // Default defense multipliers per upgrade level (0..3) - used if config not loaded
    private static final double[] DEFAULT_DEFENSE_MULTIPLIERS = {
            1.0,   // base (no +)
            1.08,  // +1 (8% increase)
            1.12,  // +2 (12% increase)
            1.20   // +3 (20% increase)
    };

    /**
     * Sets the refinement config for this system.
     * @param config The refinement configuration to use
     */
    public void setRefinementConfig(RefinementConfig config) {
        this.refinementConfig = config;
    }

    /**
     * Gets the damage multiplier for a given weapon level.
     * Uses config if available, otherwise uses defaults.
     */
    private double getDamageMultiplier(int level) {
        if (refinementConfig != null) {
            return refinementConfig.getDamageMultiplier(level);
        }
        if (level < 0 || level >= DEFAULT_DAMAGE_MULTIPLIERS.length) return 1.0;
        return DEFAULT_DAMAGE_MULTIPLIERS[level];
    }

    /**
     * Gets the defense multiplier for a given armor level.
     * Uses config if available, otherwise uses defaults.
     */
    private double getDefenseMultiplier(int level) {
        if (refinementConfig != null) {
            return refinementConfig.getDefenseMultiplier(level);
        }
        if (level < 0 || level >= DEFAULT_DEFENSE_MULTIPLIERS.length) return 1.0;
        return DEFAULT_DEFENSE_MULTIPLIERS[level];
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

        // ── Attacker weapon bonus (damage multiplier) ─────────────────────────
        Player attacker = store.getComponent(attackerRef, Player.getComponentType());
        if (attacker != null) {
            ItemStack weapon = findWeaponInHotbar(attacker);
            if (weapon != null && ReforgeEquip.isWeapon(weapon)) {
                String weaponId = getItemId(weapon);
                int upgradeLevel = ReforgeEquip.getLevelFromItem(weapon);
                int clampedLevel = Math.max(0, Math.min(upgradeLevel, 3));
                double multiplier = getDamageMultiplier(clampedLevel);

                System.out.println("═══════════════════════════════════════════════");
                System.out.println("[EquipmentRefineEST] Attacker: " + attacker.getPlayerRef().getUsername());
                System.out.println("[EquipmentRefineEST] Weapon: " + weaponId + " (level " + clampedLevel + ")");
                System.out.println("[EquipmentRefineEST] Damage multiplier: " + multiplier);

                float newDamage = (float) (damage.getAmount() * multiplier);
                System.out.println("[EquipmentRefineEST] Damage: " + damage.getAmount() + " -> " + newDamage);
                System.out.println("═══════════════════════════════════════════════");
                damage.setAmount(newDamage);
            }
        }

        // ── Defender armor bonus (defense / damage reduction) ─────────────────
        Player defender = store.getComponent(targetRef, Player.getComponentType());
        if (defender != null) {
            List<ItemStack> armorPieces = getAllEquippedArmor(defender);
            if (!armorPieces.isEmpty()) {
                double avgDefenseMultiplier = calculateAverageDefenseMultiplier(armorPieces);

                // Get the highest level armor for display purposes
                int highestLevel = 0;
                for (ItemStack armor : armorPieces) {
                    String armorId = getItemId(armor);
                    if (armorId != null) {
                        int level = ReforgeEquip.getLevelFromItem(armor);
                        if (level > highestLevel) highestLevel = level;
                    }
                }
                int clampedLevel = Math.max(0, Math.min(highestLevel, 3));

                System.out.println("═══════════════════════════════════════════════");
                System.out.println("[EquipmentRefineEST] Defender: " + defender.getPlayerRef().getUsername());
                System.out.println("[EquipmentRefineEST] Armor pieces: " + armorPieces.size());
                System.out.println("[EquipmentRefineEST] Highest armor level: " + clampedLevel);
                System.out.println("[EquipmentRefineEST] Average defense multiplier: " + avgDefenseMultiplier);

                float reducedDamage = (float) (damage.getAmount() / avgDefenseMultiplier);
                System.out.println("[EquipmentRefineEST] Damage after defense: " + damage.getAmount() + " -> " + reducedDamage);
                System.out.println("═══════════════════════════════════════════════");
                damage.setAmount(reducedDamage);
            }
        }
    }

    /**
     * Looks in the player's hotbar for the currently held weapon.
     */
    private ItemStack findWeaponInHotbar(Player player) {
        try {
            ItemContainer hotbar = player.getInventory().getHotbar();

            // Try to get currently selected slot first
            short selectedSlot = player.getInventory().getActiveHotbarSlot();
            ItemStack selectedItem = hotbar.getItemStack(selectedSlot);

            if (selectedItem != null && !selectedItem.isEmpty() && ReforgeEquip.isWeapon(selectedItem)) {
                return selectedItem;
            }

            // Fallback: search entire hotbar
            for (short slot = 0; slot < hotbar.getCapacity(); slot++) {
                ItemStack stack = hotbar.getItemStack(slot);
                if (stack != null && !stack.isEmpty() && ReforgeEquip.isWeapon(stack)) {
                    return stack;
                }
            }

        } catch (Exception e) {
            System.err.println("[EquipmentRefineEST] Error finding weapon: " + e.getMessage());
        }

        return null;
    }

    /**
     * Gets all equipped armor pieces from the player's equipment slots and inventory.
     * Returns a list of armor ItemStacks.
     */
    private List<ItemStack> getAllEquippedArmor(Player player) {
        List<ItemStack> armorPieces = new ArrayList<>();

        try {
            // First, check player's equipment slots (helmet, chestplate, leggings, boots)
            try {
                ItemContainer armorContainer = player.getInventory().getArmor();
                if (armorContainer != null) {
                    for (short slot = 0; slot < armorContainer.getCapacity(); slot++) {
                        ItemStack stack = armorContainer.getItemStack(slot);
                        if (stack != null && !stack.isEmpty() && ReforgeEquip.isArmor(stack)) {
                            armorPieces.add(stack);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[EquipmentRefineEST] Error checking armor container: " + e.getMessage());
            }

            // Then, check inventory (hotbar and storage) for any additional armor
            for (ItemContainer container : new ItemContainer[]{
                    player.getInventory().getHotbar(),
                    player.getInventory().getStorage()}) {
                if (container == null) continue;
                for (short slot = 0; slot < container.getCapacity(); slot++) {
                    ItemStack stack = container.getItemStack(slot);
                    if (stack != null && !stack.isEmpty() && ReforgeEquip.isArmor(stack)) {
                        armorPieces.add(stack);
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("[EquipmentRefineEST] Error getting equipped armor: " + e.getMessage());
        }

        return armorPieces;
    }

    /**
     * Calculates the average defense multiplier from all equipped armor pieces.
     */
    private double calculateAverageDefenseMultiplier(List<ItemStack> armorPieces) {
        if (armorPieces.isEmpty()) {
            return 1.0; // No armor, no bonus
        }

        double totalMultiplier = 0.0;
        int count = 0;

        for (ItemStack armor : armorPieces) {
            String armorId = getItemId(armor);
            if (armorId != null) {
                int level = ReforgeEquip.getLevelFromItem(armor);
                int clampedLevel = Math.max(0, Math.min(level, 3));
                double multiplier = getDefenseMultiplier(clampedLevel);
                totalMultiplier += multiplier;
                count++;
            }
        }

        if (count == 0) {
            return 1.0;
        }

        return totalMultiplier / count;
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
