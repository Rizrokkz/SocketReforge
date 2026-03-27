package irai.mod.reforge.Socket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import com.hypixel.hytale.server.core.inventory.ItemStack;

import irai.mod.reforge.Interactions.ReforgeEquip;
import irai.mod.reforge.Util.LangLoader;

/**
 * Runeword-like resonance matching for socket combinations.
 * Resonance activates only when all sockets are filled and match an exact order.
 */
public final class ResonanceSystem {

    public static final String META_RESONANCE_NAME = "SocketReforge.Resonance.Name";
    public static final String META_RESONANCE_EFFECT = "SocketReforge.Resonance.Effect";
    public static final String META_RESONANCE_TYPE = "SocketReforge.Resonance.Type";
    public static final String META_RESONANCE_QUALITY = "SocketReforge.Resonance.Quality";
    public static final String META_RESONANCE_QUALITY_INDEX = "qualityIndex";

    public static final String LEGENDARY_QUALITY = "Legendary";
    public static final int LEGENDARY_QUALITY_INDEX = 3;

    public enum ResonanceType {
        NONE,
        BURN_ON_CRIT,
        CHAIN_SLOW,
        EXECUTE,
        ARMOR_SHRED,
        THUNDER_STRIKE,
        MULTISHOT_BARRAGE,
        CROSSBOW_AUTO_RELOAD,
        PLUNDERING_BLADE,
        FROST_NOVA_ON_HIT,
        THORNS_SHOCK,
        CHEAT_DEATH,
        HEAL_SURGE,
        SHOCK_DODGE,
        AURA_BURN
    }

    public record ResonanceRecipe(String name, Essence.Type[] pattern, String appliesTo) {}

    public record ResonanceResult(
            String name,
            String effect,
            ResonanceType type,
            Map<EssenceEffect.StatType, double[]> bonuses) {
        public static final ResonanceResult NONE = new ResonanceResult("", "", ResonanceType.NONE, Map.of());

        public boolean active() {
            return name != null && !name.isBlank();
        }
    }

    private enum Scope {
        WEAPON,
        ARMOR
    }

    private enum WeaponClass {
        SWORD,
        AXE,
        MACE,
        DAGGER,
        BOW,
        CROSSBOW,
        STAFF,
        GENERIC
    }

    private record Bonus(EssenceEffect.StatType stat, double flat, double percent) {}

    private static final class Definition {
        final String name;
        final String effect;
        final ResonanceType type;
        final Scope scope;
        final WeaponClass requiredWeaponClass;
        final Essence.Type[] pattern;
        final Map<EssenceEffect.StatType, double[]> bonuses;

        Definition(String name,
                   String effect,
                   ResonanceType type,
                   Scope scope,
                   WeaponClass requiredWeaponClass,
                   Essence.Type[] pattern,
                   Map<EssenceEffect.StatType, double[]> bonuses) {
            this.name = name;
            this.effect = effect;
            this.type = type;
            this.scope = scope;
            this.requiredWeaponClass = requiredWeaponClass;
            this.pattern = pattern;
            this.bonuses = bonuses;
        }

        boolean matches(List<Essence.Type> sequence, boolean isWeapon, boolean isArmor, WeaponClass weaponClass) {
            if (scope == Scope.WEAPON && !isWeapon) return false;
            if (scope == Scope.ARMOR && !isArmor) return false;
            if (requiredWeaponClass != null && !matchesWeaponClass(requiredWeaponClass, weaponClass)) return false;
            if (sequence.size() != pattern.length) return false;
            for (int i = 0; i < pattern.length; i++) {
                if (sequence.get(i) != pattern[i]) {
                    return false;
                }
            }
            return true;
        }

        private boolean matchesWeaponClass(WeaponClass expected, WeaponClass actual) {
            if (expected == WeaponClass.BOW) {
                // Keep existing bow resonances valid for crossbows for backwards compatibility.
                return actual == WeaponClass.BOW || actual == WeaponClass.CROSSBOW;
            }
            return expected == actual;
        }

        ResonanceResult toResult() {
            EnumMap<EssenceEffect.StatType, double[]> copy = new EnumMap<>(EssenceEffect.StatType.class);
            for (Map.Entry<EssenceEffect.StatType, double[]> entry : bonuses.entrySet()) {
                double[] v = entry.getValue();
                copy.put(entry.getKey(), new double[] {v[0], v[1]});
            }
            return new ResonanceResult(name, effect, type, copy);
        }
    }

