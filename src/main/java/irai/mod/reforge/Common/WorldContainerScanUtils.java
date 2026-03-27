package irai.mod.reforge.Common;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.spatial.SpatialData;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import irai.mod.reforge.Entity.Events.OpenGuiListener;

/**
 * Utility for scanning loaded world containers and migrating resonant items/recipes.
 */
public final class WorldContainerScanUtils {
    private WorldContainerScanUtils() {}

    public static WorldContainerScanResult scanWorldContainers(World world) {
        if (world == null) {
            return WorldContainerScanResult.error("World is not available.");
        }

        ChunkStore chunkStore = world.getChunkStore();
        if (chunkStore == null) {
            return WorldContainerScanResult.error("Chunk store unavailable.");
        }

        Store<ChunkStore> store = chunkStore.getStore();
        if (store == null) {
            return WorldContainerScanResult.error("Chunk store registry unavailable.");
        }

        BlockModule blockModule = BlockModule.get();
        if (blockModule == null) {
            return WorldContainerScanResult.error("Block module unavailable.");
        }

        ResourceType<ChunkStore, SpatialResource<Ref<ChunkStore>, ChunkStore>> resourceType =
                blockModule.getItemContainerSpatialResourceType();
        if (resourceType == null) {
            return WorldContainerScanResult.error("Item container spatial resource unavailable.");
        }

        SpatialResource<Ref<ChunkStore>, ChunkStore> resource = store.getResource(resourceType);
        if (resource == null) {
            return WorldContainerScanResult.error("Item container spatial resource not loaded.");
        }

        SpatialData<Ref<ChunkStore>> spatialData = resource.getSpatialData();
        if (spatialData == null || spatialData.size() == 0) {
            return new WorldContainerScanResult(0, 0, 0, null);
        }

        int containersScanned = 0;
        int containersUpdated = 0;
        int itemsMigrated = 0;

        for (int i = 0; i < spatialData.size(); i++) {
            Vector3d position = spatialData.getVector(i);
            if (position == null) {
                continue;
            }
            int x = (int) Math.floor(position.getX());
            int y = (int) Math.floor(position.getY());
            int z = (int) Math.floor(position.getZ());

            ItemContainerBlock containerBlock = BlockModule.getComponent(
                    blockModule.getItemContainerBlockComponentType(),
                    world,
                    x,
                    y,
                    z);
            if (containerBlock == null) {
                continue;
            }

            ItemContainer container = containerBlock.getItemContainer();
            if (container == null) {
                continue;
            }

            containersScanned++;
            int migrated = OpenGuiListener.migrateResonantItemsInContainer(container);
            if (migrated > 0) {
                itemsMigrated += migrated;
                containersUpdated++;
            }
        }

        return new WorldContainerScanResult(containersScanned, containersUpdated, itemsMigrated, null);
    }

    public record WorldContainerScanResult(
            int containersScanned,
            int containersUpdated,
            int itemsMigrated,
            String error
    ) {
        public static WorldContainerScanResult error(String message) {
            return new WorldContainerScanResult(0, 0, 0, message);
        }

        public boolean isSuccess() {
            return error == null || error.isBlank();
        }
    }
}
