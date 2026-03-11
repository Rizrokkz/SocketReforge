package irai.mod.reforge.Common;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.spatial.SpatialData;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.meta.BlockStateModule;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerState;
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

        BlockStateModule blockStateModule = BlockStateModule.get();
        if (blockStateModule == null) {
            return WorldContainerScanResult.error("BlockState module unavailable.");
        }

        ResourceType<ChunkStore, SpatialResource<Ref<ChunkStore>, ChunkStore>> resourceType =
                blockStateModule.getItemContainerSpatialResourceType();
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
            Ref<ChunkStore> ref = spatialData.getData(i);
            if (ref == null) {
                continue;
            }

            Store<ChunkStore> refStore = ref.getStore();
            if (refStore == null) {
                continue;
            }

            BlockState blockState = BlockState.getBlockState(ref, refStore);
            if (!(blockState instanceof ItemContainerState containerState)) {
                continue;
            }

            containersScanned++;
            int migrated = OpenGuiListener.migrateResonantItemsInContainer(containerState.getItemContainer());
            if (migrated > 0) {
                itemsMigrated += migrated;
                containersUpdated++;
                containerState.markNeedsSave();
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
