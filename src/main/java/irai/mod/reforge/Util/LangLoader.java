package irai.mod.reforge.Util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for loading and querying .lang files.
 * Supports both vanilla and modded .lang files with any filename.
 * 
 * Lang file format:
 * - Lines starting with # are comments
 * - Empty lines are ignored
 * - Format: key = value (spaces around = are optional)
 * - Example: items.Weapon_Sword_Gaias_Wrath.name = Gaias Wrath
 */
public final class LangLoader {

    // Pattern to match lang file entries: key = value
    private static final Pattern LANG_ENTRY_PATTERN = Pattern.compile("^\\s*([\\w.]+)\\s*=\\s*(.+?)\\s*$");
    
    // Pattern to match .lang files (any filename ending with .lang)
    private static final Pattern LANG_FILE_PATTERN = Pattern.compile(".*\\.lang$", Pattern.CASE_INSENSITIVE);
    
    // Cache of loaded translations: namespace -> (key -> value)
    private static final Map<String, Map<String, String>> translationCache = new ConcurrentHashMap<>();
    
    // Default language code
    private static final String DEFAULT_LANG = "en-US";
    
    // Loaded flag
    private static boolean initialized = false;
    
    private LangLoader() {} // Prevent instantiation
    
    /**
     * Initializes the lang loader by scanning for .lang files.
     * Should be called during plugin startup.
     */
    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        
        // Load from Server/Languages directory
        loadFromDirectory("Server/Languages");
        
        // Also try to load from Common/Languages for shared translations
        loadFromDirectory("Common/Languages");
        
