package irai.mod.reforge.Entity.Events;

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

import irai.mod.reforge.Interactions.ReforgeEquip;
import irai.mod.reforge.Socket.EffectType;
import irai.mod.reforge.Socket.SocketData;
import irai.mod.reforge.Socket.SocketManager;
import irai.mod.reforge.Socket.StatType;

/**
 * Entity Stat Type (EST) that applies socket effects to damage calculations.
 * Integrates with the existing damage system to apply essence bonuses.
 */
@SuppressWarnings("removal")
public class SocketEffectEST extends DamageEventSystem {

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

        // Apply attacker socket bonuses (damage increase)
        Player attacker = store.getComponent(attackerRef, Player.getComponentType());
        if (attacker != null) {
            ItemStack weapon = findWeaponInHotbar(attacker);
            if (weapon != null && ReforgeEquip.isWeapon(weapon)) {
                double damageMultiplier = calculateSocketDamageBonus(weapon);
                double flatDamage = calculateSocketFlatDamage(weapon);
                
                if (damageMultiplier != 1.0 || flatDamage != 0) {
                    float newDamage = (float) ((damage.getAmount() * damageMultiplier) + flatDamage);
                    
                    damage.setAmount(newDamage);
                }
            }
        }

        // Apply defender socket bonuses (damage reduction from armor sockets)
        Player defender = store.getComponent(targetRef, Player.getComponentType());
        if (defender != null) {
            List<ItemStack> armorPieces = getAllEquippedArmor(defender);
            if (!armorPieces.isEmpty()) {
                double defenseMultiplier = calculateSocketDefenseBonus(armorPieces);
                double flatReduction = calculateSocketFlatDefense(armorPieces);
                
                if (defenseMultiplier != 1.0 || flatReduction != 0) {
                    float reducedDamage = (float) Math.max(0, (damage.getAmount() / defenseMultiplier) - flatReduction);
                    
                    damage.setAmount(reducedDamage);
                }
            }
        }
    }

    /**
     * Calculates the total damage multiplier from socketed essences.
     */
    private double calculateSocketDamageBonus(ItemStack weapon) {
        SocketData socketData = SocketManager.getSocketData(weapon);
        if (socketData == null || socketData.getMaxSockets() == 0) {
            return 1.0;
        }

        double percentageBonus = socketData.getTotalStatBonus(StatType.DAMAGE, EffectType.PERCENTAGE);
        return 1.0 + (percentageBonus / 100.0);
    }

    /**
     * Calculates the total flat damage bonus from socketed essences.
     */
    private double calculateSocketFlatDamage(ItemStack weapon) {
        SocketData socketData = SocketManager.getSocketData(weapon);
        if (socketData == null || socketData.getMaxSockets() == 0) {
            return 0;
        }

        return socketData.getTotalStatBonus(StatType.DAMAGE, EffectType.FLAT);
    }

    /**
     * Calculates the total defense multiplier from socketed armor essences.
     */
    private double calculateSocketDefenseBonus(List<ItemStack> armorPieces) {
        double totalMultiplier = 1.0;

        for (ItemStack armor : armorPieces) {
            SocketData socketData = SocketManager.getSocketData(armor);
            if (socketData != null && socketData.getMaxSockets() > 0) {
                double percentageBonus = socketData.getTotalStatBonus(StatType.DEFENSE, EffectType.PERCENTAGE);
                totalMultiplier *= (1.0 + percentageBonus / 100.0);
            }
        }

        return totalMultiplier;
    }

    /**
     * Calculates the total flat defense reduction from socketed armor essences.
     */
    private double calculateSocketFlatDefense(List<ItemStack> armorPieces) {
        double totalReduction = 0;

        for (ItemStack armor : armorPieces) {
            SocketData socketData = SocketManager.getSocketData(armor);
            if (socketData != null && socketData.getMaxSockets() > 0) {
                totalReduction += socketData.getTotalStatBonus(StatType.DEFENSE, EffectType.FLAT);
            }
        }

        return totalReduction;
    }

    /**
     * Looks in the player's hotbar for the currently held weapon.
     */
    private ItemStack findWeaponInHotbar(Player player) {
        try {
            ItemContainer hotbar = player.getInventory().getHotbar();
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
            System.err.println("[SocketEffectEST] Error finding weapon: " + e.getMessage());
        }

        return null;
    }

    /**
     * Gets all equipped armor pieces from the player.
     */
    private List<ItemStack> getAllEquippedArmor(Player player) {
        java.util.ArrayList<ItemStack> armorPieces = new java.util.ArrayList<>();

        try {
            // Check armor container
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
            System.err.println("[SocketEffectEST] Error getting armor: " + e.getMessage());
        }

        return armorPieces;
    }
}
