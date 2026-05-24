package irai.mod.reforge.Config;

import static com.hypixel.hytale.codec.Codec.DOUBLE_ARRAY;
import static com.hypixel.hytale.codec.Codec.STRING_ARRAY;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Configuration for Lore sockets, leveling, and feeding.
 */
@SuppressWarnings("removal")
public class LoreConfig implements ConfigDefaultInjector {

    public static final BuilderCodec<LoreConfig> CODEC = BuilderCodec.<LoreConfig>builder(LoreConfig.class, LoreConfig::new)
            .append(
                    new KeyedCodec<>("LORE_SOCKET_CHEST_CHANCE", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.chestLoreSocketChance = v[0]; },
                    cfg -> new double[]{cfg.chestLoreSocketChance}
            ).add()
            .append(
                    new KeyedCodec<>("LORE_SOCKET_DROP_CHANCE", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.dropLoreSocketChance = v[0]; },
                    cfg -> new double[]{cfg.dropLoreSocketChance}
            ).add()
            .append(
                    new KeyedCodec<>("LORE_SOCKET_MIN", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.minLoreSockets = (int) Math.round(v[0]); },
                    cfg -> new double[]{cfg.minLoreSockets}
            ).add()
            .append(
                    new KeyedCodec<>("LORE_SOCKET_MAX", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.maxLoreSockets = (int) Math.round(v[0]); },
                    cfg -> new double[]{cfg.maxLoreSockets}
            ).add()
            .append(
                    new KeyedCodec<>("LORE_LEVEL_MAX", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.maxLevel = (int) Math.round(v[0]); },
                    cfg -> new double[]{cfg.maxLevel}
            ).add()
            .append(
                    new KeyedCodec<>("LORE_XP_PER_PROC", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.xpPerProc = (int) Math.round(v[0]); },
                    cfg -> new double[]{cfg.xpPerProc}
            ).add()
            .append(
                    new KeyedCodec<>("LORE_BASE_XP_PER_LEVEL", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.baseXpPerLevel = (int) Math.round(v[0]); },
                    cfg -> new double[]{cfg.baseXpPerLevel}
            ).add()
            .append(
                    new KeyedCodec<>("LORE_XP_GROWTH_PER_LEVEL", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.xpGrowthPerLevel = v[0]; },
                    cfg -> new double[]{cfg.xpGrowthPerLevel}
            ).add()
            .append(
                    new KeyedCodec<>("LORE_FEED_INTERVAL", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.feedInterval = (int) Math.round(v[0]); },
                    cfg -> new double[]{cfg.feedInterval}
            ).add()
            .append(
                    new KeyedCodec<>("LORE_FEED_BASE", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.feedBase = (int) Math.round(v[0]); },
                    cfg -> new double[]{cfg.feedBase}
            ).add()
            .append(
                    new KeyedCodec<>("LORE_FEED_MULTIPLIER", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.feedMultiplier = (int) Math.round(v[0]); },
                    cfg -> new double[]{cfg.feedMultiplier}
            ).add()
            .append(
                    new KeyedCodec<>("LORE_BLEED_MAX_HP_PCT_PER_TICK", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.setBleedMaxHpPctPerTick(v[0]); },
                    cfg -> new double[]{cfg.getBleedMaxHpPctPerTick()}
            ).add()
            .append(
                    new KeyedCodec<>("LORE_BLEED_RAMP_PER_TICK", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.setBleedRampPerTick(v[0]); },
                    cfg -> new double[]{cfg.getBleedRampPerTick()}
            ).add()
            .append(
                    new KeyedCodec<>("LORE_BLEED_WEAPON_BASE_CAP_PCT", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.setBleedWeaponBaseCapPct(v[0]); },
                    cfg -> new double[]{cfg.getBleedWeaponBaseCapPct()}
            ).add()
            .append(
                    new KeyedCodec<>("LORE_BLEED_TOTAL_CURRENT_HP_PCT", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.setBleedTotalCurrentHpPct(v[0]); },
                    cfg -> new double[]{cfg.getBleedTotalCurrentHpPct()}
            ).add()
            .append(
                    new KeyedCodec<>("LORE_BLEED_WEAPON_REFERENCE_BASE", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.setBleedWeaponReferenceBase(v[0]); },
                    cfg -> new double[]{cfg.getBleedWeaponReferenceBase()}
            ).add()
            .append(
                    new KeyedCodec<>("LORE_BLEED_WEAPON_SCALE_MIN", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.setBleedWeaponScaleMin(v[0]); },
                    cfg -> new double[]{cfg.getBleedWeaponScaleMin()}
            ).add()
            .append(
                    new KeyedCodec<>("LORE_BLEED_WEAPON_SCALE_MAX", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.setBleedWeaponScaleMax(v[0]); },
                    cfg -> new double[]{cfg.getBleedWeaponScaleMax()}
            ).add()
            .append(
                    // Legacy fallback key for older bleed-only configs.
                    new KeyedCodec<>("LORE_BLEED_REAPPLY_STEP", DOUBLE_ARRAY),
                    (cfg, v) -> { if (v != null && v.length > 0) cfg.setLegacyBleedBossReapplyStepFallback((int) Math.round(v[0])); },
                    cfg -> cfg.getStatusBossReapplyRules().length == 0
                            ? new double[]{cfg.getLegacyBleedBossReapplyStepFallback()}
                            : null
            ).add()
            .append(
                    // Legacy fallback key for older bleed-only configs.
                    new KeyedCodec<>("LORE_BLEED_REAPPLY_PATTERN", STRING_ARRAY),
                    (cfg, v) -> cfg.setLegacyBleedBossReapplyPatternFallback(firstString(v, "LINEAR")),
                    cfg -> cfg.getStatusBossReapplyRules().length == 0
                            ? new String[]{cfg.getLegacyBleedBossReapplyPatternFallback()}
                            : null
            ).add()
            .append(
                    new KeyedCodec<>("LORE_STATUS_REAPPLY_RULES", STRING_ARRAY),
                    (cfg, v) -> cfg.setStatusBossReapplyRules(v),
                    LoreConfig::getStatusBossReapplyRules
            ).add()
            .append(
                    new KeyedCodec<>("LORE_FEED_ITEM_IDS", STRING_ARRAY),
                    (cfg, v) -> cfg.feedItemIds = v,
                    cfg -> cfg.feedItemIds == null ? new String[0] : cfg.feedItemIds
            ).add()
            .append(
                    new KeyedCodec<>("LORE_CLEAR_ITEM_IDS", STRING_ARRAY),
                    (cfg, v) -> cfg.clearItemIds = v,
                    cfg -> cfg.clearItemIds == null ? new String[0] : cfg.clearItemIds
            ).add()
            .append(
                    new KeyedCodec<>("LORE_NPC_STATUS_RESISTANCES", STRING_ARRAY),
                    (cfg, v) -> cfg.setNpcStatusResistanceEntries(v),
                    LoreConfig::getNpcStatusResistanceEntries
            ).add()
            .append(
                    new KeyedCodec<>("LORE_STATUS_COUNTER_NPC_IDS", STRING_ARRAY),
                    (cfg, v) -> cfg.setStatusBossCounterNpcIds(v),
                    LoreConfig::getStatusBossCounterNpcIds
            ).add()
            .append(
                    new KeyedCodec<>("LORE_BLEED_COUNTER_NPC_IDS", STRING_ARRAY),
                    (cfg, v) -> cfg.setBleedBossCounterNpcIds(v),
                    cfg -> cfg.getStatusBossCounterNpcIds().length == 0
                            && cfg.getBleedBossCounterNpcIds().length > 0
                            ? cfg.getBleedBossCounterNpcIds()
                            : null
            ).add()
            .build();

