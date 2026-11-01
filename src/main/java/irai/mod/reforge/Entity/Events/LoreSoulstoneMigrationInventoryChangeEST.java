package irai.mod.reforge.Entity.Events;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.InventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.Transaction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Lore.LoreSoulstoneMigration;

/** Converts old lore socket metadata when equipment enters a player's inventory. */
public final class LoreSoulstoneMigrationInventoryChangeEST
        extends EntityEventSystem<EntityStore, InventoryChangeEvent> {

    public LoreSoulstoneMigrationInventoryChangeEST() {
        super(InventoryChangeEvent.class);
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
                       InventoryChangeEvent event) {
        if (chunk == null || store == null || event == null) {
            return;
        }
        Transaction transaction = event.getTransaction();
        if (transaction != null && !transaction.succeeded()) {
            return;
        }
        Player player = store.getComponent(chunk.getReferenceTo(index), Player.getComponentType());
        ItemContainer container = event.getItemContainer();
        if (!LoreSoulstoneMigration.isPlayerInventoryContainer(player, container)) {
            return;
        }
        LoreSoulstoneMigration.migrateContainer(container);
    }
}
