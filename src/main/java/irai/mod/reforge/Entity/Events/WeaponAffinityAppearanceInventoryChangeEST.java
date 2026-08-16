package irai.mod.reforge.Entity.Events;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.InventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Common.WeaponAffinityAppearanceState;

/**
 * Refreshes the held-weapon affinity visuals whenever the player's held inventory containers
 * (hotbar / tools / utility) change.
 *
 * <p>{@code InventorySetActiveSlotEvent} only fires when the active {@code slot index} changes.
 * Replacing the item in the already-active slot (drag &amp; drop through a GUI, auto-pickup,
 * console give) fires {@code InventoryChangeEvent} instead, leaving the appearance stat stale
 * (e.g. always showing the previously held weapon's affinity such as Fire). This system closes
 * that gap by re-deriving the appearance from the current held item after any such change.
 */
public final class WeaponAffinityAppearanceInventoryChangeEST
        extends EntityEventSystem<EntityStore, InventoryChangeEvent> {

    public WeaponAffinityAppearanceInventoryChangeEST() {
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
        if (store == null || chunk == null || event == null) {
            return;
        }
        Ref<EntityStore> playerRef = chunk.getReferenceTo(index);
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null || player.getInventory() == null) {
            return;
        }
        if (!isHeldContainer(player.getInventory(), event.getItemContainer())) {
            return;
        }
        // The container already reflects the change at this point, so the appearance state is
        // recomputed from the item currently in the active held slot.
        WeaponAffinityAppearanceState.refresh(player, playerRef);
        WeaponAffinityAppearanceState.refreshDeferred(player);
    }

    private static boolean isHeldContainer(Inventory inventory, ItemContainer container) {
        if (inventory == null || container == null) {
            return false;
        }
        try {
            return inventory.getHotbar() == container
                    || inventory.getTools() == container
                    || inventory.getUtility() == container;
        } catch (Exception ignored) {
            return false;
        }
    }
}
