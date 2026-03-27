package irai.mod.reforge.Lore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import irai.mod.reforge.Config.LoreMappingConfig;
import irai.mod.reforge.Lore.LoreGemRegistry;

/**
 * Registry for lore abilities (explicit config + procedural fallback).
 */
public final class LoreAbilityRegistry {
    private static final Map<String, LoreAbility> EXPLICIT = new ConcurrentHashMap<>();
    private static volatile LoreMappingConfig config = new LoreMappingConfig();

    private static final LoreTrigger[] PROCEDURAL_TRIGGERS = new LoreTrigger[] {
            LoreTrigger.ON_HIT,
            LoreTrigger.ON_KILL,
            LoreTrigger.ON_DAMAGED,
            LoreTrigger.ON_CRIT,
            LoreTrigger.ON_BLOCK,
            LoreTrigger.ON_NEAR_DEATH
    };

    private static final LoreTrigger[] UNIQUE_TRIGGER_POOL = new LoreTrigger[] {
            LoreTrigger.ON_HIT,
            LoreTrigger.ON_CRIT,
            LoreTrigger.ON_KILL,
            LoreTrigger.ON_DAMAGED,
            LoreTrigger.ON_BLOCK,
            LoreTrigger.ON_NEAR_DEATH,
            LoreTrigger.ON_SPRINT,
            LoreTrigger.ON_JUMP,
            LoreTrigger.ON_SNEAK,
            LoreTrigger.ON_HEAL,
            LoreTrigger.ON_STATUS_APPLY,
            LoreTrigger.ON_POTION_USE,
            LoreTrigger.ON_SKILL_USE
    };

    private static final LoreEffectType[] UNIQUE_EFFECT_POOL = new LoreEffectType[] {
            LoreEffectType.DAMAGE_TARGET,
            LoreEffectType.DAMAGE_ATTACKER,
            LoreEffectType.HEAL_SELF,
            LoreEffectType.HEAL_DEFENDER,
            LoreEffectType.HEAL_SELF_OVER_TIME,
            LoreEffectType.HEAL_AREA,
            LoreEffectType.HEAL_AREA_OVER_TIME,
            LoreEffectType.LIFESTEAL,
            LoreEffectType.APPLY_BURN,
            LoreEffectType.APPLY_FREEZE,
            LoreEffectType.APPLY_SHOCK,
            LoreEffectType.APPLY_BLEED,
            LoreEffectType.APPLY_POISON,
            LoreEffectType.APPLY_SLOW,
            LoreEffectType.APPLY_WEAKNESS,
            LoreEffectType.APPLY_BLIND,
            LoreEffectType.APPLY_ROOT,
            LoreEffectType.APPLY_STUN,
            LoreEffectType.APPLY_FEAR,
            LoreEffectType.APPLY_HASTE,
            LoreEffectType.APPLY_INVISIBLE,
            LoreEffectType.APPLY_SHIELD,
            LoreEffectType.DOUBLE_CAST,
            LoreEffectType.MULTI_HIT,
            LoreEffectType.CRIT_CHARGE,
            LoreEffectType.BERSERK,
            LoreEffectType.CHARGE_ATTACK,
            LoreEffectType.CAUSTIC_FINALE,
            LoreEffectType.SHRAPNEL_FINALE,
            LoreEffectType.BURN_FINALE,
            LoreEffectType.DRAIN_LIFE
    };

    private static volatile Map<String, AbilityProfile> UNIQUE_ABILITY_PROFILES = new ConcurrentHashMap<>();
    private static final Object HP_MAP_LOCK = new Object();
    private static volatile boolean hpMappingLoaded = false;
    private static volatile Map<String, String> npcAbilityOverrideBySpirit = new ConcurrentHashMap<>();
    private static final Pattern MAX_HEALTH_PATTERN = Pattern.compile("\"MaxHealth\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)");

    private static final String[] NPC_ABILITY_TYPES = new String[] {
            "BURST_DAMAGE","LIFESTEAL","APPLY_BLEED","APPLY_POISON","APPLY_BURN","CHAIN_LIGHTNING","VOID_STRIKE",
            "SHATTER","MANA_DRAIN","KNOCKBACK","MARK_TARGET","PROC_AURA_PULSE","CRIT_DAMAGE_AMP","EXECUTE",
            "EXPOSE_ARMOR","APPLY_SHRED","BLIND","STAGGER","DOUBLE_STRIKE_PROC","MANA_BURST","SPLINTER_SHRAPNEL",
            "FEAR","ECHOING_CRIT","SKILL_DAMAGE_AMP","COOLDOWN_REDUCTION","ECHO_CAST","MANA_REFUND",
            "BUFF_REFRESH","ELEMENTAL_INFUSE","PROC_HASTE","APPLY_WEAKNESS","DAMAGE_REDUCTION",
            "RETRIBUTION_DAMAGE","SPAWN_SHIELD_ORB","HEAL_ON_HIT","APPLY_ROOT","BLIND_ATTACKER","ESCAPE_PROC",
            "COUNTER_DEBUFF","RAGE_STACK","RESIST_BREAK","THORNS_REFLECT","BLOCK_DAMAGE_RETURN","STAMINA_RESTORE",
            "PARRY_WINDOW","STUN_ATTACKER","ARMOR_BUFF","RIPOSTE_STRIKE","COUNTER_BUFF_STACK","RESTORE_MANA",
            "DEFLECT_PROJECTILE","HASTE_BUFF","INVISIBLE_PROC","EVASION_STACK","COUNTER_DAMAGE","RESET_COOLDOWN",
            "MOMENTUM_CHARGE","SHADOW_STEP","STAMINA_REFUND","XP_SURGE","HEAL_BURST","DAMAGE_AMP_STACK",
            "LOOT_BONUS","EXECUTE_AURA","SPEED_BURST","BUFF_DURATION_EXTEND","SOUL_ABSORB","FEAR_NEARBY",
            "EXPLODE_CORPSE","SUMMON_SHADE","LAST_STAND_INVULN","DEATH_MARK_CLEANSE","BARRIER_BURST",
            "REVIVE_PROC","EXECUTE_IMMUNITY","VENGEANCE_BUFF","DRAIN_NEARBY","SCREAM_AOE_FEAR","SLOW_PULSE",
            "FADE","SPIRIT_ASSIGNMENT","LORE_XP_GRANT","BONUS_DROP_ROLL","ABSORB_FRAGMENT","MOB_KNOWLEDGE_BUFF",
            "ESSENCE_BONUS","SPEED_AMPLIFY","EVASION_UP","MOMENTUM_DAMAGE","BLUR","GROUND_TRAIL_EFFECT",
            "RAM_KNOCKBACK","STAMINA_REGEN","WHIRLWIND_AURA","AERIAL_STRIKE_AMP","FEATHER_FALL","SLAM_DAMAGE",
            "AIR_DODGE_UNLOCK","HOVER_PROC","GRAVITY_PULSE","PLATFORM_CLEAR","BACKSTAB_AMP","SILENCE_AURA",
            "FOOTSTEP_MUTE","CRIT_CHANCE_UP","VANISH","TRAP_PLANT_SPEED","PICKPOCKET_PROC","DETECT_HIDDEN",
            "OVERHEAL_SHIELD","HEAL_AMP","MANA_ON_HEAL","HEAL_PULSE_AOE","REGENERATION_STACK","CLEANSE_DEBUFF",
            "SURGE_OF_LIFE","POTION_AMP","POTION_MANA_BURST","HASTE_SURGE","SECOND_WIND","RESISTANCE_UP",
            "AURA_BURST","INSTANT_COOLDOWN_RESET","PROC_ECHO","XP_BONUS","COOLDOWN_CUT","MANA_FEED",
            "SYNERGY_TRIGGER","BUFF_AMPLIFY","STATUS_DURATION_EXTEND","IMMUNITY_WINDOW","COUNTER_STATUS",
            "BURST_ON_CLEANSE","STACK_EXPLOIT","CATALYST_DAMAGE"
    };

