package irai.mod.reforge.Util;

import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.Bench;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.CraftingBench;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import irai.mod.reforge.ReforgePlugin;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.BsonValue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Applies SocketReforge's embedded bench patch assets directly to the loaded
 * crafting bench definitions. This keeps patch authoring declarative without
 * depending on external patch plugins.
 */
public final class RuntimeBenchPatchHost {
    private static final String PATCH_DIR = "Server/Patch/";
    private static final String BENCH_ITEM_DIR = "Server/Item/Items/Bench/";

    private RuntimeBenchPatchHost() {
    }

    public static void applyEmbeddedBenchPatches(ReforgePlugin plugin) {
        if (plugin == null) {
            return;
        }

        List<EmbeddedPatch> patches;
        try {
            patches = readEmbeddedPatches(plugin.getFile());
        } catch (Exception e) {
            System.err.println("[SocketReforge] Failed to scan embedded bench patches: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        if (patches.isEmpty()) {
            System.out.println("[SocketReforge] No embedded bench patches found.");
            return;
        }

        int touchedPatches = 0;
        int categoryChanges = 0;
        for (EmbeddedPatch patch : patches) {
            try {
                int changes = applyBenchPatch(patch);
                if (changes > 0) {
                    touchedPatches++;
                    categoryChanges += changes;
                }
            } catch (Exception e) {
                System.err.println("[SocketReforge] Failed to apply bench patch " + patch.resourcePath + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        if (categoryChanges > 0) {
            System.out.println("[SocketReforge] Applied " + categoryChanges
                    + " embedded bench patch change(s) across "
                    + touchedPatches
                    + " patch file(s).");
        } else {
            System.out.println("[SocketReforge] Embedded bench patches were already up to date.");
        }
    }

    private static int applyBenchPatch(EmbeddedPatch patch) throws ReflectiveOperationException {
        String baseAssetPath = getString(patch.document, "_BaseAssetPath");
        if (baseAssetPath == null || !baseAssetPath.startsWith(BENCH_ITEM_DIR) || !baseAssetPath.endsWith(".json")) {
            return 0;
        }

        BsonArray categoryOps = getArray(getDocument(getDocument(patch.document, "BlockType"), "Bench"), "Categories");
        if (categoryOps == null || categoryOps.isEmpty()) {
            return 0;
        }

        String itemId = baseAssetPath.substring(baseAssetPath.lastIndexOf('/') + 1, baseAssetPath.length() - ".json".length());
        Item item = resolveItem(itemId);
        if (item == null) {
            System.out.println("[SocketReforge] Bench patch skipped for " + itemId + " (item asset unavailable).");
            return 0;
        }

        String blockId = item.getBlockId();
        if (blockId == null || blockId.isBlank()) {
            System.out.println("[SocketReforge] Bench patch skipped for " + itemId + " (no block id).");
            return 0;
        }

        BlockType blockType = BlockType.fromString(blockId);
        if (blockType == null || blockType == BlockType.UNKNOWN || blockType.isUnknown()) {
            System.out.println("[SocketReforge] Bench patch skipped for " + itemId + " (block type unavailable: " + blockId + ").");
            return 0;
        }

        Bench bench = blockType.getBench();
        if (!(bench instanceof CraftingBench craftingBench)) {
            System.out.println("[SocketReforge] Bench patch skipped for " + itemId + " (not a crafting bench).");
            return 0;
        }

        int changes = 0;
        for (BsonValue value : categoryOps) {
            if (!value.isDocument()) {
                continue;
            }
            changes += applyCategoryOperation(craftingBench, value.asDocument());
        }

        if (changes > 0) {
            invalidateBenchCaches(item, blockType);
            System.out.println("[SocketReforge] Bench patch applied to " + itemId + " via " + patch.resourcePath + ".");
        }

        return changes;
    }

    private static int applyCategoryOperation(CraftingBench craftingBench, BsonDocument operation) throws ReflectiveOperationException {
        String op = normalizeOperation(getString(operation, "_op"));
        BsonDocument selector = getDocument(operation, "_find");
        String targetId = firstNonBlank(getString(operation, "Id"), getString(selector, "Id"));
        if (targetId == null || targetId.isBlank()) {
            return 0;
        }

        CraftingBench.BenchCategory[] existingCategories = safeCategories(craftingBench.getCategories());
        int existingIndex = indexOfCategory(existingCategories, targetId);

        if ("remove".equals(op)) {
            if (existingIndex < 0) {
                return 0;
            }
            CraftingBench.BenchCategory[] updated = new CraftingBench.BenchCategory[existingCategories.length - 1];
            if (existingIndex > 0) {
                System.arraycopy(existingCategories, 0, updated, 0, existingIndex);
            }
            if (existingIndex < existingCategories.length - 1) {
                System.arraycopy(existingCategories, existingIndex + 1, updated, existingIndex, existingCategories.length - existingIndex - 1);
            }
            setField(craftingBench, "categories", updated);
            return 1;
        }

        CraftingBench.BenchCategory merged = mergeCategory(
                existingIndex >= 0 ? existingCategories[existingIndex] : null,
                operation,
                targetId
        );
        if (merged == null) {
            return 0;
        }

        if (existingIndex >= 0) {
            if (sameCategory(existingCategories[existingIndex], merged)) {
                return 0;
            }
            existingCategories[existingIndex] = merged;
            setField(craftingBench, "categories", existingCategories);
            return 1;
        }

        CraftingBench.BenchCategory[] updated = Arrays.copyOf(existingCategories, existingCategories.length + 1);
        updated[updated.length - 1] = merged;
        setField(craftingBench, "categories", updated);
        return 1;
    }

    private static CraftingBench.BenchCategory mergeCategory(CraftingBench.BenchCategory existing,
                                                             BsonDocument operation,
                                                             String targetId) {
        String id = firstNonBlank(getString(operation, "Id"), existing == null ? null : existing.getId(), targetId);
        String name = getNullableString(operation, "Name");
        String icon = getNullableString(operation, "Icon");

        CraftingBench.BenchItemCategory[] existingItemCategories = existing == null
                ? new CraftingBench.BenchItemCategory[0]
                : safeItemCategories(existing.getItemCategories());
        CraftingBench.BenchItemCategory[] itemCategories = operation.containsKey("ItemCategories")
                ? parseItemCategories(operation.get("ItemCategories"), existingItemCategories)
                : existingItemCategories;

        if (name == null) {
            name = existing == null ? "" : defaultString(existing.getName());
        }
        if (icon == null) {
            icon = existing == null ? "" : defaultString(existing.getIcon());
        }
        if (itemCategories == null) {
            itemCategories = new CraftingBench.BenchItemCategory[0];
        }

        return new CraftingBench.BenchCategory(id, name, icon, itemCategories);
    }

    private static CraftingBench.BenchItemCategory[] parseItemCategories(BsonValue value,
                                                                         CraftingBench.BenchItemCategory[] fallback) {
        if (value == null || !value.isArray()) {
            return fallback;
        }

        List<CraftingBench.BenchItemCategory> categories = new ArrayList<>();
        for (BsonValue entry : value.asArray()) {
            if (!entry.isDocument()) {
                continue;
            }
            BsonDocument doc = entry.asDocument();
            String id = defaultString(getString(doc, "Id"));
            String name = defaultString(getString(doc, "Name"));
            String icon = defaultString(getString(doc, "Icon"));
            String diagram = defaultString(getString(doc, "Diagram"));
            int slots = getInt(doc, "Slots", 0);
            boolean specialSlot = getBoolean(doc, "SpecialSlot", false);
            categories.add(new CraftingBench.BenchItemCategory(id, name, icon, diagram, slots, specialSlot));
        }
        return categories.toArray(new CraftingBench.BenchItemCategory[0]);
    }

    private static boolean sameCategory(CraftingBench.BenchCategory left, CraftingBench.BenchCategory right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return Objects.equals(left.getId(), right.getId())
                && Objects.equals(left.getName(), right.getName())
                && Objects.equals(left.getIcon(), right.getIcon())
                && sameItemCategories(left.getItemCategories(), right.getItemCategories());
    }

    private static boolean sameItemCategories(CraftingBench.BenchItemCategory[] left,
                                              CraftingBench.BenchItemCategory[] right) {
        CraftingBench.BenchItemCategory[] safeLeft = safeItemCategories(left);
        CraftingBench.BenchItemCategory[] safeRight = safeItemCategories(right);
        if (safeLeft.length != safeRight.length) {
            return false;
        }
        for (int i = 0; i < safeLeft.length; i++) {
            CraftingBench.BenchItemCategory l = safeLeft[i];
            CraftingBench.BenchItemCategory r = safeRight[i];
            if (!Objects.equals(l.getId(), r.getId())
                    || !Objects.equals(l.getName(), r.getName())
                    || !Objects.equals(l.getIcon(), r.getIcon())
                    || !Objects.equals(l.getDiagram(), r.getDiagram())
                    || l.getSlots() != r.getSlots()
                    || l.isSpecialSlot() != r.isSpecialSlot()) {
                return false;
            }
        }
        return true;
    }

    private static void invalidateBenchCaches(Item item, BlockType blockType) throws ReflectiveOperationException {
        item.invalidatePacketCache();
        setField(blockType, "cachedPacket", null);
    }

    private static Item resolveItem(String itemId) {
        DefaultAssetMap<String, Item> assetMap = Item.getAssetMap();
        if (assetMap == null || assetMap.getAssetMap() == null) {
            return null;
        }
        Item item = assetMap.getAssetMap().get(itemId);
        if (item == null || item == Item.UNKNOWN) {
            return null;
        }
        return item;
    }

    private static int indexOfCategory(CraftingBench.BenchCategory[] categories, String id) {
        for (int i = 0; i < categories.length; i++) {
            CraftingBench.BenchCategory category = categories[i];
            if (category != null && Objects.equals(category.getId(), id)) {
                return i;
            }
        }
        return -1;
    }

    private static CraftingBench.BenchCategory[] safeCategories(CraftingBench.BenchCategory[] categories) {
        return categories == null ? new CraftingBench.BenchCategory[0] : Arrays.copyOf(categories, categories.length);
    }

    private static CraftingBench.BenchItemCategory[] safeItemCategories(CraftingBench.BenchItemCategory[] categories) {
        return categories == null ? new CraftingBench.BenchItemCategory[0] : Arrays.copyOf(categories, categories.length);
    }

    private static List<EmbeddedPatch> readEmbeddedPatches(Path pluginFile) throws IOException {
        if (pluginFile == null) {
            return Collections.emptyList();
        }
        if (Files.isDirectory(pluginFile)) {
            return readPatchesFromDirectory(pluginFile);
        }
        if (!Files.exists(pluginFile)) {
            return Collections.emptyList();
        }
        return readPatchesFromJar(pluginFile);
    }

    private static List<EmbeddedPatch> readPatchesFromDirectory(Path pluginDir) throws IOException {
        Path patchDir = pluginDir.resolve(PATCH_DIR);
        if (!Files.isDirectory(patchDir)) {
            return Collections.emptyList();
        }

        List<EmbeddedPatch> patches = new ArrayList<>();
        try (var stream = Files.walk(patchDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .forEach(path -> {
                        try {
                            String resourcePath = pluginDir.relativize(path).toString().replace('\\', '/');
                            patches.add(new EmbeddedPatch(resourcePath, parsePatch(Files.readString(path))));
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw e;
        }
        return patches;
    }

    private static List<EmbeddedPatch> readPatchesFromJar(Path pluginJar) throws IOException {
        List<EmbeddedPatch> patches = new ArrayList<>();
        try (JarFile jarFile = new JarFile(pluginJar.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith(PATCH_DIR) || !name.endsWith(".json")) {
                    continue;
                }
                try (InputStream in = jarFile.getInputStream(entry)) {
                    String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    patches.add(new EmbeddedPatch(name, parsePatch(json)));
                }
            }
        }
        patches.sort((left, right) -> left.resourcePath.compareTo(right.resourcePath));
        return patches;
    }

    private static BsonDocument parsePatch(String json) {
        return BsonDocument.parse(json);
    }

    private static void setField(Object target, String fieldName, Object value) throws ReflectiveOperationException {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        field.set(target, value);
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

    private static BsonDocument getDocument(BsonDocument doc, String key) {
        if (doc == null || key == null || !doc.containsKey(key)) {
            return null;
        }
        BsonValue value = doc.get(key);
        return value != null && value.isDocument() ? value.asDocument() : null;
    }

    private static BsonArray getArray(BsonDocument doc, String key) {
        if (doc == null || key == null || !doc.containsKey(key)) {
            return null;
        }
        BsonValue value = doc.get(key);
        return value != null && value.isArray() ? value.asArray() : null;
    }

    private static String getString(BsonDocument doc, String key) {
        if (doc == null || key == null || !doc.containsKey(key)) {
            return null;
        }
        BsonValue value = doc.get(key);
        return value instanceof BsonString bsonString ? bsonString.getValue() : null;
    }

    private static String getNullableString(BsonDocument doc, String key) {
        if (doc == null || key == null || !doc.containsKey(key)) {
            return null;
        }
        BsonValue value = doc.get(key);
        if (value == null || value.isNull()) {
            return "";
        }
        return value instanceof BsonString bsonString ? bsonString.getValue() : null;
    }

    private static int getInt(BsonDocument doc, String key, int fallback) {
        if (doc == null || key == null || !doc.containsKey(key)) {
            return fallback;
        }
        BsonValue value = doc.get(key);
        if (value instanceof BsonInt32 int32) {
            return int32.getValue();
        }
        return fallback;
    }

    private static boolean getBoolean(BsonDocument doc, String key, boolean fallback) {
        if (doc == null || key == null || !doc.containsKey(key)) {
            return fallback;
        }
        BsonValue value = doc.get(key);
        if (value instanceof BsonBoolean bsonBoolean) {
            return bsonBoolean.getValue();
        }
        return fallback;
    }

    private static String normalizeOperation(String op) {
        if (op == null || op.isBlank()) {
            return "upsert";
        }
        return op.trim().toLowerCase();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }

    private record EmbeddedPatch(String resourcePath, BsonDocument document) {
    }
}
