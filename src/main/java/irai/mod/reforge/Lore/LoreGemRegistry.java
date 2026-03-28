package irai.mod.reforge.Lore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import com.hypixel.hytale.assetstore.map.IndexedLookupTableAssetMap;
import com.hypixel.hytale.server.spawning.assets.spawns.config.RoleSpawnParameters;
import com.hypixel.hytale.server.spawning.assets.spawns.config.WorldNPCSpawn;

import irai.mod.reforge.Config.LoreMappingConfig;

/**
 * Resolves lore gem colors and spirit pools from config.
 */
public final class LoreGemRegistry {
    private static final class GemColorRule {
        private final String token;
        private final String color;

        private GemColorRule(String token, String color) {
            this.token = token;
            this.color = color;
        }
    }

    private static volatile List<GemColorRule> gemRules = List.of();
    private static volatile Map<String, List<String>> colorSpiritMap = Map.of();
    private static volatile List<String> knownColors = List.of();
    private static volatile List<String> coreColors = List.of();
    private static volatile Map<String, List<String>> environmentSpiritMap = Map.of();
    private static volatile List<String> globalSpiritPool = List.of();
    private static volatile Set<String> globalSpiritSet = Set.of();
    private static volatile Map<String, String> globalSpiritByKey = Map.of();
    private static volatile Map<String, String> spiritColorMap = Map.of();
    private static final Object NPC_POOL_LOCK = new Object();
    private static volatile long lastNpcPoolBuildAttempt = 0L;
    private static final String[][] SPIRIT_COLOR_RULES = new String[][] {
            {"black", "voidstone", "void", "shadow", "abyss", "dark", "demon", "undead", "wraith", "ghost", "shade", "necrom"},
            {"white", "diamond", "holy", "angel", "radiant", "light", "pure", "sanct"},
            {"yellow", "topaz", "lightning", "thunder", "spark", "storm", "sun", "gold", "desert", "sand"},
            {"red", "ruby", "fire", "flame", "ember", "lava", "ash", "magma", "blood", "inferno", "dragon"},
            {"blue", "sapphire", "ice", "frost", "snow", "glacier", "water", "aqua", "sea", "river"},
            {"green", "emerald", "forest", "jungle", "swamp", "moss", "leaf", "vine", "thorn", "root", "nature", "druid", "earth"},
            {"cyan", "zephyr", "sky", "wind", "air", "gust", "cloud", "bird", "hawk", "eagle", "gryphon", "wing"}
    };

    private LoreGemRegistry() {}

    public static void initialize(LoreMappingConfig config) {
        if (config == null) {
            gemRules = List.of();
            colorSpiritMap = Map.of();
            knownColors = List.of();
            coreColors = List.of();
            environmentSpiritMap = Map.of();
            globalSpiritPool = List.of();
            globalSpiritSet = Set.of();
            globalSpiritByKey = Map.of();
            spiritColorMap = Map.of();
            return;
        }
        gemRules = parseGemRules(config.getGemColorEntries());
        colorSpiritMap = parseSpiritPools(config.getColorSpiritEntries());
        coreColors = parseCoreColors(config.getCoreColorEntries());
        knownColors = extractColors(gemRules);
        refreshNpcPools(true);
    }

    public static String resolveColor(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        String lower = itemId.toLowerCase(Locale.ROOT);
        for (GemColorRule rule : gemRules) {
            if (rule == null || rule.token == null) {
                continue;
            }
            if (lower.contains(rule.token)) {
                return rule.color;
            }
        }
        if (lower.contains("gem")) {
            String inferred = inferGemColor(lower);
            if (inferred != null && !inferred.isBlank()) {
                return inferred;
            }
        }
        return null;
    }

    public static String resolveGemItemIdForColor(String color) {
        if (color == null || color.isBlank()) {
            return null;
        }
        String normalized = color.trim().toLowerCase(Locale.ROOT);
        List<GemColorRule> rules = gemRules;
        if (rules == null || rules.isEmpty()) {
            return null;
        }
        for (GemColorRule rule : rules) {
            if (rule == null || rule.color == null || rule.token == null) {
                continue;
            }
            if (rule.color.trim().equalsIgnoreCase(normalized)) {
                return rule.token;
            }
        }
        return null;
    }

