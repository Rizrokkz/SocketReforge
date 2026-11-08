package irai.mod.reforge.Progression;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;

/** Persistent, bench-earned character progression and passive skill nodes. */
public final class CharacterPathService {
    public static final int XP_PER_LEVEL = 100;

    public enum Category {
        VITALITY, ARCANA, ENDURANCE, COMBAT, GUARD,
        DAGGERS, SWORDS, AXES, MACES, SPEARS, SHORTBOWS, CROSSBOWS, STAFFS
    }

    /** Weapon families are matched by native item id, so upgrades follow the weapon in hand. */
    public enum WeaponClass {
        DAGGERS("Weapon_Daggers_", "Daggers", Category.DAGGERS),
        SWORDS("Weapon_Sword_", "Swords", Category.SWORDS),
        AXES("Weapon_Axe_", "Axes", Category.AXES),
        MACES("Weapon_Mace_", "Maces", Category.MACES),
        SPEARS("Weapon_Spear_", "Spears", Category.SPEARS),
        SHORTBOWS("Weapon_Shortbow_", "Shortbows", Category.SHORTBOWS),
        CROSSBOWS("Weapon_Crossbow_", "Crossbows", Category.CROSSBOWS),
        STAFFS("Weapon_Staff_", "Staves", Category.STAFFS);

        public final String itemPrefix;
        public final String displayName;
        public final Category category;

        WeaponClass(String itemPrefix, String displayName, Category category) {
            this.itemPrefix = itemPrefix;
            this.displayName = displayName;
            this.category = category;
        }
    }

    /** Values are stored as fractions for percentage bonuses and native stat units for flat bonuses. */
    public enum Bonus {
        MAX_HEALTH_FLAT, HEALTH_REGEN_PER_SECOND,
        MAX_MANA_FLAT, MANA_REGEN_PER_SECOND,
        MAX_STAMINA_FLAT, STAMINA_REGEN_PER_SECOND,
        DAMAGE_PERCENT, DAMAGE_RESISTANCE, CRIT_DAMAGE_PERCENT, CRIT_CHANCE_PERCENT, ATTACK_SPEED_PERCENT,
        LORE_XP_GAIN_PERCENT,

        // Bench and elemental bonuses are authorable through the skill-tree editor.
        // REFINING_CHANCE remains for existing trees and grants an upgrade guarantee.
        REFINING_CHANCE, REFINING_BREAK_PROTECTION, REFINING_DEGRADE_PROTECTION,
        REFINING_SAME_CHANCE, REFINING_UPGRADE_CHANCE, REFINING_JACKPOT_CHANCE,
        SOCKET_CHANCE, ELEMENTAL_DAMAGE,
        ELEMENTAL_DOT_CRIT_CHANCE, ELEMENTAL_DOT_CRIT_DAMAGE,
        ELEMENTAL_STACK_BASE, POISON_STACK_BASE, BLEED_STACK_BASE
    }

    public record NodeBonus(Bonus bonus, double perRank) {}

    public static final class Node {
        public final String id;
        public final Category category;
        public final String name;
        public final String description;
        public final int maxRank;
        /** First connected node retained for older UI callers. */
        public String prerequisite;
        private List<String> connections = List.of();
        public final List<NodeBonus> bonuses;
        public final boolean core;
        /** Small path node; it is selectable and its rank gates connected nodes. */
        public final boolean pathOnly;
        public final WeaponClass weaponClass;

        /** Backwards-compatible view for code that expects a single node bonus. */
        public final Bonus bonus;
        public final double perRank;

        private Node(String id, Category category, String name, String description,
                     int maxRank, String prerequisite, boolean core, boolean pathOnly,
                     WeaponClass weaponClass, List<NodeBonus> bonuses) {
            this.id = id;
            this.category = category;
            this.name = name;
            this.description = description;
            this.maxRank = maxRank;
            this.prerequisite = prerequisite;
            this.core = core;
            this.pathOnly = pathOnly;
            this.weaponClass = weaponClass;
            this.bonuses = List.copyOf(bonuses);
            this.bonus = bonuses.isEmpty() ? null : bonuses.get(0).bonus();
            this.perRank = bonuses.isEmpty() ? 0.0d : bonuses.get(0).perRank();
        }

        public List<String> connections() { return connections; }

        private void setConnections(List<String> connected) {
            this.connections = List.copyOf(connected);
            if (this.prerequisite == null && !connected.isEmpty()) this.prerequisite = connected.get(0);
        }
    }

    public record GraphEdge(String first, String second) {}

    /** Definition used by the in-game tree authoring tool when a draft is applied. */
    public record NodeDefinition(String id, Category category, String name, String description,
                                 int maxRank, boolean core, boolean pathOnly,
                                 WeaponClass weaponClass, List<NodeBonus> bonuses) {}

    public static final class Profile {
        public long xp;
        public Map<String, Integer> ranks = new LinkedHashMap<>();
        /** Stat chosen by this player for each allocated path node. */
        public Map<String, String> pathChoices = new LinkedHashMap<>();
        public Profile() {}
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, Profile> PROFILES = new ConcurrentHashMap<>();
    private static volatile List<Node> NODES;
    private static volatile Map<String, Node> NODE_BY_ID;
    private static volatile List<GraphEdge> GRAPH_EDGES;