    private static final int SIGNATURE_MIN_HP = 40;
    private static final int SIGNATURE_FORCE_HP = 140;

    private static final SignatureAbility[] SIGNATURE_ABILITIES = new SignatureAbility[] {
            new SignatureAbility("SIGNATURE_VORTEXSTRIKE",
                    "tooltip.lore.signature.vortexstrike", "Vortexstrike",
                    LoreTrigger.ON_HIT, 0.06d, 4600L, LoreEffectType.VORTEXSTRIKE, 0.42d, 0.02d),
            new SignatureAbility("SIGNATURE_GROUNDSLAM",
                    "tooltip.lore.signature.groundslam", "Groundslam",
                    LoreTrigger.ON_HIT, 0.07d, 5200L, LoreEffectType.APPLY_STUN, 1.5d, 0.10d),
            new SignatureAbility("SIGNATURE_RAZORSTRIKE",
                    "tooltip.lore.signature.razorstrike", "Razorstrike",
                    LoreTrigger.ON_CRIT, 0.08d, 3800L, LoreEffectType.APPLY_BLEED, 2.2d, 0.12d),
            new SignatureAbility("SIGNATURE_WHIRLWIND",
                    "tooltip.lore.signature.whirlwind", "Whirlwind",
                    LoreTrigger.ON_HIT, 0.06d, 4800L, LoreEffectType.BERSERK, 0.35d, 0.02d),
            new SignatureAbility("SIGNATURE_VOLLEY",
                    "tooltip.lore.signature.volley", "Volley",
                    LoreTrigger.ON_HIT, 0.07d, 4200L, LoreEffectType.DOUBLE_CAST, 0.40d, 0.02d),
            new SignatureAbility("SIGNATURE_BIG_ARROW",
                    "tooltip.lore.signature.big_arrow", "Big Arrow",
                    LoreTrigger.ON_CRIT, 0.06d, 5000L, LoreEffectType.DAMAGE_TARGET, 3.4d, 0.18d),
            new SignatureAbility("SIGNATURE_OMNISLASH",
                    "tooltip.lore.signature.omnislash", "Omnislash",
                    LoreTrigger.ON_HIT, 0.05d, 180000L, LoreEffectType.OMNISLASH, 0.35d, 0.012d),
            new SignatureAbility("SIGNATURE_OCTASLASH",
                    "tooltip.lore.signature.octaslash", "Octaslash",
                    LoreTrigger.ON_HIT, 0.05d, 180000L, LoreEffectType.OCTASLASH, 0.32d, 0.010d),
            new SignatureAbility("SIGNATURE_PUMMEL",
                    "tooltip.lore.signature.pummel", "Pummel",
                    LoreTrigger.ON_HIT, 0.08d, 60000L, LoreEffectType.PUMMEL, 0.28d, 0.010d),
            new SignatureAbility("SIGNATURE_BLOOD_RUSH",
                    "tooltip.lore.signature.blood_rush", "Blood Rush",
                    LoreTrigger.ON_HIT, 0.06d, 120000L, LoreEffectType.BLOOD_RUSH, 0.30d, 0.012d),
            new SignatureAbility("SIGNATURE_CHARGE_ATTACK",
                    "tooltip.lore.signature.charge_attack", "Charge Attack",
                    LoreTrigger.ON_HIT, 0.07d, 7000L, LoreEffectType.CHARGE_ATTACK, 0.40d, 0.015d),
            new SignatureAbility("SIGNATURE_AREA_HEAL",
                    "tooltip.lore.signature.area_heal", "Area Heal",
                    LoreTrigger.ON_HIT, 0.06d, 8000L, LoreEffectType.HEAL_AREA, 2.0d, 0.10d)
            ,
            new SignatureAbility("SIGNATURE_CAUSTIC_FINALE",
                    "tooltip.lore.signature.caustic_finale", "Caustic Finale",
                    LoreTrigger.ON_KILL, 0.08d, 12000L, LoreEffectType.CAUSTIC_FINALE, 2.0d, 0.10d),
            new SignatureAbility("SIGNATURE_SHRAPNEL",
                    "tooltip.lore.signature.shrapnel_finale", "Shrapnel",
                    LoreTrigger.ON_KILL, 0.08d, 12000L, LoreEffectType.SHRAPNEL_FINALE, 2.0d, 0.10d),
            new SignatureAbility("SIGNATURE_BURN_FINALE",
                    "tooltip.lore.signature.burn_finale", "Inferno",
                    LoreTrigger.ON_KILL, 0.08d, 12000L, LoreEffectType.BURN_FINALE, 2.0d, 0.10d)
    };

    private static final Map<String, SignatureAbility> SIGNATURE_ABILITY_BY_ID = buildSignatureAbilityMap();

