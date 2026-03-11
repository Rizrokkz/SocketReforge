package irai.mod.reforge.Common;

import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDrop;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
import com.hypixel.hytale.server.core.asset.type.item.config.container.ItemDropContainer;
import com.hypixel.hytale.server.core.asset.type.item.config.container.MultipleItemDropContainer;
import com.hypixel.hytale.server.core.asset.type.item.config.container.SingleItemDropContainer;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import org.bson.BsonDocument;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import irai.mod.reforge.Config.LootSocketRollConfig;

public final class CropEssenceDropUtils {
    private static final boolean DEBUG_LOGGING = false;
    private static final String WATER_ESSENCE_ID = "Ingredient_Water_Essence";
    private static final String LIGHTNING_ESSENCE_ID = "Ingredient_Lightning_Essence";
    private static volatile double cropWaterEssenceChance = 0.05d;
    private static volatile double cropLightningEssenceChance = 0.15d;
    private static volatile int cropWaterEssenceMinQuantity = 1;
    private static volatile int cropWaterEssenceMaxQuantity = 5;
    private static volatile int cropLightningEssenceMinQuantity = 1;
    private static volatile int cropLightningEssenceMaxQuantity = 5;
    private static final Set<String> STAMINA_CROP_IDS = Set.of(
            "Plant_Crop_Stamina1",
            "Plant_Crop_Stamina2",
            "Plant_Crop_Stamina3"
    );
    private static final Set<String> PATCHED_DROP_LIST_IDS = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean SCAN_COMPLETE = new AtomicBoolean(false);

    private CropEssenceDropUtils() {}

    public static void onServerStart() {
        ensureCropDropListsPatched();
    }

    public static void setConfig(LootSocketRollConfig config) {
        if (config == null) {
            return;
        }
        cropWaterEssenceChance = clamp01(config.getCropWaterEssenceChance());
        cropLightningEssenceChance = clamp01(config.getCropLightningEssenceChance());
        cropWaterEssenceMinQuantity = Math.max(0, config.getCropWaterEssenceMinQuantity());
        cropWaterEssenceMaxQuantity = Math.max(cropWaterEssenceMinQuantity, config.getCropWaterEssenceMaxQuantity());
        cropLightningEssenceMinQuantity = Math.max(0, config.getCropLightningEssenceMinQuantity());
        cropLightningEssenceMaxQuantity = Math.max(cropLightningEssenceMinQuantity, config.getCropLightningEssenceMaxQuantity());
        updateExistingEssenceSettings();
    }

    public static void onDamageBlock(DamageBlockEvent event) {
        if (event == null || event.isCancelled()) {
            return;
        }
        BlockType blockType = event.getBlockType();
        if (!isCropBlock(blockType)) {
            return;
        }
        ensureCropDropListsPatched();
    }

    public static void onBreakBlock(BreakBlockEvent event) {
        if (event == null || event.isCancelled()) {
            return;
        }
        BlockType blockType = event.getBlockType();
        if (!isCropBlock(blockType)) {
            return;
        }
        ensureCropDropListsPatched();
    }

    private static boolean isCropBlock(BlockType blockType) {
        if (blockType == null || blockType == BlockType.UNKNOWN) {
            return false;
        }
        String id = blockType.getId();
        return id != null && id.contains("Plant_Crop_");
    }

