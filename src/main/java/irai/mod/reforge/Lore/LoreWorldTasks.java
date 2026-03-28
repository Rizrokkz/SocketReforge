package irai.mod.reforge.Lore;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Shared helper for safely queueing work on the world thread.
 */
public final class LoreWorldTasks {
    private LoreWorldTasks() {}

    public static boolean queue(Store<EntityStore> store, Runnable task) {
        if (store == null || task == null || store.isShutdown()) {
            return false;
        }
        EntityStore entityStore = store.getExternalData();
        if (entityStore == null || entityStore.getWorld() == null) {
            return false;
        }
        entityStore.getWorld().execute(task);
        return true;
    }
}