    private static final SignatureRule[] SIGNATURE_RULES = new SignatureRule[] {
            new SignatureRule("SIGNATURE_GROUNDSLAM", 80,
                    "golem", "rhino", "ogre", "troll", "giant", "behemoth", "brute",
                    "crusher", "magma", "stone", "rock", "earth", "minotaur", "ram"),
            new SignatureRule("SIGNATURE_VORTEXSTRIKE", 120,
                    "dragon", "wyrm", "drake", "hydra", "phoenix", "lich", "wraith", "specter", "boss", "guardian"),
            new SignatureRule("SIGNATURE_RAZORSTRIKE", 50,
                    "raptor", "wolf", "tiger", "panther", "lynx", "cat", "cougar", "cheetah",
                    "spider", "scorpion", "assassin", "stalker", "fang", "claw"),
            new SignatureRule("SIGNATURE_WHIRLWIND", 70,
                    "skeleton", "goblin", "bandit", "raider", "pirate", "soldier", "knight", "warrior",
                    "berserker", "duke", "guard"),
            new SignatureRule("SIGNATURE_VOLLEY", 40,
                    "archer", "bow", "ranger", "hunter", "sharpshooter", "marksman"),
            new SignatureRule("SIGNATURE_BIG_ARROW", 60,
                    "crossbow", "gunner", "rifle", "sniper", "bolt"),
            new SignatureRule("SIGNATURE_OMNISLASH", 160,
                    "ancient", "elder", "overlord", "warlord", "champion", "boss", "guardian"),
            new SignatureRule("SIGNATURE_OCTASLASH", 90,
                    "assassin", "duelist", "swordsman", "blademaster", "ronin"),
            new SignatureRule("SIGNATURE_PUMMEL", 100,
                    "brute", "ogre", "golem", "troll", "giant", "ram", "rhino"),
            new SignatureRule("SIGNATURE_BLOOD_RUSH", 110,
                    "berserker", "blood", "vampire", "cultist", "wraith")
    };

    private LoreAbilityRegistry() {}

    public static void initialize(LoreMappingConfig cfg) {
        if (cfg != null) {
            config = cfg;
        }
        reload();
    }

    public static void reload() {
        EXPLICIT.clear();
        String[] entries = config == null ? null : config.getAbilityEntries();
        if (entries == null || entries.length == 0) {
            return;
        }
        for (String raw : entries) {
            LoreAbility ability = parseEntry(raw);
            if (ability != null && ability.getSpiritId() != null && !ability.getSpiritId().isBlank()) {
                EXPLICIT.put(normalizeSpiritId(ability.getSpiritId()), ability);
            }
        }
    }

    public static LoreAbility getAbility(String spiritId) {
        if (spiritId == null || spiritId.isBlank()) {
            return null;
        }
        LoreAbility explicit = EXPLICIT.get(normalizeSpiritId(spiritId));
        if (explicit != null) {
            return explicit;
        }
        if (LoreGemRegistry.isSpawnableSpirit(spiritId)) {
            LoreAbility npcMapped = buildNpcMapped(spiritId);
            if (npcMapped != null) {
                return npcMapped;
            }
        }
        return buildProcedural(spiritId);
    }

    public static List<LoreAbility> getAllAbilities() {
        if (EXPLICIT.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(EXPLICIT.values());
    }

    private static LoreAbility parseEntry(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return null;
        }
        String[] split = trimmed.split("=", 2);
        if (split.length < 2) {
            return null;
        }
        String spiritId = split[0].trim();
        if (spiritId.isEmpty()) {
            return null;
        }
        String[] params = split[1].split(",");
        if (params.length == 1) {
            String token = params[0].trim();
            if (!token.isBlank()) {
                String signatureId = token.toUpperCase(Locale.ROOT);
                SignatureAbility signature = SIGNATURE_ABILITY_BY_ID.get(signatureId);
                if (signature != null) {
                    return signature.toLoreAbility(spiritId);
                }
            }
            return null;
        }
        if (params.length < 4) {
            return null;
        }

        LoreTrigger trigger = LoreTrigger.fromString(params[0], LoreTrigger.ON_HIT);
        double procChance = parseDouble(params[1], 0.10d);
        if (procChance > 1.0d) {
            procChance = procChance / 100.0d;
        }
        procChance = clamp01(procChance);

        long cooldownMs = (long) parseDouble(params[2], 2000d);
        LoreEffectType effectType = LoreEffectType.fromString(params[3], LoreEffectType.DAMAGE_TARGET);
        double baseValue = params.length > 4 ? parseDouble(params[4], 2.0d) : 2.0d;
        double perLevel = params.length > 5 ? parseDouble(params[5], 0.1d) : 0.1d;

        return new LoreAbility(spiritId, trigger, procChance, cooldownMs, effectType, baseValue, perLevel);
    }

    private static LoreAbility buildProcedural(String spiritId) {
        int hash = Math.abs(spiritId.hashCode());
        LoreTrigger trigger = PROCEDURAL_TRIGGERS[hash % PROCEDURAL_TRIGGERS.length];
        LoreEffectType effectType = pickEffectForTrigger(trigger, hash);

        double procChance = 0.05d + ((hash % 12) / 100.0d);
        long cooldownMs = 1500L + (hash % 4000);
        double baseValue = 2.0d + (hash % 6);
        double perLevel = 0.15d + ((hash % 5) / 100.0d);

        return new LoreAbility(spiritId, trigger, procChance, cooldownMs, effectType, baseValue, perLevel);
    }

    private static LoreAbility buildNpcMapped(String spiritId) {
        if (spiritId == null || spiritId.isBlank()) {
            return null;
        }
        LoreAbility wolfAbility = buildWolfPackAbility(spiritId);
        if (wolfAbility != null) {
            return wolfAbility;
        }
        String abilityId = pickNpcAbilityId(spiritId);
        if (abilityId == null) {
            return null;
        }
        SignatureAbility signature = SIGNATURE_ABILITY_BY_ID.get(abilityId);
        if (signature != null) {
            return signature.toLoreAbility(spiritId);
        }
        AbilityProfile profile = getUniqueAbilityProfile(abilityId);
        if (profile == null) {
            LoreTrigger trigger = mapTriggerForAbilityId(abilityId);
            LoreEffectType effectType = mapEffectForAbilityId(abilityId);
            AbilityTuning tuning = tuneAbility(effectType, trigger, abilityId);
            return new LoreAbility(spiritId, trigger, tuning.procChance, tuning.cooldownMs,
                    effectType, tuning.baseValue, tuning.perLevel);
        }
        return new LoreAbility(spiritId, profile.trigger, profile.procChance, profile.cooldownMs,
                profile.effectType, profile.baseValue, profile.perLevel);
    }

