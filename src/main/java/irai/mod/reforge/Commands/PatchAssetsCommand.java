package irai.mod.reforge.Commands;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.annotation.Nonnull;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

/**
 * In-game asset patcher command.
 *
 * Since the Hytale command framework does not pass arguments to commands,
 * the Assets.zip path is configured via ONE of the following (checked in order):
 *
 *   1. assets_path.txt file in the server root directory:
 *         Create a file called assets_path.txt next to your server jar,
 *         containing just the path on a single line:
 *         F:\XboxGames\Hytale\install\release\package\game\latest\Assets.zip
 *
 *   2. ASSETSPATH environment variable:
 *         Windows: set ASSETSPATH=F:\XboxGames\Hytale\install\release\package\game\latest\Assets.zip
 *         Linux:   export ASSETSPATH=/path/to/Assets.zip
 *
 *   3. No path set — only local Assets folder/zip and mod JARs will be scanned.
 *
 * Usage: /patchassets
 */
public class PatchAssetsCommand extends CommandBase {

    private static final String MOD_NAME = "SocketReforge";
    private static final int MAX_UPGRADE_LEVEL = 3;

    private static final String DEFAULT_SOURCE = "Assets";
    private static final String DEFAULT_TARGET = "irai.mod.reforge_" + MOD_NAME;

    // Name of the config file that holds the assets path
    private static final String PATH_CONFIG_FILE = "assets_path.txt";

    // In-memory overrides, settable at runtime
    private static String storedAssetsPath     = null;
    private static String storedModsPath       = null;
    private static String storedGlobalModsPath = null;

    /**
     * Dynamically discovered armor parent template names.
     * Populated during scanning of items found in known Armor/ paths.
     * Used to identify armor items in JARs that don't use standard parent names.
     */
    private final java.util.Set<String> discoveredArmorParents = new java.util.LinkedHashSet<>();

    public static void setStoredAssetsPath(String path)     { storedAssetsPath = path; }
    public static String getStoredAssetsPath()              { return storedAssetsPath; }

    public static void setStoredModsPath(String path)       { storedModsPath = path; }
    public static String getStoredModsPath()                { return storedModsPath; }

    public static void setStoredGlobalModsPath(String path) { storedGlobalModsPath = path; }
    public static String getStoredGlobalModsPath()          { return storedGlobalModsPath; }

    public PatchAssetsCommand(@NonNullDecl String name,
                              @NonNullDecl String description,
                              boolean requiresConfirmation) {
        super(name, description, requiresConfirmation);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        try {
            // Read config file once, then resolve both paths from it
            Map<String, String> config = readConfig(context);
            Path explicitSource = resolveAssetsPath(context, config);
            Path saveMods       = resolveModsPath(context, config);
            Path globalMods     = resolveGlobalModsPath(context, config);

            if (saveMods == null) {
                context.sendMessage(Message.raw("[ERROR] Could not detect save mods folder."));
                context.sendMessage(Message.raw("Set mods_path in " + PATH_CONFIG_FILE + " and run /patchassets again."));
                context.sendMessage(Message.raw("  Client: mods_path = F:\\XboxGames\\Hytale\\UserData\\Saves\\MySave\\mods"));
                context.sendMessage(Message.raw("  Server: mods_path = .\\server\\mods"));
                return;
            }
            context.sendMessage(Message.raw("[DIR] Save mods folder:   " + saveMods.toAbsolutePath()));
            if (globalMods != null) {
                context.sendMessage(Message.raw("[DIR] Global mods folder: " + globalMods.toAbsolutePath()));
            } else {
                context.sendMessage(Message.raw("[DIR] No global mods folder (server mode or not set)"));
            }

            Path basePath = saveMods;

            Path targetPath = basePath.resolve(DEFAULT_TARGET);
            if (!Files.exists(targetPath)) {
                Files.createDirectories(targetPath);
                context.sendMessage(Message.raw("Created target directory: " + targetPath));
            }

            context.sendMessage(Message.raw("Scanning for weapons and armor..."));
            // Map: itemId -> [content, sourceModFolder, subfolder] (sourceModFolder null = base game)
            Map<String, String[]> itemData = scanAllSources(context, saveMods, globalMods, explicitSource);
            
            // Map: itemId -> displayName from source server.lang
            Map<String, String> sourceLangNames = new LinkedHashMap<>();
            scanAllLangFiles(context, saveMods, globalMods, explicitSource, sourceLangNames);

            if (itemData.isEmpty()) {
                context.sendMessage(Message.raw("[ERROR] No weapons or armor found!"));
                context.sendMessage(Message.raw("Create assets_path.txt in your server root containing the path to Assets.zip"));
                context.sendMessage(Message.raw("Example: F:\\XboxGames\\Hytale\\install\\release\\package\\game\\latest\\Assets.zip"));
                return;
            }

            long weaponCount = itemData.keySet().stream().filter(id -> id.toLowerCase().startsWith("weapon_")).count();
            long armorCount  = itemData.keySet().stream().filter(id -> id.toLowerCase().startsWith("armor_")).count();
            context.sendMessage(Message.raw("Found " + weaponCount + " base weapons, " + armorCount + " base armor pieces"));

            Set<String> baseIds    = new LinkedHashSet<>(itemData.keySet());
            Set<String> upgradeIds = new LinkedHashSet<>();
            for (String baseId : baseIds) {
                for (int level = 1; level <= MAX_UPGRADE_LEVEL; level++) {
                    upgradeIds.add(baseId + level);
                }
            }

            int baseWritten     = processWeapons(itemData, targetPath, saveMods);
            int upgradesCreated = createUpgrades(itemData, targetPath, saveMods);
            int langEntries     = writeServerLang(context, targetPath, itemData, sourceLangNames, baseIds, upgradeIds);
            boolean manifestCreated = writeManifest(targetPath, baseIds, upgradeIds);

            context.sendMessage(Message.raw("========== SUMMARY =========="));
            context.sendMessage(Message.raw("Base weapons:  " + weaponCount));
            context.sendMessage(Message.raw("Base armor:    " + armorCount));
            context.sendMessage(Message.raw("Upgrades:      " + upgradesCreated));
            context.sendMessage(Message.raw("Lang entries:  " + langEntries));
            context.sendMessage(Message.raw("Manifest:      " + (manifestCreated ? "created" : "updated")));
            context.sendMessage(Message.raw("Done! Please restart your server!"));

        } catch (Exception e) {
            context.sendMessage(Message.raw("[ERROR] " + e.getMessage()));
            e.printStackTrace();
        }
    }

    // -------------------------------------------------------------------------
    // Path resolution
    // -------------------------------------------------------------------------