    static {
        List<Node> nodes = new ArrayList<>();
        Map<String, Node> byId = new LinkedHashMap<>();
        add(nodes, byId, node("vitality_health", Category.VITALITY, "Vitality", "+4 maximum Health per rank.", 4, null,
                effect(Bonus.MAX_HEALTH_FLAT, 4.0)));
        add(nodes, byId, node("vitality_regen", Category.VITALITY, "Second Wind", "+0.10 Health regenerated per second per rank.", 4, null,
                effect(Bonus.HEALTH_REGEN_PER_SECOND, 0.10)));
        add(nodes, byId, node("vitality_core", Category.VITALITY, "Vitality Keystone", "+10 maximum Health and +0.25 Health regenerated per second.", 1, null,
                effect(Bonus.MAX_HEALTH_FLAT, 10.0), effect(Bonus.HEALTH_REGEN_PER_SECOND, 0.25)));
        add(nodes, byId, node("vitality_vigor", Category.VITALITY, "Vigor", "+2 maximum Health per rank.", 3, null,
                effect(Bonus.MAX_HEALTH_FLAT, 2.0)));
        add(nodes, byId, node("vitality_pulse", Category.VITALITY, "Steady Pulse", "+0.05 Health regenerated per second per rank.", 3, null,
                effect(Bonus.HEALTH_REGEN_PER_SECOND, 0.05)));
        add(nodes, byId, node("vitality_resolve", Category.VITALITY, "Resolve", "+2 maximum Health per rank.", 3, null,
                effect(Bonus.MAX_HEALTH_FLAT, 2.0)));
        add(nodes, byId, node("arcana_mana", Category.ARCANA, "Mana Reserve", "+3 maximum Mana per rank.", 4, null,
                effect(Bonus.MAX_MANA_FLAT, 3.0)));
        add(nodes, byId, node("arcana_regen", Category.ARCANA, "Flowing Focus", "+0.10 Mana regenerated per second per rank.", 4, null,
                effect(Bonus.MANA_REGEN_PER_SECOND, 0.10)));
        add(nodes, byId, node("arcana_core", Category.ARCANA, "Arcane Keystone", "+8 maximum Mana and +0.25 Mana regenerated per second.", 1, null,
                effect(Bonus.MAX_MANA_FLAT, 8.0), effect(Bonus.MANA_REGEN_PER_SECOND, 0.25)));
        add(nodes, byId, node("arcana_lore_insight", Category.ARCANA, "Lore Insight",
                "+10% Lore XP gained from each proc per rank.", 4, null,
                effect(Bonus.LORE_XP_GAIN_PERCENT, 0.10)));
        add(nodes, byId, node("arcana_resonance", Category.ARCANA, "Resonance", "+1.5 maximum Mana per rank.", 3, null,
                effect(Bonus.MAX_MANA_FLAT, 1.5)));
        add(nodes, byId, node("arcana_focus", Category.ARCANA, "Quiet Focus", "+0.05 Mana regenerated per second per rank.", 3, null,
                effect(Bonus.MANA_REGEN_PER_SECOND, 0.05)));
        add(nodes, byId, node("arcana_spark", Category.ARCANA, "Arcane Spark", "+1.5 maximum Mana per rank.", 3, null,
                effect(Bonus.MAX_MANA_FLAT, 1.5)));
        add(nodes, byId, node("endurance_stamina", Category.ENDURANCE, "Stamina Reserve", "+4 maximum Stamina per rank.", 4, null,
                effect(Bonus.MAX_STAMINA_FLAT, 4.0)));
        add(nodes, byId, node("endurance_regen", Category.ENDURANCE, "Steady Pace", "+0.10 Stamina regenerated per second per rank.", 4, null,
                effect(Bonus.STAMINA_REGEN_PER_SECOND, 0.10)));
        add(nodes, byId, node("endurance_core", Category.ENDURANCE, "Endurance Keystone", "+10 maximum Stamina and +0.25 Stamina regenerated per second.", 1, null,
                effect(Bonus.MAX_STAMINA_FLAT, 10.0), effect(Bonus.STAMINA_REGEN_PER_SECOND, 0.25)));
        add(nodes, byId, node("endurance_reserve", Category.ENDURANCE, "Deep Reserve", "+2 maximum Stamina per rank.", 3, null,
                effect(Bonus.MAX_STAMINA_FLAT, 2.0)));
        add(nodes, byId, node("endurance_rhythm", Category.ENDURANCE, "Measured Breathing", "+0.05 Stamina regenerated per second per rank.", 3, null,
                effect(Bonus.STAMINA_REGEN_PER_SECOND, 0.05)));
        add(nodes, byId, node("endurance_drive", Category.ENDURANCE, "Drive", "+2 maximum Stamina per rank.", 3, null,
                effect(Bonus.MAX_STAMINA_FLAT, 2.0)));
        add(nodes, byId, node("combat_damage", Category.COMBAT, "Weapon Mastery", "+0.5% damage per rank.", 4, null,
                effect(Bonus.DAMAGE_PERCENT, 0.005)));
        add(nodes, byId, node("combat_crit_chance", Category.COMBAT, "Keen Edge", "+0.3% critical chance per rank.", 4, null,
                effect(Bonus.CRIT_CHANCE_PERCENT, 0.003)));
        add(nodes, byId, node("combat_crit_damage", Category.COMBAT, "Critical Force", "+1.5% critical damage per rank.", 4, null,
                effect(Bonus.CRIT_DAMAGE_PERCENT, 0.015)));
        add(nodes, byId, node("combat_core", Category.COMBAT, "Combat Keystone", "+1.5% damage, +0.5% critical chance, and +2.5% critical damage.", 1, null,
                effect(Bonus.DAMAGE_PERCENT, 0.015), effect(Bonus.CRIT_CHANCE_PERCENT, 0.005), effect(Bonus.CRIT_DAMAGE_PERCENT, 0.025)));
        add(nodes, byId, node("combat_tempo", Category.COMBAT, "Rapid Tempo",
                "+5% attack speed per rank. Each point above base 5 adds 20 percentage points of chance to trigger one extra hit dealing 35% damage (75% chance cap).", 4, null,
                effect(Bonus.ATTACK_SPEED_PERCENT, 0.05)));
        add(nodes, byId, node("combat_momentum", Category.COMBAT, "Momentum", "+0.25% damage per rank.", 3, null,
                effect(Bonus.DAMAGE_PERCENT, 0.0025)));
        add(nodes, byId, node("combat_precision", Category.COMBAT, "Fine Edge", "+0.15% critical chance per rank.", 3, null,
                effect(Bonus.CRIT_CHANCE_PERCENT, 0.0015)));
        add(nodes, byId, node("combat_finesse", Category.COMBAT, "Finishing Force", "+0.75% critical damage per rank.", 3, null,
                effect(Bonus.CRIT_DAMAGE_PERCENT, 0.0075)));
        add(nodes, byId, node("combat_execution", Category.COMBAT, "Execution", "+0.25% damage per rank.", 3, null,
                effect(Bonus.DAMAGE_PERCENT, 0.0025)));
        add(nodes, byId, node("guard_resistance", Category.GUARD, "Iron Guard", "+1% damage resistance per rank.", 4, null,
                effect(Bonus.DAMAGE_RESISTANCE, 0.01)));
        add(nodes, byId, node("guard_bastion", Category.GUARD, "Bastion", "+0.5% damage resistance per rank.", 3, null,
                effect(Bonus.DAMAGE_RESISTANCE, 0.005)));
        add(nodes, byId, node("guard_steadfast", Category.GUARD, "Steadfast", "+0.5% damage resistance per rank.", 3, null,
                effect(Bonus.DAMAGE_RESISTANCE, 0.005)));
        add(nodes, byId, node("guard_core", Category.GUARD, "Guardian Keystone", "+2% damage resistance.", 1, null,
                effect(Bonus.DAMAGE_RESISTANCE, 0.02)));

        // Repeating mixed-stat constellations: small ranks with larger one-rank keystone rewards.
        add(nodes, byId, node("hybrid_life", Category.VITALITY, "Living Bastion", "+2 Health and +0.05 Health/s per rank.", 3, null,
                effect(Bonus.MAX_HEALTH_FLAT, 2.0), effect(Bonus.HEALTH_REGEN_PER_SECOND, 0.05)));
        add(nodes, byId, node("hybrid_reserve", Category.ARCANA, "Twin Reservoir", "+1.5 Mana and +2 Stamina per rank.", 3, null,
                effect(Bonus.MAX_MANA_FLAT, 1.5), effect(Bonus.MAX_STAMINA_FLAT, 2.0)));
        add(nodes, byId, node("hybrid_flow", Category.ENDURANCE, "Measured Flow", "+0.05 Mana/s and +0.05 Stamina/s per rank.", 4, null,
                effect(Bonus.MANA_REGEN_PER_SECOND, 0.05), effect(Bonus.STAMINA_REGEN_PER_SECOND, 0.05)));
        add(nodes, byId, node("hybrid_guardian", Category.GUARD, "Warden's Pulse", "+1 Health and +0.25% damage resistance per rank.", 3, null,
                effect(Bonus.MAX_HEALTH_FLAT, 1.0), effect(Bonus.DAMAGE_RESISTANCE, 0.0025)));
        add(nodes, byId, node("hybrid_precision", Category.COMBAT, "Patient Aim", "+0.15% critical chance and +0.75% critical damage per rank.", 4, null,
                effect(Bonus.CRIT_CHANCE_PERCENT, 0.0015), effect(Bonus.CRIT_DAMAGE_PERCENT, 0.0075)));
        add(nodes, byId, node("hybrid_assault", Category.COMBAT, "Open Wound", "+0.25% damage and +0.5% critical damage per rank.", 4, null,
                effect(Bonus.DAMAGE_PERCENT, 0.0025), effect(Bonus.CRIT_DAMAGE_PERCENT, 0.005)));
        add(nodes, byId, node("hybrid_last_stand", Category.GUARD, "Last Stand", "+2 Health and +0.25% damage resistance per rank.", 3, null,
                effect(Bonus.MAX_HEALTH_FLAT, 2.0), effect(Bonus.DAMAGE_RESISTANCE, 0.0025)));

        for (WeaponClass weaponClass : WeaponClass.values()) {
            String prefix = weaponClass.name().toLowerCase(java.util.Locale.ROOT);
            WeaponTuning tuning = tuning(weaponClass);
            add(nodes, byId, weaponNode(prefix + "_core", weaponClass.category, weaponClass.displayName + " Core",
                    "+0.5% damage while wielding " + weaponClass.displayName + ". This is a starting point for its constellation.",
                    1, weaponClass, true, effect(Bonus.DAMAGE_PERCENT, 0.005)));
            add(nodes, byId, weaponNode(prefix + "_mastery", weaponClass.category, weaponClass.displayName + " Mastery",
                    String.format(java.util.Locale.ROOT, "+%.2f%% damage per rank while wielding %s.", tuning.damagePerRank * 100, weaponClass.displayName),
                    4, weaponClass, false, effect(Bonus.DAMAGE_PERCENT, tuning.damagePerRank)));
            add(nodes, byId, weaponNode(prefix + "_technique", weaponClass.category, weaponClass.displayName + " Technique",
                    String.format(java.util.Locale.ROOT, "+%.2f%% critical chance and +%.2f%% critical damage per rank while wielding %s.",
                            tuning.critChancePerRank * 100, tuning.critDamagePerRank * 100, weaponClass.displayName),
                    4, weaponClass, false, effect(Bonus.CRIT_CHANCE_PERCENT, tuning.critChancePerRank),
                    effect(Bonus.CRIT_DAMAGE_PERCENT, tuning.critDamagePerRank)));
            add(nodes, byId, weaponNode(prefix + "_hybrid", weaponClass.category, weaponClass.displayName + " Flourish",
                    String.format(java.util.Locale.ROOT, "+%.2f%% damage and +%.2f%% critical damage per rank while wielding %s.",
                            tuning.flourishDamagePerRank * 100, tuning.flourishCritDamagePerRank * 100, weaponClass.displayName),
                    3, weaponClass, false, effect(Bonus.DAMAGE_PERCENT, tuning.flourishDamagePerRank),
                    effect(Bonus.CRIT_DAMAGE_PERCENT, tuning.flourishCritDamagePerRank)));
            add(nodes, byId, weaponNode(prefix + "_edge_path", weaponClass.category, weaponClass.displayName + " Edge",
                    String.format(java.util.Locale.ROOT, "+%.2f%% damage per rank while wielding %s.",
                            tuning.damagePerRank * 0.45 * 100, weaponClass.displayName),
                    3, weaponClass, false, effect(Bonus.DAMAGE_PERCENT, tuning.damagePerRank * 0.45)));
            add(nodes, byId, weaponNode(prefix + "_focus_path", weaponClass.category, weaponClass.displayName + " Focus",
                    String.format(java.util.Locale.ROOT, "+%.2f%% critical chance and +%.2f%% critical damage per rank while wielding %s.",
                            tuning.critChancePerRank * 0.5 * 100, tuning.critDamagePerRank * 0.5 * 100,
                            weaponClass.displayName),
                    3, weaponClass, false, effect(Bonus.CRIT_CHANCE_PERCENT, tuning.critChancePerRank * 0.5),
                    effect(Bonus.CRIT_DAMAGE_PERCENT, tuning.critDamagePerRank * 0.5)));
            add(nodes, byId, weaponNode(prefix + "_tempo_path", weaponClass.category, weaponClass.displayName + " Tempo",
                    String.format(java.util.Locale.ROOT, "+%.2f%% damage and +%.2f%% critical damage per rank while wielding %s.",
                            tuning.flourishDamagePerRank * 0.5 * 100, tuning.flourishCritDamagePerRank * 0.5 * 100,
                            weaponClass.displayName),
                    3, weaponClass, false, effect(Bonus.DAMAGE_PERCENT, tuning.flourishDamagePerRank * 0.5),
                    effect(Bonus.CRIT_DAMAGE_PERCENT, tuning.flourishCritDamagePerRank * 0.5)));
            add(nodes, byId, weaponNode(prefix + "_edge_branch", weaponClass.category, weaponClass.displayName + " Edge Branch",
                    String.format(java.util.Locale.ROOT, "+%.2f%% damage per rank while wielding %s.",
                            tuning.damagePerRank * 0.35 * 100, weaponClass.displayName),
                    3, weaponClass, false, effect(Bonus.DAMAGE_PERCENT, tuning.damagePerRank * 0.35)));
            add(nodes, byId, weaponNode(prefix + "_focus_branch", weaponClass.category, weaponClass.displayName + " Focus Branch",
                    String.format(java.util.Locale.ROOT, "+%.2f%% critical chance and +%.2f%% critical damage per rank while wielding %s.",
                            tuning.critChancePerRank * 0.3 * 100, tuning.critDamagePerRank * 0.3 * 100,
                            weaponClass.displayName),
                    3, weaponClass, false, effect(Bonus.CRIT_CHANCE_PERCENT, tuning.critChancePerRank * 0.3),
                    effect(Bonus.CRIT_DAMAGE_PERCENT, tuning.critDamagePerRank * 0.3)));
        }

        // Purchasable stat nodes form the central octagonal guide and gate its branches.
        for (int i = 0; i < 8; i++) add(nodes, byId, pathNode("path_" + i, "Path " + (i + 1)));

        List<GraphEdge> edges = new ArrayList<>();
        // Small stat constellations branch out from keystones; only their notable links
        // connect them to neighboring clusters.
        connect(edges, "vitality_health", "vitality_vigor");
        connect(edges, "vitality_vigor", "vitality_regen");
        connect(edges, "vitality_regen", "vitality_pulse");
        connect(edges, "vitality_pulse", "vitality_core");
        connect(edges, "vitality_core", "vitality_resolve");
        connect(edges, "vitality_core", "hybrid_life");
        connect(edges, "hybrid_life", "hybrid_guardian");
        connect(edges, "guard_core", "hybrid_last_stand");
        connect(edges, "arcana_mana", "arcana_resonance");
        connect(edges, "arcana_resonance", "arcana_regen");
        connect(edges, "arcana_regen", "arcana_focus");
        connect(edges, "arcana_focus", "arcana_core");
        connect(edges, "arcana_core", "arcana_spark");
        connect(edges, "arcana_core", "arcana_lore_insight");
        connect(edges, "arcana_core", "hybrid_reserve");
        connect(edges, "endurance_stamina", "endurance_reserve");
        connect(edges, "endurance_reserve", "endurance_regen");
        connect(edges, "endurance_regen", "endurance_rhythm");
        connect(edges, "endurance_rhythm", "endurance_core");
        connect(edges, "endurance_core", "endurance_drive");
        connect(edges, "endurance_core", "hybrid_flow");
        connect(edges, "combat_damage", "combat_momentum");
        connect(edges, "combat_momentum", "combat_crit_chance");
        connect(edges, "combat_crit_chance", "combat_precision");
        connect(edges, "combat_precision", "combat_crit_damage");
        connect(edges, "combat_crit_damage", "combat_finesse");
        connect(edges, "combat_finesse", "combat_core");
        connect(edges, "combat_core", "combat_execution");
        connect(edges, "combat_core", "combat_tempo");
        connect(edges, "guard_resistance", "guard_bastion");
        connect(edges, "guard_bastion", "guard_core");
        connect(edges, "guard_core", "guard_steadfast");
        connect(edges, "combat_core", "hybrid_precision");
        connect(edges, "hybrid_precision", "hybrid_assault");
        // The octagonal guide ring is the only direct cross-constellation route.
        for (int i = 0; i < 8; i++) connect(edges, "path_" + i, "path_" + ((i + 1) % 8));
        connect(edges, "vitality_resolve", "path_7");
        connect(edges, "arcana_spark", "path_0");
        connect(edges, "combat_core", "path_2");
        connect(edges, "guard_core", "path_5");
        connect(edges, "endurance_core", "path_4");

        WeaponClass[] classes = WeaponClass.values();
        for (int i = 0; i < classes.length; i++) {
            WeaponClass current = classes[i];
            String prefix = current.name().toLowerCase(java.util.Locale.ROOT);
            connect(edges, prefix + "_core", prefix + "_edge_path");
            connect(edges, prefix + "_edge_path", prefix + "_mastery");
            connect(edges, prefix + "_edge_path", prefix + "_edge_branch");
            connect(edges, prefix + "_mastery", prefix + "_focus_path");
            connect(edges, prefix + "_focus_path", prefix + "_technique");
            connect(edges, prefix + "_focus_path", prefix + "_focus_branch");
            connect(edges, prefix + "_technique", prefix + "_tempo_path");
            connect(edges, prefix + "_tempo_path", prefix + "_hybrid");
        }
        // Class constellations enter the web at separate guide points.
        connect(edges, "daggers_hybrid", "path_7");
        connect(edges, "swords_hybrid", "path_0");
        connect(edges, "axes_hybrid", "path_1");
        connect(edges, "maces_hybrid", "path_2");
        connect(edges, "spears_hybrid", "path_3");
        connect(edges, "shortbows_hybrid", "path_4");
        connect(edges, "crossbows_hybrid", "path_5");
        connect(edges, "staffs_hybrid", "path_6");

        Map<String, List<String>> adjacency = new HashMap<>();
        for (Node node : nodes) adjacency.put(node.id, new ArrayList<>());
        Set<String> uniqueEdges = new HashSet<>();
        List<GraphEdge> normalizedEdges = new ArrayList<>();
        for (GraphEdge edge : edges) {
            if (!byId.containsKey(edge.first()) || !byId.containsKey(edge.second()) || edge.first().equals(edge.second())) continue;
            String key = edge.first().compareTo(edge.second()) < 0
                    ? edge.first() + "|" + edge.second() : edge.second() + "|" + edge.first();
            if (!uniqueEdges.add(key)) continue;
            adjacency.get(edge.first()).add(edge.second());
            adjacency.get(edge.second()).add(edge.first());
            normalizedEdges.add(new GraphEdge(edge.first(), edge.second()));
        }
        nodes.forEach(node -> node.setConnections(adjacency.getOrDefault(node.id, List.of())));
        NODES = List.copyOf(nodes);
        NODE_BY_ID = Map.copyOf(byId);
        GRAPH_EDGES = List.copyOf(normalizedEdges);
    }
    private static File dataDirectory;