    private double chestLoreSocketChance = 0.0d;
    private double dropLoreSocketChance = 0.0d;
    private int minLoreSockets = 1;
    private int maxLoreSockets = 3;

    private int maxLevel = 30;
    private int xpPerProc = 1;
    private int baseXpPerLevel = 10;
    private double xpGrowthPerLevel = 2.0d;

    private int feedInterval = 3;
    private int feedBase = 1;
    private int feedMultiplier = 2;
    private double bleedMaxHpPctPerTick = 0.005d;
    private double bleedRampPerTick = 0.08d;
    private double bleedWeaponBaseCapPct = 0.0d;
    private double bleedTotalCurrentHpPct = 0.0005d;
    private double bleedWeaponReferenceBase = 200.0d;
    private double bleedWeaponScaleMin = 0.75d;
    private double bleedWeaponScaleMax = 1.50d;
    /**
     * Legacy fallback used only when the newer status-based boss reapply settings are absent.
     */
    private int legacyBleedBossReapplyStepFallback = 5;
    /**
     * Legacy fallback used only when the newer status-based boss reapply settings are absent.
     */
    private String legacyBleedBossReapplyPatternFallback = "LINEAR";
    /**
     * Entry format: {@code bleed=5, FIBONACCI} or {@code *=4, LINEAR}.
     * Wildcard applies when a status-specific rule is not present.
     */
    private String[] statusBossReapplyRules = new String[0];
    private String[] feedItemIds = new String[]{"Ingredient_Resonant_Essence", "Ingredient_Ghastly_Essence"};
    private String[] clearItemIds = new String[]{"Ingredient_Ghastly_Essence"};
    /**
     * Entry format: {@code NPC_ID|bleed=0.50|burn=1.0|poison=0.25|freeze=0.75}
     * where values are resistance amounts from 0.0 to 1.0 and 1.0 means immune.
     */
    private String[] npcStatusResistanceEntries = new String[0];
    /**
     * Entry format: {@code NPC_ID|bleed|burn|poison} or {@code NPC_ID|*}.
     * Entries without ailment tokens default to all statuses.
     */
    private String[] statusBossCounterNpcIds = new String[0];
    private String[] bleedBossCounterNpcIds = new String[0];

