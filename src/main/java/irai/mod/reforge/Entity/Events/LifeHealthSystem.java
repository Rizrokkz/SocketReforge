package irai.mod.reforge.Entity.Events;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatsSystems;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Socket.EssenceEffect;

/**
 * Applies Life-essence armor health bonuses to player max health while equipped.
 */
public class LifeHealthSystem extends EntityTickingSystem<EntityStore> implements EntityStatsSystems.StatModifyingSystem {

    private static final String HEALTH_BONUS_MODIFIER_ID = "socket_reforge.armor.health_bonus";

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
            if (player == null || statMap == null) return;
            int healthStatIndex = getHealthStatIndex(statMap);
            if (healthStatIndex < 0) return;

            float healthBonus = (float) SocketArmorBonusHelper.getScaledFlatBonus(player, EssenceEffect.StatType.HEALTH);

            if (healthBonus > 0f) {
                statMap.putModifier(
                        healthStatIndex,
                        HEALTH_BONUS_MODIFIER_ID,
                        new StaticModifier(Modifier.ModifierTarget.MAX, StaticModifier.CalculationType.ADDITIVE, healthBonus)
                );
            } else {
                statMap.removeModifier(healthStatIndex, HEALTH_BONUS_MODIFIER_ID);
            }

            // Clamp current health to max when bonus is removed.
            EntityStatValue health = statMap.get(healthStatIndex);
            if (health != null && health.get() > health.getMax()) {
                statMap.setStatValue(healthStatIndex, health.getMax());
            }
        } catch (Throwable t) {
            System.err.println("[SocketReforge] LifeHealthSystem tick error: " + t.getMessage());
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