    /**
     * Resolve the Assets.zip/folder path in priority order:
     *   1. In-memory override (set programmatically)
     *   2. assets_path.txt file in the server root
     *   3. ASSETSPATH environment variable
     *
     * Returns the resolved Path if it exists, or null if none found.
     */
    /**
     * Read all key=value pairs from assets_path.txt, skipping comments and blank lines.
     * Creates the file with a template if it doesn't exist yet.
     * Returns a map of key -> value (keys are lowercase).
     */
    private Map<String, String> readConfig(CommandContext context) {
        Map<String, String> config = new LinkedHashMap<>();
        Path configFile = Paths.get(PATH_CONFIG_FILE);

        if (!Files.exists(configFile)) {
            try {
                Files.writeString(configFile,
                    "# assets_path.txt - SocketReforge Asset Patcher Configuration\n" +
                    "# Lines starting with # are comments and are ignored.\n" +
                    "# Format: key = value\n" +
                    "#\n" +
                    "# ASSETS PATH - full path to your game's Assets.zip\n" +
                    "# Examples:\n" +
                    "#   Windows (Xbox):  F:\\XboxGames\\Hytale\\install\\release\\package\\game\\latest\\Assets.zip\n" +
                    "#   Windows (Steam): C:\\Program Files (x86)\\Steam\\steamapps\\common\\Hytale\\install\\release\\package\\game\\latest\\Assets.zip\n" +
                    "#   Linux:           /home/user/.local/share/Hytale/install/release/package/game/latest/Assets.zip\n" +
                    "#\n" +
                    "assets_path =\n" +
                    "#\n" +
                    "# MODS PATH - full path to your server mods folder\n" +
                    "# Examples:\n" +
                    "#   Windows: C:\\HytaleServer\\mods\n" +
                    "#   Linux:   /opt/hytale/server/mods\n" +
                    "#\n" +
                    "mods_path =\n" +
                    "#\n" +
                    "# GLOBAL MODS PATH - client only: path to UserData\\Mods folder\n" +
                    "# Leave blank on a dedicated server (global == save mods on server)\n" +
                    "# Example (Xbox): F:\\XboxGames\\Hytale\\UserData\\Mods\n" +
                    "#\n" +
                    "global_mods_path =\n"
                );
                context.sendMessage(Message.raw("[INFO] Created " + PATH_CONFIG_FILE + " - edit it to set your paths, then run /patchassets again."));
            } catch (IOException e) {
                context.sendMessage(Message.raw("[WARN] Could not create " + PATH_CONFIG_FILE + ": " + e.getMessage()));
            }
            return config;
        }

        try {
            Files.lines(configFile)
                .map(String::trim)
                .filter(l -> !l.isBlank() && !l.startsWith("#"))
                .forEach(line -> {
                    int eq = line.indexOf('=');
                    if (eq > 0) {
                        String key = line.substring(0, eq).trim().toLowerCase();
                        String val = line.substring(eq + 1).trim();
                        if (!val.isBlank()) config.put(key, val);
                    }
                });
            context.sendMessage(Message.raw("[CONFIG] Loaded " + config.size() + " setting(s) from " + PATH_CONFIG_FILE));
            config.forEach((k, v) -> context.sendMessage(Message.raw("[CONFIG]   " + k + " = " + v)));
        } catch (IOException e) {
            context.sendMessage(Message.raw("[WARN] Could not read " + PATH_CONFIG_FILE + ": " + e.getMessage()));
        }

        return config;
    }

    private Path resolveAssetsPath(CommandContext context, Map<String, String> config) {
        // 1. In-memory override
        if (storedAssetsPath != null && !storedAssetsPath.isBlank()) {
            Path p = Paths.get(storedAssetsPath);
            if (Files.exists(p)) {
                context.sendMessage(Message.raw("[CONFIG] Using stored assets path: " + p.toAbsolutePath()));
                return p;
            }
            context.sendMessage(Message.raw("[WARN] Stored assets path not found: " + storedAssetsPath));
        }

        // 2. assets_path.txt config file
        String configVal = config.get("assets_path");
        if (configVal != null) {
            Path p = Paths.get(configVal);
            if (Files.exists(p)) {
                context.sendMessage(Message.raw("[CONFIG] Using assets_path from config: " + p.toAbsolutePath()));
                return p;
            }
            context.sendMessage(Message.raw("[WARN] assets_path in config not found: " + configVal));
        }

        // 3. Environment variable fallback
        String envPath = System.getenv("ASSETSPATH");
        if (envPath != null && !envPath.isBlank()) {
            Path p = Paths.get(envPath);
            if (Files.exists(p)) {
                context.sendMessage(Message.raw("[CONFIG] Using ASSETSPATH env var: " + p.toAbsolutePath()));
                return p;
            }
            context.sendMessage(Message.raw("[WARN] ASSETSPATH env var path not found: " + envPath));
        }

        context.sendMessage(Message.raw("[INFO] No assets_path configured - scanning local sources only."));
        return null;
    }

    private Path resolveModsPath(CommandContext context, Map<String, String> config) {
        // 1. In-memory override
        if (storedModsPath != null && !storedModsPath.isBlank()) {
            Path p = Paths.get(storedModsPath);
            if (Files.exists(p) && Files.isDirectory(p)) {
                context.sendMessage(Message.raw("[CONFIG] Using stored mods path: " + p.toAbsolutePath()));
                return p;
            }
            context.sendMessage(Message.raw("[WARN] Stored mods path not found: " + storedModsPath));
        }

        // 2. assets_path.txt config file
        String configVal = config.get("mods_path");
        if (configVal != null) {
            Path p = Paths.get(configVal);
            if (Files.exists(p) && Files.isDirectory(p)) {
                context.sendMessage(Message.raw("[CONFIG] Using mods_path from config: " + p.toAbsolutePath()));
                return p;
            }
            context.sendMessage(Message.raw("[WARN] mods_path in config not found or not a directory: " + configVal));
        }

        // 3. Environment variable fallback
        String envPath = System.getenv("MODSPATH");
        if (envPath != null && !envPath.isBlank()) {
            Path p = Paths.get(envPath);
            if (Files.exists(p) && Files.isDirectory(p)) {
                context.sendMessage(Message.raw("[CONFIG] Using MODSPATH env var: " + p.toAbsolutePath()));
                return p;
            }
            context.sendMessage(Message.raw("[WARN] MODSPATH env var path not found: " + envPath));
        }

        // 4. Auto-detect fallback
        context.sendMessage(Message.raw("[CONFIG] No mods_path configured - attempting auto-detection..."));
        return detectBasePath(context);
    }

    private Path resolveGlobalModsPath(CommandContext context, Map<String, String> config) {
        // 1. In-memory override
        if (storedGlobalModsPath != null && !storedGlobalModsPath.isBlank()) {
            Path p = Paths.get(storedGlobalModsPath);
            if (Files.exists(p) && Files.isDirectory(p)) {
                context.sendMessage(Message.raw("[CONFIG] Using stored global mods path: " + p.toAbsolutePath()));
                return p;
            }
            context.sendMessage(Message.raw("[WARN] Stored global mods path not found: " + storedGlobalModsPath));
        }

        // 2. Config file
        String configVal = config.get("global_mods_path");
        if (configVal != null) {
            Path p = Paths.get(configVal);
            if (Files.exists(p) && Files.isDirectory(p)) {
                context.sendMessage(Message.raw("[CONFIG] Using global_mods_path from config: " + p.toAbsolutePath()));
                return p;
            }
            context.sendMessage(Message.raw("[WARN] global_mods_path not found: " + configVal));
        }

        // 3. Environment variable
        String envPath = System.getenv("GLOBALMODSPATH");
        if (envPath != null && !envPath.isBlank()) {
            Path p = Paths.get(envPath);
            if (Files.exists(p) && Files.isDirectory(p)) {
                context.sendMessage(Message.raw("[CONFIG] Using GLOBALMODSPATH env var: " + p.toAbsolutePath()));
                return p;
            }
            context.sendMessage(Message.raw("[WARN] GLOBALMODSPATH env var not found: " + envPath));
        }

        // 4. Auto-detect: walk up from Assets.zip to find UserData/Mods
        String assetsVal = config.get("assets_path");
        if (assetsVal != null) {
            try {
                Path p = Paths.get(assetsVal).getParent();
                for (int i = 0; i < 8 && p != null; i++) {
                    Path candidate = p.resolve("UserData/Mods");
                    if (Files.exists(candidate) && Files.isDirectory(candidate)) {
                        context.sendMessage(Message.raw("[DIR] Auto-detected global mods: " + candidate.toAbsolutePath()));
                        return candidate;
                    }
                    p = p.getParent();
                }
            } catch (Exception ignored) { /* not critical */ }
        }

        // Server mode: global mods == save mods, return null so caller skips duplicate scan
        return null;
    }

    // -------------------------------------------------------------------------
    // Source scanning
    // -------------------------------------------------------------------------

