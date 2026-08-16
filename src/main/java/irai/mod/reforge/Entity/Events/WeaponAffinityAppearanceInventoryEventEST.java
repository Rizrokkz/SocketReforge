package irai.mod.reforge.Entity.Events;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.InventorySetActiveSlotEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Common.PlayerInventoryUtils;
import irai.mod.reforge.Common.WeaponAffinityAppearanceState;

/**
 * Refreshes the held-weapon affinity visuals when the player changes their selected
 * hotbar / tools / utility slot (the slot-selection case).
 *
 * <p>{@code InventorySetActiveSlotEvent} is dispatched while the inventory's active-slot pointer
 * has not yet been advanced, so {@link PlayerInventoryUtils#getHeldItem(Player)} would resolve to
 * the <em>previously</em> selected weapon and the appearance stat would stay stale (every
 * affinity appeared to keep showing the last-switched one, e.g. Fire). Instead this handler reads
 * the weapon at the event's new slot directly -- mirroring the sibling
 * {@code NativeTooltipInventoryEventEST} -- and feeds it to the 3-arg/4-arg refresh the Essence
 * Socket Bench uses, passing the chunk-provided {@link Ref playerRef} so the refresh does not
 * depend on {@code world.getEntityRef(uuid)} resolving at event time.
 *
 * <p>{@link WeaponAffinityAppearanceState#refresh(Player, Ref, ItemStack, ItemStack)}'s own javadoc
 * states this is exactly the path to use from {@code InventorySetActiveSlotEvent} handlers.
 */
public final class WeaponAffinityAppearanceInventoryEventEST
        extends EntityEventSystem<EntityStore, InventorySetActiveSlotEvent> {

    public WeaponAffinityAppearanceInventoryEventEST() {
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
        if (store == null || chunk == null || event == null) {
            return;
        }
        int sectionId = event.getInventorySectionId();
        if (!isHeldSection(sectionId)) {
            return;
        }
        Ref<EntityStore> playerRef = chunk.getReferenceTo(index);
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null || player.getInventory() == null) {
            return;
        }

        // Read the weapon at the slot the player is switching TO (post-apply of the item move,
        // regardless of whether the active-slot pointer has caught up yet).
        ItemStack newSlotItem = readItemAtActiveSlot(player, sectionId, event.getNewSlot());

        if (sectionId == PlayerInventoryUtils.UTILITY_SECTION_ID) {
            // Utility slot changed: primary stays the held hotbar/tools weapon, secondary is the
            // newly slotted utility item.
            WeaponAffinityAppearanceState.refresh(player, playerRef,
                    PlayerInventoryUtils.getHeldItem(player), newSlotItem);
        } else {
            // Hotbar / tools slot changed: that slot's item is the new primary weapon.
            WeaponAffinityAppearanceState.refresh(player, playerRef, newSlotItem);
        }
        WeaponAffinityAppearanceState.refreshDeferred(player);
    }

    private static ItemStack readItemAtActiveSlot(Player player, int sectionId, byte newSlot) {
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return null;
        }
        try {
            ItemContainer container = inventory.getSectionById(sectionId);
            if (container == null) {
                return null;
            }
            return container.getItemStack(newSlot);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isHeldSection(int sectionId) {
        return sectionId == PlayerInventoryUtils.HOTBAR_SECTION_ID
                || sectionId == PlayerInventoryUtils.TOOLS_SECTION_ID
                || sectionId == PlayerInventoryUtils.UTILITY_SECTION_ID;
    }
}