    private transient volatile Map<String, StatusResistanceProfile> npcStatusResistanceCache;
    private transient volatile Map<String, StatusBossCounterProfile> statusBossCounterNpcCache;
    private transient volatile Map<String, StatusBossReapplyRule> statusBossReapplyRuleCache;
    private transient volatile Map<String, Boolean> bleedBossCounterNpcCache;

    public double getChestLoreSocketChance() { return chestLoreSocketChance; }
    public double getDropLoreSocketChance() { return dropLoreSocketChance; }
    public int getMinLoreSockets() { return minLoreSockets; }
    public int getMaxLoreSockets() { return maxLoreSockets; }
    public int getMaxLevel() { return maxLevel; }
    public int getXpPerProc() { return xpPerProc; }
    public int getBaseXpPerLevel() { return baseXpPerLevel; }
    public double getXpGrowthPerLevel() { return xpGrowthPerLevel; }
    public int getFeedInterval() { return feedInterval; }
    public int getFeedBase() { return feedBase; }
    public int getFeedMultiplier() { return feedMultiplier; }
    public double getBleedMaxHpPctPerTick() { return clamp(bleedMaxHpPctPerTick, 0.0d, 1.0d); }
    public double getBleedRampPerTick() { return Math.max(0.0d, bleedRampPerTick); }
    public double getBleedWeaponBaseCapPct() { return Math.max(0.0d, bleedWeaponBaseCapPct); }
    public double getBleedTotalCurrentHpPct() { return clamp(bleedTotalCurrentHpPct, 0.0d, 1.0d); }
    public double getBleedWeaponReferenceBase() { return Math.max(1.0d, bleedWeaponReferenceBase); }
    public double getBleedWeaponScaleMin() { return clamp(bleedWeaponScaleMin, 0.0d, Math.max(0.0d, bleedWeaponScaleMax)); }
    public double getBleedWeaponScaleMax() { return Math.max(getBleedWeaponScaleMin(), bleedWeaponScaleMax); }
    public int getLegacyBleedBossReapplyStepFallback() { return Math.max(1, legacyBleedBossReapplyStepFallback); }
    public String[] getStatusBossReapplyRules() {
        return statusBossReapplyRules == null ? new String[0] : statusBossReapplyRules;
    }
    public int getStatusBossReapplyStep(String ailmentKey) {
        String normalizedAilment = normalizeStatusKey(ailmentKey);
        if (normalizedAilment == null) {
            return getLegacyBleedBossReapplyStepFallback();
        }
        Map<String, StatusBossReapplyRule> rules = getStatusBossReapplyRulesMap();
        StatusBossReapplyRule specific = rules.get(normalizedAilment);
        if (specific != null) {
            return specific.step();
        }
        StatusBossReapplyRule wildcard = rules.get("*");
        if (wildcard != null) {
            return wildcard.step();
        }
        return getLegacyBleedBossReapplyStepFallback();
    }
    public String getLegacyBleedBossReapplyPatternFallback() {
        return normalizeBleedBossReapplyPattern(legacyBleedBossReapplyPatternFallback);
    }
    public String getStatusBossReapplyPattern(String ailmentKey) {
        String normalizedAilment = normalizeStatusKey(ailmentKey);
        if (normalizedAilment == null) {
            return getLegacyBleedBossReapplyPatternFallback();
        }
        Map<String, StatusBossReapplyRule> rules = getStatusBossReapplyRulesMap();
        StatusBossReapplyRule specific = rules.get(normalizedAilment);
        if (specific != null && specific.pattern() != null) {
            return specific.pattern();
        }
        StatusBossReapplyRule wildcard = rules.get("*");
        if (wildcard != null && wildcard.pattern() != null) {
            return wildcard.pattern();
        }
        return getLegacyBleedBossReapplyPatternFallback();
    }
    public String[] getFeedItemIds() { return feedItemIds; }
    public String[] getClearItemIds() { return clearItemIds; }
    public String[] getNpcStatusResistanceEntries() {
        return npcStatusResistanceEntries == null ? new String[0] : npcStatusResistanceEntries;
    }
    public String[] getStatusBossCounterNpcIds() {
        return statusBossCounterNpcIds == null ? new String[0] : statusBossCounterNpcIds;
    }
    public String[] getBleedBossCounterNpcIds() {
        return bleedBossCounterNpcIds == null ? new String[0] : bleedBossCounterNpcIds;
    }

