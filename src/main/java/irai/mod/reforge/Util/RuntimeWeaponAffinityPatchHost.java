package irai.mod.reforge.Util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.builtin.asseteditor.AssetEditorPlugin;
import com.hypixel.hytale.builtin.asseteditor.datasource.DataSource;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;

import irai.mod.reforge.Common.WeaponAffinityAppearanceInjector;
import irai.mod.reforge.ReforgePlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Startup patch host for weapon affinity visuals.
 *
 * Loaded item JSON can be patched in-memory like the arcane bench categories.
 * Raw blockymodel node data is not exposed by Hytale's loaded ModelAsset, so
 * model patching is limited to actual .blockymodel files available on disk.
 */
public final class RuntimeWeaponAffinityPatchHost {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int LOG_ID_CHUNK_SIZE = 40;
    private static final String RUNTIME_MOD_DATA_FOLDER = "irai.mod.reforge_Socket Reforge";
    private static final String SPIN_TEMPLATE_MODEL = "Items/Weapons/Sword/Crude.blockymodel";
    private static final String RUNTIME_TEMPLATE_FOLDER = "SocketReforge/Templates";
    private static final String RUNTIME_SPIN_NODE_TEMPLATE = "WeaponAffinitySpinNodeTemplate.json";
    private static final String RUNTIME_CONDITION_TEMPLATE = "WeaponAffinityItemAppearanceCondition.json";
    private static final String RUNTIME_TEMPLATE_README = "README.md";
    private static final String RUNTIME_OVERRIDE_MANIFEST = "manifest.json";
    private static final String RAW_CONDITION_TEMPLATE =
            "SocketReforge/Templates/WeaponAffinityItemAppearanceCondition.json";
    private static final String SPIN_NODE_NAME = "Spin1";
    private static final String FIRST_SPIN_ATTACHMENT = "Attachment_Spin1";
    private static final String ITEM_APPEARANCE_CONDITIONS = "ItemAppearanceConditions";
    private static final String ANIMATION = "Animation";
    private static final String MODEL = "Model";
    private static final String WEAPON = "Weapon";
    private static final String ARMOR = "Armor";
    private static final String QUALITY = "Quality";
    private static final String TEMPLATE_QUALITY = "Template";
    private static final String CONDITION = "Condition";
    private static final String CONDITION_VALUE_TYPE = "ConditionValueType";
    private static final String ABSOLUTE = "Absolute";
    private static final String PARTICLES = "Particles";
    private static final String FIRST_PERSON_PARTICLES = "FirstPersonParticles";
    private static final String SYSTEM_ID = "SystemId";
    private static final String TARGET_ENTITY_PART = "TargetEntityPart";
    private static final String TARGET_NODE_NAME = "TargetNodeName";
    private static final String SCALE = "Scale";
    private static final String PRIMARY_ITEM = "PrimaryItem";
    private static final String SECONDARY_ITEM = "SecondaryItem";

    private RuntimeWeaponAffinityPatchHost() {
    }

    public static void applyStartupWeaponAffinityPatches(ReforgePlugin plugin) {
        TemplateExportResult templates = exportRuntimePatchTemplates(plugin);
        ModelPatchResult models = patchAvailableWeaponModels(plugin);
        RawItemPatchResult rawItems = patchRawWeaponItemJson(plugin, models.readyModelPaths());
        WeaponAffinityAppearanceInjector.PatchResult itemAssets =
                WeaponAffinityAppearanceInjector.applyToLoadedItemAssetMap(models.readyModelPaths());
        ExportedItemPatchResult exportedItems =
                exportLoadedWeaponItemJsonOverrides(plugin, models.readyModelPaths());
        System.out.println("[SocketReforge] Weapon affinity startup patch complete. Item assets patched: "
                + itemAssets.patchedCount()
                + ", raw item JSON eligible: "
                + rawItems.eligible
                + ", raw item JSON patched: "
                + rawItems.patched
                + ", raw item JSON already ready: "
                + rawItems.ready
                + ", exported item JSON overrides: "
                + exportedItems.exported
                + ", item JSON overrides already ready: "
                + exportedItems.ready
                + ", model files patched: "
                + models.patched
                + ", model files already ready: "
                + models.present
                + ", raw model files unavailable: "
                + models.missing
                + ", models without anchor: "
                + models.noAnchor
                + ".");
        logList("Weapon affinity runtime templates exported", templates.exportedPaths);
        logList("Weapon affinity runtime templates updated", templates.updatedPaths);
        logList("Weapon affinity runtime templates already present", templates.readyPaths);
        logList("Weapon affinity obsolete runtime templates removed", templates.removedPaths);
        logList("Weapon affinity runtime template export failures", templates.failedPaths);
        logList("Weapon affinity patched item ids", itemAssets.patchedIds());
        logList("Weapon affinity skipped item ids without verified spin-ready models",
                itemAssets.unverifiedModelIds());
        logList("Weapon affinity failed item ids", itemAssets.failedIds());
        logList("Weapon affinity raw item JSON roots", rawItems.roots);
        logList("Weapon affinity patched raw item JSON files", rawItems.patchedPaths);
        logList("Weapon affinity raw item JSON files already ready", rawItems.readyPaths);
        logList("Weapon affinity skipped raw item JSON files without verified spin-ready models",
                rawItems.unverifiedModelPaths);
        logList("Weapon affinity skipped generated raw item JSON files", rawItems.skippedPaths);
        logList("Weapon affinity failed raw item JSON files", rawItems.failedPaths);
        logList("Weapon affinity exported item JSON overrides", exportedItems.exportedPaths);
        logList("Weapon affinity item JSON overrides already ready", exportedItems.readyPaths);
        logList("Weapon affinity exported item JSON override sources", exportedItems.sourcePaths);
        logList("Weapon affinity skipped exported item JSON overrides", exportedItems.skippedPaths);
        logList("Weapon affinity failed exported item JSON overrides", exportedItems.failedPaths);
        logList("Weapon affinity patched model paths", models.patchedPaths);
        logList("Weapon affinity obsolete fallback model clones removed", models.removedClonePaths);
        logList("Weapon affinity exported patched model overrides", models.exportedPaths);
        logList("Weapon affinity exported model override sources", models.exportedSourcePaths);
        logList("Weapon affinity ready model paths", models.presentPaths);
        logList("Weapon affinity prepared empty model override folders for missing sources",
                models.preparedOverrideFolders);
        logList("Weapon affinity missing raw model paths", models.missingPaths);
        logList("Weapon affinity model paths without Handle/R-Attachment anchors", models.noAnchorPaths);
    }