    private static String inferGemColor(String lowerItemId) {
        if (lowerItemId == null || lowerItemId.isBlank()) {
            return null;
        }
        if (containsAny(lowerItemId, "voidstone", "void")) {
            return "black";
        }
        if (containsAny(lowerItemId, "diamond", "white", "holy", "light")) {
            return "white";
        }
        if (containsAny(lowerItemId, "ruby", "red", "fire", "flame")) {
            return "red";
        }
        if (containsAny(lowerItemId, "sapphire", "blue", "water", "ice")) {
            return "blue";
        }
        if (containsAny(lowerItemId, "emerald", "green", "nature", "earth")) {
            return "green";
        }
        if (containsAny(lowerItemId, "topaz", "yellow", "gold", "storm", "lightning")) {
            return "yellow";
        }
        if (containsAny(lowerItemId, "zephyr", "cyan", "wind", "air")) {
            return "cyan";
        }
        return null;
    }

    private static boolean containsAny(String value, String... tokens) {
        if (value == null || value.isBlank() || tokens == null || tokens.length == 0) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            if (lower.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public static List<String> getAllowedSpirits(String color) {
        return getAllowedSpirits(color, null, null, null);
    }

    public static List<String> getAllowedSpirits(String color, String zoneName, String regionName, String biomeName) {
        List<String> zonePool = resolveZonePool(zoneName, regionName, biomeName);
        List<String> explicit = resolveExplicitColorPool(color);
        if (explicit != null && !explicit.isEmpty()) {
            if (zonePool == null || zonePool.isEmpty()) {
                return explicit;
            }
            List<String> filtered = filterByPool(explicit, zonePool);
            return filtered.isEmpty() ? explicit : filtered;
        }
        if (zonePool == null || zonePool.isEmpty()) {
            return List.of();
        }
        List<String> autoFiltered = filterBySpiritColor(zonePool, color);
        return autoFiltered.isEmpty() ? zonePool : autoFiltered;
    }

    public static String pickSpiritId(String color, String descriptor) {
        return pickSpiritId(color, descriptor, null, null, null);
    }

    public static String pickSpiritId(String color, String descriptor, String zoneName, String regionName, String biomeName) {
        String normalizedColor = normalizeKey(color);
        List<String> pool = getAllowedSpirits(normalizedColor, zoneName, regionName, biomeName);
        String seed = buildSeed(descriptor, normalizedColor, zoneName, regionName, biomeName);

        if (pool != null && !pool.isEmpty()) {
            int idx = Math.abs(seed == null ? 0 : seed.hashCode()) % pool.size();
            return pool.get(idx);
        }

        if (descriptor != null && !descriptor.isBlank()) {
            return "lore.spirit." + sanitizeToken(descriptor);
        }
        if (normalizedColor != null && !normalizedColor.isBlank()) {
            return "lore.spirit." + sanitizeToken(normalizedColor);
        }
        return "lore.spirit.unknown";
    }

    public static String pickRandomColor(Random rng) {
        List<String> colors = knownColors;
        if (colors == null || colors.isEmpty()) {
            return "unknown";
        }
        Random random = rng == null ? ThreadLocalRandom.current() : rng;
        int idx = Math.floorMod(random.nextInt(), colors.size());
        return colors.get(idx);
    }

    public static List<String> getKnownColors() {
        return knownColors;
    }

    public static boolean isSpawnableSpirit(String spiritId) {
        if (spiritId == null || spiritId.isBlank()) {
            return false;
        }
        refreshNpcPools(false);
        String normalized = spiritId.trim().toLowerCase(Locale.ROOT);
        Set<String> set = globalSpiritSet;
        return set != null && set.contains(normalized);
    }

    public static String resolveSpawnableSpiritId(String descriptor) {
        if (descriptor == null || descriptor.isBlank()) {
            return null;
        }
        refreshNpcPools(false);
        Map<String, String> map = globalSpiritByKey;
        if (map == null || map.isEmpty()) {
            return null;
        }
        return map.get(descriptor.trim().toLowerCase(Locale.ROOT));
    }

    public static String getSpiritAssignedColor(String spiritId) {
        if (spiritId == null || spiritId.isBlank()) {
            return null;
        }
        refreshNpcPools(false);
        Map<String, String> map = spiritColorMap;
        if (map == null || map.isEmpty()) {
            return null;
        }
        return map.get(spiritId.trim().toLowerCase(Locale.ROOT));
    }

    public static String resolveSpiritColor(String spiritId) {
        if (spiritId == null || spiritId.isBlank()) {
            return null;
        }
        String explicit = resolveExplicitSpiritColor(spiritId);
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }
        refreshNpcPools(false);
        String normalized = spiritId.trim().toLowerCase(Locale.ROOT);
        Map<String, String> map = spiritColorMap;
        if (map != null && !map.isEmpty()) {
            String assigned = map.get(normalized);
            if (assigned != null && !assigned.isBlank()) {
                return assigned;
            }
        }
        List<String> colors = knownColors;
        if (colors == null || colors.isEmpty()) {
            return null;
        }
        String ruleColor = resolveColorForSpirit(normalized, colors);
        if (ruleColor != null && !ruleColor.isBlank()) {
            return ruleColor.toLowerCase(Locale.ROOT);
        }
        int idx = Math.floorMod(normalized.hashCode(), colors.size());
        return colors.get(idx);
    }

    public static boolean isCoreColor(String color) {
        if (color == null || color.isBlank()) {
            return false;
        }
        List<String> cores = coreColors;
        if (cores == null || cores.isEmpty()) {
            return false;
        }
        String normalized = color.trim().toLowerCase(Locale.ROOT);
        for (String entry : cores) {
            if (entry != null && entry.equalsIgnoreCase(normalized)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isCoreSpirit(String spiritId) {
        String color = resolveSpiritColor(spiritId);
        return isCoreColor(color);
    }

    private static String resolveExplicitSpiritColor(String spiritId) {
        if (spiritId == null || spiritId.isBlank()) {
            return null;
        }
        Map<String, List<String>> map = colorSpiritMap;
        if (map == null || map.isEmpty()) {
            return null;
        }
        String normalized = spiritId.trim().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            if (entry == null) {
                continue;
            }
            String color = entry.getKey();
            if (color == null || color.isBlank()) {
                continue;
            }
            List<String> spirits = entry.getValue();
            if (spirits == null || spirits.isEmpty()) {
                continue;
            }
            for (String spirit : spirits) {
                if (spirit == null || spirit.isBlank()) {
                    continue;
                }
                if (spirit.trim().equalsIgnoreCase(normalized)) {
                    return color.trim().toLowerCase(Locale.ROOT);
                }
            }
        }
        return null;
    }

    public static Map<String, String> getSpiritColorMapSnapshot() {
        Map<String, String> map = spiritColorMap;
        if (map == null || map.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(map);
    }

    private static List<String> resolveExplicitColorPool(String color) {
        if (color == null || color.isBlank()) {
            return List.of();
        }
        List<String> pool = colorSpiritMap.get(color.toLowerCase(Locale.ROOT));
        return pool == null ? List.of() : pool;
    }

    private static List<String> filterByPool(List<String> explicit, List<String> zonePool) {
        if (explicit == null || explicit.isEmpty() || zonePool == null || zonePool.isEmpty()) {
            return List.of();
        }
        Set<String> zoneSet = new LinkedHashSet<>();
        for (String entry : zonePool) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            zoneSet.add(entry.trim().toLowerCase(Locale.ROOT));
        }
        List<String> filtered = new ArrayList<>();
        for (String entry : explicit) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String trimmed = entry.trim();
            if (zoneSet.contains(trimmed.toLowerCase(Locale.ROOT))) {
                filtered.add(trimmed);
            }
        }
        return filtered;
    }

    private static List<String> filterBySpiritColor(List<String> zonePool, String color) {
        if (zonePool == null || zonePool.isEmpty() || color == null || color.isBlank()) {
            return List.of();
        }
        Map<String, String> map = spiritColorMap;
        if (map == null || map.isEmpty()) {
            return List.of();
        }
        String target = color.trim().toLowerCase(Locale.ROOT);
        List<String> filtered = new ArrayList<>();
        for (String spirit : zonePool) {
            if (spirit == null || spirit.isBlank()) {
                continue;
            }
            String assigned = map.get(spirit.trim().toLowerCase(Locale.ROOT));
            if (assigned != null && assigned.equalsIgnoreCase(target)) {
                filtered.add(spirit.trim());
            }
        }
        return filtered;
    }

    private static List<String> resolveZonePool(String zoneName, String regionName, String biomeName) {
        refreshNpcPools(false);
        if (environmentSpiritMap == null || environmentSpiritMap.isEmpty()) {
            return globalSpiritPool == null ? List.of() : globalSpiritPool;
        }
        Set<String> pool = new LinkedHashSet<>();
        addZoneMatches(pool, zoneName);
        addZoneMatches(pool, regionName);
        addZoneMatches(pool, biomeName);
        if (pool.isEmpty()) {
            return globalSpiritPool == null ? List.of() : globalSpiritPool;
        }
        List<String> sorted = new ArrayList<>(pool);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(sorted);
    }

    private static void addZoneMatches(Set<String> pool, String token) {
        if (pool == null || token == null || token.isBlank()) {
            return;
        }
        String key = normalizeKey(token);
        if (key == null || key.isBlank()) {
            return;
        }
        List<String> exact = environmentSpiritMap.get(key);
        if (exact != null && !exact.isEmpty()) {
            pool.addAll(exact);
            return;
        }
        String compactKey = stripUnderscores(key);
        for (Map.Entry<String, List<String>> entry : environmentSpiritMap.entrySet()) {
            String envKey = entry.getKey();
            if (envKey == null || envKey.isBlank()) {
                continue;
            }
            if (key.contains(envKey) || envKey.contains(key)) {
                pool.addAll(entry.getValue());
                continue;
            }
            String compactEnv = stripUnderscores(envKey);
            if (!compactEnv.isEmpty() && !compactKey.isEmpty()) {
                if (compactEnv.contains(compactKey) || compactKey.contains(compactEnv)) {
                    pool.addAll(entry.getValue());
                }
            }
        }
    }

    private static void refreshNpcPools(boolean force) {
        long now = System.currentTimeMillis();
        if (!force) {
            if ((environmentSpiritMap != null && !environmentSpiritMap.isEmpty())
                    || (globalSpiritPool != null && !globalSpiritPool.isEmpty())) {
                return;
            }
            if (now - lastNpcPoolBuildAttempt < 10_000L) {
                return;
            }
        }
        synchronized (NPC_POOL_LOCK) {
            if (!force) {
                if ((environmentSpiritMap != null && !environmentSpiritMap.isEmpty())
                        || (globalSpiritPool != null && !globalSpiritPool.isEmpty())) {
                    return;
                }
                if (now - lastNpcPoolBuildAttempt < 10_000L) {
                    return;
                }
            }
            lastNpcPoolBuildAttempt = now;
            rebuildNpcPools();
        }
    }

    private static void rebuildNpcPools() {
        Map<String, Set<String>> envMap = new LinkedHashMap<>();
        Set<String> allRoles = new LinkedHashSet<>();

        try {
            IndexedLookupTableAssetMap<String, WorldNPCSpawn> assetMap = WorldNPCSpawn.getAssetMap();
            Map<String, WorldNPCSpawn> raw = assetMap == null ? null : assetMap.getAssetMap();
            if (raw != null) {
                for (WorldNPCSpawn spawn : raw.values()) {
                    if (spawn == null) {
                        continue;
                    }
                    List<String> roleIds = extractRoleIds(spawn.getNPCs());
                    if (roleIds.isEmpty()) {
                        continue;
                    }
                    allRoles.addAll(roleIds);

                    String[] environments = spawn.getEnvironments();
                    if (environments != null && environments.length > 0) {
                        for (String env : environments) {
                            String envKey = normalizeKey(env);
                            if (envKey == null || envKey.isBlank()) {
                                continue;
                            }
                            envMap.computeIfAbsent(envKey, k -> new LinkedHashSet<>()).addAll(roleIds);
                        }
                    }

                    String spawnId = normalizeKey(spawn.getId());
                    if (spawnId != null && !spawnId.isBlank()) {
                        envMap.computeIfAbsent(spawnId, k -> new LinkedHashSet<>()).addAll(roleIds);
                    }
                }
            }
        } catch (Throwable ignored) {
            // Asset map not ready yet.
        }

        environmentSpiritMap = freezeEnvironmentMap(envMap);
        globalSpiritPool = freezeList(allRoles);
        globalSpiritSet = freezeSet(allRoles);
        globalSpiritByKey = buildSpiritKeyMap(allRoles);
        spiritColorMap = buildSpiritColorMap(allRoles, knownColors);
    }

    private static List<String> extractRoleIds(RoleSpawnParameters[] params) {
        if (params == null || params.length == 0) {
            return List.of();
        }
        List<String> roles = new ArrayList<>();
        for (RoleSpawnParameters param : params) {
            if (param == null) {
                continue;
            }
            String id = param.getId();
            if (id == null || id.isBlank()) {
                continue;
            }
            roles.add(id.trim());
        }
        return roles;
    }

    private static Map<String, List<String>> freezeEnvironmentMap(Map<String, Set<String>> envMap) {
        if (envMap == null || envMap.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> frozen = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : envMap.entrySet()) {
            if (entry == null || entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            Set<String> roles = entry.getValue();
            if (roles == null || roles.isEmpty()) {
                continue;
            }
            List<String> sorted = new ArrayList<>(roles);
            sorted.sort(String.CASE_INSENSITIVE_ORDER);
            frozen.put(entry.getKey(), List.copyOf(sorted));
        }
        return Map.copyOf(frozen);
    }

    private static List<String> freezeList(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        List<String> sorted = new ArrayList<>(roles);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(sorted);
    }

    private static Map<String, String> buildSpiritColorMap(Set<String> roles, List<String> colors) {
        if (roles == null || roles.isEmpty() || colors == null || colors.isEmpty()) {
            return Map.of();
        }
        Map<String, String> map = new LinkedHashMap<>();
        int size = colors.size();
        for (String role : roles) {
            if (role == null || role.isBlank()) {
                continue;
            }
            String normalized = role.trim().toLowerCase(Locale.ROOT);
            String assigned = resolveColorForSpirit(normalized, colors);
            if (assigned == null || assigned.isBlank()) {
                int idx = Math.floorMod(normalized.hashCode(), size);
                assigned = colors.get(idx);
            }
            if (assigned != null && !assigned.isBlank()) {
                map.put(normalized, assigned.toLowerCase(Locale.ROOT));
            }
        }
        return Map.copyOf(map);
    }

    private static String resolveColorForSpirit(String spiritId, List<String> colors) {
        if (spiritId == null || spiritId.isBlank() || colors == null || colors.isEmpty()) {
            return null;
        }
        String key = spiritId.toLowerCase(Locale.ROOT);
        for (String[] rule : SPIRIT_COLOR_RULES) {
            if (rule == null || rule.length < 2) {
                continue;
            }
            String color = rule[0];
            if (!hasColor(colors, color)) {
                continue;
            }
            for (int i = 1; i < rule.length; i++) {
                String token = rule[i];
                if (token == null || token.isBlank()) {
                    continue;
                }
                if (key.contains(token)) {
                    return color;
                }
            }
        }
        return null;
    }

    private static boolean hasColor(List<String> colors, String color) {
        if (colors == null || colors.isEmpty() || color == null || color.isBlank()) {
            return false;
        }
        for (String entry : colors) {
            if (entry != null && entry.equalsIgnoreCase(color)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> freezeSet(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String role : roles) {
            if (role == null || role.isBlank()) {
                continue;
            }
            normalized.add(role.trim().toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(normalized);
    }

    private static Map<String, String> buildSpiritKeyMap(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Map.of();
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (String role : roles) {
            if (role == null || role.isBlank()) {
                continue;
            }
            String normalized = role.trim().toLowerCase(Locale.ROOT);
            map.putIfAbsent(normalized, role.trim());
        }
        return Map.copyOf(map);
    }

    private static List<GemColorRule> parseGemRules(String[] entries) {
        if (entries == null || entries.length == 0) {
            return List.of();
        }
        List<GemColorRule> rules = new ArrayList<>();
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String trimmed = entry.trim();
            if (trimmed.startsWith("#")) {
                continue;
            }
            String[] split = trimmed.split("=", 2);
            if (split.length < 2) {
                continue;
            }
            String token = split[0].trim().toLowerCase(Locale.ROOT);
            String color = split[1].trim().toLowerCase(Locale.ROOT);
            if (token.isEmpty() || color.isEmpty()) {
                continue;
            }
            rules.add(new GemColorRule(token, color));
        }
        rules.sort(Comparator.comparingInt((GemColorRule rule) -> rule.token.length()).reversed());
        return List.copyOf(rules);
    }

    private static Map<String, List<String>> parseSpiritPools(String[] entries) {
        if (entries == null || entries.length == 0) {
            return Map.of();
        }
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String trimmed = entry.trim();
            if (trimmed.startsWith("#")) {
                continue;
            }
            String[] split = trimmed.split("=", 2);
            if (split.length < 2) {
                continue;
            }
            String color = split[0].trim().toLowerCase(Locale.ROOT);
            if (color.isEmpty()) {
                continue;
            }
            String[] spiritsRaw = split[1].split(",");
            List<String> spirits = new ArrayList<>();
            for (String spirit : spiritsRaw) {
                if (spirit == null || spirit.isBlank()) {
                    continue;
                }
                spirits.add(spirit.trim());
            }
            map.put(color, List.copyOf(spirits));
        }
        return map;
    }

    private static List<String> parseCoreColors(String[] entries) {
        if (entries == null || entries.length == 0) {
            return List.of();
        }
        Set<String> colors = new LinkedHashSet<>();
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String normalized = entry.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isEmpty()) {
                colors.add(normalized);
            }
        }
        if (colors.isEmpty()) {
            return List.of();
        }
        return List.copyOf(colors);
    }

    private static List<String> extractColors(List<GemColorRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }
        Set<String> colors = new LinkedHashSet<>();
        for (GemColorRule rule : rules) {
            if (rule == null || rule.color == null || rule.color.isBlank()) {
                continue;
            }
            colors.add(rule.color.trim().toLowerCase(Locale.ROOT));
        }
        if (colors.isEmpty()) {
            return List.of();
        }
        return List.copyOf(colors);
    }

    private static String buildSeed(String descriptor, String color, String zoneName, String regionName, String biomeName) {
        StringBuilder sb = new StringBuilder();
        String desc = normalizeSeedToken(descriptor);
        String colorToken = normalizeSeedToken(color);
        String zoneToken = normalizeSeedToken(firstNonBlank(zoneName, regionName, biomeName));

        if (desc != null) {
            sb.append(desc);
        }
        if (colorToken != null) {
            if (sb.length() > 0) {
                sb.append('|');
            }
            sb.append(colorToken);
        }
        if (zoneToken != null) {
            if (sb.length() > 0) {
                sb.append('|');
            }
            sb.append(zoneToken);
        }
        if (sb.length() == 0) {
            return "unknown";
        }
        return sb.toString();
    }

    private static String normalizeSeedToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private static String firstNonBlank(String a, String b, String c) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        if (c != null && !c.isBlank()) return c;
        return null;
    }

    private static String normalizeKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String lower = raw.trim().toLowerCase(Locale.ROOT);
        String sanitized = lower.replaceAll("[^a-z0-9]+", "_");
        sanitized = sanitized.replaceAll("^_+", "").replaceAll("_+$", "");
        if (sanitized.isEmpty()) {
            return null;
        }
        return sanitized;
    }

    private static String stripUnderscores(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("_", "");
    }

    private static String sanitizeToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unknown";
        }
        String lower = raw.trim().toLowerCase(Locale.ROOT);
        String sanitized = lower.replaceAll("[^a-z0-9]+", "_");
        sanitized = sanitized.replaceAll("^_+", "").replaceAll("_+$", "");
        if (sanitized.isEmpty()) {
            return "unknown";
        }
        return sanitized;
    }
}
