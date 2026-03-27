package irai.mod.reforge.Entity.Events;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
import irai.mod.reforge.Common.ResonantRecipeUtils;
import irai.mod.reforge.Socket.SocketData;
import irai.mod.reforge.Socket.Socket;
import irai.mod.reforge.Socket.Essence;
import irai.mod.reforge.Socket.EssenceRegistry;
import irai.mod.reforge.Socket.ResonanceSystem;
import irai.mod.reforge.Socket.SocketManager;
import irai.mod.reforge.Util.DynamicTooltipUtils;

public class OpenGuiListener {

    // Scheduler for delayed tooltip refresh
    private static final ScheduledExecutorService tooltipScheduler = Executors.newSingleThreadScheduledExecutor();
    private static final AtomicBoolean refreshQueued = new AtomicBoolean(false);

    public static void openGui(PlayerReadyEvent event) {
        Ref<EntityStore> ref = event.getPlayerRef();
        Player player = event.getPlayer();
        World world = player.getWorld();
        if (world == null) {
            return;
        }
        world.execute(() -> openGuiOnWorldThread(ref));
    }

    private static void openGuiOnWorldThread(Ref<EntityStore> ref) {
        if (ref == null) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        // Scan player inventory and register tooltips for items with reforge/socket metadata
        scanAndRegisterTooltips(player);
    }
    