    private static ModelPatchResult patchAvailableWeaponModels(ReforgePlugin plugin) {
        ModelPatchResult result = new ModelPatchResult();
        DefaultAssetMap<String, Item> assetMap = Item.getAssetMap();
        if (assetMap == null || assetMap.getAssetMap() == null) {
            return result;
        }

        Set<Path> commonRoots = commonRoots(plugin);
        JsonObject spinTemplate = readSpinTemplate(commonRoots);
        if (spinTemplate == null) {
            System.out.println("[SocketReforge] Weapon affinity model patch skipped (Spin1 template unavailable).");
            return result;
        }
        JsonObject spinSourceModel = readSpinSourceModel(commonRoots);

        Set<String> modelPaths = new LinkedHashSet<>();
        for (Map.Entry<String, Item> entry : assetMap.getAssetMap().entrySet()) {
            Item item = entry.getValue();
            if (!WeaponAffinityAppearanceInjector.isEligibleWeapon(entry.getKey(), item)) {
                continue;
            }
            String model = item.getModel();
            if (model != null && !model.isBlank() && model.endsWith(".blockymodel")) {
                modelPaths.add(model.replace('\\', '/'));
            }
        }
        modelPaths.addAll(collectRawWeaponModelPaths(plugin));

        for (String modelPath : modelPaths) {
            Path file = resolveModelFile(commonRoots, modelPath);
            if (file == null) {
                if (isPackagedModelSpinReady(modelPath)) {
                    result.present++;
                    result.presentPaths.add(modelPath);
                    continue;
                }
                if (exportAndPatchPackagedModelOverride(plugin, commonRoots, modelPath, spinTemplate, result)) {
                    continue;
                }
                result.missing++;
                result.missingPaths.add(modelPath);
                continue;
            }
            try {
                if (isObsoleteRuntimeFallbackClone(plugin, file, modelPath, spinSourceModel)) {
                    Files.deleteIfExists(file);
                    result.removedClonePaths.add(file.toString());
                    result.missing++;
                    result.missingPaths.add(modelPath);
                    prepareRuntimeModelOverrideFolders(plugin, modelPath, result);
                    continue;
                }
                String state = patchModelFile(file, spinTemplate);
                switch (state) {
                    case "patched" -> {
                        result.patched++;
                        result.patchedPaths.add(modelPath);
                    }
                    case "present" -> {
                        result.present++;
                        result.presentPaths.add(modelPath);
                    }
                    case "no-anchor" -> {
                        result.noAnchor++;
                        result.noAnchorPaths.add(modelPath);
                    }
                    default -> {
                        result.missing++;
                        result.missingPaths.add(modelPath);
                    }
                }
            } catch (Exception e) {
                result.noAnchor++;
                result.noAnchorPaths.add(modelPath);
                System.err.println("[SocketReforge] Failed to patch weapon model " + file + ": " + e.getMessage());
            }
        }
        return result;
    }

    private static boolean exportAndPatchPackagedModelOverride(ReforgePlugin plugin,
                                                               Set<Path> commonRoots,
                                                               String modelPath,
                                                               JsonObject spinTemplate,
                                                               ModelPatchResult result) {
        JsonObject model;
        ModelSource source;
        try {
            source = readModelSource(commonRoots, modelPath);
            model = source == null ? null : source.model();
        } catch (Exception e) {
            source = null;
            model = null;
        }
        if (model == null) {
            prepareRuntimeModelOverrideFolders(plugin, modelPath, result);
            return false;
        }

        String state = patchModelObject(model, spinTemplate);
        if (!"patched".equals(state) && !"present".equals(state)) {
            if ("no-anchor".equals(state)) {
                result.noAnchor++;
                result.noAnchorPaths.add(modelPath);
                return true;
            }
            prepareRuntimeModelOverrideFolders(plugin, modelPath, result);
            return false;
        }

        Path exported = writeRuntimeModelOverride(plugin, modelPath, model);
        if (exported == null) {
            prepareRuntimeModelOverrideFolders(plugin, modelPath, result);
            return false;
        }
        result.exportedPaths.add(exported.toString());
        if (source != null) {
            result.exportedSourcePaths.add(modelPath + " <= " + source.description());
        }
        if ("patched".equals(state)) {
            result.patched++;
            result.patchedPaths.add(modelPath);
        } else {
            result.present++;
            result.presentPaths.add(modelPath);
        }
        return true;
    }