    private CharacterPathService() {}

    private static NodeBonus effect(Bonus bonus, double perRank) {
        return new NodeBonus(bonus, perRank);
    }

    private static Node node(String id, Category category, String name, String description,
                             int maxRank, String prerequisite, NodeBonus... bonuses) {
        return new Node(id, category, name, description, maxRank, prerequisite, false, false, null, List.of(bonuses));
    }

    private static Node pathNode(String id, String name) {
        int index;
        try { index = Integer.parseInt(id.substring(id.lastIndexOf('_') + 1)); }
        catch (Exception ignored) { index = 0; }
        Bonus stat = switch (Math.floorMod(index, 3)) {
            case 1 -> Bonus.MAX_MANA_FLAT;
            case 2 -> Bonus.MAX_STAMINA_FLAT;
            default -> Bonus.MAX_HEALTH_FLAT;
        };
        Category category = switch (stat) {
            case MAX_MANA_FLAT -> Category.ARCANA;
            case MAX_STAMINA_FLAT -> Category.ENDURANCE;
            default -> Category.VITALITY;
        };
        return new Node(id, category, name, "Path node · +1 " + stat.name().replace('_', ' ')
                + "; allocate it to continue along this branch.", 1, null, false, true, null,
                List.of(effect(stat, 1.0)));
    }

