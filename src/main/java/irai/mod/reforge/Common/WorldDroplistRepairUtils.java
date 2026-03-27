package irai.mod.reforge.Common;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.spatial.SpatialData;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

/**
 * One-time repair tool for legacy worlds that saved empty-string droplists.
 */
public final class WorldDroplistRepairUtils {
    private WorldDroplistRepairUtils() {}

    public static WorldDroplistRepairResult clearEmptyDroplists(World world) {
        if (world == null) {
            return WorldDroplistRepairResult.error("World is not available.");
        }

        ChunkStore chunkStore = world.getChunkStore();
        if (chunkStore == null) {
            return WorldDroplistRepairResult.error("Chunk store unavailable.");
        }

        Store<ChunkStore> store = chunkStore.getStore();
        if (store == null) {
            return WorldDroplistRepairResult.error("Chunk store registry unavailable.");
        }

        BlockModule blockModule = BlockModule.get();
        if (blockModule == null) {
            return WorldDroplistRepairResult.error("Block module unavailable.");
        }

        ResourceType<ChunkStore, SpatialResource<Ref<ChunkStore>, ChunkStore>> resourceType =
                blockModule.getItemContainerSpatialResourceType();
        if (resourceType == null) {
            return WorldDroplistRepairResult.error("Item container spatial resource unavailable.");
        }

        SpatialResource<Ref<ChunkStore>, ChunkStore> resource = store.getResource(resourceType);
        if (resource == null) {
            return WorldDroplistRepairResult.error("Item container spatial resource not loaded.");
        }

        SpatialData<Ref<ChunkStore>> spatialData = resource.getSpatialData();
        if (spatialData == null || spatialData.size() == 0) {
            return new WorldDroplistRepairResult(0, 0, null);
        }

        int containersScanned = 0;
        int droplistsCleared = 0;

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

            containersScanned++;
            String droplist = containerBlock.getDroplist();
            if (droplist != null && droplist.isBlank()) {
                containerBlock.setDroplist(null);
                droplistsCleared++;
            }
        }

        return new WorldDroplistRepairResult(containersScanned, droplistsCleared, null);
    }

    public record WorldDroplistRepairResult(
            int containersScanned,
            int droplistsCleared,
            String error
    ) {
        public static WorldDroplistRepairResult error(String message) {
            return new WorldDroplistRepairResult(0, 0, message);
        }

        public boolean isSuccess() {
            return error == null || error.isBlank();
        }
    }
}