    private static LoreAbility buildWolfPackAbility(String spiritId) {
        if (!isWolfSpirit(spiritId)) {
            return null;
        }
        return new LoreAbility(
                spiritId,
                LoreTrigger.ON_HIT,
                0.12d,
                60000L,
                LoreEffectType.SUMMON_WOLF_PACK,
                1.0d,
                0.0d,
                "tooltip.lore.signature.wolfpack",
                "Wolf Pack"
        );
    }

    private static boolean isWolfSpirit(String spiritId) {
        if (spiritId == null || spiritId.isBlank()) {
            return false;
        }
        String lower = spiritId.toLowerCase(Locale.ROOT);
        return lower.contains("wolf");
    }

    private static String pickNpcAbilityId(String spiritId) {
        if (spiritId == null || spiritId.isBlank()) {
            return null;
        }
        ensureHpAbilityMappingLoaded();
        Map<String, String> overrides = npcAbilityOverrideBySpirit;
        if (overrides != null && !overrides.isEmpty()) {
            String mapped = overrides.get(spiritId.trim().toLowerCase(Locale.ROOT));
            if (mapped != null && !mapped.isBlank()) {
                return mapped;
            }
        }
        if (NPC_ABILITY_TYPES.length == 0) {
            return null;
        }
        int idx = Math.floorMod(spiritId.trim().toLowerCase(Locale.ROOT).hashCode(), NPC_ABILITY_TYPES.length);
        return NPC_ABILITY_TYPES[idx];
    }

    private static void ensureHpAbilityMappingLoaded() {
        if (hpMappingLoaded) {
            return;
        }
        synchronized (HP_MAP_LOCK) {
            if (hpMappingLoaded) {
                return;
            }
            Map<String, Integer> hpMap = loadNpcMaxHealthFromAssets();
            if (hpMap == null || hpMap.isEmpty()) {
                hpMappingLoaded = true;
                return;
            }
            npcAbilityOverrideBySpirit = buildHpBasedAbilityOverrides(hpMap);
            hpMappingLoaded = true;
        }
    }

    private static Map<String, Integer> loadNpcMaxHealthFromAssets() {
        List<Path> roots = new ArrayList<>();
        Path serverMods = Paths.get("server", "mods");
        if (Files.isDirectory(serverMods)) {
            roots.add(serverMods);
        }
        Path mods = Paths.get("mods");
        if (Files.isDirectory(mods)) {
            roots.add(mods);
        }
        if (roots.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> hpBySpirit = new ConcurrentHashMap<>();
        for (Path root : roots) {
            try {
                try (var stream = Files.walk(root)) {
                    stream.filter(Files::isRegularFile)
                            .filter(path -> path.toString().contains("Server") && path.toString().contains("NPC")
                                    && path.toString().contains("Roles") && path.toString().endsWith(".json"))
                            .forEach(path -> {
                                String roleId = path.getFileName().toString();
                                if (roleId.endsWith(".json")) {
                                    roleId = roleId.substring(0, roleId.length() - 5);
                                }
                                if (roleId.isBlank()) {
                                    return;
                                }
                                try {
                                    String text = Files.readString(path);
                                    Matcher matcher = MAX_HEALTH_PATTERN.matcher(text);
                                    if (!matcher.find()) {
                                        return;
                                    }
                                    double hpValue = Double.parseDouble(matcher.group(1));
                                    int hp = (int) Math.max(1, Math.round(hpValue));
                                    hpBySpirit.putIfAbsent(roleId.trim().toLowerCase(Locale.ROOT), hp);
                                } catch (Throwable ignored) {
                                    // Best effort.
                                }
                            });
                }
            } catch (Throwable ignored) {
                // Best effort.
            }
        }
        return Map.copyOf(hpBySpirit);
    }

    private static Map<String, String> buildHpBasedAbilityOverrides(Map<String, Integer> hpBySpirit) {
        if (hpBySpirit == null || hpBySpirit.isEmpty()) {
            return Map.of();
        }

        List<SpiritHp> spirits = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : hpBySpirit.entrySet()) {
            if (entry == null || entry.getKey() == null) {
                continue;
            }
            int hp = entry.getValue() == null ? 0 : entry.getValue();
            if (hp <= 0) {
                continue;
            }
            spirits.add(new SpiritHp(entry.getKey(), hp));
        }
        if (spirits.isEmpty()) {
            return Map.of();
        }
        spirits.sort(Comparator.comparingInt((SpiritHp s) -> s.hp).thenComparing(s -> s.spiritId));

        Map<Integer, List<String>> tierAbilities = buildAbilityTierMap();
        if (tierAbilities.isEmpty()) {
            return Map.of();
        }

        int total = spirits.size();
        int signatureCount = Math.max(1, (int) Math.round(total * 0.10d));
        int signatureStart = Math.max(0, total - signatureCount);
        int[] tierIndices = new int[6];
        Map<String, String> overrides = new ConcurrentHashMap<>();
        for (int i = 0; i < total; i++) {
            SpiritHp spirit = spirits.get(i);
            boolean topSignature = i >= signatureStart;
            String signatureId = pickSignatureAbilityId(spirit.spiritId, spirit.hp);
            if (signatureId == null && topSignature) {
                signatureId = pickSignatureByHash(spirit.spiritId);
            }
            if (signatureId != null && !signatureId.isBlank()) {
                overrides.put(spirit.spiritId, signatureId);
                continue;
            }

            int tier = resolveHpTier(i, total);
            List<String> pool = resolveTierPool(tierAbilities, tier);
            if (pool == null || pool.isEmpty()) {
                continue;
            }
            int idx = tierIndices[Math.min(tier, tierIndices.length - 1)]++;
            String abilityId = pool.get(Math.floorMod(idx, pool.size()));
            if (abilityId != null && !abilityId.isBlank()) {
                overrides.put(spirit.spiritId, abilityId);
            }
        }
        return Map.copyOf(overrides);
    }

