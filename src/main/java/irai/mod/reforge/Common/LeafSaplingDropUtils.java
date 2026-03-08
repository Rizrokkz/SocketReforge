package irai.mod.reforge.Common;

import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.PhysicsDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.SoftBlockDropType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDrop;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
import com.hypixel.hytale.server.core.asset.type.item.config.container.ItemDropContainer;
import com.hypixel.hytale.server.core.asset.type.item.config.container.MultipleItemDropContainer;
import com.hypixel.hytale.server.core.asset.type.item.config.container.SingleItemDropContainer;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.bson.BsonDocument;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.StampedLock;

public final class LeafSaplingDropUtils {
    private static final boolean DEBUG_LOGGING = false;
    private static final String NO_LEAF = "";
    private static final Set<String> REGISTERED_DROP_LIST_IDS = ConcurrentHashMap.newKeySet();
    private static final Set<String> PATCHED_BLOCK_TYPE_IDS = ConcurrentHashMap.newKeySet();
    private static final Map<String, String> RESOLVED_LEAF_IDS_BY_BLOCK_TYPE = new ConcurrentHashMap<>();
    private static final Map<String, BlockType> CONFIGURED_LEAF_BLOCK_TYPES = new ConcurrentHashMap<>();
    private static final AtomicBoolean ALL_CONFIGURED_LEAVES_PATCHED = new AtomicBoolean(false);
    private static final double LEAF_FIBRE_CHANCE = 50.0d;
    private static final double LEAF_SAPLING_CHANCE = 5.0d;
    private static final double LEAF_PHYSICS_FIBRE_CHANCE = 2.0d;
    private static final double LEAF_PHYSICS_SAPLING_CHANCE = 5.0d;

    private LeafSaplingDropUtils() {}

    public static void onDamageBlock(DamageBlockEvent event) {
        if (event == null) {
            return;
        }

        BlockType blockType = event.getBlockType();
        String directLeafId = resolveLeafItemId(blockType);
        boolean relevantTreeBlock = directLeafId != null || TreeBlockUtils.isTrunkBlock(blockType);
        if (relevantTreeBlock) {
            debug("damage", "received block=" + describeBlock(blockType)
                    + ", item=" + describeItem(event.getItemInHand())
                    + ", cancelled=" + event.isCancelled());
        }

        if (event.isCancelled()) {
            if (relevantTreeBlock) {
                debug("damage", "skipped block=" + describeBlock(blockType)
                        + " because event was cancelled");
            }
            return;
        }

        if (directLeafId != null) {
            handleLeafBlock(blockType, directLeafId);
            return;
        }
        if (!isPlayerTriggered(event.getItemInHand())) {
            if (relevantTreeBlock) {
                debug("damage", "skipped trunk block=" + describeBlock(blockType)
                        + " because itemInHand was empty");
            }
            return;
        }

        handleTreeBlockEvent(blockType);
    }

    public static void onBreakBlock(BreakBlockEvent event) {
        if (event == null) {
            return;
        }

        BlockType blockType = event.getBlockType();
        String directLeafId = resolveLeafItemId(blockType);
        boolean relevantTreeBlock = directLeafId != null || TreeBlockUtils.isTrunkBlock(blockType);
        if (relevantTreeBlock) {
            debug("break", "received block=" + describeBlock(blockType)
                    + ", item=" + describeItem(event.getItemInHand())
                    + ", cancelled=" + event.isCancelled());
        }

        if (event.isCancelled()) {
            if (relevantTreeBlock) {
                debug("break", "skipped block=" + describeBlock(blockType)
                        + " because event was cancelled");
            }
            return;
        }

        if (directLeafId != null) {
            handleLeafBlock(blockType, directLeafId);
            return;
        }
        if (!isPlayerTriggered(event.getItemInHand())) {
            if (relevantTreeBlock) {
                debug("break", "skipped trunk block=" + describeBlock(blockType)
                        + " because itemInHand was empty");
            }
            return;
        }

        handleTreeBlockEvent(blockType);
    }

    public static void onExternalTreeTrunkTouched(BlockType blockType, String source) {
        if (blockType == null || blockType == BlockType.UNKNOWN || !TreeBlockUtils.isTrunkBlock(blockType)) {
            return;
        }

        debug("external", "tree trunk touched by " + source + ", block=" + describeBlock(blockType));
        handleTreeBlockEvent(blockType);
    }

    private static boolean isPlayerTriggered(ItemStack itemInHand) {
        return itemInHand != null && !itemInHand.isEmpty();
    }