    public boolean isBleedBossCounterNpc(String npcId) {
        return isStatusBossCounterNpc(npcId, "bleed");
    }

    public boolean isStatusBossCounterNpc(String npcId, String ailmentKey) {
        String normalizedNpcId = normalizeKey(npcId);
        String normalizedAilment = normalizeStatusKey(ailmentKey);
        if (normalizedAilment == null) {
            return false;
        }
        boolean enabled = false;

        Map<String, StatusBossCounterProfile> profiles = getStatusBossCounterNpcProfiles();
        StatusBossCounterProfile wildcardProfile = profiles.get("*");
        if (wildcardProfile != null) {
            enabled = wildcardProfile.matches(normalizedAilment);
        }
        if (normalizedNpcId != null) {
            StatusBossCounterProfile profile = profiles.get(normalizedNpcId);
            if (profile != null) {
                enabled = enabled || profile.matches(normalizedAilment);
            }
        }

        if ("bleed".equals(normalizedAilment)) {
            Map<String, Boolean> legacyIds = getBleedBossCounterNpcCache();
            if (legacyIds.containsKey("*")) {
                enabled = true;
            } else if (normalizedNpcId != null && legacyIds.containsKey(normalizedNpcId)) {
                enabled = true;
            }
        }
        return enabled;
    }

