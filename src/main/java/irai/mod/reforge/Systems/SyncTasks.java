package irai.mod.reforge.Systems;

import com.hypixel.hytale.server.core.entity.entities.Player;
import irai.mod.reforge.Entity.Events.ContainerEventListener;

import java.util.Collection;

/**
 * Periodic task that syncs weapon upgrades for all online players.
 * This ensures weapon display names stay correct even if they're moved around.
 *
 * Run this every 30 seconds or 1 minute.
 */
public class SyncTasks implements Runnable {

    private final Collection<Player> players;
    private boolean cancelled = false;

    /**
     * @param players Collection of online players (from PlayerManager)
     */
    public SyncTasks(Collection<Player> players) {
        this.players = players;
    }

    @Override
    public void run() {
        if (cancelled) {
            return;
        }

        try {
            int syncedPlayers = 0;

            for (Player player : players) {
                if (player == null) {
                    continue;
                }

                // Only sync players who have upgraded weapons
                if (ContainerEventListener.hasUpgradedWeapons(player)) {
                    ContainerEventListener.syncPlayerInventory(player);
                    syncedPlayers++;
                }
            }

            if (syncedPlayers > 0) {
                System.out.println("[WeaponSyncTask] Synced weapons for " + syncedPlayers + " players");
            }

        } catch (Exception e) {
            System.err.println("[WeaponSyncTask] Error during sync: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void cancel() {
        this.cancelled = true;
    }
}