    /**
     * Scan all sources in priority order (lowest to highest):
     *   1. Explicit Assets.zip or folder (from config)
     *   2. JAR files in the global mods folder
     *   3. JAR files in the save mods folder (highest priority)
     * Scans both weapons (Item/Items/Weapon/) and armor (Item/Items/Armor/).
     */
    private Map<String, String[]> scanAllSources(CommandContext context, Path saveMods, Path globalMods, Path explicitSource) throws IOException {
        // value: [content, sourceModFolder, subfolder]  -- sourceModFolder is null for base game assets
        Map<String, String[]> items = new LinkedHashMap<>();

        // 1. Assets.zip - base game items (lowest priority, no source mod folder)
        if (explicitSource != null) {
            context.sendMessage(Message.raw("[SCAN] Assets source: " + explicitSource.toAbsolutePath()));
            if (Files.isDirectory(explicitSource)) {
                int before = items.size();
                scanFolder(explicitSource, items);
                context.sendMessage(Message.raw("[SCAN] Assets folder: found " + (items.size() - before) + " items"));
            } else {
                int before = items.size();
                scanZip(explicitSource, items);
                context.sendMessage(Message.raw("[SCAN] Assets ZIP: found " + (items.size() - before) + " items"));
            }
        } else {
            context.sendMessage(Message.raw("[SCAN] No assets_path configured - base game items will not be scanned"));
        }

        // 2. Global mods - client: UserData\Mods, server: skip (null)
        if (globalMods != null && !globalMods.equals(saveMods)) {
            context.sendMessage(Message.raw("[SCAN] Global mods: " + globalMods.toAbsolutePath()));
            int before = items.size();
            scanJars(globalMods, items, context);
            context.sendMessage(Message.raw("[SCAN] Global mods: found " + (items.size() - before) + " items"));
        }

        // 3. Per-save mods - highest priority, overrides everything above
        context.sendMessage(Message.raw("[SCAN] Save mods: " + saveMods.toAbsolutePath()));
        int before = items.size();
        scanJars(saveMods, items, context);
        context.sendMessage(Message.raw("[SCAN] Save mods: found " + (items.size() - before) + " items"));

        return items;
    }

    private void scanFolder(Path sourcePath, Map<String, String[]> items) throws IOException {
        // Scan weapons
        Path weaponsPath = sourcePath.resolve("Server/Item/Items/Weapon");
        if (!Files.exists(weaponsPath)) weaponsPath = sourcePath.resolve("Item/Items/Weapon");
        if (Files.exists(weaponsPath)) {
            System.out.println("[SCAN]   Weapon folder: " + weaponsPath.toAbsolutePath());
            scanFolderRecursive(weaponsPath, items, false);
        } else {
            System.out.println("[SCAN]   No Weapon subfolder found under: " + sourcePath);
        }

        // Scan armor
        Path armorPath = sourcePath.resolve("Server/Item/Items/Armor");
        if (!Files.exists(armorPath)) armorPath = sourcePath.resolve("Item/Items/Armor");
        if (Files.exists(armorPath)) {
            System.out.println("[SCAN]   Armor folder: " + armorPath.toAbsolutePath());
            scanFolderRecursive(armorPath, items, true);
        } else {
            System.out.println("[SCAN]   No Armor subfolder found under: " + sourcePath);
        }
    }