    public double getNpcStatusResistance(String npcId, String ailmentKey) {
        String normalizedNpcId = normalizeKey(npcId);
        String normalizedAilment = normalizeStatusKey(ailmentKey);
        if (normalizedAilment == null) {
            return 0.0d;
        }
        Map<String, StatusResistanceProfile> profiles = getNpcStatusResistanceProfiles();
        double resistance = 0.0d;
        StatusResistanceProfile wildcard = profiles.get("*");
        if (wildcard != null) {
            resistance = wildcard.get(normalizedAilment);
        }
        if (normalizedNpcId == null) {
            return clamp(resistance, 0.0d, 1.0d);
        }
        StatusResistanceProfile profile = profiles.get(normalizedNpcId);
        if (profile != null) {
            resistance = Math.max(resistance, profile.get(normalizedAilment));
        }
        return clamp(resistance, 0.0d, 1.0d);
    }

    public void setChestLoreSocketChance(double v) { this.chestLoreSocketChance = v; }
    public void setDropLoreSocketChance(double v) { this.dropLoreSocketChance = v; }
    public void setMinLoreSockets(int v) { this.minLoreSockets = Math.max(0, v); }
    public void setMaxLoreSockets(int v) { this.maxLoreSockets = Math.max(this.minLoreSockets, v); }
    public void setMaxLevel(int v) { this.maxLevel = Math.max(1, v); }
    public void setXpPerProc(int v) { this.xpPerProc = Math.max(1, v); }
    public void setBaseXpPerLevel(int v) { this.baseXpPerLevel = Math.max(1, v); }
    public void setXpGrowthPerLevel(double v) { this.xpGrowthPerLevel = Math.max(0.0d, v); }
    public void setFeedInterval(int v) { this.feedInterval = Math.max(1, v); }
    public void setFeedBase(int v) { this.feedBase = Math.max(0, v); }
    public void setFeedMultiplier(int v) { this.feedMultiplier = Math.max(1, v); }
    public void setBleedMaxHpPctPerTick(double v) { this.bleedMaxHpPctPerTick = clamp(v, 0.0d, 1.0d); }
    public void setBleedRampPerTick(double v) { this.bleedRampPerTick = Math.max(0.0d, v); }
    public void setBleedWeaponBaseCapPct(double v) { this.bleedWeaponBaseCapPct = Math.max(0.0d, v); }
    public void setBleedTotalCurrentHpPct(double v) { this.bleedTotalCurrentHpPct = clamp(v, 0.0d, 1.0d); }
    public void setBleedWeaponReferenceBase(double v) { this.bleedWeaponReferenceBase = Math.max(1.0d, v); }
    public void setBleedWeaponScaleMin(double v) { this.bleedWeaponScaleMin = Math.max(0.0d, v); }
    public void setBleedWeaponScaleMax(double v) { this.bleedWeaponScaleMax = Math.max(0.0d, v); }
    public void setLegacyBleedBossReapplyStepFallback(int v) {
        this.legacyBleedBossReapplyStepFallback = Math.max(1, v);
    }
    public void setLegacyBleedBossReapplyPatternFallback(String v) {
        this.legacyBleedBossReapplyPatternFallback = normalizeBleedBossReapplyPattern(v);
    }
    public void setStatusBossReapplyRules(String[] v) {
        this.statusBossReapplyRules = v == null ? new String[0] : v;
        this.statusBossReapplyRuleCache = null;
    }
    public void setFeedItemIds(String[] v) { this.feedItemIds = v; }
    public void setClearItemIds(String[] v) { this.clearItemIds = v; }
    public void setNpcStatusResistanceEntries(String[] v) {
        this.npcStatusResistanceEntries = v == null ? new String[0] : v;
        this.npcStatusResistanceCache = null;
    }
    public void setStatusBossCounterNpcIds(String[] v) {
        this.statusBossCounterNpcIds = v == null ? new String[0] : v;
        this.statusBossCounterNpcCache = null;
    }
    public void setBleedBossCounterNpcIds(String[] v) {
        this.bleedBossCounterNpcIds = v == null ? new String[0] : v;
        this.bleedBossCounterNpcCache = null;
    }