    private static void handleTreeBlockEvent(BlockType blockType) {
        if (blockType == null || blockType == BlockType.UNKNOWN) {
            return;
        }

        try {
            if (!TreeBlockUtils.isTrunkBlock(blockType)) {
                return;
            }

            debug("trunk", "trunk event for block=" + describeBlock(blockType)
                    + ", patching configured leaf drop lists");
            injectAllConfiguredLeafDropLists();
        } catch (Exception e) {
            System.err.println("[SocketReforge] Failed to inject leaf sapling drop on break: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleLeafBlock(BlockType blockType, String directLeafId) {
        if (blockType == null || directLeafId == null) {
            return;
        }

        try {
            debug("leaf", "patching direct leaf block=" + describeBlock(blockType)
                    + ", leafId=" + directLeafId);
            ensureLeafDropListInjection(directLeafId, blockType);
        } catch (Exception e) {
            System.err.println("[SocketReforge] Failed to patch direct leaf drop list: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void injectAllConfiguredLeafDropLists() throws ReflectiveOperationException {
        if (ALL_CONFIGURED_LEAVES_PATCHED.get()) {
            debug("trunk", "configured leaf block types already patched");
            return;
        }

        int patchedCount = 0;
        int missingCount = 0;
        boolean patchedAll = true;
        for (Map.Entry<String, String> entry : TreeBlockUtils.getConfiguredTreeLeafDrops().entrySet()) {
            String leafId = entry.getKey();
            BlockType leafBlockType = resolveConfiguredLeafBlockType(leafId);
            if (leafBlockType == null || leafBlockType == BlockType.UNKNOWN) {
                patchedAll = false;
                missingCount++;
                continue;
            }
            if (ensureLeafDropListInjection(leafId, leafBlockType)) {
                patchedCount++;
            }
        }

        if (patchedAll) {
            ALL_CONFIGURED_LEAVES_PATCHED.set(true);
        }
        debug("trunk", "configured leaf patch complete: patched=" + patchedCount
                + ", missing=" + missingCount
                + ", total=" + TreeBlockUtils.getConfiguredTreeLeafDrops().size()
                + ", fullyPatched=" + patchedAll);
    }

    private static boolean ensureLeafDropListInjection(String leafId, BlockType blockType) throws ReflectiveOperationException {
        String blockTypeId = TreeBlockUtils.normalizeId(blockType == null ? null : blockType.getId());
        if (blockTypeId != null && PATCHED_BLOCK_TYPE_IDS.contains(blockTypeId)) {
            debug("patch", "already patched blockType=" + blockTypeId + ", leafId=" + leafId);
            return false;
        }

        String dropItemId = TreeBlockUtils.getDropItemIdForLeaf(leafId);
        if (dropItemId == null) {
            debug("patch", "skipped blockType=" + describeBlock(blockType) + " because no tree drop item matched leafId=" + leafId);
            return false;
        }

        String normalDropListId = "SocketReforge_" + leafId + "_Drops";
        String physicsDropListId = "SocketReforge_" + leafId + "_PhysicsDrops";

        ensureLeafDropListRegistered(normalDropListId, dropItemId, false);
        ensureLeafDropListRegistered(physicsDropListId, dropItemId, true);
        if (injectLeafDropListsIntoBlockType(blockType, normalDropListId, physicsDropListId)
                || hasInjectedDropLists(blockType.getGathering(), normalDropListId, physicsDropListId)) {
            if (blockTypeId != null) {
                PATCHED_BLOCK_TYPE_IDS.add(blockTypeId);
            }
            debug("patch", "patched blockType=" + describeBlock(blockType)
                    + ", leafId=" + leafId
                    + ", dropItemId=" + dropItemId
                    + ", normalDropList=" + normalDropListId
                    + ", physicsDropList=" + physicsDropListId);
            return true;
        }
        debug("patch", "no gathering change applied for blockType=" + describeBlock(blockType)
                + ", leafId=" + leafId);
        return false;
    }

    private static void ensureLeafDropListRegistered(String dropListId,
                                                     String saplingId,
                                                     boolean physics) throws ReflectiveOperationException {
        if (!REGISTERED_DROP_LIST_IDS.add(dropListId)) {
            return;
        }

        try {
            DefaultAssetMap<String, ItemDropList> dropListAssetMap = ItemDropList.getAssetMap();
            if (dropListAssetMap == null) {
                return;
            }

            ItemDropContainer[] containers = new ItemDropContainer[] {
                    createWeightedSingleDrop("Ingredient_Fibre", 1, 1, physics ? LEAF_PHYSICS_FIBRE_CHANCE : LEAF_FIBRE_CHANCE),
                    createWeightedSingleDrop(saplingId, 1, 1, physics ? LEAF_PHYSICS_SAPLING_CHANCE : LEAF_SAPLING_CHANCE)
            };
            ItemDropList injectedDropList = new ItemDropList(
                    dropListId,
                    new MultipleItemDropContainer(containers, 100.0d, 1, 1));
            putRuntimeAsset(dropListAssetMap, dropListId, injectedDropList);
        } catch (ReflectiveOperationException e) {
            REGISTERED_DROP_LIST_IDS.remove(dropListId);
            throw e;
        }
    }

    private static SingleItemDropContainer createWeightedSingleDrop(String itemId,
                                                                    int quantityMin,
                                                                    int quantityMax,
                                                                    double weight) {
        return new SingleItemDropContainer(
                new ItemDrop(itemId, new BsonDocument(), quantityMin, quantityMax),
                weight);
    }

    private static String resolveLeafItemId(BlockType blockType) {
        if (blockType == null || blockType == BlockType.UNKNOWN) {
            return null;
        }

        String normalizedBlockTypeId = TreeBlockUtils.normalizeId(blockType.getId());

        if (normalizedBlockTypeId != null) {
            String cachedLeafId = RESOLVED_LEAF_IDS_BY_BLOCK_TYPE.get(normalizedBlockTypeId);
            if (cachedLeafId != null) {
                return NO_LEAF.equals(cachedLeafId) ? null : cachedLeafId;
            }
        }

        String match = TreeBlockUtils.resolveLeafBlockId(blockType);

        if (normalizedBlockTypeId != null) {
            RESOLVED_LEAF_IDS_BY_BLOCK_TYPE.putIfAbsent(normalizedBlockTypeId, match == null ? NO_LEAF : match);
        }
        return match;
    }

    private static BlockType resolveConfiguredLeafBlockType(String leafId) {
        String normalizedLeafId = TreeBlockUtils.normalizeId(leafId);
        if (normalizedLeafId == null) {
            return null;
        }

        BlockType cachedBlockType = CONFIGURED_LEAF_BLOCK_TYPES.get(normalizedLeafId);
        if (cachedBlockType != null) {
            return cachedBlockType;
        }

        DefaultAssetMap<String, BlockType> blockTypeAssetMap = BlockType.getAssetMap();
        if (blockTypeAssetMap != null && blockTypeAssetMap.getAssetMap() != null) {
            BlockType directBlockType = blockTypeAssetMap.getAssetMap().get(normalizedLeafId);
            if (directBlockType != null && directBlockType != BlockType.UNKNOWN) {
                CONFIGURED_LEAF_BLOCK_TYPES.put(normalizedLeafId, directBlockType);
                return directBlockType;
            }
        }

        DefaultAssetMap<String, Item> itemAssetMap = Item.getAssetMap();
        if (itemAssetMap == null || itemAssetMap.getAssetMap() == null) {
            return null;
        }

        Item leafItem = itemAssetMap.getAssetMap().get(normalizedLeafId);
        if (leafItem == null || leafItem == Item.UNKNOWN || leafItem.getBlockId() == null) {
            return null;
        }

        if (blockTypeAssetMap == null || blockTypeAssetMap.getAssetMap() == null) {
            return null;
        }

        BlockType blockType = blockTypeAssetMap.getAssetMap().get(leafItem.getBlockId());
        if (blockType != null && blockType != BlockType.UNKNOWN) {
            CONFIGURED_LEAF_BLOCK_TYPES.put(normalizedLeafId, blockType);
        }
        return blockType;
    }

    private static boolean injectLeafDropListsIntoBlockType(BlockType blockType,
                                                            String normalDropListId,
                                                            String physicsDropListId)
            throws ReflectiveOperationException {
        BlockGathering currentGathering = blockType.getGathering();
        if (currentGathering == null) {
            return false;
        }

        if (hasInjectedDropLists(currentGathering, normalDropListId, physicsDropListId)) {
            return false;
        }

        BlockGathering gathering = cloneBlockGathering(currentGathering);
        boolean changed = false;

        SoftBlockDropType currentSoft = gathering.getSoft();
        if (currentSoft != null && !normalDropListId.equals(currentSoft.getDropListId())) {
            SoftBlockDropType updatedSoft = new SoftBlockDropType(
                    currentSoft.getItemId(),
                    normalDropListId,
                    currentSoft.isWeaponBreakable());
            setField(gathering, "soft", updatedSoft);
            changed = true;
        }

        PhysicsDropType currentPhysics = gathering.getPhysics();
        if (currentPhysics != null && !physicsDropListId.equals(currentPhysics.getDropListId())) {
            PhysicsDropType updatedPhysics = new PhysicsDropType(
                    currentPhysics.getItemId(),
                    physicsDropListId);
            setField(gathering, "physics", updatedPhysics);
            changed = true;
        }

        if (changed) {
            setField(blockType, "gathering", gathering);
            setField(blockType, "cachedPacket", null);
        }

        return changed;
    }

    private static boolean hasInjectedDropLists(BlockGathering gathering,
                                                String normalDropListId,
                                                String physicsDropListId) {
        if (gathering == null) {
            return false;
        }

        SoftBlockDropType soft = gathering.getSoft();
        PhysicsDropType physics = gathering.getPhysics();
        boolean softMatches = soft == null || normalDropListId.equals(soft.getDropListId());
        boolean physicsMatches = physics == null || physicsDropListId.equals(physics.getDropListId());
        return softMatches && physicsMatches;
    }

    private static BlockGathering cloneBlockGathering(BlockGathering source) throws ReflectiveOperationException {
        Constructor<BlockGathering> constructor = BlockGathering.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        BlockGathering clone = constructor.newInstance();
        copyField(source, clone, "breaking");
        copyField(source, clone, "harvest");
        copyField(source, clone, "soft");
        copyField(source, clone, "physics");
        copyField(source, clone, "toolDataRaw");
        copyField(source, clone, "toolData");
        copyField(source, clone, "useDefaultDropWhenPlaced");
        return clone;
    }

    private static boolean putRuntimeAsset(DefaultAssetMap<String, ?> assetMap,
                                           String assetId,
                                           Object assetValue) throws ReflectiveOperationException {
        StampedLock lock = getFieldValue(assetMap, "assetMapLock", StampedLock.class);
        long stamp = lock.writeLock();
        try {
            Map<String, Object> backingAssetMap = getFieldValue(assetMap, "assetMap", Map.class);
            Object previous = backingAssetMap.put(assetId, assetValue);

            Map<String, Object> assetChainMap = getFieldValue(assetMap, "assetChainMap", Map.class);
            assetChainMap.put(assetId, createAssetRefArray(assetValue));

            Map<String, Set<String>> packAssetKeys = getFieldValue(assetMap, "packAssetKeys", Map.class);
            Set<String> defaultPackKeys = packAssetKeys.get(DefaultAssetMap.DEFAULT_PACK_KEY);
            if (defaultPackKeys != null) {
                defaultPackKeys.add(assetId);
            }

            return previous != assetValue;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    private static Object createAssetRefArray(Object assetValue) throws ReflectiveOperationException {
        Class<?> assetRefClass = Class.forName("com.hypixel.hytale.assetstore.map.DefaultAssetMap$AssetRef");
        Constructor<?> constructor = assetRefClass.getDeclaredConstructor(String.class, java.nio.file.Path.class, Object.class);
        constructor.setAccessible(true);

        Object assetRef = constructor.newInstance(DefaultAssetMap.DEFAULT_PACK_KEY, null, assetValue);
        Object assetRefArray = Array.newInstance(assetRefClass, 1);
        Array.set(assetRefArray, 0, assetRef);
        return assetRefArray;
    }

    private static void setField(Object target, String fieldName, Object value) throws ReflectiveOperationException {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void copyField(Object source, Object target, String fieldName) throws ReflectiveOperationException {
        Field field = findField(source.getClass(), fieldName);
        field.setAccessible(true);
        field.set(target, field.get(source));
    }

    private static <T> T getFieldValue(Object target, String fieldName, Class<T> type) throws ReflectiveOperationException {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        return type.cast(field.get(target));
    }

    private static Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    private static String describeBlock(BlockType blockType) {
        if (blockType == null) {
            return "null";
        }
        return String.valueOf(TreeBlockUtils.normalizeId(blockType.getId()));
    }

    private static String describeItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return "<empty>";
        }
        return itemStack.getItemId();
    }

    private static void debug(String stage, String message) {
        if (!DEBUG_LOGGING || message == null || message.isBlank()) {
            return;
        }
        System.out.println("[SocketReforge][TREE_DROP][" + stage + "] " + message);
    }
}
