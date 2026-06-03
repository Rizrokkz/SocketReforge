package irai.mod.reforge.Entity.Events;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.InventorySetActiveSlotEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Common.PlayerInventoryUtils;
import irai.mod.reforge.Util.DynamicTooltipUtils;
import irai.mod.reforge.Util.LangLoader;

@SuppressWarnings("removal")
public final class NativeTooltipInventoryEventEST extends EntityEventSystem<EntityStore, InventorySetActiveSlotEvent> {

    public NativeTooltipInventoryEventEST() {
        super(InventorySetActiveSlotEvent.class);
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
                       InventorySetActiveSlotEvent event) {
        Player player = store.getComponent(chunk.getReferenceTo(index), Player.getComponentType());
        if (player == null || player.getInventory() == null || event == null) {
            return;
        }

        String locale = LangLoader.getPlayerLanguage(player);
        Inventory inventory = player.getInventory();
        ItemContainer container = null;
        try {
            container = inventory.getSectionById(event.getInventorySectionId());
        } catch (Exception ignored) {
        }
        if (container != null) {
            DynamicTooltipUtils.refreshContainerSlot(container, event.getNewSlot(), locale);
            return;
        }

        PlayerInventoryUtils.HeldItemContext held = PlayerInventoryUtils.getHeldItemContext(player);
        if (held != null && held.getContainer() != null && held.getSlot() >= 0) {
            DynamicTooltipUtils.refreshContainerSlot(held.getContainer(), held.getSlot(), locale);
        }
    }
}