    private static final List<Definition> DEFINITIONS = List.of(
            // Sword
            def("Kingsbrand", "Damage and crit enhanced; hits can call a lightning strike.",
                    ResonanceType.THUNDER_STRIKE, Scope.WEAPON, WeaponClass.SWORD,
                    b(EssenceEffect.StatType.DAMAGE, 0, 8),
                    b(EssenceEffect.StatType.CRIT_CHANCE, 0, 6),
                    b(EssenceEffect.StatType.LIFE_STEAL, 0, 2),
                    Essence.Type.FIRE, Essence.Type.LIGHTNING, Essence.Type.LIFE),
            def("Oathblade", "Burning crits with heavy damage scaling.",
                    ResonanceType.BURN_ON_CRIT, Scope.WEAPON, WeaponClass.SWORD,
                    b(EssenceEffect.StatType.DAMAGE, 0, 12),
                    b(EssenceEffect.StatType.CRIT_CHANCE, 0, 4),
                    b(EssenceEffect.StatType.CRIT_DAMAGE, 0, 10),
                    Essence.Type.FIRE, Essence.Type.FIRE, Essence.Type.VOID, Essence.Type.LIFE),
            def("Winter Duelist", "Hits chain slow and punish chilled targets.",
                    ResonanceType.CHAIN_SLOW, Scope.WEAPON, WeaponClass.SWORD,
                    b(EssenceEffect.StatType.DAMAGE, 2, 6),
                    b(EssenceEffect.StatType.CRIT_CHANCE, 0, 4),
                    Essence.Type.ICE, Essence.Type.LIGHTNING, Essence.Type.ICE, Essence.Type.WATER),

            // Axe
            def("Butcher's Mark", "Execute bonus against low-health enemies.",
                    ResonanceType.EXECUTE, Scope.WEAPON, WeaponClass.AXE,
                    b(EssenceEffect.StatType.DAMAGE, 0, 10),
                    b(EssenceEffect.StatType.CRIT_CHANCE, 0, 4),
                    b(EssenceEffect.StatType.CRIT_DAMAGE, 0, 8),
                    Essence.Type.VOID, Essence.Type.FIRE, Essence.Type.FIRE),
            def("Warhowl", "Armor-shredding impacts with aggressive tempo.",
                    ResonanceType.ARMOR_SHRED, Scope.WEAPON, WeaponClass.AXE,
                    b(EssenceEffect.StatType.DAMAGE, 0, 8),
                    b(EssenceEffect.StatType.ATTACK_SPEED, 0, 6),
                    Essence.Type.FIRE, Essence.Type.VOID, Essence.Type.LIGHTNING, Essence.Type.FIRE),
            def("Red Harvest", "Sustain spikes during combat momentum.",
                    ResonanceType.HEAL_SURGE, Scope.WEAPON, WeaponClass.AXE,
                    b(EssenceEffect.StatType.DAMAGE, 0, 10),
                    b(EssenceEffect.StatType.LIFE_STEAL, 0, 5),
                    Essence.Type.LIFE, Essence.Type.VOID, Essence.Type.FIRE, Essence.Type.LIFE, Essence.Type.LIGHTNING),

            // Mace
            def("Stormmaul", "Crushing blows can trigger shock strikes.",
                    ResonanceType.THUNDER_STRIKE, Scope.WEAPON, WeaponClass.MACE,
                    b(EssenceEffect.StatType.DAMAGE, 3, 7),
                    b(EssenceEffect.StatType.CRIT_CHANCE, 0, 5),
                    Essence.Type.LIGHTNING, Essence.Type.LIGHTNING, Essence.Type.ICE),
            def("Tomb Bell", "Execution pressure with dark impact.",
                    ResonanceType.EXECUTE, Scope.WEAPON, WeaponClass.MACE,
                    b(EssenceEffect.StatType.DAMAGE, 2, 8),
                    b(EssenceEffect.StatType.CRIT_CHANCE, 0, 4),
                    b(EssenceEffect.StatType.CRIT_DAMAGE, 0, 10),
                    Essence.Type.VOID, Essence.Type.ICE, Essence.Type.VOID, Essence.Type.LIFE),
            def("Siege Psalm", "Balanced offense and thunder proc potential.",
                    ResonanceType.THUNDER_STRIKE, Scope.WEAPON, WeaponClass.MACE,
                    b(EssenceEffect.StatType.DAMAGE, 2, 9),
                    b(EssenceEffect.StatType.ATTACK_SPEED, 0, 5),
                    b(EssenceEffect.StatType.CRIT_CHANCE, 0, 5),
                    Essence.Type.FIRE, Essence.Type.LIFE, Essence.Type.LIGHTNING, Essence.Type.ICE, Essence.Type.VOID),

            // Dagger
            def("Nightneedle", "Rapid crit pressure with execution finish.",
                    ResonanceType.EXECUTE, Scope.WEAPON, WeaponClass.DAGGER,
                    b(EssenceEffect.StatType.DAMAGE, 1, 5),
                    b(EssenceEffect.StatType.ATTACK_SPEED, 0, 8),
                    b(EssenceEffect.StatType.CRIT_CHANCE, 0, 8),
                    Essence.Type.VOID, Essence.Type.LIGHTNING, Essence.Type.WATER),
            def("Frostfang", "Crits apply chilling pressure.",
                    ResonanceType.CHAIN_SLOW, Scope.WEAPON, WeaponClass.DAGGER,
                    b(EssenceEffect.StatType.DAMAGE, 2, 6),
                    b(EssenceEffect.StatType.CRIT_CHANCE, 0, 6),
                    Essence.Type.ICE, Essence.Type.VOID, Essence.Type.LIGHTNING, Essence.Type.ICE),
            def("Ghoststep", "High tempo with sustain surges.",
                    ResonanceType.HEAL_SURGE, Scope.WEAPON, WeaponClass.DAGGER,
                    b(EssenceEffect.StatType.DAMAGE, 1, 6),
                    b(EssenceEffect.StatType.ATTACK_SPEED, 0, 10),
                    b(EssenceEffect.StatType.LIFE_STEAL, 0, 3),
                    Essence.Type.LIGHTNING, Essence.Type.WATER, Essence.Type.VOID, Essence.Type.LIFE, Essence.Type.ICE),
            def("Plundering Blade", "Strikes can steal loot directly from enemy drop tables.",
                    ResonanceType.PLUNDERING_BLADE, Scope.WEAPON, WeaponClass.DAGGER,
                    b(EssenceEffect.StatType.DAMAGE, 2, 7),
                    b(EssenceEffect.StatType.ATTACK_SPEED, 0, 8),
                    b(EssenceEffect.StatType.LUCK, 0, 6),
                    Essence.Type.VOID, Essence.Type.LIFE, Essence.Type.WATER, Essence.Type.LIGHTNING, Essence.Type.FIRE),

            // Bow
            def("Gale String", "Fast volleys with shock strikes.",
                    ResonanceType.THUNDER_STRIKE, Scope.WEAPON, WeaponClass.BOW,
                    b(EssenceEffect.StatType.ATTACK_SPEED, 0, 10),
                    b(EssenceEffect.StatType.CRIT_CHANCE, 0, 6),
                    Essence.Type.LIGHTNING, Essence.Type.WATER, Essence.Type.LIGHTNING),
            def("Frostline", "Arrows punish targets with chained slow.",
                    ResonanceType.CHAIN_SLOW, Scope.WEAPON, WeaponClass.BOW,
                    b(EssenceEffect.StatType.DAMAGE, 2, 8),
                    b(EssenceEffect.StatType.CRIT_CHANCE, 0, 4),
                    Essence.Type.ICE, Essence.Type.WATER, Essence.Type.ICE, Essence.Type.LIGHTNING),
            def("Sunshot", "Explosive burn-style crit spikes.",
                    ResonanceType.BURN_ON_CRIT, Scope.WEAPON, WeaponClass.BOW,
                    b(EssenceEffect.StatType.DAMAGE, 3, 12),
                    b(EssenceEffect.StatType.CRIT_CHANCE, 0, 4),
                    b(EssenceEffect.StatType.CRIT_DAMAGE, 0, 12),
                    Essence.Type.FIRE, Essence.Type.LIGHTNING, Essence.Type.FIRE, Essence.Type.LIFE, Essence.Type.VOID),
            def("Storm Quiver", "Charged bow shots can split into a 3-arrow multishot burst.",
                    ResonanceType.MULTISHOT_BARRAGE, Scope.WEAPON, WeaponClass.BOW,
                    b(EssenceEffect.StatType.DAMAGE, 3, 9),
                    b(EssenceEffect.StatType.ATTACK_SPEED, 0, 8),
                    b(EssenceEffect.StatType.CRIT_CHANCE, 0, 5),
                    Essence.Type.LIGHTNING, Essence.Type.ICE, Essence.Type.WATER, Essence.Type.LIGHTNING, Essence.Type.LIFE),
            def("Clockwork Loader", "Crossbow bolts can be refunded directly back into your quiver.",
                    ResonanceType.CROSSBOW_AUTO_RELOAD, Scope.WEAPON, WeaponClass.CROSSBOW,
                    b(EssenceEffect.StatType.DAMAGE, 2, 8),
                    b(EssenceEffect.StatType.CRIT_CHANCE, 0, 6),
                    b(EssenceEffect.StatType.ATTACK_SPEED, 0, 6),
                    Essence.Type.VOID, Essence.Type.LIGHTNING, Essence.Type.WATER, Essence.Type.LIFE, Essence.Type.VOID),

            // Staff
            def("Sagebind", "Spellflow sustain and utility.",
                    ResonanceType.HEAL_SURGE, Scope.WEAPON, WeaponClass.STAFF,
                    b(EssenceEffect.StatType.DAMAGE, 1, 5),
                    b(EssenceEffect.StatType.ATTACK_SPEED, 0, 6),
                    b(EssenceEffect.StatType.LIFE_STEAL, 0, 4),
                    Essence.Type.LIFE, Essence.Type.ICE, Essence.Type.LIGHTNING),
            def("Riftbranch", "Void-channel lightning bursts.",
                    ResonanceType.THUNDER_STRIKE, Scope.WEAPON, WeaponClass.STAFF,
                    b(EssenceEffect.StatType.DAMAGE, 2, 7),
                    b(EssenceEffect.StatType.CRIT_CHANCE, 0, 6),
                    Essence.Type.VOID, Essence.Type.WATER, Essence.Type.LIGHTNING, Essence.Type.ICE),
            def("Star Conduit", "All-round legendary spell weapon profile.",
                    ResonanceType.THUNDER_STRIKE, Scope.WEAPON, WeaponClass.STAFF,
                    b(EssenceEffect.StatType.DAMAGE, 2, 10),
                    b(EssenceEffect.StatType.CRIT_CHANCE, 0, 6),
                    b(EssenceEffect.StatType.ATTACK_SPEED, 0, 6),
                    b(EssenceEffect.StatType.CRIT_DAMAGE, 0, 8),
                    Essence.Type.LIFE, Essence.Type.FIRE, Essence.Type.ICE, Essence.Type.LIGHTNING, Essence.Type.VOID),

            // Generic weapon
            def("Merciless", "Reliable execute-focused offense.",
                    ResonanceType.EXECUTE, Scope.WEAPON, WeaponClass.GENERIC,
                    b(EssenceEffect.StatType.DAMAGE, 2, 10),
                    b(EssenceEffect.StatType.CRIT_CHANCE, 0, 6),
                    Essence.Type.FIRE, Essence.Type.VOID, Essence.Type.LIGHTNING),
            def("Trinity Edge", "Balanced universal weapon resonance.",
                    ResonanceType.THUNDER_STRIKE, Scope.WEAPON, WeaponClass.GENERIC,
                    b(EssenceEffect.StatType.DAMAGE, 2, 8),
                    b(EssenceEffect.StatType.ATTACK_SPEED, 0, 4),
                    b(EssenceEffect.StatType.CRIT_CHANCE, 0, 4),
                    b(EssenceEffect.StatType.CRIT_DAMAGE, 0, 6),
                    b(EssenceEffect.StatType.LIFE_STEAL, 0, 3),
                    Essence.Type.FIRE, Essence.Type.ICE, Essence.Type.LIGHTNING, Essence.Type.LIFE, Essence.Type.VOID),

            // Armor
            def("Tideguard", "Max health and regeneration surge.",
                    ResonanceType.HEAL_SURGE, Scope.ARMOR, null,
                    b(EssenceEffect.StatType.HEALTH, 20, 0),
                    b(EssenceEffect.StatType.REGENERATION, 2, 0),
                    Essence.Type.WATER, Essence.Type.LIFE, Essence.Type.WATER),
            def("Cryobastion", "Defensive shell with frost nova retaliation.",
                    ResonanceType.FROST_NOVA_ON_HIT, Scope.ARMOR, null,
                    b(EssenceEffect.StatType.DEFENSE, 0, 8),
                    b(EssenceEffect.StatType.FIRE_DEFENSE, 0, 4),
                    Essence.Type.ICE, Essence.Type.ICE, Essence.Type.LIFE),
            def("Stormweave", "Evasion-biased anti-melee defense.",
                    ResonanceType.SHOCK_DODGE, Scope.ARMOR, null,
                    b(EssenceEffect.StatType.EVASION, 0, 10),
                    b(EssenceEffect.StatType.DEFENSE, 0, 4),
                    Essence.Type.LIGHTNING, Essence.Type.WATER, Essence.Type.ICE),
            def("Black Bulwark", "Thorns-style retaliation package.",
                    ResonanceType.THORNS_SHOCK, Scope.ARMOR, null,
                    b(EssenceEffect.StatType.DEFENSE, 0, 10),
                    b(EssenceEffect.StatType.HEALTH, 12, 0),
                    Essence.Type.VOID, Essence.Type.LIFE, Essence.Type.VOID, Essence.Type.WATER),
            def("Sunplate", "Burning aura defensive profile.",
                    ResonanceType.AURA_BURN, Scope.ARMOR, null,
                    b(EssenceEffect.StatType.FIRE_DEFENSE, 0, 10),
                    b(EssenceEffect.StatType.DEFENSE, 0, 6),
                    Essence.Type.FIRE, Essence.Type.LIFE, Essence.Type.WATER, Essence.Type.FIRE),
            def("Grave Mantle", "Cold retaliation with resilience.",
                    ResonanceType.FROST_NOVA_ON_HIT, Scope.ARMOR, null,
                    b(EssenceEffect.StatType.DEFENSE, 0, 8),
                    b(EssenceEffect.StatType.HEALTH, 10, 0),
                    Essence.Type.VOID, Essence.Type.ICE, Essence.Type.LIFE, Essence.Type.WATER),
            def("Glacier Heart", "Frost nova chance on taking hits.",
                    ResonanceType.FROST_NOVA_ON_HIT, Scope.ARMOR, null,
                    b(EssenceEffect.StatType.DEFENSE, 0, 10),
                    b(EssenceEffect.StatType.REGENERATION, 2, 0),
                    Essence.Type.ICE, Essence.Type.WATER, Essence.Type.ICE, Essence.Type.LIFE, Essence.Type.VOID),
            def("Tempest Shell", "Evasion package with shock retaliation.",
                    ResonanceType.THORNS_SHOCK, Scope.ARMOR, null,
                    b(EssenceEffect.StatType.EVASION, 0, 12),
                    b(EssenceEffect.StatType.DEFENSE, 0, 6),
                    Essence.Type.LIGHTNING, Essence.Type.LIGHTNING, Essence.Type.WATER, Essence.Type.ICE, Essence.Type.LIFE),
            def("Phoenix Aegis", "Cheat-death shield with high mitigation.",
                    ResonanceType.CHEAT_DEATH, Scope.ARMOR, null,
                    b(EssenceEffect.StatType.DEFENSE, 0, 12),
                    b(EssenceEffect.StatType.FIRE_DEFENSE, 0, 12),
                    b(EssenceEffect.StatType.HEALTH, 20, 0),
                    Essence.Type.FIRE, Essence.Type.LIFE, Essence.Type.FIRE, Essence.Type.WATER, Essence.Type.VOID),
            def("Worldskin", "Tank package: health, regen, mitigation.",
                    ResonanceType.HEAL_SURGE, Scope.ARMOR, null,
                    b(EssenceEffect.StatType.HEALTH, 30, 0),
                    b(EssenceEffect.StatType.REGENERATION, 3, 0),
                    b(EssenceEffect.StatType.DEFENSE, 0, 10),
                    Essence.Type.LIFE, Essence.Type.WATER, Essence.Type.LIFE, Essence.Type.ICE, Essence.Type.LIGHTNING)
    );

