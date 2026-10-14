package irai.mod.reforge.Common;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.blockhealth.BlockHealthSection;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public final class WorldBlockUtils {
    private static final float FULL_BLOCK_HEALTH = 1.0f;

    private WorldBlockUtils() {}

    public static Ref<ChunkStore> getLoadedSectionRef(World world, int x, int y, int z) {
        if (world == null || world.getChunkStore() == null) {
            return null;
        }
        Ref<ChunkStore> sectionRef = world.getChunkStore().getChunkSectionReferenceAtBlock(x, y, z);
        return sectionRef != null && sectionRef.isValid() ? sectionRef : null;
    }

    public static BlockType getBlockType(World world, int x, int y, int z) {
        if (world == null || world.getChunkStore() == null) {
            return null;
        }
        try {
            ChunkStore chunkStore = world.getChunkStore();
            Ref<ChunkStore> sectionRef = getLoadedSectionRef(world, x, y, z);
            if (sectionRef == null) {
                return null;
            }
            Store<ChunkStore> store = chunkStore.getStore();
            if (store == null) {
                return null;
            }
            BlockSection section = store.getComponent(sectionRef, BlockSection.getComponentType());
            if (section == null) {
                return null;
            }
            int blockIndex = section.get(x, y, z);
            return BlockType.getAssetMap().getAssetOrDefault(blockIndex, BlockType.UNKNOWN);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean setBlock(World world, int x, int y, int z, String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return false;
        }
        BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
        if (blockType == null) {
            blockType = BlockType.fromString(blockId);
        }
        return setBlock(world, x, y, z, blockType);
    }

    public static boolean setBlock(World world, int x, int y, int z, BlockType blockType) {
        if (world == null || world.getChunkStore() == null || blockType == null || blockType.isUnknown()) {
            return false;
        }
        try {
            ChunkStore chunkStore = world.getChunkStore();
            Ref<ChunkStore> sectionRef = getLoadedSectionRef(world, x, y, z);
            if (sectionRef == null) {
                return false;
            }
            int blockIndex = BlockType.getAssetMap().getIndexOrDefault(blockType.getId(), BlockType.UNKNOWN_ID);
            if (blockIndex == BlockType.UNKNOWN_ID) {
                return false;
            }
            return BlockOperations.setBlock(
                    chunkStore,
                    sectionRef,
                    ChunkUtil.localCoordinate(x),
                    ChunkUtil.localCoordinate(y),
                    ChunkUtil.localCoordinate(z),
                    blockIndex,
                    blockType,
                    0,
                    0,
                    0);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static float getBlockHealth(World world, int x, int y, int z) {
        if (world == null || world.getChunkStore() == null) {
            return FULL_BLOCK_HEALTH;
        }
        try {
            Ref<ChunkStore> sectionRef = getLoadedSectionRef(world, x, y, z);
            if (sectionRef == null) {
                return FULL_BLOCK_HEALTH;
            }
            Store<ChunkStore> store = world.getChunkStore().getStore();
            if (store == null) {
                return FULL_BLOCK_HEALTH;
            }
            BlockHealthSection healthSection = store.getComponent(sectionRef, BlockHealthSection.getComponentType());
            return healthSection == null ? FULL_BLOCK_HEALTH : healthSection.getHealth(x, y, z);
        } catch (Throwable ignored) {
            return FULL_BLOCK_HEALTH;
        }
    }
}