    private static void ensureCropDropListsPatched() {
        if (SCAN_COMPLETE.get()) {
            return;
        }
        try {
            int[] results = patchCropDropLists();
            int targetCount = results[0];
            if (targetCount > 0) {
                SCAN_COMPLETE.set(true);
            }
        } catch (Exception e) {
            System.err.println("[SocketReforge] Failed to inject crop essence drops: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static int[] patchCropDropLists() throws ReflectiveOperationException {
        DefaultAssetMap<String, ItemDropList> assetMap = ItemDropList.getAssetMap();
        if (assetMap == null) {
            return new int[] {0, 0};
        }
        Map<String, ItemDropList> dropLists = assetMap.getAssetMap();
        if (dropLists == null || dropLists.isEmpty()) {
            return new int[] {0, 0};
        }

        int targetCount = 0;
        int patchedCount = 0;
        for (Map.Entry<String, ItemDropList> entry : dropLists.entrySet()) {
            String dropListId = entry.getKey();
            ItemDropList dropList = entry.getValue();
            if (dropListId == null || dropList == null) {
                continue;
            }
            if (!isCropDropList(assetMap, dropListId)) {
                continue;
            }
            if (!containsCropProduce(dropList)) {
                continue;
            }
            targetCount++;
            if (PATCHED_DROP_LIST_IDS.contains(dropListId)) {
                continue;
            }
            if (injectEssenceIntoDropList(dropListId, dropList)) {
                patchedCount++;
            }
        }

        debug("scan", "crop drop scan complete: targets=" + targetCount + ", patched=" + patchedCount);
        return new int[] {targetCount, patchedCount};
    }

    private static boolean isCropDropList(DefaultAssetMap<String, ItemDropList> assetMap, String dropListId) {
        Path path = assetMap == null ? null : assetMap.getPath(dropListId);
        if (path != null) {
            String normalized = path.toString().replace('\\', '/').toLowerCase();
            if (!normalized.contains("/drops/crop/")) {
                return false;
            }
        } else {
            if (!dropListId.startsWith("Drops_Plant_Crop_") && !dropListId.startsWith("Plant_Crop_")) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsCropProduce(ItemDropList dropList) {
        return containsItemPrefix(dropList, "Plant_Crop_");
    }

    private static boolean containsItemPrefix(ItemDropList dropList, String prefix) {
        if (dropList == null || prefix == null) {
            return false;
        }
        ItemDropContainer container = dropList.getContainer();
        if (container == null) {
            return false;
        }
        List<ItemDrop> drops = container.getAllDrops(new ArrayList<>());
        for (ItemDrop drop : drops) {
            if (drop == null) {
                continue;
            }
            String itemId = drop.getItemId();
            if (itemId != null && itemId.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsItemId(ItemDropList dropList, String itemId) {
        if (dropList == null || itemId == null) {
            return false;
        }
        ItemDropContainer container = dropList.getContainer();
        if (container == null) {
            return false;
        }
        List<ItemDrop> drops = container.getAllDrops(new ArrayList<>());
        for (ItemDrop drop : drops) {
            if (drop == null) {
                continue;
            }
            if (itemId.equals(drop.getItemId())) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsStaminaCrop(ItemDropList dropList) {
        if (dropList == null) {
            return false;
        }
        ItemDropContainer container = dropList.getContainer();
        if (container == null) {
            return false;
        }
        List<ItemDrop> drops = container.getAllDrops(new ArrayList<>());
        for (ItemDrop drop : drops) {
            if (drop == null) {
                continue;
            }
            String itemId = drop.getItemId();
            if (itemId != null && STAMINA_CROP_IDS.contains(itemId)) {
                return true;
            }
        }
        return false;
    }

    private static boolean injectEssenceIntoDropList(String dropListId, ItemDropList dropList)
            throws ReflectiveOperationException {
        try {
            boolean stamina = containsStaminaCrop(dropList);
            String essenceId = stamina ? LIGHTNING_ESSENCE_ID : WATER_ESSENCE_ID;
            double chance = stamina ? cropLightningEssenceChance : cropWaterEssenceChance;
            double weight = toWeight(chance);
            int minQuantity = stamina ? cropLightningEssenceMinQuantity : cropWaterEssenceMinQuantity;
            int maxQuantity = stamina ? cropLightningEssenceMaxQuantity : cropWaterEssenceMaxQuantity;

            if (containsItemId(dropList, essenceId)) {
                updateEssenceSettings(dropList);
                debug("patch", "dropList=" + dropListId + " already contains " + essenceId);
                PATCHED_DROP_LIST_IDS.add(dropListId);
                return true;
            }

            ItemDropContainer container = dropList.getContainer();
            if (container == null) {
                return false;
            }

            SingleItemDropContainer essenceContainer = createWeightedSingleDrop(essenceId, minQuantity, maxQuantity, weight);
            if (container instanceof MultipleItemDropContainer multiple) {
                appendContainer(multiple, essenceContainer);
            } else {
                MultipleItemDropContainer wrapper = new MultipleItemDropContainer(
                        new ItemDropContainer[] {container, essenceContainer},
                        100.0d,
                        1,
                        1
                );
                setField(dropList, "container", wrapper);
            }

            PATCHED_DROP_LIST_IDS.add(dropListId);
            debug("patch", "patched dropList=" + dropListId + " essence=" + essenceId + " chance=" + chance);
            return true;
        } catch (ReflectiveOperationException e) {
            throw e;
        }
    }

    private static void updateExistingEssenceSettings() {
        DefaultAssetMap<String, ItemDropList> assetMap = ItemDropList.getAssetMap();
        if (assetMap == null) {
            return;
        }
        Map<String, ItemDropList> dropLists = assetMap.getAssetMap();
        if (dropLists == null || dropLists.isEmpty()) {
            return;
        }

        for (Map.Entry<String, ItemDropList> entry : dropLists.entrySet()) {
            String dropListId = entry.getKey();
            ItemDropList dropList = entry.getValue();
            if (dropListId == null || dropList == null) {
                continue;
            }
            if (!isCropDropList(assetMap, dropListId)) {
                continue;
            }
            if (!containsCropProduce(dropList)) {
                continue;
            }
            updateEssenceSettings(dropList);
        }
    }

    private static void updateEssenceSettings(ItemDropList dropList) {
        if (dropList == null) {
            return;
        }
        ItemDropContainer container = dropList.getContainer();
        if (container == null) {
            return;
        }

        double waterWeight = toWeight(cropWaterEssenceChance);
        double lightningWeight = toWeight(cropLightningEssenceChance);
        updateEssenceEntry(container,
                WATER_ESSENCE_ID,
                waterWeight,
                cropWaterEssenceMinQuantity,
                cropWaterEssenceMaxQuantity);
        updateEssenceEntry(container,
                LIGHTNING_ESSENCE_ID,
                lightningWeight,
                cropLightningEssenceMinQuantity,
                cropLightningEssenceMaxQuantity);
    }

    private static boolean updateEssenceEntry(ItemDropContainer container,
                                              String essenceId,
                                              double weight,
                                              int minQuantity,
                                              int maxQuantity) {
        if (container == null || essenceId == null) {
            return false;
        }
        boolean updated = false;
        if (container instanceof SingleItemDropContainer single) {
            ItemDrop drop = single.getDrop();
            if (drop != null && essenceId.equals(drop.getItemId())) {
                try {
                    setField(container, "weight", weight);
                    setField(drop, "quantityMin", minQuantity);
                    setField(drop, "quantityMax", maxQuantity);
                } catch (ReflectiveOperationException ignored) {
                }
                return true;
            }
        } else if (container instanceof MultipleItemDropContainer multiple) {
            try {
                ItemDropContainer[] containers = getFieldValue(multiple, "containers", ItemDropContainer[].class);
                if (containers != null) {
                    for (ItemDropContainer child : containers) {
                        if (updateEssenceEntry(child, essenceId, weight, minQuantity, maxQuantity)) {
                            updated = true;
                        }
                    }
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return updated;
    }

    private static SingleItemDropContainer createWeightedSingleDrop(String itemId,
                                                                    int quantityMin,
                                                                    int quantityMax,
                                                                    double weight) {
        return new SingleItemDropContainer(
                new ItemDrop(itemId, new BsonDocument(), quantityMin, quantityMax),
                weight
        );
    }

    private static void appendContainer(MultipleItemDropContainer multiple,
                                        ItemDropContainer extra) throws ReflectiveOperationException {
        ItemDropContainer[] containers = getFieldValue(multiple, "containers", ItemDropContainer[].class);
        if (containers == null) {
            containers = ItemDropContainer.EMPTY_ARRAY;
        }
        ItemDropContainer[] updated = Arrays.copyOf(containers, containers.length + 1);
        updated[updated.length - 1] = extra;
        setField(multiple, "containers", updated);
    }

    private static void setField(Object target, String fieldName, Object value) throws ReflectiveOperationException {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        field.set(target, value);
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

    private static double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private static double toWeight(double chance) {
        return clamp01(chance) * 100.0d;
    }

    private static void debug(String stage, String message) {
        if (!DEBUG_LOGGING || message == null || message.isBlank()) {
            return;
        }
        System.out.println("[SocketReforge][CROP_DROP][" + stage + "] " + message);
    }
}
