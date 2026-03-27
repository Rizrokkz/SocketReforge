package irai.mod.reforge.Util;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Utility methods shared across interaction classes.
 * Eliminates duplicate interaction logic.
 */
public final class InteractionUtils {
    private InteractionUtils() {} // Prevent instantiation

    /**
     * Extracts a Player from an InteractionContext.
     * Returns null if the context has no owning entity.
     *
     * @param context the interaction context
     * @return the player, or null if not available
     */
    public static Player getPlayerFromContext(InteractionContext context) {
        if (context == null) {
            return null;
        }
        Ref<EntityStore> owningEntity = context.getOwningEntity();
        if (owningEntity == null) return null;
        Store<EntityStore> store = owningEntity.getStore();
        if (store == null) return null;
        return store.getComponent(owningEntity, Player.getComponentType());
    }
}
