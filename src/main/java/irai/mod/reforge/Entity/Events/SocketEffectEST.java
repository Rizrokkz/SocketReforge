package irai.mod.reforge.Entity.Events;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Locale;

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
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Interactions.ReforgeEquip;
import irai.mod.reforge.Socket.Essence;
import irai.mod.reforge.Socket.EssenceEffect;
import irai.mod.reforge.Socket.SocketData;
import irai.mod.reforge.Socket.SocketManager;

/**
 * Entity Stat Type (EST) that applies socket effects to damage calculations.
 * Integrates with the existing damage system to apply essence bonuses.
 */
@SuppressWarnings("removal")
public class SocketEffectEST extends DamageEventSystem {
    private static final Map<UUID, Long> FROZEN_UNTIL = new ConcurrentHashMap<>();
    private static final long FREEZE_DURATION_MILLIS = 2000L;
    private static final double FREEZE_DAMAGE_PENALTY = 0.30d;
    private static final double ICE_SLOW_DAMAGE_PENALTY_CAP = 50.0d;
    private static final double FIRE_BURN_REFLECT_RATIO = 0.05d;
    private static final float FIRE_BURN_MIN_DAMAGE = 1.0f;

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
        try {
            float beforeDefenderSocketDamage = damage.getAmount();
            // Get target (entity receiving damage)
            Ref<EntityStore> targetRef = chunk.getReferenceTo(index);

            // Get damage source
            Damage.Source source = damage.getSource();
            if (!(source instanceof Damage.EntitySource entitySource)) {
                return;
            }

            Ref<EntityStore> attackerRef = entitySource.getRef();
            Player attackerPlayer = null;
            ItemStack attackerWeapon = null;

            // Apply attacker socket bonuses (damage increase)
            Player attacker = store.getComponent(attackerRef, Player.getComponentType());
            attackerPlayer = attacker;
            if (attacker != null) {
                applyFreezePenaltyIfPresent(attacker, damage);
                ItemStack weapon = findWeaponInHotbar(attacker);
                if (weapon != null && ReforgeEquip.isWeapon(weapon)) {
                    attackerWeapon = weapon;
                    // NOTE: attacker damage (refine + sockets) is applied in EquipmentRefineEST
                    // to avoid multi-system write races on Damage amount.
                }
            }

            // Apply defender socket bonuses (damage reduction from armor sockets)
            Player defender = store.getComponent(targetRef, Player.getComponentType());
            if (defender != null) {
                // Apply Ice Freeze effect on hit if attacker has Max Tier Ice Essence.
                // This is independent of defender armor and is applied as a short debuff.
                if (attacker != null) {
                    applyIceFreezeOnHit(attacker, defender);
                }

                List<ItemStack> armorPieces = getAllEquippedArmor(defender);
                if (!armorPieces.isEmpty()) {
                    SocketStatSystem.DefensiveBonuses defensiveBonuses = SocketStatSystem.getDefensiveBonuses(defender);

                    // Evasion is a full dodge chance from armor sockets.
                    double evasionChance = Math.max(0.0, Math.min(100.0, defensiveBonuses.evasionPercent()));
                    if (evasionChance > 0 && ThreadLocalRandom.current().nextDouble(100.0) < evasionChance) {
                        damage.setAmount(0f);
                        return;
                    }

                    double defenseMultiplier = 1.0 + (defensiveBonuses.defensePercent() / 100.0);
                    double flatReduction = calculateSocketFlatDefense(armorPieces);
                    double slowPercent = 0.0;
                    // ICE armor slow is enemy-only: it should penalize the attacker, never the defender.
                    if (attacker != null && !isSamePlayer(attacker, defender)) {
                        slowPercent = SocketArmorBonusHelper.getScaledPercentBonus(defender, EssenceEffect.StatType.MOVEMENT_SPEED);
                        slowPercent = Math.max(0.0, Math.min(ICE_SLOW_DAMAGE_PENALTY_CAP, slowPercent));
                    }
                    double fireDefensePercent = isFireDamage(damage) ? defensiveBonuses.fireDefensePercent() : 0.0;
                    double fireDefenseMultiplier = Math.max(0.0, 1.0 - (fireDefensePercent / 100.0));

                    if (defenseMultiplier != 1.0 || flatReduction != 0 || fireDefenseMultiplier != 1.0 || slowPercent > 0.0) {
                        float damageAmount = damage.getAmount();

                        // Ice armor slow: model as reduced attacker hit potency.
                        if (slowPercent > 0.0) {
                            damageAmount = (float) (damageAmount * Math.max(0.0, 1.0 - (slowPercent / 100.0)));
                        }

                        float reducedDamage = (float) Math.max(0, (damageAmount / defenseMultiplier) - flatReduction);
                        
                        // Fire defense only applies to fire-like damage causes.
                        reducedDamage = (float) (reducedDamage * fireDefenseMultiplier);

                        damage.setAmount(reducedDamage);
                    }

                    // Apply Fire Burn effect on hit if Fire Essence is Max Tier
                    applyFireBurnOnHit(store, defender, attackerRef, damage.getAmount());
                }
            }

            // Log final per-hit damage after socket processing for easier balancing/debugging.
            if (attackerPlayer != null) {
                applyWeaponLifeSteal(store, attackerRef, attackerWeapon, damage.getAmount());

                System.out.println("[SocketReforge][DEF_DMG] attacker=" + attackerPlayer.getUuid()
                        + " beforeDefenderSocket=" + beforeDefenderSocketDamage
                        + " final=" + damage.getAmount());
            }
        } catch (Throwable t) {
            System.err.println("[SocketReforge] SocketEffectEST handle error: " + t.getMessage());
            t.printStackTrace();
        }
    }

    /**
     * Applies Burn effect to attacker if defender has Max Tier Fire Essence.
     */
    private void applyFireBurnOnHit(Store<EntityStore> store,
                                    Player defender,
                                    Ref<EntityStore> attackerRef,
                                    float finalIncomingDamage) {
        if (store == null || defender == null || attackerRef == null || finalIncomingDamage <= 0f) {
            return;
        }

        // Check if defender has armor with Fire Essence Tier 5
        List<ItemStack> armorPieces = getAllEquippedArmor(defender);
        boolean hasFireTierFive = false;
        for (ItemStack armor : armorPieces) {
            SocketData socketData = SocketManager.getSocketData(armor);
            if (socketData != null && socketData.getMaxSockets() > 0) {
                Map<Essence.Type, Integer> tierMap = SocketManager.calculateConsecutiveTiers(socketData);
                Integer fireTier = tierMap.get(Essence.Type.FIRE);
                if (fireTier != null && fireTier >= 5) {
                    hasFireTierFive = true;
                    break;
                }
            }
        }
        if (!hasFireTierFive) {
            return;
        }

        float burnDamage = Math.max(FIRE_BURN_MIN_DAMAGE, (float) (finalIncomingDamage * FIRE_BURN_REFLECT_RATIO));
        applyDirectHealthLoss(store, attackerRef, burnDamage);
    }

    /**
     * Applies Freeze effect to defender if attacker has Max Tier Ice Essence.
     */
    private void applyIceFreezeOnHit(Player attacker, Player defender) {
        if (attacker == null || defender == null) {
            return;
        }
        if (isSamePlayer(attacker, defender)) {
            return;
        }

        // Check if attacker has weapon with Ice Essence Tier 5
        ItemStack weapon = findWeaponInHotbar(attacker);
        if (weapon == null || weapon.isEmpty() || !ReforgeEquip.isWeapon(weapon)) {
            return;
        }

        SocketData socketData = SocketManager.getSocketData(weapon);
        if (socketData != null && socketData.getMaxSockets() > 0) {
             Map<Essence.Type, Integer> tierMap = SocketManager.calculateConsecutiveTiers(socketData);
             Integer iceTier = tierMap.get(Essence.Type.ICE);
             if (iceTier != null && iceTier >= 5) {
                 UUID defenderUuid = defender.getUuid();
                 if (defenderUuid != null) {
                     FROZEN_UNTIL.put(defenderUuid, System.currentTimeMillis() + FREEZE_DURATION_MILLIS);
                 }
             }
        }
    }

    private void applyFreezePenaltyIfPresent(Player attacker, Damage damage) {
        if (attacker == null || damage == null) {
            return;
        }
        UUID attackerUuid = attacker.getUuid();
        if (attackerUuid == null) {
            return;
        }

        Long until = FROZEN_UNTIL.get(attackerUuid);
        if (until == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now >= until) {
            FROZEN_UNTIL.remove(attackerUuid);
            return;
        }

        float penalized = (float) (damage.getAmount() * (1.0 - FREEZE_DAMAGE_PENALTY));
        damage.setAmount(Math.max(0f, penalized));
    }

    private boolean isFireDamage(Damage damage) {
        if (damage == null) {
            return false;
        }
        try {
            var cause = damage.getCause();
            if (cause == null) {
                return false;
            }
            String causeId = cause.getId();
            if (causeId == null || causeId.isBlank()) {
                return false;
            }
            String lower = causeId.toLowerCase(Locale.ROOT);
            return lower.contains("fire")
                    || lower.contains("burn")
                    || lower.contains("lava")
                    || lower.contains("flame");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void applyDirectHealthLoss(Store<EntityStore> store, Ref<EntityStore> targetRef, float rawDamage) {
        if (store == null || targetRef == null || rawDamage <= 0f) {
            return;
        }
        EntityStatMap statMap = store.getComponent(targetRef, EntityStatMap.getComponentType());
        if (statMap == null) {
            return;
        }
        int healthStatIndex = getHealthStatIndex(statMap);
        if (healthStatIndex < 0) {
            return;
        }
        EntityStatValue health = statMap.get(healthStatIndex);
        if (health == null || health.get() <= 0f) {
            return;
        }
        float maxSpendable = Math.max(0f, health.get() - 0.1f);
        float applied = Math.min(rawDamage, maxSpendable);
        if (applied <= 0f) {
            return;
        }
        statMap.addStatValue(healthStatIndex, -applied);
    }

    private boolean isSamePlayer(Player a, Player b) {
        if (a == null || b == null) {
            return false;
        }
        UUID ua = a.getUuid();
        UUID ub = b.getUuid();
        return ua != null && ua.equals(ub);
    }

    /**
     * Calculates the total defense multiplier from socketed armor essences using tier-based calculation.
     */
    private double calculateSocketDefenseBonus(List<ItemStack> armorPieces) {
        double totalPercent = 0.0;

        for (ItemStack armor : armorPieces) {
            double[] bonuses = SocketManager.getStoredStatBonus(armor, EssenceEffect.StatType.DEFENSE);
            totalPercent += bonuses[1];
        }

        return 1.0 + (totalPercent / 100.0);
    }

    /**
     * Calculates the total flat defense reduction from socketed armor essences using tier-based calculation.
     */
    private double calculateSocketFlatDefense(List<ItemStack> armorPieces) {
        double totalReduction = 0;

        for (ItemStack armor : armorPieces) {
            double[] bonuses = SocketManager.getStoredStatBonus(armor, EssenceEffect.StatType.DEFENSE);
            totalReduction += bonuses[0];
        }

        return totalReduction;
    }

    /**
     * Applies Health bonus from Life Essence to the player.
     */
    private void applyHealthBonus(Player player) {
        List<ItemStack> armorPieces = getAllEquippedArmor(player);
        double healthBonus = 0;

        for (ItemStack armor : armorPieces) {
            SocketData socketData = SocketManager.getSocketData(armor);
            if (socketData != null && socketData.getMaxSockets() > 0) {
                // Calculate Health bonus (flat)
                // We assume it's a flat bonus based on EssenceEffect.StatType.HEALTH
                double[] bonuses = SocketManager.calculateTieredBonus(socketData, EssenceEffect.StatType.HEALTH, false);
                healthBonus += bonuses[0]; // Flat bonus
            }
        }

        if (healthBonus > 0) {
            // Apply health bonus (Heal player)
            // Assuming player has getHealth() and setHealth() or similar
            // For now, we'll log it
            System.out.println("[SocketReforge] Applying " + healthBonus + " HP.");
            // player.setHealth(player.getHealth() + (float)healthBonus);
        }
    }

    /**
     * Weapon Lifesteal:
     * Heals attacker by final dealt damage * lifesteal%.
     */
    private void applyWeaponLifeSteal(
            Store<EntityStore> store,
            Ref<EntityStore> attackerRef,
            ItemStack weapon,
            float finalDamageDealt) {
        if (store == null || attackerRef == null || weapon == null || weapon.isEmpty()) {
            return;
        }
        if (finalDamageDealt <= 0f) {
            return;
        }
        if (!ReforgeEquip.isWeapon(weapon)) {
            return;
        }

        double lifeStealPercent = getLifeStealPercent(weapon);
        if (lifeStealPercent <= 0.0d) {
            return;
        }

        EntityStatMap statMap = store.getComponent(attackerRef, EntityStatMap.getComponentType());
        if (statMap == null) {
            return;
        }

        int healthStatIndex = getHealthStatIndex(statMap);
        if (healthStatIndex < 0) {
            return;
        }

        EntityStatValue health = statMap.get(healthStatIndex);
        if (health == null || health.getMax() <= 0f) {
            return;
        }

        float missing = health.getMax() - health.get();
        if (missing <= 0f) {
            return;
        }

        float heal = (float) (finalDamageDealt * (lifeStealPercent / 100.0d));
        if (heal <= 0f) {
            return;
        }
        heal = Math.min(heal, missing);
        statMap.addStatValue(healthStatIndex, heal);
    }

    private double getLifeStealPercent(ItemStack weapon) {
        if (weapon == null || weapon.isEmpty()) {
            return 0.0d;
        }
        SocketData socketData = SocketManager.getSocketData(weapon);
        if (socketData == null || socketData.getMaxSockets() == 0) {
            return 0.0d;
        }

        // Prefer persisted values; fall back to tier calculation for older items.
        double[] bonuses = SocketManager.getStoredStatBonus(weapon, EssenceEffect.StatType.LIFE_STEAL);
        if (bonuses[0] == 0.0d && bonuses[1] == 0.0d) {
            bonuses = SocketManager.calculateTieredBonus(socketData, EssenceEffect.StatType.LIFE_STEAL, true);
        }
        return Math.max(0.0d, bonuses[1]);
    }

    private int getHealthStatIndex(EntityStatMap statMap) {
        int byDefault = DefaultEntityStatTypes.getHealth();
        if (byDefault >= 0) {
            EntityStatValue value = statMap.get(byDefault);
            if (value != null) return value.getIndex();
        }
        String[] aliases = {"health", "Health", "HP", "hp"};
        for (String alias : aliases) {
            EntityStatValue value = statMap.get(alias);
            if (value != null) return value.getIndex();
        }
        return -1;
    }

    /**
     * Calculates the total fire defense multiplier from socketed armor essences using tier-based calculation.
     */
    private double calculateSocketFireDefenseBonus(List<ItemStack> armorPieces) {
        double totalPercent = 0.0;

        for (ItemStack armor : armorPieces) {
            double[] bonuses = SocketManager.getStoredStatBonus(armor, EssenceEffect.StatType.FIRE_DEFENSE);
            totalPercent += bonuses[1];
        }

        return Math.max(0.0, 1.0 - (totalPercent / 100.0));
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
        ArrayList<ItemStack> armorPieces = new ArrayList<>();

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