    private static Node weaponNode(String id, Category category, String name, String description,
                                   int maxRank, WeaponClass weaponClass, boolean core, NodeBonus... bonuses) {
        return new Node(id, category, name, description, maxRank, null, core, false, weaponClass, List.of(bonuses));
    }

    private static void add(List<Node> nodes, Map<String, Node> byId, Node node) {
        if (byId.putIfAbsent(node.id, node) == null) nodes.add(node);
    }

    private static void connect(List<GraphEdge> edges, String first, String second) {
        edges.add(new GraphEdge(first, second));
    }

    private static WeaponTuning tuning(WeaponClass weaponClass) {
        return switch (weaponClass) {
            case DAGGERS -> new WeaponTuning(0.0035, 0.0020, 0.0030, 0.0015, 0.0050);
            case SWORDS -> new WeaponTuning(0.0030, 0.0015, 0.0040, 0.0015, 0.0050);
            case AXES -> new WeaponTuning(0.0040, 0.0010, 0.0040, 0.0015, 0.0040);
            case MACES -> new WeaponTuning(0.0040, 0.0010, 0.0050, 0.0015, 0.0040);
            case SPEARS -> new WeaponTuning(0.0030, 0.0015, 0.0030, 0.0015, 0.0050);
            case SHORTBOWS -> new WeaponTuning(0.0025, 0.0015, 0.0030, 0.0010, 0.0060);
            case CROSSBOWS -> new WeaponTuning(0.0035, 0.0010, 0.0050, 0.0010, 0.0040);
            case STAFFS -> new WeaponTuning(0.0025, 0.0010, 0.0040, 0.0010, 0.0050);
        };
    }