    private static final Object SEED_LOCK = new Object();
    private static volatile boolean seedConfigured = false;
    private static volatile long configuredSeed = 0L;
    private static volatile List<Definition> seededDefinitions = null;

    private static final double RECIPE_WEIGHT_3_SOCKET = 25.0d;
    private static final double RECIPE_WEIGHT_4_SOCKET = 10.0d;
    private static final double RECIPE_WEIGHT_5_SOCKET = 1.0d;

    private ResonanceSystem() {}

    /**
     * Configures the resonance mapping seed. Use the main world seed to make
     * resonance combinations server-unique.
     */
    public static void setResonanceSeed(long seed) {
        synchronized (SEED_LOCK) {
            configuredSeed = seed;
            seedConfigured = true;
            seededDefinitions = null;
        }
    }

    public static boolean isResonanceSeedConfigured() {
        return seedConfigured;
    }

    private static List<Definition> getDefinitions() {
        if (!seedConfigured) {
            return DEFINITIONS;
        }
        List<Definition> cached = seededDefinitions;
        if (cached != null) {
            return cached;
        }
        synchronized (SEED_LOCK) {
            if (seededDefinitions == null) {
                seededDefinitions = buildSeededDefinitions(configuredSeed);
            }
            return seededDefinitions;
        }
    }

