package irai.mod.reforge.Entity.Events;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Event-independent fallback scanner for open chest windows.
 */
@SuppressWarnings("removal")
public final class ChestWindowSocketLootEST extends EntityTickingSystem<EntityStore> {

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
        Player player = store.getComponent(chunk.getReferenceTo(index), Player.getComponentType());
        if (player == null || player.getWindowManager() == null) {
            return;
        }

        if (player.getWindowManager().getWindows() == null || player.getWindowManager().getWindows().isEmpty()) {
            return;
        }

        TreasureChestSocketLootListener.scanOpenChestWindows(player, "ecs_tick");
    }
}
