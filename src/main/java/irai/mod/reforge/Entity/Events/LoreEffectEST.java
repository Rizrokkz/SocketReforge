package irai.mod.reforge.Entity.Events;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

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

import irai.mod.reforge.Common.PlayerInventoryUtils;
import irai.mod.reforge.Lore.LoreProcHandler;
import irai.mod.reforge.Lore.LoreSocketData;
import irai.mod.reforge.Lore.LoreSocketManager;
import irai.mod.reforge.Lore.LoreTrigger;
import irai.mod.reforge.Socket.Essence;
import irai.mod.reforge.Socket.EssenceEffect;
import irai.mod.reforge.Socket.SocketData;
import irai.mod.reforge.Socket.SocketManager;

/**
 * Applies lore socket procs on damage events (on-hit/on-crit/on-damaged).
 */
@SuppressWarnings("removal")
public final class LoreEffectEST extends DamageEventSystem {
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
        if (store == null || damage == null) {
            return;
        }

        Ref<EntityStore> targetRef = chunk.getReferenceTo(index);
        Damage.Source source = damage.getSource();
        if (!(source instanceof Damage.EntitySource entitySource)) {
            return;
        }
        Ref<EntityStore> attackerRef = entitySource.getRef();

        Player attacker = attackerRef == null ? null : store.getComponent(attackerRef, Player.getComponentType());
        Player defender = targetRef == null ? null : store.getComponent(targetRef, Player.getComponentType());

        if (attacker != null && targetRef != null) {
            LoreProcHandler.tryApplyFrozenShatter(store, attackerRef, targetRef, damage);
        }