    @Override
    public boolean injectMissingDefaults() {
        LoreConfig defaults = new LoreConfig();
        boolean changed = false;

        String[] mergedFeedItemIds = ConfigMergeUtils.mergeUniqueValues(feedItemIds, defaults.feedItemIds);
        if (!Arrays.equals(feedItemIds, mergedFeedItemIds)) {
            this.feedItemIds = mergedFeedItemIds;
            changed = true;
        }

        String[] mergedClearItemIds = ConfigMergeUtils.mergeUniqueValues(clearItemIds, defaults.clearItemIds);
        if (!Arrays.equals(clearItemIds, mergedClearItemIds)) {
            this.clearItemIds = mergedClearItemIds;
            changed = true;
        }

        return changed;
    }

    private Map<String, StatusResistanceProfile> getNpcStatusResistanceProfiles() {
        Map<String, StatusResistanceProfile> cached = npcStatusResistanceCache;
        if (cached != null) {
            return cached;
        }

        Map<String, StatusResistanceProfile> parsed = new LinkedHashMap<>();
        for (String raw : getNpcStatusResistanceEntries()) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String[] parts = raw.split("\\|");
            if (parts.length == 0) {
                continue;
            }
            String npcId = normalizeKey(parts[0]);
            if (npcId == null) {
                continue;
            }
            StatusResistanceProfile profile = parsed.computeIfAbsent(npcId, ignored -> new StatusResistanceProfile());
            for (int i = 1; i < parts.length; i++) {
                String token = parts[i] == null ? "" : parts[i].trim();
                if (token.isEmpty()) {
                    continue;
                }
                int equalsIndex = token.indexOf('=');
                if (equalsIndex <= 0 || equalsIndex >= token.length() - 1) {
                    continue;
                }
                String key = normalizeStatusKey(token.substring(0, equalsIndex));
                if (key == null) {
                    continue;
                }
                double resistance = parseDouble(token.substring(equalsIndex + 1), 0.0d);
                profile.put(key, resistance);
            }
        }