    private record WeaponTuning(double damagePerRank, double critChancePerRank,
                                double critDamagePerRank, double flourishDamagePerRank,
                                double flourishCritDamagePerRank) {}

    public static synchronized void initialize(File dataDirectory) {
        CharacterPathService.dataDirectory = dataDirectory;
        PROFILES.clear();
    }

    public static List<Node> nodes(Category category) {
        if (category == null) return List.of();
        return NODES.stream().filter(node -> node.category == category).toList();
    }

    public static List<Node> nodes() {
        return NODES;
    }

    public static List<GraphEdge> graphEdges() { return GRAPH_EDGES; }

    /** Replaces the live tree with an authored definition while preserving valid player ranks. */
    public static synchronized void replaceTree(List<NodeDefinition> definitions, List<GraphEdge> edges) {
        List<Node> nodes = new ArrayList<>();
        Map<String, Node> byId = new LinkedHashMap<>();
        if (definitions != null) {
            for (NodeDefinition definition : definitions) {
                if (definition == null || definition.id() == null || definition.id().isBlank()) continue;
                List<NodeBonus> bonuses = definition.bonuses() == null ? List.of() : definition.bonuses();
                Node node = new Node(definition.id(), definition.category(),
                        definition.name() == null ? definition.id() : definition.name(),
                        definition.description() == null ? "" : definition.description(),
                        Math.max(0, Math.min(20, definition.maxRank())), null,
                        definition.core(), definition.pathOnly(), definition.weaponClass(), bonuses);
                add(nodes, byId, node);
            }
        }

        Map<String, List<String>> adjacency = new LinkedHashMap<>();
        for (Node node : nodes) adjacency.put(node.id, new ArrayList<>());
        Set<String> uniqueEdges = new HashSet<>();
        List<GraphEdge> normalizedEdges = new ArrayList<>();
        if (edges != null) {
            for (GraphEdge edge : edges) {
                if (edge == null || edge.first() == null || edge.second() == null
                        || edge.first().equals(edge.second())
                        || !byId.containsKey(edge.first()) || !byId.containsKey(edge.second())) continue;
                String key = edge.first().compareTo(edge.second()) < 0
                        ? edge.first() + "|" + edge.second() : edge.second() + "|" + edge.first();
                if (!uniqueEdges.add(key)) continue;
                adjacency.get(edge.first()).add(edge.second());
                adjacency.get(edge.second()).add(edge.first());
                normalizedEdges.add(edge);
            }
        }
        nodes.forEach(node -> node.setConnections(adjacency.getOrDefault(node.id, List.of())));

        NODES = List.copyOf(nodes);
        NODE_BY_ID = Map.copyOf(byId);
        GRAPH_EDGES = List.copyOf(normalizedEdges);

        for (Map.Entry<UUID, Profile> entry : PROFILES.entrySet()) {
            Profile profile = entry.getValue();
            if (profile.ranks == null) profile.ranks = new LinkedHashMap<>();
            if (profile.pathChoices == null) profile.pathChoices = new LinkedHashMap<>();
            profile.ranks.entrySet().removeIf(rank -> {
                Node node = NODE_BY_ID.get(rank.getKey());
                return node == null || rank.getValue() == null
                        || rank.getValue() <= 0 || rank.getValue() > node.maxRank;
            });
            profile.pathChoices.entrySet().removeIf(choice -> {
                Node node = NODE_BY_ID.get(choice.getKey());
                return node == null || !node.pathOnly || !profile.ranks.containsKey(choice.getKey())
                        || pathBonus(choice.getValue()) == null;
            });
            // Preserve old allocated guide nodes by assigning their authored stat as
            // the player's initial choice when migrating an older profile.
            for (String nodeId : profile.ranks.keySet()) {
                Node node = NODE_BY_ID.get(nodeId);
                if (node != null && node.pathOnly && !profile.pathChoices.containsKey(nodeId)) {
                    Bonus authored = node.bonus;
                    profile.pathChoices.put(nodeId, authored == Bonus.MAX_MANA_FLAT ? "MANA"
                            : authored == Bonus.MAX_STAMINA_FLAT ? "STAMINA" : "HEALTH");
                }
            }
            pruneDisconnectedRanks(profile);
            save(entry.getKey(), profile);
        }
    }

