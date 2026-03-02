package irai.mod.reforge.Entity.Events;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Socket.EssenceEffect;

/**
 * Applies passive health regeneration from Water-essence armor sockets.
 */
public class WaterRegenSystem extends EntityTickingSystem<EntityStore> {

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public boolean isParallel(int from, int to) {
        return false;
    }

    @Override
    public void tick(float time,
                     int index,
                     ArchetypeChunk<EntityStore> chunk,
                     Store<EntityStore> store,
                     CommandBuffer<EntityStore> commandBuffer) {
        try {
            Player player = store.getComponent(chunk.getReferenceTo(index), Player.getComponentType());
            EntityStatMap statMap = store.getComponent(chunk.getReferenceTo(index), EntityStatMap.getComponentType());
            if (player == null || statMap == null || time <= 0f) return;

            int healthStatIndex = getHealthStatIndex(statMap);
            if (healthStatIndex < 0) return;

            float regenPerSecond = (float) SocketArmorBonusHelper.getScaledFlatBonus(player, EssenceEffect.StatType.REGENERATION);
            if (regenPerSecond <= 0f) return;

            EntityStatValue health = statMap.get(healthStatIndex);
            if (health == null || health.get() >= health.getMax()) return;

            float healAmount = regenPerSecond * time;
            if (healAmount <= 0f) return;

            statMap.addStatValue(healthStatIndex, healAmount);
        } catch (Throwable t) {
            System.err.println("[SocketReforge] WaterRegenSystem tick error: " + t.getMessage());
            t.printStackTrace();
        }
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