        cached = Collections.unmodifiableMap(parsed);
        npcStatusResistanceCache = cached;
        return cached;
    }

    private Map<String, StatusBossCounterProfile> getStatusBossCounterNpcProfiles() {
        Map<String, StatusBossCounterProfile> cached = statusBossCounterNpcCache;
        if (cached != null) {
            return cached;
        }

        Map<String, StatusBossCounterProfile> parsed = new LinkedHashMap<>();
        for (String raw : getStatusBossCounterNpcIds()) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String[] parts = raw.split("\\|");
            if (parts.length == 0) {
                continue;
            }
            String npcId = normalizeKey(parts[0]);
            if (npcId == null) {
                continue;
            }
            StatusBossCounterProfile profile =
                    parsed.computeIfAbsent(npcId, ignored -> new StatusBossCounterProfile());
            if (parts.length == 1) {
                profile.put("*");
                continue;
            }
            for (int i = 1; i < parts.length; i++) {
                String token = parts[i] == null ? "" : parts[i].trim();
                if (token.isEmpty()) {
                    continue;
                }
                String key = normalizeStatusKey(token);
                if (key != null) {
                    profile.put(key);
                }
            }
        }

        cached = Collections.unmodifiableMap(parsed);
        statusBossCounterNpcCache = cached;
        return cached;
    }

    private Map<String, StatusBossReapplyRule> getStatusBossReapplyRulesMap() {
        Map<String, StatusBossReapplyRule> cached = statusBossReapplyRuleCache;
        if (cached != null) {
            return cached;
        }

        Map<String, StatusBossReapplyRule> parsed = new LinkedHashMap<>();
        for (String raw : getStatusBossReapplyRules()) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String[] parts = raw.split(",");
            if (parts.length == 0) {
                continue;
            }
            String head = parts[0] == null ? "" : parts[0].trim();
            int equalsIndex = head.indexOf('=');
            if (equalsIndex <= 0 || equalsIndex >= head.length() - 1) {
                continue;
            }
            String key = normalizeStatusKey(head.substring(0, equalsIndex));
            if (key == null) {
                continue;
            }
            int step = Math.max(1, (int) Math.round(parseDouble(head.substring(equalsIndex + 1), 1.0d)));
            String pattern = parts.length >= 2
                    ? normalizeBleedBossReapplyPattern(parts[1])
                    : null;
            parsed.put(key, new StatusBossReapplyRule(step, pattern));
        }

        cached = Collections.unmodifiableMap(parsed);
        statusBossReapplyRuleCache = cached;
        return cached;
    }

    private Map<String, Boolean> getBleedBossCounterNpcCache() {
        Map<String, Boolean> cached = bleedBossCounterNpcCache;
        if (cached != null) {
            return cached;
        }

        Map<String, Boolean> parsed = new LinkedHashMap<>();
        for (String raw : getBleedBossCounterNpcIds()) {
            String normalized = normalizeKey(raw);
            if (normalized == null) {
                continue;
            }
            parsed.put(normalized, Boolean.TRUE);
        }
        cached = Collections.unmodifiableMap(parsed);
        bleedBossCounterNpcCache = cached;
        return cached;
    }

    private static String normalizeBleedBossReapplyPattern(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return "LINEAR";
        }
        String normalized = pattern.trim().toUpperCase(Locale.ROOT);
        if ("FIBONACCI".equals(normalized)) {
            return normalized;
        }
        return "LINEAR";
    }

    private static String firstString(String[] values, String fallback) {
        if (values == null || values.length == 0 || values[0] == null || values[0].isBlank()) {
            return fallback;
        }
        return values[0];
    }

    private static String normalizeKey(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private static String normalizeStatusKey(String value) {
        String normalized = normalizeKey(value);
        if (normalized == null) {
            return null;
        }
        return switch (normalized) {
            case "*", "all", "any" -> "*";
            case "bleed", "apply_bleed" -> "bleed";
            case "burn", "apply_burn", "fire" -> "burn";
            case "poison", "apply_poison", "toxic" -> "poison";
            case "freeze", "frozen", "apply_freeze" -> "freeze";
            case "shock", "apply_shock" -> "shock";
            case "slow", "slowness", "apply_slow" -> "slow";
            case "weak", "weakness", "apply_weakness" -> "weakness";
            case "blind", "blindness", "apply_blind" -> "blind";
            case "root", "apply_root" -> "root";
            case "stun", "apply_stun" -> "stun";
            case "fear", "apply_fear" -> "fear";
            case "drain", "drain_life", "life_drain", "lifedrain" -> "drain";
            default -> null;
        };
    }

    private static double parseDouble(String value, double fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class StatusResistanceProfile {
        private final Map<String, Double> values = new LinkedHashMap<>();

        private void put(String statusKey, double resistance) {
            if (statusKey == null) {
                return;
            }
            values.put(statusKey, clamp(resistance, 0.0d, 1.0d));
        }

        private double get(String statusKey) {
            Double specific = values.get(statusKey);
            if (specific != null) {
                return specific;
            }
            Double wildcard = values.get("*");
            return wildcard == null ? 0.0d : wildcard;
        }
    }

    private static final class StatusBossCounterProfile {
        private final Map<String, Boolean> ailments = new LinkedHashMap<>();

        private void put(String ailmentKey) {
            if (ailmentKey == null) {
                return;
            }
            ailments.put(ailmentKey, Boolean.TRUE);
        }

        private boolean matches(String ailmentKey) {
            if (ailmentKey == null) {
                return false;
            }
            return ailments.containsKey("*") || ailments.containsKey(ailmentKey);
        }
    }

    private static final class StatusBossReapplyRule {
        private final int step;
        private final String pattern;

        private StatusBossReapplyRule(int step, String pattern) {
            this.step = Math.max(1, step);
            this.pattern = pattern == null || pattern.isBlank()
                    ? null
                    : normalizeBleedBossReapplyPattern(pattern);
        }

        private int step() {
            return step;
        }

        private String pattern() {
            return pattern;
        }
    }
}