    private void scanFolderRecursive(Path dir, Map<String, String[]> items, boolean isArmorDir) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    System.out.println("[SCAN]     Subfolder: " + entry.getFileName());
                    scanFolderRecursive(entry, items, isArmorDir);
                } else if (entry.toString().endsWith(".json")) {
                    String fileName = entry.getFileName().toString();
                    String content = Files.readString(entry);
                    boolean validItem = isArmorDir ? hasArmorCategory(content) : hasWeaponCategory(content);
                    if (!validItem) {
                        System.out.println("[SCAN]     ~ skipped: " + fileName + " (no " + (isArmorDir ? "Items.Armor" : "Items.Weapons") + " category)");
                        continue;
                    }
                    // If this is an armor item found by path, record its Parent for future checks
                    if (isArmorDir) {
                        String parent = extractParentValue(content);
                        if (parent != null && !parent.isBlank()) {
                            discoveredArmorParents.add(parent);
                            System.out.println("[SCAN]     [armor-parent] " + parent);
                        }
                    }
                    String id = extractWeaponIdFromContent(content, fileName);
                    if (id != null && !id.matches(".*\\d$")) {
                        String sub = entry.getParent() != null ? entry.getParent().getFileName().toString() : (isArmorDir ? "Armor" : "Weapon");
                        items.put(id, new String[]{content, null, sub}); // null modFolder = base game
                        System.out.println("[SCAN]     + " + id + "  (" + fileName + ")");
                    } else {
                        System.out.println("[SCAN]     ~ skipped: " + fileName + (id != null ? " (upgrade id)" : " (no item id found)"));
                    }
                }
            }
        }
    }

    private void scanZip(Path zipPath, Map<String, String[]> items) {
        System.out.println("[SCAN]   Opening ZIP: " + zipPath.toAbsolutePath());
        int found = 0, skipped = 0;
        try (ZipFile zf = new ZipFile(zipPath.toFile())) {
            System.out.println("[SCAN]   Entries in ZIP: " + zf.size());
            var entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.endsWith(".json")) {
                    String nameLower = name.replace("\\", "/").toLowerCase();
                    boolean byWeaponPath = nameLower.contains("item/items/weapon/");
                    boolean byArmorPath  = nameLower.contains("item/items/armor/");
                    if (!byWeaponPath && !byArmorPath) continue;
                    boolean isArmorEntry = byArmorPath && !byWeaponPath;
                    String fname = name.contains("/") ? name.substring(name.lastIndexOf("/") + 1) : name;
                    try (InputStream is = zf.getInputStream(entry)) {
                        String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        boolean validItem = isArmorEntry ? hasArmorCategory(content) : hasWeaponCategory(content);
                        if (!validItem) {
                            System.out.println("[SCAN]     ~ skipped: " + name + " (no " + (isArmorEntry ? "Items.Armor" : "Items.Weapons") + " category)");
                            skipped++;
                            continue;
                        }
                        // Collect armor parent names for dynamic detection
                        if (isArmorEntry) {
                            String parent = extractParentValue(content);
                            if (parent != null && !parent.isBlank()) {
                                discoveredArmorParents.add(parent);
                            }
                        }
                        String id = extractWeaponIdFromContent(content, fname);
                        if (id != null && !id.matches(".*\\d$")) {
                            String sub = extractSubfolder(name);
                            items.put(id, new String[]{content, null, sub}); // null = base game
                            System.out.println("[SCAN]     + " + id + "  (" + name + ")");
                            found++;
                        } else {
                            System.out.println("[SCAN]     ~ skipped: " + name + (id != null ? " (upgrade id)" : " (no item id found)"));
                            skipped++;
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[SCAN]   ERROR scanning ZIP " + zipPath + ": " + e.getMessage());
        }
        System.out.println("[SCAN]   ZIP result: " + found + " added, " + skipped + " skipped");
    }

    private void scanJars(Path modsPath, Map<String, String[]> items, CommandContext context) {
        if (!Files.exists(modsPath) || !Files.isDirectory(modsPath)) {
            context.sendMessage(Message.raw("[SCAN] Mods path does not exist: " + modsPath));
            return;
        }
        // Scan both .jar and .zip mod files - some mods are distributed as either
        for (String glob : new String[]{"*.jar", "*.zip"}) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsPath, glob)) {
                for (Path modPath : stream) {
                    String fname = modPath.getFileName().toString().toLowerCase();
                    if (fname.equals("assets.zip")) {
                        context.sendMessage(Message.raw("[SCAN]   Skipping assets.zip (not a mod)"));
                        continue;
                    }
                    context.sendMessage(Message.raw("[SCAN]   Found mod file: " + modPath.getFileName()));
                    scanSingleJar(modPath, items, context);
                }
            } catch (IOException e) {
                context.sendMessage(Message.raw("[SCAN]   ERROR listing " + glob + ": " + e.getMessage()));
            }
        }
    }

    private void scanSingleJar(Path jarPath, Map<String, String[]> items, CommandContext context) {
        int found = 0, skipped = 0;
        try (ZipFile zf = new ZipFile(jarPath.toFile())) {
            context.sendMessage(Message.raw("[SCAN]     Entries in " + jarPath.getFileName() + ": " + zf.size()));

            // ── Pass 1: collect ALL entries upfront ───────────────────────────
            java.util.List<ZipEntry> allEntries = new java.util.ArrayList<>();
            java.util.Enumeration<? extends ZipEntry> allEnum = zf.entries();
            while (allEnum.hasMoreElements()) allEntries.add(allEnum.nextElement());

            // ── Pass 2: weapon/armor check — find passing items and cache their content ──
            // Key: entry name  Value: [content, id, subfolder]
            java.util.Map<String, String[]> passing = new java.util.LinkedHashMap<>();
            for (ZipEntry entry : allEntries) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (!name.endsWith(".json")) continue;
                String nameFwd    = name.replace("\\", "/");
                String nameLower  = nameFwd.toLowerCase();
                boolean byWeaponPath = nameLower.contains("item/items/weapon/");
                boolean byArmorPath  = nameLower.contains("item/items/armor/");
                String fname      = nameFwd.contains("/") ? nameFwd.substring(nameFwd.lastIndexOf("/") + 1) : nameFwd;
                // Pre-filter: must be under a known item path OR have a recognizable filename prefix
                boolean byPath = byWeaponPath || byArmorPath;
                boolean byFilename = fname.toLowerCase().startsWith("weapon_") || fname.toLowerCase().startsWith("armor_");
                if (!byPath && !byFilename) continue;
                boolean isArmorEntry = byArmorPath || (!byWeaponPath && fname.toLowerCase().startsWith("armor_"));
                try (InputStream is = zf.getInputStream(entry)) {
                    String wContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    boolean byCategory = isArmorEntry ? hasArmorCategory(wContent) : hasWeaponCategory(wContent);
                    // Path alone is sufficient (already in the right folder structure)
                    // Filename prefix alone is NOT sufficient — must also have the category
                    if (!byPath && !byCategory) continue;
                    // Collect armor parent names for dynamic detection
                    if (isArmorEntry) {
                        String parent = extractParentValue(wContent);
                        if (parent != null && !parent.isBlank()) {
                            discoveredArmorParents.add(parent);
                        }
                    }
                    String id = extractWeaponIdFromContent(wContent, fname);
                    if (id != null && !id.matches(".*\\d$")) {
                        passing.put(name, new String[]{wContent, id, extractSubfolder(name)});
                        context.sendMessage(Message.raw("[SCAN]     + " + id + "  (" + name + ")"));
                        found++;
                    } else {
                        context.sendMessage(Message.raw("[SCAN]     ~ skipped: " + name + (id != null ? " (upgrade id)" : " (no item id found)")));
                        skipped++;
                    }
                }
            }

            // No items found in this JAR - skip copy entirely
            if (passing.isEmpty()) {
                context.sendMessage(Message.raw("[SCAN]     No weapons or armor found - skipping copy"));
                context.sendMessage(Message.raw("[SCAN]     Result for " + jarPath.getFileName() + ": 0 added, " + skipped + " skipped"));
                return;
            }

            // ── Pass 3: read manifest — derive output folder name and plugin exclusion ──
            // Output folder = "Group.Name"  (e.g. "wanmine.mod.WansWonderWeapon")
            // Plugin exclusion = that same folder prefix inside the JAR (compiled code lives there)
            Path saveMods  = jarPath.getParent();
            String destFolderName  = null; // Group.Name — both the output folder AND the exclusion
            ZipEntry manifestEntry = zf.getEntry("manifest.json");
            if (manifestEntry != null) {
                try (InputStream mis = zf.getInputStream(manifestEntry)) {
                    String mc    = new String(mis.readAllBytes(), StandardCharsets.UTF_8);
                    String group = extractJsonStringField(mc, "Group");
                    String mName = extractJsonStringField(mc, "Name");
                    if (group != null && mName != null) destFolderName = group + "." + mName;
                    else if (group != null)              destFolderName = group;
                    else if (mName != null)              destFolderName = mName;
                    context.sendMessage(Message.raw("[SCAN]     Mod output folder: " + (destFolderName != null ? destFolderName : "(unknown, will use jar name)")));
                } catch (IOException e) {
                    context.sendMessage(Message.raw("[SCAN]     Could not read manifest.json: " + e.getMessage()));
                }
            }
            // Fallback: use JAR filename stem if manifest missing or incomplete
            if (destFolderName == null) {
                String jarStem = jarPath.getFileName().toString();
                destFolderName = jarStem.contains(".") ? jarStem.substring(0, jarStem.lastIndexOf('.')) : jarStem;
                context.sendMessage(Message.raw("[SCAN]     No manifest - using jar name as folder: " + destFolderName));
            }
            final String pluginExcludePrefix = destFolderName + "/";

            // ── Pass 4: copy everything into Group.Name/ (saveMods/Group.Name/) ──
            // Exclude: the plugin's own compiled-code folder (Group.Name/ inside the JAR)
            // Exclude: weapon entries that passed — those get written by processWeapons
            Path destRoot = saveMods.resolve(destFolderName);
            int copied = 0;
            for (ZipEntry entry : allEntries) {
                String eName   = entry.getName();
                String eNorm   = eName.replace("\\", "/");
                if (eNorm.startsWith(pluginExcludePrefix)) continue; // skip plugin code
                if (passing.containsKey(eName)) continue;            // skip weapon jsons
                if (entry.isDirectory()) {
                    Files.createDirectories(destRoot.resolve(eNorm));
                    continue;
                }
                Path dest = destRoot.resolve(eNorm);
                Files.createDirectories(dest.getParent());
                try (InputStream is = zf.getInputStream(entry)) {
                    Files.write(dest, is.readAllBytes());
                    copied++;
                }
            }
            context.sendMessage(Message.raw("[SCAN]     Copied " + copied + " files -> " + destRoot.getFileName()));

            // ── Pass 5: register passing items with their output folder ──
            // [0]=content  [1]=destFolderName (Group.Name)  [2]=itemTypeSubfolder
            for (java.util.Map.Entry<String, String[]> e : passing.entrySet()) {
                String id        = e.getValue()[1];
                String wContent  = e.getValue()[0];
                String subfolder = e.getValue()[2];
                items.put(id, new String[]{wContent, destFolderName, subfolder});
            }

        } catch (IOException e) {
            context.sendMessage(Message.raw("[SCAN]     ERROR scanning " + jarPath.getFileName() + ": " + e.getMessage()));
        }
        context.sendMessage(Message.raw("[SCAN]     Result for " + jarPath.getFileName() + ": " + found + " added, " + skipped + " skipped"));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Derives an item subfolder from the item ID when no path info is available.
     * Weapon_Sword_Iron -> Sword, Armor_Chest_Iron -> Chest,
     * Template_Weapon_Sword -> Sword, Template_Armor_Chest -> Chest.
     * Falls back to "Weapon" or "Armor" based on prefix.
     */
    private String deriveSubfolder(String itemId) {
        String[] parts = itemId.split("_");
        // Template_Weapon_<Type>_... -> parts[2]
        if (parts.length > 2 && parts[0].equalsIgnoreCase("Template") && parts[1].equalsIgnoreCase("Weapon")) {
            return Character.toUpperCase(parts[2].charAt(0)) + parts[2].substring(1).toLowerCase();
        }
        // Template_Armor_<Type>_... -> parts[2]
        if (parts.length > 2 && parts[0].equalsIgnoreCase("Template") && parts[1].equalsIgnoreCase("Armor")) {
            return Character.toUpperCase(parts[2].charAt(0)) + parts[2].substring(1).toLowerCase();
        }
        // Weapon_<Type>_... -> parts[1]
        if (parts.length > 1 && parts[0].equalsIgnoreCase("Weapon")) {
            return Character.toUpperCase(parts[1].charAt(0)) + parts[1].substring(1).toLowerCase();
        }
        // Armor_<Type>_... -> parts[1]
        if (parts.length > 1 && parts[0].equalsIgnoreCase("Armor")) {
            return Character.toUpperCase(parts[1].charAt(0)) + parts[1].substring(1).toLowerCase();
        }
        return itemId.toLowerCase().startsWith("armor_") ? "Armor" : "Weapon";
    }

    /**
     * Extracts the item type subfolder from a ZIP entry path.
     * e.g. "Server/Item/Items/Weapon/Sword/Weapon_Sword_Iron.json" -> "Sword"
     *      "Server/Item/Items/Armor/Chest/Armor_Chest_Iron.json"   -> "Chest"
     * Falls back to "Weapon" or "Armor" if not determinable.
     */
    private String extractSubfolder(String entryName) {
        String norm = entryName.replace("\\", "/");
        // Try weapon path first
        int idx = norm.toLowerCase().indexOf("item/items/weapon/");
        if (idx >= 0) {
            String after = norm.substring(idx + "item/items/weapon/".length());
            int slash = after.indexOf("/");
            return (slash > 0) ? after.substring(0, slash) : "Weapon";
        }
        // Try armor path
        idx = norm.toLowerCase().indexOf("item/items/armor/");
        if (idx >= 0) {
            String after = norm.substring(idx + "item/items/armor/".length());
            int slash = after.indexOf("/");
            return (slash > 0) ? after.substring(0, slash) : "Armor";
        }
        return "Weapon"; // fallback
    }

    /**
     * Returns true if the JSON content has "Categories": [ ... "Items.Weapons" ... ]
     * or inherits from a weapon template.
     */
    private boolean hasWeaponCategory(String content) {
        // Check 1: explicit Categories array contains "Items.Weapons"
        int catIdx = content.indexOf("\"Categories\"");
        if (catIdx >= 0) {
            int arrOpen  = content.indexOf('[', catIdx);
            int arrClose = arrOpen >= 0 ? content.indexOf(']', arrOpen) : -1;
            if (arrOpen >= 0 && arrClose >= 0
                    && content.substring(arrOpen, arrClose + 1).contains("Items.Weapons")) {
                return true;
            }
        }

        // Check 2: inherits from a weapon template via "Parent": "Template_Weapon_*"
        int parentIdx = content.indexOf("\"Parent\"");
        if (parentIdx >= 0) {
            int colon    = content.indexOf(':', parentIdx);
            int valOpen  = colon >= 0 ? content.indexOf('"', colon) : -1;
            int valClose = valOpen >= 0 ? content.indexOf('"', valOpen + 1) : -1;
            if (valOpen >= 0 && valClose > valOpen) {
                String parentVal = content.substring(valOpen + 1, valClose);
                if (parentVal.toLowerCase().startsWith("template_weapon_")) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Returns true if the JSON content is an armor item.
     * Uses multiple checks in order:
     *   1. Explicit "Categories" array contains "Items.Armor" or "Items.Armour"
     *   2. "Parent" value matches a dynamically discovered armor parent name
     *   3. "Parent" value contains "armor" (case-insensitive) anywhere
     *   4. Structural armor properties present (ArmorStats, DefenseProperties, etc.)
     */
    private boolean hasArmorCategory(String content) {
        // Check 1: explicit Categories array contains "Items.Armor" or "Items.Armour"
        int catIdx = content.indexOf("\"Categories\"");
        if (catIdx >= 0) {
            int arrOpen  = content.indexOf('[', catIdx);
            int arrClose = arrOpen >= 0 ? content.indexOf(']', arrOpen) : -1;
            if (arrOpen >= 0 && arrClose >= 0) {
                String catSection = content.substring(arrOpen, arrClose + 1);
                if (catSection.contains("Items.Armor") || catSection.contains("Items.Armour")) {
                    return true;
                }
            }
        }

        // Check 2 & 3: inspect the "Parent" field
        String parentVal = extractParentValue(content);
        if (parentVal != null) {
            // Check 2: matches a dynamically discovered armor parent
            if (discoveredArmorParents.contains(parentVal)) {
                return true;
            }
            // Check 3: parent name contains "armor" anywhere (case-insensitive)
            if (parentVal.toLowerCase().contains("armor") || parentVal.toLowerCase().contains("armour")) {
                return true;
            }
        }

        // Check 4: structural armor properties
        String[] armorProps = {
            "\"ArmorStats\"", "\"DefenseProperties\"", "\"ProtectionProperties\"",
            "\"ArmorType\"", "\"ArmorSlot\"", "\"EquipmentSlot\"",
            "\"ArmorValue\"", "\"Defense\"", "\"Toughness\""
        };
        for (String prop : armorProps) {
            if (content.contains(prop)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Extracts the value of the "Parent" field from JSON content.
     * Returns null if not found.
     */
    private String extractParentValue(String content) {
        int parentIdx = content.indexOf("\"Parent\"");
        if (parentIdx < 0) return null;
        int colon    = content.indexOf(':', parentIdx);
        int valOpen  = colon >= 0 ? content.indexOf('"', colon) : -1;
        int valClose = valOpen >= 0 ? content.indexOf('"', valOpen + 1) : -1;
        if (valOpen >= 0 && valClose > valOpen) {
            String val = content.substring(valOpen + 1, valClose).trim();
            return val.isBlank() ? null : val;
        }
        return null;
    }

    /**
     * Attempts to extract a weapon ID from JSON content by checking multiple
     * translation key patterns used by both the base game and mods:
     *
     *   "server.items.Weapon_Sword_Iron.name"  (base game)
     *   "item.Weapon_Sword_Iron.name"           (some mods)
     *   "items.Weapon_Sword_Iron.name"          (some mods)
     *
     * If no translation key matches, falls back to checking structural weapon
     * indicators (WeaponStats, DamageProperties, AttackProperties) and derives
     * the ID from the filename hint stored alongside content, or returns null.
     *
     * A result is only accepted if the extracted ID contains "Weapon" (case-insensitive),
     * ensuring non-weapon JSON files found during broad scans are rejected.
     */
    private String extractWeaponIdFromContent(String content) {
        return extractWeaponIdFromContent(content, null);
    }

    private String extractWeaponIdFromContent(String content, String filenameStem) {
        // ── Step 1: look inside TranslationProperties for any "X.name" value ──
        // We don't hardcode prefixes - instead find the Name field inside
        // TranslationProperties and extract the last segment before ".name"
        int translationStart = content.indexOf("\"TranslationProperties\"");
        int searchFrom = (translationStart >= 0) ? translationStart : 0;

        int nameIdx = content.indexOf("\"Name\"", searchFrom);
        if (nameIdx >= 0) {
            // Find the string value after "Name":
            int colon    = content.indexOf(':', nameIdx);
            int valOpen  = content.indexOf('"', colon);
            int valClose = (valOpen >= 0) ? content.indexOf('"', valOpen + 1) : -1;
            if (valOpen >= 0 && valClose > valOpen) {
                String val = content.substring(valOpen + 1, valClose);
                // val is something like "server.items.WanMine_God_Slayer_Battleaxe.name"
                // or "wanmine.WanMine_Sword.name"  or just "WanMine_Sword.name"
                // Strip the trailing ".name" suffix if present
                if (val.endsWith(".name")) val = val.substring(0, val.length() - 5);
                // The ID is the last dot-separated segment
                int lastDot = val.lastIndexOf('.');
                String candidate = (lastDot >= 0) ? val.substring(lastDot + 1) : val;
                if (!candidate.isBlank()) return candidate;
            }
        }

        // ── Step 2: scan ALL "X.name" string values anywhere in the file ──────
        // Handles mods where TranslationProperties uses a non-standard layout
        int pos = 0;
        while ((pos = content.indexOf(".name\"", pos)) >= 0) {
            // Walk left to find the opening quote of this string value
            int qOpen = content.lastIndexOf('"', pos - 1);
            if (qOpen >= 0) {
                String val = content.substring(qOpen + 1, pos); // e.g. "wanmine.WanMine_Sword"
                // Strip any remaining prefix segments - take last dot-segment
                int lastDot = val.lastIndexOf('.');
                String candidate = (lastDot >= 0) ? val.substring(lastDot + 1) : val;
                if (!candidate.isBlank()) return candidate;
            }
            pos++;
        }

        // ── Step 3: filename stem fallback ────────────────────────────────────
        // Caller already verified this is a weapon file (by path/category/filename),
        // so using the filename is safe even without a translation key.
        if (filenameStem != null && !filenameStem.isBlank()) {
            String stem = filenameStem.endsWith(".json")
                ? filenameStem.substring(0, filenameStem.length() - 5)
                : filenameStem;
            return stem;
        }

        return null;
    }

    /** Extracts the value of a top-level JSON string field by key. */
    private String extractJsonStringField(String json, String key) {
        int idx = json.indexOf("\"" + key + "\"");
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx);
        if (colon < 0) return null;
        int valStart = json.indexOf('"', colon);
        if (valStart < 0) return null;
        int valEnd = json.indexOf('"', valStart + 1);
        if (valEnd < 0) return null;
        String val = json.substring(valStart + 1, valEnd).trim();
        return val.isBlank() ? null : val;
    }

    private Path detectBasePath(CommandContext context) {
        context.sendMessage(Message.raw("[DIR] CWD: " + Paths.get("").toAbsolutePath()));

        String[] relativeTries = {"mods", "server/mods", "../server/mods", "../../server/mods"};
        for (String rel : relativeTries) {
            Path p = Paths.get(rel);
            boolean exists = Files.exists(p) && Files.isDirectory(p);
            context.sendMessage(Message.raw("[DIR] Checking " + p.toAbsolutePath() + " -> " + (exists ? "FOUND" : "not found")));
            if (exists) {
                context.sendMessage(Message.raw("[DIR] Using mods folder: " + p.toAbsolutePath()));
                return p;
            }
        }

        context.sendMessage(Message.raw("[DIR] Walking up from CWD to find mods folder..."));
        Path cwd = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 5; i++) {
            for (String rel : new String[]{"mods", "server/mods"}) {
                Path test = cwd.resolve(rel);
                boolean exists = Files.exists(test) && Files.isDirectory(test);
                context.sendMessage(Message.raw("[DIR]   Checking " + test + " -> " + (exists ? "FOUND" : "not found")));
                if (exists) {
                    context.sendMessage(Message.raw("[DIR] Using mods folder: " + test));
                    return test;
                }
            }
            cwd = cwd.getParent();
            if (cwd == null) break;
        }

        context.sendMessage(Message.raw("[DIR] Checking client save paths..."));
        String[] clientBases = {"Hytale/UserData/Saves", "../Hytale/UserData/Saves"};
        for (String cb : clientBases) {
            Path p = Paths.get(cb);
            boolean exists = Files.exists(p) && Files.isDirectory(p);
            context.sendMessage(Message.raw("[DIR]   Checking " + p.toAbsolutePath() + " -> " + (exists ? "FOUND" : "not found")));
            if (exists) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(p)) {
                    Path latest = null;
                    long latestTime = 0;
                    for (Path entry : stream) {
                        if (Files.isDirectory(entry)) {
                            Path mods = entry.resolve("mods");
                            if (Files.exists(mods)) {
                                long modTime = Files.getLastModifiedTime(entry).toMillis();
                                context.sendMessage(Message.raw("[DIR]   Found save with mods: " + entry));
                                if (modTime > latestTime) { latestTime = modTime; latest = entry; }
                            }
                        }
                    }
                    if (latest != null) {
                        context.sendMessage(Message.raw("[DIR] Using client mods: " + latest.resolve("mods")));
                        return latest.resolve("mods");
                    }
                } catch (IOException e) {
                    context.sendMessage(Message.raw("[DIR]   Error reading " + p + ": " + e.getMessage()));
                }
            }
        }

        context.sendMessage(Message.raw("[DIR] Could not find mods folder anywhere."));
        return null;
    }

    // -------------------------------------------------------------------------
    // Output generation
    // -------------------------------------------------------------------------

    private int processWeapons(Map<String, String[]> itemData, Path targetPath, Path saveMods) throws IOException {
        int count = 0;
        for (Map.Entry<String, String[]> entry : itemData.entrySet()) {
            String itemId        = entry.getKey();
            String wContent      = entry.getValue()[0];
            String modFolderName = entry.getValue()[1]; // null = base game
            String subfolder     = (entry.getValue().length > 2 && entry.getValue()[2] != null)
                ? entry.getValue()[2] : deriveSubfolder(itemId);

            boolean isArmorItem = itemId.toLowerCase().startsWith("armor_");
            String itemFolder   = isArmorItem ? "Armor" : "Weapon";

            // Base game items -> DEFAULT_TARGET (targetPath)
            // Mod items       -> saveMods/Group.Name/
            Path outRoot   = (modFolderName == null) ? targetPath : saveMods.resolve(modFolderName);
            Path targetDir = outRoot.resolve("Server/Item/Items/" + itemFolder + "/" + subfolder);
            Files.createDirectories(targetDir);

            String modifiedContent = modifyContent(wContent, itemId, null);
            Files.write(targetDir.resolve(itemId + ".json"), modifiedContent.getBytes(StandardCharsets.UTF_8));
            count++;
        }
        return count;
    }

    private int createUpgrades(Map<String, String[]> itemData, Path targetPath, Path saveMods) throws IOException {
        int count = 0;
        for (Map.Entry<String, String[]> entry : itemData.entrySet()) {
            String baseId        = entry.getKey();
            String baseContent   = entry.getValue()[0];
            String modFolderName = entry.getValue()[1]; // null = base game

            // Templates are parent definitions - do not generate upgrades for them
            if (baseId.toLowerCase().startsWith("template_")) continue;

            String subfolder = (entry.getValue().length > 2 && entry.getValue()[2] != null)
                ? entry.getValue()[2] : deriveSubfolder(baseId);

            boolean isArmorItem = baseId.toLowerCase().startsWith("armor_");
            String itemFolder   = isArmorItem ? "Armor" : "Weapon";

            // Base game upgrades -> DEFAULT_TARGET (targetPath)
            // Mod upgrades       -> saveMods/Group.Name/
            Path outRoot   = (modFolderName == null) ? targetPath : saveMods.resolve(modFolderName);
            Path targetDir = outRoot.resolve("Server/Item/Items/" + itemFolder + "/" + subfolder);
            Files.createDirectories(targetDir);

            for (int level = 1; level <= MAX_UPGRADE_LEVEL; level++) {
                String upgradeId       = baseId + level;
                String modifiedContent = modifyContent(baseContent, baseId, upgradeId);
                Files.write(targetDir.resolve(upgradeId + ".json"), modifiedContent.getBytes(StandardCharsets.UTF_8));
                count++;
            }
        }
        return count;
    }

    private String modifyContent(String content, String baseId, String upgradeId) {
        String result = content;
        String targetId = (upgradeId != null) ? upgradeId : baseId;

        // Update TranslationProperties.Name to match the server.lang key format.
        // Replace all known translation key patterns so both base game and mod items are handled.
        String[] prefixes = { "server.items.", "item.", "items." };
        for (String prefix : prefixes) {
            String oldKey = "\"" + prefix + baseId + ".name\"";
            String newKey = "\"" + prefix + targetId + ".name\"";
            if (result.contains(oldKey)) {
                result = result.replace(oldKey, newKey);
                break; // only one prefix will match per file
            }
        }

        // Only remove the Recipe block from upgrade copies (+1/+2/+3).
        // The base weapon copy keeps its original recipe intact.
        if (upgradeId != null) {
            result = removeRecipeBlock(result);
        }

        return result;
    }

    /**
     * Removes the entire "Recipe": { ... } block from a JSON string,
     * correctly handling nested braces of any depth.
     * Also cleans up any trailing comma left before the next field,
     * or leading comma left after the previous field.
     */
    private String removeRecipeBlock(String json) {
        int keyStart = json.indexOf("\"Recipe\"");
        if (keyStart < 0) return json; // no Recipe block, nothing to do

        // Find the opening brace of the Recipe value
        int braceStart = json.indexOf('{', keyStart);
        if (braceStart < 0) return json;

        // Walk forward counting brace depth to find the matching closing brace
        int depth = 0;
        int braceEnd = -1;
        for (int i = braceStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    braceEnd = i;
                    break;
                }
            }
        }
        if (braceEnd < 0) return json; // malformed JSON, leave it alone

        String before = json.substring(0, keyStart);
        String after  = json.substring(braceEnd + 1);

        // The comma BEFORE Recipe (on the previous field) must stay - it is still valid.
        // Only remove the comma AFTER the Recipe block that leads into the next field.
        // If Recipe was the last field, remove the trailing comma from the previous field instead.
        after = after.replaceAll("^\\s*,", "");

        // Recipe was last field - clean up the trailing comma left on `before`
        if (after.matches("(?s)^\\s*\\}.*")) {
            before = before.replaceAll(",\\s*$", "");
        }

        return before + after;
    }

    private int writeServerLang(CommandContext context, Path targetPath, Map<String, String[]> itemData, Map<String, String> sourceLangNames, Set<String> baseIds, Set<String> upgradeIds) throws IOException {
        Path langPath = targetPath.resolve("Server/Languages/en-US/server.lang");
        Files.createDirectories(langPath.getParent());

        StringBuilder sb = new StringBuilder("# Auto-patched weapon and armor entries\n");
        for (String id : baseIds) {
            if (id.toLowerCase().startsWith("template_")) continue;
            String displayName = extractDisplayName(sourceLangNames, itemData, id);
            sb.append("items.").append(id).append(".name = ").append(displayName).append("\n");
        }
        for (String id : upgradeIds) {
            String displayName = extractDisplayName(sourceLangNames, itemData, id);
            sb.append("items.").append(id).append(".name = ").append(displayName).append("\n");
        }

        Files.write(langPath, sb.toString().getBytes(StandardCharsets.UTF_8));
        return baseIds.size() + upgradeIds.size();
    }

    /**
     * Extracts the display name from source server.lang or falls back to other methods.
     */
    private String extractDisplayName(Map<String, String> sourceLangNames, Map<String, String[]> itemData, String itemId) {
        // First, check if we have the name from source server.lang
        String langName = sourceLangNames.get(itemId);
        if (langName != null && !langName.isBlank()) {
            return langName;
        }
        
        // Check if this is an upgrade item (ends with a number) - try to get base name
        if (itemId.matches(".*\\d$")) {
            int i = itemId.length() - 1;
            while (i >= 0 && Character.isDigit(itemId.charAt(i))) i--;
            String baseId = itemId.substring(0, i + 1);
            String level = itemId.substring(i + 1);
            
            String baseLangName = sourceLangNames.get(baseId);
            if (baseLangName != null && !baseLangName.isBlank()) {
                return baseLangName + " +" + level;
            }
        }
        
        // Fallback to extracting from itemData content
        String displayName = extractDisplayNameFromItemData(itemData, itemId);
        if (displayName != null) {
            return displayName;
        }
        
        // Final fallback to buildName
        return buildName(itemId);
    }

    /**
     * Extracts display name from itemData content (JSON TranslationProperties).
     */
    private String extractDisplayNameFromItemData(Map<String, String[]> itemData, String itemId) {
        // Check if this is an upgrade item
        String level = "";
        String baseId = itemId;
        
        if (itemId.matches(".*\\d$")) {
            int i = itemId.length() - 1;
            while (i >= 0 && Character.isDigit(itemId.charAt(i))) i--;
            level = itemId.substring(i + 1);
            baseId = itemId.substring(0, i + 1);
        }
        
        // Get the source content for the base item
        String[] data = itemData.get(baseId);
        if (data == null || data[0] == null) {
            return null;
        }
        
        String content = data[0];
        
        // Extract the Name from TranslationProperties
        String name = extractNameFromTranslationProperties(content);
        
        if (name != null && !name.isBlank()) {
            // Strip the trailing .name if present
            if (name.endsWith(".name")) {
                name = name.substring(0, name.length() - 5);
            }
            // Get the last segment after the last dot (the actual name key)
            int lastDot = name.lastIndexOf('.');
            if (lastDot >= 0) {
                name = name.substring(lastDot + 1);
            }
            // Replace underscores with spaces for readability
            name = name.replace("_", " ");
            
            // Add level suffix for upgrade items
            if (!level.isEmpty()) {
                name = name + " +" + level;
            }
            
            return name;
        }
        
        return null;
    }

    /**
     * Extracts the Name value from TranslationProperties in JSON content.
     */
    private String extractNameFromTranslationProperties(String content) {
        // Look for TranslationProperties section
        int tpStart = content.indexOf("\"TranslationProperties\"");
        if (tpStart < 0) return null;
        
        // Find the "Name" field after TranslationProperties
        int nameStart = content.indexOf("\"Name\"", tpStart);
        if (nameStart < 0) return null;
        
        // Find the colon after Name
        int colon = content.indexOf(':', nameStart);
        if (colon < 0) return null;
        
        // Find the opening quote
        int quoteStart = content.indexOf('"', colon);
        if (quoteStart < 0) return null;
        
        // Find the closing quote
        int quoteEnd = content.indexOf('"', quoteStart + 1);
        if (quoteEnd < 0) return null;
        
        return content.substring(quoteStart + 1, quoteEnd);
    }

    /**
     * Scans all sources for server.lang files and extracts item names.
     */
    private void scanAllLangFiles(CommandContext context, Path saveMods, Path globalMods, Path explicitSource, Map<String, String> sourceLangNames) throws IOException {
        // 1. Assets.zip - base game lang file
        if (explicitSource != null) {
            if (Files.isDirectory(explicitSource)) {
                scanFolderForLang(explicitSource, sourceLangNames);
            } else {
                scanZipForLang(explicitSource, sourceLangNames);
            }
        }

        // 2. Global mods
        if (globalMods != null && !globalMods.equals(saveMods)) {
            scanJarsForLang(globalMods, sourceLangNames, context);
        }

        // 3. Per-save mods
        scanJarsForLang(saveMods, sourceLangNames, context);
    }

    /**
     * Scans a folder for server.lang files.
     */
    private void scanFolderForLang(Path sourcePath, Map<String, String> sourceLangNames) {
        // Check both possible lang file locations
        Path langPath1 = sourcePath.resolve("Server/Languages/en-US/server.lang");
        Path langPath2 = sourcePath.resolve("Languages/en-US/server.lang");
        
        for (Path langPath : new Path[]{langPath1, langPath2}) {
            if (Files.exists(langPath)) {
                try {
                    String content = Files.readString(langPath);
                    parseServerLang(content, sourceLangNames);
                    System.out.println("[LANG] Loaded names from: " + langPath);
                } catch (IOException e) {
                    System.out.println("[LANG] Error reading: " + langPath + " - " + e.getMessage());
                }
            }
        }
    }

    /**
     * Scans a ZIP file for server.lang files.
     */
    private void scanZipForLang(Path zipPath, Map<String, String> sourceLangNames) {
        try (ZipFile zf = new ZipFile(zipPath.toFile())) {
            var entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName().replace("\\", "/");
                // Look for server.lang in language folders
                if (name.endsWith("server.lang") && name.toLowerCase().contains("languages/")) {
                    try (InputStream is = zf.getInputStream(entry)) {
                        String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        parseServerLang(content, sourceLangNames);
                        System.out.println("[LANG] Loaded names from ZIP: " + name);
                    } catch (IOException e) {
                        System.out.println("[LANG] Error reading ZIP entry: " + name);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("[LANG] Error opening ZIP: " + zipPath);
        }
    }

    /**
     * Scans JAR files in a folder for server.lang files.
     */
    private void scanJarsForLang(Path modsPath, Map<String, String> sourceLangNames, CommandContext context) {
        if (!Files.exists(modsPath) || !Files.isDirectory(modsPath)) {
            return;
        }

        for (String glob : new String[]{"*.jar", "*.zip"}) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsPath, glob)) {
                for (Path modPath : stream) {
                    String fname = modPath.getFileName().toString().toLowerCase();
                    if (fname.equals("assets.zip")) {
                        continue;
                    }
                    scanSingleJarForLang(modPath, sourceLangNames);
                }
            } catch (IOException e) {
                // Ignore errors
            }
        }
    }

    /**
     * Scans a single JAR file for server.lang files.
     */
    private void scanSingleJarForLang(Path jarPath, Map<String, String> sourceLangNames) {
        try (ZipFile zf = new ZipFile(jarPath.toFile())) {
            var entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName().replace("\\", "/");
                // Look for server.lang in language folders
                if (name.endsWith("server.lang") && name.toLowerCase().contains("languages/")) {
                    try (InputStream is = zf.getInputStream(entry)) {
                        String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        parseServerLang(content, sourceLangNames);
                        System.out.println("[LANG] Loaded names from JAR: " + jarPath.getFileName() + ":" + name);
                    } catch (IOException e) {
                        // Ignore
                    }
                }
            }
        } catch (IOException e) {
            // Ignore
        }
    }

    /**
     * Parses server.lang content and extracts item names.
     * Handles formats like:
     *   server.items.Weapon_Sword_Crude.name = Crude Sword
     *   items.Weapon_Sword_Crude.name = Crude Sword
     *   item.Weapon_Sword_Crude.name = Crude Sword
     */
    private void parseServerLang(String content, Map<String, String> sourceLangNames) {
        for (String line : content.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            
            // Look for pattern: key.name = value
            int equalsIdx = line.indexOf('=');
            if (equalsIdx <= 0) continue;
            
            String key = line.substring(0, equalsIdx).trim();
            String value = line.substring(equalsIdx + 1).trim();
            
            // We only care about keys ending with .name
            if (!key.endsWith(".name")) continue;
            
            // Strip .name suffix
            key = key.substring(0, key.length() - 5);
            
            // Get the item ID (last segment after the last dot)
            int lastDot = key.lastIndexOf('.');
            if (lastDot < 0) continue;
            
            String itemId = key.substring(lastDot + 1);
            
            // Skip template items
            if (itemId.toLowerCase().startsWith("template_")) continue;
            
            // Only store if not already present (first source takes priority)
            if (!sourceLangNames.containsKey(itemId)) {
                sourceLangNames.put(itemId, value);
            }
        }
    }

    /**
     * Builds a human-readable display name for a weapon or armor item ID.
     *
     * Weapons:  Weapon_Sword_Iron  -> "Iron Sword"
     *           Weapon_Sword_Iron1 -> "Iron Sword +1"
     * Armor:    Armor_Chest_Iron   -> "Iron Chestplate"
     *           Armor_Chest_Iron1  -> "Iron Chestplate +1"
     *           Armor_Helmet_Iron  -> "Iron Helmet"
     *           Armor_Legs_Iron    -> "Iron Leggings"
     *           Armor_Boots_Iron   -> "Iron Boots"
     */
    private String buildName(String itemId) {
        String[] parts = itemId.split("_");
        if (parts.length < 3) return itemId;

        boolean isArmor = parts[0].equalsIgnoreCase("Armor");

        // Determine the type label
        String typeLabel;
        if (isArmor) {
            String slot = parts[1].toLowerCase();
            switch (slot) {
                case "chest":    typeLabel = "Chestplate"; break;
                case "helmet":   typeLabel = "Helmet";     break;
                case "head":     typeLabel = "Helmet";     break;
                case "legs":     typeLabel = "Leggings";   break;
                case "leggings": typeLabel = "Leggings";   break;
                case "boots":    typeLabel = "Boots";      break;
                case "feet":     typeLabel = "Boots";      break;
                case "gloves":   typeLabel = "Gloves";     break;
                case "hands":    typeLabel = "Gloves";     break;
                default:
                    typeLabel = Character.toUpperCase(slot.charAt(0)) + slot.substring(1).toLowerCase();
            }
        } else {
            typeLabel = Character.toUpperCase(parts[1].charAt(0)) + parts[1].substring(1).toLowerCase();
        }

        String lastPart = parts[parts.length - 1];

        String level, material;
        if (lastPart.matches(".*\\d+")) {
            int i = lastPart.length() - 1;
            while (i >= 0 && Character.isDigit(lastPart.charAt(i))) i--;
            level    = lastPart.substring(i + 1);
            material = lastPart.substring(0, i + 1);
        } else {
            level    = "";
            material = lastPart;
        }
        material = Character.toUpperCase(material.charAt(0)) + material.substring(1).toLowerCase();

        return level.isEmpty() ? material + " " + typeLabel : material + " " + typeLabel + " +" + level;
    }

    private boolean writeManifest(Path targetPath, Set<String> baseIds, Set<String> upgradeIds) throws IOException {
        Path manifestPath = targetPath.resolve("manifest.json");
        boolean existed = Files.exists(manifestPath);

        // SocketReforge no longer owns item JSONs - they live in the source mod folders.
        // This manifest only needs to register the plugin and its lang pack.
        String json = "{\n" +
            "  \"Group\": \"irai.mod.reforge\",\n" +
            "  \"Name\": \"SocketReforge\",\n" +
            "  \"Version\": \"1.0.0\",\n" +
            "  \"Description\": \"Weapon and armor upgrade/reforge system - Patched with External Asset Patcher\",\n" +
            "  \"Authors\": [{\"Name\": \"iRaiden\", \"Email\": \"animus0416@gmail.com\", \"Url\": \"https://github.com/Rizrokkz\"}],\n" +
            "  \"Website\": \"\",\n" +
            "  \"Dependencies\": {},\n" +
            "  \"OptionalDependencies\": {},\n" +
            "  \"LoadBefore\": {},\n" +
            "  \"DisabledByDefault\": false,\n" +
            "  \"IncludesAssetPack\": true,\n" +
            "  \"SubPlugins\": [],\n" +
            "  \"Main\": \"irai.mod.reforge.ReforgePlugin\"\n" +
            "}\n";

        Files.write(manifestPath, json.getBytes(StandardCharsets.UTF_8));
        return !existed;
    }

    @Override
    public String getDescription() {
        return "Patch weapons and armor from Assets.zip/folder/jars - /patchassets";
    }
}