    private static List<Definition> buildSeededDefinitions(long seed) {
        if (DEFINITIONS.isEmpty()) {
            return DEFINITIONS;
        }

        Map<Integer, List<Essence.Type[]>> weaponPatternsByLen = new java.util.HashMap<>();
        Map<Integer, List<Essence.Type[]>> armorPatternsByLen = new java.util.HashMap<>();
        for (Definition definition : DEFINITIONS) {
            if (definition == null || definition.pattern == null) {
                continue;
            }
            Essence.Type[] clone = definition.pattern.clone();
            if (definition.scope == Scope.ARMOR) {
                armorPatternsByLen.computeIfAbsent(clone.length, ignored -> new ArrayList<>()).add(clone);
            } else {
                weaponPatternsByLen.computeIfAbsent(clone.length, ignored -> new ArrayList<>()).add(clone);
            }
        }

        long weaponSeed = mixSeed(seed, 0xB17B0F01L);
        for (Map.Entry<Integer, List<Essence.Type[]>> entry : weaponPatternsByLen.entrySet()) {
            long groupSeed = mixSeed(weaponSeed, entry.getKey());
            Collections.shuffle(entry.getValue(), new Random(groupSeed));
        }
        long armorSeed = mixSeed(seed, 0xC0FFEE02L);
        for (Map.Entry<Integer, List<Essence.Type[]>> entry : armorPatternsByLen.entrySet()) {
            long groupSeed = mixSeed(armorSeed, entry.getKey());
            Collections.shuffle(entry.getValue(), new Random(groupSeed));
        }

        Map<Integer, Integer> weaponIndexByLen = new java.util.HashMap<>();
        Map<Integer, Integer> armorIndexByLen = new java.util.HashMap<>();
        List<Definition> seeded = new ArrayList<>(DEFINITIONS.size());
        for (Definition definition : DEFINITIONS) {
            if (definition == null) {
                continue;
            }
            Essence.Type[] pattern = definition.pattern == null ? new Essence.Type[0] : definition.pattern.clone();
            if (definition.scope == Scope.ARMOR) {
                if (definition.pattern != null && definition.pattern.length > 0) {
                    int len = definition.pattern.length;
                    List<Essence.Type[]> bucket = armorPatternsByLen.get(len);
                    int index = armorIndexByLen.getOrDefault(len, 0);
                    if (bucket != null && index < bucket.size()) {
                        pattern = bucket.get(index).clone();
                        armorIndexByLen.put(len, index + 1);
                    }
                }
            } else {
                if (definition.pattern != null && definition.pattern.length > 0) {
                    int len = definition.pattern.length;
                    List<Essence.Type[]> bucket = weaponPatternsByLen.get(len);
                    int index = weaponIndexByLen.getOrDefault(len, 0);
                    if (bucket != null && index < bucket.size()) {
                        pattern = bucket.get(index).clone();
                        weaponIndexByLen.put(len, index + 1);
                    }
                }
            }
            seeded.add(new Definition(
                    definition.name,
                    definition.effect,
                    definition.type,
                    definition.scope,
                    definition.requiredWeaponClass,
                    pattern,
                    definition.bonuses
            ));
        }

        return List.copyOf(seeded);
    }

    private static long mixSeed(long seed, long value) {
        return seed * 31L + value;
    }

