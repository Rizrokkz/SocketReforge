package irai.mod.reforge.Util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bson.BsonDocument;
import org.bson.BsonValue;

import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interface_.UpdateLanguage;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;

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
    private static final Pattern JSON_FILE_PATTERN = Pattern.compile(".*\\.json$", Pattern.CASE_INSENSITIVE);
    
    // Cache of loaded translations: language -> (key -> value)
    private static final Map<String, Map<String, String>> translationCache = new ConcurrentHashMap<>();

    // Per-player language cache (tracked from UpdateLanguage packets)
    private static final Map<PlayerRef, String> playerLanguageCache = new ConcurrentHashMap<>();
    private static final Map<java.util.UUID, String> playerUuidLanguageCache = new ConcurrentHashMap<>();

    private static final AtomicBoolean languageWatcherRegistered = new AtomicBoolean(false);
    private static final AtomicBoolean i18nSyncAttempted = new AtomicBoolean(false);
    
    // Default language code
    private static final String DEFAULT_LANG = "en-US";
    
    // Default UI language (can be changed via system property or config)
    private static final String UI_LANGUAGE_PROPERTY = "reforge.ui.language";
    private static final String UI_LANGUAGE_PROPERTY_VALUE = System.getProperty(UI_LANGUAGE_PROPERTY);
    private static final boolean UI_LANGUAGE_CONFIGURED = UI_LANGUAGE_PROPERTY_VALUE != null
            && !UI_LANGUAGE_PROPERTY_VALUE.trim().isEmpty();
    private static String defaultUILang = UI_LANGUAGE_CONFIGURED
            ? UI_LANGUAGE_PROPERTY_VALUE.trim()
            : "en-US";

    private static final String KYUUBI_CORE_API = "com.kyuubisoft.core.api.CoreAPI";
    private static final String KYUUBI_LANG_HELPER = "com.kyuubisoft.core.i18n.LanguageSettingsHelper";
    private static final String KYUUBI_I18N_CONTEXT = "com.kyuubisoft.core.i18n.I18nContext";
    private static final String KYUUBI_MOD_ID = "irai.mod.reforge";
    private static final String KYUUBI_MOD_NAME = "Socket Reforge";
    private static final AtomicBoolean kyuubiAttempted = new AtomicBoolean(false);
    private static final AtomicBoolean kyuubiRegistered = new AtomicBoolean(false);
    private static volatile boolean kyuubiMissing = false;
    private static volatile java.lang.reflect.Method kyuubiIsAvailable;
    private static volatile java.lang.reflect.Method kyuubiGetInstance;
    private static volatile java.lang.reflect.Method kyuubiTranslate;
    private static volatile java.lang.reflect.Method kyuubiGetPlayerLanguage;
    private static volatile java.lang.reflect.Method kyuubiGetPlayerLanguageMod;
    private static volatile java.lang.reflect.Method kyuubiI18nRun;
    private static volatile java.lang.reflect.Method kyuubiI18nRunWithLanguage;
    private static volatile java.lang.reflect.Method kyuubiRegisterMod;
    private static volatile java.lang.reflect.Method kyuubiGetPlayerPreferences;
    private static volatile java.lang.reflect.Method kyuubiSetPlayerModLanguageOverride;
    private static volatile java.lang.reflect.Method kyuubiPrefsGetModLanguageOverride;
    private static volatile java.lang.reflect.Method kyuubiPrefsGetLanguageOverride;

    // Most recent player language observed (best-effort fallback for contexts without player info)
    private static volatile String lastKnownLanguage = null;
    
    // Loaded flag
    private static boolean initialized = false;

    private static final Pattern UI_TOKEN_PATTERN = Pattern.compile("\\{\\{\\s*(ui\\.[^}\\s]+)\\s*}}");
    
    /**
     * Sets the default UI language.
     * 
     * @param langCode The language code (e.g., "en-US", "pt-BR")
     */
    public static void setDefaultUILanguage(String langCode) {
        if (langCode != null && !langCode.trim().isEmpty()) {
            defaultUILang = langCode.trim();
        }
    }
    
    /**
     * Gets the current default UI language.
     * 
     * @return The current default UI language code
     */
    public static String getDefaultUILanguage() {
        return defaultUILang;
    }

    /**
     * Gets a best-effort fallback language for contexts without player info.
     */
    public static String getFallbackLanguage() {
        if (lastKnownLanguage != null && !lastKnownLanguage.isBlank()) {
            return lastKnownLanguage;
        }
        return defaultUILang;
    }
    
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

        // Optional integration with KyuubiSoftCore language settings
        ensureKyuubiSupport();

        // Sync our translations into the core i18n module if available
        syncTranslationsToI18n();
        
        initialized = true;
        registerLanguageWatcher();
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
            String[] commonLangCodes = {"en-US", "en-GB", "en", "zh-CN", "zh-TW", "ja", "ko", "de", "de-DE", "fr", "es", "pt-BR"};
            for (String langCode : commonLangCodes) {
                loadLangFiles(directoryPath + "/" + langCode, langCode);
                loadJsonLangFiles(directoryPath, langCode);
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
                } else if (Files.isRegularFile(langFolder)) {
                    String fileName = langFolder.getFileName().toString();
                    if (JSON_FILE_PATTERN.matcher(fileName).matches()) {
                        String langCode = fileName.substring(0, fileName.length() - ".json".length());
                        loadJsonLangFileFromPath(langFolder, langCode);
                    }
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
            "item.lang", "block.lang", "en-US.lang", "en.lang", langCode + ".lang", "irai.lang"};
            
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

    private static void loadJsonLangFiles(String basePath, String langCode) {
        try {
            String fileName = langCode + ".json";
            String fullPath = basePath + "/" + fileName;
            InputStream stream = LangLoader.class.getClassLoader().getResourceAsStream(fullPath);
            if (stream != null) {
                loadJsonLangFile(stream, langCode, fileName);
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

    private static void loadJsonLangFileFromPath(Path filePath, String langCode) {
        try (InputStream is = Files.newInputStream(filePath)) {
            loadJsonLangFile(is, langCode, filePath.getFileName().toString());
        } catch (IOException e) {
            // Failed to read json lang file
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

    private static void loadJsonLangFile(InputStream stream, String langCode, String fileName) {
        Map<String, String> translations = translationCache.computeIfAbsent(
            langCode, k -> new ConcurrentHashMap<>()
        );

        try {
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            if (json.isBlank()) {
                return;
            }
            BsonDocument doc = BsonDocument.parse(json);
            for (Map.Entry<String, BsonValue> entry : doc.entrySet()) {
                BsonValue value = entry.getValue();
                if (value != null && value.isString()) {
                    String key = entry.getKey();
                    String text = value.asString().getValue();
                    if (key != null && !key.isBlank() && text != null) {
                        translations.put(key, text);
                    }
                }
            }
        } catch (Exception e) {
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

        String resolvedLang = resolveLanguageCode(langCode);
        Map<String, String> translations = translationCache.get(resolvedLang);
        if (translations != null) {
            return translations.get(key);
        }

        // Fall back to the server i18n module for base game translations
        String i18n = getI18nTranslation(key, resolvedLang);
        if (i18n != null) {
            return i18n;
        }
        return null;
    }

    /**
     * Gets a translation for a key in a specific language without falling back
     * to other languages.
     *
     * @param key The translation key
     * @param langCode The language code (e.g., "en-US")
     * @return The translated value, or null if not found for that language
     */
    public static String getTranslationExact(String key, String langCode) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        if (!initialized) {
            initialize();
        }

        String normalized = normalizeLanguageCode(langCode);
        if (normalized != null) {
            Map<String, String> translations = translationCache.get(normalized);
            if (translations != null) {
                String value = translations.get(key);
                if (value != null) {
                    return value;
                }
            }
        }
        if (langCode != null && !langCode.isBlank()) {
            Map<String, String> translations = translationCache.get(langCode);
            if (translations != null) {
                String value = translations.get(key);
                if (value != null) {
                    return value;
                }
            }
        }
        return getI18nTranslationExact(key, normalized != null ? normalized : langCode);
    }

    /**
     * Returns a translation only if it exists in the loaded .lang cache for the given language.
     * Does not consult the I18n module or fall back to other languages.
     */
    public static String getTranslationFromCache(String key, String langCode) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        if (!initialized) {
            initialize();
        }

        String normalized = normalizeLanguageCode(langCode);
        if (normalized != null) {
            Map<String, String> translations = translationCache.get(normalized);
            if (translations != null) {
                String value = translations.get(key);
                if (value != null) {
                    return value;
                }
            }
        }
        if (langCode != null && !langCode.isBlank()) {
            Map<String, String> translations = translationCache.get(langCode);
            if (translations != null) {
                String value = translations.get(key);
                if (value != null) {
                    return value;
                }
            }
        }

        // Try root language (e.g., "pt" from "pt-BR") if present in cache
        String base = normalized;
        if (base == null || base.isBlank()) {
            base = langCode == null ? "" : langCode.trim();
        }
        int dash = base.indexOf('-');
        int underscore = base.indexOf('_');
        int split = dash >= 0 ? dash : underscore;
        if (split > 0) {
            String root = base.substring(0, split).toLowerCase(Locale.ROOT);
            Map<String, String> translations = translationCache.get(root);
            if (translations != null) {
                String value = translations.get(key);
                if (value != null) {
                    return value;
                }
            }
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
     * Gets the player's preferred language code.
     * TODO: Implement actual player locale detection when Hytale API supports it.
     * For now, returns the default UI language (configurable via system property "reforge.ui.language").
     * 
     * @param player The player (currently unused)
     * @return The language code (e.g., "en-US", "pt-BR")
     */
    public static String getPlayerLanguage(Object player) {
        if (player instanceof PlayerRef ref) {
            String refLang = resolveLanguageCode(ref.getLanguage());
            String kyuubiLang = getKyuubiPlayerLanguage(ref);
            if (kyuubiLang != null && !kyuubiLang.isBlank()) {
                String normalizedKyuubi = resolveLanguageCode(kyuubiLang);
                if (refLang != null && !refLang.isBlank()
                        && normalizedKyuubi != null && !normalizedKyuubi.isBlank()
                        && "en-US".equalsIgnoreCase(normalizedKyuubi)
                        && !refLang.equalsIgnoreCase(normalizedKyuubi)) {
                    setPlayerLanguage(ref, refLang);
                    return refLang;
                }
                setPlayerLanguage(ref, kyuubiLang);
                return resolveLanguageCode(kyuubiLang);
            }
            String cached = playerLanguageCache.get(ref);
            if (cached != null) {
                String resolved = resolveLanguageCode(cached);
                if (resolved != null && !resolved.isBlank() && !resolved.equals(cached)) {
                    playerLanguageCache.put(ref, resolved);
                    playerUuidLanguageCache.put(ref.getUuid(), resolved);
                    lastKnownLanguage = resolved;
                    return resolved;
                }
                return cached;
            }
            if (refLang != null && !refLang.isBlank()) {
                String resolved = resolveLanguageCode(refLang);
                if (resolved == null || resolved.isBlank()) {
                    resolved = refLang;
                }
                playerLanguageCache.put(ref, resolved);
                playerUuidLanguageCache.put(ref.getUuid(), resolved);
                lastKnownLanguage = resolved;
                if (!UI_LANGUAGE_CONFIGURED && DEFAULT_LANG.equalsIgnoreCase(defaultUILang)) {
                    defaultUILang = resolved;
                }
                return resolved;
            }
            return defaultUILang;
        }
        if (player instanceof Player ply) {
            PlayerRef ref = ply.getPlayerRef();
            if (ref != null) {
                String refLang = resolveLanguageCode(ref.getLanguage());
                String kyuubiLang = getKyuubiPlayerLanguage(ref);
                if (kyuubiLang != null && !kyuubiLang.isBlank()) {
                    String normalizedKyuubi = resolveLanguageCode(kyuubiLang);
                    if (refLang != null && !refLang.isBlank()
                            && normalizedKyuubi != null && !normalizedKyuubi.isBlank()
                            && "en-US".equalsIgnoreCase(normalizedKyuubi)
                            && !refLang.equalsIgnoreCase(normalizedKyuubi)) {
                        setPlayerLanguage(ref, refLang);
                        return refLang;
                    }
                    setPlayerLanguage(ref, kyuubiLang);
                    return resolveLanguageCode(kyuubiLang);
                }
                String cached = playerLanguageCache.get(ref);
                if (cached != null) {
                    String resolved = resolveLanguageCode(cached);
                    if (resolved != null && !resolved.isBlank() && !resolved.equals(cached)) {
                        playerLanguageCache.put(ref, resolved);
                        playerUuidLanguageCache.put(ref.getUuid(), resolved);
                        lastKnownLanguage = resolved;
                        return resolved;
                    }
                    return cached;
                }
                if (refLang != null && !refLang.isBlank()) {
                    String resolved = resolveLanguageCode(refLang);
                    if (resolved == null || resolved.isBlank()) {
                        resolved = refLang;
                    }
                    playerLanguageCache.put(ref, resolved);
                    playerUuidLanguageCache.put(ref.getUuid(), resolved);
                    lastKnownLanguage = resolved;
                    if (!UI_LANGUAGE_CONFIGURED && DEFAULT_LANG.equalsIgnoreCase(defaultUILang)) {
                        defaultUILang = resolved;
                    }
                    return resolved;
                }
            }
            return defaultUILang;
        }
        return defaultUILang;
    }
    
    /**
     * Gets a UI translation for the specified player.
     * 
     * @param player The player
     * @param key The UI translation key
     * @return The translated text, or key if not found
     */
    public static String getUITranslation(Object player, String key) {
        PlayerRef ref = null;
        if (player instanceof PlayerRef refValue) {
            ref = refValue;
        } else if (player instanceof Player ply) {
            ref = ply.getPlayerRef();
        }

        String kyuubi = getKyuubiTranslation(ref, key, null);
        if (kyuubi != null) {
            return kyuubi;
        }

        String langCode = getPlayerLanguage(player);
        String translation = getTranslation(key, langCode);
        if (translation == null) {
            // Fallback to default language if translation not found
            translation = getTranslation(key, DEFAULT_LANG);
            if (translation == null) {
                return key; // Return key if not found in any language
            }
        }
        return translation;
    }
    
    /**
     * Gets a UI translation with parameter substitution.
     * 
     * @param player The player
     * @param key The UI translation key
     * @param params Parameters to substitute (replaces {0}, {1}, etc.)
     * @return The translated and formatted text
     */
    public static String getUITranslation(Object player, String key, Object... params) {
        String template = getUITranslation(player, key);
        if (params.length == 0) {
            return template;
        }
        
        // Simple parameter substitution: {0}, {1}, etc.
        for (int i = 0; i < params.length; i++) {
            template = template.replace("{" + i + "}", String.valueOf(params[i]));
        }
        return template;
    }

    /**
     * Gets a translation for a key in the specified language, with fallback to default.
     *
     * @param key The translation key
     * @param langCode The desired language code
     * @return The translated value, or the key if not found
     */
    public static String getTranslationForLanguage(String key, String langCode) {
        String normalized = normalizeLanguageCode(langCode);
        String kyuubi = getKyuubiTranslation(null, key, normalized);
        if (kyuubi != null) {
            return kyuubi;
        }

        String translation = getTranslation(key, langCode);
        if (translation == null) {
            translation = getTranslation(key, DEFAULT_LANG);
        }
        return translation != null ? translation : key;
    }

    /**
     * Gets a translation for a key with parameter substitution, using a specific language code.
     *
     * @param key The translation key
     * @param langCode The language code
     * @param params Parameters to substitute (replaces {0}, {1}, etc.)
     * @return The translated and formatted text
     */
    public static String formatTranslation(String key, String langCode, Object... params) {
        String template = getTranslationForLanguage(key, langCode);
        if (params.length == 0) {
            return template;
        }
        for (int i = 0; i < params.length; i++) {
            template = template.replace("{" + i + "}", String.valueOf(params[i]));
        }
        return template;
    }

    /**
     * Replaces inline {{ui.key}} tokens in HTML with localized strings.
     *
     * @param player The player
     * @param html HTML template containing {{ui.key}} tokens
     * @return HTML with tokens replaced
     */
    public static String replaceUiTokens(Object player, String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        Matcher matcher = UI_TOKEN_PATTERN.matcher(html);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = getUITranslation(player, key);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
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
        return resolveTranslation(translationKey, DEFAULT_LANG);
    }

    /**
     * Resolves a translation key to its localized value for a specific language.
     * If not found in .lang files, extracts a readable name from the key.
     *
     * @param translationKey The translation key
     * @param langCode The language code
     * @return The localized string or extracted name
     */
    public static String resolveTranslation(String translationKey, String langCode) {
        if (translationKey == null || translationKey.isEmpty()) {
            return translationKey;
        }
        
        // First, try exact match in loaded .lang files (with fallback to default language)
        String translated = getTranslationForLanguage(translationKey, langCode);
        if (translated != null && !translated.isEmpty() && !translated.equals(translationKey)) {
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
            translated = getTranslationForLanguage(keyToTry, langCode);
            if (translated != null && !translated.isEmpty() && !translated.equals(keyToTry)) {
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
        i18nSyncAttempted.set(false);
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

    private static void ensureKyuubiSupport() {
        if (kyuubiMissing) {
            return;
        }
        if (kyuubiAttempted.compareAndSet(false, true)) {
            try {
                Class<?> coreApi = Class.forName(KYUUBI_CORE_API);
                kyuubiIsAvailable = coreApi.getMethod("isAvailable");
                kyuubiGetInstance = coreApi.getMethod("getInstance");
                kyuubiTranslate = coreApi.getMethod("translate", String.class);
                kyuubiGetPlayerLanguage = coreApi.getMethod("getPlayerLanguage", PlayerRef.class);
                try {
                    kyuubiGetPlayerLanguageMod = coreApi.getMethod("getPlayerLanguage", PlayerRef.class, String.class);
                } catch (NoSuchMethodException ignored) {
                    kyuubiGetPlayerLanguageMod = null;
                }
                try {
                    kyuubiGetPlayerPreferences = coreApi.getMethod("getPlayerPreferences", java.util.UUID.class);
                } catch (NoSuchMethodException ignored) {
                    kyuubiGetPlayerPreferences = null;
                }
                try {
                    kyuubiSetPlayerModLanguageOverride = coreApi.getMethod(
                            "setPlayerModLanguageOverride",
                            java.util.UUID.class,
                            String.class,
                            String.class,
                            String.class
                    );
                } catch (NoSuchMethodException ignored) {
                    kyuubiSetPlayerModLanguageOverride = null;
                }
                try {
                    Class<?> helper = Class.forName(KYUUBI_LANG_HELPER);
                    kyuubiRegisterMod = helper.getMethod("registerMod", String.class, String.class);
                } catch (Exception ignored) {
                    kyuubiRegisterMod = null;
                }
                try {
                    Class<?> ctx = Class.forName(KYUUBI_I18N_CONTEXT);
                    kyuubiI18nRun = ctx.getMethod("run", PlayerRef.class, Runnable.class);
                    kyuubiI18nRunWithLanguage = ctx.getMethod("runWithLanguage", String.class, Runnable.class);
                } catch (Exception ignored) {
                    kyuubiI18nRun = null;
                    kyuubiI18nRunWithLanguage = null;
                }
                try {
                    Class<?> prefs = Class.forName("com.kyuubisoft.core.i18n.PlayerPreferences");
                    kyuubiPrefsGetModLanguageOverride = prefs.getMethod("getModLanguageOverride", String.class);
                    kyuubiPrefsGetLanguageOverride = prefs.getMethod("getLanguageOverride");
                } catch (Exception ignored) {
                    kyuubiPrefsGetModLanguageOverride = null;
                    kyuubiPrefsGetLanguageOverride = null;
                }
            } catch (Exception e) {
                kyuubiMissing = true;
                return;
            }
        }

        if (!isKyuubiAvailable()) {
            return;
        }
        if (kyuubiRegistered.compareAndSet(false, true) && kyuubiRegisterMod != null) {
            try {
                kyuubiRegisterMod.invoke(null, KYUUBI_MOD_ID, KYUUBI_MOD_NAME);
            } catch (Exception ignored) {
                // Optional integration; ignore failures
            }
        }
    }

    private static boolean isKyuubiAvailable() {
        if (kyuubiMissing || kyuubiIsAvailable == null) {
            return false;
        }
        try {
            Object value = kyuubiIsAvailable.invoke(null);
            return value instanceof Boolean && (Boolean) value;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String getKyuubiPlayerLanguage(PlayerRef ref) {
        if (ref == null) {
            return null;
        }
        ensureKyuubiSupport();
        if (!isKyuubiAvailable() || kyuubiGetPlayerLanguage == null) {
            return null;
        }
        try {
            if (kyuubiGetPlayerLanguageMod != null) {
                Object modLang = kyuubiGetPlayerLanguageMod.invoke(null, ref, KYUUBI_MOD_ID);
                if (modLang instanceof String lang && !lang.isBlank()) {
                    return lang;
                }
            }
            Object lang = kyuubiGetPlayerLanguage.invoke(null, ref);
            if (lang instanceof String value && !value.isBlank()) {
                return value;
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static String getKyuubiTranslation(PlayerRef ref, String key, String langCode) {
        if (key == null || key.isBlank()) {
            return null;
        }
        ensureKyuubiSupport();
        if (!isKyuubiAvailable() || kyuubiGetInstance == null || kyuubiTranslate == null) {
            return null;
        }
        try {
            Object api = kyuubiGetInstance.invoke(null);
            if (api == null) {
                return null;
            }
            final String[] result = new String[1];
            Runnable task = () -> {
                try {
                    Object value = kyuubiTranslate.invoke(api, key);
                    if (value instanceof String text && !text.isBlank() && !text.equals(key)) {
                        result[0] = text;
                    }
                } catch (Exception ignored) {
                    // Optional integration; ignore failures
                }
            };

            if (ref != null && kyuubiI18nRun != null) {
                kyuubiI18nRun.invoke(null, ref, task);
            } else if (langCode != null && !langCode.isBlank() && kyuubiI18nRunWithLanguage != null) {
                kyuubiI18nRunWithLanguage.invoke(null, langCode, task);
            } else {
                task.run();
            }
            return result[0];
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String getKyuubiModLanguageOverride(PlayerRef ref) {
        if (ref == null) {
            return null;
        }
        ensureKyuubiSupport();
        if (!isKyuubiAvailable() || kyuubiGetPlayerPreferences == null || kyuubiPrefsGetModLanguageOverride == null) {
            return null;
        }
        try {
            Object prefs = kyuubiGetPlayerPreferences.invoke(null, ref.getUuid());
            if (prefs == null) {
                return null;
            }
            Object value = kyuubiPrefsGetModLanguageOverride.invoke(prefs, KYUUBI_MOD_ID);
            if (value instanceof String lang && !lang.isBlank()) {
                return lang;
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static String getKyuubiLanguageOverride(java.util.UUID uuid) {
        if (uuid == null) {
            return null;
        }
        ensureKyuubiSupport();
        if (!isKyuubiAvailable() || kyuubiGetPlayerPreferences == null || kyuubiPrefsGetLanguageOverride == null) {
            return null;
        }
        try {
            Object prefs = kyuubiGetPlayerPreferences.invoke(null, uuid);
            if (prefs == null) {
                return null;
            }
            Object value = kyuubiPrefsGetLanguageOverride.invoke(prefs);
            if (value instanceof String lang && !lang.isBlank()) {
                return lang;
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static void ensureKyuubiModLanguage(PlayerRef ref, String desiredLang) {
        if (ref == null) {
            return;
        }
        ensureKyuubiSupport();
        if (!isKyuubiAvailable() || kyuubiSetPlayerModLanguageOverride == null) {
            return;
        }
        String existing = getKyuubiModLanguageOverride(ref);
        if (existing != null && !existing.isBlank()) {
            if ("auto".equalsIgnoreCase(existing)) {
                return;
            }
            return;
        }
        if (desiredLang == null || desiredLang.isBlank()) {
            return;
        }
        try {
            kyuubiSetPlayerModLanguageOverride.invoke(null, ref.getUuid(), KYUUBI_MOD_ID, desiredLang, "auto");
        } catch (Exception ignored) {
            // Optional integration; ignore failures
        }
    }

    private static void registerLanguageWatcher() {
        if (!languageWatcherRegistered.compareAndSet(false, true)) {
            return;
        }
        try {
            PacketAdapters.registerInbound((PlayerRef ref, Packet packet) -> {
                if (ref == null || packet == null) {
                    return;
                }
                if (packet instanceof UpdateLanguage) {
                    UpdateLanguage update = (UpdateLanguage) packet;
                    setPlayerLanguage(ref, update.language);
                }
            });
        } catch (Exception e) {
            System.err.println("[LangLoader] Failed to register language watcher: " + e.getMessage());
        }
    }

    private static void setPlayerLanguage(PlayerRef ref, String langCode) {
        if (ref == null) {
            return;
        }
        String normalized = resolveLanguageCode(langCode);
        if (normalized == null || normalized.isBlank()) {
            normalized = defaultUILang;
        }
        playerLanguageCache.put(ref, normalized);
        playerUuidLanguageCache.put(ref.getUuid(), normalized);
        lastKnownLanguage = normalized;
        if (!UI_LANGUAGE_CONFIGURED && DEFAULT_LANG.equalsIgnoreCase(defaultUILang)) {
            defaultUILang = normalized;
        }
        ensureKyuubiModLanguage(ref, normalized);
        syncTranslationsToI18n();
    }

    public static String getLanguageForUuid(java.util.UUID uuid) {
        if (uuid == null) {
            return getFallbackLanguage();
        }
        String cached = playerUuidLanguageCache.get(uuid);
        if (cached != null && !cached.isBlank()) {
            String resolved = resolveLanguageCode(cached);
            if (resolved != null && !resolved.isBlank() && !resolved.equals(cached)) {
                playerUuidLanguageCache.put(uuid, resolved);
                return resolved;
            }
            return cached;
        }
        String override = getKyuubiLanguageOverride(uuid);
        if (override != null && !override.isBlank()) {
            String normalized = resolveLanguageCode(override);
            if (normalized != null && !normalized.isBlank()) {
                playerUuidLanguageCache.put(uuid, normalized);
                return normalized;
            }
        }
        return getFallbackLanguage();
    }

    private static void syncTranslationsToI18n() {
        if (translationCache.isEmpty()) {
            return;
        }
        I18nModule module = I18nModule.get();
        if (module == null) {
            return;
        }
        if (!i18nSyncAttempted.compareAndSet(false, true)) {
            // Allow re-entry when reload() clears the flag.
            return;
        }
        try {
            java.lang.reflect.Field languagesField = I18nModule.class.getDeclaredField("languages");
            languagesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Map<String, String>> languages =
                    (Map<String, Map<String, String>>) languagesField.get(module);

            java.lang.reflect.Field cachedField = I18nModule.class.getDeclaredField("cachedLanguages");
            cachedField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Map<String, String>> cached =
                    (Map<String, Map<String, String>>) cachedField.get(module);

            for (Map.Entry<String, Map<String, String>> entry : translationCache.entrySet()) {
                String lang = normalizeLanguageCode(entry.getKey());
                if (lang == null || lang.isBlank()) {
                    continue;
                }
                Map<String, String> target = languages.computeIfAbsent(lang, k -> new ConcurrentHashMap<>());
                target.putAll(entry.getValue());
                if (cached != null) {
                    Map<String, String> cachedMap = cached.get(lang);
                    if (cachedMap != null) {
                        cachedMap.putAll(entry.getValue());
                    }
                }
            }
        } catch (Exception ignored) {
            // Optional integration; ignore failures
        }
    }

    private static String normalizeLanguageCode(String langCode) {
        if (langCode == null) {
            return null;
        }
        String trimmed = langCode.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String normalized = trimmed.replace('_', '-');
        if ("br".equalsIgnoreCase(normalized)) {
            return "pt-BR";
        }
        int dash = normalized.indexOf('-');
        if (dash > 0 && dash < normalized.length() - 1) {
            String lang = normalized.substring(0, dash).toLowerCase(Locale.ROOT);
            String region = normalized.substring(dash + 1).toUpperCase(Locale.ROOT);
            return lang + "-" + region;
        }
        return normalized;
    }

    /**
     * Exposes language normalization for other helpers.
     */
    public static String normalizeLanguage(String langCode) {
        return normalizeLanguageCode(langCode);
    }

    private static String getI18nTranslation(String key, String langCode) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        try {
            I18nModule module = I18nModule.get();
            if (module == null) {
                return null;
            }
            String normalized = normalizeLanguageCode(langCode);
            if (normalized == null || normalized.isBlank()) {
                normalized = DEFAULT_LANG;
            }

            java.util.LinkedHashSet<String> candidates = new java.util.LinkedHashSet<>();
            candidates.add(normalized);
            if (normalized.contains("-")) {
                candidates.add(normalized.replace('-', '_'));
            } else if (normalized.contains("_")) {
                candidates.add(normalized.replace('_', '-'));
            }
            int dash = normalized.indexOf('-');
            int underscore = normalized.indexOf('_');
            int split = dash >= 0 ? dash : underscore;
            if (split > 0) {
                candidates.add(normalized.substring(0, split).toLowerCase(Locale.ROOT));
            }

            for (String locale : candidates) {
                if (locale == null || locale.isBlank()) {
                    continue;
                }
                String value = module.getMessage(locale, key);
                if (value != null && !value.isBlank() && !value.equals(key)) {
                    return value;
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String getI18nTranslationExact(String key, String langCode) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        try {
            I18nModule module = I18nModule.get();
            if (module == null) {
                return null;
            }
            String normalized = normalizeLanguageCode(langCode);
            if (normalized == null || normalized.isBlank()) {
                return null;
            }

            java.util.LinkedHashSet<String> candidates = new java.util.LinkedHashSet<>();
            candidates.add(normalized);
            if (normalized.contains("-")) {
                candidates.add(normalized.replace('-', '_'));
            } else if (normalized.contains("_")) {
                candidates.add(normalized.replace('_', '-'));
            }
            int dash = normalized.indexOf('-');
            int underscore = normalized.indexOf('_');
            int split = dash >= 0 ? dash : underscore;
            if (split > 0) {
                candidates.add(normalized.substring(0, split).toLowerCase(Locale.ROOT));
            }

            for (String locale : candidates) {
                if (locale == null || locale.isBlank()) {
                    continue;
                }
                String value = module.getMessage(locale, key);
                if (value != null && !value.isBlank() && !value.equals(key)) {
                    return value;
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String resolveLanguageCode(String langCode) {
        if (langCode == null || langCode.isBlank()) {
            return DEFAULT_LANG;
        }
        String normalized = normalizeLanguageCode(langCode);
        if (normalized != null && translationCache.containsKey(normalized)) {
            return normalized;
        }
        if (translationCache.containsKey(langCode)) {
            return langCode;
        }

        String base = normalized;
        if (base == null || base.isBlank()) {
            base = langCode.trim();
        }
        int dash = base.indexOf('-');
        if (dash > 0) {
            String root = base.substring(0, dash).toLowerCase(Locale.ROOT);
            if (translationCache.containsKey(root)) {
                return root;
            }
            for (String loaded : translationCache.keySet()) {
                if (loaded.toLowerCase(Locale.ROOT).startsWith(root + "-")) {
                    return loaded;
                }
            }
        } else if (base.length() == 2) {
            String root = base.toLowerCase(Locale.ROOT);
            if (translationCache.containsKey(root)) {
                return root;
            }
            for (String loaded : translationCache.keySet()) {
                if (loaded.toLowerCase(Locale.ROOT).startsWith(root + "-")) {
                    return loaded;
                }
            }
            if ("en".equals(root) && translationCache.containsKey("en-US")) {
                return "en-US";
            }
            if ("pt".equals(root) && translationCache.containsKey("pt-BR")) {
                return "pt-BR";
            }
        }
        return normalized != null ? normalized : DEFAULT_LANG;
    }
}