    private static String pickSignatureByHash(String spiritId) {
        if (spiritId == null || spiritId.isBlank()) {
            return null;
        }
        String[] signatureIds = getSignatureAbilityIds();
        if (signatureIds.length == 0) {
            return null;
        }
        int idx = Math.floorMod(spiritId.toLowerCase(Locale.ROOT).hashCode(), signatureIds.length);
        return signatureIds[idx];
    }

    private static Map<Integer, List<String>> buildAbilityTierMap() {
        Map<Integer, List<String>> tiers = new ConcurrentHashMap<>();
        for (String abilityId : NPC_ABILITY_TYPES) {
            if (abilityId == null || abilityId.isBlank()) {
                continue;
            }
            AbilityProfile profile = getUniqueAbilityProfile(abilityId);
            LoreEffectType effectType = profile == null ? mapEffectForAbilityId(abilityId) : profile.effectType;
            int tier = effectTier(effectType);
            tiers.computeIfAbsent(tier, key -> new ArrayList<>()).add(abilityId);
        }
        for (List<String> list : tiers.values()) {
            list.sort(String.CASE_INSENSITIVE_ORDER);
        }
        return tiers;
    }

    private static int resolveHpTier(int index, int total) {
        if (total <= 0) {
            return 3;
        }
        double pct = (index + 1) / (double) total;
        if (pct <= 0.20d) {
            return 1;
        }
        if (pct <= 0.40d) {
            return 2;
        }
        if (pct <= 0.65d) {
            return 3;
        }
        if (pct <= 0.85d) {
            return 4;
        }
        return 5;
    }

    private static List<String> resolveTierPool(Map<Integer, List<String>> tiers, int tier) {
        if (tiers == null || tiers.isEmpty()) {
            return List.of();
        }
        List<String> pool = tiers.get(tier);
        if (pool != null && !pool.isEmpty()) {
            return pool;
        }
        for (int offset = 1; offset <= 4; offset++) {
            pool = tiers.get(tier - offset);
            if (pool != null && !pool.isEmpty()) {
                return pool;
            }
            pool = tiers.get(tier + offset);
            if (pool != null && !pool.isEmpty()) {
                return pool;
            }
        }
        return List.of();
    }

    private static String pickSignatureAbilityId(String spiritId, int hp) {
        if (spiritId == null || spiritId.isBlank()) {
            return null;
        }
        if (hp < SIGNATURE_MIN_HP) {
            return null;
        }
        String lower = spiritId.toLowerCase(Locale.ROOT);
        for (SignatureRule rule : SIGNATURE_RULES) {
            if (rule == null || rule.abilityId == null || rule.tokens == null) {
                continue;
            }
            if (hp < rule.minHp) {
                continue;
            }
            if (containsAnyToken(lower, rule.tokens)) {
                return rule.abilityId;
            }
        }
        if (hp < SIGNATURE_FORCE_HP) {
            return null;
        }
        String[] signatureIds = getSignatureAbilityIds();
        if (signatureIds.length == 0) {
            return null;
        }
        int idx = Math.floorMod(lower.hashCode(), signatureIds.length);
        return signatureIds[idx];
    }

    private static String[] getSignatureAbilityIds() {
        if (SIGNATURE_ABILITIES == null || SIGNATURE_ABILITIES.length == 0) {
            return new String[0];
        }
        String[] ids = new String[SIGNATURE_ABILITIES.length];
        int count = 0;
        for (SignatureAbility ability : SIGNATURE_ABILITIES) {
            if (ability == null || ability.id == null || ability.id.isBlank()) {
                continue;
            }
            ids[count++] = ability.id;
        }
        if (count == ids.length) {
            return ids;
        }
        String[] trimmed = new String[count];
        System.arraycopy(ids, 0, trimmed, 0, count);
        return trimmed;
    }