    public static WeaponClass weaponClassFor(String itemId) {
        if (itemId == null) return null;
        for (WeaponClass weaponClass : WeaponClass.values()) {
            if (itemId.regionMatches(true, 0, weaponClass.itemPrefix, 0, weaponClass.itemPrefix.length())) {
                return weaponClass;
            }
        }
        return null;
    }

    public static synchronized Profile profile(Player player) {
        UUID id = playerId(player);
        if (id == null) return new Profile();
        return profile(id);
    }

    public static synchronized Profile profile(UUID id) {
        if (id == null) return new Profile();
        return PROFILES.computeIfAbsent(id, CharacterPathService::load);
    }

    public static synchronized int level(Player player) {
        return pointsFromXp(profile(player).xp) + 1;
    }

    public static synchronized int availablePoints(Player player) {
        Profile profile = profile(player);
        int earned = pointsFromXp(profile.xp);
        return Math.max(0, earned - spentPoints(profile));
    }

    public static synchronized int rank(Player player, String nodeId) {
        return profile(player).ranks.getOrDefault(nodeId, 0);
    }

    public static synchronized String pathChoice(Player player, String nodeId) {
        return profile(player).pathChoices.get(nodeId);
    }

    /** A node is unlocked only by a directly connected allocated node (or when it is a core). */
    public static synchronized boolean isUnlocked(Player player, String nodeId) {
        Node target = NODE_BY_ID.get(nodeId);
        if (target == null) return false;
        if (target.core) return true;
        Profile profile = profile(player);
        return target.connections().stream().anyMatch(connectedId ->
                profile.ranks.getOrDefault(connectedId, 0) > 0);
    }