        if (attacker != null) {
            applyAttackerLore(store, attacker, attackerRef, targetRef, damage, commandBuffer);
        }
        if (defender != null) {
            applyDefenderLore(store, defender, targetRef, attackerRef, damage, commandBuffer);
        }
    }

    private void applyAttackerLore(Store<EntityStore> store,
                                   Player attacker,
                                   Ref<EntityStore> attackerRef,
                                   Ref<EntityStore> defenderRef,
                                   Damage damage,
                                   CommandBuffer<EntityStore> commandBuffer) {
        PlayerInventoryUtils.HeldItemContext ctx = PlayerInventoryUtils.getHeldItemContext(attacker);
        if (ctx == null || !ctx.isValid()) {
            return;
        }
        ItemStack weapon = ctx.getItemStack();
        if (weapon == null || weapon.isEmpty() || !LoreSocketManager.isEquipment(weapon)) {
            return;
        }

        LoreSocketData data = LoreSocketManager.getLoreSocketData(weapon);
        if (data == null) {
            return;
        }

        boolean changed = LoreSocketManager.syncSocketColors(weapon, data);
        boolean isCrit = rollCrit(weapon);
        boolean isBlocked = isBlocked(damage);
        Set<String> used = new HashSet<>();
        LoreProcHandler.ProcState procState = new LoreProcHandler.ProcState();
        changed |= LoreProcHandler.applyLoreSockets(store, attacker, attackerRef, defenderRef,
                damage, data, LoreTrigger.ON_HIT, true, used, procState, commandBuffer);

        if (isCrit) {
            changed |= LoreProcHandler.applyLoreSockets(store, attacker, attackerRef, defenderRef,
                    damage, data, LoreTrigger.ON_CRIT, true, used, procState, commandBuffer);
        }
        if (isBlocked) {
            changed |= LoreProcHandler.applyLoreSockets(store, attacker, attackerRef, defenderRef,
                    damage, data, LoreTrigger.ON_BLOCKED, true, used, procState, commandBuffer);
        }

        LoreProcHandler.applyAbsorbed(store, attacker, attackerRef, defenderRef,
                damage, LoreTrigger.ON_HIT, true, used, procState, commandBuffer);
        if (isCrit) {
            LoreProcHandler.applyAbsorbed(store, attacker, attackerRef, defenderRef,
                    damage, LoreTrigger.ON_CRIT, true, used, procState, commandBuffer);
        }
        if (isBlocked) {
            LoreProcHandler.applyAbsorbed(store, attacker, attackerRef, defenderRef,
                    damage, LoreTrigger.ON_BLOCKED, true, used, procState, commandBuffer);
        }

        if (procState.hasTriggered()) {
            changed |= LoreProcHandler.applyLoreProcChain(store, attacker, attackerRef, defenderRef,
                    damage, data, true, used, commandBuffer);
        }

        if (changed) {
            ItemStack updated = LoreSocketManager.withLoreSocketData(weapon, data);
            updateHeldItem(attacker, ctx, updated);
        }
    }

    private void applyDefenderLore(Store<EntityStore> store,
                                   Player defender,
                                   Ref<EntityStore> defenderRef,
                                   Ref<EntityStore> attackerRef,
                                   Damage damage,
                                   CommandBuffer<EntityStore> commandBuffer) {
        if (store == null || defender == null || damage == null) {
            return;
        }
        boolean isBlocked = isBlocked(damage);
        if (damage.getAmount() <= 0f && !isBlocked) {
            return;
        }
        PlayerInventoryUtils.HeldItemContext ctx = PlayerInventoryUtils.getHeldItemContext(defender);
        if (ctx == null || !ctx.isValid()) {
            return;
        }
        ItemStack weapon = ctx.getItemStack();
        if (weapon == null || weapon.isEmpty() || !LoreSocketManager.isEquipment(weapon)) {
            return;
        }

        LoreSocketData data = LoreSocketManager.getLoreSocketData(weapon);
        if (data == null) {
            return;
        }

        boolean changed = LoreSocketManager.syncSocketColors(weapon, data);
        boolean nearDeath = isNearDeath(store, defenderRef, damage);
        Set<String> used = new HashSet<>();
        LoreProcHandler.ProcState procState = new LoreProcHandler.ProcState();

        changed |= LoreProcHandler.applyLoreSockets(store, defender, defenderRef, attackerRef,
                damage, data, LoreTrigger.ON_DAMAGED, false, used, procState, commandBuffer);
        if (isBlocked) {
            changed |= LoreProcHandler.applyLoreSockets(store, defender, defenderRef, attackerRef,
                    damage, data, LoreTrigger.ON_BLOCK, false, used, procState, commandBuffer);
        }
        if (nearDeath) {
            changed |= LoreProcHandler.applyLoreSockets(store, defender, defenderRef, attackerRef,
                    damage, data, LoreTrigger.ON_NEAR_DEATH, false, used, procState, commandBuffer);
        }

        LoreProcHandler.applyAbsorbed(store, defender, defenderRef, attackerRef,
                damage, LoreTrigger.ON_DAMAGED, false, used, procState, commandBuffer);
        if (isBlocked) {
            LoreProcHandler.applyAbsorbed(store, defender, defenderRef, attackerRef,
                    damage, LoreTrigger.ON_BLOCK, false, used, procState, commandBuffer);
        }
        if (nearDeath) {
            LoreProcHandler.applyAbsorbed(store, defender, defenderRef, attackerRef,
                    damage, LoreTrigger.ON_NEAR_DEATH, false, used, procState, commandBuffer);
        }

        if (procState.hasTriggered()) {
            changed |= LoreProcHandler.applyLoreProcChain(store, defender, defenderRef, attackerRef,
                    damage, data, false, used, commandBuffer);
        }

        if (changed) {
            ItemStack updated = LoreSocketManager.withLoreSocketData(weapon, data);
            updateHeldItem(defender, ctx, updated);
        }
    }

    private void updateHeldItem(Player player, PlayerInventoryUtils.HeldItemContext ctx, ItemStack updated) {
        if (player == null || ctx == null || updated == null) {
            return;
        }
        ItemContainer container = ctx.getContainer();
        short slot = ctx.getSlot();
        if (container != null && slot >= 0 && slot < container.getCapacity()) {
            container.setItemStackForSlot(slot, updated);
            return;
        }
        PlayerInventoryUtils.setSelectedHotbarItem(player, updated);
    }

    private boolean rollCrit(ItemStack weapon) {
        double critChance = calculateCritChancePercent(weapon);
        return critChance > 0.0
                && ThreadLocalRandom.current().nextDouble(100.0) < critChance;
    }

    private double calculateCritChancePercent(ItemStack weapon) {
        double[] stored = SocketManager.getStoredStatBonus(weapon, EssenceEffect.StatType.CRIT_CHANCE);
        if (stored[1] > 0.0) {
            return Math.max(0.0, Math.min(100.0, stored[1]));
        }
        int lightningTier = getEssenceTier(weapon, Essence.Type.LIGHTNING);
        return Math.max(0.0, Math.min(100.0, lightningTier));
    }

    private int getEssenceTier(ItemStack weapon, Essence.Type type) {
        if (weapon == null || weapon.isEmpty() || type == null) {
            return 0;
        }
        SocketData socketData = SocketManager.getSocketData(weapon);
        if (socketData == null || socketData.getMaxSockets() == 0) {
            return 0;
        }
        Map<Essence.Type, Integer> tierMap = SocketManager.calculateConsecutiveTiers(socketData);
        Integer tier = tierMap.get(type);
        if (tier == null) {
            return 0;
        }
        return Math.max(0, Math.min(5, tier));
    }

    private boolean isBlocked(Damage damage) {
        if (damage == null) {
            return false;
        }
        try {
            Boolean blocked = damage.getIfPresentMetaObject(Damage.BLOCKED);
            return blocked != null && blocked;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isNearDeath(Store<EntityStore> store, Ref<EntityStore> targetRef, Damage damage) {
        if (store == null || targetRef == null || damage == null) {
            return false;
        }
        EntityStatMap statMap = store.getComponent(targetRef, EntityStatMap.getComponentType());
        if (statMap == null) {
            return false;
        }
        int healthStatIndex = getHealthStatIndex(statMap);
        if (healthStatIndex < 0) {
            return false;
        }
        EntityStatValue health = statMap.get(healthStatIndex);
        if (health == null || health.getMax() <= 0f) {
            return false;
        }
        float remaining = Math.max(0f, health.get() - damage.getAmount());
        if (remaining <= 0f) {
            return false;
        }
        float ratio = remaining / health.getMax();
        return ratio > 0f && ratio <= 0.20f;
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

}