    private static boolean containsAnyToken(String value, String... tokens) {
        if (value == null || value.isBlank() || tokens == null || tokens.length == 0) {
            return false;
        }
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            if (value.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static int effectTier(LoreEffectType effectType) {
        if (effectType == null) {
            return 3;
        }
        return switch (effectType) {
            case HEAL_SELF, HEAL_DEFENDER, HEAL_SELF_OVER_TIME, HEAL_AREA, HEAL_AREA_OVER_TIME,
                    APPLY_SLOW, APPLY_WEAKNESS, APPLY_BLIND -> 1;
            case APPLY_HASTE, APPLY_SHIELD, APPLY_INVISIBLE, LIFESTEAL -> 2;
            case DAMAGE_TARGET, DAMAGE_ATTACKER, APPLY_BLEED, APPLY_POISON, DRAIN_LIFE -> 3;
            case APPLY_BURN, APPLY_SHOCK, APPLY_FREEZE, APPLY_ROOT -> 4;
            case APPLY_STUN, APPLY_FEAR, DOUBLE_CAST, MULTI_HIT, CRIT_CHARGE, BERSERK,
                    SUMMON_WOLF_PACK, CHARGE_ATTACK, OMNISLASH, OCTASLASH, VORTEXSTRIKE, PUMMEL, BLOOD_RUSH,
                    CAUSTIC_FINALE, SHRAPNEL_FINALE, BURN_FINALE -> 5;
        };
    }

    private static final class SpiritHp {
        private final String spiritId;
        private final int hp;

        private SpiritHp(String spiritId, int hp) {
            this.spiritId = spiritId;
            this.hp = hp;
        }
    }

    private static AbilityProfile getUniqueAbilityProfile(String abilityId) {
        if (abilityId == null || abilityId.isBlank()) {
            return null;
        }
        Map<String, AbilityProfile> map = UNIQUE_ABILITY_PROFILES;
        if (map == null || map.isEmpty()) {
            map = buildUniqueAbilityProfiles();
            UNIQUE_ABILITY_PROFILES = map;
        }
        return map.get(abilityId);
    }

    private static Map<String, AbilityProfile> buildUniqueAbilityProfiles() {
        if (NPC_ABILITY_TYPES.length == 0) {
            return Map.of();
        }
        List<AbilityPair> pairs = new ArrayList<>();
        for (LoreTrigger trigger : UNIQUE_TRIGGER_POOL) {
            if (trigger == null) {
                continue;
            }
            for (LoreEffectType effect : UNIQUE_EFFECT_POOL) {
                if (effect == null) {
                    continue;
                }
                pairs.add(new AbilityPair(effect, trigger));
            }
        }
        if (pairs.isEmpty()) {
            return Map.of();
        }
        Map<String, AbilityProfile> profiles = new ConcurrentHashMap<>();
        for (int i = 0; i < NPC_ABILITY_TYPES.length; i++) {
            String abilityId = NPC_ABILITY_TYPES[i];
            if (abilityId == null || abilityId.isBlank()) {
                continue;
            }
            AbilityPair pair = pairs.get(i % pairs.size());
            AbilityProfile profile = buildUniqueProfile(abilityId, pair.effectType, pair.trigger, i);
            profiles.put(abilityId, profile);
        }
        return Map.copyOf(profiles);
    }

    private static Map<String, SignatureAbility> buildSignatureAbilityMap() {
        if (SIGNATURE_ABILITIES == null || SIGNATURE_ABILITIES.length == 0) {
            return Map.of();
        }
        Map<String, SignatureAbility> map = new ConcurrentHashMap<>();
        for (SignatureAbility ability : SIGNATURE_ABILITIES) {
            if (ability == null || ability.id == null || ability.id.isBlank()) {
                continue;
            }
            map.put(ability.id, ability);
        }
        return Map.copyOf(map);
    }

    private static AbilityProfile buildUniqueProfile(String abilityId,
                                                     LoreEffectType effectType,
                                                     LoreTrigger trigger,
                                                     int index) {
        AbilityTuning base = tuneAbility(effectType, trigger, abilityId);
        int hash = Math.abs((abilityId + ":" + index).hashCode());
        double procChance = clamp01(base.procChance + ((hash % 9) - 4) * 0.004d);
        long cooldownMs = Math.max(900L, base.cooldownMs + (((hash % 11) - 5) * 180L));
        double baseValue = Math.max(0.1d, base.baseValue * (0.85d + ((hash % 7) * 0.05d)));
        double perLevel = Math.max(0.01d, base.perLevel * (0.85d + ((hash % 5) * 0.05d)));
        return new AbilityProfile(trigger, procChance, cooldownMs, effectType, baseValue, perLevel);
    }

    private static LoreTrigger mapTriggerForAbilityId(String abilityId) {
        String key = abilityId == null ? "" : abilityId.toUpperCase(Locale.ROOT);
        if (containsAny(key, "POTION")) {
            return LoreTrigger.ON_POTION_USE;
        }
        if (containsAny(key, "HEAL", "REGEN", "OVERHEAL")) {
            return LoreTrigger.ON_HEAL;
        }
        if (containsAny(key, "SNEAK", "STEALTH", "VANISH", "BACKSTAB", "PICKPOCKET")) {
            return LoreTrigger.ON_SNEAK;
        }
        if (containsAny(key, "SPRINT", "SPEED", "MOMENTUM", "DASH")) {
            return LoreTrigger.ON_SPRINT;
        }
        if (containsAny(key, "JUMP", "AERIAL", "AIR_", "SLAM", "LAND", "HOVER", "GRAVITY")) {
            return LoreTrigger.ON_JUMP;
        }
        if (containsAny(key, "BLOCK", "PARRY", "DEFLECT", "THORNS", "RIPOSTE", "COUNTER")) {
            return LoreTrigger.ON_BLOCK;
        }
        if (containsAny(key, "DODGE", "EVASION", "BLUR")) {
            return LoreTrigger.ON_BLOCK;
        }
        if (containsAny(key, "KILL", "EXECUTE", "SOUL", "LOOT", "ABSORB", "BONUS_DROP", "SHADE")) {
            return LoreTrigger.ON_KILL;
        }
        if (containsAny(key, "FIRST", "ASSIGNMENT", "SPIRIT_ASSIGNMENT")) {
            return LoreTrigger.ON_FIRST_KILL;
        }
        if (containsAny(key, "NEAR_DEATH", "LAST_STAND", "REVIVE", "SECOND_WIND", "VENGEANCE", "FADE")) {
            return LoreTrigger.ON_NEAR_DEATH;
        }
        if (containsAny(key, "STATUS", "DEBUFF", "BLEED", "POISON", "BURN", "FROST", "SHOCK",
                "SLOW", "WEAKNESS", "BLIND", "ROOT", "STUN", "FEAR")) {
            return LoreTrigger.ON_STATUS_APPLY;
        }
        if (containsAny(key, "SKILL")) {
            return LoreTrigger.ON_SKILL_USE;
        }
        if (containsAny(key, "CRIT")) {
            return LoreTrigger.ON_CRIT;
        }
        return LoreTrigger.ON_HIT;
    }

    private static LoreEffectType mapEffectForAbilityId(String abilityId) {
        String key = abilityId == null ? "" : abilityId.toUpperCase(Locale.ROOT);
        if (containsAny(key, "LIFESTEAL")) {
            return LoreEffectType.LIFESTEAL;
        }
        if (containsAny(key, "DOUBLE", "ECHO_CAST", "ECHO", "PROC_ECHO")) {
            return LoreEffectType.DOUBLE_CAST;
        }
        if (containsAny(key, "MULTI", "WHIRLWIND", "BURST_DAMAGE", "SPLINTER", "SHRAPNEL")) {
            return LoreEffectType.MULTI_HIT;
        }
        if (containsAny(key, "CRIT_DAMAGE", "CRIT_CHANCE", "ECHOING_CRIT")) {
            return LoreEffectType.CRIT_CHARGE;
        }
        if (containsAny(key, "BERSERK", "RAGE", "MOMENTUM_CHARGE", "VENGEANCE")) {
            return LoreEffectType.BERSERK;
        }
        if (containsAny(key, "HEAL", "REGEN", "OVERHEAL")) {
            return pickHealVariant(key);
        }
        if (containsAny(key, "SHIELD", "BARRIER", "WARD", "ARMOR_BUFF", "DAMAGE_REDUCTION", "RESIST")) {
            return LoreEffectType.APPLY_SHIELD;
        }
        if (containsAny(key, "INVISIBLE", "VANISH", "FADE")) {
            return LoreEffectType.APPLY_INVISIBLE;
        }
        if (containsAny(key, "HASTE", "SPEED", "MOMENTUM", "BLUR")) {
            return LoreEffectType.APPLY_HASTE;
        }
        if (containsAny(key, "ROOT")) {
            return LoreEffectType.APPLY_ROOT;
        }
        if (containsAny(key, "STUN", "STAGGER")) {
            return LoreEffectType.APPLY_STUN;
        }
        if (containsAny(key, "FEAR")) {
            return LoreEffectType.APPLY_FEAR;
        }
        if (containsAny(key, "BLIND")) {
            return LoreEffectType.APPLY_BLIND;
        }
        if (containsAny(key, "SLOW")) {
            return LoreEffectType.APPLY_SLOW;
        }
        if (containsAny(key, "WEAK", "SHRED")) {
            return LoreEffectType.APPLY_WEAKNESS;
        }
        if (containsAny(key, "BLEED")) {
            return LoreEffectType.APPLY_BLEED;
        }
        if (containsAny(key, "POISON")) {
            return LoreEffectType.APPLY_POISON;
        }
        if (containsAny(key, "BURN", "FIRE", "FLAME")) {
            return LoreEffectType.APPLY_BURN;
        }
        if (containsAny(key, "FROST", "FREEZE", "ICE", "SHATTER")) {
            return LoreEffectType.APPLY_FREEZE;
        }
        if (containsAny(key, "SHOCK", "LIGHTNING", "CHAIN_LIGHTNING")) {
            return LoreEffectType.APPLY_SHOCK;
        }
        if (containsAny(key, "THORNS", "REFLECT", "COUNTER_DAMAGE", "BLOCK_DAMAGE_RETURN")) {
            return LoreEffectType.DAMAGE_ATTACKER;
        }
        return LoreEffectType.DAMAGE_TARGET;
    }

    private static AbilityTuning tuneAbility(LoreEffectType effectType, LoreTrigger trigger, String abilityId) {
        double procChance = 0.10d;
        long cooldownMs = 2200L;
        double baseValue = 2.5d;
        double perLevel = 0.15d;

        switch (effectType) {
            case DOUBLE_CAST -> {
                procChance = 0.07d;
                cooldownMs = 3200L;
                baseValue = 0.45d;
                perLevel = 0.02d;
            }
            case MULTI_HIT -> {
                procChance = 0.08d;
                cooldownMs = 3000L;
                baseValue = 0.35d;
                perLevel = 0.02d;
            }
            case CRIT_CHARGE -> {
                procChance = 0.10d;
                cooldownMs = 2800L;
                baseValue = 0.40d;
                perLevel = 0.02d;
            }
            case BERSERK -> {
                procChance = 0.08d;
                cooldownMs = 4500L;
                baseValue = 0.30d;
                perLevel = 0.015d;
            }
            case CHARGE_ATTACK -> {
                procChance = 0.07d;
                cooldownMs = 6000L;
                baseValue = 0.40d;
                perLevel = 0.015d;
            }
            case APPLY_STUN, APPLY_FEAR, APPLY_ROOT -> {
                procChance = 0.08d;
                cooldownMs = 3500L;
                baseValue = 1.5d;
                perLevel = 0.10d;
            }
            case APPLY_SLOW, APPLY_WEAKNESS, APPLY_BLIND -> {
                procChance = 0.10d;
                cooldownMs = 2800L;
                baseValue = 2.0d;
                perLevel = 0.12d;
            }
            case APPLY_HASTE, APPLY_INVISIBLE, APPLY_SHIELD -> {
                procChance = 0.08d;
                cooldownMs = 4000L;
                baseValue = 1.5d;
                perLevel = 0.10d;
            }
            case HEAL_SELF, HEAL_DEFENDER -> {
                procChance = 0.10d;
                cooldownMs = 2600L;
                baseValue = 2.0d;
                perLevel = 0.12d;
            }
            case HEAL_SELF_OVER_TIME -> {
                procChance = 0.10d;
                cooldownMs = 3000L;
                baseValue = 2.4d;
                perLevel = 0.12d;
            }
            case HEAL_AREA -> {
                procChance = 0.09d;
                cooldownMs = 3200L;
                baseValue = 1.6d;
                perLevel = 0.10d;
            }
            case HEAL_AREA_OVER_TIME -> {
                procChance = 0.09d;
                cooldownMs = 3400L;
                baseValue = 2.0d;
                perLevel = 0.10d;
            }
            case LIFESTEAL -> {
                procChance = 0.10d;
                cooldownMs = 2600L;
                baseValue = 0.08d;
                perLevel = 0.005d;
            }
            case DAMAGE_ATTACKER -> {
                procChance = 0.08d;
                cooldownMs = 2400L;
                baseValue = 2.0d;
                perLevel = 0.12d;
            }
            case APPLY_BURN, APPLY_POISON, APPLY_BLEED, APPLY_FREEZE, APPLY_SHOCK -> {
                procChance = 0.11d;
                cooldownMs = 2600L;
                baseValue = 2.2d;
                perLevel = 0.13d;
            }
            case DRAIN_LIFE -> {
                procChance = 0.09d;
                cooldownMs = 3600L;
                baseValue = 2.0d;
                perLevel = 0.10d;
            }
            default -> {
                // Keep defaults
            }
        }

        if (trigger == LoreTrigger.ON_CRIT) {
            procChance = Math.min(0.20d, procChance + 0.03d);
        } else if (trigger == LoreTrigger.ON_KILL || trigger == LoreTrigger.ON_NEAR_DEATH) {
            cooldownMs = Math.max(3000L, cooldownMs);
            procChance = Math.max(0.08d, procChance - 0.01d);
        }

        return new AbilityTuning(procChance, cooldownMs, baseValue, perLevel);
    }

    private static final class SignatureAbility {
        private final String id;
        private final String nameKey;
        private final String nameFallback;
        private final LoreTrigger trigger;
        private final double procChance;
        private final long cooldownMs;
        private final LoreEffectType effectType;
        private final double baseValue;
        private final double perLevel;

        private SignatureAbility(String id,
                                 String nameKey,
                                 String nameFallback,
                                 LoreTrigger trigger,
                                 double procChance,
                                 long cooldownMs,
                                 LoreEffectType effectType,
                                 double baseValue,
                                 double perLevel) {
            this.id = id;
            this.nameKey = nameKey;
            this.nameFallback = nameFallback;
            this.trigger = trigger;
            this.procChance = procChance;
            this.cooldownMs = cooldownMs;
            this.effectType = effectType;
            this.baseValue = baseValue;
            this.perLevel = perLevel;
        }

        private LoreAbility toLoreAbility(String spiritId) {
            return new LoreAbility(spiritId, trigger, procChance, cooldownMs, effectType, baseValue, perLevel,
                    nameKey, nameFallback);
        }
    }

    private static final class SignatureRule {
        private final String abilityId;
        private final int minHp;
        private final String[] tokens;

        private SignatureRule(String abilityId, int minHp, String... tokens) {
            this.abilityId = abilityId;
            this.minHp = Math.max(0, minHp);
            this.tokens = tokens == null ? new String[0] : tokens;
        }
    }

    private static final class AbilityTuning {
        private final double procChance;
        private final long cooldownMs;
        private final double baseValue;
        private final double perLevel;

        private AbilityTuning(double procChance, long cooldownMs, double baseValue, double perLevel) {
            this.procChance = procChance;
            this.cooldownMs = cooldownMs;
            this.baseValue = baseValue;
            this.perLevel = perLevel;
        }
    }

    private static final class AbilityPair {
        private final LoreEffectType effectType;
        private final LoreTrigger trigger;

        private AbilityPair(LoreEffectType effectType, LoreTrigger trigger) {
            this.effectType = effectType;
            this.trigger = trigger;
        }
    }

    private static final class AbilityProfile {
        private final LoreTrigger trigger;
        private final double procChance;
        private final long cooldownMs;
        private final LoreEffectType effectType;
        private final double baseValue;
        private final double perLevel;

        private AbilityProfile(LoreTrigger trigger,
                               double procChance,
                               long cooldownMs,
                               LoreEffectType effectType,
                               double baseValue,
                               double perLevel) {
            this.trigger = trigger;
            this.procChance = procChance;
            this.cooldownMs = cooldownMs;
            this.effectType = effectType;
            this.baseValue = baseValue;
            this.perLevel = perLevel;
        }
    }

    private static boolean containsAny(String value, String... tokens) {
        if (value == null || value.isBlank() || tokens == null || tokens.length == 0) {
            return false;
        }
        String haystack = value.toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            if (haystack.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static LoreEffectType pickHealVariant(String key) {
        int selector = Math.abs((key == null ? "" : key).hashCode());
        int pick = selector % 4;
        return switch (pick) {
            case 0 -> LoreEffectType.HEAL_SELF;
            case 1 -> LoreEffectType.HEAL_SELF_OVER_TIME;
            case 2 -> LoreEffectType.HEAL_AREA;
            default -> LoreEffectType.HEAL_AREA_OVER_TIME;
        };
    }

    private static LoreEffectType pickEffectForTrigger(LoreTrigger trigger, int hash) {
        int selector = Math.abs(hash);
        return switch (trigger) {
            case ON_DAMAGED -> {
                int pick = selector % 6;
                yield switch (pick) {
                    case 0 -> LoreEffectType.DAMAGE_ATTACKER;
                    case 1 -> (selector % 2 == 0 ? LoreEffectType.HEAL_DEFENDER : LoreEffectType.HEAL_SELF_OVER_TIME);
                    case 2 -> LoreEffectType.APPLY_SHOCK;
                    case 3 -> LoreEffectType.APPLY_STUN;
                    case 4 -> LoreEffectType.BERSERK;
                    default -> LoreEffectType.APPLY_SLOW;
                };
            }
            case ON_NEAR_DEATH -> {
                int pick = selector % 5;
                yield switch (pick) {
                    case 0 -> (selector % 2 == 0 ? LoreEffectType.HEAL_SELF : LoreEffectType.HEAL_SELF_OVER_TIME);
                    case 1 -> LoreEffectType.APPLY_FEAR;
                    case 2 -> LoreEffectType.APPLY_SHIELD;
                    case 3 -> LoreEffectType.BERSERK;
                    default -> LoreEffectType.APPLY_HASTE;
                };
            }
            case ON_BLOCK -> {
                int pick = selector % 5;
                yield switch (pick) {
                    case 0 -> LoreEffectType.DAMAGE_ATTACKER;
                    case 1 -> (selector % 2 == 0 ? LoreEffectType.HEAL_DEFENDER : LoreEffectType.HEAL_SELF_OVER_TIME);
                    case 2 -> LoreEffectType.APPLY_STUN;
                    case 3 -> LoreEffectType.BERSERK;
                    default -> LoreEffectType.APPLY_SLOW;
                };
            }
            case ON_KILL -> {
                int pick = selector % 6;
                yield switch (pick) {
                    case 0 -> (selector % 2 == 0 ? LoreEffectType.HEAL_SELF : LoreEffectType.HEAL_AREA);
                    case 1 -> LoreEffectType.APPLY_BURN;
                    case 2 -> LoreEffectType.DAMAGE_TARGET;
                    case 3 -> LoreEffectType.BERSERK;
                    case 4 -> LoreEffectType.DOUBLE_CAST;
                    default -> LoreEffectType.APPLY_FEAR;
                };
            }
            case ON_CRIT -> {
                int pick = selector % 6;
                yield switch (pick) {
                    case 0 -> LoreEffectType.CRIT_CHARGE;
                    case 1 -> LoreEffectType.DOUBLE_CAST;
                    case 2 -> LoreEffectType.DAMAGE_TARGET;
                    case 3 -> LoreEffectType.APPLY_FREEZE;
                    case 4 -> LoreEffectType.LIFESTEAL;
                    default -> LoreEffectType.APPLY_BLEED;
                };
            }
            default -> {
                int pick = selector % 7;
                yield switch (pick) {
                    case 0 -> LoreEffectType.DAMAGE_TARGET;
                    case 1 -> {
                        int healPick = selector % 3;
                        yield switch (healPick) {
                            case 0 -> LoreEffectType.HEAL_SELF;
                            case 1 -> LoreEffectType.HEAL_AREA;
                            default -> LoreEffectType.HEAL_AREA_OVER_TIME;
                        };
                    }
                    case 2 -> LoreEffectType.APPLY_BURN;
                    case 3 -> LoreEffectType.APPLY_POISON;
                    case 4 -> LoreEffectType.MULTI_HIT;
                    case 5 -> LoreEffectType.DOUBLE_CAST;
                    default -> LoreEffectType.LIFESTEAL;
                };
            }
        };
    }

    private static String normalizeSpiritId(String spiritId) {
        return spiritId.trim().toLowerCase(Locale.ROOT);
    }

    private static double parseDouble(String raw, double fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double clamp01(double v) {
        return Math.max(0.0d, Math.min(1.0d, v));
    }
}