    /**
     * Scans player's inventory and registers tooltips for items with reforge/socket metadata
     */
    private static void scanAndRegisterTooltips(Player player) {
        boolean tooltipsAvailable = DynamicTooltipUtils.isAvailable();

        // Ensure the dynamic metadata provider is registered when tooltips are available.
        if (tooltipsAvailable) {
            DynamicTooltipUtils.ensureProviderRegistered();
        }
        
        int registeredCount = 0;
        int migratedCount = 0;
        
        // Scan hotbar
        ItemContainer hotbar = player.getInventory().getHotbar();
        migratedCount += migrateResonantRecipes(hotbar);
        migratedCount += migrateResonantEquipment(hotbar);
        if (tooltipsAvailable) {
            registeredCount += scanContainer(hotbar);
        }
        
        // Scan storage inventory
        ItemContainer storage = player.getInventory().getStorage();
        migratedCount += migrateResonantRecipes(storage);
        migratedCount += migrateResonantEquipment(storage);
        if (tooltipsAvailable) {
            registeredCount += scanContainer(storage);
        }

        // Scan equipped armor so join-time tooltip cache is rebuilt even when
        // the player has no reforge/socket items in hotbar/storage.
        ItemContainer armor = player.getInventory().getArmor();
        migratedCount += migrateResonantEquipment(armor);
        if (tooltipsAvailable) {
            registeredCount += scanContainer(armor);
        }
        
        // Always refresh after player join so dynamic metadata tooltips are rebuilt,
        // even if there were no pre-cached tooltip lines this session.
        if (tooltipsAvailable && (registeredCount > 0 || migratedCount > 0)) {
            System.out.println("[SocketReforge] Registered tooltips for " + registeredCount + " items on player join; refreshing shortly...");
        } else if (tooltipsAvailable) {
            System.out.println("[SocketReforge] Player join tooltip refresh queued (provider-only path).");
        }
        if (tooltipsAvailable) {
            scheduleRefresh(1200, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Scans a non-inventory container (e.g. open chest window) and triggers a debounced tooltip refresh.
     */
    public static void scanAndRegisterTooltipsForContainer(ItemContainer container) {
        if (container == null) {
            return;
        }

        int migrated = migrateResonantItemsInContainer(container);
        if (DynamicTooltipUtils.isAvailable()) {
            int registeredCount = scanContainer(container);
            if (registeredCount > 0 || migrated > 0) {
                // Short delay helps ensure update-window packets are already in flight.
                scheduleRefresh(300, TimeUnit.MILLISECONDS);
            }
        }
    }

    public static int migrateResonantItemsInContainer(ItemContainer container) {
        if (container == null) {
            return 0;
        }
        int migrated = migrateResonantRecipes(container);
        migrated += migrateResonantEquipment(container);
        return migrated;
    }

    private static void scheduleRefresh(long delay, TimeUnit unit) {
        if (!refreshQueued.compareAndSet(false, true)) {
            return;
        }
        tooltipScheduler.schedule(() -> {
            try {
                DynamicTooltipUtils.refreshAllPlayers();
                System.out.println("[SocketReforge] Tooltip refresh completed");
            } finally {
                refreshQueued.set(false);
            }
        }, delay, unit);
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
            
            boolean hasRelevantMetadata = false;

            // Check for reforge level
            int reforgeLevel = ReforgeEquip.getLevelFromItem(item);
            if (reforgeLevel > 0) {
                hasRelevantMetadata = true;
            }

            // Check for socket data
            SocketData socketData = SocketManager.getSocketData(item);
            if (socketData != null && socketData.getCurrentSocketCount() > 0) {
                hasRelevantMetadata = true;
            }

            if (hasRelevantMetadata) {
                count++;
            }
        }
        
        return count;
    }

    private static int migrateResonantRecipes(ItemContainer container) {
        if (container == null) {
            return 0;
        }
        int updated = 0;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack item = container.getItemStack(slot);
            if (item == null || item.isEmpty()) {
                continue;
            }
            if (!ResonantRecipeUtils.isResonantRecipeItem(item)) {
                continue;
            }
            if (!LootSocketRoller.hasResonantRecipeMetadata(item)) {
                continue;
            }
            ItemStack migrated = ResonantRecipeUtils.remapResonantRecipePattern(item);
            ItemStack updatedItem = ResonantRecipeUtils.ensureRecipeUsages(migrated);
            if (updatedItem != item) {
                container.setItemStackForSlot(slot, updatedItem);
                updated++;
            }
        }
        return updated;
    }

    private static int migrateResonantEquipment(ItemContainer container) {
        if (container == null) {
            return 0;
        }
        int updated = 0;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack item = container.getItemStack(slot);
            if (item == null || item.isEmpty()) {
                continue;
            }
            if (!ReforgeEquip.isWeapon(item) && !ReforgeEquip.isArmor(item)) {
                continue;
            }
            String resonanceName = SocketManager.getResonanceName(item);
            SocketData existing = SocketManager.getSocketData(item);
            if (resonanceName == null || resonanceName.isBlank()) {
                if (existing != null) {
                    ResonanceSystem.ResonanceResult raw = ResonanceSystem.evaluate(item, existing);
                    if (raw != null && raw.active()) {
                        resonanceName = raw.name();
                    }
                }
            }
            if (resonanceName == null || resonanceName.isBlank()) {
                continue;
            }
            Essence.Type[] pattern = ResonanceSystem.getPatternForRecipeName(resonanceName);
            if (pattern == null || pattern.length == 0) {
                continue;
            }
            SocketData remapped = buildSocketDataForPattern(pattern, existing);
            if (remapped == null) {
                continue;
            }
            ItemStack unlocked = SocketManager.withResonanceUnlock(item, resonanceName);
            ItemStack migrated = SocketManager.withSocketData(unlocked, remapped);
            if (migrated != item) {
                container.setItemStackForSlot(slot, migrated);
                updated++;
            }
        }
        return updated;
    }

    private static SocketData buildSocketDataForPattern(Essence.Type[] pattern, SocketData existing) {
        if (pattern == null || pattern.length == 0) {
            return null;
        }
        SocketData data = new SocketData(pattern.length);
        List<Socket> existingSockets = existing != null ? existing.getSockets() : List.of();
        for (int i = 0; i < pattern.length; i++) {
            data.addSocket();
            Essence.Type type = pattern[i];
            if (type == null) {
                continue;
            }
            boolean useGreater = false;
            if (i < existingSockets.size()) {
                Socket existingSocket = existingSockets.get(i);
                if (existingSocket != null && !existingSocket.isEmpty() && !existingSocket.isBroken()) {
                    useGreater = SocketManager.isGreaterEssenceId(existingSocket.getEssenceId());
                }
            }
            String essenceId = SocketManager.buildEssenceId(type.name(), useGreater);
            if (essenceId == null || !EssenceRegistry.get().exists(essenceId)) {
                essenceId = SocketManager.buildEssenceId(type.name(), false);
            }
            if (essenceId == null || !EssenceRegistry.get().exists(essenceId)) {
                continue;
            }
            data.setEssenceAt(i, essenceId);
        }
        return data;
    }
}