    /**
     * Returns the (seeded) essence pattern for a named resonance recipe.
     */
    public static Essence.Type[] getPatternForRecipeName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        for (Definition definition : getDefinitions()) {
            if (definition == null || definition.name == null) {
                continue;
            }
            if (definition.name.trim().toLowerCase(Locale.ROOT).equals(normalized)) {
                return definition.pattern != null ? definition.pattern.clone() : new Essence.Type[0];
            }
        }
        return null;
    }

    /**
     * Returns the (seeded) resonance result for a named recipe, or NONE if not found.
     */
    public static ResonanceResult getResultForRecipeName(String name) {
        if (name == null || name.isBlank()) {
            return ResonanceResult.NONE;
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        for (Definition definition : getDefinitions()) {
            if (definition == null || definition.name == null) {
                continue;
            }
            if (definition.name.trim().toLowerCase(Locale.ROOT).equals(normalized)) {
                return definition.toResult();
            }
        }
        return ResonanceResult.NONE;
    }

    public static String getLocalizedName(String name, Object player) {
        return getLocalizedName(name, LangLoader.getPlayerLanguage(player));
    }

    public static String getLocalizedName(String name, String langCode) {
        if (name == null || name.isBlank()) {
            return name == null ? "" : name;
        }
        String key = resonanceKey(name);
        if (key.isBlank()) {
            return name;
        }
        String translated = LangLoader.getTranslationForLanguage("resonance." + key + ".name", langCode);
        return translated != null && !translated.isBlank() && !translated.equals("resonance." + key + ".name")
                ? translated
                : name;
    }

    public static String getLocalizedEffect(String name, String rawEffect, Object player) {
        return getLocalizedEffect(name, rawEffect, LangLoader.getPlayerLanguage(player));
    }

    public static String getLocalizedEffect(String name, String rawEffect, String langCode) {
        if (name != null && !name.isBlank()) {
            String key = resonanceKey(name);
            if (!key.isBlank()) {
                String translated = LangLoader.getTranslationForLanguage("resonance." + key + ".effect", langCode);
                if (translated != null && !translated.isBlank() && !translated.equals("resonance." + key + ".effect")) {
                    return translated;
                }
            }
        }
        return rawEffect == null ? "" : rawEffect;
    }

    public static String localizeAppliesTo(String raw, Object player) {
        return localizeAppliesTo(raw, LangLoader.getPlayerLanguage(player));
    }

    public static String localizeAppliesTo(String raw, String langCode) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String key = raw.trim().toLowerCase(Locale.ROOT)
                .replace('/', '_')
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (key.isBlank()) {
            return raw;
        }
        String translated = LangLoader.getTranslationForLanguage("resonance.applies." + key, langCode);
        return translated != null && !translated.isBlank() && !translated.equals("resonance.applies." + key)
                ? translated
                : raw;
    }

    public static List<RecipeDisplay> getSeededRecipeDisplays() {
        List<RecipeDisplay> list = new ArrayList<>();
        for (Definition definition : getDefinitions()) {
            if (definition == null || definition.name == null) {
                continue;
            }
            String appliesTo = resolveAppliesTo(definition);
            String pattern = formatPattern(definition.pattern);
            list.add(new RecipeDisplay(definition.name, appliesTo, pattern));
        }
        return List.copyOf(list);
    }

    public record RecipeDisplay(String name, String appliesTo, String pattern) {}

    /**
     * Builds a fully socketed resonance layout for the given item, or null if none apply.
     */
    public static SocketData buildRandomResonanceSocketData(ItemStack item) {
        return buildRandomResonanceSocketData(item, 0.0d);
    }

    /**
     * Builds a fully socketed resonance layout for the given item, with an optional chance
     * for each socket to be a greater (concentrated) essence.
     */
    public static SocketData buildRandomResonanceSocketData(ItemStack item, double greaterEssenceChance) {
        if (item == null || item.isEmpty()) {
            return null;
        }

        boolean isWeapon = ReforgeEquip.isWeapon(item);
        boolean isArmor = !isWeapon && ReforgeEquip.isArmor(item);
        if (!isWeapon && !isArmor) {
            return null;
        }

        WeaponClass weaponClass = classifyWeapon(item.getItemId());
        List<Definition> candidates = new ArrayList<>();
        for (Definition definition : getDefinitions()) {
            if (definition == null || definition.pattern == null || definition.pattern.length == 0) {
                continue;
            }
            if (!definitionApplies(definition, isWeapon, isArmor, weaponClass)) {
                continue;
            }
            candidates.add(definition);
        }

        if (candidates.isEmpty()) {
            return null;
        }

        Definition chosen = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        Essence.Type[] pattern = chosen.pattern;
        SocketData socketData = new SocketData(pattern.length);
        for (int i = 0; i < pattern.length; i++) {
            Essence.Type type = pattern[i];
            if (type == null || !socketData.addSocket()) {
                return null;
            }
            boolean useGreater = ThreadLocalRandom.current().nextDouble() < Math.max(0.0d, Math.min(1.0d, greaterEssenceChance));
            String essenceId = SocketManager.buildEssenceId(type.name(), useGreater);
            if (essenceId == null || !EssenceRegistry.get().exists(essenceId)) {
                essenceId = SocketManager.buildEssenceId(type.name(), false);
            }
            if (essenceId == null || !EssenceRegistry.get().exists(essenceId)) {
                return null;
            }
            socketData.setEssenceAt(i, essenceId);
        }

        return socketData;
    }

    /**
     * Picks a random resonance recipe applicable to the given item.
     */
    public static ResonanceRecipe rollRandomResonanceRecipe(ItemStack item) {
        return rollRandomResonanceRecipe(item, ThreadLocalRandom.current());
    }

    public static ResonanceRecipe rollRandomResonanceRecipe(ItemStack item, Random rng) {
        if (item == null || item.isEmpty()) {
            return null;
        }

        boolean isWeapon = ReforgeEquip.isWeapon(item);
        boolean isArmor = !isWeapon && ReforgeEquip.isArmor(item);
        if (!isWeapon && !isArmor) {
            return null;
        }

        WeaponClass weaponClass = classifyWeapon(item.getItemId());
        List<Definition> candidates = new ArrayList<>();
        for (Definition definition : getDefinitions()) {
            if (!definitionApplies(definition, isWeapon, isArmor, weaponClass)) {
                continue;
            }
            candidates.add(definition);
        }

        if (candidates.isEmpty()) {
            return null;
        }

        Random random = rng != null ? rng : ThreadLocalRandom.current();
        Definition chosen = chooseWeightedDefinition(candidates, random);
        Essence.Type[] pattern = chosen.pattern != null ? chosen.pattern.clone() : new Essence.Type[0];
        return new ResonanceRecipe(chosen.name, pattern, resolveAppliesTo(chosen));
    }

    /**
     * Picks a random resonance recipe without item context.
     */
    public static ResonanceRecipe rollRandomResonanceRecipe() {
        return rollRandomResonanceRecipe(ThreadLocalRandom.current());
    }

    public static ResonanceRecipe rollRandomResonanceRecipe(Random rng) {
        List<Definition> definitions = getDefinitions();
        if (definitions == null || definitions.isEmpty()) {
            return null;
        }
        Random random = rng != null ? rng : ThreadLocalRandom.current();
        Definition chosen = chooseWeightedDefinition(definitions, random);
        Essence.Type[] pattern = chosen.pattern != null ? chosen.pattern.clone() : new Essence.Type[0];
        return new ResonanceRecipe(chosen.name, pattern, resolveAppliesTo(chosen));
    }

    /**
     * Picks a random resonance recipe with an exact socket count.
     */
    public static ResonanceRecipe rollRandomResonanceRecipeBySockets(int socketCount) {
        return rollRandomResonanceRecipeBySockets(socketCount, ThreadLocalRandom.current());
    }

    public static ResonanceRecipe rollRandomResonanceRecipeBySockets(int socketCount, Random rng) {
        if (socketCount <= 0) {
            return rollRandomResonanceRecipe(rng);
        }
        List<Definition> definitions = getDefinitions();
        if (definitions == null || definitions.isEmpty()) {
            return null;
        }

        List<Definition> candidates = new ArrayList<>();
        for (Definition definition : definitions) {
            if (definition == null || definition.pattern == null) {
                continue;
            }
            if (definition.pattern.length != socketCount) {
                continue;
            }
            candidates.add(definition);
        }

        if (candidates.isEmpty()) {
            return null;
        }

        Random random = rng != null ? rng : ThreadLocalRandom.current();
        Definition chosen = candidates.get(random.nextInt(candidates.size()));
        Essence.Type[] pattern = chosen.pattern != null ? chosen.pattern.clone() : new Essence.Type[0];
        return new ResonanceRecipe(chosen.name, pattern, resolveAppliesTo(chosen));
    }

    private static String resolveAppliesTo(Definition definition) {
        if (definition == null) {
            return "Weapon";
        }
        if (definition.scope == Scope.ARMOR) {
            return "Armor";
        }
        WeaponClass weaponClass = definition.requiredWeaponClass;
        if (weaponClass == null || weaponClass == WeaponClass.GENERIC) {
            return "Weapon";
        }
        if (weaponClass == WeaponClass.BOW) {
            return "Bow/Crossbow";
        }
        String raw = weaponClass.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    private static Definition chooseWeightedDefinition(List<Definition> candidates, Random random) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        Map<Integer, List<Definition>> bySockets = new java.util.HashMap<>();
        for (Definition definition : candidates) {
            if (definition == null || definition.pattern == null) {
                continue;
            }
            int sockets = definition.pattern.length;
            if (sockets <= 0) {
                continue;
            }
            bySockets.computeIfAbsent(sockets, ignored -> new ArrayList<>()).add(definition);
        }

        if (bySockets.isEmpty()) {
            return candidates.get(random.nextInt(candidates.size()));
        }

        List<Integer> socketCounts = new ArrayList<>(bySockets.keySet());
        Collections.sort(socketCounts);

        double totalWeight = 0.0d;
        List<SocketBucket> buckets = new ArrayList<>();
        for (Integer socketCount : socketCounts) {
            if (socketCount == null) {
                continue;
            }
            double weight = getRecipeWeight(socketCount);
            if (weight <= 0.0d) {
                continue;
            }
            List<Definition> defs = bySockets.get(socketCount);
            if (defs == null || defs.isEmpty()) {
                continue;
            }
            totalWeight += weight;
            buckets.add(new SocketBucket(weight, defs));
        }

        if (buckets.isEmpty() || totalWeight <= 0.0d) {
            return candidates.get(random.nextInt(candidates.size()));
        }

        double roll = random.nextDouble() * totalWeight;
        double acc = 0.0d;
        for (SocketBucket bucket : buckets) {
            acc += bucket.weight;
            if (roll <= acc) {
                return bucket.pick(random);
            }
        }

        return buckets.get(buckets.size() - 1).pick(random);
    }

    private static double getRecipeWeight(int socketCount) {
        return switch (socketCount) {
            case 3 -> RECIPE_WEIGHT_3_SOCKET;
            case 4 -> RECIPE_WEIGHT_4_SOCKET;
            case 5 -> RECIPE_WEIGHT_5_SOCKET;
            default -> 1.0d;
        };
    }

    private static final class SocketBucket {
        private final double weight;
        private final List<Definition> definitions;

        private SocketBucket(double weight, List<Definition> definitions) {
            this.weight = weight;
            this.definitions = definitions;
        }

        private Definition pick(Random random) {
            if (definitions == null || definitions.isEmpty()) {
                return null;
            }
            return definitions.get(random.nextInt(definitions.size()));
        }
    }

    public static ResonanceResult evaluate(ItemStack item, SocketData socketData) {
        if (item == null || item.isEmpty() || socketData == null || socketData.getSockets().isEmpty()) {
            return ResonanceResult.NONE;
        }

        List<Essence.Type> sequence = extractFilledSequence(socketData);
        if (sequence.isEmpty()) {
            return ResonanceResult.NONE;
        }

        boolean isWeapon = ReforgeEquip.isWeapon(item);
        boolean isArmor = !isWeapon && ReforgeEquip.isArmor(item);
        if (!isWeapon && !isArmor) {
            return ResonanceResult.NONE;
        }

        WeaponClass weaponClass = classifyWeapon(item.getItemId());
        for (Definition definition : getDefinitions()) {
            if (definition.matches(sequence, isWeapon, isArmor, weaponClass)) {
                ResonanceResult result = definition.toResult();
                double multiplier = getResonanceGreaterMultiplier(socketData);
                if (multiplier > 1.0) {
                    return scaleResultBonuses(result, multiplier);
                }
                return result;
            }
        }

        return ResonanceResult.NONE;
    }

    private static boolean definitionApplies(Definition definition, boolean isWeapon, boolean isArmor, WeaponClass weaponClass) {
        if (definition == null || definition.pattern == null) {
            return false;
        }
        List<Essence.Type> sequence = List.of(definition.pattern);
        return definition.matches(sequence, isWeapon, isArmor, weaponClass);
    }

    /**
     * Builds a tooltip-friendly detailed effect string for active resonances.
     */
    public static String buildDetailedEffect(ResonanceResult resonance, boolean isWeapon) {
        if (resonance == null || !resonance.active()) {
            return "";
        }

        String stats = formatBonusSummary(resonance.bonuses());
        String proc = describeProc(resonance.type(), isWeapon);
        String flavor = resonance.effect() == null ? "" : resonance.effect().trim();

        if (!stats.isBlank() && !proc.isBlank()) {
            return "Stats: " + stats + ". " + proc;
        }
        if (!stats.isBlank()) {
            return "Stats: " + stats + ".";
        }
        if (!proc.isBlank()) {
            return proc;
        }
        return flavor;
    }

    public static String buildDetailedEffect(ResonanceResult resonance, boolean isWeapon, Object player) {
        return buildDetailedEffect(resonance, isWeapon, LangLoader.getPlayerLanguage(player));
    }

    public static String buildDetailedEffect(ResonanceResult resonance, boolean isWeapon, String langCode) {
        if (resonance == null || !resonance.active()) {
            return "";
        }

        String stats = formatBonusSummaryLocalized(resonance.bonuses(), langCode);
        String proc = describeProcLocalized(resonance.type(), isWeapon, langCode);
        String flavor = getLocalizedEffect(resonance.name(), resonance.effect(), langCode);

        if (!stats.isBlank() && !proc.isBlank()) {
            return LangLoader.formatTranslation("resonance.detail.stats_and_proc", langCode, stats, proc);
        }
        if (!stats.isBlank()) {
            return LangLoader.formatTranslation("resonance.detail.stats_only", langCode, stats);
        }
        if (!proc.isBlank()) {
            return LangLoader.formatTranslation("resonance.detail.proc_only", langCode, proc);
        }
        return flavor == null ? "" : flavor;
    }

    /**
     * Applies the greater-essence multiplier to the resonance bonuses if needed.
     * This is used for tooltips where we have the socket data but not a full item evaluation.
     */
    public static ResonanceResult applyGreaterEssenceScaling(ResonanceResult resonance, SocketData socketData) {
        if (resonance == null || socketData == null) {
            return resonance;
        }
        double multiplier = getResonanceGreaterMultiplier(socketData);
        if (multiplier <= 1.0) {
            return resonance;
        }
        return scaleResultBonuses(resonance, multiplier);
    }

    private static List<Essence.Type> extractFilledSequence(SocketData socketData) {
        if (socketData == null || socketData.getSockets().isEmpty()) {
            return List.of();
        }
        List<Essence.Type> result = new ArrayList<>();
        for (Socket socket : socketData.getSockets()) {
            if (socket == null || socket.isBroken() || socket.isLocked() || socket.isEmpty()) {
                return List.of();
            }
            Essence essence = EssenceRegistry.get().getById(socket.getEssenceId());
            if (essence == null || essence.getType() == null) {
                return List.of();
            }
            result.add(essence.getType());
        }
        return result;
    }

    private static String describeProc(ResonanceType type, boolean isWeapon) {
        if (type == null || type == ResonanceType.NONE) {
            return "";
        }
        return switch (type) {
            case BURN_ON_CRIT -> "On hit: 22% chance (0.7s cooldown) to deal +8% bonus damage (min +1).";
            case CHAIN_SLOW -> "On hit: 25% chance (2.0s cooldown) to freeze target for 1.5s.";
            case EXECUTE -> "On hit: deal +20% damage to targets at or below 25% HP.";
            case ARMOR_SHRED -> "On hit: 30% chance (0.9s cooldown) to deal +10% damage.";
            case THUNDER_STRIKE -> "On hit: 20% chance (1.2s cooldown) to deal +10% bonus damage (min +1).";
            case MULTISHOT_BARRAGE -> "On projectile hit: 20% chance (1.6s cooldown) to fire 2 extra arrows at 35% damage each.";
            case CROSSBOW_AUTO_RELOAD -> "On projectile hit: 35% chance to refund 1 arrow/bolt to inventory.";
            case PLUNDERING_BLADE -> "On hit: 15% chance (2.5s cooldown) to steal an item roll from NPC drop table.";
            case FROST_NOVA_ON_HIT -> "When hit: 25% chance (4.0s cooldown) to freeze attacker for 1.5s.";
            case THORNS_SHOCK -> "When hit: reflect 6% of incoming damage as Shock (min 1).";
            case CHEAT_DEATH -> "Lethal hit prevention: once every 60s, survive at 1 HP.";
            case HEAL_SURGE -> isWeapon
                    ? "On hit: heal for 10% of damage dealt (min 1) every 1.8s."
                    : "When hit: heal for 5% of incoming damage (min 1) every 5.0s.";
            case SHOCK_DODGE -> "On dodge: 20% chance (3.5s cooldown) to retaliate for 4% of incoming damage (min 1).";
            case AURA_BURN -> "When hit: 20% chance (0.9s cooldown) to burn attacker for 5% of incoming damage (min 1).";
            case NONE -> "";
        };
    }

    private static String describeProcLocalized(ResonanceType type, boolean isWeapon, String langCode) {
        if (type == null || type == ResonanceType.NONE) {
            return "";
        }
        String key = switch (type) {
            case BURN_ON_CRIT -> "resonance.proc.burn_on_crit";
            case CHAIN_SLOW -> "resonance.proc.chain_slow";
            case EXECUTE -> "resonance.proc.execute";
            case ARMOR_SHRED -> "resonance.proc.armor_shred";
            case THUNDER_STRIKE -> "resonance.proc.thunder_strike";
            case MULTISHOT_BARRAGE -> "resonance.proc.multishot_barrage";
            case CROSSBOW_AUTO_RELOAD -> "resonance.proc.crossbow_auto_reload";
            case PLUNDERING_BLADE -> "resonance.proc.plundering_blade";
            case FROST_NOVA_ON_HIT -> "resonance.proc.frost_nova_on_hit";
            case THORNS_SHOCK -> "resonance.proc.thorns_shock";
            case CHEAT_DEATH -> "resonance.proc.cheat_death";
            case HEAL_SURGE -> isWeapon ? "resonance.proc.heal_surge.weapon" : "resonance.proc.heal_surge.armor";
            case SHOCK_DODGE -> "resonance.proc.shock_dodge";
            case AURA_BURN -> "resonance.proc.aura_burn";
            case NONE -> "";
        };
        if (key.isBlank()) {
            return "";
        }
        String translated = LangLoader.getTranslationForLanguage(key, langCode);
        if (translated == null || translated.isBlank() || translated.equals(key)) {
            return describeProc(type, isWeapon);
        }
        return translated;
    }

    private static String formatBonusSummary(Map<EssenceEffect.StatType, double[]> bonuses) {
        if (bonuses == null || bonuses.isEmpty()) {
            return "";
        }

        List<String> parts = new ArrayList<>();
        for (EssenceEffect.StatType stat : EssenceEffect.StatType.values()) {
            double[] values = bonuses.get(stat);
            if (values == null || values.length < 2) {
                continue;
            }
            double flat = values[0];
            double percent = values[1];
            String label = formatStatLabel(stat);
            if (Math.abs(flat) > 1.0E-9) {
                parts.add((flat > 0 ? "+" : "") + formatNumber(flat) + " " + label);
            }
            if (Math.abs(percent) > 1.0E-9) {
                parts.add((percent > 0 ? "+" : "") + formatNumber(percent) + "% " + label);
            }
        }
        return String.join(", ", parts);
    }

    private static String formatBonusSummaryLocalized(Map<EssenceEffect.StatType, double[]> bonuses, String langCode) {
        if (bonuses == null || bonuses.isEmpty()) {
            return "";
        }

        List<String> parts = new ArrayList<>();
        for (EssenceEffect.StatType stat : EssenceEffect.StatType.values()) {
            double[] values = bonuses.get(stat);
            if (values == null || values.length < 2) {
                continue;
            }
            double flat = values[0];
            double percent = values[1];
            String label = formatStatLabelLocalized(stat, langCode);
            if (Math.abs(flat) > 1.0E-9) {
                parts.add((flat > 0 ? "+" : "") + formatNumber(flat) + " " + label);
            }
            if (Math.abs(percent) > 1.0E-9) {
                parts.add((percent > 0 ? "+" : "") + formatNumber(percent) + "% " + label);
            }
        }
        return String.join(", ", parts);
    }

    private static String formatStatLabel(EssenceEffect.StatType stat) {
        if (stat == null) {
            return "Stat";
        }
        return switch (stat) {
            case DAMAGE -> "Damage";
            case DEFENSE -> "Defense";
            case FIRE_DEFENSE -> "Fire Defense";
            case HEALTH -> "Health";
            case MOVEMENT_SPEED -> "Slow";
            case REGENERATION -> "Regen";
            case CRIT_CHANCE -> "Crit Chance";
            case CRIT_DAMAGE -> "Crit Damage";
            case ATTACK_SPEED -> "Attack Speed";
            case LIFE_STEAL -> "Lifesteal";
            case EVASION -> "Evasion";
            case LUCK -> "Luck";
        };
    }

    private static String formatStatLabelLocalized(EssenceEffect.StatType stat, String langCode) {
        if (stat == null) {
            return LangLoader.getTranslationForLanguage("resonance.stat.generic", langCode);
        }
        String key = switch (stat) {
            case DAMAGE -> "resonance.stat.damage";
            case DEFENSE -> "resonance.stat.defense";
            case FIRE_DEFENSE -> "resonance.stat.fire_defense";
            case HEALTH -> "resonance.stat.health";
            case MOVEMENT_SPEED -> "resonance.stat.slow";
            case REGENERATION -> "resonance.stat.regen";
            case CRIT_CHANCE -> "resonance.stat.crit_chance";
            case CRIT_DAMAGE -> "resonance.stat.crit_damage";
            case ATTACK_SPEED -> "resonance.stat.attack_speed";
            case LIFE_STEAL -> "resonance.stat.lifesteal";
            case EVASION -> "resonance.stat.evasion";
            case LUCK -> "resonance.stat.luck";
        };
        String translated = LangLoader.getTranslationForLanguage(key, langCode);
        if (translated == null || translated.isBlank() || translated.equals(key)) {
            return formatStatLabel(stat);
        }
        return translated;
    }

    private static String resonanceKey(String name) {
        if (name == null) {
            return "";
        }
        String key = name.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty()) {
            return "";
        }
        key = key.replaceAll("[^a-z0-9]+", "_");
        key = key.replaceAll("^_+|_+$", "");
        return key;
    }

    private static String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0E-9) {
            return String.valueOf((int) Math.rint(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String formatPattern(Essence.Type[] pattern) {
        if (pattern == null || pattern.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Essence.Type type : pattern) {
            sb.append('[').append(formatEssenceToken(type)).append(']');
        }
        return sb.toString();
    }

    private static String formatEssenceToken(Essence.Type type) {
        String raw = type == null ? "" : type.name().toLowerCase(Locale.ROOT);
        if (raw.isEmpty()) {
            return "x";
        }
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    private static double getResonanceGreaterMultiplier(SocketData socketData) {
        if (socketData == null || socketData.getSockets().isEmpty()) {
            return 1.0;
        }
        int total = 0;
        int greater = 0;
        for (Socket socket : socketData.getSockets()) {
            if (socket == null || socket.isEmpty() || socket.isBroken()) {
                continue;
            }
            total++;
            if (SocketManager.isGreaterEssenceId(socket.getEssenceId())) {
                greater++;
            }
        }
        if (total <= 0) {
            return 1.0;
        }
        double ratio = (double) greater / (double) total;
        return 1.0 + (0.5 * ratio);
    }

    private static ResonanceResult scaleResultBonuses(ResonanceResult result, double multiplier) {
        if (result == null || result.bonuses() == null || result.bonuses().isEmpty() || multiplier == 1.0) {
            return result;
        }
        EnumMap<EssenceEffect.StatType, double[]> scaled = new EnumMap<>(EssenceEffect.StatType.class);
        for (Map.Entry<EssenceEffect.StatType, double[]> entry : result.bonuses().entrySet()) {
            double[] values = entry.getValue();
            if (values == null || values.length < 2) {
                continue;
            }
            scaled.put(entry.getKey(), new double[] {values[0] * multiplier, values[1] * multiplier});
        }
        return new ResonanceResult(result.name(), result.effect(), result.type(), scaled);
    }

    private static WeaponClass classifyWeapon(String itemId) {
        String id = itemId == null ? "" : itemId.toLowerCase(java.util.Locale.ROOT);
        if (id.contains("sword")) return WeaponClass.SWORD;
        if (id.contains("battleaxe") || id.contains("axe")) return WeaponClass.AXE;
        if (id.contains("mace") || id.contains("club")) return WeaponClass.MACE;
        if (id.contains("dagger") || id.contains("knife")) return WeaponClass.DAGGER;
        if (id.contains("crossbow")) return WeaponClass.CROSSBOW;
        if (id.contains("bow")) return WeaponClass.BOW;
        if (id.contains("staff")
                || id.contains("spear")
                || id.contains("spellbook")
                || id.contains("spell_book")
                || id.contains("spell-book")
                || id.contains("tome")
                || id.contains("grimoire")) {
            return WeaponClass.STAFF;
        }
        return WeaponClass.GENERIC;
    }

    private static Definition def(String name,
                                  String effect,
                                  ResonanceType type,
                                  Scope scope,
                                  WeaponClass weaponClass,
                                  Bonus b1,
                                  Essence.Type p1,
                                  Essence.Type p2,
                                  Essence.Type p3) {
        return new Definition(name, effect, type, scope, weaponClass, new Essence.Type[] {p1, p2, p3}, bonusMap(b1));
    }

    private static Definition def(String name,
                                  String effect,
                                  ResonanceType type,
                                  Scope scope,
                                  WeaponClass weaponClass,
                                  Bonus b1,
                                  Bonus b2,
                                  Essence.Type p1,
                                  Essence.Type p2,
                                  Essence.Type p3) {
        return new Definition(name, effect, type, scope, weaponClass, new Essence.Type[] {p1, p2, p3}, bonusMap(b1, b2));
    }

    private static Definition def(String name,
                                  String effect,
                                  ResonanceType type,
                                  Scope scope,
                                  WeaponClass weaponClass,
                                  Bonus b1,
                                  Bonus b2,
                                  Bonus b3,
                                  Essence.Type p1,
                                  Essence.Type p2,
                                  Essence.Type p3) {
        return new Definition(name, effect, type, scope, weaponClass, new Essence.Type[] {p1, p2, p3}, bonusMap(b1, b2, b3));
    }

    private static Definition def(String name,
                                  String effect,
                                  ResonanceType type,
                                  Scope scope,
                                  WeaponClass weaponClass,
                                  Bonus b1,
                                  Bonus b2,
                                  Essence.Type p1,
                                  Essence.Type p2,
                                  Essence.Type p3,
                                  Essence.Type p4) {
        return new Definition(name, effect, type, scope, weaponClass, new Essence.Type[] {p1, p2, p3, p4}, bonusMap(b1, b2));
    }

    private static Definition def(String name,
                                  String effect,
                                  ResonanceType type,
                                  Scope scope,
                                  WeaponClass weaponClass,
                                  Bonus b1,
                                  Bonus b2,
                                  Bonus b3,
                                  Essence.Type p1,
                                  Essence.Type p2,
                                  Essence.Type p3,
                                  Essence.Type p4) {
        return new Definition(name, effect, type, scope, weaponClass, new Essence.Type[] {p1, p2, p3, p4}, bonusMap(b1, b2, b3));
    }

    private static Definition def(String name,
                                  String effect,
                                  ResonanceType type,
                                  Scope scope,
                                  WeaponClass weaponClass,
                                  Bonus b1,
                                  Bonus b2,
                                  Essence.Type p1,
                                  Essence.Type p2,
                                  Essence.Type p3,
                                  Essence.Type p4,
                                  Essence.Type p5) {
        return new Definition(name, effect, type, scope, weaponClass, new Essence.Type[] {p1, p2, p3, p4, p5}, bonusMap(b1, b2));
    }

    private static Definition def(String name,
                                  String effect,
                                  ResonanceType type,
                                  Scope scope,
                                  WeaponClass weaponClass,
                                  Bonus b1,
                                  Bonus b2,
                                  Bonus b3,
                                  Essence.Type p1,
                                  Essence.Type p2,
                                  Essence.Type p3,
                                  Essence.Type p4,
                                  Essence.Type p5) {
        return new Definition(name, effect, type, scope, weaponClass, new Essence.Type[] {p1, p2, p3, p4, p5}, bonusMap(b1, b2, b3));
    }

    private static Definition def(String name,
                                  String effect,
                                  ResonanceType type,
                                  Scope scope,
                                  WeaponClass weaponClass,
                                  Bonus b1,
                                  Bonus b2,
                                  Bonus b3,
                                  Bonus b4,
                                  Essence.Type p1,
                                  Essence.Type p2,
                                  Essence.Type p3,
                                  Essence.Type p4,
                                  Essence.Type p5) {
        return new Definition(name, effect, type, scope, weaponClass, new Essence.Type[] {p1, p2, p3, p4, p5}, bonusMap(b1, b2, b3, b4));
    }

    private static Definition def(String name,
                                  String effect,
                                  ResonanceType type,
                                  Scope scope,
                                  WeaponClass weaponClass,
                                  Bonus b1,
                                  Bonus b2,
                                  Bonus b3,
                                  Bonus b4,
                                  Bonus b5,
                                  Essence.Type p1,
                                  Essence.Type p2,
                                  Essence.Type p3,
                                  Essence.Type p4,
                                  Essence.Type p5) {
        return new Definition(name, effect, type, scope, weaponClass, new Essence.Type[] {p1, p2, p3, p4, p5}, bonusMap(b1, b2, b3, b4, b5));
    }

    private static Bonus b(EssenceEffect.StatType stat, double flat, double percent) {
        return new Bonus(stat, flat, percent);
    }

    private static Map<EssenceEffect.StatType, double[]> bonusMap(Bonus... bonuses) {
        EnumMap<EssenceEffect.StatType, double[]> map = new EnumMap<>(EssenceEffect.StatType.class);
        if (bonuses == null) {
            return map;
        }
        for (Bonus bonus : bonuses) {
            if (bonus == null || bonus.stat() == null) {
                continue;
            }
            double[] values = map.computeIfAbsent(bonus.stat(), ignored -> new double[] {0.0, 0.0});
            values[0] += bonus.flat();
            values[1] += bonus.percent();
        }
        return map;
    }
}