    public static synchronized double bonus(Player player, Bonus bonus) {
        if (player == null || bonus == null) return 0.0d;
        return bonus(profile(player), bonus, null, false);
    }

    /** Global path bonuses plus the constellation for the weapon currently held. */
    public static synchronized double bonusForWeapon(Player player, String itemId, Bonus bonus) {
        if (player == null || bonus == null) return 0.0d;
        return bonus(profile(player), bonus, weaponClassFor(itemId), true);
    }

    private static double bonus(Profile profile, Bonus bonus, WeaponClass activeClass, boolean includeMatchingWeaponNodes) {
        if (profile == null || profile.ranks == null) return 0.0d;
        double total = 0.0d;
        for (Node node : NODES) {
            if (node.weaponClass != null
                    && (!includeMatchingWeaponNodes || activeClass == null || node.weaponClass != activeClass)) continue;
            int rank = profile.ranks.getOrDefault(node.id, 0);
            if (rank <= 0) continue;
            if (node.pathOnly) {
                if (pathBonus(profile.pathChoices.get(node.id)) == bonus) total += rank;
                continue;
            }
            for (NodeBonus effect : node.bonuses) {
                if (effect.bonus() == bonus) total += effect.perRank() * rank;
            }
        }
        return total;
    }

    public static synchronized boolean allocate(Player player, String nodeId) {
        UUID id = playerId(player);
        Node target = NODE_BY_ID.get(nodeId);
        if (id == null || target == null) return false;
        Profile profile = profile(id);
        int rank = profile.ranks.getOrDefault(nodeId, 0);
        if (target.pathOnly || rank >= target.maxRank || availablePoints(player) < 1) return false;
        if (!target.core && !isUnlocked(player, nodeId)) return false;
        profile.ranks.put(nodeId, rank + 1);
        save(id, profile);
        return true;
    }

    /** Allocates a path node with the player's chosen +1 core stat. */
    public static synchronized boolean allocatePath(Player player, String nodeId, String choice) {
        UUID id = playerId(player);
        Node target = nodeId == null ? null : NODE_BY_ID.get(nodeId);
        Bonus selectedBonus = pathBonus(choice);
        if (id == null || target == null || !target.pathOnly || selectedBonus == null
                || profile(id).ranks.getOrDefault(nodeId, 0) > 0
                || availablePoints(player) < 1 || !isUnlocked(player, nodeId)) return false;
        Profile profile = profile(id);
        profile.ranks.put(nodeId, 1);
        profile.pathChoices.put(nodeId, pathChoiceName(selectedBonus));
        save(id, profile);
        return true;
    }

    public static Bonus pathBonus(String choice) {
        if (choice == null) return null;
        return switch (choice.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "HEALTH", "HP", "MAX_HEALTH_FLAT" -> Bonus.MAX_HEALTH_FLAT;
            case "MANA", "MP", "MAX_MANA_FLAT" -> Bonus.MAX_MANA_FLAT;
            case "STAMINA", "MAX_STAMINA_FLAT" -> Bonus.MAX_STAMINA_FLAT;
            default -> null;
        };
    }

    public static String pathChoiceName(Bonus bonus) {
        if (bonus == Bonus.MAX_MANA_FLAT) return "MANA";
        if (bonus == Bonus.MAX_STAMINA_FLAT) return "STAMINA";
        return "HEALTH";
    }

    public static String pathIcon(String choice) {
        return switch (pathChoiceName(pathBonus(choice))) {
            case "MANA" -> "Potion_Mana";
            case "STAMINA" -> "Potion_Stamina";
            default -> "Potion_Health";
        };
    }

    /** Grants test XP without attributing it to a bench process. */
    public static synchronized long grantXp(Player player, long amount) {
        UUID id = playerId(player);
        if (id == null || amount <= 0) return -1L;
        Profile profile = profile(id);
        long currentXp = Math.max(0L, profile.xp);
        profile.xp = amount > Long.MAX_VALUE - currentXp ? Long.MAX_VALUE : currentXp + amount;
        save(id, profile);
        return profile.xp;
    }

    /** Clears all progression for operator testing, including XP and allocations. */
    public static synchronized boolean resetProgress(Player player) {
        UUID id = playerId(player);
        if (id == null) return false;
        Profile profile = profile(id);
        profile.xp = 0L;
        profile.ranks.clear();
        profile.pathChoices.clear();
        save(id, profile);
        return true;
    }

    /** Refunds every allocated rank while preserving earned XP. */
    public static synchronized int respec(Player player) {
        UUID id = playerId(player);
        if (id == null) return -1;
        Profile profile = profile(id);
        int refunded = spentPoints(profile);
        if (refunded > 0) {
            profile.ranks.clear();
            profile.pathChoices.clear();
            save(id, profile);
        }
        return refunded;
    }