    private static boolean isPackagedModelSpinReady(String modelPath) {
        try {
            JsonObject model = readJsonResource("Common/" + modelPath.replace('\\', '/'));
            return model != null && findNodeByName(nodes(model), FIRST_SPIN_ATTACHMENT) != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static RawItemPatchResult patchRawWeaponItemJson(ReforgePlugin plugin, Set<String> spinReadyModelPaths) {
        RawItemPatchResult result = new RawItemPatchResult();
        for (Path root : itemRoots(plugin)) {
            result.roots.add(root.toString());
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".json"))
                        .forEach(path -> patchRawWeaponItemJsonFile(path, spinReadyModelPaths, result));
            } catch (IOException e) {
                result.failedPaths.add(root.toString());
                System.err.println("[SocketReforge] Failed to scan weapon item JSON root "
                        + root + ": " + e.getMessage());
            }
        }
        return result;
    }

    private static void patchRawWeaponItemJsonFile(Path file,
                                                   Set<String> spinReadyModelPaths,
                                                   RawItemPatchResult result) {
        try {
            if (isGeneratedAppendageFile(file)) {
                result.skippedPaths.add(file.toString());
                return;
            }
            JsonObject item = readJsonObject(file);
            if (!isRawWeaponItem(file, item)) {
                return;
            }
            result.eligible++;
            if (hasUnsafeRawInteractionHooks(item)) {
                if (removeRawItemAffinityHooks(item)) {
                    writeJsonObject(file, item);
                    result.patched++;
                    result.patchedPaths.add(file.toString() + " (removed unsafe affinity hooks)");
                }
                result.skippedPaths.add(file.toString() + " (unsafe interaction vars)");
                return;
            }
            String model = stringValue(item, MODEL);
            boolean spinReady = spinReadyModelPaths != null
                    && model != null
                    && spinReadyModelPaths.contains(model.replace('\\', '/'));
            if (!spinReady) {
                result.unverifiedModelPaths.add(file.toString());
                return;
            }
            if (ensureRawItemAffinityHooks(item, rawItemIdFromFile(file))) {
                writeJsonObject(file, item);
                result.patched++;
                result.patchedPaths.add(file.toString());
            } else {
                result.ready++;
                result.readyPaths.add(file.toString());
            }
        } catch (Exception e) {
            result.failedPaths.add(file.toString());
            System.err.println("[SocketReforge] Failed to patch weapon item JSON "
                    + file + ": " + e.getMessage());
        }
    }

    private static ExportedItemPatchResult exportLoadedWeaponItemJsonOverrides(ReforgePlugin plugin,
                                                                               Set<String> spinReadyModelPaths) {
        ExportedItemPatchResult result = new ExportedItemPatchResult();
        DefaultAssetMap<String, Item> assetMap = Item.getAssetMap();
        if (assetMap == null || assetMap.getAssetMap() == null) {
            return result;
        }

        for (Map.Entry<String, Item> entry : assetMap.getAssetMap().entrySet()) {
            String itemId = entry.getKey();
            Item loadedItem = entry.getValue();
            if (!WeaponAffinityAppearanceInjector.isEligibleWeapon(itemId, loadedItem)) {
                continue;
            }
            String model = loadedItem == null ? null : loadedItem.getModel();
            boolean spinReady = spinReadyModelPaths != null
                    && model != null
                    && spinReadyModelPaths.contains(model.replace('\\', '/'));
            if (!spinReady) {
                continue;
            }

            try {
                ItemSource source = readItemSource(itemId);
                if (source == null || source.item() == null || source.sourcePath() == null) {
                    result.failedPaths.add(itemId + " (source JSON unavailable)");
                    continue;
                }

                JsonObject item = source.item();
                if (hasUnsafeRawInteractionHooks(item)) {
                    result.skippedPaths.add(itemId + " (unsafe interaction vars) <= " + source.description());
                    continue;
                }
                ensureRawItemAffinityHooks(item, itemId);
                Path exported = writeRuntimeItemOverride(plugin, source.sourcePath(), item);
                if (exported == null) {
                    result.failedPaths.add(itemId + " (write failed)");
                    continue;
                }
                result.sourcePaths.add(itemId + " <= " + source.description());
                if (isSameJsonFile(exported, item)) {
                    result.ready++;
                    result.readyPaths.add(exported.toString());
                } else {
                    writeJsonObject(exported, item);
                    result.exported++;
                    result.exportedPaths.add(exported.toString());
                }
            } catch (Exception e) {
                String reason = exceptionSummary(e);
                result.failedPaths.add(itemId + " (" + reason + ")");
                System.err.println("[SocketReforge] Failed to export weapon affinity item JSON override for "
                        + itemId + ": " + reason);
            }
        }
        return result;
    }

    private static TemplateExportResult exportRuntimePatchTemplates(ReforgePlugin plugin) {
        TemplateExportResult result = new TemplateExportResult();
        try {
            JsonObject spinSource = readJsonResource("Common/" + SPIN_TEMPLATE_MODEL);
            JsonObject spinNode = spinSource == null ? null : findNodeByName(nodes(spinSource), SPIN_NODE_NAME);
            String conditionTemplate = readTextResource(RAW_CONDITION_TEMPLATE);
            if (spinSource == null || spinNode == null || conditionTemplate == null) {
                return result;
            }

            for (Path runtimeRoot : runtimeModDataRoots(plugin, true)) {
                writeTemplateIfMissing(runtimeRoot.resolve(RUNTIME_OVERRIDE_MANIFEST),
                        runtimeOverrideManifest(),
                        result);
                Path templateFolder = runtimeRoot.resolve(RUNTIME_TEMPLATE_FOLDER).toAbsolutePath().normalize();
                Files.createDirectories(templateFolder);
                deleteTemplateIfPresent(templateFolder.resolve("WeaponAffinitySpinSource.blockymodel"), result);
                writeTemplateIfMissing(templateFolder.resolve(RUNTIME_SPIN_NODE_TEMPLATE),
                        GSON.toJson(spinNode) + System.lineSeparator(),
                        result);
                writeTemplateIfMissing(templateFolder.resolve(RUNTIME_CONDITION_TEMPLATE),
                        conditionTemplate.endsWith(System.lineSeparator())
                                ? conditionTemplate
                                : conditionTemplate + System.lineSeparator(),
                        result);
                writeTemplateIfMissing(templateFolder.resolve(RUNTIME_TEMPLATE_README),
                        runtimeTemplateReadme(),
                        result);
            }
        } catch (Exception e) {
            result.failedPaths.add("runtime templates: " + e.getMessage());
            System.err.println("[SocketReforge] Failed to export weapon affinity runtime templates: "
                    + e.getMessage());
        }
        return result;
    }

    private static void writeTemplateIfMissing(Path file, String content, TemplateExportResult result)
            throws IOException {
        if (Files.isRegularFile(file)) {
            String existing = Files.readString(file, StandardCharsets.UTF_8);
            if (existing.equals(content)) {
                result.readyPaths.add(file.toString());
            } else {
                Files.writeString(file, content, StandardCharsets.UTF_8);
                result.updatedPaths.add(file.toString());
            }
            return;
        }
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        result.exportedPaths.add(file.toString());
    }

    private static String runtimeTemplateReadme() {
        return """
                # Weapon Affinity Patch Templates

                These files are exported by Socket Reforge so runtime model overrides can be prepared beside configs.

                - `WeaponAffinitySpinNodeTemplate.json` is the only model fragment used for patching.
                  It contains the `Spin1` subtree injected under each weapon model's own `Handle` or `R-Attachment`.
                - `WeaponAffinityItemAppearanceCondition.json` is the item JSON condition template.

                To enable a weapon model that logs as missing, place its `.blockymodel` at:
                `Common/<logged model path>`

                The patcher may create those folders automatically, but empty folders are not patched/exported models.
                A model override is exported only when Socket Reforge can read the real source `.blockymodel`.

                On next startup, the patcher injects spin nodes into that model and only then patches the matching item JSON/asset hooks.
                """;
    }

    private static String runtimeOverrideManifest() {
        return """
                {
                  "Group": "irai.mod.reforge.generated",
                  "Name": "Socket Reforge Generated Overrides",
                  "Version": "1.0.0",
                  "Description": "Generated asset overrides for Socket Reforge runtime weapon affinity visuals.",
                  "Authors": [
                    {
                      "Name": "Socket Reforge"
                    }
                  ],
                  "ServerVersion": ">=0.5.0 <0.6.0",
                  "Dependencies": {
                    "irai.mod.reforge:Socket Reforge": "*"
                  },
                  "OptionalDependencies": {},
                  "LoadBefore": {},
                  "DisabledByDefault": false,
                  "IncludesAssetPack": true,
                  "SubPlugins": []
                }
                """;
    }

    private static void deleteTemplateIfPresent(Path file, TemplateExportResult result) throws IOException {
        if (Files.deleteIfExists(file)) {
            result.removedPaths.add(file.toString());
        }
    }

    private static Set<String> collectRawWeaponModelPaths(ReforgePlugin plugin) {
        Set<String> modelPaths = new LinkedHashSet<>();
        for (Path root : itemRoots(plugin)) {
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".json"))
                        .forEach(path -> collectRawWeaponModelPath(path, modelPaths));
            } catch (IOException e) {
                System.err.println("[SocketReforge] Failed to scan weapon item JSON root for models "
                        + root + ": " + e.getMessage());
            }
        }
        return modelPaths;
    }

    private static void collectRawWeaponModelPath(Path file, Set<String> modelPaths) {
        try {
            if (isGeneratedAppendageFile(file)) {
                return;
            }
            JsonObject item = readJsonObject(file);
            if (!isRawWeaponItem(file, item)) {
                return;
            }
            String model = stringValue(item, MODEL);
            if (model != null && model.endsWith(".blockymodel")) {
                modelPaths.add(model.replace('\\', '/'));
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean isGeneratedAppendageFile(Path file) {
        if (file == null || file.getParent() == null) {
            return false;
        }
        String fileName = file.getFileName().toString();
        if (!fileName.endsWith(".json")) {
            return false;
        }
        String baseName = fileName.substring(0, fileName.length() - ".json".length());
        int split = baseName.length();
        while (split > 0 && Character.isDigit(baseName.charAt(split - 1))) {
            split--;
        }
        if (split == baseName.length() || split == 0) {
            return false;
        }
        String canonicalName = baseName.substring(0, split) + ".json";
        return Files.isRegularFile(file.getParent().resolve(canonicalName));
    }

    private static void logList(String label, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        int chunks = (values.size() + LOG_ID_CHUNK_SIZE - 1) / LOG_ID_CHUNK_SIZE;
        for (int chunk = 0; chunk < chunks; chunk++) {
            int from = chunk * LOG_ID_CHUNK_SIZE;
            int to = Math.min(from + LOG_ID_CHUNK_SIZE, values.size());
            System.out.println("[SocketReforge] " + label + " (" + values.size() + ") ["
                    + (chunk + 1)
                    + "/"
                    + chunks
                    + "]: "
                    + String.join(", ", values.subList(from, to)));
        }
    }

    private static boolean ensureRawItemAffinityHooks(JsonObject item, String itemId) {
        boolean changed = false;
        JsonObject conditions = objectValue(item, ITEM_APPEARANCE_CONDITIONS);
        if (conditions == null) {
            conditions = new JsonObject();
            item.add(ITEM_APPEARANCE_CONDITIONS, conditions);
            changed = true;
        }

        changed = ensureRawConditionList(conditions,
                WeaponAffinityAppearanceInjector.PRIMARY_STAT_ID,
                PRIMARY_ITEM) || changed;
        changed = ensureRawConditionList(conditions,
                WeaponAffinityAppearanceInjector.SECONDARY_STAT_ID,
                SECONDARY_ITEM) || changed;

        String animation = stringValue(item, ANIMATION);
        if (animation == null || animation.isBlank()) {
            item.addProperty(ANIMATION, WeaponAffinityAppearanceInjector.AFFINITY_SPIN_ANIMATION);
            changed = true;
        }
        changed = ensureRawDualWieldRender(item, itemId) || changed;
        return changed;
    }

    private static boolean removeRawItemAffinityHooks(JsonObject item) {
        if (item == null) {
            return false;
        }
        boolean changed = false;
        JsonObject conditions = objectValue(item, ITEM_APPEARANCE_CONDITIONS);
        if (conditions != null) {
            if (conditions.remove(WeaponAffinityAppearanceInjector.PRIMARY_STAT_ID) != null) {
                changed = true;
            }
            if (conditions.remove(WeaponAffinityAppearanceInjector.SECONDARY_STAT_ID) != null) {
                changed = true;
            }
            if (conditions.size() == 0 && item.remove(ITEM_APPEARANCE_CONDITIONS) != null) {
                changed = true;
            }
        }
        String animation = stringValue(item, ANIMATION);
        if (WeaponAffinityAppearanceInjector.AFFINITY_SPIN_ANIMATION.equals(animation)) {
            item.remove(ANIMATION);
            changed = true;
        }
        return changed;
    }

    private static boolean hasUnsafeRawInteractionHooks(JsonObject item) {
        if (item == null) {
            return false;
        }
        return containsUnsafeInteractionToken(item.get("Interactions"))
                || containsUnsafeInteractionToken(item.get("InteractionVars"));
    }

    private static boolean containsUnsafeInteractionToken(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return false;
        }
        if (element.isJsonPrimitive()) {
            try {
                String value = element.getAsString();
                if (value == null || value.isBlank()) {
                    return false;
                }
                String normalized = value.toLowerCase(Locale.ROOT);
                return normalized.contains("***")
                        || normalized.contains("staff_cast")
                        || normalized.contains("magical_cast");
            } catch (Exception ignored) {
                return false;
            }
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                if (containsUnsafeInteractionToken(child)) {
                    return true;
                }
            }
            return false;
        }
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                if (containsUnsafeInteractionToken(new JsonPrimitive(entry.getKey()))
                        || containsUnsafeInteractionToken(entry.getValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean ensureRawDualWieldRender(JsonObject item, String itemId) {
        if (!looksLikeDualWeapon(itemId)) {
            return false;
        }
        JsonObject weapon = objectValue(item, WEAPON);
        if (weapon == null) {
            return false;
        }
        JsonElement current = weapon.get("RenderDualWielded");
        if (current != null && current.isJsonPrimitive()) {
            try {
                if (current.getAsBoolean()) {
                    return false;
                }
            } catch (Exception ignored) {
            }
        }
        weapon.addProperty("RenderDualWielded", true);
        return true;
    }

    private static boolean looksLikeDualWeapon(String itemId) {
        if (itemId == null) {
            return false;
        }
        String normalized = itemId.toLowerCase(Locale.ROOT);
        return normalized.contains("dagger")
                || normalized.contains("daggers")
                || normalized.contains("glaive")
                || normalized.contains("glaives")
                || normalized.contains("dual")
                || normalized.contains("twin");
    }

    private static String rawItemIdFromFile(Path file) {
        if (file == null || file.getFileName() == null) {
            return null;
        }
        String name = file.getFileName().toString();
        return name.endsWith(".json") ? name.substring(0, name.length() - ".json".length()) : name;
    }

    private static boolean ensureRawConditionList(JsonObject conditions, String statId, String targetPart) {
        JsonArray existing = arrayValue(conditions, statId);
        if (existing == null) {
            existing = new JsonArray();
            conditions.add(statId, existing);
        }

        boolean changed = normalizeRawAffinityConditions(existing);
        for (AffinityVisual visual : affinityVisuals()) {
            if (containsRawCondition(existing, visual.value())) {
                continue;
            }
            existing.add(createRawCondition(visual, targetPart));
            changed = true;
        }
        return changed;
    }

    private static JsonObject createRawCondition(AffinityVisual visual, String targetPart) {
        JsonObject template = readJsonObjectResource(RAW_CONDITION_TEMPLATE);
        if (template == null) {
            throw new IllegalStateException("Missing weapon affinity raw item condition template: "
                    + RAW_CONDITION_TEMPLATE);
        }
        Map<String, JsonElement> values = Map.of(
                "VALUE", new JsonPrimitive(visual.value()),
                "SYSTEM_ID", new JsonPrimitive(visual.systemId()),
                "TARGET_ENTITY_PART", new JsonPrimitive(targetPart),
                "CONDITION_VALUE_TYPE", new JsonPrimitive(ABSOLUTE),
                "SCALE", new JsonPrimitive(0.2d));
        return substituteTemplate(template, values).getAsJsonObject();
    }

    private static boolean normalizeRawAffinityConditions(JsonArray conditions) {
        boolean changed = false;
        for (JsonElement element : conditions) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject condition = element.getAsJsonObject();
            if (!isRawAffinityCondition(condition)) {
                continue;
            }
            JsonElement current = condition.get(CONDITION_VALUE_TYPE);
            if (current == null || !current.isJsonPrimitive() || !ABSOLUTE.equalsIgnoreCase(current.getAsString())) {
                condition.addProperty(CONDITION_VALUE_TYPE, ABSOLUTE);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean isRawAffinityCondition(JsonObject condition) {
        JsonArray particles = arrayValue(condition, PARTICLES);
        if (particles == null || particles.isEmpty() || !particles.get(0).isJsonObject()) {
            return false;
        }
        String systemId = stringValue(particles.get(0).getAsJsonObject(), SYSTEM_ID);
        return systemId != null && systemId.startsWith("Weapon_Affinity_");
    }

    private static boolean containsRawCondition(JsonArray conditions, int value) {
        for (JsonElement element : conditions) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonArray range = arrayValue(element.getAsJsonObject(), CONDITION);
            if (range == null || range.size() < 2) {
                continue;
            }
            if (range.get(0).getAsInt() == value && range.get(1).getAsInt() == value) {
                return true;
            }
        }
        return false;
    }

    private static List<AffinityVisual> affinityVisuals() {
        return List.of(
                new AffinityVisual(1, "Weapon_Affinity_Fire"),
                new AffinityVisual(2, "Weapon_Affinity_Ice"),
                new AffinityVisual(3, "Weapon_Affinity_Life"),
                new AffinityVisual(4, "Weapon_Affinity_Lightning"),
                new AffinityVisual(5, "Weapon_Affinity_Void"),
                new AffinityVisual(6, "Weapon_Affinity_Water"));
    }

    private static boolean isRawWeaponItem(Path file, JsonObject item) {
        if (item == null || !item.has(WEAPON) || item.has(ARMOR)) {
            return false;
        }
        String baseName = file == null ? "" : file.getFileName().toString();
        String fileName = baseName.toLowerCase(Locale.ROOT);
        if (fileName.startsWith("template_")
            || fileName.startsWith("test_")
            || fileName.startsWith("debug_")
            || fileName.startsWith("weapon_arrow")
            || fileName.contains("spawn_marker")
            || fileName.contains("camera")
            || fileName.contains("_projectile")
            || fileName.contains("_bomb")
            || fileName.contains("staff")
            || fileName.contains("wand")
            || fileName.contains("spellbook")
            || fileName.contains("shield")
                || fileName.contains("buckler")) {
            return false;
        }
        String quality = stringValue(item, QUALITY);
        if (TEMPLATE_QUALITY.equalsIgnoreCase(quality)) {
            return false;
        }
        String model = stringValue(item, MODEL);
        if (model == null || !model.endsWith(".blockymodel")) {
            return false;
        }
        String normalizedModel = model.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (normalizedModel.contains("/staff/")
                || normalizedModel.contains("/wand/")
                || normalizedModel.contains("/spellbook/")
                || normalizedModel.contains("staff")
                || normalizedModel.contains("wand")
                || normalizedModel.contains("spellbook")) {
            return false;
        }
        return normalizedModel.startsWith("items/weapons/")
                || normalizedModel.startsWith("npc/");
    }

    private static JsonObject objectValue(JsonObject object, String name) {
        if (object == null) {
            return null;
        }
        JsonElement element = object.get(name);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static JsonArray arrayValue(JsonObject object, String name) {
        if (object == null) {
            return null;
        }
        JsonElement element = object.get(name);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static String stringValue(JsonObject object, String name) {
        if (object == null) {
            return null;
        }
        JsonElement element = object.get(name);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        try {
            return element.getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    private static Set<Path> commonRoots(ReforgePlugin plugin) {
        Set<Path> roots = new LinkedHashSet<>();
        addRoot(roots, Path.of("Common"));
        addRoot(roots, Path.of("Server/Common"));
        addRoot(roots, Path.of("src/main/resources/Common"));
        addRoot(roots, Path.of("server/Common"));
        addRoot(roots, Path.of("server/Server/Common"));
        for (Path runtimeRoot : runtimeModDataRoots(plugin, true)) {
            addRoot(roots, runtimeRoot.resolve("Common"));
        }

        if (plugin != null && plugin.getFile() != null) {
            Path pluginFile = plugin.getFile();
            if (Files.isDirectory(pluginFile)) {
                addRoot(roots, pluginFile.resolve("Common"));
            }
            Path parent = pluginFile.getParent();
            if (parent != null) {
                addRoot(roots, parent.resolve("Common"));
                Path grandParent = parent.getParent();
                if (grandParent != null) {
                    addRoot(roots, grandParent.resolve("Common"));
                }
            }
        }
        return roots;
    }

    private static Set<Path> itemRoots(ReforgePlugin plugin) {
        Set<Path> roots = new LinkedHashSet<>();
        addRoot(roots, Path.of("Item/Items/Weapon"));
        addRoot(roots, Path.of("Server/Item/Items/Weapon"));
        addRoot(roots, Path.of("src/main/resources/Server/Item/Items/Weapon"));
        addRoot(roots, Path.of("server/Item/Items/Weapon"));
        addRoot(roots, Path.of("server/Server/Item/Items/Weapon"));
        for (Path runtimeRoot : runtimeModDataRoots(plugin, true)) {
            addRoot(roots, runtimeRoot.resolve("Server/Item/Items/Weapon"));
        }

        if (plugin != null && plugin.getFile() != null) {
            Path pluginFile = plugin.getFile();
            if (Files.isDirectory(pluginFile)) {
                addRoot(roots, pluginFile.resolve("Server/Item/Items/Weapon"));
            }
            Path parent = pluginFile.getParent();
            if (parent != null) {
                addRoot(roots, parent.resolve("Item/Items/Weapon"));
                addRoot(roots, parent.resolve("Server/Item/Items/Weapon"));
                Path grandParent = parent.getParent();
                if (grandParent != null) {
                    addRoot(roots, grandParent.resolve("Item/Items/Weapon"));
                    addRoot(roots, grandParent.resolve("Server/Item/Items/Weapon"));
                }
            }
        }
        return roots;
    }

    private static Set<Path> runtimeModDataRoots(ReforgePlugin plugin, boolean create) {
        Set<Path> roots = new LinkedHashSet<>();
        addRuntimeModDataRoot(roots, Path.of("mods"), create);
        addRuntimeModDataRoot(roots, Path.of("server/mods"), create);

        if (plugin != null && plugin.getFile() != null) {
            Path pluginFile = plugin.getFile();
            if (Files.isDirectory(pluginFile) && RUNTIME_MOD_DATA_FOLDER.equals(pluginFile.getFileName().toString())) {
                addRuntimeRoot(roots, pluginFile, create);
            }
            Path parent = pluginFile.getParent();
            if (parent != null) {
                addRuntimeModDataRoot(roots, parent, create);
            }
        }
        return roots;
    }

    private static void addRuntimeModDataRoot(Set<Path> roots, Path modsRoot, boolean create) {
        if (modsRoot == null) {
            return;
        }
        Path absoluteModsRoot = modsRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(absoluteModsRoot)) {
            return;
        }
        addRuntimeRoot(roots, absoluteModsRoot.resolve(RUNTIME_MOD_DATA_FOLDER), create);
    }

    private static void addRuntimeRoot(Set<Path> roots, Path root, boolean create) {
        if (root == null) {
            return;
        }
        Path absolute = root.toAbsolutePath().normalize();
        if (create) {
            try {
                Files.createDirectories(absolute.resolve("Common"));
                Files.createDirectories(absolute.resolve("Server/Item/Items/Weapon"));
                Files.createDirectories(absolute.resolve(RUNTIME_TEMPLATE_FOLDER));
            } catch (IOException e) {
                System.err.println("[SocketReforge] Failed to prepare weapon affinity runtime override root "
                        + absolute + ": " + e.getMessage());
                return;
            }
        }
        if (Files.isDirectory(absolute)) {
            roots.add(absolute);
        }
    }

    private static void prepareRuntimeModelOverrideFolders(ReforgePlugin plugin,
                                                           String modelPath,
                                                           ModelPatchResult result) {
        if (modelPath == null || modelPath.isBlank()) {
            return;
        }
        Path relative = Path.of(modelPath.replace('\\', '/'));
        Path parent = relative.getParent();
        if (parent == null) {
            return;
        }
        for (Path runtimeRoot : runtimeModDataRoots(plugin, true)) {
            Path folder = runtimeRoot.resolve("Common").resolve(parent).toAbsolutePath().normalize();
            try {
                Files.createDirectories(folder);
                String path = folder.toString();
                if (!result.preparedOverrideFolders.contains(path)) {
                    result.preparedOverrideFolders.add(path);
                }
            } catch (IOException e) {
                System.err.println("[SocketReforge] Failed to prepare weapon affinity model override folder "
                        + folder + ": " + e.getMessage());
            }
        }
    }

    private static Path writeRuntimeModelOverride(ReforgePlugin plugin, String modelPath, JsonObject model) {
        if (modelPath == null || modelPath.isBlank() || model == null) {
            return null;
        }
        Path relative = Path.of(modelPath.replace('\\', '/'));
        for (Path runtimeRoot : runtimeModDataRoots(plugin, true)) {
            Path file = runtimeRoot.resolve("Common").resolve(relative).toAbsolutePath().normalize();
            try {
                Files.createDirectories(file.getParent());
                writeJsonObject(file, model);
                return file;
            } catch (IOException e) {
                System.err.println("[SocketReforge] Failed to export weapon affinity model override "
                        + file + ": " + e.getMessage());
            }
        }
        return null;
    }

    private static ItemSource readItemSource(String itemId) throws IOException {
        Path loadedAssetPath = resolveLoadedItemAssetPath(itemId);
        Path loadedRelativePath = normalizeItemOverridePath(loadedAssetPath);
        if (loadedAssetPath != null && Files.isRegularFile(loadedAssetPath)) {
            return new ItemSource(readJsonObject(loadedAssetPath),
                    loadedRelativePath == null ? loadedAssetPath : loadedRelativePath,
                    loadedAssetPath.toString());
        }

        ItemSource editorSource = readItemSourceFromAssetEditor(itemId, loadedRelativePath);
        if (editorSource != null) {
            return editorSource;
        }
        return null;
    }

    private static ItemSource readItemSourceFromAssetEditor(String itemId, Path loadedRelativePath) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        AssetEditorPlugin editor;
        try {
            editor = AssetEditorPlugin.get();
        } catch (Exception ignored) {
            return null;
        }
        if (editor == null) {
            return null;
        }

        String packId = resolveLoadedItemAssetPack(itemId);
        List<DataSource> sources = new ArrayList<>();
        if (packId != null && !packId.isBlank()) {
            try {
                DataSource source = editor.getDataSourceForPack(packId);
                if (source != null) {
                    sources.add(source);
                }
            } catch (Exception ignored) {
            }
        }
        try {
            for (DataSource source : editor.getDataSources()) {
                if (source != null && !sources.contains(source)) {
                    sources.add(source);
                }
            }
        } catch (Exception ignored) {
        }

        List<Path> candidatePaths = itemAssetCandidatePaths(itemId, loadedRelativePath);
        for (DataSource source : sources) {
            for (Path candidatePath : candidatePaths) {
                try {
                    if (!source.doesAssetExist(candidatePath)) {
                        continue;
                    }
                    byte[] bytes = source.getAssetBytes(candidatePath);
                    if (bytes == null || bytes.length == 0) {
                        continue;
                    }
                    String json = new String(bytes, StandardCharsets.UTF_8);
                    JsonObject item = JsonParser.parseString(json).getAsJsonObject();
                    return new ItemSource(item,
                            normalizeItemOverridePath(candidatePath),
                            "asset editor datasource "
                                    + source.getRootPath()
                                    + " :: "
                                    + candidatePath);
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private static List<Path> itemAssetCandidatePaths(String itemId, Path loadedRelativePath) {
        List<Path> paths = new ArrayList<>();
        addCandidatePath(paths, loadedRelativePath);
        Path assetPath = resolveLoadedItemAssetPath(itemId);
        addCandidatePath(paths, normalizeItemOverridePath(assetPath));
        addCandidatePath(paths, Path.of("Server", "Item", "Items", itemId + ".json"));
        addCandidatePath(paths, Path.of("Item", "Items", itemId + ".json"));
        addCandidatePath(paths, Path.of("Server", "Item", "Items", "Weapon", itemId + ".json"));
        addCandidatePath(paths, Path.of("Item", "Items", "Weapon", itemId + ".json"));
        return paths;
    }

    private static void addCandidatePath(List<Path> paths, Path path) {
        if (path == null) {
            return;
        }
        Path normalized = path.normalize();
        if (!paths.contains(normalized)) {
            paths.add(normalized);
        }
    }

    private static Path writeRuntimeItemOverride(ReforgePlugin plugin, Path sourcePath, JsonObject item) {
        if (sourcePath == null || item == null) {
            return null;
        }
        Path relative = normalizeItemOverridePath(sourcePath);
        if (relative == null) {
            return null;
        }
        for (Path runtimeRoot : runtimeModDataRoots(plugin, true)) {
            Path file = runtimeRoot.resolve(relative).toAbsolutePath().normalize();
            try {
                Files.createDirectories(file.getParent());
                return file;
            } catch (IOException e) {
                System.err.println("[SocketReforge] Failed to prepare weapon affinity item override "
                        + file + ": " + e.getMessage());
            }
        }
        return null;
    }

    private static boolean isSameJsonFile(Path file, JsonObject item) {
        if (file == null || item == null || !Files.isRegularFile(file)) {
            return false;
        }
        try {
            return readJsonObject(file).equals(item);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Path normalizeItemOverridePath(Path path) {
        if (path == null) {
            return null;
        }
        Path normalized = path.normalize();
        List<String> names = new ArrayList<>();
        for (Path name : normalized) {
            names.add(name.toString());
        }
        for (int i = 0; i < names.size(); i++) {
            if ("Server".equalsIgnoreCase(names.get(i)) && isItemAssetPath(names, i + 1)) {
                return subpath(names, i);
            }
            if ("Item".equalsIgnoreCase(names.get(i)) && isItemAssetPath(names, i)) {
                return Path.of("Server").resolve(subpath(names, i));
            }
        }
        return normalized;
    }

    private static boolean isItemAssetPath(List<String> names, int start) {
        if (names == null
                || start < 0
                || start + 1 >= names.size()
                || !"Item".equalsIgnoreCase(names.get(start))
                || !"Items".equalsIgnoreCase(names.get(start + 1))) {
            return false;
        }
        return start + 2 == names.size()
                || (start + 2 < names.size() && "Weapon".equalsIgnoreCase(names.get(start + 2)))
                || (start + 2 < names.size() && names.get(start + 2).endsWith(".json"));
    }

    private static Path subpath(List<String> names, int start) {
        if (names == null || start < 0 || start >= names.size()) {
            return null;
        }
        Path path = Path.of(names.get(start));
        for (int i = start + 1; i < names.size(); i++) {
            path = path.resolve(names.get(i));
        }
        return path;
    }

    private static boolean isObsoleteRuntimeFallbackClone(ReforgePlugin plugin,
                                                          Path file,
                                                          String modelPath,
                                                          JsonObject spinSourceModel) {
        if (file == null || modelPath == null || spinSourceModel == null) {
            return false;
        }
        if (SPIN_TEMPLATE_MODEL.equals(modelPath.replace('\\', '/'))) {
            return false;
        }

        Path normalizedFile = file.toAbsolutePath().normalize();
        boolean runtimeOverride = false;
        for (Path runtimeRoot : runtimeModDataRoots(plugin, false)) {
            Path commonRoot = runtimeRoot.resolve("Common").toAbsolutePath().normalize();
            if (normalizedFile.startsWith(commonRoot)) {
                runtimeOverride = true;
                break;
            }
        }
        if (!runtimeOverride) {
            return false;
        }

        try {
            return readJsonObject(normalizedFile).equals(spinSourceModel);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void addRoot(Set<Path> roots, Path path) {
        if (path == null) {
            return;
        }
        Path absolute = path.toAbsolutePath().normalize();
        if (Files.isDirectory(absolute)) {
            roots.add(absolute);
        }
    }

    private static JsonObject readSpinTemplate(Set<Path> commonRoots) {
        Path template = resolveModelFile(commonRoots, SPIN_TEMPLATE_MODEL);
        try {
            JsonObject model = template == null
                    ? readJsonResource("Common/" + SPIN_TEMPLATE_MODEL)
                    : readJsonObject(template);
            if (model == null) {
                return null;
            }
            System.out.println("[SocketReforge] Weapon affinity Spin1 node template loaded from "
                    + (template == null ? "packaged resource Common/" + SPIN_TEMPLATE_MODEL : template));
            return findNodeByName(nodes(model), SPIN_NODE_NAME);
        } catch (Exception e) {
            System.err.println("[SocketReforge] Failed to read weapon affinity spin template: " + e.getMessage());
            return null;
        }
    }

    private static JsonObject readSpinSourceModel(Set<Path> commonRoots) {
        Path template = resolveModelFile(commonRoots, SPIN_TEMPLATE_MODEL);
        try {
            return template == null
                    ? readJsonResource("Common/" + SPIN_TEMPLATE_MODEL)
                    : readJsonObject(template);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Path resolveModelFile(Set<Path> commonRoots, String modelPath) {
        if (modelPath == null || modelPath.isBlank()) {
            return null;
        }
        String normalized = modelPath.replace('\\', '/');
        for (Path root : commonRoots) {
            Path candidate = root.resolve(normalized).normalize();
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static ModelSource readModelSource(Set<Path> commonRoots, String modelPath) throws IOException {
        Path file = resolveModelFile(commonRoots, modelPath);
        if (file != null) {
            return new ModelSource(readJsonObject(file), file.toString());
        }

        Path loadedAssetPath = resolveLoadedModelAssetPath(modelPath);
        if (loadedAssetPath != null && Files.isRegularFile(loadedAssetPath)) {
            return new ModelSource(readJsonObject(loadedAssetPath), loadedAssetPath.toString());
        }

        ModelSource editorSource = readModelSourceFromAssetEditor(modelPath);
        if (editorSource != null) {
            return editorSource;
        }

        JsonObject resource = readJsonResource("Common/" + modelPath.replace('\\', '/'));
        if (resource != null) {
            return new ModelSource(resource, "packaged resource Common/" + modelPath.replace('\\', '/'));
        }
        return null;
    }

    private static ModelSource readModelSourceFromAssetEditor(String modelPath) {
        if (modelPath == null || modelPath.isBlank()) {
            return null;
        }
        AssetEditorPlugin editor;
        try {
            editor = AssetEditorPlugin.get();
        } catch (Exception ignored) {
            return null;
        }
        if (editor == null) {
            return null;
        }

        String normalized = modelPath.replace('\\', '/');
        String packId = resolveLoadedModelAssetPack(normalized);
        List<DataSource> sources = new ArrayList<>();
        if (packId != null && !packId.isBlank()) {
            try {
                DataSource source = editor.getDataSourceForPack(packId);
                if (source != null) {
                    sources.add(source);
                }
            } catch (Exception ignored) {
            }
        }
        try {
            for (DataSource source : editor.getDataSources()) {
                if (source != null && !sources.contains(source)) {
                    sources.add(source);
                }
            }
        } catch (Exception ignored) {
        }

        List<Path> candidatePaths = new ArrayList<>();
        candidatePaths.add(Path.of("Common").resolve(Path.of(normalized)));
        candidatePaths.add(Path.of(normalized));

        for (DataSource source : sources) {
            for (Path candidatePath : candidatePaths) {
                try {
                    if (!source.doesAssetExist(candidatePath)) {
                        continue;
                    }
                    byte[] bytes = source.getAssetBytes(candidatePath);
                    if (bytes == null || bytes.length == 0) {
                        continue;
                    }
                    String json = new String(bytes, StandardCharsets.UTF_8);
                    JsonObject model = JsonParser.parseString(json).getAsJsonObject();
                    String sourceLabel = "asset editor datasource "
                            + source.getRootPath()
                            + " :: "
                            + candidatePath;
                    return new ModelSource(model, sourceLabel);
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private static String resolveLoadedModelAssetPack(String modelPath) {
        if (modelPath == null || modelPath.isBlank()) {
            return null;
        }
        DefaultAssetMap<String, ModelAsset> modelMap = ModelAsset.getAssetMap();
        if (modelMap == null) {
            return null;
        }
        String normalized = modelPath.replace('\\', '/');
        List<String> keys = new ArrayList<>();
        keys.add(normalized);
        if (normalized.endsWith(".blockymodel")) {
            keys.add(normalized.substring(0, normalized.length() - ".blockymodel".length()));
        }
        keys.add("Common/" + normalized);

        for (String key : keys) {
            try {
                String packId = modelMap.getAssetPack(key);
                if (packId != null && !packId.isBlank()) {
                    return packId;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static String resolveLoadedItemAssetPack(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        DefaultAssetMap<String, Item> itemMap = Item.getAssetMap();
        if (itemMap == null) {
            return null;
        }
        try {
            String packId = itemMap.getAssetPack(itemId);
            return packId == null || packId.isBlank() ? null : packId;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Path resolveLoadedModelAssetPath(String modelPath) {
        if (modelPath == null || modelPath.isBlank()) {
            return null;
        }
        DefaultAssetMap<String, ModelAsset> modelMap = ModelAsset.getAssetMap();
        if (modelMap == null) {
            return null;
        }
        String normalized = modelPath.replace('\\', '/');
        List<String> keys = new ArrayList<>();
        keys.add(normalized);
        if (normalized.endsWith(".blockymodel")) {
            keys.add(normalized.substring(0, normalized.length() - ".blockymodel".length()));
        }
        keys.add("Common/" + normalized);

        for (String key : keys) {
            try {
                Path path = modelMap.getPath(key);
                if (path != null && Files.isRegularFile(path)) {
                    return path.toAbsolutePath().normalize();
                }
            } catch (Exception ignored) {
            }
        }

        try {
            Map<String, Path> pathMap = modelMap.getPathMap(DefaultAssetMap.DEFAULT_PACK_KEY);
            if (pathMap != null) {
                for (Map.Entry<String, Path> entry : pathMap.entrySet()) {
                    String key = entry.getKey() == null ? "" : entry.getKey().replace('\\', '/');
                    Path path = entry.getValue();
                    if ((normalized.equals(key)
                            || key.endsWith("/" + normalized)
                            || (normalized.endsWith(".blockymodel")
                            && key.equals(normalized.substring(0, normalized.length() - ".blockymodel".length()))))
                            && path != null
                            && Files.isRegularFile(path)) {
                        return path.toAbsolutePath().normalize();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Path resolveLoadedItemAssetPath(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        DefaultAssetMap<String, Item> itemMap = Item.getAssetMap();
        if (itemMap == null) {
            return null;
        }

        try {
            Path path = itemMap.getPath(itemId);
            if (path != null) {
                return path.normalize();
            }
        } catch (Exception ignored) {
        }

        try {
            Map<String, Path> pathMap = itemMap.getPathMap(DefaultAssetMap.DEFAULT_PACK_KEY);
            if (pathMap != null) {
                for (Map.Entry<String, Path> entry : pathMap.entrySet()) {
                    String key = entry.getKey() == null ? "" : entry.getKey();
                    Path path = entry.getValue();
                    if (itemId.equals(key) && path != null) {
                        return path.normalize();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String patchModelFile(Path file, JsonObject spinTemplate) throws IOException {
        JsonObject model = readJsonObject(file);
        String state = patchModelObject(model, spinTemplate);
        if ("patched".equals(state)) {
            writeJsonObject(file, model);
        }
        return state;
    }

    private static String patchModelObject(JsonObject model, JsonObject spinTemplate) {
        if (findNodeByName(nodes(model), FIRST_SPIN_ATTACHMENT) != null) {
            return "present";
        }

        JsonObject anchor = findNodeByName(nodes(model), "Handle");
        if (anchor == null) {
            anchor = findNodeByName(nodes(model), "R-Attachment");
        }
        if (anchor == null) {
            return "no-anchor";
        }

        JsonObject spin = spinTemplate.deepCopy();
        int nextId = maxNodeId(nodes(model)) + 1;
        assignNodeIds(spin, new int[] {nextId});
        children(anchor).add(spin);
        return "patched";
    }

    private static JsonObject readJsonObject(Path file) throws IOException {
        String json = Files.readString(file, StandardCharsets.UTF_8);
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static JsonObject readJsonResource(String path) throws IOException {
        String json = readTextResource(path);
        return json == null ? null : JsonParser.parseString(json).getAsJsonObject();
    }

    private static String readTextResource(String path) throws IOException {
        ClassLoader loader = RuntimeWeaponAffinityPatchHost.class.getClassLoader();
        try (InputStream stream = loader == null ? null : loader.getResourceAsStream(path)) {
            return stream == null ? null : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static JsonObject readJsonObjectResource(String path) {
        try {
            return readJsonResource(path);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read JSON template resource " + path, e);
        }
    }

    private static JsonElement substituteTemplate(JsonElement element, Map<String, JsonElement> values) {
        if (element == null || element.isJsonNull()) {
            return element;
        }
        if (element.isJsonObject()) {
            JsonObject copy = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                copy.add(entry.getKey(), substituteTemplate(entry.getValue(), values));
            }
            return copy;
        }
        if (element.isJsonArray()) {
            JsonArray copy = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) {
                copy.add(substituteTemplate(child, values));
            }
            return copy;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return element.deepCopy();
        }

        String text = element.getAsString();
        JsonElement exact = values.get(templateKey(text));
        if (exact != null) {
            return exact.deepCopy();
        }
        String replaced = text;
        for (Map.Entry<String, JsonElement> entry : values.entrySet()) {
            if (entry.getValue().isJsonPrimitive()) {
                JsonPrimitive primitive = entry.getValue().getAsJsonPrimitive();
                if (primitive.isString() || primitive.isNumber() || primitive.isBoolean()) {
                    replaced = replaced.replace("${" + entry.getKey() + "}", primitive.getAsString());
                }
            }
        }
        return new JsonPrimitive(replaced);
    }

    private static String templateKey(String text) {
        if (text == null || !text.startsWith("${") || !text.endsWith("}")) {
            return "";
        }
        return text.substring(2, text.length() - 1);
    }

    private static void writeJsonObject(Path file, JsonObject object) throws IOException {
        Files.writeString(file, GSON.toJson(object) + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    private static String exceptionSummary(Exception exception) {
        if (exception == null) {
            return "unknown";
        }
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return exception.getClass().getSimpleName() + ": " + message;
    }

    private static JsonArray nodes(JsonObject model) {
        JsonElement nodes = model.get("nodes");
        return nodes != null && nodes.isJsonArray() ? nodes.getAsJsonArray() : new JsonArray();
    }

    private static JsonArray children(JsonObject node) {
        JsonElement children = node.get("children");
        if (children != null && children.isJsonArray()) {
            return children.getAsJsonArray();
        }
        JsonArray array = new JsonArray();
        node.add("children", array);
        return array;
    }

    private static JsonObject findNodeByName(JsonArray nodes, String name) {
        for (JsonElement element : nodes) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject node = element.getAsJsonObject();
            JsonElement nodeName = node.get("name");
            if (nodeName != null && name.equals(nodeName.getAsString())) {
                return node;
            }
            JsonObject found = findNodeByName(children(node), name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static int maxNodeId(JsonArray nodes) {
        int max = -1;
        for (JsonElement element : nodes) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject node = element.getAsJsonObject();
            JsonElement id = node.get("id");
            if (id != null) {
                try {
                    max = Math.max(max, Integer.parseInt(id.getAsString()));
                } catch (NumberFormatException ignored) {
                }
            }
            max = Math.max(max, maxNodeId(children(node)));
        }
        return max;
    }

    private static void assignNodeIds(JsonObject node, int[] nextId) {
        if (node.has("id")) {
            node.addProperty("id", Integer.toString(nextId[0]++));
        }
        for (JsonElement child : children(node)) {
            if (child.isJsonObject()) {
                assignNodeIds(child.getAsJsonObject(), nextId);
            }
        }
    }

    private static final class ModelPatchResult {
        private int patched;
        private int present;
        private int missing;
        private int noAnchor;
        private final List<String> patchedPaths = new java.util.ArrayList<>();
        private final List<String> removedClonePaths = new java.util.ArrayList<>();
        private final List<String> exportedPaths = new java.util.ArrayList<>();
        private final List<String> exportedSourcePaths = new java.util.ArrayList<>();
        private final List<String> presentPaths = new java.util.ArrayList<>();
        private final List<String> preparedOverrideFolders = new java.util.ArrayList<>();
        private final List<String> missingPaths = new java.util.ArrayList<>();
        private final List<String> noAnchorPaths = new java.util.ArrayList<>();

        private Set<String> readyModelPaths() {
            Set<String> paths = new LinkedHashSet<>();
            paths.addAll(patchedPaths);
            paths.addAll(presentPaths);
            return paths;
        }
    }

    private record ModelSource(JsonObject model, String description) {
    }

    private record ItemSource(JsonObject item, Path sourcePath, String description) {
    }

    private static final class RawItemPatchResult {
        private int eligible;
        private int patched;
        private int ready;
        private final List<String> roots = new ArrayList<>();
        private final List<String> patchedPaths = new ArrayList<>();
        private final List<String> readyPaths = new ArrayList<>();
        private final List<String> unverifiedModelPaths = new ArrayList<>();
        private final List<String> skippedPaths = new ArrayList<>();
        private final List<String> failedPaths = new ArrayList<>();
    }

    private static final class ExportedItemPatchResult {
        private int exported;
        private int ready;
        private final List<String> exportedPaths = new ArrayList<>();
        private final List<String> readyPaths = new ArrayList<>();
        private final List<String> sourcePaths = new ArrayList<>();
        private final List<String> skippedPaths = new ArrayList<>();
        private final List<String> failedPaths = new ArrayList<>();
    }

    private static final class TemplateExportResult {
        private final List<String> exportedPaths = new ArrayList<>();
        private final List<String> updatedPaths = new ArrayList<>();
        private final List<String> readyPaths = new ArrayList<>();
        private final List<String> removedPaths = new ArrayList<>();
        private final List<String> failedPaths = new ArrayList<>();
    }

    private record AffinityVisual(int value, String systemId) {
    }
}
