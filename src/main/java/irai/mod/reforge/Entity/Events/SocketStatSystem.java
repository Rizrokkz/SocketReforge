package irai.mod.reforge.Entity.Events;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Socket.EssenceEffect;

/**
 * Tracks balanced defensive armor socket bonuses for each player while equipped.
 * This provides a central source for EVASION/DEFENSE/FIRE_DEFENSE values.
 */
public class SocketStatSystem extends EntityTickingSystem<EntityStore> {

    public record DefensiveBonuses(double defensePercent, double fireDefensePercent, double evasionPercent) {}

    private static final Map<UUID, DefensiveBonuses> DEFENSIVE_CACHE = new ConcurrentHashMap<>();

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
            if (player == null) return;

            double defense = SocketArmorBonusHelper.getScaledPercentBonus(player, EssenceEffect.StatType.DEFENSE);
            double fireDefense = SocketArmorBonusHelper.getScaledPercentBonus(player, EssenceEffect.StatType.FIRE_DEFENSE);
            double evasion = SocketArmorBonusHelper.getScaledPercentBonus(player, EssenceEffect.StatType.EVASION);

            UUID uuid = player.getUuid();
            if (uuid != null) {
                DEFENSIVE_CACHE.put(uuid, new DefensiveBonuses(defense, fireDefense, evasion));
            }
        } catch (Throwable t) {
            System.err.println("[SocketReforge] SocketStatSystem tick error: " + t.getMessage());
            t.printStackTrace();
        }
    }

    public static DefensiveBonuses getDefensiveBonuses(Player player) {
        if (player == null) return new DefensiveBonuses(0.0, 0.0, 0.0);
        UUID uuid = player.getUuid();
        if (uuid == null) return new DefensiveBonuses(0.0, 0.0, 0.0);
        return DEFENSIVE_CACHE.getOrDefault(uuid, new DefensiveBonuses(0.0, 0.0, 0.0));
    }
}