        initialized = true;
    }
    
    /**
     * Loads all .lang files from a directory.
     * 
     * @param directoryPath Path to the directory containing language folders
     */
    private static void loadFromDirectory(String directoryPath) {
        try {
            // Try to access the directory as a resource
            InputStream dirStream = LangLoader.class.getClassLoader()
                .getResourceAsStream(directoryPath);
            
            if (dirStream == null) {
                // Try as file system path
                Path path = Path.of(directoryPath);
                if (Files.exists(path) && Files.isDirectory(path)) {
                    loadFromFileSystem(path);
                }
                return;
            }
            
            // For resources, we need to scan for language subdirectories
            // This is limited in JAR environments, so we'll try common language codes
            String[] commonLangCodes = {"en-US", "en-GB", "en", "zh-CN", "zh-TW", "ja", "ko", "de", "fr", "es"};
            for (String langCode : commonLangCodes) {
                loadLangFiles(directoryPath + "/" + langCode, langCode);
            }
            
        } catch (Exception e) {
            // Failed to load from directory
        }
    }
    
    /**
     * Loads .lang files from the file system.
     */
    private static void loadFromFileSystem(Path langDir) {
        if (!Files.exists(langDir) || !Files.isDirectory(langDir)) {
            return;
        }
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(langDir)) {
            for (Path langFolder : stream) {
                if (Files.isDirectory(langFolder)) {
                    String langCode = langFolder.getFileName().toString();
                    loadLangFilesFromPath(langFolder, langCode);
                }
            }
        } catch (IOException e) {
            // Failed to scan directory
        }
    }
    
    /**
     * Loads .lang files from a specific resource path.
     * Tries to find ANY .lang file, not just predefined names.
     */
    private static void loadLangFiles(String resourcePath, String langCode) {
        try {
            // Try common filenames first
            String[] commonNames = {"server.lang", "client.lang", "items.lang", "blocks.lang", "main.lang", 
                "item.lang", "block.lang", "en-US.lang", "en.lang", langCode + ".lang"};
            
            for (String fileName : commonNames) {
                String fullPath = resourcePath + "/" + fileName;
                InputStream stream = LangLoader.class.getClassLoader()
                    .getResourceAsStream(fullPath);
                
                if (stream != null) {
                    loadLangFile(stream, langCode, fileName);
                }
            }
            
        } catch (Exception e) {
            // Silently ignore - lang files are optional
        }
    }
    
    /**
     * Loads .lang files from a file system path.
     */
    private static void loadLangFilesFromPath(Path folder, String langCode) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, "*.lang")) {
            for (Path langFile : stream) {
                try (InputStream is = Files.newInputStream(langFile)) {
                    loadLangFile(is, langCode, langFile.getFileName().toString());
                }
            }
        } catch (IOException e) {
            // Failed to load lang files
        }
    }
    
    /**
     * Loads a single .lang file from an input stream.
     * 
     * @param stream The input stream to read from
     * @param langCode The language code (e.g., "en-US")
     * @param fileName The filename for logging purposes
     */
    private static void loadLangFile(InputStream stream, String langCode, String fileName) {
        Map<String, String> translations = translationCache.computeIfAbsent(
            langCode, k -> new ConcurrentHashMap<>()
        );
        
        int count = 0;
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                // Skip comments and empty lines
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                // Parse key = value
                Matcher matcher = LANG_ENTRY_PATTERN.matcher(line);
                if (matcher.matches()) {
                    String key = matcher.group(1);
                    String value = matcher.group(2);
                    translations.put(key, value);
                    count++;
                }
            }
            
        } catch (IOException e) {
            System.err.println("[LangLoader] Error reading " + fileName + ": " + e.getMessage());
        }
    }
    
    /**
     * Gets a translation for a key.
     * 
     * @param key The translation key (e.g., "items.Weapon_Sword_Gaias_Wrath.name")
     * @return The translated value, or null if not found
     */
    public static String getTranslation(String key) {
        return getTranslation(key, DEFAULT_LANG);
    }
    
    /**
     * Gets a translation for a key in a specific language.
     * 
     * @param key The translation key
     * @param langCode The language code (e.g., "en-US")
     * @return The translated value, or null if not found
     */
    public static String getTranslation(String key, String langCode) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        
        // Ensure initialized
        if (!initialized) {
            initialize();
        }
        
        Map<String, String> translations = translationCache.get(langCode);
        if (translations != null) {
            return translations.get(key);
        }
        
        return null;
    }
    
    /**
     * Gets a translation, falling back to a default value if not found.
     * 
     * @param key The translation key
     * @param defaultValue The default value to return if not found
     * @return The translated value, or defaultValue if not found
     */
    public static String getTranslationOrDefault(String key, String defaultValue) {
        String value = getTranslation(key);
        return value != null ? value : defaultValue;
    }
    
    /**
     * Checks if a translation exists for a key.
     * 
     * @param key The translation key
     * @return true if a translation exists
     */
    public static boolean hasTranslation(String key) {
        return getTranslation(key) != null;
    }
    
    /**
     * Resolves a translation key to its localized value.
     * If not found in .lang files, extracts a readable name from the key.
     * 
     * Translation key formats supported:
     * - server.items.<itemid>.name
     * - items.<itemid>.name
     * - <namespace>.items.<itemid>.name
     * 
     * Lang file format: key = Actual Display Name
     * 
     * @param translationKey The translation key
     * @return The localized string or extracted name
     */
    public static String resolveTranslation(String translationKey) {
        if (translationKey == null || translationKey.isEmpty()) {
            return translationKey;
        }
        
        // First, try exact match in loaded .lang files
        String translated = getTranslation(translationKey);
        if (translated != null && !translated.isEmpty()) {
            return translated;
        }
        
        // Try common key format variations
        // The translation key from item could be in various formats:
        // - "server.items.ItemId.name" (game standard)
        // - "items.ItemId.name" (mod standard)
        // - "wanmine.items.ItemId.name" (namespaced)
        String[] keyVariations = {
            translationKey,
            // Try with "server." prefix
            translationKey.startsWith("server.") ? translationKey : "server." + translationKey,
            // Try without "server." prefix
            translationKey.startsWith("server.") ? translationKey.substring(7) : translationKey,
            // Try without any namespace prefix (keep items.X.name)
            translationKey.replaceFirst("^[^.]+\\.(items\\..*)$", "$1"),
            // Try just the items.X.name part
            translationKey.contains(".items.") ? "items." + translationKey.substring(translationKey.indexOf(".items.") + 7) : translationKey,
        };
        
        for (String keyToTry : keyVariations) {
            if (keyToTry == null || keyToTry.isEmpty()) continue;
            translated = getTranslation(keyToTry);
            if (translated != null && !translated.isEmpty()) {
                return translated;
            }
        }
        
        // Fall back to extracting name from key
        return extractNameFromKey(translationKey);
    }
    
    /**
     * Extracts a readable name from a translation key as a fallback.
     * E.g., "wanmine.items.Weapon_Sword_Gaias_Wrath.name" -> "Gaias Wrath"
     *       "wanmine.items.Weapon_Axe_Iron.name" -> "Iron Axe"
     *       "wanmine.items.Armor_Adamantite_Head.name" -> "Adamantite Head"
     *
     * @param translationKey The translation key
     * @return A readable name extracted from the key
     */
    private static String extractNameFromKey(String translationKey) {
        if (translationKey == null || translationKey.isEmpty()) {
            return translationKey;
        }

        // Remove ".name" suffix if present
        String key = translationKey.replaceFirst("\\.name$", "");
        
        // Get the last part after the last dot
        int lastDot = key.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < key.length() - 1) {
            String namePart = key.substring(lastDot + 1);
            
            // Detect item category (Weapon, Armor, Item)
            boolean isArmor = namePart.startsWith("Armor_");
            boolean isWeapon = namePart.startsWith("Weapon_");
            
            // Remove common prefixes like "Weapon_", "Armor_", "Item_"
            namePart = namePart.replaceFirst("^(Weapon_|Armor_|Item_)", "");
            
            String[] parts = namePart.split("_");
            
            if (isArmor && parts.length == 2) {
                // Armor format: Material_Slot (e.g., "Adamantite_Head" -> "Adamantite Head")
                String material = capitalize(parts[0]);  // Adamantite
                String slot = capitalize(parts[1]);       // Head
                return material + " " + slot;
            } else if (isWeapon && parts.length == 2) {
                // Generic weapon format: Type_Material (e.g., "Axe_Iron" -> "Iron Axe")
                String type = capitalize(parts[0]);       // Axe
                String material = capitalize(parts[1]);   // Iron
                return material + " " + type;
            } else if (parts.length > 2) {
                // Unique item: Type_Name_Part1_Part2... format
                // Skip the type and use the rest (e.g., "Sword_Gaias_Wrath" -> "Gaias Wrath")
                StringBuilder result = new StringBuilder();
                for (int i = 1; i < parts.length; i++) {
                    if (parts[i].isEmpty()) continue;
                    if (result.length() > 0) result.append(" ");
                    result.append(capitalize(parts[i]));
                }
                if (result.length() > 0) {
                    return result.toString();
                }
            }
            
            // Fallback: replace underscores with spaces and capitalize words
            StringBuilder result = new StringBuilder();
            for (String word : parts) {
                if (word.isEmpty()) continue;
                if (result.length() > 0) result.append(" ");
                result.append(capitalize(word));
            }
            return result.toString();
        }

        return translationKey;
    }
    
    /**
     * Capitalizes the first letter of a word.
     */
    private static String capitalize(String word) {
        if (word == null || word.isEmpty()) {
            return word;
        }
        return Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase();
    }
    
    /**
     * Reloads all language files.
     * Useful for hot-reloading during development.
     */
    public static synchronized void reload() {
        translationCache.clear();
        initialized = false;
        initialize();
    }
    
    /**
     * Gets the number of loaded translations for a language.
     * 
     * @param langCode The language code
     * @return The number of translations, or 0 if not loaded
     */
    public static int getTranslationCount(String langCode) {
        Map<String, String> translations = translationCache.get(langCode);
        return translations != null ? translations.size() : 0;
    }
    
    /**
     * Gets all loaded language codes.
     * 
     * @return Array of loaded language codes
     */
    public static String[] getLoadedLanguages() {
        return translationCache.keySet().toArray(new String[0]);
    }
}