    /** Refunds the requested node and any allocations that become disconnected, preserving earned XP. */
    public static synchronized int respec(Player player, String nodeId) {
        UUID id = playerId(player);
        Node node = nodeId == null ? null : NODE_BY_ID.get(nodeId);
        if (id == null || node == null) return -1;
        Profile profile = profile(id);
        int refunded = Math.max(0, profile.ranks.getOrDefault(nodeId, 0));
        if (refunded <= 0) return 0;

        profile.ranks.remove(nodeId);
        profile.pathChoices.remove(nodeId);
        Set<String> connectedToAllocatedCore = connectedAllocatedNodes(profile);
        for (String allocatedId : new ArrayList<>(profile.ranks.keySet())) {
            if (connectedToAllocatedCore.contains(allocatedId)) continue;
            Integer disconnectedRanks = profile.ranks.remove(allocatedId);
            profile.pathChoices.remove(allocatedId);
            if (disconnectedRanks != null) refunded += Math.max(0, disconnectedRanks);
        }
        save(id, profile);
        return refunded;
    }

    /**
     * Finds allocated nodes that remain connected to an allocated core after a respec.
     * Every node, including a path node, only passes the search when it still has at
     * least one allocated rank. This preserves alternate allocated routes and removes
     * branches whose only purchased connection was refunded.
     */
    private static Set<String> connectedAllocatedNodes(Profile profile) {
        Set<String> connected = new HashSet<>();
        java.util.ArrayDeque<String> pending = new java.util.ArrayDeque<>();
        for (Node node : NODES) {
            if (!node.core || profile.ranks.getOrDefault(node.id, 0) <= 0) continue;
            if (connected.add(node.id)) pending.addLast(node.id);
        }

        while (!pending.isEmpty()) {
            Node current = NODE_BY_ID.get(pending.removeFirst());
            if (current == null) continue;
            for (String connectedId : current.connections()) {
                Node neighbor = NODE_BY_ID.get(connectedId);
                if (neighbor == null) continue;
                boolean traversable = profile.ranks.getOrDefault(neighbor.id, 0) > 0;
                if (traversable && connected.add(neighbor.id)) pending.addLast(neighbor.id);
            }
        }
        return connected;
    }

    private static void pruneDisconnectedRanks(Profile profile) {
        Set<String> connected = connectedAllocatedNodes(profile);
        profile.ranks.entrySet().removeIf(rank -> !connected.contains(rank.getKey()));
        if (profile.pathChoices != null) {
            profile.pathChoices.keySet().removeIf(nodeId -> !profile.ranks.containsKey(nodeId));
        }
    }

    /** Awards XP only at successful bench operation points, after their inputs are committed. */
    public static synchronized void awardBenchXp(Player player, int amount, String processName) {
        UUID id = playerId(player);
        if (id == null || amount <= 0) return;
        Profile profile = profile(id);
        int oldPoints = pointsFromXp(profile.xp);
        profile.xp = Math.max(0L, profile.xp) + amount;
        int newPoints = pointsFromXp(profile.xp);
        save(id, profile);
        String label = processName == null || processName.isBlank() ? "bench work" : processName;
        String message = "+" + amount + " character XP from " + label + " (" + profile.xp + " XP)";
        if (newPoints > oldPoints) message += " - skill point earned";
        player.getPlayerRef().sendMessage(Message.raw(message));
    }

    private static int spentPoints(Profile profile) {
        int spent = 0;
        if (profile.ranks != null) for (int rank : profile.ranks.values()) spent += Math.max(0, rank);
        return spent;
    }

    private static int pointsFromXp(long xp) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, xp) / XP_PER_LEVEL);
    }

    private static UUID playerId(Player player) {
        try { return player == null || player.getPlayerRef() == null ? null : player.getPlayerRef().getUuid(); }
        catch (Throwable ignored) { return null; }
    }

    private static Profile load(UUID id) {
        if (dataDirectory == null) return new Profile();
        File file = new File(dataDirectory, id + ".json");
        if (!file.isFile()) return new Profile();
        try {
            Profile profile = GSON.fromJson(Files.readString(file.toPath(), StandardCharsets.UTF_8), Profile.class);
            if (profile == null) return new Profile();
            profile.xp = Math.max(0L, profile.xp);
            if (profile.ranks == null) profile.ranks = new LinkedHashMap<>();
            if (profile.pathChoices == null) profile.pathChoices = new LinkedHashMap<>();
            Map<String, Integer> validRanks = new LinkedHashMap<>();
            for (Node node : NODES) {
                int rank = Math.max(0, Math.min(node.maxRank, profile.ranks.getOrDefault(node.id, 0)));
                if (rank > 0) validRanks.put(node.id, rank);
            }
            profile.ranks = validRanks;
            profile.pathChoices.entrySet().removeIf(choice -> {
                Node node = NODE_BY_ID.get(choice.getKey());
                return node == null || !node.pathOnly || !profile.ranks.containsKey(choice.getKey())
                        || pathBonus(choice.getValue()) == null;
            });
            for (String nodeId : profile.ranks.keySet()) {
                Node node = NODE_BY_ID.get(nodeId);
                if (node != null && node.pathOnly && !profile.pathChoices.containsKey(nodeId)) {
                    Bonus authored = node.bonus;
                    profile.pathChoices.put(nodeId, pathChoiceName(authored));
                }
            }
            pruneDisconnectedRanks(profile);
            return profile;
        } catch (Exception exception) {
            System.err.println("[SocketReforge] Failed loading character path " + id + ": " + exception.getMessage());
            return new Profile();
        }
    }

    private static void save(UUID id, Profile profile) {
        if (dataDirectory == null || id == null) return;
        try {
            Files.createDirectories(dataDirectory.toPath());
            Files.writeString(new File(dataDirectory, id + ".json").toPath(),
                    GSON.toJson(profile) + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            System.err.println("[SocketReforge] Failed saving character path " + id + ": " + exception.getMessage());
        }
    }
}
