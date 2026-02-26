package irai.mod.reforge.Entity.Events;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Interactions.ReforgeEquip;
import irai.mod.reforge.Socket.SocketData;
import irai.mod.reforge.Socket.SocketManager;
import irai.mod.reforge.Util.DynamicTooltipUtils;

public class OpenGuiListener {

    // Scheduler for delayed tooltip refresh
    private static final ScheduledExecutorService tooltipScheduler = Executors.newSingleThreadScheduledExecutor();

    public static void openGui(PlayerReadyEvent event) {
        Player player = event.getPlayer();
        Ref<EntityStore> ref = event.getPlayerRef();
        Store<EntityStore> store = ref.getStore();
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        World world = player.getWorld();

        assert world != null;
        assert playerRef != null;
        
        // Scan player inventory and register tooltips for items with reforge/socket metadata
        scanAndRegisterTooltips(player);
    }
    
    /**
     * Scans player's inventory and registers tooltips for items with reforge/socket metadata
     */
    private static void scanAndRegisterTooltips(Player player) {
        if (!DynamicTooltipUtils.isAvailable()) {
            return;
        }
        
        int registeredCount = 0;
        
        // Scan hotbar
        ItemContainer hotbar = player.getInventory().getHotbar();
        registeredCount += scanContainer(hotbar);
        
        // Scan storage inventory
        ItemContainer storage = player.getInventory().getStorage();
        registeredCount += scanContainer(storage);
        
        if (registeredCount > 0) {
            System.out.println("[SocketReforge] Registered tooltips for " + registeredCount + " items on player join, refreshing in 2 seconds...");
            
            // Add delay before refreshing players to ensure client is ready
            tooltipScheduler.schedule(() -> {
                DynamicTooltipUtils.refreshAllPlayers();
                System.out.println("[SocketReforge] Tooltip refresh completed");
            }, 5, TimeUnit.SECONDS);
        }
    }
    
    /**
     * Scans an item container and registers tooltips for items with metadata
     */
    private static int scanContainer(ItemContainer container) {
        if (container == null) {
            return 0;
        }
        
        int count = 0;
        short capacity = container.getCapacity();
        
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack item = container.getItemStack(slot);
            
            if (item == null || item.isEmpty()) {
                continue;
            }
            
            // Check for reforge level
            int reforgeLevel = ReforgeEquip.getLevelFromItem(item);
            if (reforgeLevel > 0) {
                String itemId = item.getItemId();
                boolean isArmor = ReforgeEquip.isArmor(item);
                
                DynamicTooltipUtils.registerReforgeTooltip(itemId, reforgeLevel, isArmor);
                count++;
            }
            
            // Check for socket data
            SocketData socketData = SocketManager.getSocketData(item);
            if (socketData != null && socketData.getCurrentSocketCount() > 0) {
                String itemId = item.getItemId();
                
                // Use SocketData's built-in method to register all tooltips
                socketData.registerTooltips(itemId);
                count++;
            }
        }
        
        return count;
    }
}
